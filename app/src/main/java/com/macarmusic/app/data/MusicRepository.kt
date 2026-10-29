package com.macarmusic.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.macarmusic.app.R
import com.macarmusic.app.model.BrowserLocation
import com.macarmusic.app.model.FolderSource
import com.macarmusic.app.model.Library
import com.macarmusic.app.model.LibraryEdits
import com.macarmusic.app.model.LibraryScan
import com.macarmusic.app.model.MetadataReuse
import com.macarmusic.app.model.MetadataEdits
import com.macarmusic.app.model.MetadataUpdate
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
    private val scope: CoroutineScope,
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
    private val enrichment by lazy { MetadataEnrichment(context, this, scope, report) }
    val metadataProgress get() = enrichment.progress

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
            resumeMetadata()
        }
    }

    suspend fun awaitReady() = ready.await()

    suspend fun update(edit: (Library) -> Library): Library = withContext(Dispatchers.IO) {
        ready.await()
        mutex.withLock {
            if (loadFailed) throw IOException(context.getString(R.string.library_read_only))
            val updated = edit(mutableLibrary.value)
            if (updated === mutableLibrary.value) return@withLock updated
            store.write(LibraryCodec.encode(updated))
            mutableLibrary.value = updated
            updated
        }
    }

    suspend fun addSource(uri: Uri, flags: Int): FolderSource = addSources(listOf(uri), flags).single()

    suspend fun addSources(uris: List<Uri>, flags: Int): List<FolderSource> = withContext(Dispatchers.IO) {
        documents.grantMutex.withLock {
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
                        restoredFrom = existing?.restoredFrom.orEmpty(),
                    )
                }
                val keys = sources.mapTo(hashSetOf()) { it.uri }
                library.copy(sources = library.sources.filterNot { it.uri in keys } + refreshed)
            }
            sources
        }
    }

    suspend fun openDocument(uri: Uri, flags: Int): MetadataResult = withContext(Dispatchers.IO) {
        documents.grantMutex.withLock {
            val persisted = documents.retainReadPermission(uri, flags)
            val result = metadata.read(documents.describe(uri), standalonePersisted = persisted)
            update { LibraryEdits.upsertTracks(it, listOf(result.track)) }
            result
        }
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

    suspend fun resolveLocation(source: FolderSource, relativePath: String): BrowserLocation = withContext(Dispatchers.IO) {
        require(com.macarmusic.app.model.PlaybackCollection.validPath(relativePath))
        if (source.needsReconnect) throw IOException(context.getString(R.string.reconnect_folder_first))
        var id = documents.rootId(source.uri)
        var name = source.name
        if (relativePath.isNotEmpty()) for (part in relativePath.split('/')) {
            val folder = documents.children(source.uri, id).singleOrNull { it.isDirectory && it.name == part }
                ?: throw IOException(context.getString(R.string.document_unavailable))
            id = folder.id
            name = folder.name
        }
        BrowserLocation(source.uri, id, name, relativePath)
    }

    suspend fun scan(
        source: FolderSource,
        location: BrowserLocation?,
        forceMetadata: Boolean = false,
        deferMetadata: Boolean = false,
        onProgress: (ScanProgress) -> Unit,
    ): ScanResult = withContext(Dispatchers.IO) {
        if (MediaStoreAccess.folder(Uri.parse(source.uri)) != null) {
            return@withContext scanIndexed(listOf(source to location), forceMetadata, deferMetadata, onProgress).single()
        }
        ready.await()
        if (loadFailed) throw IOException(context.getString(R.string.library_read_only))
        cancelMetadata()
        val saved = mutableLibrary.value
        val known = saved.tracks.associateBy { it.id }
        val reuseMetadata = !forceMetadata && !source.unavailable &&
            saved.sources.firstOrNull { it.uri == source.uri }?.unavailable == false
        val rootId = documents.rootId(source.uri)
        val tree = Uri.parse(source.uri)
        val documentProvider = DocumentsContract.isDocumentUri(context,
            DocumentsContract.buildDocumentUriUsingTree(tree, rootId))
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
        var reused = 0
        for ((index, entry) in entries.values.withIndex()) {
            currentCoroutineContext().ensureActive()
            // All entries came from this provider's child query; resolve its document authority once.
            val identity = if (documentProvider) DocumentsContract.buildDocumentUri(tree.authority, entry.id).toString()
                else entry.uri
            val previous = known[identity]
            val result = if (reuseMetadata && MetadataReuse.unchanged(previous, entry)) {
                reused++
                MetadataResult(checkNotNull(previous).copy(uri = entry.uri,
                    sourceUris = mapOf(source.uri to entry.uri), missing = false), warning = false)
            } else if (deferMetadata && !forceMetadata) {
                MetadataResult(pendingReference(entry, source.uri, previous, identity), warning = false)
            } else metadata.read(entry, source.uri, identity = identity)
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
                val merged = LibraryEdits.applyScan(library, source.uri, tracks, System.currentTimeMillis(), null)
                missing = merged.tracks.count { source.uri in it.sourceUris && it.id !in ids }
                val issue = if (missing > 0) context.getString(R.string.scan_missing, missing) else null
                merged.copy(sources = merged.sources.map { if (it.uri == source.uri) it.copy(lastIssue = issue) else it })
            } else LibraryEdits.upsertTracks(library, tracks)
        }
        resumeMetadata()
        ScanResult(tracks, warnings, missing, reused)
    }

    suspend fun scanIndexed(
        requests: List<Pair<FolderSource, BrowserLocation?>>,
        forceMetadata: Boolean = false,
        deferMetadata: Boolean = false,
        onProgress: (ScanProgress) -> Unit,
    ): List<ScanResult> = withContext(Dispatchers.IO) {
        ready.await()
        if (loadFailed) throw IOException(context.getString(R.string.library_read_only))
        if (requests.isEmpty()) return@withContext emptyList()
        cancelMetadata()
        val roots = requests.map { (source, location) ->
            val folder = requireNotNull(MediaStoreAccess.folder(Uri.parse(source.uri)))
            val path = location?.documentId ?: folder.path
            require(MediaStoreAccess.contains(folder.path, path)) { "Scan outside selected source" }
            folder.copy(path = path)
        }
        onProgress(ScanProgress(context.getString(R.string.device_music_loading)))
        val catalog = documents.deviceMusic.scanCatalog(roots)
        val saved = library.value
        val known = saved.tracks.associateBy { it.id }
        val available = saved.sources.filterNot { it.unavailable }.mapTo(hashSetOf()) { it.uri }
        val extracted = hashMapOf<String, MetadataResult>()
        val scans = mutableListOf<LibraryScan>()
        val results = requests.mapIndexed { index, (source, location) ->
            val root = roots[index]
            val full = root.path == documents.rootId(source.uri)
            val startPath = if (full) "" else location?.relativePath?.ifBlank { location.name }.orEmpty()
            var reused = 0
            var indexed = 0
            var warnings = 0
            val tracks = catalog.getValue(root).mapIndexed { songIndex, song ->
                currentCoroutineContext().ensureActive()
                val entry = song.entry
                val previous = known[entry.uri]
                val canReuse = !forceMetadata && !source.unavailable && source.uri in available &&
                    MetadataReuse.unchanged(previous, entry)
                val result = when {
                    canReuse -> {
                        reused++
                        MetadataResult(checkNotNull(previous), warning = false)
                    }
                    !forceMetadata && song.tags != null && song.tags.durationMs > 0 -> {
                        indexed++
                        MetadataResult(song.tags.toTrack(entry), warning = false)
                    }
                    deferMetadata && !forceMetadata ->
                        MetadataResult(pendingReference(entry, source.uri, previous), warning = false)
                    else -> extracted.getOrPut(entry.uri) { metadata.read(entry, source.uri) }
                }
                if (result.warning) warnings++
                if (songIndex % 32 == 0) onProgress(ScanProgress(documents.displayName(source), songIndex + 1))
                result.track.copy(
                    uri = entry.uri, missing = false, standaloneUri = null, standalonePersisted = false,
                    sourceUris = mapOf(source.uri to entry.uri),
                    sourceParents = mapOf(source.uri to song.folder.path),
                    sourcePaths = mapOf(source.uri to listOf(startPath,
                        song.folder.path.removePrefix(root.path).removeSuffix("/"), entry.name)
                        .filter(String::isNotEmpty).joinToString("/")),
                )
            }
            scans.add(LibraryScan(source.uri, tracks, full))
            ScanResult(tracks, warnings, 0, reused, indexed)
        }
        currentCoroutineContext().ensureActive()
        if (!documents.deviceMusic.hasPermission()) throw SecurityException(context.getString(R.string.music_permission_denied))
        val missing = IntArray(requests.size)
        update { current ->
            val sources = current.sources.mapTo(hashSetOf()) { it.uri }
            if (requests.any { it.first.uri !in sources }) throw IOException(context.getString(R.string.source_removed))
            val merged = LibraryEdits.applyScans(current, scans, System.currentTimeMillis()) {
                context.getString(R.string.scan_missing, it)
            }
            scans.forEachIndexed { index, scan ->
                if (scan.fullSource) {
                    val ids = scan.tracks.mapTo(hashSetOf()) { it.id }
                    missing[index] = merged.tracks.count { scan.sourceUri in it.sourceUris && it.id !in ids }
                }
            }
            merged
        }
        resumeMetadata()
        results.mapIndexed { index, result -> result.copy(missingCount = missing[index]) }
    }

    suspend fun metadataForDetails(requested: Track): MetadataResult = withContext(Dispatchers.IO) {
        val track = library.value.tracks.firstOrNull { it.id == requested.id && it.uri == requested.uri } ?: requested
        if (!track.metadataFromIndex && !track.metadataPending) return@withContext MetadataResult(track, warning = false)
        val entry = documents.describe(Uri.parse(track.uri))
        val result = metadata.read(entry).let { read ->
            if (read.warning) return@withContext MetadataResult(track, warning = true)
            read.copy(track = read.track.copy(
                sourceUris = track.sourceUris, sourceParents = track.sourceParents, sourcePaths = track.sourcePaths,
                standaloneUri = track.standaloneUri, standalonePersisted = track.standalonePersisted,
                restoredIds = track.restoredIds,
            ))
        }
        if (!result.warning) update { current ->
            if (current.tracks.firstOrNull { it.id == track.id } != track) current
            else LibraryEdits.upsertTracks(current, listOf(result.track))
        }
        result
    }

    suspend fun metadataForLyrics(tracks: List<Track>, onProgress: (ScanProgress) -> Unit): List<Track> =
        withContext(Dispatchers.IO) {
            if (tracks.none { it.metadataPending }) return@withContext tracks
            cancelMetadata()
            try {
                val known = library.value.tracks.associateBy { it.id }
                val updates = mutableListOf<MetadataUpdate>()
                var warnings = 0
                val resolved = tracks.mapIndexed { index, track ->
                    currentCoroutineContext().ensureActive()
                    val latest = known[track.id] ?: track
                    if (!latest.metadataPending) latest else {
                        onProgress(ScanProgress(latest.fileName, index + 1))
                        val read = metadata.read(MetadataEdits.entry(latest), identity = latest.id)
                        if (read.warning) warnings++
                        updates.add(MetadataUpdate(latest, if (read.warning) latest.copy(metadataStamp = null) else read.track))
                        MetadataEdits.complete(Library(tracks = listOf(latest)), listOf(updates.last())).tracks.single()
                    }
                }
                currentCoroutineContext().ensureActive()
                if (updates.isNotEmpty()) update { MetadataEdits.complete(it, updates) }
                if (warnings > 0) report(context.getString(R.string.metadata_background_warnings, warnings))
                resolved
            } finally {
                resumeMetadata()
            }
        }

    fun cancelMetadata() { enrichment.cancel() }

    private fun resumeMetadata() {
        if (isReadable && library.value.tracks.any { it.metadataPending && !it.missing }) enrichment.resume()
    }

    private fun pendingReference(entry: com.macarmusic.app.model.DocumentEntry, source: String, previous: Track?,
        identity: String? = null,
    ): Track {
        val reference = metadata.reference(entry, source, identity = identity)
        return (previous ?: reference).copy(
            uri = entry.uri, fileName = entry.name, mimeType = entry.mimeType, missing = false,
            sourceUris = mapOf(source to entry.uri), standaloneUri = null, standalonePersisted = false,
            metadataStamp = MetadataReuse.stamp(entry), metadataPending = true,
        )
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
