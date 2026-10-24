package com.macarmusic.app.model

import org.junit.Assert.*
import org.junit.Test

class ExplorerModeTest {
    @Test fun everyModeAllowsNavigationButOnlyTheAppropriateFiles() {
        for (mode in ExplorerMode.entries) {
            assertTrue(mode.accepts("Folder", "", directory = true))
            assertFalse(mode.accepts("notes.txt", "text/plain", directory = false))
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

    @Test fun onlyCacheAndBackupDestinationsRequestWriteAccess() {
        assertEquals(setOf(ExplorerMode.LYRIC_CACHE, ExplorerMode.BACKUP_EXPORT),
            ExplorerMode.entries.filter { it.writable }.toSet())
        assertEquals(setOf(ExplorerMode.MUSIC_FOLDER, ExplorerMode.MUSIC_FILE),
            ExplorerMode.entries.filter { it.music }.toSet())
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
