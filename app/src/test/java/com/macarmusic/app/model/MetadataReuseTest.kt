package com.macarmusic.app.model

import com.macarmusic.app.data.LibraryCodec
import org.junit.Assert.*
import org.junit.Test

class MetadataReuseTest {
    private val entry = DocumentEntry("a", "content://local/a", "a.mp3", "audio/mpeg",
        isDirectory = false, isVirtual = false, sizeBytes = 12_345, modifiedMs = 100_000)
    private val track = Track("a", entry.uri, entry.name, "Tagged song",
        mimeType = entry.mimeType, metadataStamp = MetadataReuse.stamp(entry))

    @Test fun onlyPositiveCompleteFileStampsCanBeReused() {
        assertTrue(MetadataReuse.unchanged(track, entry))
        for (size in listOf(null, 0L, -1L)) {
            assertFalse(MetadataReuse.unchanged(track, entry.copy(sizeBytes = size)))
        }
        for (date in listOf(null, 0L, -1L)) {
            assertFalse(MetadataReuse.unchanged(track, entry.copy(modifiedMs = date)))
        }
    }

    @Test fun changedDateSizeNameOrTypeRequiresReadingTags() {
        for (changed in listOf(entry.copy(sizeBytes = 12_346), entry.copy(modifiedMs = 100_001),
            entry.copy(name = "renamed.mp3"), entry.copy(mimeType = "audio/flac"))) {
            assertFalse(MetadataReuse.unchanged(track, changed))
        }
    }

    @Test fun legacyMissingOrFailedMetadataMustBeReadAgain() {
        assertFalse(MetadataReuse.unchanged(null, entry))
        assertFalse(MetadataReuse.unchanged(track.copy(metadataStamp = null), entry))
        assertFalse(MetadataReuse.unchanged(track.copy(missing = true), entry))
        assertFalse(MetadataReuse.unchanged(track.copy(metadataPending = true), entry))
        assertFalse(MetadataReuse.unchanged(track, entry.copy(isVirtual = true)))
        assertFalse(MetadataReuse.unchanged(track, entry.copy(isDirectory = true)))
    }

    @Test fun fingerprintRoundTripsWithTheLibraryAndOldLibrariesRemainReadable() {
        val library = Library(tracks = listOf(track))
        assertEquals(library, LibraryCodec.decodeLibrary(LibraryCodec.encode(library)))
        assertNull(LibraryCodec.decodeLibrary("""{"tracks":[{"id":"a","uri":"content://local/a","fileName":"a.mp3","title":"A"}]}""")
            .tracks.single().metadataStamp)
    }
}
