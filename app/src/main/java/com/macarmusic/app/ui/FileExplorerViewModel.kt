package com.macarmusic.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.macarmusic.app.MaCarMusicApp
import com.macarmusic.app.R
import com.macarmusic.app.data.DocumentUris
import com.macarmusic.app.data.LegacyStorage
import com.macarmusic.app.data.MediaStoreAccess
import com.macarmusic.app.model.BackgroundImages
import com.macarmusic.app.model.DocumentEntry
import com.macarmusic.app.model.ExplorerMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

@Serializable internal enum class ExplorerPlace { DEVICE, INDEX, FOLDER }

@Serializable internal data class ExplorerLocation(
    val place: ExplorerPlace, val name: String, val uri: String = "",
)

@Serializable internal data class ExplorerSelection(val uri: String, val name: String)

internal data class ExplorerItem(
    val name: String,
    val detail: String = "",
    val location: ExplorerLocation? = null,
    val document: DocumentEntry? = null,
    val grant: Boolean = false,
) {
    val key get() = document?.uri ?: location?.let { "${it.place}:${it.uri}" } ?: "grant"
}

internal data class ExplorerResult(val uris: List<String>, val flags: Int)

internal data class ExplorerState(
    val path: List<ExplorerLocation> = emptyList(),
    val items: List<ExplorerItem> = emptyList(),
    val selected: Set<String> = emptySet(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val canSelectFolder: Boolean = false,
    val error: String? = null,
    val result: ExplorerResult? = null,
)

internal class FileExplorerViewModel(application: Application, private val saved: SavedStateHandle) :
    AndroidViewModel(application) {
    private val app = application as MaCarMusicApp
    private val context = app.localizedContext
    private val documents = app.repository.documents
    private val resolver = context.contentResolver
    private val labels = context.getSharedPreferences("explorer", Context.MODE_PRIVATE)
    val mode = ExplorerMode.valueOf(requireNotNull(saved.get<String>(EXTRA_MODE)))
    private var restoreError: String? = null
    private var path = restore<List<ExplorerLocation>>("path", emptyList())
    private var selections = restore<List<ExplorerSelection>>("selections", emptyList())
    private var temporaryRoots = restore<List<String>>("temporaryRoots", emptyList())
    private val mutableState = MutableStateFlow(ExplorerState(path = path, selected = selections.map { it.uri }.toSet()))
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var generation = 0
    var externalGrant: Boolean
        get() = saved["externalGrant"] ?: false
        set(value) { saved["externalGrant"] = value }
    var permissionTarget: String?
        get() = saved["permissionTarget"]
        set(value) { saved["permissionTarget"] = value }
    var filename: String
        get() = saved[EXTRA_FILENAME] ?: ""
        set(value) { saved[EXTRA_FILENAME] = value }

    init {
        if (path.size > 64 || selections.size > BackgroundImages.MAX_COUNT || !withinStateLimit(path, selections)) {
            path = emptyList()
            selections = emptyList()
            restoreError = context.getString(R.string.explorer_unavailable)
        }
        refresh()
    }

    private inline fun <reified T> restore(key: String, default: T): T = try {
        saved.get<String>(key)?.let { Json.decodeFromString<T>(it) } ?: default
    } catch (error: SerializationException) {
        Log.w(TAG, "Cannot restore explorer navigation", error)
        restoreError = context.getString(R.string.explorer_unavailable)
        default
    }

    fun navigate(location: ExplorerLocation) {
        if (state.value.saving) return
        if (path.size >= 64 || !withinStateLimit(path + location, selections)) {
            error(context.getString(R.string.folder_safety_limit))
            return
        }
        path = path + location
        remember()
        refresh()
    }

    fun up(): Boolean {
        if (state.value.saving) return true
        if (path.isEmpty()) return false
        path = path.dropLast(1)
        remember()
        refresh()
        return true
    }

    fun home() {
        if (state.value.saving) return
        path = emptyList()
        remember()
        refresh()
    }

    fun refresh() {
        if (state.value.saving) return
        loadJob?.cancel()
        val request = ++generation
        val location = path.lastOrNull()
        mutableState.value = state.value.copy(path = path, items = emptyList(), loading = true,
            error = null, canSelectFolder = false, selected = selections.map { it.uri }.toSet())
        loadJob = viewModelScope.launch {
            guard {
                val loaded = withContext(Dispatchers.IO) { load(location) }
                if (generation != request) return@guard
                mutableState.value = state.value.copy(items = loaded.first, canSelectFolder = loaded.second,
                    loading = false, error = restoreError)
                restoreError = null
            }
        }
    }

    private suspend fun load(location: ExplorerLocation?): Pair<List<ExplorerItem>, Boolean> {
        if (location == null) return locations() to false
        return when (location.place) {
            ExplorerPlace.DEVICE -> {
                val roots = LegacyStorage.roots(context, refresh = true)
                if (roots.isEmpty()) throw IOException(context.getString(R.string.explorer_no_storage))
                roots.map { root ->
                    val name = LegacyStorage.label(context, root)
                    ExplorerItem(name, root.directory.path, ExplorerLocation(ExplorerPlace.FOLDER, name,
                        LegacyStorage.tree(context, root).toString()))
                } to false
            }
            ExplorerPlace.INDEX -> documents.deviceMusic.folders().map { source ->
                ExplorerItem(source.name, context.getString(R.string.device_music),
                    ExplorerLocation(ExplorerPlace.FOLDER, source.name, source.uri))
            } to false
            ExplorerPlace.FOLDER -> {
                val uri = Uri.parse(location.uri)
                val indexed = MediaStoreAccess.folder(uri) != null
                val folder = if (indexed) null else documents.describe(DocumentUris.document(uri))
                if (folder != null && (!folder.isDirectory || folder.isVirtual))
                    throw IOException(context.getString(R.string.invalid_folder))
                val children = documents.children(location.uri, documents.rootId(location.uri), allFiles = true)
                    .filter { !it.isVirtual && mode.accepts(it.name, it.mimeType, it.isDirectory) }
                    .map { ExplorerItem(it.name, document = it,
                        location = if (it.isDirectory) ExplorerLocation(ExplorerPlace.FOLDER, it.name, it.uri) else null) }
                val selectable = mode.folder && (!mode.writable || folder != null && writable(uri, folder))
                children to selectable
            }
        }
    }

    private fun locations(): List<ExplorerItem> = buildList {
        if (LegacyStorage.available()) {
            val name = context.getString(R.string.explorer_device_storage)
            add(ExplorerItem(name, location = ExplorerLocation(ExplorerPlace.DEVICE, name)))
        }
        if (mode.music) {
            val name = context.getString(R.string.device_music)
            add(ExplorerItem(name, location = ExplorerLocation(ExplorerPlace.INDEX, name)))
        }
        add(ExplorerItem(context.getString(R.string.explorer_add_location), grant = true))
        val sources = app.repository.library.value.sources.associateBy { it.uri }
        val roots = resolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri } +
            temporaryRoots.map(Uri::parse)
        for (uri in roots.distinct().filter { DocumentUris.isTree(it) && !LegacyStorage.owns(context, it) }) {
            val name = labels.getString(uri.toString(), null) ?: sources[uri.toString()]?.name
                ?: DocumentsContract.getTreeDocumentId(uri).substringAfterLast('/').ifEmpty { requireNotNull(uri.authority) }
            add(ExplorerItem(name, context.getString(R.string.explorer_authorized),
                ExplorerLocation(ExplorerPlace.FOLDER, name, uri.toString())))
        }
    }

    private fun writable(uri: Uri, folder: DocumentEntry): Boolean {
        if (folder.flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE == 0) return false
        if (LegacyStorage.owns(context, uri)) return LegacyStorage.permitted(context, write = true)
        if (mode == ExplorerMode.LYRIC_CACHE) return resolver.persistedUriPermissions.any {
            it.uri == DocumentUris.grant(uri) && it.isReadPermission && it.isWritePermission
        }
        return context.checkUriPermission(uri, Process.myPid(), Process.myUid(),
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    fun addGrant(uri: Uri, flags: Int) {
        loadJob?.cancel()
        ++generation
        mutableState.value = state.value.copy(loading = true, error = null)
        loadJob = viewModelScope.launch {
            guard {
                val folder = withContext(Dispatchers.IO) {
                    if (!DocumentUris.isTree(uri)) throw IOException(context.getString(R.string.invalid_folder))
                    val grant = DocumentUris.grant(uri)
                    val wanted = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    if (flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0 && wanted != 0) {
                        try { resolver.takePersistableUriPermission(grant, wanted) }
                        catch (error: SecurityException) {
                            Log.w(TAG, "Folder provider offered only temporary access", error)
                        }
                    }
                    documents.describe(DocumentUris.document(uri)).also {
                        if (!it.isDirectory || it.isVirtual) throw IOException(context.getString(R.string.invalid_folder))
                        val retained = resolver.persistedUriPermissions.map { permission -> permission.uri.toString() }.toSet()
                        val editor = labels.edit()
                        labels.all.keys.filter { key -> key !in retained }.forEach(editor::remove)
                        editor.putString(grant.toString(), it.name).apply()
                    }
                }
                val granted = resolver.persistedUriPermissions.any { it.uri == DocumentUris.grant(uri) && it.isReadPermission }
                if (!granted) {
                    temporaryRoots = (temporaryRoots + uri.toString()).distinct().takeLast(20)
                    saved["temporaryRoots"] = Json.encodeToString(temporaryRoots)
                    restoreError = context.getString(R.string.explorer_grant_temporary)
                }
                path = emptyList()
                navigate(ExplorerLocation(ExplorerPlace.FOLDER, folder.name, uri.toString()))
            }
        }
    }

    fun choose(entry: DocumentEntry) {
        if (state.value.loading || state.value.saving || entry.isDirectory || entry.isVirtual) return
        if (!mode.accepts(entry.name, entry.mimeType, false)) {
            error(context.getString(R.string.explorer_unavailable))
            return
        }
        if (mode == ExplorerMode.IMAGES) {
            val next = if (selections.any { it.uri == entry.uri }) selections.filterNot { it.uri == entry.uri }
                else selections + ExplorerSelection(entry.uri, entry.name)
            if (next.size > BackgroundImages.MAX_COUNT || !withinStateLimit(path, next)) {
                error(context.getString(R.string.explorer_selection_limit, BackgroundImages.MAX_COUNT))
                return
            }
            selections = next
            remember()
            mutableState.value = state.value.copy(selected = selections.map { it.uri }.toSet(), error = null)
        } else finish(listOf(entry.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun select() {
        if (state.value.loading || state.value.saving) return
        if (mode == ExplorerMode.IMAGES) {
            if (selections.isNotEmpty()) finish(selections.map { it.uri }, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            return
        }
        val location = path.lastOrNull()?.takeIf { it.place == ExplorerPlace.FOLDER } ?: return
        if (!state.value.canSelectFolder) {
            error(context.getString(R.string.explorer_read_only))
            return
        }
        if (mode != ExplorerMode.BACKUP_EXPORT) {
            finish(listOf(location.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION or
                if (mode.writable) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
            return
        }
        val name = validFilename() ?: return
        mutableState.value = state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            guard {
                val created = withContext(Dispatchers.IO) {
                    val parent = Uri.parse(location.uri)
                    val entries = documents.children(location.uri, documents.rootId(location.uri), allFiles = true)
                    if (entries.any { it.name.equals(name, true) })
                        throw IOException(context.getString(R.string.explorer_exists))
                    ensureActive()
                    val uri = DocumentsContract.createDocument(resolver, DocumentUris.document(parent), "application/zip", name)
                        ?: throw IOException(context.getString(R.string.explorer_create_failed))
                    if (uri.scheme != "content" || uri.authority != parent.authority ||
                        entries.any { documents.identity(Uri.parse(it.uri)) == documents.identity(uri) })
                        throw IOException(context.getString(R.string.explorer_create_failed))
                    uri.toString()
                }
                finish(listOf(created), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
    }

    fun validFilename(): String? = try { ExplorerMode.backupName(filename) }
    catch (_: IllegalArgumentException) { error(context.getString(R.string.explorer_invalid_name)); null }

    fun externalResult(uris: List<Uri>, flags: Int) {
        val distinct = uris.distinct()
        if (distinct.isEmpty() || distinct.any { it.scheme != "content" || it.authority.isNullOrBlank() }) {
            error(context.getString(R.string.picker_no_document))
        } else if (distinct.size > if (mode == ExplorerMode.IMAGES) BackgroundImages.MAX_COUNT else 1) {
            error(if (mode == ExplorerMode.IMAGES)
                context.getString(R.string.explorer_selection_limit, BackgroundImages.MAX_COUNT)
                else context.getString(R.string.picker_no_document))
        } else finish(distinct.map(Uri::toString), flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION))
    }

    private fun finish(uris: List<String>, flags: Int) {
        mutableState.value = state.value.copy(saving = false, result = ExplorerResult(uris, flags))
    }

    fun error(message: String) {
        mutableState.value = state.value.copy(loading = false, saving = false, error = message)
    }

    private suspend fun guard(action: suspend () -> Unit) {
        try { action() }
        catch (error: CancellationException) { throw error }
        catch (error: SecurityException) { problem(error, R.string.explorer_permission_denied) }
        catch (error: IOException) { problem(error) }
        catch (error: IllegalArgumentException) { problem(error) }
        catch (error: IllegalStateException) { problem(error) }
        catch (error: UnsupportedOperationException) { problem(error) }
    }

    private suspend fun problem(cause: Exception, message: Int = R.string.explorer_unavailable) {
        currentCoroutineContext().ensureActive()
        Log.w(TAG, "Explorer operation failed", cause)
        error(if (cause is IOException && !cause.localizedMessage.isNullOrBlank())
            context.getString(R.string.operation_failed, cause.localizedMessage.orEmpty()) else context.getString(message))
    }

    private fun remember() {
        saved["path"] = Json.encodeToString(path)
        saved["selections"] = Json.encodeToString(selections)
    }

    private fun withinStateLimit(path: List<ExplorerLocation>, selected: List<ExplorerSelection>): Boolean =
        path.sumOf { it.uri.length + it.name.length } + selected.sumOf { it.uri.length + it.name.length } <= 100_000

    companion object {
        const val EXTRA_MODE = "explorerMode"
        const val EXTRA_FILENAME = "explorerFilename"
        private const val TAG = "FileExplorer"
    }
}
