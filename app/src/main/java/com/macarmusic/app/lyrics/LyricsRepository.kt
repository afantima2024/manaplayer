package com.macarmusic.app.lyrics

import android.content.Context
import android.util.Log
import com.macarmusic.app.data.AtomicTextStore
import com.macarmusic.app.data.MusicRepository
import com.macarmusic.app.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class LyricsRepository(
    context: Context,
    music: MusicRepository,
    scope: CoroutineScope,
    private val client: OnlineLyricsClient = OnlineLyricsClient(HttpsLyricsTransport(scope)) { provider, error ->
        Log.w(TAG, "$provider lyric lookup failed", error)
    },
) {
    private val local = LocalLyricsLoader(context, music)
    private val directory = File(context.cacheDir, "lyrics")
    private val mutex = Mutex()
    private val misses = linkedMapOf<String, Pair<Long, LyricIssue>>()

    suspend fun load(track: Track, online: Boolean, refresh: Boolean = false): LyricResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val localResult = local.load(track)
            if (localResult.lyrics != null || !online) return@withLock localResult
            currentCoroutineContext().ensureActive()
            val query = LyricQuery.from(track)
            val key = key(query, track.fileName)
            if (!refresh) {
                readCache(key)?.let {
                    return@withLock LyricResult(LyricsParser.parse(it.text), it.origin, cached = true, issue = localResult.issue)
                }
                misses[key]?.takeIf { it.first > System.currentTimeMillis() }?.let {
                    return@withLock localResult.copy(issue = localResult.issue ?: it.second)
                }
            }
            try {
                val found = withTimeout(25_000) { client.find(query, track.fileName) }
                if (found == null) {
                    rememberMiss(key, LyricIssue.NO_MATCH)
                    localResult.copy(issue = localResult.issue ?: LyricIssue.NO_MATCH)
                } else {
                    currentCoroutineContext().ensureActive()
                    saveCache(key, found)
                    misses.remove(key)
                    LyricResult(LyricsParser.parse(found.text), found.origin, issue = localResult.issue)
                }
            } catch (error: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                failed(key, localResult, error)
            } catch (error: IOException) {
                failed(key, localResult, error)
            }
        }
    }

    private fun failed(key: String, local: LyricResult, error: Exception): LyricResult {
        Log.w(TAG, "Lyric lookup unavailable; playback is unchanged", error)
        rememberMiss(key, LyricIssue.LOOKUP_FAILED)
        return local.copy(issue = if (local.issue == LyricIssue.LOCAL_UNREADABLE) local.issue else LyricIssue.LOOKUP_FAILED)
    }

    private fun rememberMiss(key: String, issue: LyricIssue) {
        misses[key] = System.currentTimeMillis() + 10 * 60_000 to issue
        while (misses.size > 100) misses.remove(misses.keys.first())
    }

    private fun readCache(key: String): CachedLyric? {
        val file = File(directory, "$key.json")
        if (!file.exists()) return null
        return try {
            if (file.length() > 2 * LyricsParser.MAX_BYTES || file.lastModified() < System.currentTimeMillis() - 30L * 86_400_000) {
                if (!file.delete()) Log.w(TAG, "Could not remove an expired lyric cache entry")
                null
            } else Json.decodeFromString<CachedLyric>(AtomicTextStore(file).read() ?: return null)
                .takeIf { LyricsParser.parse(it.text) != null }
        } catch (error: IOException) {
            Log.w(TAG, "Lyric cache could not be read", error)
            null
        } catch (error: SerializationException) {
            Log.w(TAG, "Lyric cache is invalid", error)
            null
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Lyric cache contains invalid values", error)
            null
        }
    }

    private fun saveCache(key: String, lyric: OnlineLyric) {
        try {
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create lyric cache")
            val file = File(directory, "$key.json")
            AtomicTextStore(file).write(Json.encodeToString(CachedLyric(lyric.text, lyric.origin)))
            val files = directory.listFiles()?.filter { it.extension == "json" }?.sortedBy { it.lastModified() }
                ?: throw IOException("Cannot read lyric cache")
            var size = files.sumOf(File::length)
            var count = files.size
            for (entry in files) {
                if (size <= 4 * 1024 * 1024 && count <= 100) break
                val length = entry.length()
                if (entry.delete()) { size -= length; count-- }
                else Log.w(TAG, "Could not remove an old lyric cache entry")
            }
        } catch (error: IOException) {
            Log.w(TAG, "Lyrics loaded but could not be cached", error)
        }
    }

    private fun key(query: LyricQuery, fileName: String): String = MessageDigest.getInstance("SHA-256")
        .digest("v2\u0000${query.title}\u0000${query.artist}\u0000${query.durationMs}\u0000$fileName".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    @Serializable private data class CachedLyric(val text: String, val origin: LyricOrigin)
    private companion object { const val TAG = "LyricsRepository" }
}
