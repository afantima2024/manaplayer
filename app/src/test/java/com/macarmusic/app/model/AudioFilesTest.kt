package com.macarmusic.app.model

import org.junit.Assert.*
import org.junit.Test

class AudioFilesTest {
    @Test fun commonExtensionsSurviveWrongProviderMime() {
        for (extension in AudioFiles.extensions) {
            assertTrue(extension, AudioFiles.accepts("a.$extension", "application/octet-stream"))
            assertTrue(extension, AudioFiles.accepts("a.${extension.uppercase()}", null))
        }
    }

    @Test fun knownMimeWorksWithoutExtension() {
        assertTrue(AudioFiles.accepts("No extension", "audio/mpeg"))
        assertTrue(AudioFiles.accepts("No extension", "Audio/FLAC; charset=binary"))
        assertTrue(AudioFiles.accepts("No extension", "application/ogg"))
    }

    @Test fun unsupportedAndMisleadingNamesAreNotOffered() {
        listOf("file.wma", "file.dsf", "file.m3u", "file.mp3.exe", "file.txt", "file.mp4", "file.mp3/").forEach {
            assertFalse(it, AudioFiles.accepts(it, "application/octet-stream"))
        }
        assertFalse(AudioFiles.accepts("playlist", "application/vnd.apple.mpegurl"))
    }

    @Test fun filenameAndMalformedTagFallbacks() {
        assertEquals("01 - A.song", AudioFiles.titleFromFileName("01 - A.song.FLAC"))
        assertEquals("No extension", AudioFiles.titleFromFileName("No extension"))
        assertEquals("Artist", AudioFiles.cleanTag("\u0000 Artist \u0000"))
        assertEquals("", AudioFiles.cleanTag(null))
        assertEquals(2048, AudioFiles.cleanTag("x".repeat(5000)).length)
    }

    @Test fun searchCombinesWordsAcrossTagsCaseInsensitively() {
        val first = track("1", "Road Home").copy(artist = "Test Artist", genre = "Jazz")
        val second = track("2", "Home").copy(artist = "Another")
        assertEquals(listOf(first), AudioFiles.sortAndFilter(listOf(first, second), " ROAD jazz ARTist ", SortOrder.TITLE))
    }

    @Test fun favoritesAndQueryAreBothApplied() {
        val tracks = listOf(track("1", "B"), track("2", "A"), track("3", "C"))
        assertEquals(listOf(tracks[2]), AudioFiles.sortAndFilter(tracks, "c", SortOrder.TITLE, setOf("2", "3")))
        assertTrue(AudioFiles.sortAndFilter(tracks, "", SortOrder.TITLE, emptySet()).isEmpty())
    }

    @Test fun albumSortUsesNumericTrackNumber() {
        val tracks = listOf(track("1", "Alpha").copy(album = "Album", trackNumber = "10/12"),
            track("2", "Zulu").copy(album = "album", trackNumber = "2/12"),
            track("3", "First").copy(album = "A", trackNumber = "1"))
        assertEquals(listOf("3", "2", "1"), AudioFiles.sortAndFilter(tracks, "", SortOrder.ALBUM).map { it.id })
    }

    @Test fun titleArtistAndFilenameSortsAreDeterministic() {
        val a = track("a", "B").copy(artist = "Zulu", fileName = "b.mp3")
        val b = track("b", "a").copy(artist = "alpha", fileName = "A.mp3")
        for (sort in listOf(SortOrder.TITLE, SortOrder.ARTIST, SortOrder.FILE_NAME)) {
            assertEquals(listOf(b, a), AudioFiles.sortAndFilter(listOf(a, b), "", sort))
        }
    }

    private fun track(id: String, title: String) = Track(id, "content://local/$id", "$title.mp3", title)
}
