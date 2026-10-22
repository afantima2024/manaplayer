package com.macarmusic.app

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.macarmusic.app.lyrics.CachedLyric
import com.macarmusic.app.lyrics.InternalLyricCache
import com.macarmusic.app.lyrics.LyricCache
import com.macarmusic.app.lyrics.LyricCacheFiles
import com.macarmusic.app.lyrics.LyricOrigin
import com.macarmusic.app.lyrics.LyricIssue
import com.macarmusic.app.lyrics.LyricRemovalFailure
import com.macarmusic.app.lyrics.LyricDownloadResult
import com.macarmusic.app.lyrics.LyricQuery
import com.macarmusic.app.lyrics.LocalLyricsLoader
import com.macarmusic.app.lyrics.LyricsRepository
import com.macarmusic.app.lyrics.OnlineLyricsClient
import com.macarmusic.app.lyrics.SafLyricCache
import com.macarmusic.app.model.Library
import com.macarmusic.app.model.LibraryEdits
import com.macarmusic.app.model.Track
import com.macarmusic.app.model.LyricCacheExpiry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.Locale

internal fun syntheticOnlineLyrics(first: String): String = first + "\n" +
    (2..8).joinToString("\n") { "[00:${it * 7}]Synthetic fixture marker $it" }

@RunWith(AndroidJUnit4::class)
class LyricCacheIntegrationTest {
    private val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = object : ContextWrapper(app) {
        override fun getCacheDir() = File(app.cacheDir, "lyric-cache-tests")
        override fun getFilesDir() = File(app.filesDir, "lyric-cache-tests")
        override fun getSharedPreferences(name: String, mode: Int) =
            app.getSharedPreferences("lyric-cache-tests-$name", mode)
    }
    private val now = System.currentTimeMillis()
    private val directory get() = File(context.cacheDir, "lyrics")
    private val internal get() = InternalLyricCache(directory, { now }, { internalExpiry },
        File(context.filesDir, "downloaded-lyrics"))
    private var internalExpiry = LyricCacheExpiry.THIRTY_DAYS
    private var portableExpiry = LyricCacheExpiry.NEVER
    private val root = tree("root")
    private val music = DocumentsContract.buildTreeDocumentUri("com.macarmusic.app.test.documents", "music")
    private val problems = mutableListOf<String>()
    private lateinit var cache: LyricCache

    @Before fun setUp() = runBlocking(Dispatchers.IO) {
        grantFolders()
        faults { putBoolean("reset", true); putBoolean("resetCounters", true) }
        app.repository.awaitReady()
        app.repository.update { Library() }
        context.getSharedPreferences("lyric_cache", Context.MODE_PRIVATE).edit().clear().commit()
        internal.clear()
        internalExpiry = LyricCacheExpiry.THIRTY_DAYS
        portableExpiry = LyricCacheExpiry.NEVER
        cache = newCache()
        app.contentResolver.call(music, "setUnavailable", null, Bundle().apply { putBoolean("value", false) })
        app.contentResolver.call(music, "setLyricsMode", "missing", null)
        Unit
    }

    private suspend fun grantFolders(documentId: String? = null) {
        val token = java.util.UUID.randomUUID().toString()
        instrumentation.context.startActivity(Intent().apply {
            component = ComponentName("com.macarmusic.app.test", FixtureGrantActivity::class.java.name)
            putExtra("cacheGrantToken", token)
            putExtra("cacheDocumentId", documentId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        withTimeout(10_000) {
            while (app.checkUriPermission(root, Process.myPid(), Process.myUid(), READ_WRITE) != PackageManager.PERMISSION_GRANTED) {
                kotlinx.coroutines.delay(50)
            }
            while (app.contentResolver.call(root, "cacheFixture", null, null)?.getString("grantToken") != token) {
                kotlinx.coroutines.delay(25)
            }
        }
    }

    @After fun tearDown() = runBlocking(Dispatchers.IO) {
        faults()
        app.repository.update { Library() }
        cache.useInternal()
        internal.clear()
        for (name in listOf("keep.lrc", "keep.txt", "unrelated.json")) File(directory, name).delete()
        faults { putBoolean("reset", true) }
        app.contentResolver.call(music, "setLyricsMode", "available", null)
        Unit
    }

    @Test fun clearOnlyRemovesManagedInternalRecordsIncludingAtomicArtifacts() = runBlocking(Dispatchers.IO) {
        seed(1)
        File(directory, "${key(2)}.json.bak").writeBytes(LyricCacheFiles.encode(lyric(2)))
        File(directory, "${key(3)}.json.new").writeText("incomplete")
        val preserved = listOf("keep.lrc", "keep.txt", "unrelated.json").map { name ->
            File(directory, name).apply { writeText("Synthetic original $name") }
        }
        val before = cache.inspect()
        assertEquals(3L, before.files)
        assertEquals(before, cache.clear())
        assertTrue(internal.keys().isEmpty())
        preserved.forEach { assertEquals("Synthetic original ${it.name}", it.readText()) }
    }

    @Test fun explicitDownloadsSurviveAutomaticEvictionButRespectExpiryAndClear() = runBlocking(Dispatchers.IO) {
        for (number in 1..105) internal.write(key(number), lyric(number).copy(downloaded = true))
        for (number in 201..310) seed(number)
        assertEquals(205L, cache.inspect().files)
        for (number in 1..105) {
            assertEquals(lyric(number).copy(downloaded = true), cache.read(key(number)).value)
            assertTrue(File(context.filesDir, "downloaded-lyrics/${key(number)}.json").isFile)
            assertFalse(File(directory, "${key(number)}.json").exists())
        }
        internal.write(key(500), lyric(500).copy(downloaded = true, savedAtMs = now - 31L * 86_400_000))
        assertNull(cache.read(key(500)).value)
        assertEquals(205L, cache.clear().files)
        assertTrue(internal.keys().isEmpty())
        assertEquals(0L, cache.inspect().files)
    }

    @Test fun bulkKeepsGoodCachedLyricsAndPromotesThemForOfflineUseWithoutAnotherRequest() = runBlocking(Dispatchers.IO) {
        val track = track()
        val calls = mutableListOf<String>()
        val repository = selectionRepository(track, calls)
        cache = repository.cache
        val original = repository.load(track, online = true)
        val count = calls.size
        assertTrue(File(directory, "${trackKey(track)}.json").isFile)
        assertEquals(LyricDownloadResult.KEPT, repository.downloadOffline(track))
        assertEquals(count, calls.size)
        assertFalse(File(directory, "${trackKey(track)}.json").exists())
        val retained = cache.read(trackKey(track)).value!!
        assertEquals(original.document!!.copy(downloaded = true), retained)
        val restarted = LyricsRepository(context, app.repository, app.applicationScope,
            OnlineLyricsClient({ _, _, _ -> error("Offline downloads must not use the network") }) { _, _ -> })
        val offline = restarted.load(track, online = false, allowNetwork = false)
        assertTrue(offline.cached)
        assertTrue(offline.availableOffline)
        assertEquals(retained, offline.document)
        assertEquals(original.lyrics, offline.lyrics)
    }

    @Test fun bulkRepairsShortAutomaticLyricsButNeverReplacesManualTimingOrBest() = runBlocking(Dispatchers.IO) {
        val track = track()
        val calls = mutableListOf<String>()
        val repository = selectionRepository(track, calls)
        cache = repository.cache
        val short = CachedLyric("[00:01]Synthetic truncated version\n[00:10]End", LyricOrigin.QQ, now)
        assertTrue(cache.write(trackKey(track), short, cache.generation))
        assertNull(repository.load(track, online = true, allowNetwork = false).lyrics)
        val displayed = repository.latest.value
        assertEquals(LyricDownloadResult.SAVED, repository.downloadOffline(track))
        assertEquals(displayed, repository.latest.value)
        val repaired = repository.load(track, online = false, allowNetwork = false)
        assertEquals(50_000L, repaired.lyrics!!.lastTimestampMs)
        assertTrue(repaired.document!!.downloaded)
        assertTrue(repaired.document.versions.any { it.id == short.version().id })
        assertEquals(LyricDownloadResult.KEPT, repository.downloadOffline(track))
        cache.clear()
        val edited = repository.saveTiming(track, short, 750)
        val beforeManual = calls.size
        assertEquals(LyricDownloadResult.KEPT, repository.downloadOffline(track))
        assertEquals(beforeManual, calls.size)
        assertEquals(edited.document!!.copy(downloaded = true), cache.read(trackKey(track)).value)
        cache.clear()
        assertTrue(cache.write(trackKey(track), short, cache.generation))
        val available = repository.available(track, null)
        repository.best(track, available.choices.single(), null, available.generation)
        val best = cache.read(trackKey(track)).value!!
        assertEquals(LyricDownloadResult.KEPT, repository.downloadOffline(track))
        assertEquals(beforeManual, calls.size)
        assertEquals(best.copy(downloaded = true), cache.read(trackKey(track)).value)
        assertEquals(short.text, repository.load(track, online = false).document!!.text)
    }

    @Test fun automaticLookupAlsoReplacesOldTruncatedCacheEntries() = runBlocking(Dispatchers.IO) {
        val track = track()
        val repository = selectionRepository(track, mutableListOf())
        cache = repository.cache
        val short = CachedLyric("[00:10]Synthetic old truncated lyrics", LyricOrigin.QQ, now)
        assertTrue(cache.write(trackKey(track), short, cache.generation))
        val refreshed = repository.load(track, online = true)
        assertEquals(50_000L, refreshed.lyrics!!.lastTimestampMs)
        assertFalse(refreshed.document!!.downloaded)
        assertNotEquals(short.text, refreshed.document.text)
    }

    @Test fun retainingManualAlternativesForOfflineDoesNotInvalidateTheActiveVisit() = runBlocking(Dispatchers.IO) {
        val track = track()
        val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
        val repository = selectionRepository(track, calls)
        cache = repository.cache
        val default = repository.load(track, online = true)
        val candidate = repository.searchOnline(track).toList().flatMap { it.candidates }.single { it.key.id == "second" }
        val chosen = requireNotNull(repository.selectOnline(track, candidate, cache.generation))
        val generation = cache.generation
        val displayed = repository.latest.value
        val before = cache.read(trackKey(track)).value!!
        val count = calls.size
        assertNotEquals(default.document!!.text, chosen.document!!.text)
        assertEquals(LyricDownloadResult.KEPT, repository.downloadOffline(track))
        assertEquals(generation, cache.generation)
        assertEquals(displayed, repository.latest.value)
        assertEquals(count, calls.size)
        assertEquals(before.copy(downloaded = true), cache.read(trackKey(track)).value)
    }

    @Test fun portableOfflineDownloadsRetainTheirFlagAndNeverRedirectOnUnplug() = runBlocking(Dispatchers.IO) {
        val track = track()
        val calls = mutableListOf<String>()
        val repository = selectionRepository(track, calls)
        cache = repository.cache
        assertEquals(LyricDownloadResult.SAVED, repository.downloadOffline(track))
        val stored = cache.read(trackKey(track)).value!!
        assertTrue(stored.downloaded)
        assertEquals(1L, cache.moveInternalTo(root, FLAGS).files)
        assertEquals(stored, cache.read(trackKey(track)).value)
        assertEquals(LyricDownloadResult.SAVED, repository.downloadOffline(track.copy(id = "second", fileName = "Another copy.wav")))
        assertTrue(internal.keys().isEmpty())
        val restarted = LyricsRepository(context, app.repository, app.applicationScope,
            OnlineLyricsClient({ _, _, _ -> error("Portable offline lyric fetched from network") }) { _, _ -> })
        assertEquals(stored, restarted.load(track, online = false).document)
        val before = calls.size
        faults { putBoolean("unavailable", true) }
        fails { repository.downloadOffline(track.copy(title = "Another synthetic song")) }
        assertEquals(before, calls.size)
        assertFalse(cache.info.value.internal)
        assertTrue(cache.info.value.unavailable)
        assertTrue(internal.keys().isEmpty())
        faults()
        assertEquals(stored, cache.read(trackKey(track)).value)
        assertEquals(2L, cache.clear().files)
    }

    @Test fun offlineDownloadWriteFailureIsReportedAndNeverRedirectedToInternalStorage() = runBlocking(Dispatchers.IO) {
        cache.moveInternalTo(root, FLAGS)
        val track = track()
        val repository = selectionRepository(track, mutableListOf())
        cache = repository.cache
        faults { putInt("failAfterWrites", 0); putBoolean("resetCounters", true) }
        fails { repository.downloadOffline(track) }
        assertFalse(cache.info.value.internal)
        assertTrue(problems.isNotEmpty())
        assertTrue(internal.keys().isEmpty())
        assertNull(repository.latest.value.result)
        faults()
        assertEquals(LyricDownloadResult.SAVED, repository.downloadOffline(track))
        assertTrue(cache.read(trackKey(track)).value!!.downloaded)
        assertTrue(internal.keys().isEmpty())
    }

    @Test fun clearingDuringBulkDownloadRejectsTheLateSave() = runBlocking(Dispatchers.IO) {
        val track = track()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repository = selectionRepository(track, mutableListOf()) {
            if ("fcg_query_lyric_new" in it) { entered.complete(Unit); release.await() }
        }
        cache = repository.cache
        val pending = async {
            try { repository.downloadOffline(track); false }
            catch (_: IOException) { true }
        }
        try {
            withTimeout(5_000) { entered.await() }
            cache.clear()
            release.complete(Unit)
            assertTrue(pending.await())
            assertEquals(0L, cache.inspect().files)
            assertNull(repository.latest.value.result)
        } finally { release.complete(Unit); pending.cancel() }
    }

    @Test fun aPartialProviderOutageDoesNotPretendTheWholeDownloadConnectionIsOffline() = runBlocking(Dispatchers.IO) {
        for (responding in listOf(true, false)) {
            val repository = LyricsRepository(context, app.repository, app.applicationScope,
                OnlineLyricsClient({ url, _, _ ->
                    if (responding && "client_search_cp" in url) """{"code":0,"data":{"song":{"list":[]}}}"""
                    else throw IOException("Synthetic unavailable provider")
                }) { _, _ -> })
            cache = repository.cache
            assertEquals(if (responding) LyricDownloadResult.PARTIAL_FAILURE else LyricDownloadResult.SEARCH_FAILED,
                repository.downloadOffline(track()))
            assertEquals(0L, cache.inspect().files)
            assertFalse(cache.info.value.unavailable)
        }
    }

    @Test fun batchLocalLookupListsASharedFolderOnlyOnceAndDoesNotPersistTheIndex() = runBlocking(Dispatchers.IO) {
        val source = app.repository.addSource(music, 0)
        val track = app.repository.scan(source, null) {}.tracks.first()
        app.contentResolver.call(music, "scanFixtureState", null, Bundle().apply { putBoolean("resetReads", true) })
        val loader = LocalLyricsLoader(context, app.repository)
        val batch = loader.batchLookup()
        repeat(5) {
            assertNull(batch(track.copy(id = "synthetic-$it", fileName = "Synthetic $it.wav")).lyrics)
        }
        assertEquals(1, app.contentResolver.call(music, "scanFixtureState", null, null)!!.getInt("childReads"))
        assertNull(loader.batchLookup()(track).lyrics)
        assertEquals(2, app.contentResolver.call(music, "scanFixtureState", null, null)!!.getInt("childReads"))
    }

    @Test fun migrationVerifiesCopiesRecoversLegacyBackupAndSkipsUncommittedArtifacts() = runBlocking(Dispatchers.IO) {
        seed(1)
        val legacy = File(directory, "${key(2)}.json.bak").apply {
            writeText("""{"text":"[00:01]Synthetic legacy lyric","origin":"QQ"}""")
            assertTrue(setLastModified(now - 40L * 86_400_000))
        }
        File(directory, "${key(3)}.json.new").writeText("incomplete")
        val original = create(rootDocument(root), "song.lrc", "[00:00]Synthetic original lyric")
        val expectedAge = legacy.lastModified()
        val moved = cache.moveInternalTo(root, FLAGS)
        assertEquals(2L, moved.files)
        assertTrue(internal.keys().isEmpty())
        assertEquals(lyric(1), cache.read(key(1)).value)
        assertEquals(expectedAge, cache.read(key(2)).value!!.savedAtMs)
        assertEquals("[00:00]Synthetic original lyric", read(original))
        val restarted = newCache()
        assertEquals(cache.info.value.folder, restarted.info.value.folder)
        assertEquals(lyric(1), restarted.read(key(1)).value)
        assertEquals(2L, restarted.inspect().files)
        assertTrue(DocumentsContract.getDocumentId(Uri.parse(cache.info.value.folder!!.documentUri)).startsWith("node-"))
    }

    @Test fun partialMigrationKeepsInternalDataAndLocationUntilEveryCopyIsVerified() = runBlocking(Dispatchers.IO) {
        seed(1); seed(2)
        faults { putInt("failAfterWrites", 2); putBoolean("resetCounters", true) }
        fails { cache.moveInternalTo(root, FLAGS) }
        assertTrue(cache.info.value.internal)
        assertEquals(setOf(key(1), key(2)), internal.keys())
        assertEquals(lyric(1), internal.read(key(1)))
        assertEquals(0, grantFlags(root))
        faults()
        grantFolders()
        assertEquals(2L, cache.moveInternalTo(root, FLAGS).files)
        assertTrue(internal.keys().isEmpty())
        assertEquals(lyric(2), cache.read(key(2)).value)
    }

    @Test fun readbackMismatchNeverRemovesTheInternalOriginal() = runBlocking(Dispatchers.IO) {
        seed(1)
        faults { putBoolean("corruptReadback", true) }
        fails { cache.moveInternalTo(root, FLAGS) }
        assertEquals(lyric(1), internal.read(key(1)))
        assertTrue(cache.info.value.internal)
        faults()
        grantFolders()
        assertEquals(1L, cache.moveInternalTo(root, FLAGS).files)
        assertEquals(lyric(1), cache.read(key(1)).value)
    }

    @Test fun readonlyTemporaryAndForeignFoldersAreRejectedWithoutDataLoss() = runBlocking(Dispatchers.IO) {
        seed(1)
        val marker = create(rootDocument(tree("foreign")), "cache-info.json", "{}")
        for (target in listOf(tree("readonly"), tree("temporary"), tree("foreign"))) {
            fails { cache.moveInternalTo(target, FLAGS) }
            assertTrue(cache.info.value.internal)
            assertEquals(lyric(1), internal.read(key(1)))
            assertEquals(0, grantFlags(target))
        }
        assertEquals("{}", read(marker))
    }

    @Test fun switchingArchivesAndReturningInternalLeavesPreviousUsbCollectionsIntact() = runBlocking(Dispatchers.IO) {
        seed(1)
        cache.moveInternalTo(root, FLAGS)
        val first = SafLyricCache(app.contentResolver, cache.info.value.folder!!)
        internal.write(key(2), lyric(2))
        faults { putInt("failAfterWrites", 0); putBoolean("resetCounters", true) }
        fails { cache.moveInternalTo(tree("second"), FLAGS) }
        assertEquals(root.toString(), cache.info.value.folder!!.treeUri)
        assertEquals(lyric(1), cache.read(key(1)).value)
        assertEquals(lyric(2), internal.read(key(2)))
        faults()
        grantFolders()
        cache.moveInternalTo(tree("second"), FLAGS)
        assertEquals(lyric(1), first.read(key(1)))
        val second = SafLyricCache(app.contentResolver, cache.info.value.folder!!)
        cache.useInternal()
        assertTrue(cache.info.value.internal)
        assertEquals(lyric(1), first.read(key(1)))
        assertEquals(lyric(2), second.read(key(2)))
        assertTrue(internal.keys().isEmpty())
    }

    @Test fun portableCacheDoesNotEvictOldLyricsOrApplyInternalCountAndByteLimits() = runBlocking(Dispatchers.IO) {
        val old = now - 60L * 86_400_000
        val text = (0 until 420).joinToString("\n") { "[00:01]Synthetic retained line ${"x".repeat(90)}" }
        val record = CachedLyric(text, LyricOrigin.QQ, old)
        internal.write(key(1), record)
        cache.moveInternalTo(root, FLAGS)
        for (number in 2..105) assertTrue(cache.write(key(number), record, cache.generation))
        val size = cache.inspect()
        assertEquals(105L, size.files)
        assertTrue("Portable cache must retain more than 4 MiB", size.bytes > LyricCacheFiles.INTERNAL_BYTES)
        assertEquals(record, cache.read(key(1)).value)
        assertEquals(record, newCache().read(key(105)).value)
        cache.useInternal()
        grantFolders()
        cache.moveInternalTo(root, FLAGS)
        assertEquals(size, cache.inspect())
        assertEquals(record, cache.read(key(1)).value)
    }

    @Test fun partialClearReportsFailureAndNeverDeletesLocalLyricsOrUnknownDocuments() = runBlocking(Dispatchers.IO) {
        seed(1); seed(2)
        cache.moveInternalTo(root, FLAGS)
        val folder = Uri.parse(cache.info.value.folder!!.documentUri)
        val bucket = child(folder, key(1).take(2))
        val originals = listOf("keep.lrc", "keep.txt", "${key(1)}-0.json", "unrelated.json").map {
            create(bucket, it, "Synthetic original $it")
        }
        faults { putInt("failAfterDeletes", 1); putBoolean("resetCounters", true) }
        fails { cache.clear() }
        assertTrue(cache.info.value.unavailable)
        assertEquals(1, faults().getInt("deletes"))
        assertEquals(1L, cache.inspect().files)
        assertEquals(1L, cache.clear().files)
        assertEquals(0L, cache.inspect().files)
        for (uri in originals) assertTrue(read(uri).startsWith("Synthetic original"))
        assertTrue(read(child(folder, "cache-info.json")).contains("ManaMusicPlayer lyric cache"))
    }

    @Test fun unavailableUsbNeverFallsBackToWritingInternalCache() = runBlocking(Dispatchers.IO) {
        seed(1); cache.moveInternalTo(root, FLAGS)
        val folder = cache.info.value.folder!!
        faults { putBoolean("unavailable", true) }
        assertTrue(cache.read(key(1)).unavailable)
        assertFalse(cache.write(key(2), lyric(2), cache.generation))
        assertTrue(internal.keys().isEmpty())
        assertEquals(folder, cache.info.value.folder)
        assertTrue(problems.isNotEmpty())
        cache.useInternal()
        assertTrue(cache.info.value.internal)
        faults()
        assertEquals(lyric(1), SafLyricCache(app.contentResolver, folder).read(key(1)))
    }

    @Test fun sharedMusicGrantSurvivesRemovalAndOnlyUnusedBitsAreReleased() = runBlocking(Dispatchers.IO) {
        app.repository.addSource(root, FLAGS)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, grantFlags(root))
        seed(1); cache.moveInternalTo(root, FLAGS)
        assertEquals(READ_WRITE, grantFlags(root))
        cache.useInternal()
        assertEquals("Music still needs the read grant", Intent.FLAG_GRANT_READ_URI_PERMISSION, grantFlags(root))
        grantFolders()
        cache.moveInternalTo(root, FLAGS)
        app.repository.update { LibraryEdits.removeSources(it, setOf(root.toString())) }
        assertTrue(app.repository.documents.releaseReadPermissions(setOf(root.toString())).isEmpty())
        assertEquals(READ_WRITE, grantFlags(root))
        assertEquals(lyric(1), cache.read(key(1)).value)
        cache.useInternal()
        assertEquals(0, grantFlags(root))
    }

    @Test fun grantReleaseWaitsForPendingMusicReferenceCommit() = runBlocking(Dispatchers.IO) {
        seed(1); cache.moveInternalTo(root, FLAGS)
        val source = app.repository.documents.source(root, FLAGS)
        val lock = app.repository.documents.grantMutex
        lock.lock()
        val release = async { cache.useInternal() }
        try {
            withTimeout(2_000) { cache.info.first { it.internal } }
            assertFalse(release.isCompleted)
            app.repository.update { it.copy(sources = listOf(source)) }
        } finally { lock.unlock() }
        withTimeout(2_000) { release.await() }
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, grantFlags(root))
        app.repository.update { Library() }
        assertTrue(app.repository.documents.releaseReadPermissions(setOf(root.toString())).isEmpty())
    }

    @Test fun selectingTheMarkedCacheFolderDirectlyReusesItsOpaqueDocuments() = runBlocking(Dispatchers.IO) {
        seed(1); cache.moveInternalTo(root, FLAGS)
        val folderId = DocumentsContract.getDocumentId(Uri.parse(cache.info.value.folder!!.documentUri))
        cache.useInternal()
        grantFolders(folderId)
        val selected = tree(folderId)
        assertEquals(0L, cache.moveInternalTo(selected, FLAGS).files)
        assertEquals(selected.toString(), cache.info.value.folder!!.treeUri)
        assertEquals(lyric(1), cache.read(key(1)).value)
        assertEquals(1L, cache.inspect().files)
    }

    @Test fun internalLimitsAndLegacyExpiryRemainEffective() = runBlocking(Dispatchers.IO) {
        for (number in 1..105) internal.write(key(number), lyric(number))
        assertEquals(100L, internal.size().files)
        val text = (0 until 420).joinToString("\n") { "Synthetic line ${"x".repeat(95)}" }
        for (number in 106..210) internal.write(key(number), CachedLyric(text, LyricOrigin.QQ, now))
        assertTrue(internal.size().files <= 100)
        assertTrue(internal.size().bytes <= LyricCacheFiles.INTERNAL_BYTES)
        internal.write(key(999), lyric(999).copy(savedAtMs = now - 31L * 86_400_000))
        assertNull(internal.read(key(999)))
        assertFalse(key(999) in internal.keys())
    }

    @Test fun internalExpiryCanBeDisabledAndChangedWithoutTouchingOriginals() = runBlocking(Dispatchers.IO) {
        val old = lyric(1).copy(savedAtMs = now - 90L * 86_400_000)
        internal.write(key(1), old)
        internalExpiry = LyricCacheExpiry.NEVER
        assertEquals(old, cache.read(key(1)).value)
        assertEquals(1L, cache.inspect().files)
        val original = File(directory, "keep.lrc").apply { writeText("Synthetic original") }
        internalExpiry = LyricCacheExpiry.NINETY_DAYS
        assertEquals(0L, cache.inspect().files)
        assertNull(cache.read(key(1)).value)
        assertEquals("Synthetic original", original.readText())
    }

    @Test fun portableExpiryUsesFetchedAgeAndNeverResurrectsAnOlderRevision() = runBlocking(Dispatchers.IO) {
        val old = lyric(1).copy(savedAtMs = now - 7L * 86_400_000)
        internal.write(key(1), old)
        cache.moveInternalTo(root, FLAGS)
        val folder = Uri.parse(cache.info.value.folder!!.documentUri)
        val bucket = child(folder, key(1).take(2))
        create(bucket, "${key(1)}-2.json", LyricCacheFiles.encode(old).toString(Charsets.UTF_8))
        val original = create(bucket, "keep.lrc", "Synthetic original")
        assertEquals(old, cache.read(key(1)).value)
        portableExpiry = LyricCacheExpiry.SEVEN_DAYS
        assertNull(newCache().read(key(1)).value)
        assertEquals(0L, cache.inspect().files)
        assertEquals("Synthetic original", read(original))
        portableExpiry = LyricCacheExpiry.NEVER
        assertNull(cache.read(key(1)).value)
    }

    @Test fun expiryDeletionFailureIsReportedAndDoesNotServeAStaleRevision() = runBlocking(Dispatchers.IO) {
        internal.write(key(1), lyric(1).copy(savedAtMs = now - 86_400_000))
        cache.moveInternalTo(root, FLAGS)
        portableExpiry = LyricCacheExpiry.ONE_DAY
        faults { putInt("failAfterDeletes", 0); putBoolean("resetCounters", true) }
        val read = cache.read(key(1))
        assertTrue(read.unavailable)
        assertNull(read.value)
        assertTrue(problems.isNotEmpty())
        faults()
        assertEquals(0L, cache.inspect().files)
        assertFalse(cache.info.value.unavailable)
    }

    @Test fun maintenanceSkipsFreshEntriesAndSupportsCancellation() = runBlocking(Dispatchers.IO) {
        val old = lyric(1).copy(savedAtMs = now - 31L * 86_400_000)
        internal.write(key(1), old)
        seed(2)
        cache.moveInternalTo(root, FLAGS)
        portableExpiry = LyricCacheExpiry.THIRTY_DAYS
        val store = SafLyricCache(app.contentResolver, cache.info.value.folder!!, { now }, { portableExpiry })
        try {
            store.pruneExpired { throw kotlinx.coroutines.CancellationException("Synthetic cancellation") }
            fail("Expiry maintenance must be cancellable")
        } catch (_: kotlinx.coroutines.CancellationException) { }
        assertEquals(2L, store.size().files)
        assertEquals(1L, cache.inspect().files)
        assertEquals(lyric(2), cache.read(key(2)).value)
        assertNull(cache.read(key(1)).value)
    }

    @Test fun reorderedProviderColumnsAndCorruptNewestRevisionAreHandledSafely() = runBlocking(Dispatchers.IO) {
        faults { putBoolean("reverseColumns", true) }
        seed(1); cache.moveInternalTo(root, FLAGS)
        val folder = Uri.parse(cache.info.value.folder!!.documentUri)
        val bucket = child(folder, key(1).take(2))
        create(bucket, "${key(1)}-2.json", "{bad")
        assertEquals(lyric(1), cache.read(key(1)).value)
        assertTrue(cache.write(key(1), lyric(2), cache.generation))
        assertEquals(lyric(2), cache.read(key(1)).value)
        assertEquals(1L, cache.inspect().files)
    }

    @Test fun corruptSavedLocationStaysUnavailableUntilExplicitlyChanged() = runBlocking(Dispatchers.IO) {
        context.getSharedPreferences("lyric_cache", Context.MODE_PRIVATE).edit().putString("folder", "{bad").commit()
        cache = newCache()
        assertTrue(cache.info.value.invalidLocation)
        assertTrue(cache.read(key(1)).unavailable)
        assertFalse(cache.write(key(1), lyric(1), cache.generation))
        assertTrue(internal.keys().isEmpty())
        cache.useInternal()
        assertTrue(cache.info.value.internal)
        assertTrue(cache.write(key(1), lyric(1), cache.generation))
    }

    @Test fun clearingDuringNetworkLookupPreventsDiskRepopulationWithoutDroppingDisplayedLyrics() = runBlocking(Dispatchers.IO) {
        val entered = CompletableDeferred<Unit>()
        val released = CompletableDeferred<Unit>()
        var requests = 0
        val track = track()
        val client = OnlineLyricsClient({ url, _, _ ->
            requests++
            entered.complete(Unit)
            released.await()
            if (url.contains("/search?")) {
                """{"status":200,"candidates":[{"id":"42","accesskey":"fixture","song":"${track.title}","singer":"","duration":60000}]}"""
            } else {
                val text = android.util.Base64.encodeToString(syntheticOnlineLyrics("[00:01]Synthetic online lyric").toByteArray(), android.util.Base64.NO_WRAP)
                """{"status":200,"content":"$text"}"""
            }

        }) { _, error -> throw AssertionError(error) }
        val repository = LyricsRepository(context, app.repository, app.applicationScope, client)
        val pending = async { repository.load(track, online = true, refresh = true) }
        try {
            withTimeout(2_000) { entered.await() }
            repository.cache.clear()
            released.complete(Unit)
            assertNotNull(pending.await().lyrics)
            assertEquals(0L, repository.cache.inspect().files)
            val afterFetch = requests
            assertNull(repository.load(track, online = true, allowNetwork = false).lyrics)
            assertEquals(afterFetch, requests)
            assertNotNull(repository.load(track, online = true).lyrics)
            assertEquals(1L, repository.cache.inspect().files)
            val moved = repository.cache.moveInternalTo(root, FLAGS)
            assertEquals(1L, moved.files)
            val offline = LyricsRepository(context, app.repository, app.applicationScope,
                OnlineLyricsClient({ _, _, _ -> throw AssertionError("Portable cache must work without a network request") }) { _, _ -> })
            assertTrue(offline.load(track, online = true, allowNetwork = false).cached)
            cache = repository.cache
        } finally { released.complete(Unit); pending.cancel() }
    }

    @Test fun clearingInvalidatesNegativeLookupCacheWithoutPolling() = runBlocking(Dispatchers.IO) {
        var requests = 0
        val repository = LyricsRepository(context, app.repository, app.applicationScope,
            OnlineLyricsClient({ _, _, _ -> requests++; throw IOException("Synthetic offline lookup") }) { _, _ -> })
        repository.load(track(), online = true)
        val first = requests
        assertTrue(first > 0)
        repository.load(track(), online = true)
        assertEquals(first, requests)
        repository.cache.clear()
        repository.load(track(), online = true)
        assertTrue(requests > first)
    }

    @Test fun nextMatchPersistsOverridesAndHistoryButAutomaticCacheRemainsLocalFirst() = runBlocking(Dispatchers.IO) {
        val source = app.repository.addSource(music, 0)
        val track = app.repository.scan(source, null) {}.tracks.first()
        val calls = mutableListOf<String>()
        val repository = selectionRepository(track, calls)
        cache = repository.cache
        val first = repository.load(track, online = true)
        assertEquals(LyricOrigin.QQ, first.origin)
        assertFalse(first.document!!.selected)
        val second = requireNotNull(repository.next(track, first))
        assertEquals("Synthetic alternative", second.lyrics!!.lines.first().text)
        assertTrue(second.document!!.selected)
        assertEquals(0L, second.document.offsetMs)
        assertEquals(second, repository.latest.value.result)
        assertEquals(1L, cache.inspect().files)
        app.contentResolver.call(music, "setLyricsMode", "available", null)
        val restored = selectionRepository(track, calls)
        val before = calls.size
        assertEquals(second.document, restored.load(track, online = true, allowNetwork = false).document)
        assertEquals(before, calls.size)
        assertEquals(LyricOrigin.LOCAL_FILE, restored.load(track, online = false).origin)
        assertNull(repository.next(track, second))
        assertEquals(second, repository.latest.value.result)
        cache.clear()
        assertEquals(LyricOrigin.LOCAL_FILE, restored.load(track, online = true).origin)
    }

    @Test fun localTimingSavesPortableCopiesWithoutEditingOriginalAndClearRestoresLocalTiming() = runBlocking(Dispatchers.IO) {
        app.contentResolver.call(music, "setLyricsMode", "available", null)
        val source = app.repository.addSource(music, 0)
        val track = app.repository.scan(source, null) {}.tracks.first()
        val repository = selectionRepository(track, mutableListOf())
        cache = repository.cache
        val original = repository.load(track, online = false)
        val raw = original.document!!.text
        val saved = repository.saveTiming(track, original.document, 300)
        assertEquals(4700L, saved.lyrics!!.lines.first().timeMs)
        assertEquals(raw, saved.document!!.text)
        assertEquals(raw, LocalLyricsLoader(context, app.repository).load(track).document!!.text)
        assertEquals(5000L, LocalLyricsLoader(context, app.repository).load(track).lyrics!!.lines.first().timeMs)
        assertEquals(saved.document, selectionRepository(track, mutableListOf()).load(track, online = false).document)
        assertEquals(1L, cache.moveInternalTo(root, FLAGS).files)
        val restored = selectionRepository(track, mutableListOf())
        cache = restored.cache
        val portable = restored.load(track, online = false, allowNetwork = false)
        assertEquals(saved.document, portable.document)
        val delayed = restored.saveTiming(track, portable.document!!, -1000)
        assertEquals(6000L, delayed.lyrics!!.lines.first().timeMs)
        val again = selectionRepository(track, mutableListOf()).load(track, online = false, allowNetwork = false)
        assertEquals(delayed.document, again.document)
        assertEquals(1L, cache.clear().files)
        assertEquals(original.lyrics, restored.load(track, online = false).lyrics)
    }

    @Test fun disconnectedPortableTimingSaveKeepsPreviousSelectionAndDoesNotFallBackInternally() = runBlocking(Dispatchers.IO) {
        val track = track()
        val repository = selectionRepository(track, mutableListOf())
        cache = repository.cache
        val first = repository.load(track, online = true)
        val previous = repository.saveTiming(track, first.document!!, 100)
        cache.moveInternalTo(root, FLAGS)
        faults { putBoolean("unavailable", true) }
        fails { repository.saveTiming(track, previous.document!!, 1000) }
        assertEquals(previous, repository.latest.value.result)
        assertTrue(cache.info.value.unavailable)
        assertFalse(cache.info.value.internal)
        assertTrue(internal.keys().isEmpty())
        faults()
        assertEquals(previous.document,
            selectionRepository(track, mutableListOf()).load(track, online = true, allowNetwork = false).document)
    }

    @Test fun timingCommitWinsAgainstAnOlderInFlightLookup() = runBlocking(Dispatchers.IO) {
        val track = track()
        val entered = CompletableDeferred<Unit>()
        val released = CompletableDeferred<Unit>()
        val repository = selectionRepository(track, mutableListOf()) { url ->
            if ("client_search_cp" in url) { entered.complete(Unit); released.await() }
        }
        cache = repository.cache
        val original = CachedLyric("[00:01]Synthetic chosen line", LyricOrigin.QQ, now)
        val pending = async { repository.load(track, online = true, refresh = true) }
        try {
            withTimeout(5000) { entered.await() }
            val selected = repository.saveTiming(track, original, -1000)
            released.complete(Unit)
            assertEquals(selected.document, pending.await().document)
            assertEquals(selected.document, repository.latest.value.result!!.document)
            assertEquals(selected.document,
                selectionRepository(track, mutableListOf()).load(track, online = true, allowNetwork = false).document)
        } finally { released.complete(Unit); pending.cancel() }
    }

    @Test fun cancelNextLookupLeavesCurrentLyricsAndCachedTimingUntouched() = runBlocking(Dispatchers.IO) {
        val track = track()
        val entered = CompletableDeferred<Unit>()
        val released = CompletableDeferred<Unit>()
        val repository = selectionRepository(track, mutableListOf()) { url ->
            if ("client_search_cp" in url) { entered.complete(Unit); released.await() }
        }
        cache = repository.cache
        val previous = repository.saveTiming(track, CachedLyric("[00:01]Synthetic retained", LyricOrigin.QQ, now), 100)
        val pending = async { repository.next(track, previous) }
        try {
            withTimeout(5000) { entered.await() }
            pending.cancel()
            released.complete(Unit)
            pending.join()
            assertTrue(pending.isCancelled)
            assertEquals(previous, repository.latest.value.result)
            assertEquals(previous.document,
                selectionRepository(track, mutableListOf()).load(track, online = true, allowNetwork = false).document)
        } finally { released.complete(Unit); pending.cancel() }
    }

    @Test fun failedNextOrCacheWriteDoesNotDiscardDisplayedSelection() = runBlocking(Dispatchers.IO) {
        val track = track()
        val repository = selectionRepository(track, mutableListOf())
        cache = repository.cache
        val first = repository.load(track, online = true)
        cache.moveInternalTo(root, FLAGS)
        faults { putInt("failAfterWrites", 0); putBoolean("resetCounters", true) }
        fails { repository.next(track, first) }
        assertEquals(first, repository.latest.value.result)
        faults()
        assertEquals(first.document,
            selectionRepository(track, mutableListOf()).load(track, online = true, allowNetwork = false).document)
    }

    @Test fun clearingWhileNextIsPendingPreventsRepopulationAndKeepsDisplayedLyrics() = runBlocking(Dispatchers.IO) {
        val track = track()
        val entered = CompletableDeferred<Unit>()
        val released = CompletableDeferred<Unit>()
        val repository = selectionRepository(track, mutableListOf()) { url ->
            if ("client_search_cp" in url) { entered.complete(Unit); released.await() }
        }
        cache = repository.cache
        val previous = repository.saveTiming(track, CachedLyric("[00:01]Synthetic retained", LyricOrigin.QQ, now), 100)
        val pending = async {
            try {
                repository.next(track, previous)
                false
            } catch (_: IOException) { true }
        }
        try {
            withTimeout(5000) { entered.await() }
            assertEquals(1L, cache.clear().files)
            released.complete(Unit)
            assertTrue(pending.await())
            assertEquals(0L, cache.inspect().files)
            assertEquals(previous, repository.latest.value.result)
        } finally { released.complete(Unit); pending.cancel() }
    }

    @Test fun untimedLyricsCannotOverwriteAnExistingTimingSelection() = runBlocking(Dispatchers.IO) {
        val track = track()
        val repository = selectionRepository(track, mutableListOf())
        cache = repository.cache
        val previous = repository.saveTiming(track, CachedLyric("[00:01]Synthetic retained", LyricOrigin.QQ, now), 100)
        fails { repository.saveTiming(track, CachedLyric("Synthetic untimed text", LyricOrigin.LOCAL_FILE), 1000) }
        assertEquals(previous, repository.latest.value.result)
        assertEquals(previous.document,
            selectionRepository(track, mutableListOf()).load(track, online = true, allowNetwork = false).document)
    }

    @Test fun downloadedVersionInventoryKeepsPreviousProvidersWithoutAnyNewRequests() = runBlocking(Dispatchers.IO) {
        val track = track()
        val calls = mutableListOf<String>()
        val repository = selectionRepository(track, calls)
        cache = repository.cache
        val first = repository.load(track, online = true)
        val second = requireNotNull(repository.next(track, first))
        val third = repository.saveTiming(track, CachedLyric("[00:01]Synthetic Kuwo copy", LyricOrigin.KUWO, now), 200)
        val requests = calls.size
        val available = repository.available(track, third)
        assertEquals(3, available.choices.size)
        assertEquals(setOf(LyricOrigin.QQ, LyricOrigin.KUWO), available.choices.map { it.origin }.toSet())
        assertTrue(available.choices.all { it.cached })
        assertTrue(available.choices.any { it.version?.text == first.document!!.text })
        assertTrue(available.choices.any { it.version?.text == second.document!!.text })
        assertEquals(third.document!!.version().id, available.currentKey)
        assertEquals(requests, calls.size)
        assertEquals(1L, cache.inspect().files)
    }

    @Test fun temporaryOfflineSelectionDoesNotReplaceAutomaticDefaultWithoutBest() = runBlocking(Dispatchers.IO) {
        val track = track()
        val calls = mutableListOf<String>()
        val repository = selectionRepository(track, calls)
        cache = repository.cache
        val first = repository.load(track, online = true)
        val second = requireNotNull(repository.next(track, first))
        val inventory = repository.available(track, second)
        val requests = calls.size
        val chosen = repository.select(track, inventory.choices.single { it.version?.text == first.document!!.text }, inventory.generation)
        assertEquals(first.document!!.text, chosen.document!!.text)
        assertTrue(chosen.availableOffline)
        assertFalse(chosen.document.selected)
        assertEquals(second.document!!.text,
            selectionRepository(track, calls).load(track, online = true, allowNetwork = false).document!!.text)
        assertEquals(requests, calls.size)
    }

    @Test fun bestIsSeparateFromCurrentSelectionSurvivesRestartAndPortableMoveWithoutNetwork() = runBlocking(Dispatchers.IO) {
        val track = track()
        val calls = mutableListOf<String>()
        val repository = selectionRepository(track, calls)
        cache = repository.cache
        val first = repository.load(track, online = true)
        val second = requireNotNull(repository.next(track, first))
        val inventory = repository.available(track, second)
        val original = inventory.choices.single { it.version?.text == first.document!!.text }
        val requests = calls.size
        val marked = requireNotNull(repository.best(track, original, second, inventory.generation))
        assertEquals(second.document!!.text, marked.document!!.text)
        assertEquals(original.key, marked.document.bestVersionId)
        val updated = repository.available(track, marked)
        assertEquals(original.key, updated.bestKey)
        val temporary = repository.select(track, updated.choices.single { it.version?.text == second.document.text }, updated.generation)
        assertEquals(second.document.text, temporary.document!!.text)
        assertEquals(original.key, temporary.document.bestVersionId)
        assertEquals(first.document!!.text, selectionRepository(track, calls).load(track, online = false).document!!.text)
        cache.moveInternalTo(root, FLAGS)
        val restarted = selectionRepository(track, calls)
        cache = restarted.cache
        val restored = restarted.load(track, online = false, allowNetwork = false)
        assertEquals(first.document.text, restored.document!!.text)
        assertEquals(original.key, restored.document.bestVersionId)
        assertTrue(internal.keys().isEmpty())
        assertEquals(requests, calls.size)
        restarted.best(track, null, restored, cache.generation)
        assertNull(restarted.available(track, restored).bestKey)
    }

    @Test fun matchingLocalVersionsAreLazyUntilPickerAndBestNeverModifiesOriginalFiles() = runBlocking(Dispatchers.IO) {
        app.contentResolver.call(music, "setLyricsMode", "versions", null)
        val source = app.repository.addSource(music, 0)
        val track = app.repository.scan(source, null) {}.tracks.first()
        val calls = mutableListOf<String>()
        val repository = selectionRepository(track, calls)
        cache = repository.cache
        val original = repository.load(track, online = false)
        val inventory = repository.available(track, original)
        assertEquals(3, inventory.choices.size)
        assertTrue(inventory.choices.all { it.origin == LyricOrigin.LOCAL_FILE })
        val alternate = inventory.choices.single { "Alternate" in it.name }
        val marked = requireNotNull(repository.best(track, alternate, original, inventory.generation))
        assertEquals(original.lyrics, marked.lyrics)
        val restored = selectionRepository(track, calls).load(track, online = false)
        assertEquals("Synthetic local alternate", restored.lyrics!!.lines.first().text)
        assertEquals(original.document!!.text, LocalLyricsLoader(context, app.repository).load(track).document!!.text)
        val list = repository.available(track, marked)
        assertEquals("A cached copy and its unchanged original must be one version", 3, list.choices.size)
        assertEquals(inventory.choices.map { it.key },
            repository.available(track, marked, inventory).choices.map { it.key })
        assertTrue(calls.isEmpty())
        cache.clear()
        assertEquals(original.lyrics, repository.load(track, online = false).lyrics)
    }

    @Test fun localFileChangesAndOldPickerGenerationsAreRejectedWithoutChangingCurrentLyrics() = runBlocking(Dispatchers.IO) {
        app.contentResolver.call(music, "setLyricsMode", "versions", null)
        val source = app.repository.addSource(music, 0)
        val track = app.repository.scan(source, null) {}.tracks.first()
        val repository = selectionRepository(track, mutableListOf())
        cache = repository.cache
        val original = repository.load(track, online = false)
        val inventory = repository.available(track, original)
        val alternate = inventory.choices.single { "Alternate" in it.name }
        app.contentResolver.call(music, "setLyricsMode", "versions-changed", null)
        fails { repository.select(track, alternate, inventory.generation) }
        assertEquals(original, repository.latest.value.result)
        assertEquals(0L, cache.inspect().files)
        app.contentResolver.call(music, "setLyricsMode", "versions", null)
        cache.clear()
        fails { repository.best(track, alternate, original, inventory.generation) }
        assertEquals(0L, cache.inspect().files)
        val fresh = repository.available(track, original)
        cache.moveInternalTo(root, FLAGS)
        fails { repository.select(track, fresh.choices.first(), fresh.generation) }
        assertEquals(original, repository.latest.value.result)
    }

    @Test fun unreadableLocalAlternativeStillShowsOtherVersionsAndAnExplicitIssue() = runBlocking(Dispatchers.IO) {
        val source = app.repository.addSource(music, 0)
        val track = app.repository.scan(source, null) {}.tracks.first()
        val calls = mutableListOf<String>()
        val repository = selectionRepository(track, calls)
        cache = repository.cache
        for (mode in listOf("versions-unreadable", "versions-denied")) {
            app.contentResolver.call(music, "setLyricsMode", mode, null)
            val original = repository.load(track, online = false)
            val inventory = repository.available(track, original)
            assertEquals(2, inventory.choices.size)
            assertEquals(LyricIssue.LOCAL_UNREADABLE, inventory.issue)
            assertTrue(inventory.choices.any { "Third" in it.name })
        }
        assertTrue(calls.isEmpty())
    }

    @Test fun markingAndRemovingBestWithoutCurrentLyricsDoesNotCreateAnAutomaticOverride() = runBlocking(Dispatchers.IO) {
        app.contentResolver.call(music, "setLyricsMode", "versions", null)
        val source = app.repository.addSource(music, 0)
        val track = app.repository.scan(source, null) {}.tracks.first()
        val calls = mutableListOf<String>()
        val repository = selectionRepository(track, calls)
        cache = repository.cache
        val inventory = repository.available(track, null)
        val alternate = inventory.choices.single { "Alternate" in it.name }
        assertNull(repository.best(track, alternate, null, inventory.generation))
        assertEquals("Synthetic local alternate", repository.load(track, online = false).lyrics!!.lines.first().text)
        assertNull(repository.best(track, null, null, cache.generation))
        val restored = selectionRepository(track, calls).load(track, online = false)
        assertEquals("Synthetic start", restored.lyrics!!.lines.first().text)
        assertFalse(restored.cached)
        assertNull(repository.available(track, restored).bestKey)
        assertTrue(calls.isEmpty())
    }

    @Test fun oversizeTemporarySelectionKeepsDefaultAndBestIntactInBothCacheLocations() = runBlocking(Dispatchers.IO) {
        fun large(number: Int) = lyric(number).copy(
            text = (0 until 150).joinToString("\n") { "[00:01]Synthetic $number ${"x".repeat(1400)}" },
        )
        val best = large(1)
        val original = large(2).copy(versions = listOf(best.version()), bestVersionId = best.version().id)
        val incoming = large(3).copy(offlineSelected = true, automatic = false)
        internal.write(key(1), original)
        repeat(2) { location ->
            if (location == 1) cache.moveInternalTo(root, FLAGS)
            val generation = cache.generation
            assertNull(cache.retain(key(1), incoming, generation, preserveActive = true))
            assertTrue(cache.info.value.unavailable)
            assertEquals(generation, cache.generation)
            assertEquals(original, cache.read(key(1)).value)
        }
        assertTrue(problems.isNotEmpty())
    }

    @Test fun failedPortableBestChangeKeepsThePreviousPreferenceAndNeverFallsBackInternally() = runBlocking(Dispatchers.IO) {
        val track = track()
        val calls = mutableListOf<String>()
        val repository = selectionRepository(track, calls)
        cache = repository.cache
        val first = repository.load(track, online = true)
        val second = requireNotNull(repository.next(track, first))
        val inventory = repository.available(track, second)
        val firstChoice = inventory.choices.single { it.version?.text == first.document!!.text }
        val current = requireNotNull(repository.best(track, firstChoice, second, inventory.generation))
        cache.moveInternalTo(root, FLAGS)
        val portable = repository.available(track, current)
        faults { putInt("failAfterWrites", 0); putBoolean("resetCounters", true) }
        fails { repository.best(track, portable.choices.single { it.key != firstChoice.key }, current, portable.generation) }
        assertEquals(current, repository.latest.value.result)
        assertTrue(cache.info.value.unavailable)
        assertFalse(cache.info.value.internal)
        assertTrue(internal.keys().isEmpty())
        faults()
        assertEquals(firstChoice.key, selectionRepository(track, calls).load(track, online = false).document!!.bestVersionId)
    }

    @Test fun eachVersionExpiresIndependentlyInInternalAndVerifiedPortableRecords() = runBlocking(Dispatchers.IO) {
        val old = lyric(1).copy(savedAtMs = now - 40L * 86_400_000)
        val current = lyric(2).copy(versions = listOf(old.version()), bestVersionId = old.version().id)
        internal.write(key(1), current)
        val pruned = requireNotNull(cache.read(key(1)).value)
        assertEquals(current.text, pruned.text)
        assertNull(pruned.bestVersionId)
        assertTrue(pruned.versions.isEmpty())
        internal.write(key(1), current)
        cache.moveInternalTo(root, FLAGS)
        assertEquals(current, cache.read(key(1)).value)
        portableExpiry = LyricCacheExpiry.THIRTY_DAYS
        faults { putInt("failAfterWrites", 0); putBoolean("resetCounters", true) }
        assertTrue(cache.read(key(1)).unavailable)
        faults()
        assertEquals(pruned, cache.read(key(1)).value)
        assertEquals(1L, cache.inspect().files)
        assertEquals(pruned, newCache().read(key(1)).value)
    }

    @Test fun playbackTransitionsDiscardTemporaryWidgetResultsBeforeReturningToTheSong() = runBlocking(Dispatchers.IO) {
        val track = track()
        val calls = mutableListOf<String>()
        val repository = selectionRepository(track, calls)
        cache = repository.cache
        repository.trackChanged(track.id)
        val original = repository.load(track, online = true)
        val inventory = repository.available(track, original)
        val current = repository.select(track, inventory.choices.first(), inventory.generation)
        assertEquals(current, repository.latest.value.result)
        val generation = repository.playbackGeneration
        repository.trackChanged("another-song")
        assertNull(repository.latest.value.result)
        repository.trackChanged(track.id)
        assertTrue(repository.playbackGeneration > generation)
        assertNull(repository.latest.value.result)
        val requests = calls.size
        repository.load(track, online = true, allowNetwork = false, publishResult = false)
        assertEquals(requests, calls.size)
    }

    @Test fun removingCurrentUsesBestThenOtherSavedLyricsAndFinallyClearsWithoutNetwork() = runBlocking(Dispatchers.IO) {
        val track = track()
        val calls = mutableListOf<String>()
        val repository = selectionRepository(track, calls)
        cache = repository.cache
        val first = repository.saveTiming(track, lyric(1), 100)
        val second = repository.saveTiming(track, lyric(2), -200)
        val third = repository.saveTiming(track, lyric(3), 300)
        val inventory = repository.available(track, third)
        val preferred = inventory.choices.single { it.key == first.document!!.version().id }
        val marked = requireNotNull(repository.best(track, preferred, third, inventory.generation))
        var choices = repository.available(track, marked)
        var removal = repository.remove(track, choices.choices.single { it.key == choices.currentKey }, marked, choices.generation)
        assertEquals(first.lyrics, removal.result.lyrics)
        assertEquals(preferred.key, removal.choices.bestKey)
        assertEquals(first.document!!.savedAtMs, removal.result.document!!.savedAtMs)
        assertEquals(first.lyrics, selectionRepository(track, calls).load(track, online = false).lyrics)
        choices = removal.choices
        removal = repository.remove(track, choices.choices.single { it.key == choices.currentKey }, removal.result, choices.generation)
        assertEquals(second.lyrics, removal.result.lyrics)
        assertNull(removal.choices.bestKey)
        choices = removal.choices
        removal = repository.remove(track, choices.choices.single(), removal.result, choices.generation)
        assertNull(removal.result.lyrics)
        assertNull(removal.result.document)
        assertNull(removal.choices.currentKey)
        assertTrue(removal.choices.choices.isEmpty())
        assertEquals(removal.result, repository.latest.value.result)
        assertTrue(repository.latest.value.userEdited)
        assertEquals(0L, cache.inspect().files)
        assertNull(selectionRepository(track, calls).load(track, online = true, allowNetwork = false).lyrics)
        assertTrue(calls.isEmpty())
    }

    @Test fun removingNoncurrentBestKeepsTheDisplayedVersionAndStaleSnapshotsCannotResurrectIt() = runBlocking(Dispatchers.IO) {
        val track = track()
        val calls = mutableListOf<String>()
        val repository = selectionRepository(track, calls)
        cache = repository.cache
        val first = repository.saveTiming(track, lyric(1), 100)
        repository.saveTiming(track, lyric(2), 200)
        val third = repository.saveTiming(track, lyric(3), 300)
        val inventory = repository.available(track, third)
        val target = inventory.choices.single { it.key == first.document!!.version().id }
        val marked = requireNotNull(repository.best(track, target, third, inventory.generation))
        val removal = repository.remove(track, target, marked, cache.generation)
        assertEquals(third.lyrics, removal.result.lyrics)
        assertNull(removal.choices.bestKey)
        assertEquals(2, removal.choices.choices.size)
        assertFalse(removal.result.document!!.versions.any { it.id == target.key })
        val adjusted = repository.saveTiming(track, marked.document!!, 500)
        assertEquals(500L, adjusted.document!!.offsetMs)
        assertFalse(repository.available(track, adjusted).choices.any { it.key == target.key })
        val other = removal.choices.choices.single { it.key != removal.choices.currentKey }
        repository.best(track, other, marked, cache.generation)
        assertFalse(repository.available(track, repository.latest.value.result).choices.any { it.key == target.key })
        assertTrue(calls.isEmpty())
    }

    @Test fun deletingTheOnlySavedLocalCopyKeepsOriginalFileAndRestoresItsUnadjustedTiming() = runBlocking(Dispatchers.IO) {
        app.contentResolver.call(music, "setLyricsMode", "available", null)
        val source = app.repository.addSource(music, 0)
        val track = app.repository.scan(source, null) {}.tracks.first()
        val calls = mutableListOf<String>()
        val repository = selectionRepository(track, calls)
        cache = repository.cache
        val original = repository.load(track, online = false)
        val saved = repository.saveTiming(track, original.document!!, 750)
        val inventory = repository.available(track, saved)
        val removal = repository.remove(track, inventory.choices.single(), saved, inventory.generation)
        assertEquals(original.lyrics, removal.result.lyrics)
        assertEquals(0L, removal.result.document!!.offsetMs)
        assertFalse(removal.result.cached)
        assertFalse(removal.choices.choices.single().cached)
        assertEquals(0L, cache.inspect().files)
        assertEquals(original.document.text, LocalLyricsLoader(context, app.repository).load(track).document!!.text)
        fails { repository.remove(track, removal.choices.choices.single(), removal.result, removal.choices.generation) }
        assertTrue(calls.isEmpty())
    }

    @Test fun deletingCurrentSavedLocalCopyPrefersAnotherLocalVersionWithoutSavingItAgain() = runBlocking(Dispatchers.IO) {
        app.contentResolver.call(music, "setLyricsMode", "versions", null)
        val source = app.repository.addSource(music, 0)
        val track = app.repository.scan(source, null) {}.tracks.first()
        val calls = mutableListOf<String>()
        val repository = selectionRepository(track, calls)
        cache = repository.cache
        val original = repository.load(track, online = false)
        val saved = repository.saveTiming(track, original.document!!, 500)
        val inventory = repository.available(track, saved)
        val current = inventory.choices.single { it.key == inventory.currentKey }
        val removal = repository.remove(track, current, saved, inventory.generation)
        assertNotEquals(current.key, removal.choices.currentKey)
        assertEquals(LyricOrigin.LOCAL_FILE, removal.result.origin)
        assertFalse(removal.result.cached)
        assertEquals(3, removal.choices.choices.size)
        assertTrue(removal.choices.choices.none { it.cached })
        assertEquals(0L, cache.inspect().files)
        assertEquals(original.lyrics, LocalLyricsLoader(context, app.repository).load(track).lyrics)
        assertTrue(calls.isEmpty())
    }

    @Test fun perVersionRemovalPersistsInBothStoresAndInvalidatesOlderWrites() = runBlocking(Dispatchers.IO) {
        val second = lyric(2).copy(offsetMs = 500, savedAtMs = now - 1000)
        val third = lyric(3)
        val record = lyric(1).copy(versions = listOf(second.version(), third.version()), bestVersionId = third.version().id)
        repeat(2) { location ->
            cache.useInternal()
            internal.clear()
            internal.write(key(1), record)
            seed(9)
            if (location == 1) cache.moveInternalTo(root, FLAGS)
            val generation = cache.generation
            val remaining = requireNotNull(cache.removeVersion(key(1), second.version().id, generation))
            assertEquals(record.copy(versions = listOf(third.version())), remaining)
            assertEquals(remaining, newCache().read(key(1)).value)
            assertFalse(cache.write(key(1), record, generation))
            fails { cache.removeVersion(key(1), third.version().id, generation) }
            assertNull(cache.removeVersion(key(1), third.version().id, cache.generation)!!.bestVersionId)
            assertNull(cache.removeVersion(key(1), record.version().id, cache.generation))
            assertNull(newCache().read(key(1)).value)
            assertEquals(lyric(9), cache.read(key(9)).value)
            assertEquals(1L, cache.inspect().files)
            if (location == 1) assertTrue(internal.keys().isEmpty())
        }
    }

    @Test fun deletingLastInternalVersionAlsoRemovesOnlyItsAtomicArtifacts() = runBlocking(Dispatchers.IO) {
        seed(1); seed(2)
        File(directory, "${key(1)}.json.bak").writeBytes(LyricCacheFiles.encode(lyric(1)))
        File(directory, "${key(1)}.json.new").writeText("unfinished")
        val original = File(directory, "keep.lrc").apply { writeText("Synthetic original") }
        assertNull(cache.removeVersion(key(1), lyric(1).version().id, cache.generation))
        assertTrue(directory.listFiles()!!.none { LyricCacheFiles.localKey(it.name) == key(1) })
        assertEquals(lyric(2), cache.read(key(2)).value)
        assertEquals("Synthetic original", original.readText())
    }

    @Test fun portableLastVersionRemovalDeletesOlderRevisionsFirstAndCanRecoverAfterPartialFailure() = runBlocking(Dispatchers.IO) {
        seed(1); seed(9)
        cache.moveInternalTo(root, FLAGS)
        val folder = Uri.parse(cache.info.value.folder!!.documentUri)
        val bucket = child(folder, key(1).take(2))
        create(bucket, "${key(1)}-2.json", LyricCacheFiles.encode(lyric(1).copy(offsetMs = 1000)).toString(Charsets.UTF_8))
        create(bucket, "${key(1)}-3.json", LyricCacheFiles.encode(lyric(1).copy(offsetMs = 2000)).toString(Charsets.UTF_8))
        faults { putInt("failAfterDeletes", 1); putBoolean("resetCounters", true) }
        fails { cache.removeVersion(key(1), lyric(1).version().id, cache.generation) }
        assertTrue(cache.info.value.unavailable)
        assertEquals(2000L, newCache().read(key(1)).value!!.offsetMs)
        assertEquals(lyric(9), cache.read(key(9)).value)
        faults()
        assertNull(cache.removeVersion(key(1), lyric(1).version().id, cache.generation))
        assertNull(newCache().read(key(1)).value)
        assertEquals(1L, cache.inspect().files)
        assertTrue(internal.keys().isEmpty())
    }

    @Test fun failedPortableRemovalPreservesSelectionAndNeverFallsBackToInternalStorage() = runBlocking(Dispatchers.IO) {
        val track = track()
        val calls = mutableListOf<String>()
        val repository = selectionRepository(track, calls)
        cache = repository.cache
        repository.saveTiming(track, lyric(1), 100)
        val current = repository.saveTiming(track, lyric(2), 200)
        cache.moveInternalTo(root, FLAGS)
        var inventory = repository.available(track, current)
        faults { putInt("failAfterWrites", 0); putBoolean("resetCounters", true) }
        fails { repository.remove(track, inventory.choices.first(), current, inventory.generation) }
        assertEquals(current.lyrics, repository.latest.value.result!!.lyrics)
        faults()
        inventory = repository.available(track, current)
        assertEquals(2, inventory.choices.size)
        faults { putBoolean("unavailable", true) }
        fails { repository.remove(track, inventory.choices.first(), current, inventory.generation) }
        assertEquals(current.lyrics, repository.latest.value.result!!.lyrics)
        assertFalse(cache.info.value.internal)
        assertTrue(internal.keys().isEmpty())
        assertTrue(problems.isNotEmpty())
        assertTrue(calls.isEmpty())
        faults()
        assertEquals(current.document,
            selectionRepository(track, calls).load(track, online = true, allowNetwork = false).document)
    }

    @Test fun partialPortableRemovalReportsFailureAndReconcilesActualVersionsWithoutResurrectingThem() = runBlocking(Dispatchers.IO) {
        val track = track()
        val calls = mutableListOf<String>()
        val repository = selectionRepository(track, calls)
        cache = repository.cache
        val first = repository.saveTiming(track, lyric(1), 100)
        val current = repository.saveTiming(track, lyric(2), 200)
        cache.moveInternalTo(root, FLAGS)
        val inventory = repository.available(track, current)
        faults { putBoolean("denyDelete", true) }
        val failure = try {
            repository.remove(track, inventory.choices.single { it.key == inventory.currentKey }, current, inventory.generation)
            throw AssertionError("A verified new revision with failed old-copy cleanup is not complete success")
        } catch (expected: LyricRemovalFailure) { expected }
        assertEquals(first.lyrics, failure.removal.result.lyrics)
        assertEquals(1, failure.removal.choices.choices.size)
        assertFalse(failure.removal.choices.choices.any { it.key == current.document!!.version().id })
        assertEquals(failure.removal.result, repository.latest.value.result)
        assertFalse(cache.info.value.internal)
        assertTrue(internal.keys().isEmpty())
        faults()
        val adjusted = repository.saveTiming(track, failure.removal.result.document!!, 400)
        assertEquals(1, repository.available(track, adjusted).choices.size)
        assertEquals(2, faults().getInt("records"))
        assertTrue(calls.isEmpty())
    }

    @Test fun deletingLastVersionDefeatsOlderInFlightLookupOnDiskAndInDisplayedResult() = runBlocking(Dispatchers.IO) {
        val track = track()
        val entered = CompletableDeferred<Unit>()
        val released = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        val repository = selectionRepository(track, calls) { url ->
            if ("client_search_cp" in url) { entered.complete(Unit); released.await() }
        }
        cache = repository.cache
        val current = repository.saveTiming(track, lyric(1), 100)
        val pending = async { repository.load(track, online = true, refresh = true) }
        try {
            withTimeout(5000) { entered.await() }
            val count = calls.size
            val inventory = repository.available(track, current)
            val removal = repository.remove(track, inventory.choices.single(), current, inventory.generation)
            assertNull(removal.result.lyrics)
            assertEquals(count, calls.size)
            released.complete(Unit)
            assertNull(pending.await().lyrics)
            assertEquals(removal.result, repository.latest.value.result)
            assertEquals(0L, cache.inspect().files)
            assertTrue(repository.latest.value.userEdited)
        } finally { released.complete(Unit); pending.cancel() }
    }

    @Test fun inventoryNeverLabelsAnExpiredOrClearedCurrentSnapshotAsASavedCopy() = runBlocking(Dispatchers.IO) {
        val track = track()
        val repository = selectionRepository(track, mutableListOf())
        cache = repository.cache
        val current = repository.saveTiming(track, lyric(1), 100)
        cache.clear()
        val inventory = repository.available(track, current)
        assertEquals(current.document!!.version().id, inventory.currentKey)
        assertFalse(inventory.choices.single().cached)
        fails { repository.remove(track, inventory.choices.single(), current, inventory.generation) }
        assertEquals(0L, cache.inspect().files)
    }

    @Test fun onlineCatalogDoesNotWriteAndSelectedDownloadPreservesBestTimingAndRetrievalAge() = runBlocking(Dispatchers.IO) {
        val track = track()
        val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
        val repository = selectionRepository(track, calls)
        cache = repository.cache
        val original = repository.load(track, online = true)
        val inventory = repository.available(track, original)
        val marked = requireNotNull(repository.best(track, inventory.choices.single(), original, inventory.generation))
        val key = internal.keys().single()
        val before = cache.read(key).value
        val downloads = calls.count { "fcg_query_lyric_new" in it }
        val catalogs = repository.searchOnline(track).toList()
        assertEquals(5, catalogs.size)
        assertEquals(before, cache.read(key).value)
        assertEquals(downloads, calls.count { "fcg_query_lyric_new" in it })
        val choice = catalogs.flatMap { it.candidates }.single { it.key.id == "second" }
        val chosen = requireNotNull(repository.selectOnline(track, choice, cache.generation))
        assertEquals("Synthetic alternative", chosen.lyrics!!.lines.first().text)
        assertEquals(marked.document!!.bestVersionId, chosen.document!!.bestVersionId)
        assertTrue(chosen.availableOffline)
        assertFalse(chosen.document.selected)
        val oldAge = now - 86_400_000
        val timed = repository.saveTiming(track, chosen.document.copy(savedAtMs = oldAge), 750)
        val selectedAgain = requireNotNull(repository.selectOnline(track, choice, cache.generation))
        assertEquals(750L, selectedAgain.document!!.offsetMs)
        assertEquals(oldAge, selectedAgain.document.savedAtMs)
        assertEquals(timed.document!!.bestVersionId, selectedAgain.document.bestVersionId)
        assertEquals(2, repository.available(track, selectedAgain).choices.size)
        val fresh = selectionRepository(track, calls).load(track, online = true, allowNetwork = false)
        assertEquals(original.document!!.text, fresh.document!!.text)
    }

    @Test fun clearingCacheDuringSelectedOnlineDownloadRejectsTheLateSave() = runBlocking(Dispatchers.IO) {
        val track = track()
        val entered = CompletableDeferred<Unit>()
        val released = CompletableDeferred<Unit>()
        val repository = selectionRepository(track, mutableListOf()) { url ->
            if ("fcg_query_lyric_new" in url) { entered.complete(Unit); released.await() }
        }
        cache = repository.cache
        val original = repository.saveTiming(track, CachedLyric("[00:01]Synthetic retained", LyricOrigin.QQ, now), 100)
        val candidate = com.macarmusic.app.lyrics.OnlineLyricCandidate(
            com.macarmusic.app.lyrics.LyricCandidateId(com.macarmusic.app.lyrics.LyricSearchRoute.QQ, "second"),
            track.title, track.artist)
        val generation = cache.generation
        val pending = async { fails { repository.selectOnline(track, candidate, generation) } }
        try {
            withTimeout(5_000) { entered.await() }
            assertEquals(1L, cache.clear().files)
            released.complete(Unit)
            pending.await()
            assertEquals(0L, cache.inspect().files)
            assertEquals(original, repository.latest.value.result)
            fails { repository.selectOnline(track, candidate, generation) }
        } finally { released.complete(Unit); pending.cancel() }
    }

    private fun selectionRepository(track: Track, calls: MutableList<String>, before: suspend (String) -> Unit = {}) =
        LyricsRepository(context, app.repository, app.applicationScope, OnlineLyricsClient({ url, _, _ ->
            calls.add(url)
            before(url)
            when {
                "lyrics.kugou.com/search" in url -> """{"status":200,"candidates":[]}"""
                "mobilecdn.kugou.com" in url -> """{"status":1,"data":{"info":[]}}"""
                "client_search_cp" in url -> """{"code":0,"data":{"song":{"list":[
                    {"songname":"${track.title}","singer":[{"name":"${track.artist}"}],"songmid":"first"},
                    {"songname":"${track.title}","singer":[{"name":"${track.artist}"}],"songmid":"second"}]}}}"""
                "fcg_query_lyric_new" in url -> {
                    val name = if ("songmid=first" in url) "Synthetic original" else "Synthetic alternative"
                    val encoded = android.util.Base64.encodeToString("[00:01]$name\n[00:50]Synthetic next line".toByteArray(),
                        android.util.Base64.NO_WRAP)
                    """{"code":0,"lyric":"$encoded"}"""
                }
                "music.163.com" in url -> """{"code":200,"result":{"songCount":0}}"""
                "search.kuwo.cn" in url -> """{"abslist":[]}"""
                else -> throw AssertionError("Unexpected fixture request: $url")
            }
        }) { _, error -> throw AssertionError(error) }, nowMs = { now }, onCacheProblem = { problems.add(it) })

    private fun newCache() = LyricCache(context, {
        app.repository.awaitReady()
        app.repository.library.value.sources.mapTo(hashSetOf()) { it.uri }
    }, { problems.add(it) }, { now }, app.repository.documents.grantMutex, { internalExpiry }, { portableExpiry })

    private fun seed(number: Int) { internal.write(key(number), lyric(number)) }
    private fun lyric(number: Int) = CachedLyric("[00:01]Synthetic lyric $number", LyricOrigin.QQ, now)
    private fun key(number: Int) = String.format(Locale.ROOT, "%064x", number)
    private fun tree(id: String) = DocumentsContract.buildTreeDocumentUri("com.macarmusic.app.test.cache", id)
    private fun rootDocument(tree: Uri) = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    private fun track() = Track("cache-fixture", DocumentsContract.buildDocumentUri("com.macarmusic.app.test.documents", "music/one").toString(),
        "Synthetic cache song.wav", "Synthetic cache song", durationMs = 60_000)
    private fun trackKey(track: Track): String {
        val query = LyricQuery.from(track)
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest("v2\u0000${query.title}\u0000${query.artist}\u0000${query.durationMs}\u0000${track.fileName}".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun faults(edit: Bundle.() -> Unit = {}): Bundle = requireNotNull(
        app.contentResolver.call(root, "cacheFixture", null, Bundle().apply(edit)))

    private fun grantFlags(uri: Uri): Int {
        val permission = app.contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return 0
        return (if (permission.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (permission.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
    }

    private fun create(parent: Uri, name: String, text: String): Uri {
        val uri = requireNotNull(DocumentsContract.createDocument(app.contentResolver, parent, "application/json", name))
        requireNotNull(app.contentResolver.openOutputStream(uri, "wt")).use { it.write(text.toByteArray()) }
        return uri
    }

    private fun child(parent: Uri, name: String): Uri {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent))
        requireNotNull(app.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)).use {
            while (it.moveToNext()) if (it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) == name) {
                return DocumentsContract.buildDocumentUriUsingTree(parent,
                    it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)))
            }
        }
        throw AssertionError("Missing fixture child: $name")
    }

    private fun read(uri: Uri) = requireNotNull(app.contentResolver.openInputStream(uri)).bufferedReader().use { it.readText() }

    private suspend fun fails(action: suspend () -> Unit) {
        try { action(); fail("Expected an explicit cache operation failure") }
        catch (expected: IOException) { assertFalse(expected.message.isNullOrBlank()) }
    }

    private companion object {
        const val READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        const val FLAGS = READ_WRITE or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    }
}
