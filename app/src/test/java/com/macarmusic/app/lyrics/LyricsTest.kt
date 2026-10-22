package com.macarmusic.app.lyrics

import com.macarmusic.app.data.BoundedInput
import com.macarmusic.app.model.DocumentEntry
import com.macarmusic.app.model.Track
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.nio.charset.Charset

class LyricsTest {
    @Test fun multipleTimestampsFractionsAndOffsetAreSorted() {
        val lyrics = LyricsParser.parse("[ti:Fixture]\n[offset:500]\n[00:20.12][00:01.5]First marker\n[00:10.123]Next marker")!!
        assertEquals(listOf(1_000L, 9_623L, 19_620L), lyrics.lines.map { it.timeMs })
        assertEquals(-1, lyrics.activeLine(999))
        assertEquals(0, lyrics.activeLine(1_000))
        assertEquals(1, lyrics.activeLine(19_000))
        assertEquals(0, lyrics.activeLine(2_000))
        assertEquals(2, lyrics.activeLine(999_000))
    }

    @Test fun translationsAtTheSameTimeAreJoinedAndDuplicateTextIsRemoved() {
        val lyrics = LyricsParser.parse("[00:01]First\n[00:01]Translation\n[00:01]First")!!
        assertEquals(listOf(LyricLine(1_000, "First\nTranslation")), lyrics.lines)
    }

    @Test fun emptyTimedMarkersEndThePreviousLine() {
        val lyrics = LyricsParser.parse("[00:00]First\n[00:03]\n[00:07]Second")!!
        assertEquals("", lyrics.lines[lyrics.activeLine(5_000)].text)
    }

    @Test fun lastTimestampUsesTheSortedEndpointIncludingBlankEndMarkersAndSavedOffsets() {
        val text = "[offset:500]\n[03:42]\n[00:01][01:30]Synthetic words\n[02:00]Other words"
        assertEquals(221_500L, LyricsParser.parse(text)!!.lastTimestampMs)
        assertEquals(220_500L, LyricsParser.parse(text, additionalOffsetMs = 1_000)!!.lastTimestampMs)
        assertEquals(222_500L, LyricsParser.parse(text, additionalOffsetMs = -1_000)!!.lastTimestampMs)
        assertEquals(0L, LyricsParser.parse("[offset:2000]\n[00:01]First")!!.lastTimestampMs)
    }

    @Test fun untimedLyricsHaveNoInventedDuration() {
        assertNull(LyricsParser.parse("[length:03:42]\nSynthetic plain text")!!.lastTimestampMs)
        assertNull(Lyrics(emptyList()).lastTimestampMs)
    }

    @Test fun durationChecksHaveInclusiveBoundariesAndAllowRealOutrosNotShortPreviews() {
        fun words(end: Long) = Lyrics(listOf(LyricLine(0, "Start"), LyricLine(end, "End")))
        assertTrue(LyricMatch.durationMatches(180_000, 162_000))
        assertTrue(LyricMatch.durationMatches(180_000, 198_000))
        assertFalse(LyricMatch.durationMatches(180_000, 161_999))
        assertFalse(LyricMatch.durationMatches(180_000, 198_001))
        assertFalse(LyricMatch.timingMatches(180_000, words(40_000)))
        assertFalse(LyricMatch.timingMatches(180_000, words(134_999)))
        assertTrue(LyricMatch.timingMatches(180_000, words(135_000)))
        assertTrue(LyricMatch.timingMatches(180_000, words(198_000)))
        assertFalse(LyricMatch.timingMatches(180_000, words(198_001)))
        assertFalse(LyricMatch.timingMatches(30_000, words(5_000)))
        assertTrue(LyricMatch.timingMatches(30_000, words(22_500)))
        assertFalse(LyricMatch.timingMatches(600_000, words(539_999)))
        assertTrue(LyricMatch.timingMatches(600_000, words(540_000)))
    }

    @Test fun unknownDurationsDoNotInventTimingAndOffsetsAndBlankEndMarkersAreRespected() {
        val plain = LyricsParser.parse("Synthetic untimed lyrics")!!
        assertFalse(LyricMatch.timingMatches(180_000, plain))
        assertTrue(LyricMatch.timingMatches(0, plain))
        assertTrue(LyricMatch.durationMatches(180_000, 0))
        assertTrue(LyricMatch.durationMatches(0, 40_000))
        assertTrue(LyricMatch.timingMatches(180_000, LyricsParser.parse("[00:40]Words\n[03:00]")!!))
        assertFalse(LyricMatch.timingMatches(180_000, LyricsParser.parse("[offset:50000]\n[02:50]Words")!!))
    }

    @Test fun metadataIsNotDisplayedAndPlainLyricsCanBeScrolled() {
        val lyrics = LyricsParser.parse("[ar:Synthetic artist]\nFirst marker\n\nSecond marker")!!
        assertFalse(lyrics.timed)
        assertEquals(listOf("First marker", "Second marker"), lyrics.lines.map { it.text })
        assertEquals(-1, lyrics.activeLine(5_000))
        assertNull(LyricsParser.parse("[ar:Fixture]\n[ti:Fixture]"))
    }

    @Test fun wordTimestampsAreRemovedAndNegativeTimesClampToZero() {
        val lyrics = LyricsParser.parse("[offset:2000]\n[00:01]<00:01.00>Test <00:01.20>marker")!!
        assertEquals(LyricLine(0, "Test marker"), lyrics.lines.single())
    }

    @Test fun utf8Utf16AndChineseLegacyEncodingDecode() {
        val text = "[00:01]合成测试歌词"
        for (charset in listOf(Charsets.UTF_8, Charsets.UTF_16, Charset.forName("GB18030"))) {
            assertEquals(text, LyricsParser.decode(text.toByteArray(charset)))
        }
        val le = byteArrayOf(0xff.toByte(), 0xfe.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals(text, LyricsParser.decode(le))
    }

    @Test(expected = IOException::class) fun htmlErrorPagesAreNotLyrics() {
        LyricsParser.parse("<html>Provider error</html>")
    }

    @Test(expected = IOException::class) fun responseLimitIsEnforcedWithoutReadingEverything() {
        BoundedInput.read(ByteArray(101).inputStream(), 100)
    }

    @Test(expected = IOException::class) fun excessiveTimestampCountIsRejected() {
        LyricsParser.parse("[00:01]Synthetic marker\n".repeat(4_001))
    }

    @Test(expected = IOException::class) fun sizeLimitCountsEncodedBytesNotOnlyCharacters() {
        LyricsParser.parse(("合".repeat(1_000) + "\n").repeat(100))
    }

    @Test fun sidecarsMatchOnlySameFilenameAndPreferLrc() {
        val track = Track("id", "content://test/song", "Song.MP3", "Song")
        fun entry(name: String, directory: Boolean = false, virtual: Boolean = false) =
            DocumentEntry(name, "content://test/$name", name, "application/octet-stream", directory, virtual)
        val files = listOf(entry("SONG.txt"), entry("notes.txt"), entry("Song.LRC"), entry("Song.lrc", virtual = true),
            entry("Song.lrc", directory = true), entry("Song.mp3"))
        assertEquals(listOf("Song.LRC", "SONG.txt"), LyricFiles.matching(track, files).map { it.name })
        assertEquals(LyricFiles.matching(track, files), LyricFiles.matching(track, LyricFiles.index(files)))
        assertTrue(LyricFiles.matching(track.copy(fileName = "Other.mp3"), LyricFiles.index(files)).isEmpty())
    }

    @Test fun matchingUsesReferenceWeightsRatherThanHardSingerAndVersionRejection() {
        val query = LyricQuery("Synthetic song", "Fixture singer", 60_000)
        assertEquals(29, LyricMatch.score(query, "Synthetic Song", "Fixture singer", 61_000))
        assertEquals(24, LyricMatch.score(query, "Synthetic song (live)", "Fixture singer", 60_000))
        assertEquals(19, LyricMatch.score(query, "Synthetic song", "Someone else", 60_000))
        assertEquals(21, LyricMatch.score(query, "Synthetic song", "Fixture singer", 90_000))
    }

    @Test fun versionInventoryIncludesNamedAlternativesButDoesNotChangeAutomaticMatching() {
        val track = Track("id", "content://test/song", "01 - Synthetic.wav", "Synthetic")
        val names = listOf("01 - Synthetic.LRC", "01 - Synthetic (take 2).lrc",
            "Synthetic - QQ.txt", "Synthetic [Kuwo].lrc", "Synthetic.2.lrc",
            "Synthetic other song.lrc", "SyntheticBird.lrc", "Unrelated.lrc")
        val entries = names.map { DocumentEntry(it, "content://test/$it", it, "text/plain", false, false) }
        assertEquals(listOf(names.first()), LyricFiles.matching(track, entries).map { it.name })
        assertEquals(names.take(5).toSet(), LyricFiles.matching(track, entries, includeVersions = true).map { it.name }.toSet())
    }

    @Test fun titleOnlyMatchesDoNotRequireKnownDuration() {
        val query = LyricQuery("Fixture song", "", 60_000)
        assertEquals(19, LyricMatch.score(query, "Fixture Song", "Artist", 62_000))
        assertEquals(11, LyricMatch.score(query, "Fixture Song", "Artist", 70_000))
        assertEquals(11, LyricMatch.score(query.copy(durationMs = 0), "Fixture Song", "Artist", 60_000))
    }

    @Test fun fuzzyNormalizationAndFirstResultPreferenceMatchTheReference() {
        val query = LyricQuery("Synthetic_Song (Take)", "Fixture-Singer", 60_000)
        assertEquals(29, LyricMatch.score(query, "synthetic song take", "Fixture Singer", 60_000))
        assertEquals(28, LyricMatch.score(query, "synthetic song take", "Fixture Singer", 60_000, index = 1))
        assertEquals(16, LyricMatch.score(query, "Synthetic Song (Take)", "Fixture Singer / Guest", index = 0))
    }

    @Test fun filenameTagDisambiguationStillRecognizesBothSingers() {
        val artist = "林忆莲_李宗盛"
        assertTrue(LyricMatch.artistsMatch(artist, "李宗盛、林忆莲"))
        assertTrue(LyricMatch.artistsMatch(artist, "林忆莲/李宗盛"))
        assertFalse(LyricMatch.artistsMatch(artist, "李宗盛"))
        assertFalse(LyricMatch.artistsMatch(artist, "李宗盛、梁静茹"))
    }

    @Test fun numberedDuetFilenameRecoversSongAndBothArtists() {
        val filename = "1542. 当爱已成往事-林忆莲_李宗盛.MP3"
        val expected = LyricQuery("当爱已成往事", "林忆莲_李宗盛", 279_000)
        assertEquals(expected, LyricQueries.candidates(LyricQuery(filename, "小高网分享", 279_000)).first())
        assertEquals(expected.copy(artist = "李宗盛/林忆莲"),
            LyricQueries.candidates(LyricQuery(filename, "李宗盛/林忆莲", 279_000)).first())
    }

    @Test fun queryContainsOnlyBoundedMetadataNotLocalUris() {
        val track = Track("content://private/id", "content://private/song", "secret-file.mp3", "Song", "Singer",
            durationMs = 60_000)
        assertEquals(LyricQuery("Song", "Singer", 60_000), LyricQuery.from(track))
        assertEquals(160, LyricQuery.from(track.copy(title = "s".repeat(500))).title.length)
    }

    @Test fun localNumberAndPromotionalArtistAreNotUsedAsSongIdentity() {
        val raw = LyricQuery("1562.如果你也听说-张惠妹", "小高网分享", 311_000)
        val candidates = LyricQueries.candidates(raw)
        assertEquals(LyricQuery("如果你也听说", "张惠妹", 311_000), candidates.first())
        assertTrue(candidates.none { "1562" in it.title || "分享" in it.artist })
        assertEquals("小高网分享", raw.artist)
    }

    @Test fun localNamesSupportBothArtistTitleOrdersAndTechnicalSuffixes() {
        val expected = LyricQuery("Fixture song", "Fixture singer", 60_000)
        for (name in listOf("01. Fixture singer - Fixture song [FLAC].mp3",
            "CD1 01 - Fixture song - Fixture singer [24bit].flac")) {
            assertTrue(LyricQueries.candidates(LyricQuery(name, "<unknown>", 60_000)).contains(expected))
        }
    }

    @Test fun copiedNumberedFilenameDoesNotTurnCopyIntoTheArtist() {
        val filename = "1586.演员-薛之谦 - Copy (2).mp3"
        val expected = LyricQuery("演员", "薛之谦", 261_000)
        for (artist in listOf("", "Unknown artist", "薛之谦")) {
            assertEquals(expected, LyricQueries.candidates(LyricQuery(filename, artist, 261_000), filename).first())
        }
        assertEquals(expected, LyricQueries.candidates(
            LyricQuery("Unknown title", "Unknown artist", 261_000), filename).first())
        assertEquals("Copy", LyricQueries.candidates(LyricQuery("Copy", "Singer", 0)).first().title)
    }

    @Test fun trustedArtistDisambiguatesACombinedTitle() {
        for (title in listOf("01. Fixture singer - Fixture song", "01. Fixture song - Fixture singer")) {
            val candidates = LyricQueries.candidates(LyricQuery(title, "Fixture singer", 60_000))
            assertEquals(listOf(LyricQuery("Fixture song", "Fixture singer", 60_000)), candidates)
        }
    }

    @Test fun numberedSongNamesAndRecordingVersionsArePreserved() {
        for (title in listOf("1979", "21 Guns", "3.14", "Song - Live", "Song (Remix)")) {
            val query = LyricQuery(title, "Singer", 60_000)
            assertEquals(query, LyricQueries.candidates(query).first())
        }
        val query = LyricQuery("01. Song (Live) [FLAC]", "Singer", 60_000)
        assertEquals("Song (Live)", LyricQueries.candidates(query).first().title)
        val unknownArtist = LyricQuery("Song - Live", "", 60_000)
        assertEquals(listOf(unknownArtist), LyricQueries.candidates(unknownArtist))
    }

    @Test fun filenameCanRecoverMissingTagsWithoutUploadingAPath() {
        val candidates = LyricQueries.candidates(LyricQuery("Unknown title", "www.example.com", 311_000),
            "C:\\private\\1562.如果你也听说-张惠妹.mp3")
        assertEquals(LyricQuery("如果你也听说", "张惠妹", 311_000), candidates.first())
        assertTrue(candidates.none { "private" in it.title || "\\" in it.title || "www." in it.artist })
    }

    @Test fun realTagsHavePriorityAndQueryExpansionIsBounded() {
        val query = LyricQuery("Proper song", "Proper singer", 60_000)
        val candidates = LyricQueries.candidates(query, "Another singer - Another song.mp3")
        assertEquals(query, candidates.first())
        assertTrue(candidates.all { it.artist == query.artist })
        val large = LyricQueries.candidates(LyricQuery("a".repeat(1_000), "b".repeat(1_000), Long.MAX_VALUE),
            "c".repeat(1_000))
        assertTrue(large.size <= 6)
        assertTrue(large.all { it.title.length <= 160 && it.artist.length <= 120 && it.durationMs <= 86_400_000 })
    }

    @Test fun metadataNormalizationIgnoresHighlightMarkupButKeepsVersionWords() {
        assertEquals(LyricMatch.normalize("Synthetic song"), LyricMatch.normalize("<em>Synthetic</em> song"))
        assertNotEquals(LyricMatch.normalize("Synthetic song"), LyricMatch.normalize("<em>Synthetic song</em> (Live)"))
    }

    @Test fun durationBonusesUseTheReferenceStrictThreeFiveAndTenSecondBoundaries() {
        val query = LyricQuery("Synthetic song", "Fixture singer", 60_000)
        for ((difference, bonus) in listOf(2_999L to 8, 3_000L to 5, 4_999L to 5,
            5_000L to 2, 9_999L to 2, 10_000L to 0)) {
            assertEquals(20 + bonus, LyricMatch.score(query, query.title, query.artist, 60_000 + difference, index = 1))
        }
        assertEquals(20, LyricMatch.score(query, query.title, query.artist, index = 1))
    }

    @Test fun referenceKeywordCleanupOnlyAppliesToKugouAndKuwo() {
        val query = LyricQuery("Synthetic song (Live)（Stage）", "Singer - extra", 0)
        assertEquals("Synthetic song Singer", LyricQueries.keyword(query, cleaned = true))
        assertEquals("Synthetic song (Live)（Stage） Singer - extra", LyricQueries.keyword(query, cleaned = false))
        assertEquals("Synthetic song", LyricQueries.keyword(query.copy(artist = "Unknown artist"), cleaned = true))
    }

    @Test fun filenameRecoversUnusableTitleEvenWithAnUnrecognizedUploaderArtist() {
        val queries = LyricQueries.candidates(LyricQuery("Unknown title", "An uploader", 60_000),
            "01. Fixture song - Fixture singer.mp3")
        assertEquals(LyricQuery("Fixture song", "Fixture singer", 60_000), queries.first())
    }

    @Test fun matchingFilenameTitleCanRecoverWrongArtistOnlyAfterRealTags() {
        val tags = LyricQuery("Fixture song", "Wrong tagged artist", 60_000)
        val queries = LyricQueries.candidates(tags, "Fixture singer - Fixture song.mp3")
        assertEquals(tags, queries.first())
        assertTrue(queries.contains(tags.copy(artist = "Fixture singer")))
        val unrelated = LyricQueries.candidates(tags, "Another singer - Unrelated song.mp3")
        assertTrue(unrelated.all { it.artist == tags.artist })
    }
}
