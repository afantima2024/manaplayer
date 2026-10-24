package com.macarmusic.app.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricKaraokeTest {
    private val lyrics = LyricsParser.parse("[00:02]First\n[00:06]Second\n[00:10]Last")!!

    @Test fun coverageFollowsCurrentTimestampsAndClampsAtBothEnds() {
        assertEquals(6_000L, LyricKaraoke.endMs(lyrics, 0, 20_000))
        for ((position, expected) in listOf(0L to 0f, 2_000L to 0f, 3_000L to 0.25f,
            4_000L to 0.5f, 6_000L to 1f, Long.MAX_VALUE to 1f)) {
            assertEquals(expected, LyricKaraoke.fraction(2_000, 6_000, position), 0f)
        }
        assertEquals(0f, LyricKaraoke.fraction(null, 6_000, 4_000), 0f)
        assertEquals(0f, LyricKaraoke.fraction(2_000, null, 4_000), 0f)
        assertEquals(0f, LyricKaraoke.fraction(2_000, 2_000, 4_000), 0f)
        assertEquals(0f, LyricKaraoke.fraction(2_000, 1_000, 4_000), 0f)
        assertEquals(0f, LyricKaraoke.fraction(-1, 1_000, 500), 0f)
    }

    @Test fun finalLineNeedsKnownDurationAndNeverExtendsPastTrackEnd() {
        assertEquals(20_000L, LyricKaraoke.endMs(lyrics, 2, 20_000))
        assertEquals(0.5f, LyricKaraoke.fraction(10_000, LyricKaraoke.endMs(lyrics, 2, 20_000), 15_000), 0f)
        assertNull(LyricKaraoke.endMs(lyrics, 2, 0))
        assertNull(LyricKaraoke.endMs(lyrics, 2, -1))
        assertNull(LyricKaraoke.endMs(lyrics, 2, 8_000))
        assertEquals(5_000L, LyricKaraoke.endMs(lyrics, 0, 5_000))
        assertEquals(6_000L, LyricKaraoke.endMs(lyrics, 0, 0))
    }

    @Test fun preludeUntimedAndUnavailableLinesDoNotAcquireInventedTiming() {
        assertNull(LyricKaraoke.endMs(lyrics, -1, 20_000))
        assertNull(LyricKaraoke.endMs(lyrics, 3, 20_000))
        assertNull(LyricKaraoke.endMs(null, 0, 20_000))
        assertNull(LyricKaraoke.endMs(LyricsParser.parse("Plain lyrics"), 0, 20_000))
    }

    @Test fun blankBoundariesTranslationsAndSavedOffsetsKeepTheirTiming() {
        val value = LyricsParser.parse("[offset:500]\n[00:02]First\n[00:02]Translation\n[00:06]\n[00:10]Last", 500)!!
        assertEquals("First\nTranslation", value.lines[0].text)
        assertEquals(5_000L, LyricKaraoke.endMs(value, 0, 20_000))
        assertEquals(0.5f, LyricKaraoke.fraction(value.lines[0].timeMs, LyricKaraoke.endMs(value, 0, 20_000), 3_000), 0f)
        assertEquals("", value.lines[1].text)
        assertEquals(9_000L, LyricKaraoke.endMs(value, 1, 20_000))
    }

    @Test fun sampledClockTracksSpeedPausesAndBackwardSeeksWithoutAccumulatingDrift() {
        val running = LyricPlayback(3_000, 20_000, true, 1.5f, 10_000)
        assertEquals(3_750L, running.positionAt(10_500))
        assertEquals(3_000L, running.positionAt(9_000))
        val paused = running.copy(positionMs = running.positionAt(10_500), advancing = false)
        assertEquals(3_750L, paused.positionAt(99_999))
        val sought = running.copy(positionMs = 1_000, sampledAtMs = 11_000)
        assertEquals(1_150L, sought.positionAt(11_100))
        assertEquals(Long.MAX_VALUE, running.copy(positionMs = Long.MAX_VALUE).positionAt(20_000))
        for (speed in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { LyricPlayback(speed = speed) }
        }
    }

    @Test fun widgetPrefixesNeverSplitSurrogatePairsAndRespectUnicodeCharacters() {
        val text = "\u6b4c\uD83C\uDFB5\u8bcd"
        assertEquals(0, LyricKaraoke.prefixLength(text, -1f))
        assertEquals(1, LyricKaraoke.prefixLength(text, 0.34f))
        assertEquals(3, LyricKaraoke.prefixLength(text, 0.67f))
        assertEquals(4, LyricKaraoke.prefixLength(text, 1f))
        assertEquals(4, LyricKaraoke.prefixLength(text, 2f))
        assertEquals(0, LyricKaraoke.prefixLength("", 0.5f))
        assertThrows(IllegalArgumentException::class.java) { LyricKaraoke.prefixLength(text, Float.NaN) }
    }
}
