package com.macarmusic.app.model

import com.macarmusic.app.lyrics.LyricFiles
import java.util.Locale

enum class ExplorerMode(val folder: Boolean = false, val writable: Boolean = false, val exportMime: String? = null) {
    MUSIC_FOLDER(folder = true),
    MUSIC_FILE,
    IMAGES,
    BACKUP_IMPORT,
    RADIO_IMPORT,
    BACKUP_EXPORT(folder = true, writable = true, exportMime = "application/zip"),
    RADIO_EXPORT(folder = true, writable = true, exportMime = "audio/x-mpegurl"),
    LYRIC_CACHE(folder = true, writable = true);

    val music get() = this == MUSIC_FOLDER || this == MUSIC_FILE
    val export get() = exportMime != null

    fun exportName(value: String): String = when (this) {
        BACKUP_EXPORT -> backupName(value)
        RADIO_EXPORT -> radioName(value)
        else -> throw IllegalArgumentException("Not a file-export mode")
    }

    fun accepts(name: String, mime: String, directory: Boolean): Boolean {
        if (directory) return true
        if (folder) return false
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (this) {
            MUSIC_FILE -> !LyricFiles.accepts(name) && AudioFiles.accepts(name, mime)
            IMAGES -> mime.startsWith("image/", ignoreCase = true) || extension in IMAGE_EXTENSIONS
            BACKUP_IMPORT -> extension == "zip" || mime in ZIP_TYPES
            RADIO_IMPORT -> extension in setOf("txt", "m3u", "m3u8") ||
                mime.lowercase(Locale.ROOT) in setOf("text/plain", "audio/x-mpegurl", "audio/mpegurl",
                    "application/x-mpegurl", "application/vnd.apple.mpegurl")
            else -> false
        }
    }

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "avif")
        private val ZIP_TYPES = setOf("application/zip", "application/x-zip-compressed")
        fun backupName(value: String): String = filename(value, "zip")
        fun radioName(value: String): String = filename(value, "m3u")
        private fun filename(value: String, extension: String): String {
            val name = value.trim().let { if (it.endsWith(".$extension", true)) it else "$it.$extension" }
            require(value.isNotBlank() && value.trim() !in setOf(".", ".."))
            require(name.none { it == '/' || it == '\\' || it.isISOControl() })
            require(name.toByteArray(Charsets.UTF_8).size <= 255)
            return name
        }
    }
}
