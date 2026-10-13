package com.macarmusic.app.data

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import com.macarmusic.app.R
import com.macarmusic.app.model.AudioFiles
import com.macarmusic.app.model.DocumentEntry
import com.macarmusic.app.model.FolderSource
import com.macarmusic.app.lyrics.LyricFiles
import java.io.IOException
import java.util.Locale

class DocumentAccess(private val context: Context) {
    private val resolver = context.contentResolver
    val deviceMusic by lazy { MediaStoreAccess(context) }

    fun displayName(source: FolderSource): String =
        MediaStoreAccess.folder(Uri.parse(source.uri))?.let(deviceMusic::displayName) ?: source.name

    fun requireContentUri(uri: Uri) {
        if (uri.scheme != "content" || uri.authority.isNullOrBlank()) {
            throw IOException(context.getString(R.string.local_documents_only))
        }
    }

    fun rootId(sourceUri: String): String {
        val uri = Uri.parse(sourceUri)
        return MediaStoreAccess.folder(uri)?.path ?: DocumentsContract.getTreeDocumentId(uri)
    }

    fun identity(uri: Uri): String {
        requireContentUri(uri)
        return if (DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.buildDocumentUri(uri.authority, DocumentsContract.getDocumentId(uri)).toString()
        } else uri.toString()
    }

    fun retainReadPermission(uri: Uri, resultFlags: Int): Boolean {
        requireContentUri(uri)
        if (resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) return true
        if (resultFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION == 0 ||
            resultFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0
        ) return false
        return try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        } catch (error: SecurityException) {
            Log.w(TAG, "Provider did not retain a read grant", error)
            false
        }
    }

    fun source(uri: Uri, resultFlags: Int): FolderSource {
        requireContentUri(uri)
        MediaStoreAccess.folder(uri)?.let { return deviceMusic.source(it) }
        val treeId = DocumentsContract.getTreeDocumentId(uri)
        val persisted = retainReadPermission(uri, resultFlags)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, treeId)
        val entry = describe(documentUri)
        if (!entry.isDirectory) throw IOException(context.getString(R.string.invalid_folder))
        return FolderSource(uri.toString(), entry.name, persisted)
    }

    fun describe(uri: Uri): DocumentEntry {
        requireContentUri(uri)
        val isDocument = DocumentsContract.isDocumentUri(context, uri)
        val projection = if (isDocument) DOCUMENT_COLUMNS else arrayOf(OpenableColumns.DISPLAY_NAME)
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
            )
        }
    }

    suspend fun children(sourceUri: String, documentId: String, includeLyrics: Boolean = false): List<DocumentEntry> {
        val tree = Uri.parse(sourceUri)
        requireContentUri(tree)
        MediaStoreAccess.folder(tree)?.let {
            return sorted(deviceMusic.children(it, documentId))
        }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val cursor = resolver.query(childrenUri, DOCUMENT_COLUMNS, null, null, null)
            ?: throw IOException(context.getString(R.string.provider_no_response))
        val entries = cursor.use {
            checkProviderState(it)
            buildList {
                var examined = 0
                while (it.moveToNext()) {
                    if (++examined > 100_000) throw IOException(context.getString(R.string.folder_safety_limit))
                    val id = it.text(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    if (id.isBlank()) throw IOException(context.getString(R.string.invalid_document_id))
                    val name = it.text(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        .ifBlank { context.getString(R.string.unnamed_file) }
                    val mime = it.text(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val folder = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    val lyric = LyricFiles.accepts(name)
                    if (folder || (lyric && includeLyrics) || (!lyric && AudioFiles.accepts(name, mime))) {
                        add(DocumentEntry(
                            id, DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                            name, mime, folder, it.flags() and FLAG_VIRTUAL_DOCUMENT != 0,
                        ))
                    }
                }
            }
        }
        return sorted(entries)
    }

    private fun sorted(entries: List<DocumentEntry>): List<DocumentEntry> = entries.sortedWith(
        compareByDescending<DocumentEntry> { it.isDirectory }
            .thenBy { it.name.lowercase(Locale.ROOT) }.thenBy { it.id },
    )

    fun releaseReadPermission(sourceUri: String) {
        val uri = Uri.parse(sourceUri)
        if (MediaStoreAccess.folder(uri) != null) return
        if (resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
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

    private companion object {
        const val TAG = "DocumentAccess"
        const val FLAG_VIRTUAL_DOCUMENT = DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT
        val DOCUMENT_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
    }
}
