package com.macarmusic.app.model

import com.macarmusic.app.lyrics.Lyrics

data class WidgetPlayback(
    val track: Track? = null,
    val playing: Boolean = false,
    val advancing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val previous: Boolean = false,
    val next: Boolean = false,
    val muted: Boolean = false,
    val failed: Boolean = false,
) {
    companion object {
        fun restored(snapshot: PlaybackSnapshot, muted: Boolean): WidgetPlayback {
            val saved = PlaybackEdits.normalized(snapshot)
            val track = saved.queue.getOrNull(saved.index)
            return WidgetPlayback(
                track = track,
                positionMs = saved.positionMs,
                durationMs = track?.durationMs?.coerceAtLeast(0) ?: 0,
                previous = track != null,
                next = track != null && (saved.index < saved.queue.lastIndex || saved.repeat == 2),
                muted = muted,
            )
        }
    }
}

object WidgetPresentation {
    const val PROGRESS_MAX = 1_000

    fun backgroundAlpha(transparency: Int): Int {
        require(transparency in 0..100)
        return ((100 - transparency) * 255 + 50) / 100
    }

    fun progress(positionMs: Long, durationMs: Long): Int = if (durationMs <= 0) 0
        else (positionMs.coerceIn(0, durationMs).toDouble() / durationMs * PROGRESS_MAX).toInt()

    fun lyric(lyrics: Lyrics?, positionMs: Long): String? {
        if (lyrics == null) return null
        var index = if (lyrics.timed) lyrics.activeLine(positionMs).coerceAtLeast(0) else 0
        val lines = ArrayList<String>(2)
        while (index < lyrics.lines.size && lines.size < 2) {
            lyrics.lines[index++].text.lineSequence().filter(String::isNotBlank).take(2 - lines.size)
                .forEach { lines.add(it) }
        }
        val limit = if (lines.size == 2) 149 else 300
        return lines.joinToString("\n") { it.take(limit) }.takeIf(String::isNotBlank)
    }

    fun needsTicks(hasProgress: Boolean, advancing: Boolean, interactive: Boolean): Boolean =
        hasProgress && advancing && interactive
}
