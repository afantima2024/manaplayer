package com.macarmusic.app;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

// This standalone test-APK process deliberately depends only on the Android/Java runtime.
public class FixtureDocumentsProvider extends DocumentsProvider {
    private static volatile Bundle controllerStatus = Bundle.EMPTY;
    private volatile boolean unavailable;
    private File tone;
    private static final String[] COLUMNS = {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_FLAGS,
        DocumentsContract.Document.COLUMN_SIZE
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
        return true;
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
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : COLUMNS);
        if (parent.equals("music")) {
            documentRow(cursor, "music/album");
            documentRow(cursor, "music/one");
            documentRow(cursor, "music/ignored");
        } else if (parent.equals("music/album")) {
            documentRow(cursor, "music/album/two");
        } else if (!parent.equals("empty")) {
            throw new FileNotFoundException(parent);
        }
        return cursor;
    }

    @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        checkAvailable();
        if (!mode.equals("r") || !(id.equals("music/one") || id.equals("music/album/two"))) {
            throw new FileNotFoundException(id);
        }
        return ParcelFileDescriptor.open(tone, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public boolean isChildDocument(String parent, String id) {
        return id.startsWith(parent + "/");
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (method.equals("controllerStatus")) return new Bundle(controllerStatus);
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
        return super.call(method, arg, extras);
    }

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
            default: name = "Empty";
        }
        String mime = folder ? DocumentsContract.Document.MIME_TYPE_DIR
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
            row.add(column, value);
        }
    }
}
