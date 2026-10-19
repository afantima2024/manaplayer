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
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        colorPickerOnly = arguments != null && "true".equals(arguments.getString("colorPickerOnly"));
        systemControlsOnly = arguments != null && "true".equals(arguments.getString("systemControlsOnly"));
        homeWidgetsOnly = arguments != null && "true".equals(arguments.getString("homeWidgetsOnly"));
        lyricCacheOnly = arguments != null && "true".equals(arguments.getString("lyricCacheOnly"));
        spectrumLyricsOnly = arguments != null && "true".equals(arguments.getString("spectrumLyricsOnly"));
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
            if (spectrumLyricsOnly) {
                verifySpectrumAndLyricEdits(tree, audio);
                result.putString("result", "PASS");
                result.putString("checks", "Exact optimized APK: uniform 3/8/16/32 bands, all four in-window origins, inline local/downloaded version picker with trailing sources, separate current/Best selection, recreation and next-visit preference, timing preview/cancel/save/reset, portable storage and failed-write recovery");
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
            verifySystemInformation();
            verifySettingsPage("categoryBackgrounds", "settingsBackgroundPanel");
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
            result.putString("checks", "Non-debuggable/min23, paused launch, real playback and seek, all docks and pages, compact geometry, seven categories, header logo home action, installed About version and non-interactive logo, feature-only guide, consistent Settings borders and disabled blocks, artist/album browsing, independent playlist position restored paused, validated local ZIP export and settings restore, both bulk-folder entry points with cancellation and no file deletion");
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
        String alternate = "01 - Synthetic (Alternate).lrc";
        revealLyricVersion(alternate);
        onMain(() -> {
            ViewGroup row = lyricVersionRow(alternate);
            TextView source = row.findViewById(id("lyricsVersionSource"));
            View button = row.findViewById(id("lyricsVersionBest"));
            check(activity.getString(resource("lyrics_source_local", "string")).contentEquals(source.getText()),
                "Local source missing");
            check(source.getLeft() >= button.getRight(), "Source must be last in the row");
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
        saveScreenshot("lyric-versions-release-local.png");
        revealLyricVersion("Downloaded Kuwo");
        onMain(() -> check("Kuwo".contentEquals(((TextView) lyricVersionRow("Downloaded Kuwo")
            .findViewById(id("lyricsVersionSource"))).getText()), "Downloaded Kuwo source missing"));
        revealLyricVersion("Downloaded QQ");
        onMain(() -> check("QQ".contentEquals(((TextView) lyricVersionRow("Downloaded QQ")
            .findViewById(id("lyricsVersionSource"))).getText()), "Downloaded QQ source missing"));
        saveScreenshot("lyric-versions-release-downloaded.png");
        tap(() -> lyricVersionRow("Downloaded QQ").findViewById(id("lyricsVersionChoose")));
        await(() -> dialogWith("lyricsVersionsList") == null &&
            ((TextView) activity.findViewById(id("lyricsStatus"))).getText().toString().contains("QQ"));
        check(best.equals(new org.json.JSONObject(fileText(recordFile)).getString("bestVersionId")),
            "Temporary selection must not replace Best");
        check(pausedAt(12_000), "Choosing lyrics changed playback");
        Activity previous = activity;
        onMain(() -> activity.recreate());
        await(() -> activity != previous && activity.hasWindowFocus() &&
            ((TextView) activity.findViewById(id("lyricsStatus"))).getText().toString().contains("QQ"));
        check(pausedAt(12_000), "Recreation changed playback");
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
            if (choose != null && choose.getText().toString().startsWith(name + "\n")) return row;
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
                check(besideIcons ? bounds(label).left >= bounds(icon).right : bounds(label).top >= bounds(icon).bottom,
                    name + " label overlaps its icon");
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
                    (palette.equals("spectrum_classic") ? "classic" : "fire") + "Color" + anchor, false);
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
