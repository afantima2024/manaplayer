package com.macarmusic.app.model

import org.junit.Assert.*
import org.junit.Test

class IndexedScanTest {
    private val entry = DocumentEntry("content://media/external/audio/media/1", "content://media/external/audio/media/1",
        "Song.MP3", "audio/mpeg", false, false, 1234, 10000)

    @Test fun indexedTagsCleanUnknownsAndDecodeDiscTrackNumbers() {
        val track = IndexedTags("<unknown>", " <UNKNOWN> ", " Album ", 60000, 2012, "2020", "Rock").toTrack(entry)
        assertEquals("Song", track.title)
        assertEquals("", track.artist)
        assertEquals("Album", track.album)
        assertEquals("12", track.trackNumber)
        assertEquals("2020", track.year)
        assertEquals("Rock", track.genre)
        assertEquals(60000, track.durationMs)
        assertTrue(track.metadataFromIndex)
        assertEquals(FileStamp(1234, 10000), track.metadataStamp)
    }

    @Test fun incompleteIndexDoesNotInventDurationYearOrTrack() {
        val track = IndexedTags(durationMs = -1, track = 2000, year = "0").toTrack(entry)
        assertEquals(0, track.durationMs)
        assertEquals("", track.trackNumber)
        assertEquals("", track.year)
    }

    @Test fun batchPreservesReferencesAndMarksOnlyUndiscoveredTracksMissing() {
        val song = IndexedTags(title = "One").toTrack(entry).copy(sourceUris = mapOf("a" to entry.uri))
        val absent = song.copy(id = "missing", sourceUris = mapOf("b" to "missing"))
        val library = Library(sources = listOf(FolderSource("a", "A", true), FolderSource("b", "B", true, pinned = true)),
            tracks = listOf(song, absent), favorites = setOf(absent.id),
            playlists = listOf(LocalPlaylist("p", "P", listOf(song.id, absent.id))))
        val fromB = song.copy(sourceUris = mapOf("b" to song.uri))
        val result = LibraryEdits.applyScans(library, listOf(LibraryScan("a", emptyList()), LibraryScan("b", listOf(fromB))), 20) { "$it missing" }
        assertFalse(result.tracks.first().missing)
        assertEquals(setOf("a", "b"), result.tracks.first().sourceUris.keys)
        assertTrue(result.tracks.last().missing)
        assertEquals(library.favorites, result.favorites)
        assertEquals(library.playlists, result.playlists)
        assertTrue(result.sources.last().pinned)
        assertEquals(listOf(0, 1), result.sources.map { it.lastScanCount })
        assertTrue(result.sources.all { it.lastScanMs == 20L })
    }

    @Test fun subfolderImportDoesNotMarkSiblingReferencesMissing() {
        val first = IndexedTags().toTrack(entry).copy(sourceUris = mapOf("a" to entry.uri))
        val second = first.copy(id = "two")
        val library = Library(sources = listOf(FolderSource("a", "A", true)), tracks = listOf(first, second))
        val updated = LibraryEdits.applyScans(library, listOf(LibraryScan("a", listOf(first), fullSource = false)), 20) { "$it" }
        assertEquals(library, updated)
    }
}
