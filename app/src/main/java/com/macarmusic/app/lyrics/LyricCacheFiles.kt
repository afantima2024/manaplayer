package com.macarmusic.app.lyrics

import android.util.AtomicFile
import com.macarmusic.app.data.AtomicTextStore
import com.macarmusic.app.model.LyricCacheExpiry
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

@Serializable
data class CachedLyric(
    val text: String,
    val origin: LyricOrigin,
    val savedAtMs: Long = 0,
    val offsetMs: Long = 0,
    val selected: Boolean = false,
    val history: LyricSearchHistory = LyricSearchHistory(),
    val name: String = "",
    val sourceUri: String? = null,
    val versions: List<LyricVersion> = emptyList(),
    val bestVersionId: String? = null,
    val offlineSelected: Boolean = false,
    val automatic: Boolean = true,
) {
    fun result(cached: Boolean = false, issue: LyricIssue? = null) =
        LyricResult(LyricsParser.parse(text, offsetMs), origin, cached, issue, this)

    fun version() = LyricVersion(text, origin, savedAtMs, offsetMs, name, sourceUri)

    fun preferred(): CachedLyric {
        val best = bestVersionId ?: return this
        if (version().id == best) return this
        val version = versions.firstOrNull { it.id == best }
            ?: throw IOException("The preferred lyric version is missing")
        return version.document().copy(selected = true, history = history, offlineSelected = true,
            bestVersionId = best, versions = listOf(this.version()) + versions.filter { it.id != best })
    }
}

data class LyricCacheSize(val files: Long = 0, val bytes: Long = 0)

internal object LyricCacheFiles {
    const val MAX_RECORD_BYTES = 2 * LyricsParser.MAX_BYTES
    const val INTERNAL_BYTES = 4L * 1024 * 1024
    const val INTERNAL_FILES = 100
    private val json = Json { ignoreUnknownKeys = true }
    private val keyPattern = Regex("[0-9a-f]{64}")
    private val localPattern = Regex("([0-9a-f]{64})\\.json(?:\\.bak|\\.new)?")
    private val portablePattern = Regex("([0-9a-f]{64})-([1-9][0-9]{0,18})\\.json")

    fun requireKey(key: String) { require(keyPattern.matches(key)) }
    fun localKey(name: String): String? = localPattern.matchEntire(name)?.groupValues?.get(1)
    fun portableKey(name: String): Pair<String, Long>? {
        val match = portablePattern.matchEntire(name) ?: return null
        val revision = match.groupValues[2].toLongOrNull() ?: return null
        return match.groupValues[1] to revision
    }

    fun encodedSize(value: CachedLyric): Int = json.encodeToString(value).toByteArray(Charsets.UTF_8).size

    fun encode(value: CachedLyric): ByteArray {
        validate(value)
        return json.encodeToString(value).toByteArray(Charsets.UTF_8).also {
            if (it.size > MAX_RECORD_BYTES) throw IOException("Lyric cache record is too large")
        }
    }

    fun decode(bytes: ByteArray): CachedLyric {
        if (bytes.size > MAX_RECORD_BYTES) throw IOException("Lyric cache record is too large")
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        return try {
            json.decodeFromString<CachedLyric>(text).also(::validate)
        } catch (error: SerializationException) {
            throw IOException("Invalid lyric cache record", error)
        }
    }

    fun read(input: InputStream, limit: Int = MAX_RECORD_BYTES): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > limit) throw IOException("Lyric cache record is too large")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun validate(value: CachedLyric) {
        if (value.savedAtMs < 0 ||
            (!value.selected && !value.offlineSelected && value.origin !in OnlineLyricsClient.PROVIDERS) ||
            value.offsetMs !in -LyricTiming.MAX_OFFSET_MS..LyricTiming.MAX_OFFSET_MS ||
            LyricsParser.parse(value.text) == null) throw IOException("Invalid lyric cache contents")
        value.history.validate()
        if (value.name.length > 512 || (value.sourceUri?.length ?: 0) > 8192 ||
            value.versions.size >= LyricVersions.MAX_VERSIONS) throw IOException("Invalid lyric version metadata")
        val ids = hashSetOf(value.version().id)
        for (version in value.versions) {
            if (!ids.add(version.id) || version.text.isBlank() || version.text.length > LyricsParser.MAX_BYTES ||
                version.text.toByteArray(Charsets.UTF_8).size > LyricsParser.MAX_BYTES ||
                version.savedAtMs < 0 || version.offsetMs !in -LyricTiming.MAX_OFFSET_MS..LyricTiming.MAX_OFFSET_MS ||
                version.name.length > 512 || (version.sourceUri?.length ?: 0) > 8192 ||
                (version.id == value.bestVersionId && LyricsParser.parse(version.text) == null)) {
                throw IOException("Invalid stored lyric version")
            }
        }
        if (value.bestVersionId != null && value.bestVersionId !in ids) throw IOException("Missing preferred lyrics")
    }
}

internal interface LyricCacheStore {
    fun read(key: String): CachedLyric?
    fun write(key: String, value: CachedLyric)
    fun clear(): LyricCacheSize
    fun size(): LyricCacheSize
    fun pruneExpired(checkActive: () -> Unit = {})
    fun invalidate() = Unit
}

internal class InternalLyricCache(
    private val directory: File,
    private val nowMs: () -> Long,
    private val expiry: () -> LyricCacheExpiry = { LyricCacheExpiry.THIRTY_DAYS },
) : LyricCacheStore {
    val path: String get() = directory.absolutePath

    private fun files(): List<File> {
        if (!directory.exists()) return emptyList()
        return directory.listFiles()?.filter { it.isFile && LyricCacheFiles.localKey(it.name) != null }
            ?: throw IOException("Cannot list internal lyric cache")
    }

    fun keys(): Set<String> = files().mapNotNull { LyricCacheFiles.localKey(it.name) }.toSet()

    fun stored(key: String): CachedLyric? {
        LyricCacheFiles.requireKey(key)
        val file = File(directory, "$key.json")
        val backup = File(file.path + ".bak")
        val modified = (if (backup.exists()) backup else file).lastModified().coerceAtLeast(0)
        if (!file.exists() && !backup.exists()) return null
        val bytes = AtomicFile(file).openRead().use { LyricCacheFiles.read(it) }
        return LyricCacheFiles.decode(bytes).let {
            if (it.savedAtMs == 0L) it.copy(savedAtMs = modified) else it
        }
    }

    override fun read(key: String): CachedLyric? {
        val value = stored(key) ?: return null
        val remaining = LyricVersions.expire(value, expiry(), nowMs())
        if (remaining == null) {
            remove(key)
            return null
        }
        if (remaining !== value) write(key, remaining)
        return remaining
    }

    override fun write(key: String, value: CachedLyric) {
        LyricCacheFiles.requireKey(key)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create internal lyric cache")
        AtomicTextStore(File(directory, "$key.json")).write(LyricCacheFiles.encode(value).toString(Charsets.UTF_8))
        val entries = files().groupBy { checkNotNull(LyricCacheFiles.localKey(it.name)) }.entries
            .sortedBy { it.value.maxOf(File::lastModified) }
        var count = entries.size
        var bytes = entries.sumOf { it.value.sumOf(File::length) }
        for (entry in entries) {
            if (count <= LyricCacheFiles.INTERNAL_FILES && bytes <= LyricCacheFiles.INTERNAL_BYTES) break
            val length = entry.value.sumOf(File::length)
            remove(entry.key)
            count--
            bytes -= length
        }
    }

    fun remove(key: String) {
        LyricCacheFiles.requireKey(key)
        for (suffix in listOf(".json", ".json.bak", ".json.new")) {
            val file = File(directory, key + suffix)
            if (file.exists() && !file.delete()) throw IOException("Cannot remove an internal lyric cache record")
        }
    }

    override fun size(): LyricCacheSize {
        val files = files()
        return LyricCacheSize(files.mapNotNull { LyricCacheFiles.localKey(it.name) }.distinct().size.toLong(),
            files.sumOf(File::length))
    }

    override fun pruneExpired(checkActive: () -> Unit) {
        if (expiry() == LyricCacheExpiry.NEVER) return
        for (key in keys()) {
            checkActive()
            read(key)
        }
    }

    override fun clear(): LyricCacheSize {
        val size = size()
        keys().forEach(::remove)
        return size
    }
}
