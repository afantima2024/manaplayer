package com.macarmusic.app.model

import org.junit.Assert.*
import org.junit.Test

class PinyinInitialsTest {
    private fun track(id: String, file: String = "$id.mp3", artist: String = "", album: String = "", genre: String = "") =
        Track(id, "content://music/$id", file, id, artist, album, genre = genre)

    private fun search(query: String, vararg tracks: Track) =
        AudioFiles.sortAndFilter(tracks.toList(), query, SortOrder.TITLE).map { it.id }

    @Test fun filenameInitialsMatchEvenWhenMetadataHasAnotherTitle() {
        val song = track("Unrelated title", "消防.mp3")
        assertEquals(listOf(song.id), search("xf", song))
        assertEquals(listOf(song.id), search("  XF  ", song))
        assertTrue(search("fx", song).isEmpty())
    }

    @Test fun searchesEveryExistingMetadataFieldAndAllQueryWords() {
        val song = track("消防", artist = "周华健", album = "朋友", genre = "流行")
        for (query in listOf("xf", "zhj", "py", "lx", "xf 周华健", "xf zhj py lx"))
            assertEquals(query, listOf(song.id), search(query, song))
        assertTrue(search("xf missing", song).isEmpty())
    }

    @Test fun preservesLiteralChineseLatinNumbersAndExtensionSearch() {
        val song = track("消防 2026", "消防-Live.mp3")
        for (query in listOf("消防", "live", "2026", ".mp3", "防", "xflive"))
            assertEquals(query, listOf(song.id), search(query, song))
        assertTrue(search("xiaofang", song).isEmpty())
    }

    @Test fun simplifiedTraditionalAndPolyphonicCharactersWork() {
        for ((title, queries) in listOf(
            "消防" to listOf("xf"), "周華健" to listOf("zhj"), "重慶" to listOf("cq"),
            "重庆" to listOf("cq"), "重量" to listOf("zl"), "音乐" to listOf("yy"),
            "快乐" to listOf("kl"), "长江" to listOf("cj"), "长大" to listOf("zd"),
        )) for (query in queries) assertEquals("$title / $query", listOf(title), search(query, track(title)))
    }

    @Test fun overlappingMatchesAndLongQueriesAreNotDropped() {
        assertTrue(PinyinInitials.Query("xxf").matches("消消消防"))
        assertTrue(PinyinInitials.Query("x".repeat(64)).matches("消".repeat(64)))
        assertTrue(PinyinInitials.Query("x".repeat(65)).matches("消".repeat(65)))
        assertFalse(PinyinInitials.Query("x".repeat(65)).matches("消".repeat(64) + "防"))
    }

    @Test fun punctuationSeparatesWordsWithoutJoiningUnknownLettersOrEmoji() {
        assertTrue(PinyinInitials.Query("xf").matches("消 - 防"))
        assertFalse(PinyinInitials.Query("xf").matches("消\uD83D\uDE00防"))
        assertFalse(PinyinInitials.Query("xf").matches("消2防"))
        assertFalse(PinyinInitials.Query("xf").matches("消\u0378防"))
    }

    @Test fun initialsDoNotChangeLiteralMatchingOfLatinOnlyWords() {
        assertTrue(search("ab", track("a-b")).isEmpty())
        assertTrue(search("ob", track("Foo bar 消防")).isEmpty())
        assertEquals(listOf("a-b"), search("a-b", track("a-b")))
        assertTrue(PinyinInitials.Query("xfb").matches("消 F b"))
    }

    @Test fun initialsRespectFavoritesSortAndArtistGrouping() {
        val first = track("消防一", artist = "周华健")
        val second = track("消防二", artist = "Another")
        val library = Library(tracks = listOf(first, second), favorites = setOf(first.id))
        val groups = LibraryBrowsing.page(library, "xf", true, SortOrder.FILE_NAME, LibraryLocation(LibraryMode.ARTISTS))
        assertEquals(listOf(first), groups.tracks)
        assertEquals(1, groups.groups.single().count)
        val selected = LibraryBrowsing.page(library, "xf", false, SortOrder.TITLE,
            LibraryLocation(LibraryMode.ARTISTS, LibraryBrowsing.key(first, LibraryMode.ARTISTS)))
        assertEquals(listOf(first), selected.tracks)
    }

    @Test fun tableIncludesSupplementaryCharactersWithoutSplittingSurrogates() {
        assertTrue(PinyinInitials.mask(0x20000) != 0)
        val character = String(Character.toChars(0x20000))
        val initial = ('a'.code + Integer.numberOfTrailingZeros(PinyinInitials.mask(0x20000))).toChar().toString()
        assertTrue(PinyinInitials.Query(initial + "xf").matches(character + "消防"))
    }

    @Test fun clockwiseOriginIsIndependentOfModeAndSerializationOrder() {
        var origin = SpectrumOrigin.BOTTOM
        val sequence = List(4) { origin.clockwise().also { origin = it } }
        assertEquals(listOf(SpectrumOrigin.LEFT, SpectrumOrigin.TOP, SpectrumOrigin.RIGHT, SpectrumOrigin.BOTTOM), sequence)
        val spectrum = SpectrumOptions(style = SpectrumStyle.OFF)
        assertEquals(SpectrumStyle.OFF, spectrum.copy(origin = spectrum.origin.clockwise()).style)
        assertEquals(listOf(SpectrumOrigin.BOTTOM, SpectrumOrigin.LEFT, SpectrumOrigin.RIGHT, SpectrumOrigin.TOP),
            SpectrumOrigin.entries)
    }
}
