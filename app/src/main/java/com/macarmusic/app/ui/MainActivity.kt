package com.macarmusic.app.ui

import androidx.appcompat.widget.TooltipCompat

import android.animation.Animator
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.Spinner
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.doOnNextLayout
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.common.util.concurrent.ListenableFuture
import com.macarmusic.app.R
import com.macarmusic.app.MaCarMusicApp
import com.macarmusic.app.databinding.ActivityMainBinding
import com.macarmusic.app.databinding.ViewNowPlayingBinding
import com.macarmusic.app.databinding.ViewSettingsBinding
import com.macarmusic.app.data.BackgroundEffects
import com.macarmusic.app.data.BackgroundTint
import com.macarmusic.app.data.DisplayOptions
import com.macarmusic.app.data.DisplaySettings
import com.macarmusic.app.data.MediaStoreAccess
import com.macarmusic.app.lyrics.LyricIssue
import com.macarmusic.app.lyrics.LyricOrigin
import com.macarmusic.app.lyrics.LyricState
import com.macarmusic.app.lyrics.OnlineLyricsClient
import com.macarmusic.app.model.AudioFiles
import com.macarmusic.app.model.BlockOutline
import com.macarmusic.app.model.BlockStyle
import com.macarmusic.app.model.BrowserLocation
import com.macarmusic.app.model.LyricAppearance
import com.macarmusic.app.model.LyricSide
import com.macarmusic.app.model.ThemeAccent
import com.macarmusic.app.model.DocumentEntry
import com.macarmusic.app.model.FolderSource
import com.macarmusic.app.model.LibraryEdits
import com.macarmusic.app.model.LocalPlaylist
import com.macarmusic.app.model.LoopMode
import com.macarmusic.app.model.Notice
import com.macarmusic.app.model.PlayOrder
import com.macarmusic.app.model.PlaybackScope
import com.macarmusic.app.model.PlaybackTimeMode
import com.macarmusic.app.model.NavigationPosition
import com.macarmusic.app.model.FireStyle
import com.macarmusic.app.model.SpectrumColors
import com.macarmusic.app.model.SortOrder
import com.macarmusic.app.model.SpectrumDecay
import com.macarmusic.app.model.SpectrumPalette
import com.macarmusic.app.model.SpectrumPosition
import com.macarmusic.app.model.SpectrumStyle
import com.macarmusic.app.model.Track
import com.macarmusic.app.model.TransitionStyle
import com.macarmusic.app.playback.PlaybackService
import com.macarmusic.app.playback.toMediaItem
import com.macarmusic.app.playback.toTrack
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.io.IOException
import java.io.File

@androidx.annotation.OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {
    private val model: MusicViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private lateinit var now: ViewNowPlayingBinding
    private lateinit var settings: ViewSettingsBinding
    private lateinit var libraryPanel: ListPanel
    private lateinit var folderPanel: ListPanel
    private lateinit var queuePanel: ListPanel
    private lateinit var playlistPanel: ListPanel
    private lateinit var libraryAdapter: MusicRowAdapter
    private lateinit var folderAdapter: MusicRowAdapter
    private lateinit var queueAdapter: MusicRowAdapter
    private lateinit var playlistAdapter: MusicRowAdapter
    private lateinit var favoriteFilter: MaterialButton
    private lateinit var libraryPlay: MaterialButton
    private lateinit var libraryActions: View
    private lateinit var librarySelectionActions: View
    private lateinit var libraryAddSelected: MaterialButton
    private lateinit var librarySelectAll: MaterialButton
    private lateinit var folderUp: MaterialButton
    private lateinit var folderPlay: MaterialButton
    private lateinit var folderRescan: MaterialButton
    private lateinit var cancelScan: MaterialButton
    private lateinit var playlistBack: MaterialButton
    private lateinit var playlistPlay: MaterialButton
    private lateinit var playlistAdd: MaterialButton
    private lateinit var playlistUpdate: MaterialButton
    private lateinit var audio: AudioManager
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val controllerConnected = MutableStateFlow(false)
    private val firstFrame = CompletableDeferred<Unit>()
    private var optionalContentStarted = false
    private var coreReported = false
    private var parkedNoticeShown = false
    private var deviceMusicDialog: AlertDialog? = null
    private var renderedDeviceMusic: DeviceMusicPicker? = null
    private var artworkJob: Job? = null
    private val slideshow by lazy {
        BackgroundSlideshow(binding.backgroundImage, binding.backgroundIncoming,
            model.app.backgrounds, model::backgroundIndex) { error ->
            model.app.report(getString(R.string.background_failed,
                if (error is SecurityException) getString(R.string.document_unavailable) else error.localizedMessage.orEmpty()))
        }
    }
    private var screenAnimator: Animator? = null
    private var visibleScreen: View? = null
    private var shownSettingsCategory: SettingsCategory? = null
    private var restoringSettingsScroll = false
    private var appliedControlBlocks: Pair<Boolean, BlockOutline>? = null
    private var appliedNavigationPosition: NavigationPosition? = null
    private var appliedAccent = ThemeAccent.DEFAULT
    private var appliedCustomAccent: Int? = null
    private val compactChrome get() = resources.configuration.screenHeightDp < 480
    private val controlBlockContainers by lazy {
        listOf<View>(now.controls.trackInformation, now.controls.spectrum, now.controls.seekBlock, now.controls.volumeBlock)
    }
    private val controlBlockButtons by lazy {
        listOf(now.controls.shuffle, now.controls.previous, now.controls.rewind, now.controls.play,
            now.controls.forward, now.controls.next, now.controls.repeat,
            binding.miniPlay, binding.miniNext)
    }
    private val controlBlockOverlays by lazy {
        listOfNotNull<View>(now.cover.root, binding.miniPlayer.takeUnless { compactChrome },
            now.controls.favorite, now.controls.details)
    }
    private val originalControlBackgrounds by lazy {
        (controlBlockContainers + controlBlockButtons).associateWith { it.background }
    }
    private val originalControlPadding by lazy {
        (controlBlockContainers + now.cover.root + listOfNotNull(binding.miniPlayer.takeUnless { compactChrome })).associateWith {
            android.graphics.Rect(it.paddingStart, it.paddingTop, it.paddingEnd, it.paddingBottom)
        }
    }
    private val originalActionShapes by lazy {
        listOf(now.controls.favorite, now.controls.details).associateWith { it.shapeAppearanceModel }
    }
    private val originalActionStrokes by lazy {
        listOf(now.controls.favorite, now.controls.details).associateWith { it.strokeWidth }
    }
    private var customSystemBars = false
    private val barElevations by lazy {
        listOf(binding.toolbar, binding.navigation, binding.miniPlayer).associateWith { it.elevation }
    }
    private val navigationIndicator by lazy { binding.navigation.isItemActiveIndicatorEnabled }
    private val lyricsAdapter = LyricsAdapter()
    private lateinit var lyricScrolling: LyricScrollController
    private var renderedLyricTrackId: String? = null
    private var artworkUri: String? = null
    private var artworkWarning: String? = null
    private var draggingSeek: SeekBar? = null
    private var draggingTrackId: String? = null
    private var shownNotice: Notice? = null
    private var playlistTrackRows = emptyList<Track>()
    private var queueTracks = emptyList<Track>()
    private var queueSignature: List<Pair<String, String>> = emptyList()
    private var queueScrollPending = false

    private val pickFolder = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri == null) model.app.report(getString(R.string.picker_no_document))
            else model.addFolder(uri, result.data?.flags ?: 0)
        }
    }

    private val pickFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult(), ::handlePickedFile)
    private val pickBackground = registerForActivityResult(ActivityResultContracts.StartActivityForResult(), ::handlePickedBackground)
    // A failed start may still send RESULT_CANCELED; it must not consume the fallback's result.
    private val compatibleFiles = List(2) {
        registerForActivityResult(ActivityResultContracts.StartActivityForResult(), ::handlePickedFile)
    }
    private val compatiblePictures = List(2) {
        registerForActivityResult(ActivityResultContracts.StartActivityForResult(), ::handlePickedBackground)
    }

    private fun handlePickedFile(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri == null) model.app.report(getString(R.string.picker_no_document))
            else model.openFile(uri, result.data?.flags ?: 0)
        }
    }

    private fun handlePickedBackground(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            val selected = buildList {
                result.data?.clipData?.let { clip ->
                    for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(::add)
                }
                result.data?.data?.let(::add)
            }.distinct()
            if (selected.isEmpty()) model.app.report(getString(R.string.picker_no_document))
            else model.importBackgrounds(selected)
        }
    }

    private val requestMusicPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val permission = MediaStoreAccess.requiredPermission()
        val allowed = granted && model.repository.documents.deviceMusic.hasPermission()
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putBoolean("musicPermissionBlocked.$permission",
                !allowed && (granted || !shouldShowRequestPermissionRationale(permission))).apply()
        if (allowed) model.loadDeviceMusicFolders()
        else model.app.report(getString(R.string.music_permission_denied))
    }

    private val musicPermissionSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (model.repository.documents.deviceMusic.hasPermission()) model.loadDeviceMusicFolders()
        else model.app.report(getString(R.string.music_permission_denied))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_ManaPlayer)
        super.onCreate(savedInstanceState)
        appliedAccent = (application as MaCarMusicApp).displaySettings.options.value.themeAccent
        appliedCustomAccent = (application as MaCarMusicApp).displaySettings.options.value.customAccentColor
        AccentTheme.apply(this, appliedAccent)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureCompactChrome()
        val display = model.app.displaySettings.options.value
        configureSystemBars(display.customTheme && display.imageVersion > 0)
        ViewCompat.setOnApplyWindowInsetsListener(binding.content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            WindowInsetsCompat.CONSUMED
        }
        audio = getSystemService(AudioManager::class.java)
        volumeControlStream = AudioManager.STREAM_MUSIC
        buildPlayerScreen()
        AccentTheme.tintControls(binding.root)
        configurePlaybackControls()
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> model.openSettings()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        binding.toolbar.setNavigationOnClickListener { model.closeSettings() }
        binding.navigation.setOnItemSelectedListener(model::screen)
        binding.miniInfo.setOnClickListener { model.screen(R.id.nav_player) }
        binding.miniArtwork.setOnClickListener { model.screen(R.id.nav_player) }
        binding.dismissNotice.setOnClickListener { shownNotice?.let { model.app.dismissNotice(it.id) } }
        onBackPressedDispatcher.addCallback(this) {
            when {
                model.selectedScreen.value == R.id.action_settings -> model.closeSettings()
                model.selectedScreen.value == R.id.nav_library && model.librarySelecting.value -> model.selectLibrary(false)
                model.selectedScreen.value == R.id.nav_folders && model.browserUp() -> Unit
                model.selectedScreen.value == R.id.nav_playlists && model.selectedPlaylist.value != null -> model.playlist(null)
                model.selectedScreen.value != R.id.nav_player -> model.screen(R.id.nav_player)
                else -> finish()
            }
        }
        configureNavigation(display.navigationPosition)
        renderBackground(display)
        showScreen(model.selectedScreen.value)
        observeState()
        lifecycleScope.launch {
            binding.root.awaitDraw()
            firstFrame.complete(Unit)
        }
    }

    private fun showParkedNotice() {
        if (parkedNoticeShown) return
        parkedNoticeShown = true
        val preferences = getSharedPreferences("settings", MODE_PRIVATE)
        if (!preferences.getBoolean("parkedNoticeSeen", false)) {
            AccentTheme.dialog(this).setTitle(R.string.parked_title).setMessage(R.string.parked_body)
                .setPositiveButton(R.string.understood) { _, _ ->
                    preferences.edit().putBoolean("parkedNoticeSeen", true).apply()
                }.show()
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        val display = model.app.displaySettings.options.value
        configureSystemBars(display.customTheme && display.imageVersion > 0)
    }

    override fun onStop() {
        screenAnimator?.cancel()
        visibleScreen?.let(ViewTransitions::reset)
        if (optionalContentStarted) model.app.spectrum.setVisible(this, false)
        now.controls.spectrum.setPlaybackActive(false)
        model.setLyricsActive(false)
        if (artworkJob?.isActive == true) {
            artworkJob?.cancel()
            artworkUri = null
        }
        lyricScrolling.stop()
        draggingSeek = null
        draggingTrackId = null
        controller?.removeListener(playerListener)
        controller = null
        controllerConnected.value = false
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        super.onStop()
    }

    override fun onDestroy() {
        deviceMusicDialog?.dismiss()
        deviceMusicDialog = null
        artworkJob?.cancel()
        screenAnimator?.cancel()
        if (optionalContentStarted) model.app.spectrum.setVisible(this, false)
        super.onDestroy()
    }

    private fun connectController() {
        if (controllerFuture != null) return
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (this@MainActivity.controller === controller) {
                        this@MainActivity.controller = null
                        controllerConnected.value = false
                        controllerFuture = null
                        model.app.report(getString(R.string.connection_closed))
                        updatePlayer()
                    }
                }
            }).buildAsync()
        controllerFuture = future
        future.addListener({
            if (controllerFuture !== future) return@addListener
            try {
                controller = future.get().also { it.addListener(playerListener) }
                controllerConnected.value = true
                updatePlayer()
                updateQueue()
                consumePlayRequest()
            } catch (error: ExecutionException) {
                controllerFuture = null
                model.app.report(getString(R.string.controller_failed, error.cause?.localizedMessage.orEmpty()))
            } catch (error: CancellationException) {
                controllerFuture = null
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                model.app.report(getString(R.string.controller_failed, error.localizedMessage.orEmpty()))
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updatePlayer()
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) queueScrollPending = true
            if (events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)
            ) updateQueue()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.selectedScreen.collectLatest { screen ->
                    showScreen(screen)
                    if (screen == R.id.nav_library) model.visibleTracks.collect { renderLibrary() }
                } }
                launch { model.settingsCategory.collect(::showSettingsCategory) }
                launch { model.favoritesOnly.collect { renderLibrary() } }
                launch { model.librarySelecting.collect { renderLibrary() } }
                launch { model.librarySelection.collect { renderLibrary() } }
                launch { model.library.collect {
                    if (model.repository.loaded.value) model.pruneLibrarySelection()
                    renderFolders()
                    renderPlaylists()
                    renderSettings()
                    updatePlayer()
                    updateQueue(force = true)
                } }
                launch { model.repository.loaded.collect { renderLibrary(); renderFolders(); renderSettings() } }
                launch { model.browser.collect { renderFolders() } }
                launch { model.scan.collect { renderFolders(); renderSettings() } }
                launch { model.selectedPlaylist.collect { renderPlaylists() } }
                launch { model.app.notice.collect(::renderNotice) }
                launch { model.playRequest.collect { consumePlayRequest() } }
                launch { model.app.playbackSettings.options.collect { renderSettings(); updatePlayer() } }
                launch { model.app.playbackReady.collect { updatePlayer() } }
                launch { model.app.displaySettings.options.collect {
                    if (it.themeAccent != appliedAccent || it.customAccentColor != appliedCustomAccent) {
                        recreate()
                        return@collect
                    }
                    renderSettings()
                    configureNavigation(it.navigationPosition)
                    renderBackground(it)
                    renderLyrics(model.lyrics.value)
                    updateProgress()
                    updateSpectrum()
                } }
                launch { model.lyrics.collect(::renderLyrics) }
                launch { model.importingBackground.collect { renderSettings() } }
                launch { model.deviceMusic.collect(::renderDeviceMusicPicker) }
                launch {
                    firstFrame.await()
                    while (isActive) {
                        updateProgress()
                        updateVolume()
                        delay(500)
                    }
                }
                launch {
                    firstFrame.await()
                    connectController()
                    combine(controllerConnected, model.app.playbackReady) { connected, restored ->
                        connected && restored
                    }.first { it }
                    binding.root.awaitDraw()
                    if (!coreReported) {
                        coreReported = true
                        reportFullyDrawn()
                    }
                    optionalContentStarted = true
                    model.setLyricsActive(true)
                    updatePlayer()
                    showParkedNotice()
                    try {
                        launch {
                            model.repository.loaded.first { it }
                            delay(1_000)
                            model.app.displaySettings.options.distinctUntilChanged { old, next ->
                                old.customTheme == next.customTheme && old.imageVersion == next.imageVersion &&
                                    old.imageIds == next.imageIds && old.blur == next.blur && old.slideshow == next.slideshow
                            }.collectLatest { display ->
                                slideshow.show(display, model.backgroundIndex(display.imageVersion, display.backgroundImages.size))
                            }
                        }
                        launch { model.app.spectrum.frames.collect { now.controls.spectrum.submit(it) } }
                        awaitCancellation()
                    } finally {
                        model.app.spectrum.setVisible(this@MainActivity, false)
                        optionalContentStarted = false
                        model.setLyricsActive(false)
                    }
                }
            }
        }
    }

    private fun buildPlayerScreen() {
        now = ViewNowPlayingBinding.inflate(layoutInflater, binding.screenContainer, false)
        binding.screenContainer.addView(now.root)
        now.cover.lyricsList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = lyricsAdapter
            itemAnimator = null
        }
        lyricScrolling = LyricScrollController(now.cover.lyricsList, lyricsAdapter,
            now.cover.lyricSeekPreview, now.cover.lyricDragGuide) { trackId, position ->
            val player = controller
            if (player?.currentMediaItem?.mediaId == trackId && model.app.displaySettings.options.value.lyricsDragSeek &&
                player.isCurrentMediaItemSeekable && player.duration > 0 &&
                player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) &&
                player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)
            ) {
                player.seekTo(position.coerceIn(0, player.duration - 1))
                player.play()
            }
        }
        now.cover.retryLyrics.setOnClickListener { model.requestLyrics(currentTrack(), refresh = true) }
        now.controls.spectrum.setOnClickListener {
            model.app.displaySettings.update { it.copy(spectrum = it.spectrum.nextMode()) }
        }
    }

    private fun screenView(id: Int): View {
        val view = when (id) {
            R.id.nav_library -> {
                if (!::libraryPanel.isInitialized) buildLibrary()
                libraryPanel
            }
            R.id.nav_folders -> {
                if (!::folderPanel.isInitialized) buildFolders()
                folderPanel
            }
            R.id.nav_queue -> {
                if (!::queuePanel.isInitialized) buildQueue()
                queuePanel
            }
            R.id.nav_playlists -> {
                if (!::playlistPanel.isInitialized) buildPlaylists()
                playlistPanel
            }
            R.id.action_settings -> {
                if (!::settings.isInitialized) buildSettings()
                settings.root
            }
            else -> now.root
        }
        if (view.parent == null) {
            AccentTheme.tintControls(view)
            view.isVisible = false
            view.setBackgroundColor(Color.TRANSPARENT)
            binding.screenContainer.addView(view)
        }
        return view
    }

    private fun buildLibrary() {
        libraryPanel = ListPanel(this, compactChrome)
        libraryPanel.list.id = R.id.libraryList
        val searchLayout = TextInputLayout(this).apply {
            hint = getString(R.string.search_hint)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
            setPadding(dp(16), 0, dp(16), 0)
        }
        val search = TextInputEditText(searchLayout.context).apply {
            setSingleLine()
            setText(model.query.value)
            textSize = 16f
            minHeight = dp(if (compactChrome) 48 else 56)
            doAfterTextChanged { model.query(it?.toString().orEmpty()) }
        }
        searchLayout.addView(search)
        libraryPanel.top.addView(searchLayout)
        libraryPlay = libraryPanel.action(R.string.play_all) { model.requestPlay(model.visibleTracks.value, useLoopScope = true) }
        favoriteFilter = libraryPanel.action(R.string.favorites_only) { model.toggleFavorites() }
        libraryActions = libraryPanel.actionRow(
            libraryPlay,
            libraryPanel.action(R.string.select_songs) { model.selectLibrary(true) }.apply { id = R.id.librarySelection },
            favoriteFilter,
            libraryPanel.action(R.string.sort) { sortDialog() },
        )
        libraryAddSelected = libraryPanel.action(R.string.add_playlist) {
            val selected = model.librarySelection.value.toSet()
            choosePlaylist(model.library.value.tracks.filter { it.id in selected })
        }.apply { id = R.id.libraryAddSelected }
        librarySelectAll = libraryPanel.action(R.string.select_all_shown) { model.selectVisibleTracks() }
            .apply { id = R.id.librarySelectAll }
        librarySelectionActions = libraryPanel.actionRow(libraryAddSelected, librarySelectAll,
            libraryPanel.action(R.string.cancel) { model.selectLibrary(false) }).apply { isVisible = false }
        if (compactChrome) {
            libraryPanel.top.orientation = LinearLayout.HORIZONTAL
            libraryPanel.top.gravity = android.view.Gravity.CENTER_VERTICAL
            searchLayout.isHintEnabled = false
            searchLayout.setPadding(dp(8), 0, dp(8), 0)
            searchLayout.layoutParams = LinearLayout.LayoutParams(dp(184), dp(48))
            search.hint = getString(R.string.search_hint)
            search.setPaddingRelative(search.paddingStart, 0, search.paddingEnd, 0)
            search.layoutParams.height = dp(48)
            for (actions in listOf(libraryActions, librarySelectionActions)) {
                actions.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        }
        libraryAdapter = MusicRowAdapter(
            compact = compactChrome,
            onClick = { row ->
                if (model.librarySelecting.value) model.selectTrack(row.key)
                else {
                    val tracks = model.visibleTracks.value
                    val index = tracks.indexOfFirst { it.id == row.key }
                    if (index >= 0) model.requestPlay(tracks, index, useLoopScope = true)
                }
            },
            onMore = { row -> findTrack(row.key)?.let(::trackMenu) },
            onSelect = { row -> model.selectTrack(row.key) },
        )
        libraryPanel.list.adapter = libraryAdapter
    }

    private fun buildFolders() {
        folderPanel = ListPanel(this, compactChrome)
        folderUp = folderPanel.action(R.string.up_folder) { model.browserUp() }
        folderPlay = folderPanel.action(R.string.play_folder) {
            if (model.browser.value.path.isEmpty()) model.playSelectedFolders() else playCurrentFolder()
        }
        folderRescan = folderPanel.action(R.string.rescan_all) {
            val source = currentSource()
            if (source == null) model.scanFolders(model.library.value.sources)
            else model.scanFolders(listOf(source), model.browser.value.path.lastOrNull())
            model.refreshBrowser()
        }
        cancelScan = folderPanel.action(R.string.cancel_scan) { model.cancelScan() }
        folderPanel.actionRow(
            folderPanel.action(R.string.add_folder) { launchFolderPicker() },
            folderPanel.action(R.string.select_folders) { foldersDialog() },
            folderPanel.action(R.string.open_file) { launchFilePicker() },
            folderPanel.action(R.string.device_music) { launchDeviceMusic() },
            folderUp,
            folderPlay,
            folderRescan,
            folderPanel.action(R.string.save_folder_playlist) { saveFoldersDialog() },
            cancelScan,
        )
        folderAdapter = MusicRowAdapter(
            compact = compactChrome,
            onClick = { row ->
                val state = model.browser.value
                if (state.path.isEmpty()) model.library.value.sources.firstOrNull { it.uri == row.key }?.let(model::openSource)
                else state.entries.firstOrNull { it.uri == row.key }?.let {
                    if (it.isDirectory) model.openDirectory(it) else model.playBrowserFile(it)
                }
            },
            onMore = { row ->
                val state = model.browser.value
                if (state.path.isEmpty()) model.library.value.sources.firstOrNull { it.uri == row.key }?.let(::sourceMenu)
                else state.entries.firstOrNull { it.uri == row.key }?.let(::documentMenu)
            },
        )
        folderPanel.list.adapter = folderAdapter
    }

    private fun buildQueue() {
        queuePanel = ListPanel(this, compactChrome)
        queuePanel.list.id = R.id.queueList
        queuePanel.actionRow(
            queuePanel.action(R.string.save_queue) {
                if (queueTracks.isEmpty()) model.app.inform(getString(R.string.no_tracks_to_play))
                else newPlaylistDialog(queueTracks)
            },
            queuePanel.action(R.string.clear_queue) {
                AccentTheme.dialog(this).setTitle(R.string.clear_queue)
                    .setMessage(R.string.clear_queue_body)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.clear_queue) { _, _ ->
                        withController { it.pause(); it.clearMediaItems() }
                    }.show()
            },
        )
        queueAdapter = MusicRowAdapter(
            compact = compactChrome,
            onClick = { row ->
                val index = row.key.substringBefore(':').toIntOrNull()
                if (index != null) withController {
                    if (index < it.mediaItemCount) {
                        it.seekToDefaultPosition(index)
                        if (it.playbackState == Player.STATE_IDLE) it.prepare()
                        it.play()
                    }
                }
            },
            onMore = { row -> row.key.substringBefore(':').toIntOrNull()?.let(::queueMenu) },
        )
        queuePanel.list.adapter = queueAdapter
    }

    private fun buildPlaylists() {
        playlistPanel = ListPanel(this, compactChrome)
        playlistBack = playlistPanel.action(R.string.back_playlists) { model.playlist(null) }
        playlistPlay = playlistPanel.action(R.string.play_all) { model.requestPlay(playlistTrackRows) }
        playlistAdd = playlistPanel.action(R.string.add_songs) { addSongsDialog() }
        playlistUpdate = playlistPanel.action(R.string.update_playlist) {
            model.library.value.playlists.firstOrNull { it.id == model.selectedPlaylist.value }?.let(::playlistMenu)
        }.apply { id = R.id.playlistUpdate }
        playlistPanel.actionRow(
            playlistPanel.action(R.string.create_playlist) { newPlaylistDialog() },
            playlistPanel.action(R.string.save_folder_playlist) { saveFoldersDialog() }.apply { id = R.id.playlistSaveFolders },
            playlistBack, playlistPlay, playlistAdd, playlistUpdate,
        )
        playlistAdapter = MusicRowAdapter(
            compact = compactChrome,
            onClick = { row ->
                if (model.selectedPlaylist.value == null) model.playlist(row.key)
                else {
                    val index = playlistTrackRows.indexOfFirst { it.id == row.key }
                    if (index >= 0) model.requestPlay(playlistTrackRows, index)
                }
            },
            onMore = { row ->
                val playlistId = model.selectedPlaylist.value
                if (playlistId == null) model.library.value.playlists.firstOrNull { it.id == row.key }?.let(::playlistMenu)
                else findTrack(row.key)?.let { track -> trackMenu(track, playlistId) }
            },
        )
        playlistPanel.list.adapter = playlistAdapter
    }

    private fun buildSettings() {
        settings = ViewSettingsBinding.inflate(layoutInflater, binding.screenContainer, false)
        settings.settingsHeading.isVisible = !compactChrome
        SettingsCategory.pages.forEachIndexed { index, category ->
            if (index > 0) settings.settingsCategories.addView(View(this).apply {
                id = R.id.settingsCategoryDivider
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, index * 2 - 1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(16)).apply {
                marginStart = dp(8)
                marginEnd = dp(8)
            })
            settings.root.findViewById<MaterialButton>(category.buttonId).setOnClickListener {
                model.settingsCategory(category)
            }
        }
        renderCategoryDividers(model.app.displaySettings.options.value.blockOutline)
        ViewCompat.setAccessibilityHeading(settings.settingsHeading, true)
        settings.settingsScroll.isSaveEnabled = false
        settings.settingsScroll.setOnScrollChangeListener { _, _, y, _, _ ->
            if (!restoringSettingsScroll) shownSettingsCategory?.let { model.settingsScroll(it, y) }
        }
        showSettingsCategory(model.settingsCategory.value)
        settings.settingsLanguage.setOnClickListener { languageDialog() }
        settings.settingsTheme.setOnClickListener { themeDialog() }
        settings.settingsAccent.setOnClickListener { accentDialog() }
        settings.settingsUseCustomTheme.setOnClickListener { model.app.themeMode = DisplaySettings.CUSTOM_THEME }
        settings.settingsControlBlocks.setOnCheckedChangeListener { _, checked ->
            model.app.displaySettings.update { it.copy(controlBlocks = checked) }
        }
        settings.settingsBlockCharacter.setOnClickListener { blockCharacterDialog() }
        settings.blockArc.setOnSeekBarChangeListener(backgroundSlider(
            label = { settings.blockArcValue.text = getString(R.string.block_arc_value, it) },
            save = { value -> model.app.displaySettings.update { it.copy(blockOutline = it.blockOutline.copy(arcDp = value)) } },
        ))
        settings.settingsBlockShadow.setOnCheckedChangeListener { _, checked ->
            model.app.displaySettings.update { it.copy(blockOutline = it.blockOutline.copy(shadow = checked)) }
        }
        settings.settingsLyrics.setOnCheckedChangeListener { _, checked ->
            model.app.displaySettings.update { it.copy(lyricsEnabled = checked) }
        }
        settings.settingsOnlineLyrics.setOnCheckedChangeListener { _, checked ->
            model.app.displaySettings.update { it.copy(onlineLyrics = checked) }
        }
        settings.settingsLyricDragSeek.setOnCheckedChangeListener { _, checked ->
            model.app.displaySettings.update { it.copy(lyricsDragSeek = checked) }
        }
        settings.settingsLyricSmoothScroll.setOnCheckedChangeListener { _, checked ->
            model.app.displaySettings.update { it.copy(lyricsSmoothScroll = checked) }
        }
        settings.lyricActiveSize.setOnSeekBarChangeListener(backgroundSlider(
            label = { settings.lyricActiveSizeValue.text = getString(R.string.font_size_value, getString(R.string.lyric_active_size), it + 12) },
            save = { value -> model.app.displaySettings.update { it.copy(lyricAppearance = it.lyricAppearance.copy(activeSizeSp = value + 12)) } },
        ))
        settings.lyricOtherSize.setOnSeekBarChangeListener(backgroundSlider(
            label = { settings.lyricOtherSizeValue.text = getString(R.string.font_size_value, getString(R.string.lyric_other_size), it + 12) },
            save = { value -> model.app.displaySettings.update { it.copy(lyricAppearance = it.lyricAppearance.copy(otherSizeSp = value + 12)) } },
        ))
        settings.lyricActiveColor.setOnClickListener { lyricColorDialog(active = true) }
        settings.lyricOtherColor.setOnClickListener { lyricColorDialog(active = false) }
        settings.lyricResetAppearance.setOnClickListener {
            model.app.displaySettings.update { it.copy(lyricAppearance = LyricAppearance()) }
        }
        settings.lyricsCacheLocation.text = getString(R.string.lyrics_cache_location, File(cacheDir, "lyrics").absolutePath)
        settings.settingsBackgroundImage.setOnClickListener { launchBackgroundPicker() }
        settings.settingsBackgroundTint.setOnClickListener { backgroundTintDialog() }
        settings.backgroundInterval.setOnSeekBarChangeListener(backgroundSlider(
            label = { settings.backgroundIntervalValue.text = resources.getQuantityString(R.plurals.background_interval_value, it + 1, it + 1) },
            save = { value ->
                model.app.displaySettings.update { it.copy(slideshow = it.slideshow.withInterval(value + 1)) }
            },
        ))
        settings.backgroundDuration.setOnSeekBarChangeListener(backgroundSlider(
            label = { settings.backgroundDurationValue.text = getString(R.string.background_duration_value, (it / 10.0).toString()) },
            save = { value ->
                model.app.displaySettings.update { it.copy(slideshow = it.slideshow.copy(durationMs = value * 100)) }
            },
        ))
        val display = model.app.displaySettings.options.value
        choices(settings.lyricsLandscapePosition, LyricSide.entries, display.landscapeLyrics,
            { getString(if (it == LyricSide.START) R.string.position_left else R.string.position_right) }) { side ->
            model.app.displaySettings.update { it.copy(landscapeLyrics = side) }
        }
        choices(settings.lyricsPortraitPosition, LyricSide.entries, display.portraitLyrics,
            { getString(if (it == LyricSide.START) R.string.navigation_top else R.string.navigation_bottom) }) { side ->
            model.app.displaySettings.update { it.copy(portraitLyrics = side) }
        }
        choices(settings.settingsBlockStyle, BlockStyle.entries, display.blockOutline.style,
            { getString(blockStyleLabel(it)) }) { value ->
            model.app.displaySettings.update { it.copy(blockOutline = it.blockOutline.copy(style = value)) }
        }
        choices(settings.settingsNavigationPosition, NavigationPosition.entries, display.navigationPosition,
            { getString(navigationLabel(it)) }) { value ->
            model.app.displaySettings.update { it.copy(navigationPosition = value) }
        }
        choices(settings.settingsMainTimeMode, PlaybackTimeMode.entries, display.mainTimeMode,
            { getString(if (it == PlaybackTimeMode.ELAPSED) R.string.time_elapsed else R.string.time_remaining) }) { value ->
            model.app.displaySettings.update { it.copy(mainTimeMode = value) }
        }
        choices(settings.backgroundTransition, TransitionStyle.entries, display.slideshow.transition,
            { getString(transitionLabel(it)) }) { value ->
            model.app.displaySettings.update { it.copy(slideshow = it.slideshow.copy(transition = value)) }
        }
        choices(settings.settingsTransition, TransitionStyle.entries, display.settingsTransition,
            { getString(transitionLabel(it)) }) { value ->
            model.app.displaySettings.update { it.copy(settingsTransition = value) }
        }
        choices(settings.settingsTimeMode, PlaybackTimeMode.entries, display.timeMode,
            { getString(if (it == PlaybackTimeMode.ELAPSED) R.string.time_elapsed else R.string.time_remaining) }) { value ->
            model.app.displaySettings.update { it.copy(timeMode = value) }
        }
        buildSpectrumSettings()
        settings.backgroundBlur.setOnSeekBarChangeListener(backgroundSlider(
            label = { settings.backgroundBlurValue.text = getString(R.string.background_blur_value, it) },
            save = { value -> model.app.displaySettings.update { it.copy(blur = value) } },
        ))
        settings.backgroundTintStrength.setOnSeekBarChangeListener(backgroundSlider(
            label = { settings.backgroundTintValue.text = getString(R.string.background_tint_value, it) },
            save = { value -> model.app.displaySettings.update { it.copy(tintStrength = value) } },
        ))
        settings.settingsLoop.setOnClickListener { loopDialog() }
        settings.settingsOrder.setOnClickListener { orderDialog() }
        settings.settingsFolders.setOnClickListener { foldersDialog() }
        settings.settingsPinFolders.setOnClickListener { pinFoldersDialog() }
        settings.settingsAddFolder.setOnClickListener { launchFolderPicker() }
        settings.settingsDeviceMusic.setOnClickListener { launchDeviceMusic() }
        settings.settingsPlayFolders.setOnClickListener { model.playSelectedFolders() }
        settings.settingsManageFolders.setOnClickListener { model.screen(R.id.nav_folders) }
    }

    private fun buildSpectrumSettings() {
        val fields = settings.spectrumSettings
        val spectrum = model.app.displaySettings.options.value.spectrum
        settings.settingsSpectrum.setOnCheckedChangeListener { _, checked ->
            model.app.displaySettings.update { it.copy(spectrum = it.spectrum.copy(enabled = checked)) }
        }
        fields.spectrumPeaks.setOnCheckedChangeListener { _, checked ->
            model.app.displaySettings.update { it.copy(spectrum = it.spectrum.copy(peaks = checked)) }
        }
        choices(fields.spectrumPosition, SpectrumPosition.entries, spectrum.position,
            { getString(if (it == SpectrumPosition.LEFT) R.string.position_left else R.string.position_right) }) { value ->
            model.app.displaySettings.update { it.copy(spectrum = it.spectrum.copy(position = value)) }
        }
        choices(fields.spectrumStyle, SpectrumStyle.entries, spectrum.style,
            { getString(if (it == SpectrumStyle.BARS) R.string.spectrum_bars else R.string.spectrum_line) }) { value ->
            model.app.displaySettings.update { it.copy(spectrum = it.spectrum.copy(style = value)) }
        }
        choices(fields.spectrumPalette, SpectrumPalette.entries, spectrum.palette,
            { getString(when (it) {
                SpectrumPalette.CLASSIC -> R.string.spectrum_classic
                SpectrumPalette.FIRE -> R.string.spectrum_fire
                SpectrumPalette.THEME -> R.string.spectrum_theme
            }) }) { value ->
            model.app.displaySettings.update { it.copy(spectrum = it.spectrum.copy(palette = value)) }
        }
        choices(fields.spectrumFireStyle, FireStyle.entries, spectrum.rendering,
            { getString(if (it == FireStyle.LED) R.string.spectrum_led else R.string.spectrum_smooth) }) { value ->
            model.app.displaySettings.update { it.copy(spectrum = it.spectrum.withRendering(value)) }
        }
        listOf(fields.spectrumColorLow, fields.spectrumColorMiddle, fields.spectrumColorHigh).forEachIndexed { index, button ->
            button.setOnClickListener { spectrumColorDialog(index) }
        }
        fields.spectrumResetColors.setOnClickListener {
            val palette = model.app.displaySettings.options.value.spectrum.palette
            changeSpectrumColors(palette) { if (palette == SpectrumPalette.FIRE) SpectrumColors.FIRE else SpectrumColors.CLASSIC }
        }
        choices(fields.spectrumBands, listOf(16, 32), spectrum.bands,
            { getString(if (it == 16) R.string.spectrum_thick else R.string.spectrum_thin) }) { value ->
            model.app.displaySettings.update { it.copy(spectrum = it.spectrum.copy(bands = value)) }
        }
        choices(fields.spectrumFalloff, SpectrumDecay.entries, spectrum.falloff, { getString(decayLabel(it)) }) { value ->
            model.app.displaySettings.update { it.copy(spectrum = it.spectrum.copy(falloff = value)) }
        }
        choices(fields.spectrumPeakFalloff, SpectrumDecay.entries, spectrum.peakFalloff, { getString(decayLabel(it)) }) { value ->
            model.app.displaySettings.update { it.copy(spectrum = it.spectrum.copy(peakFalloff = value)) }
        }
        choices(fields.spectrumRefreshRate, listOf(15, 30, 60), spectrum.refreshRate,
            { getString(R.string.spectrum_fps, it) }) { value ->
            model.app.displaySettings.update { it.copy(spectrum = it.spectrum.copy(refreshRate = value)) }
        }
    }

    private fun <T> choices(view: Spinner, values: List<T>, selected: T, label: (T) -> String, save: (T) -> Unit) {
        view.adapter = ArrayAdapter(this, R.layout.row_choice, values.map(label)).apply {
            setDropDownViewResource(R.layout.row_choice)
        }
        view.setSelection(values.indexOf(selected), false)
        view.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, child: View?, position: Int, id: Long) {
                save(values[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun spectrumColorDialog(index: Int) {
        val spectrum = model.app.displaySettings.options.value.spectrum
        val colors = if (spectrum.palette == SpectrumPalette.FIRE) spectrum.fireColors else spectrum.classicColors
        val color = listOf(colors.low, colors.middle, colors.high)[index]
        val label = listOf(R.string.spectrum_color_low, R.string.spectrum_color_middle, R.string.spectrum_color_high)[index]
        ColorSwatchDialog.show(this, getString(label), color) { selected, _ ->
            changeSpectrumColors(spectrum.palette) {
                when (index) {
                    0 -> it.copy(low = selected)
                    1 -> it.copy(middle = selected)
                    else -> it.copy(high = selected)
                }
            }
        }
    }

    private fun blockStyleLabel(style: BlockStyle) = when (style) {
        BlockStyle.SOLID -> R.string.block_solid
        BlockStyle.DASHED -> R.string.block_dashed
        BlockStyle.ARC -> R.string.block_arc
        BlockStyle.CHARACTERS -> R.string.block_characters
        BlockStyle.DASHED_ARC -> R.string.block_dashed_arc
    }

    private fun accentDialog() {
        val swatches = AccentTheme.swatches(this)
        val options = model.app.displaySettings.options.value
        val selected = options.customAccentColor ?: swatches.getValue(options.themeAccent)
        ColorSwatchDialog.show(this, getString(R.string.theme_accent), selected, swatches.values.toList(),
            customInitial = options.customAccentColor != null,
            reset = { model.app.displaySettings.update {
                it.copy(themeAccent = ThemeAccent.DEFAULT, customAccentColor = null)
            } }) { color, custom ->
            val accent = if (custom) null else swatches.entries.firstOrNull { it.value == color }?.key
            model.app.displaySettings.update {
                it.copy(themeAccent = accent ?: ThemeAccent.DEFAULT, customAccentColor = color.takeIf { accent == null })
            }
        }
    }

    private fun lyricColorDialog(active: Boolean) {
        val appearance = model.app.displaySettings.options.value.lyricAppearance
        val color = (if (active) appearance.activeColor else appearance.otherColor) ?: AccentTheme.color(binding.root,
            if (active) com.google.android.material.R.attr.colorPrimary else com.google.android.material.R.attr.colorOnSurface)
        fun save(value: Int?) = model.app.displaySettings.update {
            it.copy(lyricAppearance = if (active) it.lyricAppearance.copy(activeColor = value)
                else it.lyricAppearance.copy(otherColor = value))
        }
        ColorSwatchDialog.show(this, getString(if (active) R.string.lyric_active_color else R.string.lyric_other_color),
            color and 0xffffff, reset = { save(null) }) { selected, _ -> save(selected) }
    }

    private fun colorButton(button: MaterialButton, label: Int, color: Int) {
        button.setText(label)
        button.icon = GradientDrawable().apply {
            setColor(color or 0xff000000.toInt())
            setSize(dp(24), dp(24))
            cornerRadius = dp(3).toFloat()
            setStroke(dp(1), MaterialColors.getColor(button, com.google.android.material.R.attr.colorOutline))
        }
        button.iconTint = null
    }

    private fun blockCharacterDialog() {
        val field = TextInputEditText(this).apply {
            id = R.id.blockCharacterInput
            setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(2))
            setText(model.app.displaySettings.options.value.blockOutline.character)
        }
        val input = TextInputLayout(this).apply {
            hint = getString(R.string.block_character)
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(field)
        }
        val dialog = AccentTheme.dialog(this).setTitle(R.string.block_character)
            .setView(input).setPositiveButton(R.string.apply, null).setNegativeButton(R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = field.text?.toString().orEmpty()
                if (!BlockOutline.isValidCharacter(value)) input.error = getString(R.string.block_character_invalid)
                else {
                    model.app.displaySettings.update { it.copy(blockOutline = it.blockOutline.copy(character = value)) }
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun changeSpectrumColors(palette: SpectrumPalette, edit: (SpectrumColors) -> SpectrumColors) {
        model.app.displaySettings.update { display ->
            val spectrum = display.spectrum
            display.copy(spectrum = when (palette) {
                SpectrumPalette.CLASSIC -> spectrum.copy(classicColors = edit(spectrum.classicColors))
                SpectrumPalette.FIRE -> spectrum.copy(fireColors = edit(spectrum.fireColors))
                SpectrumPalette.THEME -> spectrum
            })
        }
    }

    private fun renderSettings() {
        if (!::settings.isInitialized || visibleScreen !== settings.root) return
        val options = model.app.playbackSettings.options.value
        val language = when (AppCompatDelegate.getApplicationLocales()[0]?.language) {
            "en" -> R.string.language_english
            "zh" -> R.string.language_chinese
            else -> R.string.language_system
        }
        val theme = when (model.app.themeMode) {
            DisplaySettings.CUSTOM_THEME -> R.string.theme_custom
            AppCompatDelegate.MODE_NIGHT_NO -> R.string.theme_day
            AppCompatDelegate.MODE_NIGHT_YES -> R.string.theme_night
            else -> R.string.theme_system
        }
        settings.settingsLanguage.text = getString(R.string.setting_value, getString(R.string.language), getString(language))
        settings.settingsTheme.text = getString(R.string.setting_value, getString(R.string.theme), getString(theme))
        val display = model.app.displaySettings.options.value
        colorButton(settings.settingsAccent, R.string.theme_accent,
            AccentTheme.color(binding.root, com.google.android.material.R.attr.colorPrimary))
        settings.settingsControlBlocks.isChecked = display.controlBlocks
        settings.blockStyleControls.isVisible = display.controlBlocks
        settings.settingsBlockStyle.setSelection(display.blockOutline.style.ordinal, false)
        settings.settingsBlockCharacter.isVisible = display.blockOutline.style == BlockStyle.CHARACTERS
        settings.settingsBlockCharacter.text = getString(R.string.setting_value,
            getString(R.string.block_character), display.blockOutline.character)
        settings.blockArcControls.isVisible = display.blockOutline.hasArc
        if (!settings.blockArc.isPressed) {
            settings.blockArc.progress = display.blockOutline.arcDp
            settings.blockArcValue.text = getString(R.string.block_arc_value, display.blockOutline.arcDp)
        }
        settings.settingsBlockShadow.isChecked = display.blockOutline.shadow
        settings.settingsLyrics.isChecked = display.lyricsEnabled
        settings.settingsOnlineLyrics.isChecked = display.onlineLyrics
        settings.settingsOnlineLyrics.isEnabled = display.lyricsEnabled
        settings.settingsLyricDragSeek.isChecked = display.lyricsDragSeek
        settings.settingsLyricDragSeek.isEnabled = display.lyricsEnabled
        settings.settingsLyricSmoothScroll.isChecked = display.lyricsSmoothScroll
        settings.settingsLyricSmoothScroll.isEnabled = display.lyricsEnabled
        settings.lyricsLandscapePosition.setSelection(display.landscapeLyrics.ordinal, false)
        settings.lyricsPortraitPosition.setSelection(display.portraitLyrics.ordinal, false)
        settings.lyricActiveSize.isEnabled = display.lyricsEnabled
        settings.lyricOtherSize.isEnabled = display.lyricsEnabled
        if (!settings.lyricActiveSize.isPressed) {
            settings.lyricActiveSize.progress = display.lyricAppearance.activeSizeSp - 12
            settings.lyricActiveSizeValue.text = getString(R.string.font_size_value,
                getString(R.string.lyric_active_size), display.lyricAppearance.activeSizeSp)
        }
        if (!settings.lyricOtherSize.isPressed) {
            settings.lyricOtherSize.progress = display.lyricAppearance.otherSizeSp - 12
            settings.lyricOtherSizeValue.text = getString(R.string.font_size_value,
                getString(R.string.lyric_other_size), display.lyricAppearance.otherSizeSp)
        }
        colorButton(settings.lyricActiveColor, R.string.lyric_active_color, display.lyricAppearance.activeColor ?:
            AccentTheme.color(binding.root, com.google.android.material.R.attr.colorPrimary))
        colorButton(settings.lyricOtherColor, R.string.lyric_other_color, display.lyricAppearance.otherColor ?:
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface))
        val providers = OnlineLyricsClient.PROVIDERS.mapIndexed { index, provider ->
            getString(R.string.lyrics_provider_step, index + 1, getString(
                if (provider == LyricOrigin.KUGOU) R.string.provider_kugou else R.string.provider_netease,
            ))
        }.joinToString("\n")
        settings.lyricsProviderOrder.text = getString(R.string.lyrics_provider_order, providers,
            getString(if (display.lyricsEnabled && display.onlineLyrics) R.string.lyrics_online_enabled else R.string.lyrics_online_disabled))
        settings.settingsBackground.isVisible = display.customTheme
        settings.settingsUseCustomTheme.isVisible = !display.customTheme
        settings.settingsBackgroundImage.isEnabled = !model.importingBackground.value
        settings.settingsBackgroundImage.setText(
            if (display.imageVersion == 0L) R.string.background_choose else R.string.background_change,
        )
        settings.backgroundSummary.text = when {
            model.importingBackground.value -> getString(R.string.background_importing)
            display.imageVersion == 0L -> getString(R.string.background_missing)
            else -> getString(R.string.background_ready, display.backgroundImages.size)
        }
        settings.settingsSlideshow.isVisible = display.backgroundImages.size > 1
        if (!settings.backgroundInterval.isPressed) {
            settings.backgroundInterval.progress = display.slideshow.intervalSeconds - 1
            settings.backgroundIntervalValue.text = resources.getQuantityString(R.plurals.background_interval_value,
                display.slideshow.intervalSeconds, display.slideshow.intervalSeconds)
        }
        settings.backgroundDuration.max = minOf(100, display.slideshow.intervalSeconds * 10)
        if (!settings.backgroundDuration.isPressed) {
            settings.backgroundDuration.progress = display.slideshow.durationMs / 100
            settings.backgroundDurationValue.text = getString(R.string.background_duration_value,
                (display.slideshow.durationMs / 1_000.0).toString())
        }
        settings.backgroundDuration.isEnabled = display.slideshow.transition != TransitionStyle.NONE
        settings.backgroundTransition.setSelection(display.slideshow.transition.ordinal, false)
        settings.settingsTransition.setSelection(display.settingsTransition.ordinal, false)
        settings.settingsTimeMode.setSelection(display.timeMode.ordinal, false)
        settings.settingsMainTimeMode.setSelection(display.mainTimeMode.ordinal, false)
        settings.settingsNavigationPosition.setSelection(display.navigationPosition.ordinal, false)
        settings.settingsSpectrum.isChecked = display.spectrum.enabled
        settings.spectrumSettings.apply {
            root.isVisible = display.spectrum.enabled
            spectrumPosition.setSelection(display.spectrum.position.ordinal, false)
            spectrumStyle.setSelection(display.spectrum.style.ordinal, false)
            spectrumPalette.setSelection(display.spectrum.palette.ordinal, false)
            spectrumFireStyle.setSelection(display.spectrum.rendering.ordinal, false)
            fireStyleControls.isVisible = display.spectrum.style == SpectrumStyle.BARS
            spectrumColorControls.isVisible = display.spectrum.palette != SpectrumPalette.THEME
            val colors = if (display.spectrum.palette == SpectrumPalette.FIRE) display.spectrum.fireColors else display.spectrum.classicColors
            val labels = listOf(R.string.spectrum_color_low, R.string.spectrum_color_middle, R.string.spectrum_color_high)
            val values = listOf(colors.low, colors.middle, colors.high)
            listOf(spectrumColorLow, spectrumColorMiddle, spectrumColorHigh).forEachIndexed { index, button ->
                colorButton(button, labels[index], values[index])
            }
            spectrumBands.setSelection(if (display.spectrum.bands == 16) 0 else 1, false)
            spectrumPeaks.isChecked = display.spectrum.peaks
            spectrumFalloff.setSelection(display.spectrum.falloff.ordinal, false)
            spectrumPeakFalloff.setSelection(display.spectrum.peakFalloff.ordinal, false)
            spectrumPeakFalloff.isEnabled = display.spectrum.peaks
            spectrumRefreshRate.setSelection(listOf(15, 30, 60).indexOf(display.spectrum.refreshRate), false)
        }
        if (!settings.backgroundBlur.isPressed) {
            settings.backgroundBlur.progress = display.blur
            settings.backgroundBlurValue.text = getString(R.string.background_blur_value, display.blur)
        }
        settings.settingsBackgroundTint.text = getString(R.string.setting_value,
            getString(R.string.background_tint), getString(tintLabel(display.tint)))
        if (!settings.backgroundTintStrength.isPressed) {
            settings.backgroundTintStrength.progress = display.tintStrength
            settings.backgroundTintValue.text = getString(R.string.background_tint_value, display.tintStrength)
        }
        settings.backgroundTintStrength.isEnabled = display.tint != BackgroundTint.ORIGINAL
        settings.settingsLoop.text = getString(R.string.setting_value, getString(R.string.loop_mode), getString(loopLabel(options.loop)))
        settings.settingsOrder.text = getString(R.string.setting_value, getString(R.string.play_order), getString(orderLabel(options.order)))
        val sources = model.library.value.sources
        val selected = PlaybackScope.selectedSources(model.library.value)
        settings.settingsFolders.text = getString(R.string.setting_value, getString(R.string.select_folders),
            getString(R.string.selected_folder_count, selected.size, sources.size))
        settings.settingsFolderSummary.text = if (selected.isEmpty()) getString(R.string.no_folders_selected)
            else selected.joinToString(" · ", transform = ::sourceName)
        settings.settingsFolders.isEnabled = model.repository.isReadable && model.scan.value == null
        settings.settingsPinFolders.isEnabled = model.repository.isReadable && sources.isNotEmpty()
        settings.settingsAddFolder.isEnabled = model.repository.isReadable && model.scan.value == null
        settings.settingsDeviceMusic.isEnabled = model.repository.isReadable && model.scan.value == null
        settings.settingsPlayFolders.isEnabled = selected.isNotEmpty() && model.scan.value == null
    }

    private fun showSettingsCategory(category: SettingsCategory) {
        if (!::settings.isInitialized) return
        if (shownSettingsCategory == category) return
        shownSettingsCategory?.let {
            if (!restoringSettingsScroll) model.settingsScroll(it, settings.settingsScroll.scrollY)
        }
        shownSettingsCategory = category
        restoringSettingsScroll = true
        SettingsCategory.pages.forEach {
            settings.root.findViewById<View>(it.panelId).isVisible = it == category
            settings.root.findViewById<MaterialButton>(it.buttonId).isChecked = it == category
        }
        settings.settingsHeading.setText(category.label)
        if (category == SettingsCategory.ABOUT) AboutContent.populate(settings.settingsAboutPanel, supportFragmentManager)
        AccentTheme.tintControls(settings.root.findViewById(category.panelId))
        ViewCompat.setAccessibilityPaneTitle(settings.settingsDetails, getString(category.label))
        settings.settingsScroll.scrollTo(0, 0)
        settings.settingsScroll.doOnNextLayout {
            if (shownSettingsCategory == category) {
                settings.settingsScroll.scrollTo(0, model.settingsScroll(category))
                restoringSettingsScroll = false
            }
        }
        settings.settingsScroll.requestLayout()
    }

    private fun showScreen(id: Int) {
        val target = screenView(id)
        val changed = visibleScreen !== target
        if (changed) {
            val previous = visibleScreen
            screenAnimator?.cancel()
            for (index in 0 until binding.screenContainer.childCount) {
                val child = binding.screenContainer.getChildAt(index)
                ViewTransitions.reset(child)
                child.isVisible = child === target
            }
            visibleScreen = target
            if (target !== now.root) lyricScrolling.stop()
            val transition = model.app.displaySettings.options.value.settingsTransition
            if (previous != null && ::settings.isInitialized &&
                (target === settings.root || previous === settings.root) &&
                transition != TransitionStyle.NONE && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) {
                screenAnimator = ViewTransitions.entrance(target, transition, 300).also { it.start() }
            }
            when (id) {
                R.id.nav_library -> renderLibrary()
                R.id.nav_folders -> renderFolders()
                R.id.nav_queue -> { queueScrollPending = true; updateQueue(force = true) }
                R.id.nav_playlists -> renderPlaylists()
                R.id.action_settings -> {
                    renderSettings()
                    showSettingsCategory(model.settingsCategory.value)
                }
            }
        }
        val inSettings = id == R.id.action_settings
        val singleRow = inSettings || compactChrome
        if (binding.miniPlayer.singleRow != singleRow) {
            binding.miniPlayer.singleRow = singleRow
            appliedControlBlocks = null
            val display = model.app.displaySettings.options.value
            renderControlBlocks(display.controlBlocks, display.blockOutline)
        }
        binding.navigation.isVisible = true
        if (!inSettings && binding.navigation.selectedItemId != id) binding.navigation.selectedItemId = id
        binding.toolbar.setTitle(if (inSettings) R.string.settings else R.string.app_name)
        if (target is ListPanel) updateCompactHeading(target)
        binding.toolbar.menu.findItem(R.id.action_settings).isVisible = !inSettings
        if (inSettings) {
            binding.toolbar.setNavigationIcon(R.drawable.ic_back)
            binding.toolbar.setNavigationIconTint(MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface))
            binding.toolbar.setNavigationContentDescription(R.string.back)
        } else binding.toolbar.navigationIcon = null
        binding.miniPlayer.isVisible = target !== now.root
        updatePlayer()
        if (changed && target === now.root) {
            now.cover.lyricsList.post { updateLyricProgress(force = true) }
        }
    }

    private fun configureNavigation(position: NavigationPosition) {
        if (appliedNavigationPosition == position) return
        appliedNavigationPosition = position
        val side = position == NavigationPosition.LEFT || position == NavigationPosition.RIGHT
        val gap = resources.getDimensionPixelSize(R.dimen.player_gap)
        binding.navigation.configure(side, compactChrome)
        binding.navigation.layoutParams = RelativeLayout.LayoutParams(
            if (side) resources.getDimensionPixelSize(R.dimen.navigation_side_width) else RelativeLayout.LayoutParams.MATCH_PARENT,
            if (side) RelativeLayout.LayoutParams.MATCH_PARENT else dp(if (compactChrome) 48 else 80),
        ).apply {
            addRule(when (position) {
                NavigationPosition.BOTTOM -> RelativeLayout.ALIGN_PARENT_BOTTOM
                NavigationPosition.TOP -> RelativeLayout.ALIGN_PARENT_TOP
                NavigationPosition.LEFT -> RelativeLayout.ALIGN_PARENT_LEFT
                NavigationPosition.RIGHT -> RelativeLayout.ALIGN_PARENT_RIGHT
            })
        }
        binding.playerBody.layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT,
        ).apply {
            addRule(when (position) {
                NavigationPosition.BOTTOM -> RelativeLayout.ABOVE
                NavigationPosition.TOP -> RelativeLayout.BELOW
                NavigationPosition.LEFT -> RelativeLayout.RIGHT_OF
                NavigationPosition.RIGHT -> RelativeLayout.LEFT_OF
            }, R.id.navigation)
            when (position) {
                NavigationPosition.BOTTOM -> bottomMargin = gap
                NavigationPosition.TOP -> topMargin = gap
                NavigationPosition.LEFT -> leftMargin = gap
                NavigationPosition.RIGHT -> rightMargin = gap
            }
        }
        binding.navigation.isVisible = true
    }

    private fun configureCompactChrome() {
        if (!compactChrome) return
        binding.noticeBar.minimumHeight = dp(48)
        binding.dismissNotice.layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        binding.noticeText.maxLines = 1
        binding.noticeText.ellipsize = android.text.TextUtils.TruncateAt.END
        binding.miniPlayer.singleRow = true
    }

    private fun updateCompactHeading(panel: ListPanel) {
        if (compactChrome && visibleScreen === panel) binding.toolbar.title = panel.heading.text
    }

    private fun navigationLabel(position: NavigationPosition) = when (position) {
        NavigationPosition.BOTTOM -> R.string.navigation_bottom
        NavigationPosition.LEFT -> R.string.navigation_left
        NavigationPosition.RIGHT -> R.string.navigation_right
        NavigationPosition.TOP -> R.string.navigation_top
    }

    private fun updateSpectrum() {
        val options = model.app.displaySettings.options.value.spectrum
        val view = now.controls.spectrum
        val row = now.controls.metadataRow
        val index = if (options.position == SpectrumPosition.LEFT) 0 else row.childCount - 1
        if (row.indexOfChild(view) != index) {
            row.removeView(view)
            row.addView(view, index)
        }
        (view.layoutParams as LinearLayout.LayoutParams).apply {
            val gap = resources.getDimensionPixelSize(R.dimen.player_gap)
            marginStart = if (options.position == SpectrumPosition.RIGHT) gap else 0
            marginEnd = if (options.position == SpectrumPosition.LEFT) gap else 0
        }
        view.isVisible = options.enabled
        view.configure(options)
        val mode = if (options.style == SpectrumStyle.LINE) getString(R.string.spectrum_line)
            else getString(R.string.spectrum_mode, getString(when (options.palette) {
                SpectrumPalette.CLASSIC -> R.string.spectrum_classic
                SpectrumPalette.FIRE -> R.string.spectrum_fire
                SpectrumPalette.THEME -> R.string.spectrum_theme
            }), getString(if (options.rendering == FireStyle.LED) R.string.spectrum_led else R.string.spectrum_smooth))
        view.contentDescription = getString(R.string.spectrum_tap_cycle, mode)
        val visible = options.enabled && visibleScreen === now.root &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (optionalContentStarted) model.app.spectrum.setVisible(this, visible)
        view.setPlaybackActive(optionalContentStarted && visible && controller?.isPlaying == true)
    }

    private fun renderLibrary() {
        if (!::libraryPanel.isInitialized || visibleScreen !== libraryPanel) return
        favoriteFilter.setText(if (model.favoritesOnly.value) R.string.all_tracks else R.string.favorites_only)
        val tracks = model.visibleTracks.value
        val selecting = model.librarySelecting.value
        val selected = model.librarySelection.value.toSet()
        libraryActions.isVisible = !selecting
        librarySelectionActions.isVisible = selecting
        libraryAddSelected.isEnabled = selected.isNotEmpty()
        librarySelectAll.isEnabled = tracks.isNotEmpty()
        librarySelectAll.setText(if (tracks.isNotEmpty() && tracks.all { it.id in selected })
            R.string.deselect_all_shown else R.string.select_all_shown)
        libraryAdapter.submitList(tracks.map { trackRow(it).copy(selected = if (selecting) it.id in selected else null) })
        libraryPanel.heading.text = if (selecting) getString(R.string.selected_songs, selected.size)
            else resources.getQuantityString(R.plurals.track_count, tracks.size, tracks.size)
        libraryPlay.isEnabled = tracks.isNotEmpty()
        val message = when {
            !model.repository.loaded.value -> R.string.loading
            model.library.value.tracks.isEmpty() -> R.string.library_empty
            else -> R.string.search_empty
        }
        libraryPanel.empty(getString(message), tracks.isEmpty())
        updateCompactHeading(libraryPanel)
    }

    private fun renderFolders() {
        if (!::folderPanel.isInitialized || visibleScreen !== folderPanel) return
        val state = model.browser.value
        val scan = model.scan.value
        folderUp.isVisible = state.path.isNotEmpty()
        folderPlay.setText(if (state.path.isEmpty()) R.string.play_selected_folders else R.string.play_folder)
        folderPlay.isEnabled = scan == null && !state.loading &&
            (state.path.isNotEmpty() || PlaybackScope.selectedSources(model.library.value).isNotEmpty())
        folderRescan.isEnabled = scan == null && model.library.value.sources.isNotEmpty()
        folderRescan.setText(if (state.path.isEmpty()) R.string.rescan_all else R.string.rescan)
        cancelScan.isVisible = scan != null
        folderPanel.heading.text = when {
            scan != null -> getString(R.string.scan_progress, scan.folderName, scan.found)
            state.path.isEmpty() -> getString(R.string.folders)
            else -> state.path.mapIndexed { index, location ->
                if (index == 0) model.library.value.sources.firstOrNull { it.uri == location.sourceUri }
                    ?.let(::sourceName) ?: location.name else location.name
            }.joinToString("  /  ")
        }
        val rows = if (state.path.isEmpty()) {
            model.library.value.sources.map { source ->
                val status = when {
                    source.unavailable -> getString(R.string.source_unavailable)
                    MediaStoreAccess.folder(Uri.parse(source.uri)) != null -> getString(R.string.access_music)
                    !source.persisted -> getString(R.string.access_temporary)
                    else -> getString(R.string.access_saved)
                }
                MusicRow(
                    source.uri, sourceName(source),
                    listOf(
                        source.lastIssue ?: getString(R.string.saved_folder_detail, source.lastScanCount, status),
                        getString(if (source.selectedForPlayback) R.string.folder_selected else R.string.folder_not_selected),
                        if (source.pinned) getString(R.string.folder_pinned) else "",
                    ).filter(String::isNotEmpty).joinToString("\n"),
                    icon = R.drawable.ic_folder,
                )
            }
        } else state.entries.map {
            MusicRow(it.uri, it.name, if (it.isDirectory) getString(R.string.folder_kind) else it.mimeType,
                icon = if (it.isDirectory) R.drawable.ic_folder else R.drawable.ic_note)
        }
        folderAdapter.submitList(rows)
        val text = when {
            state.loading -> getString(R.string.loading)
            state.error != null -> state.error
            state.path.isEmpty() -> getString(R.string.folders_empty)
            else -> getString(R.string.folder_empty)
        }
        folderPanel.empty(text, rows.isEmpty())
        updateCompactHeading(folderPanel)
    }

    private fun renderPlaylists() {
        if (!::playlistPanel.isInitialized || visibleScreen !== playlistPanel) return
        val id = model.selectedPlaylist.value
        val library = model.library.value
        val playlist = library.playlists.firstOrNull { it.id == id }
        playlistBack.isVisible = id != null
        playlistPlay.isVisible = playlist != null
        playlistAdd.isVisible = playlist != null
        playlistUpdate.isVisible = playlist != null
        playlistTrackRows = if (id == null) emptyList() else LibraryEdits.playlistTracks(library, id)
        playlistPlay.isEnabled = playlistTrackRows.isNotEmpty()
        val rows = if (id == null) library.playlists.map {
            MusicRow(it.id, it.name, resources.getQuantityString(R.plurals.track_count, it.trackIds.size, it.trackIds.size), icon = R.drawable.ic_playlist)
        } else playlistTrackRows.map { trackRow(it) }
        playlistAdapter.submitList(rows)
        playlistPanel.heading.text = playlist?.name ?: getString(R.string.playlists)
        playlistPanel.empty(getString(if (id == null) R.string.playlists_empty else R.string.playlist_empty), rows.isEmpty())
        updateCompactHeading(playlistPanel)
    }

    private fun updateQueue(force: Boolean = false) {
        if (!::queuePanel.isInitialized || visibleScreen !== queuePanel) return
        val player = controller
        val signature = if (player == null) emptyList() else (0 until player.mediaItemCount).map {
            val item = player.getMediaItemAt(it)
            item.mediaId to item.mediaMetadata.title.toString()
        }
        if (force || signature != queueSignature) {
            val known = model.library.value.tracks.associateBy { it.id }
            queueTracks = if (player == null) emptyList() else (0 until player.mediaItemCount).map {
                val item = player.getMediaItemAt(it)
                item.toTrack(known[item.mediaId])
            }
            queueSignature = signature
        }
        queueAdapter.submitList(queueTracks.mapIndexed { index, track ->
            trackRow(track).copy(key = "$index:${track.id}", active = index == player?.currentMediaItemIndex)
        }) {
            val index = controller?.currentMediaItemIndex ?: -1
            if (queueScrollPending && visibleScreen === queuePanel && index in queueAdapter.currentList.indices) {
                (queuePanel.list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(index, 0)
                queueScrollPending = false
            }
        }
        queuePanel.heading.text = resources.getQuantityString(R.plurals.track_count, queueTracks.size, queueTracks.size)
        queuePanel.empty(
            getString(if (player == null) R.string.connecting else R.string.queue_empty),
            queueTracks.isEmpty(),
        )
        updateCompactHeading(queuePanel)
    }

    private fun configurePlaybackControls() {
        now.controls.position.setOnClickListener {
            model.app.displaySettings.update { it.copy(mainTimeMode =
                if (it.mainTimeMode == PlaybackTimeMode.ELAPSED) PlaybackTimeMode.REMAINING else PlaybackTimeMode.ELAPSED) }
        }
        now.controls.play.setOnClickListener { togglePlay() }
        binding.miniPlay.setOnClickListener { togglePlay() }
        now.controls.previous.setOnClickListener { withController { it.seekToPrevious() } }
        now.controls.rewind.setOnClickListener { seekBy(-10_000) }
        now.controls.forward.setOnClickListener { seekBy(10_000) }
        now.controls.next.setOnClickListener { withController { it.seekToNextMediaItem() } }
        binding.miniNext.setOnClickListener { withController { it.seekToNextMediaItem() } }
        now.controls.shuffle.setOnClickListener {
            val options = model.app.playbackSettings.options.value
            model.app.playbackSettings.setOrder(if (options.order == PlayOrder.RANDOM) PlayOrder.SEQUENTIAL else PlayOrder.RANDOM)
        }
        now.controls.repeat.setOnClickListener { loopDialog() }
        now.controls.favorite.setOnClickListener { currentTrack()?.let(model::favorite) }
        now.controls.details.setOnClickListener { currentTrack()?.let(::detailsDialog) }
        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                draggingSeek = seekBar
                draggingTrackId = controller?.currentMediaItem?.mediaId
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val player = controller
                if (draggingSeek === seekBar && draggingTrackId == player?.currentMediaItem?.mediaId) {
                    seekToProgress(seekBar.progress)
                }
                draggingSeek = null
                draggingTrackId = null
                updateProgress()
            }
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    if (draggingSeek == null) seekToProgress(progress)
                    updateProgress()
                }
            }
        }
        now.controls.seek.setOnSeekBarChangeListener(seekListener)
        binding.miniSeek.setOnSeekBarChangeListener(seekListener)
        now.controls.volume.max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        now.controls.volume.isEnabled = !audio.isVolumeFixed
        now.controls.volume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    try {
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                    } catch (error: SecurityException) {
                        model.app.report(getString(R.string.volume_failed))
                    }
                }
            }
        })
        if (audio.isVolumeFixed) now.controls.volume.contentDescription = getString(R.string.volume_fixed)
    }

    private fun togglePlay() = withController { player ->
        if (player.playWhenReady) player.pause()
        else if (player.mediaItemCount > 0) {
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
            if (player.playbackState == Player.STATE_IDLE || player.playerError != null) player.prepare()
            player.play()
        } else model.screen(R.id.nav_folders)
    }

    private fun consumePlayRequest() {
        val player = controller ?: return
        val request = model.playRequest.value ?: return
        val mediaItems = request.tracks.map(Track::toMediaItem)
        when (request.mode) {
            QueueMode.REPLACE -> {
                player.setMediaItems(mediaItems, request.index, 0)
                player.prepare()
                player.play()
            }
            QueueMode.APPEND -> {
                player.addMediaItems(mediaItems)
                model.app.inform(resources.getQuantityString(R.plurals.queue_added, mediaItems.size, mediaItems.size))
            }
            QueueMode.NEXT -> {
                val index = (player.currentMediaItemIndex + 1).coerceIn(0, player.mediaItemCount)
                player.addMediaItems(index, mediaItems)
                model.app.inform(resources.getQuantityString(R.plurals.queue_added, mediaItems.size, mediaItems.size))
            }
        }
        model.consumedRequest(request.token)
        updatePlayer()
        updateQueue(force = true)
    }

    private fun updatePlayer() {
        val player = controller
        val ready = player != null && model.app.playbackReady.value
        val track = currentTrack()
        val title = track?.title?.ifBlank { track.fileName } ?: getString(R.string.no_track)
        val artist = track?.artist?.ifBlank { getString(R.string.unknown_artist) } ?: getString(R.string.no_track_hint)
        now.controls.title.text = title
        now.controls.artist.text = artist
        now.controls.album.text = track?.album?.ifBlank { getString(R.string.unknown_album) }.orEmpty()
        binding.miniTitle.text = if (binding.miniPlayer.singleRow && track != null)
            getString(R.string.mini_track_summary, title, artist) else title
        binding.miniArtist.text = artist
        val wantsPlay = player?.playWhenReady == true
        val icon = if (wantsPlay) R.drawable.ic_pause else R.drawable.ic_play
        val description = getString(if (wantsPlay) R.string.pause else R.string.play)
        listOf(now.controls.play, binding.miniPlay).forEach {
            it.setImageResource(icon)
            it.contentDescription = description
            it.isEnabled = ready
        }
        now.controls.previous.isEnabled = player != null && track != null
        val canNext = player?.hasNextMediaItem() == true
        now.controls.next.isEnabled = canNext
        binding.miniNext.isEnabled = canNext
        val options = model.app.playbackSettings.options.value
        val shuffled = options.order == PlayOrder.RANDOM
        now.controls.shuffle.contentDescription = getString(orderLabel(options.order))
        now.controls.shuffle.imageTintList = tint(shuffled)
        now.controls.repeat.setImageResource(when (options.loop) {
            LoopMode.SINGLE_FILE -> R.drawable.ic_repeat_one
            LoopMode.FOLDER -> R.drawable.ic_folder
            else -> R.drawable.ic_repeat
        })
        now.controls.repeat.imageTintList = tint(options.loop != LoopMode.OFF)
        now.controls.repeat.contentDescription = getString(R.string.loop_mode_description, getString(loopLabel(options.loop)))
        now.controls.playbackOptions.text = getString(R.string.playback_options_summary,
            getString(R.string.loop_mode_description, getString(loopLabel(options.loop))), getString(orderLabel(options.order)))
        val favored = track?.id in model.library.value.favorites
        val favoriteLabel = getString(if (favored) R.string.unfavorite else R.string.favorite)
        now.controls.favorite.contentDescription = favoriteLabel
        now.controls.favorite.text = if (now.root.compactControls) "" else favoriteLabel
        now.controls.favorite.setIconResource(if (favored) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
        now.controls.favorite.isEnabled = track != null && findTrack(track.id) != null
        now.controls.details.isEnabled = track != null
        val status = when {
            !ready -> getString(R.string.connecting)
            player.playbackState == Player.STATE_BUFFERING -> getString(R.string.buffering)
            player.playbackState == Player.STATE_ENDED -> getString(R.string.ended)
            player.isPlaying -> getString(R.string.playing)
            player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE -> getString(R.string.interrupted_audio)
            else -> getString(R.string.paused)
        }
        now.controls.status.text = if (player != null && player.mediaItemCount > 0) {
            "$status  ·  ${getString(R.string.queue_position, player.currentMediaItemIndex + 1, player.mediaItemCount)}"
        } else status
        updateProgress()
        updateSpectrum()
        updateArtwork(track?.uri)
        model.requestLyrics(track)
    }

    private fun updateProgress() {
        val player = controller
        val duration = player?.duration?.takeIf { it > 0 && it != C.TIME_UNSET }
        val fallback = currentTrack()?.durationMs?.takeIf { it > 0 }
        val total = duration ?: fallback
        val seekable = duration != null && player?.isCurrentMediaItemSeekable == true &&
            player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        now.controls.rewind.isEnabled = seekable
        now.controls.forward.isEnabled = seekable
        // Retain an invalidated drag until release so later move events cannot seek the next track.
        val preview = draggingSeek?.takeIf { seekable && draggingTrackId == player?.currentMediaItem?.mediaId }
        val position = if (preview != null && duration != null) {
            duration * preview.progress / 10_000
        } else player?.currentPosition?.coerceAtLeast(0) ?: 0
        now.controls.duration.setTextIfChanged(total?.let(TimeText::format) ?: getString(R.string.empty_time))
        val mainTimeMode = model.app.displaySettings.options.value.mainTimeMode
        now.controls.position.setTextIfChanged(TimeText.position(position, total, mainTimeMode, getString(R.string.empty_time)))
        now.controls.position.contentDescription = getString(R.string.toggle_time_mode,
            now.controls.position.text, getString(if (mainTimeMode == PlaybackTimeMode.ELAPSED) R.string.time_remaining else R.string.time_elapsed))
        for (seek in listOf(now.controls.seek, binding.miniSeek)) {
            seek.isEnabled = seekable
            if (seek !== preview) {
                seek.progress = if (duration != null) ((position * 10_000) / duration).coerceIn(0, 10_000).toInt() else 0
            }
        }
        binding.miniTime.setTextIfChanged(TimeText.progress(position, total, model.app.displaySettings.options.value.timeMode,
            getString(R.string.empty_time)))
        updateLyricProgress()
    }

    private fun seekToProgress(progress: Int) {
        val player = controller ?: return
        val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: return
        if (player.isCurrentMediaItemSeekable && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            player.seekTo(duration * progress.coerceIn(0, 10_000) / 10_000)
        }
    }

    private fun seekBy(deltaMs: Long) = withController { player ->
        val duration = player.duration
        if (duration > 0 && duration != C.TIME_UNSET && player.isCurrentMediaItemSeekable &&
            player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        ) {
            player.seekTo((player.currentPosition + deltaMs).coerceIn(0, duration - 1))
            updateProgress()
        }
    }

    private fun updateVolume() {
        now.controls.volume.progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        now.controls.volumeValue.setTextIfChanged(getString(R.string.volume_level, now.controls.volume.progress))
    }

    private fun TextView.setTextIfChanged(value: String) {
        if (!value.contentEquals(text)) text = value
    }

    private fun updateArtwork(uri: String?) {
        if (!optionalContentStarted) return
        if (artworkUri == uri) return
        artworkUri = uri
        artworkWarning = null
        artworkJob?.cancel()
        now.cover.artwork.setImageResource(R.drawable.ic_launcher)
        binding.miniArtwork.setImageResource(R.drawable.ic_launcher)
        binding.navigation.artwork.setImageResource(R.drawable.ic_launcher)
        if (uri.isNullOrBlank()) return
        artworkJob = lifecycleScope.launch {
            val cover = model.app.artwork.load(uri)
            if (artworkUri == uri) {
                artworkWarning = cover.warning
                cover.bitmap?.let {
                    now.cover.artwork.setImageBitmap(it)
                    binding.miniArtwork.setImageBitmap(it)
                    binding.navigation.artwork.setImageBitmap(it)
                }
            }
        }
    }

    private fun renderLyrics(state: LyricState) {
        val enabled = model.app.displaySettings.options.value.lyricsEnabled &&
            state.trackId != null && state.trackId == currentTrack()?.id
        val result = state.result
        val lyrics = result?.lyrics.takeIf { enabled }
        val changed = lyrics != lyricsAdapter.lyrics || state.trackId != renderedLyricTrackId
        renderedLyricTrackId = state.trackId
        val display = model.app.displaySettings.options.value
        val resized = lyricsAdapter.configure(display.lyricAppearance)
        now.root.configureLyrics(display.landscapeLyrics, display.portraitLyrics)
        lyricScrolling.configure(display.lyricsDragSeek, display.lyricsSmoothScroll)
        lyricScrolling.submit(lyrics, state.trackId)
        now.cover.artwork.isVisible = lyrics == null
        now.cover.lyricsList.isVisible = lyrics != null
        now.cover.lyricsStatus.isVisible = enabled
        now.cover.retryLyrics.isVisible = enabled && !state.loading && lyrics == null
        now.cover.lyricsStatus.text = when {
            !enabled -> ""
            state.loading -> getString(R.string.lyrics_loading)
            lyrics != null -> {
                val source = when (result?.origin) {
                    LyricOrigin.LOCAL_FILE -> R.string.lyrics_local
                    LyricOrigin.EMBEDDED -> R.string.lyrics_embedded
                    LyricOrigin.KUGOU -> R.string.lyrics_kugou
                    LyricOrigin.NETEASE -> R.string.lyrics_netease
                    null -> R.string.lyrics
                }
                val label = if (result?.cached == true) getString(R.string.lyrics_cached, getString(source)) else getString(source)
                if (result?.issue == LyricIssue.LOCAL_UNREADABLE) "$label\n${getString(R.string.lyrics_local_failed)}" else label
            }
            result?.issue == LyricIssue.LOCAL_UNREADABLE -> getString(R.string.lyrics_local_failed)
            result?.issue == LyricIssue.LOOKUP_FAILED -> getString(R.string.lyrics_lookup_failed)
            result?.issue == LyricIssue.FOLDER_ACCESS_NEEDED -> getString(R.string.lyrics_folder_needed)
            else -> getString(R.string.lyrics_not_found)
        }
        if (changed || resized) {
            now.cover.lyricsList.post { updateLyricProgress(force = true) }
        }
    }

    private fun updateLyricProgress(force: Boolean = false) {
        val player = controller
        lyricScrolling.progress(player?.currentPosition?.coerceAtLeast(0) ?: 0,
            player?.isCurrentMediaItemSeekable == true && player.duration > 0 &&
                player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) &&
                player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE), force)
    }

    @Suppress("DEPRECATION")
    private fun configureSystemBars(custom: Boolean) {
        customSystemBars = custom
        if (custom) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            )
            if (Build.VERSION.SDK_INT >= 29) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        } else enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 35) {
            // Android 15 can restore light-bar appearance without the corresponding layout flags.
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    private fun renderBackground(options: DisplayOptions) {
        renderControlBlocks(options.controlBlocks, options.blockOutline)
        val custom = options.customTheme && options.imageVersion > 0
        if (customSystemBars != custom) {
            configureSystemBars(custom)
        }
        val surface = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurface)
        binding.backgroundTint.isVisible = custom
        binding.backgroundTint.setBackgroundColor(BackgroundEffects.tintColor(options.tint, options.tintStrength))
        binding.toolbar.setBackgroundColor(Color.TRANSPARENT)
        binding.navigation.setBackgroundColor(if (custom) Color.TRANSPARENT else surface)
        binding.miniPlayer.setBackgroundColor(if (custom) Color.TRANSPARENT
            else MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurfaceVariant))
        for (view in binding.screenContainer.children) {
            view.setBackgroundColor(Color.TRANSPARENT)
        }
        for (view in listOf(now.controls.root, now.cover.root)) {
            view.background = null
        }
        barElevations.forEach { (view, elevation) -> view.elevation = if (custom) 0f else elevation }
        val indicator = navigationIndicator
        binding.navigation.isItemActiveIndicatorEnabled = !custom && indicator
        binding.noticeBar.setBackgroundColor(Color.TRANSPARENT)
        renderNotice(shownNotice)
    }

    private fun renderControlBlocks(enabled: Boolean, outline: BlockOutline) {
        if (appliedControlBlocks == (enabled to outline)) return
        val backgrounds = originalControlBackgrounds
        val padding = originalControlPadding
        val shapes = originalActionShapes
        val strokes = originalActionStrokes
        val color = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOutline)
        val ripple = ColorStateList.valueOf(MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorControlHighlight))
        fun border() = PlayerBlockDrawable(outline, color, resources.displayMetrics.density)
        controlBlockContainers.forEach {
            it.background = if (enabled) border() else backgrounds[it]
        }
        val inset = if (!enabled) 0 else dp(if (outline.style == BlockStyle.CHARACTERS) 12 else 8)
        padding.forEach { (view, original) ->
            if (view === binding.miniPlayer && binding.miniPlayer.singleRow) return@forEach
            val vertical = if (view === now.controls.trackInformation || view === now.controls.spectrum ||
                view === now.cover.root || view === binding.miniPlayer) inset else 0
            view.setPaddingRelative(maxOf(original.left, inset), maxOf(original.top, vertical),
                maxOf(original.right, inset), maxOf(original.bottom, vertical))
        }
        controlBlockButtons.forEach {
            it.background = if (enabled) RippleDrawable(ripple, border(), ColorDrawable(Color.WHITE)) else backgrounds[it]
        }
        controlBlockOverlays.forEach {
            it.foreground = if (enabled && !(it === binding.miniPlayer && binding.miniPlayer.singleRow)) border() else null
        }
        binding.navigation.configureBlocks(enabled, outline)
        renderCategoryDividers(outline)
        shapes.forEach { (button, shape) ->
            button.shapeAppearanceModel = if (enabled) shape.toBuilder().setAllCornerSizes(dp(6).toFloat()).build() else shape
            button.strokeWidth = if (enabled) 0 else requireNotNull(strokes[button])
        }
        appliedControlBlocks = enabled to outline
    }

    private fun renderCategoryDividers(outline: BlockOutline) {
        if (!::settings.isInitialized) return
        val color = MaterialColors.getColor(settings.root, com.google.android.material.R.attr.colorOutline)
        settings.settingsCategories.children.filter { it.id == R.id.settingsCategoryDivider }.forEach {
            it.background = PlayerBlockDrawable(outline, color, resources.displayMetrics.density, divider = true)
        }
    }

    private fun backgroundSlider(label: (Int) -> Unit, save: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            label(progress)
            if (fromUser && !seekBar.isPressed) save(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar) { save(seekBar.progress) }
    }

    private fun launchBackgroundPicker() {
        try {
            if (!DocumentPicker.launch("image/*", multiple = true) { intent ->
                when {
                    intent.action == Intent.ACTION_OPEN_DOCUMENT -> pickBackground
                    intent.hasCategory(Intent.CATEGORY_OPENABLE) -> compatiblePictures[0]
                    else -> compatiblePictures[1]
                }.launch(intent)
            }) {
                model.app.report(getString(R.string.picture_picker_missing))
            }
        } catch (error: SecurityException) {
            Log.w("MainActivity", "Android denied picture selection", error)
            model.app.report(getString(R.string.picker_denied))
        }
    }

    private fun currentTrack(): Track? {
        val item = controller?.currentMediaItem ?: return null
        return item.toTrack(findTrack(item.mediaId))
    }

    private fun findTrack(id: String) = model.library.value.tracks.firstOrNull { it.id == id }

    private fun trackRow(track: Track): MusicRow = MusicRow(
        key = track.id,
        title = track.title,
        subtitle = if (track.missing) getString(R.string.missing_reference)
        else listOf(track.artist.ifBlank { getString(R.string.unknown_artist) }, track.album)
            .filter(String::isNotBlank).joinToString(" · "),
        detail = if (track.durationMs > 0) TimeText.format(track.durationMs) else "",
        icon = if (track.id in model.library.value.favorites) R.drawable.ic_favorite else R.drawable.ic_note,
    )

    private fun withController(action: (MediaController) -> Unit) {
        val player = controller
        if (player == null) {
            model.app.inform(getString(R.string.connecting))
            connectController()
        } else action(player)
    }

    private fun launchFolderPicker() {
        try {
            pickFolder.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            })
        } catch (error: ActivityNotFoundException) {
            Log.i("MainActivity", "Android folder picker unavailable; offering indexed music access", error)
            AccentTheme.dialog(this).setTitle(R.string.folder_picker_missing)
                .setMessage(R.string.folder_picker_compatibility)
                .setPositiveButton(R.string.device_music) { _, _ -> launchDeviceMusic() }
                .setNeutralButton(R.string.open_file) { _, _ -> launchFilePicker() }
                .setNegativeButton(R.string.cancel, null).show()
        } catch (error: SecurityException) {
            Log.w("MainActivity", "Android denied folder selection", error)
            model.app.report(getString(R.string.picker_denied))
        }
    }

    private fun launchFilePicker() {
        try {
            // Some USB providers mislabel audio as application/octet-stream; validate after selection.
            if (!DocumentPicker.launch("*/*") { intent ->
                when {
                    intent.action == Intent.ACTION_OPEN_DOCUMENT -> pickFile
                    intent.hasCategory(Intent.CATEGORY_OPENABLE) -> compatibleFiles[0]
                    else -> compatibleFiles[1]
                }.launch(intent)
            }) {
                model.app.report(getString(R.string.picker_missing))
                launchDeviceMusic()
            }
        } catch (error: SecurityException) {
            Log.w("MainActivity", "Android denied file selection", error)
            model.app.report(getString(R.string.picker_denied))
        }
    }

    private fun launchDeviceMusic() {
        if (model.repository.documents.deviceMusic.hasPermission()) {
            model.loadDeviceMusicFolders()
            return
        }
        val permission = MediaStoreAccess.requiredPermission()
        val preferences = getSharedPreferences("settings", MODE_PRIVATE)
        val settingsRequired = preferences.getBoolean("musicPermissionBlocked.$permission", false) &&
            !shouldShowRequestPermissionRationale(permission)
        AccentTheme.dialog(this).setTitle(R.string.device_music)
            .setMessage(if (settingsRequired) R.string.music_permission_settings else R.string.music_permission_explanation)
            .setPositiveButton(if (settingsRequired) R.string.open_app_settings else R.string.allow_music_access) { _, _ ->
                try {
                    if (settingsRequired) {
                        musicPermissionSettings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null)))
                    } else {
                        requestMusicPermission.launch(permission)
                    }
                } catch (error: ActivityNotFoundException) {
                    Log.w("MainActivity", "Android music-permission UI is unavailable", error)
                    model.app.report(getString(R.string.permission_ui_missing))
                } catch (error: SecurityException) {
                    Log.w("MainActivity", "Android denied music-permission setup", error)
                    model.app.report(getString(R.string.music_permission_denied))
                }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun renderDeviceMusicPicker(choice: DeviceMusicPicker?) {
        if (choice == null) {
            deviceMusicDialog?.dismiss()
            deviceMusicDialog = null
            renderedDeviceMusic = null
            return
        }
        val rendered = renderedDeviceMusic
        if (deviceMusicDialog?.isShowing == true && rendered?.token == choice.token &&
            rendered.loading == choice.loading
        ) {
            deviceMusicDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = choice.selected.isNotEmpty()
            return
        }
        deviceMusicDialog?.dismiss()
        val builder = AccentTheme.dialog(this)
            .setTitle(if (choice.loading) R.string.device_music else R.string.device_music_choose_folders)
            .setNegativeButton(R.string.cancel) { _, _ -> model.dismissDeviceMusic() }
            .setOnCancelListener { model.dismissDeviceMusic() }
        if (choice.loading) builder.setMessage(R.string.device_music_loading)
        else {
            builder.setMultiChoiceItems(choice.folders.map(::sourceName).toTypedArray(),
                BooleanArray(choice.folders.size) { choice.folders[it].uri in choice.selected }) { _, index, checked ->
                model.selectDeviceMusicFolder(choice.folders[index].uri, checked)
            }.setPositiveButton(R.string.add_folder) { _, _ -> model.addDeviceMusicFolders() }
        }
        renderedDeviceMusic = choice
        deviceMusicDialog = builder.show().also {
            if (!choice.loading) it.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = choice.selected.isNotEmpty()
        }
    }

    private fun currentSource(): FolderSource? = model.browser.value.path.lastOrNull()?.sourceUri?.let { uri ->
        model.library.value.sources.firstOrNull { it.uri == uri }
    }

    private fun sourceName(source: FolderSource): String = model.repository.documents.displayName(source)

    private fun playCurrentFolder() {
        val source = currentSource() ?: return
        model.scanFolders(listOf(source), model.browser.value.path.lastOrNull(), play = true)
    }

    private fun sourceMenu(source: FolderSource) = choiceDialog(
        sourceName(source),
        listOf(
            getString(R.string.browse) to { model.openSource(source) },
            getString(R.string.rescan) to { model.scanFolders(listOf(source)) },
            getString(R.string.play_recursively) to { model.scanFolders(listOf(source), play = true) },
            getString(R.string.save_folder_playlist) to {
                newPlaylistDialog(folders = listOf(BrowserLocation(source.uri,
                    model.repository.documents.rootId(source.uri), sourceName(source))))
            },
            getString(if (source.pinned) R.string.unpin_folder else R.string.pin_folder) to { model.togglePin(source); Unit },
            getString(R.string.remove_source) to {
                AccentTheme.dialog(this).setTitle(R.string.remove_source)
                    .setMessage(getString(R.string.remove_source_body, sourceName(source)))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.remove) { _, _ -> model.removeSource(source) }.show()
                Unit
            },
        ),
    )

    private fun documentMenu(entry: DocumentEntry) {
        if (entry.isDirectory) {
            choiceDialog(entry.name, listOf(
                getString(R.string.browse) to { model.openDirectory(entry) },
                getString(R.string.play_recursively) to {
                    val source = currentSource()
                    if (source != null) {
                        val parent = model.browser.value.path.lastOrNull()?.relativePath.orEmpty()
                        val location = BrowserLocation(source.uri, entry.id, entry.name,
                            listOf(parent, entry.name).filter(String::isNotEmpty).joinToString("/"))
                        model.scanFolders(listOf(source), location, play = true)
                    }
                },
            ))
        } else {
            val source = currentSource() ?: return
            val parent = model.browser.value.path.lastOrNull()?.documentId ?: return
            lifecycleScope.launch {
                model.guard {
                    val path = model.browser.value.path.lastOrNull()?.relativePath.orEmpty()
                    val track = model.repository.browserTrack(entry.uri, source.uri, parent,
                        listOf(path, entry.name).filter(String::isNotEmpty).joinToString("/")).track
                    trackMenu(track)
                }
            }
        }
    }

    private fun trackMenu(track: Track, playlistId: String? = null) {
        val actions = mutableListOf<Pair<String, () -> Unit>>(
            getString(R.string.play_only) to { model.requestPlay(listOf(track)) },
            getString(R.string.play_next) to { model.requestPlay(listOf(track), mode = QueueMode.NEXT) },
            getString(R.string.add_queue) to { model.requestPlay(listOf(track), mode = QueueMode.APPEND) },
            getString(if (track.id in model.library.value.favorites) R.string.unfavorite else R.string.favorite) to { model.favorite(track); Unit },
            getString(R.string.add_playlist) to { choosePlaylist(listOf(track)) },
            getString(R.string.details) to { detailsDialog(track) },
        )
        if (playlistId != null) actions.add(getString(R.string.remove_playlist_song) to {
            model.removePlaylistTrack(playlistId, track.id); Unit
        })
        actions.add(getString(R.string.remove_track) to {
            AccentTheme.dialog(this).setTitle(R.string.remove_track)
                .setMessage(getString(R.string.remove_track_body, track.title))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.remove) { _, _ -> model.removeTrack(track) }.show()
            Unit
        })
        choiceDialog(track.title, actions)
    }

    private fun queueMenu(index: Int) {
        val track = queueTracks.getOrNull(index) ?: return
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (index > 0) actions.add(getString(R.string.move_up) to {
            withController { if (index < it.mediaItemCount) it.moveMediaItem(index, index - 1) }
        })
        if (index < queueTracks.lastIndex) actions.add(getString(R.string.move_down) to {
            withController { if (index + 1 < it.mediaItemCount) it.moveMediaItem(index, index + 1) }
        })
        actions.add(getString(R.string.remove_queue) to {
            withController { if (index < it.mediaItemCount) it.removeMediaItem(index) }
        })
        actions.add(getString(R.string.add_playlist) to { choosePlaylist(listOf(track)) })
        actions.add(getString(R.string.details) to { detailsDialog(track) })
        choiceDialog(track.title, actions)
    }

    private fun playlistMenu(playlist: LocalPlaylist) {
        val actions = mutableListOf(
            getString(R.string.play_all) to { model.requestPlay(LibraryEdits.playlistTracks(model.library.value, playlist.id)) },
            getString(R.string.add_songs) to { model.playlist(playlist.id); addSongsDialog() },
            getString(R.string.edit_playlist_songs) to { addSongsDialog(edit = playlist) },
            getString(R.string.edit_playlist_folders) to { saveFoldersDialog(playlist) },
            getString(R.string.rename_playlist) to { newPlaylistDialog(rename = playlist) },
            getString(R.string.delete_playlist) to {
                AccentTheme.dialog(this).setTitle(R.string.delete_playlist)
                    .setMessage(getString(R.string.delete_playlist_body, playlist.name))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.remove) { _, _ -> model.removePlaylist(playlist.id) }.show()
                Unit
            },
        )
        if (playlist.folders.isNotEmpty()) actions.add(getString(R.string.refresh_playlist_folders) to {
            model.updatePlaylistFolders(playlist.id, playlist.folders.map { it.location() })
        })
        choiceDialog(playlist.name, actions)
    }

    private fun newPlaylistDialog(tracks: List<Track> = emptyList(), folders: List<BrowserLocation> = emptyList(),
        rename: LocalPlaylist? = null,
    ) {
        val field = TextInputLayout(this).apply {
            hint = getString(R.string.playlist_name)
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val name = TextInputEditText(field.context).apply {
            setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(80))
            setText(rename?.name ?: folders.singleOrNull()?.name.orEmpty())
        }
        field.addView(name)
        val dialog = AccentTheme.dialog(this)
            .setTitle(if (rename == null) R.string.create_playlist else R.string.rename_playlist).setView(field)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(if (rename == null) R.string.create else R.string.save, null).create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = name.text?.toString().orEmpty().trim()
                if (text.isEmpty() || text.length > 80 ||
                    model.library.value.playlists.any { it.id != rename?.id && it.name.equals(text, true) }) {
                    field.error = getString(R.string.playlist_name_invalid)
                } else {
                    if (rename == null) model.createPlaylist(text, tracks, folders)
                    else model.renamePlaylist(rename.id, text)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun choosePlaylist(tracks: List<Track>) {
        if (tracks.isEmpty()) {
            model.app.inform(getString(R.string.no_tracks_to_play))
            return
        }
        val actions = model.library.value.playlists.map { playlist ->
            playlist.name to { model.addToPlaylist(playlist.id, tracks); Unit }
        } + (getString(R.string.create_playlist) to { newPlaylistDialog(tracks) })
        choiceDialog(getString(R.string.add_playlist), actions)
    }

    private fun addSongsDialog(edit: LocalPlaylist? = null) {
        val id = edit?.id ?: model.selectedPlaylist.value ?: return
        val tracks = AudioFiles.sortAndFilter(model.library.value.tracks, "", SortOrder.TITLE)
        if (tracks.isEmpty()) {
            model.app.inform(getString(R.string.no_tracks_to_play))
            return
        }
        val original = edit?.trackIds.orEmpty().toSet()
        val selected = BooleanArray(tracks.size) { tracks[it].id in original }
        AccentTheme.dialog(this).setTitle(if (edit == null) R.string.add_songs else R.string.edit_playlist_songs)
            .setMultiChoiceItems(tracks.map { "${it.title} · ${it.artist}" }.toTypedArray(), selected) { dialog, which, checked ->
                if (!checked && tracks[which].id in original && LibraryEdits.isPinned(model.library.value, tracks[which])) {
                    (dialog as AlertDialog).listView.setItemChecked(which, true)
                    selected[which] = true
                    model.app.inform(getString(R.string.unpin_first))
                } else selected[which] = checked
            }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(if (edit == null) R.string.add_selected else R.string.save) { _, _ ->
                val chosen = tracks.filterIndexed { index, _ -> selected[index] }
                if (edit != null) {
                    val ids = chosen.map { it.id }
                    val selectedIds = ids.toHashSet()
                    model.updatePlaylistTracks(id, edit.trackIds.filter(selectedIds::contains) + ids.filterNot(original::contains))
                } else if (chosen.isNotEmpty()) model.addToPlaylist(id, chosen)
            }.show()
    }

    private fun saveFoldersDialog(playlist: LocalPlaylist? = null) {
        model.operation {
            val sources = model.library.value.sources.associateBy { it.uri }
            val roots = sources.values.map { BrowserLocation(it.uri, model.repository.documents.rootId(it.uri), sourceName(it)) }
            val current = model.browser.value.path.lastOrNull()
            val choices = (playlist?.folders?.map { it.location() }.orEmpty() + listOfNotNull(current) + roots)
                .distinctBy { it.sourceUri to it.documentId }
            if (choices.isEmpty()) {
                model.app.inform(getString(R.string.select_folders_first))
                return@operation
            }
            val existing = playlist?.folders?.mapTo(hashSetOf()) { it.key }.orEmpty()
            val selected = BooleanArray(choices.size) { index ->
                val location = choices[index]
                when {
                    playlist != null -> (location.sourceUri to location.documentId) in existing
                    current != null -> location == current
                    else -> sources[location.sourceUri]?.selectedForPlayback == true
                }
            }
            val labels = choices.map { location ->
                val source = sources[location.sourceUri]
                val name = if (roots.any { it.sourceUri == location.sourceUri && it.documentId == location.documentId })
                    location.name else "${source?.let(::sourceName).orEmpty()} / ${location.relativePath.ifEmpty { location.name }}"
                if (source?.pinned == true) getString(R.string.pinned_folder_name, name) else name
            }
            AccentTheme.dialog(this@MainActivity)
                .setTitle(if (playlist == null) R.string.save_folder_playlist else R.string.edit_playlist_folders)
                .setMultiChoiceItems(labels.toTypedArray(), selected) { dialog, index, checked ->
                    val location = choices[index]
                    if (!checked && sources[location.sourceUri]?.pinned == true &&
                        (location.sourceUri to location.documentId) in existing
                    ) {
                        selected[index] = true
                        (dialog as AlertDialog).listView.setItemChecked(index, true)
                        model.app.inform(getString(R.string.unpin_first))
                    } else selected[index] = checked
                }
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save) { _, _ ->
                    val folders = choices.filterIndexed { index, _ -> selected[index] }
                    if (playlist != null) model.updatePlaylistFolders(playlist.id, folders)
                    else if (folders.isNotEmpty()) newPlaylistDialog(folders = folders)
                    else model.app.inform(getString(R.string.select_folders_first))
                }.show()
        }
    }

    private fun detailsDialog(track: Track) {
        fun value(text: String) = text.ifBlank { getString(R.string.not_available) }
        fun details(location: String): String = getString(
            R.string.song_details, value(track.title), value(track.artist), value(track.album),
            if (track.durationMs > 0) TimeText.format(track.durationMs) else getString(R.string.not_available),
            value(track.trackNumber), value(track.year), value(track.genre), value(track.fileName),
            value(track.mimeType), location,
        )
        val warning = if (track.uri == artworkUri) artworkWarning else null
        val dialog = AccentTheme.dialog(this).setTitle(R.string.details)
            .setMessage(details(getString(R.string.loading)))
            .setPositiveButton(R.string.close, null).show()
        val job = lifecycleScope.launch {
            val location = withContext(Dispatchers.IO) {
                val documents = model.repository.documents
                try {
                    documents.location(track, model.library.value.sources)
                } catch (error: IOException) {
                    Log.w("SongDetails", "Cannot resolve the file location", error)
                    documents.partialLocation(track, model.library.value.sources)
                } catch (error: SecurityException) {
                    Log.w("SongDetails", "Location permission is unavailable", error)
                    documents.partialLocation(track, model.library.value.sources)
                } catch (error: IllegalArgumentException) {
                    Log.w("SongDetails", "Provider does not expose a path", error)
                    documents.partialLocation(track, model.library.value.sources)
                } catch (error: UnsupportedOperationException) {
                    Log.w("SongDetails", "Provider does not support file locations", error)
                    documents.partialLocation(track, model.library.value.sources)
                } catch (error: IllegalStateException) {
                    Log.w("SongDetails", "Provider is unavailable", error)
                    documents.partialLocation(track, model.library.value.sources)
                }
            }
            if (dialog.isShowing) {
                val body = details(location)
                dialog.setMessage(if (warning == null) body else "$body\n\n${getString(R.string.artwork_issue, warning)}")
                dialog.findViewById<TextView>(android.R.id.message)?.setTextIsSelectable(true)
            }
        }
        dialog.setOnDismissListener { job.cancel() }
    }

    private fun sortDialog() {
        val labels = intArrayOf(R.string.sort_title, R.string.sort_artist, R.string.sort_album, R.string.sort_file)
        AccentTheme.dialog(this).setTitle(R.string.sort)
            .setSingleChoiceItems(labels.map(::getString).toTypedArray(), model.sort.value.ordinal) { dialog, index ->
                model.sort(SortOrder.entries[index])
                dialog.dismiss()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun themeDialog() {
        val modes = intArrayOf(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, DisplaySettings.CUSTOM_THEME)
        val labels = intArrayOf(R.string.theme_night, R.string.theme_day, R.string.theme_system, R.string.theme_custom)
        AccentTheme.dialog(this).setTitle(R.string.theme)
            .setSingleChoiceItems(labels.map(::getString).toTypedArray(), modes.indexOf(model.app.themeMode)) { dialog, index ->
                dialog.dismiss()
                model.app.themeMode = modes[index]
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun backgroundTintDialog() {
        val values = BackgroundTint.entries
        AccentTheme.dialog(this).setTitle(R.string.background_tint)
            .setSingleChoiceItems(values.map { getString(tintLabel(it)) }.toTypedArray(),
                values.indexOf(model.app.displaySettings.options.value.tint)) { dialog, index ->
                dialog.dismiss()
                model.app.displaySettings.update { it.copy(tint = values[index]) }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun tintLabel(tint: BackgroundTint): Int = when (tint) {
        BackgroundTint.ORIGINAL -> R.string.tint_original
        BackgroundTint.DARK -> R.string.tint_dark
        BackgroundTint.LIGHT -> R.string.tint_light
    }

    private fun transitionLabel(style: TransitionStyle): Int = when (style) {
        TransitionStyle.FADE -> R.string.transition_fade
        TransitionStyle.SLIDE -> R.string.transition_slide
        TransitionStyle.ZOOM -> R.string.transition_zoom
        TransitionStyle.NONE -> R.string.transition_none
    }

    private fun decayLabel(speed: SpectrumDecay): Int = when (speed) {
        SpectrumDecay.SLOW -> R.string.speed_slow
        SpectrumDecay.MEDIUM -> R.string.speed_medium
        SpectrumDecay.FAST -> R.string.speed_fast
    }

    private fun languageDialog() {
        val tags = arrayOf("en", "zh-CN", "")
        val labels = intArrayOf(R.string.language_english, R.string.language_chinese, R.string.language_system)
        val language = AppCompatDelegate.getApplicationLocales()[0]?.language
        val selected = when (language) { "en" -> 0; "zh" -> 1; else -> 2 }
        AccentTheme.dialog(this).setTitle(R.string.language)
            .setSingleChoiceItems(labels.map(::getString).toTypedArray(), selected) { dialog, index ->
                dialog.dismiss()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[index]))
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun loopDialog() {
        val modes = LoopMode.entries
        AccentTheme.dialog(this).setTitle(R.string.loop_mode)
            .setSingleChoiceItems(modes.map { getString(loopLabel(it)) }.toTypedArray(),
                modes.indexOf(model.app.playbackSettings.options.value.loop)) { dialog, index ->
                dialog.dismiss()
                model.app.playbackSettings.setLoop(modes[index])
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun orderDialog() {
        val modes = PlayOrder.entries
        AccentTheme.dialog(this).setTitle(R.string.play_order)
            .setSingleChoiceItems(modes.map { getString(orderLabel(it)) }.toTypedArray(),
                modes.indexOf(model.app.playbackSettings.options.value.order)) { dialog, index ->
                dialog.dismiss()
                model.app.playbackSettings.setOrder(modes[index])
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun foldersDialog() {
        val sources = model.library.value.sources
        if (sources.isEmpty()) {
            model.app.inform(getString(R.string.select_folders_first))
            model.openSettings()
            return
        }
        val selected = BooleanArray(sources.size) { sources[it].selectedForPlayback }
        AccentTheme.dialog(this).setTitle(R.string.select_folders)
            .setMultiChoiceItems(sources.map {
                val label = getString(R.string.folder_choice, sourceName(it), it.lastScanCount)
                if (it.pinned) getString(R.string.pinned_folder_name, label) else label
            }.toTypedArray(), selected) { dialog, index, checked ->
                if (!checked && sources[index].pinned) {
                    selected[index] = true
                    (dialog as AlertDialog).listView.setItemChecked(index, true)
                    model.app.inform(getString(R.string.unpin_first))
                } else selected[index] = checked
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.pin_folders) { _, _ -> pinFoldersDialog() }
            .setPositiveButton(R.string.apply) { _, _ ->
                model.selectFolders(sources.filterIndexed { index, _ -> selected[index] }.mapTo(hashSetOf()) { it.uri })
            }.show()
    }

    private fun pinFoldersDialog() {
        val sources = model.library.value.sources
        if (sources.isEmpty()) {
            model.app.inform(getString(R.string.select_folders_first))
            return
        }
        val pinned = BooleanArray(sources.size) { sources[it].pinned }
        AccentTheme.dialog(this).setTitle(R.string.pin_folders)
            .setMultiChoiceItems(sources.map(::sourceName).toTypedArray(), pinned) { _, index, checked -> pinned[index] = checked }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.apply) { _, _ ->
                model.pinFolders(sources.filterIndexed { index, _ -> pinned[index] }.mapTo(hashSetOf()) { it.uri })
            }.show()
    }

    private fun loopLabel(mode: LoopMode): Int = when (mode) {
        LoopMode.OFF -> R.string.loop_off
        LoopMode.SINGLE_FILE -> R.string.loop_single_file
        LoopMode.FOLDER -> R.string.loop_folder
        LoopMode.SELECTED_FOLDERS -> R.string.loop_selected_folders
    }

    private fun orderLabel(order: PlayOrder): Int =
        if (order == PlayOrder.RANDOM) R.string.order_random else R.string.order_sequential

    private fun choiceDialog(title: String, actions: List<Pair<String, () -> Unit>>) {
        AccentTheme.dialog(this).setTitle(title)
            .setItems(actions.map { it.first }.toTypedArray()) { _, index -> actions[index].second() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun renderNotice(notice: Notice?) {
        shownNotice = notice
        binding.noticeBar.isVisible = notice != null
        binding.noticeText.text = notice?.text
        if (compactChrome) TooltipCompat.setTooltipText(binding.noticeText, notice?.text)
        if (notice != null) {
            val custom = model.app.displaySettings.options.value.let { it.customTheme && it.imageVersion > 0 }
            val background = if (custom) Color.TRANSPARENT else MaterialColors.getColor(binding.root,
                if (notice.isError) com.google.android.material.R.attr.colorErrorContainer
                else com.google.android.material.R.attr.colorPrimaryContainer)
            val foreground = MaterialColors.getColor(binding.root,
                if (custom) com.google.android.material.R.attr.colorOnSurface
                else if (notice.isError) com.google.android.material.R.attr.colorOnErrorContainer
                else com.google.android.material.R.attr.colorOnPrimaryContainer)
            binding.noticeBar.setBackgroundColor(background)
            binding.noticeText.setTextColor(foreground)
            binding.dismissNotice.imageTintList = ColorStateList.valueOf(foreground)
        }
    }

    private fun tint(active: Boolean): ColorStateList = ColorStateList.valueOf(
        AccentTheme.color(binding.root, if (active) com.google.android.material.R.attr.colorPrimary
        else com.google.android.material.R.attr.colorOnSurfaceVariant),
    )

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()
}
