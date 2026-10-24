package com.macarmusic.app.data

import android.database.Cursor
import android.database.MatrixCursor
import android.os.Binder
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.macarmusic.app.R
import com.macarmusic.app.model.LegacyDocumentPaths
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale

class LocalDocumentsProvider : DocumentsProvider() {
    override fun onCreate() = true

    // DocumentsProvider requires an exported, MANAGE_DOCUMENTS-protected declaration.
    // This bridge is deliberately app-only; it is not a storage provider for other apps or DocumentsUI.
    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: arrayOf("root_id", "flags", "icon", "title", "summary", "document_id", "available_bytes"))

    private fun caller(write: Boolean = false) {
        if (Binder.getCallingUid() != Process.myUid()) throw SecurityException("App-only document access")
        LegacyStorage.requirePermission(requireNotNull(context), write)
    }

    private fun resolve(id: String, write: Boolean = false): LegacyDocumentPaths.Document {
        caller(write)
        return try {
            LegacyDocumentPaths.resolve(LegacyStorage.roots(requireNotNull(context)), id).also {
                if (!it.file.exists() || !it.file.canRead()) throw FileNotFoundException()
            }
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Rejected document outside readable shared storage", error)
            throw FileNotFoundException(requireNotNull(context).getString(R.string.explorer_unavailable))
        }
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: COLUMNS).apply {
            val document = resolve(documentId)
            add(document, this, LegacyStorage.permitted(requireNotNull(context), write = true))
        }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val parent = resolve(parentDocumentId)
        val result = MatrixCursor(projection ?: COLUMNS)
        val write = LegacyStorage.permitted(requireNotNull(context), write = true)
        try {
            directory(parent).use { descriptor ->
                val names = File("/proc/self/fd/${descriptor.fd}").list()
                    ?: throw FileNotFoundException(requireNotNull(context).getString(R.string.explorer_unavailable))
                if (names.size > 100_000) throw IOException(requireNotNull(context).getString(R.string.folder_safety_limit))
                for (name in names) {
                    if (!LegacyDocumentPaths.validSegment(name)) continue
                    val file = File(parent.file, name)
                    val document = try { LegacyDocumentPaths.document(parent.root, file) }
                    catch (_: IllegalArgumentException) { continue } // Hide links and other apps' private directories.
                    if (file.canRead() && (file.isDirectory || file.isFile)) add(document, result, write)
                }
            }
            return result
        } catch (error: Exception) {
            result.close()
            throw error
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = resolve(parentDocumentId)
        val child = resolve(documentId)
        return parent.root == child.root &&
            (child.file == parent.file || child.file.path.startsWith(parent.file.path + File.separator))
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val writable = mode != "r"
        val document = resolve(documentId, writable)
        signal?.throwIfCanceled()
        if (!document.file.isFile || writable && !writableFile(document.file)) throw FileNotFoundException()
        val requested = ParcelFileDescriptor.parseMode(mode)
        val access = when (requested and ParcelFileDescriptor.MODE_READ_WRITE) {
            ParcelFileDescriptor.MODE_READ_ONLY -> OsConstants.O_RDONLY
            ParcelFileDescriptor.MODE_WRITE_ONLY -> OsConstants.O_WRONLY
            else -> OsConstants.O_RDWR
        }
        val flags = access or OsConstants.O_NOFOLLOW or OsConstants.O_NONBLOCK or
            if (requested and ParcelFileDescriptor.MODE_APPEND != 0) OsConstants.O_APPEND else 0
        val descriptor = open(document, flags)
        try {
            if (!OsConstants.S_ISREG(Os.fstat(descriptor.fileDescriptor).st_mode)) throw FileNotFoundException()
            signal?.throwIfCanceled()
            // Validate the opened inode before a truncate can modify anything.
            if (requested and ParcelFileDescriptor.MODE_TRUNCATE != 0) Os.ftruncate(descriptor.fileDescriptor, 0)
            return descriptor
        } catch (error: android.system.ErrnoException) {
            descriptor.close()
            throw IOException(error)
        } catch (error: Exception) {
            descriptor.close()
            throw error
        }
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = resolve(parentDocumentId, write = true)
        if (!LegacyDocumentPaths.validSegment(displayName)) throw FileNotFoundException()
        val destination = LegacyDocumentPaths.document(parent.root, File(parent.file, displayName))
        if (mimeType != Document.MIME_TYPE_DIR && !writableFile(File(displayName))) throw FileNotFoundException()
        directory(parent).use { descriptor ->
            val entry = File("/proc/self/fd/${descriptor.fd}", displayName)
            val created = if (mimeType == Document.MIME_TYPE_DIR) entry.mkdir() else entry.createNewFile()
            if (!created) throw FileNotFoundException(requireNotNull(context).getString(R.string.explorer_exists))
        }
        return destination.id
    }

    override fun deleteDocument(documentId: String) {
        val document = resolve(documentId, write = true)
        if (document.file == document.root.directory ||
            !document.file.isDirectory && !writableFile(document.file)) throw FileNotFoundException()
        val parent = LegacyDocumentPaths.document(document.root, requireNotNull(document.file.parentFile))
        directory(parent).use { descriptor ->
            if (!File("/proc/self/fd/${descriptor.fd}", document.file.name).delete())
                throw FileNotFoundException(requireNotNull(context).getString(R.string.explorer_unavailable))
        }
    }

    private fun directory(document: LegacyDocumentPaths.Document): ParcelFileDescriptor {
        val descriptor = open(document, OsConstants.O_RDONLY or OsConstants.O_NONBLOCK or
            OsConstants.O_NOFOLLOW)
        try {
            if (!OsConstants.S_ISDIR(Os.fstat(descriptor.fileDescriptor).st_mode)) throw FileNotFoundException()
            return descriptor
        } catch (error: android.system.ErrnoException) {
            descriptor.close()
            throw IOException(error)
        } catch (error: Exception) {
            descriptor.close()
            throw error
        }
    }

    private fun open(document: LegacyDocumentPaths.Document, flags: Int): ParcelFileDescriptor {
        val fileDescriptor = try { Os.open(document.file.path, flags, 0) }
        catch (error: android.system.ErrnoException) { throw IOException(error) }
        val descriptor = try {
            try { ParcelFileDescriptor.dup(fileDescriptor) } finally { Os.close(fileDescriptor) }
        } catch (error: android.system.ErrnoException) { throw IOException(error) }
        try {
            val actual = File(Os.readlink("/proc/self/fd/${descriptor.fd}"))
            if (actual != document.file || LegacyDocumentPaths.document(document.root, actual).file != actual)
                throw FileNotFoundException()
            return descriptor
        } catch (error: android.system.ErrnoException) {
            descriptor.close()
            throw IOException(error)
        } catch (error: Exception) {
            descriptor.close()
            throw error
        }
    }

    private fun add(document: LegacyDocumentPaths.Document, cursor: MatrixCursor, writePermission: Boolean) {
        val file = document.file
        val folder = file.isDirectory
        val write = writePermission && file.canWrite()
        val flags = if (!write) 0 else if (folder) Document.FLAG_DIR_SUPPORTS_CREATE
            else if (writableFile(file)) Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE else 0
        cursor.addRow(cursor.columnNames.map<String, Any?> { column -> when (column) {
            Document.COLUMN_DOCUMENT_ID -> document.id
            Document.COLUMN_DISPLAY_NAME -> if (file == document.root.directory)
                LegacyStorage.label(requireNotNull(context), document.root) else file.name
            Document.COLUMN_MIME_TYPE -> LegacyStorage.mime(file)
            Document.COLUMN_FLAGS -> flags
            Document.COLUMN_SIZE -> if (folder) null else file.length()
            Document.COLUMN_LAST_MODIFIED -> file.lastModified()
            else -> null
        } }.toTypedArray())
    }

    private fun writableFile(file: File) = file.extension.lowercase(Locale.ROOT) in WRITE_EXTENSIONS

    private companion object {
        const val TAG = "LocalDocuments"
        val WRITE_EXTENSIONS = setOf("json", "zip")
        val COLUMNS = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
    }
}
