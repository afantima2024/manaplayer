package com.macarmusic.app.data

import com.macarmusic.app.model.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    @get:Rule val temp = TemporaryFolder()
    private val imageName = "00000000-0000-0000-0000-000000000001.png"
    private val settings = BackupSettings(DisplayOptions(), PlaybackOptions(), SortOrder.ALBUM, 2, "zh-CN",
        ColorSlots().define(0, 0x123abc))
    private val source = FolderSource("content://disk/tree/music", "Music", true, pinned = true)
    private val song = Track("song", "content://disk/song", "song.mp3", "Song", "Artist", "Album",
        durationMs = 60_000, sourceUris = mapOf(source.uri to "content://disk/song"), sourcePaths = mapOf(source.uri to "Album/song.mp3"))
    private val library = Library(sources = listOf(source), tracks = listOf(song), favorites = setOf(song.id),
        playlists = listOf(LocalPlaylist("p", "Mix", listOf(song.id), listOf(PlaylistFolder(source.uri, "album", "Album", listOf(song.id), "Album")), emptyList())))
    private fun backup() = BackupManifest(origin = "00000000-0000-0000-0000-000000000099", createdAtMs = 1000,
        settings = settings, library = library, positions = listOf(CollectionPosition(PlaybackCollection.playlist("p"), song.id, 8000, 999)))

    private fun raw(backup: BackupManifest, entries: Map<String, ByteArray> = emptyMap(), text: String? = null): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write((text ?: Json.encodeToString(backup)).toByteArray())
            zip.closeEntry()
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(content); zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun read(bytes: ByteArray): BackupManifest =
        BackupArchive.read(ByteArrayInputStream(bytes), temp.newFolder())

    @Test fun roundTripKeepsSettingsFavoritesPlaylistsPinsAndIndependentPositions() {
        val value = backup().copy(settings = settings.copy(display = DisplayOptions(
            blockOutline = BlockOutline(BlockStyle.NONE, "+", 36, true),
            spectrum = SpectrumOptions(refreshRate = 60), navigationPosition = NavigationPosition.LEFT,
            lyricAppearance = LyricAppearance(30, 16, 0x123456, 0xabcdef), lightMode = true, widgetTransparency = 85,
            internalLyricExpiry = LyricCacheExpiry.NEVER, portableLyricExpiry = LyricCacheExpiry.SEVEN_DAYS)))
        val output = ByteArrayOutputStream()
        BackupArchive.write(output, value, emptyMap())
        assertEquals(value, read(output.toByteArray()))
    }

    @Test fun olderBackupsKeepOmittedPlaybackAndTimeDefaults() {
        val restored = read(raw(backup()))
        assertEquals(LoopMode.OFF, restored.settings.playback.loop)
        assertEquals(PlayOrder.SEQUENTIAL, restored.settings.playback.order)
        assertEquals(PlaybackTimeMode.ELAPSED, restored.settings.display.timeMode)
        assertEquals(PlaybackTimeMode.ELAPSED, restored.settings.display.mainTimeMode)
        assertEquals(50, restored.settings.display.widgetTransparency)
        assertEquals(LyricCacheExpiry.THIRTY_DAYS, restored.settings.display.internalLyricExpiry)
        assertEquals(LyricCacheExpiry.NEVER, restored.settings.display.portableLyricExpiry)
    }

    @Test fun imageDataIsStoredOnlyUnderValidatedPrivateNames() {
        val value = backup().copy(settings = settings.copy(display = DisplayOptions(imageVersion = 1, imageIds = listOf(imageName))))
        val file = temp.newFile("input.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val output = ByteArrayOutputStream()
        BackupArchive.write(output, value, mapOf(imageName to file))
        val directory = temp.newFolder()
        assertEquals(value, BackupArchive.read(ByteArrayInputStream(output.toByteArray()), directory))
        assertArrayEquals(file.readBytes(), File(directory, imageName).readBytes())
    }

    @Test(expected = IllegalArgumentException::class) fun unknownVersionIsRejectedBeforeExtraction() {
        read(raw(backup().copy(version = 2)))
    }

    @Test(expected = IllegalArgumentException::class) fun unrelatedZipIsRejected() {
        read(raw(backup().copy(format = "Other product")))
    }

    @Test fun traversalEntryCannotWriteOutsideTheStagingDirectory() {
        val outside = File(temp.root, "outside")
        try {
            read(raw(backup(), mapOf("../outside" to byteArrayOf(1))))
            fail("Traversal must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertFalse(outside.exists())
    }

    @Test(expected = IllegalArgumentException::class) fun unexpectedFilesAreRejected() {
        read(raw(backup(), mapOf("music.mp3" to byteArrayOf(1))))
    }

    @Test(expected = IllegalArgumentException::class) fun missingImagesAreRejected() {
        val value = backup().copy(settings = settings.copy(display = DisplayOptions(imageVersion = 1, imageIds = listOf(imageName))))
        read(raw(value))
    }

    @Test(expected = IOException::class) fun oversizedImageIsBoundedWhileReading() {
        val value = backup().copy(settings = settings.copy(display = DisplayOptions(imageVersion = 1, imageIds = listOf(imageName))))
        read(raw(value, mapOf("images/$imageName" to ByteArray(BackupArchive.MAX_IMAGE_BYTES + 1))))
    }

    @Test(expected = IOException::class) fun highlyCompressedMetadataStillHasADecompressedLimit() {
        read(raw(backup(), text = " ".repeat(BackupArchive.MAX_METADATA_BYTES + 1)))
    }

    @Test(expected = SerializationException::class) fun malformedJsonIsNotAcceptedAsAnEmptyBackup() {
        read(raw(backup(), text = "{broken"))
    }

    @Test(expected = IllegalArgumentException::class) fun remoteUrisCannotBeImported() {
        read(raw(backup().copy(library = library.copy(tracks = listOf(song.copy(uri = "https://example.invalid/song.mp3"))))))
    }

    @Test(expected = IllegalArgumentException::class) fun danglingPlaylistEntriesAreRejected() {
        val broken = library.copy(playlists = listOf(LocalPlaylist("p", "Mix", listOf("missing"))))
        read(raw(backup().copy(library = broken)))
    }

    @Test(expected = IllegalArgumentException::class) fun duplicateTrackIdsAreRejected() {
        read(raw(backup().copy(library = library.copy(tracks = listOf(song, song)))))
    }

    @Test(expected = IllegalArgumentException::class) fun unknownSettingsEnumsAreRejected() {
        read(raw(backup(), text = Json.encodeToString(backup()).replace("\"ALBUM\"", "\"FUTURE_SORT\"")))
    }

    @Test fun cancelledCopyDoesNotReturnAValidBackup() {
        try {
            BackupArchive.read(ByteArrayInputStream(raw(backup())), temp.newFolder()) { throw IOException("cancelled") }
            fail("Cancellation must abort")
        } catch (error: IOException) { assertEquals("cancelled", error.message) }
    }
}
