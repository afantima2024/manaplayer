package com.macarmusic.app.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.macarmusic.app.MaCarMusicApp
import com.macarmusic.app.R
import com.macarmusic.app.playback.PlaybackService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class HomeWidgetKind(val layout: Int, val label: Int, val provider: Class<out AppWidgetProvider>) {
    CONTROLS(R.layout.widget_controls, R.string.widget_controls_name, ControlsWidgetProvider::class.java),
    ARTWORK(R.layout.widget_artwork, R.string.widget_artwork_name, ArtworkWidgetProvider::class.java),
    LYRICS(R.layout.widget_lyrics, R.string.widget_lyrics_name, LyricsWidgetProvider::class.java),
    PLAY(R.layout.widget_play, R.string.widget_play_name, PlayWidgetProvider::class.java);

    fun component(context: Context) = ComponentName(context, provider)
}

class ControlsWidgetProvider : HomeWidgetProvider()
class ArtworkWidgetProvider : HomeWidgetProvider()
class LyricsWidgetProvider : HomeWidgetProvider()
class PlayWidgetProvider : HomeWidgetProvider()

@androidx.annotation.OptIn(UnstableApi::class)
open class HomeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
    override fun onEnabled(context: Context) = refresh(context)
    override fun onDisabled(context: Context) = refresh(context)
    override fun onDeleted(context: Context, ids: IntArray) = refresh(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        refresh(context)

    override fun onReceive(context: Context, intent: Intent) {
        val action = HomeWidgetAction.from(intent.action)
        if (action == null) {
            if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED || intent.action == Intent.ACTION_LOCALE_CHANGED) {
                refresh(context)
            } else super.onReceive(context, intent)
            return
        }
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)
        if (info?.provider != ComponentName(context, javaClass)) {
            Log.w(TAG, "Ignored a control request for a removed or different widget")
            return
        }
        val app = context.applicationContext as MaCarMusicApp
        val pending = goAsync()
        app.applicationScope.launch(Dispatchers.Main.immediate) {
            var future: ListenableFuture<MediaController>? = null
            try {
                withTimeout(8_000) {
                    val connection = MediaController.Builder(app,
                        SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
                    future = connection
                    val controller = connection.awaitResult()
                    val result = controller.sendCustomCommand(action.command, Bundle.EMPTY).awaitResult()
                    if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                        failure(app, result.extras.getString(HomeWidgetAction.ERROR_MESSAGE)
                            ?: app.text(R.string.widget_action_failed))
                    }
                }
            } catch (error: TimeoutCancellationException) {
                failure(app, app.text(R.string.widget_action_failed), error)
            } catch (error: CancellationException) {
                throw error
            } catch (error: ExecutionException) {
                failure(app, app.text(R.string.widget_action_failed), error)
            } catch (error: IllegalStateException) {
                failure(app, app.text(R.string.widget_action_failed), error)
            } catch (error: IllegalArgumentException) {
                failure(app, app.text(R.string.widget_action_failed), error)
            } catch (error: SecurityException) {
                failure(app, app.text(R.string.widget_action_failed), error)
            } finally {
                try { future?.let(MediaController::releaseFuture) } finally { pending.finish() }
            }
        }
    }

    private fun refresh(context: Context) {
        val app = context.applicationContext as MaCarMusicApp
        val pending = goAsync()
        app.applicationScope.launch(Dispatchers.Main.immediate) {
            try {
                withTimeout(8_000) { app.homeWidgets.refresh() }
            } catch (error: TimeoutCancellationException) {
                failure(app, app.text(R.string.widget_update_failed), error)
            } catch (error: IllegalStateException) {
                failure(app, app.text(R.string.widget_update_failed), error)
            } catch (error: IllegalArgumentException) {
                failure(app, app.text(R.string.widget_update_failed), error)
            } catch (error: SecurityException) {
                failure(app, app.text(R.string.widget_update_failed), error)
            } finally {
                pending.finish()
            }
        }
    }

    private fun failure(app: MaCarMusicApp, message: String, error: Exception? = null) {
        Log.w(TAG, message, error)
        app.report(message)
        Toast.makeText(app.localizedContext, message, Toast.LENGTH_LONG).show()
    }

    private suspend fun <T> ListenableFuture<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addListener({
            try {
                continuation.resume(get())
            } catch (error: ExecutionException) {
                continuation.resumeWithException(error)
            } catch (error: java.util.concurrent.CancellationException) {
                continuation.cancel(error)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                continuation.resumeWithException(IllegalStateException("Widget connection interrupted", error))
            }
        }, MoreExecutors.directExecutor())
        continuation.invokeOnCancellation { cancel(false) }
    }

    private companion object { const val TAG = "HomeWidgetProvider" }
}
