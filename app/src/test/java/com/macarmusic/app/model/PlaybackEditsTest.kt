package com.macarmusic.app.model

import org.junit.Assert.*
import org.junit.Test

class PlaybackEditsTest {
    private val tracks = (0..2).map { Track("$it", "content://local/$it", "$it.mp3", "$it", durationMs = 10_000) }

    @Test fun relativeSeekStaysInsideTheCurrentTrack() {
        assertEquals(25_000L, PlaybackEdits.seekPosition(15_000, 60_000, 10_000))
        assertEquals(5_000L, PlaybackEdits.seekPosition(15_000, 60_000, -10_000))
        assertEquals(0L, PlaybackEdits.seekPosition(100, 60_000, -10_000))
        assertEquals(59_999L, PlaybackEdits.seekPosition(55_000, 60_000, 10_000))
        assertEquals(0L, PlaybackEdits.seekPosition(0, 1, 10_000))
    }

    @Test fun relativeSeekRejectsUnknownDurationAndAvoidsOverflow() {
        assertNull(PlaybackEdits.seekPosition(100, 0, 10_000))
        assertNull(PlaybackEdits.seekPosition(100, Long.MIN_VALUE + 1, 10_000))
        assertEquals(Long.MAX_VALUE - 1, PlaybackEdits.seekPosition(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(0L, PlaybackEdits.seekPosition(Long.MAX_VALUE, Long.MAX_VALUE, Long.MIN_VALUE))
    }

    @Test fun outOfRangeRestoreIsClamped() {
        val result = PlaybackEdits.normalized(PlaybackSnapshot(queue = tracks, index = 8, positionMs = 99_000, repeat = 91))
        assertEquals(2, result.index)
        assertEquals(9_999, result.positionMs)
        assertEquals(0, result.repeat)
    }

    @Test fun negativeValuesAndEmptyQueueAreNormalized() {
        val empty = PlaybackEdits.normalized(PlaybackSnapshot(index = -3, positionMs = -1))
        assertEquals(0, empty.index)
        assertEquals(0, empty.positionMs)
        val tracks = PlaybackEdits.normalized(PlaybackSnapshot(queue = tracks, index = -2, positionMs = -1))
        assertEquals(0, tracks.index)
        assertEquals(0, tracks.positionMs)
    }

    @Test fun unknownDurationKeepsSavedPosition() {
        val result = PlaybackEdits.normalized(PlaybackSnapshot(queue = listOf(tracks[0].copy(durationMs = 0)), positionMs = 45_000))
        assertEquals(45_000, result.positionMs)
    }

    @Test fun removingEarlierTrackPreservesCurrentIdentityAndPosition() {
        val result = PlaybackEdits.remove(PlaybackSnapshot(queue = tracks, index = 1, positionMs = 6_000), 0)
        assertEquals(0, result.index)
        assertEquals("1", result.queue[result.index].id)
        assertEquals(6_000, result.positionMs)
    }

    @Test fun removingCurrentTrackResetsPosition() {
        val result = PlaybackEdits.remove(PlaybackSnapshot(queue = tracks, index = 1, positionMs = 6_000), 1)
        assertEquals("2", result.queue[result.index].id)
        assertEquals(0, result.positionMs)
    }

    @Test fun removingLastAndOnlyTrackLeavesValidState() {
        val result = PlaybackEdits.remove(PlaybackSnapshot(queue = listOf(tracks[0]), positionMs = 6_000), 0)
        assertTrue(result.queue.isEmpty())
        assertEquals(0, result.index)
        assertEquals(0, result.positionMs)
    }

    @Test fun repeatAndShuffleSurviveNormalization() {
        for (repeat in 0..2) {
            val snapshot = PlaybackSnapshot(queue = tracks, repeat = repeat, shuffle = true)
            assertEquals(snapshot, PlaybackEdits.normalized(snapshot))
        }
    }

    @Test(expected = IllegalArgumentException::class) fun invalidQueueRemovalFailsExplicitly() {
        PlaybackEdits.remove(PlaybackSnapshot(queue = tracks), 3)
    }

    @Test fun removedLibraryReferencesAreNotRestoredIntoTheQueue() {
        val original = PlaybackSnapshot(queue = tracks, index = 1, positionMs = 6_000)
        val remaining = listOf(tracks[1].copy(uri = "content://new-grant/1"), tracks[2])
        val result = PlaybackEdits.reconcile(original, remaining)
        assertEquals(2, result.queue.size)
        assertEquals(0, result.index)
        assertEquals("content://new-grant/1", result.queue[0].uri)
        assertEquals(6_000, result.positionMs)
    }

    @Test fun reconciliationHandlesAllTracksRemoved() {
        val result = PlaybackEdits.reconcile(PlaybackSnapshot(queue = tracks, index = 2), emptyList())
        assertTrue(result.queue.isEmpty())
        assertEquals(0, result.index)
    }
}
