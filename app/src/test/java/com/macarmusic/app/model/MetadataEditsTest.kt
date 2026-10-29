package com.macarmusic.app.model

import com.macarmusic.app.data.LibraryCodec
import org.junit.Assert.*
import org.junit.Test

class MetadataEditsTest {
    private val pending = Track("song", "content://music/song", "song.mp3", "song",
        mimeType = "audio/mpeg", sourceUris = mapOf("folder" to "content://music/song"),
        metadataStamp = FileStamp(1234, 5678), metadataPending = true)
    private val read = pending.copy(title = "Tagged title", artist = "Artist", album = "Album",
        durationMs = 180_000, trackNumber = "3", year = "2026", genre = "Genre", metadataPending = false)

    @Test fun completionPreservesConcurrentOwnershipAndUserEdits() {
        val latest = pending.copy(sourceUris = pending.sourceUris + ("other" to "content://music/other"),
            sourcePaths = mapOf("other" to "Album/song.mp3"), sourceParents = mapOf("other" to "Album"),
            standaloneUri = "content://music/standalone", standalonePersisted = true, restoredIds = setOf("restored"))
        val library = Library(tracks = listOf(latest), favorites = setOf("song"),
            playlists = listOf(LocalPlaylist("playlist", "My songs", listOf("song"))))
        val completed = MetadataEdits.complete(library, listOf(MetadataUpdate(pending, read)))
        assertEquals(read.copy(sourceUris = latest.sourceUris, sourcePaths = latest.sourcePaths,
            sourceParents = latest.sourceParents, standaloneUri = latest.standaloneUri,
            standalonePersisted = true, restoredIds = latest.restoredIds), completed.tracks.single())
        assertSame(library.favorites, completed.favorites)
        assertSame(library.playlists, completed.playlists)
    }

    @Test fun staleRemovedMissingRenamedAndAlreadyCompletedRequestsNeverOverwrite() {
        for (latest in listOf(
            emptyList(), listOf(read), listOf(pending.copy(missing = true)),
            listOf(pending.copy(uri = "content://music/reconnected")),
            listOf(pending.copy(fileName = "renamed.mp3")),
            listOf(pending.copy(mimeType = "audio/flac")),
            listOf(pending.copy(metadataStamp = FileStamp(4321, 5678))),
        )) {
            val library = Library(tracks = latest)
            assertSame(library, MetadataEdits.complete(library, listOf(MetadataUpdate(pending, read))))
        }
    }

    @Test fun batchesOnlyCompleteTheMatchingRecords() {
        val other = pending.copy(id = "other", uri = "content://music/other")
        val library = Library(tracks = listOf(pending, other))
        val completed = MetadataEdits.complete(library, listOf(MetadataUpdate(pending, read)))
        assertEquals(listOf(read, other), completed.tracks)
        assertFalse(MetadataReuse.unchanged(other, MetadataEdits.entry(other)))
        assertTrue(MetadataReuse.unchanged(read, MetadataEdits.entry(read)))
    }

    @Test fun pendingDetailsRoundTripWithoutChangingOldLibraries() {
        val library = Library(tracks = listOf(pending))
        assertEquals(library, LibraryCodec.decodeLibrary(LibraryCodec.encode(library)))
        val old = LibraryCodec.decodeLibrary("""{"tracks":[{"id":"a","uri":"content://music/a","fileName":"a.mp3","title":"A"}]}""")
        assertFalse(old.tracks.single().metadataPending)
        assertNull(old.tracks.single().metadataStamp)
    }

    @Test fun unknownFingerprintsRemainUntrustedAfterCompletion() {
        val unknown = pending.copy(metadataStamp = null)
        val completed = MetadataEdits.complete(Library(tracks = listOf(unknown)),
            listOf(MetadataUpdate(unknown, read.copy(metadataStamp = null)))).tracks.single()
        assertFalse(completed.metadataPending)
        assertNull(MetadataEdits.entry(completed).sizeBytes)
        assertFalse(MetadataReuse.unchanged(completed, MetadataEdits.entry(completed)))
    }
}
