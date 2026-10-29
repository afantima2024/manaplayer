package com.macarmusic.app

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.FileObserver
import android.os.Process
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.macarmusic.app.data.LibraryCodec
import com.macarmusic.app.data.MusicRepository
import com.macarmusic.app.model.Library
import com.macarmusic.app.model.LibraryEdits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ProgressiveScanTest {
    private val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val root = DocumentsContract.buildTreeDocumentUri("${app.packageName}.test.documents", "music")
    private val directory = File(app.cacheDir, "progressive-scan-${SystemClock.uptimeMillis()}")
    private val context = object : ContextWrapper(app) { override fun getFilesDir() = directory }
    private val errors = CopyOnWriteArrayList<String>()
    private lateinit var scope: CoroutineScope
    private lateinit var repository: MusicRepository

    @Before fun setup() = runBlocking {
        val token = "scan-${SystemClock.uptimeMillis()}"
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
            app.checkUriPermission(cache, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
        }
        await { app.contentResolver.call(cache, "cacheFixture", null, null)?.getString("grantToken") == token }
        available(true)
        configure()
        assertTrue(directory.mkdirs())
        restartRepository()
    }

    @After fun cleanup() = runBlocking {
        if (::scope.isInitialized) scope.coroutineContext[Job]!!.cancelAndJoin()
        available(true)
        configure()
        assertTrue(directory.deleteRecursively())
    }

    @Test fun thousandSongsAreDurableBeforeAnyTagReadAndDetailsUseBoundedBatches() = runBlocking(Dispatchers.IO) {
        configure(count = 1000, delayMs = 8)
        val source = repository.addSource(root, 0)
        val commits = AtomicInteger()
        val observer = observeCommits(commits)
        val started = SystemClock.elapsedRealtime()
        try {
            val result = repository.scan(source, null, deferMetadata = true) {}
            val readyMs = SystemClock.elapsedRealtime() - started
            assertEquals(1000, result.tracks.size)
            assertTrue(result.tracks.all { it.metadataPending && it.title.isNotBlank() })
            assertEquals("Discovery must not open audio files", 0, state().getInt("audioReads"))
            assertEquals(repository.library.value, saved())
            await { repository.library.value.tracks.none { it.metadataPending } }
            val allMs = SystemClock.elapsedRealtime() - started
            assertTrue("The library must be usable well before the final metadata batch", readyMs * 3 < allMs)
            assertTrue(repository.library.value.tracks.all { it.durationMs in 59_000..61_000 })
            assertEquals(1000, state().getInt("audioReads"))
            assertEquals(1, state().getInt("maxAudioReads"))
            await { commits.get() > 1 }
            assertTrue("Do not rewrite the library once per song: ${commits.get()}", commits.get() <= 40)
            assertEquals(repository.library.value, saved())
            assertTrue(errors.toString(), errors.isEmpty())
            instrumentation.sendStatus(0, Bundle().apply {
                putString("progressive_scan", "songs=1000; readyMs=$readyMs; detailsMs=$allMs; commits=${commits.get()}")
            })
            configure(count = 1000)
            val quick = repository.scan(source, null, deferMetadata = true) {}
            assertEquals(1000, quick.reusedMetadata)
            assertEquals(0, state().getInt("audioReads"))
        } finally { observer.stopWatching() }
    }

    @Test fun cancellationPreservesDiscoveredSongsAndRestartResumesWithoutWalkingFolders() = runBlocking(Dispatchers.IO) {
        configure(count = 130, delayMs = 20)
        val source = repository.addSource(root, 0)
        repository.scan(source, null, deferMetadata = true) {}
        await { repository.library.value.tracks.any { !it.metadataPending } }
        repository.cancelMetadata()
        await { repository.metadataProgress.value == null && state().getInt("activeAudioReads") == 0 }
        val before = saved()
        val remaining = before.tracks.count { it.metadataPending }
        assertTrue(remaining in 1..129)
        scope.coroutineContext[Job]!!.cancelAndJoin()
        configure(count = 130)
        restartRepository()
        assertEquals(before, repository.library.value)
        await { repository.library.value.tracks.none { it.metadataPending } }
        assertEquals(0, state().getInt("childReads"))
        assertEquals(remaining, state().getInt("audioReads"))
        assertEquals(repository.library.value, saved())
    }

    @Test fun removalDuringNativeReadDoesNotResurrectSongsOrIssueAnAccessError() = runBlocking(Dispatchers.IO) {
        configure(count = 100, delayMs = 300)
        val source = repository.addSource(root, 0)
        repository.scan(source, null, deferMetadata = true) {}
        await { state().getInt("activeAudioReads") == 1 }
        repository.update { LibraryEdits.removeSource(it, source.uri) }
        available(false)
        await { repository.metadataProgress.value == null }
        assertTrue(repository.library.value.tracks.isEmpty())
        assertTrue(saved().tracks.isEmpty())
        assertTrue(errors.toString(), errors.isEmpty())
        assertEquals(0, state().getInt("deleteRequests"))
    }

    @Test fun unplugPausesOnceRetainsReferencesAndRescanRetries() = runBlocking(Dispatchers.IO) {
        configure(count = 70, delayMs = 100)
        val source = repository.addSource(root, 0)
        repository.scan(source, null, deferMetadata = true) {}
        await { state().getInt("activeAudioReads") == 1 }
        available(false)
        await { repository.metadataProgress.value == null && errors.isNotEmpty() }
        val reads = state().getInt("audioReads")
        delay(400)
        assertEquals(reads, state().getInt("audioReads"))
        assertEquals(1, errors.size)
        assertEquals(70, saved().tracks.size)
        available(true)
        configure(count = 70)
        repository.scan(source, null, deferMetadata = true) {}
        await { repository.library.value.tracks.none { it.metadataPending } }
        assertEquals(70, state().getInt("audioReads"))
    }

    @Test fun discoveryCancellationDoesNotCommitAndFullRefreshAndLyricsStillReadCompleteTags() = runBlocking(Dispatchers.IO) {
        configure(count = 20)
        val source = repository.addSource(root, 0)
        val before = repository.library.value
        try {
            repository.scan(source, null, deferMetadata = true) {
                if (it.found > 0) throw CancellationException("Synthetic cancelled discovery")
            }
            fail("Expected discovery cancellation")
        } catch (_: CancellationException) {
            assertEquals(before, repository.library.value)
            assertEquals(before, saved())
        }
        val pending = repository.scan(source, null, deferMetadata = true) {}.tracks
        val detailed = repository.metadataForDetails(pending.last())
        assertFalse(detailed.track.metadataPending)
        assertTrue(detailed.track.durationMs > 0)
        val commits = AtomicInteger()
        val observer = observeCommits(commits)
        try {
            val prepared = repository.metadataForLyrics(pending) {}
            assertTrue(prepared.all { !it.metadataPending && it.durationMs > 0 })
            await { commits.get() > 0 }
            assertEquals("Bulk lyric preparation must not persist once per song", 1, commits.get())
        } finally { observer.stopWatching() }
        configure(count = 20)
        val full = repository.scan(source, null, forceMetadata = true, deferMetadata = true) {}
        assertEquals(20, state().getInt("audioReads"))
        assertEquals(0, full.reusedMetadata)
        assertTrue(full.tracks.all { !it.metadataPending && it.durationMs > 0 })
    }

    @Test fun enrichmentPreservesLiveFavoritesPlaylistsAndStandaloneReferences() = runBlocking(Dispatchers.IO) {
        configure(count = 70, delayMs = 10)
        val source = repository.addSource(root, 0)
        val tracks = repository.scan(source, null, deferMetadata = true) {}.tracks
        val selected = tracks.last()
        repository.update {
            LibraryEdits.addToPlaylist(LibraryEdits.createPlaylist(LibraryEdits.toggleFavorite(it, selected.id),
                "playlist", "Keep this"), "playlist", listOf(selected.id))
        }
        val standalone = repository.openDocument(android.net.Uri.parse(selected.uri), 0).track
        await { repository.library.value.tracks.none { it.metadataPending } }
        assertEquals(setOf(selected.id), saved().favorites)
        assertEquals(listOf(selected.id), saved().playlists.single().trackIds)
        val latest = saved().tracks.single { it.id == selected.id }
        assertEquals(standalone.standaloneUri, latest.standaloneUri)
        assertEquals(selected.sourceUris, latest.sourceUris)
        assertEquals(1, state().getInt("maxAudioReads"))
    }

    private suspend fun restartRepository() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = MusicRepository(context, scope, errors::add)
        repository.awaitReady()
    }

    private fun saved() = LibraryCodec.decodeLibrary(File(directory, "library.json").readText())
    private fun state() = app.contentResolver.call(root, "scanFixtureState", null, null)!!
    private fun configure(count: Int = 2, delayMs: Long = 0) {
        app.contentResolver.call(root, "scanFixtureState", null, Bundle().apply {
            putBoolean("resetReads", true)
            putInt("extraRows", count - 2)
            putLong("audioDelayMs", delayMs)
        })
    }
    private fun available(value: Boolean) {
        app.contentResolver.call(root, "setUnavailable", null, Bundle().apply { putBoolean("value", !value) })
    }
    private suspend fun await(test: () -> Boolean) = withTimeout(120_000) {
        while (!test()) delay(25)
    }
    @Suppress("DEPRECATION")
    private fun observeCommits(count: AtomicInteger): FileObserver =
        object : FileObserver(directory.path, CLOSE_WRITE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == "library.json" || path == "library.json.new") count.incrementAndGet()
            }
        }.also { it.startWatching() }
}
