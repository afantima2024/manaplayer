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
    val lastTimestampMs: Long? get() = lines.lastOrNull()?.timeMs

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

    fun parse(text: String, additionalOffsetMs: Long = 0): Lyrics? {
        LyricTiming.requireOffset(additionalOffsetMs)
        if (text.length > MAX_BYTES || text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            throw IOException("Lyrics exceed the size limit")
        }
        if (text.contains("<html", ignoreCase = true) || text.contains("<!doctype", ignoreCase = true)) {
            throw IOException("The lyric source returned an HTML page")
        }
        val adjustment = (offset.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0) + additionalOffsetMs
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
    private val order = compareBy<DocumentEntry> { !it.name.endsWith(".lrc", ignoreCase = true) }.thenBy { it.name }
    fun accepts(name: String): Boolean = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("lrc", "txt")

    internal fun index(entries: List<DocumentEntry>): Map<String, List<DocumentEntry>> =
        entries.filter { !it.isDirectory && !it.isVirtual && accepts(it.name) }
            .groupBy { normalize(it.name.substringBeforeLast('.')) }
            .mapValues { (_, files) -> files.sortedWith(order) }

    internal fun matching(track: Track, index: Map<String, List<DocumentEntry>>): List<DocumentEntry> =
        index[normalize(track.fileName.substringBeforeLast('.', track.fileName))].orEmpty()

    fun matching(track: Track, entries: List<DocumentEntry>, includeVersions: Boolean = false): List<DocumentEntry> {
        val stem = normalize(track.fileName.substringBeforeLast('.', track.fileName))
        val stems = if (includeVersions) (listOf(stem) + LyricQueries.candidates(LyricQuery.from(track), track.fileName)
            .map { normalize(it.title) }).filter(String::isNotBlank).distinct() else listOf(stem)
        return entries.filter {
            val name = normalize(it.name.substringBeforeLast('.'))
            !it.isDirectory && !it.isVirtual && accepts(it.name) &&
                stems.any { base -> name == base || (includeVersions && name.startsWith(base) &&
                    name.substring(base.length).trimStart().firstOrNull() in listOf('.', '-', '_', '(', '[', '（', '【')) }
        }.sortedWith(order)
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
    private val copySuffix = Regex("(?:\\s+[-–—]\\s*(?:copy|副本)(?:\\s*\\(\\d+\\))?)+$", RegexOption.IGNORE_CASE)
    private val searchParentheses = Regex("[\\(（][^\\)）]*[\\)）]")
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
        fun splitTitle(title: String): List<String>? {
            val spaced = title.split(Regex("\\s+[-–—]\\s+"), limit = 2)
            val parts = if (spaced.size == 2) spaced else title.split(Regex("[-–—]"))
            return parts.takeIf { it.size == 2 && it.all { part -> part.isNotBlank() && !version.containsMatchIn(part.trim()) } }
                ?.map(String::trim)
        }
        fun addTitle(raw: String, taggedArtist: String = artist) {
            val title = cleanTitle(raw)
            if (title.isBlank() || unusable(title)) return
            val split = splitTitle(title)
            when {
                split != null && taggedArtist.isNotBlank() && LyricMatch.artistsMatch(taggedArtist, split[0]) ->
                    add(split[1], taggedArtist)
                split != null && taggedArtist.isNotBlank() && LyricMatch.artistsMatch(taggedArtist, split[1]) ->
                    add(split[0], taggedArtist)
                taggedArtist.isNotBlank() -> add(title, taggedArtist)
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
        if (fileName.isNotBlank()) {
            val filename = cleanTitle(fileName.substringAfterLast('/').substringAfterLast('\\'))
            val title = cleanTitle(query.title)
            if (title.isBlank() || unusable(title)) addTitle(filename, "")
            else {
                val split = splitTitle(filename)?.takeUnless { it.any(::unusable) }
                if (split != null) {
                    // A matching filename title can recover a bad singer tag, but unrelated filenames cannot.
                    if (LyricMatch.titleKey(title) == LyricMatch.titleKey(split[0])) add(split[0], split[1])
                    else if (LyricMatch.titleKey(title) == LyricMatch.titleKey(split[1])) add(split[1], split[0])
                }
                addTitle(filename)
            }
        }
        return results.values.toList()
    }

    private fun plain(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .filterNot(Char::isISOControl).replace(Regex("\\s+"), " ").trim()

    private fun cleanTitle(value: String): String {
        val stem = copySuffix.replace(audioExtension.replace(plain(value).take(320), ""), "")
        val unnumbered = trackPrefix.replace(discPrefix.replace(stem, ""), "")
        return quality.replace(unnumbered, " ").replace(Regex("\\s+"), " ").trim()
    }

    internal fun keyword(query: LyricQuery, cleaned: Boolean): String {
        if (!cleaned) return listOf(query.title, query.artist).filter(String::isNotBlank).joinToString(" ")
        val title = searchParentheses.replace(query.title, " ").replace(Regex("\\s+"), " ").trim()
            .ifBlank { query.title.trim() }
        val artist = query.artist.trim().substringBefore(" - ").trim()
            .takeUnless { it.lowercase(Locale.ROOT) in setOf("unknown", "unknown artist", "未知") }.orEmpty()
        return listOf(title, artist).filter(String::isNotBlank).joinToString(" ")
    }

    private fun unusable(value: String): Boolean =
        LyricMatch.normalize(value) in unknown || promotion.containsMatchIn(value)
}

object LyricMatch {
    private val searchPunctuation = Regex("""[\s\-_()（）【】\[\].,，。!！?？'"`~@#$%^&*+=/\\|<>]""")
    private val highlight = Regex("</?(?:em|b|strong)>", RegexOption.IGNORE_CASE)
    private val artistSeparator = Regex("[,，、/&;；_＋+]")
    private val annotation = Regex(
        "[（(【\\[]\\s*(?:(?:\\d{4}\\s+)?remaster(?:ed)?(?:\\s+\\d{4})?|" +
            "(?:电影|电视剧|电视|网剧)?《[^》]{1,100}》(?:主题曲|片头曲|片尾曲|插曲))\\s*[）)】\\]]",
        RegexOption.IGNORE_CASE,
    )

    fun normalize(value: String): String =
        Normalizer.normalize(highlight.replace(LyricResponse.decode(value), ""), Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)

    internal fun titleKey(value: String): String = normalize(annotation.replace(LyricResponse.decode(value), ""))

    fun artistsMatch(wanted: String, candidate: String): Boolean {
        val artist = normalize(wanted)
        if (artist.isBlank()) return false
        if (artist == normalize(candidate)) return true
        val wantedArtists = LyricResponse.decode(wanted).split(artistSeparator).map(::normalize).filter(String::isNotBlank).toSet()
        val candidates = LyricResponse.decode(candidate).split(artistSeparator).map(::normalize).filter(String::isNotBlank).toSet()
        return wantedArtists.isNotEmpty() && candidates.containsAll(wantedArtists)
    }

    fun durationMatches(trackMs: Long, candidateMs: Long): Boolean =
        trackMs <= 0 || candidateMs <= 0 || kotlin.math.abs(trackMs - candidateMs) <= durationTolerance(trackMs)

    fun timingMatches(trackMs: Long, lyrics: Lyrics): Boolean {
        if (trackMs <= 0) return true
        val end = lyrics.lastTimestampMs ?: return false
        // Last-line starts are not song lengths: allow an outro, but not a short preview of a full song.
        return end in (trackMs - minOf(trackMs / 4, 60_000))..(trackMs + durationTolerance(trackMs))
    }

    private fun durationTolerance(trackMs: Long): Long = (trackMs / 10).coerceIn(10_000, 30_000)

    fun score(query: LyricQuery, title: String, artist: String, durationMs: Long = 0, index: Int = 0): Int {
        fun similarity(first: String, second: String): Int {
            val a = searchPunctuation.replace(first.lowercase(Locale.ROOT), "").trim()
            val b = searchPunctuation.replace(second.lowercase(Locale.ROOT), "").trim()
            return when {
                a == b -> 10
                a.isNotEmpty() && b.isNotEmpty() && (a.contains(b) || b.contains(a)) -> 5
                else -> 0
            }
        }
        val difference = if (query.durationMs > 0 && durationMs > 0)
            kotlin.math.abs(query.durationMs - durationMs) else Long.MAX_VALUE
        return similarity(title, query.title) +
            (if (query.artist.isNotEmpty()) similarity(artist, query.artist) else 0) +
            (if (index == 0) 1 else 0) + when {
                difference < 3_000 -> 8
                difference < 5_000 -> 5
                difference < 10_000 -> 2
                else -> 0
            }
    }
}

enum class LyricOrigin { LOCAL_FILE, EMBEDDED, KUGOU, NETEASE, QQ, KUWO }
enum class LyricIssue { LOCAL_UNREADABLE, LOOKUP_FAILED, NO_MATCH, FOLDER_ACCESS_NEEDED, CACHE_UNAVAILABLE }

data class LyricResult(
    val lyrics: Lyrics? = null,
    val origin: LyricOrigin? = null,
    val cached: Boolean = false,
    val issue: LyricIssue? = null,
    val document: CachedLyric? = null,
) {
    val availableOffline: Boolean get() = origin == null || origin == LyricOrigin.LOCAL_FILE ||
        origin == LyricOrigin.EMBEDDED || document?.offlineSelected == true || document?.bestVersionId != null ||
        document?.downloaded == true
}

data class LyricState(
    val trackId: String? = null,
    val loading: Boolean = false,
    val result: LyricResult? = null,
    val userEdited: Boolean = false,
)
