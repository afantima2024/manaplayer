package com.macarmusic.app.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.macarmusic.app.MaCarMusicApp
import com.macarmusic.app.R
import com.macarmusic.app.data.LocalizedContext
import com.macarmusic.app.model.Library
import com.macarmusic.app.model.LoopMode
import com.macarmusic.app.model.PlayOrder
import com.macarmusic.app.model.PlaybackOptions
import com.macarmusic.app.model.PlaybackScope
import com.macarmusic.app.model.PlaybackSnapshot
import com.macarmusic.app.model.PlaybackEdits
import com.macarmusic.app.model.Track
import com.macarmusic.app.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val restored = CompletableDeferred<Unit>()
    private lateinit var app: MaCarMusicApp
    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null
    private var cachedQueue = emptyList<Track>()
    private var restoring = true
    private var released = false
    private var appliedOptions = PlaybackOptions()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalizedContext(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        app = application as MaCarMusicApp
        val trackSelector = DefaultTrackSelector(this).apply {
            parameters = buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
        }
        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(DataSource.Factory { LocalDataSource(this) }))
            .setUsePlatformDiagnostics(false)
            .build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                    true,
                )
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_LOCAL)
                addListener(playerListener)
            }
        val activityIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaLibrarySession.Builder(this, player, SessionCallback())
            .setSessionActivity(activityIntent)
            .setBitmapLoader(LocalBitmapLoader(app.artwork, scope))
            .build()
        setListener(object : Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                player.pause()
                app.report(getString(R.string.foreground_failed))
            }
        })
        scope.launch {
            app.repository.awaitReady()
            val saved = app.playback.read()
            val snapshot = withContext(Dispatchers.Default) {
                if (app.repository.isReadable) PlaybackEdits.reconcile(saved, app.repository.library.value.tracks) else saved
            }
            cachedQueue = snapshot.queue
            app.playbackSettings.migrate(snapshot)
            appliedOptions = app.playbackSettings.options.value
            applyPlayerOptions(appliedOptions)
            player.playWhenReady = false
            if (snapshot.queue.isNotEmpty()) {
                player.setMediaItems(snapshot.queue.map(Track::toMediaItem), snapshot.index, snapshot.positionMs)
            }
            // Do not prepare or acquire audio focus until an explicit Play command.
            restoring = false
            restored.complete(Unit)
            notifyBrowsers()
            observeLibraryReferences()
            observePlaybackSettings()
            while (isActive) {
                delay(5_000)
                persist()
            }
        }
    }

    private fun applyPlayerOptions(options: PlaybackOptions) {
        player.shuffleModeEnabled = options.order == PlayOrder.RANDOM
        player.repeatMode = options.loop.repeatMode
    }

    private fun observePlaybackSettings() {
        var selected = PlaybackScope.selectedSources(app.repository.library.value).map { it.uri }.toSet()
        scope.launch {
            combine(app.playbackSettings.options, app.repository.library) { options, library -> options to library }
                .collect { (options, library) ->
                    val nextSelected = PlaybackScope.selectedSources(library).map { it.uri }.toSet()
                    val rebuild = options.loop != appliedOptions.loop ||
                        (options.loop == LoopMode.SELECTED_FOLDERS && selected != nextSelected)
                    appliedOptions = options
                    selected = nextSelected
                    applyPlayerOptions(options)
                    if (rebuild && options.loop in listOf(LoopMode.FOLDER, LoopMode.SELECTED_FOLDERS)) {
                        applyFolderScope(library, options)
                    }
                    persist()
                }
        }
    }

    private suspend fun applyFolderScope(library: Library, options: PlaybackOptions) {
        val item = player.currentMediaItem
        val current = item?.toTrack(library.tracks.firstOrNull { it.id == item.mediaId }
            ?: cachedQueue.firstOrNull { it.id == item.mediaId })
        val tracks = withContext(Dispatchers.Default) {
            PlaybackScope.tracks(library, options.loop, current, app.sortOrder)
        }
        // An explicit new playback request wins over an older, still-computing settings change.
        if (player.currentMediaItem != item || app.playbackSettings.options.value != options) return
        if (tracks.isEmpty()) {
            player.pause()
            player.clearMediaItems()
            app.inform(getString(
                if (options.loop == LoopMode.FOLDER) R.string.folder_loop_unavailable
                else R.string.selected_folders_empty,
            ))
            return
        }
        val index = tracks.indexOfFirst { it.id == current?.id }
        val position = if (index >= 0) player.currentPosition else 0
        val wasPrepared = player.playbackState != Player.STATE_IDLE
        if (index < 0) player.pause()
        player.setMediaItems(tracks.map(Track::toMediaItem), index.coerceAtLeast(0), position)
        if (wasPrepared) player.prepare()
        if (current != null && index < 0) app.inform(getString(R.string.scope_changed_paused))
    }

    private fun observeLibraryReferences() {
        var previous = app.repository.library.value.tracks.associateBy { it.id }
        scope.launch {
            app.repository.library.collect { library ->
                val known = library.tracks.associateBy { it.id }
                val removed = previous.keys - known.keys
                for (index in player.mediaItemCount - 1 downTo 0) {
                    val item = player.getMediaItemAt(index)
                    if (item.mediaId in removed) {
                        if (index == player.currentMediaItemIndex) player.pause()
                        player.removeMediaItem(index)
                    } else {
                        val track = known[item.mediaId]
                        if (track != null && previous[item.mediaId]?.uri != track.uri) {
                            player.replaceMediaItem(index, track.toMediaItem())
                        }
                    }
                }
                previous = known
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (restoring || released) return
            val order = if (shuffleModeEnabled) PlayOrder.RANDOM else PlayOrder.SEQUENTIAL
            if (order != app.playbackSettings.options.value.order) app.playbackSettings.setOrder(order)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            if (restoring || released) return
            if (repeatMode != app.playbackSettings.options.value.loop.repeatMode) {
                app.playbackSettings.setLoop(LoopMode.fromRepeatMode(repeatMode))
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (restoring || released) return
            if (events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)
            ) {
                val known = app.repository.library.value.tracks.associateBy { it.id }
                val previous = cachedQueue.associateBy { it.id }
                cachedQueue = (0 until player.mediaItemCount).map { index ->
                    val item = player.getMediaItemAt(index)
                    item.toTrack(known[item.mediaId] ?: previous[item.mediaId])
                }
            }
            if (events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)
            ) {
                notifyBrowsers()
            }
            persist()
        }

        override fun onPlayerError(error: PlaybackException) {
            player.pause()
            Log.e(TAG, "Playback stopped; automatic skipping is disabled", error)
            app.report(getString(
                R.string.playback_failed,
                player.currentMediaItem?.mediaMetadata?.title ?: getString(R.string.unnamed_file),
                error.errorCodeName,
            ))
            persist()
        }
    }

    private fun persist() {
        if (restoring || released) return
        app.playback.save(PlaybackSnapshot(
            queue = cachedQueue,
            index = player.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = player.currentPosition.coerceAtLeast(0),
            shuffle = player.shuffleModeEnabled,
            repeat = player.repeatMode,
        ))
    }

    private fun notifyBrowsers() {
        session?.notifyChildrenChanged(BROWSER_ROOT, if (player.currentMediaItem == null) 0 else 1, null)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        persist()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        persist()
        released = true
        scope.cancel()
        player.removeListener(playerListener)
        session?.release()
        session = null
        player.release()
        clearListener()
        super.onDestroy()
    }

    private inner class SessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val result = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            if (controller.uid != Process.myUid()) {
                // Ordinary head-unit widgets need transport access without privileged system permissions.
                result.setAvailablePlayerCommands(Player.Commands.Builder().addAll(
                    Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_STOP,
                    Player.COMMAND_SEEK_TO_DEFAULT_POSITION, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_MEDIA_ITEM, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD,
                    Player.COMMAND_SET_SHUFFLE_MODE, Player.COMMAND_SET_REPEAT_MODE,
                    Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE,
                    Player.COMMAND_GET_METADATA, Player.COMMAND_SET_MEDIA_ITEM,
                ).build())
                result.setAvailableSessionCommands(SessionCommands.Builder()
                    .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT)
                    .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM)
                    .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN)
                    .add(SessionCommand.COMMAND_CODE_LIBRARY_SUBSCRIBE)
                    .add(SessionCommand.COMMAND_CODE_LIBRARY_UNSUBSCRIBE)
                    .build())
            }
            return result.build()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(browserRoot(), params))

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = afterRestore {
            val item = if (mediaId == BROWSER_ROOT) browserRoot() else {
                (cachedQueue.firstOrNull { it.id == mediaId }
                    ?: app.repository.library.value.tracks.firstOrNull { it.id == mediaId })?.toMediaItem()
            }
            if (item == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            else LibraryResult.ofItem(item, null)
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = afterRestore {
            if (parentId != BROWSER_ROOT || page < 0 || pageSize < 1) {
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            } else {
                val items = if (page == 0) listOfNotNull(player.currentMediaItem) else emptyList()
                LibraryResult.ofItemList(items, params)
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = afterRestore {
            val items = resolveMediaItems(controller, mediaItems)
            app.playback.allowReplacingUnreadableQueue()
            items
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = afterRestore {
            val items = resolveMediaItems(controller, mediaItems)
            app.playback.allowReplacingUnreadableQueue()
            val queueIndex = if (controller.uid != Process.myUid() && items.size == 1) {
                cachedQueue.indexOfFirst { it.id == items.single().mediaId }
            } else C.INDEX_UNSET
            if (queueIndex >= 0) {
                // Media-ID resumption must not replace a saved multi-song queue with one song.
                val position = if (startPositionMs != C.TIME_UNSET) startPositionMs
                    else if (queueIndex == player.currentMediaItemIndex) player.currentPosition else 0
                MediaSession.MediaItemsWithStartPosition(cachedQueue.map(Track::toMediaItem), queueIndex, position)
            } else {
                MediaSession.MediaItemsWithStartPosition(items, startIndex, startPositionMs)
            }
        }

        private fun browserRoot(): MediaItem = MediaItem.Builder()
            .setMediaId(BROWSER_ROOT)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(getString(R.string.now_playing))
                .setSubtitle(getString(R.string.app_name))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build())
            .build()

        private fun resolveMediaItems(
            controller: MediaSession.ControllerInfo,
            items: List<MediaItem>,
        ): List<MediaItem> {
            val resolved = if (controller.uid == Process.myUid()) items else {
                val known = (app.repository.library.value.tracks + cachedQueue).associateBy { it.id }
                val byUri = known.values.associateBy { it.uri }
                items.map { item ->
                    val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri
                    val track = known[item.mediaId] ?: byUri[uri?.toString()]
                    if (track == null || (uri != null && uri.toString() != track.uri)) {
                        throw IOException(getString(R.string.external_media_unavailable))
                    }
                    track.toMediaItem()
                }
            }
            checkLocalItems(resolved)
            return resolved
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = afterRestore {
            MediaSession.MediaItemsWithStartPosition(
                cachedQueue.map(Track::toMediaItem),
                player.currentMediaItemIndex.coerceAtLeast(0),
                player.currentPosition.coerceAtLeast(0),
            )
        }
    }

    private fun checkLocalItems(items: List<MediaItem>) {
        if (items.any { it.localConfiguration?.uri?.scheme != "content" }) {
            throw IOException(getString(R.string.local_documents_only))
        }
    }

    private fun <T> afterRestore(block: () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        val job = scope.launch {
            try {
                restored.await()
                future.set(block())
            } catch (error: CancellationException) {
                future.cancel(false)
                throw error
            } catch (error: IOException) {
                Log.w(TAG, "Rejected unavailable media request", error)
                app.report(error.localizedMessage ?: getString(R.string.local_documents_only))
                future.setException(error)
            } catch (error: IllegalArgumentException) {
                Log.w(TAG, "Rejected invalid media request", error)
                app.report(getString(R.string.operation_failed, error.localizedMessage.orEmpty()))
                future.setException(error)
            }
        }
        future.addListener({ if (future.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
        return future
    }

    private companion object {
        const val TAG = "PlaybackService"
        const val BROWSER_ROOT = "manaplayer:now-playing"
    }
}
