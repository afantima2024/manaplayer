package com.macarmusic.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

// No AndroidX/Kotlin test runtime: debug tests otherwise depend on classes renamed by release R8.
public class OptimizedSmokeInstrumentation extends Instrumentation {
    private static final String PACKAGE = "com.macarmusic.app";
    private Activity activity;
    private MediaBrowser browser;
    private MediaController controller;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        ActivityMonitor monitor = null;
        try {
            ApplicationInfo info = getTargetContext().getApplicationInfo();
            check(info.minSdkVersion == 29, "Android 10 must be the minimum");
            check((info.flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0, "Expected optimized release code");
            getTargetContext().getSharedPreferences("settings", 0).edit()
                .putBoolean("parkedNoticeSeen", true).commit();
            getContext().startActivity(new Intent().setClassName(PACKAGE + ".test",
                PACKAGE + ".FixtureGrantActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Uri tree = DocumentsContract.buildTreeDocumentUri(PACKAGE + ".test.documents", "music");
            await(() -> getTargetContext().checkUriPermission(tree, Process.myPid(), Process.myUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED);
            Uri audio = DocumentsContract.buildDocumentUriUsingTree(tree, "music/one");
            monitor = new ActivityMonitor() {
                @Override public ActivityResult onStartActivity(Intent intent) {
                    return Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction())
                        ? new ActivityResult(Activity.RESULT_OK,
                            new Intent().setData(audio).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        : null;
                }
            };
            addMonitor(monitor);
            activity = startActivitySync(new Intent().setClassName(PACKAGE, PACKAGE + ".ui.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            CountDownLatch connected = new CountDownLatch(1);
            runOnMainSync(() -> {
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
            check(controller.getPlaybackState().getState() != PlaybackState.STATE_PLAYING, "Unexpected autoplay");
            tap(() -> activity.findViewById(id("nav_folders")));
            String open = getTargetContext().getString(resource("open_file", "string"));
            tap(() -> textView(activity.getWindow().getDecorView(), open));
            await(() -> controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING
                && controller.getMetadata() != null);
            check(controller.getMetadata().getDescription().getTitle().toString().contains("Synthetic"),
                "Expected actual synthetic audio metadata");
            runOnMainSync(() -> {
                controller.getTransportControls().pause();
                controller.getTransportControls().seekTo(10_000);
            });
            await(() -> {
                PlaybackState state = controller.getPlaybackState();
                return state.getState() == PlaybackState.STATE_PAUSED
                    && state.getPosition() >= 9_500 && state.getPosition() <= 10_500;
            });
            for (String[] page : new String[][] {{"nav_library", "play_all"}, {"nav_queue", "save_queue"},
                    {"nav_playlists", "create_playlist"}, {"nav_folders", "open_file"}}) {
                tap(() -> activity.findViewById(id(page[0])));
                String label = getTargetContext().getString(resource(page[1], "string"));
                await(() -> textView(activity.getWindow().getDecorView(), label) != null);
            }
            tap(() -> activity.findViewById(id("nav_player")));
            tap(() -> activity.findViewById(id("action_settings")));
            await(() -> activity.findViewById(id("settingsPanel")) != null);
            for (String[] category : new String[][] {{"categoryGeneral", "settingsGeneralPanel"},
                    {"categoryAppearance", "settingsAppearancePanel"}, {"categoryBackgrounds", "settingsBackgroundPanel"},
                    {"categoryPlayback", "settingsPlaybackPanel"}, {"categorySpectrum", "settingsSpectrumPanel"},
                    {"categoryLyrics", "settingsLyricsPanel"}, {"categoryFolders", "settingsFoldersPanel"}}) {
                tap(() -> activity.findViewById(id(category[0])));
                await(() -> activity.findViewById(id(category[1])).isShown());
            }
            result.putString("result", "PASS");
            result.putString("checks", "Non-debuggable/min29, paused launch, real playback, seek, five pages, seven categories");
        } catch (Throwable error) {
            result.putString("result", "FAIL");
            result.putString("error", android.util.Log.getStackTraceString(error));
        } finally {
            if (monitor != null) removeMonitor(monitor);
            runOnMainSync(() -> {
                if (controller != null) controller.getTransportControls().pause();
                if (browser != null) browser.disconnect();
                if (activity != null) activity.finish();
            });
            finish("PASS".equals(result.getString("result")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
        }
    }

    private int id(String name) { return resource(name, "id"); }

    private int resource(String name, String type) {
        int value = getTargetContext().getResources().getIdentifier(name, type, PACKAGE);
        check(value != 0, "Missing retained resource: " + name);
        return value;
    }

    private void await(BooleanSupplier condition) {
        long deadline = SystemClock.uptimeMillis() + 25_000;
        while (SystemClock.uptimeMillis() < deadline) {
            boolean[] ready = {false};
            runOnMainSync(() -> ready[0] = condition.getAsBoolean());
            if (ready[0]) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Optimized APK did not reach the expected state");
    }

    private void tap(Supplier<View> target) {
        await(() -> {
            View view = target.get();
            return view != null && view.isShown() && view.isEnabled() && view.hasWindowFocus();
        });
        runOnMainSync(() -> {
            View view = target.get();
            view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true);
        });
        waitForIdleSync();
        float[] point = new float[2];
        runOnMainSync(() -> {
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
}
