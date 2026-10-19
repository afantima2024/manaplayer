package com.macarmusic.app.data

import com.macarmusic.app.model.PlaybackSnapshot
import com.macarmusic.app.model.Track
import com.macarmusic.app.model.PlaybackCollection
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PlaybackStateStoreTest {
    private val first = Track("a", "content://local/a", "a.mp3", "First", durationMs = 60_000)
    private val second = first.copy(id = "b", uri = "content://local/b", title = "Second")

    private class MemoryStore(var text: String? = null) : TextStore {
        var writes = 0
        var failWrite = false
        var failRead = false
        override fun read(): String? {
            if (failRead) throw IOException("Synthetic read failure")
            return text
        }
        override fun write(text: String) {
            if (failWrite) throw IOException("Synthetic write failure")
            this.text = text
            writes++
        }
    }

    @Test fun legacyQueueLoadsWithoutAnEagerMigrationWrite() {
        val snapshot = PlaybackSnapshot(queue = listOf(first, second, first), index = 2, positionMs = 12_000)
        val queue = MemoryStore(LibraryCodec.encode(snapshot))
        val position = MemoryStore()
        assertEquals(snapshot, PlaybackStateStore(queue, position).read().snapshot)
        assertEquals(0, queue.writes + position.writes)
        assertNull(position.text)
    }

    @Test fun positionOnlyChangesNeverRewriteQueueMetadata() {
        val queue = MemoryStore()
        val position = MemoryStore()
        val store = PlaybackStateStore(queue, position)
        val snapshot = PlaybackSnapshot(queue = List(2000) { first.copy(id = "$it") })
        store.write(snapshot)
        val original = queue.text
        repeat(20) { store.write(snapshot.copy(positionMs = (it + 1) * 1000L)) }
        assertEquals(1, queue.writes)
        assertEquals(21, position.writes)
        assertEquals(original, queue.text)
        assertTrue(position.text!!.toByteArray().size < 256)
        assertEquals(20_000L, PlaybackStateStore(queue, position).read().snapshot.positionMs)
    }

    @Test fun metadataOrderAndRepeatedOccurrencesArePersistedWhenTheyChange() {
        val queue = MemoryStore()
        val position = MemoryStore()
        val store = PlaybackStateStore(queue, position)
        val snapshot = PlaybackSnapshot(queue = listOf(first, second, first), index = 2, positionMs = 9000)
        store.write(snapshot)
        val edited = snapshot.copy(queue = listOf(second, first.copy(title = "Updated"), second), index = 1)
        store.write(edited)
        assertEquals(2, queue.writes)
        assertEquals(edited, PlaybackStateStore(queue, position).read().snapshot)
    }

    @Test fun equivalentRecreatedQueuesUseOnlyPositionWrites() {
        val queue = MemoryStore()
        val position = MemoryStore()
        val store = PlaybackStateStore(queue, position)
        val original = PlaybackSnapshot(queue = listOf(first, second, first))
        store.write(original)
        val recreated = original.copy(queue = original.queue.map { it.copy() }, positionMs = 5000)
        assertNotSame(original.queue, recreated.queue)
        store.write(recreated)
        assertEquals(1, queue.writes)
        assertEquals(2, position.writes)
        assertEquals(recreated, PlaybackStateStore(queue, position).read().snapshot)
    }

    @Test fun shuffleRepeatAndIndexUseTheSmallCheckpoint() {
        val queue = MemoryStore()
        val position = MemoryStore()
        val store = PlaybackStateStore(queue, position)
        val snapshot = PlaybackSnapshot(queue = listOf(first, second, first))
        store.write(snapshot)
        val edited = snapshot.copy(index = 2, positionMs = 15_000, shuffle = true, repeat = 2)
        store.write(edited)
        assertEquals(1, queue.writes)
        assertEquals(edited, PlaybackStateStore(queue, position).read().snapshot)
    }

    @Test fun interruptedQueueReplacementUsesItsOwnEmbeddedRecoveryPosition() {
        val queue = MemoryStore()
        val position = MemoryStore()
        val store = PlaybackStateStore(queue, position)
        store.write(PlaybackSnapshot(queue = listOf(first, second), index = 1, positionMs = 20_000))
        val replacement = PlaybackSnapshot(queue = listOf(first), positionMs = 7000)
        position.failWrite = true
        try { store.write(replacement); fail("Expected interrupted checkpoint write") } catch (_: IOException) { }
        val recovered = PlaybackStateStore(queue, position).read()
        assertEquals(replacement, recovered.snapshot)
        assertTrue(recovered.ignoredPosition)
        assertNull(recovered.positionError)
    }

    @Test fun failedQueueCommitDoesNotWriteAnIncompatiblePosition() {
        val queue = MemoryStore()
        val position = MemoryStore()
        val store = PlaybackStateStore(queue, position)
        val original = PlaybackSnapshot(queue = listOf(first), positionMs = 6000)
        store.write(original)
        queue.failWrite = true
        try {
            store.write(original.copy(queue = listOf(second), positionMs = 18_000))
            fail("Expected queue write failure")
        } catch (_: IOException) { }
        assertEquals(1, position.writes)
        assertEquals(original, PlaybackStateStore(queue, position).read().snapshot)
    }

    @Test fun missingPositionRecoversTheQueueCommit() {
        val queue = MemoryStore()
        val position = MemoryStore()
        val original = PlaybackSnapshot(queue = listOf(first), positionMs = 6000)
        PlaybackStateStore(queue, position).write(original)
        position.text = null
        assertEquals(original, PlaybackStateStore(queue, position).read().snapshot)
    }

    @Test fun corruptPositionIsReportedWhileRetainingBothFilesAndQueue() {
        val queue = MemoryStore()
        val position = MemoryStore()
        val original = PlaybackSnapshot(queue = listOf(first), positionMs = 6000)
        PlaybackStateStore(queue, position).write(original)
        position.text = "{broken"
        val queueText = queue.text
        val loaded = PlaybackStateStore(queue, position).read()
        assertEquals(original, loaded.snapshot)
        assertNotNull(loaded.positionError)
        assertEquals("{broken", position.text)
        assertEquals(queueText, queue.text)
    }

    @Test fun unreadablePositionIsReportedWithoutDiscardingQueue() {
        val queue = MemoryStore()
        val position = MemoryStore()
        val original = PlaybackSnapshot(queue = listOf(first), positionMs = 6000)
        PlaybackStateStore(queue, position).write(original)
        position.failRead = true
        assertEquals(original, PlaybackStateStore(queue, position).read().snapshot)
        assertTrue(PlaybackStateStore(queue, position).read().positionError is IOException)
    }

    @Test fun legacyWriterCannotAccidentallyInheritANewerCheckpoint() {
        val queue = MemoryStore()
        val position = MemoryStore()
        val store = PlaybackStateStore(queue, position)
        store.write(PlaybackSnapshot(queue = listOf(first, second), index = 1, positionMs = 20_000))
        val legacy = PlaybackSnapshot(queue = listOf(second), positionMs = 2000)
        queue.text = LibraryCodec.encode(legacy)
        val migrated = PlaybackStateStore(queue, position)
        assertEquals(legacy, migrated.read().snapshot)
        migrated.write(legacy.copy(positionMs = 4000))
        assertEquals(4000L, PlaybackStateStore(queue, position).read().snapshot.positionMs)
        assertEquals(legacy.copy(positionMs = 4000), LibraryCodec.decodePlayback(queue.text!!))
    }

    @Test fun emptyQueueDoesNotInheritTheLastSongsPosition() {
        val queue = MemoryStore()
        val position = MemoryStore()
        val store = PlaybackStateStore(queue, position)
        store.write(PlaybackSnapshot(queue = listOf(first), positionMs = 20_000))
        store.write(PlaybackSnapshot())
        assertEquals(PlaybackSnapshot(), PlaybackStateStore(queue, position).read().snapshot)
    }

    @Test(expected = IOException::class) fun missingQueueWithAPositionIsNotTreatedAsANewInstall() {
        PlaybackStateStore(MemoryStore(), MemoryStore("{}")).read()
    }

    @Test fun newInstallHasNoFilesOrWrites() {
        val queue = MemoryStore()
        val position = MemoryStore()
        assertEquals(PlaybackSnapshot(), PlaybackStateStore(queue, position).read().snapshot)
        assertEquals(0, queue.writes + position.writes)
    }

    @Test fun collectionChangesAreCommittedEvenWhenTheSongsAreIdentical() {
        val queue = MemoryStore()
        val position = MemoryStore()
        val store = PlaybackStateStore(queue, position)
        val firstList = PlaybackSnapshot(queue = listOf(first), positionMs = 4000, collection = PlaybackCollection.playlist("first"))
        store.write(firstList)
        val secondList = firstList.copy(collection = PlaybackCollection.playlist("second"), positionMs = 15000)
        store.write(secondList)
        assertEquals(2, queue.writes)
        assertEquals(secondList, PlaybackStateStore(queue, position).read().snapshot)
        repeat(5) { store.write(secondList.copy(positionMs = 16000L + it)) }
        assertEquals(2, queue.writes)
        assertTrue(position.text!!.length < 256)
    }

    @Test fun legacySnapshotsHaveNoCollectionAndDoNotTriggerMigrationWrites() {
        val queue = MemoryStore("""{"version":1,"queue":[],"index":0,"positionMs":0}""")
        val position = MemoryStore()
        assertNull(PlaybackStateStore(queue, position).read().snapshot.collection)
        assertEquals(0, queue.writes + position.writes)
    }
}
