package com.macarmusic.app.playback

import android.app.PendingIntent
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
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
import com.macarmusic.app.model.PlaybackCollection
import com.macarmusic.app.model.CollectionKind
import com.macarmusic.app.model.CollectionPositions
import com.macarmusic.app.model.BackupEdits
import com.macarmusic.app.ui.MainActivity
import com.macarmusic.app.widgets.HomeWidgetAction
import com.macarmusic.app.radio.RadioCatalogState
import com.macarmusic.app.radio.RadioCommands
import com.macarmusic.app.radio.RadioProgram
import com.macarmusic.app.radio.RadioNavigation
import com.macarmusic.app.radio.RadioNavigationOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val restored = CompletableDeferred<Unit>()
    private val musicRestored = CompletableDeferred<Unit>()
    private lateinit var app: MaCarMusicApp
    private lateinit var player: ExoPlayer
    private lateinit var sessionPlayer: RadioSessionPlayer
    private var session: MediaLibrarySession? = null
    private var cachedQueue = emptyList<Track>()
    private var cachedTimeline: Timeline? = null
    private var restoring = true
    private var released = false
    private var appliedOptions = PlaybackOptions()
    private var currentCollection: PlaybackCollection? = null
    private var systemSeekEnabled = false
    private var radioMode = false
    private var changingSource = false
    private var errorPausePending = false
    private val sessionCommands = Mutex()
    private var appliedRadioCatalog: RadioCatalogState? = null
    private var radioEpoch = 0L
    private var radioFallback: Job? = null
    private val attemptedSources = linkedSetOf<String>()
    private var logoJob: Job? = null
    private var logoKey: String? = null
    private var trafficKey: String? = null
    private var navigationPrograms: List<RadioProgram>? = null
    private var navigationOptions: RadioNavigationOptions? = null
    private var navigationLocale: java.util.Locale? = null
    private var navigationItems = emptyList<MediaItem>()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalizedContext(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        app = application as MaCarMusicApp
        app.playbackReady.value = false
        if (Build.VERSION.SDK_INT >= 26) {
            val notifications = getSystemService(NotificationManager::class.java)
            notifications.getNotificationChannel(DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID)?.let { channel ->
                channel.name = getString(R.string.media_controls_channel)
                notifications.createNotificationChannel(channel)
            }
        }
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this)
            .setChannelName(R.string.media_controls_channel)
            .build().apply { setSmallIcon(R.drawable.ic_notification_music) })
        val trackSelector = DefaultTrackSelector(this).apply {
            parameters = buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
        }
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(app.spectrum.audioProcessor()))
                    .build()
        }
        player = ExoPlayer.Builder(this, renderers)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(PlayerMediaSources(this))
            .setLoadControl(RadioLoadControl { app.radio.options.value.tuning })
            .setUsePlatformDiagnostics(false)
            .build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                    true,
                )
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_LOCAL)
                volume = if (app.playbackSettings.muted.value) 0f else 1f
                addListener(playerListener)
            }
        val activityIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_APP_MUSIC)
                putExtra(StartupPlayback.OPEN_PLAYER_ONLY, true)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        sessionPlayer = RadioSessionPlayer(player, { navigationItems.map { it.mediaId } }, ::cancelRadioWork)
        session = MediaLibrarySession.Builder(this, sessionPlayer, SessionCallback())
            .setSessionActivity(activityIntent)
            .setBitmapLoader(LocalBitmapLoader(app.artwork, scope))
            .setMediaButtonPreferences(SystemMediaControls.buttons(this, enabled = false))
            .build()
        scope.launch {
            app.radioCatalogWarmed.first { it }
            val catalog = app.radioCatalog.state.value
            if (catalog.error == null) radioNavigationItems(catalog)
        }
        setListener(object : Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                player.pause()
                app.report(getString(R.string.foreground_failed))
            }
        })
        val savedMusic = scope.async {
            val saved = app.playback.read()
            app.repository.awaitReady()
            val library = app.repository.takeIf { it.isReadable }?.library?.value
            withContext(Dispatchers.Default) {
                if (library != null) PlaybackEdits.reconcile(saved, library.tracks) else saved
            }
        }
        scope.launch {
            val catalog = if (app.radio.options.value.active) {
                app.radioCatalog.awaitReady()
                app.radioCatalog.state.value
            } else null
            val radio = app.radio.options.value
            val station = catalog?.find(radio.stationId).takeIf { radio.active }
            if (station != null) {
                radioMode = true
                app.radio.select(station)
                player.shuffleModeEnabled = false
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.pauseAtEndOfMediaItems = true
                player.setWakeMode(C.WAKE_MODE_NETWORK)
                appliedRadioCatalog = catalog
                // The current stream can restore before the local library and full station timeline.
                sessionPlayer.setRadioQueue(listOf(station.mediaItem(this@PlaybackService)), 0)
                finishRestoration()
            }
            val snapshot = savedMusic.await()
            cachedQueue = snapshot.queue
            currentCollection = snapshot.collection
            app.playbackSettings.migrate(snapshot)
            appliedOptions = app.playbackSettings.options.value
            if (station != null) app.radio.rememberMusic(snapshot)
            else {
                applyPlayerOptions(appliedOptions)
                if (snapshot.queue.isNotEmpty()) {
                    val items = withContext(Dispatchers.Default) { snapshot.queue.map(Track::toMediaItem) }
                    player.setMediaItems(items, snapshot.index, snapshot.positionMs)
                }
            }
            musicRestored.complete(Unit)
            if (radio.active && station == null) {
                app.startupPlayback.cancel()
                app.radio.leave()
                app.report(getString(R.string.radio_station_invalid))
            }
            if (station == null) finishRestoration()
            if (station != null) sessionCommands.withLock {
                if (radioMode) sessionPlayer.updateRadioCatalog(radioNavigationItems(app.radioCatalog.state.value))
            }
            app.homeWidgets.attach(sessionPlayer)
            notifyBrowsers()
            observeLibraryReferences()
            observePlaybackSettings()
            observeRadioSettings()
            while (isActive) {
                delay(5_000)
                persist()
            }
        }
    }

    private fun finishRestoration() {
        cachedTimeline = player.currentTimeline
        restoring = false
        persist()
        restored.complete(Unit)
        app.playbackReady.value = true
        resumeStartupPlayback()
    }

    private fun resumeStartupPlayback() {
        if (app.startupPlayback.consume(app.displaySettings.options.value.autoPlayOnLaunch) &&
            player.mediaItemCount > 0 && !player.playWhenReady) resumePlayback()
    }

    private fun applyPlayerOptions(options: PlaybackOptions) {
        if (radioMode) return
        player.shuffleModeEnabled = options.order == PlayOrder.RANDOM
        player.repeatMode = options.loop.repeatMode
    }

    private fun observePlaybackSettings() {
        var selected: Set<String>? = null
        var pendingScope = false
        scope.launch {
            app.playbackSettings.muted.collect { muted -> player.volume = if (muted) 0f else 1f }
        }
        scope.launch {
            combine(app.playbackSettings.options, app.repository.library, app.repository.loaded) {
                options, library, loaded -> Triple(options, library, loaded)
            }.collect { (options, library, loaded) ->
                pendingScope = pendingScope || options.loop != appliedOptions.loop
                appliedOptions = options
                applyPlayerOptions(options)
                if (loaded && app.repository.isReadable) {
                    val nextSelected = PlaybackScope.selectedSources(library).map { it.uri }.toSet()
                    val rebuild = pendingScope || (selected != null && selected != nextSelected &&
                        options.loop == LoopMode.SELECTED_FOLDERS)
                    selected = nextSelected
                    pendingScope = false
                    if (!radioMode && rebuild && options.loop in listOf(LoopMode.FOLDER, LoopMode.SELECTED_FOLDERS)) {
                        applyFolderScope(library, options)
                    }
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
        if (radioMode || player.currentMediaItem != item || app.playbackSettings.options.value != options) return
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
        persist(rememberCollection = true)
        currentCollection = if (options.loop == LoopMode.FOLDER) PlaybackScope.folderFor(library, current)?.let {
            PlaybackCollection(CollectionKind.FOLDER, it.uri)
        } else null
        if (index < 0) player.pause()
        player.setMediaItems(tracks.map(Track::toMediaItem), index.coerceAtLeast(0), position)
        if (wasPrepared) player.prepare()
        if (current != null && index < 0) app.inform(getString(R.string.scope_changed_paused))
    }

    private fun observeLibraryReferences() {
        scope.launch {
            app.repository.awaitReady()
            if (!app.repository.isReadable) return@launch
            // Reconcile the live queue, not the original restore snapshot: intervening user commands win.
            var previous = cachedQueue.associateBy { it.id }
            var previousTracks: List<Track>? = null
            app.repository.library.collect { library ->
                if (previousTracks === library.tracks) return@collect
                if (radioMode) {
                    val saved = app.radio.savedMusic.value
                    if (saved != null) {
                        val updated = withContext(Dispatchers.Default) { PlaybackEdits.reconcile(saved, library.tracks) }
                        if (radioMode && app.radio.savedMusic.value === saved) {
                            app.radio.rememberMusic(updated)
                            app.playback.save(updated)
                            cachedQueue = updated.queue
                        }
                    }
                    previous = library.tracks.associateBy { it.id }
                    previousTracks = library.tracks
                    return@collect
                }
                val known = library.tracks.associateBy { it.id }
                player.reconcileLibraryQueue(previous, known)
                previous = known
                previousTracks = library.tracks
                refreshQueue()
                persist()
            }
        }
    }

    private fun observeRadioSettings() {
        scope.launch {
            app.radio.options.map { RadioNavigationOptions(it) }.distinctUntilChanged().collect {
                sessionCommands.withLock {
                    if (radioMode)
                        sessionPlayer.updateRadioCatalog(radioNavigationItems(app.radioCatalog.state.value))
                }
            }
        }
        scope.launch {
            app.radio.options.map { it.tuning.downloadLogos to it.stationLogos }.distinctUntilChanged().collect { (enabled, _) ->
                if (!enabled) {
                    logoJob?.cancel()
                    logoKey = null
                } else if (radioMode) updateRadioLogo()
            }
        }
        scope.launch {
            app.radio.options.map { it.active }.distinctUntilChanged().collectLatest { active ->
                if (active) app.radioCatalog.state.collect { catalog ->
                    if (!catalog.ready || catalog.error != null) return@collect
                    sessionCommands.withLock {
                        if (!radioMode ||
                            appliedRadioCatalog === catalog || catalog !== app.radioCatalog.state.value) return@withLock
                        if (appliedRadioCatalog?.localRevision == catalog.localRevision) {
                            val changed = appliedRadioCatalog?.programs !== catalog.programs
                            appliedRadioCatalog = catalog
                            if (changed) {
                                sessionPlayer.updateRadioCatalog(radioNavigationItems(catalog))
                                notifyBrowsers()
                            }
                            return@withLock
                        }
                        val current = currentRadioProgram()
                        val edited = catalog.find(player.currentMediaItem?.mediaId)
                        val retained = edited?.let {
                            when {
                                player.currentMediaItem?.hasSource(it.id, it.streamUri) == true -> it
                                appliedRadioCatalog?.find(it.id)?.streamUri == it.streamUri ->
                                    current?.copy(name = it.name, group = it.group, logoUri = it.logoUri)
                                else -> null
                            }
                        }
                        if (retained != null) {
                            appliedRadioCatalog = catalog
                            app.radio.select(retained)
                            val navigation = radioNavigationItems(catalog)
                            val items = if (navigation.any { it.mediaId == retained.id }) navigation.map {
                                if (it.mediaId == retained.id) retained.mediaItem(this@PlaybackService) else it
                            } else listOf(retained.mediaItem(this@PlaybackService)) + navigation
                            sessionPlayer.updateRadioCatalog(items, updateCurrent = true,
                                eligibleIds = navigation.map { it.mediaId })
                            notifyBrowsers()
                            return@withLock
                        }
                        val station = catalog.find(player.currentMediaItem?.mediaId) ?: catalog.programs.firstOrNull()
                        if (station == null) {
                            cancelRadioWork()
                            player.pause()
                            player.stop()
                            try { returnToMusic() }
                            catch (error: IOException) {
                                Log.e(TAG, "Cannot restore music after replacing an empty radio catalog", error)
                                app.report(getString(R.string.radio_command_failed, error.localizedMessage.orEmpty()))
                            }
                            return@withLock
                        }
                        changingSource = true
                        try {
                            cancelRadioWork()
                            player.pause()
                            player.stop()
                            setRadioQueue(catalog, station)
                            app.radio.select(station)
                            appliedRadioCatalog = catalog
                        } finally { changingSource = false }
                        notifyBrowsers()
                        app.inform(getString(R.string.radio_list_updated_paused))
                    }
                }
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // Media3 can defer this callback until after onPlayerError's scoped guard has ended.
            if (!playWhenReady && errorPausePending && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                errorPausePending = false
                return
            }
            if (!radioMode || changingSource || restoring || released) return
            if (!playWhenReady) {
                cancelRadioWork()
                if (player.playbackState != Player.STATE_IDLE) player.stop()
            }
            else if (playWhenReady && player.playbackState == Player.STATE_IDLE) {
                attemptedSources.clear()
                currentRadioProgram()?.streamUri?.let(attemptedSources::add)
                player.seekToDefaultPosition()
                player.prepare()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (restoring || released) return
            if (radioMode) {
                if (shuffleModeEnabled) player.shuffleModeEnabled = false
                return
            }
            val order = if (shuffleModeEnabled) PlayOrder.RANDOM else PlayOrder.SEQUENTIAL
            if (order != app.playbackSettings.options.value.order) app.playbackSettings.setOrder(order)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            if (restoring || released) return
            if (radioMode) {
                if (repeatMode != Player.REPEAT_MODE_OFF) player.repeatMode = Player.REPEAT_MODE_OFF
                return
            }
            if (repeatMode != app.playbackSettings.options.value.loop.repeatMode) {
                app.playbackSettings.setLoop(LoopMode.fromRepeatMode(repeatMode))
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION))
                app.lyricsTrackChanged(player.currentMediaItem?.mediaId.takeUnless { radioMode })
            app.spectrum.setPlaying(player.isPlaying)
            app.playbackActive.value = player.playWhenReady &&
                (player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY)
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_POSITION_DISCONTINUITY)
            ) app.spectrum.reset()
            if (restoring || released || changingSource) return
            app.playbackProblem.value?.let { problem ->
                val item = player.currentMediaItem
                if (!problem.matches(item?.mediaId, item?.localConfiguration?.uri?.toString()) ||
                    player.playbackState == Player.STATE_READY && player.playerError == null) {
                    app.playbackProblem.compareAndSet(problem, null)
                }
            }
            val canSeek = SystemMediaControls.canSeek(player)
            if (systemSeekEnabled != canSeek) {
                systemSeekEnabled = canSeek
                session?.setMediaButtonPreferences(SystemMediaControls.buttons(this@PlaybackService, canSeek))
            }
            if (radioMode) {
                val station = currentRadioProgram()
                (station ?: app.radioCatalog.find(player.currentMediaItem?.mediaId))?.let(app.radio::select)
                val key = station?.let { "${it.id}:${it.streamUri}" }
                if (trafficKey != key) {
                    trafficKey = key
                    app.radioTraffic.select(station?.id, station?.streamUri)
                    attemptedSources.clear()
                    station?.streamUri?.let(attemptedSources::add)
                }
                updateRadioLogo()
                if (player.playbackState == Player.STATE_IDLE && player.playWhenReady) player.pause()
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) notifyBrowsers()
                return
            }
            if (events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)
            ) {
                refreshQueue()
            }
            if (events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)
            ) {
                notifyBrowsers()
            }
            persist(rememberCollection = !player.playWhenReady &&
                (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                    events.contains(Player.EVENT_POSITION_DISCONTINUITY)))
        }

        override fun onPlayerError(error: PlaybackException) {
            val requested = player.playWhenReady
            val station = currentRadioProgram()
            if (requested) errorPausePending = true
            changingSource = true
            try { player.pause() }
            finally { changingSource = false }
            if (radioMode && requested && station != null && app.radio.options.value.tuning.autoSource) {
                attemptedSources.add(station.streamUri)
                val next = station.sources.indexOfFirst { it !in attemptedSources }
                if (next >= 0) {
                    val epoch = radioEpoch
                    app.inform(getString(R.string.radio_source_fallback, next + 1, station.sources.size))
                    radioFallback = scope.launch {
                        sessionCommands.withLock {
                            if (radioEpoch != epoch || !radioMode) return@withLock
                            try {
                                changingSource = true
                                val selected = app.radioCatalog.selectSource(station.id, next, station.sources[next])
                                if (radioEpoch == epoch && radioMode) {
                                    playRadio(selected, resetAttempts = false)
                                }
                            } catch (failure: IOException) {
                                Log.e(TAG, "Cannot switch radio source", failure)
                                rememberPlaybackProblem(error)
                                app.report(getString(R.string.radio_command_failed, failure.localizedMessage.orEmpty()))
                            } finally { changingSource = false }
                        }
                    }
                    return
                }
            }
            Log.e(TAG, "Playback stopped; automatic skipping is disabled", error)
            rememberPlaybackProblem(error)
            app.report(getString(
                if (radioMode) R.string.radio_failed else R.string.playback_failed,
                player.currentMediaItem?.mediaMetadata?.title ?: getString(R.string.unnamed_file),
                error.errorCodeName,
            ))
            persist()
        }
    }

    private fun rememberPlaybackProblem(error: PlaybackException) {
        val item = player.currentMediaItem ?: return
        app.playbackProblem.value = com.macarmusic.app.model.PlaybackProblem(
            item.mediaId, item.localConfiguration?.uri?.toString().orEmpty(),
            item.mediaMetadata.title?.toString().orEmpty(), error.errorCodeName, radioMode,
        )
    }

    private fun persist(rememberCollection: Boolean = false) {
        if (restoring || released || radioMode || changingSource) return
        if (cachedTimeline !== player.currentTimeline) refreshQueue()
        if (cachedQueue.isEmpty()) currentCollection = null
        val current = cachedQueue.getOrNull(player.currentMediaItemIndex)
        if (currentCollection != null && current != null && app.repository.isReadable &&
            !CollectionPositions.contains(app.repository.library.value, checkNotNull(currentCollection), current)) {
            currentCollection = null
        }
        val snapshot = PlaybackSnapshot(
            queue = cachedQueue,
            index = player.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = player.currentPosition.coerceAtLeast(0),
            shuffle = player.shuffleModeEnabled,
            repeat = player.repeatMode,
            collection = currentCollection,
        )
        app.playback.save(snapshot)
        if (rememberCollection && snapshot.collection != null) app.collections.remember(snapshot)
    }

    private fun refreshQueue() {
        if (radioMode) return
        val known = app.repository.library.value.tracks.associateBy { it.id }
        val previous = cachedQueue.associateBy { it.id }
        cachedQueue = (0 until player.mediaItemCount).map { index ->
            val item = player.getMediaItemAt(index)
            item.toTrack(known[item.mediaId] ?: previous[item.mediaId])
        }
        cachedTimeline = player.currentTimeline
    }

    private fun enterRadio(station: RadioProgram, catalog: RadioCatalogState) {
        if (!radioMode) {
            persist(rememberCollection = true)
            app.radio.rememberMusic(PlaybackSnapshot(
                queue = cachedQueue, index = player.currentMediaItemIndex.coerceAtLeast(0),
                positionMs = player.currentPosition.coerceAtLeast(0), shuffle = player.shuffleModeEnabled,
                repeat = player.repeatMode, collection = currentCollection,
            ))
        }
        radioMode = true
        app.radio.select(station)
        player.shuffleModeEnabled = false
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.pauseAtEndOfMediaItems = true
        player.setWakeMode(C.WAKE_MODE_NETWORK)
        appliedRadioCatalog = catalog
    }

    private suspend fun radioNavigationItems(catalog: RadioCatalogState): List<MediaItem> {
        val options = RadioNavigationOptions(app.radio.options.value)
        val locale = resources.configuration.locale
        if (navigationPrograms !== catalog.programs || navigationOptions != options || navigationLocale != locale) {
            navigationItems = withContext(Dispatchers.Default) {
                RadioNavigation.ordered(catalog.programs, options).map { it.mediaItem(this@PlaybackService) }
            }
            navigationPrograms = catalog.programs
            navigationOptions = options
            navigationLocale = locale
        }
        return navigationItems
    }

    private suspend fun setRadioQueue(catalog: RadioCatalogState, station: RadioProgram) {
        val navigation = radioNavigationItems(catalog)
        val items = navigation.takeIf { list -> list.any { it.mediaId == station.id } }
            ?: (listOf(station.mediaItem(this)) + navigation)
        sessionPlayer.setRadioQueue(items, items.indexOfFirst { it.mediaId == station.id }, navigation.map { it.mediaId })
    }

    private suspend fun playRadio(station: RadioProgram, resetAttempts: Boolean = true) {
        val catalog = app.radioCatalog.state.value
        val selected = catalog.find(station.id) ?: throw IOException(getString(R.string.radio_station_invalid))
        if (player.isActiveSource(selected.id, selected.streamUri)) return
        if (resetAttempts) {
            cancelRadioWork()
            attemptedSources.clear()
        }
        attemptedSources.add(selected.streamUri)
        trafficKey = "${selected.id}:${selected.streamUri}"
        app.radioTraffic.select(selected.id, selected.streamUri)
        enterRadio(selected, catalog)
        val navigationReady = navigationPrograms === catalog.programs &&
            navigationOptions == RadioNavigationOptions(app.radio.options.value) &&
            navigationLocale == resources.configuration.locale
        changingSource = true
        try {
            player.pause()
            if (navigationReady) setRadioQueue(catalog, selected)
            else sessionPlayer.setRadioQueue(listOf(selected.mediaItem(this)), 0)
            player.prepare()
            player.play()
        } finally {
            changingSource = false
        }
        // First use must not hold the stream behind the full station-navigation timeline.
        if (!navigationReady) sessionPlayer.updateRadioCatalog(radioNavigationItems(catalog))
    }

    private fun currentRadioProgram(): RadioProgram? =
        player.currentMediaItem?.localConfiguration?.tag as? RadioProgram

    private fun cancelRadioWork() {
        radioEpoch++
        radioFallback?.cancel()
        radioFallback = null
        logoJob?.cancel()
        logoJob = null
        logoKey = null
    }

    private fun updateRadioLogo() {
        if (!player.isPlaying || !app.radio.options.value.tuning.downloadLogos) return
        val station = currentRadioProgram() ?: return
        val key = app.radioLogos.identity(station)
        if (key == logoKey) return
        logoJob?.cancel()
        logoKey = key
        logoJob = scope.launch {
            try { app.radioLogos.download(station) }
            catch (error: IOException) {
                Log.w(TAG, "Radio logo download failed", error)
                app.inform(getString(R.string.radio_logo_failed))
            }
        }
    }

    private suspend fun returnToMusic(resume: Boolean = false) {
        while (radioMode) {
            val saved = app.radio.savedMusic.value
                ?: throw IOException(getString(R.string.playback_load_failed, "Missing saved music queue"))
            val library = app.repository.takeIf { it.isReadable }?.library?.value
            val (snapshot, items) = withContext(Dispatchers.Default) {
                val snapshot = if (library == null) saved else PlaybackEdits.reconcile(saved, library.tracks)
                snapshot to snapshot.queue.map(Track::toMediaItem)
            }
            if (app.radio.savedMusic.value !== saved ||
                app.repository.takeIf { it.isReadable }?.library?.value !== library) continue
            if (resume && items.isEmpty()) throw IOException(getString(R.string.no_tracks_to_play))
            changingSource = true
            try {
                cancelRadioWork()
                app.radioTraffic.select(null)
                trafficKey = null
                player.pause()
                player.stop()
                radioMode = false
                sessionPlayer.clearRadioQueue()
                player.pauseAtEndOfMediaItems = false
                app.radio.leave()
                player.setWakeMode(C.WAKE_MODE_LOCAL)
                cachedQueue = snapshot.queue
                currentCollection = snapshot.collection
                if (items.isEmpty()) player.clearMediaItems()
                else player.setMediaItems(items, snapshot.index, snapshot.positionMs)
                applyPlayerOptions(app.playbackSettings.options.value)
                cachedTimeline = player.currentTimeline
                app.radio.rememberMusic(null)
            } finally {
                changingSource = false
            }
            persist()
            notifyBrowsers()
        }
        if (resume) resumePlayback()
    }

    private fun resumePlayback() {
        if (player.mediaItemCount == 0) throw IOException(getString(R.string.no_tracks_to_play))
        if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
        if (player.playbackState == Player.STATE_IDLE || player.playerError != null) player.prepare()
        player.play()
    }

    private fun notifyBrowsers() {
        session?.notifyChildrenChanged(BROWSER_ROOT, if (player.currentMediaItem == null) 0 else 1, null)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        persist(rememberCollection = true)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        app.startupPlayback.cancel()
        persist()
        app.homeWidgets.detach(sessionPlayer)
        released = true
        app.playbackReady.value = false
        app.playbackActive.value = false
        app.radioPreparing.value = false
        scope.cancel()
        player.removeListener(playerListener)
        session?.release()
        session = null
        player.release()
        app.spectrum.setPlaying(false)
        clearListener()
        super.onDestroy()
    }

    private inner class SessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val result = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            var commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
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
                commands = SessionCommands.Builder()
                    .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT)
                    .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM)
                    .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN)
                    .add(SessionCommand.COMMAND_CODE_LIBRARY_SUBSCRIBE)
                    .add(SessionCommand.COMMAND_CODE_LIBRARY_UNSUBSCRIBE)
                    .build()
            }
            result.setAvailableSessionCommands(commands.buildUpon()
                .add(SystemMediaControls.back).add(SystemMediaControls.forward).apply {
                    if (controller.uid == Process.myUid()) HomeWidgetAction.entries.forEach { add(it.command) }
                    if (controller.uid == Process.myUid()) {
                        add(RadioCommands.play); add(RadioCommands.music); add(RadioCommands.startup)
                    }
                }.build())
            return result.build()
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            systemSeekEnabled = SystemMediaControls.canSeek(player)
            session.setMediaButtonPreferences(SystemMediaControls.buttons(this@PlaybackService, systemSeekEnabled))
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand == RadioCommands.startup) {
                if (controller.uid != Process.myUid())
                    return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
                return afterRestore {
                    resumeStartupPlayback()
                    SessionResult(SessionResult.RESULT_SUCCESS)
                }
            }
            if (customCommand == RadioCommands.play || customCommand == RadioCommands.music) {
                if (controller.uid != Process.myUid())
                    return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
                app.startupPlayback.cancel()
                val requested = if (customCommand == RadioCommands.play && app.radioCatalogInitialized)
                    app.radioCatalog.state.value.find(args.getString(RadioCommands.STATION)) else null
                val source = requested?.let {
                    it.sources.getOrNull(if (args.containsKey(RadioCommands.SOURCE)) args.getInt(RadioCommands.SOURCE) else it.sourceIndex)
                }
                if (customCommand == RadioCommands.music || requested == null || !player.isActiveSource(requested.id, source))
                    cancelRadioWork()
                return afterRestore(waitForLibrary = true) {
                    if (customCommand == RadioCommands.music) returnToMusic(resume = args.getBoolean(RadioCommands.RESUME, false))
                    else {
                        // Silence Music before the service publishes the pending Radio UI.
                        if (!radioMode) player.pause()
                        app.radioPreparing.value = true
                        try {
                            withContext(Dispatchers.IO) { app.radioCatalog.awaitReady() }
                            var station = app.radioCatalog.find(args.getString(RadioCommands.STATION))
                                ?: throw IOException(getString(R.string.radio_station_invalid))
                            if (args.containsKey(RadioCommands.SOURCE)) {
                                val index = args.getInt(RadioCommands.SOURCE)
                                if (index !in station.sources.indices) throw IOException(getString(R.string.radio_station_invalid))
                                changingSource = true
                                try { station = app.radioCatalog.selectSource(station.id, index, args.getString(RadioCommands.SOURCE_URI)) }
                                finally { changingSource = false }
                            }
                            playRadio(station)
                        } finally {
                            app.radioPreparing.value = false
                        }
                    }
                    SessionResult(SessionResult.RESULT_SUCCESS)
                }
            }
            HomeWidgetAction.from(customCommand.customAction)?.let { action ->
                if (controller.uid != Process.myUid()) {
                    return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
                }
                app.startupPlayback.cancel()
                return afterRestore { action.execute(app, sessionPlayer) }
            }
            val offset = SystemMediaControls.offset(customCommand)
                ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            app.startupPlayback.cancel()
            return afterRestore {
                if (SystemMediaControls.seek(player, offset)) SessionResult(SessionResult.RESULT_SUCCESS)
                else {
                    app.inform(getString(R.string.seek_unavailable))
                    SessionResult(SessionError.ERROR_INVALID_STATE)
                }
            }
        }

        override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo,
            playerCommand: Int): Int {
            if (app.radioPreparing.value && !radioMode &&
                (playerCommand == Player.COMMAND_PLAY_PAUSE || playerCommand == Player.COMMAND_PREPARE)) {
                return SessionError.ERROR_INVALID_STATE
            }
            when (playerCommand) {
                Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_STOP,
                Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD ->
                    app.startupPlayback.cancel()
            }
            if (radioMode && playerCommand in setOf(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD,
                    Player.COMMAND_SET_REPEAT_MODE, Player.COMMAND_SET_SHUFFLE_MODE)) {
                return SessionError.ERROR_NOT_SUPPORTED
            }
            return SessionResult.RESULT_SUCCESS
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
        ): ListenableFuture<LibraryResult<MediaItem>> = afterRestore(waitForLibrary = mediaId != BROWSER_ROOT) {
            val item = if (mediaId == BROWSER_ROOT) browserRoot() else {
                if (RadioProgram.isRadio(mediaId)) {
                    app.radioCatalog.awaitReady()
                    app.radioCatalog.find(mediaId)?.mediaItem(this@PlaybackService)
                } else (cachedQueue.firstOrNull { it.id == mediaId }
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
        ): ListenableFuture<List<MediaItem>> = afterRestore(waitForLibrary = true) {
            if (mediaItems.any { RadioProgram.isRadio(it.mediaId) }) throw IOException(getString(R.string.radio_queue_only))
            val items = resolveMediaItems(controller, mediaItems)
            returnToMusic()
            app.playback.allowReplacingUnreadableQueue()
            items
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = afterRestore(waitForLibrary = true) {
            val items = resolveMediaItems(controller, mediaItems)
            val catalog = if (items.any { RadioProgram.isRadio(it.mediaId) }) {
                app.radioCatalog.state.value
            } else null
            val station = catalog?.find(items.getOrNull(startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))?.mediaId)
            if (station != null) {
                val navigation = radioNavigationItems(checkNotNull(catalog))
                val current = navigation.takeIf { list -> list.any { it.mediaId == station.id } }
                    ?: (listOf(station.mediaItem(this@PlaybackService)) + navigation)
                enterRadio(station, checkNotNull(catalog))
                val selected = current.indexOfFirst { it.mediaId == station.id }
                return@afterRestore MediaSession.MediaItemsWithStartPosition(current, selected, C.TIME_UNSET)
            }
            returnToMusic()
            app.playback.allowReplacingUnreadableQueue()
            val queueIndex = if (controller.uid != Process.myUid() && items.size == 1) {
                cachedQueue.indexOfFirst { it.id == items.single().mediaId }
            } else C.INDEX_UNSET
            if (queueIndex >= 0) {
                // Media-ID resumption must not replace a saved multi-song queue with one song.
                val position = if (startPositionMs != C.TIME_UNSET || queueIndex == player.currentMediaItemIndex)
                    startPositionMs else 0
                MediaSession.MediaItemsWithStartPosition(cachedQueue.map(Track::toMediaItem), queueIndex, position)
            } else {
                persist(rememberCollection = true)
                currentCollection = if (controller.uid == Process.myUid()) mediaItems.firstOrNull()?.collection() else null
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

        private suspend fun resolveMediaItems(
            controller: MediaSession.ControllerInfo,
            items: List<MediaItem>,
        ): List<MediaItem> {
            if (items.any { RadioProgram.isRadio(it.mediaId) }) {
                app.radioCatalog.awaitReady()
                val catalog = app.radioCatalog.state.value
                return items.map { item ->
                    val station = catalog.find(item.mediaId)
                        ?: throw IOException(getString(R.string.radio_queue_only))
                    val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri
                    if (uri != null && uri.toString() !in station.sources) throw IOException(getString(R.string.radio_station_invalid))
                    station.mediaItem(this@PlaybackService)
                }
            }
            val resolved = if (controller.uid == Process.myUid()) {
                items.withLibraryMetadata(app.repository.library.value.tracks.associateBy { it.id })
            } else {
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
                if (radioMode) (0 until sessionPlayer.mediaItemCount).map(sessionPlayer::getMediaItemAt) else cachedQueue.map(Track::toMediaItem),
                sessionPlayer.currentMediaItemIndex.coerceAtLeast(0),
                if (radioMode) C.TIME_UNSET else player.currentPosition.coerceAtLeast(0),
            )
        }
    }

    private fun checkLocalItems(items: List<MediaItem>) {
        if (items.any { it.localConfiguration?.uri?.authority == BackupEdits.RESTORED_AUTHORITY }) {
            throw IOException(getString(R.string.reconnect_track_first))
        }
        if (items.any { it.localConfiguration?.uri?.scheme != "content" }) {
            throw IOException(getString(R.string.local_documents_only))
        }
    }

    private fun <T> afterRestore(waitForLibrary: Boolean = false, block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        val job = scope.launch {
            try {
                restored.await()
                if (waitForLibrary) musicRestored.await()
                // Source changes may map a large music queue off-main; later commands run afterwards.
                sessionCommands.withLock { future.set(block()) }
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
