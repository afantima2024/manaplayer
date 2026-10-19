package com.macarmusic.app.widgets

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.macarmusic.app.MaCarMusicApp
import com.macarmusic.app.R
import com.macarmusic.app.lyrics.LyricIssue
import com.macarmusic.app.lyrics.LyricResult
import com.macarmusic.app.model.WidgetPlayback
import com.macarmusic.app.model.WidgetPresentation
import com.macarmusic.app.playback.toTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException

@androidx.annotation.OptIn(UnstableApi::class)
class HomeWidgets(private val app: MaCarMusicApp) {
    private val scope = CoroutineScope(app.applicationScope.coroutineContext + Dispatchers.Main.immediate)
    private val manager = AppWidgetManager.getInstance(app)
    private val power = app.getSystemService(PowerManager::class.java)
    private var ids = emptyMap<HomeWidgetKind, IntArray>()
    private var player: Player? = null
    private var generation = 0
    private var state = WidgetPlayback()
    private var ticker: Job? = null
    private var artworkJob: Job? = null
    private var artworkUri: String? = null
    private var cover: Bitmap? = null
    private var lyricJob: Job? = null
    private var lyricKey: Pair<String, Boolean>? = null
    private var lyricResult: LyricResult? = null
    private var lyricObserver: Job? = null
    private var settingsObserver: Job? = null
    private var screenRegistered = false
    private var updateFailed = false
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (ids.isNotEmpty()) updatePlayer()
        }
    }
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                ticker?.cancel()
                ticker = null
                cancelAssets()
            } else if (intent.action == Intent.ACTION_SCREEN_ON) {
                if (player != null) updatePlayer() else scope.launch { refresh() }
            }
        }
    }

    fun attach(value: Player) {
        player?.removeListener(listener)
        player = value
        generation++
        value.addListener(listener)
        scope.launch { refresh() }
    }

    fun detach(value: Player) {
        if (player !== value) return
        value.removeListener(listener)
        player = null
        generation++
        state = state.copy(playing = false, advancing = false, positionMs = value.currentPosition.coerceAtLeast(0))
        stopLiveWork()
        publish()
    }

    suspend fun refresh() {
        updateFailed = false
        ids = HomeWidgetKind.entries.associateWith { manager.getAppWidgetIds(it.component(app)) }
            .filterValues { it.isNotEmpty() }
        if (ids.isEmpty()) {
            stopLiveWork()
            settingsObserver?.cancel()
            settingsObserver = null
            cover = null
            lyricResult = null
            return
        }
        observeScreen()
        observeSettings()
        if (player != null) updatePlayer() else {
            val version = generation
            val saved = app.playback.read()
            if (player == null && generation == version) {
                state = WidgetPlayback.restored(saved, app.playbackSettings.muted.value)
                clearStaleAssets()
                publish()
                loadAssets()
            }
        }
    }

    private fun updatePlayer() {
        val source = player ?: return
        if (ids.isEmpty() || updateFailed) return
        val item = source.currentMediaItem
        val track = item?.toTrack(app.repository.library.value.tracks.firstOrNull { it.id == item.mediaId }
            ?: state.track?.takeIf { it.id == item.mediaId })
        if (track?.sourceUris != state.track?.sourceUris || track?.sourceParents != state.track?.sourceParents) {
            lyricKey = null
        }
        state = WidgetPlayback(
            track = track,
            playing = source.playWhenReady && source.playbackState != Player.STATE_ENDED && source.playerError == null,
            advancing = source.isPlaying,
            positionMs = source.currentPosition.coerceAtLeast(0),
            durationMs = source.duration.takeIf { it > 0 } ?: track?.durationMs?.coerceAtLeast(0) ?: 0,
            previous = track != null && source.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS),
            next = track != null && source.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM),
            muted = app.playbackSettings.muted.value,
            failed = source.playerError != null,
        )
        clearStaleAssets()
        publish()
        loadAssets()
        observeScreen()
        val needsTicks = WidgetPresentation.needsTicks(ids.keys.any { it != HomeWidgetKind.PLAY },
            state.advancing, power.isInteractive)
        if (!needsTicks || updateFailed) {
            ticker?.cancel()
            ticker = null
        } else if (ticker?.isActive != true) {
            ticker = scope.launch {
                while (isActive) {
                    delay(1_000)
                    val active = player ?: break
                    state = state.copy(positionMs = active.currentPosition.coerceAtLeast(0))
                    publishDynamic()
                }
            }
        }
    }

    private fun clearStaleAssets() {
        if (artworkUri != state.track?.uri) cover = null
        if (lyricKey?.first != state.track?.id) lyricResult = null
    }

    private fun observeScreen() {
        if (screenRegistered) return
        ContextCompat.registerReceiver(app, screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenRegistered = true
    }

    private fun observeSettings() {
        if (settingsObserver?.isActive == true) return
        settingsObserver = scope.launch {
            combine(app.playbackSettings.muted,
                app.displaySettings.options.map { it.onlineLyrics to it.widgetTransparency }.distinctUntilChanged()) { muted, display -> muted to display }
                .collect { (muted, display) ->
                    val online = display.first
                    state = state.copy(muted = muted)
                    if (lyricKey?.second != online) {
                        lyricKey = null
                        lyricResult = null
                    }
                    publish()
                    loadAssets()
                }
        }
    }

    private fun loadAssets() {
        val track = state.track
        if (HomeWidgetKind.ARTWORK !in ids || artworkUri != track?.uri) {
            artworkJob?.cancel()
            artworkJob = null
            artworkUri = null
            cover = null
        }
        if (HomeWidgetKind.LYRICS !in ids || lyricKey?.first != track?.id) {
            lyricJob?.cancel()
            lyricJob = null
            lyricKey = null
            lyricResult = null
        }
        if (HomeWidgetKind.LYRICS !in ids) {
            lyricObserver?.cancel()
            lyricObserver = null
        }
        if (track == null || !power.isInteractive) return
        if (HomeWidgetKind.ARTWORK in ids && artworkUri == null) {
            artworkUri = track.uri
            artworkJob = scope.launch {
                try {
                    val result = app.artwork.load(track.uri)
                    result.warning?.let { Log.w(TAG, it) }
                    val bitmap = withContext(Dispatchers.Default) {
                        result.bitmap?.let { original ->
                            val scale = minOf(1.0, 128.0 / maxOf(original.width, original.height))
                            Bitmap.createScaledBitmap(original, (original.width * scale).toInt().coerceAtLeast(1),
                                (original.height * scale).toInt().coerceAtLeast(1), true)
                        }
                    }
                    if (state.track?.uri == track.uri && HomeWidgetKind.ARTWORK in ids) {
                        cover = bitmap
                        publish()
                    }
                } catch (error: IOException) {
                    Log.w(TAG, "Widget artwork unavailable", error)
                } catch (error: SecurityException) {
                    Log.w(TAG, "Widget artwork access was revoked", error)
                }
            }
        }
        if (HomeWidgetKind.LYRICS !in ids) return
        val online = app.displaySettings.options.value.onlineLyrics
        if (lyricObserver?.isActive != true) {
            lyricObserver = scope.launch {
                app.lyrics.latest.collect { latest ->
                    if (latest.trackId == state.track?.id && latest.result != null &&
                        (app.displaySettings.options.value.onlineLyrics || latest.result.availableOffline)) {
                        lyricResult = latest.result
                        publishDynamic()
                    }
                }
            }
        }
        val key = track.id to online
        if (lyricKey == key) return
        lyricJob?.cancel()
        lyricKey = key
        lyricJob = scope.launch {
            val latest = app.lyrics.latest.value
            val ready = latest.result?.takeIf { latest.trackId == track.id && (it.lyrics != null || latest.userEdited) &&
                (online || it.availableOffline) }
            lyricResult = ready
            publishDynamic()
            if (ready != null) return@launch
            try {
                // A desktop lyric widget reads local/cached lyrics; only the player UI starts online lookups.
                val result = withTimeout(8_000) {
                    app.lyrics.load(track, online, allowNetwork = false, publishResult = false)
                }
                if (lyricKey == key && state.track?.id == track.id) {
                    val updated = app.lyrics.latest.value
                    val selection = updated.result?.takeIf { updated !== latest && updated.trackId == track.id &&
                        (it.lyrics != null || updated.userEdited) &&
                        (online || it.availableOffline) }
                    if (selection != null) lyricResult = selection
                    else if (result.lyrics != null || lyricResult?.lyrics == null) lyricResult = result
                    publishDynamic()
                }
            } catch (error: TimeoutCancellationException) {
                if (lyricKey == key) lyricFailure(error)
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                if (lyricKey == key) lyricFailure(error)
            } catch (error: SecurityException) {
                if (lyricKey == key) lyricFailure(error)
            }
        }
    }

    private fun lyricFailure(error: Exception) {
        Log.w(TAG, "Widget lyrics unavailable", error)
        lyricResult = LyricResult(issue = LyricIssue.LOCAL_UNREADABLE)
        publishDynamic()
    }

    private fun lyricText(): String = WidgetPresentation.lyric(lyricResult?.lyrics, state.positionMs)
        ?: app.text(when {
            state.track == null -> R.string.widget_select_music
            lyricResult?.issue == LyricIssue.LOCAL_UNREADABLE ||
                lyricResult?.issue == LyricIssue.CACHE_UNAVAILABLE -> R.string.widget_lyrics_unavailable
            lyricResult?.issue == LyricIssue.FOLDER_ACCESS_NEEDED -> R.string.lyrics_folder_needed
            lyricResult == null -> R.string.lyrics_loading
            else -> R.string.widget_no_lyrics
        })

    private fun publish() {
        if (updateFailed) return
        update {
            for ((kind, widgetIds) in ids) for (id in widgetIds) {
                manager.updateAppWidget(id, WidgetViews.full(app.localizedContext, kind, id, state, cover, lyricText(),
                    app.displaySettings.options.value.widgetTransparency))
            }
        }
    }

    private fun publishDynamic() {
        if (updateFailed) return
        update {
            for ((kind, widgetIds) in ids) if (kind != HomeWidgetKind.PLAY) {
                manager.partiallyUpdateAppWidget(widgetIds, WidgetViews.dynamic(app.localizedContext, kind, state, lyricText()))
            }
        }
    }

    private inline fun update(block: () -> Unit) {
        try {
            block()
        } catch (error: IllegalArgumentException) {
            updateFailure(error)
        } catch (error: IllegalStateException) {
            updateFailure(error)
        } catch (error: SecurityException) {
            updateFailure(error)
        }
    }

    private fun updateFailure(error: Exception) {
        Log.e(TAG, "Launcher widget update failed", error)
        updateFailed = true
        ticker?.cancel()
        ticker = null
        app.report(app.text(R.string.widget_update_failed))
    }

    private fun cancelAssets() {
        artworkJob?.cancel()
        artworkJob = null
        artworkUri = null
        lyricJob?.cancel()
        lyricJob = null
        lyricKey = null
    }

    private fun stopLiveWork() {
        ticker?.cancel()
        ticker = null
        cancelAssets()
        lyricObserver?.cancel()
        lyricObserver = null
        if (screenRegistered) {
            app.unregisterReceiver(screen)
            screenRegistered = false
        }
    }

    private companion object { const val TAG = "HomeWidgets" }
}
