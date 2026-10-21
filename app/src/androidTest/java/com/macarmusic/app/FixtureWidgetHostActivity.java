package com.macarmusic.app;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RemoteViews;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.LinkedHashMap;
import java.util.Map;

// A real launcher-style host running in the separate test APK's UID.
public class FixtureWidgetHostActivity extends Activity {
    private static final String PLAYER = "com.macarmusic.app";
    private static final String[] PROVIDERS = {
        "ControlsWidgetProvider", "ArtworkWidgetProvider", "LyricsWidgetProvider", "PlayWidgetProvider"
    };
    private static FixtureWidgetHostActivity active;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, AppWidgetHostView> views = new LinkedHashMap<>();
    private final Map<Integer, Integer> ids = new LinkedHashMap<>();
    private AppWidgetHost host;
    private Resources playerResources;
    private String error;
    private int updates;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { publish(); handler.postDelayed(this, 100); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        active = this;
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            playerResources = getPackageManager().getResourcesForApplication(PLAYER);
        } catch (PackageManager.NameNotFoundException failure) {
            throw new IllegalStateException("Player package is not installed", failure);
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(8), dp(8), dp(8), dp(8));
        column.setBackgroundColor(0xff202c39);
        scroll.addView(column);
        setContentView(scroll);
        host = new AppWidgetHost(this, 1515) {
            @Override protected AppWidgetHostView onCreateView(Context context, int id, AppWidgetProviderInfo info) {
                return new AppWidgetHostView(context) {
                    @Override public void updateAppWidget(RemoteViews remote) {
                        super.updateAppWidget(remote);
                        updates++;
                    }
                };
            }
        };
        host.deleteHost();
        host.startListening();
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        int requested = getIntent().getIntExtra("kind", -1);
        for (int kind = 0; kind < PROVIDERS.length; kind++) {
            if (requested >= 0 && requested != kind) continue;
            int id = host.allocateAppWidgetId();
            ComponentName component = new ComponentName(PLAYER, PLAYER + ".widgets." + PROVIDERS[kind]);
            int height = kind == 2 ? 128 : 56;
            int width = kind == 3 ? 56 : 208;
            Bundle options = new Bundle();
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height);
            if (!manager.bindAppWidgetIdIfAllowed(id, component, options)) {
                error = "Widget bind permission not granted for the disposable test host";
                host.deleteAppWidgetId(id);
                break;
            }
            AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
            if (info == null) {
                error = "Missing installed provider: " + component;
                break;
            }
            AppWidgetHostView view = host.createView(this, id, info);
            view.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(width), dp(height));
            params.bottomMargin = dp(8);
            column.addView(view, params);
            views.put(kind, view);
            ids.put(kind, id);
        }
        handler.post(refresh);
    }

    static void command(String command, String argument) {
        FixtureWidgetHostActivity current = active;
        if (current == null) {
            Bundle status = new Bundle();
            status.putString("error", "Widget host is not running");
            FixtureDocumentsProvider.setWidgetStatus(status);
            return;
        }
        current.handler.post(() -> current.execute(command, argument));
    }

    private void execute(String command, String argument) {
        if ("finish".equals(command)) {
            for (int id : ids.values()) host.deleteAppWidgetId(id);
            views.clear();
            ids.clear();
            publish();
            finish();
            return;
        }
        String[] parts = argument.split("[:,]", 2);
        int kind = Integer.parseInt(parts[0]);
        AppWidgetHostView root = views.get(kind);
        if (root == null) {
            error = "Missing hosted widget " + kind;
        } else if ("click".equals(command)) {
            View button = root.findViewById(id(parts[1]));
            if (button == null || !button.isEnabled() || !button.performClick()) error = "Widget click failed: " + argument;
        } else if ("resize".equals(command)) {
            String[] size = parts[1].split("x");
            int width = Integer.parseInt(size[0]);
            int height = size.length == 2 ? Integer.parseInt(size[1]) :
                Math.round(root.getHeight() / getResources().getDisplayMetrics().density);
            root.getLayoutParams().width = dp(width);
            root.getLayoutParams().height = dp(height);
            root.requestLayout();
            root.updateAppWidgetSize(new Bundle(), width, height, width, height);
        } else if ("delete".equals(command)) {
            host.deleteAppWidgetId(ids.remove(kind));
            ((ViewGroup) root.getParent()).removeView(root);
            views.remove(kind);
        } else error = "Unknown widget command: " + command;
        publish();
    }

    private void publish() {
        Bundle status = new Bundle();
        status.putInt("uid", Process.myUid());
        status.putInt("count", views.size());
        status.putInt("updates", updates);
        if (error != null) status.putString("error", error);
        for (Map.Entry<Integer, AppWidgetHostView> entry : views.entrySet()) {
            int kind = entry.getKey();
            AppWidgetHostView root = entry.getValue();
            status.putInt("id" + kind, ids.get(kind));
            status.putInt("width" + kind, root.getWidth());
            status.putInt("height" + kind, root.getHeight());
            text(status, root, kind, "title", "widget_title");
            text(status, root, kind, "time", "widget_time");
            text(status, root, kind, "lyric", "widget_lyric");
            text(status, root, kind, "lyricNext", "widget_lyric_next");
            int[] rootPosition = new int[2];
            root.getLocationOnScreen(rootPosition);
            for (String button : new String[] {"play", "previous", "next", "mute"}) {
                View view = root.findViewById(id("widget_" + button));
                status.putBoolean(button + "Present" + kind, view != null);
                if (view != null) {
                    status.putString(button + kind, String.valueOf(view.getContentDescription()));
                    status.putBoolean(button + "Enabled" + kind, view.isEnabled());
                    status.putInt(button + "Width" + kind, view.getWidth());
                    status.putInt(button + "Height" + kind, view.getHeight());
                    status.putBoolean(button + "Visible" + kind, fullyVisible(view));
                    status.putFloat(button + "Alpha" + kind, view.getAlpha());
                    int[] position = new int[2];
                    view.getLocationOnScreen(position);
                    status.putInt(button + "Top" + kind, position[1] - rootPosition[1]);
                }
            }
            ProgressBar progress = root.findViewById(id("widget_progress"));
            if (progress != null) {
                status.putInt("progress" + kind, progress.getProgress());
                status.putInt("max" + kind, progress.getMax());
                status.putBoolean("progressVisible" + kind, fullyVisible(progress));
            }
            ImageView image = root.findViewById(id("widget_artwork"));
            status.putBoolean("artwork" + kind, image != null && image.getDrawable() != null && image.isShown());
            ImageView background = root.findViewById(id("widget_background"));
            if (background != null) {
                status.putInt("backgroundImageAlpha" + kind, background.getImageAlpha());
                status.putFloat("backgroundViewAlpha" + kind, background.getAlpha());
            }
            TextView lyric = root.findViewById(id("widget_lyric"));
            if (lyric != null) status.putBoolean("lyricVisible" + kind, fullyVisible(lyric));
            TextView nextLyric = root.findViewById(id("widget_lyric_next"));
            if (nextLyric != null) {
                status.putBoolean("lyricNextVisible" + kind, fullyVisible(nextLyric));
                status.putInt("lyricNextMaxLines" + kind, nextLyric.getMaxLines());
            }
            status.putBoolean("providerError" + kind, containsError(root));
        }
        FixtureDocumentsProvider.setWidgetStatus(status);
    }

    private boolean fullyVisible(View view) {
        Rect visible = new Rect();
        return view.isShown() && view.getLocalVisibleRect(visible) && visible.width() == view.getWidth() &&
            visible.height() == view.getHeight();
    }

    private boolean containsError(View view) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains("Problem loading")) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (containsError(group.getChildAt(i))) return true;
        }
        return false;
    }

    private void text(Bundle result, View root, int kind, String key, String name) {
        TextView view = root.findViewById(id(name));
        if (view == null) return;
        result.putString(key + kind, view.getText().toString());
        result.putInt(key + "Color" + kind, view.getCurrentTextColor());
        result.putFloat(key + "SizeSp" + kind, view.getTextSize() / view.getResources().getDisplayMetrics().scaledDensity);
        result.putInt(key + "Height" + kind, view.getHeight());
        result.putInt(key + "LayoutHeight" + kind, view.getLayout() == null ? 0 : view.getLayout().getHeight());
        int[] position = new int[2];
        int[] origin = new int[2];
        view.getLocationOnScreen(position);
        root.getLocationOnScreen(origin);
        result.putInt(key + "Top" + kind, position[1] - origin[1]);
    }
    private int id(String name) { return playerResources.getIdentifier(name, "id", PLAYER); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (host != null) {
            for (int id : ids.values()) host.deleteAppWidgetId(id);
            if (active == this) host.stopListening();
        }
        if (active == this) active = null;
        super.onDestroy();
    }
}
