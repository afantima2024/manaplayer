package com.macarmusic.app.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.core.content.PermissionChecker
import com.macarmusic.app.R
import com.macarmusic.app.model.LegacyDocumentPaths
import java.io.File
import java.util.Locale

internal object LegacyStorage {
    private var cachedAt = 0L
    private var cachedRoots = emptyList<LegacyDocumentPaths.Root>()

    fun available(sdk: Int = Build.VERSION.SDK_INT) = sdk in 23..28
    fun authority(context: Context) = "${context.packageName}.localdocuments"
    fun owns(context: Context, uri: Uri) = uri.scheme == "content" && uri.authority == authority(context)

    fun permissions(write: Boolean, sdk: Int = Build.VERSION.SDK_INT): List<String> =
        if (!available(sdk)) emptyList() else listOf(Manifest.permission.READ_EXTERNAL_STORAGE) +
            if (write) listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE) else emptyList()

    fun permitted(context: Context, write: Boolean = false): Boolean = available() &&
        permissions(write).all { PermissionChecker.checkSelfPermission(context, it) == PermissionChecker.PERMISSION_GRANTED }

    fun requirePermission(context: Context, write: Boolean = false) {
        if (!permitted(context, write)) throw SecurityException(context.getString(
            if (available()) R.string.explorer_permission_denied else R.string.explorer_legacy_unavailable))
    }

    fun grantFlags(context: Context): Int = if (!permitted(context)) 0 else
        Intent.FLAG_GRANT_READ_URI_PERMISSION or
            if (permitted(context, write = true)) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0

    @Synchronized
    @Suppress("DEPRECATION")
    fun roots(context: Context, refresh: Boolean = false): List<LegacyDocumentPaths.Root> {
        requirePermission(context)
        val now = SystemClock.elapsedRealtime()
        if (!refresh && cachedRoots.isNotEmpty() && now - cachedAt < 2_000) return cachedRoots
        val primary = Environment.getExternalStorageDirectory().canonicalFile
        val candidates = linkedSetOf(primary)
        // Legacy Android has no public StorageVolume.directory API. Only inspect shared-storage mount roots.
        context.getExternalFilesDirs(null).filterNotNull().forEach { files ->
            val android = generateSequence(files) { it.parentFile }
                .firstOrNull { it.name == "Android" && File(it, "data").isDirectory }
            android?.parentFile?.canonicalFile?.let(candidates::add)
        }
        File("/storage").listFiles()?.take(128)?.filter {
            it.name !in setOf("emulated", "self") && !it.name.startsWith('.')
        }?.forEach { candidates.add(it.canonicalFile) }
        cachedRoots = candidates.filter {
            it.parentFile != null && it.path !in setOf("/", "/data", "/storage", "/mnt") &&
                it.isDirectory && it.canRead()
        }.distinct().map(LegacyDocumentPaths::Root)
        cachedAt = now
        return cachedRoots
    }

    @Suppress("DEPRECATION")
    fun label(context: Context, root: LegacyDocumentPaths.Root): String =
        if (root.directory == Environment.getExternalStorageDirectory().canonicalFile)
            context.getString(R.string.device_shared_storage)
        else context.getString(R.string.device_storage_volume, root.directory.name)

    fun tree(context: Context, root: LegacyDocumentPaths.Root): Uri =
        DocumentsContract.buildTreeDocumentUri(authority(context), root.id)

    fun mime(file: File): String {
        if (file.isDirectory) return DocumentsContract.Document.MIME_TYPE_DIR
        val extension = file.extension.lowercase(Locale.ROOT)
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: when (extension) {
                "flac" -> "audio/flac"
                "opus" -> "audio/opus"
                "lrc" -> "text/plain"
                else -> "application/octet-stream"
            }
    }
}
