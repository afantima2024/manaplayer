package com.macarmusic.app.model

import org.junit.Assert.*
import org.junit.Test

class CollectionPositionsTest {
    private val source = FolderSource("content://disk/tree/music", "Music", true)
    private val a = Track("a", "content://disk/a", "a.mp3", "A", durationMs = 60_000,
        sourceUris = mapOf(source.uri to "content://disk/a"), sourcePaths = mapOf(source.uri to "Album/a.mp3"))
    private val b = a.copy(id = "b", uri = "content://disk/b", sourceUris = mapOf(source.uri to "content://disk/b"),
        sourcePaths = mapOf(source.uri to "Album extra/b.mp3"))
    private val library = Library(sources = listOf(source), tracks = listOf(a, b),
        playlists = listOf(LocalPlaylist("p", "Mix", listOf(b.id, a.id))))
    private val folder = PlaybackCollection(CollectionKind.FOLDER, source.uri, "Album")

    @Test fun positionsAreIndependentPerFolderSubfolderAndPlaylist() {
        val values = listOf(
            CollectionPosition(folder, "a", 5000, 1),
            CollectionPosition(folder.copy(relativePath = ""), "b", 9000, 2),
            CollectionPosition(PlaybackCollection.playlist("p"), "a", 17000, 3),
        )
        assertEquals(values, CollectionPositions.decode(CollectionPositions.encode(values)))
        assertFalse(CollectionPositions.encode(values).contains("playWhenReady"))
    }

    @Test fun capturePreservesSelectedTrackAndBoundsThePosition() {
        val snapshot = PlaybackSnapshot(queue = listOf(b, a), index = 1, positionMs = 90_000, collection = folder)
        assertEquals(CollectionPosition(folder, "a", 59_999, 10), CollectionPositions.capture(snapshot, 10))
        assertNull(CollectionPositions.capture(snapshot.copy(collection = null), 10))
        assertNull(CollectionPositions.capture(snapshot.copy(queue = emptyList()), 10))
    }

    @Test fun folderSelectionIncludesDescendantsButNotSimilarFolderNames() {
        assertEquals(listOf(a), CollectionPositions.tracks(library, folder, SortOrder.TITLE))
        assertEquals(listOf(b, a), CollectionPositions.tracks(library, PlaybackCollection.playlist("p"), SortOrder.TITLE))
        assertFalse(CollectionPositions.contains(library, folder, b))
    }

    @Test fun renamedPlaylistsKeepPositionsAndDeletedReferencesArePruned() {
        val position = CollectionPosition(PlaybackCollection.playlist("p"), "a", 12345, 1)
        assertEquals(listOf(position), CollectionPositions.reconcile(library, listOf(position)))
        assertEquals(listOf(position), CollectionPositions.reconcile(LibraryEdits.renamePlaylist(library, "p", "New name"), listOf(position)))
        assertTrue(CollectionPositions.reconcile(library.copy(playlists = emptyList()), listOf(position)).isEmpty())
        assertTrue(CollectionPositions.reconcile(library.copy(tracks = listOf(b)), listOf(position)).isEmpty())
    }

    @Test fun songsAppendedOutsideACollectionDoNotReplaceItsValidPosition() {
        val valid = CollectionPosition(folder, "a", 5000, 1)
        val outside = valid.copy(trackId = "b", updatedAtMs = 2)
        assertEquals(listOf(valid), CollectionPositions.reconcile(library, listOf(valid, outside)))
    }

    @Test fun relinkedSourcesAndTrackAliasesKeepTheSavedPosition() {
        val moved = source.copy(uri = "content://other/tree/music", restoredFrom = setOf(source.uri))
        val track = a.copy(id = "new-id", sourceUris = mapOf(moved.uri to "content://other/a"),
            sourcePaths = mapOf(moved.uri to "Album/a.mp3"), restoredIds = setOf(a.id))
        val position = CollectionPosition(folder, "a", 21000, 1)
        val result = CollectionPositions.reconcile(Library(sources = listOf(moved), tracks = listOf(track)), listOf(position)).single()
        assertEquals(folder.copy(id = moved.uri), result.collection)
        assertEquals("new-id", result.trackId)
        assertEquals(21000L, result.positionMs)
    }

    @Test fun partiallyReconnectedFoldersKeepThePositionOfAnUnmatchedSong() {
        val pending = source.copy(needsReconnect = true)
        val moved = source.copy(uri = "content://other/tree/music", restoredFrom = setOf(source.uri))
        val track = a.copy(uri = "content://com.macarmusic.app.restored/track/pending", missing = true)
        val position = CollectionPosition(folder, a.id, 21000, 1)
        val result = CollectionPositions.reconcile(Library(sources = listOf(pending, moved), tracks = listOf(track)), listOf(position)).single()
        assertEquals(folder.copy(id = moved.uri), result.collection)
        assertEquals(a.id, result.trackId)
        assertEquals(21000L, result.positionMs)
    }

    @Test(expected = IllegalArgumentException::class) fun duplicateCollectionsAreRejected() {
        val position = CollectionPosition(folder, "a", 1, 1)
        CollectionPositions.decode(CollectionPositions.encode(listOf(position, position)))
    }

    @Test(expected = IllegalArgumentException::class) fun parentTraversalIsNotACollectionPath() {
        folder.copy(relativePath = "../Other")
    }

    @Test(expected = IllegalArgumentException::class) fun unsafeMediaTimeIsRejected() {
        CollectionPosition(folder, "a", Long.MAX_VALUE, 1)
    }
}
