package com.macarmusic.app.model

import org.junit.Assert.*
import org.junit.Test

class LibraryEditsTest {
    private val source = FolderSource("content://disk/tree/music", "Music", true)
    private val other = FolderSource("content://disk/tree/album", "Album", true)
    private val track = Track("id", "content://disk/tree/music/document/song", "song.mp3", "Song",
        sourceUris = mapOf(source.uri to "content://disk/tree/music/document/song"))

    @Test fun overlappingFoldersProduceOneTrackWithBothGrants() {
        val original = Library(sources = listOf(source, other), tracks = listOf(track), favorites = setOf(track.id))
        val incoming = track.copy(uri = "content://disk/tree/album/document/song", sourceUris = mapOf(other.uri to "content://disk/tree/album/document/song"))
        val result = LibraryEdits.upsertTracks(original, listOf(incoming))
        assertEquals(1, result.tracks.size)
        assertEquals(setOf(source.uri, other.uri), result.tracks.single().sourceUris.keys)
        assertEquals(original.favorites, result.favorites)
    }

    @Test fun lyricParentsMergePerSourceAndRemovedGrantsArePruned() {
        val original = track.copy(sourceParents = mapOf(source.uri to "opaque-parent"))
        val incoming = track.copy(sourceUris = mapOf(other.uri to "content://disk/other"),
            sourceParents = mapOf(other.uri to "other-parent"))
        val merged = LibraryEdits.upsertTracks(Library(tracks = listOf(original)), listOf(incoming))
        assertEquals(mapOf(source.uri to "opaque-parent", other.uri to "other-parent"), merged.tracks.single().sourceParents)
        val removed = LibraryEdits.removeSource(merged, source.uri)
        assertEquals(mapOf(other.uri to "other-parent"), removed.tracks.single().sourceParents)
    }

    @Test fun emptyScanRetainsLibraryFavoritesAndPlaylistEntries() {
        val original = Library(sources = listOf(source), tracks = listOf(track),
            favorites = setOf(track.id), playlists = listOf(LocalPlaylist("p", "Road", listOf(track.id))))
        val result = LibraryEdits.applyScan(original, source.uri, emptyList(), 123, "Missing")
        assertTrue(result.tracks.single().missing)
        assertEquals(original.favorites, result.favorites)
        assertEquals(original.playlists, result.playlists)
        assertEquals(123, result.sources.single().lastScanMs)
    }

    @Test fun successfulRescanClearsMissingMarkerAndError() {
        val original = Library(sources = listOf(source.copy(unavailable = true, lastIssue = "Unplugged")),
            tracks = listOf(track.copy(missing = true)))
        val result = LibraryEdits.applyScan(original, source.uri, listOf(track), 3, null)
        assertFalse(result.tracks.single().missing)
        assertFalse(result.sources.single().unavailable)
        assertNull(result.sources.single().lastIssue)
    }

    @Test fun sourceRemovalPrunesOnlyUnownedTracksAndReferences() {
        val independent = track.copy(id = "single", uri = "content://local/single", sourceUris = emptyMap(),
            standaloneUri = "content://local/single")
        val original = Library(sources = listOf(source), tracks = listOf(track, independent),
            favorites = setOf(track.id, independent.id),
            playlists = listOf(LocalPlaylist("p", "Road", listOf(track.id, independent.id))))
        val result = LibraryEdits.removeSource(original, source.uri)
        assertEquals(listOf(independent), result.tracks)
        assertEquals(setOf(independent.id), result.favorites)
        assertEquals(listOf(independent.id), result.playlists.single().trackIds)
        assertTrue(result.sources.isEmpty())
    }

    @Test fun removingOneOverlappingGrantRebindsRemainingTrackUri() {
        val remainingUri = "content://disk/tree/album/document/song"
        val shared = track.copy(sourceUris = track.sourceUris + (other.uri to remainingUri))
        val result = LibraryEdits.removeSource(Library(sources = listOf(source, other), tracks = listOf(shared)), source.uri)
        assertEquals(remainingUri, result.tracks.single().uri)
        assertEquals(mapOf(other.uri to remainingUri), result.tracks.single().sourceUris)
    }

    @Test fun standaloneReferenceSurvivesFolderRemoval() {
        val single = track.copy(standaloneUri = "content://disk/document/song", standalonePersisted = true)
        val result = LibraryEdits.removeSource(Library(sources = listOf(source), tracks = listOf(single)), source.uri)
        assertEquals(single.standaloneUri, result.tracks.single().uri)
        assertTrue(result.tracks.single().standalonePersisted)
    }

    @Test fun rescanningPreservesStandaloneGrant() {
        val original = Library(tracks = listOf(track.copy(standaloneUri = "content://local/song", standalonePersisted = true)))
        val result = LibraryEdits.upsertTracks(original, listOf(track))
        assertEquals("content://local/song", result.tracks.single().standaloneUri)
        assertTrue(result.tracks.single().standalonePersisted)
    }

    @Test fun temporaryGrantCannotReplaceAUsablePersistedStandaloneGrant() {
        val old = track.copy(standaloneUri = "content://persisted/id", standalonePersisted = true)
        val temporary = track.copy(standaloneUri = "content://temporary/id", standalonePersisted = false)
        val result = LibraryEdits.upsertTracks(Library(tracks = listOf(old)), listOf(temporary))
        assertEquals("content://persisted/id", result.tracks.single().standaloneUri)
        assertTrue(result.tracks.single().standalonePersisted)
    }

    @Test fun removeTrackUpdatesEveryReference() {
        val original = Library(tracks = listOf(track), favorites = setOf(track.id),
            playlists = listOf(LocalPlaylist("p", "Road", listOf(track.id))))
        val result = LibraryEdits.removeTrack(original, track.id)
        assertTrue(result.tracks.isEmpty())
        assertTrue(result.favorites.isEmpty())
        assertTrue(result.playlists.single().trackIds.isEmpty())
    }

    @Test fun favoritesToggleIsReversible() {
        val original = Library(tracks = listOf(track))
        val favorite = LibraryEdits.toggleFavorite(original, track.id)
        assertTrue(track.id in favorite.favorites)
        assertEquals(original, LibraryEdits.toggleFavorite(favorite, track.id))
    }

    @Test fun playlistPreservesOrderDeduplicatesAndRejectsUnknownIds() {
        val second = track.copy(id = "second")
        val original = Library(tracks = listOf(track, second))
        val created = LibraryEdits.createPlaylist(original, "p", "  Road  ")
        val result = LibraryEdits.addToPlaylist(created, "p", listOf("second", track.id, "second", "unknown"))
        assertEquals("Road", result.playlists.single().name)
        assertEquals(listOf("second", track.id), result.playlists.single().trackIds)
        assertEquals(listOf(second, track), LibraryEdits.playlistTracks(result, "p"))
        assertEquals(listOf(track.id), LibraryEdits.removeFromPlaylist(result, "p", "second").playlists.single().trackIds)
    }

    @Test(expected = IllegalArgumentException::class) fun duplicatePlaylistNamesAreRejected() {
        LibraryEdits.createPlaylist(Library(playlists = listOf(LocalPlaylist("a", "Road"))), "b", " road ")
    }

    @Test(expected = IllegalArgumentException::class) fun blankPlaylistNamesAreRejected() {
        LibraryEdits.createPlaylist(Library(), "p", "  ")
    }

    @Test(expected = IllegalArgumentException::class) fun longPlaylistNamesAreRejected() {
        LibraryEdits.createPlaylist(Library(), "p", "x".repeat(81))
    }

    @Test(expected = IllegalArgumentException::class) fun unknownFavoriteIsRejected() {
        LibraryEdits.toggleFavorite(Library(), "missing")
    }
}
