package com.macarmusic.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.service.media.MediaBrowserService;
import java.util.List;

// Runs as the test APK's separate UID, without notification-listener or media-control privileges.
public class FixtureMediaControllerActivity extends Activity {
    private static FixtureMediaControllerActivity active;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaBrowser browser;
    private MediaController controller;
    private String error;
    private String browsedId;
    private boolean discovered;
    private boolean discoveredMusicApp;
    private int metadataChanges;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { publish(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) {
            metadataChanges++;
            publish();
        }
        @Override public void onSessionDestroyed() {
            error = "Session destroyed";
            publish();
        }
    };
    private final MediaBrowser.SubscriptionCallback subscription = new MediaBrowser.SubscriptionCallback() {
        @Override public void onChildrenLoaded(String parentId, List<MediaBrowser.MediaItem> children) {
            browsedId = null;
            if (!children.isEmpty()) {
                MediaBrowser.MediaItem item = children.get(0);
                if (!item.isPlayable()) error = "Now-playing item is not marked playable";
                browsedId = item.getMediaId();
            }
            publish();
        }
        @Override public void onError(String parentId) {
            error = "Cannot read browser root: " + parentId;
            publish();
        }
    };
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            publish();
            handler.postDelayed(this, 200);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        active = this;
        discovered = !getPackageManager().queryIntentServices(
            new Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage("com.macarmusic.app"), 0).isEmpty();
        discoveredMusicApp = !getPackageManager().queryIntentActivities(
            new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
                .setPackage("com.macarmusic.app"), PackageManager.MATCH_DEFAULT_ONLY).isEmpty();
        Bundle hints = new Bundle();
        hints.putBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT, getIntent().getBooleanExtra("recent", false));
        browser = new MediaBrowser(this,
            new ComponentName("com.macarmusic.app", "com.macarmusic.app.playback.PlaybackService"),
            new MediaBrowser.ConnectionCallback() {
                @Override public void onConnected() {
                    controller = new MediaController(FixtureMediaControllerActivity.this, browser.getSessionToken());
                    controller.registerCallback(callback, handler);
                    browser.subscribe(browser.getRoot(), subscription);
                    publish();
                }
                @Override public void onConnectionFailed() {
                    error = "Android MediaBrowser connection rejected";
                    publish();
                }
                @Override public void onConnectionSuspended() {
                    error = "Android MediaBrowser connection suspended";
                    publish();
                }
            }, hints);
        publish();
        browser.connect();
        handler.post(refresh);
    }

    static void command(String command, String value) {
        FixtureMediaControllerActivity activity = active;
        if (activity == null) {
            Bundle result = new Bundle();
            result.putString("error", "External controller activity is not running");
            FixtureDocumentsProvider.setControllerStatus(result);
            return;
        }
        activity.handler.post(() -> activity.execute(command, value));
    }

    private void execute(String command, String value) {
        if ("disconnect".equals(command)) {
            finish();
            return;
        }
        if (controller == null) {
            error = "External controller is not connected";
            publish();
            return;
        }
        MediaController.TransportControls controls = controller.getTransportControls();
        switch (command) {
            case "play": controls.play(); break;
            case "pause": controls.pause(); break;
            case "next": controls.skipToNext(); break;
            case "previous": controls.skipToPrevious(); break;
            case "seek": controls.seekTo(Long.parseLong(value)); break;
            case "playId": controls.playFromMediaId(value, Bundle.EMPTY); break;
            case "playUri": controls.playFromUri(Uri.parse(value), Bundle.EMPTY); break;
            case "stop": controls.stop(); break;
            case "refreshBrowser":
                browser.unsubscribe(browser.getRoot());
                browser.subscribe(browser.getRoot(), subscription);
                break;
            default: error = "Unknown external command: " + command;
        }
        publish();
    }

    private void publish() {
        Bundle result = new Bundle();
        result.putInt("uid", Process.myUid());
        result.putBoolean("discovered", discovered);
        result.putBoolean("discoveredMusicApp", discoveredMusicApp);
        if (Build.VERSION.SDK_INT >= 28) {
            result.putBoolean("trusted", getSystemService(MediaSessionManager.class).isTrustedForMediaControl(
                new MediaSessionManager.RemoteUserInfo(getPackageName(), Process.myPid(), Process.myUid())));
        }
        result.putBoolean("connected", browser != null && browser.isConnected());
        result.putInt("metadataChanges", metadataChanges);
        if (error != null) result.putString("error", error);
        if (browsedId != null) result.putString("browsedId", browsedId);
        if (controller != null) {
            List<?> queue = controller.getQueue();
            result.putInt("queueSize", queue == null ? 0 : queue.size());
            PlaybackState state = controller.getPlaybackState();
            if (state != null) {
                result.putInt("state", state.getState());
                long position = state.getPosition();
                if (state.getState() == PlaybackState.STATE_PLAYING) {
                    position += (long) ((SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime())
                        * state.getPlaybackSpeed());
                }
                result.putLong("position", position);
                result.putLong("actions", state.getActions());
            }
            MediaMetadata metadata = controller.getMetadata();
            if (metadata != null) {
                result.putString("mediaId", metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID));
                result.putString("title", metadata.getString(MediaMetadata.METADATA_KEY_TITLE));
                result.putLong("duration", metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
            }
        }
        FixtureDocumentsProvider.setControllerStatus(result);
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (controller != null) controller.unregisterCallback(callback);
        if (browser != null) browser.disconnect();
        if (active == this) active = null;
        super.onDestroy();
    }
}
