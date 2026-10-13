package com.macarmusic.app.lyrics

import com.macarmusic.app.data.BoundedInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun interface LyricsTransport {
    suspend fun request(url: String, body: String?, referer: String?): String
}

class HttpsLyricsTransport(private val scope: CoroutineScope) : LyricsTransport {
    override suspend fun request(url: String, body: String?, referer: String?): String {
        val address = URL(url)
        require(address.protocol == "https" && address.host in ALLOWED_HOSTS)
        if (address.host == "songsearch.kugou.com") catalogRequests.await()
        return suspendCancellableCoroutine { continuation ->
            val connection = address.openConnection() as HttpsURLConnection
            val worker = scope.launch(Dispatchers.IO) {
                try {
                    ensureActive()
                    connection.apply {
                        connectTimeout = 5_000
                        readTimeout = 6_000
                        instanceFollowRedirects = false
                        useCaches = false
                        setRequestProperty("User-Agent", "Mozilla/5.0 ManaMusicPlayer/1.8.0")
                        if (referer != null) setRequestProperty("Referer", referer)
                        if (body != null) {
                            requestMethod = "POST"
                            doOutput = true
                            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                            val bytes = body.toByteArray(Charsets.UTF_8)
                            setFixedLengthStreamingMode(bytes.size)
                            outputStream.use { it.write(bytes) }
                        }
                    }
                    if (connection.responseCode !in 200..299) {
                        throw IOException("Lyric service returned HTTP ${connection.responseCode}")
                    }
                    if (connection.contentLength > MAX_RESPONSE) throw IOException("Lyric response is too large")
                    val bytes = connection.inputStream.use { input ->
                        BoundedInput.read(input, MAX_RESPONSE) { ensureActive() }
                    }
                    continuation.resume(String(bytes, Charsets.UTF_8))
                } catch (error: IOException) {
                    continuation.resumeWithException(error)
                } catch (error: SecurityException) {
                    continuation.resumeWithException(IOException("Lyric connection was denied", error))
                } catch (error: CancellationException) {
                    continuation.cancel(error)
                } finally {
                    connection.disconnect()
                }
            }
            continuation.invokeOnCancellation {
                worker.cancel()
                connection.disconnect()
            }
        }
    }

    private companion object {
        const val MAX_RESPONSE = 1024 * 1024
        val ALLOWED_HOSTS = setOf("songsearch.kugou.com", "lyrics.kugou.com", "music.163.com")
        val catalogRequests = CatalogRequestPacer()
    }
}

internal class CatalogRequestPacer(
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private var lastRequestMs: Long? = null

    suspend fun await() = mutex.withLock {
        // Closely spaced Kugou catalog requests can return status=1 with an empty list.
        val remaining = lastRequestMs?.let { 10_000 - (nowMs() - it) } ?: 0
        if (remaining > 0) wait(remaining)
        lastRequestMs = nowMs()
    }
}

data class OnlineLyric(val text: String, val origin: LyricOrigin)

class OnlineLyricsClient(
    private val transport: LyricsTransport,
    private val providerTimeoutMs: Long = 15_000,
    private val reportFailure: (LyricOrigin, Exception) -> Unit,
) {
    init { require(providerTimeoutMs > 0) }

    suspend fun find(query: LyricQuery, fileName: String = ""): OnlineLyric? {
        val queries = LyricQueries.candidates(query, fileName)
        var failure: IOException? = null
        for (provider in PROVIDERS) {
            try {
                val found = withTimeout(providerTimeoutMs) {
                    for (candidate in queries) {
                        currentCoroutineContext().ensureActive()
                        val result = if (provider == LyricOrigin.KUGOU) kugou(candidate) else netease(candidate)
                        if (result != null && LyricsParser.parse(result) != null) {
                            return@withTimeout OnlineLyric(result, provider)
                        }
                    }
                    null
                }
                if (found != null) return found
            } catch (error: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                val timeout = IOException("$provider lyric search timed out", error)
                reportFailure(provider, timeout)
                failure = timeout
            } catch (error: IOException) {
                reportFailure(provider, error)
                failure = error
            } catch (error: SerializationException) {
                reportFailure(provider, error)
                failure = IOException("Invalid lyric response", error)
            } catch (error: IllegalArgumentException) {
                reportFailure(provider, error)
                failure = IOException("Invalid lyric response", error)
            }
        }
        if (failure != null) throw failure
        return null
    }

    private suspend fun kugou(query: LyricQuery): String? {
        val referer = "https://www.kugou.com/"
        val keywords = listOf(listOf(query.title, LyricMatch.artistKeywords(query.artist))
            .filter(String::isNotBlank).joinToString(" "), query.title).distinct()
        val tried = mutableSetOf<String>()
        for (keyword in keywords) {
            val catalog = json(transport.request(
                "https://songsearch.kugou.com/song_search_v2?keyword=${encode(keyword)}&page=1&pagesize=10&platform=WebFilter&filter=2",
                null, referer,
            ))
            if (catalog.number("status") != 1L || catalog.number("error_code") != 0L) {
                throw IOException("Kugou song search is unavailable")
            }
            val entries = (catalog["data"] as? JsonObject)?.get("lists") as? JsonArray
                ?: throw IOException("Kugou song search returned no catalog")
            val hashes = entries.take(10).mapNotNull { item ->
                val song = item as? JsonObject ?: return@mapNotNull null
                val duration = song.number("Duration").takeIf { it in 1L..86_400L }?.times(1_000) ?: 0
                val score = LyricMatch.score(query, song.text("SongName"), song.text("SingerName"), duration)
                    ?: return@mapNotNull null
                val hash = song.text("FileHash").uppercase()
                if (hash.matches(Regex("[A-F0-9]{32}"))) score to hash else null
            }.sortedByDescending { it.first }.map { it.second }.distinct()
            for (hash in hashes) {
                if (tried.size >= 2) return null
                if (!tried.add(hash)) continue
                kugouLyrics(query, hash)?.let { return it }
            }
        }
        return null
    }

    private suspend fun kugouLyrics(query: LyricQuery, hash: String): String? {
        val referer = "https://www.kugou.com/"
        val response = json(transport.request(
            "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=${encode(hash)}",
            null, referer,
        ))
        if (response.number("status") != 200L) throw IOException("Kugou lyric search is unavailable")
        val candidates = response.array("candidates").take(20).mapNotNull { item ->
            val song = item as? JsonObject ?: return@mapNotNull null
            // Song search reports seconds, but lyric candidates report milliseconds.
            val score = LyricMatch.score(query, song.text("song"), song.text("singer"), song.number("duration"))
                ?: return@mapNotNull null
            val id = song.text("id")
            val key = song.text("accesskey")
            if (id.isBlank() || key.isBlank()) null else Triple(score, id, key)
        }.sortedByDescending { it.first }.take(2)
        for ((_, id, key) in candidates) {
            val download = json(transport.request(
                "https://lyrics.kugou.com/download?ver=1&client=pc&id=${encode(id)}&accesskey=${encode(key)}&fmt=lrc&charset=utf8",
                null, referer,
            ))
            if (download.number("status") != 200L) throw IOException("Kugou lyric download is unavailable")
            val encoded = download.text("content")
            if (encoded.isBlank()) continue
            val bytes = decodeBase64(encoded)
            val text = LyricsParser.decode(bytes)
            if (LyricsParser.parse(text) != null) return text
        }
        return null
    }

    private suspend fun netease(query: LyricQuery): String? {
        val keyword = encode(listOf(query.title, LyricMatch.artistKeywords(query.artist)).filter(String::isNotBlank).joinToString(" "))
        val response = json(transport.request(
            "https://music.163.com/api/search/get/web", "s=$keyword&type=1&limit=10&offset=0", "https://music.163.com/",
        ))
        if (response.number("code") != 200L) throw IOException("NetEase lyric search is unavailable")
        val result = response["result"] as? JsonObject ?: throw IOException("NetEase search returned no result")
        val songs = result["songs"] as? JsonArray ?: if ((result["songCount"] as? JsonPrimitive)?.longOrNull == 0L) {
            JsonArray(emptyList())
        } else throw IOException("NetEase search returned no catalog")
        val candidates = songs.take(10).mapNotNull { item ->
            val song = item as? JsonObject ?: return@mapNotNull null
            val artists = song.array("artists").mapNotNull { (it as? JsonObject)?.text("name") }.joinToString("/")
            val score = LyricMatch.score(query, song.text("name"), artists, song.number("duration"))
                ?: return@mapNotNull null
            val id = song.number("id")
            if (id <= 0) null else score to id
        }.sortedByDescending { it.first }.take(2)
        for ((_, id) in candidates) {
            val download = json(transport.request(
                "https://music.163.com/api/song/lyric?id=$id&lv=-1&kv=-1&tv=-1", null, "https://music.163.com/",
            ))
            if (download.number("code") != 200L) throw IOException("NetEase lyric download is unavailable")
            for (field in listOf("lrc", "tlyric")) {
                val text = (download[field] as? JsonObject)?.text("lyric").orEmpty()
                if (LyricsParser.parse(text) != null) return text
            }
        }
        return null
    }

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text) as? JsonObject
        ?: throw IOException("Expected a lyric JSON object")

    private fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.number(key: String): Long = (get(key) as? JsonPrimitive)?.longOrNull ?: 0
    private fun JsonObject.array(key: String): JsonArray = get(key) as? JsonArray ?: JsonArray(emptyList())
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    // java.util.Base64 requires API 26; this small strict decoder also keeps provider tests JVM-only.
    internal fun decodeBase64(value: String): ByteArray {
        val compact = value.filterNot(Char::isWhitespace)
        if (compact.length > LyricsParser.MAX_BYTES * 4 / 3 + 4 || compact.length % 4 != 0) {
            throw IOException("Invalid lyric encoding")
        }
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val output = java.io.ByteArrayOutputStream()
        for (offset in compact.indices step 4) {
            val block = compact.substring(offset, offset + 4)
            val padding = block.takeLastWhile { it == '=' }.length
            if (padding > 2 || (padding > 0 && offset + 4 != compact.length)) throw IOException("Invalid lyric encoding")
            var bits = 0
            for ((index, character) in block.withIndex()) {
                val digit = if (character == '=' && index >= 4 - padding) 0 else alphabet.indexOf(character)
                if (digit < 0) throw IOException("Invalid lyric encoding")
                bits = (bits shl 6) or digit
            }
            output.write(bits ushr 16 and 0xff)
            if (padding < 2) output.write(bits ushr 8 and 0xff)
            if (padding < 1) output.write(bits and 0xff)
        }
        return output.toByteArray()
    }

    companion object {
        val PROVIDERS: List<LyricOrigin> = listOf(LyricOrigin.KUGOU, LyricOrigin.NETEASE)
    }
}
