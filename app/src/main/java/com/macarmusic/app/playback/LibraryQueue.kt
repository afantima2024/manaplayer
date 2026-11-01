package com.macarmusic.app.playback

import androidx.media3.common.MediaItem
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

    val next = (0 until current).count { updated[it] != null }
    replaceQueueAroundCurrent(original, updated.filterNotNull(), current, next)
}

internal fun Player.reselectCurrentItem(items: List<MediaItem>, index: Int): Boolean {
    val selected = items.getOrNull(index) ?: return false
    if (playerError != null || playbackState == Player.STATE_ENDED ||
        currentMediaItem?.hasSource(selected.mediaId, selected.localConfiguration?.uri?.toString()) != true) return false
    replaceQueueAroundCurrent(List(mediaItemCount, ::getMediaItemAt), items, currentMediaItemIndex, index)
    return true
}

private fun Player.replaceQueueAroundCurrent(
    original: List<MediaItem>, updated: List<MediaItem>, current: Int, next: Int,
) {
    fun replaceChangedRange(from: Int, until: Int, newFrom: Int, newUntil: Int) {
        var first = from
        var end = until
        var newFirst = newFrom
        var newEnd = newUntil
        while (first < end && newFirst < newEnd && original[first] == updated[newFirst]) { first++; newFirst++ }
        while (end > first && newEnd > newFirst && original[end - 1] == updated[newEnd - 1]) { end--; newEnd-- }
        if (first < end || newFirst < newEnd) replaceMediaItems(first, end, updated.subList(newFirst, newEnd))
    }

    // Keep the selected media period and buffer, including when its queue position changes.
    replaceChangedRange(current + 1, original.size, next + 1, updated.size)
    if (updated[next] != original[current]) replaceMediaItem(current, updated[next])
    replaceChangedRange(0, current, 0, next)
}
