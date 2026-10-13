package com.macarmusic.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
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
import android.view.inspector.WindowInspector;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// No AndroidX/Kotlin test runtime: debug tests otherwise depend on classes renamed by release R8.
public class OptimizedSmokeInstrumentation extends Instrumentation {
    private static final String PACKAGE = "com.macarmusic.app";
    private volatile Activity activity;
    private MediaBrowser browser;
    private MediaController controller;
    private boolean colorPickerOnly;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        colorPickerOnly = arguments != null && "true".equals(arguments.getString("colorPickerOnly"));
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
                .putBoolean("parkedNoticeSeen", true).commit();
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
            activity = startActivitySync(new Intent().setClassName(PACKAGE, PACKAGE + ".ui.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            if (colorPickerOnly) {
                verifyColorPickers();
                result.putString("result", "PASS");
                result.putString("checks", "Optimized nine color dialogs: repeated resizing, blocks, custom HEX preview, invalid-input guard, Cancel/Apply, persisted colors and custom-accent recreation");
                return;
            }
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
            check(controller.getPlaybackState().getState() != PlaybackState.STATE_PLAYING, "Unexpected autoplay");
            tap(() -> activity.findViewById(id("nav_folders")));
            String open = activity.getString(resource("open_file", "string"));
            tap(() -> textView(activity.getWindow().getDecorView(), open));
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
            for (String[] page : new String[][] {{"nav_library", "play_all"}, {"nav_queue", "save_queue"},
                    {"nav_playlists", "create_playlist"}, {"nav_folders", "open_file"}}) {
                tap(() -> activity.findViewById(id(page[0])));
                String label = activity.getString(resource(page[1], "string"));
                await(() -> textView(activity.getWindow().getDecorView(), label) != null);
            }
            tap(() -> activity.findViewById(id("nav_player")));
            tap(() -> activity.findViewById(id("rewind")));
            await(() -> controller.getPlaybackState().getPosition() == 0);
            tap(() -> activity.findViewById(id("forward")));
            await(() -> controller.getPlaybackState().getPosition() == 10_000);
            check(controller.getPlaybackState().getState() == PlaybackState.STATE_PAUSED,
                "Transport seeking must not start paused playback");
            tap(() -> activity.findViewById(id("action_settings")));
            await(() -> activity.findViewById(id("settingsPanel")) != null);
            check(activity.findViewById(id("navigation")).isShown(), "Settings must leave navigation available");
            for (String[] category : new String[][] {{"categoryGeneral", "settingsGeneralPanel"},
                    {"categoryBackgrounds", "settingsBackgroundPanel"},
                    {"categoryPlayback", "settingsPlaybackPanel"}, {"categorySpectrum", "settingsSpectrumPanel"},
                    {"categoryLyrics", "settingsLyricsPanel"}, {"categoryFolders", "settingsFoldersPanel"},
                    {"categoryAbout", "settingsAboutPanel"}}) {
                tap(() -> activity.findViewById(id(category[0])));
                await(() -> activity.findViewById(id(category[1])).isShown());
            }
            check(activity.findViewById(id("aboutPhoto")) != null, "About content must be available in Settings");
            tap(() -> activity.findViewById(id("nav_queue")));
            await(() -> activity.findViewById(id("queueList")).isShown()
                && !activity.findViewById(id("settingsPanel")).isShown());
            result.putString("result", "PASS");
            result.putString("checks", "Non-debuggable/min23, paused launch, real playback, seek, rewind/forward, five pages, seven categories, About, Settings navigation");
        } catch (Throwable error) {
            result.putString("result", "FAIL");
            result.putString("error", android.util.Log.getStackTraceString(error));
        } finally {
            if (monitor != null) removeMonitor(monitor);
            onMain(() -> {
                if (controller != null) controller.getTransportControls().pause();
                if (browser != null) browser.disconnect();
                if (activity != null) activity.finish();
            });
            finish("PASS".equals(result.getString("result")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
        }
    }

    private int id(String name) { return resource(name, "id"); }

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
                check(count > 1, "Expected color blocks");
                View initial = grid.getChildAt(0);
                check(initial.isSelected(), "Initial color must remain selected");
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
            onMain(() -> check(colorDialog().findViewById(
                apply ? android.R.id.button1 : android.R.id.button2).performClick(), "Dialog action failed"));
            await(() -> colorDialog() == null);
        }
    }

    private void verifyCustomColor(String button, String key, boolean recreates) {
        android.content.SharedPreferences preferences = getTargetContext().getSharedPreferences("settings", 0);
        int original = preferences.getInt(key, -1);
        tap(() -> activity.findViewById(id(button)));
        await(() -> colorDialog() != null);
        onMain(() -> {
            ((EditText) colorDialog().findViewById(id("customColorInput"))).setText("#BAD");
            colorDialog().findViewById(android.R.id.button1).performClick();
            check(colorDialog() != null, "Invalid HEX must not dismiss the picker");
            check(preferences.getInt(key, -1) == original, "Invalid HEX must not save");
            ((EditText) colorDialog().findViewById(id("customColorInput"))).setText("#13579B");
            check(((ColorDrawable) colorDialog().findViewById(id("colorPreview")).getBackground()).getColor()
                == 0xff13579b, "Custom preview must match entered RGB");
            colorDialog().findViewById(android.R.id.button2).performClick();
        });
        await(() -> colorDialog() == null);
        check(preferences.getInt(key, -1) == original, "Cancel must preserve stored color");
        tap(() -> activity.findViewById(id(button)));
        await(() -> colorDialog() != null);
        Activity previous = activity;
        onMain(() -> {
            ((EditText) colorDialog().findViewById(id("customColorInput"))).setText("13579b");
            colorDialog().findViewById(android.R.id.button1).performClick();
        });
        if (recreates && original != 0x13579b) {
            // Legacy ActivityMonitor can return an instance that is already being destroyed.
            await(() -> activity != previous && !activity.isDestroyed());
        }
        await(() -> colorDialog() == null && preferences.getInt(key, -1) == 0x13579b);
        tap(() -> activity.findViewById(id(button)));
        await(() -> colorDialog() != null);
        onMain(() -> {
            check("#13579B".contentEquals(((EditText) colorDialog().findViewById(id("customColorInput"))).getText()),
                "Reopened picker must retain custom RGB");
            GridLayout grid = colorDialog().findViewById(id("colorGrid"));
            check(grid.getChildAt(0).isSelected(), "Custom color must join the palette as the selected block");
            colorDialog().findViewById(android.R.id.button2).performClick();
        });
        await(() -> colorDialog() == null);
    }

    private View colorDialog() {
        for (Object window : windowRoots()) {
            if (window instanceof View) {
                View root = (View) window;
                if (root.isShown() && root.findViewById(id("colorPreview")) != null) return root;
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
                return view != null && view.isShown() && view.isEnabled() && view.hasWindowFocus();
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
