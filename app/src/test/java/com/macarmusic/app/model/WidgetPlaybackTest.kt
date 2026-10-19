package com.macarmusic.app.model

import com.macarmusic.app.lyrics.LyricLine
import com.macarmusic.app.lyrics.Lyrics
import org.junit.Assert.*
import org.junit.Test

class WidgetPlaybackTest {
    private fun track(id: String) = Track(id, "content://music/$id", "$id.mp3", id, durationMs = 60_000)

    @Test fun restoredQueueNeverAutoplaysAndPreservesTheCurrentOccurrence() {
        val one = track("one")
        val two = track("two")
        val restored = WidgetPlayback.restored(PlaybackSnapshot(
            queue = listOf(one, two, one), index = 2, positionMs = 25_000), muted = true)
        assertEquals(one, restored.track)
        assertEquals(25_000L, restored.positionMs)
        assertEquals(60_000L, restored.durationMs)
        assertTrue(restored.muted)
        assertTrue(restored.previous)
        assertFalse(restored.next)
        assertFalse(restored.playing)
        assertFalse(restored.advancing)
    }

    @Test fun emptyAndOutOfRangeSavedQueuesAreSafe() {
        val empty = WidgetPlayback.restored(PlaybackSnapshot(index = 99, positionMs = -2), false)
        assertNull(empty.track)
        assertEquals(0L, empty.positionMs)
        assertFalse(empty.next)
        assertFalse(empty.previous)
        val single = WidgetPlayback.restored(PlaybackSnapshot(queue = listOf(track("one")),
            index = 99, positionMs = Long.MAX_VALUE, repeat = 2), false)
        assertEquals(59_999L, single.positionMs)
        assertTrue(single.next)
    }

    @Test fun progressIsBoundedForUnknownDurationsAndLongLimits() {
        assertEquals(0, WidgetPresentation.progress(500, 0))
        assertEquals(0, WidgetPresentation.progress(500, -1))
        assertEquals(0, WidgetPresentation.progress(-10, 60_000))
        assertEquals(250, WidgetPresentation.progress(15_000, 60_000))
        assertEquals(1_000, WidgetPresentation.progress(Long.MAX_VALUE, 60_000))
        assertEquals(1_000, WidgetPresentation.progress(Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(500, WidgetPresentation.progress(Long.MAX_VALUE / 2, Long.MAX_VALUE))
    }

    @Test fun lyricLineFollowsSeeksAndHandlesUntimedOrMissingLyrics() {
        val timed = Lyrics(listOf(LyricLine(1_000, "First"), LyricLine(5_000, "Second")))
        assertEquals("First\nSecond", WidgetPresentation.lyric(timed, 0))
        assertEquals("Second", WidgetPresentation.lyric(timed, 5_000))
        assertEquals("First\nSecond", WidgetPresentation.lyric(timed, 2_000))
        assertEquals("Second", WidgetPresentation.lyric(timed, Long.MAX_VALUE))
        assertEquals("One\nTwo", WidgetPresentation.lyric(
            Lyrics(listOf(LyricLine(null, "One"), LyricLine(null, "Two"), LyricLine(null, "Three"))), 0))
        assertNull(WidgetPresentation.lyric(null, 0))
        assertNull(WidgetPresentation.lyric(Lyrics(emptyList()), 0))
        assertEquals(300, WidgetPresentation.lyric(Lyrics(listOf(LyricLine(null, "a".repeat(500)))), 0)?.length)
    }

    @Test fun lyricPreviewKeepsTwoRowsForLongLinesAndSameTimeTranslations() {
        val long = Lyrics(listOf(LyricLine(0, "a".repeat(500)), LyricLine(5_000, "b".repeat(500))))
        val rows = WidgetPresentation.lyric(long, 1_000)!!.lines()
        assertEquals(listOf("a".repeat(149), "b".repeat(149)), rows)
        val translated = Lyrics(listOf(LyricLine(0, "First\nTranslation"), LyricLine(2_000, "Next")))
        assertEquals("First\nTranslation", WidgetPresentation.lyric(translated, 0))
        assertEquals("Next", WidgetPresentation.lyric(translated, 2_000))
    }

    @Test fun progressWorkRequiresAVisibleScreenActivePlaybackAndAProgressWidget() {
        assertTrue(WidgetPresentation.needsTicks(true, true, true))
        assertFalse(WidgetPresentation.needsTicks(false, true, true))
        assertFalse(WidgetPresentation.needsTicks(true, false, true))
        assertFalse(WidgetPresentation.needsTicks(true, true, false))
    }

    @Test fun backgroundTransparencyHasExactEndpointsAndRoundedMidpoint() {
        assertEquals(255, WidgetPresentation.backgroundAlpha(0))
        assertEquals(128, WidgetPresentation.backgroundAlpha(50))
        assertEquals(0, WidgetPresentation.backgroundAlpha(100))
        assertTrue((0..100).map(WidgetPresentation::backgroundAlpha).zipWithNext().all { (a, b) -> a >= b })
    }

    @Test(expected = IllegalArgumentException::class) fun negativeTransparencyIsRejected() {
        WidgetPresentation.backgroundAlpha(-1)
    }

    @Test(expected = IllegalArgumentException::class) fun excessiveTransparencyIsRejected() {
        WidgetPresentation.backgroundAlpha(101)
    }
}
