package com.macarmusic.app.model

import org.junit.Assert.*
import org.junit.Test

class ExplorerModeTest {
    @Test fun everyModeAllowsNavigationButOnlyTheAppropriateFiles() {
        for (mode in ExplorerMode.entries) {
            assertTrue(mode.accepts("Folder", "", directory = true))
            assertEquals(mode == ExplorerMode.RADIO_IMPORT, mode.accepts("notes.txt", "text/plain", directory = false))
        }
        for (extension in listOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus")) {
            assertTrue(ExplorerMode.MUSIC_FILE.accepts("消防.$extension", "application/octet-stream", false))
        }
        assertTrue(ExplorerMode.MUSIC_FILE.accepts("no extension", "audio/mpeg", false))
        assertFalse(ExplorerMode.MUSIC_FILE.accepts("song.LRC", "audio/mpeg", false))
        assertFalse(ExplorerMode.MUSIC_FILE.accepts("song.txt", "audio/wav", false))
        assertTrue(ExplorerMode.IMAGES.accepts("background.PNG", "application/octet-stream", false))
        assertTrue(ExplorerMode.IMAGES.accepts("no extension", "image/jpeg", false))
        assertFalse(ExplorerMode.IMAGES.accepts("music.mp3", "audio/mpeg", false))
        assertTrue(ExplorerMode.BACKUP_IMPORT.accepts("Backup.ZIP", "application/octet-stream", false))
        assertTrue(ExplorerMode.BACKUP_IMPORT.accepts("backup", "application/zip", false))
        assertFalse(ExplorerMode.MUSIC_FOLDER.accepts("song.mp3", "audio/mpeg", false))
        assertFalse(ExplorerMode.LYRIC_CACHE.accepts("record.json", "application/json", false))
    }

    @Test fun radioImportUsesTheSameBrowsableLocationsAndOnlyReadsLists() {
        val mode = ExplorerMode.RADIO_IMPORT
        assertFalse(mode.folder)
        assertFalse(mode.music)
        assertFalse(mode.writable)
        for (name in listOf("Stations.txt", "电台.M3U", "Radio.m3u8")) assertTrue(mode.accepts(name, "application/octet-stream", false))
        for (mime in listOf("text/plain", "audio/x-mpegurl", "application/vnd.apple.mpegurl"))
            assertTrue(mode.accepts("Stations", mime, false))
        for (name in listOf("music.mp3", "photo.png", "backup.zip")) assertFalse(mode.accepts(name, "application/octet-stream", false))
    }

    @Test fun onlyCacheAndExportDestinationsRequestWriteAccess() {
        assertEquals(setOf(ExplorerMode.LYRIC_CACHE, ExplorerMode.BACKUP_EXPORT, ExplorerMode.RADIO_EXPORT),
            ExplorerMode.entries.filter { it.writable }.toSet())
        assertEquals(setOf(ExplorerMode.MUSIC_FOLDER, ExplorerMode.MUSIC_FILE),
            ExplorerMode.entries.filter { it.music }.toSet())
    }

    @Test fun radioExportUsesANewM3uFileInAWritableFolder() {
        val mode = ExplorerMode.RADIO_EXPORT
        assertTrue(mode.folder && mode.writable && mode.export)
        assertEquals("audio/x-mpegurl", mode.exportMime)
        assertFalse(mode.accepts("Existing.m3u", "audio/x-mpegurl", false))
        assertEquals("Stations.m3u", mode.exportName(" Stations "))
        assertEquals("电台.M3U", mode.exportName("电台.M3U"))
        assertEquals("a".repeat(251) + ".m3u", mode.exportName("a".repeat(251)))
        for (name in listOf("", " ", ".", "..", "../stations", "C:\\stations", "nested/list.m3u",
            "bad\u0000name", "bad\nname", "a".repeat(252), "中".repeat(84))) {
            try { mode.exportName(name); fail("Invalid radio export filename accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun backupNamesAreSingleFilesAndNeverEscapeTheChosenDirectory() {
        assertEquals("Backup.zip", ExplorerMode.backupName(" Backup "))
        assertEquals("备份.ZIP", ExplorerMode.backupName("备份.ZIP"))
        assertEquals("a".repeat(251) + ".zip", ExplorerMode.backupName("a".repeat(251)))
        for (name in listOf("", " ", ".", "..", "../backup", "/backup.zip", "C:\\backup.zip",
            "child/backup.zip", "bad\u0000name", "bad\nname", "a".repeat(252), "中".repeat(84))) {
            try { ExplorerMode.backupName(name); fail("Accepted invalid filename: $name") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
