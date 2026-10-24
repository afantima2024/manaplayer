package com.macarmusic.app.model

import java.io.File
import java.security.MessageDigest

internal object LegacyDocumentPaths {
    data class Root(val directory: File) {
        init {
            require(directory.isAbsolute && directory == directory.canonicalFile && directory.parentFile != null)
        }
        val key: String = MessageDigest.getInstance("SHA-256").digest(directory.path.toByteArray(Charsets.UTF_8))
            .take(16).joinToString("") { "%02x".format(it) }
        val id get() = "$key:"
    }

    data class Document(val root: Root, val file: File) {
        val id: String get() = root.key + ":" +
            file.relativeTo(root.directory).path.replace(File.separatorChar, '/')
    }

    fun resolve(roots: List<Root>, id: String): Document {
        val separator = id.indexOf(':')
        require(separator == 32)
        val root = roots.singleOrNull { it.key == id.substring(0, separator) }
            ?: throw IllegalArgumentException("Unavailable storage root")
        val relative = id.substring(separator + 1)
        require(relative.isEmpty() || relative.split('/').all(::validSegment))
        return document(root, File(root.directory, relative.replace('/', File.separatorChar)))
    }

    fun document(root: Root, file: File): Document {
        val absolute = file.absoluteFile
        val canonical = file.canonicalFile
        require(absolute == canonical) { "Symbolic links are not exposed by the local picker" }
        require(canonical == root.directory || canonical.path.startsWith(root.directory.path + File.separator))
        val parts = canonical.relativeTo(root.directory).path.split(File.separatorChar)
        require(parts.size < 2 || !parts[0].equals("Android", true) ||
            parts[1].lowercase() !in setOf("data", "obb", "sandbox")) { "App-private storage is not exposed" }
        return Document(root, canonical)
    }

    fun validSegment(value: String): Boolean = value.isNotEmpty() && value != "." && value != ".." &&
        value.none { it == '/' || it == '\\' || it.isISOControl() } &&
        value.toByteArray(Charsets.UTF_8).size <= 255
}
