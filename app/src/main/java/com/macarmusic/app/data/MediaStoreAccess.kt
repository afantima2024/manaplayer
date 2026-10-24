package com.macarmusic.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.PermissionChecker
import com.macarmusic.app.R
import com.macarmusic.app.model.AudioFiles
import com.macarmusic.app.model.DocumentEntry
import com.macarmusic.app.model.FolderSource
import com.macarmusic.app.model.IndexedTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

class MediaStoreAccess(private val context: Context) {
    data class Folder(val volume: String, val path: String) {
        val collection: Uri get() = MediaStore.Audio.Media.getContentUri(volume)
        val uri: Uri get() = collection.buildUpon().appendQueryParameter(FOLDER_KEY, path).build()
    }

    data class Song(val folder: Folder, val entry: DocumentEntry, val tags: IndexedTags? = null)

    fun hasPermission(): Boolean = PermissionChecker.checkSelfPermission(context, requiredPermission()) ==
        PermissionChecker.PERMISSION_GRANTED

    private fun requirePermission() {
        if (!hasPermission()) throw SecurityException(context.getString(R.string.music_permission_denied))
    }

    fun source(folder: Folder): FolderSource {
        requirePermission()
        return sourceRecord(folder)
    }

    fun displayName(folder: Folder): String {
        val storage = when (folder.volume) {
            "external", "external_primary" -> context.getString(R.string.device_shared_storage)
            else -> context.getString(R.string.device_storage_volume, folder.volume)
        }
        return context.getString(R.string.device_music_folder_name, storage,
            folder.path.ifEmpty { context.getString(R.string.device_music_unfiled) })
    }

    private fun sourceRecord(folder: Folder): FolderSource = FolderSource(
        uri = folder.uri.toString(),
        name = displayName(folder),
        persisted = true,
    )

    suspend fun folders(): List<FolderSource> = withContext(Dispatchers.IO) {
        readSongs(null).map { it.folder }.distinct().map(::sourceRecord)
            .sortedWith(compareBy<FolderSource> { it.name.lowercase(Locale.ROOT) }.thenBy { it.uri })
    }

    suspend fun children(source: Folder, directory: String): List<DocumentEntry> = withContext(Dispatchers.IO) {
        require(contains(source.path, directory)) { "The requested folder is outside its saved source" }
        val folders = linkedMapOf<String, DocumentEntry>()
        val tracks = mutableListOf<DocumentEntry>()
        for (song in readSongs(source.copy(path = directory))) {
            val child = directChild(directory, song.folder.path)
            if (child == null) {
                if (song.folder.path == directory) tracks.add(song.entry)
            } else {
                folders.getOrPut(child) { DocumentEntry(
                    id = child,
                    uri = source.copy(path = child).uri.toString(),
                    name = child.removeSuffix("/").substringAfterLast('/'),
                    mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                    isDirectory = true,
                    isVirtual = false,
                ) }
            }
        }
        folders.values.toList() + tracks
    }

    suspend fun scanCatalog(folders: List<Folder>): Map<Folder, List<Song>> = withContext(Dispatchers.IO) {
        val result = folders.distinct().associateWith { mutableListOf<Song>() }
        var matched = 0
        for ((volume, roots) in result.keys.groupBy { it.volume }) {
            val selected = roots.associateBy { it.path }
            // One query for a volume replaces a subtree query for every directory and chosen root.
            for (song in readSongs(roots.singleOrNull(), volume, includeTags = true)) {
                ensureActive()
                var path = song.folder.path
                while (true) {
                    selected[path]?.let {
                        val depth = song.folder.path.substring(path.length).count { character -> character == '/' }
                        if (depth > 64 || ++matched > 100_000) {
                            throw IOException(context.getString(R.string.folder_safety_limit))
                        }
                        result.getValue(it).add(song)
                    }
                    if (path.isEmpty() || path == "/") break
                    val trimmed = path.removeSuffix("/")
                    val slash = trimmed.lastIndexOf('/')
                    if (slash < 0) break
                    path = trimmed.substring(0, slash + 1)
                }
            }
        }
        for ((folder, songs) in result) {
            if (Build.VERSION.SDK_INT < 29 && songs.isEmpty()) {
                throw IOException(context.getString(R.string.document_unavailable) + ": " + displayName(folder))
            }
        }
        result
    }

    private suspend fun readSongs(folder: Folder?, volume: String? = folder?.volume,
        includeTags: Boolean = false,
    ): List<Song> = withContext(Dispatchers.IO) {
        requirePermission()
        if (volume != null && volume != "external") {
            if (Build.VERSION.SDK_INT < 29 || volume !in MediaStore.getExternalVolumeNames(context)) {
                throw IOException(context.getString(R.string.document_unavailable))
            }
        }
        @Suppress("DEPRECATION")
        val pathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.RELATIVE_PATH
            else MediaStore.MediaColumns.DATA
        val columns = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE, pathColumn,
            MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED) +
            if (Build.VERSION.SDK_INT >= 29) arrayOf(MediaStore.MediaColumns.VOLUME_NAME) else emptyArray()
        val tagColumns = arrayOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR) + if (Build.VERSION.SDK_INT >= 30) arrayOf(MediaStore.Audio.Media.GENRE) else emptyArray()
        val prefix = folder?.path?.takeIf { it.isNotEmpty() }
        val selection = prefix?.let { "$pathColumn LIKE ? ESCAPE '\\'" }
        val arguments = prefix?.let { arrayOf(likePrefix(it)) }
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                val collection = volume?.let(MediaStore.Audio.Media::getContentUri) ?: MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                var queriedTags = includeTags
                val cursor = try {
                    context.contentResolver.query(collection, if (includeTags) columns + tagColumns else columns,
                        selection, arguments, null, signal)
                } catch (error: IllegalArgumentException) {
                    if (!includeTags) throw error
                    Log.w("MediaStoreAccess", "Provider rejected indexed tags; using file metadata instead", error)
                    queriedTags = false
                    context.contentResolver.query(collection, columns, selection, arguments, null, signal)
                } ?: throw IOException(context.getString(R.string.provider_no_response))
                val songs = cursor.use {
                    buildList {
                        var examined = 0
                        while (it.moveToNext()) {
                            continuation.context.ensureActive()
                            if (++examined > 100_000) throw IOException(context.getString(R.string.folder_safety_limit))
                            // Old MediaStore paths are metadata for grouping only; playback still uses content URIs.
                            val path = if (Build.VERSION.SDK_INT >= 29) normalizeFolder(it.text(pathColumn))
                                else legacyFolder(it.text(pathColumn))
                            if (folder != null && !contains(folder.path, path)) continue
                            val name = it.text(MediaStore.Audio.Media.DISPLAY_NAME)
                            val mime = it.text(MediaStore.Audio.Media.MIME_TYPE)
                            if (!AudioFiles.accepts(name, mime)) continue
                            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                            if (it.isNull(idColumn)) throw IOException(context.getString(R.string.invalid_document_id))
                            val id = it.getLong(idColumn)
                            if (id < 0) throw IOException(context.getString(R.string.invalid_document_id))
                            val rowVolume = it.text(MediaStore.MediaColumns.VOLUME_NAME)
                                .ifEmpty { volume ?: "external" }
                            val location = Folder(rowVolume, path)
                            val uri = ContentUris.withAppendedId(location.collection, id)
                            add(Song(location, DocumentEntry(
                                id = uri.toString(), uri = uri.toString(),
                                name = name.ifBlank { context.getString(R.string.unnamed_file) },
                                mimeType = mime, isDirectory = false, isVirtual = false,
                                sizeBytes = it.positiveLong(MediaStore.MediaColumns.SIZE),
                                modifiedMs = it.positiveLong(MediaStore.MediaColumns.DATE_MODIFIED)
                                    ?.takeIf { seconds -> seconds <= Long.MAX_VALUE / 1000 }?.times(1000),
                            ), if (queriedTags) IndexedTags(
                                title = it.text(MediaStore.Audio.Media.TITLE),
                                artist = it.text(MediaStore.Audio.Media.ARTIST),
                                album = it.text(MediaStore.Audio.Media.ALBUM),
                                durationMs = it.positiveLong(MediaStore.Audio.Media.DURATION) ?: 0,
                                track = it.positiveLong(MediaStore.Audio.Media.TRACK) ?: 0,
                                year = it.text(MediaStore.Audio.Media.YEAR),
                                genre = if (Build.VERSION.SDK_INT >= 30) it.text(MediaStore.Audio.Media.GENRE) else "",
                            ) else null))
                        }
                    }
                }
                if (Build.VERSION.SDK_INT < 29 && folder != null && songs.isEmpty()) {
                    // Legacy providers cannot report individual USB mount states. Preserve saved references.
                    throw IOException(context.getString(R.string.document_unavailable))
                }
                if (continuation.isActive) continuation.resume(songs)
            } catch (error: OperationCanceledException) {
                continuation.context.ensureActive()
                throw IOException(context.getString(R.string.device_music_query_cancelled), error)
            }
        }
    }

    private fun Cursor.text(column: String): String {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getString(index).orEmpty() else ""
    }

    private fun Cursor.positiveLong(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getLong(index).takeIf { it > 0 } else null
    }

    companion object {
        private const val FOLDER_KEY = "manaplayer_folder"

        @SuppressLint("InlinedApi") // These are inlined permission names, selected explicitly by SDK level.
        fun requiredPermission(sdk: Int = Build.VERSION.SDK_INT): String =
            if (sdk >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

        fun folder(uri: Uri): Folder? {
            if (uri.scheme != "content" || uri.authority != "media" || uri.pathSegments.size != 3 ||
                uri.pathSegments[1] != "audio" || uri.pathSegments[2] != "media" ||
                uri.queryParameterNames != setOf(FOLDER_KEY)
            ) return null
            val path = uri.getQueryParameter(FOLDER_KEY) ?: return null
            if (path != normalizeFolder(path)) return null
            return Folder(uri.pathSegments[0], path)
        }

        internal fun isAudioFile(uri: Uri): Boolean = uri.scheme == "content" && uri.authority == "media" &&
            uri.query == null && uri.fragment == null && uri.pathSegments.let {
                it.size == 4 && it[0].isNotBlank() && it[1] == "audio" && it[2] == "media" &&
                    it[3].toLongOrNull()?.let { id -> id >= 0 } == true
            }

        internal fun normalizeFolder(path: String): String = when {
            path.isEmpty() -> ""
            path.endsWith('/') -> path
            else -> "$path/"
        }

        internal fun legacyFolder(file: String): String =
            if (file.startsWith('/') && !file.endsWith('/')) file.substringBeforeLast('/') + "/" else ""

        internal fun contains(parent: String, child: String): Boolean =
            child == parent || (parent.isNotEmpty() && child.startsWith(parent))

        internal fun directChild(parent: String, descendant: String): String? {
            if (!contains(parent, descendant) || parent == descendant) return null
            return parent + descendant.removePrefix(parent).substringBefore('/') + "/"
        }

        internal fun likePrefix(path: String): String =
            path.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    }
}
