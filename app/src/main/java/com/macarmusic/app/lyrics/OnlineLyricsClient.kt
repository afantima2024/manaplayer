package com.macarmusic.app.lyrics

import com.macarmusic.app.data.BoundedInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
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

class HttpsLyricsTransport(
    private val scope: CoroutineScope,
    private val openConnection: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection },
) : LyricsTransport {
    override suspend fun request(url: String, body: String?, referer: String?): String {
        val address = URL(url)
        require(address.protocol == "https" && address.host in LyricProviderConfig.allowedHosts)
        return suspendCancellableCoroutine { continuation ->
            val connection = openConnection(address)
            val worker = scope.launch(Dispatchers.IO) {
                try {
                    ensureActive()
                    connection.apply {
                        connectTimeout = 8_000
                        readTimeout = 8_000
                        instanceFollowRedirects = false
                        useCaches = false
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        setRequestProperty("Accept", "application/json, text/plain, */*")
                        setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
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
                // Cancellation can originate on the UI thread; closing TLS can perform network I/O.
                Dispatchers.IO.asExecutor().execute { connection.disconnect() }
            }
        }
    }

    private companion object {
        const val MAX_RESPONSE = 1024 * 1024
    }
}

enum class LyricSearchRoute(val origin: LyricOrigin) {
    KUGOU_DIRECT(LyricOrigin.KUGOU), KUGOU_MOBILE(LyricOrigin.KUGOU),
    QQ(LyricOrigin.QQ), NETEASE(LyricOrigin.NETEASE), KUWO(LyricOrigin.KUWO),
}

data class OnlineLyric(val text: String, val origin: LyricOrigin, val history: LyricSearchHistory = LyricSearchHistory())

class OnlineLyricsClient(
    private val transport: LyricsTransport,
    private val providerTimeoutMs: Long = 33_000,
    private val reportFailure: (LyricOrigin, Exception) -> Unit,
) {
    init { require(providerTimeoutMs > 0) }

    fun search(query: LyricQuery, fileName: String = ""): Flow<OnlineLyricCatalog> = channelFlow {
        val identity = LyricQueries.candidates(query, fileName).firstOrNull()
            ?: throw IOException("No usable song title is available")
        if (ROUTES.isEmpty()) throw IOException("No lyric providers are enabled in the project configuration")
        val requests = Semaphore(2)
        for (route in ROUTES) launch {
            requests.withPermit {
                val attempt = SearchAttempt(null).apply { catalog = mutableListOf() }
                try {
                    withTimeout(providerTimeoutMs) { findRoute(route, identity, attempt) }
                    send(OnlineLyricCatalog(route, checkNotNull(attempt.catalog)))
                } catch (error: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    reportFailure(route.origin, IOException("$route lyric search timed out", error))
                    send(OnlineLyricCatalog(route, failed = true))
                } catch (error: IOException) {
                    reportFailure(route.origin, error)
                    send(OnlineLyricCatalog(route, failed = true))
                } catch (error: IllegalArgumentException) {
                    reportFailure(route.origin, error)
                    send(OnlineLyricCatalog(route, failed = true))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun download(candidate: OnlineLyricCandidate): OnlineLyric? {
        require(candidate.key.valid() && candidate.key.route in ROUTES)
        val text = withTimeout(providerTimeoutMs) {
            when (candidate.key.route) {
                LyricSearchRoute.KUGOU_DIRECT -> downloadKugou(candidate.key.id, candidate.accessKey, mobile = false)
                LyricSearchRoute.KUGOU_MOBILE -> downloadKugou(candidate.key.id, candidate.key.id, mobile = true)
                LyricSearchRoute.QQ -> downloadQq(candidate.key.id)
                LyricSearchRoute.NETEASE -> downloadNetease(candidate.key.id)
                LyricSearchRoute.KUWO -> downloadKuwo(candidate.key.id)
            }
        } ?: return null
        val lyrics = LyricsParser.parse(text) ?: return null
        return OnlineLyric(text, candidate.key.route.origin, LyricSearchHistory(
            listOf(candidate.key), listOf(LyricSearchHistory.fingerprint(lyrics))))
    }

    suspend fun find(query: LyricQuery, fileName: String = "", alternatives: LyricAlternatives? = null): OnlineLyric? {
        val identity = LyricQueries.candidates(query, fileName).firstOrNull() ?: return null
        if (ROUTES.isEmpty()) throw IOException("No lyric providers are enabled in the project configuration")
        val attempt = SearchAttempt(alternatives)
        val start = ROUTES.indexOfFirst { it.origin == alternatives?.from }.coerceAtLeast(0)
        for (route in ROUTES.drop(start)) {
            currentCoroutineContext().ensureActive()
            try {
                val found = withTimeout(providerTimeoutMs) {
                    findRoute(route, identity, attempt)
                }
                if (found != null) return found
            } catch (error: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                val timeout = IOException("$route lyric search timed out", error)
                reportFailure(route.origin, timeout)
                attempt.failure = timeout
            } catch (error: IOException) {
                reportFailure(route.origin, error)
                attempt.failure = error
            } catch (error: IllegalArgumentException) {
                reportFailure(route.origin, error)
                attempt.failure = IOException("Invalid lyric response", error)
            }
        }
        attempt.failure?.let { throw it }
        return null
    }

    private suspend fun findRoute(route: LyricSearchRoute, query: LyricQuery, attempt: SearchAttempt): OnlineLyric? =
        when (route) {
            LyricSearchRoute.KUGOU_DIRECT -> kugouDirect(query, attempt)
            LyricSearchRoute.KUGOU_MOBILE -> kugouMobile(query, attempt)
            LyricSearchRoute.QQ -> qq(query, attempt)
            LyricSearchRoute.NETEASE -> netease(query, attempt)
            LyricSearchRoute.KUWO -> kuwo(query, attempt)
        }

    private suspend fun kugouDirect(query: LyricQuery, attempt: SearchAttempt): OnlineLyric? {
        val duration = if (query.durationMs > 0) (query.durationMs / 1_000).toString() else ""
        val response = json(transport.request(
            "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=${encode(LyricQueries.keyword(query, true))}&duration=$duration",
            null, null,
        ))
        if (response.number("status") != 200L) throw IOException("Kugou lyric search is unavailable")
        return pick(LyricSearchRoute.KUGOU_DIRECT, attempt, response.requiredArray("candidates"), query,
            "song", { it.text("singer") }, { it.text("id") },
            // The current lyric endpoint reports milliseconds, unlike the reference's older seconds field.
            duration = { it.number("duration").takeIf { value -> value in 1..86_400_000 } ?: 0 }, minimumScore = 10) { song ->
            downloadKugou(song.text("id"), song.text("accesskey"), mobile = false)
        }
    }

    private suspend fun kugouMobile(query: LyricQuery, attempt: SearchAttempt): OnlineLyric? {
        val response = json(transport.request(
            "https://mobilecdn.kugou.com/api/v3/search/song?keyword=${encode(LyricQueries.keyword(query, true))}&page=1&pagesize=10&showtype=1",
            null, null,
        ))
        if (response.number("status") != 1L || response.number("error_code") != 0L) {
            throw IOException("Kugou mobile search is unavailable")
        }
        val songs = (response["data"] as? JsonObject)?.requiredArray("info")
            ?: throw IOException("Kugou mobile search returned no catalog")
        return pick(LyricSearchRoute.KUGOU_MOBILE, attempt, songs, query,
            "songname", { it.text("singername") }, { it.text("hash") },
            selectable = { it.text("hash").isNotBlank() }) { song ->
            downloadKugou(song.text("hash"), song.text("hash"), mobile = true)
        }
    }

    private suspend fun downloadKugou(id: String, key: String, mobile: Boolean): String? {
        if (key.isBlank()) return null
        val charset = if (mobile) "" else "&charset=utf8"
        val download = json(transport.request(
            "https://lyrics.kugou.com/download?ver=1&client=pc&id=${encode(id)}&accesskey=${encode(key)}&fmt=lrc$charset",
            null, null,
        ))
        if (download.number("status") != 200L) throw IOException("Kugou lyric download is unavailable")
        val content = download.text("content")
        return if (content.isBlank()) null else fetched(decodeContent(content))
    }

    private suspend fun netease(query: LyricQuery, attempt: SearchAttempt): OnlineLyric? {
        val referer = "https://music.163.com"
        val response = json(transport.request(
            "https://music.163.com/api/search/get/web?s=${encode(LyricQueries.keyword(query, false))}&type=1&offset=0&total=true&limit=10",
            null, referer,
        ))
        if (response.number("code") != 200L) throw IOException("NetEase lyric search is unavailable")
        val result = response["result"] as? JsonObject ?: throw IOException("NetEase search returned no result")
        val songs = result["songs"] as? JsonArray ?: if ((result["songCount"] as? JsonPrimitive)?.longOrNull == 0L) {
            JsonArray(emptyList())
        } else throw IOException("NetEase search returned no catalog")
        return pick(LyricSearchRoute.NETEASE, attempt, songs, query, "name", { it.firstArtist("artists") },
            { it.number("id").takeIf { id -> id > 0 }?.toString().orEmpty() }) { song ->
            downloadNetease(song.number("id").toString())
        }
    }

    private suspend fun downloadNetease(id: String): String? {
        val download = json(transport.request(
            "https://music.163.com/api/song/lyric?id=${encode(id)}&lv=1&kv=1&tv=-1", null, "https://music.163.com",
        ))
        if (download.number("code") != 200L) throw IOException("NetEase lyric download is unavailable")
        val original = (download["lrc"] as? JsonObject) ?: return null
        return original.text("lyric").takeUnless(::emptyLyrics)
            ?: (download["tlyric"] as? JsonObject)?.text("lyric")?.takeUnless(::emptyLyrics)
    }

    private suspend fun qq(query: LyricQuery, attempt: SearchAttempt): OnlineLyric? {
        val referer = "https://y.qq.com/"
        val response = json(transport.request(
            "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=${encode(LyricQueries.keyword(query, false))}&format=json&p=1&n=10",
            null, referer,
        ))
        if ((response["code"] as? JsonPrimitive)?.longOrNull != 0L) throw IOException("QQ lyric search is unavailable")
        val songs = ((response["data"] as? JsonObject)?.get("song") as? JsonObject)?.requiredArray("list")
            ?: throw IOException("QQ search returned no catalog")
        return pick(LyricSearchRoute.QQ, attempt, songs, query, "songname", { it.firstArtist("singer") },
            { it.text("songmid") }) { song ->
            downloadQq(song.text("songmid"))
        }
    }

    private suspend fun downloadQq(id: String): String? {
        val download = json(transport.request(
            "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=${encode(id)}&g_tk=5381&format=json&nobase64=0",
            null, "https://y.qq.com/",
        ))
        if ((download["code"] as? JsonPrimitive)?.longOrNull != 0L) throw IOException("QQ lyric download is unavailable")
        val content = download.text("lyric")
        if (content.isBlank()) return null
        val text = LyricResponse.decode(LyricsParser.decode(decodeBase64(content)))
        if (!emptyLyrics(text)) return text
        val translated = download.text("trans")
        return if (translated.isBlank()) null
            else LyricResponse.decode(LyricsParser.decode(decodeBase64(translated))).takeUnless(::emptyLyrics)
    }

    private suspend fun kuwo(query: LyricQuery, attempt: SearchAttempt): OnlineLyric? {
        val raw = transport.request(
            "https://search.kuwo.cn/r.s?all=${encode(LyricQueries.keyword(query, true))}&ft=music&itemset=web_2013&client=kt&pn=0&rn=10&rformat=json&encoding=utf8",
            null, null,
        )
        val response = LyricResponse.json(raw.substring(raw.indexOf('{').coerceAtLeast(0)), singleQuoted = true)
        return pick(LyricSearchRoute.KUWO, attempt, response.requiredArray("abslist"), query, "SONGNAME",
            { it.text("ARTIST").replace("&nbsp;", " ") },
            { it.text("MUSICRID").removePrefix("MUSIC_") },
            selectable = { it.text("MUSICRID").removePrefix("MUSIC_").isNotBlank() }) { song ->
            downloadKuwo(song.text("MUSICRID").removePrefix("MUSIC_"))
        }
    }

    private suspend fun downloadKuwo(id: String): String? {
        val download = json(transport.request(
            "https://www.kuwo.cn/openapi/v1/www/lyric/getlyric?musicId=${encode(id)}", null, "https://www.kuwo.cn/",
        ))
        val lines = (download["data"] as? JsonObject)?.requiredArray("lrclist")
            ?: throw IOException("Kuwo returned no lyric data")
        if (lines.size > 4_000) throw IOException("Too many lyric lines")
        val text = buildString {
            for (item in lines) {
                val line = item as? JsonObject ?: throw IOException("Invalid Kuwo lyric line")
                val words = line.text("lineLyric")
                if (words.isEmpty()) continue
                val seconds = (line["time"] as? JsonPrimitive)?.doubleOrNull?.toFloat()
                if (seconds == null || !seconds.isFinite() || seconds !in 0f..<86_400f) {
                    reportFailure(LyricOrigin.KUWO, IOException("Invalid Kuwo lyric timestamp"))
                    continue
                }
                val minute = (seconds / 60).toInt()
                val second = (seconds % 60).toInt()
                val fraction = ((seconds - seconds.toInt()) * 100).toInt()
                append('[').append(minute.toString().padStart(2, '0')).append(':')
                    .append(second.toString().padStart(2, '0')).append('.')
                    .append(fraction.toString().padStart(2, '0')).append(']').append(words).append('\n')
            }
        }
        return fetched(LyricResponse.decode(text))
    }

    private data class Match(val song: JsonObject, val score: Int)

    private class SearchAttempt(alternatives: LyricAlternatives?) {
        val next = alternatives != null
        val candidates = alternatives?.history?.candidates?.toMutableSet() ?: linkedSetOf()
        val contents = alternatives?.history?.contents?.toMutableSet() ?: linkedSetOf()
        var failure: IOException? = null
        var catalog: MutableList<OnlineLyricCandidate>? = null

        init { alternatives?.history?.validate() }
        fun history() = LyricSearchHistory(candidates.toList(), contents.toList()).also { it.validate() }
    }

    private suspend fun pick(
        route: LyricSearchRoute,
        attempt: SearchAttempt,
        songs: JsonArray,
        query: LyricQuery,
        titleField: String,
        artist: (JsonObject) -> String,
        identifier: (JsonObject) -> String,
        duration: (JsonObject) -> Long = { 0 },
        selectable: (JsonObject) -> Boolean = { true },
        minimumScore: Int = Int.MIN_VALUE,
        download: suspend (JsonObject) -> String?,
    ): OnlineLyric? {
        val ranked = songs.mapIndexedNotNull { index, item ->
            val song = item as? JsonObject ?: throw IOException("Invalid lyric search candidate")
            val title = song.text(titleField).replace("&nbsp;", " ")
            val score = LyricMatch.score(query, title, artist(song), duration(song), index)
            if (selectable(song) && (attempt.catalog != null || score >= minimumScore)) Match(song, score) else null
        }.sortedByDescending { it.score }.take(if (attempt.next || attempt.catalog != null) MAX_CANDIDATES else 1)
        for (match in ranked) {
            currentCoroutineContext().ensureActive()
            val candidate = LyricCandidateId(route, identifier(match.song))
            if (!candidate.valid() || candidate in attempt.candidates) continue
            attempt.catalog?.let { catalog ->
                val song = match.song
                val key = if (route == LyricSearchRoute.KUGOU_DIRECT) song.text("accesskey") else ""
                if ((route != LyricSearchRoute.KUGOU_DIRECT || key.isNotBlank()) && key.length <= 160) {
                    catalog.add(OnlineLyricCandidate(candidate,
                        song.text(titleField).replace("&nbsp;", " ").take(200).ifBlank { query.title },
                        artist(song).take(200), catalogDuration(route, song), key))
                }
                attempt.candidates.add(candidate)
            }
            if (attempt.catalog != null) continue
            try {
                val text = download(match.song)
                val lyrics = text?.let { LyricsParser.parse(it) }
                attempt.candidates.add(candidate)
                if (text == null || lyrics == null) continue
                if (!attempt.contents.add(LyricSearchHistory.fingerprint(lyrics))) continue
                return OnlineLyric(text, route.origin, attempt.history())
            } catch (error: IOException) {
                if (!attempt.next) throw error
                reportFailure(route.origin, error)
                attempt.failure = error
            }
        }
        return null
    }

    private fun catalogDuration(route: LyricSearchRoute, song: JsonObject): Long {
        val value = when (route) {
            LyricSearchRoute.KUGOU_DIRECT, LyricSearchRoute.NETEASE -> song.number("duration")
            LyricSearchRoute.KUGOU_MOBILE -> song.number("duration").coerceIn(0, 86_400) * 1_000
            LyricSearchRoute.QQ -> song.number("interval").coerceIn(0, 86_400) * 1_000
            LyricSearchRoute.KUWO -> song.number("DURATION").coerceIn(0, 86_400) * 1_000
        }
        return value.takeIf { it in 1..86_400_000 } ?: 0
    }

    private fun fetched(text: String): String? = text.takeIf {
        it.lineSequence().map(String::trim).filter(TIMED_LINE::matches).take(8).count() >= 8
    }

    private fun decodeContent(value: String): String {
        val text = LyricResponse.decode(value)
        return if ('[' in text) text else LyricResponse.decode(LyricsParser.decode(decodeBase64(text)))
    }

    private fun emptyLyrics(text: String): Boolean = text.isBlank() || text.trim() == "[]"
    private fun json(text: String): JsonObject = LyricResponse.json(text)

    private fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.number(key: String): Long = (get(key) as? JsonPrimitive)?.longOrNull ?: 0
    private fun JsonObject.array(key: String): JsonArray = get(key) as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.firstArtist(key: String): String = (array(key).firstOrNull() as? JsonObject)?.text("name").orEmpty()
    private fun JsonObject.requiredArray(key: String): JsonArray = get(key) as? JsonArray
        ?: throw IOException("Lyric response is missing $key")
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    // java.util.Base64 requires API 26; this small strict decoder also keeps provider tests JVM-only.
    internal fun decodeBase64(value: String): ByteArray {
        val unpadded = value.filterNot(Char::isWhitespace)
        if (unpadded.length > LyricsParser.MAX_BYTES * 4 / 3 + 4 || unpadded.length % 4 == 1 ||
            ('=' in unpadded && unpadded.length % 4 != 0)) {
            throw IOException("Invalid lyric encoding")
        }
        val compact = unpadded.padEnd((unpadded.length + 3) / 4 * 4, '=')
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
        const val TOTAL_TIMEOUT_MS = 166_000L
        val ROUTES: List<LyricSearchRoute> = LyricProviderConfig.routes
        val PROVIDERS: List<LyricOrigin> = LyricSearchRoute.entries.map { it.origin }.distinct()
        private const val MAX_CANDIDATES = 10
        private val TIMED_LINE = Regex("^\\[\\d{1,2}:\\d{2}(?:\\.\\d{1,3})?].*$")
    }
}
