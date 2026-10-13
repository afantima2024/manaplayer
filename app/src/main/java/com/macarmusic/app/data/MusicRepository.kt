package com.macarmusic.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.macarmusic.app.R
import com.macarmusic.app.model.BrowserLocation
import com.macarmusic.app.model.FolderSource
import com.macarmusic.app.model.Library
import com.macarmusic.app.model.LibraryEdits
import com.macarmusic.app.model.ScanProgress
import com.macarmusic.app.model.ScanResult
import com.macarmusic.app.model.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException
import java.util.ArrayDeque

class MusicRepository(
    private val context: Context,
    scope: CoroutineScope,
    private val report: (String) -> Unit,
) {
    val documents = DocumentAccess(context)
    val metadata = MetadataReader(context, documents)
    private val store = AtomicTextStore(File(context.filesDir, "library.json"))
    private val mutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private var loadFailed = false
    private val mutableLibrary = MutableStateFlow(Library())
    val library = mutableLibrary.asStateFlow()
    private val mutableLoaded = MutableStateFlow(false)
    val loaded = mutableLoaded.asStateFlow()
    val isReadable: Boolean get() = mutableLoaded.value && !loadFailed

    init {
        scope.launch {
            try {
                mutableLibrary.value = store.read()?.let(LibraryCodec::decodeLibrary) ?: Library()
            } catch (error: IOException) {
                loadFailure(error)
            } catch (error: SerializationException) {
                loadFailure(error)
            } catch (error: IllegalArgumentException) {
                loadFailure(error)
            } finally {
                mutableLoaded.value = true
                ready.complete(Unit)
            }
        }
    }

    suspend fun awaitReady() = ready.await()

    suspend fun update(edit: (Library) -> Library): Library = withContext(Dispatchers.IO) {
        ready.await()
        mutex.withLock {
            if (loadFailed) throw IOException(context.getString(R.string.library_read_only))
            val updated = edit(mutableLibrary.value)
            store.write(LibraryCodec.encode(updated))
            mutableLibrary.value = updated
            updated
        }
    }

    suspend fun addSource(uri: Uri, flags: Int): FolderSource = addSources(listOf(uri), flags).single()

    suspend fun addSources(uris: List<Uri>, flags: Int): List<FolderSource> = withContext(Dispatchers.IO) {
        require(uris.isNotEmpty()) { context.getString(R.string.select_folders_first) }
        val sources = uris.distinct().map { documents.source(it, flags) }
        update { library ->
            val refreshed = sources.map { source ->
                val existing = library.sources.firstOrNull { it.uri == source.uri }
                source.copy(
                    lastScanMs = existing?.lastScanMs ?: 0,
                    lastScanCount = existing?.lastScanCount ?: 0,
                    selectedForPlayback = existing?.selectedForPlayback ?: true,
                    pinned = existing?.pinned ?: false,
                )
            }
            val keys = sources.mapTo(hashSetOf()) { it.uri }
            library.copy(sources = library.sources.filterNot { it.uri in keys } + refreshed)
        }
        sources
    }

    suspend fun openDocument(uri: Uri, flags: Int): MetadataResult = withContext(Dispatchers.IO) {
        val persisted = documents.retainReadPermission(uri, flags)
        val result = metadata.read(documents.describe(uri), standalonePersisted = persisted)
        update { LibraryEdits.upsertTracks(it, listOf(result.track)) }
        result
    }

    suspend fun browserTrack(uri: String, sourceUri: String, parentId: String,
        relativePath: String? = null,
    ): MetadataResult = withContext(Dispatchers.IO) {
        val metadata = metadata.read(documents.describe(Uri.parse(uri)), sourceUri)
        val result = metadata.copy(track = metadata.track.copy(sourceParents = mapOf(sourceUri to parentId),
            sourcePaths = relativePath?.let { mapOf(sourceUri to it) }.orEmpty()))
        update { LibraryEdits.upsertTracks(it, listOf(result.track)) }
        result
    }

    suspend fun scan(
        source: FolderSource,
        location: BrowserLocation?,
        onProgress: (ScanProgress) -> Unit,
    ): ScanResult = withContext(Dispatchers.IO) {
        val rootId = documents.rootId(source.uri)
        val startingId = location?.documentId ?: rootId
        val fullSource = startingId == rootId
        val startPath = if (fullSource) "" else location?.relativePath?.ifBlank { location.name }.orEmpty()
        val folders = ArrayDeque<ScanFolder>().apply { add(ScanFolder(startingId, 0, startPath)) }
        val visited = hashSetOf<String>()
        val entries = linkedMapOf<String, com.macarmusic.app.model.DocumentEntry>()
        val parents = hashMapOf<String, String>()
        val paths = hashMapOf<String, String>()
        val label = if (location == null || fullSource) documents.displayName(source) else location.name
        onProgress(ScanProgress(label))
        while (folders.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (id, depth, relativePath) = folders.removeFirst()
            if (!visited.add(id)) continue
            if (depth > 64 || visited.size + entries.size > 100_000) {
                throw IOException(context.getString(R.string.folder_safety_limit))
            }
            for (entry in documents.children(source.uri, id)) {
                currentCoroutineContext().ensureActive()
                val entryPath = listOf(relativePath, entry.name).filter(String::isNotEmpty).joinToString("/")
                if (entry.isDirectory) folders.add(ScanFolder(entry.id, depth + 1, entryPath))
                else {
                    entries[entry.id] = entry
                    parents[entry.id] = id
                    paths[entry.id] = entryPath
                }
                if (folders.size + visited.size + entries.size > 100_000) {
                    throw IOException(context.getString(R.string.folder_safety_limit))
                }
            }
            onProgress(ScanProgress(label, entries.size))
        }
        val tracks = ArrayList<Track>(entries.size)
        var warnings = 0
        for ((index, entry) in entries.values.withIndex()) {
            currentCoroutineContext().ensureActive()
            val result = metadata.read(entry, source.uri)
            tracks.add(result.track.copy(sourceParents = mapOf(source.uri to parents.getValue(entry.id)),
                sourcePaths = mapOf(source.uri to paths.getValue(entry.id))))
            if (result.warning) warnings++
            if (index % 10 == 0) onProgress(ScanProgress(label, index + 1))
        }
        currentCoroutineContext().ensureActive()
        val ids = tracks.mapTo(hashSetOf()) { it.id }
        var missing = 0
        update { library ->
            if (library.sources.none { it.uri == source.uri }) {
                throw IOException(context.getString(R.string.source_removed))
            }
            if (fullSource) {
                missing = library.tracks.count { source.uri in it.sourceUris && it.id !in ids }
                val issue = if (missing > 0) context.getString(R.string.scan_missing, missing) else null
                LibraryEdits.applyScan(library, source.uri, tracks, System.currentTimeMillis(), issue)
            } else LibraryEdits.upsertTracks(library, tracks)
        }
        ScanResult(tracks, warnings, missing)
    }

    suspend fun markSourceError(uri: String, message: String) {
        update { library -> library.copy(sources = library.sources.map {
            if (it.uri == uri) it.copy(unavailable = true, lastIssue = message) else it
        }) }
    }

    private fun loadFailure(error: Exception) {
        loadFailed = true
        Log.e(TAG, "Library was preserved after a load failure", error)
        report(context.getString(R.string.library_load_failed, error.localizedMessage.orEmpty()))
    }

    private companion object {
        const val TAG = "MusicRepository"
    }

    private data class ScanFolder(val id: String, val depth: Int, val relativePath: String)
}
