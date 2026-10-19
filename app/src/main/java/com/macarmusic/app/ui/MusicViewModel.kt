package com.macarmusic.app.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.macarmusic.app.MaCarMusicApp
import com.macarmusic.app.R
import com.macarmusic.app.data.MediaStoreAccess
import com.macarmusic.app.model.AudioFiles
import com.macarmusic.app.model.BrowserLocation
import com.macarmusic.app.model.BrowserState
import com.macarmusic.app.model.DocumentEntry
import com.macarmusic.app.model.FolderSource
import com.macarmusic.app.model.LibraryEdits
import com.macarmusic.app.model.LoopMode
import com.macarmusic.app.model.PlayOrder
import com.macarmusic.app.model.PlaybackScope
import com.macarmusic.app.model.ScanProgress
import com.macarmusic.app.model.ScanResult
import com.macarmusic.app.model.SortOrder
import com.macarmusic.app.model.Track
import com.macarmusic.app.model.PlaylistFolder
import com.macarmusic.app.model.PinnedFolderException
import com.macarmusic.app.model.LibraryBrowsing
import com.macarmusic.app.model.LibraryLocation
import com.macarmusic.app.model.LibraryMode
import com.macarmusic.app.model.LibraryPage
import com.macarmusic.app.model.PlaybackCollection
import com.macarmusic.app.model.CollectionKind
import com.macarmusic.app.model.CollectionPositions
import com.macarmusic.app.model.BackupEdits
import com.macarmusic.app.data.PreparedBackup
import com.macarmusic.app.data.BackupRestoreResult
import com.macarmusic.app.lyrics.LyricState
import com.macarmusic.app.lyrics.LyricResult
import com.macarmusic.app.lyrics.LyricIssue
import com.macarmusic.app.lyrics.LyricTiming
import com.macarmusic.app.lyrics.CachedLyric
import com.macarmusic.app.lyrics.LyricChoices
import com.macarmusic.app.lyrics.LyricRemovalFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import kotlin.random.Random

enum class QueueMode { REPLACE, APPEND, NEXT }
data class PlayRequest(
    val token: Long, val tracks: List<Track>, val index: Int, val mode: QueueMode,
    val positionMs: Long = 0, val playWhenReady: Boolean = true, val collection: PlaybackCollection? = null,
)
data class DeviceMusicPicker(
    val token: Long,
    val loading: Boolean = true,
    val folders: List<FolderSource> = emptyList(),
    val selected: Set<String> = emptySet(),
    val browse: Boolean = false,
)

class MusicViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    val app = application as MaCarMusicApp
    val repository = app.repository
    val library = repository.library
    val selectedScreen = saved.getStateFlow("screen", R.id.nav_player)
    val settingsCategory = saved.run {
        get<SettingsCategory>("settingsCategory")?.let { set("settingsCategory", it.current) }
        getStateFlow("settingsCategory", SettingsCategory.GENERAL)
    }
    val query = saved.getStateFlow("query", "")
    val favoritesOnly = saved.getStateFlow("favorites", false)
    val selectedPlaylist = saved.getStateFlow<String?>("playlist", null)
    // Large selections must not travel in Android's size-limited activity saved-state Bundle.
    private val mutableLibrarySelecting = MutableStateFlow(false)
    val librarySelecting = mutableLibrarySelecting.asStateFlow()
    private val mutableLibrarySelection = MutableStateFlow<Set<String>>(emptySet())
    val librarySelection = mutableLibrarySelection.asStateFlow()
    private val mutableSort = MutableStateFlow(app.sortOrder)
    val sort = mutableSort.asStateFlow()
    private val libraryLocation = saved.getStateFlow("libraryLocation", LibraryLocation())
    val libraryPage = combine(library, query, favoritesOnly, sort, libraryLocation) { library, query, favorites, sort, location ->
        LibraryBrowsing.page(library, query, favorites, sort, location)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, replayExpirationMillis = 0), LibraryPage())
    private val mutableBrowser = MutableStateFlow(BrowserState())
    val browser = mutableBrowser.asStateFlow()
    private val mutableScan = MutableStateFlow<ScanProgress?>(null)
    val scan = mutableScan.asStateFlow()
    private val mutableRequest = MutableStateFlow<PlayRequest?>(null)
    val playRequest = mutableRequest.asStateFlow()
    private var requestToken = 0L
    private var browserJob: Job? = null
    private var scanJob: Job? = null
    private var deviceMusicJob: Job? = null
    private var openFileJob: Job? = null
    private var deviceMusicToken = 0L
    private val mutableDeviceMusic = MutableStateFlow<DeviceMusicPicker?>(null)
    val deviceMusic = mutableDeviceMusic.asStateFlow()
    private var lyricJob: Job? = null
    private var lyricPreviewJob: Job? = null
    private var lyricChoicesJob: Job? = null
    private var lyricEditTrackId: String? = null
    private var manualLyrics: LyricState? = null
    private var manualCacheGeneration = 0L
    private var manualPlaybackGeneration = 0L
    private var lyricTrack: Track? = null
    private data class LyricRequest(val track: Track?, val enabled: Boolean, val online: Boolean, val allowNetwork: Boolean)
    private var lyricRequest: LyricRequest? = null
    internal var lyricsActive = false
        private set
    private val mutableLyrics = MutableStateFlow(LyricState())
    val lyrics = mutableLyrics.asStateFlow()
    private val mutableLyricChoices = MutableStateFlow<LyricChoices?>(null)
    val lyricChoices = mutableLyricChoices.asStateFlow()
    private val mutableImportingBackground = MutableStateFlow(false)
    val importingBackground = mutableImportingBackground.asStateFlow()
    private val mutableLyricCacheBusy = MutableStateFlow(false)
    val lyricCacheBusy = mutableLyricCacheBusy.asStateFlow()
    private val mutableBackupBusy = MutableStateFlow(false)
    val backupBusy = mutableBackupBusy.asStateFlow()
    private val mutablePreparedBackup = MutableStateFlow<PreparedBackup?>(null)
    val preparedBackup = mutablePreparedBackup.asStateFlow()
    private val mutableBackupRestored = MutableStateFlow<BackupRestoreResult?>(null)
    val backupRestored = mutableBackupRestored.asStateFlow()
    private val mutableSharedBackup = MutableStateFlow<Uri?>(null)
    val sharedBackup = mutableSharedBackup.asStateFlow()

    init {
        viewModelScope.launch {
            app.displaySettings.options.map { Triple(it.lyricsEnabled, it.onlineLyrics, it.lightMode) }
                .distinctUntilChanged().collect {
                requestLyrics(lyricTrack)
            }
        }
    }

    fun setLyricsActive(active: Boolean) {
        if (lyricsActive == active) return
        lyricsActive = active
        requestLyrics(lyricTrack)
    }

    fun requestLyrics(track: Track?, refresh: Boolean = false) {
        if (track?.id != lyricTrack?.id) {
            manualLyrics = null
            closeLyricVersions()
        }
        lyricTrack = track
        val settings = app.displaySettings.options.value
        val request = LyricRequest(track, lyricsActive && settings.lyricsEnabled, settings.onlineLyrics, !settings.lightMode)
        if (!refresh && lyricRequest == request) return
        lyricRequest = request
        lyricJob?.cancel()
        lyricPreviewJob?.cancel()
        if (!request.enabled || track == null) {
            if (!settings.lyricsEnabled || track == null) closeLyricVersions()
            mutableLyrics.value = LyricState()
            return
        }
        if (lyricEditTrackId == track.id) {
            mutableLyrics.value = LyricState(track.id, loading = true,
                result = app.lyrics.latest.value.takeIf { it.trackId == track.id }?.result)
            return
        }
        manualLyrics?.takeIf { !refresh && it.trackId == track.id &&
            manualCacheGeneration == app.lyrics.cache.generation &&
            manualPlaybackGeneration == app.lyrics.playbackGeneration &&
            (request.online || it.result?.availableOffline == true) }?.let {
            mutableLyrics.value = it
            return
        }
        mutableLyrics.value = LyricState(track.id, loading = true)
        lyricJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    app.lyrics.load(track, request.online, refresh, allowNetwork = request.allowNetwork)
                }
                if (lyricRequest == request) mutableLyrics.value = LyricState(track.id, result = result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                lyricFailure(track, error)
            } catch (error: SecurityException) {
                lyricFailure(track, error)
            }
        }
    }

    private fun lyricFailure(track: Track, error: Exception) {
        Log.w(TAG, "Could not load lyrics", error)
        mutableLyrics.value = LyricState(track.id, result = LyricResult(issue = LyricIssue.LOOKUP_FAILED))
    }

    fun nextLyrics() {
        val track = lyricTrack
        val previous = lyrics.value
        val display = app.displaySettings.options.value
        if (!display.onlineLyrics || display.lightMode) {
            app.inform(app.text(R.string.lyrics_next_disabled))
            return
        }
        if (track == null || previous.trackId != track.id || previous.result?.lyrics == null) {
            app.inform(app.text(R.string.lyrics_not_found))
            return
        }
        if (previous.loading || lyricCacheBusy.value || backupBusy.value) {
            app.inform(app.text(R.string.lyrics_cache_busy))
            return
        }
        lyricJob?.cancel()
        lyricPreviewJob?.cancel()
        mutableLyrics.value = previous.copy(loading = true)
        lyricJob = viewModelScope.launch {
            try {
                val result = app.lyrics.next(track, previous.result)
                currentCoroutineContext().ensureActive()
                mutableLyrics.value = previous.copy(result = result ?: previous.result)
                if (result != null) rememberLyrics(track.id, result)
                app.inform(app.text(if (result == null) R.string.lyrics_no_more_matches else R.string.lyrics_match_saved))
            } catch (error: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                lyricEditFailure(previous, R.string.lyrics_next_failed, error)
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                lyricEditFailure(previous, R.string.lyrics_next_failed, error)
            } catch (error: SecurityException) {
                lyricEditFailure(previous, R.string.lyrics_next_failed, error)
            }
        }
    }

    fun previewLyricTiming(trackId: String, original: LyricResult, offsetMs: Long) {
        LyricTiming.requireOffset(offsetMs)
        val document = original.document ?: return
        lyricPreviewJob?.cancel()
        lyricPreviewJob = viewModelScope.launch {
            val preview = withContext(Dispatchers.Default) {
                document.copy(offsetMs = offsetMs).result(original.cached, original.issue)
            }
            if (sameLyric(trackId, document) && !lyrics.value.loading) {
                mutableLyrics.value = LyricState(trackId, result = preview)
            }
        }
    }

    fun cancelLyricTiming(trackId: String, original: LyricResult) {
        lyricPreviewJob?.cancel()
        if (original.document?.let { sameLyric(trackId, it) } == true && !lyrics.value.loading) {
            mutableLyrics.value = LyricState(trackId, result = original)
        }
    }

    fun saveLyricTiming(trackId: String, original: LyricResult, offsetMs: Long): Boolean {
        val track = lyricTrack
        val document = original.document
        if (track == null || document == null || !sameLyric(trackId, document)) {
            app.inform(app.text(R.string.lyrics_song_changed))
            return false
        }
        if (lyrics.value.loading || lyricCacheBusy.value || backupBusy.value) {
            app.inform(app.text(R.string.lyrics_cache_busy))
            return false
        }
        lyricPreviewJob?.cancel()
        lyricJob?.cancel()
        val previous = LyricState(trackId, result = original)
        mutableLyrics.value = lyrics.value.copy(loading = true)
        lyricJob = viewModelScope.launch {
            try {
                val result = app.lyrics.saveTiming(track, document, offsetMs)
                currentCoroutineContext().ensureActive()
                mutableLyrics.value = LyricState(trackId, result = result)
                rememberLyrics(trackId, result)
                app.inform(app.text(R.string.lyrics_timing_saved))
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                lyricEditFailure(previous, R.string.lyrics_timing_failed, error)
            } catch (error: SecurityException) {
                lyricEditFailure(previous, R.string.lyrics_timing_failed, error)
            }
        }
        return true
    }

    private fun sameLyric(trackId: String, document: CachedLyric): Boolean {
        val current = lyrics.value
        val source = current.result?.document
        return current.trackId == trackId && source?.text == document.text && source.origin == document.origin &&
            source.history == document.history
    }

    private fun lyricEditFailure(previous: LyricState, message: Int, error: Exception) {
        Log.w(TAG, "Could not complete the lyric change", error)
        if (lyricTrack?.id == previous.trackId) mutableLyrics.value = previous
        app.report(app.text(message))
    }

    private fun rememberLyrics(trackId: String, result: LyricResult) {
        manualLyrics = LyricState(trackId, result = result)
        manualCacheGeneration = app.lyrics.cache.generation
        manualPlaybackGeneration = app.lyrics.playbackGeneration
    }

    fun showLyricVersions() {
        val track = lyricTrack
        if (track == null || !app.displaySettings.options.value.lyricsEnabled) {
            app.inform(app.text(R.string.lyrics_not_found))
            return
        }
        if (lyricEditTrackId != null || lyricChoices.value?.busy == true || lyricCacheBusy.value || backupBusy.value) {
            app.inform(app.text(R.string.lyrics_cache_busy))
            return
        }
        lyricChoicesJob?.cancel()
        mutableLyricChoices.value = LyricChoices(track.id)
        lyricChoicesJob = viewModelScope.launch {
            try {
                val current = lyrics.value.takeIf { it.trackId == track.id }?.result
                val choices = app.lyrics.available(track, current)
                currentCoroutineContext().ensureActive()
                if (lyricTrack?.id == track.id) mutableLyricChoices.value = choices
            } catch (error: CancellationException) { throw error }
            catch (error: IOException) { lyricChoicesFailure(track.id, error) }
            catch (error: SecurityException) { lyricChoicesFailure(track.id, error) }
        }
    }

    fun closeLyricVersions() {
        if (lyricEditTrackId == null) lyricChoicesJob?.cancel()
        mutableLyricChoices.value = null
    }

    private enum class LyricEdit { USE, BEST, REMOVE }

    fun chooseLyricVersion(key: String, best: Boolean = false) =
        editLyricVersion(key, if (best) LyricEdit.BEST else LyricEdit.USE)

    fun removeLyricVersion(key: String) = editLyricVersion(key, LyricEdit.REMOVE)

    private fun editLyricVersion(key: String, edit: LyricEdit) {
        val track = lyricTrack
        val picker = lyricChoices.value
        if (track == null || picker == null || picker.trackId != track.id) {
            app.inform(app.text(R.string.lyrics_song_changed))
            return
        }
        if (lyricEditTrackId != null || picker.loading || picker.busy || lyricCacheBusy.value || backupBusy.value) {
            app.inform(app.text(R.string.lyrics_cache_busy))
            return
        }
        val choice = picker.choices.firstOrNull { it.key == key }
        if (choice == null) {
            app.report(app.text(R.string.lyrics_versions_failed))
            return
        }
        lyricJob?.cancel()
        lyricPreviewJob?.cancel()
        lyricChoicesJob?.cancel()
        val previous = (lyrics.value.takeIf { it.trackId == track.id }
            ?: app.lyrics.latest.value.takeIf { it.trackId == track.id }
            ?: LyricState(track.id)).copy(loading = false)
        mutableLyrics.value = previous.copy(loading = true)
        mutableLyricChoices.value = picker.copy(busy = true)
        lyricEditTrackId = track.id
        lyricChoicesJob = viewModelScope.launch {
            try {
                val removal = if (edit == LyricEdit.REMOVE)
                    app.lyrics.remove(track, choice, previous.result, picker.generation) else null
                val result = when (edit) {
                    LyricEdit.BEST -> app.lyrics.best(track,
                        choice.takeUnless { it.key == picker.bestKey }, previous.result, picker.generation)
                    LyricEdit.USE -> app.lyrics.select(track, choice, picker.generation)
                    LyricEdit.REMOVE -> checkNotNull(removal).result
                }
                currentCoroutineContext().ensureActive()
                if (lyricTrack?.id != track.id) return@launch
                mutableLyrics.value = LyricState(track.id, result = result ?: previous.result)
                if (result != null) rememberLyrics(track.id, result)
                if (removal != null) {
                    mutableLyricChoices.value = removal.choices
                    app.inform(app.text(R.string.lyrics_version_removed))
                } else if (edit == LyricEdit.BEST) {
                    mutableLyricChoices.value = app.lyrics.available(track, result ?: previous.result, picker)
                    app.inform(app.text(if (picker.bestKey == key) R.string.lyrics_best_removed else R.string.lyrics_best_saved))
                } else {
                    mutableLyricChoices.value = null
                }
            } catch (error: CancellationException) {
                if (lyricTrack?.id == track.id && lyrics.value.loading) mutableLyrics.value = previous
                throw error
            } catch (error: LyricRemovalFailure) {
                val removal = error.removal
                lyricEditFailure(LyricState(track.id, result = removal.result), R.string.lyrics_version_delete_failed, error)
                if (lyricTrack?.id == track.id) {
                    rememberLyrics(track.id, removal.result)
                    mutableLyricChoices.value = removal.choices
                }
            } catch (error: IOException) {
                lyricEditFailure(previous, if (edit == LyricEdit.REMOVE) R.string.lyrics_version_delete_failed
                    else R.string.lyrics_versions_failed, error)
                if (lyricTrack?.id == track.id) mutableLyricChoices.value = picker.copy(busy = false)
            } catch (error: SecurityException) {
                lyricEditFailure(previous, if (edit == LyricEdit.REMOVE) R.string.lyrics_version_delete_failed
                    else R.string.lyrics_versions_failed, error)
                if (lyricTrack?.id == track.id) mutableLyricChoices.value = picker.copy(busy = false)
            } finally {
                lyricEditTrackId = null
            }
        }
    }

    private fun lyricChoicesFailure(trackId: String, error: Exception) {
        Log.w(TAG, "Could not list available lyric versions", error)
        mutableLyricChoices.value = LyricChoices(trackId, loading = false, issue = LyricIssue.LOCAL_UNREADABLE)
        app.report(app.text(R.string.lyrics_versions_failed))
    }

    fun importBackground(uri: Uri) = importBackgrounds(listOf(uri))

    fun importBackgrounds(uris: List<Uri>) {
        if (backupBusy.value) {
            app.inform(app.text(R.string.backup_busy))
            return
        }
        if (mutableImportingBackground.value) return
        mutableImportingBackground.value = true
        operation {
            try {
                app.backgrounds.importImages(uris, app.displaySettings::installImages)
                app.inform(app.text(R.string.background_saved))
            } finally {
                mutableImportingBackground.value = false
            }
        }
    }

    fun backgroundIndex(version: Long, count: Int): Int {
        if (saved.get<Long>("backgroundVersion") != version) {
            saved["backgroundVersion"] = version
            saved["backgroundIndex"] = 0
        }
        return (saved.get<Int>("backgroundIndex") ?: 0).coerceIn(0, (count - 1).coerceAtLeast(0))
    }

    fun backgroundIndex(index: Int) { saved["backgroundIndex"] = index }

    fun screen(id: Int) { saved["screen"] = id }
    fun openSettings() {
        if (selectedScreen.value != R.id.action_settings) saved["settingsReturn"] = selectedScreen.value
        screen(R.id.action_settings)
    }
    fun closeSettings() { screen(saved["settingsReturn"] ?: R.id.nav_player) }
    fun settingsCategory(category: SettingsCategory) { saved["settingsCategory"] = category.current }
    fun settingsScroll(category: SettingsCategory): Int = saved["settingsScroll.${category.name}"] ?: 0
    fun settingsScroll(category: SettingsCategory, y: Int) { saved["settingsScroll.${category.name}"] = y }
    fun query(value: String) { saved["query"] = value }
    fun libraryMode(mode: LibraryMode) {
        selectLibrary(false)
        saved["libraryLocation"] = LibraryLocation(mode)
    }
    fun libraryGroup(key: String) {
        selectLibrary(false)
        saved["libraryLocation"] = libraryLocation.value.copy(group = key)
    }
    fun libraryUp(): Boolean {
        if (libraryLocation.value.group == null) return false
        libraryMode(libraryLocation.value.mode)
        return true
    }
    fun playLibraryGroup(key: String) = operation {
        val location = libraryLocation.value.copy(group = key)
        val page = withContext(Dispatchers.Default) {
            LibraryBrowsing.page(library.value, query.value, favoritesOnly.value, sort.value, location)
        }
        requestPlay(page.tracks)
    }
    fun toggleFavorites() { saved["favorites"] = !favoritesOnly.value }
    fun playlist(id: String?) { saved["playlist"] = id }
    fun selectLibrary(enabled: Boolean) {
        mutableLibrarySelecting.value = enabled
        if (!enabled) mutableLibrarySelection.value = emptySet()
    }
    fun selectTrack(id: String) {
        selectLibrary(true)
        val selected = librarySelection.value.toMutableSet()
        if (!selected.add(id)) selected.remove(id)
        mutableLibrarySelection.value = selected
    }
    fun selectVisibleTracks() {
        val visible = libraryPage.value.tracks.mapTo(hashSetOf()) { it.id }
        val selected = librarySelection.value.toMutableSet()
        if (selected.containsAll(visible)) selected.removeAll(visible) else selected.addAll(visible)
        mutableLibrarySelection.value = selected
    }
    fun pruneLibrarySelection() {
        if (librarySelection.value.isEmpty()) return
        val known = library.value.tracks.mapTo(hashSetOf()) { it.id }
        val remaining = librarySelection.value.filterTo(linkedSetOf(), known::contains)
        if (remaining.size != librarySelection.value.size) mutableLibrarySelection.value = remaining
    }
    fun sort(value: SortOrder) {
        app.sortOrder = value
        mutableSort.value = value
    }

    fun requestPlay(
        tracks: List<Track>,
        index: Int? = null,
        mode: QueueMode = QueueMode.REPLACE,
        useLoopScope: Boolean = false,
        collection: PlaybackCollection? = null,
        positionMs: Long = 0,
        playWhenReady: Boolean = true,
    ) {
        if (tracks.isEmpty()) {
            app.inform(app.text(R.string.no_tracks_to_play))
            return
        }
        operation {
            val options = app.playbackSettings.options.value
            val anchor = tracks[index?.coerceIn(tracks.indices) ?: 0]
            if (index != null && BackupEdits.unavailable(anchor.uri)) {
                app.report(app.text(R.string.reconnect_track_first))
                return@operation
            }
            val (candidates, noSavedFolder) = withContext(Dispatchers.Default) {
                val scoped = useLoopScope && mode == QueueMode.REPLACE &&
                    options.loop in listOf(LoopMode.FOLDER, LoopMode.SELECTED_FOLDERS)
                val standalone = scoped && options.loop == LoopMode.FOLDER &&
                    PlaybackScope.folderFor(library.value, anchor) == null
                val requested = when {
                    standalone -> if (index == null) tracks.toList() else listOf(anchor)
                    scoped -> PlaybackScope.tracks(library.value, options.loop, anchor, sort.value)
                    else -> tracks.toList()
                }
                requested to standalone
            }
            val selection = candidates.filterNot { BackupEdits.unavailable(it.uri) }
            if (selection.isNotEmpty() && selection.size != candidates.size) {
                app.inform(app.text(R.string.backup_tracks_skipped, candidates.size - selection.size))
            }
            if (selection.isEmpty()) {
                if (candidates.any { BackupEdits.unavailable(it.uri) }) {
                    app.report(app.text(R.string.reconnect_track_first))
                    return@operation
                }
                app.inform(app.text(
                    if (options.loop == LoopMode.FOLDER) R.string.folder_loop_unavailable
                    else R.string.selected_folders_empty,
                ))
                return@operation
            }
            val requestedIndex = index?.let { selection.indexOfFirst { it.id == anchor.id } }
            if (requestedIndex == -1) {
                app.inform(app.text(R.string.track_outside_selected_folders))
                return@operation
            }
            val start = requestedIndex ?: if (options.order == PlayOrder.RANDOM) Random.nextInt(selection.size) else 0
            if (noSavedFolder) app.inform(app.text(R.string.folder_loop_song_selection))
            mutableRequest.value = PlayRequest(++requestToken, selection, start, mode,
                CollectionPositions.position(positionMs, selection[start].durationMs), playWhenReady, collection)
            if (mode == QueueMode.REPLACE) screen(R.id.nav_player)
        }
    }

    fun restorePosition(collection: PlaybackCollection) = operation {
        repository.awaitReady()
        if (collection.kind == CollectionKind.FOLDER &&
            library.value.sources.firstOrNull { it.uri == collection.id }?.needsReconnect == true) {
            app.report(app.text(R.string.reconnect_folder_first))
            return@operation
        }
        val current = CollectionPositions.capture(app.playback.read(), System.currentTimeMillis())
        val history = app.collections.read()
        val positions = withContext(Dispatchers.Default) {
            CollectionPositions.reconcile(library.value, history + listOfNotNull(current))
        }
        val position = positions.firstOrNull { it.collection == collection }
        if (position == null) {
            app.inform(app.text(R.string.collection_position_empty))
            return@operation
        }
        val tracks = withContext(Dispatchers.Default) { CollectionPositions.tracks(library.value, collection, sort.value) }
        val index = tracks.indexOfFirst { it.id == position.trackId }
        if (index < 0 || BackupEdits.unavailable(tracks[index].uri)) {
            app.report(app.text(R.string.collection_track_missing))
            return@operation
        }
        requestPlay(tracks, index, collection = collection, positionMs = position.positionMs, playWhenReady = false)
    }

    fun selectFolders(selected: Set<String>) = operation {
        if (scanJob?.isCompleted == false) {
            app.inform(app.text(R.string.scan_busy))
            return@operation
        }
        repository.update { LibraryEdits.selectSources(it, selected) }
    }

    fun pinFolders(pinned: Set<String>) = operation {
        repository.update { LibraryEdits.pinSources(it, pinned) }
    }

    fun togglePin(source: FolderSource) = operation {
        repository.update { library ->
            val pinned = library.sources.filter { it.pinned }.mapTo(hashSetOf()) { it.uri }
            if (!pinned.add(source.uri)) pinned.remove(source.uri)
            LibraryEdits.pinSources(library, pinned)
        }
    }

    fun playSelectedFolders() {
        val sources = PlaybackScope.selectedSources(library.value)
        if (sources.isEmpty()) app.inform(app.text(R.string.select_folders_first))
        else scanFolders(sources, play = true, loop = LoopMode.SELECTED_FOLDERS)
    }

    fun consumedRequest(token: Long) {
        val request = mutableRequest.value
        if (request?.token == token) mutableRequest.compareAndSet(request, null)
    }

    fun addFolder(uri: Uri, flags: Int) = operation {
        if (scanJob?.isCompleted == false) {
            app.inform(app.text(R.string.scan_busy))
            return@operation
        }
        val source = repository.addSource(uri, flags)
        if (!source.persisted) app.inform(app.text(R.string.temporary_grant))
        if (selectedScreen.value != R.id.action_settings) openSource(source)
        scanFolders(listOf(source))
    }

    fun prepareDeviceMusic(browse: Boolean) { saved["deviceMusicBrowse"] = browse }

    fun loadDeviceMusicFolders() {
        if (scanJob?.isCompleted == false) {
            app.inform(app.text(R.string.scan_busy))
            return
        }
        deviceMusicJob?.cancel()
        val loading = DeviceMusicPicker(++deviceMusicToken, browse = saved["deviceMusicBrowse"] ?: false)
        mutableDeviceMusic.value = loading
        deviceMusicJob = viewModelScope.launch {
            try {
                guard {
                    val folders = repository.documents.deviceMusic.folders()
                    if (folders.isEmpty()) {
                        mutableDeviceMusic.value = null
                        app.inform(app.text(R.string.device_music_empty))
                    } else {
                        val savedUris = library.value.sources.mapTo(hashSetOf()) { it.uri }
                        mutableDeviceMusic.value = loading.copy(loading = false, folders = folders,
                            selected = folders.map { it.uri }.filter { it in savedUris }.toSet())
                        app.inform(app.text(if (loading.browse) R.string.device_music_browse_help else R.string.device_music_selection_help))
                    }
                }
            } finally {
                mutableDeviceMusic.compareAndSet(loading, null)
            }
        }
    }

    fun selectDeviceMusicFolder(uri: String, selected: Boolean) {
        val choice = mutableDeviceMusic.value ?: return
        if (choice.folders.none { it.uri == uri }) return
        mutableDeviceMusic.value = choice.copy(selected = if (selected) choice.selected + uri else choice.selected - uri)
    }

    fun selectAllDeviceMusicFolders() {
        val choice = mutableDeviceMusic.value ?: return
        if (choice.loading || choice.browse) return
        val all = choice.folders.mapTo(linkedSetOf()) { it.uri }
        mutableDeviceMusic.value = choice.copy(selected = if (choice.selected == all) emptySet() else all)
    }

    fun browseDeviceMusicFolder(uri: String) {
        val choice = mutableDeviceMusic.value ?: return
        if (!choice.browse || choice.folders.none { it.uri == uri }) return
        dismissDeviceMusic()
        operation {
            val source = repository.addSource(Uri.parse(uri), 0)
            openSource(source)
            screen(R.id.nav_folders)
        }
    }

    fun dismissDeviceMusic() {
        deviceMusicJob?.cancel()
        mutableDeviceMusic.value = null
    }

    fun addDeviceMusicFolders() {
        val choice = mutableDeviceMusic.value ?: return
        val selected = choice.folders.filter { it.uri in choice.selected }
        if (selected.isEmpty()) {
            app.inform(app.text(R.string.select_folders_first))
            return
        }
        dismissDeviceMusic()
        operation {
            if (scanJob?.isCompleted == false) {
                app.inform(app.text(R.string.scan_busy))
                return@operation
            }
            val sources = repository.addSources(selected.map { Uri.parse(it.uri) }, 0)
            if (selectedScreen.value != R.id.action_settings) {
                showPath(emptyList())
                screen(R.id.nav_folders)
            }
            scanFolders(sources)
        }
    }

    fun openFile(uri: Uri, flags: Int): Job {
        openFileJob?.cancel()
        return operation {
            val result = repository.openDocument(uri, flags)
            if (!result.track.standalonePersisted) app.inform(app.text(R.string.temporary_grant))
            else if (result.warning) app.inform(app.text(R.string.metadata_warning))
            requestPlay(listOf(result.track))
        }.also { openFileJob = it }
    }

    fun playBrowserFile(entry: DocumentEntry) = operation {
        val location = browser.value.path.lastOrNull() ?: return@operation
        val result = repository.browserTrack(entry.uri, location.sourceUri, location.documentId,
            listOf(location.relativePath, entry.name).filter(String::isNotEmpty).joinToString("/"))
        if (result.warning) app.inform(app.text(R.string.metadata_warning))
        requestPlay(listOf(result.track), collection = PlaybackCollection.folder(location))
    }

    fun openSource(source: FolderSource) {
        operation {
            if (source.needsReconnect) {
                app.report(app.text(R.string.reconnect_folder_first))
                return@operation
            }
            showPath(listOf(BrowserLocation(source.uri, repository.documents.rootId(source.uri),
                repository.documents.displayName(source))))
        }
    }

    fun openDirectory(entry: DocumentEntry) {
        val path = browser.value.path
        val current = path.lastOrNull() ?: return
        if (path.any { it.documentId == entry.id }) {
            app.report(app.text(R.string.provider_cycle))
            return
        }
        showPath(path + BrowserLocation(current.sourceUri, entry.id, entry.name,
            listOf(current.relativePath, entry.name).filter(String::isNotEmpty).joinToString("/")))
    }

    fun browserUp(): Boolean {
        val path = browser.value.path
        if (path.isEmpty()) return false
        showPath(path.dropLast(1))
        return true
    }

    fun refreshBrowser() = showPath(browser.value.path)

    private fun showPath(path: List<BrowserLocation>) {
        browserJob?.cancel()
        if (path.isEmpty()) {
            mutableBrowser.value = BrowserState()
            return
        }
        mutableBrowser.value = BrowserState(path = path, loading = true)
        browserJob = viewModelScope.launch {
            try {
                val location = path.last()
                val entries = withContext(Dispatchers.IO) {
                    repository.documents.children(location.sourceUri, location.documentId)
                }
                mutableBrowser.value = BrowserState(path = path, entries = entries)
            } catch (error: CancellationException) {
                throw error
            } catch (error: SecurityException) {
                browserFailure(path, error, app.text(R.string.permission_lost))
            } catch (error: IOException) {
                browserFailure(path, error, error.localizedMessage.orEmpty())
            } catch (error: IllegalArgumentException) {
                browserFailure(path, error, error.localizedMessage.orEmpty())
            } catch (error: IllegalStateException) {
                browserFailure(path, error, error.localizedMessage.orEmpty())
            }
        }
    }

    private fun browserFailure(path: List<BrowserLocation>, error: Exception, text: String) {
        Log.w(TAG, "Folder browsing failed", error)
        mutableBrowser.value = BrowserState(path = path, error = text)
        app.report(text)
    }

    fun scanFolders(
        sources: List<FolderSource>,
        location: BrowserLocation? = null,
        play: Boolean = false,
        loop: LoopMode? = null,
        forceMetadata: Boolean = false,
    ) {
        scanRequests(sources.map { it to location }, play, loop, forceMetadata)
    }

    private fun scanRequests(
        requests: List<Pair<FolderSource, BrowserLocation?>>,
        play: Boolean = false,
        loop: LoopMode? = null,
        forceMetadata: Boolean = false,
        completed: (suspend (List<PlaylistFolder>) -> Unit)? = null,
    ) {
        if (scanJob?.isCompleted == false) {
            app.inform(app.text(R.string.scan_busy))
            return
        }
        scanJob = viewModelScope.launch {
            val resultTracks = mutableListOf<Track>()
            val scannedFolders = mutableListOf<PlaylistFolder>()
            val failures = mutableListOf<String>()
            var warnings = 0
            var missing = 0
            var reused = 0
            var indexed = 0
            fun collected(source: FolderSource, location: BrowserLocation?, result: ScanResult) {
                resultTracks.addAll(result.tracks)
                scannedFolders.add(PlaylistFolder(source.uri,
                    location?.documentId ?: repository.documents.rootId(source.uri),
                    location?.name ?: source.name, result.tracks.map { it.id }, location?.relativePath.orEmpty()))
                warnings += result.metadataWarnings
                missing += result.missingCount
                reused += result.reusedMetadata
                indexed += result.indexedMetadata
            }
            try {
                if (requests.any { it.first.needsReconnect }) {
                    app.report(app.text(R.string.reconnect_folder_first))
                    return@launch
                }
                if (requests.isNotEmpty() && requests.all { MediaStoreAccess.folder(Uri.parse(it.first.uri)) != null }) {
                    try {
                        val results = repository.scanIndexed(requests, forceMetadata) { mutableScan.value = it }
                        requests.forEachIndexed { index, (source, location) -> collected(source, location, results[index]) }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: SecurityException) {
                        for ((source, _) in requests) scanFailure(source, error, failures)
                    } catch (error: IOException) {
                        for ((source, _) in requests) scanFailure(source, error, failures)
                    } catch (error: IllegalArgumentException) {
                        for ((source, _) in requests) scanFailure(source, error, failures)
                    } catch (error: IllegalStateException) {
                        for ((source, _) in requests) scanFailure(source, error, failures)
                    }
                } else {
                    for ((source, location) in requests) {
                        try {
                            collected(source, location, repository.scan(source, location, forceMetadata) { mutableScan.value = it })
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: SecurityException) {
                            scanFailure(source, error, failures)
                        } catch (error: IOException) {
                            scanFailure(source, error, failures)
                        } catch (error: IllegalArgumentException) {
                            scanFailure(source, error, failures)
                        } catch (error: IllegalStateException) {
                            scanFailure(source, error, failures)
                        }
                    }
                }
                app.artwork.clear()
                requestLyrics(lyricTrack, refresh = true)
                if (failures.isNotEmpty()) app.report(failures.joinToString("\n"))
                else {
                    val summary = app.text(R.string.scan_finished, resultTracks.size, warnings) +
                        (if (reused > 0) "\n${app.localizedContext.resources.getQuantityString(R.plurals.scan_metadata_reused, reused, reused)}" else "") +
                        (if (indexed > 0) "\n${app.localizedContext.resources.getQuantityString(R.plurals.scan_indexed_tags, indexed, indexed)}" else "")
                    app.inform(if (missing > 0) "$summary\n${app.text(R.string.scan_missing, missing)}" else summary)
                }
                if (play && failures.isEmpty()) {
                    if (loop != null) app.playbackSettings.setLoop(loop)
                    val collection = requests.singleOrNull()?.let { (source, location) ->
                        PlaybackCollection(CollectionKind.FOLDER, source.uri, location?.relativePath.orEmpty())
                    }
                    requestPlay(AudioFiles.sortAndFilter(resultTracks.distinctBy { it.id }, "", sort.value), collection = collection)
                }
                if (failures.isEmpty() && completed != null) guard { completed(scannedFolders) }
            } catch (error: CancellationException) {
                app.inform(app.text(R.string.scan_cancelled))
                throw error
            } finally {
                mutableScan.value = null
            }
        }
    }

    private suspend fun scanFailure(source: FolderSource, error: Exception, failures: MutableList<String>) {
        Log.w(TAG, "Source scan failed", error)
        val detail = if (error is SecurityException) app.text(R.string.permission_lost)
        else error.localizedMessage.orEmpty()
        val message = app.text(R.string.source_failed, repository.documents.displayName(source), detail)
        failures.add(message)
        try {
            repository.markSourceError(source.uri, detail)
        } catch (writeError: IOException) {
            Log.e(TAG, "Could not save the source error", writeError)
            failures.add(app.text(R.string.operation_failed, writeError.localizedMessage.orEmpty()))
        }
    }

    fun cancelScan() { scanJob?.cancel() }

    fun removeSource(source: FolderSource) = removeSources(setOf(source.uri))

    fun removeSources(sourceUris: Set<String>) = operation {
        if (scanJob?.isCompleted == false) {
            app.inform(app.text(R.string.scan_busy))
            return@operation
        }
        if (backupBusy.value) {
            app.inform(app.text(R.string.backup_busy))
            return@operation
        }
        val updated = repository.update { LibraryEdits.removeSources(it, sourceUris) }
        app.playback.reconcile(updated)
        if (browser.value.path.firstOrNull()?.sourceUri in sourceUris) showPath(emptyList())
        val errors = withContext(Dispatchers.IO) {
            repository.documents.releaseReadPermissions(sourceUris)
        }
        errors.forEach {
            Log.w(TAG, "Removed folder references but could not release a grant", it)
        }
        if (errors.isEmpty()) app.inform(app.text(R.string.references_removed))
        else app.report(app.text(R.string.grant_release_failed, errors.joinToString("\n") { it.localizedMessage.orEmpty() }))
    }

    fun removeTrack(track: Track) = operation {
        val updated = repository.update { LibraryEdits.removeTrack(it, track.id) }
        app.playback.reconcile(updated)
        app.inform(app.text(R.string.reference_removed))
    }

    fun favorite(track: Track) = operation {
        repository.update { LibraryEdits.toggleFavorite(it, track.id) }
    }

    fun createPlaylist(name: String, tracks: List<Track> = emptyList(), folders: List<BrowserLocation> = emptyList()) {
        scanPlaylistFolders(folders) { links ->
            val id = UUID.randomUUID().toString()
            repository.update {
                val withTracks = LibraryEdits.upsertTracks(it, tracks)
                val created = LibraryEdits.createPlaylist(withTracks, id, name)
                val withManual = LibraryEdits.addToPlaylist(created, id, tracks.map(Track::id))
                LibraryEdits.setPlaylistFolders(withManual, id, links)
            }
            selectLibrary(false)
            playlist(id)
            screen(R.id.nav_playlists)
            app.inform(app.text(R.string.playlist_created, name.trim()))
        }
    }

    fun addToPlaylist(id: String, tracks: List<Track>) = operation {
        val updated = repository.update {
            LibraryEdits.addToPlaylist(LibraryEdits.upsertTracks(it, tracks), id, tracks.map(Track::id))
        }
        val name = updated.playlists.first { it.id == id }.name
        selectLibrary(false)
        app.inform(app.text(R.string.playlist_added, name))
    }

    fun removePlaylist(id: String) = operation {
        repository.update { LibraryEdits.deletePlaylist(it, id) }
        if (selectedPlaylist.value == id) playlist(null)
        app.inform(app.text(R.string.playlist_deleted))
    }

    fun removePlaylistTrack(playlistId: String, trackId: String) = operation {
        repository.update { LibraryEdits.removeFromPlaylist(it, playlistId, trackId) }
    }

    fun updatePlaylistTracks(id: String, tracks: List<String>) = operation {
        repository.update { LibraryEdits.replacePlaylistTracks(it, id, tracks) }
        app.inform(app.text(R.string.playlist_updated))
    }

    fun renamePlaylist(id: String, name: String) = operation {
        repository.update { LibraryEdits.renamePlaylist(it, id, name) }
        app.inform(app.text(R.string.playlist_updated))
    }

    fun updatePlaylistFolders(id: String, folders: List<BrowserLocation>) {
        scanPlaylistFolders(folders) { scanned ->
            repository.update { library ->
                val previous = requireNotNull(library.playlists.firstOrNull { it.id == id }).folders.associateBy { it.key }
                val unresolved = previous.values.filter { it.documentId.isEmpty() }.associateBy { it.pathKey }
                val known = library.tracks.mapTo(hashSetOf()) { it.id }
                val refreshed = scanned.map { folder ->
                    folder.copy(trackIds = ((previous[folder.key] ?: unresolved[folder.pathKey])?.trackIds.orEmpty() + folder.trackIds)
                        .filter(known::contains).distinct())
                }
                LibraryEdits.setPlaylistFolders(library, id, refreshed)
            }
            app.inform(app.text(R.string.playlist_updated))
        }
    }

    private fun scanPlaylistFolders(folders: List<BrowserLocation>, completed: suspend (List<PlaylistFolder>) -> Unit) {
        operation {
            if (folders.isEmpty()) completed(emptyList())
            else {
                val sources = library.value.sources.associateBy { it.uri }
                val requests = folders.distinctBy { it.key }.map {
                    val source = sources[it.sourceUri] ?: throw IOException(app.text(R.string.source_removed))
                    if (source.needsReconnect) throw IOException(app.text(R.string.reconnect_folder_first))
                    source to if (it.documentId.isEmpty()) repository.resolveLocation(source, it.relativePath) else it
                }
                scanRequests(requests, completed = completed)
            }
        }
    }

    fun prepareSharedBackup(language: String) {
        if (!startBackup()) return
        operation {
            try { mutableSharedBackup.value = app.backups.sharedExport(language) }
            finally { mutableBackupBusy.value = false }
        }
    }

    fun consumedSharedBackup(uri: Uri) { mutableSharedBackup.compareAndSet(uri, null) }

    fun exportBackup(uri: Uri, language: String) {
        if (!startBackup()) return
        operation {
            try {
                app.backups.export(uri, language)
                app.inform(app.text(R.string.backup_saved))
            } finally { mutableBackupBusy.value = false }
        }
    }

    fun previewBackup(uri: Uri) {
        if (!startBackup()) return
        operation {
            try {
                mutablePreparedBackup.value?.let { app.backups.discard(it) }
                mutablePreparedBackup.value = null
                mutablePreparedBackup.value = app.backups.prepare(uri)
            } finally { mutableBackupBusy.value = false }
        }
    }

    fun dismissBackup() {
        val backup = mutablePreparedBackup.value ?: return
        mutablePreparedBackup.value = null
        operation { app.backups.discard(backup) }
    }

    fun restoreBackup() {
        val backup = mutablePreparedBackup.value ?: return
        if (!startBackup()) return
        mutablePreparedBackup.value = null
        operation {
            try {
                val result = app.backups.restore(backup)
                mutableSort.value = app.sortOrder
                selectLibrary(false)
                refreshBrowser()
                mutableBackupRestored.value = result
            } finally { mutableBackupBusy.value = false }
        }
    }

    fun consumedBackup(result: BackupRestoreResult) { mutableBackupRestored.compareAndSet(result, null) }

    fun inspectLyricCache(reportResult: Boolean = false) = lyricCacheOperation {
        val size = app.lyrics.cache.inspect()
        if (reportResult) app.inform(app.text(R.string.lyrics_cache_size, size.files,
            android.text.format.Formatter.formatFileSize(app, size.bytes)))
    }

    fun clearLyricCache() = lyricCacheOperation {
        val cleared = app.lyrics.cache.clear()
        app.inform(app.text(R.string.lyrics_cache_cleared, cleared.files,
            android.text.format.Formatter.formatFileSize(app, cleared.bytes)))
    }

    fun moveLyricCache(uri: Uri, flags: Int) = lyricCacheOperation {
        val moved = app.lyrics.cache.moveInternalTo(uri, flags)
        app.inform(app.text(R.string.lyrics_cache_moved, moved.files,
            android.text.format.Formatter.formatFileSize(app, moved.bytes)))
    }

    fun useInternalLyricCache() = lyricCacheOperation {
        app.lyrics.cache.useInternal()
        app.inform(app.text(R.string.lyrics_cache_internal_active))
    }

    private fun lyricCacheOperation(action: suspend () -> Unit) {
        if (lyricCacheBusy.value) {
            app.inform(app.text(R.string.lyrics_cache_busy))
            return
        }
        if (backupBusy.value || scanJob?.isCompleted == false) {
            app.inform(app.text(R.string.backup_busy))
            return
        }
        mutableLyricCacheBusy.value = true
        operation {
            try { action() }
            finally { mutableLyricCacheBusy.value = false }
        }
    }

    private fun startBackup(): Boolean {
        if (backupBusy.value || scanJob?.isCompleted == false || importingBackground.value || lyricCacheBusy.value) {
            app.inform(app.text(R.string.backup_busy))
            return false
        }
        mutableBackupBusy.value = true
        return true
    }

    fun reconnectSource(source: FolderSource, target: FolderSource) = operation {
        if (scanJob?.isCompleted == false || backupBusy.value) {
            app.inform(app.text(R.string.backup_busy))
            return@operation
        }
        require(!target.needsReconnect && source.uri != target.uri)
        var count = 0
        repository.update {
            val replacements = BackupEdits.matches(it, source.uri, target.uri)
            count = replacements.size
            BackupEdits.reconnect(it, replacements, source.uri to target.uri)
        }
        app.inform(app.text(R.string.backup_reconnected, count))
    }

    fun prepareReconnectTrack(id: String?, picker: Int) {
        saved["reconnectTrackId"] = id
        saved["reconnectPicker"] = picker
    }

    fun consumeReconnectTrack(picker: Int): String? {
        if (saved.get<Int>("reconnectPicker") != picker) return null
        return saved.remove<String>("reconnectTrackId")
    }

    fun reconnectTrack(id: String, uri: Uri, flags: Int) = operation {
        val track = repository.openDocument(uri, flags).track
        repository.update { BackupEdits.reconnect(it, mapOf(id to track)) }
        app.inform(app.text(R.string.backup_reconnected, 1))
    }

    override fun onCleared() {
        mutablePreparedBackup.value?.let { backup ->
            app.applicationScope.launch { guard { app.backups.discard(backup) } }
        }
        super.onCleared()
    }

    fun operation(block: suspend () -> Unit): Job = viewModelScope.launch { guard(block) }

    suspend fun guard(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            Log.w(TAG, "Storage permission denied", error)
            app.report(app.text(R.string.permission_lost))
        } catch (error: IOException) {
            operationFailure(error)
        } catch (error: PinnedFolderException) {
            Log.i(TAG, "Blocked removal from a pinned folder")
            app.report(app.text(R.string.unpin_first))
        } catch (error: IllegalArgumentException) {
            operationFailure(error)
        } catch (error: IllegalStateException) {
            operationFailure(error)
        }
    }

    private fun operationFailure(error: Exception) {
        Log.w(TAG, "Local music operation failed", error)
        app.report(app.text(R.string.operation_failed, error.localizedMessage.orEmpty()))
    }

    private companion object {
        const val TAG = "MusicViewModel"
    }
}
