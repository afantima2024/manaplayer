package com.macarmusic.app.model

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LegacyDocumentPathsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun documentIdsSurviveRootReorderingAndKeepUnicodeNames() {
        val first = LegacyDocumentPaths.Root(temp.newFolder("volume-a").canonicalFile)
        val second = LegacyDocumentPaths.Root(temp.newFolder("volume-b").canonicalFile)
        val file = File(first.directory, "Music${File.separator}消防 - Copy (2).mp3")
        val document = LegacyDocumentPaths.document(first, file)
        assertEquals(first.key + ":Music/消防 - Copy (2).mp3", document.id)
        assertEquals(document, LegacyDocumentPaths.resolve(listOf(second, first), document.id))
        assertEquals(first.directory, LegacyDocumentPaths.resolve(listOf(first), first.id).file)
        assertNotEquals(first.key, second.key)
        assertEquals(first.key, LegacyDocumentPaths.Root(first.directory).key)
    }

    @Test fun malformedTraversalAndUnavailableVolumeIdsAreRejected() {
        val root = LegacyDocumentPaths.Root(temp.newFolder("storage").canonicalFile)
        for (relative in listOf("../outside.mp3", "Music/../../outside.mp3", "/absolute.mp3",
            "Music\\file.mp3", "Music//file.mp3", "Music/./file.mp3", "Music/../file.mp3",
            "Music/", "Music/\u0000file", "Music/\nfile", "a".repeat(256))) {
            rejected { LegacyDocumentPaths.resolve(listOf(root), root.key + ":" + relative) }
        }
        for (id in listOf("", "primary:", "0".repeat(32) + ":Music/song.mp3"))
            rejected { LegacyDocumentPaths.resolve(listOf(root), id) }
        rejected { LegacyDocumentPaths.resolve(emptyList(), root.id) }
        rejected { LegacyDocumentPaths.document(root, File(root.directory.parentFile, "storage-2/song.mp3")) }
    }

    @Test fun otherAppsPrivateAreasAreExcludedButSharedMediaRemainsAvailable() {
        val root = LegacyDocumentPaths.Root(temp.newFolder("storage").canonicalFile)
        for (relative in listOf("Android/data", "Android/data/app/files/music.mp3", "Android/obb",
            "Android/sandbox/app", "ANDROID/DATA/app")) {
            rejected { LegacyDocumentPaths.resolve(listOf(root), root.key + ":" + relative) }
        }
        for (relative in listOf("Android", "Android/media/app/song.mp3", "Music/song.mp3")) {
            assertTrue(LegacyDocumentPaths.resolve(listOf(root), root.key + ":" + relative).id.endsWith(relative))
        }
    }

    private fun rejected(action: () -> Unit) {
        try { action(); fail("Unsafe document ID was accepted") }
        catch (_: IllegalArgumentException) { }
    }
}
