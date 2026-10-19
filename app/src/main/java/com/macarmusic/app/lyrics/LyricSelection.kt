package com.macarmusic.app.lyrics

import kotlinx.serialization.Serializable
import java.io.IOException
import java.security.MessageDigest

@Serializable
data class LyricCandidateId(val route: LyricSearchRoute, val id: String) {
    fun valid() = id.isNotBlank() && id.length <= 160 && id.none(Char::isISOControl)
}

@Serializable
data class LyricSearchHistory(
    val candidates: List<LyricCandidateId> = emptyList(),
    val contents: List<String> = emptyList(),
) {
    fun validate() {
        if (candidates.size > MAX_CHOICES || contents.size > MAX_CHOICES ||
            candidates.any { !it.valid() } || contents.any { !HASH.matches(it) }) {
            throw IOException("Invalid lyric selection history")
        }
    }

    fun including(lyrics: Lyrics): LyricSearchHistory {
        val hash = fingerprint(lyrics)
        return if (hash in contents) this else copy(contents = contents + hash).also { it.validate() }
    }

    companion object {
        const val MAX_CHOICES = 100
        private val HASH = Regex("[0-9a-f]{64}")

        fun fingerprint(lyrics: Lyrics): String {
            val digest = MessageDigest.getInstance("SHA-256")
            for (line in lyrics.lines) {
                digest.update((line.timeMs?.toString().orEmpty() + "\u0000" + line.text.trim() + "\u0000")
                    .toByteArray(Charsets.UTF_8))
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

data class LyricAlternatives(val from: LyricOrigin?, val history: LyricSearchHistory)

object LyricTiming {
    const val MAX_OFFSET_MS = 600_000L

    fun requireOffset(offsetMs: Long) {
        require(offsetMs in -MAX_OFFSET_MS..MAX_OFFSET_MS) { "Lyric timing offset must be within ten minutes" }
    }
}
