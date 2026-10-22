package com.macarmusic.app.data

import com.macarmusic.app.model.BackgroundImages
import com.macarmusic.app.model.ColorSlots
import com.macarmusic.app.model.CollectionKind
import com.macarmusic.app.model.CollectionPosition
import com.macarmusic.app.model.Library
import com.macarmusic.app.model.PlaybackCollection
import com.macarmusic.app.model.PlaybackOptions
import com.macarmusic.app.model.SortOrder
import com.macarmusic.app.model.SpectrumColors
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupSettings(
    val display: DisplayOptions,
    val playback: PlaybackOptions,
    val sort: SortOrder,
    val nightMode: Int,
    val language: String,
    val colors: ColorSlots,
)

@Serializable
data class BackupManifest(
    val format: String = "ManaMusicPlayer",
    val version: Int = 1,
    val origin: String,
    val createdAtMs: Long,
    val settings: BackupSettings,
    val library: Library,
    val positions: List<CollectionPosition> = emptyList(),
)

object BackupArchive {
    private val json = Json { encodeDefaults = true }
    const val MAX_METADATA_BYTES = 16 * 1024 * 1024
    const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    private const val MANIFEST = "manifest.json"

    fun validate(backup: BackupManifest) {
        require(backup.format == "ManaMusicPlayer" && backup.version == 1) { "Unsupported backup format or version" }
        require(UUID.fromString(backup.origin).toString() == backup.origin && backup.createdAtMs >= 0)
        require(backup.settings.nightMode in setOf(-1, 1, 2))
        require(backup.settings.language in setOf("", "en", "zh", "zh-CN"))
        require(backup.settings.colors.colors.size <= 1024) { "Backup color palette is too large" }
        val display = backup.settings.display
        require(display.blur in 0..100 && display.tintStrength in 0..100 && display.imageVersion >= 0)
        require(display.imageIds.size <= BackgroundImages.MAX_COUNT && display.imageIds.distinct() == display.imageIds &&
            display.imageIds.all(BackgroundImages::isImportedName))
        require((display.imageVersion == 0L) == display.imageIds.isEmpty())
        val library = LibraryCodec.validateLibrary(backup.library)
        require(library.tracks.size <= 100_000 && library.sources.size <= 10_000 && library.playlists.size <= 10_000)
        val sources = library.sources.mapTo(hashSetOf()) { it.uri }
        val ids = library.tracks.mapTo(hashSetOf()) { it.id }
        require(ids.containsAll(library.favorites))
        fun local(value: String) {
            require(value.length <= 8192)
            val uri = URI.create(value)
            require(uri.scheme == "content" && !uri.authority.isNullOrBlank()) { "Backup contains a non-local music reference" }
        }
        library.sources.forEach { source ->
            local(source.uri)
            require(source.name.length <= 8192 && source.lastScanMs >= 0 && source.lastScanCount >= 0)
            require(source.restoredFrom.size <= 1024)
            source.restoredFrom.forEach(::local)
        }
        library.tracks.forEach { track ->
            require(track.id.isNotBlank() && track.id.length <= 8192 && track.durationMs >= 0)
            local(track.uri)
            track.standaloneUri?.let(::local)
            require(sources.containsAll(track.sourceUris.keys))
            require(track.sourceUris.keys.containsAll(track.sourceParents.keys) &&
                track.sourceUris.keys.containsAll(track.sourcePaths.keys))
            track.sourceUris.values.forEach(::local)
            require(track.sourcePaths.values.all(PlaybackCollection::validPath))
            require(listOf(track.fileName, track.title, track.artist, track.album, track.genre, track.trackNumber, track.year)
                .all { it.length <= 8192 && '\u0000' !in it })
            require(track.restoredIds.size <= 1024 && track.restoredIds.all { it.length <= 8192 })
        }
        library.playlists.forEach { playlist ->
            require(playlist.id.isNotBlank() && playlist.id.length <= 8192 && playlist.name.isNotBlank() && playlist.name.length <= 80)
            require(ids.containsAll(playlist.trackIds) && playlist.trackIds.distinct() == playlist.trackIds)
            require(playlist.manualTrackIds == null || playlist.trackIds.containsAll(playlist.manualTrackIds))
            require(playlist.folders.distinctBy { it.key }.size == playlist.folders.size)
            playlist.folders.forEach {
                require(it.sourceUri in sources && playlist.trackIds.containsAll(it.trackIds))
                require(it.documentId.length <= 8192 && '\u0000' !in it.documentId && PlaybackCollection.validPath(it.relativePath))
            }
        }
        require(backup.positions.size <= 100_000 && backup.positions.distinctBy { it.collection }.size == backup.positions.size)
        val playlists = library.playlists.mapTo(hashSetOf()) { it.id }
        backup.positions.forEach {
            require(it.trackId in ids)
            require(if (it.collection.kind == CollectionKind.PLAYLIST) it.collection.id in playlists else it.collection.id in sources)
        }
    }

    fun write(output: OutputStream, backup: BackupManifest, images: Map<String, File>, checkCancelled: () -> Unit = {}) {
        validate(backup)
        require(images.keys == backup.settings.display.imageIds.toSet())
        val manifest = json.encodeToString(backup).toByteArray(Charsets.UTF_8)
        require(manifest.size <= MAX_METADATA_BYTES) { "Backup metadata exceeds 16 MiB" }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest)
            zip.closeEntry()
            zip.setLevel(0)
            images.forEach { (name, file) ->
                require(BackgroundImages.isImportedName(name))
                zip.putNextEntry(ZipEntry("images/$name"))
                file.inputStream().use { copyBounded(it, zip, MAX_IMAGE_BYTES, checkCancelled) }
                zip.closeEntry()
            }
        }
    }

    fun read(input: InputStream, directory: File, checkCancelled: () -> Unit = {}): BackupManifest {
        require(directory.isDirectory && directory.listFiles()?.isEmpty() == true)
        return ZipInputStream(input).use { zip ->
            require(zip.nextEntry?.name == MANIFEST) { "Not a ManaMusicPlayer backup" }
            val bytes = BoundedInput.read(zip, MAX_METADATA_BYTES, checkCancelled)
            val backup = decodeManifest(bytes.toString(Charsets.UTF_8))
            validate(backup)
            val remaining = backup.settings.display.imageIds.toMutableSet()
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.removePrefix("images/")
                require(entry.name == "images/$name" && !entry.isDirectory &&
                    BackgroundImages.isImportedName(name) && remaining.remove(name)) { "Unexpected or duplicate backup entry" }
                File(directory, name).outputStream().use { output ->
                    copyBounded(zip, output, MAX_IMAGE_BYTES, checkCancelled)
                    output.fd.sync()
                }
            }
            require(remaining.isEmpty()) { "Backup background images are missing" }
            backup
        }
    }

    private fun decodeManifest(text: String): BackupManifest {
        val root = json.parseToJsonElement(text).jsonObject
        val settings = root["settings"] as? JsonObject ?: return json.decodeFromJsonElement(root)
        val display = settings["display"] as? JsonObject ?: return json.decodeFromJsonElement(root)
        val migrated = display.toMutableMap()
        // Accept the two retired version-1 fields without allowing arbitrary unknown settings.
        migrated.remove("lightMode")?.let {
            require(it is JsonPrimitive && !it.isString && it.booleanOrNull != null) { "Invalid legacy Light mode value" }
        }
        val spectrum = display["spectrum"] as? JsonObject
        if (spectrum != null && "classicColors" in spectrum) {
            json.decodeFromJsonElement<SpectrumColors>(spectrum.getValue("classicColors"))
            migrated["spectrum"] = JsonObject(spectrum - "classicColors")
        }
        return json.decodeFromJsonElement(JsonObject(root +
            ("settings" to JsonObject(settings + ("display" to JsonObject(migrated))))))
    }

    private fun copyBounded(input: InputStream, output: OutputStream, limit: Int, checkCancelled: () -> Unit) {
        val buffer = ByteArray(16_384)
        var total = 0L
        while (true) {
            checkCancelled()
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw IOException("Backup entry exceeds the safe size limit")
            output.write(buffer, 0, count)
        }
    }
}
