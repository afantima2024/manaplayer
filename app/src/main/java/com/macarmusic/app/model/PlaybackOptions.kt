package com.macarmusic.app.model

import kotlinx.serialization.Serializable

enum class LoopMode(val repeatMode: Int) {
    OFF(0), SINGLE_FILE(1), FOLDER(2), SELECTED_FOLDERS(2);

    companion object {
        fun fromRepeatMode(repeatMode: Int): LoopMode = when (repeatMode) {
            1 -> SINGLE_FILE
            2 -> SELECTED_FOLDERS
            else -> OFF
        }
    }
}

enum class PlayOrder { SEQUENTIAL, RANDOM }

@Serializable
data class PlaybackOptions(
    val loop: LoopMode = LoopMode.OFF,
    val order: PlayOrder = PlayOrder.SEQUENTIAL,
)

object PlaybackScope {
    fun selectedSources(library: Library): List<FolderSource> =
        library.sources.filter { it.selectedForPlayback && !it.needsReconnect }

    fun folderFor(library: Library, track: Track?): FolderSource? {
        if (track == null) return selectedSources(library).firstOrNull()
        val candidates = library.sources.filter { it.uri in track.sourceUris }
        return candidates.firstOrNull { track.sourceUris[it.uri] == track.uri } ?: candidates.firstOrNull()
    }

    fun tracks(library: Library, loop: LoopMode, current: Track?, sort: SortOrder): List<Track> {
        val sources = when (loop) {
            LoopMode.FOLDER -> listOfNotNull(folderFor(library, current))
            LoopMode.SELECTED_FOLDERS -> selectedSources(library)
            else -> return emptyList()
        }
        val sourceIds = sources.mapTo(hashSetOf()) { it.uri }
        val tracks = library.tracks.mapNotNull { track ->
            val uri = track.sourceUris.entries.firstOrNull { it.key in sourceIds }?.value
                ?: return@mapNotNull null
            track.copy(uri = uri)
        }.distinctBy { it.id }
        return AudioFiles.sortAndFilter(tracks, "", sort)
    }
}
