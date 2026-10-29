package com.macarmusic.app

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.macarmusic.app.model.Track
import com.macarmusic.app.model.CollectionKind
import com.macarmusic.app.model.PlaybackCollection
import com.macarmusic.app.playback.reconcileLibraryQueue
import com.macarmusic.app.playback.toMediaItem
import com.macarmusic.app.playback.collection
import com.macarmusic.app.playback.withCollection
import com.macarmusic.app.playback.withLibraryMetadata
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class LibraryQueueTest {
    private val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val root = DocumentsContract.buildTreeDocumentUri("${app.packageName}.test.documents", "music")
    private val audio = DocumentsContract.buildDocumentUriUsingTree(root, "music/one").toString()

    @Before fun grantFixture() {
        val token = "queue-${SystemClock.uptimeMillis()}"
        instrumentation.context.startActivity(Intent().apply {
            component = ComponentName("${app.packageName}.test", FixtureGrantActivity::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("cacheGrantToken", token)
        })
        await {
            app.checkUriPermission(root, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
        }
        val cache = DocumentsContract.buildTreeDocumentUri("${app.packageName}.test.cache", "root")
        await {
            app.contentResolver.call(cache, "cacheFixture", null, null)?.getString("grantToken") == token
        }
    }

    @Test fun removingTenThousandEntriesUsesOneQueueMutation() = withPlayer { player, counted ->
        val tracks = tracks(10_000)
        onMain {
            player.setMediaItems(tracks.map(Track::toMediaItem), 5000, 20_000)
            player.playWhenReady = true
            counted.reconcileLibraryQueue(tracks.associateBy(Track::id), emptyMap())
            assertEquals(1, counted.mutations)
            assertEquals(0, player.mediaItemCount)
            assertFalse(player.playWhenReady)
            assertEquals(0L, player.currentPosition)
        }
    }

    @Test fun aMenuOpenedBeforeTagCompletionQueuesTheLatestMetadataWithoutLosingItsCollection() {
        val pending = tracks(1).single().copy(title = "Filename", durationMs = 0, metadataPending = true)
        val completed = pending.copy(title = "Tagged title", artist = "Tagged artist", durationMs = 60_000,
            metadataPending = false)
        val collection = PlaybackCollection(CollectionKind.FOLDER, root.toString(), "Album")
        val item = pending.toMediaItem().withCollection(collection)
        val resolved = listOf(item).withLibraryMetadata(mapOf(completed.id to completed)).single()
        assertEquals("Tagged title", resolved.mediaMetadata.title)
        assertEquals("Tagged artist", resolved.mediaMetadata.artist)
        assertEquals(60_000L, resolved.mediaMetadata.durationMs)
        assertEquals(collection, resolved.collection())
        assertEquals(item.localConfiguration, resolved.localConfiguration)
    }

    @Test fun currentMetadataAndUnknownOrDifferentUriRequestsRemainUnchanged() {
        val track = tracks(1).single()
        val item = track.toMediaItem()
        assertSame(item, listOf(item).withLibraryMetadata(mapOf(track.id to track)).single())
        assertSame(item, listOf(item).withLibraryMetadata(emptyMap()).single())
        assertSame(item, listOf(item).withLibraryMetadata(mapOf(track.id to track.copy(uri = "$audio?new"))).single())
    }

    @Test fun interleavedRemovalPreservesPlayingDuplicateOccurrenceAndBuffer() = withPlayer { player, counted ->
        val tracks = tracks(1000).toMutableList().apply {
            this[101] = this[501]
            this[701] = this[501]
        }
        val known = tracks.filterIndexed { index, _ -> index % 2 == 1 }.associateBy(Track::id)
        onMain {
            player.setMediaItems(tracks.map(Track::toMediaItem), 501, 20_000)
            player.shuffleModeEnabled = true
            player.repeatMode = Player.REPEAT_MODE_ALL
            player.prepare()
            player.play()
        }
        await { player.isPlaying }
        var currentLoads = 0
        onMain {
            val period = player.currentTimeline.getUidOfPeriod(player.currentPeriodIndex)
            player.addAnalyticsListener(object : AnalyticsListener {
                override fun onLoadStarted(eventTime: AnalyticsListener.EventTime,
                    loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
                    if (eventTime.mediaPeriodId?.periodUid == period) currentLoads++
                }
            })
            val position = player.currentPosition
            counted.reconcileLibraryQueue(tracks.associateBy(Track::id), known)
            assertEquals(2, counted.mutations)
            assertEquals(500, player.mediaItemCount)
            assertEquals(250, player.currentMediaItemIndex)
            assertEquals("queue-501", player.currentMediaItem!!.mediaId)
            assertEquals(period, player.currentTimeline.getUidOfPeriod(player.currentPeriodIndex))
            assertTrue(player.currentPosition in position..position + 500)
            assertTrue(player.isPlaying)
            assertTrue(player.shuffleModeEnabled)
            assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)
            assertEquals(tracks.filterIndexed { index, _ -> index % 2 == 1 }.map(Track::id),
                (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId })
        }
        SystemClock.sleep(250)
        onMain {
            assertTrue(player.isPlaying)
            assertEquals("Kept current audio must not be reopened", 0, currentLoads)
        }
    }

    @Test fun pausedCurrentTrackSurvivesPrefixAndSuffixRemoval() = withPlayer { player, counted ->
        val tracks = tracks(1000)
        onMain {
            player.setMediaItems(tracks.map(Track::toMediaItem), 500, 17_000)
            counted.reconcileLibraryQueue(tracks.associateBy(Track::id),
                tracks.filter { it.id in setOf("queue-100", "queue-500", "queue-900") }.associateBy(Track::id))
            assertEquals(2, counted.mutations)
            assertEquals(3, player.mediaItemCount)
            assertEquals(1, player.currentMediaItemIndex)
            assertEquals(17_000L, player.currentPosition)
            assertEquals(Player.STATE_IDLE, player.playbackState)
            assertFalse(player.playWhenReady)
        }
    }

    @Test fun removedCurrentTrackSelectsNextSurvivorWithoutAutoplay() = withPlayer { player, counted ->
        val tracks = tracks(1000)
        onMain {
            player.setMediaItems(tracks.map(Track::toMediaItem), 500, 17_000)
            player.playWhenReady = true
            counted.reconcileLibraryQueue(tracks.associateBy(Track::id),
                tracks.filter { it.id in setOf("queue-100", "queue-900") }.associateBy(Track::id))
            assertEquals(1, counted.mutations)
            assertEquals(2, player.mediaItemCount)
            assertEquals("queue-900", player.currentMediaItem!!.mediaId)
            assertEquals(1, player.currentMediaItemIndex)
            assertEquals(0L, player.currentPosition)
            assertFalse(player.playWhenReady)
        }
    }

    @Test fun changedMetadataAndGrantAreBatchedWithoutDroppingNewQueueItems() = withPlayer { player, counted ->
        val tracks = tracks(1000)
        val fresh = tracks(1).single().copy(id = "new-request")
        val known = tracks.associate { track ->
            track.id to track.copy(title = "New ${track.title}",
                uri = if (track.id == "queue-500") audio else "$audio?renewed=1")
        }
        onMain {
            player.setMediaItems((tracks + fresh).map(Track::toMediaItem), 500, 12_000)
            val period = player.currentTimeline.getUidOfPeriod(player.currentPeriodIndex)
            counted.reconcileLibraryQueue(tracks.associateBy(Track::id), known)
            assertEquals(3, counted.mutations)
            assertEquals(1001, player.mediaItemCount)
            assertEquals(500, player.currentMediaItemIndex)
            assertEquals(period, player.currentTimeline.getUidOfPeriod(player.currentPeriodIndex))
            assertEquals("New Track 500", player.currentMediaItem!!.mediaMetadata.title)
            assertEquals(Uri.parse("$audio?renewed=1"), player.getMediaItemAt(999).localConfiguration!!.uri)
            assertEquals("new-request", player.getMediaItemAt(1000).mediaId)
            assertEquals(12_000L, player.currentPosition)
            assertFalse(player.playWhenReady)
        }
    }

    @Test fun unchangedReferencesAndNonMediaMetadataDoNotMutateThePlayer() = withPlayer { player, counted ->
        val tracks = tracks(1000)
        val previous = tracks.associateBy(Track::id)
        onMain {
            player.setMediaItems(tracks.map(Track::toMediaItem), 500, 12_000)
            counted.reconcileLibraryQueue(previous, previous)
            counted.reconcileLibraryQueue(previous, previous.mapValues { (_, track) ->
                track.copy(sourceUris = mapOf("content://kept-folder" to track.uri))
            })
            assertEquals(0, counted.mutations)
            assertEquals(12_000L, player.currentPosition)
        }
    }

    private fun tracks(count: Int) = List(count) { index ->
        Track("queue-$index", audio, "$index.wav", "Track $index", durationMs = 60_000)
    }

    private fun withPlayer(test: (ExoPlayer, CountingPlayer) -> Unit) {
        lateinit var player: ExoPlayer
        onMain { player = ExoPlayer.Builder(app).setUsePlatformDiagnostics(false).build() }
        try { test(player, CountingPlayer(player)) } finally { onMain { player.release() } }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            onMain { ready = condition() }
            if (ready) return
            SystemClock.sleep(25)
        }
        fail("Player did not reach the expected state")
    }

    private fun onMain(action: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private class CountingPlayer(player: Player) : ForwardingPlayer(player) {
        var mutations = 0
        override fun clearMediaItems() { mutations++; super.clearMediaItems() }
        override fun setMediaItems(items: List<MediaItem>, index: Int, positionMs: Long) {
            mutations++
            super.setMediaItems(items, index, positionMs)
        }
        override fun replaceMediaItems(fromIndex: Int, toIndex: Int, items: List<MediaItem>) {
            mutations++
            super.replaceMediaItems(fromIndex, toIndex, items)
        }
        override fun replaceMediaItem(index: Int, item: MediaItem) {
            mutations++
            super.replaceMediaItem(index, item)
        }
    }
}
