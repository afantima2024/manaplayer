package com.macarmusic.app.model

object PlaybackEdits {
    fun normalized(snapshot: PlaybackSnapshot): PlaybackSnapshot {
        require(snapshot.version == 1) { "Unsupported playback version" }
        if (snapshot.queue.isEmpty()) return snapshot.copy(index = 0, positionMs = 0, repeat = validRepeat(snapshot.repeat))
        val index = snapshot.index.coerceIn(snapshot.queue.indices)
        val duration = snapshot.queue[index].durationMs
        val position = snapshot.positionMs.coerceAtLeast(0).let {
            if (duration > 0) it.coerceAtMost((duration - 1).coerceAtLeast(0)) else it
        }
        return snapshot.copy(index = index, positionMs = position, repeat = validRepeat(snapshot.repeat))
    }

    fun remove(snapshot: PlaybackSnapshot, at: Int): PlaybackSnapshot {
        require(at in snapshot.queue.indices)
        val index = when {
            at < snapshot.index -> snapshot.index - 1
            at == snapshot.index -> snapshot.index.coerceAtMost(snapshot.queue.lastIndex - 1)
            else -> snapshot.index
        }
        return normalized(snapshot.copy(
            queue = snapshot.queue.filterIndexed { indexInQueue, _ -> indexInQueue != at },
            index = index,
            positionMs = if (at == snapshot.index) 0 else snapshot.positionMs,
        ))
    }

    fun validRepeat(repeat: Int): Int = if (repeat in 0..2) repeat else 0

    fun reconcile(snapshot: PlaybackSnapshot, tracks: List<Track>): PlaybackSnapshot {
        val known = tracks.associateBy { it.id }
        val original = normalized(snapshot)
        val retained = original.queue.indices.filter { original.queue[it].id in known }
        val currentIndex = retained.indexOf(original.index)
        val nextIndex = if (currentIndex >= 0) currentIndex
        else retained.indexOfFirst { it > original.index }.takeIf { it >= 0 } ?: retained.lastIndex
        return normalized(original.copy(
            queue = retained.map { known.getValue(original.queue[it].id) },
            index = nextIndex,
            positionMs = if (currentIndex >= 0) original.positionMs else 0,
        ))
    }
}
