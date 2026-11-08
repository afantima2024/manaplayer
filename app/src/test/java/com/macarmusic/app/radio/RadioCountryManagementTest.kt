package com.macarmusic.app.radio

import com.macarmusic.app.R
import com.macarmusic.app.data.TextStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.UUID

class RadioCountryManagementTest {
    private class Store(var text: String) : TextStore {
        var failWrite = false
        var writes = 0
        override fun read() = text
        override fun write(text: String) {
            if (failWrite) throw IOException("Fixture write failure")
            this.text = text
            writes++
        }
    }
    private fun station(index: Int, group: String, name: String = "Station $index", uri: String = "https://audio.example/$index") =
        RadioProgram("radio:custom:${index.toString(16).padStart(64, '0')}", name, uri, group)
    private fun subscription(name: String, programs: List<RadioProgram>, enabled: Boolean = true) =
        RadioSubscription(UUID.randomUUID().toString(), name, "https://lists.example/$name", enabled = enabled,
            programs = programs, etag = "\"$name\"", lastModified = "Sun, 01 Nov 2026 00:00:00 GMT",
            validatorUrl = "https://lists.example/$name")
    private fun run(snapshot: RadioCatalogSnapshot, defaults: List<RadioProgram> = emptyList(),
        block: suspend (RadioCatalogRepository, Store, CoroutineScope) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = Store(RadioList.encode(snapshot.programs, snapshot.includeDefaults, snapshot.subscriptions,
            snapshot.sourceSelections, snapshot.removedDefaults))
        try {
            val repository = RadioCatalogRepository(store, scope, { defaults }) { throw AssertionError(it) }
            repository.awaitReady()
            block(repository, store, scope)
        } finally { scope.cancel() }
    }

    @Test fun deletionRemovesAllOwnersWithoutFlatteningOtherCountriesOrSubscriptions() {
        val embedded = station(1, "US")
        val national = CnrRadio.stations.first().program
        val local = station(2, "USA", "Local US", "https://audio.example/shared")
        val uk = station(3, "UK")
        val onlyFeed = station(4, "美国")
        val renamed = station(5, "Canada", "Another name", "HTTPS://AUDIO.example:443/shared")
        val sameName = station(6, "US", "LOCAL US")
        val cached = station(7, "USA")
        val feeds = listOf(subscription("inactive", listOf(renamed, cached), false),
            subscription("one", listOf(renamed, onlyFeed, uk)), subscription("two", listOf(sameName)),
            subscription("untouched", listOf(uk)))
        val initial = RadioCatalogSnapshot(listOf(local, uk), subscriptions = feeds,
            sourceSelections = mapOf(local.id to sameName.uri))
        run(initial, listOf(embedded, national)) { repository, store, scope ->
            val before = repository.state.value
            assertEquals(3, repository.deleteGroups(setOf("united states"), before.snapshot))
            val after = repository.snapshot()
            assertTrue(after.includeDefaults)
            assertEquals(listOf(uk), after.programs)
            assertEquals(setOf(embedded.id), after.removedDefaults)
            assertEquals(feeds.map { it.id }, after.subscriptions.map { it.id })
            assertFalse(after.subscriptions.first().enabled)
            assertEquals(listOf(0, 1, 0, 1), after.subscriptions.map { it.programs.size })
            assertEquals(listOf("", "", "", "\"untouched\""), after.subscriptions.map { it.etag })
            assertTrue(after.subscriptions.take(3).all { it.lastModified.isEmpty() && it.validatorUrl.isEmpty() })
            assertTrue(after.sourceSelections.isEmpty())
            assertEquals(listOf(national, uk), repository.state.value.programs)
            assertEquals(before.localRevision, repository.state.value.localRevision)
            assertEquals(after, RadioList.decode(store.text))
            val restored = RadioCatalogRepository(store, scope, { listOf(embedded, national) }) { throw AssertionError(it) }
            restored.awaitReady()
            assertEquals(repository.state.value.programs, restored.state.value.programs)
            assertEquals(after, restored.snapshot())
            assertEquals(1, store.writes)
        }
    }

    @Test fun refreshDownloadsDeletedEntriesAgainAndResetRestoresOnlyEmbeddedEntries() {
        val embedded = station(10, "US")
        val local = station(11, "USA")
        val retained = station(12, "UK")
        val entries = listOf(RadioListEntry("Feed US", "https://audio.example/feed", group = "United States"),
            RadioListEntry("Feed UK", "https://audio.example/uk", group = "UK"))
        val feed = subscription("feed", RadioSubscriptions.programs(entries, emptyMap()))
        run(RadioCatalogSnapshot(listOf(local, retained), subscriptions = listOf(feed)), listOf(embedded)) { repository, _, _ ->
            repository.deleteGroups(setOf("united states"), repository.snapshot())
            assertNull(repository.find(embedded.id))
            assertNull(repository.find(local.id))
            assertEquals(1, repository.snapshot().subscriptions.single().programs.size)
            assertEquals(RadioSubscriptionResult.UPDATED, repository.refreshSubscription(feed.id, emptyMap(), {
                assertTrue(it.etag.isEmpty())
                assertTrue(it.lastModified.isEmpty())
                assertTrue(it.validatorUrl.isEmpty())
                RadioSubscriptionDownload(entries, etag = "\"new\"", validatorUrl = feed.url)
            }))
            assertTrue(repository.state.value.programs.any { it.name == "Feed US" })
            assertNull(repository.find(embedded.id))
            repository.resetBuiltins()
            assertEquals(embedded, repository.find(embedded.id))
            assertEquals(retained, repository.find(retained.id))
            assertNull(repository.find(local.id))
            assertTrue(repository.snapshot().removedDefaults.isEmpty())
            assertEquals(2, repository.snapshot().subscriptions.single().programs.size)
        }
    }

    @Test fun staleConfirmationAndFailedWritesNeverDeleteOrPublishPartialData() {
        val us = station(20, "US")
        val uk = station(21, "UK")
        run(RadioCatalogSnapshot(listOf(us, uk))) { repository, store, _ ->
            val old = repository.snapshot()
            repository.edit(uk, uk.copy(name = "Updated UK"))
            val current = repository.state.value
            val text = store.text
            try { repository.deleteGroups(setOf("united states"), old); fail("Stale deletion accepted") }
            catch (error: RadioListException) { assertEquals(R.string.radio_list_changed, error.messageId) }
            store.failWrite = true
            try { repository.deleteGroups(setOf("united states"), current.snapshot); fail("Write failure hidden") }
            catch (_: IOException) { }
            assertSame(current, repository.state.value)
            assertEquals(text, store.text)
            assertEquals(us, repository.find(us.id))
        }
    }

    @Test fun deletedBuiltinsCanReturnViaSubscriptionWithoutResettingAllDefaults() {
        val embedded = CnrRadio.stations.first().program
        val other = station(25, "UK")
        val feed = subscription("national", listOf(embedded))
        run(RadioCatalogSnapshot(listOf(other), subscriptions = listOf(feed)), listOf(embedded)) { repository, _, _ ->
            repository.deleteGroups(setOf("national"), repository.snapshot())
            assertTrue(repository.snapshot().removedDefaults.contains(embedded.id))
            assertTrue(repository.snapshot().subscriptions.single().programs.isEmpty())
            val entries = listOf(RadioListEntry("CNR 1", "https://audio.example/restored"))
            repository.refreshSubscription(feed.id, mapOf(RadioList.key("CNR 1") to embedded.id), {
                RadioSubscriptionDownload(entries)
            })
            assertEquals("https://audio.example/restored", repository.find(embedded.id)!!.streamUri)
            assertEquals(other, repository.find(other.id))
        }
    }

    @Test fun largeCacheDeletionIsLocalAndKeepsUnselectedStations() {
        val programs = List(5000) { station(it + 100, if (it % 2 == 0) "US" else "UK") }
        val feed = subscription("large", programs)
        run(RadioCatalogSnapshot(emptyList(), false, listOf(feed))) { repository, store, _ ->
            assertEquals(2500, repository.deleteGroups(setOf("united states"), repository.snapshot()))
            assertEquals(programs.filterIndexed { index, _ -> index % 2 != 0 }, repository.state.value.programs)
            assertEquals(2500, repository.snapshot().subscriptions.single().programs.size)
            assertEquals(1, store.writes)
        }
    }

    @Test fun deletingCctvCategoryKeepsNationalRadioAndOtherSubscriptions() {
        val television = RadioDefaults.corePrograms.single { it.id == RadioDefaults.CCTV_ID }
        val national = CnrRadio.stations.first().program
        val cached = station(30, "National", "My television",
            "https://piccpndali.v.myalicdn.com/audio/cctv1_2.m3u8")
        val uk = station(31, "UK")
        val feed = subscription("television", listOf(cached, national))
        val inactive = subscription("inactive", listOf(cached), enabled = false)
        val kept = subscription("uk", listOf(uk))
        run(RadioCatalogSnapshot(emptyList(), subscriptions = listOf(feed, inactive, kept)),
            listOf(television, national)) { repository, store, _ ->
            assertEquals(2, repository.deleteGroups(setOf(RadioGroups.cctvTelevision.id), repository.snapshot()))
            assertEquals(setOf(national.id, uk.id), repository.state.value.programs.map { it.id }.toSet())
            assertEquals(setOf(television.id), repository.snapshot().removedDefaults)
            assertEquals(listOf(national), repository.snapshot().subscriptions[0].programs)
            assertTrue(repository.snapshot().subscriptions[1].programs.isEmpty())
            assertFalse(repository.snapshot().subscriptions[1].enabled)
            assertEquals(kept, repository.snapshot().subscriptions[2])
            assertEquals(1, store.writes)
        }
    }

    @Test fun countryPreferencesAndDeletionMetadataAreBoundedAndBackwardCompatible() {
        val hidden = setOf("united states", "country:de", "import:my stations")
        val settings = RadioFavorites(hiddenGroups = hidden)
        assertEquals(settings, Json.decodeFromString<RadioFavorites>(Json.encodeToString(settings)))
        assertTrue(Json.decodeFromString<RadioFavorites>("{}").hiddenGroups.isEmpty())
        val old = RadioList.decode("""{"version":4,"programs":[]}""")
        assertTrue(old.removedDefaults.isEmpty())
        assertTrue(old.includeDefaults)
        for (invalid in listOf(setOf(""), setOf("bad\nid"), setOf("a".repeat(273)), (0..5000).mapTo(hashSetOf()) { "group$it" })) {
            try { RadioFavorites(hiddenGroups = invalid); fail("Invalid hidden groups accepted") }
            catch (_: IllegalArgumentException) { }
        }
        try { RadioList.decode("""{"version":5,"programs":[],"removedDefaults":["invalid"]}"""); fail() }
        catch (_: IllegalArgumentException) { }
        for (name in listOf("US", "USA", "United States", "美国")) assertEquals("united states", RadioGroups.of(station(1, name)).id)
        assertEquals(RadioGroups.of(station(1, "德国")), RadioGroups.named("country:de"))
        assertEquals(RadioGroups.national, RadioGroups.named("national"))
    }
}
