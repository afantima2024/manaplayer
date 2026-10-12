package com.macarmusic.app.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.macarmusic.app.MaCarMusicApp
import com.macarmusic.app.R
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
import com.macarmusic.app.model.SortOrder
import com.macarmusic.app.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import kotlin.random.Random

enum class QueueMode { REPLACE, APPEND, NEXT }
data class PlayRequest(val token: Long, val tracks: List<Track>, val index: Int, val mode: QueueMode)

class MusicViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    val app = application as MaCarMusicApp
    val repository = app.repository
    val library = repository.library
    val selectedScreen = saved.getStateFlow("screen", R.id.nav_player)
    val query = saved.getStateFlow("query", "")
    val favoritesOnly = saved.getStateFlow("favorites", false)
    val selectedPlaylist = saved.getStateFlow<String?>("playlist", null)
    private val mutableSort = MutableStateFlow(app.sortOrder)
    val sort = mutableSort.asStateFlow()
    val visibleTracks = combine(library, query, favoritesOnly, sort) { library, query, favorites, sort ->
        AudioFiles.sortAndFilter(library.tracks, query, sort, if (favorites) library.favorites else null)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val mutableBrowser = MutableStateFlow(BrowserState())
    val browser = mutableBrowser.asStateFlow()
    private val mutableScan = MutableStateFlow<ScanProgress?>(null)
    val scan = mutableScan.asStateFlow()
    private val mutableRequest = MutableStateFlow<PlayRequest?>(null)
    val playRequest = mutableRequest.asStateFlow()
    private var requestToken = 0L
    private var browserJob: Job? = null
    private var scanJob: Job? = null

    fun screen(id: Int) { saved["screen"] = id }
    fun openSettings() {
        if (selectedScreen.value != R.id.action_settings) saved["settingsReturn"] = selectedScreen.value
        screen(R.id.action_settings)
    }
    fun closeSettings() { screen(saved["settingsReturn"] ?: R.id.nav_player) }
    fun query(value: String) { saved["query"] = value }
    fun toggleFavorites() { saved["favorites"] = !favoritesOnly.value }
    fun playlist(id: String?) { saved["playlist"] = id }
    fun sort(value: SortOrder) {
        app.sortOrder = value
        mutableSort.value = value
    }

    fun requestPlay(
        tracks: List<Track>,
        index: Int? = null,
        mode: QueueMode = QueueMode.REPLACE,
        useLoopScope: Boolean = false,
    ) {
        if (tracks.isEmpty()) {
            app.inform(app.text(R.string.no_tracks_to_play))
            return
        }
        operation {
            val options = app.playbackSettings.options.value
            val anchor = tracks[index?.coerceIn(tracks.indices) ?: 0]
            val selection = withContext(Dispatchers.Default) {
                if (useLoopScope && mode == QueueMode.REPLACE &&
                    options.loop in listOf(LoopMode.FOLDER, LoopMode.SELECTED_FOLDERS)
                ) PlaybackScope.tracks(library.value, options.loop, anchor, sort.value)
                else tracks.toList()
            }
            if (selection.isEmpty()) {
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
            mutableRequest.value = PlayRequest(++requestToken, selection, start, mode)
            if (mode == QueueMode.REPLACE) screen(R.id.nav_player)
        }
    }

    fun selectFolders(selected: Set<String>) = operation {
        if (scanJob?.isCompleted == false) {
            app.inform(app.text(R.string.scan_busy))
            return@operation
        }
        repository.update { LibraryEdits.selectSources(it, selected) }
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

    fun openFile(uri: Uri, flags: Int) = operation {
        val result = repository.openDocument(uri, flags)
        if (!result.track.standalonePersisted) app.inform(app.text(R.string.temporary_grant))
        else if (result.warning) app.inform(app.text(R.string.metadata_warning))
        requestPlay(listOf(result.track))
    }

    fun playBrowserFile(entry: DocumentEntry) = operation {
        val source = browser.value.path.lastOrNull()?.sourceUri ?: return@operation
        val result = repository.browserTrack(entry.uri, source)
        if (result.warning) app.inform(app.text(R.string.metadata_warning))
        requestPlay(listOf(result.track))
    }

    fun openSource(source: FolderSource) {
        operation {
            showPath(listOf(BrowserLocation(source.uri, repository.documents.rootId(source.uri), source.name)))
        }
    }

    fun openDirectory(entry: DocumentEntry) {
        val path = browser.value.path
        val current = path.lastOrNull() ?: return
        if (path.any { it.documentId == entry.id }) {
            app.report(app.text(R.string.provider_cycle))
            return
        }
        showPath(path + BrowserLocation(current.sourceUri, entry.id, entry.name))
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
    ) {
        if (scanJob?.isCompleted == false) {
            app.inform(app.text(R.string.scan_busy))
            return
        }
        scanJob = viewModelScope.launch {
            val resultTracks = mutableListOf<Track>()
            val failures = mutableListOf<String>()
            var warnings = 0
            var missing = 0
            try {
                for (source in sources) {
                    try {
                        val result = repository.scan(source, location) { mutableScan.value = it }
                        resultTracks.addAll(result.tracks)
                        warnings += result.metadataWarnings
                        missing += result.missingCount
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
                app.artwork.clear()
                if (failures.isNotEmpty()) app.report(failures.joinToString("\n"))
                else {
                    val summary = app.text(R.string.scan_finished, resultTracks.size, warnings)
                    app.inform(if (missing > 0) "$summary\n${app.text(R.string.scan_missing, missing)}" else summary)
                }
                if (play && failures.isEmpty()) {
                    if (loop != null) app.playbackSettings.setLoop(loop)
                    requestPlay(AudioFiles.sortAndFilter(resultTracks.distinctBy { it.id }, "", sort.value))
                }
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
        val message = app.text(R.string.source_failed, source.name, detail)
        failures.add(message)
        try {
            repository.markSourceError(source.uri, detail)
        } catch (writeError: IOException) {
            Log.e(TAG, "Could not save the source error", writeError)
            failures.add(app.text(R.string.operation_failed, writeError.localizedMessage.orEmpty()))
        }
    }

    fun cancelScan() { scanJob?.cancel() }

    fun removeSource(source: FolderSource) = operation {
        if (scanJob?.isCompleted == false) {
            app.inform(app.text(R.string.scan_busy))
            return@operation
        }
        val updated = repository.update { LibraryEdits.removeSource(it, source.uri) }
        app.playback.reconcile(updated)
        if (browser.value.path.firstOrNull()?.sourceUri == source.uri) showPath(emptyList())
        try {
            withContext(Dispatchers.IO) { repository.documents.releaseReadPermission(source.uri) }
            app.inform(app.text(R.string.reference_removed))
        } catch (error: SecurityException) {
            app.report(app.text(R.string.grant_release_failed, error.localizedMessage.orEmpty()))
        }
    }

    fun removeTrack(track: Track) = operation {
        val updated = repository.update { LibraryEdits.removeTrack(it, track.id) }
        app.playback.reconcile(updated)
        app.inform(app.text(R.string.reference_removed))
    }

    fun favorite(track: Track) = operation {
        repository.update { LibraryEdits.toggleFavorite(it, track.id) }
    }

    fun createPlaylist(name: String, tracks: List<Track> = emptyList()) = operation {
        val id = UUID.randomUUID().toString()
        repository.update {
            val withTracks = LibraryEdits.upsertTracks(it, tracks)
            val created = LibraryEdits.createPlaylist(withTracks, id, name)
            LibraryEdits.addToPlaylist(created, id, tracks.map(Track::id))
        }
        playlist(id)
        screen(R.id.nav_playlists)
        app.inform(app.text(R.string.playlist_created, name.trim()))
    }

    fun addToPlaylist(id: String, tracks: List<Track>) = operation {
        val updated = repository.update {
            LibraryEdits.addToPlaylist(LibraryEdits.upsertTracks(it, tracks), id, tracks.map(Track::id))
        }
        val name = updated.playlists.first { it.id == id }.name
        app.inform(app.text(R.string.playlist_added, name))
    }

    fun removePlaylist(id: String) = operation {
        repository.update { it.copy(playlists = it.playlists.filterNot { playlist -> playlist.id == id }) }
        if (selectedPlaylist.value == id) playlist(null)
    }

    fun removePlaylistTrack(playlistId: String, trackId: String) = operation {
        repository.update { LibraryEdits.removeFromPlaylist(it, playlistId, trackId) }
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
