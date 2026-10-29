package com.macarmusic.app.playback

import androidx.media3.common.Player
import com.macarmusic.app.model.Track

internal fun Player.reconcileLibraryQueue(previous: Map<String, Track>, known: Map<String, Track>) {
    if (mediaItemCount == 0) return
    val original = List(mediaItemCount, ::getMediaItemAt)
    val updated = original.map { item ->
        val track = known[item.mediaId]
        when {
            track == null && item.mediaId in previous -> null
            track != null && previous[item.mediaId] != track ->
                track.toMediaItem().let { if (it == item) item else it }
            else -> item
        }
    }
    if (original.indices.all { original[it] === updated[it] }) return
    val current = currentMediaItemIndex.coerceIn(original.indices)
    if (updated[current] == null) {
        pause()
        val retained = updated.filterNotNull()
        if (retained.isEmpty()) clearMediaItems()
        else {
            val next = (0 until current).count { updated[it] != null }.coerceAtMost(retained.lastIndex)
            setMediaItems(retained, next, 0)
        }
        return
    }

    fun replaceChangedRange(from: Int, until: Int) {
        var first = from
        var end = until
        while (first < end && original[first] === updated[first]) first++
        while (end > first && original[end - 1] === updated[end - 1]) end--
        if (first < end) replaceMediaItems(first, end, updated.subList(first, end).filterNotNull())
    }

    // Keep the current media source/buffer alive; batch each side instead of rebuilding N timelines.
    replaceChangedRange(current + 1, original.size)
    if (updated[current] !== original[current]) replaceMediaItem(current, checkNotNull(updated[current]))
    replaceChangedRange(0, current)
}
