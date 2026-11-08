package com.macarmusic.app.playback

import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.macarmusic.app.radio.RadioProgram
import com.macarmusic.app.radio.RadioNavigation
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Futures

@androidx.annotation.OptIn(UnstableApi::class)
internal class RadioSessionPlayer(player: Player, private val eligibleStations: (() -> List<String>)? = null,
    private val onRadioCommand: () -> Unit) : ForwardingSimpleBasePlayer(player) {
    private val isRadio get() = RadioProgram.isRadio(player.currentMediaItem?.mediaId)
    private var stations = emptyList<MediaItem>()
    private var stationData = emptyList<MediaItemData>()
    private var stationIndex = 0
    private var navigationIds = emptyList<String>()

    fun setRadioQueue(items: List<MediaItem>, index: Int, eligibleIds: List<String> = items.map { it.mediaId }) {
        require(items.isNotEmpty() && items.all { RadioProgram.isRadio(it.mediaId) })
        stations = items.toList()
        navigationIds = eligibleIds
        stationData = stations.map { MediaItemData.Builder(it.mediaId).setMediaItem(it).setIsDynamic(true).build() }
        stationIndex = index.coerceIn(stations.indices)
        // ExoPlayer otherwise preloads the following station when a progressive stream ends.
        // Keep only the selected source in ExoPlayer, and expose navigation through this timeline.
        val selected = stations[stationIndex]
        if (!player.isActiveSource(selected.mediaId, selected.localConfiguration?.uri?.toString()))
            player.setMediaItem(selected)
        invalidateState()
    }

    fun clearRadioQueue() {
        if (stations.isEmpty()) return
        stations = emptyList()
        stationData = emptyList()
        stationIndex = 0
        navigationIds = emptyList()
        invalidateState()
    }

    fun updateRadioCatalog(items: List<MediaItem>, updateCurrent: Boolean = false,
        eligibleIds: List<String> = items.map { it.mediaId }) {
        var current = player.currentMediaItem?.takeIf { RadioProgram.isRadio(it.mediaId) } ?: return
        val index = items.indexOfFirst { it.mediaId == current.mediaId }
        if (updateCurrent) {
            val replacement = items.getOrNull(index)
            require(replacement != null && current.hasSource(replacement.mediaId, replacement.localConfiguration?.uri?.toString()))
            // Preserve the live configuration installed by PlayerMediaSources, otherwise HLS
            // treats a metadata edit as a new source and discards its connection and buffer.
            val metadata = current.buildUpon().setMediaMetadata(replacement.mediaMetadata)
                .setTag(replacement.localConfiguration?.tag).build()
            if (metadata != current) player.replaceMediaItem(player.currentMediaItemIndex, metadata)
            current = metadata
        }
        val updated = if (index < 0) listOf(current) + items else items.mapIndexed { position, item ->
            if (position == index) current else item
        }
        if (updated == stations && navigationIds == eligibleIds) return
        // Keep the selected media period; metadata-only edits do not reopen its source.
        stations = updated
        navigationIds = eligibleIds
        stationData = stations.map { MediaItemData.Builder(it.mediaId).setMediaItem(it).setIsDynamic(true).build() }
        stationIndex = index.coerceAtLeast(0)
        invalidateState()
    }

    override fun handleSetMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        if (mediaItems.isNotEmpty() && mediaItems.all { RadioProgram.isRadio(it.mediaId) }) {
            val selected = mediaItems[startIndex.coerceIn(mediaItems.indices)]
            if (!player.isActiveSource(selected.mediaId, selected.localConfiguration?.uri?.toString())) onRadioCommand()
            setRadioQueue(mediaItems, startIndex.coerceAtLeast(0), eligibleStations?.invoke() ?: mediaItems.map { it.mediaId })
            return Futures.immediateVoidFuture()
        }
        if (isRadio) onRadioCommand()
        clearRadioQueue()
        if (startPositionMs == C.TIME_UNSET && player.reselectCurrentItem(mediaItems, startIndex)) {
            invalidateState()
            return Futures.immediateVoidFuture()
        }
        return super.handleSetMediaItems(mediaItems, startIndex, startPositionMs)
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (isRadio && (!playWhenReady || !player.playWhenReady)) onRadioCommand()
        return super.handleSetPlayWhenReady(playWhenReady)
    }

    override fun handleStop(): ListenableFuture<*> {
        if (isRadio) onRadioCommand()
        return super.handleStop()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (!isRadio || stations.isEmpty()) return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        val index = when (seekCommand) {
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> adjacentIndex(-1)
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> adjacentIndex(1)
            Player.COMMAND_SEEK_TO_MEDIA_ITEM -> mediaItemIndex
            else -> stationIndex
        }
        if (index in stations.indices) {
            val selected = stations[index]
            if (index == stationIndex &&
                player.isActiveSource(selected.mediaId, selected.localConfiguration?.uri?.toString()))
                return Futures.immediateVoidFuture()
            onRadioCommand()
            val playing = player.playWhenReady
            player.stop()
            if (stations.first().mediaId !in navigationIds && selected.mediaId in navigationIds) {
                stations = stations.drop(1)
                stationData = stationData.drop(1)
            }
            stationIndex = stations.indexOfFirst { it.mediaId == selected.mediaId }
            player.setMediaItem(selected)
            if (playing) { player.prepare(); player.play() }
            invalidateState()
        }
        return Futures.immediateVoidFuture()
    }

    private fun adjacentIndex(direction: Int): Int {
        val id = RadioNavigation.adjacent(navigationIds, stations[stationIndex].mediaId, direction)
        return stations.indexOfFirst { it.mediaId == id }
    }

    override fun getState(): State {
        val state = super.getState()
        if (!isRadio || stations.isEmpty()) return state
        val canNavigate = navigationIds.any { it != stations[stationIndex].mediaId }
        val commands = state.availableCommands.buildUpon().removeAll(
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_SET_REPEAT_MODE, Player.COMMAND_SET_SHUFFLE_MODE, Player.COMMAND_CHANGE_MEDIA_ITEMS,
        ).apply {
            add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
            for (command in listOf(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
                if (canNavigate) add(command) else remove(command)
            for (command in listOf(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
                if (canNavigate) add(command) else remove(command)
        }.build()
        val timeline = stationData.toMutableList()
        state.playlist.firstOrNull()?.let { timeline[stationIndex] = it.buildUpon().setUid(stations[stationIndex].mediaId).build() }
        return state.buildUpon().setPlaylist(timeline).setCurrentMediaItemIndex(stationIndex)
            .setRepeatMode(if (canNavigate) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF)
            .setAvailableCommands(commands).build()
    }
}
