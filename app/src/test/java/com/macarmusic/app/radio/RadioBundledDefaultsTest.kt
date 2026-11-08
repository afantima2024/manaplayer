package com.macarmusic.app.radio

import com.macarmusic.app.data.TextStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.UUID

class RadioBundledDefaultsTest {
    private fun bundled() = RadioBundledDefaults.load { "Preset $it" }
    private class Store(var text: String? = null, var failWrite: Boolean = false) : TextStore {
        var writes = 0
        override fun read() = text
        override fun write(text: String) {
            if (failWrite) throw IOException("Fixture storage failure")
            this.text = text
            writes++
        }
    }

    @Test fun shippedSnapshotsAreCompleteEnabledAndMergeCctvWithoutDuplicatingTheProgram() {
        val subscriptions = bundled()
        assertEquals(4, subscriptions.size)
        assertTrue(subscriptions.all { it.enabled && it.checkedAtMs > 0 && it.programs.isNotEmpty() })
        assertTrue(subscriptions[0].url.endsWith("/top/top-500.m3u"))
        assertTrue(subscriptions[1].url.endsWith("/countries/us.m3u"))
        assertTrue(subscriptions[2].url.endsWith("/countries/tw.m3u"))
        assertEquals(RadioSubscriptions.CCTV_AUDIO_URL, subscriptions[3].url)
        assertEquals(20, subscriptions[3].programs.size)
        assertTrue(subscriptions[3].programs.all { RadioGroups.of(it) == RadioGroups.cctvTelevision && it.logoUri.isEmpty() })
        listOf("world", "us", "tw", "cctv").forEachIndexed { index, name ->
            val parsed = requireNotNull(javaClass.getResourceAsStream("/radio/subscriptions/$name.m3u"))
                .use { RadioList.parse(it, subscription = true) }
            assertEquals(0, parsed.invalid)
            assertEquals(parsed.entries.map { it.uri }.toSet(),
                subscriptions[index].programs.flatMap { it.sources }.toSet())
        }
        val catalog = RadioSubscriptions.catalog(RadioDefaults.bundled, subscriptions, emptyMap())
        val cctv = catalog.single { it.id == RadioDefaults.CCTV_ID }
        assertEquals(2, cctv.sources.size)
        assertTrue(cctv.sources.all { "/audio/cctv13_2.m3u8" in it })
        assertEquals(RadioGroups.cctvTelevision, RadioGroups.of(cctv))
        assertEquals(RadioDefaults.CCTV_ID, RadioDefaults.pins.first())
        assertEquals(1, catalog.count { station -> station.sources.any { "/audio/cctv13_2.m3u8" in it } })
        assertEquals(20, catalog.count { station -> station.sources.any { "/audio/cctv" in it } })
        println("Bundled subscriptions: ${subscriptions.map { it.name to it.programs.size }}; combined=${catalog.size}")
    }

    @Test fun upgradeAddsOnlyCctvAndPreservesDisabledRemovedAndEditedOlderSubscriptions() {
        val seeds = bundled()
        val disabled = seeds[0].copy(name = "My list", enabled = false, programs = seeds[0].programs.take(2),
            etag = "\"custom\"", validatorUrl = seeds[0].url)
        val local = seeds[1].programs.first().copy(name = "Local edit")
        val extra = RadioSubscription(UUID.randomUUID().toString(), "Extra", "https://example.com/extra")
        val saved = RadioCatalogSnapshot(listOf(local), includeDefaults = false,
            subscriptions = listOf(disabled, extra), sourceSelections = mapOf(local.id to local.uri),
            removedDefaults = setOf(RadioDefaults.CCTV_ID), bundledRevision = 1, initializeBundledSettings = true)
        val upgraded = RadioBundledDefaults.install(saved, seeds)
        assertEquals(saved.copy(subscriptions = saved.subscriptions + seeds.last(),
            bundledRevision = RadioBundledDefaults.REVISION), upgraded)
        assertSame(upgraded, RadioBundledDefaults.install(upgraded, seeds))
        val alreadyAdded = saved.copy(subscriptions = saved.subscriptions + seeds.last().copy(enabled = false))
        assertEquals(alreadyAdded.subscriptions, RadioBundledDefaults.install(alreadyAdded, seeds).subscriptions)
    }

    @Test fun existingCctvCacheChangesCategoryWithoutAnotherMigrationOrWrite() {
        val feeds = bundled().map { feed ->
            if (feed.url == RadioSubscriptions.CCTV_AUDIO_URL)
                feed.copy(programs = feed.programs.map { it.copy(group = "National") }, enabled = false)
            else feed
        }
        val saved = RadioCatalogSnapshot(emptyList(), subscriptions = feeds, bundledRevision = 2)
        assertSame(saved, RadioBundledDefaults.install(saved, bundled()))
        assertTrue(saved.subscriptions.last().programs.all { it.group == "National" })
        assertTrue(saved.subscriptions.last().programs.all { RadioGroups.of(it) == RadioGroups.cctvTelevision })
        assertFalse(saved.subscriptions.last().enabled)
        assertSame(saved, RadioBundledDefaults.install(saved, emptyList()))
    }

    @Test fun upgradeCapacityFailureLeavesThePriorSnapshotUntouched() {
        val subscriptions = List(RadioSubscriptions.MAX_SUBSCRIPTIONS) {
            RadioSubscription(UUID.randomUUID().toString(), "Custom $it", "https://example.com/$it")
        }
        val saved = RadioCatalogSnapshot(emptyList(), subscriptions = subscriptions, bundledRevision = 1)
        try {
            RadioBundledDefaults.install(saved, bundled())
            fail("Upgrade must not evict an existing subscription")
        } catch (error: IllegalArgumentException) {
            assertEquals("Too many radio subscriptions", error.message)
        }
        assertEquals(subscriptions, saved.subscriptions)
        assertEquals(1, saved.bundledRevision)
    }

    @Test fun initialVisibilityKeepsOnlyTheRequestedGroupsAndAlsoHidesNewKnownCountries() {
        val catalog = RadioSubscriptions.catalog(RadioDefaults.bundled, bundled(), emptyMap())
        val hidden = RadioGroups.defaultHiddenGroups(catalog)
        val visible = catalog.map { RadioGroups.of(it).id }.toSet() - hidden
        assertEquals(RadioGroups.defaultVisibleIds, visible)
        assertTrue("country:de" in hidden)
        assertTrue("country:fr" in hidden)
        assertTrue("zhejiang" in hidden)
        assertTrue("other" in hidden)
        assertTrue(hidden.all(RadioGroup::validId))
    }

    @Test fun countryAndNationalAliasesKeepTheirGroupIdentity() {
        val aliases = mapOf(
            "china" to "CN|China|中国|中國|中国大陆|中國大陸",
            "national" to "CNR|CRI|National|National stations|全国|全国电台|中央电台|央广",
            "united states" to "US|USA|United States of America",
            "united kingdom" to "UK|GB|Great Britain",
            "hong kong" to "HK|Hong Kong SAR China|香港特别行政区",
            "taiwan" to "TW|Taiwan (Province of China)",
            "macao" to "MO|Macau SAR China|Macao SAR China",
        )
        aliases.forEach { (id, names) -> names.split('|').forEach { name ->
            assertEquals(name, id, RadioGroups.of(RadioDefaults.corePrograms.first().copy(group = name)).id)
        } }
    }

    @Test fun migrationPreservesLocalEditsExtraFeedsIdentitiesAndNewerDownloads() {
        val seeds = bundled()
        val local = RadioDefaults.bundled.first { it.builtin == null }.copy(name = "My edited station")
        val previous = seeds[0].copy(id = UUID.randomUUID().toString(), name = "My named list",
            enabled = false, checkedAtMs = 0, updatedAtMs = 0, programs = emptyList())
        val newer = seeds[1].copy(enabled = false, checkedAtMs = seeds[1].checkedAtMs + 1000,
            programs = listOf(local), etag = "\"newer\"")
        val extra = RadioSubscription(UUID.randomUUID().toString(), "My extra list", "https://example.com/my-list")
        val saved = RadioCatalogSnapshot(listOf(local), includeDefaults = false,
            subscriptions = listOf(previous, newer, extra), sourceSelections = mapOf(local.id to local.uri))
        val migrated = RadioBundledDefaults.install(saved, seeds)
        assertEquals(saved.programs, migrated.programs)
        assertFalse(migrated.includeDefaults)
        assertEquals(saved.sourceSelections, migrated.sourceSelections)
        assertEquals(previous.id, migrated.subscriptions[0].id)
        assertEquals(previous.name, migrated.subscriptions[0].name)
        assertEquals(seeds[0].programs, migrated.subscriptions[0].programs)
        assertEquals(newer.copy(enabled = true), migrated.subscriptions[1])
        assertEquals(extra, migrated.subscriptions.last())
        assertTrue(migrated.subscriptions.all { it.enabled })
        assertTrue(migrated.initializeBundledSettings)
    }

    @Test fun failedNewerCheckDoesNotMakeAnOlderCachedDownloadFresherThanTheBundle() {
        val seeds = bundled()
        val old = seeds.first().copy(name = "My list", programs = seeds.first().programs.take(1),
            checkedAtMs = seeds.first().checkedAtMs + 2000, updatedAtMs = seeds.first().checkedAtMs - 1000,
            failure = RadioSubscriptionFailure(RadioSubscriptionProblem.HTTP, 503))
        val migrated = RadioBundledDefaults.install(RadioCatalogSnapshot(emptyList(), subscriptions = listOf(old)), seeds)
        assertEquals(seeds.first().copy(id = old.id, name = old.name), migrated.subscriptions.first())
        val newerGoodCache = old.copy(updatedAtMs = seeds.first().checkedAtMs + 1000)
        assertEquals(newerGoodCache, RadioBundledDefaults.install(RadioCatalogSnapshot(emptyList(),
            subscriptions = listOf(newerGoodCache)), seeds).subscriptions.first())
    }

    @Test fun catalogMigrationPersistsOnceAndNeverReenablesRemovedOrDisabledFeedsOnRestart() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = Store()
            var reads = 0
            fun repository() = RadioCatalogRepository(store, scope, loadDefaults = { RadioDefaults.bundled },
                loadSubscriptions = { reads++; bundled() }) { throw AssertionError(it) }
            val first = repository()
            first.awaitReady()
            assertEquals(1, store.writes)
            assertEquals(1, reads)
            val selected = first.state.value.subscriptions.first()
            first.changeSubscription(selected, enabled = false)
            first.changeSubscription(first.state.value.subscriptions.last(), remove = true)
            val snapshot = first.snapshot()
            assertEquals(RadioBundledDefaults.REVISION, snapshot.bundledRevision)
            val before = store.text
            val writes = store.writes
            val second = repository()
            second.awaitReady()
            assertEquals(snapshot, second.snapshot())
            assertEquals(before, store.text)
            assertEquals(writes, store.writes)
            assertEquals(1, reads)
            assertSame(snapshot, RadioBundledDefaults.install(snapshot, bundled()))
        } finally { scope.cancel() }
    }

    @Test fun explicitRestoresAndReplacementImportsNeverResurrectSubscriptions() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = Store()
            val repository = RadioCatalogRepository(store, scope, loadSubscriptions = ::bundled) { throw AssertionError(it) }
            repository.awaitReady()
            repository.withReplacement(emptyList(), includeDefaults = false) { Unit }
            assertEquals(RadioBundledDefaults.REVISION, repository.snapshot().bundledRevision)
            assertFalse(repository.snapshot().initializeBundledSettings)
            val restored = RadioCatalogRepository(store, scope, loadSubscriptions = {
                throw AssertionError("A restored catalog must not reload defaults")
            }) { throw AssertionError(it) }
            restored.awaitReady()
            assertTrue(restored.state.value.programs.isEmpty())
            assertTrue(restored.state.value.subscriptions.isEmpty())
            val imported = listOf(RadioListEntry("Only this", "https://example.com/only"))
            restored.merge(imported, emptyMap(), mode = RadioImportMode.REPLACE_ALL)
            val again = RadioCatalogRepository(store, scope, loadSubscriptions = {
                throw AssertionError("Replace-all must not reload defaults")
            }) { throw AssertionError(it) }
            again.awaitReady()
            assertEquals(listOf("Only this"), again.state.value.programs.map { it.name })
            assertFalse(again.state.value.initializeBundledSettings)
        } finally { scope.cancel() }
    }

    @Test fun replacementAfterFailedMigrationSuppressesBothSubscriptionsAndInitialSettings() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = Store(failWrite = true)
            var reads = 0
            val errors = mutableListOf<Exception>()
            fun repository() = RadioCatalogRepository(store, scope, loadSubscriptions = { reads++; bundled() },
                reportDefaults = { errors.add(it) }) { throw AssertionError(it) }
            val first = repository()
            first.awaitReady()
            assertEquals(1, errors.size)
            assertEquals(0, first.snapshot().bundledRevision)
            store.failWrite = false
            first.merge(listOf(RadioListEntry("My replacement", "https://example.com/only")), emptyMap(),
                mode = RadioImportMode.REPLACE_ALL)
            val second = repository()
            second.awaitReady()
            assertEquals(1, reads)
            assertEquals(RadioBundledDefaults.REVISION, second.snapshot().bundledRevision)
            assertFalse(second.state.value.initializeBundledSettings)
            assertTrue(second.state.value.subscriptions.isEmpty())
            assertEquals(listOf("My replacement"), second.state.value.programs.map { it.name })
        } finally { scope.cancel() }
    }

    @Test fun failedOrOverCapacityMigrationReportsAnErrorWithoutChangingSavedLists() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            for (capacity in listOf(false, true)) {
                val extras = if (capacity) List(10) { RadioSubscription(UUID.randomUUID().toString(),
                    "Extra $it", "https://example.com/list-$it") } else emptyList()
                val text = RadioList.encode(emptyList(), false, extras)
                val store = Store(text, failWrite = !capacity)
                val errors = mutableListOf<Exception>()
                val repository = RadioCatalogRepository(store, scope, loadSubscriptions = ::bundled,
                    reportDefaults = { errors.add(it) }) { throw AssertionError(it) }
                repository.awaitReady()
                assertEquals(1, errors.size)
                assertNull(repository.state.value.error)
                assertEquals(extras, repository.state.value.subscriptions)
                assertEquals(0, repository.snapshot().bundledRevision)
                assertEquals(text, store.text)
                assertEquals(0, store.writes)
            }
        } finally { scope.cancel() }
    }
}
