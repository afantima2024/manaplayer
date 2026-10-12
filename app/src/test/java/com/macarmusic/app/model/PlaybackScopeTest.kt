package com.macarmusic.app.model

import com.macarmusic.app.data.LibraryCodec
import org.junit.Assert.*
import org.junit.Test

class PlaybackScopeTest {
    private val first = FolderSource("content://disk/tree/first", "First", true)
    private val second = FolderSource("content://disk/tree/second", "Second", true)
    private val excluded = FolderSource("content://disk/tree/excluded", "Excluded", true, selectedForPlayback = false)
    private fun track(id: String, source: FolderSource): Track {
        val uri = "${source.uri}/document/$id"
        return Track(id, uri, "$id.mp3", id, sourceUris = mapOf(source.uri to uri))
    }
    private val a = track("A", first)
    private val b = track("B", second)
    private val c = track("C", excluded)
    private val library = Library(sources = listOf(first, second, excluded), tracks = listOf(c, b, a))

    @Test fun selectedFolderScopeCombinesSeveralSourcesWithoutExcludedMusic() {
        assertEquals(listOf(a, b), PlaybackScope.tracks(library, LoopMode.SELECTED_FOLDERS, null, SortOrder.TITLE))
    }

    @Test fun overlappingSelectionsDeduplicateAndUseAnEnabledGrant() {
        val shared = a.copy(sourceUris = a.sourceUris + (second.uri to "${second.uri}/document/A"))
        val original = library.copy(tracks = listOf(shared, b))
        assertEquals(2, PlaybackScope.tracks(original, LoopMode.SELECTED_FOLDERS, shared, SortOrder.TITLE).size)
        val selected = LibraryEdits.selectSources(original, setOf(second.uri))
        val tracks = PlaybackScope.tracks(selected, LoopMode.SELECTED_FOLDERS, shared, SortOrder.TITLE)
        assertEquals("${second.uri}/document/A", tracks.first().uri)
    }

    @Test fun folderScopeOnlyIncludesCurrentTracksSourceAndItsDescendants() {
        val nested = track("Nested", first)
        val result = PlaybackScope.tracks(library.copy(tracks = library.tracks + nested),
            LoopMode.FOLDER, a, SortOrder.TITLE)
        assertEquals(listOf(a, nested), result)
    }

    @Test fun folderScopeUsesTheGrantThatOpenedAnOverlappingTrack() {
        val shared = a.copy(uri = "${second.uri}/document/A",
            sourceUris = a.sourceUris + (second.uri to "${second.uri}/document/A"))
        assertEquals(second, PlaybackScope.folderFor(library, shared))
    }

    @Test fun folderScopeCanPlayAnExplicitFolderEvenIfNotInTheMultiSelection() {
        assertEquals(listOf(c), PlaybackScope.tracks(library, LoopMode.FOLDER, c, SortOrder.TITLE))
    }

    @Test fun noCurrentTrackUsesFirstSelectedFolderOnly() {
        assertEquals(listOf(a), PlaybackScope.tracks(library, LoopMode.FOLDER, null, SortOrder.TITLE))
    }

    @Test fun standaloneFileDoesNotGuessItsFolderFromTheUri() {
        val standalone = a.copy(sourceUris = emptyMap(), standaloneUri = a.uri)
        assertTrue(PlaybackScope.tracks(library, LoopMode.FOLDER, standalone, SortOrder.TITLE).isEmpty())
    }

    @Test fun emptySelectionNeverFallsBackToAllFolders() {
        val none = LibraryEdits.selectSources(library, emptySet())
        assertTrue(PlaybackScope.tracks(none, LoopMode.SELECTED_FOLDERS, a, SortOrder.TITLE).isEmpty())
        assertTrue(PlaybackScope.tracks(none, LoopMode.FOLDER, null, SortOrder.TITLE).isEmpty())
    }

    @Test fun selectingFoldersDoesNotRemoveSongsFavoritesOrPlaylists() {
        val original = library.copy(favorites = setOf(a.id), playlists = listOf(LocalPlaylist("p", "Trip", listOf(a.id))))
        val result = LibraryEdits.selectSources(original, setOf(second.uri, excluded.uri))
        assertEquals(original.tracks, result.tracks)
        assertEquals(original.favorites, result.favorites)
        assertEquals(original.playlists, result.playlists)
        assertEquals(listOf(second.uri, excluded.uri), PlaybackScope.selectedSources(result).map { it.uri })
    }

    @Test fun selectionRoundTripAndOlderLibrariesDefaultToSelected() {
        val selected = LibraryEdits.selectSources(library, setOf(second.uri))
        assertEquals(selected, LibraryCodec.decodeLibrary(LibraryCodec.encode(selected)))
        val old = """{"sources":[{"uri":"content://disk/tree/old","name":"Old","persisted":true}]}"""
        assertTrue(LibraryCodec.decodeLibrary(old).sources.single().selectedForPlayback)
    }

    @Test fun missingMediaRemainsVisibleInsteadOfBeingSilentlySkipped() {
        val missing = a.copy(missing = true)
        val original = library.copy(tracks = listOf(missing, b))
        assertEquals(listOf(missing, b), PlaybackScope.tracks(original, LoopMode.SELECTED_FOLDERS, null, SortOrder.TITLE))
    }

    @Test fun loopModesAndLegacyMigrationMatchMediaPlayerRepeatValues() {
        assertEquals(0, LoopMode.OFF.repeatMode)
        assertEquals(1, LoopMode.SINGLE_FILE.repeatMode)
        assertEquals(2, LoopMode.FOLDER.repeatMode)
        assertEquals(2, LoopMode.SELECTED_FOLDERS.repeatMode)
        assertEquals(LoopMode.SINGLE_FILE, LoopMode.fromRepeatMode(1))
        assertEquals(LoopMode.SELECTED_FOLDERS, LoopMode.fromRepeatMode(2))
        assertEquals(LoopMode.OFF, LoopMode.fromRepeatMode(0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun selectingAnUnknownFolderIsRejected() {
        LibraryEdits.selectSources(library, setOf("missing"))
    }
}
