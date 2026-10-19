package com.macarmusic.app.model

import org.junit.Assert.*
import org.junit.Test

class LibraryBrowsingTest {
    private fun track(id: String, artist: String, album: String, number: String = "") =
        Track(id, "content://music/$id", "$id.mp3", id, artist, album, trackNumber = number)
    private val songs = listOf(
        track("Second", "Artist", "Record", "2/12"),
        track("First", " artist ", "record", "1/12"),
        track("Other", "Another", "Record"),
        track("Unknown", "", ""),
    )

    @Test fun songModePreservesExistingSearchAndSort() {
        val library = Library(tracks = songs, favorites = setOf("First"))
        val page = LibraryBrowsing.page(library, "record", false, SortOrder.TITLE, LibraryLocation())
        assertEquals(AudioFiles.sortAndFilter(songs, "record", SortOrder.TITLE), page.tracks)
        assertFalse(page.showingGroups)
        assertTrue(page.groups.isEmpty())
    }

    @Test fun artistsNormalizeCaseAndOuterWhitespaceWithoutLosingUnknownArtists() {
        val page = LibraryBrowsing.page(Library(tracks = songs), "", false, SortOrder.TITLE, LibraryLocation(LibraryMode.ARTISTS))
        assertTrue(page.showingGroups)
        assertEquals(3, page.groups.size)
        assertEquals(2, page.groups.single { it.key == "artist" }.count)
        assertEquals(1, page.groups.single { it.key.isEmpty() }.count)
    }

    @Test fun equallyNamedAlbumsFromDifferentArtistsStaySeparate() {
        val page = LibraryBrowsing.page(Library(tracks = songs), "", false, SortOrder.TITLE, LibraryLocation(LibraryMode.ALBUMS))
        assertEquals(3, page.groups.size)
        assertEquals(listOf(1, 1, 2), page.groups.map { it.count }.sorted())
        val key = LibraryBrowsing.key(songs.first(), LibraryMode.ALBUMS)
        val album = LibraryBrowsing.page(Library(tracks = songs), "", false, SortOrder.TITLE, LibraryLocation(LibraryMode.ALBUMS, key))
        assertEquals(setOf("First", "Second"), album.tracks.map { it.id }.toSet())
        assertFalse(album.showingGroups)
        assertNotNull(album.selectedGroup)
    }

    @Test fun searchAndFavoritesFilterBothGroupCountsAndSongs() {
        val library = Library(tracks = songs, favorites = setOf("First", "Other"))
        val groups = LibraryBrowsing.page(library, "first", true, SortOrder.TITLE, LibraryLocation(LibraryMode.ARTISTS))
        assertEquals(1, groups.groups.size)
        assertEquals(1, groups.groups.single().count)
        val selected = LibraryBrowsing.page(library, "", true, SortOrder.TITLE, LibraryLocation(LibraryMode.ARTISTS, "artist"))
        assertEquals(listOf("First"), selected.tracks.map { it.id })
    }

    @Test fun clearingSearchKeepsTheChosenArtistAndAnEmptySearchDoesNotLoseItsName() {
        val location = LibraryLocation(LibraryMode.ARTISTS, "artist")
        val empty = LibraryBrowsing.page(Library(tracks = songs), "no-match", false, SortOrder.TITLE, location)
        assertTrue(empty.tracks.isEmpty())
        assertEquals("Artist", empty.selectedGroup?.name)
        assertEquals(2, LibraryBrowsing.page(Library(tracks = songs), "", false, SortOrder.TITLE, location).tracks.size)
    }

    @Test fun albumKeysDoNotCollideForConcatenatedArtistAndAlbumNames() {
        assertNotEquals(LibraryBrowsing.key(track("a", "AB", "C"), LibraryMode.ALBUMS),
            LibraryBrowsing.key(track("b", "A", "BC"), LibraryMode.ALBUMS))
    }

    @Test fun removedGroupReturnsAnEmptyPageInsteadOfAnotherArtistsSongs() {
        val page = LibraryBrowsing.page(Library(tracks = songs), "", false, SortOrder.TITLE, LibraryLocation(LibraryMode.ARTISTS, "deleted"))
        assertTrue(page.tracks.isEmpty())
        assertNull(page.selectedGroup)
    }
}
