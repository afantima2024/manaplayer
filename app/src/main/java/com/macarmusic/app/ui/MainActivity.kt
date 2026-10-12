package com.macarmusic.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.common.util.concurrent.ListenableFuture
import com.macarmusic.app.R
import com.macarmusic.app.databinding.ActivityMainBinding
import com.macarmusic.app.databinding.ViewNowPlayingBinding
import com.macarmusic.app.databinding.ViewSettingsBinding
import com.macarmusic.app.model.AudioFiles
import com.macarmusic.app.model.DocumentEntry
import com.macarmusic.app.model.FolderSource
import com.macarmusic.app.model.LibraryEdits
import com.macarmusic.app.model.LocalPlaylist
import com.macarmusic.app.model.LoopMode
import com.macarmusic.app.model.Notice
import com.macarmusic.app.model.PlayOrder
import com.macarmusic.app.model.PlaybackScope
import com.macarmusic.app.model.SortOrder
import com.macarmusic.app.model.Track
import com.macarmusic.app.playback.PlaybackService
import com.macarmusic.app.playback.toMediaItem
import com.macarmusic.app.playback.toTrack
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

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
    private lateinit var folderUp: MaterialButton
    private lateinit var folderPlay: MaterialButton
    private lateinit var folderRescan: MaterialButton
    private lateinit var cancelScan: MaterialButton
    private lateinit var playlistBack: MaterialButton
    private lateinit var playlistPlay: MaterialButton
    private lateinit var playlistAdd: MaterialButton
    private lateinit var audio: AudioManager
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var artworkJob: Job? = null
    private var artworkUri: String? = null
    private var artworkWarning: String? = null
    private var draggingSeek = false
    private var shownNotice: Notice? = null
    private var playlistTrackRows = emptyList<Track>()
    private var queueTracks = emptyList<Track>()
    private var queueSignature: List<Pair<String, String>> = emptyList()

    private val pickFolder = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri == null) model.app.report(getString(R.string.picker_no_document))
            else model.addFolder(uri, result.data?.flags ?: 0)
        }
    }

    private val pickFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri == null) model.app.report(getString(R.string.picker_no_document))
            else model.openFile(uri, result.data?.flags ?: 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, binding.root).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            WindowInsetsCompat.CONSUMED
        }
        audio = getSystemService(AudioManager::class.java)
        volumeControlStream = AudioManager.STREAM_MUSIC
        buildScreens()
        configurePlaybackControls()
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> model.openSettings()
                R.id.action_about -> MaterialAlertDialogBuilder(this).setTitle(R.string.about)
                    .setMessage(R.string.about_body).setPositiveButton(R.string.close, null).show()
                else -> return@setOnMenuItemClickListener false
            }
            binding.toolbar.setNavigationOnClickListener { model.closeSettings() }
            true
        }
        binding.navigation.setOnItemSelectedListener { item -> model.screen(item.itemId); true }
        binding.miniInfo.setOnClickListener { model.screen(R.id.nav_player) }
        binding.miniArtwork.setOnClickListener { model.screen(R.id.nav_player) }
        binding.dismissNotice.setOnClickListener { shownNotice?.let { model.app.dismissNotice(it.id) } }
        onBackPressedDispatcher.addCallback(this) {
            when {
                model.selectedScreen.value == R.id.action_settings -> model.closeSettings()
                model.selectedScreen.value == R.id.nav_folders && model.browserUp() -> Unit
                model.selectedScreen.value == R.id.nav_playlists && model.selectedPlaylist.value != null -> model.playlist(null)
                model.selectedScreen.value != R.id.nav_player -> model.screen(R.id.nav_player)
                else -> finish()
            }
        }
        observeState()
        showScreen(model.selectedScreen.value)
        updatePlayer()
        val preferences = getSharedPreferences("settings", MODE_PRIVATE)
        if (!preferences.getBoolean("parkedNoticeSeen", false)) {
            MaterialAlertDialogBuilder(this).setTitle(R.string.parked_title).setMessage(R.string.parked_body)
                .setPositiveButton(R.string.understood) { _, _ ->
                    preferences.edit().putBoolean("parkedNoticeSeen", true).apply()
                }.show()
        }
    }

    override fun onStart() {
        super.onStart()
        connectController()
    }

    override fun onStop() {
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        super.onStop()
    }

    override fun onDestroy() {
        artworkJob?.cancel()
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.selectedScreen.collect(::showScreen) }
                launch { model.visibleTracks.collect { renderLibrary() } }
                launch { model.favoritesOnly.collect {
                    favoriteFilter.setText(if (it) R.string.all_tracks else R.string.favorites_only)
                    renderLibrary()
                } }
                launch { model.library.collect {
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
                launch {
                    while (isActive) {
                        updateProgress()
                        updateVolume()
                        delay(500)
                    }
                }
            }
        }
    }

    private fun buildScreens() {
        now = ViewNowPlayingBinding.inflate(layoutInflater, binding.screenContainer, false)
        binding.screenContainer.addView(now.root)
        buildLibrary()
        buildFolders()
        buildQueue()
        buildPlaylists()
        buildSettings()
        listOf(libraryPanel, folderPanel, queuePanel, playlistPanel).forEach {
            binding.screenContainer.addView(it, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT,
            ))
        }
    }

    private fun buildLibrary() {
        libraryPanel = ListPanel(this)
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
            minHeight = dp(56)
            doAfterTextChanged { model.query(it?.toString().orEmpty()) }
        }
        searchLayout.addView(search)
        libraryPanel.top.addView(searchLayout)
        libraryPlay = libraryPanel.action(R.string.play_all) { model.requestPlay(model.visibleTracks.value, useLoopScope = true) }
        favoriteFilter = libraryPanel.action(R.string.favorites_only) { model.toggleFavorites() }
        libraryPanel.actionRow(
            libraryPlay,
            favoriteFilter,
            libraryPanel.action(R.string.sort) { sortDialog() },
        )
        libraryAdapter = MusicRowAdapter(
            onClick = { row ->
                val tracks = model.visibleTracks.value
                val index = tracks.indexOfFirst { it.id == row.key }
                if (index >= 0) model.requestPlay(tracks, index, useLoopScope = true)
            },
            onMore = { row -> findTrack(row.key)?.let(::trackMenu) },
        )
        libraryPanel.list.adapter = libraryAdapter
    }

    private fun buildFolders() {
        folderPanel = ListPanel(this)
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
            folderUp,
            folderPlay,
            folderRescan,
            cancelScan,
        )
        folderAdapter = MusicRowAdapter(
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
        queuePanel = ListPanel(this)
        queuePanel.actionRow(
            queuePanel.action(R.string.save_queue) {
                if (queueTracks.isEmpty()) model.app.inform(getString(R.string.no_tracks_to_play))
                else newPlaylistDialog(queueTracks)
            },
            queuePanel.action(R.string.clear_queue) {
                MaterialAlertDialogBuilder(this).setTitle(R.string.clear_queue)
                    .setMessage(R.string.clear_queue_body)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.clear_queue) { _, _ ->
                        withController { it.pause(); it.clearMediaItems() }
                    }.show()
            },
        )
        queueAdapter = MusicRowAdapter(
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
        playlistPanel = ListPanel(this)
        playlistBack = playlistPanel.action(R.string.back_playlists) { model.playlist(null) }
        playlistPlay = playlistPanel.action(R.string.play_all) { model.requestPlay(playlistTrackRows) }
        playlistAdd = playlistPanel.action(R.string.add_songs) { addSongsDialog() }
        playlistPanel.actionRow(
            playlistPanel.action(R.string.create_playlist) { newPlaylistDialog() },
            playlistBack, playlistPlay, playlistAdd,
        )
        playlistAdapter = MusicRowAdapter(
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
        binding.screenContainer.addView(settings.root)
        settings.settingsLanguage.setOnClickListener { languageDialog() }
        settings.settingsTheme.setOnClickListener { themeDialog() }
        settings.settingsLoop.setOnClickListener { loopDialog() }
        settings.settingsOrder.setOnClickListener { orderDialog() }
        settings.settingsFolders.setOnClickListener { foldersDialog() }
        settings.settingsAddFolder.setOnClickListener { launchFolderPicker() }
        settings.settingsPlayFolders.setOnClickListener { model.playSelectedFolders() }
        settings.settingsManageFolders.setOnClickListener { model.screen(R.id.nav_folders) }
        renderSettings()
    }

    private fun renderSettings() {
        val options = model.app.playbackSettings.options.value
        val language = when (AppCompatDelegate.getApplicationLocales()[0]?.language) {
            "en" -> R.string.language_english
            "zh" -> R.string.language_chinese
            else -> R.string.language_system
        }
        val theme = when (model.app.themeMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.string.theme_day
            AppCompatDelegate.MODE_NIGHT_YES -> R.string.theme_night
            else -> R.string.theme_system
        }
        settings.settingsLanguage.text = getString(R.string.setting_value, getString(R.string.language), getString(language))
        settings.settingsTheme.text = getString(R.string.setting_value, getString(R.string.theme), getString(theme))
        settings.settingsLoop.text = getString(R.string.setting_value, getString(R.string.loop_mode), getString(loopLabel(options.loop)))
        settings.settingsOrder.text = getString(R.string.setting_value, getString(R.string.play_order), getString(orderLabel(options.order)))
        val sources = model.library.value.sources
        val selected = PlaybackScope.selectedSources(model.library.value)
        settings.settingsFolders.text = getString(R.string.setting_value, getString(R.string.select_folders),
            getString(R.string.selected_folder_count, selected.size, sources.size))
        settings.settingsFolderSummary.text = if (selected.isEmpty()) getString(R.string.no_folders_selected)
            else selected.joinToString(" · ") { it.name }
        settings.settingsFolders.isEnabled = model.repository.isReadable && model.scan.value == null
        settings.settingsAddFolder.isEnabled = model.repository.isReadable && model.scan.value == null
        settings.settingsPlayFolders.isEnabled = selected.isNotEmpty() && model.scan.value == null
    }

    private fun showScreen(id: Int) {
        val target = when (id) {
            R.id.nav_library -> libraryPanel
            R.id.nav_folders -> folderPanel
            R.id.nav_queue -> queuePanel
            R.id.nav_playlists -> playlistPanel
            R.id.action_settings -> settings.root
            else -> now.root
        }
        for (index in 0 until binding.screenContainer.childCount) {
            val child = binding.screenContainer.getChildAt(index)
            child.isVisible = child === target
        }
        val inSettings = id == R.id.action_settings
        binding.navigation.isVisible = !inSettings
        if (!inSettings && binding.navigation.selectedItemId != id) binding.navigation.selectedItemId = id
        binding.toolbar.setTitle(if (inSettings) R.string.settings else R.string.app_name)
        binding.toolbar.menu.findItem(R.id.action_settings).isVisible = !inSettings
        if (inSettings) {
            binding.toolbar.setNavigationIcon(R.drawable.ic_back)
            binding.toolbar.setNavigationIconTint(MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface))
            binding.toolbar.setNavigationContentDescription(R.string.back)
        } else binding.toolbar.navigationIcon = null
        binding.miniPlayer.isVisible = target !== now.root
        updatePlayer()
    }

    private fun renderLibrary() {
        val tracks = model.visibleTracks.value
        libraryAdapter.submitList(tracks.map { trackRow(it) })
        libraryPanel.heading.text = resources.getQuantityString(R.plurals.track_count, tracks.size, tracks.size)
        libraryPlay.isEnabled = tracks.isNotEmpty()
        val message = when {
            !model.repository.loaded.value -> R.string.loading
            model.library.value.tracks.isEmpty() -> R.string.library_empty
            else -> R.string.search_empty
        }
        libraryPanel.empty(getString(message), tracks.isEmpty())
    }

    private fun renderFolders() {
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
            else -> state.path.joinToString("  /  ") { it.name }
        }
        val rows = if (state.path.isEmpty()) {
            model.library.value.sources.map { source ->
                val status = when {
                    source.unavailable -> getString(R.string.source_unavailable)
                    !source.persisted -> getString(R.string.access_temporary)
                    else -> getString(R.string.access_saved)
                }
                MusicRow(
                    source.uri, source.name,
                    listOf(
                        source.lastIssue ?: getString(R.string.saved_folder_detail, source.lastScanCount, status),
                        getString(if (source.selectedForPlayback) R.string.folder_selected else R.string.folder_not_selected),
                    ).joinToString("\n"),
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
    }

    private fun renderPlaylists() {
        val id = model.selectedPlaylist.value
        val library = model.library.value
        val playlist = library.playlists.firstOrNull { it.id == id }
        playlistBack.isVisible = id != null
        playlistPlay.isVisible = playlist != null
        playlistAdd.isVisible = playlist != null
        playlistTrackRows = if (id == null) emptyList() else LibraryEdits.playlistTracks(library, id)
        playlistPlay.isEnabled = playlistTrackRows.isNotEmpty()
        val rows = if (id == null) library.playlists.map {
            MusicRow(it.id, it.name, resources.getQuantityString(R.plurals.track_count, it.trackIds.size, it.trackIds.size), icon = R.drawable.ic_playlist)
        } else playlistTrackRows.map { trackRow(it) }
        playlistAdapter.submitList(rows)
        playlistPanel.heading.text = playlist?.name ?: getString(R.string.playlists)
        playlistPanel.empty(getString(if (id == null) R.string.playlists_empty else R.string.playlist_empty), rows.isEmpty())
    }

    private fun updateQueue(force: Boolean = false) {
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
        })
        queuePanel.heading.text = resources.getQuantityString(R.plurals.track_count, queueTracks.size, queueTracks.size)
        queuePanel.empty(
            getString(if (player == null) R.string.connecting else R.string.queue_empty),
            queueTracks.isEmpty(),
        )
    }

    private fun configurePlaybackControls() {
        now.controls.play.setOnClickListener { togglePlay() }
        binding.miniPlay.setOnClickListener { togglePlay() }
        now.controls.previous.setOnClickListener { withController { it.seekToPrevious() } }
        now.controls.next.setOnClickListener { withController { it.seekToNextMediaItem() } }
        binding.miniNext.setOnClickListener { withController { it.seekToNextMediaItem() } }
        now.controls.shuffle.setOnClickListener {
            val options = model.app.playbackSettings.options.value
            model.app.playbackSettings.setOrder(if (options.order == PlayOrder.RANDOM) PlayOrder.SEQUENTIAL else PlayOrder.RANDOM)
        }
        now.controls.repeat.setOnClickListener { loopDialog() }
        now.controls.favorite.setOnClickListener { currentTrack()?.let(model::favorite) }
        now.controls.details.setOnClickListener { currentTrack()?.let(::detailsDialog) }
        now.controls.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) { draggingSeek = true }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                withController { player ->
                    val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: return@withController
                    player.seekTo(duration * seekBar.progress / 10_000)
                }
                draggingSeek = false
            }
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = controller?.duration?.takeIf { it > 0 } ?: return
                    now.controls.position.text = TimeText.format(duration * progress / 10_000)
                }
            }
        })
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
        val track = currentTrack()
        val title = track?.title?.ifBlank { track.fileName } ?: getString(R.string.no_track)
        val artist = track?.artist?.ifBlank { getString(R.string.unknown_artist) } ?: getString(R.string.no_track_hint)
        now.controls.title.text = title
        now.controls.artist.text = artist
        now.controls.album.text = track?.album?.ifBlank { getString(R.string.unknown_album) }.orEmpty()
        binding.miniTitle.text = title
        binding.miniArtist.text = artist
        val wantsPlay = player?.playWhenReady == true
        val icon = if (wantsPlay) R.drawable.ic_pause else R.drawable.ic_play
        val description = getString(if (wantsPlay) R.string.pause else R.string.play)
        listOf(now.controls.play, binding.miniPlay).forEach {
            it.setImageResource(icon)
            it.contentDescription = description
            it.isEnabled = player != null
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
        now.controls.favorite.setText(if (favored) R.string.unfavorite else R.string.favorite)
        now.controls.favorite.setIconResource(if (favored) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
        now.controls.favorite.isEnabled = track != null && findTrack(track.id) != null
        now.controls.details.isEnabled = track != null
        val status = when {
            player == null -> getString(R.string.connecting)
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
        updateArtwork(track?.uri)
    }

    private fun updateProgress() {
        val player = controller
        val duration = player?.duration?.takeIf { it > 0 && it != C.TIME_UNSET }
        val fallback = currentTrack()?.durationMs?.takeIf { it > 0 }
        now.controls.duration.text = (duration ?: fallback)?.let(TimeText::format) ?: getString(R.string.empty_time)
        now.controls.seek.isEnabled = duration != null && player?.isCurrentMediaItemSeekable == true
        if (!draggingSeek) {
            val position = player?.currentPosition?.coerceAtLeast(0) ?: 0
            now.controls.position.text = TimeText.format(position)
            now.controls.seek.progress = if (duration != null) ((position * 10_000) / duration).coerceIn(0, 10_000).toInt() else 0
        }
    }

    private fun updateVolume() {
        now.controls.volume.progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        now.controls.volumeValue.text = getString(R.string.volume_level, now.controls.volume.progress)
    }

    private fun updateArtwork(uri: String?) {
        if (artworkUri == uri) return
        artworkUri = uri
        artworkWarning = null
        artworkJob?.cancel()
        now.artwork.setImageResource(R.drawable.ic_launcher)
        binding.miniArtwork.setImageResource(R.drawable.ic_launcher)
        if (uri.isNullOrBlank()) return
        artworkJob = lifecycleScope.launch {
            val cover = model.app.artwork.load(uri)
            if (artworkUri == uri) {
                artworkWarning = cover.warning
                cover.bitmap?.let {
                    now.artwork.setImageBitmap(it)
                    binding.miniArtwork.setImageBitmap(it)
                }
            }
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
            model.app.report(getString(R.string.picker_missing))
        }
    }

    private fun launchFilePicker() {
        try {
            pickFile.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                // Some USB providers mislabel audio as application/octet-stream; validate after selection.
                type = "*/*"
                putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            })
        } catch (error: ActivityNotFoundException) {
            model.app.report(getString(R.string.picker_missing))
        }
    }

    private fun currentSource(): FolderSource? = model.browser.value.path.lastOrNull()?.sourceUri?.let { uri ->
        model.library.value.sources.firstOrNull { it.uri == uri }
    }

    private fun playCurrentFolder() {
        val source = currentSource() ?: return
        model.scanFolders(listOf(source), model.browser.value.path.lastOrNull(), play = true)
    }

    private fun sourceMenu(source: FolderSource) = choiceDialog(
        source.name,
        listOf(
            getString(R.string.browse) to { model.openSource(source) },
            getString(R.string.rescan) to { model.scanFolders(listOf(source)) },
            getString(R.string.play_recursively) to { model.scanFolders(listOf(source), play = true) },
            getString(R.string.remove_source) to {
                MaterialAlertDialogBuilder(this).setTitle(R.string.remove_source)
                    .setMessage(getString(R.string.remove_source_body, source.name))
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
                        val location = com.macarmusic.app.model.BrowserLocation(source.uri, entry.id, entry.name)
                        model.scanFolders(listOf(source), location, play = true)
                    }
                },
            ))
        } else {
            val source = currentSource() ?: return
            lifecycleScope.launch {
                model.guard {
                    val track = model.repository.browserTrack(entry.uri, source.uri).track
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
            MaterialAlertDialogBuilder(this).setTitle(R.string.remove_track)
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

    private fun playlistMenu(playlist: LocalPlaylist) = choiceDialog(
        playlist.name,
        listOf(
            getString(R.string.play_all) to { model.requestPlay(LibraryEdits.playlistTracks(model.library.value, playlist.id)) },
            getString(R.string.add_songs) to { model.playlist(playlist.id); addSongsDialog() },
            getString(R.string.delete_playlist) to {
                MaterialAlertDialogBuilder(this).setTitle(R.string.delete_playlist)
                    .setMessage(getString(R.string.delete_playlist_body, playlist.name))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.remove) { _, _ -> model.removePlaylist(playlist.id) }.show()
                Unit
            },
        ),
    )

    private fun newPlaylistDialog(tracks: List<Track> = emptyList()) {
        val field = TextInputLayout(this).apply {
            hint = getString(R.string.playlist_name)
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val name = TextInputEditText(field.context).apply {
            setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(80))
        }
        field.addView(name)
        val dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.create_playlist).setView(field)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.create, null).create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = name.text?.toString().orEmpty().trim()
                if (text.isEmpty() || text.length > 80 || model.library.value.playlists.any { it.name.equals(text, true) }) {
                    field.error = getString(R.string.playlist_name_invalid)
                } else {
                    model.createPlaylist(text, tracks)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun choosePlaylist(tracks: List<Track>) {
        val actions = model.library.value.playlists.map { playlist ->
            playlist.name to { model.addToPlaylist(playlist.id, tracks); Unit }
        } + (getString(R.string.create_playlist) to { newPlaylistDialog(tracks) })
        choiceDialog(getString(R.string.add_playlist), actions)
    }

    private fun addSongsDialog() {
        val id = model.selectedPlaylist.value ?: return
        val tracks = AudioFiles.sortAndFilter(model.library.value.tracks, "", SortOrder.TITLE)
        if (tracks.isEmpty()) {
            model.app.inform(getString(R.string.no_tracks_to_play))
            return
        }
        val selected = BooleanArray(tracks.size)
        MaterialAlertDialogBuilder(this).setTitle(R.string.add_songs)
            .setMultiChoiceItems(tracks.map { "${it.title} · ${it.artist}" }.toTypedArray(), selected) { _, which, checked ->
                selected[which] = checked
            }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.add_selected) { _, _ ->
                val chosen = tracks.filterIndexed { index, _ -> selected[index] }
                if (chosen.isNotEmpty()) model.addToPlaylist(id, chosen)
            }.show()
    }

    private fun detailsDialog(track: Track) {
        fun value(text: String) = text.ifBlank { getString(R.string.not_available) }
        val details = getString(
            R.string.song_details, value(track.title), value(track.artist), value(track.album),
            if (track.durationMs > 0) TimeText.format(track.durationMs) else getString(R.string.not_available),
            value(track.trackNumber), value(track.year), value(track.genre), value(track.fileName),
            value(track.mimeType), track.uri,
        )
        val warning = if (track.uri == artworkUri) artworkWarning else null
        MaterialAlertDialogBuilder(this).setTitle(R.string.details)
            .setMessage(if (warning == null) details else "$details\n\n${getString(R.string.artwork_issue, warning)}")
            .setPositiveButton(R.string.close, null).show()
    }

    private fun sortDialog() {
        val labels = intArrayOf(R.string.sort_title, R.string.sort_artist, R.string.sort_album, R.string.sort_file)
        MaterialAlertDialogBuilder(this).setTitle(R.string.sort)
            .setSingleChoiceItems(labels.map(::getString).toTypedArray(), model.sort.value.ordinal) { dialog, index ->
                model.sort(SortOrder.entries[index])
                dialog.dismiss()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun themeDialog() {
        val modes = intArrayOf(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val labels = intArrayOf(R.string.theme_night, R.string.theme_day, R.string.theme_system)
        MaterialAlertDialogBuilder(this).setTitle(R.string.theme)
            .setSingleChoiceItems(labels.map(::getString).toTypedArray(), modes.indexOf(model.app.themeMode)) { dialog, index ->
                dialog.dismiss()
                model.app.themeMode = modes[index]
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun languageDialog() {
        val tags = arrayOf("en", "zh-CN", "")
        val labels = intArrayOf(R.string.language_english, R.string.language_chinese, R.string.language_system)
        val language = AppCompatDelegate.getApplicationLocales()[0]?.language
        val selected = when (language) { "en" -> 0; "zh" -> 1; else -> 2 }
        MaterialAlertDialogBuilder(this).setTitle(R.string.language)
            .setSingleChoiceItems(labels.map(::getString).toTypedArray(), selected) { dialog, index ->
                dialog.dismiss()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[index]))
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun loopDialog() {
        val modes = LoopMode.entries
        MaterialAlertDialogBuilder(this).setTitle(R.string.loop_mode)
            .setSingleChoiceItems(modes.map { getString(loopLabel(it)) }.toTypedArray(),
                modes.indexOf(model.app.playbackSettings.options.value.loop)) { dialog, index ->
                dialog.dismiss()
                model.app.playbackSettings.setLoop(modes[index])
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun orderDialog() {
        val modes = PlayOrder.entries
        MaterialAlertDialogBuilder(this).setTitle(R.string.play_order)
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
        MaterialAlertDialogBuilder(this).setTitle(R.string.select_folders)
            .setMultiChoiceItems(sources.map { getString(R.string.folder_choice, it.name, it.lastScanCount) }.toTypedArray(),
                selected) { _, index, checked -> selected[index] = checked }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.apply) { _, _ ->
                model.selectFolders(sources.filterIndexed { index, _ -> selected[index] }.mapTo(hashSetOf()) { it.uri })
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
        MaterialAlertDialogBuilder(this).setTitle(title)
            .setItems(actions.map { it.first }.toTypedArray()) { _, index -> actions[index].second() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun renderNotice(notice: Notice?) {
        shownNotice = notice
        binding.noticeBar.isVisible = notice != null
        binding.noticeText.text = notice?.text
        if (notice != null) {
            val background = MaterialColors.getColor(binding.root,
                if (notice.isError) com.google.android.material.R.attr.colorErrorContainer
                else com.google.android.material.R.attr.colorPrimaryContainer)
            val foreground = MaterialColors.getColor(binding.root,
                if (notice.isError) com.google.android.material.R.attr.colorOnErrorContainer
                else com.google.android.material.R.attr.colorOnPrimaryContainer)
            binding.noticeBar.setBackgroundColor(background)
            binding.noticeText.setTextColor(foreground)
            binding.dismissNotice.imageTintList = ColorStateList.valueOf(foreground)
        }
    }

    private fun tint(active: Boolean): ColorStateList = ColorStateList.valueOf(
        MaterialColors.getColor(binding.root, if (active) com.google.android.material.R.attr.colorPrimary
        else com.google.android.material.R.attr.colorOnSurfaceVariant),
    )

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()
}
