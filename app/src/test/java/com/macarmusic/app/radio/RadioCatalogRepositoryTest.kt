package com.macarmusic.app.radio

import com.macarmusic.app.R
import com.macarmusic.app.data.TextStore
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RadioCatalogRepositoryTest {
    private class MemoryStore(var text: String? = null) : TextStore {
        var failWrite = false
        var writes = 0
        override fun read() = text
        override fun write(text: String) {
            if (failWrite) throw IOException("Fixture write failed")
            this.text = text
            writes++
        }
    }
    private val entries = listOf(RadioListEntry("Test", "https://example.com/one.mp3"))
    private fun check(initial: String? = null, defaults: () -> List<RadioProgram> = { RadioDefaults.corePrograms },
        action: suspend (RadioCatalogRepository, MemoryStore, CoroutineScope,
        List<Exception>) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = MemoryStore(initial)
        val errors = mutableListOf<Exception>()
        try {
            val repository = RadioCatalogRepository(store, scope, loadDefaults = defaults, report = errors::add)
            repository.awaitReady()
            action(repository, store, scope, errors)
        } finally { scope.cancel() }
    }

    @Test fun committedCatalogReloadsAndIdenticalImportsDoNotWriteOrPublish() = check { repository, store, scope, errors ->
        assertEquals(17, repository.state.value.programs.size)
        val result = repository.merge(entries, emptyMap())
        assertEquals(RadioCatalogSnapshot(result.programs), RadioList.decode(store.text!!))
        val state = repository.state.value
        repository.merge(entries, emptyMap())
        assertSame(state, repository.state.value)
        assertEquals(1, store.writes)
        val loaded = RadioCatalogRepository(store, scope) { throw AssertionError(it) }
        assertEquals(result.programs, loaded.savedPrograms())
        assertTrue(errors.isEmpty())
    }

    @Test fun failedWritePreservesTheSavedFileAndPublishedState() = check { repository, store, _, _ ->
        repository.merge(entries, emptyMap())
        val state = repository.state.value
        val text = store.text
        store.failWrite = true
        try { repository.merge(listOf(entries[0].copy(uri = "https://example.com/two")), emptyMap()); fail() }
        catch (_: IOException) { }
        assertSame(state, repository.state.value)
        assertEquals(text, store.text)
    }

    @Test fun malformedSavedDataIsReportedAndCannotBeOverwritten() = check("{") { repository, store, _, errors ->
        assertEquals(1, errors.size)
        assertEquals(17, repository.state.value.programs.size)
        assertNotNull(repository.state.value.error)
        try { repository.merge(entries, emptyMap()); fail() } catch (_: IOException) { }
        try { repository.resetBuiltins(); fail() } catch (_: IOException) { }
        assertEquals("{", store.text)
        assertEquals(0, store.writes)
    }

    @Test fun stalePreviewCannotOverwriteAConcurrentImport() = check { repository, _, _, _ ->
        val before = repository.snapshot()
        repository.merge(entries, emptyMap())
        try { repository.merge(listOf(entries[0].copy(uri = "https://example.com/replaced")), emptyMap(), before); fail() }
        catch (error: RadioListException) { assertEquals(R.string.radio_list_changed, error.messageId) }
        assertEquals(entries[0].uri, repository.savedPrograms().single().uri)
    }

    @Test fun concurrentImportsAreSerializedWithoutLosingStations() = check { repository, _, scope, _ ->
        (1..20).map { index -> scope.async {
            repository.merge(listOf(RadioListEntry("Radio $index", "https://example.com/$index")), emptyMap())
        } }.awaitAll()
        assertEquals(20, repository.savedPrograms().size)
    }

    @Test fun backupReplacementIsNotPublishedUntilCommitAndRollsBackOnFailure() = check { repository, store, _, _ ->
        repository.merge(entries, emptyMap())
        val before = repository.state.value
        val text = store.text
        val replacement = RadioList.merge(emptyList(), listOf(entries[0].copy(name = "Replacement")), emptyMap()).programs
        try {
            repository.withReplacement(replacement) {
                assertSame(before, repository.state.value)
                throw IOException("Fixture library write failed")
            }
            fail()
        } catch (_: IOException) { }
        assertSame(before, repository.state.value)
        assertEquals(text, store.text)
        repository.withReplacement(replacement) { assertSame(before, repository.state.value) }
        assertEquals(replacement, repository.savedPrograms())
        repository.withReplacement(null) { Unit }
        assertEquals(replacement, repository.savedPrograms())
    }

    @Test fun resetRestoresBuiltinsWithoutDroppingCustomStations() = check { repository, _, _, _ ->
        repository.merge(entries + entries[0].copy(name = "CNR override", id = CnrRadio.stations[0].id), emptyMap())
        repository.resetBuiltins()
        assertEquals(CnrRadio.stations[0].program, repository.state.value.programs[0])
        assertEquals(1, repository.savedPrograms().size)
    }

    @Test fun stationEditingRejectsStaleDialogsAndPersistsChosenSourcesAtomically() = check { repository, store, _, _ ->
        val original = repository.find(CnrRadio.stations.first().id)!!
        val updated = original.copy(alternateUris = listOf("https://example.com/backup"), group = "Province")
        repository.edit(original, updated)
        val selected = repository.selectSource(updated.id, 1)
        assertEquals("https://example.com/backup", selected.streamUri)
        assertEquals(RadioCatalogSnapshot(listOf(selected)), RadioList.decode(store.text!!))
        try { repository.selectSource(updated.id, 0, "https://example.com/stale"); fail("Stale source dialog accepted") }
        catch (error: RadioListException) { assertEquals(R.string.radio_list_changed, error.messageId) }
        try { repository.edit(original, original.copy(name = "Stale")); fail("Stale edit accepted") }
        catch (error: RadioListException) { assertEquals(R.string.radio_list_changed, error.messageId) }
        store.failWrite = true
        try { repository.selectSource(updated.id, 0); fail("Failed persistence ignored") } catch (_: IOException) { }
        assertEquals(selected, repository.find(selected.id))
    }

    @Test fun changingSourcesAndEditingNamesPreserveCustomChannelOrder() = check { repository, _, _, _ ->
        val entries = RadioList.parse(("One,https://example.com/one\nOne,https://example.com/backup\n" +
            "Two,https://example.com/two").byteInputStream()).entries
        val saved = repository.merge(entries, emptyMap()).programs
        val selected = repository.selectSource(saved.first().id, 1)
        repository.edit(selected, selected.copy(name = "Renamed"))
        assertEquals(saved.map { it.id }, repository.savedPrograms().map { it.id })
    }

    @Test fun catalogUpgradeOverlaysSavedEditsWithoutWritingAndLoadsDefaultsOnlyOnce() {
        val station = RadioDefaults.bundled.first { it.builtin == null }.copy(name = "Retained edit",
            uri = "https://example.com/primary", alternateUris = listOf("https://example.com/secondary"), sourceIndex = 1)
        var loads = 0
        check(RadioList.encode(listOf(station)), defaults = { loads++; RadioDefaults.bundled }) { repository, store, _, errors ->
            assertEquals(station, repository.find(station.id))
            assertEquals(RadioDefaults.bundled.size, repository.state.value.programs.size)
            assertEquals(0, store.writes)
            val original = repository.find(station.id)!!
            repository.edit(original, original.copy(group = "Edited group"))
            repository.selectSource(station.id, 0)
            repository.resetBuiltins()
            assertEquals(RadioDefaults.bundled.first { it.id == station.id }, repository.find(station.id))
            assertTrue(repository.savedPrograms().isEmpty())
            assertEquals(1, loads)
            assertTrue(errors.isEmpty())
        }
    }

    @Test fun bundledLoadFailureIsReportedAndCannotOverwriteSavedData() =
        check("preserved", defaults = { throw IOException("Fixture bundled read failed") }) { repository, store, _, errors ->
            assertEquals(1, errors.size)
            assertNotNull(repository.state.value.error)
            try { repository.resetBuiltins(); fail("Failed load was overwritten") } catch (_: IOException) { }
            assertEquals("preserved", store.text)
            assertEquals(0, store.writes)
        }

    @Test fun replaceAllPersistsWithoutResurrectingDefaultsAndResetRestoresThem() = check { repository, store, scope, _ ->
        val old = repository.find(CnrRadio.stations.first().id)!!
        repository.edit(old, old.copy(alternateUris = listOf("https://example.com/old")))
        val result = repository.merge(entries, emptyMap(), mode = RadioImportMode.REPLACE_ALL)
        assertEquals(17, result.removed)
        assertEquals(result.programs, repository.state.value.programs)
        assertFalse(repository.snapshot().includeDefaults)
        val loaded = RadioCatalogRepository(store, scope, loadDefaults = { RadioDefaults.bundled }) { throw AssertionError(it) }
        loaded.awaitReady()
        assertEquals(result.programs, loaded.state.value.programs)
        assertFalse(loaded.state.value.includeDefaults)
        val state = loaded.state.value
        val writes = store.writes
        loaded.merge(entries, emptyMap(), mode = RadioImportMode.APPEND)
        assertSame(state, loaded.state.value)
        assertEquals(writes, store.writes)
        loaded.merge(listOf(entries.single().copy(uri = "https://example.com/backup")), emptyMap(), mode = RadioImportMode.APPEND)
        assertEquals(1, loaded.state.value.programs.size)
        assertEquals(2, loaded.state.value.programs.single().sources.size)
        loaded.resetBuiltins()
        assertTrue(loaded.snapshot().includeDefaults)
        assertEquals(RadioDefaults.bundled.size + 1, loaded.state.value.programs.size)
    }

    @Test fun failedOrEmptyReplaceAllDoesNotEraseTheOriginalCatalogOrItsMode() = check { repository, store, _, _ ->
        repository.merge(entries, emptyMap())
        val state = repository.state.value
        val text = store.text
        store.failWrite = true
        try {
            repository.merge(listOf(entries.single().copy(uri = "https://example.com/new")), emptyMap(),
                mode = RadioImportMode.REPLACE_ALL)
            fail("Failed replacement was accepted")
        } catch (_: IOException) { }
        assertSame(state, repository.state.value)
        assertEquals(text, store.text)
        store.failWrite = false
        try {
            repository.merge(emptyList(), emptyMap(), mode = RadioImportMode.REPLACE_ALL)
            fail("An empty file erased the catalog")
        } catch (_: RadioListException) { }
        assertSame(state, repository.state.value)
        assertEquals(text, store.text)
    }

    @Test fun stalePreviewDetectsModeChangesAndBackupRollbackRestoresTheMode() = check { repository, store, _, _ ->
        repository.merge(entries, emptyMap())
        val before = repository.snapshot()
        repository.withReplacement(before.programs, includeDefaults = false) { Unit }
        try { repository.merge(entries, emptyMap(), expected = before, mode = RadioImportMode.REPLACE_ALL); fail() }
        catch (error: RadioListException) { assertEquals(R.string.radio_list_changed, error.messageId) }
        val replaced = repository.state.value
        val text = store.text
        try {
            repository.withReplacement(before.programs, includeDefaults = true) {
                assertSame(replaced, repository.state.value)
                throw IOException("Fixture backup failure")
            }
            fail()
        } catch (_: IOException) { }
        assertSame(replaced, repository.state.value)
        assertEquals(text, store.text)
        assertFalse(RadioList.decode(checkNotNull(store.text)).includeDefaults)
    }

    @Test fun exportingAnUnreadableCatalogFailsInsteadOfExportingFallbackDefaults() =
        check("{") { repository, _, _, _ ->
            try { repository.exportPrograms(); fail("Unreadable catalog exported") } catch (_: IOException) { }
        }
}
