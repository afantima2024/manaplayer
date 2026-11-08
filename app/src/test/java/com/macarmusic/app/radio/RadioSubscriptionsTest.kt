package com.macarmusic.app.radio

import com.macarmusic.app.R
import com.macarmusic.app.data.TextStore
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.UUID

class RadioSubscriptionsTest {
    private class Store(var text: String? = null) : TextStore {
        var writes = 0
        var failWrite = false
        override fun read() = text
        override fun write(text: String) {
            if (failWrite) throw IOException("Fixture write failure")
            this.text = text
            writes++
        }
    }
    private fun entry(name: String, source: String) = RadioListEntry(name, "https://audio.example.com/$source")
    private fun download(vararg entries: RadioListEntry) = RadioSubscriptionDownload(entries.toList(),
        etag = "\"fixture\"", validatorUrl = "https://lists.example.com/a")
    private fun run(initial: String? = null, block: suspend (RadioCatalogRepository, Store, CoroutineScope) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = Store(initial)
        try {
            val repository = RadioCatalogRepository(store, scope, loadDefaults = { emptyList() }) { throw AssertionError(it) }
            repository.awaitReady()
            block(repository, store, scope)
        } finally { scope.cancel() }
    }

    @Test fun legacyCatalogsAndNewDefaultsDoNotCreateOrFetchSubscriptions() {
        val legacy = RadioList.decode("""{"version":3,"programs":[],"includeDefaults":false}""")
        assertTrue(legacy.subscriptions.isEmpty())
        assertTrue(legacy.sourceSelections.isEmpty())
        assertFalse(legacy.includeDefaults)
        assertEquals(legacy, RadioList.decode(RadioList.encode(emptyList(), false)))
    }

    @Test fun subscriptionsAreExplicitSeparateAndCanonicalUrlsCannotBeAddedTwice() = run { repository, store, _ ->
        assertTrue(repository.state.value.subscriptions.isEmpty())
        assertEquals(0, store.writes)
        val original = repository.saveSubscription("One", "HTTPS://LISTS.example.com:443/a")
        assertEquals("https://lists.example.com/a", original.url)
        assertTrue(repository.state.value.programs.isEmpty())
        assertEquals(0L, original.checkedAtMs)
        try { repository.saveSubscription("Duplicate", "https://lists.example.com/a"); fail() }
        catch (error: RadioListException) { assertEquals(R.string.radio_subscription_duplicate, error.messageId) }
        assertEquals(1, repository.snapshot().subscriptions.size)
        assertEquals(1, store.writes)
    }

    @Test fun cachedSourceChoicesDoNotRejectLargeLegacyListsWithoutContributingSubscriptions() {
        val saved = List(RadioList.MAX_PROGRAMS) {
            RadioProgram("radio:custom:" + it.toString(16).padStart(64, '0'), "Legacy $it",
                "https://audio.example.com/$it", alternateUris = listOf("https://audio.example.com/$it/alternate"))
        }
        val base = RadioList.catalog(saved)
        assertTrue(base.size > RadioList.MAX_PROGRAMS)
        val selected = saved.last()
        val subscription = RadioSubscription(UUID.randomUUID().toString(), "Not updated", "https://lists.example.com/legacy")
        for (subscriptions in listOf(emptyList(), listOf(subscription),
            listOf(subscription.copy(enabled = false, programs = listOf(saved.first()))))) {
            val result = RadioSubscriptions.catalog(base, subscriptions, mapOf(selected.id to selected.sources.last()))
            assertEquals(base.size, result.size)
            assertEquals(selected.sources.last(), result.single { it.id == selected.id }.streamUri)
            assertSame(base.first(), result.first())
        }
    }

    @Test fun onlyTheKnownHacksPageMapsToItsPublishedRawPlaylist() {
        assertEquals("https://live.hacks.tools/radio/m3u/index.m3u",
            RadioSubscriptions.address("https://iptv.hacks.tools/content/fm-radio/m3u/"))
        assertEquals("https://example.com/page", RadioSubscriptions.address("https://example.com/page"))
        assertNull(RadioSubscriptions.address("file:///tmp/radio.m3u"))
        assertNull(RadioSubscriptions.address("https://user:password@example.com/list"))
        assertNull(RadioSubscriptions.address("https://example.com/list#fragment"))
        assertNull(RadioSubscriptions.address("javascript:alert(1)"))
    }

    @Test fun presetUrlsAreUniqueHttpsAndIncludeChinaHongKongTaiwanUsUkWorldwideAndCctv() {
        val presets = RadioSubscriptions.presets
        assertEquals(7, presets.size)
        assertEquals(7, presets.map { it.url }.distinct().size)
        assertTrue(presets.all { it.url.startsWith("https://") && RadioSubscriptions.address(it.url) == it.url })
        assertTrue(presets.any { "/countries/us.m3u" in it.url })
        assertTrue(presets.any { "/countries/gb.m3u" in it.url })
        assertTrue(presets.any { "/countries/hk.m3u" in it.url })
        assertTrue(presets.any { "/countries/tw.m3u" in it.url })
        assertTrue(presets.any { "/top/top-500.m3u" in it.url })
        assertTrue(presets.any { "fanmingming.com" in it.url })
        assertTrue(presets.any { it.url == RadioSubscriptions.CCTV_AUDIO_URL })
    }

    @Test fun underscoreRadioHostsUseStrictHttpValidationAndCanonicalPorts() {
        assertEquals("http://radio_station.example.com/live",
            RadioAddress.canonical("HTTP://RADIO_STATION.example.com:80/live"))
        assertEquals("https://radio_station.example.com:8443/live",
            RadioAddress.canonical("https://radio_station.example.com:8443/live"))
        for (invalid in listOf("http://name:secret@radio_station.example.com/live",
            "http://radio_station.example.com:65536/live", "http://radio_station.example.com:0/live",
            "http://radio_station.example.com:-1/live",
            "http://radio_station.example.com:bad/live", "http://radio_station.example.com/live#fragment",
            "http://radio_station.example.com/a b", "file://radio_station.example.com/live")) {
            assertNull(invalid, RadioAddress.canonical(invalid))
        }
    }

    @Test fun tenSubscriptionLimitIsEnforcedWithoutChangingExistingState() = run { repository, store, _ ->
        repeat(10) { repository.saveSubscription("List $it", "https://lists.example.com/$it") }
        val state = repository.state.value
        val text = store.text
        try { repository.saveSubscription("Eleven", "https://lists.example.com/11"); fail() }
        catch (error: RadioListException) { assertEquals(R.string.radio_subscription_limit, error.messageId) }
        assertSame(state, repository.state.value)
        assertEquals(text, store.text)
    }

    @Test fun matchingStationsMergeSourcesWithoutLosingOwnershipOrManualAddresses() = run { repository, _, _ ->
        repository.merge(listOf(entry("Shared station", "manual")), emptyMap())
        val local = repository.savedPrograms().single()
        val a = repository.saveSubscription("A", "https://lists.example.com/a")
        val b = repository.saveSubscription("B", "https://lists.example.com/b")
        repository.refreshSubscription(a.id, emptyMap(), { download(entry("Shared station", "first"), entry("Only A", "a")) })
        repository.refreshSubscription(b.id, emptyMap(), { download(entry("shared station", "first"), entry("Shared station", "second")) })
        assertEquals(listOf(local), repository.savedPrograms())
        assertEquals(2, repository.state.value.programs.size)
        assertEquals(listOf("manual", "first", "second").map { "https://audio.example.com/$it" },
            repository.find(local.id)!!.sources)
        repository.changeSubscription(repository.snapshot().subscriptions.first(), remove = true)
        assertEquals(1, repository.state.value.programs.size)
        assertEquals(3, repository.find(local.id)!!.sources.size)
        repository.changeSubscription(repository.snapshot().subscriptions.single(), remove = true)
        assertEquals(listOf(local), repository.state.value.programs)
    }

    @Test fun sourceOnlyMatchesAlsoMergeAndUnsubscribingTheFirstListKeepsStableNameIds() = run { repository, _, _ ->
        val a = repository.saveSubscription("A", "https://lists.example.com/a")
        val b = repository.saveSubscription("B", "https://lists.example.com/b")
        repository.refreshSubscription(a.id, emptyMap(), { download(entry("Shared", "one").copy(id = "provider-a")) })
        val id = repository.state.value.programs.single().id
        repository.refreshSubscription(b.id, emptyMap(), { download(entry("Shared", "two").copy(id = "provider-b")) })
        assertEquals(id, repository.state.value.programs.single().id)
        repository.changeSubscription(repository.snapshot().subscriptions.first(), remove = true)
        assertEquals(id, repository.state.value.programs.single().id)
        repository.refreshSubscription(b.id, emptyMap(), { download(entry("Different spelling", "two")) })
        repository.merge(listOf(entry("Local alias", "two")), emptyMap())
        assertEquals(1, repository.state.value.programs.size)
        assertEquals(1, repository.state.value.programs.single().sources.size)
    }

    @Test fun refreshReplacesOnlyItsOwnCachedSourcesInsteadOfPermanentlyImportingThem() = run { repository, _, _ ->
        val a = repository.saveSubscription("A", "https://lists.example.com/a")
        repository.refreshSubscription(a.id, emptyMap(), { download(entry("Station", "old"), entry("Removed", "removed")) })
        val station = repository.state.value.programs.first()
        repository.refreshSubscription(a.id, emptyMap(), { download(entry("Station", "new")) })
        assertEquals(1, repository.state.value.programs.size)
        assertEquals(listOf("https://audio.example.com/new"), repository.find(station.id)!!.sources)
        assertTrue(repository.savedPrograms().isEmpty())
    }

    @Test fun unchangedListsAnd304KeepProgramIdentityAndPlaybackRevision() = run { repository, store, _ ->
        val a = repository.saveSubscription("A", "https://lists.example.com/a")
        val revision = repository.state.value.localRevision
        assertEquals(RadioSubscriptionResult.UPDATED,
            repository.refreshSubscription(a.id, emptyMap(), { download(entry("Station", "one")) }, { 10 }))
        val programs = repository.state.value.programs
        assertEquals(RadioSubscriptionResult.UNCHANGED,
            repository.refreshSubscription(a.id, emptyMap(), { download(entry("Station", "one")) }, { 20 }))
        assertSame(programs, repository.state.value.programs)
        assertEquals(RadioSubscriptionResult.UNCHANGED,
            repository.refreshSubscription(a.id, emptyMap(), {
                RadioSubscriptionDownload(etag = it.etag, validatorUrl = it.validatorUrl)
            }, { 30 }))
        assertSame(programs, repository.state.value.programs)
        assertEquals(revision, repository.state.value.localRevision)
        val saved = RadioList.decode(store.text!!).subscriptions.single()
        assertEquals(10L, saved.updatedAtMs)
        assertEquals(30L, saved.checkedAtMs)
    }

    @Test fun selectedSubscribedSourcePersistsWithoutTurningTheCacheIntoManualStations() = run { repository, store, scope ->
        val a = repository.saveSubscription("A", "https://lists.example.com/a")
        repository.refreshSubscription(a.id, emptyMap(), { download(entry("Station", "first"), entry("Station", "second")) })
        val station = repository.state.value.programs.single()
        repository.selectSource(station.id, 1, station.sources[1])
        assertTrue(repository.savedPrograms().isEmpty())
        val snapshot = RadioList.decode(store.text!!)
        assertEquals(station.sources[1], snapshot.sourceSelections[station.id])
        val reloaded = RadioCatalogRepository(store, scope, loadDefaults = { emptyList() }) { throw AssertionError(it) }
        reloaded.awaitReady()
        assertEquals(station.sources[1], reloaded.find(station.id)!!.streamUri)
        reloaded.refreshSubscription(a.id, emptyMap(), { download(entry("Station", "second"), entry("Station", "new")) })
        assertEquals(station.sources[1], reloaded.find(station.id)!!.streamUri)
        assertEquals(2, reloaded.find(station.id)!!.sources.size)
        reloaded.changeSubscription(reloaded.snapshot().subscriptions.single(), remove = true)
        assertTrue(reloaded.snapshot().sourceSelections.isEmpty())
    }

    @Test fun disabledListsKeepCachedDataAndKnownPinIdsWithoutBeingFetchedOnReload() = run { repository, store, scope ->
        val a = repository.saveSubscription("A", "https://lists.example.com/a")
        repository.refreshSubscription(a.id, emptyMap(), { download(entry("Station", "first")) })
        val station = repository.state.value.programs.single()
        repository.changeSubscription(repository.snapshot().subscriptions.single(), enabled = false)
        assertTrue(repository.state.value.programs.isEmpty())
        assertTrue(station.id in repository.state.value.knownIds)
        val writes = store.writes
        val loaded = RadioCatalogRepository(store, scope, loadDefaults = { emptyList() }) { throw AssertionError(it) }
        loaded.awaitReady()
        assertTrue(loaded.state.value.programs.isEmpty())
        assertEquals(writes, store.writes)
        loaded.changeSubscription(loaded.snapshot().subscriptions.single(), enabled = true)
        assertEquals(station, loaded.state.value.programs.single())
    }

    @Test fun emptyInvalidAndFailedDownloadsPreserveTheLastGoodListAndSurfaceFailure() = run { repository, _, _ ->
        val a = repository.saveSubscription("A", "https://lists.example.com/a")
        repository.refreshSubscription(a.id, emptyMap(), { download(entry("Station", "first")) })
        val programs = repository.state.value.programs
        assertEquals(RadioSubscriptionResult.FAILED, repository.refreshSubscription(a.id, emptyMap(), { download() }))
        assertSame(programs, repository.state.value.programs)
        assertEquals(RadioSubscriptionProblem.INVALID_LIST, repository.snapshot().subscriptions.single().failure!!.problem)
        assertEquals(RadioSubscriptionResult.FAILED, repository.refreshSubscription(a.id, emptyMap(), {
            throw RadioSubscriptionException(RadioSubscriptionFailure(RadioSubscriptionProblem.HTTP, 503))
        }))
        assertSame(programs, repository.state.value.programs)
        assertEquals(503, repository.snapshot().subscriptions.single().failure!!.httpStatus)
        repository.refreshSubscription(a.id, emptyMap(), { download(entry("Station", "first")) })
        assertNull(repository.snapshot().subscriptions.single().failure)
    }

    @Test fun failedCacheWritesDoNotPublishSuccessOrLosePreviousData() = run { repository, store, _ ->
        val a = repository.saveSubscription("A", "https://lists.example.com/a")
        repository.refreshSubscription(a.id, emptyMap(), { download(entry("Station", "first")) })
        val state = repository.state.value
        val text = store.text
        store.failWrite = true
        try { repository.refreshSubscription(a.id, emptyMap(), { download(entry("Station", "changed")) }); fail() }
        catch (_: IOException) { }
        assertSame(state, repository.state.value)
        assertEquals(text, store.text)
    }

    @Test fun requestsDoNotHoldTheCatalogLockAndStaleDownloadsCannotResurrectRemovedSubscriptions() =
        run { repository, _, scope ->
            val a = repository.saveSubscription("A", "https://lists.example.com/a")
            val began = CompletableDeferred<Unit>()
            val resume = CompletableDeferred<Unit>()
            val work = scope.async {
                try {
                    repository.refreshSubscription(a.id, emptyMap(), {
                        began.complete(Unit); resume.await(); download(entry("Station", "late"))
                    })
                    null
                } catch (error: RadioListException) { error }
            }
            began.await()
            withTimeout(1500) { repository.changeSubscription(a, remove = true) }
            resume.complete(Unit)
            assertEquals(R.string.radio_subscription_changed, work.await()!!.messageId)
            assertTrue(repository.state.value.programs.isEmpty())
            assertTrue(repository.snapshot().subscriptions.isEmpty())
        }

    @Test fun cancellationNeverPublishesAnIncompleteDownload() = run { repository, store, scope ->
        val a = repository.saveSubscription("A", "https://lists.example.com/a")
        val state = repository.state.value
        val text = store.text
        val began = CompletableDeferred<Unit>()
        val work = scope.launch {
            repository.refreshSubscription(a.id, emptyMap(), {
                began.complete(Unit); awaitCancellation()
            })
        }
        began.await()
        work.cancelAndJoin()
        assertSame(state, repository.state.value)
        assertEquals(text, store.text)
    }

    @Test fun replaceAllDisablesSubscriptionsAndBackupReplacementRollsBackTheirOwnership() = run { repository, store, _ ->
        val a = repository.saveSubscription("A", "https://lists.example.com/a")
        repository.refreshSubscription(a.id, emptyMap(), { download(entry("From feed", "one")) })
        val original = repository.snapshot()
        val text = store.text
        try {
            repository.withReplacement(emptyList(), false, emptyList()) { throw IOException("Fixture failed backup") }
            fail()
        } catch (_: IOException) { }
        assertEquals(original, repository.snapshot())
        assertEquals(text, store.text)
        repository.merge(listOf(entry("From file", "two")), emptyMap(), mode = RadioImportMode.REPLACE_ALL)
        assertEquals("From file", repository.state.value.programs.single().name)
        assertFalse(repository.snapshot().subscriptions.single().enabled)
        assertEquals(original.subscriptions.single().programs, repository.snapshot().subscriptions.single().programs)
        repository.withReplacement(original.programs, original.includeDefaults, original.subscriptions, original.sourceSelections) { Unit }
        assertEquals(original.copy(bundledRevision = RadioBundledDefaults.REVISION), repository.snapshot())
    }

    @Test fun fiveThousandSourceLimitIncludesDisabledCachesAndRejectsOnlyTheNewUpdate() = run { repository, _, _ ->
        val a = repository.saveSubscription("Large", "https://lists.example.com/a")
        val entries = (1..5000).map { entry("Station $it", "large/$it") }
        assertEquals(RadioSubscriptionResult.UPDATED, repository.refreshSubscription(a.id, emptyMap(), {
            RadioSubscriptionDownload(entries)
        }))
        assertEquals(5000, repository.state.value.programs.size)
        repository.changeSubscription(repository.snapshot().subscriptions.single(), enabled = false)
        val b = repository.saveSubscription("Overflow", "https://lists.example.com/b")
        assertEquals(RadioSubscriptionResult.FAILED,
            repository.refreshSubscription(b.id, emptyMap(), { download(entry("Overflow", "overflow")) }))
        assertEquals(5000, repository.snapshot().subscriptions.first().programs.size)
        assertTrue(repository.snapshot().subscriptions.last().programs.isEmpty())
        assertEquals(RadioSubscriptionProblem.LIMIT, repository.snapshot().subscriptions.last().failure!!.problem)
    }

    @Test fun savedSubscriptionValidationRejectsHeaderInjectionAndBadIds() {
        val subscription = RadioSubscription(UUID.randomUUID().toString(), "Test", "https://lists.example.com/a")
        for (invalid in listOf(subscription.copy(etag = "ok\r\nX-Injected: yes"),
            subscription.copy(id = "../bad"), subscription.copy(name = "Bad\nname"),
            subscription.copy(updatedAtMs = 100, checkedAtMs = 99))) {
            try { RadioList.encode(emptyList(), subscriptions = listOf(invalid)); fail("Invalid subscription accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
