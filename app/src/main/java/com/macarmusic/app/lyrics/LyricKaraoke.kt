package com.macarmusic.app.lyrics

object LyricKaraoke {
    fun endMs(lyrics: Lyrics?, index: Int, durationMs: Long): Long? {
        val start = lyrics?.lines?.getOrNull(index)?.timeMs ?: return null
        val next = lyrics.lines.getOrNull(index + 1)?.timeMs
        val end = if (durationMs > 0) minOf(next ?: durationMs, durationMs) else next
        return end?.takeIf { it > start }
    }

    fun fraction(startMs: Long?, endMs: Long?, positionMs: Long): Float {
        if (startMs == null || endMs == null || startMs < 0 || endMs <= startMs) return 0f
        return ((positionMs.coerceIn(startMs, endMs) - startMs).toDouble() / (endMs - startMs)).toFloat()
    }

    fun prefixLength(text: String, fraction: Float): Int {
        require(fraction.isFinite())
        val count = (text.codePointCount(0, text.length) * fraction.coerceIn(0f, 1f)).toInt()
        return text.offsetByCodePoints(0, count)
    }
}

data class LyricPlayback(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val advancing: Boolean = false,
    val speed: Float = 1f,
    val sampledAtMs: Long = 0,
) {
    init { require(speed.isFinite() && speed > 0) }

    fun positionAt(nowMs: Long): Long = if (!advancing) positionMs else
        (positionMs.toDouble() + (nowMs - sampledAtMs).coerceAtLeast(0).toDouble() * speed).toLong()
}
