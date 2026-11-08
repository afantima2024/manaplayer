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
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
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
import java.io.OutputStream;
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
    private volatile Activity explorer;
    private MediaBrowser browser;
    private MediaController controller;
    private boolean colorPickerOnly;
    private boolean aboutFocusOnly;
    private boolean radioPolishOnly;
    private boolean radioRefreshOnly;
    private boolean radioRefreshResumeOnly;
    private int refreshFeedCount;
    private final java.util.Map<String, String> refreshCaches = new java.util.LinkedHashMap<>();
    private boolean systemControlsOnly;
    private boolean homeWidgetsOnly;
    private boolean radioOnly;
    private boolean radioBaselineOnly;
    private boolean radioHiddenBaselineOnly;
    private boolean radioResumeOnly;
    private boolean radioListOnly;
    private boolean radioListResumeOnly;
    private boolean radioExchangeOnly;
    private boolean radioExchangeResumeOnly;
    private boolean radioReselectionOnly;
    private boolean radioMetadataOnly;
    private boolean radioAppearanceOnly;
    private boolean radioAppearanceResumeOnly;
    private boolean radioThemesOnly;
    private boolean radioThemesResumeOnly;
    private boolean gradientBaselineOnly;
    private boolean gradientUpgradeOnly;
    private boolean gradientDefaultsOnly;
    private boolean uiContinuityBaselineOnly;
    private boolean uiContinuityOnly;
    private boolean uiContinuityResumeOnly;
    private String uiRefreshMethod;
    private boolean uiRefreshBaseline;
    private boolean radioLayoutOnly;
    private boolean radioVisibilityOnly;
    private boolean radioVisibilityResumeOnly;
    private boolean playbackModesOnly;
    private boolean playerExperienceOnly;
    private boolean startupMusicOnly;
    private boolean startupRadioOnly;
    private boolean startupPausedOnly;
    private boolean startupPassiveOnly;
    private boolean musicReselectionOnly;
    private boolean navigationPreferencesOnly;
    private boolean navigationPreferencesResumeOnly;
    private boolean radioSubscriptionsOnly;
    private boolean radioSubscriptionsResumeOnly;
    private boolean radioButtonsOnly;
    private boolean radioDisabledResumeOnly;
    private boolean radioLiveOnly;
    private boolean radioLiveResumeOnly;
    private String radioSamplePath;
    private boolean lyricCacheOnly;
    private boolean spectrumLyricsOnly;
    private boolean queueOnly;
    private boolean lyricDownloadsOnly;
    private boolean playerUiOnly;
    private boolean fileExplorerOnly;
    private boolean explorerLocationsOnly;
    private boolean explorerLocationsResume;
    private boolean karaokeOnly;
    private boolean explorerStorageSimulation;
    private boolean remoteNavigationOnly;
    private boolean bulkRemovalOnly;
    private boolean bulkRemovalRestart;
    private boolean bulkRemovalRecovery;
    private boolean progressiveScanOnly;
    private boolean progressiveScanBaseline;
    private boolean progressiveScanLeavePending;
    private boolean progressiveScanResume;
    private int bulkRemovalCount;
    private String explorerSimulationRoot;
    private String explorerSimulationVolume;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        colorPickerOnly = arguments != null && "true".equals(arguments.getString("colorPickerOnly"));
        aboutFocusOnly = arguments != null && "true".equals(arguments.getString("aboutFocusOnly"));
        radioPolishOnly = arguments != null && "true".equals(arguments.getString("radioPolishOnly"));
        radioRefreshOnly = arguments != null && "true".equals(arguments.getString("radioRefreshOnly"));
        radioRefreshResumeOnly = arguments != null && "true".equals(arguments.getString("radioRefreshResumeOnly"));
        systemControlsOnly = arguments != null && "true".equals(arguments.getString("systemControlsOnly"));
        homeWidgetsOnly = arguments != null && "true".equals(arguments.getString("homeWidgetsOnly"));
        radioOnly = arguments != null && "true".equals(arguments.getString("radioOnly"));
        radioHiddenBaselineOnly = arguments != null && "true".equals(arguments.getString("radioHiddenBaselineOnly"));
        uiContinuityBaselineOnly = arguments != null && "true".equals(arguments.getString("uiContinuityBaselineOnly"));
        uiContinuityOnly = arguments != null && "true".equals(arguments.getString("uiContinuityOnly"));
        uiContinuityResumeOnly = arguments != null && "true".equals(arguments.getString("uiContinuityResumeOnly"));
        uiRefreshMethod = arguments == null ? null : arguments.getString("uiRefreshMethod");
        uiRefreshBaseline = arguments != null && "true".equals(arguments.getString("uiRefreshBaseline"));
        gradientBaselineOnly = arguments != null && "true".equals(arguments.getString("gradientBaselineOnly"));
        gradientUpgradeOnly = arguments != null && "true".equals(arguments.getString("gradientUpgradeOnly"));
        gradientDefaultsOnly = arguments != null && "true".equals(arguments.getString("gradientDefaultsOnly"));
        radioBaselineOnly = radioHiddenBaselineOnly || gradientBaselineOnly || uiContinuityBaselineOnly ||
            arguments != null && "true".equals(arguments.getString("radioBaselineOnly"));
        radioResumeOnly = arguments != null && "true".equals(arguments.getString("radioResumeOnly"));
        radioListOnly = arguments != null && "true".equals(arguments.getString("radioListOnly"));
        radioListResumeOnly = arguments != null && "true".equals(arguments.getString("radioListResumeOnly"));
        radioExchangeOnly = arguments != null && "true".equals(arguments.getString("radioExchangeOnly"));
        radioExchangeResumeOnly = arguments != null && "true".equals(arguments.getString("radioExchangeResumeOnly"));
        radioReselectionOnly = arguments != null && "true".equals(arguments.getString("radioReselectionOnly"));
        radioMetadataOnly = arguments != null && "true".equals(arguments.getString("radioMetadataOnly"));
        radioAppearanceOnly = arguments != null && "true".equals(arguments.getString("radioAppearanceOnly"));
        radioAppearanceResumeOnly = arguments != null && "true".equals(arguments.getString("radioAppearanceResumeOnly"));
        radioThemesOnly = uiContinuityOnly || gradientUpgradeOnly || arguments != null && "true".equals(arguments.getString("radioThemesOnly"));
        radioThemesResumeOnly = uiContinuityResumeOnly || arguments != null && "true".equals(arguments.getString("radioThemesResumeOnly"));
        radioLayoutOnly = arguments != null && "true".equals(arguments.getString("radioLayoutOnly"));
        radioVisibilityOnly = arguments != null && "true".equals(arguments.getString("radioVisibilityOnly"));
        radioVisibilityResumeOnly = arguments != null && "true".equals(arguments.getString("radioVisibilityResumeOnly"));
        playbackModesOnly = arguments != null && "true".equals(arguments.getString("playbackModesOnly"));
        playerExperienceOnly = arguments != null && "true".equals(arguments.getString("playerExperienceOnly"));
        startupMusicOnly = arguments != null && "true".equals(arguments.getString("startupMusicOnly"));
        startupRadioOnly = arguments != null && "true".equals(arguments.getString("startupRadioOnly"));
        startupPausedOnly = arguments != null && "true".equals(arguments.getString("startupPausedOnly"));
        startupPassiveOnly = arguments != null && "true".equals(arguments.getString("startupPassiveOnly"));
        musicReselectionOnly = arguments != null && "true".equals(arguments.getString("musicReselectionOnly"));
        navigationPreferencesOnly = arguments != null && "true".equals(arguments.getString("navigationPreferencesOnly"));
        navigationPreferencesResumeOnly = arguments != null && "true".equals(arguments.getString("navigationPreferencesResumeOnly"));
        radioSubscriptionsOnly = arguments != null && "true".equals(arguments.getString("radioSubscriptionsOnly"));
        radioSubscriptionsResumeOnly = arguments != null && "true".equals(arguments.getString("radioSubscriptionsResumeOnly"));
        radioButtonsOnly = arguments != null && "true".equals(arguments.getString("radioButtonsOnly"));
        radioDisabledResumeOnly = arguments != null && "true".equals(arguments.getString("radioDisabledResumeOnly"));
        radioLiveOnly = arguments != null && "true".equals(arguments.getString("radioLiveOnly"));
        radioLiveResumeOnly = arguments != null && "true".equals(arguments.getString("radioLiveResumeOnly"));
        radioSamplePath = arguments == null ? null : arguments.getString("radioSamplePath");
        lyricCacheOnly = arguments != null && "true".equals(arguments.getString("lyricCacheOnly"));
        spectrumLyricsOnly = arguments != null && "true".equals(arguments.getString("spectrumLyricsOnly"));
        queueOnly = arguments != null && "true".equals(arguments.getString("queueOnly"));
        lyricDownloadsOnly = arguments != null && "true".equals(arguments.getString("lyricDownloadsOnly"));
        playerUiOnly = arguments != null && "true".equals(arguments.getString("playerUiOnly"));
        fileExplorerOnly = arguments != null && "true".equals(arguments.getString("fileExplorerOnly"));
        explorerLocationsOnly = arguments != null && "true".equals(arguments.getString("explorerLocationsOnly"));
        explorerLocationsResume = arguments != null && "true".equals(arguments.getString("explorerLocationsResume"));
        karaokeOnly = arguments != null && "true".equals(arguments.getString("karaokeOnly"));
        explorerStorageSimulation = arguments != null && "true".equals(arguments.getString("explorerStorageSimulation"));
        remoteNavigationOnly = arguments != null && "true".equals(arguments.getString("remoteNavigationOnly"));
        bulkRemovalOnly = arguments != null && "true".equals(arguments.getString("bulkRemovalOnly"));
        bulkRemovalRestart = arguments != null && "true".equals(arguments.getString("bulkRemovalRestart"));
        bulkRemovalRecovery = arguments != null && "true".equals(arguments.getString("bulkRemovalRecovery"));
        progressiveScanOnly = arguments != null && "true".equals(arguments.getString("progressiveScanOnly"));
        progressiveScanBaseline = arguments != null && "true".equals(arguments.getString("progressiveScanBaseline"));
        progressiveScanLeavePending = arguments != null && "true".equals(arguments.getString("progressiveScanLeavePending"));
        progressiveScanResume = arguments != null && "true".equals(arguments.getString("progressiveScanResume"));
        bulkRemovalCount = arguments == null ? 1000 : Integer.parseInt(arguments.getString("bulkRemovalCount", "1000"));
        explorerSimulationRoot = arguments == null ? null : arguments.getString("explorerSimulationRoot");
        explorerSimulationVolume = arguments == null ? null : arguments.getString("explorerSimulationVolume");
        start();
    }

    @Override public void callActivityOnResume(Activity resumed) {
        super.callActivityOnResume(resumed);
        if ((PACKAGE + ".ui.MainActivity").equals(resumed.getClass().getName())) activity = resumed;
        if ((PACKAGE + ".ui.FileExplorerActivity").equals(resumed.getClass().getName())) explorer = resumed;
        if (uiContinuityOnly && (PACKAGE + ".ui.MainActivity").equals(resumed.getClass().getName()))
            android.util.Log.d("UiContinuity", "resumed=" + System.identityHashCode(resumed) +
                " config=" + resumed.getResources().getConfiguration());
    }

    @Override public void callActivityOnDestroy(Activity destroyed) {
        if (uiContinuityOnly && (PACKAGE + ".ui.MainActivity").equals(destroyed.getClass().getName()))
            android.util.Log.d("UiContinuity", "destroyed=" + System.identityHashCode(destroyed) +
                " changingConfigurations=" + destroyed.isChangingConfigurations());
        super.callActivityOnDestroy(destroyed);
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
            if (queueOnly || lyricDownloadsOnly || playerUiOnly || remoteNavigationOnly || radioBaselineOnly || gradientDefaultsOnly || playerExperienceOnly)
                writeQueueFixture(tree, audio);
            if (radioBaselineOnly) getTargetContext().getSharedPreferences("settings", 0).edit()
                .putBoolean("radioFavoritesOnly", true)
                .putStringSet("radioFavorites", java.util.Collections.singleton("radio:cnr:zgzs")).commit();
            FixtureRadioNetwork radioNetwork = radioRefreshOnly || radioRefreshResumeOnly || radioPolishOnly || aboutFocusOnly || radioBaselineOnly || radioOnly || radioResumeOnly || radioListOnly || radioListResumeOnly ||
                radioButtonsOnly || radioDisabledResumeOnly || radioLiveOnly || radioLiveResumeOnly ||
                radioExchangeOnly || radioExchangeResumeOnly || radioReselectionOnly || radioMetadataOnly ||
                radioAppearanceOnly || radioAppearanceResumeOnly || radioThemesOnly || radioThemesResumeOnly || gradientDefaultsOnly || radioLayoutOnly ||
                radioVisibilityOnly || radioVisibilityResumeOnly || playbackModesOnly || playerExperienceOnly || startupMusicOnly ||
                startupRadioOnly || startupPausedOnly || startupPassiveOnly ||
                navigationPreferencesOnly || navigationPreferencesResumeOnly || radioSubscriptionsOnly || radioSubscriptionsResumeOnly
                ? FixtureRadioNetwork.install() : null;
            if (aboutFocusOnly || radioBaselineOnly || radioThemesOnly || radioLayoutOnly || radioButtonsOnly || radioVisibilityOnly ||
                radioVisibilityResumeOnly || playbackModesOnly || playerExperienceOnly || startupMusicOnly || startupRadioOnly || startupPausedOnly || startupPassiveOnly) {
                StringBuilder finite = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n");
                for (int index = 0; index < 30; index++) finite.append("#EXTINF:4.0,\ntone").append(index).append(".aac\n");
                radioNetwork.customPlaylist = finite.append("#EXT-X-ENDLIST\n").toString();
            }
            if (bulkRemovalOnly && !bulkRemovalRestart) writeBulkRemovalFixture(tree, audio);
            if (radioRefreshOnly) prepareRadioRefresh(radioNetwork);
            {
                Bundle options = new Bundle();
                options.putInt("extraRows", progressiveScanOnly ? 998 : 0);
                options.putLong("audioDelayMs", progressiveScanOnly ? 15 : 0);
                options.putBoolean("resetReads", true);
                getTargetContext().getContentResolver().call(tree, "scanFixtureState", null, options);
            }
            long launchStarted = SystemClock.uptimeMillis();
            activity = launchPlayer(audio);
            if (colorPickerOnly) {
                verifyColorPickers();
                result.putString("result", "PASS");
                result.putString("checks", "Optimized nine color dialogs: visible remote focus on swatches, rebuilt slots and RGB/HEX inputs; resizing, seven rainbow blocks, shared custom slots, invalid-input guard, Cancel/Apply, persisted colors and custom-accent recreation");
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
            if (playerExperienceOnly) {
                verifyPlayerExperience(tree, radioNetwork, result);
                result.putString("result", "PASS");
                result.putString("checks", "Exact release: first-use destination draw before Settings construction; warm page reuse; shared configurable transport/mode highlight with cancel, RGB, recreation and default reset; separated mode/pin actions; persistent error after popup/recreation; manual same-song/same-station recovery; preserved music queue and bounded source behavior");
                return;
            }
            if (radioRefreshOnly || radioRefreshResumeOnly) {
                verifyRadioRefresh(radioNetwork);
                result.putString("result", "PASS");
                result.putString("checks", radioRefreshResumeOnly
                    ? "Exact release fresh process: weekly choice and automatic-attempt throttle retained, cached lists and paused music preserved, no startup or repeated foreground downloads"
                    : "Exact release: default Manual, four persisted choices with current-choice remote focus, delayed sequential automatic checks, failed-download cache retention, selectable System information with reachable Close");
                return;
            }
            if (radioPolishOnly) {
                verifyRadioPolish(radioNetwork);
                result.putString("result", "PASS");
                result.putString("checks", "Exact release: shared saved outlines on radio, sticky and subscription rows; aligned search; separate CCTV Television category from unchanged legacy cache; no browsing requests or autoplay; retained music and preferences");
                return;
            }
            if (aboutFocusOnly) {
                verifySettingsCleanup();
                verifyAboutFocus(radioNetwork);
                result.putString("result", "PASS");
                result.putString("checks", "Exact release: all 102 Settings controls retain task order without preference changes or autoplay; retained music baseline, current Aurora choice, independent Settings focus, bilingual Help with 72 styled headings, pinned focus across pages and recreation without reconnecting radio");
                return;
            }
            if (uiRefreshMethod != null) {
                profileUiRefresh(result);
                result.putString("result", "PASS");
                result.putString("checks", "Exact release repeated UI refresh: stable text, drawable reuse, paused queue and measured allocations");
                return;
            }
            if (gradientDefaultsOnly) {
                verifyGradientDefaults(radioNetwork);
                result.putString("result", "PASS");
                result.putString("checks", "Exact release fresh defaults: nine responsive themes with Logo selected, left/8/Fire/LED/bottom/15fps spectrum, green-yellow-red live bars, paused launch and saved choices");
                return;
            }
            if (radioVisibilityOnly || radioVisibilityResumeOnly) {
                verifyRadioVisibility(radioNetwork);
                result.putString("result", "PASS");
                result.putString("checks", radioVisibilityResumeOnly
                    ? "Exact release: hidden Radio restores paused without requests, names and sources persist, showing the panel does not autoplay, hidden settings and mode switching remain usable"
                    : "Exact release: upgrade preserves legacy hidden preference, names omit source counts, visibility changes keep the same stream, hidden source/media/mode controls work and saved music is retained");
                return;
            }
            if (radioLayoutOnly) {
                verifyPlayerLayoutAndNotices(radioNetwork);
                result.putString("result", "PASS");
                result.putString("checks", "Exact release: retained music, visible music/radio controls, useful left-to-right volume travel, real HTTP error overlay with unchanged geometry/captions, full error details, and already-added subscription management without downloads");
                return;
            }
            if (radioThemesOnly || radioThemesResumeOnly) {
                verifyRadioThemes(radioNetwork);
                if (uiContinuityOnly) verifyUiContinuity(radioNetwork);
                if (uiContinuityResumeOnly) check("zh".equals(activity.getResources().getConfiguration().locale.getLanguage()),
                    "The final selected language did not survive a fresh process");
                result.putString("result", "PASS");
                result.putString("checks", uiContinuityOnly
                    ? "Exact release upgrade: preserved data and defaults, opposite Aurora gradient, visible held touch/mouse/Enter menus with safe release, retained drag reorder, same-window Chinese/English changes with compositor frames and uninterrupted playback"
                    : uiContinuityResumeOnly
                    ? "Exact release new process: Chinese language, palette, logo, disabled subscription, spectrum preferences and paused saved music survive without startup network requests"
                    : radioThemesResumeOnly
                    ? "Exact release: palette, selected station mark, hidden-country defaults and disabled subscription survive a new process without autoplay or downloads"
                    : "Exact release: preserved upgrade settings and music, fixed twelve-logo picker, nine responsive palettes, General radio switch and uninterrupted playback");
                return;
            }
            if (playbackModesOnly || startupMusicOnly || startupRadioOnly || startupPausedOnly || startupPassiveOnly) {
                verifyPlaybackModes(radioNetwork);
                result.putString("result", "PASS");
                result.putString("checks", playbackModesOnly
                    ? "Exact release: default-off launch setting, Music/Radio button order and target sizes, saved music position and selected radio source, unchanged loop/order and no immediate autoplay on setting change"
                    : "Exact release: persisted music/radio startup preference, opt-in fresh-launch playback, paused/default and passive-open suppression, recreation stays paused");
                return;
            }
            if (radioAppearanceOnly || radioAppearanceResumeOnly) {
                verifyRadioAppearance(radioNetwork);
                result.putString("result", "PASS");
                result.putString("checks", radioAppearanceResumeOnly
                    ? "Exact release: logo, launcher alias, colorful icons, radio fallback and navigation preferences survive a fresh process without radio downloads or autoplay; local queue retained"
                    : "Exact release: selectable app/launcher and fallback logos, colorful/grey controls, explicit cached pinned-logo downloads, pinned/global wrap, current-station reveal, uninterrupted setting changes and retained music");
                return;
            }
            if (radioMetadataOnly) {
                verifyRadioMetadata(radioNetwork);
                result.putString("result", "PASS");
                result.putString("checks", "Exact release: native name edits preserve live connection, position, buffer and playing state; controller and UI metadata update; stopped edits never reconnect or autoplay; music restored");
                return;
            }
            if (radioSubscriptionsOnly || radioSubscriptionsResumeOnly) {
                verifyRadioSubscriptions(radioNetwork);
                result.putString("result", "PASS");
                result.putString("checks", radioSubscriptionsResumeOnly
                    ? "Exact release: separate subscription caches, disabled ownership and pins survive a fresh process; no startup downloads, autoplay or cache-toggle downloads; music baseline retained"
                    : "Exact release: opt-in presets, two independent subscriptions, conditional updates, last-good failure cache, shared station sources, uninterrupted active broadcast, cached disable, English/Chinese management and retained upgrade data");
                return;
            }
            if (navigationPreferencesOnly || navigationPreferencesResumeOnly) {
                verifyNavigationPreferences(radioNetwork);
                result.putString("result", "PASS");
                result.putString("checks", navigationPreferencesResumeOnly
                    ? "Exact release: hidden pages and radio selection option survive a fresh process; no autoplay/network; required pages stay accessible and all optional pages can be restored"
                    : "Exact release: upgrade defaults and saved music retained; three independent navigation switches, safe Settings Back, uninterrupted audio, grouped radio stays by default, optional Playing navigation, English/Chinese settings");
                return;
            }
            if (radioOnly || radioResumeOnly || radioListOnly || radioListResumeOnly || radioButtonsOnly ||
                radioDisabledResumeOnly || radioLiveOnly || radioLiveResumeOnly || radioExchangeOnly ||
                radioExchangeResumeOnly || radioReselectionOnly) {
                int openPlayer = getTargetContext().getResources().getIdentifier("radioOpenPlayer", "id", PACKAGE);
                if (openPlayer != 0) {
                    openRadioSettings();
                    if (!((CompoundButton) activity.findViewById(openPlayer)).isChecked())
                        tap(() -> activity.findViewById(openPlayer));
                    tap(() -> activity.findViewById(id("nav_player")));
                }
            }
            if (radioReselectionOnly || musicReselectionOnly) {
                if (radioReselectionOnly) verifyRadioReselection(radioNetwork);
                else verifyMusicReselection();
                result.putString("result", "PASS");
                result.putString("checks", radioReselectionOnly
                    ? "Exact release: same station, pinned/quick buttons and selected source keep their connection; alternate source and stopped-stream retry reconnect; local music remains paused and intact"
                    : "Exact release: upgrade retains 100 songs and paused position; current queue row, paused resume and library selection retain position; another song still starts normally");
                return;
            }
            if (radioExchangeOnly || radioExchangeResumeOnly) {
                verifyRadioExchange(radioNetwork, result);
                result.putString("result", "PASS");
                result.putString("checks", radioExchangeResumeOnly
                    ? "Exact release: replacement-only stations, sources and pin survive a fresh process; no startup fetch/autoplay; original music queue and reset retained"
                    : "Exact release: in-place upgrade, complete M3U export through the shared SAF explorer, offline edit, cancel/merge/deduplication/replace-all, unchanged import during playback, original music retained");
                return;
            }
            if (radioLiveOnly || radioLiveResumeOnly) {
                verifyRadioLiveOptions(radioNetwork);
                result.putString("result", "PASS");
                result.putString("checks", radioLiveResumeOnly
                    ? "Exact release: multi-source/tuning/pin restoration, paused startup with no fetch, selected source playback and preserved local queue"
                    : "Exact release: station editor, source switching/count, buffer/retry/status/logo controls, persistent logo clear and multi-source import");
                return;
            }
            if (radioButtonsOnly || radioDisabledResumeOnly) {
                verifyRadioButtons(radioNetwork);
                result.putString("result", "PASS");
                result.putString("checks", radioDisabledResumeOnly
                    ? "Exact release: hidden Radio restored across a fresh process, retained music/catalog/pins, no autoplay/network, panel shown without autoplay"
                    : "Exact release: pinned native buttons, grouped list, responsive toolbar, remote Menu, quick switching, English/Chinese branding, visibility-only switching and uninterrupted playback");
                return;
            }
            if (radioListOnly || radioListResumeOnly) {
                verifyRadioLists(radioNetwork, result);
                result.putString("result", "PASS");
                result.putString("checks", radioListResumeOnly
                    ? "Exact release: imported station/pin restored paused without fetching; radio playback and saved local queue retained"
                    : "Exact release: same explorer TXT/M3U selection, cancel/apply, built-in address override, HTTP playback, reset retaining custom station/pin, source-file independence");
                return;
            }
            if (radioBaselineOnly) {
                verifyRadioMusicBaseline();
                seedRadioUpgrade();
                if (gradientBaselineOnly || uiContinuityBaselineOnly) {
                    await(() -> !activity.findViewById(id("settingsPanel")).isShown());
                    openRadioSettings();
                    verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
                    tap(() -> activity.findViewById(id("settingsButtonIcons")));
                    tap(() -> focusedDialogView("iconsOcean"));
                    await(() -> "OCEAN".equals(getTargetContext().getSharedPreferences("settings", 0)
                        .getString("buttonIcons", "")));
                    verifySavedSpectrum(uiContinuityBaselineOnly);
                    tap(() -> activity.findViewById(id("nav_player")));
                    verifyRadioMusicBaseline();
                    check(getTargetContext().getSharedPreferences("settings", 0).edit().commit(),
                        "Could not persist the baseline appearance choices");
                    saveScreenshot(uiContinuityBaselineOnly ? "radio148-upgrade-baseline.png" : "radio147-upgrade-appearance-baseline.png");
                }
                result.putString("result", "PASS");
                result.putString("checks", "Baseline release: 100 local songs, saved folder/playlist, paused queue at song 76 / 20 seconds, edited two-source station, selected second source and explicit unpin");
                return;
            }
            if (radioOnly || radioResumeOnly) {
                verifyRadio(radioNetwork);
                result.putString("result", "PASS");
                result.putString("checks", radioResumeOnly
                    ? "Exact optimized APK: fresh-process paused radio restore, saved pins, no startup connection, retained local music queue"
                    : "Exact optimized APK: in-place baseline update, CNR search/pins, synthetic AAC/HLS, live controls, screen-off playback, notification, four cross-UID widgets, disconnected Stop and paused music restoration");
                return;
            }
            if (progressiveScanOnly) {
                result.putLong("launchMs", SystemClock.uptimeMillis() - launchStarted);
                verifyProgressiveScan(tree, result);
                result.putString("result", "PASS");
                result.putString("checks", progressiveScanBaseline ? "Original APK: complete 1000-song scan before publishing the library"
                    : "Exact optimized APK: 1000 slow-tag songs, durable early library, usable controls/playback during enrichment, no autoplay, completed durations, main-thread responsiveness and resumed pending details");
                return;
            }
            if (bulkRemovalOnly) {
                result.putLong("launchMs", SystemClock.uptimeMillis() - launchStarted);
                verifyLargeFolderRemoval(tree, result);
                result.putString("result", "PASS");
                result.putString("checks", "Exact optimized APK: large saved library/queue, bulk removal UI responsiveness, durable empty queue, retained playlists, zero file deletion, paused restart and interrupted-removal recovery");
                return;
            }
            if (remoteNavigationOnly) {
                verifyRemoteNavigation(tree);
                result.putString("result", "PASS");
                result.putString("checks", "Exact optimized APK: D-pad playback traversal and 10-second seek activation; focusable explorer locations, folders and files without touch or browsing autoplay");
                return;
            }
            if (explorerStorageSimulation) {
                removeMonitor(monitor);
                monitor = null;
                verifyExplorerStorageSimulation(result);
                result.putString("result", "PASS");
                result.putString("checks", "Exact delivered APK: internal and virtual removable roots, browse without import/autoplay, recursive folder imports, mixed-folder queue, individual-file playback, ejected-volume error with retained library and uninterrupted internal playback, same-volume remount and playback recovery");
                return;
            }
            if (karaokeOnly) {
                verifyKaraoke(tree, audio);
                result.putString("result", "PASS");
                result.putString("checks", "Exact optimized APK: optional karaoke switch and shared color picker, partial glyph coverage, advancing and paused rendering, backward seek, persisted recreation, cross-UID lyric-widget spans, mute alignment and player-only mute, appearance reset");
                return;
            }
            if (explorerLocationsOnly) {
                verifyExplorerLocations(tree);
                result.putString("result", "PASS");
                result.putString("checks", "Exact optimized APK: themed location Remove/Cancel, unused read-grant release, "
                    + "explicit regrant, saved-song and playing-queue preservation, no document deletion, "
                    + "durable hidden locations across a fresh process, and no restart autoplay");
                return;
            }
            if (fileExplorerOnly) {
                removeMonitor(monitor);
                monitor = null;
                verifyFileExplorer(tree, audio);
                result.putString("result", "PASS");
                result.putString("storageMode", Build.VERSION.SDK_INT <= 28 ? "legacy shared storage" : "scoped SAF subtree");
                result.putString("checks", "Exact optimized APK: native folder/file selection, real metadata and playback, retained explorer recreation, no browsing autoplay and uninterrupted playback; legacy write rejection or scoped subtree grants as applicable");
                return;
            }
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
                String guide = expectedAboutText();
                check(guide.contentEquals(body.getText()), "About must contain the introduction and complete guide");
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
                    android.graphics.drawable.Drawable foreground = activity.findViewById(id(name)).getForeground();
                    check("cover".equals(name) ? foreground == null :
                        foreground instanceof android.graphics.drawable.StateListDrawable,
                        "Disabled blocks remove " + name + " frame but retain state-only keyboard focus");
                }
                check(!activity.findViewById(id("settingsBlockShadow")).isShown(), "Disabled blocks hide shadow controls");
                check(activity.findViewById(id("settingsLanguage")).getForeground() instanceof android.graphics.drawable.StateListDrawable,
                    "Settings buttons must also restore standard controls with keyboard focus");
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
            if (homeWidgetsOnly || karaokeOnly || radioOnly) widgetCommand("finish", "");
            onMain(() -> {
                if (controller != null) controller.getTransportControls().pause();
                if (browser != null) browser.disconnect();
                if (activity != null) activity.finish();
            });
            finish("PASS".equals(result.getString("result")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
        }
    }

    private int id(String name) { return resource(name, "id"); }

    private void verifyPlayerExperience(Uri tree, FixtureRadioNetwork network, Bundle result) throws Exception {
        verifyRadioMusicBaseline();
        check(network.requests.isEmpty(), "Offline Radio warm-up must not add startup network work");
        StringBuilder finite = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n");
        for (int i = 0; i < 30; i++) finite.append("#EXTINF:4.0,\ntone").append(i).append(".aac\n");
        network.customPlaylist = finite.append("#EXT-X-ENDLIST\n").toString();
        long[] timing = {-1, -1, -1};
        android.view.ViewTreeObserver.OnDrawListener draw = () -> {
            if (timing[0] < 0) return;
            View loading = activity.findViewById(id("screenLoading"));
            View settings = activity.findViewById(id("settingsScroll"));
            if (timing[1] < 0 && loading != null && loading.isShown() && settings == null)
                timing[1] = SystemClock.elapsedRealtime() - timing[0];
            if (timing[2] < 0 && settings != null && settings.isShown())
                timing[2] = SystemClock.elapsedRealtime() - timing[0];
        };
        View root = activity.getWindow().getDecorView();
        onMain(() -> root.getViewTreeObserver().addOnDrawListener(draw));
        try {
            tap(() -> activity.findViewById(id("action_settings")),
                () -> timing[0] = SystemClock.elapsedRealtime());
            await(() -> timing[2] >= 0);
            check(timing[1] >= 0 && timing[1] < timing[2], "Settings did not draw its destination before construction");
            check(timing[1] < 1000, "First-use feedback took " + timing[1] + "ms");
            result.putLong("settingsInputToFeedbackMs", timing[1]);
            result.putLong("settingsInputToContentMs", timing[2]);
            saveScreenshot("ux155-settings.png");
        } finally { onMain(() -> root.getViewTreeObserver().removeOnDrawListener(draw)); }
        View firstSettings = activity.findViewById(id("settingsScroll"));
        tap(() -> activity.findViewById(id("nav_player")));
        tap(() -> activity.findViewById(id("action_settings")));
        await(() -> firstSettings.isShown());
        check(activity.findViewById(id("settingsScroll")) == firstSettings, "Return visit rebuilt Settings");
        check(!activity.findViewById(id("screenLoading")).isShown(), "Return visit showed a first-use loading state");
        verifyPrimaryControlColor(result);
        tap(() -> activity.findViewById(id("nav_player")));
        check(((TextView) activity.findViewById(id("status"))).getText().toString()
            .startsWith(activity.getString(resource("music_mode", "string"))), "Missing current Music mode");
        verifyPlayerControlsFit();
        saveScreenshot("ux155-playing.png");
        tap(() -> activity.findViewById(id("play")));
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        recordMusicRecoveryState(result, tree, "BeforeStop");
        onMain(() -> controller.getTransportControls().stop());
        await(() -> {
            PlaybackState state = controller.getPlaybackState();
            return state != null && (state.getState() == PlaybackState.STATE_STOPPED ||
                state.getState() == PlaybackState.STATE_NONE);
        });
        recordMusicRecoveryState(result, tree, "Stopped");
        setMusicUnavailable(tree, true);
        int opensBefore = recordMusicRecoveryState(result, tree, "Unavailable");
        try {
            tap(() -> activity.findViewById(id("play")));
            recordMusicRecoveryState(result, tree, "AfterPlay");
            try {
                await(() -> activity.findViewById(id("recoveryStatus")).isShown());
            } finally {
                recordMusicRecoveryState(result, tree, "RecoveryAwaitEnd");
            }
            check(getTargetContext().getContentResolver().call(tree, "scanFixtureState", null, null)
                .getInt("openRequests") > opensBefore, "Play did not reopen the unavailable source");
            await(() -> !activity.findViewById(id("noticeBar")).isShown());
            check("queue-fixture-76".equals(mediaId()), "Failure skipped to another song");
            check(controller.getQueue().size() == 100, "Failure changed the saved queue");
            saveScreenshot("ux155-music-recovery.png");
            Activity before = activity;
            onMain(before::recreate);
            await(() -> activity != before && activity.hasWindowFocus() &&
                activity.findViewById(id("recoveryStatus")).isShown() &&
                activity.findViewById(id("recoveryRetry")).isEnabled());
        } finally { setMusicUnavailable(tree, false); }
        tap(() -> activity.findViewById(id("recoveryRetry")));
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING &&
            !activity.findViewById(id("recoveryStatus")).isShown());
        recordMusicRecoveryState(result, tree, "Retried");
        check("queue-fixture-76".equals(mediaId()), "Retry did not keep the failed song");
        onMain(() -> {
            controller.getTransportControls().pause();
            controller.getTransportControls().seekTo(20_000);
        });
        await(() -> pausedAt(20_000));
        seedRadioUpgrade();
        tap(() -> activity.findViewById(id("nav_player")));
        network.failStreams = 503;
        try {
            tap(() -> activity.findViewById(id("switchPlaybackMode")));
            await(() -> activity.findViewById(id("recoveryStatus")).isShown());
            await(() -> !activity.findViewById(id("noticeBar")).isShown());
            int attempts = network.manifests.get();
            SystemClock.sleep(800);
            check(attempts == network.manifests.get(), "Stopped radio entered a retry loop");
            check("radio:cnr:zgzs".equals(mediaId()), "Radio failure changed station");
            check(!activity.findViewById(id("recoveryReselect")).isShown(), "Radio must not offer local folder recovery");
            check(((TextView) activity.findViewById(id("status"))).getText().toString()
                .startsWith(activity.getString(resource("radio", "string"))), "Missing current Radio mode");
            saveScreenshot("ux155-radio-recovery.png");
        } finally { network.failStreams = 0; }
        tap(() -> activity.findViewById(id("recoveryRetry")));
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING &&
            !activity.findViewById(id("recoveryStatus")).isShown());
        check("radio:cnr:zgzs".equals(mediaId()), "Radio retry selected a different station");
        boolean[] pinned = {false};
        onMain(() -> pinned[0] = activity.findViewById(id("favorite")).isSelected());
        tap(() -> activity.findViewById(id("favorite")));
        await(() -> activity.findViewById(id("favorite")).isSelected() != pinned[0]);
        check("radio:cnr:zgzs".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING,
            "The second action must pin the station without switching playback");
        verifyCurrentRadioHighlight(network);
        tap(() -> activity.findViewById(id("favorite")));
        await(() -> activity.findViewById(id("favorite")).isSelected() == pinned[0]);
        tap(() -> activity.findViewById(id("switchPlaybackMode")));
        await(() -> "queue-fixture-76".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        onMain(() -> {
            controller.getTransportControls().pause();
            controller.getTransportControls().seekTo(20_000);
        });
        await(() -> pausedAt(20_000));
        verifyRadioMusicBaseline();
        check(network.subscriptions.get() == 0, "UX work must not refresh subscriptions");
    }

    private void setMusicUnavailable(Uri tree, boolean value) {
        Bundle request = new Bundle();
        request.putBoolean("value", value);
        Bundle state = getTargetContext().getContentResolver().call(tree, "setUnavailable", null, request);
        check(state != null && state.containsKey("unavailable") && state.getBoolean("unavailable") == value,
            "Provider did not apply unavailable=" + value);
    }

    private int recordMusicRecoveryState(Bundle result, Uri tree, String stage) throws Exception {
        Bundle provider = getTargetContext().getContentResolver().call(tree, "scanFixtureState", null, null);
        check(provider != null && provider.containsKey("unavailable") && provider.containsKey("openRequests"),
            "Missing provider recovery diagnostics");
        String[] playback = {""};
        onMain(() -> {
            View play = activity.findViewById(id("play"));
            View mini = activity.findViewById(id("miniPlay"));
            View recovery = activity.findViewById(id("recoveryStatus"));
            playback[0] = "legacy=" + controller.getPlaybackState() +
                ", mainAction=" + (play == null ? null : play.getContentDescription()) +
                ", miniAction=" + (mini == null ? null : mini.getContentDescription()) +
                ", recoveryShown=" + (recovery != null && recovery.isShown()) +
                ", mediaId=" + mediaId();
        });
        String snapshot = playback[0] + ", providerUnavailable=" + provider.getBoolean("unavailable") +
            ", openRequests=" + provider.getInt("openRequests") + ", audioReads=" + provider.getInt("audioReads");
        result.putString("musicRecovery" + stage, snapshot);
        android.util.Log.i("ManaPlayerUX", "MUSIC_RECOVERY " + stage + ": " + snapshot);
        return provider.getInt("openRequests");
    }

    private void verifyPrimaryControlColor(Bundle result) throws Exception {
        android.content.SharedPreferences saved = getTargetContext().getSharedPreferences("settings", 0);
        int[] automatic = {0};
        onMain(() -> {
            automatic[0] = primaryButtonFill(activity.findViewById(id("play")));
            check(primaryButtonsHaveColor(automatic[0]), "Default highlight must be shared by both playback controls and mode");
        });
        Activity original = activity;
        String rawIconsBefore = saved.getString("buttonIcons", null);
        // The first settings write materializes the implicit LOGO default.
        String iconsBefore = saved.getString("buttonIcons", "LOGO");
        verifyCustomColor("settingsPrimaryControlColor", "primaryControlColor", false);
        await(() -> primaryButtonsHaveColor(0xff13579b));
        check(activity == original, "Choosing a highlight must not recreate the Activity");
        String rawIconsAfter = saved.getString("buttonIcons", null);
        String iconsAfter = saved.getString("buttonIcons", "LOGO");
        String iconState = "rawBefore=" + rawIconsBefore + ", rawAfter=" + rawIconsAfter +
            ", resolvedBefore=" + iconsBefore + ", resolvedAfter=" + iconsAfter;
        result.putString("highlightIconPalette", iconState);
        check(java.util.Objects.equals(iconsBefore, iconsAfter), "Highlight changed the icon palette: " + iconState);
        check(restoredAt(20_000), "Choosing a highlight changed the saved music position or autoplayed");
        saveScreenshot("ux155-highlight-settings.png");
        tap(() -> activity.findViewById(id("nav_player")));
        verifyPlayerControlsFit();
        saveScreenshot("ux155-highlight-playing.png");
        onMain(original::recreate);
        await(() -> activity != original && activity.hasWindowFocus() &&
            activity.findViewById(id("play")).isEnabled() && primaryButtonsHaveColor(0xff13579b));
        check(saved.getInt("primaryControlColor", -1) == 0x13579b, "Highlight did not survive recreation");
        tap(() -> activity.findViewById(id("action_settings")));
        tap(() -> activity.findViewById(id("settingsPrimaryControlColor")));
        await(() -> colorDialog() != null);
        tap(() -> colorDialog().findViewById(android.R.id.button3));
        await(() -> colorDialog() == null && saved.getInt("primaryControlColor", -1) == -1 &&
            primaryButtonsHaveColor(automatic[0]));
        check(restoredAt(20_000), "Resetting the highlight changed playback");
    }

    private boolean primaryButtonsHaveColor(int color) {
        for (String name : new String[] {"play", "miniPlay", "switchPlaybackMode"}) {
            View view = activity.findViewById(id(name));
            if (view == null || !view.isEnabled() || primaryButtonFill(view) != color) return false;
        }
        return true;
    }

    private void verifyCurrentRadioHighlight(FixtureRadioNetwork network) throws Exception {
        String station = mediaId();
        int connections = network.manifests.get();
        int[] automatic = {0};
        onMain(() -> automatic[0] = primaryButtonFill(activity.findViewById(id("play"))));
        Activity original = activity;
        tap(() -> activity.findViewById(id("action_settings")));
        verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        verifyCustomColor("settingsPrimaryControlColor", "primaryControlColor", false);
        check(activity == original, "Station highlight changes must not recreate the Activity");
        android.content.SharedPreferences saved = getTargetContext().getSharedPreferences("settings", 0);
        for (String gridName : new String[] {"radioQuickStations", "radioList"}) {
            boolean playing = "radioQuickStations".equals(gridName);
            tap(() -> activity.findViewById(id(playing ? "nav_player" : "nav_radio")));
            await(() -> radioGrid(gridName) != null && radioGrid(gridName).isShown());
            if (!playing) onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("Upgrade 134 CNR"));
            int position = playing ? java.util.Arrays.asList(saved.getString("radioPins", "").split("\n")).indexOf(station) : 0;
            check(position >= 0, "The selected station must be pinned for both highlight surfaces");
            onMain(() -> radioGrid(gridName).scrollBy(0, -100000));
            await(() -> {
                View tile = revealCurrentRadioTile(gridName, false);
                return tile != null && primaryButtonFill(tile) == 0xff13579b;
            });
            onMain(() -> {
                View tile = currentRadioTile(gridName, false);
                check(tile.isSelected(), "The highlighted tile must be the current station");
                check(colorContrast(((TextView) tile).getCurrentTextColor(), 0xff13579b) >= 4.5,
                    "The current station text must contrast with its highlight");
                check(primaryButtonsHaveColor(0xff13579b), "Station and playback highlights diverged");
            });
            saveScreenshot("ux155-radio-highlight-" + (playing ? "playing" : "pins") + ".png");
        }
        expandRadioGroup("radio_group_national");
        await(() -> radioItemCount(radioGrid("radioList")) >= 4);
        onMain(() -> radioGrid("radioList").scrollBy(0, 100000));
        await(() -> revealCurrentRadioTile("radioList", true) != null);
        onMain(() -> {
            View row = currentRadioTile("radioList", true);
            check(row.isSelected() && primaryButtonFill(row) == 0xff13579b, "Expanded station lost the shared highlight");
            check(colorContrast(((TextView) row).getCurrentTextColor(), 0xff13579b) >= 4.5,
                "Expanded station text does not contrast with its highlight");
        });
        saveScreenshot("ux155-radio-highlight-channel.png");
        check(station.equals(mediaId()) && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING &&
            connections == network.manifests.get(), "Highlight changes reconnected or changed the station");
        tap(() -> activity.findViewById(id("action_settings")));
        verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        tap(() -> activity.findViewById(id("settingsPrimaryControlColor")));
        await(() -> colorDialog() != null);
        tap(() -> colorDialog().findViewById(android.R.id.button3));
        await(() -> colorDialog() == null && primaryButtonsHaveColor(automatic[0]));
        tap(() -> activity.findViewById(id("nav_player")));
        int position = java.util.Arrays.asList(saved.getString("radioPins", "").split("\n")).indexOf(station);
        check(position >= 0, "Resetting the highlight must preserve the station pin");
        onMain(() -> radioGrid("radioQuickStations").scrollBy(0, -100000));
        await(() -> {
            View tile = revealCurrentRadioTile("radioQuickStations", false);
            return tile != null && primaryButtonFill(tile) == automatic[0];
        });
        check(connections == network.manifests.get(), "Resetting the highlight reconnected radio");
    }

    private View revealCurrentRadioTile(String gridName, boolean channel) {
        ViewGroup grid = radioGrid(gridName);
        if (grid == null || grid.isLayoutRequested() || grid.getHeight() <= 0) return null;
        View tile = currentRadioTile(gridName, channel);
        if (tile != null) {
            if (tile.getBottom() > 0 && tile.getTop() < grid.getHeight()) return tile;
            grid.scrollBy(0, tile.getTop());
        } else {
            grid.scrollBy(0, grid.canScrollVertically(1) ? Math.max(48, grid.getHeight() / 2) : -100000);
        }
        return null;
    }

    private int radioItemCount(ViewGroup grid) {
        AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        try {
            grid.onInitializeAccessibilityEvent(event);
            return event.getItemCount();
        } finally {
            event.recycle();
        }
    }

    private static double colorContrast(int foreground, int background) {
        if ((background >>> 24) != 255) throw new IllegalArgumentException("Contrast needs an opaque background");
        int alpha = foreground >>> 24;
        int[] shifts = {16, 8, 0};
        double[] weights = {0.2126, 0.7152, 0.0722};
        double foregroundLuminance = 0.05, backgroundLuminance = 0.05;
        for (int index = 0; index < shifts.length; index++) {
            int back = (background >>> shifts[index]) & 255;
            int front = (foreground >>> shifts[index]) & 255;
            int composited = (front * alpha + back * (255 - alpha)) / 255;
            foregroundLuminance += weights[index] * linearColorChannel(composited);
            backgroundLuminance += weights[index] * linearColorChannel(back);
        }
        return Math.max(foregroundLuminance, backgroundLuminance) /
            Math.min(foregroundLuminance, backgroundLuminance);
    }

    private static double linearColorChannel(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private View currentRadioTile(String gridName, boolean channel) {
        ViewGroup grid = radioGrid(gridName);
        if (grid == null) return null;
        String wanted = channel ? "channel:" + mediaId() : mediaId();
        for (int index = 0; index < grid.getChildCount(); index++) {
            View child = grid.getChildAt(index);
            Object key = channel ? child.getTag(id("playerFocusKey")) : child.getTag();
            if (wanted != null && wanted.equals(key)) return child;
        }
        return null;
    }

    private int primaryButtonFill(View view) {
        android.graphics.drawable.Drawable background = view.getBackground();
        Rect bounds = new Rect(background.getBounds());
        int[] state = background.getState();
        android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(80, 80, android.graphics.Bitmap.Config.ARGB_8888);
        try {
            background.setState(new int[] {android.R.attr.state_enabled});
            background.jumpToCurrentState();
            background.setBounds(0, 0, 80, 80);
            background.draw(new android.graphics.Canvas(image));
            return image.getPixel(40, 40);
        } finally {
            background.setBounds(bounds);
            background.setState(state);
            background.jumpToCurrentState();
            image.recycle();
        }
    }

    private void verifyNavigationPreferences(FixtureRadioNetwork network) throws Exception {
        verifyRadioMusicBaseline();
        check(network.requests.isEmpty(), "Navigation settings must not add startup network work");
        android.content.SharedPreferences saved = getTargetContext().getSharedPreferences("settings", 0);
        String[][] pages = {{"nav_library", "settingsShowLibrary", "showLibrary"},
            {"nav_queue", "settingsShowQueue", "showQueue"},
            {"nav_playlists", "settingsShowPlaylists", "showPlaylists"}};
        StringBuilder finite = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n");
        for (int i = 0; i < 30; i++) finite.append("#EXTINF:4.0,\ntone").append(i).append(".aac\n");
        network.customPlaylist = finite.append("#EXT-X-ENDLIST\n").toString();
        if (navigationPreferencesResumeOnly) {
            verifyOptionalNavigation(false);
            for (String[] page : pages) check(!saved.getBoolean(page[2], true), "Hidden page preference was lost");
            check(saved.getBoolean("radioOpenPlayerOnSelection", false), "Radio selection choice was lost");
            openRadioSettings();
            check(((CompoundButton) activity.findViewById(id("radioOpenPlayer"))).isChecked(), "Radio switch did not restore");
            tap(() -> activity.findViewById(id("radioOpenPlayer")));
            check(network.requests.isEmpty(), "Changing navigation preferences connected radio");
            tap(() -> activity.findViewById(id("nav_radio")));
            onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("jjzs"));
            String title = activity.getString(resource("radio_cnr_2", "string"));
            tap(() -> descriptionView(radioGrid("radioList"), title));
            await(() -> "radio:cnr:jjzs".equals(mediaId()) &&
                controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
            check(activity.findViewById(id("nav_radio")).isSelected(), "Turning off automatic navigation must stay in Radio");
            openRadioSettings();
            tap(() -> activity.findViewById(id("radioReturnMusic")));
            verifyRadioMusicBaseline();
            tap(() -> activity.findViewById(id("action_settings")));
            verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
            for (String[] page : pages) {
                check(!((CompoundButton) activity.findViewById(id(page[1]))).isChecked(), "Hidden switch did not restore");
                tap(() -> activity.findViewById(id(page[1])));
            }
            verifyOptionalNavigation(true);
            tap(() -> activity.findViewById(id("nav_player")));
            verifyNavigationLabels();
            saveScreenshot("navigation1360-restored-pages.png");
            return;
        }
        verifyOptionalNavigation(true);
        for (String[] page : pages) check(saved.getBoolean(page[2], true), "Upgrade must retain visible navigation by default");
        check(!saved.getBoolean("radioOpenPlayerOnSelection", false), "Radio should stay on its page by default");
        onMain(() -> controller.getTransportControls().play());
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        for (String[] page : pages) {
            tap(() -> activity.findViewById(id(page[0])));
            tap(() -> activity.findViewById(id("action_settings")));
            verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
            check(((CompoundButton) activity.findViewById(id(page[1]))).isChecked(), "Optional navigation should initially be enabled");
            tap(() -> activity.findViewById(id(page[1])));
            await(() -> !saved.getBoolean(page[2], true) &&
                activity.findViewById(id(page[0])).getVisibility() == View.GONE);
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            await(() -> !activity.findViewById(id("settingsPanel")).isShown() &&
                activity.findViewById(id("nav_player")).isSelected());
            check("queue-fixture-76".equals(mediaId()) && controller.getQueue().size() == 100 &&
                controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING &&
                controller.getPlaybackState().getPosition() >= 20_000,
                "Hiding navigation changed or interrupted music");
        }
        verifyOptionalNavigation(false);
        tap(() -> activity.findViewById(id("nav_folders")));
        saveScreenshot("navigation1360-hidden-pages.png");
        onMain(() -> { controller.getTransportControls().pause(); controller.getTransportControls().seekTo(20_000); });
        await(() -> restoredAt(20_000));
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("Upgrade 134 CNR"));
        expandRadioGroup("radio_group_national");
        tap(() -> descriptionView(radioGrid("radioList"), "Upgrade 134 CNR"));
        await(() -> "radio:cnr:zgzs".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        int connections = network.manifests.get();
        for (int i = 0; i < 3; i++) tap(() -> descriptionView(radioGrid("radioList"), "Upgrade 134 CNR"));
        check(network.manifests.get() == connections, "Repeated station selection restarted audio");
        onMain(() -> {
            check(activity.findViewById(id("nav_radio")).isSelected(), "Default station selection left Radio");
            check("Upgrade 134 CNR".contentEquals(((EditText) activity.findViewById(id("radioSearch"))).getText()),
                "Station selection cleared the search");
            check(descriptionView(radioGrid("radioList"), "Upgrade 134 CNR") != null, "Station selection collapsed its group");
        });
        saveScreenshot("navigation1360-radio-stays.png");
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("jjzs"));
        String title = activity.getString(resource("radio_cnr_2", "string"));
        tap(() -> descriptionView(radioGrid("radioList"), title));
        await(() -> "radio:cnr:jjzs".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        check(activity.findViewById(id("nav_radio")).isSelected(), "Selecting another station left Radio");
        connections = network.manifests.get();
        tap(() -> activity.findViewById(id("radioSettings")));
        check(!((CompoundButton) activity.findViewById(id("radioOpenPlayer"))).isChecked(), "Radio choice should initially be off");
        tap(() -> activity.findViewById(id("radioOpenPlayer")));
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        await(() -> activity.findViewById(id("radioSearch")).isShown());
        tap(() -> descriptionView(radioGrid("radioList"), title));
        await(() -> activity.findViewById(id("nav_player")).isSelected() &&
            activity.findViewById(id("radioQuickStations")) != null &&
            activity.findViewById(id("radioQuickStations")).isShown());
        check(network.manifests.get() == connections, "Open Playing preference reopened an unchanged station");
        saveScreenshot("navigation1360-open-playing.png");
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioReturnMusic")));
        verifyRadioMusicBaseline();
        tap(() -> activity.findViewById(id("action_settings")));
        verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        tap(() -> activity.findViewById(id("settingsLanguage")));
        tap(() -> windowText(activity.getString(resource("language_chinese", "string"))));
        await(() -> "zh".equals(activity.getResources().getConfiguration().locale.getLanguage()) && activity.hasWindowFocus());
        tap(() -> activity.findViewById(id("settingsShowPlaylists")));
        tap(() -> activity.findViewById(id("settingsShowPlaylists")));
        saveScreenshot("navigation1360-settings-zh.png");
        verifySettingsPage("categoryRadio", "settingsRadioPanel");
        onMain(() -> check(((CompoundButton) activity.findViewById(id("radioOpenPlayer"))).isChecked(),
            "Language change lost radio navigation preference"));
        tap(() -> activity.findViewById(id("radioOpenPlayer")));
        tap(() -> activity.findViewById(id("radioOpenPlayer")));
        onMain(() -> check(((CompoundButton) activity.findViewById(id("radioOpenPlayer"))).isChecked(),
            "Chinese radio navigation switch did not retain its choice"));
        saveScreenshot("navigation1360-radio-setting-zh.png");
        tap(() -> activity.findViewById(id("nav_player")));
        verifyOptionalNavigation(false);
        verifyRadioMusicBaseline();
    }

    private void verifyRadioSubscriptions(FixtureRadioNetwork network) throws Exception {
        verifyRadioMusicBaseline();
        check(controller.getPlaybackState().getState() != PlaybackState.STATE_PLAYING, "Subscription startup played audio");
        check(network.connectionAttempts.get() == 0, "Subscription startup attempted a network connection");
        android.content.SharedPreferences saved = getTargetContext().getSharedPreferences("settings", 0);
        if (radioSubscriptionsResumeOnly) {
            org.json.JSONArray lists = savedSubscriptions();
            check(lists.length() == 2 && !lists.getJSONObject(0).getBoolean("enabled") &&
                lists.getJSONObject(1).getBoolean("enabled"), "Subscription definitions or enabled states were lost");
            String stationId = lists.getJSONObject(0).getJSONArray("programs").getJSONObject(0).getString("id");
            check(java.util.Arrays.asList(saved.getString("radioPins", "").split("\n")).contains(stationId),
                "A disabled cache lost its station pin");
            check(saved.getString("radioPins", "").equals(saved.getString("radio138ExpectedPins", "")),
                "The shared 12-pin order was not restored");
            check(java.util.Collections.singleton("united kingdom")
                .equals(saved.getStringSet("radioHiddenGroups", java.util.Collections.emptySet())),
                "Hidden countries were not restored after restarting the optimized app");
            openRadioSettings();
            tap(() -> activity.findViewById(id("radioSubscriptions")));
            await(() -> focusedDialogView("radioSubscriptionRows") != null);
            for (String action : new String[] {"radio_subscription_enable", "radio_subscription_disable"}) {
                tap(() -> subscriptionRow(0));
                tap(() -> windowText(activity.getString(resource(action, "string"))));
                await(() -> savedSubscriptions().optJSONObject(0).optBoolean("enabled") ==
                    "radio_subscription_enable".equals(action) && subscriptionRow(0) != null && subscriptionRow(0).isEnabled());
            }
            check(network.connectionAttempts.get() == 0, "Opening or toggling cached subscriptions downloaded data");
            saveScreenshot("subscriptions1370-restored-cache-zh.png");
            tap(() -> dialogButton(activity.getString(resource("close", "string"))));
            tap(() -> activity.findViewById(id("nav_player")));
            verifyRadioMusicBaseline();
            return;
        }
        check(savedSubscriptions().length() == 0, "Upgrade subscribed or downloaded a preset automatically");
        verifyOptionalNavigation(true);
        check(!saved.getBoolean("radioOpenPlayerOnSelection", false), "Upgrade lost the station-selection navigation default");
        String china = "https://live.fanmingming.com/radio/m3u/index.m3u";
        String usa = "https://raw.githubusercontent.com/AlonDrilich/radio-playlists/main/countries/us.m3u";
        String primary = "https://radio.example/subscribed-primary/index.m3u8";
        String alternate = "https://radio.example/subscribed-alternate/index.m3u8";
        network.subscriptionBodies.put(china, "#EXTM3U\n#EXTINF:-1,Missing address\n\n" +
            "#EXTINF:-1,Subscribed fixture\n" + primary + "\n#EXTINF:-1,Subscribed fixture\n" + alternate + "\n");
        network.subscriptionBodies.put(usa, "#EXTM3U\n#EXTINF:-1,Subscribed fixture\n" + alternate + "\n");
        network.subscriptionEtags.put(china, "\"china-one\"");
        network.subscriptionEtags.put(usa, "\"usa-one\"");
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioSubscriptions")));
        tap(() -> dialogButton(activity.getString(resource("radio_subscription_add", "string"))));
        verifyRadioSubscriptionChoices();
        tapDialogChoice(activity.getString(resource("radio_subscription_china", "string")));
        await(() -> focusedDialogView("radioSubscriptionUrl") != null);
        onMain(() -> check(china.contentEquals(((EditText) focusedDialogView("radioSubscriptionUrl")).getText()),
            "China preset has the wrong playlist address"));
        check(network.connectionAttempts.get() == 0, "Opening a preset editor downloaded a list");
        tap(() -> dialogButton(activity.getString(resource("cancel", "string"))));
        check(savedSubscriptions().length() == 0, "Cancelling a preset editor added a subscription");
        for (String preset : new String[] {"radio_subscription_china", "radio_subscription_us"}) {
            int expected = savedSubscriptions().length() + 1;
            tap(() -> dialogButton(activity.getString(resource("radio_subscription_add", "string"))));
            tapDialogChoice(activity.getString(resource(preset, "string")));
            tap(() -> dialogButton(activity.getString(resource("radio_subscription_add_update", "string"))));
            await(() -> savedSubscriptions().length() == expected &&
                savedSubscriptions().optJSONObject(expected - 1).optLong("checkedAtMs") > 0 &&
                subscriptionRow(expected - 1) != null && subscriptionRow(expected - 1).isEnabled());
            check(savedSubscriptions().getJSONObject(expected - 1).isNull("failure"), "Preset update failed");
        }
        check(savedSubscriptions().getJSONObject(0).getInt("unavailableStations") == 1,
            "The unavailable station was not reported");
        check(savedSubscriptions().getJSONObject(0).getJSONArray("programs").length() == 1 &&
            savedSubscriptions().getJSONObject(0).getJSONArray("programs").getJSONObject(0)
                .getJSONArray("alternateUris").length() == 1, "Same-name sources were not combined");
        tap(() -> dialogButton(activity.getString(resource("radio_subscription_update_all", "string"))));
        await(() -> network.subscriptionConditionals.get() == 2 && subscriptionRow(0) != null && subscriptionRow(0).isEnabled());
        check(network.maxSubscriptionInFlight.get() == 1, "Update all ran simultaneous playlist downloads");
        long lastGood = savedSubscriptions().getJSONObject(0).getLong("updatedAtMs");
        network.subscriptionStatuses.put(china, 503);
        tap(() -> subscriptionRow(0));
        tap(() -> windowText(activity.getString(resource("radio_subscription_update", "string"))));
        await(() -> savedSubscriptions().optJSONObject(0).optJSONObject("failure") != null &&
            subscriptionRow(0) != null && subscriptionRow(0).isEnabled());
        check(savedSubscriptions().getJSONObject(0).getLong("updatedAtMs") == lastGood &&
            savedSubscriptions().getJSONObject(0).getJSONArray("programs").length() == 1,
            "A failed update replaced the last-good cache");
        network.subscriptionStatuses.remove(china);
        saveScreenshot("subscriptions1370-independent-lists-en.png");
        tap(() -> dialogButton(activity.getString(resource("close", "string"))));
        StringBuilder finite = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n");
        for (int index = 0; index < 30; index++) finite.append("#EXTINF:4.0,\ntone").append(index).append(".aac\n");
        network.customPlaylist = finite.append("#EXT-X-ENDLIST\n").toString();
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("Subscribed fixture"));
        expandRadioGroup("radio_group_other");
        tap(() -> descriptionView(radioGrid("radioList"), "Subscribed fixture"));
        String stationId = savedSubscriptions().getJSONObject(0).getJSONArray("programs").getJSONObject(0).getString("id");
        await(() -> stationId.equals(mediaId()) && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        check(activity.findViewById(id("nav_radio")).isSelected(), "Subscription station selection left Radio");
        int connections = network.manifests.get();
        tap(() -> activity.findViewById(id("nav_player")));
        tap(() -> activity.findViewById(id("favorite")));
        await(() -> java.util.Arrays.asList(saved.getString("radioPins", "").split("\n")).contains(stationId));
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioSubscriptions")));
        network.subscriptionBodies.put(china,
            "#EXTM3U\n#EXTINF:-1,Subscribed fixture\nhttps://radio.example/subscribed-new/index.m3u8\n");
        network.subscriptionEtags.put(china, "\"china-two\"");
        tap(() -> subscriptionRow(0));
        tap(() -> windowText(activity.getString(resource("radio_subscription_update", "string"))));
        await(() -> "\"china-two\"".equals(savedSubscriptions().optJSONObject(0).optString("etag")) &&
            subscriptionRow(0) != null && subscriptionRow(0).isEnabled());
        tap(() -> subscriptionRow(0));
        tap(() -> windowText(activity.getString(resource("radio_subscription_disable", "string"))));
        await(() -> !savedSubscriptions().optJSONObject(0).optBoolean("enabled") &&
            subscriptionRow(0) != null && subscriptionRow(0).isEnabled());
        check(network.manifests.get() == connections && stationId.equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING,
            "Refreshing or disabling the subscription interrupted the active broadcast");
        tap(() -> dialogButton(activity.getString(resource("close", "string"))));
        verifyRadioPinRefinements(network);
        verifyRadioCountryControls(network);
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioReturnMusic")));
        verifyRadioMusicBaseline();
        tap(() -> activity.findViewById(id("action_settings")));
        verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        tap(() -> activity.findViewById(id("settingsLanguage")));
        tap(() -> windowText(activity.getString(resource("language_chinese", "string"))));
        await(() -> "zh".equals(activity.getResources().getConfiguration().locale.getLanguage()) && activity.hasWindowFocus());
        verifySettingsPage("categoryRadio", "settingsRadioPanel");
        tap(() -> activity.findViewById(id("radioSubscriptions")));
        tap(() -> dialogButton(activity.getString(resource("radio_subscription_add", "string"))));
        verifyRadioSubscriptionChoices();
        saveScreenshot("subscriptions1370-presets-zh.png");
        tap(() -> dialogButton(activity.getString(resource("cancel", "string"))));
        saveScreenshot("subscriptions1370-management-zh.png");
        tap(() -> dialogButton(activity.getString(resource("close", "string"))));
        tap(() -> activity.findViewById(id("nav_player")));
        verifyRadioMusicBaseline();
        check(network.logos.get() == 0, "Updating subscriptions downloaded logos");
    }

    private void verifyRadioPinRefinements(FixtureRadioNetwork network) throws Exception {
        android.content.SharedPreferences saved = getTargetContext().getSharedPreferences("settings", 0);
        int connections = network.manifests.get();
        String playing = mediaId();
        tap(() -> activity.findViewById(id("nav_radio")));
        String[] codes = {"zhzs", "szzs", "hxzs", "xgzs"};
        for (int index = 0; index < codes.length; index++) {
            String code = codes[index];
            String title = activity.getString(resource("radio_cnr_" + (index + 5), "string"));
            onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText(title));
            expandRadioGroup("radio_group_national");
            await(() -> descriptionView(radioGrid("radioList"), title) != null);
            onMain(() -> descriptionView(radioGrid("radioList"), title).performLongClick());
            tapDialogChoice(activity.getString(resource("radio_pin", "string")));
            await(() -> java.util.Arrays.asList(saved.getString("radioPins", "").split("\n")).contains("radio:cnr:" + code));
        }
        java.util.List<String> pins = new java.util.ArrayList<>(java.util.Arrays.asList(saved.getString("radioPins", "").split("\n")));
        check(pins.size() == 12, "The optimized app must accept 12 pins");
        if (activity.findViewById(id("noticeBar")).isShown()) tap(() -> activity.findViewById(id("dismissNotice")));
        onMain(() -> {
            ((EditText) activity.findViewById(id("radioSearch"))).setText("");
            ((android.view.inputmethod.InputMethodManager) activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0);
        });
        for (String gridName : new String[] {"radioList", "radioQuickStations", "radioList"}) {
            tap(() -> activity.findViewById(id("radioList".equals(gridName) ? "nav_radio" : "nav_player")));
            await(() -> radioGrid(gridName) != null && radioGrid(gridName).isShown() && radioGrid(gridName).getHeight() > 48);
            String first = pins.get(0), second = pins.get(1);
            await(() -> {
                ViewGroup grid = radioGrid(gridName);
                grid.scrollBy(0, -100000);
                return grid.getChildCount() >= 2 && !grid.isLayoutRequested() &&
                    first.equals(grid.getChildAt(0).getTag()) && second.equals(grid.getChildAt(1).getTag());
            });
            dragRadioPin(() -> radioGrid(gridName).findViewWithTag(first), () -> radioGrid(gridName).findViewWithTag(second));
            java.util.Collections.swap(pins, 0, 1);
            String expected = android.text.TextUtils.join("\n", pins);
            await(() -> expected.equals(saved.getString("radioPins", "")));
            onMain(() -> {
                ViewGroup grid = radioGrid(gridName);
                check(first.equals(grid.getChildAt(1).getTag()) && second.equals(grid.getChildAt(0).getTag()),
                    "The visible pin order did not match the saved order");
                for (int index = 0; index < grid.getChildCount(); index++) {
                    View child = grid.getChildAt(index);
                    int position = pins.indexOf(child.getTag());
                    if (position < 0) continue;
                    check(child instanceof TextView && ((TextView) child).getText().toString().split("\n", 2)[0]
                        .contentEquals(child.getContentDescription()), "Pinned shortcut must show its name without a sequence prefix");
                }
            });
            saveScreenshot("radio141-release-" + gridName + "-pins.png");
        }
        saved.edit().putString("radio138ExpectedPins", saved.getString("radioPins", "")).commit();
        String usa = activity.getString(resource("radio_region_usa", "string"));
        for (int step = 0; step < 80; step++) {
            boolean[] found = {false};
            onMain(() -> found[0] = descriptionView(radioGrid("radioList"), usa) != null);
            if (found[0]) break;
            onMain(() -> radioGrid("radioList").scrollBy(0, radioGrid("radioList").getHeight() / 2));
            SystemClock.sleep(30);
        }
        expandRadioGroup("radio_region_usa");
        await(() -> {
            View heading = descriptionView(radioGrid("radioList"), usa);
            if (heading == null || radioGrid("radioList").isLayoutRequested()) return false;
            AccessibilityNodeInfo node = heading.createAccessibilityNodeInfo();
            try {
                CharSequence state = Build.VERSION.SDK_INT >= 30 ? node.getStateDescription() :
                    node.getExtras().getCharSequence("androidx.view.accessibility.AccessibilityNodeInfoCompat.STATE_DESCRIPTION_KEY");
                return activity.getString(resource("radio_group_expanded", "string")).contentEquals(state == null ? "" : state);
            } finally { node.recycle(); }
        });
        onMain(() -> {
            View heading = descriptionView(radioGrid("radioList"), usa);
            radioGrid("radioList").scrollBy(0, heading.getTop() + (int) (120 * heading.getResources().getDisplayMetrics().density));
        });
        await(() -> activity.findViewById(id("radioStickyGroup")).isShown() &&
            activity.findViewById(id("radioStickyGroup")).getHeight() >= 48);
        onMain(() -> check(usa.contentEquals(((TextView) activity.findViewById(id("radioStickyGroup"))).getText()),
            "The sticky country heading is wrong"));
        saveScreenshot("radio138-release-sticky-country.png");
        tap(() -> activity.findViewById(id("radioStickyGroup")));
        await(() -> !activity.findViewById(id("radioStickyGroup")).isShown() &&
            descriptionView(radioGrid("radioList"), usa) != null &&
            descriptionView(radioGrid("radioList"), activity.getString(resource("radio_region_uk", "string"))) != null);
        onMain(() -> check(descriptionView(radioGrid("radioList"), usa).getTop() <
            descriptionView(radioGrid("radioList"), activity.getString(resource("radio_region_uk", "string"))).getTop(),
            "US and UK should lead the international countries"));
        check(network.manifests.get() == connections && playing.equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING,
            "Moving pins or collapsing countries restarted playback");
    }

    private void dragRadioPin(Target source, Target target) {
        await(() -> source.get() != null && target.get() != null && source.get().getHeight() > 0 &&
            target.get().getHeight() > 0 && !source.get().isLayoutRequested());
        float[] points = new float[4];
        onMain(() -> {
            for (int index = 0; index < 2; index++) {
                View view = index == 0 ? source.get() : target.get();
                Rect visible = new Rect();
                int[] location = new int[2];
                view.getLocationOnScreen(location);
                points[index * 2] = location[0] + view.getWidth() / 2f;
                points[index * 2 + 1] = location[1] + view.getHeight() / 2f;
                check(view.getGlobalVisibleRect(visible) &&
                    visible.contains((int) points[index * 2], (int) points[index * 2 + 1]), "Drag center is not visible");
            }

            float extra = 8 * activity.getResources().getDisplayMetrics().density;
            points[2] += Math.signum(points[2] - points[0]) * extra;
            points[3] += Math.signum(points[3] - points[1]) * extra;
        });
        long down = SystemClock.uptimeMillis();
        for (int step = 0; step <= 14; step++) {
            float fraction = Math.max(0, Math.min(1, (step - 1) / 12f));
            int action = step == 0 ? MotionEvent.ACTION_DOWN : step == 14 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                points[0] + (points[2] - points[0]) * fraction, points[1] + (points[3] - points[1]) * fraction, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try { sendPointerSync(event); } finally { event.recycle(); }
            SystemClock.sleep(step == 0 ? android.view.ViewConfiguration.getLongPressTimeout() + 150L : 30);
        }
        waitForIdleSync();
    }

    private void verifyRadioCountryControls(FixtureRadioNetwork network) throws Exception {
        android.content.SharedPreferences saved = getTargetContext().getSharedPreferences("settings", 0);
        String pins = saved.getString("radioPins", "");
        String playing = mediaId();
        int connections = network.manifests.get();
        String catalog = radioSnapshot().toString();
        String usa = activity.getString(resource("radio_region_usa", "string"));
        onMain(() -> descriptionView(radioGrid("radioList"), usa).performLongClick());
        await(() -> focusedDialogView("playerDialogList") instanceof android.widget.ListView);
        verifyCountryActionRow(true);
        onMain(() -> check(((android.widget.ListView) focusedDialogView("playerDialogList")).getCheckedItemCount() == 1,
            "Country long press must preselect that country"));
        saveScreenshot("radio141-release-country-before-select-all.png");
        tap(() -> focusedDialogView("playerDialogSelectAll"));
        await(() -> {
            android.widget.ListView list = (android.widget.ListView) focusedDialogView("playerDialogList");
            return list.getCheckedItemCount() == list.getCount();
        });
        tap(() -> focusedDialogView("playerDialogDeselectAll"));
        await(() -> ((android.widget.ListView) focusedDialogView("playerDialogList")).getCheckedItemCount() == 0);
        tapRadioCountryChoice("radio_region_usa");
        tapRadioCountryChoice("radio_region_uk");
        saveScreenshot("radio141-release-country-selection.png");
        tap(() -> dialogButton(activity.getString(resource("radio_groups_hide", "string"))));
        java.util.Set<String> hidden = new java.util.HashSet<>(java.util.Arrays.asList("united states", "united kingdom"));
        await(() -> hidden.equals(saved.getStringSet("radioHiddenGroups", java.util.Collections.emptySet())));
        check(catalog.equals(radioSnapshot().toString()), "Hiding countries rewrote the radio catalog");
        check(pins.equals(saved.getString("radioPins", "")), "Hiding countries removed pins");
        tap(() -> dialogButton(activity.getString(resource("radio_groups_show", "string"))));
        await(() -> saved.getStringSet("radioHiddenGroups", java.util.Collections.emptySet()).isEmpty());
        tap(() -> dialogButton(activity.getString(resource("radio_groups_hide", "string"))));
        await(() -> hidden.equals(saved.getStringSet("radioHiddenGroups", java.util.Collections.emptySet())));
        tap(() -> dialogButton(activity.getString(resource("close", "string"))));
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioShowGroups")));
        await(() -> focusedDialogView("playerDialogList") instanceof android.widget.ListView);
        verifyCountryActionRow(false);
        onMain(() -> check(((android.widget.ListView) focusedDialogView("playerDialogList")).getCheckedItemCount() == 2,
            "The visibility manager must offer all hidden countries"));
        tap(() -> focusedDialogView("playerDialogDeselectAll"));
        await(() -> ((android.widget.ListView) focusedDialogView("playerDialogList")).getCheckedItemCount() == 0);
        tap(() -> focusedDialogView("playerDialogSelectAll"));
        await(() -> ((android.widget.ListView) focusedDialogView("playerDialogList")).getCheckedItemCount() == 2);
        saveScreenshot("radio141-release-hidden-footer.png");
        tapRadioCountryChoice("radio_region_uk");
        tap(() -> dialogButton(activity.getString(resource("radio_groups_show", "string"))));
        await(() -> java.util.Collections.singleton("united kingdom")
            .equals(saved.getStringSet("radioHiddenGroups", java.util.Collections.emptySet())));
        onMain(() -> check(dialogButton(activity.getString(resource("radio_groups_hide", "string"))) != null,
            "Unhiding the selected countries must switch the same dialog back to Hide"));
        tap(() -> dialogButton(activity.getString(resource("close", "string"))));
        check(network.manifests.get() == connections && playing.equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING,
            "Country visibility changed the playing source or reconnected it");
        saveScreenshot("radio141-release-country-settings.png");
    }

    private void verifyCountryActionRow(boolean hasDelete) {
        await(() -> {
            View first = focusedDialogView("playerDialogSelectAll");
            return first != null && first.getHeight() > 0 && !first.isLayoutRequested() &&
                first.hasWindowFocus() && !((View) first.getParent()).isLayoutRequested();
        });
        onMain(() -> {
            View first = focusedDialogView("playerDialogSelectAll");
            ViewGroup row = (ViewGroup) first.getParent();
            check(row.getParent() instanceof android.widget.HorizontalScrollView,
                "Country actions need one horizontally scrollable footer");
            java.util.List<View> controls = new java.util.ArrayList<>();
            controls.add(first);
            controls.add(focusedDialogView("playerDialogDeselectAll"));
            if (hasDelete) controls.add(first.getRootView().findViewById(android.R.id.button3));
            controls.add(first.getRootView().findViewById(android.R.id.button2));
            controls.add(first.getRootView().findViewById(android.R.id.button1));
            for (int index = 0; index < controls.size(); index++) {
                View button = controls.get(index);
                check(button.getParent() == row && button.getTop() == first.getTop(),
                    "Country actions must be in the same bottom row");
                check(button.getHeight() >= 48 * button.getResources().getDisplayMetrics().density,
                    "Country action touch target is too small");
                if (index > 0) check(controls.get(index - 1).getRight() < button.getLeft(), "Footer buttons overlap");
            }
            check(first.getLeft() == 0 && controls.get(controls.size() - 1).getRight() == row.getWidth(),
                "Selection controls must be at the left and other actions at the right");
        });
    }

    private void tapRadioCountryChoice(String resourceName) {
        String prefix = activity.getString(resource(resourceName, "string")) + "\n";
        String[] label = {null};
        onMain(() -> {
            android.widget.ListView list = (android.widget.ListView) focusedDialogView("playerDialogList");
            for (int index = 0; index < list.getCount(); index++) {
                String text = list.getItemAtPosition(index).toString();
                if (text.startsWith(prefix)) { label[0] = text; break; }
            }
        });
        check(label[0] != null, "Country is missing from the manager: " + prefix);
        tapDialogChoice(label[0]);
    }

    private org.json.JSONArray savedSubscriptions() {
        org.json.JSONArray subscriptions = radioSnapshot().optJSONArray("subscriptions");
        return subscriptions == null ? new org.json.JSONArray() : subscriptions;
    }

    private void verifyRadioSubscriptionChoices() {
        await(() -> focusedDialogView("playerDialogList") instanceof android.widget.ListView);
        onMain(() -> {
            android.widget.ListAdapter choices = ((android.widget.ListView) focusedDialogView("playerDialogList")).getAdapter();
            String[] labels = {"radio_subscription_china", "radio_subscription_hongkong", "radio_subscription_taiwan",
                "radio_subscription_us", "radio_subscription_uk", "radio_subscription_world", "radio_subscription_custom"};
            check(choices.getCount() == labels.length, "Expected six presets and Custom");
            for (int index = 0; index < labels.length; index++)
                check(activity.getString(resource(labels[index], "string")).equals(choices.getItem(index)),
                    "Missing or out-of-order subscription choice: " + labels[index]);
        });
    }

    private void tapDialogChoice(String text) {
        await(() -> focusedDialogView("playerDialogList") instanceof android.widget.ListView);
        onMain(() -> {
            android.widget.ListView list = (android.widget.ListView) focusedDialogView("playerDialogList");
            for (int index = 0; index < list.getAdapter().getCount(); index++) {
                if (text.equals(list.getAdapter().getItem(index))) {
                    list.setSelection(index);
                    return;
                }
            }
            throw new AssertionError("Missing dialog choice: " + text);
        });
        tap(() -> windowText(text));
    }

    private View focusedDialogView(String name) {
        for (Object window : windowRoots()) {
            if (!(window instanceof View)) continue;
            View root = (View) window;
            if (!root.hasWindowFocus() || root == activity.getWindow().getDecorView()) continue;
            View child = root.findViewById(id(name));
            if (child != null && child.isShown()) return child;
        }
        return null;
    }

    private View subscriptionRow(int index) {
        View root = focusedDialogView("radioSubscriptionRows");
        org.json.JSONObject subscription = savedSubscriptions().optJSONObject(index);
        return root == null || subscription == null ? null : root.findViewWithTag(subscription.optString("id"));
    }

    private void verifyOptionalNavigation(boolean shown) {
        onMain(() -> {
            for (String name : new String[] {"nav_library", "nav_queue", "nav_playlists"})
                check(activity.findViewById(id(name)).getVisibility() == (shown ? View.VISIBLE : View.GONE),
                    "Wrong optional page visibility: " + name);
            for (String name : new String[] {"nav_player", "nav_folders"}) {
                View button = activity.findViewById(id(name));
                check(button.isShown() && button.isEnabled(), "Required page is unavailable: " + name);
                float minimum = 48 * activity.getResources().getDisplayMetrics().density;
                check(button.getWidth() >= minimum && button.getHeight() >= minimum,
                    "Required navigation target is too small: " + name);
            }
        });
    }

    private void verifyMusicReselection() throws Exception {
        verifyRadioMusicBaseline();
        tap(() -> activity.findViewById(id("nav_queue")));
        await(() -> queueRow("Queue 076") != null);
        onMain(() -> controller.getTransportControls().play());
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        for (int index = 0; index < 3; index++) tap(() -> queueRow("Queue 076").findViewById(id("title")));
        SystemClock.sleep(500);
        check(controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING &&
            controller.getPlaybackState().getPosition() >= 20_000,
            "Current queue selection restarted the song");
        onMain(() -> {
            controller.getTransportControls().pause();
            controller.getTransportControls().seekTo(23_000);
        });
        await(() -> pausedAt(23_000));
        tap(() -> queueRow("Queue 076").findViewById(id("title")));
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        check(controller.getPlaybackState().getPosition() >= 23_000, "Paused music selection restarted instead of resuming");
        tap(() -> activity.findViewById(id("nav_library")));
        await(() -> visibleEditor(activity.getWindow().getDecorView()) != null);
        onMain(() -> visibleEditor(activity.getWindow().getDecorView()).setText("Queue 076"));
        await(() -> {
            ViewGroup list = activity.findViewById(id("libraryList"));
            return list != null && list.getChildCount() == 1 && textView(list, "Queue 076") != null;
        });
        tap(() -> textView(activity.findViewById(id("libraryList")), "Queue 076"));
        await(() -> controller.getQueue().size() == 1 &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        check("queue-fixture-76".equals(mediaId()) && controller.getPlaybackState().getPosition() >= 23_000,
            "Library selection rebuilt the current audio instead of only changing its queue");
        saveScreenshot("reselect1351-music.png");
        tap(() -> activity.findViewById(id("nav_library")));
        onMain(() -> visibleEditor(activity.getWindow().getDecorView()).setText("Queue 002"));
        await(() -> textView(activity.findViewById(id("libraryList")), "Queue 002") != null);
        tap(() -> textView(activity.findViewById(id("libraryList")), "Queue 002"));
        await(() -> "queue-fixture-2".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        check(controller.getPlaybackState().getPosition() < 5_000, "A different music selection did not start normally");
        onMain(() -> {
            controller.getTransportControls().pause();
            controller.getTransportControls().seekTo(12_000);
        });
        await(() -> pausedAt(12_000));
    }

    private java.util.Map<String, Rect> playerControlGeometry() {
        java.util.Map<String, Rect> result = new java.util.LinkedHashMap<>();
        onMain(() -> {
            for (String name : new String[] {"shuffle", "previous", "rewind", "play", "forward", "next", "repeat",
                    "favorite", "switchPlaybackMode", "details", "volumeMute", "volume", "screenContainer",
                    "cover", "metadataRow", "seekBlock", "transportRow", "actionRow", "volumeBlock", "navigation"})
                result.put(name, bounds(activity.findViewById(id(name))));
        });
        return result;
    }

    private java.util.List<String> playerActionCaptions() {
        java.util.List<String> result = new java.util.ArrayList<>();
        onMain(() -> {
            for (String name : new String[] {"favorite", "switchPlaybackMode", "details"})
                result.add(((TextView) activity.findViewById(id(name))).getText().toString());
        });
        return result;
    }

    private void verifyPlayerControlsFit() {
        java.util.Map<String, Rect> geometry = playerControlGeometry();
        onMain(() -> {
            float density = activity.getResources().getDisplayMetrics().density;
            int minimum = Math.round(48 * density);
            Rect viewport = geometry.get("screenContainer");
            for (String name : new String[] {"shuffle", "previous", "rewind", "play", "forward", "next", "repeat",
                    "favorite", "switchPlaybackMode", "details", "volumeMute", "volume"}) {
                Rect control = geometry.get(name);
                check(control.width() >= minimum && control.height() >= minimum, "Small player target: " + name);
                check(viewport.contains(control), "Player control requires scrolling: " + name + " " + geometry);
            }
            ViewGroup screens = activity.findViewById(id("screenContainer"));
            boolean found = false;
            for (int index = 0; index < screens.getChildCount(); index++) {
                View child = screens.getChildAt(index);
                if (child instanceof android.widget.ScrollView && child.isShown()) {
                    android.widget.ScrollView player = (android.widget.ScrollView) child;
                    check(player.getScrollY() == 0 && player.getChildAt(0).getHeight() == player.getHeight(),
                        "The player must fit without outer scrolling");
                    found = true;
                }
            }
            check(found, "Playing page is not visible");
            View mode = activity.findViewById(id("switchPlaybackMode"));
            View pin = activity.findViewById(id("favorite"));
            ViewGroup actionRow = (ViewGroup) mode.getParent();
            check(actionRow.indexOfChild(mode) + 1 == actionRow.indexOfChild(pin) &&
                actionRow.indexOfChild(pin) + 1 == actionRow.indexOfChild(activity.findViewById(id("details"))),
                "Player actions must be Radio/Music, Favorite/Pin, Details");
            float playCenter = geometry.get("play").exactCenterX();
            check(Math.abs(geometry.get("switchPlaybackMode").exactCenterX() - playCenter) >
                Math.abs(geometry.get("favorite").exactCenterX() - playCenter),
                "Mode switch is not separated from central playback");
            Rect previous = null;
            for (String name : new String[] {"previous", "rewind", "play", "forward", "next"}) {
                Rect control = geometry.get(name);
                if (previous != null) check(previous.top == control.top && previous.right <= control.left,
                    "Transport order or row changed");
                previous = control;
            }
            android.widget.SeekBar volume = activity.findViewById(id("volume"));
            check(volume.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR, "Volume direction is reversed");
            check(volume.getWidth() - volume.getPaddingLeft() - volume.getPaddingRight() >= 96 * density,
                "Volume track was squeezed");
            int progress = volume.getProgress();
            try {
                volume.setProgress(0);
                int left = volume.getThumb().getBounds().centerX();
                check(volume.getPaddingLeft() - volume.getThumbOffset() + volume.getThumb().getBounds().left >= 0,
                    "Volume thumb exceeds its left boundary");
                volume.setProgress(volume.getMax());
                check(volume.getThumb().getBounds().centerX() > left, "Increasing volume did not move right");
                check(volume.getPaddingLeft() - volume.getThumbOffset() + volume.getThumb().getBounds().right
                    <= volume.getWidth(), "Volume thumb exceeds its right boundary");
            } finally { volume.setProgress(progress); }
        });
    }

    private void verifyRadioVisibility(FixtureRadioNetwork network) throws Exception {
        android.content.SharedPreferences saved = getTargetContext().getSharedPreferences("settings", 0);
        check(!saved.getBoolean("radioEnabled", true), "The saved hidden Radio preference was lost");
        onMain(() -> check(activity.findViewById(id("nav_radio")).getVisibility() == View.GONE,
            "The hidden Radio tab is visible"));
        check(network.requests.isEmpty(), "Restoration fetched radio audio, subscriptions or logos");
        check(savedRadioPrograms().length() == 1, "The edited station was lost");
        org.json.JSONObject station = savedRadioPrograms().getJSONObject(0);
        check("Upgrade 134 CNR".equals(station.getString("name")) &&
            "https://radio.example/upgrade-b/index.m3u8".equals(savedRadioSource(station)),
            "The station name or selected source was lost");
        String catalog = savedRadioPrograms().toString();
        String pins = saved.getString("radioPins", "");
        check(!pins.contains("radio:cnr:zgzs"), "Upgrade re-pinned an explicitly unpinned station");
        if (radioVisibilityResumeOnly) {
            await(() -> "radio:cnr:zgzs".equals(mediaId()) && radioStopped());
            SystemClock.sleep(600);
            check(network.requests.isEmpty(), "Hidden restored radio connected without Play");
        } else {
            verifyRadioMusicBaseline();
            tap(() -> activity.findViewById(id("switchPlaybackMode")));
            await(() -> "radio:cnr:zgzs".equals(mediaId()) &&
                controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
            check(network.requests.contains("radio.example/upgrade-b/index.m3u8"),
                "The hidden Radio shortcut did not resume the saved source");
        }
        await(() -> activity.findViewById(id("radioQuickStations")) != null &&
            radioGrid("radioQuickStations").isShown() && radioGrid("radioQuickStations").getChildCount() > 0);
        onMain(() -> {
            ViewGroup grid = radioGrid("radioQuickStations");
            for (int i = 0; i < grid.getChildCount(); i++) {
                TextView button = (TextView) grid.getChildAt(i);
                check(button.getText().toString().contentEquals(button.getContentDescription()) &&
                    button.getText().toString().indexOf('\n') < 0, "Pinned names contain extra source annotations");
            }
            check(activity.getString(resource("radio_sources", "string"), 2)
                .contentEquals(activity.findViewById(id("details")).getContentDescription()),
                "The Sources control lost its count");
        });
        verifyPlayerControlsFit();
        saveScreenshot(radioVisibilityResumeOnly ? "radio146-release-hidden-restored.png" : "radio146-release-hidden-playing.png");
        int connections = network.manifests.get();
        openRadioSettings();
        onMain(() -> {
            for (String name : new String[] {"radioSubscriptions", "radioImport", "radioExport",
                    "radioReset", "radioDownloadPinnedLogos", "radioReturnMusic"})
                check(activity.findViewById(id(name)).isEnabled(), "Hidden Radio disabled settings: " + name);
        });
        verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        tap(() -> activity.findViewById(id("settingsRadio")));
        await(() -> saved.getBoolean("radioEnabled", false) &&
            activity.findViewById(id("nav_radio")).getVisibility() == View.VISIBLE);
        check(connections == network.manifests.get() && "radio:cnr:zgzs".equals(mediaId()),
            "Showing Radio changed the current source");
        if (radioVisibilityResumeOnly) {
            check(radioStopped(), "Showing the panel unexpectedly started radio");
        } else {
            check(controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING,
                "Showing the panel stopped radio");
        }
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("Upgrade 134 CNR"));
        expandRadioGroup("radio_group_national");
        await(() -> descriptionView(radioGrid("radioList"), "Upgrade 134 CNR") != null);
        onMain(() -> check("Upgrade 134 CNR".contentEquals(((TextView)
            descriptionView(radioGrid("radioList"), "Upgrade 134 CNR")).getText()),
            "A multi-source station name contains a source-count suffix"));
        saveScreenshot("radio146-release-name-only-list.png");
        openRadioSettings();
        verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        tap(() -> activity.findViewById(id("settingsRadio")));
        await(() -> !saved.getBoolean("radioEnabled", true) &&
            activity.findViewById(id("nav_radio")).getVisibility() == View.GONE);
        check(connections == network.manifests.get() && "radio:cnr:zgzs".equals(mediaId()),
            "Hiding Radio reconnected or replaced the current stream");
        if (radioVisibilityResumeOnly) check(radioStopped(), "Hiding the panel unexpectedly started radio");
        else check(controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING,
            "Hiding the panel interrupted audio");
        tap(() -> activity.findViewById(id("nav_player")));
        tap(() -> activity.findViewById(id("details")));
        await(() -> windowText(activity.getString(resource("radio_source_item", "string"), 2,
            "https://radio.example/upgrade-b/index.m3u8")) != null);
        saveScreenshot("radio146-release-source-details.png");
        clickDialogAction("cancel");
        if (!radioVisibilityResumeOnly) {
            tap(() -> activity.findViewById(id("rewind")));
            await(() -> network.requests.contains("radio.example/upgrade-a/index.m3u8") &&
                controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
            int beforeNext = network.manifests.get();
            tap(() -> activity.findViewById(id("forward")));
            await(() -> network.manifests.get() > beforeNext &&
                controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
            onMain(() -> controller.getTransportControls().pause());
            await(() -> radioStopped() && network.active.get() == 0);
            int beforePlay = network.manifests.get();
            onMain(() -> controller.getTransportControls().play());
            await(() -> network.manifests.get() > beforePlay &&
                controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        }
        tap(() -> activity.findViewById(id("switchPlaybackMode")));
        await(() -> "queue-fixture-76".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        onMain(() -> { controller.getTransportControls().pause(); controller.getTransportControls().seekTo(20_000); });
        await(() -> pausedAt(20_000));
        verifyRadioMusicBaseline();
        tap(() -> activity.findViewById(id("switchPlaybackMode")));
        await(() -> "radio:cnr:zgzs".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        onMain(() -> controller.getTransportControls().pause());
        await(() -> radioStopped() && network.active.get() == 0);
        check(catalog.equals(savedRadioPrograms().toString()) && pins.equals(saved.getString("radioPins", "")),
            "Visibility or mode switching changed saved station data");
        check(!saved.getBoolean("radioEnabled", true), "Mode switching changed panel visibility");
        verifyPlayerControlsFit();
    }

    private void verifyPlayerLayoutAndNotices(FixtureRadioNetwork network) throws Exception {
        verifyRadioMusicBaseline();
        check(network.requests.isEmpty(), "Layout startup downloaded radio data");
        tap(() -> activity.findViewById(id("nav_player")));
        verifyPlayerControlsFit();
        saveScreenshot("radio145-release-music.png");
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("Upgrade 134 CNR"));
        expandRadioGroup("radio_group_national");
        tap(() -> descriptionView(radioGrid("radioList"), "Upgrade 134 CNR"));
        await(() -> "radio:cnr:zgzs".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        check(network.requests.contains("radio.example/upgrade-b/index.m3u8"),
            "The upgrade lost the selected radio source");
        tap(() -> activity.findViewById(id("nav_player")));
        verifyPlayerControlsFit();
        java.util.Map<String, Rect> playing = playerControlGeometry();
        java.util.List<String> captions = playerActionCaptions();
        saveScreenshot("radio145-release-radio.png");
        try {
            network.failStreams = 503;
            tap(() -> activity.findViewById(id("rewind")));
            await(() -> activity.findViewById(id("noticeBar")).isShown());
            waitForIdleSync();
            check(playing.equals(playerControlGeometry()), "HTTP error moved player controls");
            check(captions.equals(playerActionCaptions()), "HTTP error changed action captions");
            verifyPlayerControlsFit();
            saveScreenshot("radio145-release-radio-error.png");
            String[] message = new String[1];
            onMain(() -> message[0] = ((TextView) activity.findViewById(id("noticeText"))).getText().toString());
            check(message[0].contains("HTTP") || message[0].contains("503"), "Expected the actual stream failure");
            tap(() -> activity.findViewById(id("noticeText")));
            await(() -> windowText(message[0]) != null);
            saveScreenshot("radio145-release-error-details.png");
            clickDialogAction("close");
            tap(() -> activity.findViewById(id("dismissNotice")));
            check(playing.equals(playerControlGeometry()), "Dismissing an error moved player controls");
        } finally { network.failStreams = 0; }
        tap(() -> activity.findViewById(id("switchPlaybackMode")));
        await(() -> "queue-fixture-76".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        onMain(() -> {
            controller.getTransportControls().pause();
            controller.getTransportControls().seekTo(20_000);
        });
        await(() -> pausedAt(20_000));
        verifyRadioMusicBaseline();
        verifyPlayerControlsFit();
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioSubscriptions")));
        tap(() -> focusedDialogView("radioSubscriptionRows").getRootView().findViewById(android.R.id.button1));
        await(() -> focusedDialogView("playerDialogList") instanceof android.widget.ListView);
        onMain(() -> {
            android.widget.ListAdapter choices = ((android.widget.ListView) focusedDialogView("playerDialogList")).getAdapter();
            for (String preset : new String[] {"radio_subscription_world", "radio_subscription_us", "radio_subscription_taiwan"}) {
                String added = activity.getString(resource("radio_subscription_preset_added", "string"),
                    activity.getString(resource(preset, "string")));
                boolean found = false;
                for (int index = 0; index < choices.getCount(); index++) found |= added.equals(choices.getItem(index));
                check(found, "Missing Added indicator: " + added);
            }
        });
        saveScreenshot("radio145-release-added-presets.png");
        tapDialogChoice(activity.getString(resource("radio_subscription_preset_added", "string"),
            activity.getString(resource("radio_subscription_us", "string"))));
        await(() -> windowText(activity.getString(resource("radio_subscription_disable", "string"))) != null);
        check(focusedDialogView("radioSubscriptionName") == null, "An added preset opened a duplicate editor");
        tap(() -> dialogButton(activity.getString(resource("cancel", "string"))));
        clickDialogAction("close");
        check(network.subscriptions.get() == 0, "Opening added presets downloaded a subscription");
        tap(() -> activity.findViewById(id("nav_player")));
        verifyPlayerControlsFit();
    }

    private void verifyPlaybackModes(FixtureRadioNetwork network) throws Exception {
        android.content.SharedPreferences saved = getTargetContext().getSharedPreferences("settings", 0);
        if (startupPausedOnly || startupPassiveOnly) {
            check(saved.getBoolean("autoPlayOnLaunch", false) == startupPassiveOnly,
                "The saved autoplay preference was not retained");
            await(() -> "queue-fixture-76".equals(mediaId()) && restoredAt(31_000));
            SystemClock.sleep(750);
            check(restoredAt(31_000), "Disabled or passive startup unexpectedly played music");
            check(network.connectionAttempts.get() == 0, "Passive music startup opened the radio");
            check(controller.getPlaybackState().getState() == PlaybackState.STATE_NONE ||
                controller.getPlaybackState().getState() == PlaybackState.STATE_STOPPED,
                "Paused restoration must not prepare audio");
            setStartupPlayback(!startupPassiveOnly);
            check(restoredAt(31_000), "Changing the autoplay setting played music immediately");
            return;
        }
        if (startupMusicOnly || startupRadioOnly) {
            check(saved.getBoolean("autoPlayOnLaunch", false), "Autoplay opt-in was not persisted");
            String expected = startupRadioOnly ? "radio:cnr:zgzs" : "queue-fixture-76";
            await(() -> expected.equals(mediaId()) &&
                controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
            if (startupRadioOnly) {
                check(network.manifests.get() == 1, "Radio startup opened more than one stream");
                check(network.requests.contains("radio.example/upgrade-b/index.m3u8"),
                    "Radio startup did not restore the selected source");
            } else {
                check(network.connectionAttempts.get() == 0, "Music autoplay contacted radio");
                check(controller.getPlaybackState().getPosition() >= 25_000,
                    "Music autoplay lost its saved position");
                check(controller.getQueue().size() == 100, "Music autoplay lost the queue");
            }
            onMain(() -> controller.getTransportControls().pause());
            await(() -> radioStopped());
            Activity previous = activity;
            onMain(() -> previous.recreate());
            await(() -> activity != previous && activity.hasWindowFocus() &&
                activity.findViewById(id("switchPlaybackMode")).isEnabled());
            check(radioStopped(), "Activity recreation restarted paused playback");
            if (startupRadioOnly) {
                tap(() -> activity.findViewById(id("switchPlaybackMode")));
                await(() -> "queue-fixture-76".equals(mediaId()) &&
                    controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
                onMain(() -> {
                    controller.getTransportControls().pause();
                    controller.getTransportControls().seekTo(31_000);
                });
                await(() -> pausedAt(31_000));
                setStartupPlayback(false);
            } else {
                onMain(() -> controller.getTransportControls().seekTo(27_000));
                await(() -> pausedAt(27_000));
                tap(() -> activity.findViewById(id("switchPlaybackMode")));
                await(() -> "radio:cnr:zgzs".equals(mediaId()) &&
                    controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
                onMain(() -> controller.getTransportControls().pause());
                await(() -> radioStopped());
            }
            check(network.subscriptions.get() == 0, "Autoplay must not refresh subscriptions");
            return;
        }
        if (saved.getBoolean("radioActive", false)) {
            check(radioStopped(), "A preceding Radio flow must leave playback paused");
            openRadioSettings();
            tap(() -> activity.findViewById(id("radioReturnMusic")));
        }
        verifyRadioMusicBaseline();
        check(!saved.getBoolean("autoPlayOnLaunch", false), "An upgrade must keep autoplay off by default");
        check(network.requests.isEmpty(), "Default startup contacted radio");
        tap(() -> activity.findViewById(id("play")));
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        CountDownLatch streamRelease = new CountDownLatch(1);
        network.manifestRelease = streamRelease;
        long[] switchTiming = {-1, -1};
        android.view.ViewTreeObserver.OnDrawListener switchDraw = () -> {
            View pins = activity.findViewById(id("radioQuickStations"));
            if (switchTiming[0] >= 0 && switchTiming[1] < 0 && pins != null && pins.isShown())
                switchTiming[1] = SystemClock.elapsedRealtime() - switchTiming[0];
        };
        onMain(() -> activity.getWindow().getDecorView().getViewTreeObserver().addOnDrawListener(switchDraw));
        try {
            tap(() -> activity.findViewById(id("switchPlaybackMode")),
                () -> switchTiming[0] = SystemClock.elapsedRealtime());
            await(() -> switchTiming[1] >= 0);
            long panelMs = switchTiming[1];
            check(panelMs < 1000, "The first Music-to-Radio switch held the panel for " + panelMs + " ms");
            saveScreenshot("radio1541-release-pending-switch.png");
            android.util.Log.i("RadioSwitch", "panelMs=" + panelMs);
        } finally {
            onMain(() -> activity.getWindow().getDecorView().getViewTreeObserver().removeOnDrawListener(switchDraw));
            streamRelease.countDown();
            network.manifestRelease = null;
        }
        await(() -> "radio:cnr:zgzs".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        check(network.requests.contains("radio.example/upgrade-b/index.m3u8"),
            "The upgrade lost the selected radio source");
        tap(() -> activity.findViewById(id("nav_player")));
        onMain(() -> {
            View mode = activity.findViewById(id("switchPlaybackMode"));
            ViewGroup row = (ViewGroup) mode.getParent();
            check(row.indexOfChild(mode) + 1 == row.indexOfChild(activity.findViewById(id("favorite"))) &&
                row.indexOfChild(activity.findViewById(id("favorite"))) + 1 == row.indexOfChild(activity.findViewById(id("details"))),
                "The mode button must come before Favorite/Pin and Details");
            int minimum = Math.round(48 * activity.getResources().getDisplayMetrics().density);
            for (String name : new String[] {"favorite", "switchPlaybackMode", "details"}) {
                View button = activity.findViewById(id(name));
                check(button.getWidth() >= minimum && button.getHeight() >= minimum, "Mode-row touch target is too small");
            }
        });
        saveScreenshot("radio143-release-radio-resume.png");
        tap(() -> activity.findViewById(id("switchPlaybackMode")));
        await(() -> "queue-fixture-76".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        check(controller.getPlaybackState().getPosition() >= 20_000, "Mode switch lost the saved music position");
        check(controller.getQueue().size() == 100, "Mode switch changed the saved queue");
        onMain(() -> {
            controller.getTransportControls().pause();
            controller.getTransportControls().seekTo(25_000);
        });
        await(() -> pausedAt(25_000));
        int connections = network.manifests.get();
        tap(() -> activity.findViewById(id("switchPlaybackMode")));
        await(() -> "radio:cnr:zgzs".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        check(network.manifests.get() == connections + 1, "Mode switch opened duplicate radio streams");
        check("https://radio.example/upgrade-b/index.m3u8".equals(
            savedRadioSource(savedRadioPrograms().getJSONObject(0))), "Mode switch changed the source");
        tap(() -> activity.findViewById(id("switchPlaybackMode")));
        await(() -> "queue-fixture-76".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        check(controller.getPlaybackState().getPosition() >= 25_000, "Repeated mode switch lost music position");
        onMain(() -> {
            controller.getTransportControls().pause();
            controller.getTransportControls().seekTo(25_000);
        });
        await(() -> pausedAt(25_000));
        check("OFF".equals(saved.getString("loopMode", "")) &&
            "SEQUENTIAL".equals(saved.getString("playOrder", "")), "Switching changed music playback options");
        saveScreenshot("radio143-release-music-resume.png");
        setStartupPlayback(true);
        check(pausedAt(25_000), "Enabling autoplay must not start playback immediately");
        saveScreenshot("radio143-release-autoplay-setting.png");
    }

    private void setStartupPlayback(boolean enabled) {
        tap(() -> activity.findViewById(id("action_settings")));
        verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        android.widget.CompoundButton option = (android.widget.CompoundButton) activity.findViewById(id("settingsAutoPlay"));
        if (option.isChecked() != enabled) tap(() -> option);
        await(() -> getTargetContext().getSharedPreferences("settings", 0).getBoolean("autoPlayOnLaunch", false) == enabled);
        // The runner exits immediately; finish the normal async preference write before the next process.
        check(getTargetContext().getSharedPreferences("settings", 0).edit().commit(), "Could not persist the fixture's startup choice");
    }

    private void prepareRadioRefresh(FixtureRadioNetwork network) throws Exception {
        org.json.JSONObject catalog = radioSnapshot();
        org.json.JSONArray subscriptions = catalog.getJSONArray("subscriptions");
        for (int index = 0; index < subscriptions.length(); index++) {
            org.json.JSONObject subscription = subscriptions.getJSONObject(index);
            refreshCaches.put(subscription.getString("id"), subscription.getJSONArray("programs").toString());
            subscription.put("checkedAtMs", 0).put("updatedAtMs", 0);
            if (subscription.optBoolean("enabled", true)) refreshFeedCount++;
            String url = subscription.getString("url");
            network.subscriptionBodies.put(url, "Fixture,https://radio.example/test/index.m3u8");
            network.subscriptionStatuses.put(url, 503);
        }
        check(refreshFeedCount > 0, "The upgrade fixture needs enabled subscriptions");
        writeFixture(new File(getTargetContext().getFilesDir(), "radio-stations.json"), catalog.toString());
    }

    private void verifyRadioRefresh(FixtureRadioNetwork network) throws Exception {
        verifyRadioMusicBaseline();
        android.content.SharedPreferences saved = getTargetContext().getSharedPreferences("settings", 0);
        check(network.connectionAttempts.get() == 0, "Startup fetched a subscription or stream");
        openRadioSettings();
        String[] choices = {"MANUAL", "MONTHLY", "WEEKLY", "DAILY"};
        String expected = radioRefreshResumeOnly ? "WEEKLY" : "MANUAL";
        check(expected.equals(saved.getString("radioRefreshInterval", "MANUAL")), "Refresh choice not retained/defaulted");
        if (radioRefreshResumeOnly) {
            check(saved.getLong("radioAutomaticRefreshAttempt", 0) > 0, "Automatic attempt time was lost");
            SystemClock.sleep(65_000);
            check(network.connectionAttempts.get() == 0, "Fresh foreground session retried a recent automatic attempt");
            verifyRadioMusicBaseline();
            return;
        }
        verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        verifySystemInformation();
        verifySettingsPage("categoryRadio", "settingsRadioPanel");
        for (int index = 0; index < choices.length; index++) {
            final int choice = index;
            tap(() -> activity.findViewById(id("radioRefreshInterval")));
            await(() -> focusedDialogView("playerDialogList") != null);
            onMain(() -> {
                android.widget.ListView list = (android.widget.ListView) focusedDialogView("playerDialogList");
                int current = java.util.Arrays.asList(choices).indexOf(saved.getString("radioRefreshInterval", "MANUAL"));
                check(list.getCheckedItemPosition() == current, "The saved refresh interval is not checked");
                if (!list.isInTouchMode()) {
                    check(list.getSelectedItemPosition() == current, "Keyboard entry must start on the saved interval");
                }
            });
            setInTouchMode(true);
            await(() -> focusedDialogView("playerDialogList").isInTouchMode());
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            waitForIdleSync();
            onMain(() -> {
                android.widget.ListView list = (android.widget.ListView) focusedDialogView("playerDialogList");
                check(list.getCount() == 4, "Expected Manual/Monthly/Weekly/Daily choices");
                int current = java.util.Arrays.asList(choices).indexOf(saved.getString("radioRefreshInterval", "MANUAL"));
                check(list.getCheckedItemPosition() == current && list.getSelectedItemPosition() == current,
                    "Remote focus must start on the selected refresh interval: expected " + current +
                        ", checked " + list.getCheckedItemPosition() + ", focused " + list.getSelectedItemPosition());
                list.performItemClick(list.getChildAt(choice - list.getFirstVisiblePosition()),
                    choice, list.getAdapter().getItemId(choice));
            });
            await(() -> ownedPanel() == null && choices[choice].equals(saved.getString("radioRefreshInterval", "")));
        }
        saveScreenshot("radio151-release-refresh-daily.png");
        SystemClock.sleep(55_000);
        check(network.subscriptions.get() == 0, "Automatic refresh started during the startup grace period");
        await(() -> network.subscriptions.get() == refreshFeedCount &&
            !activity.findViewById(id("radioSubscriptionsStatus")).isShown());
        check(network.maxSubscriptionInFlight.get() == 1, "Automatic subscriptions overlapped");
        check(saved.getLong("radioAutomaticRefreshAttempt", 0) > 0, "Automatic refresh was not throttled persistently");
        org.json.JSONArray after = radioSnapshot().getJSONArray("subscriptions");
        for (int index = 0; index < after.length(); index++) {
            org.json.JSONObject subscription = after.getJSONObject(index);
            check(refreshCaches.get(subscription.getString("id")).equals(subscription.getJSONArray("programs").toString()),
                "A failed refresh discarded the last-good cache");
            if (subscription.optBoolean("enabled", true)) {
                check(subscription.getLong("checkedAtMs") > 0 &&
                    subscription.getJSONObject("failure").getInt("httpStatus") == 503, "Automatic failure not reported");
            }
        }
        verifyRadioMusicBaseline();
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioRefreshInterval")));
        await(() -> focusedDialogView("playerDialogList") != null);
        onMain(() -> {
            android.widget.ListView list = (android.widget.ListView) focusedDialogView("playerDialogList");
            list.performItemClick(list.getChildAt(2 - list.getFirstVisiblePosition()), 2, list.getAdapter().getItemId(2));
        });
        await(() -> "WEEKLY".equals(saved.getString("radioRefreshInterval", "")));
        check(saved.edit().commit(), "Could not persist refresh choice for the restart check");
    }

    private void verifyRadioPolish(FixtureRadioNetwork network) throws Exception {
        verifyRadioMusicBaseline();
        android.content.SharedPreferences saved = getTargetContext().getSharedPreferences("settings", 0);
        boolean originalEnabled = saved.getBoolean("controlBlocks", true);
        String originalStyle = saved.getString("blockStyle", "SOLID");
        String catalog = radioSnapshot().toString();
        String groupTitle = activity.getString(resource("radio_group_cctv_television", "string"));
        String[] styles = {"SOLID", "ARC", "DASHED", "DASHED_ARC", "CHARACTERS"};
        for (int selection : new int[] {3, 4, -1}) {
            openRadioSettings();
            verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
            boolean enabled = selection >= 0;
            onMain(() -> {
                ((CompoundButton) activity.findViewById(id("settingsControlBlocks"))).setChecked(enabled);
                if (enabled) ((Spinner) activity.findViewById(id("settingsBlockStyle"))).setSelection(selection);
            });
            await(() -> saved.getBoolean("controlBlocks", !enabled) == enabled &&
                (!enabled || styles[selection].equals(saved.getString("blockStyle", ""))));
            tap(() -> activity.findViewById(id("nav_radio")));
            onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("CCTV"));
            await(() -> {
                radioGrid("radioList").scrollBy(0, 100000);
                return descriptionView(radioGrid("radioList"), groupTitle) != null &&
                    !radioGrid("radioList").isLayoutRequested();
            });
            View reference = activity.findViewById(id("radioSettings"));
            onMain(() -> {
                View search = activity.findViewById(id("radioSearch"));
                Rect input = bounds(search), button = bounds(reference);
                if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
                    check(input.top == button.top && input.bottom == button.bottom,
                        "Radio search and Settings visible bounds differ: " + input + " vs " + button);
                else check(button.top >= input.bottom, "Portrait Radio settings overlaps search");
                assertMatchingOutline(reference, descriptionView(radioGrid("radioList"), groupTitle), enabled);
            });
            expandRadioGroup("radio_group_cctv_television");
            await(() -> {
                ViewGroup grid = radioGrid("radioList");
                View station = descriptionView(grid, "CCTV-1 综合");
                if (station == null) grid.scrollBy(0, (int) (48 * grid.getResources().getDisplayMetrics().density));
                return station != null && !grid.isLayoutRequested();
            });
            onMain(() -> assertMatchingOutline(reference,
                descriptionView(radioGrid("radioList"), "CCTV-1 综合"), enabled));
            saveScreenshot("update1501-release-cctv-" + (enabled ? styles[selection] : "disabled") + ".png");
            onMain(() -> radioGrid("radioList").scrollBy(0, 100000));
            await(() -> activity.findViewById(id("radioStickyGroup")).isShown() &&
                !radioGrid("radioList").isLayoutRequested());
            onMain(() -> {
                View sticky = activity.findViewById(id("radioStickyGroup"));
                check(groupTitle.contentEquals(((TextView) sticky).getText()), "Incorrect sticky CCTV category");
                assertMatchingOutline(reference, sticky, enabled);
                ViewGroup grid = radioGrid("radioList");
                assertMatchingOutline(reference, grid.getChildAt(grid.getChildCount() - 1), enabled);
            });
            saveScreenshot("update1501-release-sticky-" + (enabled ? styles[selection] : "disabled") + ".png");
            tap(() -> activity.findViewById(id("radioStickyGroup")));
            await(() -> descriptionView(radioGrid("radioList"), groupTitle) != null);
            openRadioSettings();
            tap(() -> activity.findViewById(id("radioSubscriptions")));
            await(() -> subscriptionRow(0) != null && subscriptionRow(0).getWidth() > 0);
            onMain(() -> {
                ViewGroup rows = (ViewGroup) focusedDialogView("radioSubscriptionRows");
                check(rows.getChildCount() == savedSubscriptions().length(), "Missing cached subscription buttons");
                for (int index = 0; index < rows.getChildCount(); index++)
                    assertMatchingOutline(reference, rows.getChildAt(index), enabled);
            });
            saveScreenshot("update1501-release-subscriptions-" + (enabled ? styles[selection] : "disabled") + ".png");
            tap(() -> dialogButton(activity.getString(resource("close", "string"))));
        }
        verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        onMain(() -> {
            ((CompoundButton) activity.findViewById(id("settingsControlBlocks"))).setChecked(true);
            int selected = java.util.Arrays.asList(styles).indexOf(originalStyle);
            check(selected >= 0, "Unknown saved outline: " + originalStyle);
            ((Spinner) activity.findViewById(id("settingsBlockStyle"))).setSelection(selected);
        });
        await(() -> originalStyle.equals(saved.getString("blockStyle", "")));
        onMain(() -> ((CompoundButton) activity.findViewById(id("settingsControlBlocks"))).setChecked(originalEnabled));
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText(""));
        tap(() -> activity.findViewById(id("nav_player")));
        verifyRadioMusicBaseline();
        check(catalog.equals(radioSnapshot().toString()), "Styling or CCTV regrouping rewrote the saved catalog");
        check(saved.getBoolean("controlBlocks", !originalEnabled) == originalEnabled &&
            originalStyle.equals(saved.getString("blockStyle", "")), "Saved outline was not restored");
        check(network.requests.isEmpty(), "Browsing fetched a subscription, stream or logo");
    }

    private void assertMatchingOutline(View reference, View target, boolean enabled) {
        check(target != null && target.getWidth() > 0, "Missing styled radio control");
        android.graphics.Bitmap expected = renderOutline(reference);
        android.graphics.Bitmap actual = renderOutline(target);
        try {
            boolean painted = false;
            for (int y = 0; y < expected.getHeight(); y++) for (int x = 0; x < expected.getWidth(); x++)
                painted |= android.graphics.Color.alpha(expected.getPixel(x, y)) != 0;
            check(painted == enabled, "Reference control outline does not match the General setting");
            check(expected.sameAs(actual), "Radio control does not share the General outline: " + target);
        } finally {
            expected.recycle();
            actual.recycle();
        }
    }

    private android.graphics.Bitmap renderOutline(View view) {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(256, 96, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.drawable.Drawable foreground = view.getForeground();
        if (foreground != null) {
            Rect previousBounds = new Rect(foreground.getBounds());
            int[] previousState = foreground.getState().clone();
            try {
                foreground.setState(new int[] {android.R.attr.state_enabled});
                foreground.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                foreground.draw(new android.graphics.Canvas(bitmap));
            } finally {
                foreground.setBounds(previousBounds);
                foreground.setState(previousState);
            }
        }
        return bitmap;
    }

    private void verifyRadioThemes(FixtureRadioNetwork network) throws Exception {
        verifyRadioMusicBaseline();
        android.content.SharedPreferences saved = getTargetContext().getSharedPreferences("settings", 0);
        org.json.JSONObject before = radioSnapshot();
        String previousPins = saved.getString("radioPins", "");
        String previousLogos = saved.getString("radioStationLogos", "{}");
        java.util.Set<String> previousHidden = new java.util.HashSet<>(
            saved.getStringSet("radioHiddenGroups", java.util.Collections.emptySet()));
        if (gradientUpgradeOnly || uiContinuityOnly) {
            check("OCEAN".equals(saved.getString("buttonIcons", "")), "Upgrade overwrote the selected old palette");
            verifySavedSpectrum(uiContinuityOnly);
        }
        check(network.requests.isEmpty(), "Theme startup fetched radio data");
        tap(() -> activity.findViewById(id("nav_radio")));
        await(() -> saved.getInt("radioDefaultsApplied", 0) == 2 && new File(getTargetContext().getFilesDir(), "radio-stations.json").isFile());
        check(radioSnapshot().getInt("bundledRevision") == 2, "Bundled defaults were not persisted");
        check(saved.getString("radioPins", "").startsWith("radio:cctv:13\n"), "CCTV-13 is not the first pin");
        check(!saved.getString("radioPins", "").contains("radio:cnr:zgzs"), "Upgrade re-pinned a previously unpinned station");
        check(savedRadioPrograms().length() == 1 &&
            "Upgrade 134 CNR".equals(savedRadioPrograms().getJSONObject(0).getString("name")),
            "Upgrade lost the manually edited station");
        java.util.Set<String> hidden = saved.getStringSet("radioHiddenGroups", java.util.Collections.emptySet());
        for (String visible : new String[] {"united states", "united kingdom", "singapore", "national", "china",
            "beijing", "sichuan", "taiwan", "hong kong", "macao", "guangdong"})
            check(!hidden.contains(visible), "Requested visible group is hidden: " + visible);
        for (String excluded : new String[] {"country:de", "country:fr", "zhejiang", "other"})
            check(hidden.contains(excluded), "Other countries/regions should start hidden: " + excluded);
        org.json.JSONArray subscriptions = radioSnapshot().getJSONArray("subscriptions");
        check(subscriptions.length() == 4, "The four bundled subscriptions were not installed");
        if (before.optInt("bundledRevision") == 1) {
            org.json.JSONArray previous = before.getJSONArray("subscriptions");
            check(subscriptions.length() == previous.length() + 1, "Upgrade resurrected an older subscription");
            for (int i = 0; i < previous.length(); i++)
                check(previous.getJSONObject(i).toString().equals(subscriptions.getJSONObject(i).toString()),
                    "Upgrade changed an existing subscription or its cache");
            check(previousPins.equals(saved.getString("radioPins", "")), "CCTV upgrade reordered pins");
            check(previousLogos.equals(saved.getString("radioStationLogos", "{}")), "CCTV upgrade changed logos");
            previousHidden.remove("national");
            check(previousHidden.equals(hidden), "CCTV upgrade reset unrelated country visibility");
            check(before.getJSONObject("sourceSelections").toString().equals(
                radioSnapshot().getJSONObject("sourceSelections").toString()), "CCTV upgrade changed selected sources");
        }
        boolean cctvFound = false;
        for (int i = 0; i < subscriptions.length(); i++) {
            org.json.JSONObject subscription = subscriptions.getJSONObject(i);
            check(subscription.getLong("checkedAtMs") > 0 && subscription.getJSONArray("programs").length() > 0,
                "A bundled list has no refreshed cache");
            check(subscription.getBoolean("enabled") == !(radioThemesResumeOnly && i == 1),
                "Subscription enabled state was not preserved");
            if (subscription.getString("url").equals(
                "https://raw.githubusercontent.com/Kimentanm/aptv/master/m3u/radio.m3u")) {
                cctvFound = true;
                org.json.JSONArray channels = subscription.getJSONArray("programs");
                check(channels.length() == 20, "The optimized catalog must contain all twenty CCTV audio channels");
                for (int channel = 0; channel < channels.length(); channel++) {
                    org.json.JSONObject station = channels.getJSONObject(channel);
                    check((station.getString("group").equals("National") || station.getString("group").equals("CCTV Television")) &&
                        station.getString("uri").contains("/audio/cctv") && station.getString("logoUri").isEmpty(),
                        "CCTV preset contains a non-audio, hidden-group or remote-logo entry");
                }
            }
        }
        check(cctvFound, "The CCTV audio subscription is missing from the optimized catalog");
        org.json.JSONObject marks = new org.json.JSONObject(saved.getString("radioStationLogos", "{}"));
        check((radioThemesResumeOnly ? "JAZZ" : "CCTV").equals(marks.optString("radio:cctv:13")),
            "CCTV station mark was not preserved");
        check(network.requests.isEmpty(), "Browsing bundled defaults contacted the network");
        if (radioThemesResumeOnly) {
            check("COPILOT_SUNRISE".equals(saved.getString("buttonIcons", "")), "Palette was not restored");
            verifySavedSpectrum(uiContinuityResumeOnly);
            tap(() -> activity.findViewById(id("nav_player")));
            verifyRenderedControl("play", false);
            saveScreenshot("radio144-release-restored.png");
            return;
        }
        saveScreenshot("radio144-release-defaults.png");
        openRadioSettings();
        verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        onMain(() -> {
            View playlists = activity.findViewById(id("settingsShowPlaylists"));
            View radio = activity.findViewById(id("settingsRadio"));
            check(playlists.getParent() == radio.getParent() && radio.getTop() >= playlists.getBottom(),
                "The General radio switch must follow Playlists");
        });
        String[] choices = {"iconsMonochrome", "iconsColorful", "iconsOcean", "iconsAurora", "iconsSunset", "iconsOrchid",
            "iconsLogo", "iconsCopilotAurora", "iconsCopilotSunrise"};
        String[] values = {"MONOCHROME", "COLORFUL", "OCEAN", "AURORA", "SUNSET", "ORCHID",
            "LOGO", "COPILOT_AURORA", "COPILOT_SUNRISE"};
        for (int i = 0; i < choices.length; i++) {
            final String choice = choices[i], value = values[i];
            tap(() -> activity.findViewById(id("settingsButtonIcons")));
            if (i == 0) {
                verifyNineThemeTiles();
                saveScreenshot("radio147-release-palette-picker.png");
            }
            tap(() -> focusedDialogView(choice));
            await(() -> value.equals(saved.getString("buttonIcons", "MONOCHROME")));
            tap(() -> activity.findViewById(id("nav_player")));
            if (i != 0) verifyRenderedControl("play", false);
            openRadioSettings();
            verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        }
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("CCTV"));
        String cctv = activity.getString(resource("radio_cctv_13", "string"));
        tap(() -> descriptionView(radioGrid("radioList"), cctv));
        await(() -> "radio:cctv:13".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        tap(() -> activity.findViewById(id("nav_radio")));
        int connections = network.manifests.get();
        onMain(() -> descriptionView(radioGrid("radioList"), cctv).performLongClick());
        tap(() -> windowText(activity.getString(resource("radio_station_logo", "string"))));
        await(() -> windowText(activity.getString(resource("radio_mark_news", "string"))) != null);
        View[] jazz = new View[1];
        onMain(() -> {
            ViewGroup choicesGrid = (ViewGroup) windowText(activity.getString(resource("radio_mark_news", "string"))).getParent();
            check(choicesGrid.getChildCount() == 12, "The fixed picker must have exactly twelve marks");
            for (int i = 0; i < choicesGrid.getChildCount(); i++)
                check(((TextView) choicesGrid.getChildAt(i)).getCompoundDrawablesRelative()[1] != null,
                    "A fixed mark failed to load through the app's compatible drawable path");
            jazz[0] = textView(choicesGrid, activity.getString(resource("radio_mark_jazz", "string")));
            check(jazz[0] != null, "Jazz mark is missing");
        });
        saveScreenshot("radio144-release-mark-picker.png");
        tap(() -> jazz[0]);
        await(() -> {
            try { return "JAZZ".equals(new org.json.JSONObject(saved.getString("radioStationLogos", "{}")).optString("radio:cctv:13")); }
            catch (org.json.JSONException error) { throw new AssertionError(error); }
        });
        check(connections == network.manifests.get() &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING, "Changing a logo restarted audio");
        tap(() -> activity.findViewById(id("nav_player")));
        verifyRenderedControl("shuffle", true);
        verifyRenderedControl("repeat", true);
        saveScreenshot("radio144-release-player.png");
        openRadioSettings();
        verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        tap(() -> activity.findViewById(id("settingsRadio")));
        await(() -> !saved.getBoolean("radioEnabled", true) &&
            activity.findViewById(id("nav_radio")).getVisibility() == View.GONE);
        check("radio:cctv:13".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING &&
            connections == network.manifests.get(), "Hiding Radio interrupted the broadcast");
        tap(() -> activity.findViewById(id("settingsRadio")));
        await(() -> saved.getBoolean("radioEnabled", false));
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioReturnMusic")));
        verifyRadioMusicBaseline();
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioSubscriptions")));
        onMain(() -> check(windowText("How subscriptions work") == null && windowText("订阅使用说明") == null,
            "The removed explanation button is still in the subscription window"));
        String usId = subscriptions.getJSONObject(1).getString("id");
        tap(() -> ((ViewGroup) focusedDialogView("radioSubscriptionRows")).findViewWithTag(usId));
        tap(() -> windowText(activity.getString(resource("radio_subscription_disable", "string"))));
        await(() -> !radioSnapshot().optJSONArray("subscriptions").optJSONObject(1).optBoolean("enabled"));
        clickDialogAction("close");
        check(network.subscriptions.get() == 0, "Opening or disabling bundled lists downloaded a feed");
        verifyRadioMusicBaseline();
    }

    private void verifySavedSpectrum(boolean defaults) {
        android.content.SharedPreferences saved = getTargetContext().getSharedPreferences("settings", 0);
        check(saved.contains("spectrumEnabled") && saved.getBoolean("spectrumEnabled", !defaults) == defaults,
            "Spectrum enabled preference changed");
        check((defaults ? "LEFT" : "RIGHT").equals(saved.getString("spectrumPosition", "")),
            "Spectrum position preference changed");
        check((defaults ? "FIRE" : "CLASSIC").equals(saved.getString("spectrumPalette", "")),
            "Spectrum palette preference changed");
        check(saved.getInt("spectrumBands", 0) == (defaults ? 8 : 32), "Spectrum band preference changed");
        check(saved.getInt("spectrumRefreshRate", 0) == 15, "Spectrum refresh must remain 15fps");
        check("BARS".equals(saved.getString("spectrumStyle", "")), "Spectrum display changed");
        check("BOTTOM".equals(saved.getString("spectrumOrigin", "")), "Spectrum origin changed");
        check("LED".equals(saved.getString("spectrumFireStyle", "")), "Fire rendering changed");
        check((defaults ? "LED" : "SMOOTH").equals(saved.getString("spectrumBarStyle", "")),
            "Classic rendering changed");
        check(saved.getInt("fireColorLow", -1) == (defaults ? 0x3dcf74 : 0xffdf69) &&
            saved.getInt("fireColorMiddle", -1) == (defaults ? 0xfcd34d : 0xff9828) &&
            saved.getInt("fireColorHigh", -1) == (defaults ? 0xf04c44 : 0xed4529), "Spectrum colors changed");
    }

    private void verifyUiContinuity(FixtureRadioNetwork network) throws Exception {
        android.content.SharedPreferences saved = getTargetContext().getSharedPreferences("settings", 0);
        openRadioSettings();
        verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        tap(() -> activity.findViewById(id("settingsButtonIcons")));
        verifyNineThemeTiles();
        saveScreenshot("radio148-release-palettes.png");
        tap(() -> focusedDialogView("iconsCopilotAurora"));
        await(() -> "COPILOT_AURORA".equals(saved.getString("buttonIcons", "")));
        tap(() -> activity.findViewById(id("settingsButtonIcons")));
        tap(() -> focusedDialogView("iconsCopilotSunrise"));
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText(""));
        tap(() -> radioGrid("radioList").findViewWithTag("radio:cctv:13"));
        await(() -> "radio:cctv:13".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        int connections = network.manifests.get();
        String pins = saved.getString("radioPins", "");
        for (String grid : new String[] {"radioList", "radioQuickStations"}) {
            tap(() -> activity.findViewById(id(grid.equals("radioList") ? "nav_radio" : "nav_player")));
            for (String input : new String[] {"touch", "mouse", "enter"}) {
                verifyHeldRadioMenu(() -> radioGrid(grid).findViewWithTag("radio:cnr:jjzs"), input);
                check(connections == network.manifests.get() && "radio:cctv:13".equals(mediaId()) &&
                    controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING,
                    "Opening or releasing the held menu restarted playback");
                check(pins.equals(saved.getString("radioPins", "")), "Holding a stationary tile changed its position");
            }
        }
        dragRadioPin(() -> radioGrid("radioQuickStations").findViewWithTag("radio:cnr:jjzs"),
            () -> radioGrid("radioQuickStations").findViewWithTag("radio:cctv:13"));
        await(() -> !pins.equals(saved.getString("radioPins", "")));
        dragRadioPin(() -> radioGrid("radioQuickStations").findViewWithTag("radio:cnr:jjzs"),
            () -> radioGrid("radioQuickStations").findViewWithTag("radio:cctv:13"));
        await(() -> pins.equals(saved.getString("radioPins", "")));

        openRadioSettings();
        verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        tap(() -> activity.findViewById(id("settingsTheme")));
        tap(() -> windowText(activity.getString(resource("theme_day", "string"))));
        await(() -> activity.hasWindowFocus() &&
            (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_NO);
        Activity original = activity;
        View decor = original.getWindow().getDecorView();
        android.os.IBinder token = decor.getWindowToken();
        AtomicInteger detachments = new AtomicInteger();
        AtomicInteger interruptions = new AtomicInteger();
        View.OnAttachStateChangeListener attachment = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {}
            @Override public void onViewDetachedFromWindow(View view) { detachments.incrementAndGet(); }
        };
        MediaController.Callback playback = new MediaController.Callback() {
            @Override public void onPlaybackStateChanged(PlaybackState state) {
                if (state != null && state.getState() != PlaybackState.STATE_PLAYING) interruptions.incrementAndGet();
            }
        };
        onMain(() -> { decor.addOnAttachStateChangeListener(attachment); controller.registerCallback(playback); });
        int frames = 0;
        try {
            for (String language : new String[] {"zh", "en", "zh"}) {
                int blackFrames = 0;
                tap(() -> activity.findViewById(id("settingsLanguage")));
                onMain(() -> {
                    android.widget.ListView choices = (android.widget.ListView) focusedDialogView("playerDialogList");
                    int index = language.equals("zh") ? 1 : 0;
                    android.util.Log.d("UiContinuity", "request=" + language + " activity=" +
                        System.identityHashCode(activity) + " config=" + activity.getResources().getConfiguration());
                    decor.postDelayed(() -> choices.performItemClick(choices.getChildAt(index), index,
                        choices.getAdapter().getItemId(index)), 100);
                });
                for (int frame = 0; frame < 12; frame++) {
                    android.graphics.Bitmap image = getUiAutomation().takeScreenshot();
                    check(image != null, "Could not capture the language-transition frame");
                    try {
                        int visible = 0, sampled = 0;
                        for (int y = image.getHeight() / 8; y < image.getHeight() * 7 / 8; y += 12)
                            for (int x = image.getWidth() / 8; x < image.getWidth() * 7 / 8; x += 12) {
                                int pixel = image.getPixel(x, y);
                                if (Math.max(android.graphics.Color.red(pixel), Math.max(android.graphics.Color.green(pixel),
                                    android.graphics.Color.blue(pixel))) > 24) visible++;
                                sampled++;
                            }
                        if (visible <= sampled / 2) {
                            blackFrames++;
                            File directory = new File(getTargetContext().getExternalFilesDir(null), "test-screenshots");
                            if (!directory.exists()) check(directory.mkdirs(), "Cannot create screenshot directory");
                            try (OutputStream output = new FileOutputStream(new File(directory,
                                "radio148-release-dark-" + frames + ".png"))) {
                                check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output),
                                    "Cannot save the dark compositor frame");
                            }
                            android.util.Log.e("UiContinuity", "dark frame=" + frames + " visible=" + visible + "/" + sampled +
                                " originalDestroyed=" + original.isDestroyed() + " activity=" + System.identityHashCode(activity) +
                                " config=" + activity.getResources().getConfiguration());
                        }
                        frames++;
                    } finally { image.recycle(); }
                    SystemClock.sleep(20);
                }
                await(() -> language.equals(activity.getResources().getConfiguration().locale.getLanguage()) &&
                    activity.hasWindowFocus() && ((TextView) activity.findViewById(id("settingsHeading"))).getText()
                        .toString().equals(activity.getString(resource("category_general", "string"))));
                check(activity == original && !original.isDestroyed() && activity.getWindow().getDecorView() == decor &&
                    decor.getWindowToken() == token && detachments.get() == 0,
                    "Language change replaced the activity/window: original=" + System.identityHashCode(original) +
                        ", current=" + System.identityHashCode(activity) + ", originalDestroyed=" + original.isDestroyed() +
                        ", detachments=" + detachments.get());
                check(blackFrames == 0, "Release compositor frames went black during " + language + ": " + blackFrames);
                check(interruptions.get() == 0 && network.manifests.get() == connections &&
                    controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING, "Language change interrupted audio");
                saveScreenshot("radio148-release-language-" + language + ".png");
            }
        } finally {
            onMain(() -> { decor.removeOnAttachStateChangeListener(attachment); controller.unregisterCallback(playback); });
        }
        File directory = new File(getTargetContext().getExternalFilesDir(null), "test-screenshots");
        writeFixture(new File(directory, "radio148-release-language-frames.json"),
            "{\"frames\":" + frames + ",\"detachments\":" + detachments.get() + ",\"interruptions\":" + interruptions.get() + "}");
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioReturnMusic")));
        verifyRadioMusicBaseline();
        verifySavedSpectrum(true);
        check(saved.edit().commit(), "Could not persist final preferences");
    }

    private void verifyHeldRadioMenu(Target target, String input) throws Exception {
        await(() -> target.get() != null && target.get().isShown() && target.get().hasWindowFocus());
        View[] button = new View[1];
        float[] position = new float[2];
        onMain(() -> {
            button[0] = target.get();
            int[] location = new int[2];
            button[0].getLocationOnScreen(location);
            position[0] = location[0] + button[0].getWidth() / 2f;
            position[1] = location[1] + button[0].getHeight() / 2f;
        });
        boolean keyboard = input.equals("enter");
        if (keyboard) {
            setInTouchMode(false);
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
            await(() -> !button[0].isInTouchMode());
            onMain(() -> check(button[0].requestFocus(), "Cannot focus the pinned button"));
        } else setInTouchMode(true);
        long down = SystemClock.uptimeMillis();
        if (keyboard) sendKeySync(new KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0));
        else sendHeldPointer(input, down, MotionEvent.ACTION_DOWN, position);
        SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout() + 180L);
        View[] menu = new View[1];
        onMain(() -> {
            for (Object candidate : windowRoots()) {
                if (!(candidate instanceof View) || candidate == activity.getWindow().getDecorView()) continue;
                View root = (View) candidate;
                View choices = root.findViewById(id("playerDialogList"));
                if (choices != null && choices.isShown() && choices.isAttachedToWindow()) menu[0] = root;
            }
            check(menu[0] != null && activity.hasWindowFocus(), input + " menu was not visible before release");
            int flags = ((android.view.WindowManager.LayoutParams) menu[0].getLayoutParams()).flags;
            check((flags & android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0 &&
                (flags & android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0,
                "The held menu must not receive the triggering release");
        });
        saveScreenshot("radio148-release-held-" + input + ".png");
        if (keyboard) sendKeySync(new KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0));
        else sendHeldPointer(input, down, MotionEvent.ACTION_UP, position);
        await(() -> focusedDialogView("playerDialogList") != null);
        clickDialogAction("cancel");
    }

    private void sendHeldPointer(String input, long down, int action, float[] position) {
        boolean mouse = input.equals("mouse");
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = mouse ? MotionEvent.TOOL_TYPE_MOUSE : MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords coordinates = new MotionEvent.PointerCoords();
        coordinates.x = position[0]; coordinates.y = position[1];
        coordinates.pressure = action == MotionEvent.ACTION_DOWN ? 1 : 0;
        coordinates.size = 1;
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
            new MotionEvent.PointerProperties[] {properties}, new MotionEvent.PointerCoords[] {coordinates}, 0,
            mouse && action == MotionEvent.ACTION_DOWN ? MotionEvent.BUTTON_PRIMARY : 0, 1, 1, 0, 0,
            mouse ? InputDevice.SOURCE_MOUSE : InputDevice.SOURCE_TOUCHSCREEN, 0);
        try { sendPointerSync(event); } finally { event.recycle(); }
    }

    private void verifyNineThemeTiles() {
        await(() -> {
            View grid = focusedDialogView("appearanceGrid");
            return grid != null && grid.isShown() && grid.getHeight() > 0 && !grid.isLayoutRequested();
        });
        onMain(() -> {
            GridLayout grid = (GridLayout) focusedDialogView("appearanceGrid");
            check(grid.getChildCount() == 9 && grid.getColumnCount() == 3, "Expected nine themes in three columns");
            for (int i = 0; i < grid.getChildCount(); i++) {
                TextView tile = (TextView) grid.getChildAt(i);
                Rect visible = new Rect();
                check(tile.getGlobalVisibleRect(visible) && visible.height() == tile.getHeight(),
                    "A theme tile is clipped in the normal-size window");
                check(tile.getCompoundDrawablesRelative()[1] != null, "A theme preview did not load");
                for (int line = 0; line < tile.getLayout().getLineCount(); line++)
                    check(tile.getLayout().getEllipsisCount(line) == 0, "A theme label was truncated");
            }
        });
    }

    private void verifyGradientDefaults(FixtureRadioNetwork network) throws Exception {
        verifyRadioMusicBaseline();
        android.content.SharedPreferences saved = getTargetContext().getSharedPreferences("settings", 0);
        check(network.requests.isEmpty(), "Fresh appearance defaults requested radio data");
        check(!saved.getBoolean("autoPlayOnLaunch", false), "New defaults enabled autoplay");
        verifyRenderedControl("play", false);
        tap(() -> activity.findViewById(id("action_settings")));
        verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        tap(() -> activity.findViewById(id("settingsButtonIcons")));
        verifyNineThemeTiles();
        onMain(() -> check(((android.widget.Checkable) focusedDialogView("iconsLogo")).isChecked(),
            "Fresh install did not select the Logo gradient"));
        saveScreenshot("radio147-release-new-default-theme.png");
        tap(() -> focusedDialogView("iconsCopilotAurora"));
        tap(() -> activity.findViewById(id("settingsButtonIcons")));
        tap(() -> focusedDialogView("iconsLogo"));
        verifySavedSpectrum(true);
        verifySettingsPage("categorySpectrum", "settingsSpectrumPanel");
        onMain(() -> {
            check(((CompoundButton) activity.findViewById(id("settingsSpectrum"))).isChecked(),
                "Fresh spectrum is not enabled");
            String[][] choices = {{"spectrumPosition", "position_left"}, {"spectrumStyle", "spectrum_bars"},
                {"spectrumPalette", "spectrum_fire"}, {"spectrumFireStyle", "spectrum_led"},
                {"spectrumOrigin", "navigation_bottom"}};
            for (String[] choice : choices)
                check(activity.getString(resource(choice[1], "string")).equals(
                    ((Spinner) activity.findViewById(id(choice[0]))).getSelectedItem().toString()),
                    "Incorrect initial spectrum selector: " + choice[0]);
            check(activity.getString(resource("spectrum_band_count", "string"), 8).equals(
                ((Spinner) activity.findViewById(id("spectrumBands"))).getSelectedItem().toString()), "Expected 8 bands");
            check(activity.getString(resource("spectrum_fps", "string"), 15).equals(
                ((Spinner) activity.findViewById(id("spectrumRefreshRate"))).getSelectedItem().toString()), "Expected 15fps");
        });
        tap(() -> activity.findViewById(id("nav_player")));
        await(() -> activity.findViewById(id("spectrum")).isShown());
        onMain(() -> check(((ViewGroup) activity.findViewById(id("metadataRow"))).getChildAt(0).getId() == id("spectrum"),
            "Default spectrum is not on the left"));
        tap(() -> activity.findViewById(id("play")));
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        long deadline = SystemClock.uptimeMillis() + 25_000;
        while (!spectrumHasDefaultColors()) {
            check(SystemClock.uptimeMillis() < deadline, "The visible spectrum did not draw green, yellow and red");
            SystemClock.sleep(100);
        }
        saveScreenshot("radio147-release-default-spectrum.png");
        onMain(() -> {
            controller.getTransportControls().pause();
            controller.getTransportControls().seekTo(20_000);
        });
        await(() -> pausedAt(20_000));
        verifySavedSpectrum(true);
        check(getTargetContext().getSharedPreferences("settings", 0).edit().commit(), "Could not persist defaults");
    }

    private boolean spectrumHasDefaultColors() {
        Rect bounds = new Rect();
        onMain(() -> {
            View spectrum = activity.findViewById(id("spectrum"));
            if (!spectrum.isShown()) return;
            int[] position = new int[2];
            spectrum.getLocationOnScreen(position);
            bounds.set(position[0], position[1], position[0] + spectrum.getWidth(), position[1] + spectrum.getHeight());
        });
        if (bounds.isEmpty()) return false;
        android.graphics.Bitmap image = getUiAutomation().takeScreenshot();
        check(image != null, "Could not capture the visible spectrum");
        try {
            check(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= image.getWidth() &&
                bounds.bottom <= image.getHeight(), "The spectrum is outside the visible screen");
            boolean green = false, yellow = false, red = false;
            for (int y = bounds.top; y < bounds.bottom; y++) for (int x = bounds.left; x < bounds.right; x++) {
                int color = image.getPixel(x, y);
                int r = android.graphics.Color.red(color), g = android.graphics.Color.green(color),
                    b = android.graphics.Color.blue(color);
                green |= g > r + 30 && g > b + 20;
                yellow |= r > 170 && g > 140 && b < 140;
                red |= r > g + 60 && r > b + 60;
            }
            return green && yellow && red;
        } finally { image.recycle(); }
    }

    private void verifyRadioAppearance(FixtureRadioNetwork network) throws Exception {
        verifyRadioMusicBaseline();
        android.content.SharedPreferences saved = getTargetContext().getSharedPreferences("settings", 0);
        check(network.requests.isEmpty(), "Appearance startup must not fetch radio data");
        if (radioAppearanceResumeOnly) {
            check("RADIO".equals(saved.getString("appLogo", "")), "App logo was not restored");
            check("COLORFUL".equals(saved.getString("buttonIcons", "")), "Icon style was not restored");
            check("CLASSIC_RADIO".equals(saved.getString("radioButtonLogo", "")), "Radio fallback was not restored");
            check("PINNED_ONLY".equals(saved.getString("radioNavigationMode", "")), "Radio navigation was not restored");
            verifyRadioLauncher();
            verifyRenderedControl("play", false);
            openRadioSettings();
            check(network.requests.isEmpty(), "Opening restored settings downloaded logos");
            saveScreenshot("radio142-release-restored-settings.png");
            return;
        }
        openRadioSettings();
        verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        tap(() -> activity.findViewById(id("settingsAppLogo")));
        saveScreenshot("radio142-release-app-logo-choices.png");
        tap(() -> focusedDialogView("appearanceRadio"));
        await(() -> "RADIO".equals(saved.getString("appLogo", "")));
        verifyRadioLauncher();
        tap(() -> activity.findViewById(id("settingsButtonIcons")));
        saveScreenshot("radio142-release-icon-style-choices.png");
        tap(() -> focusedDialogView("iconsColorful"));
        await(() -> "COLORFUL".equals(saved.getString("buttonIcons", "")));
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioButtonLogo")));
        tap(() -> focusedDialogView("radioLogoClassic"));
        await(() -> "CLASSIC_RADIO".equals(saved.getString("radioButtonLogo", "")));
        check(network.requests.isEmpty(), "Appearance selection downloaded radio data");
        tap(() -> activity.findViewById(id("radioClearCache")));
        clickDialogAction("radio_clear_cache");
        await(() -> {
            File[] files = new File(getTargetContext().getFilesDir(), "radio-logos").listFiles();
            return files == null || files.length == 0;
        });
        network.logoStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        network.logoRelease = release;
        try {
            tap(() -> activity.findViewById(id("radioDownloadPinnedLogos")));
            check(network.logoStarted.await(10, TimeUnit.SECONDS), "Pinned-logo action did not fetch a configured image");
            await(() -> activity.findViewById(id("radioLogosProgress")).isShown() &&
                activity.findViewById(id("radioCancelLogos")).isEnabled());
            saveScreenshot("radio142-release-logo-download.png");
        } finally {
            release.countDown();
            network.logoRelease = null;
            network.logoStarted = null;
        }
        await(() -> activity.findViewById(id("radioDownloadPinnedLogos")).isEnabled() &&
            !activity.findViewById(id("radioLogosProgress")).isShown());
        int downloads = network.logos.get();
        check(downloads > 0, "Pinned-logo download did no work");
        File[] cached = new File(getTargetContext().getFilesDir(), "radio-logos").listFiles();
        check(cached != null && cached.length > 0, "Pinned logos were not persisted");
        tap(() -> activity.findViewById(id("radioDownloadPinnedLogos")));
        await(() -> activity.findViewById(id("radioDownloadPinnedLogos")).isEnabled() &&
            !activity.findViewById(id("radioLogosProgress")).isShown());
        check(downloads == network.logos.get(), "Cached logos were downloaded again");

        StringBuilder finite = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n");
        for (int index = 0; index < 30; index++) finite.append("#EXTINF:4.0,\ntone").append(index).append(".aac\n");
        network.customPlaylist = finite.append("#EXT-X-ENDLIST\n").toString();
        tap(() -> activity.findViewById(id("radioNavigation")));
        tapDialogChoice(activity.getString(resource("radio_navigation_global", "string")));
        tap(() -> activity.findViewById(id("nav_radio")));
        String title = activity.getString(resource("radio_cnr_2", "string"));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("jjzs"));
        tap(() -> descriptionView(radioGrid("radioList"), title));
        await(() -> "radio:cnr:jjzs".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        java.util.List<String> pins = java.util.Arrays.asList(saved.getString("radioPins", "").split("\n"));
        check(pins.size() > 1, "The navigation fixture requires multiple pins");
        await(() -> controller.getQueue() != null && controller.getQueue().size() > pins.size());
        String firstVisible = null;
        for (android.media.session.MediaSession.QueueItem item : controller.getQueue()) {
            if (!pins.contains(item.getDescription().getMediaId())) {
                firstVisible = item.getDescription().getMediaId();
                break;
            }
        }
        check(firstVisible != null, "Global navigation must include visible unpinned stations");
        final String firstUnpinned = firstVisible;
        final String firstPin = pins.get(0), lastPin = pins.get(pins.size() - 1);
        int beforeJump = network.manifests.get();
        onMain(() -> {
            for (android.media.session.MediaSession.QueueItem item : controller.getQueue())
                if (lastPin.equals(item.getDescription().getMediaId())) {
                    controller.getTransportControls().skipToQueueItem(item.getQueueId());
                    return;
                }
            throw new AssertionError("Last pin is not in the session queue");
        });
        await(() -> lastPin.equals(mediaId()) && network.manifests.get() > beforeJump &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        int connections = network.manifests.get();
        AtomicInteger interruptions = new AtomicInteger();
        MediaController.Callback callback = new MediaController.Callback() {
            @Override public void onPlaybackStateChanged(PlaybackState state) {
                if (state != null && state.getState() != PlaybackState.STATE_PLAYING) interruptions.incrementAndGet();
            }
        };
        onMain(() -> controller.registerCallback(callback));
        try {
            openRadioSettings();
            tap(() -> activity.findViewById(id("radioNavigation")));
            tapDialogChoice(activity.getString(resource("radio_navigation_pinned", "string")));
            await(() -> controller.getQueue().size() == pins.size());
            check(network.manifests.get() == connections && interruptions.get() == 0,
                "Changing navigation restarted or interrupted playback");
        } finally { onMain(() -> controller.unregisterCallback(callback)); }
        stepRadioStation(network, true, firstPin);
        stepRadioStation(network, false, lastPin);
        connections = network.manifests.get();
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioNavigation")));
        tapDialogChoice(activity.getString(resource("radio_navigation_global", "string")));
        await(() -> controller.getQueue().size() > pins.size());
        check(network.manifests.get() == connections, "Restoring global order reconnected the current source");
        stepRadioStation(network, true, firstUnpinned);
        String unpinnedTitle = controller.getMetadata().getDescription().getTitle().toString();
        tap(() -> activity.findViewById(id("nav_player")));
        verifyRenderedControl("shuffle", true);
        verifyRenderedControl("repeat", true);
        verifyRenderedControl("play", false);
        saveScreenshot("radio142-release-radio-controls.png");
        tap(() -> activity.findViewById(id("nav_radio")));
        await(() -> {
            View row = descriptionView(radioGrid("radioList"), unpinnedTitle);
            Rect visible = new Rect();
            return row != null && row.isSelected() && row.getGlobalVisibleRect(visible) &&
                visible.height() == row.getHeight() &&
                ((EditText) activity.findViewById(id("radioSearch"))).getText().length() == 0;
        });
        saveScreenshot("radio142-release-revealed-station.png");
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioNavigation")));
        tapDialogChoice(activity.getString(resource("radio_navigation_pinned", "string")));
        tap(() -> activity.findViewById(id("radioReturnMusic")));
        verifyRadioMusicBaseline();
    }

    private void stepRadioStation(FixtureRadioNetwork network, boolean next, String expected) {
        int requests = network.manifests.get();
        onMain(() -> {
            if (next) controller.getTransportControls().skipToNext();
            else controller.getTransportControls().skipToPrevious();
        });
        await(() -> expected.equals(mediaId()) && network.manifests.get() > requests &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
    }

    private void verifyRadioLauncher() {
        java.util.List<android.content.pm.ResolveInfo> launchers = getTargetContext().getPackageManager()
            .queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(PACKAGE), 0);
        check(launchers.size() == 1 && (PACKAGE + ".RadioLauncher").equals(launchers.get(0).activityInfo.name),
            "Logo selection must leave exactly one matching launcher entry");
    }

    private void verifyRenderedControl(String name, boolean disabled) {
        onMain(() -> {
            ImageView view = activity.findViewById(id(name));
            check(view.isEnabled() != disabled, name + " has the wrong enabled state");
            android.graphics.drawable.Drawable drawable = view.getDrawable();
            Rect bounds = new Rect(drawable.getBounds());
            android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888);
            try {
                drawable.setBounds(0, 0, 96, 96);
                drawable.draw(new android.graphics.Canvas(image));
                int[] pixels = new int[96 * 96];
                image.getPixels(pixels, 0, 96, 0, 0, 96, 96);
                java.util.Set<Integer> colors = new java.util.HashSet<>();
                int ink = 0;
                for (int pixel : pixels) if (android.graphics.Color.alpha(pixel) > 250) {
                    ink++;
                    colors.add(pixel & 0xffffff);
                    if (disabled) check(android.graphics.Color.red(pixel) == android.graphics.Color.green(pixel) &&
                        android.graphics.Color.green(pixel) == android.graphics.Color.blue(pixel) &&
                        android.graphics.Color.red(pixel) >= 32 && android.graphics.Color.red(pixel) <= 220,
                        name + " must be dimmed grey, not white");
                }
                check(ink > 30, name + " has no visible icon");
                if (!disabled) check(colors.size() > 8, name + " lost its colorful gradient after shrinking");
            } finally { drawable.setBounds(bounds); image.recycle(); }
        });
    }

    private void verifyRadioMetadata(FixtureRadioNetwork network) throws Exception {
        check(network.requests.isEmpty(), "Metadata check startup connected radio");
        if (mediaId() != null && mediaId().startsWith("radio:")) {
            openRadioSettings();
            tap(() -> activity.findViewById(id("radioReturnMusic")));
            await(() -> mediaId() != null && !mediaId().startsWith("radio:"));
        }
        String music = mediaId();
        long musicPosition = controller.getPlaybackState().getPosition();
        StringBuilder finite = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n");
        for (int index = 0; index < 30; index++) finite.append("#EXTINF:4.0,\ntone").append(index).append(".aac\n");
        network.customPlaylist = finite.append("#EXT-X-ENDLIST\n").toString();
        String original = activity.getString(resource("radio_cnr_2", "string"));
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText(original));
        tap(() -> descriptionView(radioGrid("radioList"), original));
        await(() -> "radio:cnr:jjzs".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        int connections = network.manifests.get();
        AtomicInteger interruptions = new AtomicInteger();
        MediaController.Callback listener = new MediaController.Callback() {
            @Override public void onPlaybackStateChanged(PlaybackState state) {
                if (state != null && state.getState() != PlaybackState.STATE_PLAYING) interruptions.incrementAndGet();
            }
        };
        onMain(() -> controller.registerCallback(listener));
        try {
            long position = controller.getPlaybackState().getPosition();
            long buffered = controller.getPlaybackState().getBufferedPosition();
            editRadioName(original, "Renamed live 1.40");
            await(() -> "Renamed live 1.40".contentEquals(controller.getMetadata().getDescription().getTitle()));
            tap(() -> activity.findViewById(id("nav_player")));
            await(() -> "Renamed live 1.40".contentEquals(((TextView) activity.findViewById(id("title"))).getText()));
            check(network.manifests.get() == connections, "Renaming reopened the HLS manifest");
            check(interruptions.get() == 0 && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING,
                "Renaming interrupted playback");
            check(controller.getPlaybackState().getPosition() >= position &&
                controller.getPlaybackState().getBufferedPosition() >= buffered, "Renaming reset the live position or buffer");
            saveScreenshot("radio140-release-rename-playing.png");
            editRadioName("Renamed live 1.40", original);
            await(() -> original.contentEquals(controller.getMetadata().getDescription().getTitle()));
            check(network.manifests.get() == connections && interruptions.get() == 0, "Restoring the name reconnected audio");
        } finally { onMain(() -> controller.unregisterCallback(listener)); }
        onMain(() -> controller.getTransportControls().pause());
        await(() -> radioStopped() && network.active.get() == 0);
        editRadioName(original, "Renamed paused 1.40");
        await(() -> "Renamed paused 1.40".contentEquals(controller.getMetadata().getDescription().getTitle()));
        check(network.manifests.get() == connections && radioStopped(), "Stopped rename connected or autoplayed");
        editRadioName("Renamed paused 1.40", original);
        await(() -> original.contentEquals(controller.getMetadata().getDescription().getTitle()));
        check(network.manifests.get() == connections && radioStopped(), "Restoring a stopped name connected audio");
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioReturnMusic")));
        await(() -> music.equals(mediaId()) && restoredAt(musicPosition));
    }

    private void editRadioName(String from, String to) {
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> {
            ((EditText) activity.findViewById(id("radioSearch"))).setText(from);
            ((android.view.inputmethod.InputMethodManager) activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0);
        });
        await(() -> descriptionView(radioGrid("radioList"), from) != null);
        onMain(() -> descriptionView(radioGrid("radioList"), from).performLongClick());
        tapDialogChoice(activity.getString(resource("radio_edit", "string")));
        await(() -> focusedDialogView("radioEditName") instanceof EditText);
        onMain(() -> ((EditText) focusedDialogView("radioEditName")).setText(to));
        tap(() -> dialogButton(activity.getString(resource("save", "string"))));
        await(() -> focusedDialogView("radioEditName") == null);
    }

    private void verifyRadioReselection(FixtureRadioNetwork network) throws Exception {
        check(network.requests.isEmpty(), "Startup connected radio without a selection");
        check(controller.getPlaybackState().getState() != PlaybackState.STATE_PLAYING, "Startup unexpectedly played audio");
        if (mediaId() != null && mediaId().startsWith("radio:")) {
            openRadioSettings();
            tap(() -> activity.findViewById(id("radioReturnMusic")));
            await(() -> mediaId() != null && !mediaId().startsWith("radio:"));
        }
        String music = mediaId();
        int musicCount = controller.getQueue().size();
        long musicPosition = controller.getPlaybackState().getPosition();
        StringBuilder finite = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n");
        for (int i = 0; i < 30; i++) finite.append("#EXTINF:4.0,\ntone").append(i).append(".aac\n");
        network.customPlaylist = finite.append("#EXT-X-ENDLIST\n").toString();
        String title = activity.getString(resource("radio_cnr_2", "string"));
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("jjzs"));
        tap(() -> descriptionView(radioGrid("radioList"), title));
        await(() -> "radio:cnr:jjzs".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING &&
            activity.findViewById(id("radioQuickStations")) != null &&
            descriptionView(radioGrid("radioQuickStations"), title) != null);
        int connections = network.manifests.get();
        for (int index = 0; index < 3; index++) tap(() -> descriptionView(radioGrid("radioQuickStations"), title));
        SystemClock.sleep(500);
        check(network.manifests.get() == connections, "Current radio quick selection reopened the stream");
        tap(() -> activity.findViewById(id("nav_radio")));
        tap(() -> descriptionView(radioGrid("radioList"), title));
        SystemClock.sleep(300);
        check(network.manifests.get() == connections, "Current radio station button reopened the stream");
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("Upgrade 134 CNR"));
        expandRadioGroup("radio_group_national");
        tap(() -> descriptionView(radioGrid("radioList"), "Upgrade 134 CNR"));
        await(() -> "radio:cnr:zgzs".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        connections = network.manifests.get();
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> descriptionView(radioGrid("radioList"), "Upgrade 134 CNR").performLongClick());
        chooseRadioOption(2);
        int sourceIndex = savedRadioPrograms().getJSONObject(0).optInt("sourceIndex", 0);
        String sourceUri = "https://radio.example/upgrade-" + (sourceIndex == 0 ? "a" : "b") + "/index.m3u8";
        String selectedSource = activity.getString(resource("radio_source_item", "string"), sourceIndex + 1, sourceUri);
        tap(() -> windowText(selectedSource));
        await(() -> ownedPanel() == null &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        SystemClock.sleep(300);
        check(network.manifests.get() == connections, "Selecting the current source reopened the stream");
        tap(() -> activity.findViewById(id("nav_player")));
        tap(() -> activity.findViewById(id(sourceIndex == 0 ? "forward" : "rewind")));
        final int previousConnections = connections;
        await(() -> network.manifests.get() == previousConnections + 1 &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        saveScreenshot("reselect1351-radio.png");
        onMain(() -> controller.getTransportControls().pause());
        await(() -> radioStopped() && network.active.get() == 0);
        tap(() -> activity.findViewById(id("nav_radio")));
        tap(() -> descriptionView(radioGrid("radioList"), "Upgrade 134 CNR"));
        await(() -> network.manifests.get() == previousConnections + 2 &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioReturnMusic")));
        await(() -> music.equals(mediaId()) && controller.getQueue().size() == musicCount && restoredAt(musicPosition));
        check(savedLibrary().getJSONArray("tracks").length() == 100, "Radio selection changed local music");
    }

    private void profileUiRefresh(Bundle result) throws Exception {
        verifyRadioMusicBaseline();
        tap(() -> activity.findViewById(id("nav_player")));
        await(() -> activity.findViewById(id("play")).isEnabled());
        java.lang.reflect.Method refresh = activity.getClass().getDeclaredMethod(uiRefreshMethod);
        refresh.setAccessible(true);
        Object[] noArguments = new Object[0];
        Runnable update = () -> {
            try { refresh.invoke(activity, noArguments); }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        };
        org.json.JSONArray samples = new org.json.JSONArray();
        onMain(() -> {
            for (int index = 0; index < 20; index++) update.run();
            java.util.List<TextView> labels = new java.util.ArrayList<>();
            java.util.List<String> original = new java.util.ArrayList<>();
            for (String name : new String[] {"title", "artist", "album", "miniTitle", "miniArtist",
                "playbackOptions", "favorite", "switchPlaybackMode", "details", "status"}) {
                TextView label = activity.findViewById(id(name));
                check(label != null, "Missing refresh label " + name);
                labels.add(label);
                original.add(label.getText().toString());
            }
            AtomicInteger edits = new AtomicInteger();
            android.text.TextWatcher watcher = new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {
                    edits.incrementAndGet();
                }
                @Override public void onTextChanged(CharSequence value, int start, int before, int count) {}
                @Override public void afterTextChanged(android.text.Editable value) {}
            };
            ImageView play = activity.findViewById(id("play"));
            android.graphics.drawable.Drawable playIcon = play.getDrawable();
            for (int sample = 0; sample < 5; sample++) {
                long allocated = Long.parseLong(android.os.Debug.getRuntimeStat("art.gc.bytes-allocated"));
                long started = SystemClock.elapsedRealtimeNanos();
                for (int iteration = 0; iteration < 100; iteration++) update.run();
                long elapsed = SystemClock.elapsedRealtimeNanos() - started;
                allocated = Long.parseLong(android.os.Debug.getRuntimeStat("art.gc.bytes-allocated")) - allocated;
                check(playIcon == play.getDrawable(), "Repeated refresh replaced the play icon");
                for (int index = 0; index < labels.size(); index++)
                    check(original.get(index).contentEquals(labels.get(index).getText()), "Refresh changed unchanged metadata");
                try {
                    samples.put(new org.json.JSONObject().put("iterations", 100)
                        .put("allocatedBytes", allocated).put("elapsedNanos", elapsed));
                } catch (org.json.JSONException error) { throw new AssertionError(error); }
            }
            // TextWatchers can force Editable buffers, so attach them only after allocation sampling.
            for (TextView label : labels) label.addTextChangedListener(watcher);
            try {
                for (int iteration = 0; iteration < 100; iteration++) update.run();
                check(uiRefreshBaseline ? edits.get() == 1000 : edits.get() == 0,
                    "Unexpected redundant label writes: " + edits.get());
                result.putInt("uiRefreshTextWritesPer100", edits.get());
            } finally { for (TextView label : labels) label.removeTextChangedListener(watcher); }
        });
        verifyRadioMusicBaseline();
        result.putString("uiRefreshProfile", samples.toString());
    }

    private void verifyRadioMusicBaseline() throws Exception {
        await(() -> controller.getQueue() != null && controller.getQueue().size() == 100 &&
            "queue-fixture-76".equals(mediaId()) && restoredAt(20_000));
        org.json.JSONObject library = savedLibrary();
        check(library.getJSONArray("tracks").length() == 100, "Music library was lost during update");
        check(library.getJSONArray("sources").length() == 1, "Saved folder was lost during update");
        check(library.getJSONArray("playlists").getJSONObject(0).getJSONArray("trackIds").length() == 100,
            "Local playlist was lost during update");
        check("OFF".equals(getTargetContext().getSharedPreferences("settings", 0).getString("loopMode", "")),
            "Music playback preferences were lost during update");
    }

    private String mediaId() {
        return controller.getMetadata() == null ? null : controller.getMetadata().getDescription().getMediaId();
    }

    private boolean radioStopped() {
        PlaybackState state = controller.getPlaybackState();
        return state != null && (state.getState() == PlaybackState.STATE_STOPPED ||
            state.getState() == PlaybackState.STATE_NONE || state.getState() == PlaybackState.STATE_PAUSED);
    }

    private void verifyRadio(FixtureRadioNetwork network) throws Exception {
        if (radioResumeOnly) {
            await(() -> "radio:cnr:zgzs".equals(mediaId()) && radioStopped());
            SystemClock.sleep(1200);
            check(network.requests.isEmpty(), "Restored radio connected without Play");
            android.content.SharedPreferences saved = getTargetContext().getSharedPreferences("settings", 0);
            check(saved.getString("radioPins", "").contains("radio:cnr:zgzs"),
                "Radio pins were lost across process restart");
            check(saved.getInt("radioPinColor", -1) == 0x13579b, "Custom pin color was lost across process restart");
            openRadioSettings();
            tap(() -> activity.findViewById(id("radioPinColor")));
            await(() -> colorDialog() != null);
            tap(() -> colorDialog().findViewById(android.R.id.button3));
            await(() -> colorDialog() == null && saved.getInt("radioPinColor", -1) == -1);
            tap(() -> activity.findViewById(id("radioReturnMusic")));
            verifyRadioMusicBaseline();
            return;
        }
        verifyRadioMusicBaseline();
        check(network.requests.isEmpty(), "Updating or launching caused a radio connection");
        check("ManaMusicRadio".equals(activity.getString(resource("app_name", "string"))), "New English branding missing");
        verifyRadioUpgrade(network);
        verifyRadioPinColor(network);
        tap(() -> activity.findViewById(id("nav_radio")));
        await(() -> activity.findViewById(id("radioSearch")) != null);
        verifyBundledRadioRows(network);
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("zgzs"));
        await(() -> radioGrid("radioList").findViewWithTag("radio:cnr:zgzs") != null &&
            !radioGrid("radioList").isLayoutRequested());
        check(network.requests.isEmpty(), "Browsing or searching connected to radio");
        saveScreenshot("cnr130-radio-list.png");
        tap(() -> ((ViewGroup) activity.findViewById(id("radioList"))).getChildAt(0));
        await(() -> "radio:cnr:zgzs".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        check(controller.getQueue().size() == 417, "The complete packaged station catalog was not loaded");
        check(network.catalogues.get() == 1 && network.segments.get() > 0, "CNR catalogue/HLS/AAC was not exercised");
        long prohibited = PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_FAST_FORWARD | PlaybackState.ACTION_REWIND;
        check((controller.getPlaybackState().getActions() & prohibited) == 0, "Live radio advertises seeking");
        onMain(() -> {
            for (String name : new String[] {"seek", "rewind", "forward", "shuffle", "repeat"})
                check(!activity.findViewById(id(name)).isEnabled(), "Live-only control remains enabled: " + name);
        });
        await(() -> mediaNotification() != null);
        tap(() -> activity.findViewById(id("favorite")));
        check(!getTargetContext().getSharedPreferences("settings", 0).getString("radioPins", "").contains("radio:cnr:zgzs"),
            "The main action must unpin the default station");
        tap(() -> activity.findViewById(id("favorite")));
        check(getTargetContext().getSharedPreferences("settings", 0).getString("radioPins", "").contains("radio:cnr:zgzs"),
            "The main action must pin the station again");
        tap(() -> activity.findViewById(id("next")));
        await(() -> "radio:cnr:jjzs".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        onMain(() -> controller.getTransportControls().skipToPrevious());
        await(() -> "radio:cnr:zgzs".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        saveScreenshot("cnr130-radio-playing.png");
        getContext().startActivity(new Intent().setClassName(PACKAGE + ".test", PACKAGE + ".FixtureWidgetHostActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(() -> widgetStatus().getInt("count") == 4 &&
            activity.getString(resource("radio_stop", "string")).equals(widgetStatus().getString("play3")));
        check(widgetStatus().getInt("uid") != Process.myUid(), "Widget host must have its own UID");
        check(activity.getString(resource("radio_live", "string")).equals(widgetStatus().getString("time0")),
            "Radio widget must show LIVE instead of a seek position");
        check(!widgetStatus().getBoolean("previousEnabled0"), "First station must not offer Previous");
        widgetCommand("click", "0:widget_next");
        await(() -> "radio:cnr:jjzs".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        await(() -> activity.getString(resource("radio_cnr_2", "string")).equals(widgetStatus().getString("title0")));
        SystemClock.sleep(700);
        int updates = widgetStatus().getInt("updates");
        SystemClock.sleep(1400);
        check(widgetStatus().getInt("updates") == updates, "Live widgets are running a periodic progress ticker");
        saveScreenshot("cnr130-radio-widgets.png");
        long position = controller.getPlaybackState().getPosition();
        String screenOffStation = mediaId();
        int audioRequests = network.segments.get();
        shellRadio("input keyevent 223");
        await(() -> !getTargetContext().getSystemService(android.os.PowerManager.class).isInteractive());
        SystemClock.sleep(4500);
        PlaybackState screenOffState = controller.getPlaybackState();
        android.util.Log.i("RadioRelease", "Screen-off playback=" + screenOffState + "; prior position=" + position);
        // A live position is relative to the sliding HLS window, not a monotonically increasing song clock.
        check(screenOffState.getState() == PlaybackState.STATE_PLAYING && screenOffState.getPlaybackSpeed() > 0 &&
            screenOffStation.equals(mediaId()), "Screen-off radio state: " + screenOffState);
        await(() -> network.segments.get() > audioRequests);
        check(!getTargetContext().getSystemService(android.os.PowerManager.class).isInteractive(),
            "Radio must continue receiving audio before the screen is woken");
        shellRadio("input keyevent 224");
        shellRadio("input keyevent 82");
        widgetCommand("click", "1:widget_play");
        await(() -> radioStopped() && network.active.get() == 0);
        int requests = network.requests.size();
        SystemClock.sleep(1200);
        check(network.requests.size() == requests, "Stop kept receiving radio");
        widgetCommand("click", "3:widget_play");
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        check(network.requests.size() > requests, "Play did not reconnect to live audio");
        widgetCommand("finish", "");
        await(() -> activity.hasWindowFocus());
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioReturnMusic")));
        verifyRadioMusicBaseline();
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("zgzs"));
        await(() -> radioGrid("radioList").findViewWithTag("radio:cnr:zgzs") != null &&
            !radioGrid("radioList").isLayoutRequested());
        tap(() -> ((ViewGroup) activity.findViewById(id("radioList"))).getChildAt(0));
        await(() -> "radio:cnr:zgzs".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        tap(() -> activity.findViewById(id("play")));
        await(() -> radioStopped() && network.active.get() == 0);
        check(savedLibrary().getJSONArray("tracks").length() == 100, "Stations entered the music library");
    }

    private void shellRadio(String command) throws Exception {
        try (InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(
                getUiAutomation().executeShellCommand(command))) {
            byte[] buffer = new byte[512];
            while (input.read(buffer) != -1) { }
        }
    }

    private org.json.JSONArray savedRadioPrograms() {
        File file = new File(getTargetContext().getFilesDir(), "radio-stations.json");
        if (!file.exists()) return new org.json.JSONArray();
        try { return new org.json.JSONObject(fileText(file)).getJSONArray("programs"); }
        catch (org.json.JSONException error) { throw new AssertionError("Invalid saved radio catalog", error); }
    }

    private String savedRadioSource(org.json.JSONObject station) throws org.json.JSONException {
        org.json.JSONObject selections = radioSnapshot().optJSONObject("sourceSelections");
        String stationId = station.getString("id");
        if (selections != null && selections.has(stationId)) return selections.getString(stationId);
        int index = station.optInt("sourceIndex");
        return index == 0 ? station.getString("uri") : station.getJSONArray("alternateUris").getString(index - 1);
    }

    private void openRadioList(String filename) {
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioImport")));
        await(() -> explorer != null && explorer.hasWindowFocus());
        tap(() -> textView(explorer.findViewById(id("explorerList")), "root"));
        tap(() -> textView(explorer.findViewById(id("explorerList")), filename));
        await(() -> dialogButton(activity.getString(resource("radio_import_apply", "string"))) != null);
    }

    private void openRadioSettings() {
        boolean[] showing = {false};
        onMain(() -> showing[0] = activity.findViewById(id("settingsPanel")) != null &&
            activity.findViewById(id("settingsPanel")).isShown());
        if (!showing[0]) tap(() -> activity.findViewById(id("action_settings")));
        verifySettingsPage("categoryRadio", "settingsRadioPanel");
    }

    private void applyRadioList(String mode) {
        tap(() -> dialogButton(activity.getString(resource("radio_import_apply", "string"))));
        clickDialogAction(mode);
        tap(() -> activity.findViewById(id("nav_radio")));
    }

    private org.json.JSONObject radioSnapshot() {
        try { return new org.json.JSONObject(fileText(new File(getTargetContext().getFilesDir(), "radio-stations.json"))); }
        catch (org.json.JSONException error) { throw new AssertionError("Invalid radio snapshot", error); }
    }

    private void waitRadioExchange() {
        openRadioSettings();
        await(() -> activity.findViewById(id("radioExport")).isEnabled() && ownedPanel() == null);
    }

    private void verifyRadioExchange(FixtureRadioNetwork network, Bundle result) throws Exception {
        android.content.SharedPreferences settings = getTargetContext().getSharedPreferences("settings", 0);
        if (radioExchangeResumeOnly) {
            check(!radioSnapshot().getBoolean("includeDefaults") && savedRadioPrograms().length() == 2,
                "Bundled stations returned after a fresh process");
            org.json.JSONObject custom = savedRadioPrograms().getJSONObject(1);
            String customId = custom.getString("id");
            check(savedRadioPrograms().getJSONObject(0).getJSONArray("alternateUris").length() == 1,
                "Replacement source list did not persist");
            await(() -> customId.equals(mediaId()) && radioStopped() && controller.getQueue().size() == 2);
            SystemClock.sleep(500);
            check(network.requests.isEmpty(), "Replacement catalog restoration fetched media");
            check(settings.getString("radioPins", "").equals(customId), "Removed pins returned");
            onMain(() -> controller.getTransportControls().play());
            await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
            check(network.requests.contains("radio.example/custom135/index.m3u8"), "Restored imported source did not play");
            onMain(() -> controller.getTransportControls().pause());
            await(() -> radioStopped() && network.active.get() == 0);
            openRadioSettings();
            tap(() -> activity.findViewById(id("radioReturnMusic")));
            verifyRadioMusicBaseline();
            openRadioSettings();
            tap(() -> activity.findViewById(id("radioReset")));
            clickDialogAction("radio_reset");
            waitRadioExchange();
            check(radioSnapshot().getBoolean("includeDefaults") && savedRadioPrograms().length() == 1,
                "Explicit reset must restore defaults and retain the extra custom station");
            tap(() -> activity.findViewById(id("nav_radio")));
            onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText(""));
            await(() -> textView(activity.getWindow().getDecorView(),
                activity.getString(resource("radio_catalog_hint", "string"), 418)) != null);
            saveScreenshot("radio135-restored-after-reset.png");
            return;
        }
        verifyRadioMusicBaseline();
        check(network.requests.isEmpty(), "Upgrade fetched radio data");
        org.json.JSONObject upgraded = savedRadioPrograms().getJSONObject(0);
        check(savedRadioPrograms().length() == 1 && upgraded.getInt("sourceIndex") == 1 &&
            upgraded.getJSONArray("alternateUris").length() == 1 &&
            "Upgrade 134 CNR".equals(upgraded.getString("name")), "Upgrade lost station edits or selected source");
        check(!settings.getString("radioPins", "").contains("radio:cnr:zgzs"), "Upgrade re-pinned an unpinned station");
        android.content.ContentResolver resolver = getTargetContext().getContentResolver();
        Uri root = DocumentsContract.buildTreeDocumentUri(PACKAGE + ".test.cache", "root");
        resolver.takePersistableUriPermission(root, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        Bundle reset = new Bundle();
        reset.putBoolean("reset", true);
        resolver.call(root, "cacheFixture", null, reset);
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(root, "root");
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("no matching stations"));
        openRadioSettings();
        String before = radioSnapshot().toString();
        tap(() -> activity.findViewById(id("radioExport")));
        await(() -> explorer != null && explorer.hasWindowFocus());
        tap(() -> textView(explorer.findViewById(id("explorerList")), "root"));
        onMain(() -> ((EditText) explorer.findViewById(id("explorerFilename"))).setText("Offline135"));
        saveScreenshot("radio135-export-destination.png");
        tap(() -> explorer.findViewById(id("explorerSelect")));
        await(() -> activity.hasWindowFocus() && activity.findViewById(id("radioExport")).isEnabled());
        Uri output = cacheChild(parent, "Offline135.m3u");
        String exported = documentText(output);
        java.util.regex.Matcher headers = java.util.regex.Pattern.compile("^#EXTINF:", java.util.regex.Pattern.MULTILINE).matcher(exported);
        int count = 0;
        while (headers.find()) count++;
        check(count >= 418 && exported.contains("tvg-id=\"radio:cnr:zgzs\"") &&
            exported.contains("upgrade-a/index.m3u8") && exported.contains("upgrade-b/index.m3u8") &&
            exported.contains("KEXP") && exported.contains("tvg-logo=") && exported.contains("group-title="),
            "Export was partial or lost station metadata and sources");
        result.putInt("exportSourceEntries", count);
        result.putInt("exportBytes", exported.getBytes(StandardCharsets.UTF_8).length);
        check(before.equals(radioSnapshot().toString()) && network.requests.isEmpty(), "Export mutated data or fetched media");
        openRadioList("Offline135.m3u");
        applyRadioList("radio_import_append");
        waitRadioExchange();
        check(before.equals(radioSnapshot().toString()), "Unedited full export introduced redundant overrides");
        String edited = "#EXTM3U\n#EXTINF:-1 tvg-id=\"radio:cnr:zgzs\" group-title=\"Offline edits\" tvg-logo=\"\",Offline edited CNR\n" +
            "https://radio.example/upgrade-b/index.m3u8\n" +
            "#EXTINF:-1 tvg-id=\"radio:cnr:zgzs\" group-title=\"Offline edits\" tvg-logo=\"\",Offline edited CNR\n" +
            "https://radio.example/new135/index.m3u8\n" +
            "#EXTINF:-1 tvg-id=\"radio:cnr:zgzs\" group-title=\"Offline edits\" tvg-logo=\"\",Offline edited CNR\n" +
            "https://RADIO.EXAMPLE:443/new135/index.m3u8\n" +
            "#EXTINF:-1 tvg-id=\"fixture135\" group-title=\"Offline edits\" tvg-logo=\"\",Offline custom\n" +
            "https://radio.example/custom135/index.m3u8\n";
        try (OutputStream stream = resolver.openOutputStream(output, "wt")) { stream.write(edited.getBytes(StandardCharsets.UTF_8)); }
        openRadioList("Offline135.m3u");
        tap(() -> dialogButton(activity.getString(resource("radio_import_apply", "string"))));
        saveScreenshot("radio135-import-choice.png");
        clickDialogAction("cancel");
        check(before.equals(radioSnapshot().toString()), "Cancel changed the radio catalog");
        openRadioList("Offline135.m3u");
        applyRadioList("radio_import_append");
        waitRadioExchange();
        check(savedRadioPrograms().length() == 2 && radioSnapshot().getBoolean("includeDefaults") &&
            savedRadioPrograms().getJSONObject(0).getJSONArray("alternateUris").length() == 2 &&
            savedRadioPrograms().getJSONObject(0).getInt("sourceIndex") == 1,
            "Merge did not retain old sources, deduplicate equivalent URLs or preserve the selection");
        openRadioList("Offline135.m3u");
        applyRadioList("radio_import_replace");
        waitRadioExchange();
        check(savedRadioPrograms().length() == 2 && !radioSnapshot().getBoolean("includeDefaults") &&
            savedRadioPrograms().getJSONObject(0).getJSONArray("alternateUris").length() == 1 &&
            savedRadioPrograms().getJSONObject(0).getInt("sourceIndex") == 0,
            "Replace all kept unlisted stations or old sources");
        check(settings.contains("radioPins") && settings.getString("radioPins", "").isEmpty(),
            "Removed station pins still occupy slots");
        check(edited.equals(documentText(output)) && network.requests.isEmpty(), "Import wrote its input or fetched media");
        verifyRadioMusicBaseline();
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("Offline custom"));
        tap(() -> descriptionView(radioGrid("radioList"), "Offline edits"));
        tap(() -> descriptionView(radioGrid("radioList"), "Offline custom"));
        String customId = savedRadioPrograms().getJSONObject(1).getString("id");
        await(() -> customId.equals(mediaId()) && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        tap(() -> activity.findViewById(id("favorite")));
        before = radioSnapshot().toString();
        openRadioList("Offline135.m3u");
        applyRadioList("radio_import_append");
        waitRadioExchange();
        check(before.equals(radioSnapshot().toString()), "Identical import rewrote the replacement catalog");
        check(customId.equals(mediaId()) && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING,
            "Identical import interrupted radio reception");
        onMain(() -> controller.getTransportControls().pause());
        await(() -> radioStopped() && network.active.get() == 0);
        check(savedLibrary().getJSONArray("tracks").length() == 100, "Radio exchange touched the music library");
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText(""));
        saveScreenshot("radio135-replacement-list.png");
    }

    private void verifyRadioLists(FixtureRadioNetwork network, Bundle result) throws Exception {
        if (radioListResumeOnly) {
            org.json.JSONObject saved = savedRadioPrograms().getJSONObject(0);
            check(savedRadioPrograms().length() == 1, "Reset should retain just the custom entry");
            String stationId = saved.getString("id");
            await(() -> stationId.equals(mediaId()) && radioStopped());
            SystemClock.sleep(900);
            check(network.requests.isEmpty(), "Imported station connected during restoration");
            check(getTargetContext().getSharedPreferences("settings", 0).getString("radioPins", "").contains(stationId),
                "Imported station pin was lost");
            onMain(() -> controller.getTransportControls().play());
            await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
            check(network.catalogues.get() == 0 && network.segments.get() > 0, "Restored custom HLS did not play");
            onMain(() -> controller.getTransportControls().pause());
            await(() -> radioStopped() && network.active.get() == 0);
            openRadioSettings();
            tap(() -> activity.findViewById(id("radioReturnMusic")));
            verifyRadioMusicBaseline();
            return;
        }
        verifyRadioMusicBaseline();
        android.content.ContentResolver resolver = getTargetContext().getContentResolver();
        Uri root = DocumentsContract.buildTreeDocumentUri(PACKAGE + ".test.cache", "root");
        await(() -> getTargetContext().checkUriPermission(root, Process.myPid(), Process.myUid(),
            Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED);
        resolver.takePersistableUriPermission(root, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Bundle reset = new Bundle();
        reset.putBoolean("reset", true);
        resolver.call(root, "cacheFixture", null, reset);
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(root, "root");
        String text = "#EXTM3U\n#EXTINF:-1 tvg-id=\"radio:cnr:zgzs\",CNR edited\n" +
            "https://radio.example/cnr/index.m3u8\n#EXTINF:-1 tvg-id=\"fixture131\",Custom 131\n" +
            "http://radio.example/custom/index.m3u8\n";
        Uri list = DocumentsContract.createDocument(resolver, parent, "text/plain", "Stations.m3u");
        try (OutputStream output = resolver.openOutputStream(list, "wt")) { output.write(text.getBytes(StandardCharsets.UTF_8)); }
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText(""));
        openRadioList("Stations.m3u");
        saveScreenshot("radio131-import-preview.png");
        check(network.requests.isEmpty() && savedRadioPrograms().length() == 0, "Preview changed data or fetched a stream");
        clickDialogAction("cancel");
        check(savedRadioPrograms().length() == 0, "Cancel applied an import");
        openRadioList("Stations.m3u");
        applyRadioList("radio_import_replace");
        await(() -> savedRadioPrograms().length() == 2);
        check(text.equals(documentText(list)), "Import changed the source file");
        check(network.requests.isEmpty(), "Import connected to a station");
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("CNR edited"));
        tap(() -> descriptionView(activity.findViewById(id("radioList")), "CNR edited"));
        await(() -> "radio:cnr:zgzs".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        check(network.catalogues.get() == 0, "Overridden CNR URL ignored its imported address");
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioReset")));
        clickDialogAction("radio_reset");
        await(() -> savedRadioPrograms().length() == 1 && radioStopped() && network.active.get() == 0);
        check("Custom 131".equals(savedRadioPrograms().getJSONObject(0).getString("name")), "Reset removed the custom station");
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("Custom 131"));
        expandRadioGroup("radio_group_other");
        tap(() -> descriptionView(activity.findViewById(id("radioList")), "Custom 131"));
        String customId = savedRadioPrograms().getJSONObject(0).getString("id");
        await(() -> customId.equals(mediaId()) && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        tap(() -> activity.findViewById(id("favorite")));
        tap(() -> activity.findViewById(id("play")));
        await(() -> radioStopped() && network.active.get() == 0);
        Uri update = DocumentsContract.createDocument(resolver, parent, "text/plain", "Stations.txt");
        String replacement = customId + "\tCustom 131 renamed\thttps://radio.example/updated/index.m3u8\n";
        try (OutputStream output = resolver.openOutputStream(update, "wt")) { output.write(replacement.getBytes(StandardCharsets.UTF_8)); }
        openRadioList("Stations.txt");
        applyRadioList("radio_import_replace");
        await(() -> savedRadioPrograms().length() == 1 &&
            "Custom 131 renamed".equals(savedRadioPrograms().optJSONObject(0).optString("name")));
        check(getTargetContext().getSharedPreferences("settings", 0)
            .getString("radioPins", "").contains(customId), "Updating lost the pin");
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("Custom 131 renamed"));
        saveScreenshot("radio131-imported-station.png");
        if (radioSamplePath != null) {
            Uri sample = DocumentsContract.createDocument(resolver, parent, "text/plain", "Provided.m3u");
            try (InputStream input = new java.io.FileInputStream(radioSamplePath);
                 OutputStream output = resolver.openOutputStream(sample, "wt")) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            int requests = network.requests.size();
            openRadioList("Provided.m3u");
            saveScreenshot("radio131-user-list-preview.png");
            String[] preview = {""};
            onMain(() -> preview[0] = ((TextView) ownedPanel().findViewById(android.R.id.message)).getText().toString());
            java.util.regex.Matcher counts = java.util.regex.Pattern.compile("\\d+").matcher(preview[0]);
            int[] totals = new int[3];
            for (int i = 0; i < totals.length; i++) {
                check(counts.find(), "Missing sample import counts");
                totals[i] = Integer.parseInt(counts.group());
            }
            check(totals[0] == 424 && totals[2] == 0,
                "The supplied 424-station example must parse without rejected lines: " + preview[0]);
            result.putInt("sampleAccepted", 424);
            result.putInt("sampleCurrentStations", totals[1]);
            check(network.requests.size() == requests, "Sample preview made network requests");
            clickDialogAction("cancel");
            tap(() -> activity.findViewById(id("nav_radio")));
            check(savedRadioPrograms().length() == 1, "Cancelled sample changed the catalog");
            result.putString("sample", "Provided local M3U previewed through the real explorer without fetching or applying");
        }
        Bundle unavailable = new Bundle();
        unavailable.putBoolean("unavailable", true);
        resolver.call(root, "cacheFixture", null, unavailable);
        try {
            tap(() -> descriptionView(activity.findViewById(id("radioList")), "Custom 131 renamed"));
            await(() -> customId.equals(mediaId()) && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
            check(network.requests.contains("radio.example/updated/index.m3u8"), "Playback still used the old imported URL");
            tap(() -> activity.findViewById(id("play")));
            await(() -> radioStopped() && network.active.get() == 0);
        } finally { resolver.call(root, "cacheFixture", null, new Bundle()); }
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioReset")));
        clickDialogAction("radio_reset");
        await(() -> radioSnapshot().optBoolean("includeDefaults") && savedRadioPrograms().length() == 1);
    }

    private void verifyRadioButtons(FixtureRadioNetwork network) throws Exception {
        verifyRadioMusicBaseline();
        android.content.SharedPreferences saved = getTargetContext().getSharedPreferences("settings", 0);
        check(network.requests.isEmpty(), "Local startup must not fetch radio data");
        String catalog = savedRadioPrograms().toString();
        String pins = saved.getString("radioPins", "");
        check(savedRadioPrograms().length() == 1, "The preceding imported station must survive");
        if (radioDisabledResumeOnly) {
            check(!saved.getBoolean("radioEnabled", true), "Hidden Radio setting was lost across process restart");
            onMain(() -> {
                check(activity.findViewById(id("nav_radio")).getVisibility() == View.GONE, "Hidden Radio is visible");
                check(activity.findViewById(id("radioQuickStations")) == null, "Local startup inflated radio buttons");
            });
            openRadioSettings();
            verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
            check(!((android.widget.CompoundButton) activity.findViewById(id("settingsRadio"))).isChecked(),
                "Hidden Radio setting is not reflected in the switch");
            saveScreenshot("radio132-disabled-restored.png");
            tap(() -> activity.findViewById(id("settingsRadio")));
            await(() -> saved.getBoolean("radioEnabled", false) &&
                activity.findViewById(id("nav_radio")).getVisibility() == View.VISIBLE);
            verifyRadioMusicBaseline();
            check(network.requests.isEmpty(), "Showing Radio must not connect");
            check(catalog.equals(savedRadioPrograms().toString()) &&
                pins.equals(saved.getString("radioPins", "")),
                "Hidden Radio restart changed saved station data");
            return;
        }
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText(""));
        await(() -> radioGrid("radioList").getChildCount() >= 2);
        verifyRadioToolbar();
        onMain(() -> {
            ViewGroup grid = radioGrid("radioList");
            for (int i = 0; i < Math.min(pins.split("\n").length, grid.getChildCount()); i++) {
                View button = grid.getChildAt(i);
                check(button instanceof android.widget.Button && button.isFocusable(), "Station is not a native focusable button");
                check(((TextView) button).getCompoundDrawablesRelative()[1] != null, "Station logo is missing");
            }
        });
        saveScreenshot("radio132-buttons-en.png");
        setInTouchMode(false);
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
        waitForIdleSync();
        await(() -> !radioGrid("radioList").isInTouchMode() && activity.hasWindowFocus());
        onMain(() -> {
            View button = radioGrid("radioList").getChildAt(0);
            check(button.requestFocus(), "Radio button cannot accept remote focus: shown=" + button.isShown() +
                ", focusable=" + button.isFocusable() + ", touch=" + button.isInTouchMode());
        });
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_MENU);
        tap(() -> windowText(activity.getString(resource("details", "string"))));
        clickDialogAction("close");
        check(network.requests.isEmpty(), "Radio browsing/logo/details must remain offline");
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("zgzs"));
        tap(() -> descriptionView(radioGrid("radioList"), activity.getString(resource("radio_cnr_1", "string"))));
        await(() -> "radio:cnr:zgzs".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        await(() -> activity.findViewById(id("radioQuickStations")) != null &&
            radioGrid("radioQuickStations").isShown() && radioGrid("radioQuickStations").getChildCount() >= 2);
        String[] quickTitle = {""};
        onMain(() -> quickTitle[0] = radioGrid("radioQuickStations").getChildAt(1).getContentDescription().toString());
        tap(() -> radioGrid("radioQuickStations").getChildAt(1));
        await(() -> quickTitle[0].equals(controller.getMetadata().getDescription().getTitle().toString()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        onMain(() -> check(!activity.findViewById(id("artwork")).isShown() &&
            !activity.findViewById(id("lyricsList")).isShown(), "Radio must replace the lyrics/artwork area"));
        saveScreenshot("radio132-quick-en.png");
        openRadioSettings();
        verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        int connections = network.manifests.get();
        tap(() -> activity.findViewById(id("settingsRadio")));
        await(() -> !saved.getBoolean("radioEnabled", true) &&
            activity.findViewById(id("nav_radio")).getVisibility() == View.GONE);
        check(quickTitle[0].contentEquals(controller.getMetadata().getDescription().getTitle()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING &&
            connections == network.manifests.get(), "Hiding Radio interrupted its stream");
        check(catalog.equals(savedRadioPrograms().toString()) &&
            pins.equals(saved.getString("radioPins", "")),
            "Hiding Radio erased station data");
        tap(() -> activity.findViewById(id("settingsRadio")));
        await(() -> saved.getBoolean("radioEnabled", false) &&
            activity.findViewById(id("nav_radio")).getVisibility() == View.VISIBLE);
        check(connections == network.manifests.get(), "Showing Radio reconnected its stream");
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioReturnMusic")));
        verifyRadioMusicBaseline();
        await(() -> network.active.get() == 0);
        int requests = network.requests.size();
        openRadioSettings();
        verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        onMain(() -> controller.getTransportControls().play());
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        tap(() -> activity.findViewById(id("settingsRadio")));
        await(() -> !saved.getBoolean("radioEnabled", true));
        check("queue-fixture-76".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING, "Hiding Radio interrupted local music");
        onMain(() -> { controller.getTransportControls().pause(); controller.getTransportControls().seekTo(20_000); });
        await(() -> restoredAt(20_000));
        check(network.requests.size() == requests, "Changing visibility unexpectedly connected a radio stream");
        tap(() -> activity.findViewById(id("settingsRadio")));
        verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        tap(() -> activity.findViewById(id("settingsLanguage")));
        tap(() -> windowText(activity.getString(resource("language_chinese", "string"))));
        await(() -> "zh".equals(activity.getResources().getConfiguration().locale.getLanguage()) && activity.hasWindowFocus());
        check("\u5c0f\u60a6\u97f3\u4e50\u5e7f\u64ad".equals(activity.getString(resource("app_name", "string"))),
            "New Chinese branding missing");
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText(""));
        await(() -> radioGrid("radioList").getChildCount() >= 2);
        verifyRadioToolbar();
        saveScreenshot("radio132-buttons-zh.png");
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("zgzs"));
        tap(() -> descriptionView(radioGrid("radioList"), activity.getString(resource("radio_cnr_1", "string"))));
        await(() -> "radio:cnr:zgzs".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING &&
            activity.findViewById(id("radioQuickStations")).isShown());
        saveScreenshot("radio132-quick-zh.png");
        openRadioSettings();
        verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        tap(() -> activity.findViewById(id("settingsRadio")));
        await(() -> !saved.getBoolean("radioEnabled", true));
        check("radio:cnr:zgzs".equals(mediaId()) &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING,
            "Hiding Radio after a language change interrupted playback");
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioReturnMusic")));
        verifyRadioMusicBaseline();
        check(catalog.equals(savedRadioPrograms().toString()) &&
            pins.equals(saved.getString("radioPins", "")),
            "Station data changed after language/source switches");
    }

    private ViewGroup radioGrid(String name) { return activity.findViewById(id(name)); }

    private void seedRadioUpgrade() throws Exception {
        tap(() -> activity.findViewById(id("nav_radio")));
        await(() -> activity.findViewById(id("radioSearch")) != null && activity.findViewById(id("radioSearch")).isShown());
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("zgzs"));
        await(() -> descriptionView(radioGrid("radioList"), activity.getString(resource("radio_cnr_1", "string"))) != null);
        onMain(() -> descriptionView(radioGrid("radioList"),
            activity.getString(resource("radio_cnr_1", "string"))).performLongClick());
        chooseRadioOption(3);
        await(() -> ownedPanel() != null && ownedPanel().findViewById(id("radioEditSources")) != null);
        onMain(() -> {
            ((EditText) ownedPanel().findViewById(id("radioEditName"))).setText("Upgrade 134 CNR");
            ((EditText) ownedPanel().findViewById(id("radioEditSources")))
                .setText("https://radio.example/upgrade-a/index.m3u8\nhttps://radio.example/upgrade-b/index.m3u8");
        });
        clickDialogAction("save");
        await(() -> savedRadioPrograms().length() == 1 &&
            descriptionView(radioGrid("radioList"), "Upgrade 134 CNR") != null);
        onMain(() -> descriptionView(radioGrid("radioList"), "Upgrade 134 CNR").performLongClick());
        chooseRadioOption(2);
        await(() -> windowText(activity.getString(resource("radio_source_item", "string"), 2,
            "https://radio.example/upgrade-b/index.m3u8")) != null);
        chooseRadioOption(1);
        await(() -> {
            org.json.JSONObject selections = radioSnapshot().optJSONObject("sourceSelections");
            return selections != null && selections.has("radio:cnr:zgzs")
                ? "https://radio.example/upgrade-b/index.m3u8".equals(selections.optString("radio:cnr:zgzs"))
                : savedRadioPrograms().optJSONObject(0).optInt("sourceIndex") == 1;
        });
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        tap(() -> activity.findViewById(id("nav_player")));
        tap(() -> activity.findViewById(id("favorite")));
        await(() -> !getTargetContext().getSharedPreferences("settings", 0)
            .getString("radioPins", "").contains("radio:cnr:zgzs"));
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioReturnMusic")));
        verifyRadioMusicBaseline();
        if (radioHiddenBaselineOnly) {
            await(() -> !activity.findViewById(id("settingsPanel")).isShown() &&
                activity.findViewById(id("noticeBar")).isShown());
            openRadioSettings();
            verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
            tap(() -> activity.findViewById(id("settingsRadio")));
            await(() -> !getTargetContext().getSharedPreferences("settings", 0).getBoolean("radioEnabled", true));
            verifyRadioMusicBaseline();
            saveScreenshot("radio146-upgrade-hidden-baseline.png");
        }
    }

    private void verifyRadioUpgrade(FixtureRadioNetwork network) throws Exception {
        check(savedRadioPrograms().length() == 1, "Upgrade lost the saved station override");
        org.json.JSONObject station = savedRadioPrograms().getJSONObject(0);
        check("Upgrade 134 CNR".equals(station.getString("name")) &&
            "https://radio.example/upgrade-b/index.m3u8".equals(savedRadioSource(station)) &&
            station.getJSONArray("alternateUris").length() == 1,
            "Upgrade lost the edited name, addresses or selected source");
        check(!getTargetContext().getSharedPreferences("settings", 0).getString("radioPins", "").contains("radio:cnr:zgzs"),
            "Catalog expansion must not re-pin an unpinned default");
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("Upgrade 134 CNR"));
        expandRadioGroup("radio_group_national");
        await(() -> descriptionView(radioGrid("radioList"), "Upgrade 134 CNR") != null);
        onMain(() -> check("Upgrade 134 CNR".contentEquals(
            ((TextView) descriptionView(radioGrid("radioList"), "Upgrade 134 CNR")).getText()),
            "Expanded catalog changed the saved name or added a source-count suffix"));
        check(network.requests.isEmpty(), "Upgrade restoration or browsing fetched audio or a logo");
        saveScreenshot("radio134-upgraded-override.png");
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioReset")));
        clickDialogAction("radio_reset");
        await(() -> savedRadioPrograms().length() == 0);
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("zgzs"));
        expandRadioGroup("radio_group_national");
        await(() -> descriptionView(radioGrid("radioList"), activity.getString(resource("radio_cnr_1", "string"))) != null);
        onMain(() -> descriptionView(radioGrid("radioList"),
            activity.getString(resource("radio_cnr_1", "string"))).performLongClick());
        chooseRadioOption(1);
        await(() -> getTargetContext().getSharedPreferences("settings", 0)
            .getString("radioPins", "").contains("radio:cnr:zgzs"));
    }

    private void verifyBundledRadioRows(FixtureRadioNetwork network) throws Exception {
        String[][] stations = {
            {"KEXP", "radio_region_usa"}, {"BBC World Service", "radio_region_uk"},
            {"TDM Macau FM100.7", "radio_region_macao"}, {"BCC\u4e2d\u5e7f\u65b0\u95fb", "radio_region_taiwan"},
            {"\u5b89\u5fbd\u4ea4\u901a\u5e7f\u64ad", "radio_region_anhui"}
        };
        for (String[] station : stations) {
            String name = station[0];
            onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText(name));
            await(() -> descriptionView(radioGrid("radioList"), activity.getString(resource(station[1], "string"))) != null &&
                !radioGrid("radioList").isLayoutRequested());
            onMain(() -> check(descriptionView(radioGrid("radioList"), name) == null,
                "A fresh search must show only collapsed group names"));
            expandRadioGroup(station[1]);
            await(() -> descriptionView(radioGrid("radioList"), name) != null);
        }
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("RTHK Radio"));
        expandRadioGroup("radio_region_hongkong");
        await(() -> descriptionView(radioGrid("radioList"), "RTHK Radio 1") != null &&
            descriptionView(radioGrid("radioList"), "RTHK Radio 2") != null &&
            !radioGrid("radioList").isLayoutRequested());
        onMain(() -> {
            ViewGroup grid = radioGrid("radioList");
            View first = descriptionView(grid, "RTHK Radio 1");
            View second = descriptionView(grid, "RTHK Radio 2");
            check("RTHK Radio 1".contentEquals(((TextView) first).getText()),
                "Single-address rows should not waste space on source counts");
            float density = activity.getResources().getDisplayMetrics().density;
            float scale = Math.max(1f, activity.getResources().getConfiguration().fontScale);
            float available = (grid.getWidth() - grid.getPaddingLeft() - grid.getPaddingRight()) / density;
            Rect one = bounds(first);
            Rect two = bounds(second);
            if (available >= 520 * scale) {
                check(one.top == two.top && one.right <= two.left,
                    "Wide release radio rows must use two non-overlapping columns");
                check(Math.abs(one.width() - two.width()) <= 1, "Radio columns must have equal width");
            } else {
                check(one.left == two.left && one.bottom <= two.top,
                    "Narrow release radio rows must use one column");
            }
            View heading = textView(grid, activity.getString(resource("radio_region_hongkong", "string")));
            check(heading != null && heading.getWidth() >= grid.getWidth() - 24 * density,
                "Region headings must span the full row");
            check(first.isFocusable() && second.isFocusable(), "Compact channel rows must retain remote focus");
        });
        check(network.requests.isEmpty(), "Loading the full bundled catalog must not fetch streams or logos");
        saveScreenshot("radio134-regional-columns.png");
    }

    private void expandRadioGroup(String label) throws Exception {
        String title = activity.getString(resource(label, "string"));
        await(() -> descriptionView(radioGrid("radioList"), title) != null);
        tap(() -> descriptionView(radioGrid("radioList"), title));
    }

    private void verifyRadioPinColor(FixtureRadioNetwork network) throws Exception {
        openRadioSettings();
        verifyColorPicker("radioPinColor");
        verifyCustomColor("radioPinColor", "radioPinColor", false);
        android.content.SharedPreferences saved = getTargetContext().getSharedPreferences("settings", 0);
        tap(() -> activity.findViewById(id("nav_radio")));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText(""));
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("zgzs"));
        await(() -> radioGrid("radioList").findViewWithTag("radio:cnr:zgzs") != null &&
            !radioGrid("radioList").isLayoutRequested());
        onMain(() -> check(((TextView) radioGrid("radioList").findViewWithTag("radio:cnr:zgzs")).getCurrentTextColor() == 0xff13579b,
            "Pinned shortcut must use the chosen color"));
        expandRadioGroup("radio_group_national");
        await(() -> {
            radioGrid("radioList").scrollBy(0, 100000);
            return pinnedRadioListRow() != null && !radioGrid("radioList").isLayoutRequested();
        });
        onMain(() -> {
            TextView station = pinnedRadioListRow();
            check(station.getCurrentTextColor() == 0xff13579b,
                "Pinned station must remain in its group with the chosen color and pin marker");
            station.performLongClick();
        });
        chooseRadioOption(1);
        await(() -> !saved.getString("radioPins", "").contains("radio:cnr:zgzs") &&
            descriptionView(radioGrid("radioList"), activity.getString(resource("radio_cnr_1", "string"))) != null &&
            !radioGrid("radioList").isLayoutRequested());
        onMain(() -> {
            TextView station = (TextView) descriptionView(radioGrid("radioList"),
                activity.getString(resource("radio_cnr_1", "string")));
            check(station.getCurrentTextColor() != 0xff13579b && station.getCompoundDrawablesRelative()[0] == null,
                "Unpin must remove the marker/color, not the station");
            check(activity.getString(resource("radio_cnr_1", "string")).contentEquals(station.getContentDescription()),
                "Unpin removed the station from the open group");
            station.performLongClick();
        });
        chooseRadioOption(1);
        await(() -> saved.getString("radioPins", "").contains("radio:cnr:zgzs"));
        await(() -> {
            radioGrid("radioList").scrollBy(0, 100000);
            return pinnedRadioListRow() != null && !radioGrid("radioList").isLayoutRequested();
        });
        check(network.requests.isEmpty(), "Pin/color changes must not fetch streams or artwork");
        saveScreenshot("radio1342-pinned-list-color.png");
    }

    private TextView pinnedRadioListRow() {
        ViewGroup grid = radioGrid("radioList");
        String title = activity.getString(resource("radio_cnr_1", "string"));
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (child instanceof TextView && title.contentEquals(((TextView) child).getContentDescription()) &&
                ((TextView) child).getCompoundDrawablesRelative()[0] != null) return (TextView) child;
        }
        return null;
    }

    private void verifyRadioToolbar() {
        onMain(() -> {
            View search = activity.findViewById(id("radioSearch"));
            View configure = activity.findViewById(id("radioSettings"));
            if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                check(Math.abs(bounds(search).centerY() - bounds(configure).centerY()) <=
                    24 * activity.getResources().getDisplayMetrics().density, "Landscape search must share the action row");
            } else {
                check(bounds(search).centerY() < bounds(configure).centerY(), "Portrait toolbar must retain stacked rows");
            }
        });
    }

    private void chooseRadioOption(int position) {
        waitForIdleSync();
        await(() -> ownedPanel() != null && ownedPanel().findViewById(id("playerDialogList")) != null);
        android.widget.ListView[] selected = {null};
        onMain(() -> {
            selected[0] = ownedPanel().findViewById(id("playerDialogList"));
            selected[0].setSelection(position);
        });
        tap(() -> {
            android.widget.ListView list = selected[0];
            int child = position - list.getFirstVisiblePosition();
            return child < 0 || child >= list.getChildCount() ? null : list.getChildAt(child);
        });
        waitForIdleSync();
    }

    private void verifyRadioLiveOptions(FixtureRadioNetwork network) throws Exception {
        android.content.SharedPreferences saved = getTargetContext().getSharedPreferences("settings", 0);
        String stationId = savedRadioPrograms().getJSONObject(0).getString("id");
        String secondary = "https://radio.example/secondary/index.m3u8";
        if (radioLiveResumeOnly) {
            await(() -> stationId.equals(mediaId()) && radioStopped());
            SystemClock.sleep(900);
            check(network.requests.isEmpty(), "Restored source/tuning must not fetch audio or logos");
            org.json.JSONObject station = savedRadioPrograms().getJSONObject(0);
            check(station.getJSONArray("alternateUris").length() == 1 && station.getInt("sourceIndex") == 1,
                "Multi-source selection was lost after restart");
            org.json.JSONObject tuning = new org.json.JSONObject(saved.getString("radioTuning", "{}"));
            check(tuning.getInt("bufferSeconds") == 30 && tuning.getInt("startSeconds") == 3 &&
                tuning.getInt("retries") == 3 && tuning.getBoolean("autoSource") && !tuning.getBoolean("downloadLogos"),
                "Radio settings were lost after restart");
            check(saved.getString("radioPins", "").contains(stationId), "Station pin was lost after restart");
            onMain(() -> controller.getTransportControls().play());
            await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
            check(network.requests.contains("radio.example/secondary/index.m3u8") && network.logos.get() == 0,
                "Restored source or logo switch was ignored");
            onMain(() -> controller.getTransportControls().pause());
            await(() -> radioStopped() && network.active.get() == 0);
            openRadioSettings();
            tap(() -> activity.findViewById(id("radioReturnMusic")));
            verifyRadioMusicBaseline();
            return;
        }
        verifyRadioMusicBaseline();
        check(network.requests.isEmpty(), "Editing setup must remain offline");
        android.content.ContentResolver resolver = getTargetContext().getContentResolver();
        Uri root = DocumentsContract.buildTreeDocumentUri(PACKAGE + ".test.cache", "root");
        String extraName = "Additional133-" + SystemClock.uptimeMillis() + ".txt";
        Uri extra = DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(root, "root"),
            "text/plain", extraName);
        check(extra != null, "Could not create the synthetic additional-source file");
        String text = stationId + "\tCustom 131 renamed\t" + secondary + "\n";
        try (OutputStream output = resolver.openOutputStream(extra, "wt")) { output.write(text.getBytes(StandardCharsets.UTF_8)); }
        openRadioList(extraName);
        applyRadioList("radio_import_append");
        await(() -> savedRadioPrograms().optJSONObject(0).optJSONArray("alternateUris") != null &&
            savedRadioPrograms().optJSONObject(0).optJSONArray("alternateUris").length() == 1);
        check(text.equals(documentText(extra)), "Appending addresses modified the source file");
        onMain(() -> ((EditText) activity.findViewById(id("radioSearch"))).setText("Custom 131 renamed"));
        await(() -> descriptionView(radioGrid("radioList"), "Custom 131 renamed") != null);
        onMain(() -> check(descriptionView(radioGrid("radioList"), "Custom 131 renamed").performLongClick(),
            "Station must expose its long-press editor"));
        chooseRadioOption(3);
        await(() -> ownedPanel() != null && ownedPanel().findViewById(id("radioEditSources")) != null);
        onMain(() -> {
            ((EditText) ownedPanel().findViewById(id("radioEditGroup"))).setText("Zhejiang");
            ((EditText) ownedPanel().findViewById(id("radioEditSources")))
                .setText("https://radio.example/updated/index.m3u8\n" + secondary);
            ((EditText) ownedPanel().findViewById(id("radioEditLogo"))).setText("https://radio.example/logo.png");
        });
        saveScreenshot("radio133-release-editor.png");
        clickDialogAction("save");
        await(() -> "Zhejiang".equals(savedRadioPrograms().optJSONObject(0).optString("group")));
        check(network.requests.isEmpty(), "Importing/editing must not fetch logos or audio");
        await(() -> descriptionView(radioGrid("radioList"), "Custom 131 renamed") != null);
        onMain(() -> descriptionView(radioGrid("radioList"), "Custom 131 renamed").performLongClick());
        chooseRadioOption(2);
        saveScreenshot("radio133-release-sources.png");
        chooseRadioOption(1);
        await(() -> stationId.equals(mediaId()) && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        check(savedRadioPrograms().getJSONObject(0).getInt("sourceIndex") == 1, "Source selection was not saved");
        check(!network.requests.contains("radio.example/updated/index.m3u8"), "The unselected source was fetched");
        await(() -> network.logos.get() == 1 && new File(getTargetContext().getFilesDir(), "radio-logos").listFiles().length > 0);
        tap(() -> activity.findViewById(id("nav_player")));
        await(() -> activity.findViewById(id("radioStreamStatus")).isShown());
        onMain(() -> check(((TextView) activity.findViewById(id("album"))).getText().toString().contains("2/2"),
            "Playing must identify the active source"));
        onMain(() -> {
            check(activity.findViewById(id("rewind")).isEnabled(), "Second source must offer Previous source");
            check(!activity.findViewById(id("forward")).isEnabled(), "Last source must not wrap to the first");
            check(activity.getString(resource("radio_source_previous", "string"))
                .equals(activity.findViewById(id("rewind")).getContentDescription()), "Radio source label missing");
        });
        tap(() -> activity.findViewById(id("rewind")));
        await(() -> savedRadioPrograms().optJSONObject(0).optInt("sourceIndex") == 0 &&
            activity.findViewById(id("forward")).isEnabled() &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        check(stationId.equals(mediaId()) && network.requests.contains("radio.example/updated/index.m3u8"),
            "Previous source changed station or failed to reconnect");
        onMain(() -> {
            check(!activity.findViewById(id("rewind")).isEnabled(), "First source must not wrap backwards");
            activity.findViewById(id("forward")).performClick();
            activity.findViewById(id("forward")).performClick();
        });
        await(() -> savedRadioPrograms().optJSONObject(0).optInt("sourceIndex") == 1 &&
            !activity.findViewById(id("forward")).isEnabled() &&
            controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        check(stationId.equals(mediaId()), "Next source changed station");
        saveScreenshot("radio133-release-status.png");
        tap(() -> activity.findViewById(id("play")));
        await(() -> radioStopped() && network.active.get() == 0);
        onMain(() -> check(((TextView) activity.findViewById(id("radioStreamStatus"))).getText().toString().contains("0.0 KB/s"),
            "Stopped radio must not retain an active receive rate"));
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioBuffer")));
        chooseRadioOption(3);
        tap(() -> activity.findViewById(id("radioStartBuffer")));
        chooseRadioOption(2);
        tap(() -> activity.findViewById(id("radioRetries")));
        chooseRadioOption(3);
        tap(() -> activity.findViewById(id("radioAutoSource")));
        tap(() -> activity.findViewById(id("radioDownloadLogos")));
        tap(() -> activity.findViewById(id("radioShowStatus")));
        tap(() -> activity.findViewById(id("nav_player")));
        await(() -> !activity.findViewById(id("radioStreamStatus")).isShown());
        openRadioSettings();
        tap(() -> activity.findViewById(id("radioShowStatus")));
        tap(() -> activity.findViewById(id("radioClearCache")));
        clickDialogAction("radio_clear_cache");
        await(() -> new File(getTargetContext().getFilesDir(), "radio-logos").listFiles().length == 0);
        saveScreenshot("radio133-release-settings.png");
        check(saved.getString("radioPins", "").contains(stationId), "Radio settings changed station pins");
        check(savedLibrary().getJSONArray("tracks").length() == 100, "Radio settings changed the music library");
        check(savedRadioPrograms().getJSONObject(0).getJSONArray("alternateUris").length() == 1,
            "Clearing logos erased source addresses");
    }

    private void verifyProgressiveScan(Uri tree, Bundle result) throws Exception {
        check(controller.getPlaybackState().getState() != PlaybackState.STATE_PLAYING, "Scan launch must stay paused");
        long started;
        if (!progressiveScanResume) {
            tap(() -> activity.findViewById(id("action_settings")));
            verifySettingsPage("categoryLyrics", "settingsLyricsPanel");
            if (((CompoundButton) activity.findViewById(id("settingsLyrics"))).isChecked())
                tap(() -> activity.findViewById(id("settingsLyrics")));
            verifySettingsPage("categoryFolders", "settingsFoldersPanel");
            ActivityMonitor picker = new ActivityMonitor(new IntentFilter(Intent.ACTION_OPEN_DOCUMENT_TREE),
                new ActivityResult(Activity.RESULT_OK, new Intent().setData(tree).addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)), true);
            started = SystemClock.uptimeMillis();
            try {
                tap(() -> activity.findViewById(id("settingsAddFolder")));
                chooseAndroidPicker(picker);
                awaitScan(() -> {
                    org.json.JSONObject snapshot = scanSnapshot();
                    return snapshot != null && snapshot.optJSONArray("tracks") != null &&
                        snapshot.optJSONArray("tracks").length() == 1000;
                });
            } finally { removeMonitor(picker); }
            result.putLong("filesReadyMs", SystemClock.uptimeMillis() - started);
            int reads = getTargetContext().getContentResolver().call(tree, "scanFixtureState", null, null).getInt("audioReads");
            result.putInt("audioReadsWhenReady", reads);
            if (progressiveScanBaseline) {
                check(reads == 1000, "Baseline should read every song before publishing");
                return;
            }
            check(reads < 1000 && pendingDetails() > 0, "Music must be usable before details finish");
            check(controller.getPlaybackState().getState() != PlaybackState.STATE_PLAYING, "Adding a folder must not autoplay");
        } else {
            started = SystemClock.uptimeMillis();
            check(savedLibrary().getJSONArray("tracks").length() == 1000, "Restart lost discovered songs");
            check(pendingDetails() > 0, "Resume fixture must contain unfinished metadata");
        }
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        long[] previous = {0};
        long[] maximumGap = {0};
        Runnable heartbeat = new Runnable() {
            @Override public void run() {
                long now = SystemClock.uptimeMillis();
                maximumGap[0] = Math.max(maximumGap[0], now - previous[0]);
                previous[0] = now;
                handler.postDelayed(this, 16);
            }
        };
        onMain(() -> { previous[0] = SystemClock.uptimeMillis(); handler.post(heartbeat); });
        try {
            tap(() -> activity.findViewById(id("nav_library")));
            await(() -> activity.findViewById(id("libraryList")) != null &&
                ((ViewGroup) activity.findViewById(id("libraryList"))).getChildCount() > 0);
            if (!progressiveScanResume) {
                tap(() -> ((ViewGroup) activity.findViewById(id("libraryList"))).getChildAt(0).findViewById(id("title")));
                await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING &&
                    controller.getQueue() != null && controller.getQueue().size() == 1000);
                result.putLong("playableMs", SystemClock.uptimeMillis() - started);
                check(pendingDetails() > 0, "Playback must start while tags are unfinished");
                tap(() -> activity.findViewById(id("nav_queue")));
                await(() -> queueList() != null && queueList().getChildCount() > 0);
                tap(() -> activity.findViewById(id("action_settings")));
                verifySettingsPage("categoryFolders", "settingsFoldersPanel");
                onMain(() -> {
                    check(activity.findViewById(id("settingsAddFolder")).isEnabled(), "Background tags blocked Add folder");
                    check(activity.findViewById(id("settingsPlayFolders")).isEnabled(), "Background tags blocked Play");
                    check(activity.findViewById(id("settingsRemoveAllFolders")).isEnabled(), "Background tags blocked removal");
                });
                tap(() -> activity.findViewById(id("nav_player")));
                saveScreenshot("scan-background-playing-api" + Build.VERSION.SDK_INT + ".png");
            }
            if (progressiveScanLeavePending) {
                onMain(() -> controller.getTransportControls().pause());
                await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PAUSED);
                tap(() -> activity.findViewById(id("nav_folders")));
                String pauseDetails = activity.getString(resource("metadata_background_pause", "string"));
                tap(() -> textView(activity.getWindow().getDecorView(), pauseDetails));
                await(() -> textView(activity.getWindow().getDecorView(), pauseDetails) == null);
                int reads = getTargetContext().getContentResolver()
                    .call(tree, "scanFixtureState", null, null).getInt("audioReads");
                SystemClock.sleep(250);
                check(reads == getTargetContext().getContentResolver()
                    .call(tree, "scanFixtureState", null, null).getInt("audioReads"), "Pause details kept reading files");
                result.putInt("pendingAtExit", pendingDetails());
                check(pendingDetails() > 0, "Restart fixture completed unexpectedly");
                return;
            }
            awaitScan(() -> pendingDetails() == 0);
            result.putLong("allDetailsMs", SystemClock.uptimeMillis() - started);
            org.json.JSONArray tracks = savedLibrary().getJSONArray("tracks");
            for (int index = 0; index < tracks.length(); index++)
                check(tracks.getJSONObject(index).getLong("durationMs") >= 59000, "Background duration is missing");
            if (progressiveScanResume) {
                check(controller.getPlaybackState().getState() != PlaybackState.STATE_PLAYING, "Resume must not autoplay");
                check(getTargetContext().getContentResolver().call(tree, "scanFixtureState", null, null)
                    .getInt("childReads") == 0, "Pending restart must not walk storage again");
            } else {
                check(controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING,
                    "Background metadata interrupted playback");
                onMain(() -> controller.getTransportControls().pause());
            }
            result.putInt("audioReads", getTargetContext().getContentResolver()
                .call(tree, "scanFixtureState", null, null).getInt("audioReads"));
        } finally {
            onMain(() -> handler.removeCallbacks(heartbeat));
            result.putLong("maximumMainGapMs", maximumGap[0]);
        }
        check(maximumGap[0] < 1500, "Background metadata blocked UI for " + maximumGap[0] + "ms");
    }

    private int pendingDetails() {
        try {
            org.json.JSONObject snapshot = scanSnapshot();
            if (snapshot == null) return -1;
            org.json.JSONArray tracks = snapshot.getJSONArray("tracks");
            int pending = 0;
            for (int index = 0; index < tracks.length(); index++)
                if (tracks.getJSONObject(index).optBoolean("metadataPending")) pending++;
            return pending;
        } catch (org.json.JSONException error) { throw new AssertionError(error); }
    }

    private org.json.JSONObject scanSnapshot() {
        File file = new File(getTargetContext().getFilesDir(), "library.json");
        File backup = new File(file.getPath() + ".bak");
        if (!file.isFile() || backup.exists()) return null;
        try (InputStream input = new java.io.FileInputStream(file);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            if (backup.exists()) return null;
            return new org.json.JSONObject(bytes.toString("UTF-8"));
        } catch (java.io.FileNotFoundException | org.json.JSONException concurrentWrite) {
            // A legacy AtomicFile may be replaced during this instrumentation-only observation.
            android.util.Log.d("ProgressiveScanTest", "Waiting for a complete atomic write", concurrentWrite);
            return null;
        } catch (IOException error) { throw new AssertionError(error); }
    }

    private void awaitScan(Condition condition) {
        long deadline = SystemClock.uptimeMillis() + 120000;
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Scan did not reach the expected persisted state");
    }

    private void writeBulkRemovalFixture(Uri tree, Uri audio) throws Exception {
        check(bulkRemovalCount >= 1000 && bulkRemovalCount <= 10000, "Invalid large-library fixture size");
        File library = new File(getTargetContext().getFilesDir(), "library.json");
        File playback = new File(getTargetContext().getFilesDir(), "playback.json");
        check(!library.exists() && !playback.exists(), "Bulk-removal fixture requires fresh test data");
        org.json.JSONArray sources = new org.json.JSONArray();
        for (int index = 0; index < 5; index++) {
            String uri = index == 0 ? tree.toString() :
                DocumentsContract.buildTreeDocumentUri(PACKAGE + ".test.documents", "bulk-" + index).toString();
            sources.put(new org.json.JSONObject().put("uri", uri).put("name", "Bulk folder " + index)
                .put("persisted", false));
        }
        org.json.JSONArray tracks = new org.json.JSONArray();
        org.json.JSONArray ids = new org.json.JSONArray();
        for (int index = 0; index < bulkRemovalCount; index++) {
            String id = "bulk-fixture-" + index;
            tracks.put(new org.json.JSONObject().put("id", id).put("uri", audio.toString())
                .put("fileName", id + ".wav").put("title", id).put("artist", "Fixture artist")
                .put("album", "Bulk removal").put("durationMs", 60000).put("mimeType", "audio/wav")
                .put("sourceUris", new org.json.JSONObject()
                    .put(sources.getJSONObject(index % 5).getString("uri"), audio.toString())));
            ids.put(id);
        }
        org.json.JSONObject playlist = new org.json.JSONObject().put("id", "bulk-playlist")
            .put("name", "Kept playlist").put("trackIds", bulkRemovalRecovery ? new org.json.JSONArray() : ids);
        writeFixture(library, new org.json.JSONObject().put("version", 1)
            .put("tracks", bulkRemovalRecovery ? new org.json.JSONArray() : tracks)
            .put("sources", bulkRemovalRecovery ? new org.json.JSONArray() : sources)
            .put("playlists", new org.json.JSONArray().put(playlist)).toString());
        writeFixture(playback, new org.json.JSONObject().put("version", 1).put("queue", tracks)
            .put("index", bulkRemovalCount / 2).put("positionMs", 20000)
            .put("shuffle", false).put("repeat", 0).toString());
        check(getTargetContext().getSharedPreferences("settings", 0).edit()
            .putString("playOrder", "SEQUENTIAL").putString("loopMode", "OFF").commit(),
            "Cannot save bulk fixture settings");
    }

    private void verifyLargeFolderRemoval(Uri tree, Bundle result) throws Exception {
        check(controller.getPlaybackState().getState() != PlaybackState.STATE_PLAYING, "Launch must stay paused");
        if (!bulkRemovalRestart && !bulkRemovalRecovery) {
            await(() -> controller.getQueue() != null && controller.getQueue().size() == bulkRemovalCount);
            onMain(() -> controller.getTransportControls().play());
            await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
            tap(() -> activity.findViewById(id("nav_folders")));
            tap(() -> activity.findViewById(id("folderRemoveAll")));
            clickDialogAction("cancel");
            check(savedLibrary().getJSONArray("tracks").length() == bulkRemovalCount,
                "Cancelling removal changed the library");
            tap(() -> activity.findViewById(id("action_settings")));
            verifySettingsPage("categoryFolders", "settingsFoldersPanel");
            tap(() -> activity.findViewById(id("settingsRemoveAllFolders")));
            String remove = activity.getString(resource("remove", "string"));
            await(() -> dialogButton(remove) != null);
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            long[] previous = {0};
            long[] maximumGap = {0};
            Runnable heartbeat = new Runnable() {
                @Override public void run() {
                    long now = SystemClock.uptimeMillis();
                    maximumGap[0] = Math.max(maximumGap[0], now - previous[0]);
                    previous[0] = now;
                    handler.postDelayed(this, 16);
                }
            };
            long started = SystemClock.uptimeMillis();
            onMain(() -> {
                previous[0] = SystemClock.uptimeMillis();
                handler.post(heartbeat);
                check(dialogButton(remove).performClick(), "Remove confirmation was not handled");
            });
            try {
                await(() -> controller.getQueue() == null || controller.getQueue().isEmpty());
                await(() -> savedLibrary().optJSONArray("sources").length() == 0);
                SystemClock.sleep(200);
            } finally {
                onMain(() -> handler.removeCallbacks(heartbeat));
                result.putLong("removalMs", SystemClock.uptimeMillis() - started);
                result.putLong("maximumMainGapMs", maximumGap[0]);
            }
            check(maximumGap[0] < 1500, "Bulk removal blocked the main thread for " + maximumGap[0] + "ms");
        }
        await(() -> controller.getQueue() == null || controller.getQueue().isEmpty());
        await(() -> {
            try {
                return new org.json.JSONObject(fileText(new File(getTargetContext().getFilesDir(), "playback.json")))
                    .getJSONArray("queue").length() == 0;
            } catch (org.json.JSONException error) { throw new AssertionError(error); }
        });
        org.json.JSONObject library = savedLibrary();
        check(library.getJSONArray("tracks").length() == 0 && library.getJSONArray("sources").length() == 0,
            "Removed folder references returned");
        check(library.getJSONArray("playlists").length() == 1 &&
            library.getJSONArray("playlists").getJSONObject(0).getJSONArray("trackIds").length() == 0,
            "Playlist itself must remain, without removed references");
        check(controller.getPlaybackState().getState() != PlaybackState.STATE_PLAYING,
            "Removal or recovery must not autoplay");
        Bundle provider = getTargetContext().getContentResolver().call(tree, "scanFixtureState", null, null);
        check(provider.getInt("deleteRequests") == 0, "Removal deleted music");
        result.putInt("fixtureTracks", bulkRemovalCount);
        result.putBoolean("restart", bulkRemovalRestart);
        result.putBoolean("recovery", bulkRemovalRecovery);
        tap(() -> activity.findViewById(id("nav_library")));
        tap(() -> activity.findViewById(id("nav_player")));
    }

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

    private void verifyKaraoke(Uri tree, Uri audio) throws Exception {
        getTargetContext().getContentResolver().call(tree, "setLyricsMode", "available", null);
        setMusicUnavailable(tree, false);
        android.content.SharedPreferences preferences = getTargetContext().getSharedPreferences("settings", 0);
        check(!preferences.getBoolean("lyricKaraoke", false), "Karaoke must default off");
        tap(() -> activity.findViewById(id("action_settings")));
        verifySettingsPage("categoryFolders", "settingsFoldersPanel");
        ActivityMonitor picker = new ActivityMonitor(new IntentFilter(Intent.ACTION_OPEN_DOCUMENT_TREE),
            new ActivityResult(Activity.RESULT_OK, new Intent().setData(tree).addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)), true);
        try {
            tap(() -> activity.findViewById(id("settingsAddFolder")));
            chooseAndroidPicker(picker);
            await(() -> new File(getTargetContext().getFilesDir(), "library.json").isFile() &&
                savedLibrary().optJSONArray("sources") != null &&
                savedLibrary().optJSONArray("sources").length() == 1);
        } finally { removeMonitor(picker); }
        getTargetContext().startActivity(new Intent(Intent.ACTION_VIEW).setClassName(PACKAGE, PACKAGE + ".ui.MainActivity")
            .setDataAndType(audio, "audio/wav").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION));
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        onMain(() -> {
            controller.getTransportControls().pause();
            controller.getTransportControls().seekTo(7_500);
        });
        await(() -> pausedAt(7_500) && activeLyricText() != null);
        tap(() -> activity.findViewById(id("action_settings")));
        verifySettingsPage("categoryLyrics", "settingsLyricsPanel");
        tap(() -> activity.findViewById(id("settingsLyricKaraoke")));
        await(() -> preferences.getBoolean("lyricKaraoke", false));
        tap(() -> activity.findViewById(id("lyricKaraokeColor")));
        await(() -> colorDialog() != null);
        tap(() -> ((GridLayout) colorDialog().findViewById(id("colorGrid"))).getChildAt(4));
        clickDialogAction("apply");
        int color = preferences.getInt("lyricKaraokeColor", -1) | android.graphics.Color.BLACK;
        check(color != -1, "Karaoke color was not saved");
        tap(() -> activity.findViewById(id("nav_player")));
        await(() -> activeLyricText() != null && activeLyricText().isShown() && lyricColorPixels(color) > 0);
        onMain(() -> {
            TextView row = activeLyricText();
            check(row.getCurrentTextColor() != color, "Overlay must not replace the base text color");
            check(lyricColorPixels(row.getCurrentTextColor()) > 0, "Partial coverage lost the uncovered text");
            View mute = activity.findViewById(id("volumeMute"));
            check(mute.getLeft() == 0, "Mute control does not share its container's leading edge");
            check(mute.getWidth() == Math.round(48 * activity.getResources().getDisplayMetrics().density),
                "Mute touch target changed");
        });
        Activity previous = activity;
        onMain(previous::recreate);
        await(() -> activity != previous && activity.hasWindowFocus() && pausedAt(7_500) &&
            activeLyricText() != null && lyricColorPixels(color) > 0);
        check(preferences.getBoolean("lyricKaraoke", false) &&
            (preferences.getInt("lyricKaraokeColor", -1) | android.graphics.Color.BLACK) == color,
            "Recreation lost karaoke settings");
        int[] frozen = {0};
        onMain(() -> frozen[0] = lyricColorPixels(color));
        SystemClock.sleep(650);
        onMain(() -> check(frozen[0] == lyricColorPixels(color), "Paused karaoke continued moving"));
        onMain(() -> controller.getTransportControls().seekTo(6_000));
        await(() -> pausedAt(6_000) && lyricColorPixels(color) < frozen[0]);
        onMain(() -> { frozen[0] = lyricColorPixels(color); controller.getTransportControls().play(); });
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING &&
            activeLyricText() != null && "Synthetic start".contentEquals(activeLyricText().getText()) &&
            lyricColorPixels(color) > frozen[0]);
        onMain(() -> {
            controller.getTransportControls().pause();
            controller.getTransportControls().seekTo(7_500);
        });
        await(() -> pausedAt(7_500));
        saveScreenshot("karaoke-release-playing.png");
        android.media.AudioManager audioManager = getTargetContext().getSystemService(android.media.AudioManager.class);
        int volume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
        tap(() -> activity.findViewById(id("volumeMute")));
        await(() -> preferences.getBoolean("playerMuted", false));
        check(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == volume,
            "Player mute changed other apps' volume");
        tap(() -> activity.findViewById(id("volumeMute")));
        await(() -> !preferences.getBoolean("playerMuted", true));
        getContext().startActivity(new Intent().setClassName(PACKAGE + ".test",
            PACKAGE + ".FixtureWidgetHostActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(() -> widgetStatus().getInt("count") == 4 && widgetStatus().getInt("lyricSpanCount2") == 1 &&
            widgetStatus().getInt("lyricSpanEnd2") == 7);
        Bundle widget = widgetStatus();
        check(widget.getInt("uid") != Process.myUid() && widget.getInt("lyricSpanColor2") == color,
            "Karaoke spans did not survive the real widget process boundary");
        check(widget.getInt("lyricNextSpanCount2") == 0, "A future lyric was colored as current");
        saveScreenshot("karaoke-release-widget.png");
        widgetCommand("click", "2:widget_progress");
        await(() -> activity.hasWindowFocus() && activity.findViewById(id("controls")).isShown());
        check(pausedAt(7_500), "Opening the widget changed playback");
        tap(() -> activity.findViewById(id("action_settings")));
        verifySettingsPage("categoryLyrics", "settingsLyricsPanel");
        tap(() -> activity.findViewById(id("lyricResetAppearance")));
        await(() -> !preferences.getBoolean("lyricKaraoke", true) &&
            preferences.getInt("lyricKaraokeColor", -1) == 0xffd54f);
        tap(() -> activity.findViewById(id("nav_player")));
        await(() -> activeLyricText() != null && activeLyricText().isShown() && lyricColorPixels(color) == 0);
        check(pausedAt(7_500), "Appearance reset changed playback");
    }

    private TextView activeLyricText() {
        ViewGroup list = activity.findViewById(id("lyricsList"));
        if (list == null || !list.isShown()) return null;
        for (int index = 0; index < list.getChildCount(); index++) {
            View child = list.getChildAt(index);
            if (child instanceof TextView && child.isSelected()) return (TextView) child;
        }
        return null;
    }

    private int lyricColorPixels(int color) {
        TextView row = activeLyricText();
        if (row == null || row.getWidth() == 0 || row.getHeight() == 0) return 0;
        android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(row.getWidth(), row.getHeight(),
            android.graphics.Bitmap.Config.ARGB_8888);
        try {
            row.draw(new android.graphics.Canvas(image));
            int count = 0;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                if (image.getPixel(x, y) == color) count++;
            }
            return count;
        } finally { image.recycle(); }
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
        try {
            tap(() -> activity.findViewById(id("settingsAddFolder")));
            chooseAndroidPicker(picker);
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
        if (radioLayoutOnly || radioVisibilityOnly || radioVisibilityResumeOnly ||
            playbackModesOnly || startupMusicOnly || startupRadioOnly || startupPausedOnly || startupPassiveOnly)
            launch.setAction(Intent.ACTION_MAIN).putExtra("com.macarmusic.app.OPEN_PLAYER_ONLY", startupPassiveOnly);
        if (!systemControlsOnly) return startActivitySync(launch);
        launch.setAction(Intent.ACTION_VIEW).setDataAndType(audio, "audio/wav")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        getTargetContext().startActivity(launch);
        await(() -> activity != null);
        return activity;
    }

    private void remoteKey(int code) {
        long down = SystemClock.uptimeMillis();
        for (int action : new int[] {KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP})
            sendKeySync(new KeyEvent(down, SystemClock.uptimeMillis(), action, code, 0, 0,
                KeyEvent.KEYCODE_UNKNOWN, 0, 0, InputDevice.SOURCE_DPAD));
        waitForIdleSync();
    }

    private String focusName(Activity owner) {
        View focus = owner.getCurrentFocus();
        if (focus == null) return "none";
        if (focus.getId() != View.NO_ID && (focus.getId() & 0xff000000) != 0)
            return getTargetContext().getResources().getResourceEntryName(focus.getId());
        return focus.getClass().getSimpleName() + ":" + focus.getContentDescription();
    }

    private void verifyRemoteNavigation(Uri tree) throws Exception {
        StringBuilder trace = new StringBuilder();
        StringBuilder failures = new StringBuilder();
        if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            tap(() -> activity.findViewById(id("action_settings")));
            verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
            tap(() -> activity.findViewById(id("settingsLanguage")));
            tap(() -> windowText(activity.getString(resource("language_chinese", "string"))));
            await(() -> "zh".equals(activity.getResources().getConfiguration().locale.getLanguage()));
            tap(() -> activity.findViewById(id("nav_player")));
            waitForIdleSync();
        }
        verifyNavigationLabels();
        await(() -> controller.getQueue() != null && controller.getQueue().size() == 100);
        check(controller.getPlaybackState().getState() != PlaybackState.STATE_PLAYING, "Launch must not autoplay");
        await(() -> !activity.findViewById(id("rewind")).isEnabled() && !activity.findViewById(id("forward")).isEnabled());
        remoteKey(KeyEvent.KEYCODE_DPAD_UP);
        onMain(() -> check(activity.findViewById(id("previous")).requestFocus(), "Cannot focus unprepared transport"));
        remoteKey(KeyEvent.KEYCODE_DPAD_RIGHT);
        onMain(() -> {
            trace.append("Unprepared Right -> ").append(focusName(activity)).append('\n');
            check("play".equals(focusName(activity)), "Disabled seeking must be skipped without blocking Play: " +
                trace + " rewindFocusable=" + activity.findViewById(id("rewind")).isFocusable() +
                " rewindEnabled=" + activity.findViewById(id("rewind")).isEnabled());
        });
        onMain(() -> controller.getTransportControls().play());
        await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        onMain(() -> { controller.getTransportControls().pause(); controller.getTransportControls().seekTo(12_000); });
        await(() -> pausedAt(12_000) && activity.findViewById(id("rewind")).isEnabled()
            && activity.findViewById(id("forward")).isEnabled());
        remoteKey(KeyEvent.KEYCODE_DPAD_UP);
        onMain(() -> {
            for (String name : new String[] {"shuffle", "previous", "rewind", "play", "forward", "next", "repeat"}) {
                View button = activity.findViewById(id(name));
                trace.append(name).append(" enabled=").append(button.isEnabled())
                    .append(" focusable=").append(button.isFocusable())
                    .append(" touchFocusable=").append(button.isFocusableInTouchMode()).append('\n');
            }
            check(activity.findViewById(id("previous")).requestFocus(), "Cannot start transport focus");
        });
        for (String expected : new String[] {"rewind", "play", "forward", "next"}) {
            remoteKey(KeyEvent.KEYCODE_DPAD_RIGHT);
            onMain(() -> {
                String actual = focusName(activity);
                trace.append("Right -> ").append(actual).append(" expected=").append(expected).append('\n');
                if (!expected.equals(actual)) failures.append("Transport skips ").append(expected).append("; ");
            });
        }
        if (failures.length() == 0) {
            remoteKey(KeyEvent.KEYCODE_DPAD_LEFT);
            remoteKey(KeyEvent.KEYCODE_DPAD_CENTER);
            await(() -> pausedAt(22_000));
            remoteKey(KeyEvent.KEYCODE_DPAD_LEFT);
            remoteKey(KeyEvent.KEYCODE_DPAD_LEFT);
            remoteKey(KeyEvent.KEYCODE_DPAD_CENTER);
            await(() -> pausedAt(12_000));
            saveScreenshot("remote-transport.png");
        }
        getTargetContext().getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        onMain(() -> activity.startActivity(new Intent().setClassName(PACKAGE, PACKAGE + ".ui.FileExplorerActivity")
            .putExtra("explorerMode", "MUSIC_FILE")));
        await(() -> explorer != null && explorer.hasWindowFocus()
            && textView(explorer.getWindow().getDecorView(), "Synthetic queue fixture") != null);
        remoteKey(KeyEvent.KEYCODE_DPAD_UP);
        onMain(() -> {
            View list = explorer.findViewById(id("explorerList"));
            View label = textView(list, "Synthetic queue fixture");
            View row = label;
            while (row.getParent() != list) row = (View) row.getParent();
            trace.append("Explorer row clickable=").append(row.isClickable())
                .append(" focusable=").append(row.isFocusable()).append('\n');
            check(explorer.findViewById(id("explorerAndroidPicker")).requestFocus(), "Cannot start explorer focus");
        });
        remoteKey(KeyEvent.KEYCODE_DPAD_UP);
        boolean[] inside = {false};
        onMain(() -> {
            View focus = explorer.getCurrentFocus();
            View list = explorer.findViewById(id("explorerList"));
            for (View parent = focus; parent != null && parent != list;
                parent = parent.getParent() instanceof View ? (View) parent.getParent() : null) {
                if (parent.getParent() == list) inside[0] = true;
            }
            trace.append("Explorer Up -> ").append(focusName(explorer)).append(" rowFocused=").append(inside[0]).append('\n');
            if (!inside[0]) failures.append("Explorer rows skipped; ");
        });
        if (inside[0]) {
            for (int step = 0; step < 8; step++) {
                boolean[] chosen = {false};
                onMain(() -> chosen[0] = textView(explorer.getCurrentFocus(), "Synthetic queue fixture") != null);
                if (chosen[0]) break;
                remoteKey(KeyEvent.KEYCODE_DPAD_UP);
            }
            onMain(() -> check(textView(explorer.getCurrentFocus(), "Synthetic queue fixture") != null,
                "Remote cannot reach the authorized location"));
            remoteKey(KeyEvent.KEYCODE_DPAD_CENTER);
            await(() -> textView(explorer.findViewById(id("explorerList")), "Album") != null);
            await(() -> textView(explorer.getCurrentFocus(), "Album") != null);
            onMain(() -> check(textView(explorer.getCurrentFocus(), "Album") != null, "Cannot focus the first folder"));
            remoteKey(KeyEvent.KEYCODE_DPAD_CENTER);
            await(() -> textView(explorer.findViewById(id("explorerList")), "02 - Nested.WAV") != null);
            await(() -> textView(explorer.getCurrentFocus(), "02 - Nested.WAV") != null);
            check(pausedAt(12_000), "Browsing changed paused playback");
            saveScreenshot("remote-explorer-folder.png");
            trace.append("Nested folder/file retained remote focus without returning to toolbar\n");
            remoteKey(KeyEvent.KEYCODE_BACK);
            await(() -> textView(explorer.findViewById(id("explorerList")), "Album") != null);
            await(() -> textView(explorer.getCurrentFocus(), "Album") != null);
            trace.append("Remote Back returned to parent with focus in the list\n");
        }
        File folder = new File(getTargetContext().getExternalFilesDir(null), "test-screenshots");
        check(folder.isDirectory() || folder.mkdirs(), "Cannot write remote focus trace");
        try (FileOutputStream output = new FileOutputStream(new File(folder, "remote-focus.txt"))) {
            output.write(trace.toString().getBytes(StandardCharsets.UTF_8));
        }
        onMain(() -> explorer.finish());
        check(failures.length() == 0, failures.toString());
    }

    private void openLocationsExplorer() {
        tap(() -> activity.findViewById(id("nav_folders")));
        tap(() -> textView(activity.getWindow().getDecorView(), activity.getString(resource("add_folder", "string"))));
        await(() -> explorer != null && explorer.hasWindowFocus()
            && textView(explorer.findViewById(id("explorerList")),
                explorer.getString(resource("explorer_add_location", "string"))) != null);
        onMain(() -> {
            android.widget.HorizontalScrollView footer = explorer.findViewById(id("explorerActions"));
            ViewGroup root = explorer.findViewById(id("explorerRoot"));
            ViewGroup row = (ViewGroup) footer.getChildAt(0);
            View previous = null;
            int top = -1;
            int bottom = -1;
            for (String name : new String[] { "explorerAndroidPicker", "explorerCancel", "explorerSelect",
                    "explorerLocations", "explorerUp", "explorerRefresh" }) {
                View button = explorer.findViewById(id(name));
                check(button.getParent() == row, "Explorer controls must share one bottom row");
                if (previous == null) {
                    check(button.getLeft() == 0, "Picker actions must start at the left edge");
                    top = button.getTop();
                    bottom = button.getBottom();
                } else check(previous.getRight() < button.getLeft(), "Explorer action order or spacing changed");
                check(button.getTop() == top && button.getBottom() == bottom, "Explorer actions wrapped or misaligned");
                previous = button;
            }
            check(previous.getRight() == row.getWidth(), "Navigation actions must end at the right edge");
            check(root.getChildAt(root.getChildCount() - 1) == footer
                && explorer.findViewById(id("explorerList")).getBottom() <= footer.getTop()
                && footer.getBottom() == root.getHeight() - root.getPaddingBottom(),
                "Explorer actions must be below the list at the bottom of the window");
        });
    }

    private View locationRemoveButton(String name) {
        View label = textView(explorer.findViewById(id("explorerList")), name);
        if (label == null) return null;
        for (android.view.ViewParent parent = label.getParent(); parent instanceof View
                && parent != explorer.findViewById(id("explorerList")); parent = parent.getParent()) {
            View button = ((View) parent).findViewById(id("explorerRemoveLocation"));
            if (button != null) return button;
        }
        return null;
    }

    private boolean hasReadFolderAccess(Uri tree) {
        for (android.content.UriPermission permission : getTargetContext().getContentResolver().getPersistedUriPermissions())
            if (permission.getUri().equals(tree) && permission.isReadPermission()) return true;
        return false;
    }

    private void verifyExplorerLocations(Uri tree) throws Exception {
        android.content.ContentResolver resolver = getTargetContext().getContentResolver();
        int read = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        String rootName;
        Uri document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        try (android.database.Cursor cursor = resolver.query(document,
                new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME }, null, null, null)) {
            check(cursor != null && cursor.moveToFirst(), "Missing location fixture");
            rootName = cursor.getString(0);
        }
        ActivityMonitor grant = new ActivityMonitor(new IntentFilter(Intent.ACTION_OPEN_DOCUMENT_TREE),
            new ActivityResult(Activity.RESULT_OK, new Intent().setData(tree).addFlags(read
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)), true);
        try {
            check(controller.getPlaybackState().getState() != PlaybackState.STATE_PLAYING,
                "Location management must launch paused");
            if (!explorerLocationsResume) {
                resolver.takePersistableUriPermission(tree, read);
                openLocationsExplorer();
                String initialName = DocumentsContract.getTreeDocumentId(tree).substring(
                    DocumentsContract.getTreeDocumentId(tree).lastIndexOf('/') + 1);
                tap(() -> locationRemoveButton(initialName));
                clickDialogAction("cancel");
                check(locationRemoveButton(initialName) != null, "Cancel removed the location");
                check(hasReadFolderAccess(tree), "Cancel released folder access");
                tap(() -> locationRemoveButton(initialName));
                check(ownedPanel() != null, "Remove must use the player-owned panel");
                saveScreenshot("explorer-location-confirm.png");
                clickDialogAction("remove");
                await(() -> locationRemoveButton(initialName) == null
                    && !hasReadFolderAccess(tree));
                check(resolver.call(tree, "scanFixtureState", null, null).getInt("audioReads") == 0,
                    "Removing an unused location read audio");
                String grantToken = java.util.UUID.randomUUID().toString();
                getContext().startActivity(new Intent().setClassName(PACKAGE + ".test",
                    PACKAGE + ".FixtureGrantActivity").putExtra("cacheGrantToken", grantToken)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                Uri cache = DocumentsContract.buildTreeDocumentUri(PACKAGE + ".test.cache", "root");
                await(() -> getTargetContext().checkUriPermission(cache, Process.myPid(), Process.myUid(), read)
                    == PackageManager.PERMISSION_GRANTED
                    && grantToken.equals(resolver.call(cache, "cacheFixture", null, null).getString("grantToken")));
                addMonitor(grant);
                tap(() -> textView(explorer.getWindow().getDecorView(),
                    explorer.getString(resource("explorer_add_location", "string"))));
                removeMonitor(grant);
                tap(() -> explorer.findViewById(id("explorerSelect")));
                await(() -> {
                    org.json.JSONObject snapshot = scanSnapshot();
                    org.json.JSONArray tracks = snapshot == null ? null : snapshot.optJSONArray("tracks");
                    return activity.hasWindowFocus() && tracks != null && tracks.length() == 2
                        && tracks.optJSONObject(0).optLong("durationMs") >= 59000
                        && tracks.optJSONObject(1).optLong("durationMs") >= 59000;
                });
                String title = savedLibrary().getJSONArray("tracks").getJSONObject(0).getString("title");
                tap(() -> activity.findViewById(id("nav_library")));
                tap(() -> textView(activity.findViewById(id("libraryList")), title));
                await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
                String playing = controller.getMetadata().getDescription().getMediaId();
                String before = savedLibrary().toString();
                openLocationsExplorer();
                tap(() -> locationRemoveButton(rootName));
                clickDialogAction("remove");
                await(() -> locationRemoveButton(rootName) == null);
                check(before.equals(savedLibrary().toString()), "Location removal changed saved music");
                check(hasReadFolderAccess(tree), "Shared music access was released");
                check(controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING
                    && playing.equals(controller.getMetadata().getDescription().getMediaId()),
                    "Location removal interrupted playback");
                Activity previous = explorer;
                onMain(previous::recreate);
                await(() -> explorer != previous && explorer.hasWindowFocus()
                    && textView(explorer.findViewById(id("explorerList")),
                        explorer.getString(resource("explorer_add_location", "string"))) != null);
                check(locationRemoveButton(rootName) == null, "Removed location returned after recreation");
                saveScreenshot("explorer-location-removed.png");
            } else {
                check(savedLibrary().getJSONArray("sources").length() == 1
                    && savedLibrary().getJSONArray("tracks").length() == 2, "Saved songs were lost");
                check(hasReadFolderAccess(tree), "Required access did not survive process restart");
                openLocationsExplorer();
                check(locationRemoveButton(rootName) == null, "Hidden location returned after process restart");
                addMonitor(grant);
                tap(() -> textView(explorer.getWindow().getDecorView(),
                    explorer.getString(resource("explorer_add_location", "string"))));
                removeMonitor(grant);
                tap(() -> explorer.findViewById(id("explorerLocations")));
                await(() -> locationRemoveButton(rootName) != null);
                saveScreenshot("explorer-location-regranted.png");
            }
            check(resolver.call(tree, "scanFixtureState", null, null).getInt("deleteRequests") == 0,
                "Location management must never delete storage documents");
            check(grant.getHits() == 1, "The explicit regrant must use Android's tree picker exactly once");
        } finally {
            removeMonitor(grant);
            onMain(() -> {
                if (explorer != null && !explorer.isDestroyed()) explorer.finish();
                controller.getTransportControls().pause();
            });
        }
    }

    private void verifyFileExplorer(Uri tree, Uri audio) throws Exception {
        boolean legacy = Build.VERSION.SDK_INT <= 28;
        File directory = new File(android.os.Environment.getExternalStorageDirectory(), "000-ManaExplorer-optimized");
        File song = new File(directory, "Native explorer.wav");
        File backup = new File(directory, "Native backup.zip");
        ActivityMonitor grant = null;
        String rootName;
        if (legacy) {
            for (String permission : new String[] {
                "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"
            }) {
                if (Build.VERSION.SDK_INT >= 28) getUiAutomation().grantRuntimePermission(PACKAGE, permission);
                else try (InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    getUiAutomation().executeShellCommand("pm grant " + PACKAGE + " " + permission))) {
                    check(streamText(input).trim().isEmpty(), "Legacy fixture storage permission failed");
                }
            }
            check(!directory.exists() && directory.mkdir(), "Use a fresh disposable explorer fixture");
            try (InputStream input = getTargetContext().getContentResolver().openInputStream(audio);
                 FileOutputStream output = new FileOutputStream(song)) {
                byte[] bytes = new byte[8192];
                int count;
                while ((count = input.read(bytes)) >= 0) output.write(bytes, 0, count);
            }
            rootName = activity.getString(resource("device_shared_storage", "string"));
        } else {
            Uri document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
            try (android.database.Cursor cursor = getTargetContext().getContentResolver().query(document,
                new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME }, null, null, null)) {
                check(cursor != null && cursor.moveToFirst(), "Missing SAF fixture folder");
                rootName = cursor.getString(0);
            }
            grant = new ActivityMonitor(new IntentFilter(Intent.ACTION_OPEN_DOCUMENT_TREE),
                new ActivityResult(Activity.RESULT_OK, new Intent().setData(tree).addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)), true);
        }
        try {
            check(controller.getPlaybackState().getState() != PlaybackState.STATE_PLAYING, "Explorer must not autoplay");
            tap(() -> activity.findViewById(id("nav_folders")));
            tap(() -> textView(activity.getWindow().getDecorView(), activity.getString(resource("add_folder", "string"))));
            await(() -> explorer != null && explorer.hasWindowFocus());
            if (legacy) enterNativeFixture(rootName, directory.getName(), true);
            else {
                onMain(() -> check(textView(explorer.getWindow().getDecorView(),
                    activity.getString(resource("explorer_device_storage", "string"))) == null,
                    "Scoped Android must not offer raw storage"));
                addMonitor(grant);
                tap(() -> textView(explorer.getWindow().getDecorView(),
                    activity.getString(resource("explorer_add_location", "string"))));
                tap(() -> textView(explorer.getWindow().getDecorView(), "Album"));
                removeMonitor(grant);
            }
            tap(() -> explorer.findViewById(id("explorerSelect")));
            await(() -> activity.hasWindowFocus() && savedLibrary().optJSONArray("tracks") != null
                && savedLibrary().optJSONArray("tracks").length() == 1);
            check(controller.getPlaybackState().getState() != PlaybackState.STATE_PLAYING,
                "Adding a native folder started playback");
            String source = savedLibrary().getJSONArray("sources").getJSONObject(0).getString("uri");
            check(DocumentsContract.getDocumentId(Uri.parse(source)).endsWith(
                legacy ? directory.getName() : "music/album"), "Wrong selected subtree");
            org.json.JSONObject track = savedLibrary().getJSONArray("tracks").getJSONObject(0);
            check(track.getLong("durationMs") >= 59000, "Native metadata did not read the real WAV");
            String expectedName = legacy ? song.getName() : "02 - Nested.WAV";
            onMain(() -> {
                if (activity.findViewById(id("noticeBar")).isShown())
                    activity.findViewById(id("dismissNotice")).performClick();
            });
            getUiAutomation().waitForIdle(500, 5_000);
            tap(() -> textView(activity.getWindow().getDecorView(), activity.getString(resource("open_file", "string"))));
            enterNativeFixture(rootName, legacy ? directory.getName() : "Album", legacy);
            Activity previous = explorer;
            onMain(() -> previous.recreate());
            await(() -> explorer != previous && explorer.hasWindowFocus());
            tap(() -> textView(explorer.getWindow().getDecorView(), expectedName));
            await(() -> activity.hasWindowFocus() && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
            check(controller.getQueue().size() == 1, "Opening a file must play only that file");
            String selected = controller.getMetadata().getDescription().getMediaId();
            if (legacy) {
                check(selected.contains(PACKAGE + ".localdocuments"), "Native storage did not use its content bridge");
                Uri created = DocumentsContract.createDocument(getTargetContext().getContentResolver(),
                    Uri.parse(source), "application/zip", backup.getName());
                check(created != null, "Native backup destination creation failed");
                try (OutputStream output = getTargetContext().getContentResolver().openOutputStream(created, "wt")) {
                    output.write("Synthetic native backup".getBytes(StandardCharsets.UTF_8));
                }
                check(backup.length() > 0, "Native storage write was not persisted");
                long songBytes = song.length();
                boolean allowed = false;
                try (OutputStream output = getTargetContext().getContentResolver()
                    .openOutputStream(Uri.parse(track.getString("uri")), "wt")) {
                    allowed = output != null;
                } catch (java.io.FileNotFoundException | SecurityException expected) { }
                check(!allowed && song.length() == songBytes, "The native bridge allowed writing original music");
            }
            tap(() -> activity.findViewById(id("nav_folders")));
            tap(() -> textView(activity.getWindow().getDecorView(), activity.getString(resource("open_file", "string"))));
            enterNativeFixture(rootName, legacy ? directory.getName() : "Album", legacy);
            SystemClock.sleep(1200);
            check(controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING &&
                selected.equals(controller.getMetadata().getDescription().getMediaId()),
                "Opening or browsing the explorer interrupted playback");
            saveScreenshot("explorer-release.png");
            tap(() -> explorer.findViewById(id("explorerCancel")));
            await(() -> activity.hasWindowFocus());
        } finally {
            if (grant != null) removeMonitor(grant);
            onMain(() -> {
                if (explorer != null && !explorer.isDestroyed()) explorer.finish();
                controller.getTransportControls().stop();
            });
            if (legacy) {
                check(!backup.exists() || backup.delete(), "Could not remove the synthetic backup");
                check(!song.exists() || song.delete(), "Could not remove the synthetic audio");
                check(!directory.exists() || directory.delete(), "Could not remove the synthetic directory");
            }
        }
    }

    private void enterNativeFixture(String rootName, String folder, boolean legacy) {
        await(() -> explorer != null && explorer.hasWindowFocus());
        if (legacy) tap(() -> textView(explorer.getWindow().getDecorView(),
            activity.getString(resource("explorer_device_storage", "string"))));
        tap(() -> textView(explorer.getWindow().getDecorView(), rootName));
        tap(() -> textView(explorer.getWindow().getDecorView(), folder));
    }

    private void verifyExplorerStorageSimulation(Bundle result) throws Exception {
        check(Build.VERSION.SDK_INT == 28 && Build.PRODUCT.contains("sdk"),
            "Storage simulation is restricted to the owned API 28 emulator");
        check(explorerSimulationRoot != null && explorerSimulationRoot.matches("/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}") &&
            explorerSimulationVolume != null && explorerSimulationVolume.matches("public:7,[0-9]+"),
            "An emulator virtual removable volume must be supplied");
        check("true".equals(simulationShell("getprop persist.sys.virtual_disk").trim()),
            "Android virtual-disk mode must be enabled");
        if (Build.VERSION.SDK_INT >= 28) {
            getUiAutomation().grantRuntimePermission(PACKAGE, "android.permission.READ_EXTERNAL_STORAGE");
            getUiAutomation().grantRuntimePermission(PACKAGE, "android.permission.WRITE_EXTERNAL_STORAGE");
        }
        File internalRoot = android.os.Environment.getExternalStorageDirectory().getCanonicalFile();
        File removableRoot = new File(explorerSimulationRoot).getCanonicalFile();
        android.os.storage.StorageManager storage = (android.os.storage.StorageManager)
            getTargetContext().getSystemService(android.content.Context.STORAGE_SERVICE);
        boolean removable = false;
        if (Build.VERSION.SDK_INT >= 24) {
            for (android.os.storage.StorageVolume volume : storage.getStorageVolumes()) {
                if (volume.isRemovable() && removableRoot.getName().equalsIgnoreCase(volume.getUuid()) &&
                    android.os.Environment.MEDIA_MOUNTED.equals(volume.getState())) removable = true;
            }
        }
        check(removable && removableRoot.canRead(), "The supplied volume is not mounted removable storage");
        File[] directories = {
            new File(internalRoot, "000-ManaExplorer-Internal"),
            new File(removableRoot, "000-ManaExplorer-USB")
        };
        String[] labels = {
            activity.getString(resource("device_shared_storage", "string")),
            activity.getString(resource("device_storage_volume", "string"), removableRoot.getName())
        };
        String[] songs = {"Internal demo.wav", "USB demo.wav"};
        String[] selected = new String[2];
        check(!new File(getTargetContext().getFilesDir(), "library.json").exists() ||
            savedLibrary().optJSONArray("tracks") == null || savedLibrary().getJSONArray("tracks").length() == 0,
            "Simulation requires an empty disposable app library");
        for (int index = 0; index < directories.length; index++) {
            File directory = directories[index];
            check(directory.isDirectory() && new File(directory, songs[index]).isFile() &&
                new File(directory, "Album/\u6d88\u9632.wav").isFile() && new File(directory, "notes.txt").isFile(),
                "Synthetic files must be copied onto both volumes before opening the player: " + directory);
        }
        boolean unmounted = false;
        try {
            for (int index = 0; index < directories.length; index++) {
                final int expectedTracks = (index + 1) * 2;
                final String label = labels[index];
                final File directory = directories[index];
                tap(() -> activity.findViewById(id("nav_folders")));
                tap(() -> textView(activity.getWindow().getDecorView(), activity.getString(resource("add_folder", "string"))));
                await(() -> explorer != null && explorer.hasWindowFocus());
                tap(() -> textView(explorer.getWindow().getDecorView(),
                    activity.getString(resource("explorer_device_storage", "string"))));
                await(() -> textView(explorer.getWindow().getDecorView(), labels[0]) != null &&
                    textView(explorer.getWindow().getDecorView(), labels[1]) != null);
                if (index == 0) saveScreenshot("explorer-sim-01-volumes.png");
                tap(() -> textView(explorer.getWindow().getDecorView(), label));
                tap(() -> textView(explorer.getWindow().getDecorView(), directory.getName()));
                await(() -> textView(explorer.getWindow().getDecorView(), "Album") != null);
                onMain(() -> check(textView(explorer.getWindow().getDecorView(), "notes.txt") == null,
                    "Music selection must exclude non-audio files"));
                org.json.JSONArray imported = new File(getTargetContext().getFilesDir(), "library.json").exists()
                    ? savedLibrary().optJSONArray("tracks") : null;
                check((imported == null ? 0 : imported.length()) == index * 2, "Merely browsing imported music");
                check(controller.getPlaybackState().getState() != PlaybackState.STATE_PLAYING,
                    "Merely browsing started playback");
                saveScreenshot(index == 0 ? "explorer-sim-02-internal.png" : "explorer-sim-03-usb.png");
                tap(() -> explorer.findViewById(id("explorerSelect")));
                await(() -> activity.hasWindowFocus() && savedLibrary().optJSONArray("tracks") != null &&
                    savedLibrary().optJSONArray("tracks").length() == expectedTracks);
                check(savedLibrary().getJSONArray("sources").length() == index + 1,
                    "The selected folder was not retained");
                check(controller.getPlaybackState().getState() != PlaybackState.STATE_PLAYING,
                    "Folder import must remain paused");
                await(() -> textView(activity.getWindow().getDecorView(),
                    activity.getString(resource("cancel_scan", "string"))) == null);
                onMain(() -> {
                    if (activity.findViewById(id("noticeBar")).isShown())
                        activity.findViewById(id("dismissNotice")).performClick();
                });
                getUiAutomation().waitForIdle(500, 5_000);
            }
            org.json.JSONArray tracks = savedLibrary().getJSONArray("tracks");
            for (int index = 0; index < tracks.length(); index++) {
                org.json.JSONObject track = tracks.getJSONObject(index);
                check(track.getLong("durationMs") >= 59_000 && track.getLong("durationMs") <= 61_000,
                    "Real audio metadata was not read");
                check(track.getString("uri").contains(PACKAGE + ".localdocuments"),
                    "Playback references must use the app's content-URI bridge");
                for (int volume = 0; volume < songs.length; volume++) {
                    if (songs[volume].equals(track.getString("fileName"))) selected[volume] = track.getString("id");
                }
            }
            check(selected[0] != null && selected[1] != null, "Both source songs must be imported");
            tap(() -> textView(activity.getWindow().getDecorView(), activity.getString(resource("up_folder", "string"))));
            await(() -> textView(activity.getWindow().getDecorView(),
                activity.getString(resource("play_selected_folders", "string"))) != null);
            saveScreenshot("explorer-sim-04-both-folders.png");
            tap(() -> textView(activity.getWindow().getDecorView(),
                activity.getString(resource("play_selected_folders", "string"))));
            await(() -> controller.getQueue() != null && controller.getQueue().size() == 4 &&
                controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
            result.putInt("mixedFolderQueueSize", controller.getQueue().size());
            for (int index = 0; index < directories.length; index++) {
                final String song = songs[index];
                final String mediaId = selected[index];
                browseSimulationFolder(labels[index], directories[index]);
                await(() -> textView(explorer.getWindow().getDecorView(), song) != null);
                onMain(() -> check(textView(explorer.getWindow().getDecorView(), "notes.txt") == null,
                    "Open file must show music, not unrelated documents"));
                saveScreenshot(index == 0 ? "explorer-sim-05-internal-files.png" : "explorer-sim-06-usb-files.png");
                tap(() -> textView(explorer.getWindow().getDecorView(), song));
                await(() -> activity.hasWindowFocus() && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING &&
                    mediaId.equals(controller.getMetadata().getDescription().getMediaId()));
                check(controller.getQueue().size() == 1, "Opening one file must not enqueue the entire folder");
                saveScreenshot(index == 0 ? "explorer-sim-05-internal-playing.png" : "explorer-sim-06-usb-playing.png");
            }
            browseSimulationFolder(labels[0], directories[0]);
            tap(() -> textView(explorer.getWindow().getDecorView(), songs[0]));
            await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING &&
                selected[0].equals(controller.getMetadata().getDescription().getMediaId()));
            browseSimulationFolder(labels[1], directories[1]);
            await(() -> textView(explorer.getWindow().getDecorView(), songs[1]) != null);
            check(simulationShell("sm unmount " + explorerSimulationVolume).trim().isEmpty(),
                "Virtual-volume unmount failed");
            unmounted = true;
            await(() -> !directories[1].isDirectory());
            tap(() -> explorer.findViewById(id("explorerRefresh")));
            await(() -> !explorer.findViewById(id("explorerProgress")).isShown() &&
                ((TextView) explorer.findViewById(id("explorerStatus"))).getText().length() > 0);
            onMain(() -> result.putString("unmountedMessage",
                ((TextView) explorer.findViewById(id("explorerStatus"))).getText().toString()));
            check(!activity.getString(resource("explorer_empty", "string")).equals(result.getString("unmountedMessage")),
                "Unavailable storage must not be presented as an ordinary empty folder");
            check(savedLibrary().getJSONArray("tracks").length() == 4 &&
                savedLibrary().getJSONArray("sources").length() == 2, "Unavailable storage erased saved music");
            check(controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING &&
                selected[0].equals(controller.getMetadata().getDescription().getMediaId()),
                "Removing the virtual USB volume interrupted internal playback");
            saveScreenshot("explorer-sim-07-usb-unmounted.png");
            check(simulationShell("sm mount " + explorerSimulationVolume).trim().isEmpty(),
                "Virtual-volume remount failed");
            await(() -> directories[1].isDirectory());
            unmounted = false;
            tap(() -> explorer.findViewById(id("explorerRefresh")));
            await(() -> textView(explorer.getWindow().getDecorView(), songs[1]) != null);
            saveScreenshot("explorer-sim-08-usb-reconnected.png");
            tap(() -> textView(explorer.getWindow().getDecorView(), songs[1]));
            await(() -> activity.hasWindowFocus() && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING &&
                selected[1].equals(controller.getMetadata().getDescription().getMediaId()));
            check(savedLibrary().getJSONArray("tracks").length() == 4, "Reconnect duplicated saved music");
            result.putString("internalRoot", internalRoot.getPath());
            result.putString("removableRoot", removableRoot.getPath());
            result.putString("removableVolume", explorerSimulationVolume);
            result.putBoolean("removableRootWritableWithStoragePermission", removableRoot.canWrite());
            result.putInt("savedSources", savedLibrary().getJSONArray("sources").length());
            result.putInt("savedTracks", savedLibrary().getJSONArray("tracks").length());
            result.putBoolean("internalPlaybackSurvivedEject", true);
            result.putBoolean("remountedPlayback", true);
        } finally {
            onMain(() -> {
                if (explorer != null && !explorer.isDestroyed()) explorer.finish();
                controller.getTransportControls().stop();
            });
            if (unmounted) check(simulationShell("sm mount " + explorerSimulationVolume).trim().isEmpty(),
                "Could not restore the virtual volume after the simulation");
        }
    }

    private void browseSimulationFolder(String rootName, File directory) {
        tap(() -> activity.findViewById(id("nav_folders")));
        tap(() -> textView(activity.getWindow().getDecorView(), activity.getString(resource("open_file", "string"))));
        enterNativeFixture(rootName, directory.getName(), true);
    }

    private String simulationShell(String command) throws IOException {
        try (InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(
            getUiAutomation().executeShellCommand(command))) {
            return streamText(input);
        }
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
        onMain(() -> controller.getTransportControls().skipToQueueItem(controller.getQueue().get(0).getQueueId()));
        await(() -> first.equals(controller.getMetadata().getDescription().getMediaId()));
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

    private boolean systemButtonLabel(AccessibilityNodeInfo node, String label) {
        CharSequence description = node.getContentDescription() == null ? node.getText() : node.getContentDescription();
        String text = description == null ? "" : description.toString();
        return text.equalsIgnoreCase(label)
            || text.toLowerCase(java.util.Locale.ROOT).startsWith(label.toLowerCase(java.util.Locale.ROOT) + " ")
            || label.toLowerCase(java.util.Locale.ROOT).startsWith(text.toLowerCase(java.util.Locale.ROOT) + " ");
    }

    private AccessibilityNodeInfo systemButton(AccessibilityNodeInfo node, String label) {
        if (node.isClickable() && node.isVisibleToUser() && node.isEnabled() && systemButtonLabel(node, label)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo found = child == null ? null : systemButton(child, label);
            if (found != null) return found;
        }
        return null;
    }

    private void clickSystemButton(String label) throws Exception {
        android.accessibilityservice.AccessibilityServiceInfo service = getUiAutomation().getServiceInfo();
        service.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
            android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        getUiAutomation().setServiceInfo(service);
        long deadline = SystemClock.uptimeMillis() + 15_000;
        do {
            java.util.List<AccessibilityNodeInfo> roots = new java.util.ArrayList<>();
            roots.add(getUiAutomation().getRootInActiveWindow());
            for (android.view.accessibility.AccessibilityWindowInfo window : getUiAutomation().getWindows())
                roots.add(window.getRoot());
            for (AccessibilityNodeInfo root : roots) {
                if (root != null && "com.android.systemui".equals(String.valueOf(root.getPackageName())) && root.refresh()) {
                    AccessibilityNodeInfo button = systemButton(root, label);
                    if (button != null && button.refresh() && button.isVisibleToUser() && button.isEnabled()
                        && systemButtonLabel(button, label)) {
                        android.util.Log.i("OptimizedSmokeSystem", label + ": " + button.getViewIdResourceName() +
                            " / " + button.getContentDescription());
                        if (button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
                    }
                    if (button != null) button.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
                }
            }
            Thread.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        saveScreenshot("system-release-shade.png");
        StringBuilder nodes = new StringBuilder();
        for (android.view.accessibility.AccessibilityWindowInfo window : getUiAutomation().getWindows())
            describeSystemNode(window.getRoot(), nodes, 0);
        File report = new File(getTargetContext().getExternalFilesDir(null), "test-screenshots/system-ui-nodes.txt");
        try (FileOutputStream output = new FileOutputStream(report)) {
            output.write(nodes.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        throw new AssertionError("System media button unavailable: " + label);
    }

    private void describeSystemNode(AccessibilityNodeInfo node, StringBuilder output, int depth) {
        if (node == null || depth > 40) return;
        output.append(depth).append(' ').append(node.getViewIdResourceName()).append(" text=")
            .append(node.getText()).append(" description=").append(node.getContentDescription())
            .append(" clickable=").append(node.isClickable()).append(" visible=").append(node.isVisibleToUser())
            .append(" enabled=").append(node.isEnabled()).append('\n');
        for (int index = 0; index < node.getChildCount(); index++)
            describeSystemNode(node.getChild(index), output, depth + 1);
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
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        String close = activity.getString(resource("close", "string"));
        await(() -> dialogButton(close) != null && dialogButton(close).isFocused());
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
        await(() -> windowText(expected).isFocused());
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
        await(() -> dialogButton(close).isFocused());
        check(controller.getPlaybackState().getState() == state, "System information changed playback");
        saveScreenshot("system-info-release.png");
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        await(() -> ownedPanel() == null);
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
        setMusicUnavailable(music, false);
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
        try {
            tap(() -> activity.findViewById(id("settingsAddFolder")));
            chooseAndroidPicker(musicPicker);
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
        try {
            tap(() -> activity.findViewById(id("action_settings")));
            verifySettingsPage("categoryLyrics", "settingsLyricsPanel");
            tap(() -> activity.findViewById(id("lyricsCacheMove")));
            clickDialogAction("lyrics_cache_choose");
            chooseAndroidPicker(cachePicker);
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
        try {
            tap(() -> activity.findViewById(id("lyricsCacheMove")));
            clickDialogAction("lyrics_cache_choose");
            chooseAndroidPicker(picker);
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
        View notice = activity.findViewById(id("noticeBar"));
        if (notice != null && notice.isShown()) {
            tap(() -> activity.findViewById(id("dismissNotice")));
            await(() -> !notice.isShown());
        }
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
        try {
            tap(() -> activity.findViewById(id("action_settings")));
            tap(() -> activity.findViewById(id("categoryGeneral")));
            tap(() -> activity.findViewById(id("settingsBackup")));
            chooseAndroidPicker(save);
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
            chooseAndroidPicker(restore);
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

    private void chooseAndroidPicker(ActivityMonitor picker) throws Exception {
        await(() -> explorer != null && explorer.hasWindowFocus());
        Activity selecting = explorer;
        // An action-only IntentFilter also matches the native activity's null action.
        addMonitor(picker);
        try {
            tap(() -> selecting.findViewById(id("explorerAndroidPicker")));
            await(() -> selecting.isFinishing() || selecting.isDestroyed());
            check(picker.getHits() == 1, "The Android picker must receive exactly one selection request");
        } finally { removeMonitor(picker); }
    }

    private void verifyBulkFolderRemoval(Uri tree) throws Exception {
        ActivityMonitor picker = new ActivityMonitor(new IntentFilter(Intent.ACTION_OPEN_DOCUMENT_TREE),
            new ActivityResult(Activity.RESULT_OK, new Intent().setData(tree)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)), true);
        try {
            tap(() -> activity.findViewById(id("categoryFolders")));
            tap(() -> activity.findViewById(id("settingsAddFolder")));
            chooseAndroidPicker(picker);
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
        await(() -> activity.hasWindowFocus() || explorer != null && explorer.hasWindowFocus());
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
            String appName = activity.getString(resource("app_name", "string"));
            String windowTitle = activity.getTitle().toString();
            String playingPrefix = activity.getString(resource("now_playing_window_title", "string"), appName, "");
            check(windowTitle.equals(appName) ||
                windowTitle.startsWith(playingPrefix) && windowTitle.length() > playingPrefix.length(),
                "Window title must retain the localized brand and optional current name");
            View title = textView(toolbar, windowTitle);
            check(title != null, "The player toolbar must display the current window title");
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
                if (button.getVisibility() != View.VISIBLE) continue;
                View icon = button.getChildAt(0);
                TextView label = (TextView) button.getChildAt(1);
                float scale = button.isSelected() ? 1.2f : 1f;
                float density = activity.getResources().getDisplayMetrics().density;
                check(icon.getWidth() == Math.round((int) (24 * density) * scale),
                    name + " selected icon must use 120% sizing");
                float expectedText = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                    12 * scale, activity.getResources().getDisplayMetrics());
                check(Math.abs(label.getTextSize() - expectedText) < 0.05f, name + " selected text must use 120% sizing");
                if ("zh".equals(activity.getResources().getConfiguration().locale.getLanguage())) {
                    String expected = "nav_player".equals(name) ? "播放" :
                        "nav_library".equals(name) ? "乐库" : "nav_queue".equals(name) ? "队列" : null;
                    if (expected != null) check(expected.contentEquals(label.getText()), "Wrong short Chinese tab: " + name);
                }
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

    private String expectedAboutText() {
        StringBuilder text = new StringBuilder();
        for (String name : new String[] {"about_intro", "playback_mode_help", "about_body", "about_system_controls",
            "about_lyric_cache"}) {
            if (text.length() > 0) text.append("\n\n");
            text.append(activity.getText(resource(name, "string")));
        }
        appendExpectedHelp(text, new String[][] {
            {"autoplay_on_launch", "autoplay_on_launch_help"},
            {"keep_screen_on", "keep_screen_on_help"},
            {"notice_duration", "notice_duration_help"},
            {"recovery_details", "recovery_help"},
            {"directional_browsing", "directional_browsing_help"},
            {"mini_player_transparency", "mini_player_transparency_help"},
            {"navigation_buttons", "navigation_buttons_help"},
            {"radio_enabled", "radio_enabled_help"},
            {"appearance_logo", "appearance_help"},
            {"primary_control_color", "primary_control_color_help"},
            {"control_blocks", "control_blocks_help"},
            {"background_choose", "background_help"},
            {"background_interval", "slideshow_help"},
            {"home_widgets", "widgets_help"},
            {"backup_export", "backup_help"},
            {"category_playback", "playback_settings_help"},
            {"spectrum", "spectrum_help"},
            {"lyrics", "lyrics_help"}
        });
        StringBuilder providers = new StringBuilder();
        String[] names = {"provider_kugou_direct", "provider_kugou_mobile", "provider_netease", "provider_qq", "provider_kuwo"};
        for (int index = 0; index < names.length; index++) {
            if (index > 0) providers.append('\n');
            providers.append(activity.getString(resource("lyrics_provider_step", "string"),
                index + 1, activity.getString(resource(names[index], "string"))));
        }
        text.append("\n\n").append(activity.getText(resource("lyrics_provider_order_title", "string"))).append('\n')
            .append(activity.getString(resource("lyrics_provider_order", "string"), providers.toString()));
        appendExpectedHelp(text, new String[][] {{"select_folders", "multiple_folders_help"}});
        for (String name : new String[] {"radio_help_title", "radio_help", "radio_subscription_help",
            "radio_station_logos_help", "radio_bundled_defaults_help"}) {
            text.append("\n\n").append(activity.getText(resource(name, "string")));
        }
        appendExpectedHelp(text, new String[][] {
            {"radio_refresh_interval", "radio_refresh_help"},
            {"radio_navigation", "radio_navigation_help"},
            {"radio_open_player", "radio_open_player_help"},
            {"radio_manage_groups", "radio_groups_help"},
            {"radio_buffer", "radio_buffer_help"},
            {"radio_retries", "radio_retries_help"},
            {"radio_show_status", "radio_status_help"},
            {"radio_pin_color", "radio_pin_color_help"},
            {"radio_download_pinned_logos", "radio_pinned_logos_help"},
            {"radio_export", "radio_export_help"}
        });
        return text.toString();
    }

    private void appendExpectedHelp(StringBuilder text, String[][] sections) {
        for (String[] section : sections) text.append("\n\n")
            .append(activity.getText(resource(section[0], "string"))).append('\n')
            .append(activity.getText(resource(section[1], "string")));
    }

    private void verifySettingsCleanup() throws Exception {
        verifyRadioMusicBaseline();
        openRadioSettings();
        java.util.Map<String, ?> before = getTargetContext().getSharedPreferences("settings", 0).getAll();
        String[][] pages = {
            {"General", "settingsLanguage settingsAutoPlay settingsKeepScreenOn settingsNoticeDuration " +
                "settingsTheme settingsBackgroundImage backgroundInterval backgroundDuration backgroundTransition " +
                "backgroundBlur settingsBackgroundTint backgroundTintStrength settingsAccent settingsAppLogo settingsNavigationPosition " +
                "settingsShowLibrary settingsShowQueue settingsShowPlaylists settingsRadio settingsButtonIcons settingsPrimaryControlColor " +
                "settingsControlBlocks settingsBlockStyle settingsBlockCharacter blockArc settingsBlockShadow " +
                "settingsTransition settingsDefaultPlayer settingsWidgets " +
                "widgetTransparency settingsBackup settingsRestore settingsSystemInfo"},
            {"Playback", "settingsLoop settingsOrder settingsMainTimeMode settingsTimeMode miniPlayerTransparency"},
            {"Radio", "radioSubscriptions radioRefreshInterval radioNavigation radioOpenPlayer radioReturnMusic " +
                "radioManageGroups radioShowGroups radioBuffer radioStartBuffer radioRetries radioAutoSource radioShowStatus " +
                "radioPinColor radioButtonLogo radioDownloadLogos radioDownloadPinnedLogos radioCancelLogos radioClearCache " +
                "radioImport radioExport radioFormat radioReset radioAbout"},
            {"Spectrum", "settingsSpectrum spectrumPosition spectrumStyle spectrumBands spectrumFireStyle spectrumOrigin " +
                "spectrumPalette spectrumColorLow spectrumColorMiddle spectrumColorHigh spectrumResetColors spectrumPeaks " +
                "spectrumFalloff spectrumPeakFalloff spectrumRefreshRate"},
            {"Lyrics", "settingsLyrics settingsOnlineLyrics settingsLyricDragSeek settingsLyricSmoothScroll " +
                "lyricsLandscapePosition lyricsPortraitPosition lyricActiveSize lyricActiveColor lyricOtherSize lyricOtherColor " +
                "settingsLyricKaraoke lyricKaraokeColor lyricResetAppearance lyricsCacheMove lyricsCacheInternal " +
                "lyricsCacheInternalExpiry lyricsCachePortableExpiry lyricsCacheInspect lyricsCacheClear"},
            {"Folders", "settingsAddFolder settingsDeviceMusic settingsFolders settingsPlayFolders settingsPinFolders " +
                "settingsManageFolders settingsRemoveAllFolders"}
        };
        for (String[] page : pages) {
            String panel = "settings" + page[0] + "Panel";
            verifySettingsPage("category" + page[0], panel);
            onMain(() -> {
                java.util.List<String> controls = new java.util.ArrayList<>();
                collectSettingsControls(activity.findViewById(id(panel)), controls);
                check(controls.equals(java.util.Arrays.asList(page[1].split(" "))),
                    "Settings control order changed in " + page[0] + ": " + controls);
                activity.findViewById(id("settingsScroll")).scrollTo(0, 0);
                check(before.equals(getTargetContext().getSharedPreferences("settings", 0).getAll()),
                    "Browsing " + page[0] + " changed saved preferences");
            });
            waitForIdleSync();
            saveScreenshot("settings152-release-" + page[0].toLowerCase(java.util.Locale.ROOT) + ".png");
            verifyRadioMusicBaseline();
        }
        tap(() -> activity.findViewById(id("nav_player")));
    }

    private void collectSettingsControls(View root, java.util.List<String> controls) {
        if (root instanceof android.widget.Button || root instanceof Spinner || root instanceof android.widget.SeekBar)
            controls.add(root.getResources().getResourceEntryName(root.getId()));
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) collectSettingsControls(group.getChildAt(index), controls);
        }
    }

    private void verifyAboutFocus(FixtureRadioNetwork network) throws Exception {
        verifyRadioMusicBaseline();
        openRadioSettings();
        verifySettingsPage("categoryGeneral", "settingsGeneralPanel");
        tap(() -> activity.findViewById(id("settingsButtonIcons")));
        tap(() -> focusedDialogView("iconsCopilotAurora"));
        await(() -> "COPILOT_AURORA".equals(getTargetContext().getSharedPreferences("settings", 0)
            .getString("buttonIcons", "")));
        onMain(() -> check(activity.findViewById(id("settingsButtonIcons")).requestFocusFromTouch(),
            "Button theme selector must accept focus"));
        setInTouchMode(true);
        tap(() -> activity.findViewById(id("settingsButtonIcons")));
        await(() -> focusedDialogView("iconsCopilotAurora") != null);
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        await(() -> focusedDialogView("iconsCopilotAurora").isFocused());
        onMain(() -> check(((android.widget.Checkable) focusedDialogView("iconsCopilotAurora")).isChecked(),
            "Remote focus must start on the current theme"));
        saveScreenshot("focus1490-release-current-theme.png");
        int[] nextThemeDirection = {KeyEvent.KEYCODE_DPAD_RIGHT};
        onMain(() -> {
            if (focusedDialogView("iconsCopilotSunrise").getTop() > focusedDialogView("iconsCopilotAurora").getTop())
                nextThemeDirection[0] = KeyEvent.KEYCODE_DPAD_DOWN;
            check(((ImageView) focusedDialogView("playerDialogLogo")).getDrawable() != null,
                "The app-owned dialog must show its program logo");
        });
        sendKeyDownUpSync(nextThemeDirection[0]);
        await(() -> focusedDialogView("iconsCopilotSunrise").isFocused());
        onMain(() -> check(((android.widget.Checkable) focusedDialogView("iconsCopilotAurora")).isChecked(),
            "Moving focus must not select another theme"));
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        resumeRemoteFocus();
        await(() -> activity.hasWindowFocus() && activity.findViewById(id("settingsButtonIcons")).isFocused());
        verifySettingsPage("categorySpectrum", "settingsSpectrumPanel");
        onMain(() -> check(activity.findViewById(id("settingsSpectrum")).requestFocusFromTouch(),
            "Spectrum control not focusable"));
        tap(() -> activity.findViewById(id("categoryGeneral")));
        resumeRemoteFocus();
        await(() -> activity.findViewById(id("settingsButtonIcons")).isFocused());
        verifySettingsPage("categorySpectrum", "settingsSpectrumPanel");
        resumeRemoteFocus();
        await(() -> activity.findViewById(id("settingsSpectrum")).isFocused());
        tap(() -> activity.findViewById(id("categoryGeneral")));
        resumeRemoteFocus();
        await(() -> activity.findViewById(id("settingsButtonIcons")).isFocused());

        for (String language : new String[] {"en", "zh"}) {
            tap(() -> activity.findViewById(id("settingsLanguage")));
            onMain(() -> {
                android.widget.ListView choices = (android.widget.ListView) focusedDialogView("playerDialogList");
                int index = language.equals("zh") ? 1 : 0;
                choices.performItemClick(choices.getChildAt(index), index, choices.getAdapter().getItemId(index));
            });
            await(() -> activity.hasWindowFocus() &&
                language.equals(activity.getResources().getConfiguration().locale.getLanguage()));
            verifySettingsPage("categoryAbout", "settingsAboutPanel");
            onMain(() -> {
                TextView body = activity.findViewById(id("aboutBody"));
                CharSequence text = body.getText();
                check(expectedAboutText().contentEquals(text), "About text is incomplete");
                check(text.toString().contains("Ma^__^aM"), "Missing original author credit");
                check(text.toString().contains("GitHub Copilot"), "Missing AI-assistance credit");
                check(text instanceof android.text.Spanned, "Help headings lost their native formatting");
                android.text.Spanned styled = (android.text.Spanned) text;
                android.text.style.RelativeSizeSpan[] headings =
                    styled.getSpans(0, styled.length(), android.text.style.RelativeSizeSpan.class);
                check(headings.length == 72, "Expected every Help topic to have a larger heading");
                for (android.text.style.RelativeSizeSpan heading : headings) {
                    check(heading.getSizeChange() > 1f, "Heading is not larger than body text");
                    boolean bold = false;
                    for (android.text.style.StyleSpan style : styled.getSpans(styled.getSpanStart(heading),
                        styled.getSpanEnd(heading), android.text.style.StyleSpan.class))
                        if ((style.getStyle() & android.graphics.Typeface.BOLD) != 0) bold = true;
                    check(bold, "Help heading is not bold");
                }
                check(((TextView) activity.findViewById(id("aboutHeading"))).getTypeface().isBold(),
                    "App heading is not bold");
                activity.findViewById(id("settingsScroll")).scrollTo(0, 0);
            });
            saveScreenshot("focus1490-release-about-" + language + "-top.png");
            onMain(() -> {
                TextView body = activity.findViewById(id("aboutBody"));
                int line = body.getLayout().getLineForOffset(body.getText().toString().indexOf("Ma^__^aM"));
                body.requestRectangleOnScreen(new Rect(0, body.getLayout().getLineTop(Math.max(0, line - 1)),
                    body.getWidth(), body.getLayout().getLineBottom(line + 1)), true);
            });
            saveScreenshot("focus1490-release-about-" + language + "-credit.png");
            onMain(() -> {
                TextView body = activity.findViewById(id("aboutBody"));
                body.requestRectangleOnScreen(new Rect(0, body.getHeight() - 80, body.getWidth(), body.getHeight()), true);
            });
            saveScreenshot("focus1490-release-about-" + language + "-end.png");
            tap(() -> activity.findViewById(id("categoryGeneral")));
        }

        String station = "radio:cnr:jjzs";
        tap(() -> activity.findViewById(id("nav_radio")));
        tap(() -> radioGrid("radioList").findViewWithTag(station));
        await(() -> station.equals(mediaId()) && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        tap(() -> activity.findViewById(id("nav_player")));
        await(() -> radioGrid("radioQuickStations").findViewWithTag(station) != null);
        int manifests = network.manifests.get();
        tap(() -> radioGrid("radioQuickStations").findViewWithTag(station));
        resumeRemoteFocus();
        await(() -> activity.getCurrentFocus() != null && station.equals(activity.getCurrentFocus().getTag()));
        verifyMainHeaderKeys();
        onMain(() -> check(radioGrid("radioQuickStations").findViewWithTag(station).requestFocusFromTouch(),
            "Previously used station must remain focusable after visiting the header"));
        for (String page : new String[] {"nav_library", "nav_folders", "nav_queue", "nav_playlists", "action_settings"}) {
            tap(() -> activity.findViewById(id(page)));
            tap(() -> activity.findViewById(id("nav_player")));
            resumeRemoteFocus();
            try {
                await(() -> activity.getCurrentFocus() != null && station.equals(activity.getCurrentFocus().getTag()));
            } catch (AssertionError error) {
                String[] state = {""};
                onMain(() -> {
                    View focused = activity.getCurrentFocus();
                    state[0] = "focus=" + focused + ", tag=" + (focused == null ? null : focused.getTag()) +
                        ", touchMode=" + activity.getWindow().getDecorView().isInTouchMode();
                });
                throw new AssertionError("Return from " + page + ": " + state[0], error);
            }
        }
        Activity previous = activity;
        onMain(previous::recreate);
        await(() -> activity != previous && activity.hasWindowFocus());
        resumeRemoteFocus();
        await(() -> activity != previous && activity.getCurrentFocus() != null &&
            station.equals(activity.getCurrentFocus().getTag()));
        check(controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING &&
            manifests == network.manifests.get(), "Focus restoration restarted radio");
        saveScreenshot("focus1490-release-returned-pin.png");
        onMain(() -> controller.getTransportControls().pause());
        await(this::radioStopped);
    }

    private void verifyMainHeaderKeys() throws Exception {
        View[] first = new View[1];
        onMain(() -> {
            first[0] = radioGrid("radioQuickStations").getChildAt(0);
            check(first[0] != null && first[0].requestFocusFromTouch(), "The first pin must accept remote focus");
            check(!((ViewGroup) activity.findViewById(id("toolbar"))).getTouchscreenBlocksFocus(),
                "Touchscreen support must not exclude the toolbar from D-pad navigation");
        });
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
        await(() -> activity.getCurrentFocus() != null && activity.getCurrentFocus().getId() == id("homeLogo"));
        onMain(() -> check(hasVisibleFocus(activity.getCurrentFocus()), "App logo has no visible focus outline"));
        saveScreenshot("update1500-release-header-logo.png");
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
        await(() -> activity.getCurrentFocus() != null && activity.getCurrentFocus().getId() == id("action_settings"));
        onMain(() -> check(hasVisibleFocus(activity.getCurrentFocus()), "Settings has no visible focus outline"));
        saveScreenshot("update1500-release-header-settings.png");
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);
        await(() -> activity.getCurrentFocus() != null && activity.getCurrentFocus().getId() == id("homeLogo"));
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        await(() -> first[0].isFocused());
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
        await(() -> activity.getCurrentFocus() != null && activity.getCurrentFocus().getId() == id("action_settings"));
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        await(() -> activity.findViewById(id("settingsPanel")).isShown());
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        await(() -> first[0].isShown() && first[0].isFocused());
    }

    private boolean hasVisibleFocus(View view) {
        if (!view.isFocused() || view.getForeground() == null) return false;
        for (int state : view.getForeground().getState())
            if (state == android.R.attr.state_focused) return true;
        return false;
    }

    private void resumeRemoteFocus() {
        await(() -> activity.hasWindowFocus());
        boolean[] touchMode = {false};
        onMain(() -> touchMode[0] = activity.getWindow().getDecorView().isInTouchMode());
        if (touchMode[0]) sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
    }

    private void verifyColorPickers() throws Exception {
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
        verifyColorPicker("settingsPrimaryControlColor");
        verifyCustomColor("settingsPrimaryControlColor", "primaryControlColor", false);
    }

    private void verifyColorPicker(String button) throws Exception {
        for (boolean apply : new boolean[] {false, true}) {
            tap(() -> activity.findViewById(id(button)));
            await(() -> colorDialog() != null);
            verifyPickerFocus(() -> ((GridLayout) colorDialog().findViewById(id("colorGrid"))).getChildAt(0));
            if (button.equals("settingsAccent") && !apply) saveScreenshot("focus1482-release-color-picker.png");
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

    private void verifyCustomColor(String button, String key, boolean recreates) throws Exception {
        android.content.SharedPreferences preferences = getTargetContext().getSharedPreferences("settings", 0);
        int original = preferences.getInt(key, -1);
        tap(() -> activity.findViewById(id(button)));
        await(() -> colorDialog() != null);
        openColorEditor();
        for (String input : new String[] {"customColorInput", "colorRed", "colorGreen", "colorBlue"})
            verifyPickerFocus(() -> colorEditor().findViewById(id(input)));
        if (button.equals("settingsAccent")) saveScreenshot("focus1482-release-rgb-editor.png");
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
        });
        verifyPickerFocus(() -> ((GridLayout) colorDialog().findViewById(id("colorGrid"))).getChildAt(7));
        onMain(() -> colorDialog().findViewById(android.R.id.button1).performClick());
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

    private void verifyPickerFocus(Target target) {
        await(() -> target.get() != null && target.get().hasWindowFocus() && target.get().getWidth() > 0);
        setInTouchMode(false);
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
        onMain(() -> {
            View view = target.get();
            check(view.requestFocus(), "Picker control cannot receive remote focus");
            view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true);
        });
        await(() -> target.get().isFocused() && !target.get().isLayoutRequested());
        onMain(() -> {
            View view = target.get();
            android.graphics.drawable.Drawable focus = view.getForeground();
            check(focus != null, "Picker control has no focus outline");
            if (focus instanceof android.graphics.drawable.LayerDrawable) {
                android.graphics.drawable.LayerDrawable layers = (android.graphics.drawable.LayerDrawable) focus;
                focus = layers.getDrawable(layers.getNumberOfLayers() - 1);
            }
            android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(
                view.getWidth(), view.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
            try {
                focus.setBounds(0, 0, image.getWidth(), image.getHeight());
                focus.draw(new android.graphics.Canvas(image));
                java.util.Set<Integer> colors = new java.util.HashSet<>();
                int ink = 0;
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                    int color = image.getPixel(x, y);
                    if (android.graphics.Color.alpha(color) == 255) { ink++; colors.add(color); }
                }
                check(ink >= Math.min(image.getWidth(), image.getHeight()) * 2 && colors.size() >= 2,
                    "Picker control needs a visible two-tone focus outline");
            } finally { image.recycle(); }
        });
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
        tap(target, null);
    }

    private void tap(Target target, Runnable beforeInput) {
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
            Rect bounds = new Rect();
            view.getDrawingRect(bounds);
            view.requestRectangleOnScreen(bounds, true);
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
            // Single-line TextViews can have a large internal content scroll offset.
            visible.offset(-view.getScrollX(), -view.getScrollY());
            check(visible.intersect(0, 0, view.getWidth(), view.getHeight()), "Visible touch point is outside the control");
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            point[0] = location[0] + visible.exactCenterX();
            point[1] = location[1] + visible.exactCenterY();
        });
        if (beforeInput != null) beforeInput.run();
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
