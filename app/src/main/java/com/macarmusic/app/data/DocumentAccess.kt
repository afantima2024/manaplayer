package com.macarmusic.app.data

import android.content.Context
import android.content.Intent
import android.annotation.SuppressLint
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.util.Log
import com.macarmusic.app.R
import com.macarmusic.app.model.AudioFiles
import com.macarmusic.app.model.DocumentEntry
import com.macarmusic.app.model.FolderSource
import com.macarmusic.app.model.Track
import com.macarmusic.app.lyrics.LyricFiles
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

class DocumentAccess(private val context: Context) {
    private val resolver = context.contentResolver
    internal val grantMutex = Mutex()
    val deviceMusic by lazy { MediaStoreAccess(context) }

    fun displayName(source: FolderSource): String =
        MediaStoreAccess.folder(Uri.parse(source.uri))?.let(deviceMusic::displayName) ?: source.name

    @Suppress("DEPRECATION")
    fun location(track: Track, sources: List<FolderSource>): String {
        val uri = Uri.parse(track.uri)
        if (uri.authority == "com.android.externalstorage.documents" && DocumentsContract.isDocumentUri(context, uri)) {
            val id = DocumentsContract.getDocumentId(uri)
            val volume = id.substringBefore(':')
            if (':' in id) return "${storageName(volume)} / ${id.substringAfter(':')}"
        }
        for ((sourceUri, relative) in track.sourcePaths) {
            val source = sources.firstOrNull { it.uri == sourceUri } ?: continue
            return "${displayName(source)} / $relative"
        }
        if (uri.authority == "media" && Build.VERSION.SDK_INT < 29) {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
                if (it.moveToFirst()) {
                    val path = it.text(MediaStore.MediaColumns.DATA)
                    if (MediaStoreAccess.legacyFolder(path).isNotEmpty()) return path
                }
            }
        } else if (uri.authority == "media") {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.VOLUME_NAME, OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) {
                    val path = it.text(MediaStore.MediaColumns.RELATIVE_PATH)
                    if (path.isNotBlank()) return "${storageName(it.text(MediaStore.MediaColumns.VOLUME_NAME))} / " +
                        path + it.text(OpenableColumns.DISPLAY_NAME).ifBlank { track.fileName }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 26 && DocumentsContract.isTreeUri(uri)) {
            val root = DocumentsContract.getTreeDocumentId(uri)
            val path = DocumentsContract.findDocumentPath(resolver, uri)?.path.orEmpty()
            val rootIndex = path.indexOf(root)
            if (rootIndex >= 0 && path.size - rootIndex in 1..65) {
                return path.drop(rootIndex).joinToString(" / ") {
                    describe(DocumentsContract.buildDocumentUriUsingTree(uri, it)).name
                }
            }
        }
        return partialLocation(track, sources)
    }

    fun partialLocation(track: Track, sources: List<FolderSource>): String {
        val source = sources.firstOrNull { it.uri in track.sourceUris }?.let(::displayName)
        val file = listOfNotNull(source, track.fileName).joinToString(" / ")
        return context.getString(R.string.file_location_partial, file)
    }

    private fun storageName(volume: String): String = when (volume) {
        "primary", "external", "external_primary", "" -> context.getString(R.string.device_shared_storage)
        else -> context.getString(R.string.device_storage_volume, volume)
    }

    fun requireContentUri(uri: Uri) {
        if (uri.scheme != "content" || uri.authority.isNullOrBlank()) {
            throw IOException(context.getString(R.string.local_documents_only))
        }
    }

    fun rootId(sourceUri: String): String {
        val uri = Uri.parse(sourceUri)
        return MediaStoreAccess.folder(uri)?.path ?: DocumentUris.rootId(uri)
    }

    fun identity(uri: Uri): String {
        requireContentUri(uri)
        return if (DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.buildDocumentUri(uri.authority, DocumentsContract.getDocumentId(uri)).toString()
        } else uri.toString()
    }

    fun retainReadPermission(uri: Uri, resultFlags: Int): Boolean {
        requireContentUri(uri)
        if (LegacyStorage.owns(context, uri)) return LegacyStorage.permitted(context)
        if (MediaStoreAccess.isAudioFile(uri) && deviceMusic.hasPermission()) return true
        val grant = DocumentUris.grant(uri)
        if (resolver.persistedUriPermissions.any { it.uri == grant && it.isReadPermission }) return true
        if (resultFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION == 0 ||
            resultFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0
        ) return false
        return try {
            resolver.takePersistableUriPermission(grant, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        } catch (error: SecurityException) {
            Log.w(TAG, "Provider did not retain a read grant", error)
            false
        }
    }

    fun source(uri: Uri, resultFlags: Int): FolderSource {
        requireContentUri(uri)
        MediaStoreAccess.folder(uri)?.let { return deviceMusic.source(it) }
        val treeId = DocumentUris.rootId(uri)
        val persisted = retainReadPermission(uri, resultFlags)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, treeId)
        val entry = describe(documentUri)
        if (!entry.isDirectory) throw IOException(context.getString(R.string.invalid_folder))
        return FolderSource(uri.toString(), entry.name, persisted)
    }

    fun describe(uri: Uri): DocumentEntry {
        requireContentUri(uri)
        val isDocument = DocumentsContract.isDocumentUri(context, uri)
        val isIndexed = uri.authority == "media"
        val projection = if (isDocument) DOCUMENT_COLUMNS else
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE) +
                if (isIndexed) arrayOf(MediaStore.MediaColumns.DATE_MODIFIED) else emptyArray()
        val cursor = resolver.query(uri, projection, null, null, null)
            ?: throw IOException(context.getString(R.string.provider_no_response))
        return cursor.use {
            checkProviderState(it)
            if (!it.moveToFirst()) throw IOException(context.getString(R.string.document_unavailable))
            val name = it.text(OpenableColumns.DISPLAY_NAME)
                .ifBlank { uri.lastPathSegment ?: context.getString(R.string.unnamed_file) }
            val mime = if (isDocument) it.text(DocumentsContract.Document.COLUMN_MIME_TYPE)
            else resolver.getType(uri).orEmpty()
            DocumentEntry(
                id = if (isDocument) it.text(DocumentsContract.Document.COLUMN_DOCUMENT_ID) else identity(uri),
                uri = uri.toString(),
                name = name,
                mimeType = mime,
                isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                isVirtual = isDocument && it.flags() and FLAG_VIRTUAL_DOCUMENT != 0,
                sizeBytes = it.positiveLong(OpenableColumns.SIZE),
                modifiedMs = if (isDocument) it.positiveLong(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    else if (isIndexed) it.positiveLong(MediaStore.MediaColumns.DATE_MODIFIED)
                        ?.takeIf { seconds -> seconds <= Long.MAX_VALUE / 1000 }?.times(1000) else null,
                flags = if (isDocument) it.flags() else 0,
            )
        }
    }

    suspend fun children(sourceUri: String, documentId: String, includeLyrics: Boolean = false,
        allFiles: Boolean = false,
    ): List<DocumentEntry> = withContext(Dispatchers.IO) {
        val tree = Uri.parse(sourceUri)
        requireContentUri(tree)
        MediaStoreAccess.folder(tree)?.let {
            return@withContext sorted(deviceMusic.children(it, documentId))
        }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                val cursor = resolver.query(childrenUri, DOCUMENT_COLUMNS, null, null, null, signal)
                    ?: throw IOException(context.getString(R.string.provider_no_response))
                val entries = cursor.use {
                    checkProviderState(it)
                    buildList {
                        var examined = 0
                        while (it.moveToNext()) {
                            continuation.context.ensureActive()
                            if (++examined > 100_000) throw IOException(context.getString(R.string.folder_safety_limit))
                            val id = it.text(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                            if (id.isBlank()) throw IOException(context.getString(R.string.invalid_document_id))
                            val name = it.text(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                                .ifBlank { context.getString(R.string.unnamed_file) }
                            val mime = it.text(DocumentsContract.Document.COLUMN_MIME_TYPE)
                            val folder = mime == DocumentsContract.Document.MIME_TYPE_DIR
                            val lyric = LyricFiles.accepts(name)
                            if (allFiles || folder || (lyric && includeLyrics) || (!lyric && AudioFiles.accepts(name, mime))) {
                                add(DocumentEntry(
                                    id, DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                                    name, mime, folder, it.flags() and FLAG_VIRTUAL_DOCUMENT != 0,
                                    it.positiveLong(DocumentsContract.Document.COLUMN_SIZE),
                                    it.positiveLong(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                                    it.flags(),
                                ))
                            }
                        }
                    }
                }
                if (continuation.isActive) continuation.resume(sorted(entries))
            } catch (error: OperationCanceledException) {
                continuation.context.ensureActive()
                throw IOException(context.getString(R.string.provider_no_response), error)
            }
        }
    }

    private fun sorted(entries: List<DocumentEntry>): List<DocumentEntry> = entries.sortedWith(
        compareByDescending<DocumentEntry> { it.isDirectory }
            .thenBy { it.name.lowercase(Locale.ROOT) }.thenBy { it.id },
    )

    suspend fun releaseReadPermission(sourceUri: String) {
        releaseReadPermissions(setOf(sourceUri)).firstOrNull()?.let { throw it }
    }

    suspend fun releaseReadPermissions(sourceUris: Set<String>,
        retainedUris: Set<String> = emptySet(),
    ): List<SecurityException> = releaseReadPermissions(sourceUris) { retainedUris }

    internal suspend fun releaseReadPermissions(sourceUris: Set<String>,
        retainedUris: suspend () -> Set<String>,
    ): List<SecurityException> = grantMutex.withLock {
        buildList {
            val removed = sourceUris.map { DocumentUris.grant(Uri.parse(it)) }.toSet()
            val retained = retainedUris().map { DocumentUris.grant(Uri.parse(it)) }.toSet()
            // Music sources only acquire read grants; writable grants may also belong to the portable lyric cache.
            val grants = resolver.persistedUriPermissions.filter {
                it.isReadPermission && !it.isWritePermission && it.uri in removed && it.uri !in retained
            }
            for (grant in grants) {
                try {
                    resolver.releasePersistableUriPermission(grant.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (error: SecurityException) {
                    add(error)
                }
            }
        }
    }

    private fun checkProviderState(cursor: Cursor) {
        val extras = cursor.extras
        extras.getString(DocumentsContract.EXTRA_ERROR)?.takeIf(String::isNotBlank)?.let {
            throw IOException(context.getString(R.string.provider_error, it))
        }
        if (extras.getBoolean(DocumentsContract.EXTRA_LOADING, false)) {
            throw IOException(context.getString(R.string.provider_loading))
        }
    }

    private fun Cursor.text(column: String): String {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getString(index).orEmpty() else ""
    }

    private fun Cursor.flags(): Int {
        val index = getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
        return if (index >= 0 && !isNull(index)) getInt(index) else 0
    }

    private fun Cursor.positiveLong(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getLong(index).takeIf { it > 0 } else null
    }

    private companion object {
        const val TAG = "DocumentAccess"
        @SuppressLint("InlinedApi") // Recognize provider flags without calling API 24-only virtual-document APIs.
        const val FLAG_VIRTUAL_DOCUMENT = DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT
        val DOCUMENT_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
