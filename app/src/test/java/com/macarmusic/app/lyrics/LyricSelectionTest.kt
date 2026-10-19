package com.macarmusic.app.lyrics

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LyricSelectionTest {
    private val text = "[offset:+200]\n[00:01.000]Synthetic first\n[00:02.500]Synthetic second"

    @Test fun positiveOffsetsAdvanceAndNegativeOffsetsDelayWithoutChangingSourceOffset() {
        val original = requireNotNull(LyricsParser.parse(text))
        assertEquals(listOf(800L, 2300L), original.lines.map { it.timeMs })
        val earlier = requireNotNull(LyricsParser.parse(text, 300))
        assertEquals(listOf(500L, 2000L), earlier.lines.map { it.timeMs })
        assertEquals(listOf(1200L, 2700L), requireNotNull(LyricsParser.parse(text, -400)).lines.map { it.timeMs })
        assertEquals(0, earlier.activeLine(500))
        assertEquals(1, earlier.activeLine(2000))
        assertEquals(original, LyricsParser.parse(text, 0))
    }

    @Test fun repeatedPreviewAndResetAlwaysUseOriginalTextEvenAfterClampingAtZero() {
        val document = CachedLyric(text, LyricOrigin.QQ, offsetMs = 5000, selected = true)
        assertEquals(1, document.result().lyrics!!.lines.size)
        assertEquals(0L, document.result().lyrics!!.lines.first().timeMs)
        val restored = document.copy(offsetMs = 0)
        assertEquals(LyricsParser.parse(text), restored.result().lyrics)
        assertEquals(text, restored.text)
        assertEquals(LyricsParser.parse(text, -1000), restored.copy(offsetMs = -1000).result().lyrics)
    }

    @Test fun savedOffsetsSelectionsAndCandidateHistoryRoundTripForOnlineAndLocalCopies() {
        val history = LyricSearchHistory(listOf(LyricCandidateId(LyricSearchRoute.QQ, "fixture")))
            .including(requireNotNull(LyricsParser.parse(text)))
        for (origin in LyricOrigin.entries) {
            val value = CachedLyric(text, origin, 1000, -400, selected = true, history = history)
            val restored = LyricCacheFiles.decode(LyricCacheFiles.encode(value))
            assertEquals(value, restored)
            assertEquals(listOf(1200L, 2700L), restored.result().lyrics!!.lines.map { it.timeMs })
        }
    }

    @Test fun invalidSavedOffsetsAndUnboundedSelectionHistoryAreRejected() {
        for (offset in listOf(Long.MIN_VALUE, -600001, 600001, Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { LyricsParser.parse(text, offset) }
            assertThrows(IOException::class.java) { LyricCacheFiles.encode(CachedLyric(text, LyricOrigin.QQ, offsetMs = offset)) }
        }
        assertThrows(IOException::class.java) {
            LyricSearchHistory(contents = List(101) { "a".repeat(64) }).validate()
        }
        assertThrows(IOException::class.java) { LyricSearchHistory(contents = listOf("../invalid")).validate() }
        assertThrows(IOException::class.java) {
            LyricSearchHistory(candidates = listOf(LyricCandidateId(LyricSearchRoute.QQ, ""))).validate()
        }
    }

    @Test fun lyricFingerprintsIgnoreMetadataButKeepDifferentWordsAndTimingsDistinct() {
        fun hash(value: String) = LyricSearchHistory.fingerprint(requireNotNull(LyricsParser.parse(value)))
        assertEquals(hash(text), hash("[by:Another source]\n$text"))
        assertNotEquals(hash(text), hash(text.replace("first", "alternate")))
        assertNotEquals(hash(text), hash(text.replace("01.000", "01.100")))
    }
}
