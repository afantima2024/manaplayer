package com.macarmusic.app.lyrics

import android.content.Context
import android.util.Log
import com.macarmusic.app.data.MusicRepository
import com.macarmusic.app.model.Track
import com.macarmusic.app.model.LyricCacheExpiry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

class LyricsRepository(
    context: Context,
    music: MusicRepository,
    scope: CoroutineScope,
    private val client: OnlineLyricsClient = OnlineLyricsClient(HttpsLyricsTransport(scope)) { provider, error ->
        Log.w(TAG, "$provider lyric lookup failed", error)
    },
    private val nowMs: () -> Long = System::currentTimeMillis,
    onCacheProblem: (String) -> Unit = { Log.w(TAG, it) },
    internalExpiry: () -> LyricCacheExpiry = { LyricCacheExpiry.THIRTY_DAYS },
    portableExpiry: () -> LyricCacheExpiry = { LyricCacheExpiry.NEVER },
) {
    private val local = LocalLyricsLoader(context, music)
    val cache = LyricCache(context, {
        music.awaitReady()
        if (!music.isReadable) throw IOException("Cannot check the library's retained storage permissions")
        val library = music.library.value
        library.sources.mapTo(hashSetOf()) { it.uri }.apply {
            addAll(library.tracks.filter { it.standalonePersisted }.map { it.standaloneUri ?: it.uri })
        }
    }, onCacheProblem, nowMs, music.documents.grantMutex, internalExpiry, portableExpiry)
    private val networkMutex = Mutex()
    private data class Miss(val until: Long, val issue: LyricIssue, val generation: Long)
    private val misses = linkedMapOf<String, Miss>()
    private val mutableLatest = MutableStateFlow(LyricState())
    val latest = mutableLatest.asStateFlow()
    private val playbackEpoch = AtomicLong()
    val playbackGeneration: Long get() = playbackEpoch.get()
    @Volatile private var playbackTrackId: String? = null
    @Volatile private var tracksPlayback = false

    fun trackChanged(trackId: String?) {
        if (!tracksPlayback || playbackTrackId != trackId) {
            playbackTrackId = trackId
            tracksPlayback = true
            playbackEpoch.incrementAndGet()
            if (latest.value.trackId != trackId) mutableLatest.value = LyricState()
        }
    }

    private fun publish(trackId: String, result: LyricResult) {
        if (!tracksPlayback || playbackTrackId == trackId) mutableLatest.value = LyricState(trackId, result = result)
    }

    suspend fun load(
        track: Track,
        online: Boolean,
        refresh: Boolean = false,
        allowNetwork: Boolean = true,
        publishResult: Boolean = true,
    ): LyricResult = withContext(Dispatchers.IO) {
        val previous = latest.value
        val result = lookup(track, online, refresh, allowNetwork)
        currentCoroutineContext().ensureActive()
        val current = latest.value
        if (current !== previous && current.trackId == track.id &&
            (current.result?.document?.selected == true || current.result?.document?.offlineSelected == true ||
                current.result?.document?.bestVersionId != null)) {
            return@withContext current.result
        }
        if (publishResult) publish(track.id, result)
        result
    }

    private suspend fun lookup(track: Track, online: Boolean, refresh: Boolean, allowNetwork: Boolean): LyricResult {
        val generation = cache.generation
        val query = LyricQuery.from(track)
        val key = key(query, track.fileName)
        val initial = cache.read(key)
        initial.value?.takeIf { it.bestVersionId != null }?.let { return it.preferred().result(cached = true) }
        initial.value?.takeIf { it.selected && (!refresh || !allowNetwork) &&
            (online || it.offlineSelected || it.origin == LyricOrigin.LOCAL_FILE || it.origin == LyricOrigin.EMBEDDED) }?.let {
            return it.result(cached = true)
        }
        val localResult = local.load(track)
        if (localResult.lyrics != null || !online) return localResult
        currentCoroutineContext().ensureActive()
        var cacheUnavailable = initial.unavailable
        suspend fun cached(): LyricResult? {
            val read = cache.read(key)
            cacheUnavailable = cacheUnavailable || read.unavailable
            return read.value?.takeIf { it.automatic }?.let {
                it.preferred().result(cached = true, issue = localResult.issue)
            }
        }
        fun issue(fallback: LyricIssue?): LyricIssue? = when {
            localResult.issue == LyricIssue.LOCAL_UNREADABLE -> localResult.issue
            cacheUnavailable -> LyricIssue.CACHE_UNAVAILABLE
            else -> localResult.issue ?: fallback
        }
        if (!refresh || !allowNetwork) initial.value?.takeIf { it.automatic }?.let {
            return it.result(cached = true, issue = localResult.issue)
        }
        if (!allowNetwork) return localResult.copy(issue = issue(null))
        return networkMutex.withLock {
            if (!refresh) {
                // A previous caller may have filled the cache while this online lookup waited.
                cached()?.let { return@withLock it }
                misses[key]?.takeIf { it.until > nowMs() && it.generation == cache.generation }?.let {
                    return@withLock localResult.copy(issue = issue(it.issue))
                }
            }
            try {
                val found = withTimeout(OnlineLyricsClient.TOTAL_TIMEOUT_MS) { client.find(query, track.fileName) }
                if (found == null) {
                    rememberMiss(key, LyricIssue.NO_MATCH, generation)
                    localResult.copy(issue = issue(LyricIssue.NO_MATCH))
                } else {
                    currentCoroutineContext().ensureActive()
                    val document = CachedLyric(found.text, found.origin, nowMs().coerceAtLeast(0), history = found.history)
                    val written = cache.retain(key, document, generation)
                    if (written == null && generation != cache.generation) {
                        cache.read(key).value?.takeIf { it.selected || it.bestVersionId != null }?.let {
                            return@withLock it.preferred().result(cached = true)
                        }
                    }
                    cacheUnavailable = cache.info.value.unavailable
                    misses.remove(key)
                    (written ?: document).result(issue = issue(null))
                }
            } catch (error: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                failed(key, localResult, error, generation)
            } catch (error: IOException) {
                failed(key, localResult, error, generation)
            }
        }
    }

    suspend fun next(track: Track, current: LyricResult): LyricResult? = withContext(Dispatchers.IO) {
        val generation = cache.generation
        val document = current.document
        val original = document?.text?.let { LyricsParser.parse(it) } ?: current.lyrics
        val history = (document?.history ?: LyricSearchHistory()).let {
            if (original == null) it else it.including(original)
        }
        networkMutex.withLock {
            val found = withTimeout(OnlineLyricsClient.TOTAL_TIMEOUT_MS) {
                client.find(LyricQuery.from(track), track.fileName, LyricAlternatives(current.origin, history))
            } ?: return@withLock null
            currentCoroutineContext().ensureActive()
            val selected = CachedLyric(found.text, found.origin, nowMs().coerceAtLeast(0),
                selected = true, history = found.history, versions = listOfNotNull(document?.version()?.let {
                    if (it.savedAtMs == 0L) it.copy(savedAtMs = nowMs().coerceAtLeast(0)) else it
                }))
            persistSelection(track, selected, generation)
        }
    }

    suspend fun saveTiming(track: Track, document: CachedLyric, offsetMs: Long): LyricResult =
        withContext(Dispatchers.IO) {
            LyricTiming.requireOffset(offsetMs)
            if (LyricsParser.parse(document.text)?.timed != true) throw IOException("Lyrics have no synchronized timestamps")
            val selected = document.copy(offsetMs = offsetMs, selected = true,
                savedAtMs = document.savedAtMs.takeIf { it > 0 } ?: nowMs().coerceAtLeast(0))
            persistSelection(track, selected, cache.generation)
        }

    private suspend fun persistSelection(track: Track, document: CachedLyric, generation: Long): LyricResult {
        val key = key(LyricQuery.from(track), track.fileName)
        val saved = cache.retain(key, document, generation)
            ?: throw IOException("The lyric selection could not be saved in the active cache")
        currentCoroutineContext().ensureActive()
        return saved.result(cached = true).also {
            publish(track.id, it)
        }
    }

    suspend fun available(track: Track, current: LyricResult?, known: LyricChoices? = null): LyricChoices = withContext(Dispatchers.IO) {
        val generation = cache.generation
        val saved = cache.read(key(LyricQuery.from(track), track.fileName))
        val localChoices = if (known == null) local.available(track) else LocalLyricChoices(
            known.choices.filterNot { it.cached }, known.issue?.takeUnless { it == LyricIssue.CACHE_UNAVAILABLE })
        currentCoroutineContext().ensureActive()
        val choices = linkedMapOf<String, LyricChoice>()
        var cacheIssue = saved.unavailable
        fun add(version: LyricVersion, cached: Boolean) {
            try {
                val parsed = LyricsParser.parse(version.text) ?: throw IOException("Empty saved lyric version")
                val preview = parsed.lines.firstOrNull { it.text.isNotBlank() }?.text.orEmpty().take(160)
                choices[version.id] = LyricChoice(version.id, version.name, version.origin, version, cached = cached, preview = preview)
            } catch (error: IOException) {
                cacheIssue = true
                Log.w(TAG, "Cannot read a saved lyric version", error)
            }
        }
        saved.value?.let { document ->
            (listOf(document.version()) + document.versions).forEach { add(it, true) }
        }
        val currentVersion = current?.document?.version()
        currentVersion?.let { if (it.id !in choices) add(it, current.cached) }
        localChoices.choices.forEach { choice ->
            if (choice.key !in choices) choices[choice.key] = choice
        }
        LyricChoices(track.id, choices.values.toList(), currentVersion?.id, saved.value?.bestVersionId,
            loading = false, issue = if (cacheIssue) LyricIssue.CACHE_UNAVAILABLE else localChoices.issue,
            generation = generation)
    }

    private suspend fun resolve(track: Track, choice: LyricChoice, generation: Long): CachedLyric {
        if (generation != cache.generation) throw IOException("The lyric cache changed; reopen the version picker")
        if (choice.cached) {
            val record = cache.read(key(LyricQuery.from(track), track.fileName)).value
                ?: throw IOException("This downloaded lyric version is no longer available")
            val version = (listOf(record.version()) + record.versions).firstOrNull { it.id == choice.key }
                ?: throw IOException("This downloaded lyric version has expired or been removed")
            return version.document().copy(history = record.history)
        }
        choice.version?.let { return it.document() }
        val document = local.readFile(choice.uri ?: throw IOException("The lyric file is missing"), choice.name)
        if (document.version().id != choice.key) throw IOException("The lyric file changed; reopen the version picker")
        return document
    }

    suspend fun select(track: Track, choice: LyricChoice, generation: Long): LyricResult = withContext(Dispatchers.IO) {
        val document = resolve(track, choice, generation)
        if (document.result().lyrics == null) throw IOException("The selected lyric version is empty")
        val selected = document.copy(selected = false, offlineSelected = true, automatic = false,
            savedAtMs = document.savedAtMs.takeIf { it > 0 } ?: nowMs().coerceAtLeast(0))
        val saved = cache.retain(key(LyricQuery.from(track), track.fileName), selected, generation, preserveActive = true)
            ?: throw IOException("The lyric selection could not be saved in the active cache")
        currentCoroutineContext().ensureActive()
        withStoredVersions(selected, saved).result(cached = true).also {
            publish(track.id, it)
        }
    }

    suspend fun best(
        track: Track, choice: LyricChoice?, current: LyricResult?, generation: Long,
    ): LyricResult? = withContext(Dispatchers.IO) {
        val preferred = choice?.let { resolve(track, it, generation) }?.let {
            if (it.result().lyrics == null) throw IOException("The preferred lyric version is empty")
            it.copy(savedAtMs = it.savedAtMs.takeIf { time -> time > 0 } ?: nowMs().coerceAtLeast(0))
        }
        val key = key(LyricQuery.from(track), track.fileName)
        val previous = cache.read(key).value
        val active = current?.document ?: previous ?: preferred
            ?: throw IOException("No saved lyric version is available")
        val value = active.copy(offlineSelected = true,
            automatic = active.automatic && (current?.document != null || previous != null),
            savedAtMs = active.savedAtMs.takeIf { it > 0 } ?: nowMs().coerceAtLeast(0),
            versions = listOfNotNull(preferred?.version()) + active.versions)
        val saved = cache.retain(key, value, generation, preferred?.version()?.id, changeBest = true, preserveActive = true)
            ?: throw IOException("The preferred lyric version could not be saved")
        currentCoroutineContext().ensureActive()
        current?.document?.let {
            withStoredVersions(it, saved).result(cached = true).also { result -> publish(track.id, result) }
        }
    }

    private fun withStoredVersions(document: CachedLyric, saved: CachedLyric): CachedLyric {
        val id = document.version().id
        return document.copy(history = saved.history, bestVersionId = saved.bestVersionId,
            versions = (listOf(saved.version()) + saved.versions).filterNot { it.id == id })
    }

    private fun failed(key: String, local: LyricResult, error: Exception, generation: Long): LyricResult {
        Log.w(TAG, "Lyric lookup unavailable; playback is unchanged", error)
        rememberMiss(key, LyricIssue.LOOKUP_FAILED, generation)
        return local.copy(issue = when {
            local.issue == LyricIssue.LOCAL_UNREADABLE -> local.issue
            cache.info.value.unavailable -> LyricIssue.CACHE_UNAVAILABLE
            else -> LyricIssue.LOOKUP_FAILED
        })
    }

    private fun rememberMiss(key: String, issue: LyricIssue, generation: Long) {
        val lifetime = if (issue == LyricIssue.LOOKUP_FAILED) 30_000 else 10 * 60_000
        misses[key] = Miss(nowMs() + lifetime, issue, generation)
        while (misses.size > 100) misses.remove(misses.keys.first())
    }

    private fun key(query: LyricQuery, fileName: String): String = MessageDigest.getInstance("SHA-256")
        .digest("v2\u0000${query.title}\u0000${query.artist}\u0000${query.durationMs}\u0000$fileName".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object { const val TAG = "LyricsRepository" }
}
