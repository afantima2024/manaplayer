package com.macarmusic.app.radio

import com.macarmusic.app.data.TextStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException

class RadioCatalogState(val saved: List<RadioProgram> = emptyList(), val ready: Boolean = false,
    val error: Exception? = null, val defaults: List<RadioProgram> = RadioDefaults.corePrograms,
    val includeDefaults: Boolean = true) {
    val snapshot = RadioCatalogSnapshot(saved, includeDefaults)
    val programs = if (includeDefaults) RadioList.catalog(saved, defaults) else saved
    private val byId = programs.associateBy { it.id }
    fun find(id: String?): RadioProgram? = byId[id]
    fun queue(station: RadioProgram, favorites: Set<String>, favoritesOnly: Boolean): List<RadioProgram> =
        if (favoritesOnly) programs.filter { it.id in favorites || it.id == station.id } else programs
}

class RadioCatalogRepository(
    private val store: TextStore,
    scope: CoroutineScope,
    private val loadDefaults: () -> List<RadioProgram> = { RadioDefaults.corePrograms },
    private val report: (Exception) -> Unit,
) {
    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private val mutableState = MutableStateFlow(RadioCatalogState())
    private var defaults = RadioDefaults.corePrograms
    val state = mutableState.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            try {
                defaults = loadDefaults().also(RadioList::validate)
                val saved = store.read()?.let(RadioList::decode) ?: RadioCatalogSnapshot(emptyList())
                mutableState.value = RadioCatalogState(saved.programs,
                    ready = true, defaults = defaults, includeDefaults = saved.includeDefaults)
            }
            catch (error: IOException) { failed(error) }
            catch (error: SerializationException) { failed(error) }
            catch (error: IllegalArgumentException) { failed(error) }
            finally { loaded.complete(Unit) }
        }
    }

    suspend fun awaitReady() { loaded.await() }
    fun find(id: String?): RadioProgram? = state.value.find(id)

    suspend fun savedPrograms(): List<RadioProgram> {
        awaitReady()
        return readable().saved
    }

    suspend fun snapshot(): RadioCatalogSnapshot {
        awaitReady()
        return readable().snapshot
    }

    suspend fun exportPrograms(): List<RadioProgram> {
        awaitReady()
        return readable().programs
    }

    suspend fun merge(entries: List<RadioListEntry>, aliases: Map<String, String>,
        expected: RadioCatalogSnapshot? = null, mode: RadioImportMode = RadioImportMode.REPLACE): RadioListMerge = withContext(Dispatchers.IO) {
        awaitReady()
        mutex.withLock {
            val state = readable()
            if (expected != null && expected != state.snapshot) throw RadioListException(com.macarmusic.app.R.string.radio_list_changed)
            val result = RadioList.merge(state.saved, entries, aliases, mode,
                if (state.includeDefaults) defaults else emptyList())
            commit(result.programs, state.includeDefaults && mode != RadioImportMode.REPLACE_ALL)
            result
        }
    }

    suspend fun edit(original: RadioProgram, updated: RadioProgram) = withContext(Dispatchers.IO) {
        awaitReady()
        mutex.withLock {
            require(original.id == updated.id)
            val state = readable()
            if (state.find(original.id) != original) throw RadioListException(com.macarmusic.app.R.string.radio_list_changed)
            saveStation(state, updated)
        }
    }

    suspend fun selectSource(id: String, index: Int, expectedUri: String? = null): RadioProgram = withContext(Dispatchers.IO) {
        awaitReady()
        mutex.withLock {
            val state = readable()
            val station = state.find(id) ?: throw RadioListException(com.macarmusic.app.R.string.radio_station_invalid)
            if (index !in station.sources.indices || expectedUri != null && station.sources[index] != expectedUri)
                throw RadioListException(com.macarmusic.app.R.string.radio_list_changed)
            val selected = station.copy(sourceIndex = index)
            if (selected != station) saveStation(state, selected)
            selected
        }
    }

    suspend fun resetBuiltins() = withContext(Dispatchers.IO) {
        awaitReady()
        mutex.withLock { commit(RadioList.resetBuiltins(readable().saved, defaults), includeDefaults = true) }
    }

    suspend fun <T> withReplacement(programs: List<RadioProgram>?, includeDefaults: Boolean = true,
        action: suspend () -> T): T = withContext(Dispatchers.IO) {
        if (programs == null) return@withContext action()
        awaitReady()
        mutex.withLock {
            val previous = readable().snapshot
            if (RadioCatalogSnapshot(programs, includeDefaults) == previous) return@withLock action()
            val next = RadioList.encode(programs, includeDefaults)
            var committed = false
            store.write(next)
            try {
                val result = action()
                committed = true
                mutableState.value = RadioCatalogState(programs.toList(), ready = true, defaults = defaults,
                    includeDefaults = includeDefaults)
                result
            } finally {
                if (!committed) withContext(NonCancellable) {
                    try { store.write(RadioList.encode(previous.programs, previous.includeDefaults)) }
                    catch (error: IOException) { failed(error); throw IOException("Radio list rollback failed", error) }
                }
            }
        }
    }

    private fun saveStation(state: RadioCatalogState, station: RadioProgram) {
        commit(if (state.saved.any { it.id == station.id })
            state.saved.map { if (it.id == station.id) station else it } else state.saved + station)
    }

    private fun commit(programs: List<RadioProgram>, includeDefaults: Boolean = readable().includeDefaults) {
        if (RadioCatalogSnapshot(programs, includeDefaults) == readable().snapshot) return
        store.write(RadioList.encode(programs, includeDefaults))
        mutableState.value = RadioCatalogState(programs, ready = true, defaults = defaults, includeDefaults = includeDefaults)
    }

    private fun readable(): RadioCatalogState = state.value.also {
        if (it.error != null) throw IOException("Saved radio list is unreadable; changes were not applied", it.error)
    }

    private fun failed(error: Exception) {
        mutableState.value = RadioCatalogState(state.value.saved, ready = true, error = error, defaults = defaults,
            includeDefaults = state.value.includeDefaults)
        report(error)
    }
}
