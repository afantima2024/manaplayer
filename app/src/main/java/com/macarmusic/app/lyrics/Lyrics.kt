package com.macarmusic.app.lyrics

import com.macarmusic.app.model.DocumentEntry
import com.macarmusic.app.model.Track
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.util.Locale

data class LyricLine(val timeMs: Long?, val text: String)

data class Lyrics(val lines: List<LyricLine>) {
    val timed: Boolean get() = lines.firstOrNull()?.timeMs != null

    fun activeLine(positionMs: Long): Int {
        if (!timed) return -1
        var low = 0
        var high = lines.lastIndex
        while (low <= high) {
            val middle = (low + high).ushr(1)
            if (lines[middle].timeMs!! <= positionMs) low = middle + 1 else high = middle - 1
        }
        return high
    }
}

object LyricsParser {
    const val MAX_BYTES = 256 * 1024
    private const val MAX_LINES = 4_000
    private val time = Regex("\\[(\\d{1,4}):([0-5]\\d)(?:[.:](\\d{1,3}))?]")
    private val offset = Regex("\\[offset:([+-]?\\d{1,9})]", RegexOption.IGNORE_CASE)
    private val metadata = Regex("^\\[(?:ar|al|ti|au|by|re|ve|length|offset):.*]$", RegexOption.IGNORE_CASE)
    private val wordTime = Regex("<\\d{1,4}:[0-5]\\d(?:[.:]\\d{1,3})?>")

    fun decode(bytes: ByteArray): String {
        if (bytes.size > MAX_BYTES) throw IOException("Lyrics exceed the size limit")
        return when {
            bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            else -> try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: CharacterCodingException) {
                Charset.forName("GB18030").decode(ByteBuffer.wrap(bytes)).toString()
            }
        }.removePrefix("\uFEFF")
    }

    fun parse(text: String): Lyrics? {
        if (text.length > MAX_BYTES || text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            throw IOException("Lyrics exceed the size limit")
        }
        if (text.contains("<html", ignoreCase = true) || text.contains("<!doctype", ignoreCase = true)) {
            throw IOException("The lyric source returned an HTML page")
        }
        val adjustment = offset.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val timed = mutableListOf<LyricLine>()
        val plain = mutableListOf<LyricLine>()
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("\uFEFF").replace("\u0000", "")
            if (line.length > 2_000) throw IOException("Lyric line exceeds the size limit")
            if (line.isBlank() || metadata.matches(line)) continue
            val stamps = time.findAll(line).toList()
            if (stamps.isEmpty()) {
                plain.add(LyricLine(null, line))
            } else {
                val words = wordTime.replace(time.replace(line, ""), "").trim()
                for (stamp in stamps) {
                    val minutes = stamp.groupValues[1].toLong()
                    if (minutes >= 1_440) continue
                    val millis = stamp.groupValues[3].padEnd(3, '0').toLongOrNull() ?: 0
                    val start = minutes * 60_000 + stamp.groupValues[2].toLong() * 1_000 + millis
                    timed.add(LyricLine((start - adjustment).coerceIn(0, 86_400_000), words))
                }
            }
            if (timed.size + plain.size > MAX_LINES) throw IOException("Too many lyric lines")
        }
        val result = if (timed.any { it.text.isNotBlank() }) {
            timed.groupBy { it.timeMs }.toSortedMap(compareBy { it }).map { (start, lines) ->
                LyricLine(start, lines.map { it.text }.distinct().filter(String::isNotBlank).joinToString("\n"))
            }
        } else plain
        return result.takeIf { it.any { line -> line.text.isNotBlank() } }?.let(::Lyrics)
    }
}

object LyricFiles {
    fun accepts(name: String): Boolean = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("lrc", "txt")

    fun matching(track: Track, entries: List<DocumentEntry>): List<DocumentEntry> {
        val stem = normalize(track.fileName.substringBeforeLast('.', track.fileName))
        return entries.filter {
            !it.isDirectory && !it.isVirtual && accepts(it.name) &&
                normalize(it.name.substringBeforeLast('.')) == stem
        }.sortedWith(compareBy<DocumentEntry> { !it.name.endsWith(".lrc", ignoreCase = true) }.thenBy { it.name })
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()
}

data class LyricQuery(val title: String, val artist: String, val durationMs: Long) {
    companion object {
        fun from(track: Track): LyricQuery = LyricQuery(
            track.title.filterNot(Char::isISOControl).trim().take(160),
            track.artist.filterNot(Char::isISOControl).trim().take(120),
            track.durationMs.coerceIn(0, 86_400_000),
        )
    }
}

object LyricQueries {
    private val audioExtension = Regex("\\.(?:mp3|flac|wav|wave|aac|m4a|m4b|ogg|oga|opus)$", RegexOption.IGNORE_CASE)
    private val discPrefix = Regex("^(?:cd|disc)\\s*\\d{1,3}[\\s._-]+", RegexOption.IGNORE_CASE)
    private val trackPrefix = Regex(
        "^(?:track\\s*\\d{1,6}[\\s._-]+|\\d{1,6}\\s*[._、]\\s*|\\d{1,3}\\s*[-–—]\\s+|0\\d{1,2}\\s+)(?=\\p{L})",
        RegexOption.IGNORE_CASE,
    )
    private val quality = Regex(
        "[\\[（(【]\\s*(?:(?:hi[ -]?(?:res|fi)|lossless|sq|hq|flac|mp3|wav|无损|高音质|高品质)|" +
            "(?:\\d{2,3}\\s*(?:kbps|k|bit)|\\d{2,3}(?:\\.\\d+)?\\s*khz))[\\s\\w./-]*[\\]）)】]",
        RegexOption.IGNORE_CASE,
    )
    private val promotion = Regex(
        "(?:https?://|www\\.|\\.(?:com|net|org|cn)(?:\\b|/)|网(?:站|络)?分享|资源分享|音乐下载|免费下载|qq群)",
        RegexOption.IGNORE_CASE,
    )
    private val unknown = setOf("unknown", "unknownartist", "unknownalbum", "unknowntitle", "variousartists",
        "未知", "未知歌手", "未知艺术家", "未知歌曲", "未知标题")
    private val version = Regex("^(?:live|remix|dj|instrumental|acoustic|现场|伴奏|纯音乐|翻唱)(?:\\b|版)", RegexOption.IGNORE_CASE)

    fun candidates(query: LyricQuery, fileName: String = ""): List<LyricQuery> {
        val results = linkedMapOf<Pair<String, String>, LyricQuery>()
        val artist = plain(query.artist).take(120).takeUnless(::unusable).orEmpty()
        val duration = query.durationMs.coerceIn(0, 86_400_000)
        fun add(title: String, singer: String) {
            val boundedTitle = title.trim().take(160)
            val boundedArtist = singer.trim().take(120)
            if (boundedTitle.isBlank() || unusable(boundedTitle) || results.size >= 6) return
            val key = LyricMatch.normalize(boundedTitle) to LyricMatch.normalize(boundedArtist)
            results.getOrPut(key) { LyricQuery(boundedTitle, boundedArtist, duration) }
        }
        fun addTitle(raw: String) {
            val title = cleanTitle(raw)
            if (title.isBlank() || unusable(title)) return
            val spaced = title.split(Regex("\\s+[-–—]\\s+"), limit = 2)
            val parts = if (spaced.size == 2) spaced else title.split(Regex("[-–—]"))
            val split = parts.takeIf { it.size == 2 && it.all { part -> part.isNotBlank() && !version.containsMatchIn(part.trim()) } }
                ?.map(String::trim)
            when {
                split != null && artist.isNotBlank() && LyricMatch.artistsMatch(artist, split[0]) ->
                    add(split[1], artist)
                split != null && artist.isNotBlank() && LyricMatch.artistsMatch(artist, split[1]) ->
                    add(split[0], artist)
                artist.isNotBlank() -> add(title, artist)
                else -> {
                    if (split != null && split.none(::unusable)) {
                        // Local files use both "title - artist" and "artist - title".
                        add(split[0], split[1])
                        add(split[1], split[0])
                    }
                    add(title, "")
                }
            }
        }
        addTitle(query.title)
        if (fileName.isNotBlank()) addTitle(fileName.substringAfterLast('/').substringAfterLast('\\'))
        return results.values.toList()
    }

    private fun plain(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .filterNot(Char::isISOControl).replace(Regex("\\s+"), " ").trim()

    private fun cleanTitle(value: String): String {
        val stem = audioExtension.replace(plain(value).take(320), "")
        val unnumbered = trackPrefix.replace(discPrefix.replace(stem, ""), "")
        return quality.replace(unnumbered, " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun unusable(value: String): Boolean =
        LyricMatch.normalize(value) in unknown || promotion.containsMatchIn(value)
}

object LyricMatch {
    private val highlight = Regex("</?(?:em|b|strong)>", RegexOption.IGNORE_CASE)
    private val artistSeparator = Regex("[,，、/&;；_＋+]")

    fun normalize(value: String): String =
        Normalizer.normalize(highlight.replace(value, "").replace("&amp;", "&"), Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)

    fun artistsMatch(wanted: String, candidate: String): Boolean {
        val artist = normalize(wanted)
        if (artist.isBlank()) return false
        if (artist == normalize(candidate)) return true
        val wantedArtists = wanted.split(artistSeparator).map(::normalize).filter(String::isNotBlank).toSet()
        val candidates = candidate.split(artistSeparator).map(::normalize).filter(String::isNotBlank).toSet()
        return wantedArtists.isNotEmpty() && candidates.containsAll(wantedArtists)
    }

    fun artistKeywords(artist: String): String = artistSeparator.replace(artist, " ").replace(Regex("\\s+"), " ").trim()

    fun score(query: LyricQuery, title: String, artist: String, durationMs: Long): Int? {
        val wanted = normalize(query.title)
        if (wanted.isBlank() || wanted != normalize(title)) return null
        val knownDuration = query.durationMs > 0 && durationMs > 0
        val difference = if (knownDuration) kotlin.math.abs(query.durationMs - durationMs) else Long.MAX_VALUE
        if (knownDuration && difference > 8_000) return null
        val singer = normalize(query.artist)
        if (singer.isNotBlank() && !artistsMatch(query.artist, artist)) return null
        if (singer.isBlank() && (!knownDuration || difference > 3_000)) return null
        return 100 + (if (singer.isNotBlank()) 50 else 0) + (if (difference <= 2_000) 20 else 0)
    }
}

enum class LyricOrigin { LOCAL_FILE, EMBEDDED, KUGOU, NETEASE }
enum class LyricIssue { LOCAL_UNREADABLE, LOOKUP_FAILED, NO_MATCH, FOLDER_ACCESS_NEEDED }

data class LyricResult(
    val lyrics: Lyrics? = null,
    val origin: LyricOrigin? = null,
    val cached: Boolean = false,
    val issue: LyricIssue? = null,
)

data class LyricState(val trackId: String? = null, val loading: Boolean = false, val result: LyricResult? = null)
