package com.macarmusic.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inspector.WindowInspector;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.HttpsURLConnection;

// No AndroidX/Kotlin test runtime: debug tests otherwise depend on classes renamed by release R8.
public class OptimizedSmokeInstrumentation extends Instrumentation {
    private static final String PACKAGE = "com.macarmusic.app";
    private volatile Activity activity;
    private MediaBrowser browser;
    private MediaController controller;
    private boolean colorPickerOnly;
    private boolean systemControlsOnly;
    private boolean homeWidgetsOnly;
    private boolean lyricCacheOnly;
    private boolean spectrumLyricsOnly;
    private boolean queueOnly;
    private boolean lyricDownloadsOnly;
    private boolean playerUiOnly;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        colorPickerOnly = arguments != null && "true".equals(arguments.getString("colorPickerOnly"));
        systemControlsOnly = arguments != null && "true".equals(arguments.getString("systemControlsOnly"));
        homeWidgetsOnly = arguments != null && "true".equals(arguments.getString("homeWidgetsOnly"));
        lyricCacheOnly = arguments != null && "true".equals(arguments.getString("lyricCacheOnly"));
        spectrumLyricsOnly = arguments != null && "true".equals(arguments.getString("spectrumLyricsOnly"));
        queueOnly = arguments != null && "true".equals(arguments.getString("queueOnly"));
        lyricDownloadsOnly = arguments != null && "true".equals(arguments.getString("lyricDownloadsOnly"));
        playerUiOnly = arguments != null && "true".equals(arguments.getString("playerUiOnly"));
        start();
    }

    @Override public void callActivityOnResume(Activity resumed) {
        super.callActivityOnResume(resumed);
        if ((PACKAGE + ".ui.MainActivity").equals(resumed.getClass().getName())) activity = resumed;
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        ActivityMonitor monitor = null;
        try {
            ApplicationInfo info = getTargetContext().getApplicationInfo();
            if (Build.VERSION.SDK_INT >= 24) check(info.minSdkVersion == 23, "Android 6 must be the minimum");
            check((info.flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0, "Expected optimized release code");
            getTargetContext().getSharedPreferences("settings", 0).edit()
                .putBoolean("parkedNoticeSeen", true).putBoolean("onlineLyrics", false).commit();
            getContext().startActivity(new Intent().setClassName(PACKAGE + ".test",
                PACKAGE + ".FixtureGrantActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Uri tree = DocumentsContract.buildTreeDocumentUri(PACKAGE + ".test.documents", "music");
            await(() -> getTargetContext().checkUriPermission(tree, Process.myPid(), Process.myUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED);
            Uri audio = DocumentsContract.buildDocumentUriUsingTree(tree, "music/one");
            IntentFilter audioPicker = new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);
            audioPicker.addCategory(Intent.CATEGORY_OPENABLE);
            audioPicker.addDataType("*/*");
            monitor = new ActivityMonitor(audioPicker, new ActivityResult(Activity.RESULT_OK,
                new Intent().setData(audio).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)), true);
            addMonitor(monitor);
            if (queueOnly || lyricDownloadsOnly || playerUiOnly) writeQueueFixture(tree, audio);
            activity = launchPlayer(audio);
            if (colorPickerOnly) {
                verifyColorPickers();
                result.putString("result", "PASS");
                result.putString("checks", "Optimized nine color dialogs: resizing, seven rainbow blocks, shared custom slots, RGB/HEX synchronization, invalid-input guard, Cancel/Apply, persisted colors and custom-accent recreation");
                return;
            }
            waitForIdleSync();
            verifyNavigationLabels();
            verifyHeaderBranding();
            CountDownLatch connected = new CountDownLatch(1);
            onMain(() -> {
                browser = new MediaBrowser(getTargetContext(),
                    new ComponentName(PACKAGE, PACKAGE + ".playback.PlaybackService"),
                    new MediaBrowser.ConnectionCallback() {
                        @Override public void onConnected() {
                            controller = new MediaController(getTargetContext(), browser.getSessionToken());
                            connected.countDown();
                        }
                        @Override public void onConnectionFailed() { connected.countDown(); }
                    }, Bundle.EMPTY);
                browser.connect();
            });
            check(connected.await(25, TimeUnit.SECONDS) && controller != null, "Media session connection failed");
            await(() -> controller.getPlaybackState() != null);
            if (playerUiOnly) {
                verifyPlayerUiImprovements();
                result.putString("result", "PASS");
                result.putString("checks", "Exact optimized APK: bundled Chinese initials, real library filtering, owned song/detail/selector panels, clockwise double taps isolated from single taps, Off behavior, Playing label, saved origin and paused recreation");
                return;
            }
            if (lyricDownloadsOnly) {
                verifyLyricDownloads(tree);
                result.putString("result", "PASS");
                result.putString("checks", "Exact optimized APK: queue deduplication, bounded short-lyric rejection, immediate progress, retained rotation, cancellable HTTPS, preserved completed downloads, offline playback, and song/folder/playlist actions without changing playback");
                return;
            }
            if (queueOnly) {
                verifyQueue();
                result.putString("result", "PASS");
                result.putString("checks", "Exact optimized APK: stable 100-song Queue scrolling, current highlighting, inline typography, 48dp targets and 8dp duration spacing; matching Playlist song rows with retained group layout, playback, removal and back/reopen behavior");
                return;
            }
            if (spectrumLyricsOnly) {
                verifySpectrumAndLyricEdits(tree, audio);
                result.putString("result", "PASS");
                result.putString("checks", "Exact optimized APK: uniform 3/8/16/32 bands, all four in-window origins, lyric cells with internal right-aligned sources and timestamps, aligned leading More buttons and styled dialog actions, separate current/Best selection, recreation and next-visit preference, immediate metadata-only online catalog with duration comparisons, selected-only download, timing preview/cancel/save/reset, portable storage and failed-write recovery");
                return;
            }
            if (lyricCacheOnly) {
                verifyLyricCache(audio);
                result.putString("result", "PASS");
                result.putString("checks", "Exact optimized APK: cache size, clear/cancel, verified legacy internal-to-SAF move, shared block styling, persisted folder recreation, unplug/error without playback interruption, and return to internal without deleting portable lyrics");
                return;
            }
            if (homeWidgetsOnly) {
                verifyHomeWidgets(tree);
                result.putString("result", "PASS");
                result.putString("checks", "Exact optimized APK: four real cross-UID home widgets, shared play/pause/previous/next, player-only mute, live and paused progress, local timed/plain lyrics, artwork fallback, minimum width, pin setup, main speaker control, and widget return without autoplay");
                return;
            }
            if (systemControlsOnly) {
                verifySystemControls(tree);
                result.putString("result", "PASS");
                result.putString("checks", "Exact optimized APK: modern/legacy music discovery, local audio Open with, cold/warm single-activity playback, paused recreation, actual system-shade transport and 10-second buttons, native session commands, notification return without autoplay");
                return;
            }
            check(controller.getPlaybackState().getState() != PlaybackState.STATE_PLAYING, "Unexpected autoplay");
            tap(() -> activity.findViewById(id("nav_folders")));
            String open = activity.getString(resource("open_file", "string"));
            tap(() -> textView(activity.getWindow().getDecorView(), open));
            String systemPicker = activity.getString(resource("android_file_picker", "string"));
            tap(() -> {
                for (Object root : windowRoots()) {
                    if (root instanceof View) {
                        View choice = textView((View) root, systemPicker);
                        if (choice != null && choice.isShown()) return choice;
                    }
                }
                return null;
            });
            await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING
                && controller.getMetadata() != null);
            check(controller.getMetadata().getDescription().getTitle().toString().contains("Synthetic"),
                "Expected actual synthetic audio metadata");
            onMain(() -> {
                controller.getTransportControls().pause();
                controller.getTransportControls().seekTo(10_000);
            });
            await(() -> {
                PlaybackState state = controller.getPlaybackState();
                return state.getState() == PlaybackState.STATE_PAUSED
                    && state.getPosition() >= 9_500 && state.getPosition() <= 10_500;
            });
            tap(() -> activity.findViewById(id("action_settings")));
            await(() -> activity.findViewById(id("settingsPanel")) != null
                && activity.findViewById(id("settingsPanel")).isShown());
            waitForIdleSync();
            // Android 6 x86_64 ART can corrupt the result of filled-new-array/range here.
            String[] compactIds = "miniPlayer miniPlay miniNext miniSeek miniTime navigation screenContainer".split(" ");
            Rect[] compactBounds = new Rect[compactIds.length];
            onMain(() -> {
                for (int i = 0; i < compactIds.length; i++) {
                    compactBounds[i] = bounds(activity.findViewById(id(compactIds[i])));
                }
            });
            for (String[] page : new String[][] {{"nav_library", "play_all"}, {"nav_queue", "save_queue"},
                    {"nav_playlists", "create_playlist"}, {"nav_folders", "open_file"}}) {
                tap(() -> activity.findViewById(id(page[0])));
                String label = activity.getString(resource(page[1], "string"));
                await(() -> textView(activity.getWindow().getDecorView(), label) != null);
                waitForIdleSync();
                onMain(() -> {
                    for (int i = 0; i < compactIds.length; i++) {
                        Rect actual = bounds(activity.findViewById(id(compactIds[i])));
                        check(compactBounds[i].equals(actual), page[0] + " must match Settings: "
                            + compactIds[i] + " expected " + compactBounds[i] + ", actual " + actual);
                    }
                });
            }
            tap(() -> activity.findViewById(id("nav_player")));
            check(!activity.findViewById(id("miniPlayer")).isShown(),
                "Now Playing must retain its full controls instead of the mini-player");
            tap(() -> activity.findViewById(id("rewind")));
            await(() -> controller.getPlaybackState().getPosition() == 0);
            tap(() -> activity.findViewById(id("forward")));
            await(() -> controller.getPlaybackState().getPosition() == 10_000);
            check(controller.getPlaybackState().getState() == PlaybackState.STATE_PAUSED,
                "Transport seeking must not start paused playback");
            tap(() -> activity.findViewById(id("action_settings")));
            await(() -> activity.findViewById(id("settingsPanel")) != null);
            check(activity.findViewById(id("navigation")).isShown(), "Settings must leave navigation available");
            verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
            check(activity.getResources().getIdentifier("settingsLightMode", "id", PACKAGE) == 0,
                "The retired Light mode control must not exist");
            verifySystemInformation();
            check(activity.findViewById(id("settingsBackgroundPanel")).getParent() ==
                activity.findViewById(id("settingsGeneralPanel")), "Custom backgrounds belong in General");
            verifySettingsPage("categoryPlayback", "settingsPlaybackPanel");
            verifySettingsPage("categorySpectrum", "settingsSpectrumPanel");
            verifySettingsPage("categoryLyrics", "settingsLyricsPanel");
            verifySettingsPage("categoryFolders", "settingsFoldersPanel");
            verifySettingsPage("categoryAbout", "settingsAboutPanel");
            check(activity.findViewById(id("aboutLogo")) != null, "About logo must be available in Settings");
            android.content.pm.PackageInfo installed = getTargetContext().getPackageManager().getPackageInfo(PACKAGE, 0);
            long versionCode = Build.VERSION.SDK_INT >= 28 ? installed.getLongVersionCode() : installed.versionCode;
            String version = activity.getString(resource("about_version", "string"), installed.versionName, versionCode);
            onMain(() -> {
                ImageView logo = activity.findViewById(id("aboutLogo"));
                check(!logo.isClickable() && !logo.isFocusable() && !logo.hasOnClickListeners(),
                    "The About logo must not be an interactive photo button");
                check(logo.getImageTintList() == null, "The logo must retain its original colors");
                TextView versionLabel = activity.findViewById(id("aboutVersion"));
                check(version.contentEquals(versionLabel.getText()), "About must show the installed version/build");
                check(versionLabel.isShown(), "Version information must be visible");
                check(activity.findViewById(id("settingsHeading")).getVisibility() == View.GONE,
                    "About must not repeat its category heading");
                check(activity.findViewById(id("settingsAboutPanel")).getForeground() != null,
                    "About content must use the shared outline");
                TextView body = activity.findViewById(id("aboutBody"));
                String guide = activity.getString(resource("about_body", "string")) + "\n\n"
                    + activity.getString(resource("about_system_controls", "string")) + "\n\n"
                    + activity.getString(resource("about_lyric_cache", "string"));
                check(guide.contentEquals(body.getText()), "About must contain only the feature introduction");
                for (String phrase : new String[] {"requirement", "requested", "regression", "version code", "api ", "device:",
                        "photo viewer", "full resolution", "\u7167\u7247\u67e5\u770b"}) {
                    check(!guide.toLowerCase(java.util.Locale.ROOT).contains(phrase),
                        "About must not contain request or diagnostic details: " + phrase);
                }
            });
            saveScreenshot("about-release.png");
            tap(() -> activity.findViewById(id("aboutLogo")));
            waitForIdleSync();
            onMain(() -> check(activity.hasWindowFocus()
                && activity.findViewById(id("settingsAboutPanel")).isShown(),
                "Tapping the logo must leave About open without a separate viewer"));
            tap(() -> activity.findViewById(id("categoryGeneral")));
            verifyNavigationDocks();
            onMain(() -> {
                Spinner spinner = activity.findViewById(id("settingsBlockStyle"));
                check(spinner.getCount() == 5, "Only the five supported border styles are selectable");
                check(activity.findViewById(id("settingsLanguage")).getForeground() != null,
                    "Settings buttons must follow block styling");
            });
            tap(() -> activity.findViewById(id("settingsControlBlocks")));
            await(() -> !getTargetContext().getSharedPreferences("settings", 0).getBoolean("controlBlocks", true));
            onMain(() -> {
                for (String name : new String[] {"cover", "favorite", "details", "navigationArtwork",
                        "nav_player", "nav_library", "nav_folders", "nav_queue", "nav_playlists"}) {
                    check(activity.findViewById(id(name)).getForeground() == null, "Disabled blocks remove " + name + " outline");
                }
                check(!activity.findViewById(id("settingsBlockShadow")).isShown(), "Disabled blocks hide shadow controls");
                check(activity.findViewById(id("settingsLanguage")).getForeground() == null,
                    "Settings buttons must also restore standard controls");
            });
            tap(() -> activity.findViewById(id("homeLogo")));
            await(() -> !activity.findViewById(id("settingsPanel")).isShown()
                && activity.findViewById(id("controls")).isShown());
            tap(() -> activity.findViewById(id("nav_queue")));
            await(() -> activity.findViewById(id("queueList")).isShown()
                && !activity.findViewById(id("settingsPanel")).isShown());
            removeMonitor(monitor);
            monitor = null;
            verifyCollections();
            verifyBulkFolderRemoval(tree);
            result.putString("result", "PASS");
            result.putString("checks", "Non-debuggable/min23, paused launch, real playback and seek, all docks and pages, compact geometry, six categories with conditional General backgrounds, header logo home action, installed About version and non-interactive logo, feature-only guide, consistent Settings borders and disabled blocks, artist/album browsing, independent playlist position restored paused, validated local ZIP export and settings restore, both bulk-folder entry points with cancellation and no file deletion");
        } catch (Throwable error) {
            result.putString("result", "FAIL");
            result.putString("error", android.util.Log.getStackTraceString(error));
            try { saveScreenshot("optimized-failure.png"); }
            catch (Exception screenshotError) { result.putString("screenshotError", android.util.Log.getStackTraceString(screenshotError)); }
            if (spectrumLyricsOnly) {
                android.content.SharedPreferences saved = getTargetContext().getSharedPreferences("settings", 0);
                result.putString("spectrumSelection", saved.getInt("spectrumBands", 0) + "/" +
                    saved.getString("spectrumOrigin", "BOTTOM"));
            }
            if (homeWidgetsOnly) result.putString("widgets", String.valueOf(widgetStatus()));
            if (systemControlsOnly && controller != null) {
                result.putString("playback", String.valueOf(controller.getPlaybackState()));
                result.putString("queue", String.valueOf(controller.getQueue()));
                Notification notification = mediaNotification();
                StringBuilder actions = new StringBuilder();
                if (notification != null && notification.actions != null) {
                    for (Notification.Action action : notification.actions) actions.append(action.title).append("; ");
                }
                result.putString("notificationActions", notification == null ? "No notification" : actions.toString());
            }
        } finally {
            if (monitor != null) removeMonitor(monitor);
            if (homeWidgetsOnly) widgetCommand("finish", "");
            onMain(() -> {
                if (controller != null) controller.getTransportControls().pause();
                if (browser != null) browser.disconnect();
                if (activity != null) activity.finish();
            });
            finish("PASS".equals(result.getString("result")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
        }
    }

    private int id(String name) { return resource(name, "id"); }

    private void writeQueueFixture(Uri tree, Uri audio) throws Exception {
        File library = new File(getTargetContext().getFilesDir(), "library.json");
        File playback = new File(getTargetContext().getFilesDir(), "playback.json");
        check(!library.exists() && !playback.exists(), "Queue fixture requires fresh test data");
        org.json.JSONArray tracks = new org.json.JSONArray();
        for (int index = 0; index < (lyricDownloadsOnly ? 2 : 100); index++) {
            String title = String.format(java.util.Locale.ROOT, "Queue %03d", index);
            tracks.put(new org.json.JSONObject().put("id", "queue-fixture-" + index)
                .put("uri", audio.toString()).put("fileName", playerUiOnly && index == 42 ? "\u6d88\u9632.mp3" : title + ".wav").put("title", title)
                .put("artist", "Fixture artist").put("album", "Fixture album").put("durationMs", 60_000)
                .put("mimeType", "audio/wav").put("sourceUris", new org.json.JSONObject().put(tree.toString(), audio.toString())));
        }
        org.json.JSONObject source = new org.json.JSONObject().put("uri", tree.toString())
            .put("name", "Synthetic queue fixture").put("persisted", true);
        org.json.JSONArray trackIds = new org.json.JSONArray();
        for (int index = 0; index < tracks.length(); index++) trackIds.put(tracks.getJSONObject(index).getString("id"));
        org.json.JSONObject playlist = new org.json.JSONObject().put("id", "compact-playlist")
            .put("name", "Compact playlist").put("trackIds", trackIds);
        writeFixture(library, new org.json.JSONObject().put("version", 1).put("tracks", tracks)
            .put("sources", new org.json.JSONArray().put(source))
            .put("playlists", new org.json.JSONArray().put(playlist)).toString());
        org.json.JSONArray queue = lyricDownloadsOnly ? new org.json.JSONArray()
            .put(tracks.getJSONObject(0)).put(tracks.getJSONObject(0)).put(tracks.getJSONObject(1)) : tracks;
        writeFixture(playback, new org.json.JSONObject().put("version", 1).put("queue", queue)
            .put("index", lyricDownloadsOnly ? 0 : 76).put("positionMs", lyricDownloadsOnly ? 12_000 : 20_000)
            .put("shuffle", false).put("repeat", 0).toString());
        check(getTargetContext().getSharedPreferences("settings", 0).edit()
            .putString("playOrder", "SEQUENTIAL").putString("loopMode", "OFF").commit(), "Cannot save fixture playback order");
    }

    private void verifyPlayerUiImprovements() throws Exception {
        check("Playing".equals(activity.getString(resource("now_playing", "string"))), "Playing label was not updated");
        try (ZipFile apk = new ZipFile(getTargetContext().getPackageCodePath())) {
            check(apk.getEntry("pinyin-initials.bin") != null && apk.getEntry("pinyin-license.txt") != null,
                "Optimized APK must retain the initials table and license");
        }
        await(() -> restoredAt(20_000));
        tap(() -> activity.findViewById(id("nav_library")));
        await(() -> visibleEditor(activity.getWindow().getDecorView()) != null);
        onMain(() -> visibleEditor(activity.getWindow().getDecorView()).setText("XF"));
        await(() -> {
            ViewGroup list = activity.findViewById(id("libraryList"));
            return list != null && list.getChildCount() == 1 && textView(list, "Queue 042") != null;
        });
        tap(() -> ((ViewGroup) activity.findViewById(id("libraryList"))).getChildAt(0).findViewById(id("more")));
        await(() -> ownedPanel() != null);
        check(restoredAt(20_000), "Searching and opening a menu changed playback");
        saveScreenshot("owned-library-initial-search.png");
        tap(() -> windowText(activity.getString(resource("details", "string"))));
        await(() -> ownedPanel() != null && ownedPanel().findViewById(android.R.id.message) != null);
        clickDialogAction("close");
        tap(() -> activity.findViewById(id("action_settings")));
        verifySettingsPage("categorySpectrum", "settingsSpectrumPanel");
        tap(() -> activity.findViewById(id("settingsSpectrum")));
        tap(() -> activity.findViewById(id("spectrumStyle")));
        await(() -> ownedPanel() != null && ownedPanel().findViewById(id("playerDialogList")) != null);
        tap(() -> windowText(activity.getString(resource("spectrum_off", "string"))));
        android.content.SharedPreferences preferences = getTargetContext().getSharedPreferences("settings", 0);
        await(() -> "OFF".equals(preferences.getString("spectrumStyle", "")));
        tap(() -> activity.findViewById(id("nav_player")));
        await(() -> activity.findViewById(id("spectrum")).isShown() &&
            !activity.findViewById(id("settingsPanel")).isShown());
        Rect[] original = {null};
        onMain(() -> original[0] = bounds(activity.findViewById(id("spectrum"))));
        for (String origin : new String[] {"LEFT", "TOP", "RIGHT", "BOTTOM"}) {
            doubleTapSpectrum();
            SystemClock.sleep(android.view.ViewConfiguration.getDoubleTapTimeout() + 80);
            check(origin.equals(preferences.getString("spectrumOrigin", "")), "Double-tap origin is not clockwise");
            check("OFF".equals(preferences.getString("spectrumStyle", "")), "Double tap also changed display mode");
            onMain(() -> check(original[0].equals(bounds(activity.findViewById(id("spectrum")))),
                "Double tap moved the spectrum window"));
        }
        tap(() -> activity.findViewById(id("spectrum")));
        await(() -> "LINE".equals(preferences.getString("spectrumStyle", "")));
        check("BOTTOM".equals(preferences.getString("spectrumOrigin", "")), "Single tap changed the origin");
        doubleTapSpectrum();
        SystemClock.sleep(android.view.ViewConfiguration.getDoubleTapTimeout() + 80);
        check("LEFT".equals(preferences.getString("spectrumOrigin", "")), "Origin was not saved");
        Activity previous = activity;
        onMain(previous::recreate);
        await(() -> activity != previous && activity.hasWindowFocus() &&
            activity.findViewById(id("spectrum")).isShown());
        check("LEFT".equals(preferences.getString("spectrumOrigin", "")), "Recreation lost the selected origin");
        await(() -> restoredAt(20_000));
        saveScreenshot("owned-panels-and-double-tap-restored.png");
    }

    private View ownedPanel() {
        for (Object window : windowRoots()) if (window instanceof View) {
            View panel = ((View) window).findViewById(id("playerDialogPanel"));
            if (panel != null && panel.isShown() && panel.hasWindowFocus()) return panel;
        }
        return null;
    }

    private void doubleTapSpectrum() {
        float[] point = new float[2];
        onMain(() -> {
            View spectrum = activity.findViewById(id("spectrum"));
            int[] location = new int[2];
            spectrum.getLocationOnScreen(location);
            point[0] = location[0] + spectrum.getWidth() / 2f;
            point[1] = location[1] + spectrum.getHeight() / 2f;
        });
        for (int tap = 0; tap < 2; tap++) {
            long down = SystemClock.uptimeMillis();
            for (int action : new int[] {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
                MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point[0], point[1], 0);
                event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                try { sendPointerSync(event); } finally { event.recycle(); }
                if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(30);
            }
            if (tap == 0) SystemClock.sleep(60);
        }
        waitForIdleSync();
    }

    private void verifyQueue() throws Exception {
        await(() -> controller.getQueue() != null && controller.getQueue().size() == 100);
        check(controller.getPlaybackState().getState() != PlaybackState.STATE_PLAYING, "Queue restore must stay paused");
        tap(() -> activity.findViewById(id("nav_queue")));
        await(() -> firstQueueRow() != null && queueTitle(firstQueueRow()).startsWith("Queue 076"));
        onMain(() -> {
            View first = firstQueueRow();
            check(first.getTop() == 0, "Opening Queue must locate the current occurrence");
            int touch = Math.round(48 * activity.getResources().getDisplayMetrics().density);
            check(first.getHeight() == touch, "Normal-font Queue rows must be 48dp high");
            View more = first.findViewById(id("more"));
            check(more.getWidth() == touch && more.getHeight() == touch, "More must keep a 48dp touch target");
            check(more.getLeft() - first.findViewById(id("detail")).getRight() ==
                Math.round(8 * activity.getResources().getDisplayMetrics().density), "Queue time needs 8dp before More");
            TextView title = first.findViewById(id("title"));
            TextView subtitle = first.findViewById(id("subtitle"));
            check(title.getLineCount() == 1 && subtitle.getVisibility() == View.GONE, "Queue metadata must stay inline");
            check(title.getText() instanceof android.text.Spanned, "Inline metadata must retain styled text runs");
            check(title.getText().toString().endsWith(subtitle.getText().toString()), "Queue must retain its metadata");
            android.text.TextPaint titlePaint = queueTextPaint(title, 0);
            android.text.TextPaint subtitlePaint = queueTextPaint(title, title.getText().length() - 1);
            check(Math.abs(titlePaint.getTextSize() - title.getTextSize()) < 0.01f &&
                titlePaint.getTypeface().isBold(), "Title typography changed");
            check(Math.abs(subtitlePaint.getTextSize() - subtitle.getTextSize()) < 0.01f &&
                subtitlePaint.getColor() == subtitle.getCurrentTextColor() && !subtitlePaint.getTypeface().isBold(),
                "Metadata font size/color/style changed");
            queueList().scrollBy(0, -50 * first.getHeight() + 11);
        });
        await(() -> queueTitle(firstQueueRow()).startsWith("Queue 026") && firstQueueRow().getTop() == -11);
        String[] anchor = {""};
        int[] inactiveColor = {0};
        onMain(() -> {
            anchor[0] = queueAnchor();
            inactiveColor[0] = ((TextView) queueRow("Queue 028").findViewById(id("title"))).getCurrentTextColor();
        });
        long selectedId = controller.getQueue().get(28).getQueueId();
        long nextId = controller.getQueue().get(29).getQueueId();
        tap(() -> queueRow("Queue 028").findViewById(id("title")));
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING &&
            controller.getPlaybackState().getActiveQueueItemId() == selectedId);
        await(() -> ((TextView) queueRow("Queue 028").findViewById(id("title"))).getCurrentTextColor() != inactiveColor[0]);
        onMain(() -> check(anchor[0].equals(queueAnchor()), "Selecting a song moved the Queue scroll anchor"));
        saveScreenshot("optimized-compact-queue-selection.png");
        tap(() -> activity.findViewById(id("miniNext")));
        await(() -> controller.getPlaybackState().getActiveQueueItemId() == nextId);
        waitForIdleSync();
        onMain(() -> check(anchor[0].equals(queueAnchor()), "Next moved the browsed Queue"));
        tap(() -> activity.findViewById(id("nav_library")));
        tap(() -> activity.findViewById(id("nav_queue")));
        await(() -> queueTitle(firstQueueRow()).startsWith("Queue 029") && firstQueueRow().getTop() == 0);
        onMain(() -> controller.getTransportControls().pause());
        tap(() -> queueRow("Queue 029").findViewById(id("more")));
        String remove = activity.getString(resource("remove_queue", "string"));
        tap(() -> windowText(remove));
        await(() -> controller.getQueue().size() == 99);
        check(savedLibrary().getJSONArray("tracks").length() == 100, "Queue removal changed the library");
        saveScreenshot("optimized-compact-queue-removal.png");
        verifyPlaylistRows();
    }

    private void verifyLyricDownloads(Uri music) throws Exception {
        FixtureOfflineDownloads fixture = new FixtureOfflineDownloads();
        URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? fixture : null);
        getTargetContext().getContentResolver().call(music, "setLyricsMode", "missing", null);
        await(() -> controller.getQueue() != null && controller.getQueue().size() == 3 && restoredAt(12_000));
        if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            tap(() -> activity.findViewById(id("action_settings")));
            verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
            tap(() -> activity.findViewById(id("settingsLanguage")));
            tap(() -> windowText(activity.getString(resource("language_chinese", "string"))));
            await(() -> "zh".equals(activity.getResources().getConfiguration().locale.getLanguage()));
        }
        File directory = new File(getTargetContext().getFilesDir(), "downloaded-lyrics");
        try {
            tap(() -> activity.findViewById(id("nav_queue")));
            tap(() -> activity.findViewById(id("queueDownloadLyrics")));
            await(() -> dialogWith("lyricsDownloadProgress") != null && fixture.searches.get() == 1);
            onMain(() -> {
                View dialog = dialogWith("lyricsDownloadProgress");
                android.widget.ProgressBar bar = dialog.findViewById(id("lyricsDownloadProgress"));
                check(bar.getMax() == 2 && bar.getProgress() == 0, "Duplicate queue entries need one download");
                check(dialog.findViewById(android.R.id.button2).getForeground() != null, "Cancel needs the selected outline");
            });
            saveScreenshot("offline-download-release-pending.png");
            Activity previous = activity;
            onMain(previous::recreate);
            await(() -> activity != previous && dialogWith("lyricsDownloadProgress") != null);
            check(fixture.searches.get() == 1, "Rotation restarted the batch");
            fixture.release.countDown();
            checkDownloadsFinished(2, 2, 0);
            check(fixture.downloads.get() == 4, "Each song should reject one short preview and stop at its next good match");
            check(restoredAt(12_000) && controller.getQueue().size() == 3, "Downloading changed playback or queue");
            check(!getTargetContext().getSharedPreferences("settings", 0).getBoolean("onlineLyrics", true),
                "Explicit downloading changed the automatic online setting");
            File[] retained = directory.listFiles((parent, name) -> name.matches("[0-9a-f]{64}\\.json"));
            check(retained != null && retained.length == 2, "Explicit downloads must use durable storage");
            for (File file : retained) {
                org.json.JSONObject saved = new org.json.JSONObject(fileText(file));
                check(saved.getBoolean("downloaded") && saved.getString("text").contains("[00:56]"),
                    "A truncated or non-durable lyric was saved");
            }
            clickDialogAction("close");
            tap(() -> activity.findViewById(id("nav_player")));
            await(() -> textView(activity.findViewById(id("lyricsList")), "Optimized offline full") != null);
            check(fixture.searches.get() == 2 && fixture.downloads.get() == 4, "Offline use fetched lyrics again");
            saveScreenshot("offline-download-release-playback.png");
            tap(() -> activity.findViewById(id("nav_queue")));
            check(retained[0].delete(), "Cannot prepare synthetic cancellation cache miss");
            fixture.release = new CountDownLatch(1);
            tap(() -> activity.findViewById(id("queueDownloadLyrics")));
            await(() -> fixture.searches.get() == 3 && dialogWith("lyricsDownloadProgress") != null);
            tap(() -> dialogButton(activity.getString(resource("cancel", "string"))));
            await(() -> downloadStatus("lyrics_download_cancelled") && fixture.cancelled.get() > 0);
            check(fixture.downloads.get() == 4 && retained[1].isFile(), "Cancellation fetched another body or removed completed work");
            saveScreenshot("offline-download-release-cancelled.png");
            clickDialogAction("close");
            fixture.release.countDown();
            tap(() -> activity.findViewById(id("queueDownloadLyrics")));
            checkDownloadsFinished(2, 1, 1);
            check(fixture.searches.get() == 4 && fixture.downloads.get() == 6, "Restart did not reuse the completed song");
            clickDialogAction("close");
            tap(() -> firstQueueRow().findViewById(id("more")));
            tap(() -> windowText(activity.getString(resource("lyrics_download", "string"))));
            checkDownloadsFinished(1, 0, 1);
            clickDialogAction("close");
            tap(() -> activity.findViewById(id("nav_playlists")));
            tap(() -> descriptionView(activity.getWindow().getDecorView(),
                activity.getString(resource("options_for", "string"), "Compact playlist")));
            tap(() -> windowText(activity.getString(resource("lyrics_download", "string"))));
            checkDownloadsFinished(2, 0, 2);
            clickDialogAction("close");
            check(fixture.searches.get() == 4, "Already downloaded playlist triggered network searches");
            tap(() -> activity.findViewById(id("nav_folders")));
            tap(() -> descriptionView(activity.getWindow().getDecorView(),
                activity.getString(resource("options_for", "string"), "Synthetic queue fixture")));
            tap(() -> windowText(activity.getString(resource("lyrics_download", "string"))));
            checkDownloadsFinished(2, 2, 0);
            check(fixture.searches.get() == 6 && fixture.downloads.get() == 10, "Recursive folder download scope changed");
            check(restoredAt(12_000) && controller.getQueue().size() == 3, "Folder download changed playback");
            saveScreenshot("offline-download-release-folder-complete.png");
            clickDialogAction("close");
        } finally { fixture.release.countDown(); }
    }

    private boolean downloadStatus(String name) {
        View dialog = dialogWith("lyricsDownloadProgress");
        return dialog != null && activity.getString(resource(name, "string")).contentEquals(
            ((TextView) dialog.findViewById(id("lyricsDownloadStatus"))).getText());
    }

    private void checkDownloadsFinished(int total, int saved, int kept) {
        await(() -> downloadStatus("lyrics_download_complete"));
        onMain(() -> {
            View dialog = dialogWith("lyricsDownloadProgress");
            android.widget.ProgressBar bar = dialog.findViewById(id("lyricsDownloadProgress"));
            check(bar.getMax() == total && bar.getProgress() == total && !bar.isIndeterminate(),
                "Completed progress is inaccurate");
            check(activity.getString(resource("lyrics_download_counts", "string"), saved, kept, 0, 0, total, total)
                .contentEquals(((TextView) dialog.findViewById(id("lyricsDownloadCounts"))).getText()), "Download totals changed");
            View close = dialog.findViewById(android.R.id.button2);
            Rect visible = new Rect();
            check(close.getForeground() != null && close.getGlobalVisibleRect(visible) &&
                visible.width() == close.getWidth() && visible.height() == close.getHeight(), "Close outline is clipped");
        });
    }

    private View descriptionView(View view, String description) {
        if (!view.isShown()) return null;
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = descriptionView(group.getChildAt(index), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class FixtureOfflineDownloads extends URLStreamHandler {
        volatile CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger searches = new AtomicInteger();
        final AtomicInteger downloads = new AtomicInteger();
        final AtomicInteger cancelled = new AtomicInteger();

        @Override protected URLConnection openConnection(URL address) {
            return new HttpsURLConnection(address) {
                private volatile boolean closed;
                private byte[] body;
                @Override public void connect() throws IOException {
                    if (body != null) return;
                    String url = getURL().toString();
                    String text;
                    if (url.contains("lyrics.kugou.com/search")) {
                        searches.incrementAndGet();
                        CountDownLatch gate = release;
                        try {
                            while (!closed && !gate.await(25, TimeUnit.MILLISECONDS)) { }
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Synthetic search interrupted", error);
                        }
                        if (closed) { cancelled.incrementAndGet(); throw new IOException("Synthetic search cancelled"); }
                        String title = org.json.JSONObject.quote(Uri.parse(url).getQueryParameter("keyword"));
                        text = "{\"status\":200,\"candidates\":[{\"id\":\"short\",\"accesskey\":\"fixture\",\"song\":" +
                            title + ",\"duration\":60000},{\"id\":\"full\",\"accesskey\":\"fixture\",\"song\":" +
                            title + ",\"duration\":60000}]}";
                    } else if (url.contains("lyrics.kugou.com/download")) {
                        downloads.incrementAndGet();
                        boolean shortVersion = "short".equals(Uri.parse(url).getQueryParameter("id"));
                        String words = "[00:00]Optimized offline " + (shortVersion ? "preview" : "full") +
                            "\n[00:05]One\n[00:10]Two\n[00:15]Three\n[00:20]Four\n[00:25]Five\n[00:30]Six\n" +
                            (shortVersion ? "[00:40]Preview ends" : "[00:56]Full ending");
                        text = "{\"status\":200,\"content\":\"" + android.util.Base64.encodeToString(
                            words.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP) + "\"}";
                    } else throw new IOException("Unexpected offline fixture endpoint: " + url);
                    body = text.getBytes(StandardCharsets.UTF_8);
                }
                @Override public int getResponseCode() throws IOException { connect(); return 200; }
                @Override public InputStream getInputStream() throws IOException { connect(); return new ByteArrayInputStream(body); }
                @Override public int getContentLength() { return body == null ? -1 : body.length; }
                @Override public void disconnect() { closed = true; }
                @Override public boolean usingProxy() { return false; }
                @Override public String getCipherSuite() { return "Fixture"; }
                @Override public java.security.cert.Certificate[] getLocalCertificates() { return null; }
                @Override public java.security.cert.Certificate[] getServerCertificates() { return new java.security.cert.Certificate[0]; }
            };
        }
    }

    private void verifyPlaylistRows() throws Exception {
        tap(() -> activity.findViewById(id("nav_playlists")));
        await(() -> playlistList() != null && playlistList().getChildCount() > 0);
        onMain(() -> check(playlistList().getChildAt(0).findViewById(id("subtitle")).isShown(),
            "Playlist group rows must retain their separate count"));
        tap(() -> textView(activity.getWindow().getDecorView(), "Compact playlist"));
        await(() -> playlistList().getChildCount() >= 3 && playlistList().getChildAt(0)
            .findViewById(id("subtitle")).getVisibility() == View.GONE);
        onMain(() -> {
            View row = playlistList().getChildAt(0);
            TextView title = row.findViewById(id("title"));
            View more = row.findViewById(id("more"));
            float density = activity.getResources().getDisplayMetrics().density;
            check(row.getHeight() == Math.round(48 * density) && title.getLineCount() == 1 &&
                title.getText() instanceof android.text.Spanned, "Playlist songs must match compact Queue rows");
            TextView subtitle = row.findViewById(id("subtitle"));
            android.text.TextPaint caption = queueTextPaint(title, title.getText().length() - 1);
            check(Math.abs(caption.getTextSize() - subtitle.getTextSize()) < 0.01f &&
                caption.getColor() == subtitle.getCurrentTextColor() && !caption.getTypeface().isBold(),
                "Playlist metadata font size/color/style changed");
            check(more.getWidth() == Math.round(48 * density) && more.getHeight() == Math.round(48 * density) &&
                more.getLeft() - row.findViewById(id("detail")).getRight() == Math.round(8 * density),
                "Playlist duration/More spacing or target size changed");
        });
        tap(() -> songRow(playlistList(), "Queue 002").findViewById(id("title")));
        await(() -> controller.getQueue().size() == 100 &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING &&
            controller.getMetadata() != null &&
            "Queue 002".contentEquals(controller.getMetadata().getDescription().getTitle()));
        tap(() -> activity.findViewById(id("nav_playlists")));
        saveScreenshot("optimized-compact-playlist.png");
        tap(() -> songRow(playlistList(), "Queue 002").findViewById(id("more")));
        tap(() -> windowText(activity.getString(resource("remove_playlist_song", "string"))));
        await(() -> {
            try { return savedLibrary().getJSONArray("playlists").getJSONObject(0).getJSONArray("trackIds").length() == 99; }
            catch (org.json.JSONException error) { throw new AssertionError(error); }
        });
        check(savedLibrary().getJSONArray("tracks").length() == 100 && controller.getQueue().size() == 100,
            "Playlist removal must keep the library and current Queue");
        tap(() -> textView(activity.getWindow().getDecorView(), activity.getString(resource("back_playlists", "string"))));
        await(() -> playlistList().getChildCount() > 0 &&
            "Compact playlist".equals(queueTitle(playlistList().getChildAt(0))));
        onMain(() -> check(playlistList().getChildAt(0).findViewById(id("subtitle")).isShown(),
            "Returning to playlist groups reused the wrong row layout"));
        tap(() -> textView(activity.getWindow().getDecorView(), "Compact playlist"));
        await(() -> playlistList().getChildAt(0).findViewById(id("subtitle")).getVisibility() == View.GONE);
    }

    private ViewGroup playlistList() { return (ViewGroup) activity.findViewById(id("playlistList")); }

    private ViewGroup queueList() { return (ViewGroup) activity.findViewById(id("queueList")); }

    private View firstQueueRow() {
        ViewGroup list = queueList();
        if (list == null) return null;
        View first = null;
        for (int index = 0; index < list.getChildCount(); index++) {
            View row = list.getChildAt(index);
            if (row.getBottom() > 0 && row.getTop() < list.getHeight() &&
                (first == null || row.getTop() < first.getTop())) first = row;
        }
        return first;
    }

    private String queueTitle(View row) { return ((TextView) row.findViewById(id("title"))).getText().toString(); }

    private View queueRow(String title) { return songRow(queueList(), title); }

    private View songRow(ViewGroup list, String title) {
        for (int index = 0; index < list.getChildCount(); index++) {
            View row = list.getChildAt(index);
            if (queueTitle(row).startsWith(title + "  ")) return row;
        }
        throw new AssertionError("Song row is no longer visible: " + title);
    }

    private String queueAnchor() {
        View row = firstQueueRow();
        return queueTitle(row) + ":" + row.getTop();
    }

    private android.text.TextPaint queueTextPaint(TextView view, int offset) {
        android.text.TextPaint paint = new android.text.TextPaint(view.getPaint());
        paint.setColor(view.getCurrentTextColor());
        android.text.Spanned text = (android.text.Spanned) view.getText();
        for (android.text.style.CharacterStyle span : text.getSpans(offset, offset + 1, android.text.style.CharacterStyle.class)) {
            span.updateDrawState(paint);
        }
        return paint;
    }

    private void verifyHomeWidgets(Uri tree) throws Exception {
        onMain(() -> controller.getTransportControls().pause());
        tap(() -> activity.findViewById(id("action_settings")));
        tap(() -> activity.findViewById(id("categoryFolders")));
        ActivityMonitor picker = new ActivityMonitor(new IntentFilter(Intent.ACTION_OPEN_DOCUMENT_TREE),
            new ActivityResult(Activity.RESULT_OK, new Intent().setData(tree)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)), true);
        addMonitor(picker);
        try {
            tap(() -> activity.findViewById(id("settingsAddFolder")));
            await(() -> new File(getTargetContext().getFilesDir(), "library.json").isFile()
                && savedLibrary().optJSONArray("tracks") != null && savedLibrary().optJSONArray("tracks").length() == 2
                && activity.findViewById(id("settingsRemoveAllFolders")).isEnabled());
        } finally { removeMonitor(picker); }
        tap(() -> activity.findViewById(id("categoryGeneral")));
        tap(() -> activity.findViewById(id("settingsWidgets")));
        for (String name : new String[] {"widget_controls_name", "widget_artwork_name", "widget_lyrics_name", "widget_play_name"}) {
            String label = activity.getString(resource(name, "string"));
            await(() -> windowText(label) != null);
        }
        tap(() -> windowText(activity.getString(resource("widget_play_name", "string"))));
        boolean pin = Build.VERSION.SDK_INT >= 26 &&
            android.appwidget.AppWidgetManager.getInstance(getTargetContext()).isRequestPinAppWidgetSupported();
        if (pin) {
            await(() -> {
                AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
                return root != null && !PACKAGE.equals(String.valueOf(root.getPackageName()));
            });
            getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            await(() -> activity.hasWindowFocus());
        } else {
            await(() -> windowText(activity.getString(resource("widgets_help", "string"))) != null);
            clickDialogAction("understood");
        }
        tap(() -> activity.findViewById(id("nav_library")));
        tap(() -> firstLibraryRow());
        await(() -> controller.getQueue() != null && controller.getQueue().size() == 2 &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        onMain(() -> {
            controller.getTransportControls().pause();
            controller.getTransportControls().seekTo(12_000);
        });
        await(() -> pausedAt(12_000));
        tap(() -> activity.findViewById(id("homeLogo")));
        onMain(() -> {
            View mute = activity.findViewById(id("volumeMute"));
            int minimum = Math.round(48 * activity.getResources().getDisplayMetrics().density);
            check(mute.getHeight() >= minimum && mute.getWidth() >= minimum, "Main mute target too small");
        });
        getContext().startActivity(new Intent().setClassName(PACKAGE + ".test", PACKAGE + ".FixtureWidgetHostActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(() -> widgetStatus().getInt("count") == 4 && "Synthetic middle".equals(widgetStatus().getString("lyric2")));
        Bundle widgets = widgetStatus();
        check(widgets.getInt("uid") != Process.myUid(), "Widget host must be a separate UID");
        for (int kind = 0; kind < 4; kind++) {
            check(widgets.getBoolean("playPresent" + kind) && !widgets.getBoolean("providerError" + kind),
                "Actual widget did not inflate: " + kind);
        }
        check(widgets.getBoolean("artwork1") && !widgets.getBoolean("previousPresent3") &&
            !widgets.containsKey("progress3"), "Artwork/one-button widget shape is incorrect");
        widgetCommand("click", "3:widget_play");
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        int progress = widgetStatus().getInt("progress0");
        await(() -> widgetStatus().getInt("progress0") > progress + 10);
        android.media.AudioManager audio = getTargetContext().getSystemService(android.media.AudioManager.class);
        int volume = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
        widgetCommand("click", "0:widget_mute");
        await(() -> getTargetContext().getSharedPreferences("settings", 0).getBoolean("playerMuted", false));
        check(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == volume, "Widget muted other apps' audio");
        await(() -> activity.getString(resource("unmute_player", "string")).equals(widgetStatus().getString("mute2")));
        widgetCommand("click", "2:widget_next");
        await(() -> controller.getMetadata().getDescription().getTitle().toString().contains("Nested"));
        widgetCommand("click", "1:widget_play");
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PAUSED);
        await(() -> widgetStatus().getString("lyric2", "").contains("Nested text fixture"));
        onMain(() -> controller.getTransportControls().seekTo(0));
        widgetCommand("click", "0:widget_previous");
        await(() -> controller.getMetadata().getDescription().getTitle().toString().contains("Synthetic"));
        onMain(() -> controller.getTransportControls().seekTo(12_000));
        await(() -> pausedAt(12_000) && "Synthetic middle".equals(widgetStatus().getString("lyric2")));
        for (int kind = 0; kind < 3; kind++) widgetCommand("resize", kind + ":220");
        widgetCommand("resize", "2:220x110");
        await(() -> widgetStatus().getBoolean("lyricNextVisible2")
            && "Synthetic ending".equals(widgetStatus().getString("lyricNext2")));
        widgets = widgetStatus();
        check(widgets.getFloat("lyricSizeSp2") == 24f && widgets.getFloat("lyricNextSizeSp2") == 20f,
            "The optimized widget must use the default active and other lyric font sizes");
        for (String row : new String[] {"lyric", "lyricNext"}) {
            check(widgets.getInt(row + "Height2") >= widgets.getInt(row + "LayoutHeight2"),
                "Both optimized lyric rows must fit at the compact widget height");
        }
        check(widgetStatus().getInt("playTop2") <= Math.round(16 * activity.getResources().getDisplayMetrics().density),
            "Lyric widget controls must stay at the top");
        int minimum = Math.round(48 * activity.getResources().getDisplayMetrics().density);
        await(() -> widgetStatus().getInt("playWidth0") >= minimum &&
            widgetStatus().getInt("playWidth0") <= minimum + 5);
        SystemClock.sleep(1_500);
        int frozen = widgetStatus().getInt("progress0");
        SystemClock.sleep(1_500);
        check(widgetStatus().getInt("progress0") == frozen, "Paused widget progress must stay fixed");
        saveScreenshot("widgets-release-host.png");
        widgetCommand("click", "0:widget_progress");
        await(() -> activity.hasWindowFocus() && activity.findViewById(id("controls")).isShown()
            && !activity.findViewById(id("settingsPanel")).isShown());
        check(pausedAt(12_000), "Opening the player from a widget started playback or changed position");
        await(() -> activity.getString(resource("unmute_player", "string"))
            .contentEquals(activity.findViewById(id("volumeMute")).getContentDescription()));
        tap(() -> activity.findViewById(id("volumeMute")));
        await(() -> !getTargetContext().getSharedPreferences("settings", 0).getBoolean("playerMuted", true));
    }

    private Bundle widgetStatus() {
        Bundle data = getTargetContext().getContentResolver().call(
            Uri.parse("content://" + PACKAGE + ".test.documents/tree/music"), "widgetStatus", null, null);
        if (data == null) throw new AssertionError("No widget host status");
        data.keySet();
        if (data.containsKey("error")) throw new AssertionError(data.getString("error"));
        return data;
    }

    private void widgetCommand(String command, String value) {
        Bundle extra = new Bundle();
        extra.putString("value", value);
        getTargetContext().getContentResolver().call(Uri.parse("content://" + PACKAGE + ".test.documents/tree/music"),
            "widgetCommand", command, extra);
    }

    private Activity launchPlayer(Uri audio) {
        Intent launch = new Intent().setClassName(PACKAGE, PACKAGE + ".ui.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!systemControlsOnly) return startActivitySync(launch);
        launch.setAction(Intent.ACTION_VIEW).setDataAndType(audio, "audio/wav")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        getTargetContext().startActivity(launch);
        await(() -> activity != null);
        return activity;
    }

    private boolean offers(Intent intent) {
        return !getTargetContext().getPackageManager().queryIntentActivities(
            intent.setPackage(PACKAGE), PackageManager.MATCH_DEFAULT_ONLY).isEmpty();
    }

    private void verifySystemControls(Uri tree) throws Exception {
        check(offers(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)), "Music category missing");
        check(offers(new Intent("android.intent.action.MUSIC_PLAYER")), "Legacy music chooser entry missing");
        check(offers(new Intent(Intent.ACTION_VIEW).setDataAndType(tree, "audio/mpeg")), "Local audio handler missing");
        check(!offers(new Intent(Intent.ACTION_VIEW).setDataAndType(
            Uri.parse("https://example.invalid/song.mp3"), "audio/mpeg")), "Must not advertise remote audio");
        String first = DocumentsContract.buildDocumentUri(PACKAGE + ".test.documents", "music/one").toString();
        String second = DocumentsContract.buildDocumentUri(PACKAGE + ".test.documents", "music/album/two").toString();
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING
            && first.equals(controller.getMetadata().getDescription().getMediaId()));
        onMain(() -> {
            controller.getTransportControls().pause();
            controller.getTransportControls().seekTo(10_000);
        });
        await(() -> pausedAt(10_000));
        Activity original = activity;
        onMain(() -> original.recreate());
        await(() -> activity != original && activity.hasWindowFocus());
        check(pausedAt(10_000), "Activity recreation replayed a file intent");
        Activity reused = activity;
        onMain(() -> activity.startActivity(new Intent(Intent.ACTION_VIEW)
            .setClassName(PACKAGE, PACKAGE + ".ui.MainActivity")
            .setDataAndType(DocumentsContract.buildDocumentUriUsingTree(tree, "music/album/two"), "audio/wav")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)));
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING
            && second.equals(controller.getMetadata().getDescription().getMediaId()));
        check(activity == reused, "Warm file-open created another activity");
        await(() -> activity.hasWindowFocus());
        tap(() -> activity.findViewById(id("nav_library")));
        String playAll = activity.getString(resource("play_all", "string"));
        tap(() -> textView(activity.getWindow().getDecorView(), playAll));
        await(() -> controller.getQueue() != null && controller.getQueue().size() == 2
            && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        await(() -> mediaNotification() != null);
        Notification shown = mediaNotification();
        check(shown.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) != null, "Media-session token missing");
        check((controller.getPlaybackState().getActions() & PlaybackState.ACTION_SKIP_TO_NEXT) != 0,
            "Next command missing for a two-song queue");
        check(controller.getPlaybackState().getCustomActions().size() == 2, "Native seek commands missing");
        check(getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS), "Could not open shade");
        clickSystemButton(activity.getString(resource("pause", "string")));
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PAUSED);
        onMain(() -> controller.getTransportControls().seekTo(15_000));
        await(() -> pausedAt(15_000));
        clickSystemButton(activity.getString(resource("rewind", "string")));
        await(() -> pausedAt(5_000));
        clickSystemButton(activity.getString(resource("forward", "string")));
        await(() -> pausedAt(15_000));
        clickSystemButton(activity.getString(resource("next", "string")));
        await(() -> second.equals(controller.getMetadata().getDescription().getMediaId()));
        clickSystemButton(activity.getString(resource("previous", "string")));
        await(() -> first.equals(controller.getMetadata().getDescription().getMediaId()));
        saveScreenshot("system-release-shade.png");
        clickSystemButton(activity.getString(resource("play", "string")));
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        onMain(() -> {
            controller.getTransportControls().pause();
            controller.getTransportControls().seekTo(15_000);
        });
        await(() -> pausedAt(15_000));
        onMain(() -> controller.getTransportControls().sendCustomAction(PACKAGE + ".BACK_10_SECONDS", Bundle.EMPTY));
        await(() -> pausedAt(5_000));
        getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        await(() -> activity.hasWindowFocus());
        onMain(() -> check(activity.findViewById(id("action_settings")).performClick(), "Settings action failed"));
        await(() -> activity.findViewById(id("settingsPanel")).isShown());
        mediaNotification().contentIntent.send();
        await(() -> activity.findViewById(id("controls")).isShown()
            && !activity.findViewById(id("settingsPanel")).isShown());
        check(pausedAt(5_000) && controller.getQueue().size() == 2, "Notification click replaced or played the queue");
    }

    private boolean pausedAt(long position) {
        PlaybackState state = controller.getPlaybackState();
        return state != null && state.getState() == PlaybackState.STATE_PAUSED && state.getPosition() == position;
    }

    private boolean restoredAt(long position) {
        PlaybackState state = controller.getPlaybackState();
        return state != null && state.getPosition() == position &&
            (state.getState() == PlaybackState.STATE_NONE || state.getState() == PlaybackState.STATE_STOPPED ||
                state.getState() == PlaybackState.STATE_PAUSED);
    }

    private Notification mediaNotification() {
        NotificationManager manager = getTargetContext().getSystemService(NotificationManager.class);
        for (android.service.notification.StatusBarNotification item : manager.getActiveNotifications()) {
            if (Notification.CATEGORY_TRANSPORT.equals(item.getNotification().category)) return item.getNotification();
        }
        return null;
    }

    private AccessibilityNodeInfo systemButton(AccessibilityNodeInfo node, String label) {
        CharSequence description = node.getContentDescription() == null ? node.getText() : node.getContentDescription();
        String text = description == null ? "" : description.toString();
        if (node.isClickable() && (text.equalsIgnoreCase(label)
            || text.toLowerCase(java.util.Locale.ROOT).startsWith(label.toLowerCase(java.util.Locale.ROOT) + " ")
            || label.toLowerCase(java.util.Locale.ROOT).startsWith(text.toLowerCase(java.util.Locale.ROOT) + " "))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo found = child == null ? null : systemButton(child, label);
            if (found != null) return found;
        }
        return null;
    }

    private void clickSystemButton(String label) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 15_000;
        do {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null && "com.android.systemui".equals(String.valueOf(root.getPackageName())) && root.refresh()) {
                AccessibilityNodeInfo button = systemButton(root, label);
                if (button != null && button.refresh() && button.isVisibleToUser() && button.isEnabled()
                    && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
                if (button != null) button.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
            }
            Thread.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        saveScreenshot("system-release-shade.png");
        throw new AssertionError("System media button unavailable: " + label);
    }

    private void saveScreenshot(String name) throws Exception {
        waitForIdleSync();
        SystemClock.sleep(250);
        android.graphics.Bitmap bitmap = getUiAutomation().takeScreenshot();
        check(bitmap != null, "Screenshot unavailable: " + name);
        File directory = new File(getTargetContext().getExternalFilesDir(null), "test-screenshots");
        check(directory.isDirectory() || directory.mkdirs(), "Could not create screenshot directory");
        try (FileOutputStream output = new FileOutputStream(new File(directory, name))) {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output), "Screenshot write failed");
        } finally { bitmap.recycle(); }
    }

    private void verifySystemInformation() throws Exception {
        int state = controller.getPlaybackState().getState();
        tap(() -> activity.findViewById(id("settingsSystemInfo")));
        String expected = activity.getString(resource("system_info_details", "string"),
            reported(Build.VERSION.RELEASE), Build.VERSION.SDK_INT, reported(Build.VERSION.SECURITY_PATCH),
            reported(Build.MANUFACTURER), reported(Build.MODEL), reported(Build.DISPLAY),
            reported(System.getProperty("os.version")));
        await(() -> windowText(expected) != null);
        onMain(() -> check(((TextView) windowText(expected)).isTextSelectable(), "System details should be selectable"));
        check(controller.getPlaybackState().getState() == state, "System information changed playback");
        saveScreenshot("system-info-release.png");
        clickDialogAction("understood");
        onMain(() -> check(activity.findViewById(id("settingsSystemInfo")).getForeground() != null,
            "System information must use the shared button outline"));
    }

    private String reported(String value) {
        return value == null || value.trim().isEmpty()
            ? activity.getString(resource("system_info_unreported", "string")) : value;
    }

    private void verifyChoiceText(String name) {
        await(() -> {
            Spinner spinner = activity.findViewById(id(name));
            View selected = spinner.getSelectedView();
            return selected instanceof TextView && selected.getHeight() > 0 && ((TextView) selected).getLayout() != null;
        });
        onMain(() -> {
            Spinner spinner = activity.findViewById(id(name));
            TextView text = (TextView) spinner.getSelectedView();
            check(text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom() >= text.getLayout().getHeight(),
                "Selected text is clipped: " + name);
            check(text.getTop() >= spinner.getPaddingTop() && text.getBottom() <= spinner.getHeight() - spinner.getPaddingBottom(),
                "Spinner clips its selected row: " + name);
        });
    }

    private void verifySpectrumAndLyricEdits(Uri music, Uri audio) throws Exception {
        getTargetContext().getContentResolver().call(music, "setLyricsMode", "available", null);
        getTargetContext().getContentResolver().call(music, "setUnavailable", "false", null);
        tap(() -> activity.findViewById(id("action_settings")));
        verifySettingsPage("categoryLyrics", "settingsLyricsPanel");
        boolean[] online = {false};
        onMain(() -> online[0] = ((android.widget.CompoundButton)
            activity.findViewById(id("settingsOnlineLyrics"))).isChecked());
        if (online[0]) tap(() -> activity.findViewById(id("settingsOnlineLyrics")));
        await(() -> !((android.widget.CompoundButton) activity.findViewById(id("settingsOnlineLyrics"))).isChecked());
        verifySettingsPage("categoryFolders", "settingsFoldersPanel");
        ActivityMonitor musicPicker = new ActivityMonitor(new IntentFilter(Intent.ACTION_OPEN_DOCUMENT_TREE),
            new ActivityResult(Activity.RESULT_OK, new Intent().setData(music).addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)), true);
        addMonitor(musicPicker);
        try {
            tap(() -> activity.findViewById(id("settingsAddFolder")));
            await(() -> new File(getTargetContext().getFilesDir(), "library.json").isFile() &&
                savedLibrary().optJSONArray("sources") != null &&
                savedLibrary().optJSONArray("sources").length() == 1);
        } finally { removeMonitor(musicPicker); }
        getTargetContext().startActivity(new Intent(Intent.ACTION_VIEW).setClassName(PACKAGE, PACKAGE + ".ui.MainActivity")
            .setDataAndType(audio, "audio/wav").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION));
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        await(() -> activity.findViewById(id("lyricsTiming")).isShown());
        onMain(() -> {
            if (activity.findViewById(id("noticeBar")).isShown()) {
                activity.findViewById(id("dismissNotice")).performClick();
            }
        });
        await(() -> !activity.findViewById(id("noticeBar")).isShown());
        tap(() -> activity.findViewById(id("action_settings")));
        verifySettingsPage("categorySpectrum", "settingsSpectrumPanel");
        tap(() -> activity.findViewById(id("settingsSpectrum")));
        android.content.SharedPreferences preferences = getTargetContext().getSharedPreferences("settings", 0);
        for (int count : new int[] {3, 8, 16, 32}) {
            tap(() -> activity.findViewById(id("spectrumBands")));
            tap(() -> windowText(activity.getString(resource("spectrum_band_count", "string"), count)));
            await(() -> preferences.getInt("spectrumBands", 0) == count);
        }
        onMain(() -> check(((Spinner) activity.findViewById(id("spectrumBands"))).getCount() == 4,
            "All four band counts must remain available"));
        tap(() -> activity.findViewById(id("nav_player")));
        await(() -> activity.findViewById(id("spectrum")).isShown() &&
            !activity.findViewById(id("settingsPanel")).isShown());
        Rect[] originalBounds = {null};
        onMain(() -> originalBounds[0] = bounds(activity.findViewById(id("spectrum"))));
        String[] origins = "BOTTOM LEFT RIGHT TOP".split(" ");
        String[] labels = "navigation_bottom position_left position_right navigation_top".split(" ");
        for (int index = 0; index < origins.length; index++) {
            final String origin = origins[index];
            final String label = labels[index];
            tap(() -> activity.findViewById(id("action_settings")));
            verifySettingsPage("categorySpectrum", "settingsSpectrumPanel");
            tap(() -> activity.findViewById(id("spectrumOrigin")));
            tap(() -> windowText(activity.getString(resource(label, "string"))));
            await(() -> origin.equals(preferences.getString("spectrumOrigin", "")));
            tap(() -> activity.findViewById(id("nav_player")));
            await(() -> activity.findViewById(id("spectrum")).isShown() &&
                !activity.findViewById(id("settingsPanel")).isShown());
            onMain(() -> check(originalBounds[0].equals(bounds(activity.findViewById(id("spectrum")))),
                "Bar origin relocated the spectrum window: " + originalBounds[0] + " -> " +
                    bounds(activity.findViewById(id("spectrum")))));
        }
        await(() -> activity.findViewById(id("lyricsTiming")).isShown() &&
            activity.findViewById(id("nextLyrics")).isShown());
        onMain(() -> {
            View source = activity.findViewById(id("lyricsStatus"));
            View versions = activity.findViewById(id("lyricVersions"));
            View next = activity.findViewById(id("nextLyrics"));
            View timing = activity.findViewById(id("lyricsTiming"));
            check(source.getParent() == versions.getParent() && source.getParent() == next.getParent() &&
                source.getParent() == timing.getParent(),
                "Lyric actions must share the source row");
            check(versions.getRight() <= source.getLeft() && next.getLeft() >= source.getRight() && timing.getLeft() >= next.getRight(),
                "Inline controls must stay beside the source");
            check(next.isShown() && !next.isEnabled(), "Online-off must disable Next without hiding the action");
            Class<?> border = activity.findViewById(id("spectrum")).getBackground().getClass();
            for (View button : new View[] {versions, next, timing}) {
                android.graphics.drawable.Drawable background = button.getBackground();
                check(background instanceof android.graphics.drawable.RippleDrawable &&
                    ((android.graphics.drawable.RippleDrawable) background).getNumberOfLayers() > 0 &&
                    ((android.graphics.drawable.RippleDrawable) background).getDrawable(0).getClass() == border,
                    "Inline actions need the same ripple-contained border as the player controls");
            }
            controller.getTransportControls().pause();
            controller.getTransportControls().seekTo(12_000);
        });
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PAUSED &&
            controller.getPlaybackState().getPosition() == 12_000);
        tap(() -> activity.findViewById(id("lyricsTiming")));
        tap(() -> dialogWith("lyricsTimingValue").findViewById(id("lyricsEarlierFine")));
        tap(() -> dialogWith("lyricsTimingValue").findViewById(id("lyricsEarlierCoarse")));
        await(() -> ((TextView) dialogWith("lyricsTimingValue").findViewById(id("lyricsTimingValue")))
            .getText().toString().contains("1.1"));
        check(lyricRecords().length == 0, "Preview must not write the cache");
        saveScreenshot("lyric-timing-release-preview.png");
        clickDialogAction("cancel");
        check(lyricRecords().length == 0, "Cancel must not save a correction");
        tap(() -> activity.findViewById(id("lyricsTiming")));
        tap(() -> dialogWith("lyricsTimingValue").findViewById(id("lyricsLaterCoarse")));
        tap(() -> dialogWith("lyricsTimingValue").findViewById(id("lyricsLaterFine")));
        clickDialogAction("save");
        await(() -> lyricRecords().length == 1 && activity.findViewById(id("lyricsTiming")).isEnabled());
        File recordFile = lyricRecords()[0];
        org.json.JSONObject record = new org.json.JSONObject(fileText(recordFile));
        check(record.getLong("offsetMs") == -1100 && record.getBoolean("selected"), "Saved offset/selection missing");
        check(record.getString("text").contains("[00:05.00]Synthetic start"), "Source timestamps must stay intact");
        Activity old = activity;
        onMain(() -> activity.recreate());
        await(() -> activity != old && activity.hasWindowFocus() &&
            activity.findViewById(id("lyricsTiming")).isShown() && activity.findViewById(id("lyricsTiming")).isEnabled());
        check("TOP".equals(preferences.getString("spectrumOrigin", "")) &&
            preferences.getInt("spectrumBands", 0) == 32, "Spectrum preferences were not retained");
        tap(() -> activity.findViewById(id("lyricsTiming")));
        await(() -> ((TextView) dialogWith("lyricsTimingValue").findViewById(id("lyricsTimingValue")))
            .getText().toString().contains("1.1"));
        tap(() -> dialogWith("lyricsTimingValue").findViewById(id("lyricsTimingReset")));
        clickDialogAction("save");
        await(() -> activity.findViewById(id("lyricsTiming")).isEnabled());
        check(new org.json.JSONObject(fileText(recordFile)).optLong("offsetMs", 0) == 0, "Reset did not save original timing");
        tap(() -> activity.findViewById(id("lyricsTiming")));
        tap(() -> dialogWith("lyricsTimingValue").findViewById(id("lyricsEarlierCoarse")));
        clickDialogAction("save");
        await(() -> activity.findViewById(id("lyricsTiming")).isEnabled());

        verifyAvailableLyricVersions(music, audio, recordFile);
        verifyOnlineLyricCatalog(recordFile);

        Uri cacheTree = DocumentsContract.buildTreeDocumentUri(PACKAGE + ".test.cache", "root");
        Bundle reset = new Bundle();
        reset.putBoolean("reset", true);
        getTargetContext().getContentResolver().call(cacheTree, "cacheFixture", null, reset);
        ActivityMonitor cachePicker = new ActivityMonitor(new IntentFilter(Intent.ACTION_OPEN_DOCUMENT_TREE),
            new ActivityResult(Activity.RESULT_OK, new Intent().setData(cacheTree).addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)), true);
        addMonitor(cachePicker);
        try {
            tap(() -> activity.findViewById(id("action_settings")));
            verifySettingsPage("categoryLyrics", "settingsLyricsPanel");
            tap(() -> activity.findViewById(id("lyricsCacheMove")));
            clickDialogAction("lyrics_cache_choose");
            await(() -> !recordFile.exists() && activity.findViewById(id("lyricsCacheMove")).isEnabled());
            org.json.JSONObject location = new org.json.JSONObject(
                getTargetContext().getSharedPreferences("lyric_cache", 0).getString("folder", ""));
            String key = recordFile.getName().replace(".json", "");
            Uri portable = cacheChild(cacheChild(Uri.parse(location.getString("documentUri")), key.substring(0, 2)), key + "-1.json");
            String saved = documentText(portable);
            check(new org.json.JSONObject(saved).getLong("offsetMs") == 1000, "Portable move lost timing");
            tap(() -> activity.findViewById(id("nav_player")));
            await(() -> activity.findViewById(id("lyricsTiming")).isEnabled());
            Bundle unavailable = new Bundle();
            unavailable.putBoolean("unavailable", true);
            getTargetContext().getContentResolver().call(cacheTree, "cacheFixture", null, unavailable);
            tap(() -> activity.findViewById(id("lyricsTiming")));
            tap(() -> dialogWith("lyricsTimingValue").findViewById(id("lyricsLaterCoarse")));
            clickDialogAction("save");
            await(() -> activity.getString(resource("lyrics_timing_failed", "string")).contentEquals(
                ((TextView) activity.findViewById(id("noticeText"))).getText()));
            check(!recordFile.exists(), "Failed portable writes must not silently use internal storage");
            getTargetContext().getContentResolver().call(cacheTree, "cacheFixture", null, Bundle.EMPTY);
            check(saved.equals(documentText(portable)), "Failed timing save damaged the prior record");
            check(controller.getPlaybackState().getState() == PlaybackState.STATE_PAUSED &&
                controller.getPlaybackState().getPosition() == 12_000, "Lyric edits changed playback");
            saveScreenshot("spectrum-lyrics-release.png");
        } finally {
            removeMonitor(cachePicker);
            getTargetContext().getContentResolver().call(cacheTree, "cacheFixture", null, Bundle.EMPTY);
        }
    }

    private void verifyAvailableLyricVersions(Uri music, Uri audio, File recordFile) throws Exception {
        org.json.JSONObject record = new org.json.JSONObject(fileText(recordFile));
        String originalName = record.getString("name");
        String originalUri = record.getString("sourceUri");
        String originalText = documentText(Uri.parse(originalUri));
        org.json.JSONArray versions = new org.json.JSONArray();
        versions.put(new org.json.JSONObject().put("text", "[00:00]Synthetic saved QQ\n[00:30]QQ ending")
            .put("origin", "QQ").put("name", "Downloaded QQ").put("savedAtMs", System.currentTimeMillis()));
        versions.put(new org.json.JSONObject().put("text", "[00:00]Synthetic saved Kuwo\n[00:30]Kuwo ending")
            .put("origin", "KUWO").put("name", "Downloaded Kuwo").put("savedAtMs", System.currentTimeMillis()));
        record.put("versions", versions);
        try (FileOutputStream output = new FileOutputStream(recordFile)) {
            output.write(record.toString().getBytes(StandardCharsets.UTF_8));
        }
        getTargetContext().getContentResolver().call(music, "setLyricsMode", "versions", null);
        tap(() -> activity.findViewById(id("lyricVersions")));
        await(() -> dialogWith("lyricsVersionsList") != null &&
            !dialogWith("lyricsVersionsList").findViewById(id("lyricsVersionsStatus")).isShown());
        revealLyricVersion(originalName);
        onMain(() -> {
            verifyLyricMusicDuration(dialogWith("lyricsVersionsList"));
            TextView current = lyricVersionRow(originalName).findViewById(id("lyricsVersionChoose"));
            check(current.isSelected() && android.graphics.Color.alpha(current.getBackgroundTintList().getDefaultColor()) != 0,
                "Current lyrics need a visible selected highlight");
            String heading = current.getText().toString().split("\n", 2)[0];
            check(heading.contains(activity.getString(resource("lyrics_version_current", "string"))) &&
                heading.contains(activity.getString(resource("lyrics_version_saved", "string"))),
                "Current and saved labels must follow the name in the same heading");
        });
        String alternate = "01 - Synthetic (Alternate).lrc";
        revealLyricVersion(alternate);
        onMain(() -> {
            ViewGroup row = lyricVersionRow(alternate);
            TextView source = row.findViewById(id("lyricsVersionSource"));
            View button = row.findViewById(id("lyricsVersionBest"));
            check(activity.getString(resource("lyrics_source_local", "string")).contentEquals(source.getText()),
                "Local source missing");
            verifyLyricCell(row);
            check(row.findViewById(id("lyricsVersionChoose")).getForeground() != null &&
                button.getForeground() != null, "Version buttons must share block styling");
        });
        tap(() -> lyricVersionRow(alternate).findViewById(id("lyricsVersionBest")));
        await(() -> lyricVersionRow(alternate) != null &&
            lyricVersionRow(alternate).findViewById(id("lyricsVersionBest")).isEnabled() &&
            activity.getString(resource("lyrics_best_remove", "string"), alternate).contentEquals(
                lyricVersionRow(alternate).findViewById(id("lyricsVersionBest")).getContentDescription()));
        org.json.JSONObject preferred = new org.json.JSONObject(fileText(recordFile));
        String best = preferred.getString("bestVersionId");
        check(best.length() == 64, "Best must be persisted");
        check(preferred.getString("text").equals(record.getString("text")), "Marking Best must not change current/default text");
        check("Downloaded QQ".equals(preferred.getJSONArray("versions").getJSONObject(0).getString("name")) &&
            "Downloaded Kuwo".equals(preferred.getJSONArray("versions").getJSONObject(1).getString("name")),
            "Marking Best must not move retained versions");
        onMain(() -> check(lyricVersionRow(alternate).findViewById(id("lyricsVersionBest")).isSelected() &&
            !lyricVersionRow(alternate).findViewById(id("lyricsVersionChoose")).isSelected(),
            "The Best heart must stay separate from the current highlight"));
        saveScreenshot("lyric-versions-release-local.png");
        revealLyricVersion("Downloaded Kuwo");
        onMain(() -> check("Kuwo".contentEquals(((TextView) lyricVersionRow("Downloaded Kuwo")
            .findViewById(id("lyricsVersionSource"))).getText()), "Downloaded Kuwo source missing"));
        revealLyricVersion("Downloaded QQ");
        onMain(() -> {
            ViewGroup row = lyricVersionRow("Downloaded QQ");
            TextView source = row.findViewById(id("lyricsVersionSource"));
            check("QQ".contentEquals(source.getText()), "Downloaded QQ source missing");
            verifyLyricCell(row);
            check(activity.getString(resource("lyrics_last_timestamp", "string"), "0:30").contentEquals(
                ((TextView) row.findViewById(id("lyricsVersionTime"))).getText()),
                "Saved copies must show their actual final timestamp");
        });
        saveScreenshot("lyric-versions-release-downloaded.png");
        tap(() -> lyricVersionRow("Downloaded QQ").findViewById(id("lyricsVersionChoose")));
        await(() -> dialogWith("lyricsVersionsList") == null &&
            ((TextView) activity.findViewById(id("lyricsStatus"))).getText().toString().contains("QQ") &&
            textView(activity.findViewById(id("lyricsList")), "Synthetic saved QQ") != null);
        check(best.equals(new org.json.JSONObject(fileText(recordFile)).getString("bestVersionId")),
            "Temporary selection must not replace Best");
        check(pausedAt(12_000), "Choosing lyrics changed playback");
        Activity previous = activity;
        onMain(() -> activity.recreate());
        await(() -> activity != previous && activity.hasWindowFocus() &&
            ((TextView) activity.findViewById(id("lyricsStatus"))).getText().toString().contains("QQ"));
        check(pausedAt(12_000), "Recreation changed playback");
        tap(() -> activity.findViewById(id("lyricVersions")));
        await(() -> dialogWith("lyricsVersionsList") != null &&
            !dialogWith("lyricsVersionsList").findViewById(id("lyricsVersionsStatus")).isShown());
        revealLyricVersion("Downloaded QQ");
        onMain(() -> check(lyricVersionRow("Downloaded QQ").findViewById(id("lyricsVersionChoose")).isSelected(),
            "Reopened picker must highlight the actual downloaded selection"));
        clickDialogAction("close");
        Uri second = DocumentsContract.buildDocumentUriUsingTree(music, "music/album/two");
        getTargetContext().startActivity(new Intent(Intent.ACTION_VIEW).setClassName(PACKAGE, PACKAGE + ".ui.MainActivity")
            .setDataAndType(second, "audio/wav").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION));
        await(() -> controller.getMetadata() != null &&
            controller.getMetadata().getDescription().getTitle().toString().contains("Nested"));
        getTargetContext().startActivity(new Intent(Intent.ACTION_VIEW).setClassName(PACKAGE, PACKAGE + ".ui.MainActivity")
            .setDataAndType(audio, "audio/wav").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION));
        await(() -> textView(activity.findViewById(id("lyricsList")), "Synthetic local alternate") != null);
        onMain(() -> {
            controller.getTransportControls().pause();
            controller.getTransportControls().seekTo(12_000);
        });
        await(() -> pausedAt(12_000));
        check(!getTargetContext().getSharedPreferences("settings", 0).getBoolean("onlineLyrics", true),
            "The picker must work with online lookup disabled");
        saveScreenshot("lyric-versions-release-best-restored.png");

        tap(() -> activity.findViewById(id("lyricVersions")));
        await(() -> dialogWith("lyricsVersionsList") != null &&
            !dialogWith("lyricsVersionsList").findViewById(id("lyricsVersionsStatus")).isShown());
        lyricVersionAction("Downloaded QQ", "lyrics_version_delete");
        confirmLyricRemoval();
        await(() -> !savedVersionNamed(recordFile, "Downloaded QQ"));
        revealLyricVersion(alternate);
        onMain(() -> check(lyricVersionRow(alternate).findViewById(id("lyricsVersionChoose")).isSelected(),
            "Deleting another version must not change the current lyrics"));
        lyricVersionAction(alternate, "lyrics_version_delete");
        confirmLyricRemoval();
        await(() -> !savedVersionNamed(recordFile, alternate));
        check(!new org.json.JSONObject(fileText(recordFile)).has("bestVersionId"), "Deleting Best must clear its preference");
        revealLyricVersion(originalName);
        onMain(() -> check(lyricVersionRow(originalName).findViewById(id("lyricsVersionChoose")).isSelected(),
            "Deleting the current Best must activate the remaining saved version"));
        lyricVersionAction("Downloaded Kuwo", "lyrics_version_delete");
        confirmLyricRemoval();
        await(() -> !savedVersionNamed(recordFile, "Downloaded Kuwo"));
        lyricVersionAction(originalName, "lyrics_version_delete");
        saveScreenshot("lyric-version-release-delete-confirm.png");
        confirmLyricRemoval();
        await(() -> lyricRecords().length == 0);
        check(originalText.equals(documentText(Uri.parse(originalUri))), "Deleting saved copies changed the original lyric file");
        check(pausedAt(12_000), "Removing lyrics changed playback");
        saveScreenshot("lyric-versions-release-removed.png");
        lyricVersionAction(originalName, "lyrics_version_activate");
        await(() -> dialogWith("lyricsVersionsList") == null && recordFile.isFile() &&
            activity.findViewById(id("lyricsTiming")).isEnabled());
        tap(() -> activity.findViewById(id("lyricsTiming")));
        tap(() -> dialogWith("lyricsTimingValue").findViewById(id("lyricsEarlierCoarse")));
        clickDialogAction("save");
        await(() -> activity.findViewById(id("lyricsTiming")).isEnabled());
        check(new org.json.JSONObject(fileText(recordFile)).getLong("offsetMs") == 1000,
            "A newly activated original must still support saved timing");
    }

    private void lyricVersionAction(String name, String action) {
        revealLyricVersion(name);
        tap(() -> lyricVersionRow(name).findViewById(id("lyricsVersionMore")));
        await(() -> dialogWith("lyricsVersionActions") != null);
        tap(() -> windowText(activity.getString(resource(action, "string"))));
    }

    private void verifyLyricMusicDuration(View dialog) {
        long seconds = controller.getMetadata().getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) / 1000;
        check(seconds > 0 && seconds < 3600, "Synthetic song needs a known, short duration");
        String time = String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
        check(activity.getString(resource("lyrics_music_duration", "string"), time).contentEquals(
            ((TextView) dialog.findViewById(id("lyricsTrackDuration"))).getText()),
            "The actual music duration must be visible above lyric results");
        check(dialog.findViewById(android.R.id.button2).getForeground() != null,
            "Dialog Close must share the selected block border");
        check(dialog.findViewById(android.R.id.button2).getScrollX() == 0,
            "Single-line scrolling must not move the Close border out of view");
        View close = dialog.findViewById(android.R.id.button2);
        android.graphics.Rect visible = new android.graphics.Rect();
        check(close.getScrollY() == 0 && close.getLocalVisibleRect(visible) &&
            visible.equals(new android.graphics.Rect(0, 0, close.getWidth(), close.getHeight())),
            "The Close outline must be fully inside the native button panel");
    }

    private void verifyLyricCell(View row) {
        TextView choose = row.findViewById(id("lyricsVersionChoose"));
        TextView source = row.findViewById(id("lyricsVersionSource"));
        TextView time = row.findViewById(id("lyricsVersionTime"));
        Rect cell = bounds(choose);
        Rect more = bounds(row.findViewById(id("lyricsVersionMore")));
        Rect best = bounds(row.findViewById(id("lyricsVersionBest")));
        check(more.right < cell.left && best.left > cell.right, "More must precede the lyric cell");
        check(more.top == cell.top && best.top == cell.top && more.bottom == cell.bottom && best.bottom == cell.bottom,
            "More, lyrics and Best must keep aligned borders");
        check(cell.contains(bounds(source)) && cell.contains(bounds(time)), "Source/time must be inside the lyric cell");
        check(bounds(source).right == cell.right - choose.getPaddingEnd(), "Source must be right-aligned inside the cell");
        check(source.getParent().getParent() == row.findViewById(id("lyricsVersionCell")),
            "Source must not occupy a separate row column");
        check(Math.abs(source.getTextSize() - android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 12, source.getResources().getDisplayMetrics())) < 0.1f,
            "Source font size must be preserved");
    }

    private void verifyOnlineLyricCatalog(File recordFile) throws Exception {
        FixtureOnlineCatalog fixture = new FixtureOnlineCatalog();
        // Synthetic HTTPS responses are confined to this instrumentation process.
        URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? fixture : null);
        String original = fileText(recordFile);
        String originalName = new org.json.JSONObject(original).getString("name");
        String originalBest = new org.json.JSONObject(original).optString("bestVersionId");
        try {
            tap(() -> activity.findViewById(id("action_settings")));
            if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
                tap(() -> activity.findViewById(id("settingsLanguage")));
                tap(() -> windowText(activity.getString(resource("language_chinese", "string"))));
                await(() -> "zh".equals(activity.getResources().getConfiguration().locale.getLanguage()));
            }
            verifySettingsPage("categoryLyrics", "settingsLyricsPanel");
            tap(() -> activity.findViewById(id("settingsOnlineLyrics")));
            tap(() -> activity.findViewById(id("nav_player")));
            await(() -> activity.findViewById(id("nextLyrics")).isEnabled());
            tap(() -> activity.findViewById(id("nextLyrics")));
            await(() -> dialogWith("onlineLyricsList") != null && fixture.searches.get() > 0);
            onMain(() -> {
                View dialog = dialogWith("onlineLyricsList");
                verifyLyricMusicDuration(dialog);
                check(dialog.findViewById(id("onlineLyricsProgress")).isShown(), "Pending search must be visible");
                check(!((android.widget.ProgressBar) dialog.findViewById(id("onlineLyricsProgress"))).isIndeterminate(),
                    "Pending catalogs must not require a continuous loading animation");
                check(((ViewGroup) dialog.findViewById(id("onlineLyricsList"))).getChildCount() == 0,
                    "The dialog must open before metadata arrives");
            });
            check(original.equals(fileText(recordFile)) && fixture.downloads.get() == 0,
                "Opening the catalog must not download lyrics or change the cache");
            fixture.release.countDown();
            String match = "Optimized second match - Fixture singer";
            String matchRow = match + "\n" + activity.getString(resource("lyrics_provider_duration", "string"), "1:02");
            await(() -> dialogWith("onlineLyricsList") != null &&
                textView(dialogWith("onlineLyricsList"), matchRow) != null);
            onMain(() -> check(textView(dialogWith("onlineLyricsList"), "Optimized first match\n" +
                activity.getString(resource("lyrics_provider_duration", "string"),
                    activity.getString(resource("lyrics_time_unknown", "string")))) != null,
                "Missing provider durations must be explicitly Unknown"));
            check(fixture.mobileFinished.get() == 0, "Matches must be selectable while another route is pending");
            check(fixture.downloads.get() == 0 && original.equals(fileText(recordFile)),
                "Catalog results must remain metadata-only");
            saveScreenshot("online-lyrics-release-progressive.png");
            tap(() -> textView(dialogWith("onlineLyricsList"), matchRow));
            await(() -> dialogWith("onlineLyricsList") == null &&
                textView(activity.findViewById(id("lyricsList")), "Optimized selected online words") != null);
            check(fixture.downloads.get() == 1, "Only the selected match should be downloaded");
            org.json.JSONObject saved = new org.json.JSONObject(fileText(recordFile));
            check(originalBest.equals(saved.optString("bestVersionId")), "Online selection changed Best");
            check(savedVersionNamed(recordFile, match), "Selected online version was not retained");
            check(pausedAt(12_000), "Online selection changed playback");
            saveScreenshot("online-lyrics-release-selected.png");
            tap(() -> activity.findViewById(id("lyricVersions")));
            await(() -> dialogWith("lyricsVersionsList") != null &&
                !dialogWith("lyricsVersionsList").findViewById(id("lyricsVersionsStatus")).isShown());
            revealLyricVersion(match);
            onMain(() -> check(lyricVersionRow(match).findViewById(id("lyricsVersionChoose")).isSelected(),
                "Available lyrics must highlight the chosen online version"));
            revealLyricVersion(originalName);
            tap(() -> lyricVersionRow(originalName).findViewById(id("lyricsVersionChoose")));
            await(() -> dialogWith("lyricsVersionsList") == null && activity.findViewById(id("lyricsTiming")).isEnabled());
            tap(() -> activity.findViewById(id("action_settings")));
            verifySettingsPage("categoryLyrics", "settingsLyricsPanel");
            tap(() -> activity.findViewById(id("settingsOnlineLyrics")));
            tap(() -> activity.findViewById(id("nav_player")));
        } finally {
            fixture.release.countDown();
            fixture.slow.countDown();
        }
    }

    private static final class FixtureOnlineCatalog extends URLStreamHandler {
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch slow = new CountDownLatch(1);
        final AtomicInteger searches = new AtomicInteger();
        final AtomicInteger downloads = new AtomicInteger();
        final AtomicInteger mobileFinished = new AtomicInteger();

        @Override protected URLConnection openConnection(URL address) {
            return new HttpsURLConnection(address) {
                private volatile boolean closed;
                private byte[] body;
                @Override public void connect() throws IOException {
                    if (body != null) return;
                    String url = getURL().toString();
                    String text;
                    if (url.contains("fcg_query_lyric_new")) {
                        downloads.incrementAndGet();
                        if (!url.contains("songmid=optimizedSecond")) throw new IOException("Unselected fixture download");
                        text = "{\"code\":0,\"lyric\":\"" + android.util.Base64.encodeToString(
                            "[00:00]Optimized selected online words\n[00:30]Optimized ending".getBytes(StandardCharsets.UTF_8),
                            android.util.Base64.NO_WRAP) + "\"}";
                    } else {
                        searches.incrementAndGet();
                        CountDownLatch gate = url.contains("mobilecdn") ? slow : release;
                        try {
                            while (!closed && !gate.await(50, TimeUnit.MILLISECONDS)) { }
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Fixture search interrupted", error);
                        }
                        if (closed) throw new IOException("Fixture search cancelled");
                        if (url.contains("lyrics.kugou.com/search")) text = "{\"status\":200,\"candidates\":[]}";
                        else if (url.contains("mobilecdn")) {
                            mobileFinished.incrementAndGet();
                            text = "{\"status\":1,\"data\":{\"info\":[]}}";
                        } else if (url.contains("client_search_cp")) text =
                            "{\"code\":0,\"data\":{\"song\":{\"list\":[{\"songname\":\"Optimized first match\",\"songmid\":\"optimizedFirst\"}," +
                            "{\"songname\":\"Optimized second match\",\"singer\":[{\"name\":\"Fixture singer\"}],\"songmid\":\"optimizedSecond\",\"interval\":62}]}}}";
                        else if (url.contains("music.163.com")) text = "{\"code\":200,\"result\":{\"songCount\":0}}";
                        else if (url.contains("search.kuwo.cn")) text = "{\"abslist\":[]}";
                        else throw new IOException("Unexpected fixture endpoint: " + url);
                    }
                    body = text.getBytes(StandardCharsets.UTF_8);
                }
                @Override public int getResponseCode() throws IOException { connect(); return 200; }
                @Override public InputStream getInputStream() throws IOException { connect(); return new ByteArrayInputStream(body); }
                @Override public int getContentLength() { return body == null ? -1 : body.length; }
                @Override public void disconnect() { closed = true; }
                @Override public boolean usingProxy() { return false; }
                @Override public String getCipherSuite() { return "Fixture"; }
                @Override public java.security.cert.Certificate[] getLocalCertificates() { return null; }
                @Override public java.security.cert.Certificate[] getServerCertificates() { return new java.security.cert.Certificate[0]; }
            };
        }
    }

    private void confirmLyricRemoval() {
        String remove = activity.getString(resource("remove", "string"));
        await(() -> dialogButton(remove) != null);
        onMain(() -> check(dialogButton(remove).performClick(), "Removal must require confirmation"));
        waitForIdleSync();
        await(() -> dialogWith("lyricsVersionsList") != null &&
            dialogWith("lyricsVersionsList").hasWindowFocus() &&
            dialogWith("lyricsVersionsList").findViewById(android.R.id.button2).isEnabled());
    }

    private boolean savedVersionNamed(File file, String name) {
        if (new File(file.getPath() + ".bak").exists() || new File(file.getPath() + ".new").exists()) return true;
        if (!file.exists()) return false;
        try {
            org.json.JSONObject document = new org.json.JSONObject(fileText(file));
            if (name.equals(document.optString("name"))) return true;
            org.json.JSONArray versions = document.optJSONArray("versions");
            if (versions != null) for (int index = 0; index < versions.length(); index++) {
                if (name.equals(versions.getJSONObject(index).optString("name"))) return true;
            }
            return false;
        } catch (org.json.JSONException error) { throw new AssertionError("Invalid saved lyric versions", error); }
    }

    private void revealLyricVersion(String name) {
        onMain(() -> dialogWith("lyricsVersionsList").findViewById(id("lyricsVersionsList")).scrollBy(0, -10000));
        waitForIdleSync();
        for (int attempt = 0; attempt < 8; attempt++) {
            boolean[] visible = {false};
            onMain(() -> {
                View row = lyricVersionRow(name);
                Rect rect = new Rect();
                visible[0] = row != null && row.getGlobalVisibleRect(rect) && rect.height() == row.getHeight();
            });
            if (visible[0]) return;
            onMain(() -> {
                View list = dialogWith("lyricsVersionsList").findViewById(id("lyricsVersionsList"));
                list.scrollBy(0, Math.max(48, list.getHeight() / 2));
            });
            waitForIdleSync();
        }
        throw new AssertionError("Missing visible lyric version: " + name);
    }

    private ViewGroup lyricVersionRow(String name) {
        View dialog = dialogWith("lyricsVersionsList");
        if (dialog == null) return null;
        ViewGroup list = dialog.findViewById(id("lyricsVersionsList"));
        for (int index = 0; index < list.getChildCount(); index++) {
            ViewGroup row = (ViewGroup) list.getChildAt(index);
            TextView choose = row.findViewById(id("lyricsVersionChoose"));
            if (choose != null && activity.getString(resource("lyrics_version_use", "string"), name)
                .contentEquals(choose.getContentDescription())) return row;
        }
        return null;
    }

    private File[] lyricRecords() {
        File[] files = new File(getTargetContext().getCacheDir(), "lyrics").listFiles(
            (directory, name) -> name.matches("[0-9a-f]{64}\\.json"));
        return files == null ? new File[0] : files;
    }

    private void verifyLyricCache(Uri audio) throws Exception {
        Uri tree = DocumentsContract.buildTreeDocumentUri(PACKAGE + ".test.cache", "root");
        Bundle reset = new Bundle();
        reset.putBoolean("reset", true);
        getTargetContext().getContentResolver().call(tree, "cacheFixture", null, reset);
        String key = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        File directory = new File(getTargetContext().getCacheDir(), "lyrics");
        check(directory.isDirectory() || directory.mkdirs(), "Cannot create cache fixture");
        File cached = new File(directory, key + ".json");
        File original = new File(directory, "fixture-original.lrc");
        String record = "{\"text\":\"[00:01]Synthetic optimized lyric\",\"origin\":\"QQ\"}";
        writeFixture(cached, record);
        writeFixture(original, "[00:00]Synthetic original lyric");
        getTargetContext().startActivity(new Intent(Intent.ACTION_VIEW).setClassName(PACKAGE, PACKAGE + ".ui.MainActivity")
            .setDataAndType(audio, "audio/wav").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION));
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        tap(() -> activity.findViewById(id("action_settings")));
        verifySettingsPage("categoryLyrics", "settingsLyricsPanel");
        verifyChoiceText("lyricsCacheInternalExpiry");
        verifyChoiceText("lyricsCachePortableExpiry");
        for (String name : "lyricsCacheInspect lyricsCacheClear lyricsCacheMove lyricsCacheInternal".split(" ")) {
            onMain(() -> check(activity.findViewById(id(name)).getForeground() != null, "Missing shared cache button outline"));
        }
        tap(() -> activity.findViewById(id("lyricsCacheInspect")));
        String inspected = activity.getString(resource("lyrics_cache_size", "string"), 1L,
            android.text.format.Formatter.formatFileSize(activity, cached.length()));
        await(() -> !activity.findViewById(id("lyricsCacheProgress")).isShown()
            && inspected.contentEquals(((TextView) activity.findViewById(id("noticeText"))).getText()));
        onMain(() -> check(activity.findViewById(id("noticeBar")).isShown(), "Cache inspection needs a visible result"));
        check(cacheLabel().contains(inspected), "Expected one cached song and its measured size");
        File expired = new File(directory, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.json");
        writeFixture(expired, "{\"text\":\"[00:01]Synthetic expired lyric\",\"origin\":\"QQ\",\"savedAtMs\":" +
            (System.currentTimeMillis() - 2L * 86_400_000) + "}");
        tap(() -> activity.findViewById(id("lyricsCacheInternalExpiry")));
        tap(() -> windowText(activity.getResources().getQuantityString(resource("lyrics_cache_expiry_days", "plurals"), 1, 1)));
        await(() -> !expired.exists() && !activity.findViewById(id("lyricsCacheProgress")).isShown());
        check(cached.isFile() && original.isFile(), "Expiry must retain fresh cache and original lyric files");
        tap(() -> activity.findViewById(id("lyricsCacheInternalExpiry")));
        tap(() -> windowText(activity.getString(resource("lyrics_cache_expiry_never", "string"))));
        await(() -> "NEVER".equals(getTargetContext().getSharedPreferences("settings", 0).getString("internalLyricExpiry", ""))
            && !activity.findViewById(id("lyricsCacheProgress")).isShown());
        tap(() -> activity.findViewById(id("lyricsCachePortableExpiry")));
        tap(() -> windowText(activity.getResources().getQuantityString(resource("lyrics_cache_expiry_days", "plurals"), 7, 7)));
        await(() -> "SEVEN_DAYS".equals(getTargetContext().getSharedPreferences("settings", 0).getString("portableLyricExpiry", "")));
        tap(() -> activity.findViewById(id("lyricsCacheClear")));
        clickDialogAction("cancel");
        check(cached.isFile(), "Cancelling cleared the lyric");
        tap(() -> activity.findViewById(id("lyricsCacheClear")));
        clickDialogAction("lyrics_cache_clear");
        await(() -> !cached.exists());
        check(original.isFile(), "Original lyric was deleted");
        check(controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING, "Clearing interrupted playback");
        writeFixture(cached, record);
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
        ActivityMonitor picker = new ActivityMonitor(new IntentFilter(Intent.ACTION_OPEN_DOCUMENT_TREE),
            new ActivityResult(Activity.RESULT_OK, new Intent().setData(tree).addFlags(flags)), true);
        addMonitor(picker);
        try {
            tap(() -> activity.findViewById(id("lyricsCacheMove")));
            clickDialogAction("lyrics_cache_choose");
            await(() -> getTargetContext().getSharedPreferences("lyric_cache", 0).getString("folder", null) != null
                && activity.findViewById(id("lyricsCacheMove")).isEnabled());
            check(picker.getHits() == 1 && !cached.exists(), "Internal cache was not moved");
            String location = getTargetContext().getSharedPreferences("lyric_cache", 0).getString("folder", null);
            org.json.JSONObject saved = new org.json.JSONObject(location);
            Uri folder = Uri.parse(saved.getString("documentUri"));
            Uri portable = cacheChild(cacheChild(folder, "aa"), key + "-1.json");
            String copied = documentText(portable);
            check(copied.contains("Synthetic optimized lyric"), "Copied lyric is unreadable");
            String portableSize = activity.getString(resource("lyrics_cache_size", "string"), 1L,
                android.text.format.Formatter.formatFileSize(activity, copied.getBytes(StandardCharsets.UTF_8).length));
            boolean persistent = false;
            for (android.content.UriPermission permission : getTargetContext().getContentResolver().getPersistedUriPermissions()) {
                if (tree.equals(permission.getUri()) && permission.isReadPermission() && permission.isWritePermission()) persistent = true;
            }
            check(persistent, "Portable cache needs persistent read and write permissions");
            Activity old = activity;
            onMain(() -> activity.recreate());
            await(() -> activity != old && activity.hasWindowFocus()
                && activity.findViewById(id("settingsLyricsPanel")) != null
                && activity.findViewById(id("settingsLyricsPanel")).isShown());
            check(location.equals(getTargetContext().getSharedPreferences("lyric_cache", 0).getString("folder", null)),
                "Recreation changed the selected cache");
            tap(() -> activity.findViewById(id("lyricsCacheInspect")));
            await(() -> cacheLabel().contains(portableSize)
                && !activity.findViewById(id("lyricsCacheProgress")).isShown());
            saveScreenshot("cache-release-" + (activity.getResources().getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE ? "landscape" : "portrait") + ".png");
            Bundle unavailable = new Bundle();
            unavailable.putBoolean("unavailable", true);
            getTargetContext().getContentResolver().call(tree, "cacheFixture", null, unavailable);
            tap(() -> activity.findViewById(id("lyricsCacheInspect")));
            await(() -> cacheLabel().contains(activity.getString(resource("lyrics_cache_unavailable", "string"))));
            check(controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING, "Unavailable cache stopped audio");
            tap(() -> activity.findViewById(id("lyricsCacheInternal")));
            clickDialogAction("cancel");
            check(getTargetContext().getSharedPreferences("lyric_cache", 0).contains("folder"), "Cancel changed storage");
            tap(() -> activity.findViewById(id("lyricsCacheInternal")));
            clickDialogAction("lyrics_cache_internal");
            await(() -> getTargetContext().getSharedPreferences("lyric_cache", 0).getString("folder", null) == null);
            getTargetContext().getContentResolver().call(tree, "cacheFixture", null, Bundle.EMPTY);
            check(documentText(portable).contains("Synthetic optimized lyric"), "Return to internal erased USB lyrics");
            check(original.isFile() && !cached.exists(), "Original protection or no-copy-back behavior failed");
            check(controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING, "Storage change stopped playback");
            check(original.delete(), "Cannot clean up original lyric fixture");
        } finally {
            removeMonitor(picker);
            getTargetContext().getContentResolver().call(tree, "cacheFixture", null, Bundle.EMPTY);
        }
    }

    private String cacheLabel() {
        return ((TextView) activity.findViewById(id("lyricsCacheLocation"))).getText().toString();
    }

    private void writeFixture(File file, String text) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(text.getBytes(StandardCharsets.UTF_8)); }
    }

    private String documentText(Uri uri) throws Exception {
        try (InputStream input = getTargetContext().getContentResolver().openInputStream(uri)) {
            check(input != null, "Missing fixture document stream");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                check(output.size() + count <= 512 * 1024, "Fixture document too large");
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private Uri cacheChild(Uri parent, String name) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent));
        try (android.database.Cursor cursor = getTargetContext().getContentResolver().query(children, null, null, null, null)) {
            check(cursor != null, "Missing cache directory cursor");
            while (cursor.moveToNext()) {
                if (name.equals(cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)))) {
                    return DocumentsContract.buildDocumentUriUsingTree(parent,
                        cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)));
                }
            }
        }
        throw new AssertionError("Missing portable cache file: " + name);
    }

    private void verifySettingsPage(String category, String panel) {
        tap(() -> activity.findViewById(id(category)));
        await(() -> activity.findViewById(id(panel)).isShown());
        if (getTargetContext().getSharedPreferences("settings", 0).getBoolean("controlBlocks", true)) {
            check(activity.findViewById(id("miniProgress")).getBackground() != null,
                "Compact playback progress must use the shared outline");
        }
    }

    private void verifyCollections() throws Exception {
        tap(() -> activity.findViewById(id("nav_library")));
        for (String mode : new String[] {"library_artists", "library_albums"}) {
            tap(() -> activity.findViewById(id("libraryBrowse")));
            tap(() -> windowText(activity.getString(resource(mode, "string"))));
            await(() -> ((TextView) activity.findViewById(id("libraryBrowse"))).getText().toString()
                .contains(activity.getString(resource(mode, "string"))));
            tap(() -> firstLibraryRow());
            await(() -> activity.findViewById(id("libraryBack")).isShown()
                && firstLibraryRow() != null);
            SystemClock.sleep(250);
            onMain(() -> {
                TextView detail = firstLibraryRow().findViewById(id("detail"));
                check(detail != null && detail.getText().length() > 0, "A group must show real songs");
            });
            tap(() -> activity.findViewById(id("libraryBack")));
        }
        tap(() -> activity.findViewById(id("libraryBrowse")));
        tap(() -> windowText(activity.getString(resource("library_songs", "string"))));
        tap(() -> activity.findViewById(id("nav_queue")));
        tap(() -> textView(activity.getWindow().getDecorView(), activity.getString(resource("save_queue", "string"))));
        String playlist = "Collection smoke " + SystemClock.uptimeMillis();
        await(() -> visibleDialogEditor() != null);
        onMain(() -> {
            EditText editor = visibleDialogEditor();
            editor.setText(playlist);
            activity.getSystemService(android.view.inputmethod.InputMethodManager.class)
                .hideSoftInputFromWindow(editor.getWindowToken(), 0);
        });
        SystemClock.sleep(350);
        waitForIdleSync();
        onMain(() -> {
            View create = windowText(activity.getString(resource("create", "string")));
            check(create != null && create.isEnabled() && create.performClick(), "Create must accept the entered playlist name");
        });
        waitForIdleSync();
        await(() -> fileText(new File(getTargetContext().getFilesDir(), "library.json")).contains(playlist));
        tap(() -> activity.findViewById(id("nav_playlists")));
        if (!activity.findViewById(id("playlistRestorePosition")).isShown()) {
            tap(() -> textView(activity.getWindow().getDecorView(), playlist));
        }
        tap(() -> textView(activity.getWindow().getDecorView(), activity.getString(resource("play_all", "string"))));
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        onMain(() -> {
            controller.getTransportControls().pause();
            controller.getTransportControls().seekTo(22_000);
        });
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PAUSED
            && controller.getPlaybackState().getPosition() == 22_000);
        tap(() -> activity.findViewById(id("nav_library")));
        tap(() -> firstLibraryRow());
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        onMain(() -> controller.getTransportControls().pause());
        tap(() -> activity.findViewById(id("nav_playlists")));
        tap(() -> activity.findViewById(id("playlistRestorePosition")));
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PAUSED
            && controller.getPlaybackState().getPosition() == 22_000);

        File directory = new File(getTargetContext().getCacheDir(), "backup-exports");
        check(directory.isDirectory() || directory.mkdirs(), "Could not create the test export directory");
        File backup = new File(directory, "optimized-roundtrip-" + SystemClock.uptimeMillis() + ".zip");
        Uri uri = Uri.parse("content://" + PACKAGE + ".backups/backups/" + backup.getName());
        IntentFilter create = new IntentFilter(Intent.ACTION_CREATE_DOCUMENT);
        create.addCategory(Intent.CATEGORY_OPENABLE);
        create.addDataType("application/zip");
        IntentFilter open = new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);
        open.addCategory(Intent.CATEGORY_OPENABLE);
        open.addDataType("*/*");
        ActivityMonitor save = new ActivityMonitor(create, new ActivityResult(Activity.RESULT_OK, new Intent().setData(uri)), true);
        ActivityMonitor restore = new ActivityMonitor(open, new ActivityResult(Activity.RESULT_OK, new Intent().setData(uri)), true);
        addMonitor(save);
        addMonitor(restore);
        try {
            tap(() -> activity.findViewById(id("action_settings")));
            tap(() -> activity.findViewById(id("categoryGeneral")));
            tap(() -> activity.findViewById(id("settingsBackup")));
            await(() -> backup.isFile() && backup.length() > 0
                && activity.findViewById(id("settingsBackup")).isEnabled());
            try (ZipFile zip = new ZipFile(backup); InputStream input = zip.getInputStream(zip.getEntry("manifest.json"))) {
                String json = streamText(input);
                check(json.contains("\"format\":\"ManaMusicPlayer\"") && json.contains(playlist),
                    "Backup must contain the declared format and saved playlist");
            }
            tap(() -> activity.findViewById(id("settingsControlBlocks")));
            await(() -> getTargetContext().getSharedPreferences("settings", 0).getBoolean("controlBlocks", false));
            tap(() -> activity.findViewById(id("settingsRestore")));
            Target confirmRestore = () -> {
                for (Object root : windowRoots()) {
                    if (!(root instanceof View) || root == activity.getWindow().getDecorView()) continue;
                    View button = ((View) root).findViewById(android.R.id.button1);
                    if (button instanceof TextView && activity.getString(resource("backup_restore", "string"))
                        .contentEquals(((TextView) button).getText())) return button;
                }
                return null;
            };
            await(() -> confirmRestore.get() != null && confirmRestore.get().isEnabled());
            onMain(() -> check(confirmRestore.get().performClick(), "Restore must accept the confirmation"));
            waitForIdleSync();
            await(() -> !getTargetContext().getSharedPreferences("settings", 0).getBoolean("controlBlocks", true)
                && activity.findViewById(id("settingsBackup")).isEnabled());
            check(controller.getPlaybackState().getState() != PlaybackState.STATE_PLAYING,
                "Backup restore must not start playback");
        } finally {
            removeMonitor(save);
            removeMonitor(restore);
        }
    }

    private View firstLibraryRow() {
        ViewGroup list = activity.findViewById(id("libraryList"));
        return list == null || list.getChildCount() == 0 ? null : list.getChildAt(0);
    }

    private void verifyBulkFolderRemoval(Uri tree) throws Exception {
        ActivityMonitor picker = new ActivityMonitor(new IntentFilter(Intent.ACTION_OPEN_DOCUMENT_TREE),
            new ActivityResult(Activity.RESULT_OK, new Intent().setData(tree)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)), true);
        addMonitor(picker);
        try {
            tap(() -> activity.findViewById(id("categoryFolders")));
            tap(() -> activity.findViewById(id("settingsAddFolder")));
            await(() -> savedLibrary().optJSONArray("sources") != null
                && savedLibrary().optJSONArray("sources").length() == 1
                && activity.findViewById(id("settingsRemoveAllFolders")).isEnabled());
            tap(() -> activity.findViewById(id("settingsRemoveAllFolders")));
            clickDialogAction("cancel");
            check(savedLibrary().getJSONArray("sources").length() == 1, "Cancel must retain all folder references");
            tap(() -> activity.findViewById(id("nav_folders")));
            tap(() -> activity.findViewById(id("folderRemoveAll")));
            clickDialogAction("remove");
            await(() -> savedLibrary().optJSONArray("sources") == null
                || savedLibrary().optJSONArray("sources").length() == 0);
            org.json.JSONObject library = savedLibrary();
            check(library.getJSONArray("tracks").length() == 1,
                "The independently opened file must survive folder removal");
            check(library.getJSONArray("playlists").length() > 0, "Removing folders must retain playlists");
            check(getTargetContext().getContentResolver().call(tree, "scanFixtureState", null, null)
                .getInt("deleteRequests") == 0, "Bulk removal must never request document deletion");
            Uri audio = DocumentsContract.buildDocumentUri(PACKAGE + ".test.documents", "music/one");
            try (InputStream input = getContext().getContentResolver().openInputStream(audio)) {
                check(input != null && input.read() >= 0, "Music files must remain readable after reference removal");
            }
            check(controller.getPlaybackState().getState() == PlaybackState.STATE_PAUSED,
                "Bulk folder removal must not start playback");
            onMain(() -> check(!activity.findViewById(id("folderRemoveAll")).isEnabled(),
                "Empty folders must disable the bulk action"));
            tap(() -> activity.findViewById(id("homeLogo")));
            await(() -> activity.findViewById(id("controls")).isShown());
        } finally { removeMonitor(picker); }
    }

    private org.json.JSONObject savedLibrary() {
        try { return new org.json.JSONObject(fileText(new File(getTargetContext().getFilesDir(), "library.json"))); }
        catch (org.json.JSONException error) { throw new AssertionError("Invalid persisted library", error); }
    }

    private void clickDialogAction(String name) {
        String text = activity.getString(resource(name, "string"));
        await(() -> dialogButton(text) != null);
        onMain(() -> check(dialogButton(text).performClick(), "Dialog must handle " + text));
        waitForIdleSync();
        await(() -> activity.hasWindowFocus());
    }

    private View dialogButton(String text) {
        for (Object root : windowRoots()) {
            if (!(root instanceof View)) continue;
            for (int buttonId : new int[] {android.R.id.button1, android.R.id.button2, android.R.id.button3}) {
                View button = ((View) root).findViewById(buttonId);
                if (button instanceof TextView && text.contentEquals(((TextView) button).getText())
                    && button.isShown() && button.isEnabled() && button.hasWindowFocus()) return button;
            }
        }
        return null;
    }

    private View windowText(String text) {
        for (Object root : windowRoots()) {
            if (root instanceof View && root != activity.getWindow().getDecorView()) {
                View found = textView((View) root, text);
                if (found != null && found.isShown() && found.hasWindowFocus()
                    && found.getGlobalVisibleRect(new Rect())) return found;
            }
        }
        return null;
    }

    private EditText visibleDialogEditor() {
        for (Object root : windowRoots()) {
            if (root instanceof View && root != activity.getWindow().getDecorView()) {
                EditText found = visibleEditor((View) root);
                if (found != null) return found;
            }
        }
        return null;
    }

    private EditText visibleEditor(View view) {
        if (!view.isShown()) return null;
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                EditText editor = visibleEditor(group.getChildAt(index));
                if (editor != null) return editor;
            }
        }
        return null;
    }

    private String fileText(File file) {
        try (InputStream input = new java.io.FileInputStream(file)) { return streamText(input); }
        catch (Exception error) { throw new AssertionError("Cannot read the persisted test library", error); }
    }

    private String streamText(InputStream input) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static Rect bounds(View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return new Rect(location[0], location[1], location[0] + view.getWidth(), location[1] + view.getHeight());
    }

    private void verifyHeaderBranding() {
        onMain(() -> {
            ViewGroup toolbar = activity.findViewById(id("toolbar"));
            String description = activity.getString(resource("logo_now_playing", "string"));
            ImageView logo = null;
            for (int i = 0; i < toolbar.getChildCount(); i++) {
                View child = toolbar.getChildAt(i);
                if (child instanceof ImageView && description.contentEquals(
                        child.getContentDescription() == null ? "" : child.getContentDescription())) {
                    logo = (ImageView) child;
                }
            }
            check(logo != null && logo.isShown(), "Main title must show the supplied logo");
            View title = textView(toolbar, activity.getString(resource("app_name", "string")));
            float density = activity.getResources().getDisplayMetrics().density;
            check(Math.abs(logo.getWidth() - 48 * density) <= 1, "Home logo needs a 48dp touch target");
            check(Math.abs(logo.getWidth() - logo.getPaddingLeft() - logo.getPaddingRight() - 32 * density) <= 1,
                "Header artwork must remain compact");
            check(logo.isClickable() && logo.isFocusable(), "Header logo must expose the home action");
            check(bounds(title).left - bounds(logo).right >= 8 * density - 1,
                "The logo must precede the title with safe spacing");
            check(bounds(title).right <= bounds(activity.findViewById(id("action_settings"))).left,
                "The app title must not overlap Settings");
        });
    }

    private void verifyNavigationLabels() {
        onMain(() -> {
            boolean landscape = activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            String position = getTargetContext().getSharedPreferences("settings", 0).getString("navigationPosition", "BOTTOM");
            boolean side = "LEFT".equals(position) || "RIGHT".equals(position);
            boolean besideIcons = landscape && !side;
            for (String name : new String[] {"nav_player", "nav_library", "nav_folders", "nav_queue", "nav_playlists"}) {
                LinearLayout button = activity.findViewById(id(name));
                View icon = button.getChildAt(0);
                TextView label = (TextView) button.getChildAt(1);
                check(button.getOrientation() == (besideIcons ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL),
                    name + " must arrange its icon and text for dock " + position + " and the screen orientation");
                check(label.isShown(), name + " must show its label");
                check(Math.abs(bounds(label).exactCenterX() - bounds(button).exactCenterX()) <= 1,
                    name + " text must stay centered independently of its icon");
                if (besideIcons) check(icon.getLeft() == button.getPaddingLeft(), name + " icon must stay at the leading edge");
                check(besideIcons ? bounds(label).left >= bounds(icon).right : bounds(label).top >= bounds(icon).bottom,
                    name + " label overlaps its icon");
            }
            for (String name : new String[] {"favorite", "details"}) {
                TextView button = activity.findViewById(id(name));
                if (button.getText().length() > 0) {
                    float center = button.getTotalPaddingLeft() +
                        (button.getLayout().getLineLeft(0) + button.getLayout().getLineRight(0)) / 2;
                    check(Math.abs(center - button.getWidth() / 2f) <= 1, name + " label must be independently centered");
                    check(button.getCompoundDrawablesRelative()[0] != null, name + " needs its leading icon");
                }
            }
            if (side) {
                check(activity.findViewById(id("navigation")).getWidth()
                    == activity.getResources().getDimensionPixelSize(resource("navigation_side_width", "dimen")),
                    "Side docks must retain their compact width");
            }
        });
    }

    private void verifyNavigationDocks() {
        for (String[] dock : new String[][] {{"LEFT", "navigation_left"}, {"RIGHT", "navigation_right"},
                {"TOP", "navigation_top"}, {"BOTTOM", "navigation_bottom"}}) {
            onMain(() -> {
                Spinner spinner = activity.findViewById(id("settingsNavigationPosition"));
                String label = activity.getString(resource(dock[1], "string"));
                for (int i = 0; i < spinner.getCount(); i++) {
                    if (label.equals(spinner.getItemAtPosition(i).toString())) {
                        spinner.setSelection(i);
                        return;
                    }
                }
                throw new AssertionError("Missing navigation position: " + dock[0]);
            });
            await(() -> dock[0].equals(getTargetContext().getSharedPreferences("settings", 0)
                .getString("navigationPosition", null)));
            waitForIdleSync();
            verifyNavigationLabels();
            check(controller.getPlaybackState().getState() == PlaybackState.STATE_PAUSED
                && controller.getPlaybackState().getPosition() == 10_000, "Moving the dock must preserve playback");
        }
    }

    private void verifyColorPickers() {
        tap(() -> activity.findViewById(id("action_settings")));
        tap(() -> activity.findViewById(id("categorySpectrum")));
        if (!((CompoundButton) activity.findViewById(id("settingsSpectrum"))).isChecked()) {
            tap(() -> activity.findViewById(id("settingsSpectrum")));
        }
        for (String palette : new String[] {"spectrum_classic", "spectrum_fire"}) {
            onMain(() -> {
                Spinner spinner = activity.findViewById(id("spectrumPalette"));
                String label = activity.getString(resource(palette, "string"));
                for (int i = 0; i < spinner.getCount(); i++) {
                    if (label.equals(spinner.getItemAtPosition(i).toString())) {
                        spinner.setSelection(i);
                        return;
                    }
                }
                throw new AssertionError("Missing spectrum palette");
            });
            waitForIdleSync();
            for (String anchor : new String[] {"Low", "Middle", "High"}) {
                verifyColorPicker("spectrumColor" + anchor);
                verifyCustomColor("spectrumColor" + anchor,
                    "fireColor" + anchor, false);
            }
        }
        tap(() -> activity.findViewById(id("categoryLyrics")));
        verifyColorPicker("lyricActiveColor");
        verifyCustomColor("lyricActiveColor", "lyricActiveColor", false);
        verifyColorPicker("lyricOtherColor");
        verifyCustomColor("lyricOtherColor", "lyricOtherColor", false);
        tap(() -> activity.findViewById(id("categoryGeneral")));
        verifyColorPicker("settingsAccent");
        verifyCustomColor("settingsAccent", "customAccentColor", true);
    }

    private void verifyColorPicker(String button) {
        for (boolean apply : new boolean[] {false, true}) {
            tap(() -> activity.findViewById(id(button)));
            await(() -> colorDialog() != null);
            onMain(() -> {
                GridLayout grid = colorDialog().findViewById(id("colorGrid"));
                int count = grid.getChildCount();
                check(count >= 13, "Expected seven rainbow colors, five custom slots and an add button");
                View initial = grid.getChildAt(0);
                initial.performClick();
                int cell = (int) (52 * grid.getResources().getDisplayMetrics().density);
                for (int columns : new int[] {8, 3, 6, 1, 5, 8}) {
                    int width = columns * cell;
                    grid.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    grid.layout(0, 0, width, grid.getMeasuredHeight());
                    check(grid.getChildCount() == count && initial.isSelected(), "Resize lost the palette/selection");
                    for (int i = 0; i < count; i++) {
                        View swatch = grid.getChildAt(i);
                        check(swatch.getLeft() >= 0 && swatch.getRight() <= width, "Color block outside dialog width");
                        check(swatch.getTop() >= 0 && swatch.getBottom() <= grid.getHeight(), "Color block outside grid height");
                    }
                }
                View next = grid.getChildAt(1);
                next.performClick();
                check(next.isSelected() && !initial.isSelected(), "Color selection failed");
                if (apply) initial.performClick();
                grid.requestLayout();
            });
            Activity previous = activity;
            boolean recreates = apply && button.equals("settingsAccent") &&
                getTargetContext().getSharedPreferences("settings", 0).getInt("customAccentColor", -1) != 0xff0000;
            onMain(() -> check(colorDialog().findViewById(
                apply ? android.R.id.button1 : android.R.id.button2).performClick(), "Dialog action failed"));
            if (recreates) await(() -> activity != previous && !activity.isDestroyed());
            await(() -> colorDialog() == null);
        }
    }

    private void verifyCustomColor(String button, String key, boolean recreates) {
        android.content.SharedPreferences preferences = getTargetContext().getSharedPreferences("settings", 0);
        int original = preferences.getInt(key, -1);
        tap(() -> activity.findViewById(id(button)));
        await(() -> colorDialog() != null);
        openColorEditor();
        onMain(() -> {
            ((EditText) colorEditor().findViewById(id("customColorInput"))).setText("#BAD");
            colorEditor().findViewById(android.R.id.button1).performClick();
            check(colorEditor() != null, "Invalid HEX must not dismiss the editor");
            check(preferences.getInt(key, -1) == original, "Invalid HEX must not save");
            ((EditText) colorEditor().findViewById(id("customColorInput"))).setText("#13579B");
            check(((ColorDrawable) colorEditor().findViewById(id("colorEditorPreview")).getBackground()).getColor()
                == 0xff13579b, "Custom preview must match entered RGB");
            ((android.widget.SeekBar) colorEditor().findViewById(id("colorRed"))).setProgress(255);
            check("#FF579B".contentEquals(((EditText) colorEditor().findViewById(id("customColorInput"))).getText()),
                "RGB sliders must update the color and HEX preview");
            colorEditor().findViewById(android.R.id.button2).performClick();
            colorDialog().findViewById(android.R.id.button2).performClick();
        });
        await(() -> colorDialog() == null);
        check(preferences.getInt(key, -1) == original, "Cancel must preserve stored color");
        tap(() -> activity.findViewById(id(button)));
        await(() -> colorDialog() != null);
        Activity previous = activity;
        openColorEditor();
        onMain(() -> {
            ((EditText) colorEditor().findViewById(id("customColorInput"))).setText("13579b");
            colorEditor().findViewById(android.R.id.button1).performClick();
            colorDialog().findViewById(android.R.id.button1).performClick();
        });
        if (recreates && original != 0x13579b) {
            // Legacy ActivityMonitor can return an instance that is already being destroyed.
            await(() -> activity != previous && !activity.isDestroyed());
        }
        await(() -> colorDialog() == null && preferences.getInt(key, -1) == 0x13579b);
        tap(() -> activity.findViewById(id(button)));
        await(() -> colorDialog() != null);
        openColorEditor();
        onMain(() -> {
            check("#13579B".contentEquals(((EditText) colorEditor().findViewById(id("customColorInput"))).getText()),
                "Reopened picker must retain custom RGB");
            colorEditor().findViewById(android.R.id.button2).performClick();
            GridLayout grid = colorDialog().findViewById(id("colorGrid"));
            check(grid.getChildAt(7).isSelected(), "Custom color must remain selected in its saved slot");
            colorDialog().findViewById(android.R.id.button2).performClick();
        });
        await(() -> colorDialog() == null);
    }

    private void openColorEditor() {
        onMain(() -> {
            ((GridLayout) colorDialog().findViewById(id("colorGrid"))).getChildAt(7).performClick();
            if (colorEditor() == null) colorDialog().findViewById(id("colorEditSlot")).performClick();
        });
        await(() -> colorEditor() != null);
    }

    private View colorEditor() { return dialogWith("colorEditorPreview"); }

    private View colorDialog() {
        return dialogWith("colorPreview");
    }

    private View dialogWith(String childId) {
        for (Object window : windowRoots()) {
            if (window instanceof View) {
                View root = (View) window;
                if (root.isShown() && root.findViewById(id(childId)) != null) return root;
            }
        }
        return null;
    }

    private Iterable<?> windowRoots() {
        if (Build.VERSION.SDK_INT >= 29) return WindowInspector.getGlobalWindowViews();
        // Instrumentation-only discovery; the public window inspector is unavailable on legacy Android.
        try {
            Class<?> type = Class.forName("android.view.WindowManagerGlobal");
            Object manager = type.getMethod("getInstance").invoke(null);
            java.lang.reflect.Field field = type.getDeclaredField("mViews");
            field.setAccessible(true);
            return (Iterable<?>) field.get(manager);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Could not inspect legacy dialog roots", error);
        }
    }

    private int resource(String name, String type) {
        int value = getTargetContext().getResources().getIdentifier(name, type, PACKAGE);
        check(value != 0, "Missing retained resource: " + name);
        return value;
    }

    private interface Condition { boolean getAsBoolean(); }
    private interface Target { View get(); }

    private void await(Condition condition) {
        long deadline = SystemClock.uptimeMillis() + 25_000;
        while (SystemClock.uptimeMillis() < deadline) {
            boolean[] ready = {false};
            onMain(() -> ready[0] = condition.getAsBoolean());
            if (ready[0]) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Optimized APK did not reach the expected state");
    }

    private void tap(Target target) {
        try {
            await(() -> {
                View view = target.get();
                return view != null && view.isShown() && view.isEnabled() && view.hasWindowFocus()
                    && view.getWidth() > 0 && view.getHeight() > 0;
            });
        } catch (AssertionError error) {
            String[] state = {""};
            onMain(() -> {
                View view = target.get();
                state[0] = "Activity destroyed=" + activity.isDestroyed() + ", target=" + view +
                    (view == null ? "" : ", shown=" + view.isShown() + ", enabled=" + view.isEnabled()
                        + ", attached=" + view.isAttachedToWindow() + ", focus=" + view.hasWindowFocus());
            });
            throw new AssertionError(state[0], error);
        }
        onMain(() -> {
            View view = target.get();
            view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true);
        });
        waitForIdleSync();
        await(() -> {
            View view = target.get();
            return view != null && view.getLocalVisibleRect(new Rect());
        });
        try {
            getUiAutomation().waitForIdle(200, 5_000);
        } catch (java.util.concurrent.TimeoutException error) {
            throw new AssertionError("The UI did not settle before the injected tap", error);
        }
        float[] point = new float[2];
        onMain(() -> {
            Rect visible = new Rect();
            View view = target.get();
            check(view.getLocalVisibleRect(visible), "Control is not visible");
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            point[0] = location[0] + visible.exactCenterX();
            point[1] = location[1] + visible.exactCenterY();
        });
        long down = SystemClock.uptimeMillis();
        for (int action : new int[] {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point[0], point[1], 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try { sendPointerSync(event); } finally { event.recycle(); }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(60);
        }
        waitForIdleSync();
    }

    private View textView(View view, String text) {
        if (view instanceof TextView && view.isShown() && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = textView(group.getChildAt(index), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private void onMain(Runnable action) {
        Throwable[] failure = {null};
        runOnMainSync(() -> {
            try { action.run(); } catch (Throwable error) { failure[0] = error; }
        });
        if (failure[0] != null) throw new AssertionError("UI test action failed", failure[0]);
    }
}
