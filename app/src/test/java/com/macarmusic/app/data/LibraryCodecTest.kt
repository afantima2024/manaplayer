package com.macarmusic.app.data

import com.macarmusic.app.model.*
import kotlinx.serialization.SerializationException
import org.junit.Assert.*
import org.junit.Test

class LibraryCodecTest {
    @Test fun libraryRoundTripRetainsAllMetadataAndReferences() {
        val track = Track(
            "content://disk/document/id", "content://disk/tree/music/document/id", "file.flac", "Song",
            "Artist", "Album", 101_000, "2/10", "2024", "Jazz", "audio/flac",
            mapOf("content://disk/tree/music" to "content://disk/tree/music/document/id"),
            "content://disk/document/id", true, true,
        )
        val library = Library(sources = listOf(FolderSource("content://disk/tree/music", "Music", true, 100, 1)),
            tracks = listOf(track), favorites = setOf(track.id), playlists = listOf(LocalPlaylist("p", "Road", listOf(track.id))))
        assertEquals(library, LibraryCodec.decodeLibrary(LibraryCodec.encode(library)))
    }

    @Test fun queueRoundTripRetainsSettingsAndPositionWithoutAutoplayField() {
        val snapshot = PlaybackSnapshot(queue = listOf(Track("id", "content://local/id", "song.ogg", "Song")),
            positionMs = 4_000, repeat = 2, shuffle = true)
        val encoded = LibraryCodec.encode(snapshot)
        assertEquals(snapshot, LibraryCodec.decodePlayback(encoded))
        assertFalse(encoded.contains("playWhenReady"))
        assertFalse(encoded.contains("autoplay"))
    }

    @Test fun emptyAndForwardCompatibleFieldsAreAccepted() {
        assertEquals(Library(), LibraryCodec.decodeLibrary("""{"version":1,"futureField":true}"""))
    }

    @Test fun previousLibrariesRemainReadableAndNewLyricParentsRoundTrip() {
        val old = """{"version":1,"tracks":[{"id":"id","uri":"content://local/id","fileName":"song.mp3","title":"Song"}]}"""
        val track = LibraryCodec.decodeLibrary(old).tracks.single()
        assertTrue(track.sourceParents.isEmpty())
        val enriched = Library(tracks = listOf(track.copy(sourceParents = mapOf("content://local/tree" to "opaque:id"))))
        assertEquals(enriched, LibraryCodec.decodeLibrary(LibraryCodec.encode(enriched)))
    }

    @Test(expected = SerializationException::class) fun corruptDataIsNotSilentlyReset() {
        LibraryCodec.decodeLibrary("{ broken ")
    }

    @Test(expected = IllegalArgumentException::class) fun unknownSchemaIsNotOverwritten() {
        LibraryCodec.decodeLibrary("""{"version":99}""")
    }

    @Test(expected = IllegalArgumentException::class) fun duplicateTrackIdsAreRejected() {
        val track = Track("id", "content://local/id", "a.mp3", "A")
        LibraryCodec.decodeLibrary(LibraryCodec.encode(Library(tracks = listOf(track, track))))
    }

    @Test fun escapedNamesRoundTrip() {
        val playlist = LocalPlaylist("p", "Road \"Mix\"\n2026")
        assertEquals(playlist, LibraryCodec.decodeLibrary(LibraryCodec.encode(Library(playlists = listOf(playlist)))).playlists.single())
    }

    @Test fun pinsPlaylistFoldersAndLocationsSurviveRoundTripWithoutBreakingOldFiles() {
        val legacy = LibraryCodec.decodeLibrary("""{"sources":[{"uri":"content://disk/tree/music","name":"Music","persisted":true}],"playlists":[{"id":"p","name":"Road","trackIds":["song"]}]}""")
        assertFalse(legacy.sources.single().pinned)
        assertNull(legacy.playlists.single().manualTrackIds)
        assertTrue(legacy.playlists.single().folders.isEmpty())
        val source = legacy.sources.single().copy(pinned = true)
        val track = Track("song", "content://disk/song", "song.mp3", "Song",
            sourcePaths = mapOf(source.uri to "Artist/song.mp3"))
        val folder = PlaylistFolder(source.uri, "opaque-folder", "Artist", listOf(track.id), "Artist")
        val library = Library(sources = listOf(source), tracks = listOf(track),
            playlists = listOf(legacy.playlists.single().copy(folders = listOf(folder), manualTrackIds = emptyList())))
        assertEquals(library, LibraryCodec.decodeLibrary(LibraryCodec.encode(library)))
    }
}
