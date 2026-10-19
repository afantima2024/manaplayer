package com.macarmusic.app.lyrics

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.macarmusic.app.model.LyricCacheExpiry
import java.io.IOException
import java.security.MessageDigest

@Serializable
data class LyricVersion(
    val text: String,
    val origin: LyricOrigin,
    val savedAtMs: Long = 0,
    val offsetMs: Long = 0,
    val name: String = "",
    val sourceUri: String? = null,
) {
    @Transient
    val id: String = MessageDigest.getInstance("SHA-256")
        .digest((origin.name + "\u0000" + text).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun document() = CachedLyric(text, origin, savedAtMs, offsetMs,
        name = name, sourceUri = sourceUri)
}

data class LyricChoice(
    val key: String,
    val name: String,
    val origin: LyricOrigin,
    val version: LyricVersion? = null,
    val uri: String? = null,
    val cached: Boolean = false,
    val preview: String = "",
)

data class LocalLyricChoices(
    val choices: List<LyricChoice>,
    val issue: LyricIssue? = null,
)

data class LyricChoices(
    val trackId: String,
    val choices: List<LyricChoice> = emptyList(),
    val currentKey: String? = null,
    val bestKey: String? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val issue: LyricIssue? = null,
    val generation: Long = 0,
)

data class LyricRemoval(val result: LyricResult, val choices: LyricChoices)

class LyricRemovalFailure(val removal: LyricRemoval, cause: Exception) :
    IOException("Could not completely remove the saved lyric copy", cause)

internal object LyricVersions {
    const val MAX_VERSIONS = LyricSearchHistory.MAX_CHOICES

    fun merge(
        value: CachedLyric,
        previous: CachedLyric?,
        best: String? = previous?.bestVersionId,
        preserveVersionId: String? = null,
    ): CachedLyric {
        val active = value.version()
        val updates = value.versions.distinctBy { it.id }.associateBy { it.id }
        val versions = (listOfNotNull(previous?.version()) + previous?.versions.orEmpty() + value.versions)
            .distinctBy { it.id }.filter { it.id != active.id }
            .map { updates[it.id] ?: it }.toMutableList()
        val history = LyricSearchHistory(
            (previous?.history?.candidates.orEmpty() + value.history.candidates).distinct().takeLast(MAX_VERSIONS),
            (previous?.history?.contents.orEmpty() + value.history.contents).distinct().takeLast(MAX_VERSIONS),
        )
        var result = value.copy(versions = versions.toList(), bestVersionId = best, history = history)
        while (versions.size + 1 > MAX_VERSIONS || LyricCacheFiles.encodedSize(result) > LyricCacheFiles.MAX_RECORD_BYTES) {
            val oldest = versions.withIndex().filter { it.value.id != best && it.value.id != preserveVersionId }
                .minByOrNull { it.value.savedAtMs }
                ?: throw IOException("The current, default and preferred lyrics exceed the cache record limit")
            versions.removeAt(oldest.index)
            result = result.copy(versions = versions.toList())
        }
        return result
    }

    fun remove(value: CachedLyric, versionId: String): CachedLyric? {
        val all = listOf(value.version()) + value.versions
        if (all.none { it.id == versionId }) throw IOException("This saved lyric version is no longer available")
        val kept = all.filterNot { it.id == versionId }
        if (kept.isEmpty()) return null
        val best = value.bestVersionId?.takeUnless { it == versionId }
        if (value.version().id != versionId) {
            return value.copy(versions = kept.drop(1), bestVersionId = best)
        }
        val primary = kept.firstOrNull { it.id == best } ?: kept.first()
        return primary.document().copy(
            history = value.history, versions = kept.filterNot { it.id == primary.id }, bestVersionId = best,
            offlineSelected = true, automatic = value.automatic,
        )
    }

    fun expire(value: CachedLyric, expiry: LyricCacheExpiry, nowMs: Long): CachedLyric? {
        if (expiry == LyricCacheExpiry.NEVER) return value
        val activeExpired = expiry.hasExpired(value.savedAtMs, nowMs)
        val kept = value.versions.filterNot { expiry.hasExpired(it.savedAtMs, nowMs) }
        if (!activeExpired && kept.size == value.versions.size) return value
        val active = if (activeExpired) kept.maxByOrNull { it.savedAtMs } ?: return null else value.version()
        val best = value.bestVersionId?.takeIf { id -> active.id == id || kept.any { it.id == id } }
        return active.document().copy(
            selected = !activeExpired && value.selected,
            offlineSelected = value.offlineSelected || active.origin == LyricOrigin.LOCAL_FILE || active.origin == LyricOrigin.EMBEDDED,
            history = value.history, bestVersionId = best, versions = kept.filterNot { it.id == active.id },
            automatic = value.automatic && (!activeExpired || best != null),
        )
    }
}
