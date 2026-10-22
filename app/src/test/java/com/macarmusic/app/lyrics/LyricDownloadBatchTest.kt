package com.macarmusic.app.lyrics

import com.macarmusic.app.model.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LyricDownloadBatchTest {
    private fun track(id: Int) = Track("$id", "content://fixture/$id", "$id.mp3", "Synthetic $id")

    @Test fun duplicateQueueEntriesAreProcessedOnceInOrderWithAccurateMonotonicProgress() = runTest {
        val calls = mutableListOf<String>()
        val updates = mutableListOf<LyricDownloadState>()
        var active = 0
        var peak = 0
        val result = LyricDownloadBatch.run(listOf(track(1), track(2), track(1), track(3)), LyricDownloadState("Queue"), {
            calls.add(it.id)
            active++
            peak = maxOf(peak, active)
            delay(10)
            active--
            when (it.id) {
                "1" -> LyricDownloadResult.SAVED
                "2" -> LyricDownloadResult.KEPT
                else -> LyricDownloadResult.NO_MATCH
            }
        }, updates::add)
        assertEquals(listOf("1", "2", "3"), calls)
        assertEquals(1, peak)
        assertEquals(3, result.total)
        assertEquals(3, result.completed)
        assertEquals(1, result.saved)
        assertEquals(1, result.kept)
        assertEquals(1, result.unmatched)
        assertEquals(0, result.failed)
        assertEquals(LyricDownloadPhase.COMPLETE, result.phase)
        assertTrue(updates.zipWithNext().all { (before, after) -> after.completed >= before.completed })
        assertTrue(updates.all { it.completed == it.saved + it.kept + it.unmatched + it.failed })
    }

    @Test fun threeConsecutiveFailuresStopInsteadOfRetryingAnEntireOfflineCollection() = runTest {
        val results = listOf(LyricDownloadResult.SEARCH_FAILED, LyricDownloadResult.KEPT,
            LyricDownloadResult.SEARCH_FAILED, LyricDownloadResult.SEARCH_FAILED, LyricDownloadResult.SEARCH_FAILED)
        var calls = 0
        val result = LyricDownloadBatch.run((1..1000).map(::track), LyricDownloadState("Folder"),
            { results[calls++] }, {})
        assertEquals(4, calls)
        assertEquals(4, result.completed)
        assertEquals(3, result.failed)
        assertEquals(1, result.kept)
        assertEquals(LyricDownloadPhase.FAILED, result.phase)
    }

    @Test fun successfulSearchesResetTheFailureLimitButLocalCacheHitsDoNot() = runTest {
        for (recovery in listOf(LyricDownloadResult.SAVED, LyricDownloadResult.NO_MATCH, LyricDownloadResult.PARTIAL_FAILURE)) {
            val results = listOf(LyricDownloadResult.SEARCH_FAILED, LyricDownloadResult.SEARCH_FAILED,
                recovery, LyricDownloadResult.SEARCH_FAILED, LyricDownloadResult.SEARCH_FAILED)
            var calls = 0
            val result = LyricDownloadBatch.run(results.indices.map(::track), LyricDownloadState("Queue"),
                { results[calls++] }, {})
            assertEquals(5, calls)
            assertEquals(if (recovery == LyricDownloadResult.PARTIAL_FAILURE) 5 else 4, result.failed)
            assertEquals(LyricDownloadPhase.COMPLETE, result.phase)
        }
    }
    @Test fun cancellationStopsTheInFlightRequestAndDoesNotStartAnotherSong() = runTest {
        val entered = CompletableDeferred<Unit>()
        val waiting = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        var state = LyricDownloadState("Queue")
        val job = launch {
            LyricDownloadBatch.run((1..3).map(::track), state, {
                calls.add(it.id)
                if (it.id == "2") { entered.complete(Unit); waiting.await() }
                LyricDownloadResult.SAVED
            }, { state = it })
        }
        entered.await()
        job.cancelAndJoin()
        assertEquals(listOf("1", "2"), calls)
        assertEquals(1, state.saved)
        assertEquals(1, state.completed)
        assertEquals(0, state.failed)
        assertTrue(job.isCancelled)
    }

    @Test fun storageFailuresPropagateInsteadOfCountingUnsavedLyricsAsSuccess() = runTest {
        var calls = 0
        var state = LyricDownloadState("Playlist")
        try {
            LyricDownloadBatch.run((1..3).map(::track), state, {
                calls++
                if (calls == 2) throw IOException("Synthetic unplugged cache")
                LyricDownloadResult.SAVED
            }, { state = it })
            fail("Storage error was hidden")
        } catch (error: IOException) {
            assertEquals("Synthetic unplugged cache", error.message)
        }
        assertEquals(2, calls)
        assertEquals(1, state.completed)
        assertEquals(1, state.saved)
    }

    @Test fun emptyCollectionsFinishWithoutNetworkCallsOrInventedProgress() = runTest {
        val result = LyricDownloadBatch.run(emptyList(), LyricDownloadState("Empty"),
            { error("Empty collection downloaded a lyric") }, {})
        assertEquals(LyricDownloadPhase.COMPLETE, result.phase)
        assertEquals(0, result.total)
        assertEquals(0, result.completed)
        assertFalse(result.running)
    }
}
