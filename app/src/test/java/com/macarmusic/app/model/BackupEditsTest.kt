package com.macarmusic.app.model

import com.macarmusic.app.data.BackupManifest
import com.macarmusic.app.data.BackupSettings
import com.macarmusic.app.data.DisplayOptions
import org.junit.Assert.*
import org.junit.Test

class BackupEditsTest {
    private val origin = "00000000-0000-0000-0000-000000000001"
    private val otherOrigin = "00000000-0000-0000-0000-000000000002"
    private val source = FolderSource("content://disk/tree/music", "Music", true, pinned = true)
    private val song = Track("song", "content://disk/song", "song.mp3", "Song", "Artist", "Album", durationMs = 60_000,
        sourceUris = mapOf(source.uri to "content://disk/song"), sourcePaths = mapOf(source.uri to "Album/song.mp3"))
    private val old = Library(sources = listOf(source), tracks = listOf(song), favorites = setOf(song.id),
        playlists = listOf(LocalPlaylist("p", "Mix", listOf(song.id),
            listOf(PlaylistFolder(source.uri, "old-document-id", "Album", listOf(song.id), "Album")), emptyList())))
    private fun backup(library: Library = old) = BackupManifest(origin = origin, createdAtMs = 2000,
        settings = BackupSettings(DisplayOptions(), PlaybackOptions(), SortOrder.TITLE, 2, "", ColorSlots()), library = library,
        positions = listOf(CollectionPosition(PlaybackCollection(CollectionKind.FOLDER, source.uri, "Album"), song.id, 18000, 1000)))
    private val target = FolderSource("content://new/tree/files", "New drive", true, selectedForPlayback = false)
    private val actual = song.copy(id = "new-song", uri = "content://new/song", sourceUris = mapOf(target.uri to "content://new/song"),
        sourcePaths = mapOf(target.uri to "Album/song.mp3"))

    @Test fun newDeviceNeverTrustsOldContentUrisOrPermissionFlags() {
        val merged = BackupEdits.merge(Library(), backup(), otherOrigin)
        assertTrue(merged.library.sources.single().needsReconnect)
        assertFalse(merged.library.sources.single().persisted)
        assertTrue(BackupEdits.unavailable(merged.library.tracks.single().uri))
        assertTrue(merged.library.tracks.single().missing)
        assertTrue(PlaybackScope.selectedSources(merged.library).isEmpty())
        assertEquals(merged.library.tracks.single().id, merged.library.favorites.single())
    }

    @Test fun evenIdenticalMediaStoreIdsOnAnotherDeviceAreNotAssumedToBeTheSameSong() {
        val unrelated = song.copy(fileName = "unrelated.mp3", title = "Different")
        val current = old.copy(tracks = listOf(unrelated), favorites = emptySet(), playlists = emptyList())
        val result = BackupEdits.merge(current, backup(), otherOrigin).library
        assertEquals(2, result.tracks.size)
        assertFalse(song.id in result.favorites)
    }

    @Test fun sameInstallationMergesFavoritesAndListsWithoutDuplicatingSongs() {
        val current = old.copy(favorites = emptySet(), playlists = listOf(LocalPlaylist("other", "Other", listOf(song.id))))
        val result = BackupEdits.merge(current, backup(), origin).library
        assertEquals(1, result.tracks.size)
        assertEquals(setOf(song.id), result.favorites)
        assertEquals(setOf("p", "other"), result.playlists.map { it.id }.toSet())
    }

    @Test fun repeatedImportIsIdempotentAndDoesNotOverwriteExistingPlaylistNames() {
        val first = BackupEdits.merge(Library(), backup(), otherOrigin).library
        val renamed = LibraryEdits.renamePlaylist(first, "p", "Renamed")
        val second = BackupEdits.merge(renamed, backup(), otherOrigin).library
        assertEquals(renamed, second)
        assertEquals("Renamed", second.playlists.single().name)
    }

    @Test fun separatePlaylistsWithTheSameNameReceiveNonDestructiveNames() {
        val current = Library(playlists = listOf(LocalPlaylist("other", "Mix")))
        val result = BackupEdits.merge(current, backup(), otherOrigin).library
        assertEquals(setOf("Mix", "Mix (2)"), result.playlists.map { it.name }.toSet())
    }

    @Test fun reconnectUpdatesFavoritesPlaylistMembersPinsAndPositionAliases() {
        val imported = BackupEdits.merge(Library(), backup(), otherOrigin)
        val pending = imported.library.sources.single()
        val withActual = imported.library.copy(sources = imported.library.sources + target, tracks = imported.library.tracks + actual)
        val matches = BackupEdits.matches(withActual, pending.uri, target.uri)
        assertEquals(1, matches.size)
        val result = BackupEdits.reconnect(withActual, matches, pending.uri to target.uri)
        assertEquals(setOf(actual.id), result.favorites)
        assertEquals(listOf(actual.id), result.playlists.single().trackIds)
        assertEquals(actual.id, result.tracks.single().id)
        assertEquals(target.uri, result.sources.single().uri)
        assertTrue(result.sources.single().pinned)
        assertTrue(result.sources.single().selectedForPlayback)
        assertEquals("", result.playlists.single().folders.single().documentId)
        val position = CollectionPositions.reconcile(result, imported.positions).single()
        assertEquals(actual.id, position.trackId)
        assertEquals(target.uri, position.collection.id)
        assertEquals(18000L, position.positionMs)
        assertEquals(result, BackupEdits.merge(result, backup(), otherOrigin).library)
    }

    @Test fun ambiguousOrMissingMatchesRemainVisibleWithoutChangingFavorites() {
        val imported = BackupEdits.merge(Library(), backup(), otherOrigin).library
        val pending = imported.sources.single()
        val duplicate = actual.copy(id = "duplicate", uri = "content://new/duplicate")
        val current = imported.copy(sources = imported.sources + target, tracks = imported.tracks + actual + duplicate)
        assertTrue(BackupEdits.matches(current, pending.uri, target.uri).isEmpty())
        val result = BackupEdits.reconnect(current, emptyMap(), pending.uri to target.uri)
        assertTrue(result.sources.any { it.uri == pending.uri })
        assertEquals(imported.favorites, result.favorites)
    }

    @Test fun restoredReferenceUnderAnExistingSourceIsReconciledByRescan() {
        val imported = BackupEdits.merge(old.copy(tracks = emptyList(), favorites = emptySet(), playlists = emptyList()), backup(), origin).library
        assertTrue(BackupEdits.unavailable(imported.tracks.single().uri))
        val result = LibraryEdits.applyScan(imported, source.uri, listOf(song), 100, null)
        assertEquals(1, result.tracks.size)
        assertEquals(song.id, result.tracks.single().id)
        assertEquals(setOf(song.id), result.favorites)
        assertEquals(listOf(song.id), result.playlists.single().trackIds)
    }

    @Test fun locatingAStandaloneFileUpdatesEveryReference() {
        val original = song.copy(sourceUris = emptyMap(), sourcePaths = emptyMap(), standaloneUri = song.uri)
        val library = old.copy(sources = emptyList(), tracks = listOf(original),
            playlists = listOf(LocalPlaylist("p", "Mix", listOf(song.id))))
        val manifest = backup(library).copy(positions = emptyList())
        val imported = BackupEdits.merge(Library(), manifest, otherOrigin).library
        val located = actual.copy(sourceUris = emptyMap(), sourcePaths = emptyMap(), standaloneUri = actual.uri, standalonePersisted = true)
        val result = BackupEdits.reconnect(imported, mapOf(imported.tracks.single().id to located))
        assertEquals(setOf(located.id), result.favorites)
        assertEquals(listOf(located.id), result.playlists.single().trackIds)
        assertTrue(result.tracks.single().standalonePersisted)
    }

    @Test fun pinnedRestoredFolderCanResolveItsDocumentIdWithoutBeingUnpinned() {
        val pending = old.playlists.single().folders.single().copy(documentId = "")
        val current = old.copy(playlists = listOf(old.playlists.single().copy(folders = listOf(pending))))
        val resolved = pending.copy(documentId = "new-document-id")
        val updated = LibraryEdits.setPlaylistFolders(current, "p", listOf(resolved))
        assertEquals(resolved, updated.playlists.single().folders.single())
        assertTrue(updated.sources.single().pinned)
        assertTrue(pending.matches(resolved.location()))
    }

    @Test(expected = PinnedFolderException::class) fun resolvingAPinnedFolderCannotSilentlyChangeItsRelativePath() {
        val pending = old.playlists.single().folders.single().copy(documentId = "")
        val current = old.copy(playlists = listOf(old.playlists.single().copy(folders = listOf(pending))))
        LibraryEdits.setPlaylistFolders(current, "p", listOf(pending.copy(documentId = "other-id", relativePath = "Other")))
    }

    @Test fun distinctUnresolvedSubfoldersKeepDistinctBrowserKeys() {
        val first = BrowserLocation(source.uri, "", "First", "First")
        assertNotEquals(first.key, first.copy(name = "Second", relativePath = "Second").key)
    }
}
