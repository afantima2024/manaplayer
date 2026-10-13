package com.macarmusic.app.ui

import com.macarmusic.app.model.PlaybackTimeMode
import java.util.Locale

object TimeText {
    fun progress(positionMs: Long, durationMs: Long?, mode: PlaybackTimeMode, unknown: String): String {
        return "${position(positionMs, durationMs, mode, unknown)} / ${durationMs?.takeIf { it > 0 }?.let(::format) ?: unknown}"
    }

    fun position(positionMs: Long, durationMs: Long?, mode: PlaybackTimeMode, unknown: String): String {
        val duration = durationMs?.takeIf { it > 0 }
        val position = positionMs.coerceAtLeast(0).let { if (duration == null) it else it.coerceAtMost(duration) }
        return if (mode == PlaybackTimeMode.REMAINING) {
            if (duration == null) unknown else "-${format(duration - position)}"
        } else format(position)
    }

    fun format(milliseconds: Long): String {
        val seconds = milliseconds.coerceAtLeast(0) / 1000
        return if (seconds >= 3600) String.format(
            Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60,
        ) else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }
}
