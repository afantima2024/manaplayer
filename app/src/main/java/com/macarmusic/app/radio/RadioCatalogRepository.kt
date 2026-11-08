package com.macarmusic.app.radio

import com.macarmusic.app.data.TextStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.util.UUID
import com.macarmusic.app.R

class RadioCatalogState(val saved: List<RadioProgram> = emptyList(), val ready: Boolean = false,
    val error: Exception? = null, val defaults: List<RadioProgram> = RadioDefaults.corePrograms,
    val includeDefaults: Boolean = true, val subscriptions: List<RadioSubscription> = emptyList(),
    val sourceSelections: Map<String, String> = emptyMap(), val localRevision: Long = 0,
    val removedDefaults: Set<String> = emptySet(),
    val bundledRevision: Int = 0,
    val initializeBundledSettings: Boolean = false,
    previous: RadioCatalogState? = null) {
    val snapshot = RadioCatalogSnapshot(saved, includeDefaults, subscriptions, sourceSelections, removedDefaults,
        bundledRevision, initializeBundledSettings)
    val base: List<RadioProgram> = if (previous != null && saved == previous.saved && defaults === previous.defaults &&
        includeDefaults == previous.includeDefaults && removedDefaults == previous.removedDefaults) previous.base
        else if (includeDefaults) RadioList.catalog(saved, defaults, removedDefaults) else saved
    private val sameSubscriptionContent = previous != null && subscriptions.size == previous.subscriptions.size &&
        subscriptions.indices.all { subscriptions[it].enabled == previous.subscriptions[it].enabled &&
            subscriptions[it].programs == previous.subscriptions[it].programs }
    val programs: List<RadioProgram> = if (previous != null && base === previous.base && sameSubscriptionContent &&
        sourceSelections == previous.sourceSelections) previous.programs
        else RadioSubscriptions.catalog(base, subscriptions, sourceSelections).let { combined ->
            previous?.programs?.takeIf { it == combined } ?: combined
        }
    val knownIds: Set<String> = if (previous != null && programs === previous.programs && sameSubscriptionContent)
        previous.knownIds else programs.mapTo(hashSetOf()) { it.id }.apply {
        subscriptions.forEach { subscription -> subscription.programs.forEach { add(it.id) } }
    }
    private val byId: Map<String, RadioProgram> = if (previous != null && programs === previous.programs)
        previous.byId else programs.associateBy { it.id }
    fun find(id: String?): RadioProgram? = byId[id]
    fun queue(station: RadioProgram, favorites: Set<String>, favoritesOnly: Boolean): List<RadioProgram> =
        if (favoritesOnly) programs.filter { it.id in favorites || it.id == station.id } else programs
}

class RadioCatalogRepository(
    private val store: TextStore,
    scope: CoroutineScope,
    private val loadDefaults: () -> List<RadioProgram> = { RadioDefaults.corePrograms },
    private val loadSubscriptions: (() -> List<RadioSubscription>)? = null,
    private val reportDefaults: ((Exception) -> Unit)? = null,
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
                var prepared = stateFor(saved)
                if (loadSubscriptions != null && saved.bundledRevision < RadioBundledDefaults.REVISION) {
                    try {
                        val next = RadioBundledDefaults.install(saved, loadSubscriptions.invoke())
                        val migrated = stateFor(next)
                        store.write(encode(next))
                        prepared = migrated
                    } catch (error: IOException) { (reportDefaults ?: report)(error) }
                    catch (error: SerializationException) { (reportDefaults ?: report)(error) }
                    catch (error: IllegalArgumentException) { (reportDefaults ?: report)(error) }
                }
                mutableState.value = prepared
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
        return readyState().snapshot
    }

    suspend fun readyState(): RadioCatalogState { awaitReady(); return readable() }

    suspend fun exportPrograms(): List<RadioProgram> {
        awaitReady()
        return readable().programs
    }

    suspend fun merge(entries: List<RadioListEntry>, aliases: Map<String, String>,
        expected: RadioCatalogSnapshot? = null, mode: RadioImportMode = RadioImportMode.REPLACE): RadioListMerge = withContext(Dispatchers.IO) {
        awaitReady()
        mutex.withLock {
            val state = readable()
            if (expected != null && expected != state.snapshot) throw RadioListException(R.string.radio_list_changed)
            val names = state.programs.filter { it.name.isNotEmpty() }.groupBy { RadioList.key(it.name) }
                .filterValues { it.size == 1 }.mapValues { it.value.single().id }
            val result = RadioList.merge(state.saved, entries, names + aliases, mode,
                if (state.includeDefaults) defaults.filterNot { it.id in state.removedDefaults } else emptyList())
            val replacing = mode == RadioImportMode.REPLACE_ALL
            commit(state.snapshot.copy(programs = result.programs, includeDefaults = state.includeDefaults && !replacing,
                subscriptions = if (replacing) state.subscriptions.map { it.copy(enabled = false) } else state.subscriptions,
                sourceSelections = if (replacing) emptyMap() else state.sourceSelections,
                removedDefaults = if (replacing) emptySet() else state.removedDefaults,
                bundledRevision = if (replacing) RadioBundledDefaults.REVISION else state.bundledRevision,
                initializeBundledSettings = !replacing && state.initializeBundledSettings))
            if (replacing) {
                val retained = result.programs.mapTo(hashSetOf()) { it.id }
                result.copy(removed = state.programs.count { it.id !in retained })
            } else result
        }
    }

    suspend fun edit(original: RadioProgram, updated: RadioProgram) = withContext(Dispatchers.IO) {
        awaitReady()
        mutex.withLock {
            require(original.id == updated.id)
            val state = readable()
            if (state.find(original.id) != original) throw RadioListException(R.string.radio_list_changed)
            saveStation(state, updated)
        }
    }

    suspend fun selectSource(id: String, index: Int, expectedUri: String? = null): RadioProgram = withContext(Dispatchers.IO) {
        awaitReady()
        mutex.withLock {
            val state = readable()
            val station = state.find(id) ?: throw RadioListException(R.string.radio_station_invalid)
            if (index !in station.sources.indices || expectedUri != null && station.sources[index] != expectedUri)
                throw RadioListException(R.string.radio_list_changed)
            val selected = station.copy(sourceIndex = index)
            if (selected != station) {
                if (state.subscriptions.isEmpty()) saveStation(state, selected)
                else commit(state.snapshot.copy(sourceSelections = state.sourceSelections + (id to selected.streamUri)))
            }
            checkNotNull(find(id))
        }
    }

    suspend fun resetBuiltins() = withContext(Dispatchers.IO) {
        awaitReady()
        mutex.withLock {
            val state = readable()
            val ids = defaults.mapTo(hashSetOf()) { it.id }
            commit(state.snapshot.copy(programs = RadioList.resetBuiltins(state.saved, defaults), includeDefaults = true,
                sourceSelections = state.sourceSelections.filterKeys { it !in ids }, removedDefaults = emptySet()))
        }
    }

    suspend fun deleteGroups(groups: Set<String>, expected: RadioCatalogSnapshot): Int = withContext(Dispatchers.IO) {
        require(groups.isNotEmpty() && groups.size <= RadioList.MAX_PROGRAMS && groups.all(RadioGroup::validId))
        awaitReady()
        mutex.withLock {
            val state = readable()
            if (state.snapshot != expected) throw RadioListException(R.string.radio_list_changed)
            val ids = state.programs.filter { RadioGroups.of(it).id in groups }.mapTo(hashSetOf()) { it.id }
            if (ids.isEmpty()) throw RadioListException(R.string.radio_list_changed)
            val owned = mutableMapOf<String, MutableSet<String>>()
            // Use the actual merge decisions, including URL/name aliases, rather than deleting only raw IDs.
            RadioSubscriptions.catalog(state.base, state.subscriptions, state.sourceSelections) { subscription, station, id ->
                if (id in ids || !subscription.enabled && state.find(id) == null && RadioGroups.of(station).id in groups)
                    owned.getOrPut(subscription.id) { hashSetOf() }.add(station.id)
            }
            val subscriptions = state.subscriptions.map { subscription ->
                val removed = owned[subscription.id].orEmpty()
                if (removed.isEmpty()) subscription else subscription.copy(
                    programs = subscription.programs.filterNot { it.id in removed },
                    etag = "", lastModified = "", validatorUrl = "")
            }
            commit(state.snapshot.copy(programs = state.saved.filterNot { it.id in ids }, subscriptions = subscriptions,
                removedDefaults = state.removedDefaults + defaults.filter { it.id in ids }.map { it.id },
                sourceSelections = state.sourceSelections - ids), preservePlayback = true)
            ids.size
        }
    }

    suspend fun <T> withReplacement(programs: List<RadioProgram>?, includeDefaults: Boolean = true,
        subscriptions: List<RadioSubscription> = emptyList(), sourceSelections: Map<String, String> = emptyMap(),
        removedDefaults: Set<String> = emptySet(),
        action: suspend () -> T): T = withContext(Dispatchers.IO) {
        if (programs == null) return@withContext action()
        awaitReady()
        mutex.withLock {
            val previous = readable().snapshot
            val replacement = RadioCatalogSnapshot(programs, includeDefaults, subscriptions, sourceSelections, removedDefaults,
                bundledRevision = RadioBundledDefaults.REVISION)
            if (replacement == previous) return@withLock action()
            val nextState = stateFor(replacement, readable().localRevision + 1)
            val next = encode(replacement)
            var committed = false
            store.write(next)
            try {
                val result = action()
                committed = true
                mutableState.value = nextState
                result
            } finally {
                if (!committed) withContext(NonCancellable) {
                    try { store.write(encode(previous)) }
                    catch (error: IOException) { failed(error); throw IOException("Radio list rollback failed", error) }
                }
            }
        }
    }

    suspend fun saveSubscription(name: String, url: String, original: RadioSubscription? = null): RadioSubscription =
        withContext(Dispatchers.IO) {
            awaitReady()
            mutex.withLock {
                val state = readable()
                require(RadioSubscriptions.validName(name))
                val address = RadioSubscriptions.address(url) ?: throw RadioListException(R.string.radio_list_bad_url)
                if (original != null && state.subscriptions.find { it.id == original.id } != original)
                    throw RadioListException(R.string.radio_subscription_changed)
                if (state.subscriptions.any { it.url == address && it.id != original?.id })
                    throw RadioListException(R.string.radio_subscription_duplicate)
                if (original == null && state.subscriptions.size >= RadioSubscriptions.MAX_SUBSCRIPTIONS)
                    throw RadioListException(R.string.radio_subscription_limit)
                val subscription = if (original == null) RadioSubscription(UUID.randomUUID().toString(), name, address)
                    else original.copy(name = name, url = address,
                        etag = if (address == original.url) original.etag else "",
                        lastModified = if (address == original.url) original.lastModified else "",
                        validatorUrl = if (address == original.url) original.validatorUrl else "")
                commit(state.snapshot.copy(subscriptions = if (original == null) state.subscriptions + subscription
                    else state.subscriptions.map { if (it.id == subscription.id) subscription else it }), preservePlayback = true)
                subscription
            }
        }

    suspend fun changeSubscription(original: RadioSubscription, enabled: Boolean? = null, remove: Boolean = false) =
        withContext(Dispatchers.IO) {
            awaitReady()
            mutex.withLock {
                val state = readable()
                if (state.subscriptions.find { it.id == original.id } != original)
                    throw RadioListException(R.string.radio_subscription_changed)
                val subscriptions = if (remove) state.subscriptions.filterNot { it.id == original.id }
                    else state.subscriptions.map { if (it.id == original.id) it.copy(enabled = checkNotNull(enabled)) else it }
                commit(state.snapshot.copy(subscriptions = subscriptions), preservePlayback = true)
            }
        }

    suspend fun refreshSubscription(id: String, aliases: Map<String, String>,
        fetch: suspend (RadioSubscription) -> RadioSubscriptionDownload = RadioSubscriptionClient()::fetch,
        clock: () -> Long = System::currentTimeMillis): RadioSubscriptionResult = withContext(Dispatchers.IO) {
        awaitReady()
        val original = mutex.withLock {
            readable().subscriptions.find { it.id == id && it.enabled }
                ?: throw RadioListException(R.string.radio_subscription_changed)
        }
        var problem: RadioSubscriptionFailure? = null
        var downloaded: RadioSubscriptionDownload? = null
        var programs = original.programs
        try {
            downloaded = fetch(original)
            downloaded.entries?.let { programs = RadioSubscriptions.programs(it, aliases) }
            if (programs.isEmpty()) throw RadioSubscriptionException(
                RadioSubscriptionFailure(RadioSubscriptionProblem.INVALID_LIST))
        } catch (error: RadioSubscriptionException) {
            problem = error.failure
        } catch (error: RadioListException) {
            problem = RadioSubscriptionFailure(if (error.messageId in listOf(R.string.radio_list_too_many,
                R.string.radio_sources_too_many)) RadioSubscriptionProblem.LIMIT else RadioSubscriptionProblem.INVALID_LIST)
        }
        currentCoroutineContext().ensureActive()
        mutex.withLock {
            val state = readable()
            if (state.subscriptions.find { it.id == id } != original)
                throw RadioListException(R.string.radio_subscription_changed)
            val checked = maxOf(clock(), original.checkedAtMs)
            val changed = problem == null && programs != original.programs
            val next = if (problem != null) original.copy(checkedAtMs = checked, failure = problem)
                else original.copy(programs = programs, checkedAtMs = checked,
                    updatedAtMs = if (changed) checked else original.updatedAtMs, failure = null,
                    etag = checkNotNull(downloaded).etag, lastModified = downloaded.lastModified,
                    validatorUrl = downloaded.validatorUrl, unavailableStations = downloaded.unavailableStations,
                    ignoredLogos = downloaded.ignoredLogos)
            try {
                commit(state.snapshot.copy(subscriptions = state.subscriptions.map { if (it.id == id) next else it }),
                    preservePlayback = true)
            } catch (error: RadioSubscriptionException) {
                problem = error.failure
                commit(state.snapshot.copy(subscriptions = state.subscriptions.map {
                    if (it.id == id) original.copy(checkedAtMs = checked, failure = problem) else it
                }), preservePlayback = true)
            } catch (error: RadioListException) {
                problem = RadioSubscriptionFailure(RadioSubscriptionProblem.LIMIT)
                commit(state.snapshot.copy(subscriptions = state.subscriptions.map {
                    if (it.id == id) original.copy(checkedAtMs = checked, failure = problem) else it
                }), preservePlayback = true)
            }
            when {
                problem != null -> RadioSubscriptionResult.FAILED
                changed -> RadioSubscriptionResult.UPDATED
                else -> RadioSubscriptionResult.UNCHANGED
            }
        }
    }

    private suspend fun saveStation(state: RadioCatalogState, station: RadioProgram) {
        commit(state.snapshot.copy(programs = if (state.saved.any { it.id == station.id })
            state.saved.map { if (it.id == station.id) station else it } else state.saved + station,
            sourceSelections = state.sourceSelections - station.id))
    }

    private suspend fun commit(snapshot: RadioCatalogSnapshot, preservePlayback: Boolean = false) {
        val state = readable()
        if (snapshot == state.snapshot) return
        val revision = state.localRevision + if (preservePlayback) 0 else 1
        var next = stateFor(snapshot, revision)
        val selections = snapshot.sourceSelections.filterKeys(next.knownIds::contains)
        if (selections != snapshot.sourceSelections) next = stateFor(snapshot.copy(sourceSelections = selections), revision)
        val text = encode(next.snapshot)
        currentCoroutineContext().ensureActive()
        store.write(text)
        mutableState.value = next
    }

    private fun encode(snapshot: RadioCatalogSnapshot) = RadioList.encode(snapshot.programs, snapshot.includeDefaults,
        snapshot.subscriptions, snapshot.sourceSelections, snapshot.removedDefaults, snapshot.bundledRevision,
        snapshot.initializeBundledSettings)

    private fun stateFor(snapshot: RadioCatalogSnapshot, revision: Long = 0) =
        RadioCatalogState(snapshot.programs, ready = true, defaults = defaults, includeDefaults = snapshot.includeDefaults,
            subscriptions = snapshot.subscriptions, sourceSelections = snapshot.sourceSelections,
            localRevision = revision, removedDefaults = snapshot.removedDefaults, bundledRevision = snapshot.bundledRevision,
            initializeBundledSettings = snapshot.initializeBundledSettings,
            previous = state.value)

    private fun readable(): RadioCatalogState = state.value.also {
        if (it.error != null) throw IOException("Saved radio list is unreadable; changes were not applied", it.error)
    }

    private fun failed(error: Exception) {
        mutableState.value = RadioCatalogState(state.value.saved, ready = true, error = error, defaults = defaults,
            includeDefaults = state.value.includeDefaults, subscriptions = state.value.subscriptions,
            sourceSelections = state.value.sourceSelections, localRevision = state.value.localRevision,
            removedDefaults = state.value.removedDefaults, bundledRevision = state.value.bundledRevision,
            initializeBundledSettings = state.value.initializeBundledSettings)
        report(error)
    }
}
