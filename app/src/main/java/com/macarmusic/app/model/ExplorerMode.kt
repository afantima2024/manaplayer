package com.macarmusic.app.model

import com.macarmusic.app.lyrics.LyricFiles
import java.util.Locale

enum class ExplorerMode(val folder: Boolean = false, val writable: Boolean = false) {
    MUSIC_FOLDER(folder = true),
    MUSIC_FILE,
    IMAGES,
    BACKUP_IMPORT,
    BACKUP_EXPORT(folder = true, writable = true),
    LYRIC_CACHE(folder = true, writable = true);

    val music get() = this == MUSIC_FOLDER || this == MUSIC_FILE

    fun accepts(name: String, mime: String, directory: Boolean): Boolean {
        if (directory) return true
        if (folder) return false
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (this) {
            MUSIC_FILE -> !LyricFiles.accepts(name) && AudioFiles.accepts(name, mime)
            IMAGES -> mime.startsWith("image/", ignoreCase = true) || extension in IMAGE_EXTENSIONS
            BACKUP_IMPORT -> extension == "zip" || mime in ZIP_TYPES
            else -> false
        }
    }

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "avif")
        private val ZIP_TYPES = setOf("application/zip", "application/x-zip-compressed")
        fun backupName(value: String): String {
            val name = value.trim().let { if (it.endsWith(".zip", true)) it else "$it.zip" }
            require(value.isNotBlank() && value.trim() !in setOf(".", ".."))
            require(name.none { it == '/' || it == '\\' || it.isISOControl() })
            require(name.toByteArray(Charsets.UTF_8).size <= 255)
            return name
        }
    }
}
