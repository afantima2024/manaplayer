package com.macarmusic.app.ui

import kotlin.math.roundToInt
import androidx.appcompat.widget.TooltipCompat

import android.animation.Animator
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.DocumentsContract
import android.text.InputFilter
import android.util.Log
import android.view.View
import android.view.KeyEvent
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.Spinner
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import android.content.DialogInterface
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.doOnLayout
import androidx.core.view.doOnNextLayout
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
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
import com.macarmusic.app.data.LegacyStorage
import com.macarmusic.app.lyrics.LyricIssue
import com.macarmusic.app.lyrics.LyricOrigin
import com.macarmusic.app.lyrics.LyricState
import com.macarmusic.app.lyrics.CachedLyric
import com.macarmusic.app.lyrics.LyricTiming
import com.macarmusic.app.lyrics.LyricChoices
import com.macarmusic.app.lyrics.LyricDownloadState
import com.macarmusic.app.model.AudioFiles
import com.macarmusic.app.model.BlockOutline
import com.macarmusic.app.model.BlockStyle
import com.macarmusic.app.model.BrowserLocation
import com.macarmusic.app.model.LyricAppearance
import com.macarmusic.app.model.LyricCacheExpiry
import com.macarmusic.app.model.LyricSide
import com.macarmusic.app.model.ThemeAccent
import com.macarmusic.app.model.DocumentEntry
import com.macarmusic.app.model.ExplorerMode
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
import com.macarmusic.app.model.SpectrumOptions
import com.macarmusic.app.model.SpectrumOrigin
import com.macarmusic.app.model.SpectrumStyle
import com.macarmusic.app.model.Track
import com.macarmusic.app.model.LibraryMode
import com.macarmusic.app.model.PlaybackCollection
import com.macarmusic.app.model.CollectionKind
import com.macarmusic.app.model.BackupEdits
import com.macarmusic.app.data.PreparedBackup
import com.macarmusic.app.playback.withCollection
import com.macarmusic.app.model.TransitionStyle
import com.macarmusic.app.playback.PlaybackService
import com.macarmusic.app.playback.toMediaItem
import com.macarmusic.app.playback.toTrack
import com.macarmusic.app.radio.CnrRadio
import com.macarmusic.app.radio.RadioCommands
import com.macarmusic.app.radio.RadioProgram
import com.macarmusic.app.radio.RadioImportMode
import androidx.recyclerview.widget.ConcatAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.io.IOException
import java.io.File

@androidx.annotation.OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {
    private val model: MusicViewModel by viewModels()
    private lateinit var contentScope: CoroutineScope
    private var contentJob: Job? = null
    private var contentRebindJob: Job? = null
    private var contentGeneration = 0
    private var contentConfiguration = Configuration()
    private val contentCacheResets = mutableListOf<() -> Unit>()
    private fun <T> contentCache(create: () -> T): kotlin.properties.ReadOnlyProperty<MainActivity, T> {
        var cached = lazy(create)
        contentCacheResets += { cached = lazy(create) }
        return kotlin.properties.ReadOnlyProperty { _, _ -> cached.value }
    }
    private lateinit var binding: ActivityMainBinding
    private lateinit var focusMemory: PlayerFocusMemory
    private lateinit var now: ViewNowPlayingBinding
    private lateinit var settings: ViewSettingsBinding
    private lateinit var settingsButtons: BlockButtonStyler
    private lateinit var libraryPanel: ListPanel
    private lateinit var folderPanel: ListPanel
    private lateinit var queuePanel: ListPanel
    private lateinit var playlistPanel: ListPanel
    private lateinit var radioPanel: ListPanel
    private lateinit var radioAdapter: RadioButtonAdapter
    private lateinit var radioChannelAdapter: RadioChannelAdapter
    private var radioStickyHeader: RadioStickyHeader? = null
    private var radioGroupsJob: Job? = null
    private var radioQuickGrid: RecyclerView? = null
    private var radioQuickAdapter: RadioButtonAdapter? = null
    private var radioQuickEmpty: TextView? = null
    private var radioImportDialog: PlayerDialog? = null
    private var radioSubscriptionsDialog: RadioSubscriptionsDialog? = null
    private var shownRadioImport: PreparedRadioImport? = null
    private var radioSourceCommand: ListenableFuture<SessionResult>? = null
    private var restoringMusic = false
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
    private lateinit var libraryBrowse: MaterialButton
    private lateinit var libraryBack: MaterialButton
    private lateinit var folderUp: MaterialButton
    private lateinit var folderPlay: MaterialButton
    private lateinit var folderRescan: MaterialButton
    private lateinit var folderRestore: MaterialButton
    private lateinit var folderRemoveAll: MaterialButton
    private lateinit var cancelScan: MaterialButton
    private lateinit var playlistBack: MaterialButton
    private lateinit var playlistPlay: MaterialButton
    private lateinit var playlistAdd: MaterialButton
    private lateinit var playlistUpdate: MaterialButton
    private lateinit var playlistRestore: MaterialButton
    private lateinit var audio: AudioManager
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var modeSwitchCommand: ListenableFuture<SessionResult>? = null
    private val controllerConnected = MutableStateFlow(false)
    private val radioWarmupReady = MutableStateFlow(false)
    private val progressPolling = MutableStateFlow(false)
    private val volumePolling = MutableStateFlow(false)
    private val radioStatusPolling = MutableStateFlow(false)
    private val firstFrame = CompletableDeferred<Unit>()
    private var optionalContentStarted = false
    private var coreReported = false
    private var parkedNoticeShown = false
    private var deviceMusicDialog: PlayerDialog? = null
    private var lyricTimingDialog: PlayerDialog? = null
    private var lyricVersionsDialog: LyricVersionsDialog? = null
    private var onlineLyricsDialog: OnlineLyricsDialog? = null
    private var lyricDownloadsDialog: LyricDownloadsDialog? = null
    private var timingTrackId: String? = null
    private var timingDocument: CachedLyric? = null
    private var renderedDeviceMusic: DeviceMusicPicker? = null
    private var backupDialog: PlayerDialog? = null
    private var shownBackup: PreparedBackup? = null
    private var artworkJob: Job? = null
    private val slideshow by contentCache {
        BackgroundSlideshow(binding.backgroundImage, binding.backgroundIncoming,
            model.app.backgrounds, model::backgroundIndex) { error ->
            model.app.report(getString(R.string.background_failed,
                if (error is SecurityException) getString(R.string.document_unavailable) else error.localizedMessage.orEmpty()))
        }
    }
    private var screenAnimator: Animator? = null
    private var visibleScreen: View? = null
    private var screenLoadJob: Job? = null
    private var pendingScreenId: Int? = null
    private val loadingScreen by contentCache {
        TextView(this).apply {
            id = R.id.screenLoading
            gravity = android.view.Gravity.CENTER
            textSize = 18f
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setTextColor(AccentTheme.color(this, com.google.android.material.R.attr.colorOnSurface))
            isVisible = false
            binding.screenContainer.addView(this, FrameLayout.LayoutParams(-1, -1))
            focusMemory.scope(this, "screen:loading")
            ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        }
    }
    private var shownSettingsCategory: SettingsCategory? = null
    private var restoringSettingsScroll = false
    private var appliedControlBlocks: Triple<Boolean, BlockOutline, Int?>? = null
    private var appliedNavigationPosition: NavigationPosition? = null
    private var appliedAccent = ThemeAccent.DEFAULT
    private var appliedCustomAccent: Int? = null
    private var appliedAppearance: Pair<com.macarmusic.app.model.AppLogo, com.macarmusic.app.model.ButtonIconStyle>? = null
    private val compactChrome get() = resources.configuration.screenHeightDp < 480
    private val controlBlockContainers by contentCache {
        listOf<View>(now.controls.trackInformation, now.controls.spectrum, now.controls.seekBlock,
            now.controls.volumeBlock, binding.miniProgress)
    }
    private val controlBlockButtons by contentCache {
        listOf(now.controls.shuffle, now.controls.previous, now.controls.rewind, now.controls.play,
            now.controls.forward, now.controls.next, now.controls.repeat, now.controls.volumeMute,
            binding.miniPlay, binding.miniNext, now.cover.lyricVersions, now.cover.nextLyrics, now.cover.lyricsTiming)
    }
    private val controlBlockOverlays by contentCache {
        listOfNotNull<View>(now.cover.root, binding.miniPlayer.takeUnless { compactChrome },
            now.controls.favorite, now.controls.switchPlaybackMode, now.controls.details)
    }
    private val originalControlBackgrounds by contentCache {
        (controlBlockContainers + controlBlockButtons).associateWith { it.background }
    }
    private val originalControlPadding by contentCache {
        (controlBlockContainers + now.cover.root + listOfNotNull(binding.miniPlayer.takeUnless { compactChrome })).associateWith {
            android.graphics.Rect(it.paddingStart, it.paddingTop, it.paddingEnd, it.paddingBottom)
        }
    }
    private val originalActionShapes by contentCache {
        listOf(now.controls.favorite, now.controls.switchPlaybackMode, now.controls.details).associateWith { it.shapeAppearanceModel }
    }
    private val originalActionStrokes by contentCache {
        listOf(now.controls.favorite, now.controls.switchPlaybackMode, now.controls.details).associateWith { it.strokeWidth }
    }
    private val originalModeColors by contentCache {
        now.controls.switchPlaybackMode.let { it.backgroundTintList to it.textColors }
    }
    private val favoriteTextSize by contentCache { now.controls.favorite.textSize }
    private val favoriteIconSize by contentCache { now.controls.favorite.iconSize }
    private var customSystemBars = false
    private val barElevations by contentCache {
        listOf(binding.toolbar, binding.navigation, binding.miniPlayer).associateWith { it.elevation }
    }
    private val navigationIndicator by contentCache { binding.navigation.isItemActiveIndicatorEnabled }
    private val lyricsAdapter = LyricsAdapter()
    private lateinit var lyricScrolling: LyricScrollController
    private var renderedLyricTrackId: String? = null
    private var artworkUri: String? = null
    private var artworkWarning: String? = null
    private var draggingSeek: SeekBar? = null
    private var draggingTrackId: String? = null
    private var playlistTrackRows = emptyList<Track>()
    private var queueTracks = emptyList<Track>()
    private var queueSignature: List<Pair<String, String>> = emptyList()
    private var queueScrollPending = false
    private var stoppedSinceResume = false

    private val pickFolder = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri == null) model.app.report(getString(R.string.picker_no_document))
            else model.addFolder(uri, result.data?.flags ?: 0)
        }
    }

    private val pickFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult(), ::handlePickedFile)
    private val pickLyricCache = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri == null) model.app.report(getString(R.string.picker_no_document))
            else model.moveLyricCache(uri, result.data?.flags ?: 0)
        }
    }
    private val pickBackground = registerForActivityResult(ActivityResultContracts.StartActivityForResult(), ::handlePickedBackground)
    private val createBackup = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (result.data?.getBooleanExtra(FileExplorerActivity.EXTRA_SHARE_BACKUP, false) == true)
                model.prepareSharedBackup(AppCompatDelegate.getApplicationLocales().toLanguageTags())
            else if (uri == null) model.app.report(getString(R.string.picker_no_document))
            else model.exportBackup(uri, AppCompatDelegate.getApplicationLocales().toLanguageTags())
        }
    }
    private val pickBackup = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri == null) model.app.report(getString(R.string.picker_no_document)) else model.previewBackup(uri)
        }
    }
    private val pickRadioList = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri == null) model.app.report(getString(R.string.picker_no_document)) else model.previewRadioList(uri)
        }
    }
    private val createRadioList = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri == null) model.app.report(getString(R.string.picker_no_document)) else model.exportRadioList(uri)
        }
    }
    private val reconnectFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = model.consumeReconnectTrack(0)
        if (id != null && result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri == null) model.app.report(getString(R.string.picker_no_document))
            else model.reconnectTrack(id, uri, result.data?.flags ?: 0)
        }
    }
    private val reselectFolder = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val source = model.consumeFolderRecovery()
        if (source != null && result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri == null) model.app.report(getString(R.string.picker_no_document))
            else model.reselectFolder(source, uri, result.data?.flags ?: 0)
        }
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
        model.startupLaunch.onCreate(
            normalLaunch = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0 &&
                !intent.getBooleanExtra(com.macarmusic.app.playback.StartupPlayback.OPEN_PLAYER_ONLY, false) &&
                intent.action in setOf(null, Intent.ACTION_MAIN, "android.intent.action.MUSIC_PLAYER"),
            enabled = model.app.displaySettings.options.value.autoPlayOnLaunch,
        )
        audio = getSystemService(AudioManager::class.java)
        volumeControlStream = AudioManager.STREAM_MUSIC
        onBackPressedDispatcher.addCallback(this) {
            when {
                model.selectedScreen.value == R.id.action_settings -> model.closeSettings()
                model.selectedScreen.value == R.id.nav_library && model.librarySelecting.value -> model.selectLibrary(false)
                model.selectedScreen.value == R.id.nav_library && model.libraryUp() -> Unit
                model.selectedScreen.value == R.id.nav_folders && model.browserUp() -> Unit
                model.selectedScreen.value == R.id.nav_playlists && model.selectedPlaylist.value != null -> model.playlist(null)
                model.selectedScreen.value != R.id.nav_player -> model.screen(R.id.nav_player)
                else -> finish()
            }
        }
        bindContent()
        if (savedInstanceState == null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
            handlePlaybackIntent(intent)
        }
    }

    private fun bindContent(builtScreens: Set<Int> = emptySet()) {
        if (::focusMemory.isInitialized) focusMemory.dispose()
        contentJob = SupervisorJob(lifecycleScope.coroutineContext[Job])
        contentScope = CoroutineScope(lifecycleScope.coroutineContext + requireNotNull(contentJob))
        contentConfiguration = Configuration(resources.configuration)
        screenLoadJob = null
        pendingScreenId = null
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        focusMemory = PlayerFocusMemory(binding.root, model.focusPositions)
        configureCompactChrome()
        val display = model.app.displaySettings.options.value
        configureSystemBars(display.customTheme && display.imageVersion > 0)
        ViewCompat.setOnApplyWindowInsetsListener(binding.content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(binding.content)
        buildPlayerScreen()
        focusMemory.scope(now.root, "screen:${R.id.nav_player}")
        if (R.id.nav_library in builtScreens) buildLibrary()
        if (R.id.nav_folders in builtScreens) buildFolders()
        if (R.id.nav_queue in builtScreens) buildQueue()
        if (R.id.nav_playlists in builtScreens) buildPlaylists()
        if (R.id.nav_radio in builtScreens) buildRadio()
        if (R.id.action_settings in builtScreens) buildSettings()
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
        binding.toolbar.children.filterIsInstance<android.widget.ImageView>()
            .single { it.drawable === binding.toolbar.logo }.apply {
                id = R.id.homeLogo
                layoutParams = layoutParams.apply { width = dp(48); height = dp(48) }
                setPadding(dp(8), dp(8), dp(8), dp(8))
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                contentDescription = getString(R.string.logo_now_playing)
                val background = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, background, true)
                setBackgroundResource(background.resourceId)
                isFocusable = true
                setOnClickListener { model.screen(R.id.nav_player) }
                ViewCompat.setAccessibilityDelegate(this, object : androidx.core.view.AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(host: View,
                        info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = "android.widget.Button"
                    }
                })
            }
        binding.navigation.setOnItemSelectedListener(model::screen)
        applyNavigationVisibility()
        binding.miniInfo.setOnClickListener { model.screen(R.id.nav_player) }
        binding.miniArtwork.setOnClickListener { model.screen(R.id.nav_player) }
        binding.recoveryStatus.bindActions(::retryRecovery, ::reselectRecovery)
        configureNavigation(display.navigationPosition)
        renderBackground(display)
        showScreen(model.selectedScreen.value)
        observeState()
        contentScope.launch {
            binding.root.awaitDraw()
            firstFrame.complete(Unit)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Android 13 can include screen-layout qualifiers in a locale-only change.
        val handled = ActivityInfo.CONFIG_LOCALE or ActivityInfo.CONFIG_LAYOUT_DIRECTION or ActivityInfo.CONFIG_SCREEN_LAYOUT
        if (!::binding.isInitialized || contentConfiguration.diff(resources.configuration) and handled == 0) return
        contentRebindJob?.cancel()
        contentRebindJob = lifecycleScope.launch {
            contentGeneration++
            focusMemory.pause()
            contentJob?.cancelAndJoin()
            val builtScreens = buildSet {
                if (::libraryPanel.isInitialized) add(R.id.nav_library)
                if (::folderPanel.isInitialized) add(R.id.nav_folders)
                if (::queuePanel.isInitialized) add(R.id.nav_queue)
                if (::playlistPanel.isInitialized) add(R.id.nav_playlists)
                if (::radioPanel.isInitialized) add(R.id.nav_radio)
                if (::settings.isInitialized) add(R.id.action_settings)
            }
            dismissContentDialogs()
            screenAnimator?.cancel()
            screenAnimator = null
            lyricScrolling.stop()
            now.cover.lyricsList.adapter = null
            if (::radioPanel.isInitialized) radioPanel.list.adapter = null
            radioQuickGrid?.adapter = null
            radioQuickGrid = null
            radioQuickAdapter = null
            radioQuickEmpty = null
            radioStickyHeader = null
            radioGroupsJob = null
            val background = binding.backgroundImage.drawable
            val backgroundVisible = binding.backgroundImage.isVisible
            val artwork = now.cover.artwork.drawable
            val miniArtwork = binding.miniArtwork.drawable
            val navigationArtwork = binding.navigation.artwork.drawable
            val artworkTint = binding.miniArtwork.imageTintList
            if (artworkJob?.isCancelled == true) artworkUri = null
            artworkJob = null
            visibleScreen = null
            shownSettingsCategory = null
            appliedControlBlocks = null
            appliedNavigationPosition = null
            appliedAppearance = null
            renderedLyricTrackId = null
            draggingSeek = null
            draggingTrackId = null
            queueSignature = emptyList()
            contentCacheResets.forEach { it() }
            // Replace the localized tree without destroying the window or reconnecting the player.
            bindContent(builtScreens)
            binding.backgroundImage.setImageDrawable(background)
            binding.backgroundImage.isVisible = backgroundVisible
            now.cover.artwork.setImageDrawable(artwork)
            binding.miniArtwork.setImageDrawable(miniArtwork)
            binding.navigation.artwork.setImageDrawable(navigationArtwork)
            listOf(now.cover.artwork, binding.miniArtwork, binding.navigation.artwork)
                .forEach { it.imageTintList = artworkTint }
            model.app.applicationScope.launch(Dispatchers.Main.immediate) { model.app.homeWidgets.refresh() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (stoppedSinceResume) {
            stoppedSinceResume = false
            model.app.startupPlayback.launch(
                normalLaunch = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0 &&
                    !intent.getBooleanExtra(com.macarmusic.app.playback.StartupPlayback.OPEN_PLAYER_ONLY, false) &&
                    intent.action in setOf(null, Intent.ACTION_MAIN, "android.intent.action.MUSIC_PLAYER"),
                enabled = model.app.displaySettings.options.value.autoPlayOnLaunch,
            )
            if (controller != null && model.app.startupPlayback.pending) radioCommand(RadioCommands.startup)
        }
        handlePlaybackIntent(intent)
    }

    private fun handlePlaybackIntent(incoming: Intent) {
        when (incoming.action) {
            Intent.ACTION_VIEW -> {
                model.app.startupPlayback.cancel()
                val uri = incoming.data
                // A configuration change or a later launch from Recents must not replay this request.
                setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
                if (incoming.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
                model.screen(R.id.nav_player)
                if (uri == null) model.app.report(getString(R.string.picker_no_document))
                else model.openFile(uri, incoming.flags)
            }
            "android.intent.action.MUSIC_PLAYER" -> model.screen(R.id.nav_player)
            Intent.ACTION_MAIN -> if (incoming.hasCategory(Intent.CATEGORY_APP_MUSIC)) model.screen(R.id.nav_player)
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
        stoppedSinceResume = false
        val display = model.app.displaySettings.options.value
        configureSystemBars(display.customTheme && display.imageVersion > 0)
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            stoppedSinceResume = true
            model.app.startupPlayback.cancel()
        }
        screenAnimator?.cancel()
        visibleScreen?.let(ViewTransitions::reset)
        if (optionalContentStarted) model.app.spectrum.setVisible(this, false)
        now.controls.spectrum.setPlaybackActive(false)
        model.setLyricsActive(false)
        binding.root.keepScreenOn = false
        if (artworkJob?.isActive == true) {
            artworkJob?.cancel()
            artworkUri = null
        }
        lyricScrolling.stop()
        draggingSeek = null
        draggingTrackId = null
        restoringMusic = false
        modeSwitchCommand = null
        controller?.removeListener(playerListener)
        controller = null
        controllerConnected.value = false
        radioWarmupReady.value = false
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        super.onStop()
    }

    override fun onDestroy() {
        if (::focusMemory.isInitialized) focusMemory.dispose()
        dismissContentDialogs()
        artworkJob?.cancel()
        screenAnimator?.cancel()
        if (optionalContentStarted) model.app.spectrum.setVisible(this, false)
        super.onDestroy()
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (::focusMemory.isInitialized) focusMemory.touch(event)
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (::focusMemory.isInitialized && focusMemory.key(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::focusMemory.isInitialized) focusMemory.windowFocused()
    }

    private fun dismissContentDialogs() {
        lyricVersionsDialog?.dismiss()
        lyricVersionsDialog = null
        onlineLyricsDialog?.dismiss()
        onlineLyricsDialog = null
        lyricDownloadsDialog?.dismiss()
        lyricDownloadsDialog = null
        lyricTimingDialog?.dismiss()
        lyricTimingDialog = null
        timingTrackId = null
        timingDocument = null
        deviceMusicDialog?.dismiss()
        deviceMusicDialog = null
        renderedDeviceMusic = null
        backupDialog?.dismiss()
        backupDialog = null
        shownBackup = null
        radioImportDialog?.dismiss()
        radioImportDialog = null
        shownRadioImport = null
        radioSubscriptionsDialog?.dismiss()
        radioSubscriptionsDialog = null
        PlayerDialog.dismissOwned(this)
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
                if (model.app.startupPlayback.pending) radioCommand(RadioCommands.startup)
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
            if (events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)
            ) updateQueue()
        }
    }

    private fun observeState() {
        val generation = contentGeneration
        contentScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                firstFrame.await()
                radioWarmupReady.first { it }
                model.app.warmRadioCatalog()
                withContext(Dispatchers.IO) { model.app.radioCatalog.awaitReady() }
                model.radioQuickPage.collect { renderRadioQuickStations(prepare = true) }
            }
        }
        contentScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                firstFrame.await()
                model.app.playbackReady.first { it }
                model.refreshRadioWhileInUse()
            }
        }
        contentScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.selectedScreen.collectLatest { screen ->
                    showScreen(screen)
                    if (screen == R.id.nav_library) model.libraryPage.collect { renderLibrary() }
                    else if (screen == R.id.nav_radio && model.app.radio.options.value.showPanel)
                        combine(model.radioPage, model.radioChannels) { _, _ -> Unit }.collect { renderRadio() }
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
                launch { model.browser.collect { renderFolders(); renderRecovery() } }
                launch { model.scan.collect { renderFolders(); renderSettings(); renderRecovery() } }
                launch { model.recoveringSource.collect { renderRecovery() } }
                launch { model.metadataProgress.collect { renderFolders(); renderSettings() } }
                launch { model.selectedPlaylist.collect { renderPlaylists() } }
                launch { model.app.notice.collect(::renderNotice) }
                launch { model.app.playbackProblem.collect { renderRecovery() } }
                launch { model.playRequest.collect { consumePlayRequest() } }
                launch { model.app.playbackSettings.options.collect { renderSettings(); updatePlayer() } }
                launch { model.app.playbackReady.collect { updatePlayer() } }
                launch { model.app.radioPreparing.collect { renderLyrics(model.lyrics.value); updatePlayer() } }
                launch {
                    combine(model.app.displaySettings.options.map { it.keepScreenOn }.distinctUntilChanged(),
                        model.app.playbackActive) { enabled, active -> enabled && active }
                        .collect { binding.root.keepScreenOn = it }
                }
                launch { model.app.radio.options.collect {
                    applyNavigationVisibility()
                    renderSettings()
                    renderRadio()
                    updatePlayer()
                } }
                launch { model.app.radio.savedMusic.collect { updateQueue(force = true) } }
                launch { model.radioImportBusy.collect { renderRadio(); renderSettings() } }
                launch { model.subscriptionProgress.collect { renderSettings() } }
                launch { model.logoProgress.collect { renderSettings() } }
                launch { model.preparedRadioImport.collect(::renderRadioImport) }
                launch { model.radioSubscriptionsOpen.collectLatest { open ->
                    if (!open) {
                        radioSubscriptionsDialog?.dismiss()
                        radioSubscriptionsDialog = null
                    } else {
                        val dialog = radioSubscriptionsDialog ?: RadioSubscriptionsDialog(this@MainActivity, model) {
                            if (!isChangingConfigurations && generation == contentGeneration) model.closeRadioSubscriptions()
                        }.also { radioSubscriptionsDialog = it; it.show() }
                        combine(model.app.radioCatalog.state, model.subscriptionProgress, model.radioImportBusy,
                            model.backupBusy) { catalog, progress, editing, backup -> Triple(catalog, progress, editing || backup) }
                            .collect { (catalog, progress, busy) -> dialog.update(catalog, progress, busy) }
                    }
                } }
                launch {
                    combine(model.selectedScreen, model.app.radio.options, model.app.radioPreparing) { screen, options, preparing ->
                        preparing || options.active || options.showPanel && screen == R.id.nav_radio
                    }.distinctUntilChanged().collectLatest { needed ->
                        if (needed) model.app.radioCatalog.state.collect { renderRadio(); updatePlayer() }
                    }
                }
                launch {
                    combine(model.selectedScreen, model.app.radio.options) { screen, options ->
                        options.active || options.showPanel && screen == R.id.nav_radio
                    }.distinctUntilChanged().collectLatest { visible ->
                        if (visible) model.app.radioLogos.version.collect { version ->
                            if (::radioAdapter.isInitialized) radioAdapter.logosChanged(version)
                            radioQuickAdapter?.logosChanged(version)
                            if (currentRadio() != null) { artworkUri = null; updateArtwork(null) }
                        }
                    }
                }
                launch {
                    combine(model.selectedScreen, model.app.radio.options, model.app.radioPreparing) { screen, options, preparing ->
                        (preparing || options.active) && screen == R.id.nav_player
                    }.distinctUntilChanged().collectLatest { visible ->
                        if (visible) model.radioQuickPage.collect { renderRadioQuickStations() }
                    }
                }
                launch { model.app.displaySettings.options.collect {
                    if (it.themeAccent != appliedAccent || it.customAccentColor != appliedCustomAccent) {
                        recreate()
                        return@collect
                    }
                    val previousPrimaryColor = appliedControlBlocks?.third
                    renderSettings()
                    renderAppearance(it)
                    configureNavigation(it.navigationPosition)
                    applyNavigationVisibility()
                    renderBackground(it)
                    if (previousPrimaryColor != appliedControlBlocks?.third) updatePlayer()
                    renderLyrics(model.lyrics.value)
                    model.lyricChoices.value?.let { lyricVersionsDialog?.update(it) }
                    updateProgress()
                    updateSpectrum()
                } }
                launch { model.lyrics.collect(::renderLyrics) }
                launch { model.lyricChoices.collect(::renderLyricChoices) }
                launch { model.onlineLyrics.collect(::renderOnlineLyrics) }
                launch {
                    var wasRunning = false
                    model.lyricDownloads.collect {
                        renderLyricDownloads(it)
                        val running = it?.running == true
                        if (running != wasRunning) { wasRunning = running; renderLyricCache() }
                    }
                }
                launch { model.importingBackground.collect { renderSettings() } }
                launch { model.lyricCacheBusy.collect { renderLyricCache() } }
                launch { model.deviceMusic.collect(::renderDeviceMusicPicker) }
                launch { model.backupBusy.collect { renderSettings(); renderFolders(); renderRadio(); renderRecovery() } }
                launch { model.preparedBackup.collect(::renderBackupPreview) }
                launch { model.sharedBackup.collect { uri ->
                    if (uri != null) {
                        model.consumedSharedBackup(uri)
                        try {
                            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = android.content.ClipData.newRawUri("backup", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, getString(R.string.backup_share)))
                        } catch (error: ActivityNotFoundException) {
                            Log.w("MainActivity", "No application can save the shared backup", error)
                            model.app.report(getString(R.string.backup_picker_missing))
                        } catch (error: SecurityException) {
                            Log.w("MainActivity", "Android denied backup sharing", error)
                            model.app.report(getString(R.string.picker_denied))
                        }
                    }
                } }
                launch { model.backupRestored.collect { result ->
                    if (result != null) {
                        model.consumedBackup(result)
                        changeLanguage(result.language)
                        if (result.positionError != null) model.app.report(result.positionError)
                        else model.app.inform(model.app.text(R.string.backup_restored, result.unresolved))
                    }
                } }
                launch {
                    firstFrame.await()
                    progressPolling.collectLatest { active ->
                        if (active) while (isActive) {
                            delay(500)
                            updateProgress()
                        }
                    }
                }
                launch {
                    radioStatusPolling.collectLatest { active ->
                        if (active) while (isActive) {
                            updateRadioStatus()
                            delay(1_000)
                        }
                    }
                }
                launch {
                    firstFrame.await()
                    volumePolling.collectLatest { visible ->
                        if (visible) while (isActive) {
                            updateVolume()
                            delay(2_000)
                        }
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
                    if (model.app.startupPlayback.pending) radioCommand(RadioCommands.startup)
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
        now.cover.lyricVersions.setOnClickListener { model.showLyricVersions() }
        now.cover.nextLyrics.setOnClickListener { model.showOnlineLyrics() }
        now.cover.lyricsTiming.setOnClickListener { showLyricTiming() }
        TooltipCompat.setTooltipText(now.cover.nextLyrics, getString(R.string.lyrics_next))
        TooltipCompat.setTooltipText(now.cover.lyricsTiming, getString(R.string.lyrics_timing))
        now.controls.spectrum.setOnClickListener {
            model.app.displaySettings.update { it.copy(spectrum = it.spectrum.nextMode()) }
        }
        now.controls.spectrum.onOriginDoubleTap = {
            model.app.displaySettings.update { it.copy(spectrum = it.spectrum.copy(origin = it.spectrum.origin.clockwise())) }
        }
    }

    private fun screenBuilt(id: Int): Boolean = when (id) {
        R.id.nav_library -> ::libraryPanel.isInitialized
        R.id.nav_folders -> ::folderPanel.isInitialized
        R.id.nav_queue -> ::queuePanel.isInitialized
        R.id.nav_playlists -> ::playlistPanel.isInitialized
        R.id.nav_radio -> ::radioPanel.isInitialized
        R.id.action_settings -> ::settings.isInitialized
        else -> true
    }

    private fun screenLabel(id: Int): Int = when (id) {
        R.id.nav_library -> R.string.library
        R.id.nav_folders -> R.string.folders
        R.id.nav_queue -> R.string.queue
        R.id.nav_playlists -> R.string.playlists
        R.id.nav_radio -> R.string.radio
        R.id.action_settings -> R.string.settings
        else -> R.string.app_name
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
            R.id.nav_radio -> {
                if (!::radioPanel.isInitialized) buildRadio()
                radioPanel
            }
            R.id.action_settings -> {
                if (!::settings.isInitialized) buildSettings()
                settings.root
            }
            else -> now.root
        }
        if (view.parent == null) {
            AccentTheme.tintControls(view)
            if (view is ListPanel) {
                val display = model.app.displaySettings.options.value
                view.configureBlocks(display.controlBlocks, display.blockOutline)
            }
            view.isVisible = false
            view.setBackgroundColor(Color.TRANSPARENT)
            binding.screenContainer.addView(view)
        }
        if (view.getTag(R.id.playerFocusScope) == null) focusMemory.scope(view, "screen:$id")
        return view
    }

    private fun buildLibrary() {
        libraryPanel = ListPanel(this, compactChrome)
        libraryPanel.list.id = R.id.libraryList
        val searchLayout = TextInputLayout(this).apply {
            hint = getString(R.string.search_hint)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
            setEndIconDrawable(PlayerIcons.drawable(context, R.drawable.ic_close))
            setEndIconTintList(null)
            endIconMinSize = dp(48)
            setEndIconScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE)
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
        libraryPlay = libraryPanel.action(R.string.play_all) {
            val page = model.libraryPage.value
            model.requestPlay(page.tracks, useLoopScope = page.location.mode == LibraryMode.SONGS)
        }
        libraryBrowse = libraryPanel.action(R.string.browse) {
            choiceDialog(getString(R.string.library_browse), LibraryMode.entries.map { mode ->
                getString(libraryModeLabel(mode)) to { model.libraryMode(mode) }
            })
        }.apply { id = R.id.libraryBrowse }
        libraryBack = libraryPanel.action(R.string.back) { model.libraryUp() }.apply { id = R.id.libraryBack }
        favoriteFilter = libraryPanel.action(R.string.favorites_only) { model.toggleFavorites() }
        libraryActions = libraryPanel.actionRow(
            libraryPlay, libraryBrowse, libraryBack,
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
                val page = model.libraryPage.value
                if (page.showingGroups) model.libraryGroup(row.key)
                else if (model.librarySelecting.value) model.selectTrack(row.key)
                else {
                    val tracks = page.tracks
                    val index = tracks.indexOfFirst { it.id == row.key }
                    if (index >= 0) model.requestPlay(tracks, index, useLoopScope = page.location.mode == LibraryMode.SONGS)
                }
            },
            onMore = { row ->
                if (model.libraryPage.value.showingGroups) choiceDialog(row.title, listOf(
                    getString(R.string.browse) to { model.libraryGroup(row.key) },
                    getString(R.string.play_all) to { model.playLibraryGroup(row.key); Unit },
                )) else findTrack(row.key)?.let(::trackMenu)
            },
            onSelect = { row ->
                if (model.libraryPage.value.showingGroups) model.libraryGroup(row.key) else model.selectTrack(row.key)
            },
            onNavigate = { row, key ->
                if (key == KeyEvent.KEYCODE_DPAD_LEFT) model.libraryUp()
                else if (model.libraryPage.value.showingGroups && !model.librarySelecting.value) {
                    model.libraryGroup(row.key)
                    true
                } else false
            },
        )
        libraryPanel.list.adapter = libraryAdapter
        libraryPanel.list.setOnKeyListener { _, key, event ->
            key == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN && event.hasNoModifiers() &&
                (event.repeatCount > 0 || model.libraryUp())
        }
    }

    private fun buildFolders() {
        folderPanel = ListPanel(this, compactChrome)
        folderPanel.list.id = R.id.folderList
        folderUp = folderPanel.action(R.string.up_folder) { model.browserUp() }
        folderPlay = folderPanel.action(R.string.play_folder) {
            if (model.browser.value.path.isEmpty()) model.playSelectedFolders() else playCurrentFolder()
        }
        folderRestore = folderPanel.action(R.string.restore_position) {
            model.browser.value.path.lastOrNull()?.let { model.restorePosition(PlaybackCollection.folder(it)) }
        }.apply { id = R.id.folderRestorePosition }
        folderRescan = folderPanel.action(R.string.rescan_all) {
            val source = currentSource()
            val sources = source?.let(::listOf) ?: model.library.value.sources.filterNot { it.needsReconnect }
            val location = model.browser.value.path.lastOrNull()
            choiceDialog(getString(R.string.rescan), listOf(
                getString(R.string.rescan_quick) to { model.scanFolders(sources, location); model.refreshBrowser() },
                getString(R.string.rescan_full) to {
                    model.scanFolders(sources, location, forceMetadata = true)
                    model.refreshBrowser()
                },
            ))
        }
        cancelScan = folderPanel.action(R.string.cancel_scan) { model.cancelScan() }
        folderRemoveAll = folderPanel.action(R.string.remove_all_folders, ::removeAllFoldersDialog)
            .apply { id = R.id.folderRemoveAll }
        folderPanel.actionRow(
            folderPanel.action(R.string.add_folder) { launchFolderPicker() },
            folderPanel.action(R.string.select_folders) { foldersDialog() },
            folderPanel.action(R.string.open_file) { launchFilePicker() },
            folderPanel.action(R.string.device_music) { launchDeviceMusic() },
            folderUp,
            folderPlay,
            folderRestore,
            folderRescan,
            folderPanel.action(R.string.save_folder_playlist) { saveFoldersDialog() },
            folderRemoveAll,
            cancelScan,
        )
        folderAdapter = MusicRowAdapter(
            compact = compactChrome,
            onClick = ::openBrowserRow,
            onMore = { row ->
                val state = model.browser.value
                if (state.path.isEmpty()) model.library.value.sources.firstOrNull { it.uri == row.key }?.let(::sourceMenu)
                else state.entries.firstOrNull { it.uri == row.key }?.let(::documentMenu)
            },
            onNavigate = { row, key ->
                if (key == KeyEvent.KEYCODE_DPAD_LEFT) model.browserUp()
                else if (model.browser.value.path.isEmpty() ||
                    model.browser.value.entries.any { it.uri == row.key && it.isDirectory }) {
                    openBrowserRow(row)
                    true
                } else false
            },
        )
        folderPanel.list.adapter = folderAdapter
        folderPanel.list.setOnKeyListener { _, key, event ->
            key == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN && event.hasNoModifiers() &&
                (event.repeatCount > 0 || model.browserUp())
        }
    }

    private fun openBrowserRow(row: MusicRow) {
        val state = model.browser.value
        if (state.path.isEmpty()) model.library.value.sources.firstOrNull { it.uri == row.key }?.let {
            if (it.needsReconnect) reconnectSourceDialog(it) else model.openSource(it)
        }
        else state.entries.firstOrNull { it.uri == row.key }?.let {
            if (it.isDirectory) model.openDirectory(it) else model.playBrowserFile(it)
        }
    }

    private fun buildQueue() {
        queuePanel = ListPanel(this, compactChrome)
        queuePanel.list.id = R.id.queueList
        queuePanel.actionRow(
            queuePanel.action(R.string.lyrics_download) {
                model.downloadLyrics(getString(R.string.queue), queueTracks)
            }.apply { id = R.id.queueDownloadLyrics },
            queuePanel.action(R.string.save_queue) {
                if (queueTracks.isEmpty()) model.app.inform(getString(R.string.no_tracks_to_play))
                else newPlaylistDialog(queueTracks)
            },
            queuePanel.action(R.string.clear_queue) {
                AccentTheme.dialog(this).setTitle(R.string.clear_queue)
                    .setMessage(R.string.clear_queue_body)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.clear_queue) { _, _ ->
                        withMusicController { it.pause(); it.clearMediaItems() }
                    }.show()
            },
        )
        queueAdapter = MusicRowAdapter(
            compact = true,
            onClick = { row ->
                queueScrollPending = false
                val index = row.key.substringBefore(':').toIntOrNull()
                if (index != null) withMusicController {
                    if (index < it.mediaItemCount) {
                        if (index != it.currentMediaItemIndex || it.playbackState == Player.STATE_ENDED)
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

    private fun buildRadio() {
        radioPanel = ListPanel(this, compactChrome, RadioPinList(this))
        radioPanel.list.id = R.id.radioList
        radioAdapter = RadioButtonAdapter(contentScope, ::playRadio, ::radioOptions, model::reorderRadioPins)
        radioChannelAdapter = RadioChannelAdapter(::playRadio, { radioOptions(it)?.show() },
            model::toggleRadioGroup, { manageRadioGroups(it) })
        RadioButtonAdapter.configureGrid(radioPanel.list, { radioAdapter.itemCount }) {
            radioChannelAdapter.currentList.getOrNull(it)?.station != null
        }
        val searchLayout = TextInputLayout(this).apply {
            isHintEnabled = false
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
        }
        searchLayout.addView(TextInputEditText(searchLayout.context).apply {
            id = R.id.radioSearch
            hint = getString(R.string.radio_search)
            setSingleLine()
            setText(model.radioQuery.value)
            minHeight = dp(56)
            doAfterTextChanged { model.radioQuery(it?.toString().orEmpty()) }
        })
        searchLayout.findViewById<android.widget.ImageButton>(com.google.android.material.R.id.text_input_end_icon).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12))
            minimumWidth = dp(48)
            minimumHeight = dp(48)
        }
        val configure = radioPanel.action(R.string.radio_settings) {
            model.settingsCategory(SettingsCategory.RADIO)
            model.openSettings()
        }.apply { id = R.id.radioSettings }
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            configure.insetTop = 0
            configure.insetBottom = 0
            radioPanel.top.addView(HorizontalScrollView(this).apply {
                id = R.id.radioControls
                isFillViewport = true
                isHorizontalScrollBarEnabled = false
                isSmoothScrollingEnabled = false
                addView(LinearLayout(this@MainActivity).apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp(8), 0, dp(8), 0)
                    addView(searchLayout, LinearLayout.LayoutParams(dp(200), LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(8)
                        topMargin = dp(4)
                        bottomMargin = dp(4)
                    })
                    addView(configure, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                        topMargin = dp(4)
                        bottomMargin = dp(4)
                    })
                })
            })
        } else {
            searchLayout.setPadding(dp(16), 0, dp(16), 0)
            radioPanel.top.addView(searchLayout)
            radioPanel.actionRow(configure)
        }
        radioPanel.list.adapter = ConcatAdapter(radioAdapter, radioChannelAdapter)
        radioStickyHeader = RadioStickyHeader(radioPanel.list, radioChannelAdapter, { radioAdapter.itemCount },
            model::toggleRadioGroup, { manageRadioGroups(it) }).also {
            val display = model.app.displaySettings.options.value
            it.configureBlocks(display.controlBlocks, display.blockOutline)
        }
    }

    private fun manageRadioGroups(initial: com.macarmusic.app.radio.RadioGroup? = null, hiddenOnly: Boolean = false) {
        if (radioGroupsJob?.isActive == true) return
        radioGroupsJob = RadioGroupsDialog.show(this, contentScope, model, initial, hiddenOnly)
    }

    private fun configureRadioSettings() {
        val panel = settings.settingsRadioPanel
        settings.settingsRadio.setOnCheckedChangeListener { button, checked ->
            model.setRadioPanelVisible(checked)
            button.isChecked = model.app.radio.options.value.showPanel
        }
        panel.radioOpenPlayer.setOnCheckedChangeListener { _, checked ->
            model.app.radio.setOpenPlayerOnSelection(checked)
        }
        panel.radioNavigation.setOnClickListener {
            val modes = com.macarmusic.app.radio.RadioNavigationMode.entries
            AccentTheme.dialog(this).setTitle(R.string.radio_navigation).setSingleChoiceItems(
                modes.map { getString(radioNavigationLabel(it)) }.toTypedArray(),
                modes.indexOf(model.app.radio.options.value.navigationMode),
            ) { dialog, index ->
                model.app.radio.setNavigationMode(modes[index])
                dialog.dismiss()
            }.setNegativeButton(R.string.cancel, null).show()
        }
        panel.radioButtonLogo.setOnClickListener {
            AppearanceChoices.show(this, R.string.radio_button_logo, listOf(
                AppearanceChoices.Choice(R.id.radioLogoBranded, R.string.radio_logo_branded, R.drawable.ic_launcher_radio),
                AppearanceChoices.Choice(R.id.radioLogoClassic, R.string.radio_logo_classic, R.drawable.ic_radio,
                    com.macarmusic.app.model.ButtonIconStyle.MONOCHROME),
            ), model.app.radio.options.value.buttonLogo.ordinal) {
                model.app.radio.setButtonLogo(com.macarmusic.app.radio.RadioButtonLogo.entries[it])
            }
        }
        panel.radioDownloadPinnedLogos.setOnClickListener { model.downloadPinnedRadioLogos() }
        panel.radioCancelLogos.setOnClickListener { model.cancelRadioLogos() }
        panel.radioSubscriptions.setOnClickListener { model.openRadioSubscriptions() }
        panel.radioRefreshInterval.setOnClickListener {
            val intervals = com.macarmusic.app.radio.RadioRefreshInterval.entries
            AccentTheme.dialog(this).setTitle(R.string.radio_refresh_interval).setSingleChoiceItems(
                intervals.map { getString(it.label) }.toTypedArray(),
                intervals.indexOf(model.app.radio.options.value.refreshInterval),
            ) { dialog, index ->
                model.app.radio.setRefreshInterval(intervals[index])
                dialog.dismiss()
            }.setNegativeButton(R.string.cancel, null).show()
        }
        panel.radioManageGroups.setOnClickListener { manageRadioGroups() }
        panel.radioShowGroups.setOnClickListener { manageRadioGroups(hiddenOnly = true) }
        panel.radioPinColor.setOnClickListener {
            ColorSwatchDialog.show(this, getString(R.string.radio_pin_color), radioPinColor() and 0xffffff,
                reset = { model.app.radio.setPinColor(null) }, save = model.app.radio::setPinColor)
        }
        fun seconds(title: Int, values: List<Int>, selected: Int, save: (Int) -> Unit) {
            AccentTheme.dialog(this).setTitle(title).setSingleChoiceItems(
                values.map { getString(R.string.radio_seconds, it) }.toTypedArray(), values.indexOf(selected),
            ) { dialog, index -> dialog.dismiss(); save(values[index]) }
                .setNegativeButton(R.string.cancel, null).show()
        }
        panel.radioBuffer.setOnClickListener {
            val tuning = model.app.radio.options.value.tuning
            seconds(R.string.radio_buffer, listOf(5, 10, 20, 30, 60), tuning.bufferSeconds) {
                model.app.radio.tune(tuning.copy(bufferSeconds = it, startSeconds = minOf(tuning.startSeconds, it)))
            }
        }
        panel.radioStartBuffer.setOnClickListener {
            val tuning = model.app.radio.options.value.tuning
            seconds(R.string.radio_start_buffer, listOf(1, 2, 3, 5, 10).filter { it <= tuning.bufferSeconds }, tuning.startSeconds) {
                model.app.radio.tune(tuning.copy(startSeconds = it))
            }
        }
        panel.radioRetries.setOnClickListener {
            val tuning = model.app.radio.options.value.tuning
            AccentTheme.dialog(this).setTitle(R.string.radio_retries).setSingleChoiceItems(
                (0..3).map { getString(R.string.radio_retry_count, it) }.toTypedArray(), tuning.retries,
            ) { dialog, index -> dialog.dismiss(); model.app.radio.tune(tuning.copy(retries = index)) }
                .setNegativeButton(R.string.cancel, null).show()
        }
        panel.radioAutoSource.setOnCheckedChangeListener { _, checked ->
            model.app.radio.tune(model.app.radio.options.value.tuning.copy(autoSource = checked))
        }
        panel.radioDownloadLogos.setOnCheckedChangeListener { _, checked ->
            model.app.radio.tune(model.app.radio.options.value.tuning.copy(downloadLogos = checked))
        }
        panel.radioShowStatus.setOnCheckedChangeListener { _, checked ->
            model.app.radio.tune(model.app.radio.options.value.tuning.copy(showStatus = checked))
        }
        panel.radioClearCache.setOnClickListener {
            AccentTheme.dialog(this).setTitle(R.string.radio_clear_cache).setMessage(R.string.radio_clear_cache_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.radio_clear_cache) { _, _ -> model.clearRadioLogos() }.show()
        }
        panel.radioImport.setOnClickListener { launchExplorer(pickRadioList, ExplorerMode.RADIO_IMPORT) }
        panel.radioExport.setOnClickListener {
            val date = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(java.util.Date())
            launchExplorer(createRadioList, ExplorerMode.RADIO_EXPORT, "ManaMusicRadio-stations-$date.m3u")
        }
        panel.radioReset.setOnClickListener {
            AccentTheme.dialog(this).setTitle(R.string.radio_reset).setMessage(R.string.radio_reset_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.radio_reset) { _, _ -> model.resetRadioBuiltins() }.show()
        }
        panel.radioFormat.setOnClickListener {
            AccentTheme.dialog(this).setTitle(R.string.radio_list_format).setMessage(R.string.radio_list_format_body)
                .setPositiveButton(R.string.close, null).show()
        }
        panel.radioAbout.setOnClickListener {
            AccentTheme.dialog(this).setTitle(R.string.radio_help_title).setMessage(AboutContent.radioHelp(this))
                .setPositiveButton(R.string.close, null).show()
        }
        panel.radioReturnMusic.setOnClickListener {
            radioCommand(RadioCommands.music) {
                model.screen(R.id.nav_player)
                model.app.inform(getString(R.string.radio_music_restored))
            }
        }
    }

    private fun renderRadioSettings() {
        val panel = settings.settingsRadioPanel
        val preferences = model.app.radio.options.value
        val tuning = preferences.tuning
        val editable = !model.radioImportBusy.value && !model.backupBusy.value
        val progress = model.subscriptionProgress.value
        val logos = model.logoProgress.value
        panel.radioDownloadPinnedLogos.isEnabled = editable && logos == null && preferences.pins.isNotEmpty()
        panel.radioCancelLogos.isVisible = logos != null
        panel.radioCancelLogos.isEnabled = logos?.cancelling == false
        panel.radioLogosProgress.isVisible = logos != null
        panel.radioLogosProgress.isIndeterminate = logos?.total == 0
        panel.radioLogosProgress.max = logos?.total ?: 1
        panel.radioLogosProgress.progress = logos?.completed ?: 0
        panel.radioLogosStatus.isVisible = logos != null
        panel.radioLogosStatus.text = when {
            logos?.cancelling == true -> getString(R.string.radio_subscription_cancelling)
            logos != null && logos.total > 0 -> getString(R.string.radio_logos_progress, logos.completed + 1, logos.total, logos.name)
            else -> getString(R.string.loading)
        }
        panel.radioNavigation.isEnabled = editable
        panel.radioNavigation.text = getString(R.string.setting_value, getString(R.string.radio_navigation),
            getString(radioNavigationLabel(preferences.navigationMode)))
        panel.radioButtonLogo.isEnabled = editable
        panel.radioButtonLogo.text = getString(R.string.setting_value, getString(R.string.radio_button_logo),
            getString(if (preferences.buttonLogo == com.macarmusic.app.radio.RadioButtonLogo.BRANDED_RADIO)
                R.string.radio_logo_branded else R.string.radio_logo_classic))
        panel.radioSubscriptions.isEnabled = editable
        panel.radioRefreshInterval.isEnabled = editable
        panel.radioRefreshInterval.text = getString(R.string.setting_value, getString(R.string.radio_refresh_interval),
            getString(preferences.refreshInterval.label))
        panel.radioManageGroups.isEnabled = editable
        panel.radioShowGroups.isEnabled = editable && preferences.hiddenGroups.isNotEmpty()
        panel.radioShowGroups.text = getString(R.string.radio_groups_restore, preferences.hiddenGroups.size)
        panel.radioSubscriptionsStatus.isVisible = progress != null
        panel.radioSubscriptionsStatus.text = when {
            progress?.cancelling == true -> getString(R.string.radio_subscription_cancelling)
            progress != null && progress.total > 0 -> getString(R.string.radio_subscription_progress,
                progress.name, progress.completed + 1, progress.total)
            else -> getString(R.string.loading)
        }
        settings.settingsRadio.isChecked = preferences.showPanel
        settings.settingsRadio.isEnabled = !model.backupBusy.value
        panel.radioOpenPlayer.isChecked = preferences.openPlayerOnSelection
        panel.radioOpenPlayer.isEnabled = editable
        colorButton(panel.radioPinColor, R.string.radio_pin_color, radioPinColor())
        panel.radioPinColor.isEnabled = editable
        panel.radioBuffer.text = getString(R.string.setting_value, getString(R.string.radio_buffer),
            getString(R.string.radio_seconds, tuning.bufferSeconds))
        panel.radioStartBuffer.text = getString(R.string.setting_value, getString(R.string.radio_start_buffer),
            getString(R.string.radio_seconds, tuning.startSeconds))
        panel.radioRetries.text = getString(R.string.setting_value, getString(R.string.radio_retries),
            getString(R.string.radio_retry_count, tuning.retries))
        panel.radioAutoSource.isChecked = tuning.autoSource
        panel.radioDownloadLogos.isChecked = tuning.downloadLogos
        panel.radioShowStatus.isChecked = tuning.showStatus
        panel.radioImport.isEnabled = editable && progress == null
        panel.radioExport.isEnabled = panel.radioImport.isEnabled
        panel.radioReset.isEnabled = panel.radioImport.isEnabled
        panel.radioReturnMusic.isEnabled = preferences.active && model.app.playbackReady.value
    }

    private fun radioNavigationLabel(mode: com.macarmusic.app.radio.RadioNavigationMode) =
        if (mode == com.macarmusic.app.radio.RadioNavigationMode.GLOBAL) R.string.radio_navigation_global else R.string.radio_navigation_pinned

    private fun radioOptions(station: RadioProgram): PlayerDialog? {
        val current = model.app.radioCatalog.find(station.id) ?: run {
            model.app.inform(getString(R.string.radio_subscription_retired_station))
            return null
        }
        val actions = mutableListOf(
            getString(R.string.play) to { playRadio(current) },
            getString(if (current.id in model.app.radio.options.value.pins) R.string.radio_unpin else R.string.radio_pin)
                to { model.pinRadio(current) },
            getString(R.string.radio_sources, current.sources.size) to { radioSources(current) },
            getString(R.string.radio_edit) to {
                RadioStationEditor.show(this, current) { model.editRadio(current, it) }
            },
            getString(R.string.radio_station_logo) to {
                val logos = com.macarmusic.app.radio.RadioStationLogo.entries
                AppearanceChoices.show(this, R.string.radio_station_logo, logos.map {
                    AppearanceChoices.Choice(View.generateViewId(), it.title, it.image)
                }, logos.indexOf(model.app.radio.options.value.stationLogos[current.id]),
                    reset = { model.setRadioStationLogo(current, null) }) { index ->
                    model.setRadioStationLogo(current, logos[index])
                }
            },
            getString(R.string.details) to { radioDetails(current) },
        )
        if (current.id in model.app.radio.options.value.pins) {
            actions += getString(R.string.radio_move_pin) to { moveRadioPin(current) }
        }
        return choiceDialog(current.title(this), actions, show = false)
    }

    private fun moveRadioPin(station: RadioProgram) {
        val catalog = model.app.radioCatalog.state.value
        val pins = model.app.radio.options.value.pins.mapNotNull(catalog::find)
        val original = pins.map { it.id }
        val from = original.indexOf(station.id)
        if (from < 0) {
            model.app.inform(getString(R.string.radio_pin_order_changed))
            return
        }
        AccentTheme.dialog(this).setTitle(R.string.radio_move_pin)
            .setSingleChoiceItems(pins.mapIndexed { index, pin ->
                getString(R.string.radio_move_pin_position, index + 1, pin.title(this))
            }.toTypedArray(), from) { dialog, target ->
                model.reorderRadioPins(original, original.toMutableList().apply { add(target, removeAt(from)) })
                dialog.dismiss()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun radioSources(station: RadioProgram) {
        val current = model.app.radioCatalog.find(station.id) ?: run {
            model.app.inform(getString(R.string.radio_subscription_retired_station))
            return
        }
        val active = currentRadio()?.takeIf { it.id == current.id }
        AccentTheme.dialog(this).setTitle(getString(R.string.radio_sources, current.sources.size))
            .setSingleChoiceItems(current.sources.mapIndexed { index, uri ->
                getString(R.string.radio_source_item, index + 1, uri)
            }.toTypedArray(), active?.let { current.sources.indexOf(it.streamUri) } ?: current.sourceIndex) { dialog, index ->
                dialog.dismiss()
                playRadio(current, index)
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun switchRadioSource(offset: Int) {
        if (radioSourceCommand != null) return
        val playing = currentRadio() ?: return
        val station = model.app.radioCatalog.find(playing.id) ?: return
        val index = station.sources.indexOf(playing.streamUri).takeIf { it >= 0 }?.plus(offset) ?: return
        if (index !in station.sources.indices) return
        val future = radioCommand(RadioCommands.play, Bundle().apply {
            putString(RadioCommands.STATION, station.id)
            putInt(RadioCommands.SOURCE, index)
            putString(RadioCommands.SOURCE_URI, station.sources[index])
        }) ?: return
        radioSourceCommand = future
        updateProgress()
        future.addListener({
            if (radioSourceCommand === future) radioSourceCommand = null
            updateProgress()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun renderRadio() {
        if (!::radioPanel.isInitialized || visibleScreen !== radioPanel) return
        val preferences = model.app.radio.options.value
        if (!preferences.showPanel) return
        val catalog = model.app.radioCatalog.state.value
        val stations = model.radioPage.value
        val pins = preferences.pins.toSet()
        val pinColor = radioPinColor()
        val highlight = primaryControlHighlight()
        radioAdapter.configureLogo(preferences.buttonLogo, preferences.stationLogos)
        radioAdapter.selection(currentRadio()?.id, preferences.pins, pinColor, highlight)
        val byId = stations.associateBy { it.id }
        val pinned = preferences.pins.mapNotNull(byId::get)
        radioAdapter.submitList(pinned)
        radioChannelAdapter.selection(currentRadio()?.id, pins, pinColor, highlight)
        radioChannelAdapter.submitList(model.radioChannels.value) {
            radioStickyHeader?.rowsChanged()
            val reveal = model.radioRevealId
            if (reveal != null && visibleScreen === radioPanel) {
                val position = radioChannelAdapter.currentList.indexOfFirst { it.station?.id == reveal }
                if (position >= 0) {
                    model.radioRevealed(reveal)
                    radioPanel.list.post {
                        if (visibleScreen === radioPanel && currentRadio()?.id == reveal) {
                            val headerHeight = binding.root.findViewById<View>(R.id.radioStickyGroup)?.height ?: dp(56)
                            (radioPanel.list.layoutManager as androidx.recyclerview.widget.GridLayoutManager)
                                .scrollToPositionWithOffset(radioAdapter.itemCount + position, headerHeight + dp(8))
                        }
                    }
                }
            }
        }
        radioPanel.heading.text = if (model.radioImportBusy.value || !catalog.ready) getString(R.string.loading)
            else getString(R.string.radio_catalog_hint, catalog.programs.size)
        val hidden = stations.isNotEmpty() && preferences.hiddenGroups.isNotEmpty() &&
            model.radioChannels.value.isEmpty() && pinned.isEmpty()
        radioPanel.empty(getString(if (hidden) R.string.radio_groups_all_hidden else R.string.radio_empty),
            stations.isEmpty() || hidden)
        updateCompactHeading(radioPanel)
    }

    private fun renderRadioQuickStations(prepare: Boolean = false) {
        val visible = radioPanelRequested() && visibleScreen === now.root
        if (!visible && !prepare) {
            radioQuickGrid?.visibility = View.INVISIBLE
            radioQuickEmpty?.isVisible = false
            if (currentRadio() == null) now.cover.artwork.isVisible = !now.cover.lyricsList.isVisible
            return
        }
        if (radioQuickGrid == null) {
            radioQuickAdapter = RadioButtonAdapter(contentScope, ::playRadio, ::radioOptions, model::reorderRadioPins,
                compact = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
            radioQuickGrid = RadioPinList(this).apply {
                id = R.id.radioQuickStations
                contentDescription = getString(R.string.radio_quick_stations)
                RadioButtonAdapter.configureGrid(this)
                adapter = radioQuickAdapter
                visibility = View.INVISIBLE
                now.cover.mediaArea.addView(this, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                focusMemory.watch(this)
            }
            radioQuickEmpty = TextView(this).apply {
                setText(R.string.radio_pins_empty)
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                setPadding(dp(16), dp(16), dp(16), dp(16))
                now.cover.mediaArea.addView(this, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
        }
        radioQuickGrid?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        radioQuickAdapter?.configureLogo(model.app.radio.options.value.buttonLogo, model.app.radio.options.value.stationLogos)
        radioQuickEmpty?.apply {
            setText(if (model.app.radioCatalog.state.value.ready) R.string.radio_pins_empty else R.string.loading)
            isVisible = visible && model.radioQuickPage.value.isEmpty()
        }
        if (visible) now.cover.artwork.isVisible = false
        val display = model.app.displaySettings.options.value
        radioQuickAdapter?.apply {
            configureBlocks(display.controlBlocks, display.blockOutline)
            selection(currentRadio()?.id, model.app.radio.options.value.pins, radioPinColor(), primaryControlHighlight())
            submitList(model.radioQuickPage.value)
        }
    }

    private fun radioPinColor(): Int = model.app.radio.options.value.pinColor?.or(Color.BLACK)
        ?: ContextCompat.getColor(this, R.color.radio_pinned)

    private fun radioPanelRequested(): Boolean =
        RadioPresentation.radioControls(model.app.radio.options.value.active, model.app.radioPreparing.value,
            RadioProgram.isRadio(controller?.currentMediaItem?.mediaId))

    private fun currentRadio(): RadioProgram? {
        val radio = model.app.radio.options.value
        val id = RadioPresentation.stationId(controller?.currentMediaItem?.mediaId, radio.stationId,
            radio.active, controller != null, model.app.playbackReady.value) ?: return null
        return model.app.radio.currentStation?.takeIf { it.id == id } ?: model.app.radioCatalog.find(id)
    }

    private fun playRadio(station: RadioProgram) = playRadio(station, null)

    private fun playRadio(station: RadioProgram, sourceIndex: Int?) {
        val openPlayer = model.selectedScreen.value == R.id.nav_radio &&
            model.app.radio.options.value.openPlayerOnSelection
        radioCommand(RadioCommands.play, Bundle().apply {
            putString(RadioCommands.STATION, station.id)
            if (sourceIndex != null) {
                putInt(RadioCommands.SOURCE, sourceIndex)
                putString(RadioCommands.SOURCE_URI, station.sources[sourceIndex])
            }
        }) {
            if (openPlayer && model.selectedScreen.value == R.id.nav_radio) model.screen(R.id.nav_player)
        }
    }

    private fun radioDetails(station: RadioProgram) {
        AccentTheme.dialog(this).setTitle(station.title(this))
            .setMessage(getString(R.string.radio_program_details, station.id, station.sources.joinToString("\n"),
                getString(if (station.official) R.string.radio_address_official else R.string.radio_address_direct)))
            .setPositiveButton(R.string.close, null).show()
    }

    private fun renderRadioImport(preview: PreparedRadioImport?) {
        if (preview == null) {
            radioImportDialog?.dismiss()
            radioImportDialog = null
            shownRadioImport = null
            return
        }
        if (shownRadioImport === preview && radioImportDialog?.isShowing == true) return
        radioImportDialog?.dismiss()
        shownRadioImport = preview
        val messages = mutableListOf(getString(R.string.radio_import_preview,
            preview.parsed.entries.size, preview.currentCount, preview.parsed.invalid))
        val http = preview.parsed.entries.count { it.uri.startsWith("http:", true) }
        if (http > 0) messages.add(getString(R.string.radio_import_http_warning, http))
        if (preview.parsed.entries.isEmpty()) messages.add(getString(R.string.radio_import_no_stations))
        messages.addAll(preview.parsed.issues.map { getString(R.string.radio_import_issue, it.line, getString(it.message)) })
        radioImportDialog = AccentTheme.dialog(this).setTitle(R.string.radio_import)
            .setMessage(messages.joinToString("\n\n"))
            .setNegativeButton(R.string.cancel) { _, _ -> model.dismissRadioImport() }
            .setOnCancelListener { model.dismissRadioImport() }
            .apply {
                if (preview.parsed.entries.isNotEmpty())
                    setPositiveButton(R.string.radio_import_apply) { _, _ ->
                        radioImportDialog = AccentTheme.dialog(this@MainActivity).setTitle(R.string.radio_import_addresses)
                            .setMessage(getString(R.string.radio_import_modes_help) +
                                if (model.app.radioCatalog.state.value.subscriptions.isEmpty()) ""
                                else "\n\n" + getString(R.string.radio_subscription_import_replace))
                            .setNegativeButton(R.string.cancel) { _, _ -> model.dismissRadioImport() }
                            .setOnCancelListener { model.dismissRadioImport() }
                            .setNeutralButton(R.string.radio_import_append) { _, _ -> model.importRadioList(RadioImportMode.APPEND) }
                            .setPositiveButton(R.string.radio_import_replace) { _, _ -> model.importRadioList(RadioImportMode.REPLACE_ALL) }.show()
                    }
            }.show()
    }

    private fun radioCommand(command: SessionCommand, arguments: Bundle = Bundle.EMPTY,
        completed: () -> Unit = {}): ListenableFuture<SessionResult>? {
        var issued: ListenableFuture<SessionResult>? = null
        withController { player ->
            val future = player.sendCustomCommand(command, arguments)
            issued = future
            future.addListener({
                if (controller !== player) return@addListener
                try {
                    val result = future.get()
                    if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                        model.app.report(getString(R.string.radio_command_failed, result.resultCode.toString()))
                        restoringMusic = false
                    } else lifecycleScope.launch {
                        // The command result can arrive before the restored controller timeline.
                        if (command == RadioCommands.music && RadioProgram.isRadio(player.currentMediaItem?.mediaId)) {
                            val ready = CompletableDeferred<Unit>()
                            val listener = object : Player.Listener {
                                override fun onEvents(player: Player, events: Player.Events) {
                                    if (!RadioProgram.isRadio(player.currentMediaItem?.mediaId)) ready.complete(Unit)
                                }
                            }
                            player.addListener(listener)
                            val caughtUp = try {
                                withTimeoutOrNull(5_000) { ready.await(); true } == true
                            } finally { player.removeListener(listener) }
                            if (controller !== player) return@launch
                            if (!caughtUp) {
                                restoringMusic = false
                                model.app.report(getString(R.string.radio_command_failed, getString(R.string.connection_closed)))
                                return@launch
                            }
                        }
                        if (controller !== player) return@launch
                        if (command == RadioCommands.music && model.app.radio.options.value.active) {
                            restoringMusic = false
                            model.app.report(getString(R.string.radio_command_failed, getString(R.string.radio_music_saved)))
                            return@launch
                        }
                        completed()
                        updatePlayer()
                        updateQueue(force = true)
                    }
                } catch (error: ExecutionException) {
                    restoringMusic = false
                    model.app.report(getString(R.string.radio_command_failed, error.cause?.localizedMessage.orEmpty()))
                } catch (_: CancellationException) {
                    restoringMusic = false
                } catch (error: InterruptedException) {
                    restoringMusic = false
                    Thread.currentThread().interrupt()
                    model.app.report(getString(R.string.radio_command_failed, error.localizedMessage.orEmpty()))
                }
            }, ContextCompat.getMainExecutor(this))
        }
        return issued
    }

    private fun withMusicController(action: (MediaController) -> Unit) {
        if (model.app.radio.options.value.active || currentRadio() != null) {
            radioCommand(RadioCommands.music) { withController(action) }
        } else withController(action)
    }

    private fun buildPlaylists() {
        playlistPanel = ListPanel(this, compactChrome)
        playlistPanel.list.id = R.id.playlistList
        playlistBack = playlistPanel.action(R.string.back_playlists) { model.playlist(null) }
        playlistPlay = playlistPanel.action(R.string.play_all) {
            model.selectedPlaylist.value?.let {
                model.requestPlay(playlistTrackRows, collection = PlaybackCollection.playlist(it))
            }
        }
        playlistRestore = playlistPanel.action(R.string.restore_position) {
            model.selectedPlaylist.value?.let { model.restorePosition(PlaybackCollection.playlist(it)) }
        }.apply { id = R.id.playlistRestorePosition }
        playlistAdd = playlistPanel.action(R.string.add_songs) { addSongsDialog() }
        playlistUpdate = playlistPanel.action(R.string.update_playlist) {
            model.library.value.playlists.firstOrNull { it.id == model.selectedPlaylist.value }?.let(::playlistMenu)
        }.apply { id = R.id.playlistUpdate }
        playlistPanel.actionRow(
            playlistPanel.action(R.string.create_playlist) { newPlaylistDialog() },
            playlistPanel.action(R.string.save_folder_playlist) { saveFoldersDialog() }.apply { id = R.id.playlistSaveFolders },
            playlistBack, playlistPlay, playlistRestore, playlistAdd, playlistUpdate,
        )
        playlistAdapter = MusicRowAdapter(
            compact = compactChrome,
            onClick = { row ->
                if (model.selectedPlaylist.value == null) model.playlist(row.key)
                else {
                    val index = playlistTrackRows.indexOfFirst { it.id == row.key }
                    if (index >= 0) model.requestPlay(playlistTrackRows, index,
                        collection = PlaybackCollection.playlist(checkNotNull(model.selectedPlaylist.value)))
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
        settingsButtons = BlockButtonStyler(settings.root)
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
        val appearance = model.app.displaySettings.options.value
        settingsButtons.apply(appearance.controlBlocks, appearance.blockOutline)
        renderCategoryDividers(appearance.controlBlocks, appearance.blockOutline)
        ViewCompat.setAccessibilityHeading(settings.settingsHeading, true)
        settings.settingsScroll.isSaveEnabled = false
        settings.settingsScroll.setOnScrollChangeListener { _, _, y, _, _ ->
            if (!restoringSettingsScroll) shownSettingsCategory?.let { model.settingsScroll(it, y) }
        }
        showSettingsCategory(model.settingsCategory.value)
        settings.settingsLanguage.setOnClickListener { languageDialog() }
        settings.settingsAutoPlay.setOnCheckedChangeListener { _, checked ->
            model.app.displaySettings.update { it.copy(autoPlayOnLaunch = checked) }
        }
        settings.settingsKeepScreenOn.setOnCheckedChangeListener { _, checked ->
            model.app.displaySettings.update { it.copy(keepScreenOn = checked) }
        }
        settings.settingsAppLogo.setOnClickListener {
            AppearanceChoices.show(this, R.string.appearance_logo, listOf(
                AppearanceChoices.Choice(R.id.appearanceMusic, R.string.appearance_logo_music, R.drawable.ic_launcher),
                AppearanceChoices.Choice(R.id.appearanceRadio, R.string.appearance_logo_radio, R.drawable.ic_launcher_radio),
            ), model.app.displaySettings.options.value.appLogo.ordinal) { index ->
                model.setAppLogo(com.macarmusic.app.model.AppLogo.entries[index])
            }
        }
        settings.settingsButtonIcons.setOnClickListener {
            AppearanceChoices.show(this, R.string.appearance_icons, AppearanceChoices.buttonThemes,
                model.app.displaySettings.options.value.buttonIcons.ordinal) { index ->
                model.app.displaySettings.update { it.copy(buttonIcons = com.macarmusic.app.model.ButtonIconStyle.entries[index]) }
            }
        }
        settings.settingsSystemInfo.setOnClickListener { systemInformationDialog() }
        configureRadioSettings()
        settings.settingsBackup.setOnClickListener { launchBackupExport() }
        settings.settingsRestore.setOnClickListener { launchBackupImport() }
        settings.settingsWidgets.setOnClickListener { homeWidgetsDialog() }
        settings.settingsDefaultPlayer.setOnClickListener { defaultPlayerDialog() }
        settings.widgetTransparency.setOnSeekBarChangeListener(backgroundSlider(
            label = { settings.widgetTransparencyValue.text = getString(R.string.widget_transparency_value, it) },
            save = { value -> model.app.displaySettings.update { it.copy(widgetTransparency = value) } },
        ))
        settings.miniPlayerTransparency.setOnSeekBarChangeListener(backgroundSlider(
            label = { settings.miniPlayerTransparencyValue.text = getString(R.string.mini_player_transparency_value, it) },
            save = { value -> model.app.displaySettings.update { it.copy(miniPlayerTransparency = value) } },
        ))
        settings.settingsTheme.setOnClickListener { themeDialog() }
        settings.settingsAccent.setOnClickListener { accentDialog() }
        settings.settingsPrimaryControlColor.setOnClickListener {
            fun save(color: Int?) = model.app.displaySettings.update {
                it.copy(primaryControlColor = color, primaryControlHighlightEnabled = true)
            }
            ColorSwatchDialog.show(this, getString(R.string.primary_control_color),
                primaryControlColor() and 0xffffff, reset = { save(null) },
                none = { model.app.displaySettings.update { it.copy(primaryControlHighlightEnabled = false) } },
                initiallyNone = !model.app.displaySettings.options.value.primaryControlHighlightEnabled) { save(it) }
        }
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
        settings.settingsLyricKaraoke.setOnCheckedChangeListener { _, checked ->
            model.app.displaySettings.update { it.copy(lyricAppearance = it.lyricAppearance.copy(karaoke = checked)) }
        }
        settings.lyricKaraokeColor.setOnClickListener {
            fun save(color: Int) = model.app.displaySettings.update {
                it.copy(lyricAppearance = it.lyricAppearance.copy(karaokeColor = color))
            }
            ColorSwatchDialog.show(this, getString(R.string.lyric_karaoke_color),
                model.app.displaySettings.options.value.lyricAppearance.karaokeColor,
                reset = { save(LyricAppearance.DEFAULT_KARAOKE_COLOR) }) { save(it) }
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
        settings.lyricsCacheClear.setOnClickListener {
            val cache = model.app.lyrics.cache.info.value
            val location = if (cache.invalidLocation) getString(R.string.lyrics_cache_unavailable)
                else cache.folder?.name ?: getString(R.string.lyrics_cache_internal_locations, cache.internalPath, cache.downloadPath)
            AccentTheme.dialog(this).setTitle(R.string.lyrics_cache_clear)
                .setMessage(getString(R.string.lyrics_cache_clear_confirm, location))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.lyrics_cache_clear) { _, _ -> model.clearLyricCache() }.show()
        }
        settings.lyricsCacheMove.setOnClickListener {
            AccentTheme.dialog(this).setTitle(R.string.lyrics_cache_move)
                .setMessage(R.string.lyrics_cache_move_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.lyrics_cache_choose) { _, _ -> launchLyricCachePicker() }.show()
        }
        settings.lyricsCacheInternal.setOnClickListener {
            AccentTheme.dialog(this).setTitle(R.string.lyrics_cache_internal)
                .setMessage(R.string.lyrics_cache_internal_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.lyrics_cache_internal) { _, _ -> model.useInternalLyricCache() }.show()
        }
        settings.lyricsCacheInspect.setOnClickListener { model.inspectLyricCache(reportResult = true) }
        val expiry = model.app.displaySettings.options.value
        choices(settings.lyricsCacheInternalExpiry, LyricCacheExpiry.entries, expiry.internalLyricExpiry,
            ::lyricExpiryLabel) { value ->
            if (value != model.app.displaySettings.options.value.internalLyricExpiry) {
                model.app.displaySettings.update { it.copy(internalLyricExpiry = value) }
                if (model.app.lyrics.cache.info.value.internal) model.inspectLyricCache()
            }
        }
        choices(settings.lyricsCachePortableExpiry, LyricCacheExpiry.entries, expiry.portableLyricExpiry,
            ::lyricExpiryLabel) { value ->
            if (value != model.app.displaySettings.options.value.portableLyricExpiry) {
                model.app.displaySettings.update { it.copy(portableLyricExpiry = value) }
                if (!model.app.lyrics.cache.info.value.internal) model.inspectLyricCache()
            }
        }
        contentScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.app.lyrics.cache.info.collect { renderLyricCache() }
            }
        }
        renderLyricCache()
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
        choices(settings.settingsNoticeDuration, DisplayOptions.NOTICE_DURATIONS, display.noticeDurationSeconds,
            { getString(R.string.notice_duration_seconds, it) }) { value ->
            model.app.displaySettings.update { it.copy(noticeDurationSeconds = value) }
        }
        choices(settings.lyricsLandscapePosition, LyricSide.entries, display.landscapeLyrics,
            { getString(if (it == LyricSide.START) R.string.position_left else R.string.position_right) }) { side ->
            model.app.displaySettings.update { it.copy(landscapeLyrics = side) }
        }
        choices(settings.lyricsPortraitPosition, LyricSide.entries, display.portraitLyrics,
            { getString(if (it == LyricSide.START) R.string.navigation_top else R.string.navigation_bottom) }) { side ->
            model.app.displaySettings.update { it.copy(portraitLyrics = side) }
        }
        choices(settings.settingsBlockStyle, BlockStyle.selectable, display.blockOutline.style,
            { getString(blockStyleLabel(it)) }) { value ->
            model.app.displaySettings.update { it.copy(blockOutline = it.blockOutline.copy(style = value)) }
        }
        choices(settings.settingsNavigationPosition, NavigationPosition.entries, display.navigationPosition,
            { getString(navigationLabel(it)) }) { value ->
            model.app.displaySettings.update { it.copy(navigationPosition = value) }
        }
        settings.settingsShowLibrary.setOnCheckedChangeListener { _, checked ->
            model.app.displaySettings.update { it.copy(showLibrary = checked) }
        }
        settings.settingsShowQueue.setOnCheckedChangeListener { _, checked ->
            model.app.displaySettings.update { it.copy(showQueue = checked) }
        }
        settings.settingsShowPlaylists.setOnCheckedChangeListener { _, checked ->
            model.app.displaySettings.update { it.copy(showPlaylists = checked) }
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
        settings.settingsRemoveAllFolders.setOnClickListener { removeAllFoldersDialog() }
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
        choices(fields.spectrumOrigin, SpectrumOrigin.entries, spectrum.origin,
            { getString(when (it) {
                SpectrumOrigin.BOTTOM -> R.string.navigation_bottom
                SpectrumOrigin.LEFT -> R.string.position_left
                SpectrumOrigin.RIGHT -> R.string.position_right
                SpectrumOrigin.TOP -> R.string.navigation_top
            }) }) { value ->
            model.app.displaySettings.update { it.copy(spectrum = it.spectrum.copy(origin = value)) }
        }
        choices(fields.spectrumStyle, SpectrumStyle.entries, spectrum.style,
            { getString(when (it) {
                SpectrumStyle.BARS -> R.string.spectrum_bars
                SpectrumStyle.LINE -> R.string.spectrum_line
                SpectrumStyle.OFF -> R.string.spectrum_off
            }) }) { value ->
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
            changeSpectrumColors { SpectrumColors.FIRE }
        }
        choices(fields.spectrumBands, SpectrumOptions.BAND_COUNTS, spectrum.bands,
            { getString(R.string.spectrum_band_count, it) }) { value ->
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
        val colors = spectrum.colors
        val color = listOf(colors.low, colors.middle, colors.high)[index]
        val label = listOf(R.string.spectrum_color_low, R.string.spectrum_color_middle, R.string.spectrum_color_high)[index]
        ColorSwatchDialog.show(this, getString(label), color) { selected ->
            changeSpectrumColors {
                when (index) {
                    0 -> it.copy(low = selected)
                    1 -> it.copy(middle = selected)
                    else -> it.copy(high = selected)
                }
            }
        }
    }

    private fun blockStyleLabel(style: BlockStyle) = when (style) {
        BlockStyle.SOLID, BlockStyle.NONE -> R.string.block_solid
        BlockStyle.DASHED -> R.string.block_dashed
        BlockStyle.ARC -> R.string.block_arc
        BlockStyle.CHARACTERS -> R.string.block_characters
        BlockStyle.DASHED_ARC -> R.string.block_dashed_arc
    }

    private fun accentDialog() {
        val swatches = AccentTheme.swatches(this)
        val options = model.app.displaySettings.options.value
        val selected = options.customAccentColor ?: swatches.getValue(options.themeAccent)
        ColorSwatchDialog.show(this, getString(R.string.theme_accent), selected,
            reset = { model.app.displaySettings.update {
                it.copy(themeAccent = ThemeAccent.DEFAULT, customAccentColor = null)
            } }) { color ->
            model.app.displaySettings.update {
                it.copy(themeAccent = ThemeAccent.DEFAULT, customAccentColor = color)
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
            color and 0xffffff, reset = { save(null) }) { selected -> save(selected) }
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
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
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

    private fun changeSpectrumColors(edit: (SpectrumColors) -> SpectrumColors) {
        model.app.displaySettings.update { display ->
            val spectrum = display.spectrum
            display.copy(spectrum = spectrum.copy(colors = edit(spectrum.colors)))
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
        renderRadioSettings()
        settings.settingsBackup.isEnabled = !model.backupBusy.value && model.scan.value == null && !model.importingBackground.value &&
            !model.radioImportBusy.value && model.subscriptionProgress.value == null && model.logoProgress.value == null
        settings.settingsRestore.isEnabled = settings.settingsBackup.isEnabled
        settings.settingsBackupStatus.isVisible = model.backupBusy.value
        settings.settingsTheme.text = getString(R.string.setting_value, getString(R.string.theme), getString(theme))
        val display = model.app.displaySettings.options.value
        settings.settingsAppLogo.isEnabled = !model.backupBusy.value
        settings.settingsButtonIcons.isEnabled = !model.backupBusy.value
        settings.settingsAppLogo.text = getString(R.string.setting_value, getString(R.string.appearance_logo),
            getString(if (display.appLogo == com.macarmusic.app.model.AppLogo.MUSIC)
                R.string.appearance_logo_music else R.string.appearance_logo_radio))
        settings.settingsButtonIcons.text = getString(R.string.setting_value, getString(R.string.appearance_icons),
            getString(AppearanceChoices.buttonThemes[display.buttonIcons.ordinal].label))
        if (!settings.widgetTransparency.isPressed) {
            settings.widgetTransparency.progress = display.widgetTransparency
            settings.widgetTransparencyValue.text = getString(R.string.widget_transparency_value, display.widgetTransparency)
        }
        if (!settings.miniPlayerTransparency.isPressed) {
            settings.miniPlayerTransparency.progress = display.effectiveMiniPlayerTransparency
            settings.miniPlayerTransparencyValue.text = getString(
                R.string.mini_player_transparency_value, display.effectiveMiniPlayerTransparency)
        }
        settings.settingsKeepScreenOn.isChecked = display.keepScreenOn
        settings.settingsNoticeDuration.setSelection(
            DisplayOptions.NOTICE_DURATIONS.indexOf(display.noticeDurationSeconds), false)
        colorButton(settings.settingsAccent, R.string.theme_accent,
            AccentTheme.color(binding.root, com.google.android.material.R.attr.colorPrimary))
        colorButton(settings.settingsPrimaryControlColor, R.string.primary_control_color, primaryControlColor())
        if (!display.primaryControlHighlightEnabled) {
            settings.settingsPrimaryControlColor.text = getString(R.string.primary_control_color_none)
            settings.settingsPrimaryControlColor.icon = null
        }
        settings.settingsControlBlocks.isChecked = display.controlBlocks
        settings.blockStyleControls.isVisible = display.controlBlocks
        settings.settingsBlockStyle.setSelection(BlockStyle.selectable.indexOf(display.blockOutline.style), false)
        settings.settingsBlockCharacter.isVisible = display.blockOutline.style == BlockStyle.CHARACTERS
        settings.settingsBlockCharacter.text = getString(R.string.setting_value,
            getString(R.string.block_character), display.blockOutline.character)
        settings.blockArcControls.isVisible = display.blockOutline.hasArc
        if (!settings.blockArc.isPressed) {
            settings.blockArc.progress = display.blockOutline.arcDp
            settings.blockArcValue.text = getString(R.string.block_arc_value, display.blockOutline.arcDp)
        }
        settings.settingsBlockShadow.isChecked = display.blockOutline.shadow
        settings.settingsBlockShadow.isVisible = display.blockOutline.hasBorder
        settings.settingsLyrics.isChecked = display.lyricsEnabled
        settings.settingsOnlineLyrics.isChecked = display.onlineLyrics
        settings.settingsOnlineLyrics.isEnabled = display.lyricsEnabled
        settings.settingsLyricDragSeek.isChecked = display.lyricsDragSeek
        settings.settingsLyricDragSeek.isEnabled = display.lyricsEnabled
        settings.settingsLyricSmoothScroll.isChecked = display.lyricsSmoothScroll
        settings.settingsLyricSmoothScroll.isEnabled = display.lyricsEnabled
        settings.settingsLyricKaraoke.isChecked = display.lyricAppearance.karaoke
        settings.settingsLyricKaraoke.isEnabled = display.lyricsEnabled
        colorButton(settings.lyricKaraokeColor, R.string.lyric_karaoke_color, display.lyricAppearance.karaokeColor)
        settings.lyricsCacheInternalExpiry.setSelection(display.internalLyricExpiry.ordinal, false)
        settings.lyricsCachePortableExpiry.setSelection(display.portableLyricExpiry.ordinal, false)
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
        settings.settingsBackgroundPanel.isVisible = display.customTheme
        settings.settingsBackgroundImage.isEnabled = !model.importingBackground.value && !model.backupBusy.value
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
        settings.settingsAutoPlay.isChecked = display.autoPlayOnLaunch
        settings.settingsAutoPlay.isEnabled = !model.backupBusy.value
        settings.settingsShowLibrary.isChecked = display.showLibrary
        settings.settingsShowQueue.isChecked = display.showQueue
        settings.settingsShowPlaylists.isChecked = display.showPlaylists
        settings.settingsShowLibrary.isEnabled = !model.backupBusy.value
        settings.settingsShowQueue.isEnabled = !model.backupBusy.value
        settings.settingsShowPlaylists.isEnabled = !model.backupBusy.value
        settings.settingsSpectrum.isChecked = display.spectrum.enabled
        settings.spectrumSettings.apply {
            root.isVisible = display.spectrum.enabled
            spectrumPosition.setSelection(display.spectrum.position.ordinal, false)
            spectrumOrigin.setSelection(display.spectrum.origin.ordinal, false)
            spectrumStyle.setSelection(display.spectrum.style.ordinal, false)
            spectrumPalette.setSelection(display.spectrum.palette.ordinal, false)
            spectrumFireStyle.setSelection(display.spectrum.rendering.ordinal, false)
            fireStyleControls.isVisible = display.spectrum.style == SpectrumStyle.BARS
            spectrumColorControls.isVisible = display.spectrum.palette != SpectrumPalette.THEME
            val colors = display.spectrum.colors
            val labels = listOf(R.string.spectrum_color_low, R.string.spectrum_color_middle, R.string.spectrum_color_high)
            val values = listOf(colors.low, colors.middle, colors.high)
            listOf(spectrumColorLow, spectrumColorMiddle, spectrumColorHigh).forEachIndexed { index, button ->
                colorButton(button, labels[index], values[index])
            }
            spectrumBands.setSelection(SpectrumOptions.BAND_COUNTS.indexOf(display.spectrum.bands), false)
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
        model.metadataProgress.value?.let {
            settings.settingsFolderSummary.append("\n" + getString(R.string.metadata_background_progress, it.completed, it.total))
        }
        settings.settingsFolders.isEnabled = model.repository.isReadable && model.scan.value == null
        settings.settingsPinFolders.isEnabled = model.repository.isReadable && sources.isNotEmpty()
        settings.settingsAddFolder.isEnabled = model.repository.isReadable && model.scan.value == null
        settings.settingsDeviceMusic.isEnabled = model.repository.isReadable && model.scan.value == null
        settings.settingsPlayFolders.isEnabled = selected.isNotEmpty() && model.scan.value == null
        settings.settingsRemoveAllFolders.isEnabled = model.repository.isReadable && sources.isNotEmpty() &&
            model.scan.value == null && !model.backupBusy.value
    }

    private fun lyricExpiryLabel(value: LyricCacheExpiry): String =
        if (value == LyricCacheExpiry.NEVER) getString(R.string.lyrics_cache_expiry_never)
        else resources.getQuantityString(R.plurals.lyrics_cache_expiry_days, value.days, value.days)

    private fun renderLyricCache() {
        if (!::settings.isInitialized) return
        val state = model.app.lyrics.cache.info.value
        val busy = state.busy || model.lyricCacheBusy.value || model.lyricDownloadsBusy
        val location = when {
            state.invalidLocation -> getString(R.string.lyrics_cache_unavailable)
            state.folder != null -> state.folder.name
            else -> getString(R.string.lyrics_cache_internal_locations, state.internalPath, state.downloadPath)
        }
        val size = state.size?.let {
            "\n" + getString(R.string.lyrics_cache_size, it.files,
                android.text.format.Formatter.formatFileSize(this, it.bytes))
        }.orEmpty()
        val status = if (state.unavailable) "\n\n" + getString(R.string.lyrics_cache_unavailable) else ""
        settings.lyricsCacheLocation.text = getString(R.string.lyrics_cache_location, location) + size + status
        settings.lyricsCacheClear.isEnabled = !busy && !state.invalidLocation
        settings.lyricsCacheMove.isEnabled = !busy
        settings.lyricsCacheInternal.isEnabled = !busy && !state.internal
        settings.lyricsCacheInspect.isEnabled = !busy
        settings.lyricsCacheInternalExpiry.isEnabled = !busy
        settings.lyricsCachePortableExpiry.isEnabled = !busy
        settings.lyricsCacheProgress.isVisible = busy
    }

    private fun showSettingsCategory(category: SettingsCategory) {
        if (!::settings.isInitialized) return
        if (shownSettingsCategory == category) return
        focusMemory.pause()
        shownSettingsCategory?.let {
            if (!restoringSettingsScroll) model.settingsScroll(it, settings.settingsScroll.scrollY)
        }
        shownSettingsCategory = category
        val categoryPanel = settings.root.findViewById<View>(category.panelId)
        focusMemory.scope(categoryPanel, "settings:${category.name}")
        restoringSettingsScroll = true
        SettingsCategory.pages.forEach {
            settings.root.findViewById<View>(it.panelId).isVisible = it == category
            settings.root.findViewById<MaterialButton>(it.buttonId).isChecked = it == category
        }
        settings.settingsHeading.setText(category.label)
        settings.settingsHeading.isVisible = category != SettingsCategory.ABOUT
        if (category == SettingsCategory.ABOUT) {
            AboutContent.populate(settings.settingsAboutPanel)
            val appearance = model.app.displaySettings.options.value
            AboutContent.configureBorder(settings.settingsAboutPanel, appearance.controlBlocks, appearance.blockOutline)
        }
        if (category == SettingsCategory.LYRICS && !model.lyricCacheBusy.value &&
            !model.backupBusy.value && model.scan.value == null && !model.lyricDownloadsBusy) {
            val display = model.app.displaySettings.options.value
            val expiry = if (model.app.lyrics.cache.info.value.internal) display.internalLyricExpiry else display.portableLyricExpiry
            if (expiry != LyricCacheExpiry.NEVER) model.inspectLyricCache()
        }
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
        if (visibleScreen === settings.root) focusMemory.activate(categoryPanel)
    }

    private fun showScreen(id: Int) {
        if (!model.isScreenVisible(id)) {
            model.screen(R.id.nav_player)
            return
        }
        if (!screenBuilt(id)) {
            if (pendingScreenId == id) return
            screenLoadJob?.cancel()
            pendingScreenId = id
            loadingScreen.text = getString(R.string.screen_loading, getString(screenLabel(id)))
            displayScreen(id, loadingScreen, loading = true)
            screenLoadJob = contentScope.launch {
                loadingScreen.awaitDraw()
                if (model.selectedScreen.value == id) {
                    val target = screenView(id)
                    pendingScreenId = null
                    displayScreen(id, target)
                }
            }
            return
        }
        screenLoadJob?.cancel()
        screenLoadJob = null
        pendingScreenId = null
        displayScreen(id, screenView(id))
    }

    private fun displayScreen(id: Int, target: View, loading: Boolean = false) {
        val changed = visibleScreen !== target
        if (changed) {
            focusMemory.pause()
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
            if (!loading && previous != null && previous.id != R.id.screenLoading && ::settings.isInitialized &&
                (target === settings.root || previous === settings.root) &&
                transition != TransitionStyle.NONE && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) {
                screenAnimator = ViewTransitions.entrance(target, transition, 300).also { it.start() }
            }
            when (id.takeUnless { loading }) {
                R.id.nav_library -> renderLibrary()
                R.id.nav_folders -> renderFolders()
                R.id.nav_queue -> { queueScrollPending = !focusMemory.hasPosition(queuePanel); updateQueue(force = true) }
                R.id.nav_playlists -> renderPlaylists()
                R.id.nav_radio -> {
                    if (!focusMemory.hasPosition(radioPanel)) currentRadio()?.let(model::revealPlayingRadio)
                    radioPanel.findViewById<TextInputEditText>(R.id.radioSearch)?.let {
                        if (it.text?.toString() != model.radioQuery.value) it.setText(model.radioQuery.value)
                    }
                    renderRadio()
                }
                R.id.action_settings -> {
                    renderSettings()
                    showSettingsCategory(model.settingsCategory.value)
                }
            }
        }
        val inSettings = id == R.id.action_settings
        if (target !== now.root && !binding.miniPlayer.singleRow) {
            binding.miniPlayer.singleRow = true
            appliedControlBlocks = null
            val display = model.app.displaySettings.options.value
            renderControlBlocks(display.controlBlocks, display.blockOutline)
        }
        binding.navigation.isVisible = true
        if (!inSettings && binding.navigation.selectedItemId != id) binding.navigation.selectedItemId = id
        binding.toolbar.setTitle(if (inSettings) R.string.settings else R.string.app_name)
        if (loading) binding.toolbar.setTitle(screenLabel(id))
        if (target is ListPanel) updateCompactHeading(target)
        binding.toolbar.menu.findItem(R.id.action_settings).isVisible = !inSettings
        if (inSettings) {
            binding.toolbar.navigationIcon = PlayerIcons.drawable(this, R.drawable.ic_back)
            binding.toolbar.clearNavigationIconTint()
            binding.toolbar.setNavigationContentDescription(R.string.back)
        } else binding.toolbar.navigationIcon = null
        binding.miniPlayer.isVisible = target !== now.root
        updatePlayer()
        if (changed && target === now.root) {
            now.cover.lyricsList.post { updateLyricProgress(force = true) }
        }
        if (changed) {
            binding.toolbar.doOnLayout { it.installPlayerInputFocus() }
            focusMemory.activate(target)
        }
    }

    private fun applyNavigationVisibility() {
        binding.navigation.setScreenVisibility(model::isScreenVisible)
        if (!model.isScreenVisible(model.selectedScreen.value)) model.screen(R.id.nav_player)
    }

    private fun configureNavigation(position: NavigationPosition) {
        if (appliedNavigationPosition == position) return
        appliedNavigationPosition = position
        val side = position == NavigationPosition.LEFT || position == NavigationPosition.RIGHT
        val gap = resources.getDimensionPixelSize(R.dimen.player_gap)
        binding.navigation.configure(side)
        binding.navigation.layoutParams = RelativeLayout.LayoutParams(
            if (side) RelativeLayout.LayoutParams.WRAP_CONTENT else RelativeLayout.LayoutParams.MATCH_PARENT,
            if (side) RelativeLayout.LayoutParams.MATCH_PARENT else RelativeLayout.LayoutParams.WRAP_CONTENT,
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

    private fun renderAppearance(display: com.macarmusic.app.data.DisplayOptions) {
        val appearance = display.appLogo to display.buttonIcons
        if (appliedAppearance == appearance) return
        appliedAppearance = appearance
        binding.toolbar.logo = androidx.appcompat.content.res.AppCompatResources.getDrawable(this, Branding.icon(display.appLogo))
        if (::settings.isInitialized) settings.root.findViewById<android.widget.ImageView>(R.id.aboutLogo)
            ?.setImageResource(Branding.icon(display.appLogo))
        now.controls.spectrum.setLogo(display.appLogo)
        binding.navigation.refreshIcons()
        PlayerIcons.refresh(binding.root)
        binding.toolbar.menu.findItem(R.id.action_settings)?.icon = PlayerIcons.drawable(this, R.drawable.ic_settings)
        if (binding.toolbar.navigationIcon != null) {
            binding.toolbar.navigationIcon = PlayerIcons.drawable(this, R.drawable.ic_back)
            binding.toolbar.clearNavigationIconTint()
        }
        artworkUri = null
        updateArtwork(currentTrack()?.uri)
        updatePlayer()
    }

    private fun configureCompactChrome() {
        if (!compactChrome) return
        binding.noticeHost.text.maxLines = 1
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
        val mode = if (options.style == SpectrumStyle.OFF) getString(R.string.spectrum_off)
            else if (options.style == SpectrumStyle.LINE) getString(R.string.spectrum_line)
            else getString(R.string.spectrum_mode, getString(when (options.palette) {
                SpectrumPalette.CLASSIC -> R.string.spectrum_classic
                SpectrumPalette.FIRE -> R.string.spectrum_fire
                SpectrumPalette.THEME -> R.string.spectrum_theme
            }), getString(if (options.rendering == FireStyle.LED) R.string.spectrum_led else R.string.spectrum_smooth))
        view.contentDescription = getString(R.string.spectrum_tap_cycle, mode)
        val visible = options.enabled && options.style != SpectrumStyle.OFF && visibleScreen === now.root &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (optionalContentStarted) {
            model.app.spectrum.setRefreshRate(options.refreshRate)
            model.app.spectrum.setVisible(this, visible)
        }
        view.setPlaybackActive(optionalContentStarted && visible && controller?.isPlaying == true)
    }

    private fun libraryModeLabel(mode: LibraryMode): Int = when (mode) {
        LibraryMode.SONGS -> R.string.library_songs
        LibraryMode.ARTISTS -> R.string.library_artists
        LibraryMode.ALBUMS -> R.string.library_albums
    }

    private fun renderLibrary() {
        if (!::libraryPanel.isInitialized || visibleScreen !== libraryPanel) return
        val location = model.libraryPage.value.location
        val changedLocation = focusMemory.changeScope(libraryPanel, "library:${location.mode}:${location.group.orEmpty()}")
        favoriteFilter.setText(if (model.favoritesOnly.value) R.string.all_tracks else R.string.favorites_only)
        val page = model.libraryPage.value
        val tracks = page.tracks
        val selecting = model.librarySelecting.value
        val selected = model.librarySelection.value.toSet()
        libraryActions.isVisible = !selecting
        librarySelectionActions.isVisible = selecting
        libraryAddSelected.isEnabled = selected.isNotEmpty()
        librarySelectAll.isEnabled = tracks.isNotEmpty()
        librarySelectAll.setText(if (tracks.isNotEmpty() && tracks.all { it.id in selected })
            R.string.deselect_all_shown else R.string.select_all_shown)
        libraryBrowse.text = getString(R.string.setting_value, getString(R.string.browse), getString(libraryModeLabel(page.location.mode)))
        libraryBack.isVisible = page.location.group != null
        libraryPanel.findViewById<View>(R.id.librarySelection).isEnabled = !page.showingGroups
        val rows = if (page.showingGroups) page.groups.map { group ->
            val name = group.name.ifBlank {
                getString(if (page.location.mode == LibraryMode.ARTISTS) R.string.unknown_artist else R.string.unknown_album)
            }
            MusicRow(group.key, name, listOf(
                group.artist.ifBlank { getString(R.string.unknown_artist) }.takeIf { page.location.mode == LibraryMode.ALBUMS }.orEmpty(),
                resources.getQuantityString(R.plurals.track_count, group.count, group.count)).filter(String::isNotEmpty).joinToString(" · "),
                icon = if (page.location.mode == LibraryMode.ARTISTS) R.drawable.ic_note else R.drawable.ic_library)
        } else tracks.map { trackRow(it).copy(selected = if (selecting) it.id in selected else null) }
        libraryAdapter.submitList(rows)
        libraryPanel.heading.text = if (selecting) getString(R.string.selected_songs, selected.size)
            else if (page.showingGroups) getString(R.string.library_groups, getString(libraryModeLabel(page.location.mode)), rows.size)
            else if (page.selectedGroup != null) getString(R.string.library_group_tracks,
                page.selectedGroup.name.ifBlank {
                    getString(if (page.location.mode == LibraryMode.ARTISTS) R.string.unknown_artist else R.string.unknown_album)
                }, resources.getQuantityString(R.plurals.track_count, tracks.size, tracks.size))
            else resources.getQuantityString(R.plurals.track_count, tracks.size, tracks.size)
        libraryPlay.isEnabled = tracks.isNotEmpty()
        val message = when {
            !model.repository.loaded.value -> R.string.loading
            model.library.value.tracks.isEmpty() -> R.string.library_empty
            else -> R.string.search_empty
        }
        libraryPanel.empty(getString(message), rows.isEmpty())
        updateCompactHeading(libraryPanel)
        if (changedLocation) focusMemory.activate(libraryPanel)
    }

    private fun renderFolders() {
        if (!::folderPanel.isInitialized || visibleScreen !== folderPanel) return
        val state = model.browser.value
        val location = state.path.lastOrNull()
        val changedLocation = focusMemory.changeScope(folderPanel,
            "folders:${location?.let { "${it.sourceUri.length}:${it.sourceUri}${it.key.second}" }.orEmpty()}")
        val scan = model.scan.value
        val metadata = model.metadataProgress.value
        folderUp.isVisible = state.path.isNotEmpty()
        folderRestore.isVisible = state.path.isNotEmpty()
        folderRestore.isEnabled = scan == null && !state.loading
        folderPlay.setText(if (state.path.isEmpty()) R.string.play_selected_folders else R.string.play_folder)
        folderPlay.isEnabled = scan == null && !state.loading &&
            (state.path.isNotEmpty() || PlaybackScope.selectedSources(model.library.value).isNotEmpty())
        folderRescan.isEnabled = scan == null && model.library.value.sources.isNotEmpty()
        folderRescan.setText(if (state.path.isEmpty()) R.string.rescan_all else R.string.rescan)
        folderRemoveAll.isEnabled = model.repository.isReadable && scan == null && !model.backupBusy.value &&
            model.library.value.sources.isNotEmpty()
        cancelScan.isVisible = scan != null || metadata != null
        cancelScan.setText(if (scan != null) R.string.cancel_scan else R.string.metadata_background_pause)
        folderPanel.heading.text = when {
            scan != null -> getString(R.string.scan_progress, scan.folderName, scan.found)
            metadata != null -> getString(R.string.metadata_background_progress, metadata.completed, metadata.total)
            state.path.isEmpty() -> getString(R.string.folders)
            else -> state.path.mapIndexed { index, location ->
                if (index == 0) model.library.value.sources.firstOrNull { it.uri == location.sourceUri }
                    ?.let(::sourceName) ?: location.name else location.name
            }.joinToString("  /  ")
        }
        val rows = if (state.path.isEmpty()) {
            model.library.value.sources.map { source ->
                val status = when {
                    source.needsReconnect -> getString(R.string.reconnect_folder_first)
                    source.unavailable -> getString(R.string.source_unavailable)
                    MediaStoreAccess.folder(Uri.parse(source.uri)) != null -> getString(R.string.access_music)
                    LegacyStorage.owns(this, Uri.parse(source.uri)) -> getString(R.string.explorer_storage_permission)
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
        if (changedLocation) focusMemory.activate(folderPanel)
    }

    private fun renderPlaylists() {
        if (!::playlistPanel.isInitialized || visibleScreen !== playlistPanel) return
        val id = model.selectedPlaylist.value
        val changedLocation = focusMemory.changeScope(playlistPanel, "playlists:${id.orEmpty()}")
        val library = model.library.value
        val playlist = library.playlists.firstOrNull { it.id == id }
        playlistBack.isVisible = id != null
        playlistPlay.isVisible = playlist != null
        playlistAdd.isVisible = playlist != null
        playlistUpdate.isVisible = playlist != null
        playlistRestore.isVisible = playlist != null
        playlistTrackRows = if (id == null) emptyList() else LibraryEdits.playlistTracks(library, id)
        playlistPlay.isEnabled = playlistTrackRows.isNotEmpty()
        val rows = if (id == null) library.playlists.map {
            MusicRow(it.id, it.name, resources.getQuantityString(R.plurals.track_count, it.trackIds.size, it.trackIds.size), icon = R.drawable.ic_playlist)
        } else playlistTrackRows.map { trackRow(it).copy(inlineSubtitle = true) }
        playlistAdapter.submitList(rows)
        playlistPanel.heading.text = playlist?.name ?: getString(R.string.playlists)
        playlistPanel.empty(getString(if (id == null) R.string.playlists_empty else R.string.playlist_empty), rows.isEmpty())
        updateCompactHeading(playlistPanel)
        if (changedLocation) focusMemory.activate(playlistPanel)
    }

    private fun updateQueue(force: Boolean = false) {
        if (!::queuePanel.isInitialized || visibleScreen !== queuePanel) return
        val player = controller
        val saved = model.app.radio.savedMusic.value
        val signature = saved?.queue?.map { it.id to it.title } ?: if (player == null) emptyList() else (0 until player.mediaItemCount).map {
            val item = player.getMediaItemAt(it)
            item.mediaId to item.mediaMetadata.title.toString()
        }
        if (force || signature != queueSignature) {
            val known = model.library.value.tracks.associateBy { it.id }
            queueTracks = saved?.queue ?: if (player == null) emptyList() else (0 until player.mediaItemCount).map {
                val item = player.getMediaItemAt(it)
                item.toTrack(known[item.mediaId])
            }
            queueSignature = signature
        }
        queueAdapter.submitList(queueTracks.mapIndexed { index, track ->
            trackRow(track).copy(key = "$index:${track.id}",
                active = saved == null && index == player?.currentMediaItemIndex, inlineSubtitle = true)
        }) {
            val index = saved?.index ?: controller?.currentMediaItemIndex ?: -1
            if (queueScrollPending && visibleScreen === queuePanel && index in queueAdapter.currentList.indices) {
                (queuePanel.list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(index, 0)
                queueScrollPending = false
            }
        }
        queuePanel.heading.text = if (saved != null) getString(R.string.radio_music_saved)
            else resources.getQuantityString(R.plurals.track_count, queueTracks.size, queueTracks.size)
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
        now.controls.previous.setOnClickListener { withController {
            if (currentRadio() != null) it.seekToPreviousMediaItem() else it.seekToPrevious()
        } }
        now.controls.rewind.setOnClickListener {
            if (currentRadio() != null) switchRadioSource(-1) else seekBy(-10_000)
        }
        now.controls.forward.setOnClickListener {
            if (currentRadio() != null) switchRadioSource(1) else seekBy(10_000)
        }
        now.controls.next.setOnClickListener { withController { it.seekToNextMediaItem() } }
        binding.miniNext.setOnClickListener { withController { it.seekToNextMediaItem() } }
        now.controls.shuffle.setOnClickListener {
            val options = model.app.playbackSettings.options.value
            model.app.playbackSettings.setOrder(if (options.order == PlayOrder.RANDOM) PlayOrder.SEQUENTIAL else PlayOrder.RANDOM)
        }
        now.controls.repeat.setOnClickListener { loopDialog() }
        now.controls.favorite.setOnClickListener {
            val station = currentRadio()
            if (station != null) model.pinRadio(station) else currentTrack()?.let(model::favorite)
        }
        now.controls.switchPlaybackMode.setOnClickListener { switchPlaybackMode() }
        now.controls.details.setOnClickListener {
            val station = currentRadio()
            if (station != null) radioSources(station) else currentTrack()?.let(::detailsDialog)
        }
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
        listOf(now.controls.seek, binding.miniSeek, now.controls.volume).forEach {
            it.thumbTintList = tint(true)
            it.progressTintList = tint(true)
            it.progressBackgroundTintList = tint(false)
        }
        now.controls.position.setTextColor(tint(false))
        now.controls.volume.max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        now.controls.volume.isEnabled = !audio.isVolumeFixed
        now.controls.volumeMute.setOnClickListener {
            model.app.playbackSettings.setMuted(!model.app.playbackSettings.muted.value)
        }
        now.controls.volume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    try {
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                        if (progress > 0) model.app.playbackSettings.setMuted(false)
                    } catch (error: SecurityException) {
                        model.app.report(getString(R.string.volume_failed))
                    }
                }
            }
        })
        if (audio.isVolumeFixed) now.controls.volume.contentDescription = getString(R.string.volume_fixed)
    }

    private fun togglePlay() = withController { player ->
        if (model.app.radioPreparing.value && currentRadio() == null) return@withController
        // Keep Pause during focus interruptions, but offer Play for idle/ended queues.
        if (!Util.shouldShowPlayButton(player, false)) player.pause()
        else if (player.mediaItemCount > 0) {
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
            if (player.playbackState == Player.STATE_IDLE || player.playerError != null) player.prepare()
            player.play()
        } else model.screen(R.id.nav_folders)
    }

    private fun switchPlaybackMode() {
        if (modeSwitchCommand != null || model.app.radioPreparing.value) return
        val radio = model.app.radio.options.value
        val future = if (radio.active || currentRadio() != null) {
            if (model.app.radio.savedMusic.value?.queue?.isEmpty() == true) {
                model.app.inform(getString(R.string.no_tracks_to_play))
                model.screen(R.id.nav_folders)
                return
            }
            radioCommand(RadioCommands.music, Bundle().apply { putBoolean(RadioCommands.RESUME, true) })
        } else {
            val station = radio.stationId
            if (station == null) {
                if (radio.showPanel) controller?.pause()
                model.app.inform(getString(if (radio.showPanel) R.string.resume_radio_choose else R.string.resume_radio_choose_hidden))
                model.screen(R.id.nav_radio)
                return
            }
            radioCommand(RadioCommands.play, Bundle().apply { putString(RadioCommands.STATION, station) })
        } ?: return
        modeSwitchCommand = future
        updatePlayer()
        future.addListener({
            if (modeSwitchCommand === future) modeSwitchCommand = null
            updatePlayer()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun consumePlayRequest() {
        val player = controller ?: return
        val request = model.playRequest.value ?: return
        if (restoringMusic) return
        if (model.app.radio.options.value.active || currentRadio() != null) {
            restoringMusic = true
            radioCommand(RadioCommands.music) { restoringMusic = false; consumePlayRequest() }
            return
        }
        val mediaItems = request.tracks.mapIndexed { index, track ->
            val item = track.toMediaItem()
            if (index == 0 && request.collection != null) item.withCollection(request.collection) else item
        }
        when (request.mode) {
            QueueMode.REPLACE -> {
                if (!request.playWhenReady) player.pause()
                // An ordinary selection can retain the current song; explicit position restoration cannot.
                val position = if (request.playWhenReady && request.positionMs == 0L) C.TIME_UNSET else request.positionMs
                player.setMediaItems(mediaItems, request.index, position)
                if (request.playWhenReady) { player.prepare(); player.play() }
                else model.app.inform(getString(R.string.collection_position_restored,
                    request.tracks[request.index].title, TimeText.format(request.positionMs)))
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
        backupDialog?.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = ready && !model.backupBusy.value
        val radioControls = radioPanelRequested()
        val track = if (radioControls) null else currentTrack()
        val station = currentRadio()
        val pendingRadio = radioControls && station == null
        val title = if (pendingRadio) getString(if (model.app.radioPreparing.value) R.string.radio_switching else R.string.radio)
            else station?.title(this) ?: track?.title?.ifBlank { track.fileName } ?: getString(R.string.no_track)
        val artist = if (pendingRadio) getString(R.string.loading) else if (station != null) station.publisher(this)
            else track?.artist?.ifBlank { getString(R.string.unknown_artist) } ?: getString(R.string.no_track_hint)
        updateWindowTitle(title.takeIf { model.app.radioPreparing.value || station != null || track != null })
        now.controls.title.setTextIfChanged(title)
        now.controls.artist.setTextIfChanged(artist)
        now.controls.album.setTextIfChanged(if (pendingRadio) getString(R.string.radio_live) else if (station != null) getString(R.string.radio_source_position,
            station.sourceIndex + 1, station.sources.size, java.net.URI(station.streamUri).host)
            else track?.album?.ifBlank { getString(R.string.unknown_album) }.orEmpty())
        val mode = getString(if (radioControls) R.string.radio else R.string.music_mode)
        val miniTitle = if (!pendingRadio && binding.miniPlayer.singleRow && track != null)
            getString(R.string.mini_track_summary, title, artist) else title
        binding.miniTitle.setTextIfChanged(if (station != null || track != null)
            getString(R.string.playback_mode_status, mode, miniTitle) else miniTitle)
        binding.miniArtist.setTextIfChanged(artist)
        val wantsPlay = player?.playWhenReady == true
        radioWarmupReady.value = ready && !radioControls && !model.app.startupPlayback.pending &&
            model.playRequest.value == null && player?.isPlaying == true
        val showPause = !Util.shouldShowPlayButton(player, false)
        val icon = if (showPause && radioControls) R.drawable.ic_stop else if (showPause) R.drawable.ic_pause else R.drawable.ic_play
        val description = getString(if (showPause && radioControls) R.string.radio_stop else if (showPause) R.string.pause else R.string.play)
        val primaryInk = primaryControlHighlight()?.let(::primaryControlInk)
            ?: AccentTheme.color(binding.root, com.google.android.material.R.attr.colorOnSurface)
        listOf(now.controls.play, binding.miniPlay).forEach {
            PlayerIcons.image(it, icon, primaryInk)
            it.contentDescription = description
            it.isEnabled = ready && !pendingRadio
        }
        now.controls.previous.isEnabled = !pendingRadio && if (station != null)
            player?.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) == true else player != null && track != null
        val canNext = !pendingRadio && if (station != null) player?.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) == true
            else player?.hasNextMediaItem() == true
        now.controls.next.isEnabled = canNext
        binding.miniNext.isEnabled = canNext
        now.controls.rewind.contentDescription = getString(if (radioControls) R.string.radio_source_previous else R.string.rewind)
        now.controls.forward.contentDescription = getString(if (radioControls) R.string.radio_source_next else R.string.forward)
        val options = model.app.playbackSettings.options.value
        now.controls.shuffle.isEnabled = !radioControls
        now.controls.repeat.isEnabled = !radioControls
        now.controls.position.isEnabled = !radioControls
        val shuffled = options.order == PlayOrder.RANDOM
        now.controls.shuffle.contentDescription = getString(orderLabel(options.order))
        now.controls.shuffle.isSelected = shuffled && !radioControls
        PlayerIcons.image(now.controls.shuffle, R.drawable.ic_shuffle, tint(shuffled).defaultColor)
        PlayerIcons.image(now.controls.repeat, when (options.loop) {
            LoopMode.SINGLE_FILE -> R.drawable.ic_repeat_one
            LoopMode.FOLDER -> R.drawable.ic_folder
            else -> R.drawable.ic_repeat
        }, tint(options.loop != LoopMode.OFF).defaultColor)
        now.controls.repeat.isSelected = options.loop != LoopMode.OFF && !radioControls
        now.controls.repeat.contentDescription = getString(R.string.loop_mode_description, getString(loopLabel(options.loop)))
        now.controls.playbackOptions.setTextIfChanged(if (radioControls) getString(R.string.radio_music_saved)
            else getString(R.string.playback_options_summary,
                getString(R.string.loop_mode_description, getString(loopLabel(options.loop))), getString(orderLabel(options.order))))
        now.controls.playbackOptions.isVisible = !radioControls
        val favored = if (station != null) station.id in model.app.radio.options.value.pins else track?.id in model.library.value.favorites
        val favoriteLabel = getString(if (radioControls) {
            if (favored) R.string.radio_unpin else R.string.radio_pin
        } else if (favored) R.string.unfavorite else R.string.favorite)
        now.controls.favorite.contentDescription = favoriteLabel
        now.controls.favorite.setTextIfChanged(if (now.root.compactControls) "" else favoriteLabel)
        PlayerIcons.button(now.controls.favorite, if (radioControls) R.drawable.ic_pin
            else if (favored) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
        val favoriteScale = if (favored) SELECTED_CONTROL_SCALE else 1f
        now.controls.favorite.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, favoriteTextSize * favoriteScale)
        now.controls.favorite.iconSize = (favoriteIconSize * favoriteScale).roundToInt()
        now.controls.favorite.isSelected = favored
        now.controls.favorite.isEnabled = !pendingRadio && (station != null || (track != null && findTrack(track.id) != null))
        val returnToMusic = radioControls
        val modeLabel = getString(if (returnToMusic) R.string.resume_music else R.string.resume_radio)
        now.controls.switchPlaybackMode.apply {
            contentDescription = modeLabel
            ViewCompat.setStateDescription(this, mode)
            setTextIfChanged(if (now.root.compactControls) "" else getString(if (returnToMusic) R.string.music_mode else R.string.radio))
            TooltipCompat.setTooltipText(this, getString(R.string.playback_mode_switch_description, mode, modeLabel))
            isEnabled = ready && modeSwitchCommand == null && !model.app.radioPreparing.value && !model.backupBusy.value
            PlayerIcons.button(this, if (returnToMusic) R.drawable.ic_note else R.drawable.ic_radio, primaryInk)
        }
        now.controls.details.isEnabled = !pendingRadio && (station != null || track != null)
        val detailsLabel = if (station != null) getString(R.string.radio_sources, station.sources.size) else getString(R.string.details)
        now.controls.details.setTextIfChanged(if (now.root.compactControls) "" else detailsLabel)
        now.controls.details.contentDescription = detailsLabel
        PlayerIcons.refresh(now.controls.root)
        PlayerIcons.refresh(binding.miniPlayer)
        val status = when {
            !ready -> getString(R.string.connecting)
            pendingRadio -> getString(R.string.loading)
            player.playbackState == Player.STATE_BUFFERING -> getString(
                if (radioControls) R.string.radio_connecting else R.string.buffering)
            player.playbackState == Player.STATE_ENDED -> getString(R.string.ended)
            player.isPlaying -> getString(R.string.playing)
            player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE -> getString(R.string.interrupted_audio)
            else -> getString(if (radioControls) R.string.radio_stopped else R.string.paused)
        }
        val modeStatus = if (model.app.radioPreparing.value) getString(R.string.radio_switching)
            else getString(R.string.playback_mode_status, mode, status)
        now.controls.status.setTextIfChanged(if (player != null && player.mediaItemCount > 0) {
            "$modeStatus  ·  ${getString(R.string.queue_position, player.currentMediaItemIndex + 1, player.mediaItemCount)}"
        } else modeStatus)
        renderRecovery()
        updateProgress()
        updateVolume()
        progressPolling.value = !radioControls && player?.isPlaying == true && (visibleScreen === now.root || binding.miniPlayer.isVisible)
        volumePolling.value = visibleScreen === now.root
        radioStatusPolling.value = station != null && wantsPlay && visibleScreen === now.root &&
            model.app.radio.options.value.tuning.showStatus
        updateRadioStatus()
        updateSpectrum()
        updateArtwork(track?.uri)
        // A reconnecting controller is not a cleared queue.
        if (ready) model.requestLyrics(track)
        renderRadio()
        renderRadioQuickStations()
    }

    private fun updateWindowTitle(nowPlaying: String?) {
        val name = getString(R.string.app_name)
        val windowTitle = nowPlaying?.takeIf { it.isNotBlank() }
            ?.let { getString(R.string.now_playing_window_title, name, it) } ?: name
        if (this.title != windowTitle) this.title = windowTitle
        if (pendingScreenId == null && model.selectedScreen.value != R.id.action_settings &&
            !(compactChrome && visibleScreen is ListPanel) && binding.toolbar.title != windowTitle) {
            binding.toolbar.title = windowTitle
        }
    }

    private fun recoveryTrack(): Track? {
        val problem = model.app.playbackProblem.value?.takeUnless { it.radio } ?: return null
        return model.library.value.tracks.firstOrNull { it.id == problem.mediaId || problem.mediaId in it.restoredIds }
            ?: currentTrack()
    }

    private fun recoverySource(): FolderSource? {
        val problem = model.app.playbackProblem.value
        if (problem != null) {
            if (problem.radio) return null
            val track = recoveryTrack() ?: return null
            return model.library.value.sources.firstOrNull { track.sourceUris[it.uri] == problem.uri }
                ?: model.library.value.sources.firstOrNull { it.uri in track.sourceUris }
        }
        val browser = model.browser.value
        if (browser.error != null) {
            model.library.value.sources.firstOrNull { it.uri == browser.path.lastOrNull()?.sourceUri }?.let { return it }
        }
        return model.library.value.sources.firstOrNull { it.unavailable || it.needsReconnect }
    }

    private fun renderRecovery() {
        val problem = model.app.playbackProblem.value
        val source = recoverySource()
        val browser = model.browser.value
        val message = when {
            problem != null -> getString(if (problem.radio) R.string.radio_failed else R.string.playback_failed,
                problem.title.ifBlank { getString(R.string.unnamed_file) }, problem.errorCodeName)
            source != null -> getString(R.string.source_failed, sourceName(source),
                browser.error?.takeIf { browser.path.lastOrNull()?.sourceUri == source.uri }
                    ?: source.lastIssue ?: getString(if (source.needsReconnect)
                        R.string.reconnect_folder_first else R.string.source_unavailable))
            else -> null
        }
        val busy = model.backupBusy.value || model.recoveringSource.value != null ||
            if (problem != null) controller == null || !model.app.playbackReady.value ||
                controller?.playbackState == Player.STATE_BUFFERING || modeSwitchCommand != null || model.app.radioPreparing.value
            else model.scan.value != null || browser.loading
        binding.recoveryStatus.render(message, busy, when {
            source != null -> R.string.recovery_reselect_folder
            problem != null && !problem.radio && recoveryTrack() != null -> R.string.recovery_reselect_file
            else -> null
        })
        if (source?.needsReconnect == true && problem == null) binding.recoveryStatus.retry.isEnabled = false
    }

    private fun retryRecovery() {
        val problem = model.app.playbackProblem.value
        if (problem == null) {
            val source = recoverySource() ?: return
            if (!source.unavailable && model.browser.value.error != null && model.browser.value.path.lastOrNull()?.sourceUri == source.uri)
                model.refreshBrowser()
            else model.scanFolders(listOf(source))
            return
        }
        if (problem.radio) {
            val future = radioCommand(RadioCommands.play, Bundle().apply { putString(RadioCommands.STATION, problem.mediaId) }) ?: return
            modeSwitchCommand = future
            renderRecovery()
            future.addListener({
                if (modeSwitchCommand === future) modeSwitchCommand = null
                updatePlayer()
            }, ContextCompat.getMainExecutor(this))
        } else withController { player ->
            val item = player.currentMediaItem ?: return@withController
            if (!problem.matches(item.mediaId, item.localConfiguration?.uri?.toString())) return@withController
            val track = recoveryTrack()
            if (track != null && track.uri != problem.uri && !BackupEdits.unavailable(track.uri)) {
                val index = player.currentMediaItemIndex
                val position = player.currentPosition
                player.replaceMediaItem(index, item.buildUpon().setMediaId(track.id).setUri(track.uri)
                    .setMediaMetadata(track.toMediaItem().mediaMetadata).build())
                player.seekTo(index, position)
            }
            player.prepare()
            player.play()
        }
    }

    private fun reselectRecovery() {
        val source = recoverySource()
        if (source == null) {
            recoveryTrack()?.let(::launchReconnectTrack)
            return
        }
        if (source.needsReconnect) {
            reconnectSourceDialog(source)
            return
        }
        model.prepareFolderRecovery(source.uri)
        if (!launchExplorer(reselectFolder, ExplorerMode.MUSIC_FOLDER)) model.prepareFolderRecovery(null)
    }

    private fun updateProgress() {
        val mainVisible = visibleScreen === now.root
        val miniVisible = binding.miniPlayer.isVisible
        if (!mainVisible && !miniVisible) return
        val player = controller
        val station = currentRadio()
        if (radioPanelRequested()) {
            now.controls.seek.isEnabled = false
            binding.miniSeek.isEnabled = false
            now.controls.seek.progress = 0
            binding.miniSeek.progress = 0
            val canSwitch = station != null && player != null && model.app.playbackReady.value && radioSourceCommand == null
            val sources = station?.let { model.app.radioCatalog.find(it.id)?.sources }.orEmpty()
            val sourceIndex = station?.let { sources.indexOf(it.streamUri) } ?: -1
            now.controls.rewind.isEnabled = canSwitch && sourceIndex > 0
            now.controls.forward.isEnabled = canSwitch && sourceIndex >= 0 && sourceIndex < sources.lastIndex
            val position = getString(if (station == null) R.string.loading else R.string.radio_live)
            now.controls.position.setTextIfChanged(position)
            now.controls.duration.setTextIfChanged("")
            binding.miniTime.setTextIfChanged(position)
            return
        }

        val duration = player?.duration?.takeIf { it > 0 && it != C.TIME_UNSET }
        val total = duration ?: currentTrack()?.durationMs?.takeIf { it > 0 }
        val seekable = !model.app.radioPreparing.value && duration != null && player?.isCurrentMediaItemSeekable == true &&
            player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        // Retain an invalidated drag until release so later move events cannot seek the next track.
        val preview = draggingSeek?.takeIf { seekable && draggingTrackId == player?.currentMediaItem?.mediaId }
        val position = if (preview != null && duration != null) {
            duration * preview.progress / 10_000
        } else player?.currentPosition?.coerceAtLeast(0) ?: 0
        fun updateSeek(seek: SeekBar) {
            seek.isEnabled = seekable
            if (seek !== preview) {
                seek.progress = if (duration != null) ((position * 10_000) / duration).coerceIn(0, 10_000).toInt() else 0
            }
        }
        if (mainVisible) {
            now.controls.rewind.isEnabled = seekable
            now.controls.forward.isEnabled = seekable
            now.controls.duration.setTextIfChanged(total?.let(TimeText::format) ?: getString(R.string.empty_time))
            val mainTimeMode = model.app.displaySettings.options.value.mainTimeMode
            now.controls.position.setTextIfChanged(TimeText.position(position, total, mainTimeMode, getString(R.string.empty_time)))
            now.controls.position.contentDescription = getString(R.string.toggle_time_mode,
                now.controls.position.text, getString(if (mainTimeMode == PlaybackTimeMode.ELAPSED) R.string.time_remaining else R.string.time_elapsed))
            updateSeek(now.controls.seek)
            updateLyricProgress()
        }
        if (miniVisible) {
            updateSeek(binding.miniSeek)
            binding.miniTime.setTextIfChanged(TimeText.progress(position, total, model.app.displaySettings.options.value.timeMode,
                getString(R.string.empty_time)))
        }
    }

    private fun updateRadioStatus() {
        val tuning = model.app.radio.options.value.tuning
        val show = radioPanelRequested() && tuning.showStatus
        now.controls.radioStreamStatus.isVisible = show
        if (!show) return
        val station = currentRadio()
        val player = controller
        val speed = if (station != null && player?.playWhenReady == true)
            model.app.radioTraffic.bytesPerSecond(station.id) / 1024.0 else 0.0
        val buffered = if (station != null) (player?.totalBufferedDuration ?: 0).coerceAtLeast(0) / 1000.0 else 0.0
        now.controls.radioStreamStatus.setTextIfChanged(getString(R.string.radio_stream_status,
            speed, buffered, tuning.bufferSeconds))
    }

    private fun seekToProgress(progress: Int) {
        val player = controller ?: return
        val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: return
        if (player.isCurrentMediaItemSeekable && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            player.seekTo(duration * progress.coerceIn(0, 10_000) / 10_000)
        }
    }

    private fun seekBy(deltaMs: Long) = withController { player ->
        if (com.macarmusic.app.playback.SystemMediaControls.seek(player, deltaMs)) {
            updateProgress()
        }
    }

    private fun updateVolume() {
        val muted = model.app.playbackSettings.muted.value
        PlayerIcons.image(now.controls.volumeMute, if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on, tint(muted).defaultColor)
        now.controls.volumeMute.contentDescription = getString(if (muted) R.string.unmute_player else R.string.mute_player)
        now.controls.volumeMute.isSelected = muted
        now.controls.volume.progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        now.controls.volumeValue.setTextIfChanged(getString(R.string.volume_level, now.controls.volume.progress))
    }

    private fun TextView.setTextIfChanged(value: String) {
        if (!value.contentEquals(text)) text = value
    }

    private fun updateArtwork(uri: String?) {
        if (!optionalContentStarted) return
        val station = currentRadio()
        val radio = radioPanelRequested()
        val logo = model.app.displaySettings.options.value.appLogo
        val fallback = model.app.radio.options.value.buttonLogo
        val key = station?.let { "${model.app.radioLogos.identity(it)}:$fallback" }
            ?: if (radio) "radio:$fallback" else uri ?: "brand:$logo"
        if (artworkUri == key) return
        artworkUri = key
        artworkWarning = null
        artworkJob?.cancel()
        now.cover.artwork.setImageResource(if (radio) Branding.radio(fallback) else Branding.artwork(logo))
        binding.miniArtwork.setImageResource(if (radio) Branding.radio(fallback) else Branding.icon(logo))
        binding.navigation.artwork.setImageResource(if (radio) Branding.radio(fallback) else Branding.icon(logo))
        val fallbackTint = if (radio && fallback == com.macarmusic.app.radio.RadioButtonLogo.CLASSIC_RADIO) tint(true) else null
        listOf(now.cover.artwork, binding.miniArtwork, binding.navigation.artwork).forEach { it.imageTintList = fallbackTint }
        if (station != null) {
            artworkJob = contentScope.launch {
                try {
                    model.app.radioLogos.cached(station)?.let {
                        if (artworkUri == key) {
                            binding.miniArtwork.imageTintList = null
                            binding.navigation.artwork.imageTintList = null
                            binding.miniArtwork.setImageBitmap(it)
                            binding.navigation.artwork.setImageBitmap(it)
                        }
                    }
                } catch (error: IOException) {
                    Log.w("MainActivity", "Cannot read radio logo", error)
                    model.app.report(getString(R.string.radio_logo_cache_failed))
                }
            }
            return
        }
        if (radio || uri.isNullOrBlank()) return
        artworkJob = contentScope.launch {
            val cover = model.app.artwork.load(uri)
            if (artworkUri == key) {
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
        val enabled = !radioPanelRequested() && model.app.displaySettings.options.value.lyricsEnabled &&
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
        now.cover.artwork.isVisible = lyrics == null && !radioPanelRequested()
        now.cover.lyricsList.isVisible = lyrics != null
        now.cover.lyricsStatus.isVisible = enabled
        now.cover.lyricsFooter.isVisible = enabled
        now.cover.lyricsStatus.maxLines = if (lyrics == null) 4 else 1
        now.cover.lyricVersions.isVisible = enabled
        now.cover.lyricVersions.isEnabled = !state.loading
        now.cover.nextLyrics.isVisible = enabled
        now.cover.nextLyrics.isEnabled = display.onlineLyrics
        now.cover.lyricsTiming.isVisible = enabled && lyrics?.timed == true && result?.document != null
        now.cover.lyricsTiming.isEnabled = !state.loading
        if (lyricTimingDialog != null && (!enabled || timingTrackId != state.trackId ||
                timingDocument?.text != result?.document?.text || timingDocument?.origin != result?.origin)) {
            lyricTimingDialog?.dismiss()
        }
        now.cover.retryLyrics.isVisible = enabled && !state.loading && lyrics == null
        renderRadioQuickStations()
        now.cover.lyricsStatus.text = when {
            !enabled -> ""
            state.loading -> getString(R.string.lyrics_loading)
            lyrics != null -> {
                val source = when (result?.origin) {
                    LyricOrigin.LOCAL_FILE -> R.string.lyrics_local
                    LyricOrigin.EMBEDDED -> R.string.lyrics_embedded
                    LyricOrigin.KUGOU -> R.string.lyrics_kugou
                    LyricOrigin.NETEASE -> R.string.lyrics_netease
                    LyricOrigin.QQ -> R.string.lyrics_qq
                    LyricOrigin.KUWO -> R.string.lyrics_kuwo
                    null -> R.string.lyrics
                }
                val label = if (result?.cached == true) getString(R.string.lyrics_cached, getString(source)) else getString(source)
                if (result?.issue == LyricIssue.LOCAL_UNREADABLE) "$label\n${getString(R.string.lyrics_local_failed)}" else label
            }
            result?.issue == LyricIssue.LOCAL_UNREADABLE -> getString(R.string.lyrics_local_failed)
            result?.issue == LyricIssue.CACHE_UNAVAILABLE -> getString(R.string.lyrics_cache_unavailable)
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
                player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE), force,
            durationMs = player?.duration?.coerceAtLeast(0) ?: 0,
            advancing = player?.isPlaying == true && visibleScreen === now.root &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && now.cover.lyricsList.isShown,
            speed = player?.playbackParameters?.speed ?: 1f)
    }

    private fun renderLyricChoices(state: LyricChoices?) {
        if (state == null) {
            lyricVersionsDialog?.dismiss()
            return
        }
        val generation = contentGeneration
        val dialog = lyricVersionsDialog ?: LyricVersionsDialog(this,
            { model.app.displaySettings.options.value },
            { model.chooseLyricVersion(it) },
            { model.chooseLyricVersion(it, best = true) },
            { model.removeLyricVersion(it) }) {
                if (generation == contentGeneration) {
                    lyricVersionsDialog = null
                    if (!isChangingConfigurations) model.closeLyricVersions()
                }
            }.also {
                lyricVersionsDialog = it
                it.show()
            }
        dialog.update(state)
    }

    private fun renderOnlineLyrics(state: com.macarmusic.app.lyrics.OnlineLyricSearch?) {
        if (state == null) {
            onlineLyricsDialog?.dismiss()
            return
        }
        val generation = contentGeneration
        val dialog = onlineLyricsDialog ?: OnlineLyricsDialog(this,
            { model.app.displaySettings.options.value }, model::chooseOnlineLyric) {
                if (generation == contentGeneration) {
                    onlineLyricsDialog = null
                    if (!isChangingConfigurations) model.closeOnlineLyrics()
                }
            }.also {
                onlineLyricsDialog = it
                it.show()
            }
        dialog.update(state)
    }

    private fun renderLyricDownloads(state: LyricDownloadState?) {
        if (state == null) {
            lyricDownloadsDialog?.dismiss()
            return
        }
        val generation = contentGeneration
        val dialog = lyricDownloadsDialog ?: LyricDownloadsDialog(this,
            { model.app.displaySettings.options.value },
            {
                val cache = model.app.lyrics.cache.info.value
                if (cache.invalidLocation) getString(R.string.lyrics_cache_unavailable)
                else cache.folder?.name ?: getString(R.string.lyrics_download_internal)
            },
            model::cancelLyricDownloads) {
                if (generation == contentGeneration) {
                    lyricDownloadsDialog = null
                    if (!isChangingConfigurations) model.closeLyricDownloads()
                }
            }.also {
                lyricDownloadsDialog = it
                it.show()
            }
        dialog.update(state)
    }

    private fun showLyricTiming() {
        val state = model.lyrics.value
        val trackId = state.trackId ?: return
        val result = state.result ?: return
        val document = result.document ?: return
        if (state.loading || result.lyrics?.timed != true || lyricTimingDialog != null) return
        var offset = document.offsetMs
        var saveRequested = false
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val value = TextView(this).apply {
            id = R.id.lyricsTimingValue
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            minHeight = dp(48)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        }
        body.addView(value, LinearLayout.LayoutParams(-1, -2))
        val controls = mutableListOf<Pair<MaterialButton, Long>>()
        fun refresh() {
            value.text = if (offset == 0L) getString(R.string.lyrics_timing_original) else getString(
                if (offset > 0) R.string.lyrics_timing_earlier_value else R.string.lyrics_timing_later_value,
                String.format(java.util.Locale.getDefault(), "%.1f", kotlin.math.abs(offset) / 1_000.0))
            controls.forEach { (button, delta) ->
                button.isEnabled = offset + delta in -LyricTiming.MAX_OFFSET_MS..LyricTiming.MAX_OFFSET_MS
            }
        }
        for ((step, earlier, later) in listOf(
            Triple(100L, R.string.lyrics_earlier_fine, R.string.lyrics_later_fine),
            Triple(1_000L, R.string.lyrics_earlier_coarse, R.string.lyrics_later_coarse))) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            body.addView(row, LinearLayout.LayoutParams(-1, -2))
            for ((label, delta) in listOf(earlier to step, later to -step)) {
                val button = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    setText(label)
                    minHeight = dp(48)
                    isAllCaps = false
                    id = when (delta) {
                        100L -> R.id.lyricsEarlierFine
                        -100L -> R.id.lyricsLaterFine
                        1_000L -> R.id.lyricsEarlierCoarse
                        else -> R.id.lyricsLaterCoarse
                    }
                    setOnClickListener {
                        offset += delta
                        refresh()
                        model.previewLyricTiming(trackId, result, offset)
                    }
                }
                row.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginStart = dp(2); marginEnd = dp(2)
                })
                controls.add(button to delta)
            }
        }
        val reset = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = R.id.lyricsTimingReset
            setText(R.string.lyrics_timing_reset)
            minHeight = dp(48)
            isAllCaps = false
            setOnClickListener {
                offset = 0
                refresh()
                model.previewLyricTiming(trackId, result, offset)
            }
        }
        body.addView(reset, LinearLayout.LayoutParams(-1, -2))
        val display = model.app.displaySettings.options.value
        AccentTheme.tintControls(body)
        BlockButtonStyler(body).apply(display.controlBlocks, display.blockOutline)
        refresh()
        timingTrackId = trackId
        timingDocument = document
        val scroll = android.widget.ScrollView(this).apply { addView(body) }
        lyricTimingDialog = AccentTheme.dialog(this).setTitle(R.string.lyrics_timing).setView(scroll)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                saveRequested = model.saveLyricTiming(trackId, result, offset)
            }
            .create().also { dialog ->
                dialog.setOnDismissListener {
                    if (lyricTimingDialog === dialog) {
                        lyricTimingDialog = null
                        timingTrackId = null
                        timingDocument = null
                    }
                    if (!saveRequested) model.cancelLyricTiming(trackId, result)
                }
                dialog.show()
            }
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
        binding.miniPlayer.setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurfaceVariant),
            ((100 - options.effectiveMiniPlayerTransparency) * 255 / 100)))
        for (view in binding.screenContainer.children) {
            view.setBackgroundColor(Color.TRANSPARENT)
        }
        for (view in listOf(now.controls.root, now.cover.root)) {
            view.background = null
        }
        barElevations.forEach { (view, elevation) -> view.elevation = if (custom) 0f else elevation }
        val indicator = navigationIndicator
        binding.navigation.isItemActiveIndicatorEnabled = !custom && indicator
        renderNotice(model.app.notice.value)
    }

    private fun renderControlBlocks(enabled: Boolean, outline: BlockOutline) {
        val primaryColor = primaryControlHighlight()
        val appearance = Triple(enabled, outline, primaryColor)
        if (appliedControlBlocks == appearance) return
        val framed = enabled && outline.hasBorder
        val backgrounds = originalControlBackgrounds
        val padding = originalControlPadding
        val shapes = originalActionShapes
        val strokes = originalActionStrokes
        val modeColors = originalModeColors
        val color = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOutline)
        val ripple = ColorStateList.valueOf(MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorControlHighlight))
        fun border() = PlayerBlockDrawable(outline, color, resources.displayMetrics.density)
        controlBlockContainers.forEach {
            it.background = if (framed) border() else backgrounds[it]
        }
        val inset = if (!framed) 0 else dp(if (outline.style == BlockStyle.CHARACTERS) 12 else 8)
        padding.forEach { (view, original) ->
            if (view === binding.miniPlayer && binding.miniPlayer.singleRow) return@forEach
            val vertical = if (view === now.controls.trackInformation || view === now.controls.spectrum ||
                view === now.cover.root || view === binding.miniPlayer) inset else 0
            val start = if (view === now.controls.volumeBlock) original.left else maxOf(original.left, inset)
            view.setPaddingRelative(start, maxOf(original.top, vertical),
                maxOf(original.right, inset), maxOf(original.bottom, vertical))
        }
        controlBlockButtons.forEach {
            if (primaryColor != null && (it === now.controls.play || it === binding.miniPlay)) {
                fun fill(color: Int) = GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = dp(if (framed && outline.hasArc) outline.arcDp else 6).toFloat()
                }
                val fill = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_enabled), fill(primaryColor))
                    addState(intArrayOf(), fill(androidx.core.graphics.ColorUtils.setAlphaComponent(primaryColor, 96)))
                }
                val content = if (framed) LayerDrawable(arrayOf(fill, border())) else fill
                if (Build.VERSION.SDK_INT == 23) it.background?.jumpToCurrentState()
                it.background = RippleDrawable(ripple, content, ColorDrawable(Color.WHITE))
            } else it.background = if (framed) RippleDrawable(ripple, border(), ColorDrawable(Color.WHITE)) else backgrounds[it]
            it.foreground = it.playerFocusForeground()
        }
        controlBlockOverlays.forEach {
            val decoration = if (framed && !(it === binding.miniPlayer && binding.miniPlayer.singleRow)) border() else null
            it.foreground = if (it is MaterialButton) it.playerFocusForeground(decoration) else decoration
        }
        binding.navigation.configureBlocks(enabled, outline)
        listOfNotNull(
            if (::libraryPanel.isInitialized) libraryPanel else null,
            if (::radioPanel.isInitialized) radioPanel else null,
            if (::folderPanel.isInitialized) folderPanel else null,
            if (::queuePanel.isInitialized) queuePanel else null,
            if (::playlistPanel.isInitialized) playlistPanel else null,
        ).forEach { it.configureBlocks(enabled, outline) }
        radioStickyHeader?.configureBlocks(enabled, outline)
        radioSubscriptionsDialog?.configureBlocks(enabled, outline)
        radioQuickAdapter?.configureBlocks(enabled, outline)
        if (::settingsButtons.isInitialized) settingsButtons.apply(enabled, outline)
        if (::settings.isInitialized) AboutContent.configureBorder(settings.settingsAboutPanel, enabled, outline)
        renderCategoryDividers(enabled, outline)
        shapes.forEach { (button, shape) ->
            button.shapeAppearanceModel = if (framed) shape.toBuilder().setAllCornerSizes(dp(6).toFloat()).build() else shape
            button.strokeWidth = if (enabled || !outline.hasBorder) 0 else requireNotNull(strokes[button])
        }
        now.controls.switchPlaybackMode.apply {
            backgroundTintList = if (primaryColor == null) modeColors.first else
                ColorStateList(arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(primaryColor, androidx.core.graphics.ColorUtils.setAlphaComponent(primaryColor, 96)))
            setTextColor(if (primaryColor == null) modeColors.second else PlayerIcons.tint(context, primaryControlInk(primaryColor)))
        }
        appliedControlBlocks = appearance
    }

    private fun primaryControlColor(): Int = AccentTheme.primaryControlColor(binding.root)

    private fun primaryControlHighlight(): Int? = primaryControlColor()
        .takeIf { model.app.displaySettings.options.value.primaryControlHighlightEnabled }

    private fun primaryControlInk(background: Int): Int = AccentTheme.primaryControlInk(binding.root, background)

    private fun renderCategoryDividers(enabled: Boolean, outline: BlockOutline) {
        if (!::settings.isInitialized) return
        val color = MaterialColors.getColor(settings.root, com.google.android.material.R.attr.colorOutline)
        settings.settingsCategories.children.filter { it.id == R.id.settingsCategoryDivider }.forEach {
            it.background = if (enabled && outline.hasBorder)
                PlayerBlockDrawable(outline, color, resources.displayMetrics.density, divider = true) else null
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
        launchExplorer(pickBackground, ExplorerMode.IMAGES)
    }

    private fun currentTrack(): Track? {
        val item = controller?.currentMediaItem ?: return null
        if (RadioProgram.isRadio(item.mediaId)) return null
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

    private fun homeWidgetsDialog() {
        choiceDialog(getString(R.string.home_widgets), com.macarmusic.app.widgets.HomeWidgetKind.entries.map { kind ->
            getString(kind.label) to {
                val manager = android.appwidget.AppWidgetManager.getInstance(this)
                try {
                    if (Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported &&
                        manager.requestPinAppWidget(kind.component(this), null, null)) {
                        model.app.inform(getString(R.string.widget_pin_confirmation))
                    } else {
                        AccentTheme.dialog(this).setTitle(kind.label).setMessage(R.string.widgets_help)
                            .setPositiveButton(R.string.understood, null).show()
                    }
                } catch (error: IllegalStateException) {
                    Log.w("MainActivity", "Launcher refused widget pinning", error)
                    model.app.report(getString(R.string.widget_pin_failed))
                } catch (error: SecurityException) {
                    Log.w("MainActivity", "Launcher denied widget pinning", error)
                    model.app.report(getString(R.string.widget_pin_failed))
                }
                Unit
            }
        })
    }

    private fun systemInformationDialog() {
        fun reported(value: String?): String =
            value?.takeIf(String::isNotBlank) ?: getString(R.string.system_info_unreported)
        val details = getString(R.string.system_info_details,
            reported(Build.VERSION.RELEASE), Build.VERSION.SDK_INT, reported(Build.VERSION.SECURITY_PATCH),
            reported(Build.MANUFACTURER), reported(Build.MODEL), reported(Build.DISPLAY),
            reported(System.getProperty("os.version")))
        AccentTheme.dialog(this).setTitle(R.string.system_info).setMessage(details)
            .setPositiveButton(R.string.close, null).show().makeMessageSelectable()
    }

    private fun defaultPlayerDialog() {
        AccentTheme.dialog(this).setTitle(R.string.default_music_player).setMessage(R.string.default_music_player_help)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.open_system_settings) { _, _ ->
                val intents = buildList {
                    if (Build.VERSION.SDK_INT >= 24) add(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                    add(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")))
                }
                for (intent in intents) {
                    try {
                        startActivity(intent)
                        return@setPositiveButton
                    } catch (error: ActivityNotFoundException) {
                        Log.w("MainActivity", "Default-app settings page unavailable", error)
                    } catch (error: SecurityException) {
                        Log.w("MainActivity", "Default-app settings page denied", error)
                    }
                }
                model.app.report(getString(R.string.default_music_player_unavailable))
            }.show()
    }

    private fun launchBackupExport() {
        val date = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(java.util.Date())
        launchExplorer(createBackup, ExplorerMode.BACKUP_EXPORT, "ManaMusicRadio-backup-$date.zip")
    }

    private fun launchBackupImport() {
        launchExplorer(pickBackup, ExplorerMode.BACKUP_IMPORT)
    }

    private fun renderBackupPreview(backup: PreparedBackup?) {
        if (backup == null) {
            backupDialog?.dismiss()
            backupDialog = null
            shownBackup = null
            return
        }
        if (shownBackup === backup && backupDialog?.isShowing == true) return
        if (controller == null) connectController()
        backupDialog?.dismiss()
        shownBackup = backup
        backupDialog = AccentTheme.dialog(this).setTitle(R.string.backup_restore)
            .setMessage(getString(R.string.backup_restore_confirmation, backup.manifest.library.tracks.size,
                backup.manifest.library.favorites.size, backup.manifest.library.playlists.size,
                backup.manifest.settings.display.imageIds.size))
            .setNegativeButton(R.string.cancel) { _, _ -> model.dismissBackup() }
            .setOnCancelListener { model.dismissBackup() }
            .setPositiveButton(R.string.backup_restore) { _, _ ->
                withController { it.pause(); model.restoreBackup() }
            }.show().also {
                it.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = controller != null && model.app.playbackReady.value
            }
    }

    private fun reconnectSourceDialog(source: FolderSource) {
        val targets = model.library.value.sources.filterNot { it.needsReconnect || it.uri == source.uri }
        if (targets.isEmpty()) {
            model.app.inform(getString(R.string.reconnect_add_folder))
            return
        }
        choiceDialog(getString(R.string.reconnect_choose_folder, source.name), targets.map { target ->
            sourceName(target) to {
                AccentTheme.dialog(this).setTitle(R.string.reconnect_folder)
                    .setMessage(getString(R.string.reconnect_confirm, source.name, sourceName(target)))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.reconnect_folder) { _, _ -> model.reconnectSource(source, target) }.show()
                Unit
            }
        })
    }

    private fun launchReconnectTrack(track: Track) {
        model.prepareReconnectTrack(track.id, 0)
        if (!launchExplorer(reconnectFile, ExplorerMode.MUSIC_FILE)) model.prepareReconnectTrack(null, -1)
    }

    private fun launchLyricCachePicker() {
        launchExplorer(pickLyricCache, ExplorerMode.LYRIC_CACHE)
    }

    private fun launchFolderPicker() {
        launchExplorer(pickFolder, ExplorerMode.MUSIC_FOLDER)
    }

    private fun launchFilePicker() {
        launchExplorer(pickFile, ExplorerMode.MUSIC_FILE)
    }

    private fun launchExplorer(launcher: ActivityResultLauncher<Intent>, mode: ExplorerMode,
        filename: String = "",
    ): Boolean = try {
        launcher.launch(FileExplorerActivity.intent(this, mode, filename))
        true
    } catch (error: ActivityNotFoundException) {
        Log.e("MainActivity", "The installed file explorer is unavailable", error)
        model.app.report(getString(R.string.picker_missing))
        false
    } catch (error: SecurityException) {
        Log.w("MainActivity", "Android denied the file explorer", error)
        model.app.report(getString(R.string.picker_denied))
        false
    }

    private fun launchDeviceMusic(browse: Boolean = false) {
        model.prepareDeviceMusic(browse)
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
            if (!choice.loading && !choice.browse) {
                val dialog = checkNotNull(deviceMusicDialog)
                choice.folders.forEachIndexed { index, source -> dialog.listView.setItemChecked(index, source.uri in choice.selected) }
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = choice.selected.isNotEmpty()
                dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setText(
                    if (choice.selected.size == choice.folders.size) R.string.deselect_all_folders else R.string.select_all_folders)
            }
            renderedDeviceMusic = choice
            return
        }
        deviceMusicDialog?.dismiss()
        val builder = AccentTheme.dialog(this)
            .setTitle(if (choice.loading) R.string.device_music else if (choice.browse) R.string.device_music_browse else R.string.device_music_choose_folders)
            .setNegativeButton(R.string.cancel) { _, _ -> model.dismissDeviceMusic() }
            .setOnCancelListener { model.dismissDeviceMusic() }
        if (choice.loading) builder.setMessage(R.string.device_music_loading)
        else if (choice.browse) {
            builder.setItems(choice.folders.map(::sourceName).toTypedArray()) { _, index ->
                model.browseDeviceMusicFolder(choice.folders[index].uri)
            }
        } else {
            builder.setMultiChoiceItems(choice.folders.map(::sourceName).toTypedArray(),
                BooleanArray(choice.folders.size) { choice.folders[it].uri in choice.selected }) { _, index, checked ->
                model.selectDeviceMusicFolder(choice.folders[index].uri, checked)
            }.setPositiveButton(R.string.add_folder) { _, _ -> model.addDeviceMusicFolders() }
                .setNeutralButton(if (choice.selected.size == choice.folders.size)
                    R.string.deselect_all_folders else R.string.select_all_folders, null)
        }
        renderedDeviceMusic = choice
        deviceMusicDialog = builder.show().also {
            if (!choice.loading && !choice.browse) {
                it.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = choice.selected.isNotEmpty()
                it.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener { model.selectAllDeviceMusicFolders() }
            }
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

    private fun sourceMenu(source: FolderSource) {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (source.needsReconnect) actions.add(getString(R.string.reconnect_folder) to { reconnectSourceDialog(source) })
        else actions.addAll(listOf(
            getString(R.string.browse) to { model.openSource(source) },
            getString(R.string.rescan) to { model.scanFolders(listOf(source)) },
            getString(R.string.rescan_full) to { model.scanFolders(listOf(source), forceMetadata = true) },
            getString(R.string.play_recursively) to { model.scanFolders(listOf(source), play = true) },
            getString(R.string.lyrics_download) to { model.downloadFolderLyrics(source) },
            getString(R.string.restore_position) to {
                model.restorePosition(PlaybackCollection(CollectionKind.FOLDER, source.uri)); Unit
            },
            getString(R.string.save_folder_playlist) to {
                newPlaylistDialog(folders = listOf(BrowserLocation(source.uri,
                    model.repository.documents.rootId(source.uri), sourceName(source))))
            },
        ))
        actions.addAll(listOf(
            getString(if (source.pinned) R.string.unpin_folder else R.string.pin_folder) to { model.togglePin(source); Unit },
            getString(R.string.remove_source) to {
                AccentTheme.dialog(this).setTitle(R.string.remove_source)
                    .setMessage(getString(R.string.remove_source_body, sourceName(source)))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.remove) { _, _ -> model.removeSource(source) }.show()
                Unit
            },
        ))
        choiceDialog(sourceName(source), actions)
    }

    private fun removeAllFoldersDialog() {
        val sources = model.library.value.sources
        val removable = sources.filterNot { it.pinned }.mapTo(hashSetOf()) { it.uri }
        if (removable.isEmpty()) {
            model.app.inform(getString(R.string.unpin_first))
            return
        }
        AccentTheme.dialog(this).setTitle(R.string.remove_all_folders)
            .setMessage(getString(R.string.remove_all_folders_body, removable.size, sources.size - removable.size))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remove) { _, _ -> model.removeSources(removable) }.show()
    }

    private fun documentMenu(entry: DocumentEntry) {
        if (entry.isDirectory) {
            val source = currentSource() ?: return
            val parent = model.browser.value.path.lastOrNull()?.relativePath.orEmpty()
            val location = BrowserLocation(source.uri, entry.id, entry.name,
                listOf(parent, entry.name).filter(String::isNotEmpty).joinToString("/"))
            choiceDialog(entry.name, listOf(
                getString(R.string.browse) to { model.openDirectory(entry) },
                getString(R.string.play_recursively) to {
                    model.scanFolders(listOf(source), location, play = true)
                },
                getString(R.string.lyrics_download) to { model.downloadFolderLyrics(source, location) },
            ))
        } else {
            val source = currentSource() ?: return
            val parent = model.browser.value.path.lastOrNull()?.documentId ?: return
            contentScope.launch {
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
            getString(R.string.lyrics_download) to { model.downloadLyrics(track.title, listOf(track)) },
            getString(R.string.details) to { detailsDialog(track) },
        )
        if (BackupEdits.unavailable(track.uri)) actions.add(0, getString(R.string.reconnect_track) to { launchReconnectTrack(track) })
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
            withMusicController { if (index < it.mediaItemCount) it.moveMediaItem(index, index - 1) }
        })
        if (index < queueTracks.lastIndex) actions.add(getString(R.string.move_down) to {
            withMusicController { if (index + 1 < it.mediaItemCount) it.moveMediaItem(index, index + 1) }
        })
        actions.add(getString(R.string.remove_queue) to {
            withMusicController { if (index < it.mediaItemCount) it.removeMediaItem(index) }
        })
        actions.add(getString(R.string.add_playlist) to { choosePlaylist(listOf(track)) })
        actions.add(getString(R.string.lyrics_download) to { model.downloadLyrics(track.title, listOf(track)) })
        actions.add(getString(R.string.details) to { detailsDialog(track) })
        choiceDialog(track.title, actions)
    }

    private fun playlistMenu(playlist: LocalPlaylist) {
        val actions = mutableListOf(
            getString(R.string.play_all) to {
                model.requestPlay(LibraryEdits.playlistTracks(model.library.value, playlist.id),
                    collection = PlaybackCollection.playlist(playlist.id))
            },
            getString(R.string.lyrics_download) to { model.downloadPlaylistLyrics(playlist) },
            getString(R.string.restore_position) to { model.restorePosition(PlaybackCollection.playlist(playlist.id)); Unit },
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
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
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
                    (dialog as PlayerDialog).listView.setItemChecked(which, true)
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
            val roots = sources.values.filterNot { it.needsReconnect }
                .map { BrowserLocation(it.uri, model.repository.documents.rootId(it.uri), sourceName(it)) }
            val current = model.browser.value.path.lastOrNull()
            val candidates = playlist?.folders?.map { it.location() }.orEmpty() + listOfNotNull(current) + roots
            val resolvedPaths = candidates.filter { it.documentId.isNotEmpty() }.distinctBy { it.key }.groupBy { it.pathKey }
            val choices = candidates.map {
                if (it.documentId.isEmpty()) resolvedPaths[it.pathKey]?.singleOrNull() ?: it else it
            }.distinctBy { it.key }
            if (choices.isEmpty()) {
                model.app.inform(getString(R.string.select_folders_first))
                return@operation
            }
            val existing = BooleanArray(choices.size) { index -> playlist?.folders?.any { it.matches(choices[index]) } == true }
            val selected = BooleanArray(choices.size) { index ->
                val location = choices[index]
                when {
                    playlist != null -> existing[index]
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
                        existing[index]
                    ) {
                        selected[index] = true
                        (dialog as PlayerDialog).listView.setItemChecked(index, true)
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
        var displayed = track
        fun value(text: String) = text.ifBlank { getString(R.string.not_available) }
        fun details(location: String): String = getString(
            R.string.song_details, value(displayed.title), value(displayed.artist), value(displayed.album),
            if (displayed.durationMs > 0) TimeText.format(displayed.durationMs) else getString(R.string.not_available),
            value(displayed.trackNumber), value(displayed.year), value(displayed.genre), value(displayed.fileName),
            value(displayed.mimeType), location,
        )
        val warning = if (track.uri == artworkUri) artworkWarning else null
        val dialog = AccentTheme.dialog(this).setTitle(R.string.details)
            .setMessage(details(getString(R.string.loading)))
            .setPositiveButton(R.string.close, null).show()
        val job = contentScope.launch {
            try {
                val result = model.repository.metadataForDetails(track)
                displayed = result.track
                if (result.warning) model.app.inform(getString(R.string.metadata_warning))
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                Log.w("SongDetails", "Cannot refresh indexed metadata", error)
                model.app.report(getString(R.string.metadata_warning))
            } catch (error: SecurityException) {
                Log.w("SongDetails", "Metadata permission is unavailable", error)
                model.app.report(getString(R.string.permission_lost))
            } catch (error: IllegalArgumentException) {
                Log.w("SongDetails", "Provider rejected metadata lookup", error)
                model.app.report(getString(R.string.metadata_warning))
            } catch (error: IllegalStateException) {
                Log.w("SongDetails", "Provider is unavailable for metadata", error)
                model.app.report(getString(R.string.metadata_warning))
            }
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
                dialog.makeMessageSelectable()
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
                changeLanguage(tags[index])
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun changeLanguage(tags: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
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
                    (dialog as PlayerDialog).listView.setItemChecked(index, true)
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

    private fun choiceDialog(title: String, actions: List<Pair<String, () -> Unit>>, show: Boolean = true): PlayerDialog =
        AccentTheme.dialog(this).setTitle(title)
            .setItems(actions.map { it.first }.toTypedArray()) { _, index -> actions[index].second() }
            .setNegativeButton(R.string.cancel, null).create().also { if (show) it.show() }

    private fun renderNotice(notice: Notice?) {
        binding.noticeHost.render(notice)
    }

    private fun tint(active: Boolean): ColorStateList = PlayerIcons.tint(this,
        AccentTheme.color(binding.root, if (active) com.google.android.material.R.attr.colorPrimary
        else com.google.android.material.R.attr.colorOnSurfaceVariant),
    )

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()
}
