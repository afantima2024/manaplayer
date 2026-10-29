package com.macarmusic.app;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsProvider;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

// This standalone test-APK process deliberately depends only on the Android/Java runtime.
public class FixtureDocumentsProvider extends DocumentsProvider {
    private static volatile Bundle controllerStatus = Bundle.EMPTY;
    private static volatile Bundle widgetStatus = Bundle.EMPTY;
    private volatile boolean unavailable;
    private volatile String lyricsMode = "available";
    private volatile long lyricDelayMs;
    private final AtomicInteger activeLyricReads = new AtomicInteger();
    private final AtomicInteger lyricReads = new AtomicInteger();
    private final AtomicInteger audioReads = new AtomicInteger();
    private final AtomicInteger childReads = new AtomicInteger();
    private final AtomicInteger deleteRequests = new AtomicInteger();
    private final AtomicInteger activeAudioReads = new AtomicInteger();
    private final AtomicInteger maxAudioReads = new AtomicInteger();
    private volatile long audioDelayMs;
    private volatile long childDelayMs;
    private volatile int extraRows;
    private volatile boolean unknownFileStamp;
    private volatile long fileStampOffset;
    private File tone;
    private File lyrics;
    private File scrollableLyrics;
    private File plainLyrics;
    private File badLyrics;
    private File alternateLyrics;
    private File secondAlternateLyrics;
    private File changedAlternateLyrics;
    private File picture;
    private File secondPicture;
    private static final String[] COLUMNS = {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_FLAGS,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    };

    @Override public boolean onCreate() {
        tone = new File(getContext().getFilesDir(), "synthetic-tone.wav");
        if (!tone.exists()) {
            int rate = 22050;
            int samples = rate * 60;
            ByteBuffer bytes = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN);
            bytes.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + samples * 2)
                .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII))
                .putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2)
                .putShort((short) 2).putShort((short) 16).put("data".getBytes(StandardCharsets.US_ASCII))
                .putInt(samples * 2);
            for (int index = 0; index < samples; index++) {
                bytes.putShort((short) (Math.sin(2 * Math.PI * 440 * index / rate) * 1000));
            }
            try (FileOutputStream output = new FileOutputStream(tone)) {
                output.write(bytes.array());
            } catch (IOException error) {
                throw new IllegalStateException("Cannot create synthetic test audio", error);
            }
        }
        lyrics = fixture("synthetic.lrc", "[ti:Test fixture]\n[00:05.00]Synthetic start\n[00:10.00]Synthetic middle\n[00:30.00]Synthetic ending");
        StringBuilder scrolling = new StringBuilder();
        for (int index = 0; index < 30; index++) {
            scrolling.append(String.format(java.util.Locale.ROOT, "[00:%02d.00]Synthetic lyric %02d\n", index * 2, index));
        }
        scrollableLyrics = fixture("scrollable.lrc", scrolling.toString());
        plainLyrics = fixture("synthetic.txt", "Nested text fixture\nSecond plain marker");
        badLyrics = fixture("invalid.txt", "<html>Simulated invalid lyric document</html>");
        alternateLyrics = fixture("alternate.lrc", "[00:00]Synthetic local alternate\n[00:30]Alternate ending");
        secondAlternateLyrics = fixture("second-alternate.lrc", "[00:00]Synthetic local third\n[00:30]Third ending");
        changedAlternateLyrics = fixture("changed-alternate.lrc", "[00:00]Synthetic changed file\n[00:30]Changed ending");
        picture = new File(getContext().getFilesDir(), "synthetic-background.png");
        Bitmap bitmap = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                bitmap.setPixel(x, y, (x / 16 + y / 16) % 2 == 0 ? 0xff3474c0 : 0xffffbe5d);
            }
        }
        try (FileOutputStream output = new FileOutputStream(picture)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("Fixture compression failed");
        } catch (IOException error) {
            throw new IllegalStateException("Cannot create synthetic image", error);
        } finally {
            bitmap.recycle();
        }
        secondPicture = new File(getContext().getFilesDir(), "synthetic-second-background.png");
        Bitmap second = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888);
        second.eraseColor(0xff82386f);
        try (FileOutputStream output = new FileOutputStream(secondPicture)) {
            if (!second.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("Fixture compression failed");
        } catch (IOException error) {
            throw new IllegalStateException("Cannot create second synthetic image", error);
        } finally {
            second.recycle();
        }
        return true;
    }

    private File fixture(String name, String text) {
        File file = new File(getContext().getFilesDir(), name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot create synthetic fixture", error);
        }
        return file;
    }

    @Override public Cursor queryRoots(String[] projection) {
        String[] columns = projection != null ? projection : new String[] {
            DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE, DocumentsContract.Root.COLUMN_FLAGS
        };
        MatrixCursor cursor = new MatrixCursor(columns);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            Object value = null;
            if (column.equals(DocumentsContract.Root.COLUMN_ROOT_ID)) value = "fixture";
            if (column.equals(DocumentsContract.Root.COLUMN_DOCUMENT_ID)) value = "music";
            if (column.equals(DocumentsContract.Root.COLUMN_TITLE)) value = "ManaPlayer test audio";
            if (column.equals(DocumentsContract.Root.COLUMN_FLAGS)) value = 0;
            row.add(column, value);
        }
        return cursor;
    }

    @Override public Cursor queryDocument(String id, String[] projection) throws FileNotFoundException {
        checkAvailable();
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : COLUMNS);
        documentRow(cursor, id);
        return cursor;
    }

    @Override public Cursor queryChildDocuments(String parent, String[] projection, String sortOrder)
            throws FileNotFoundException {
        checkAvailable();
        childReads.incrementAndGet();
        android.os.SystemClock.sleep(childDelayMs);
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : COLUMNS);
        if (parent.equals("music")) {
            documentRow(cursor, "music/album");
            documentRow(cursor, "music/one");
            documentRow(cursor, "music/ignored");
            if (!lyricsMode.equals("missing")) documentRow(cursor, "music/lyric-data");
            if (lyricsMode.startsWith("versions")) {
                documentRow(cursor, "music/lyric-alternate");
                documentRow(cursor, "music/lyric-third");
            }
            for (int index = 0; index < extraRows; index++) {
                documentRow(cursor, "music/large-" + index);
            }
        } else if (parent.equals("music/album")) {
            documentRow(cursor, "music/album/two");
            if (!lyricsMode.equals("missing")) documentRow(cursor, "music/album/lyric-data");
        } else if (!parent.equals("empty")) {
            throw new FileNotFoundException(parent);
        }
        return cursor;
    }

    @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        checkAvailable();
        if (!mode.equals("r")) throw new FileNotFoundException(id);
        if (id.equals("music/one") || id.equals("music/album/two") || id.matches("music/large-[0-9]+")) {
            audioReads.incrementAndGet();
            int active = activeAudioReads.incrementAndGet();
            synchronized (maxAudioReads) { maxAudioReads.set(Math.max(maxAudioReads.get(), active)); }
            try {
                android.os.SystemClock.sleep(audioDelayMs);
                checkAvailable();
                if (signal != null) signal.throwIfCanceled();
                return ParcelFileDescriptor.open(tone, ParcelFileDescriptor.MODE_READ_ONLY);
            } finally {
                activeAudioReads.decrementAndGet();
            }
        }
        File selected;
        switch (id) {
            case "music/lyric-data":
                lyricReads.incrementAndGet();
                activeLyricReads.incrementAndGet();
                try {
                    android.os.SystemClock.sleep(lyricDelayMs);
                } finally {
                    activeLyricReads.decrementAndGet();
                }
                if (lyricsMode.equals("unreadable")) throw new FileNotFoundException("Synthetic lyric read failure");
                selected = lyricsMode.equals("invalid") ? badLyrics : lyricsMode.equals("scrollable") ? scrollableLyrics : lyrics;
                break;
            case "music/album/lyric-data": selected = plainLyrics; break;
            case "music/lyric-alternate":
                if (lyricsMode.equals("versions-unreadable")) throw new FileNotFoundException("Synthetic alternate failure");
                if (lyricsMode.equals("versions-denied")) throw new SecurityException("Synthetic alternate access denied");
                selected = lyricsMode.equals("versions-changed") ? changedAlternateLyrics : alternateLyrics;
                break;
            case "music/lyric-third": selected = secondAlternateLyrics; break;
            case "pictures/background": selected = picture; break;
            case "pictures/second": selected = secondPicture; break;
            case "pictures/invalid": selected = badLyrics; break;
            default: throw new FileNotFoundException(id);
        }
        return ParcelFileDescriptor.open(selected, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public boolean isChildDocument(String parent, String id) {
        return id.startsWith(parent + "/");
    }

    @Override public void deleteDocument(String id) throws FileNotFoundException {
        deleteRequests.incrementAndGet();
        throw new FileNotFoundException("Music references must never delete the fixture files");
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (method.equals("scanFixtureState")) {
            if (extras != null) {
                unknownFileStamp = extras.getBoolean("unknownStamp");
                fileStampOffset = extras.getLong("stampOffset");
                audioDelayMs = Math.max(0, Math.min(10000, extras.getLong("audioDelayMs")));
                childDelayMs = Math.max(0, Math.min(10000, extras.getLong("childDelayMs")));
                extraRows = Math.max(0, Math.min(10000, extras.getInt("extraRows")));
                if (extras.getBoolean("resetReads")) {
                    audioReads.set(0);
                    childReads.set(0);
                    deleteRequests.set(0);
                    maxAudioReads.set(0);
                }
            }
            Bundle result = new Bundle();
            result.putInt("audioReads", audioReads.get());
            result.putInt("childReads", childReads.get());
            result.putInt("activeAudioReads", activeAudioReads.get());
            result.putInt("maxAudioReads", maxAudioReads.get());
            result.putInt("deleteRequests", deleteRequests.get());
            return result;
        }
        if (method.equals("fixtureExternalDirectory")) {
            File directory = getContext().getExternalFilesDir(null);
            if (directory == null) throw new IllegalStateException("Fixture storage unavailable");
            Bundle result = new Bundle();
            result.putString("path", directory.getAbsolutePath());
            return result;
        }
        if (method.equals("seedIndexedMusic")) {
            try {
                return seedIndexedMusic("benchmark".equals(arg));
            } catch (IOException error) {
                throw new IllegalStateException("Could not create indexed audio fixtures", error);
            }
        }
        if (method.equals("clearIndexedMusic")) {
            clearIndexedMusic();
            return Bundle.EMPTY;
        }
        if (method.equals("setLyricReadDelay")) {
            lyricDelayMs = Math.max(0, Math.min(10000, Long.parseLong(arg)));
            lyricReads.set(0);
            return Bundle.EMPTY;
        }
        if (method.equals("lyricReadState")) {
            Bundle state = new Bundle();
            state.putInt("active", activeLyricReads.get());
            state.putInt("reads", lyricReads.get());
            return state;
        }
        if (method.equals("controllerStatus")) return new Bundle(controllerStatus);
        if (method.equals("widgetStatus")) return new Bundle(widgetStatus);
        if (method.equals("widgetCommand")) {
            FixtureWidgetHostActivity.command(arg, extras == null ? "" : extras.getString("value", ""));
            return Bundle.EMPTY;
        }
        if (method.equals("resetControllerStatus")) {
            controllerStatus = Bundle.EMPTY;
            return Bundle.EMPTY;
        }
        if (method.equals("controllerCommand")) {
            FixtureMediaControllerActivity.command(arg, extras == null ? "" : extras.getString("value", ""));
            return Bundle.EMPTY;
        }
        if (method.equals("setUnavailable")) {
            unavailable = extras != null && extras.getBoolean("value");
            return Bundle.EMPTY;
        }
        if (method.equals("setLyricsMode")) {
            lyricsMode = arg == null ? "available" : arg;
            return Bundle.EMPTY;
        }
        return super.call(method, arg, extras);
    }

    @SuppressWarnings("deprecation")
    private Bundle seedIndexedMusic(boolean benchmark) throws IOException {
        clearIndexedMusic();
        String[] folders = {
            "Music/ManaPlayer_Compatibility/", "Music/ManaPlayer_Compatibility/Child/",
            "Music/ManaPlayer_Compatibility 2/", "Music/ManaPlayerXCompatibility/"
        };
        if (benchmark) {
            folders = new String[120];
            for (int index = 0; index < folders.length; index++) {
                folders[index] = "Music/ManaPlayer_ScanPerf/Group" + (index % 12) + "/";
            }
        }
        ArrayList<String> uris = new ArrayList<>();
        for (int index = 0; index < folders.length; index++) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, "Indexed fixture " + index + ".wav");
            values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav");
            if (Build.VERSION.SDK_INT >= 29) {
                values.put(MediaStore.Audio.Media.RELATIVE_PATH, folders[index]);
                values.put(MediaStore.Audio.Media.IS_PENDING, 1);
            } else {
                File base = getContext().getExternalFilesDir(null);
                if (base == null) throw new IOException("Fixture storage unavailable");
                File directory = new File(base, folders[index]);
                if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create fixture directory");
                values.put(MediaStore.Audio.Media.DATA, new File(directory, "Indexed fixture " + index + ".wav").getAbsolutePath());
            }
            Uri uri = getContext().getContentResolver().insert(
                Build.VERSION.SDK_INT >= 29 ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("MediaStore rejected the fixture");
            uris.add(uri.toString());
            if (!getContext().getSharedPreferences("indexed-fixture", Context.MODE_PRIVATE).edit()
                .putStringSet("uris", new HashSet<>(uris)).commit()) {
                getContext().getContentResolver().delete(uri, null, null);
                throw new IOException("Could not record fixture ownership");
            }
            try (FileInputStream input = new FileInputStream(tone);
                 OutputStream output = getContext().getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) throw new IOException("Could not open the indexed fixture");
                byte[] bytes = new byte[8192];
                int count;
                while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
            }
            values.clear();
            values.put(MediaStore.Audio.Media.TITLE, "Indexed title " + index);
            values.put(MediaStore.Audio.Media.ARTIST, "Synthetic artist");
            values.put(MediaStore.Audio.Media.ALBUM, "Synthetic album");
            values.put(MediaStore.Audio.Media.DURATION, 60_000);
            values.put(MediaStore.Audio.Media.TRACK, 2000 + index + 1);
            values.put(MediaStore.Audio.Media.YEAR, 2026);
            values.put(MediaStore.Audio.Media.SIZE, tone.length());
            values.put(MediaStore.Audio.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000);
            if (Build.VERSION.SDK_INT >= 29) values.put(MediaStore.Audio.Media.IS_PENDING, 0);
            getContext().getContentResolver().update(uri, values, null, null);
        }
        Bundle result = new Bundle();
        result.putStringArrayList("uris", uris);
        return result;
    }

    private void clearIndexedMusic() {
        Set<String> uris = getContext().getSharedPreferences("indexed-fixture", Context.MODE_PRIVATE)
            .getStringSet("uris", java.util.Collections.emptySet());
        for (String uri : uris) getContext().getContentResolver().delete(Uri.parse(uri), null, null);
        if (!getContext().getSharedPreferences("indexed-fixture", Context.MODE_PRIVATE).edit().remove("uris").commit()) {
            throw new IllegalStateException("Could not clear fixture ownership");
        }
    }

    static void setWidgetStatus(Bundle status) { widgetStatus = new Bundle(status); }

    static void setControllerStatus(Bundle status) {
        controllerStatus = new Bundle(status);
    }

    private void checkAvailable() throws FileNotFoundException {
        if (unavailable) throw new FileNotFoundException("Synthetic unplugged volume");
    }

    private void documentRow(MatrixCursor cursor, String id) {
        boolean folder = id.equals("music") || id.equals("music/album") || id.equals("empty");
        String name;
        switch (id) {
            case "music": name = "Music"; break;
            case "music/album": name = "Album"; break;
            case "music/one": name = "01 - Synthetic.wav"; break;
            case "music/album/two": name = "02 - Nested.WAV"; break;
            case "music/ignored": name = "notes.txt"; break;
            case "music/lyric-data": name = "01 - Synthetic.LRC"; break;
            case "music/lyric-alternate": name = "01 - Synthetic (Alternate).lrc"; break;
            case "music/lyric-third": name = "01 - Synthetic - Third.lrc"; break;
            case "music/album/lyric-data": name = "02 - Nested.txt"; break;
            case "pictures/background": name = "Test background.png"; break;
            case "pictures/second": name = "Second background.png"; break;
            case "pictures/invalid": name = "Invalid picture.png"; break;
            default: name = id.startsWith("music/large-")
                ? "Large folder " + id.substring("music/large-".length()) + ".wav" : "Empty";
        }
        String mime = id.startsWith("pictures/") ? "image/png"
            : id.equals("music/lyric-alternate") || id.equals("music/lyric-third") ? "text/plain"
            : id.equals("music/lyric-data") ? "audio/mpeg"
            : id.endsWith("lyric-data") ? "application/octet-stream"
            : folder ? DocumentsContract.Document.MIME_TYPE_DIR
            : id.equals("music/ignored") ? "text/plain"
            : id.equals("music/album/two") ? "application/octet-stream" : "audio/wav";
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : cursor.getColumnNames()) {
            Object value = null;
            if (column.equals(DocumentsContract.Document.COLUMN_DOCUMENT_ID)) value = id;
            if (column.equals(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) value = name;
            if (column.equals(DocumentsContract.Document.COLUMN_MIME_TYPE)) value = mime;
            if (column.equals(DocumentsContract.Document.COLUMN_FLAGS)) value = 0;
            if (column.equals(DocumentsContract.Document.COLUMN_SIZE)) value = folder ? 0L : tone.length();
            if (column.equals(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
                value = unknownFileStamp ? null : tone.lastModified() + fileStampOffset;
            row.add(column, value);
        }
    }
}
