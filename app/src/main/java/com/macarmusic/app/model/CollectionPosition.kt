package com.macarmusic.app.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class CollectionKind { FOLDER, PLAYLIST }

@Serializable
data class PlaybackCollection(
    val kind: CollectionKind,
    val id: String,
    val relativePath: String = "",
) {
    init {
        require(id.isNotBlank() && id.length <= 8192)
        require(validPath(relativePath))
        require(kind == CollectionKind.FOLDER || relativePath.isEmpty())
    }

    companion object {
        fun folder(location: BrowserLocation) = PlaybackCollection(CollectionKind.FOLDER, location.sourceUri, location.relativePath)
        fun playlist(id: String) = PlaybackCollection(CollectionKind.PLAYLIST, id)
        fun validPath(path: String): Boolean = path.length <= 8192 &&
            (path.isEmpty() || path.split('/').all { it.isNotEmpty() && it != "." && it != ".." && '\u0000' !in it })
    }
}

@Serializable
data class CollectionPosition(
    val collection: PlaybackCollection,
    val trackId: String,
    val positionMs: Long,
    val updatedAtMs: Long,
) {
    init { require(trackId.isNotBlank() && positionMs in 0..Long.MAX_VALUE / 1000 && updatedAtMs >= 0) }
}

@Serializable
data class CollectionHistory(val version: Int = 1, val positions: List<CollectionPosition> = emptyList())

object CollectionPositions {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun decode(text: String): List<CollectionPosition> {
        val history = json.decodeFromString<CollectionHistory>(text)
        require(history.version == 1 && history.positions.size <= 100_000)
        require(history.positions.distinctBy { it.collection }.size == history.positions.size)
        return history.positions
    }

    fun encode(positions: List<CollectionPosition>): String = json.encodeToString(CollectionHistory(positions = positions))

    fun capture(snapshot: PlaybackSnapshot, at: Long): CollectionPosition? {
        val collection = snapshot.collection ?: return null
        val track = snapshot.queue.getOrNull(snapshot.index) ?: return null
        return CollectionPosition(collection, track.id, position(snapshot.positionMs, track.durationMs), at)
    }

    fun position(position: Long, duration: Long): Long =
        if (duration > 0) position.coerceIn(0, (duration - 1).coerceAtLeast(0)) else position.coerceAtLeast(0)

    fun contains(library: Library, collection: PlaybackCollection, track: Track): Boolean =
        if (collection.kind == CollectionKind.PLAYLIST)
            library.playlists.any { it.id == collection.id && track.id in it.trackIds }
        else collection.id in track.sourceUris && (collection.relativePath.isEmpty() ||
            track.sourcePaths[collection.id]?.startsWith("${collection.relativePath}/") == true)

    fun tracks(library: Library, collection: PlaybackCollection, order: SortOrder): List<Track> =
        if (collection.kind == CollectionKind.PLAYLIST) LibraryEdits.playlistTracks(library, collection.id)
        else AudioFiles.sortAndFilter(library.tracks.filter { track ->
            val path = track.sourcePaths[collection.id]
            collection.id in track.sourceUris && (collection.relativePath.isEmpty() ||
                path?.startsWith("${collection.relativePath}/") == true)
        }.map { it.copy(uri = it.sourceUris.getValue(collection.id)) }, "", order)

    fun reconcile(library: Library, positions: List<CollectionPosition>): List<CollectionPosition> {
        val tracks = library.tracks.associateByTo(hashMapOf()) { it.id }
        library.tracks.forEach { track -> track.restoredIds.forEach { if (it !in tracks) tracks[it] = track } }
        val sources = library.sources.associateByTo(hashMapOf()) { it.uri }
        library.sources.filterNot { it.needsReconnect }.forEach { source ->
            source.restoredFrom.forEach { sources[it] = source }
        }
        val playlists = library.playlists.mapTo(hashSetOf()) { it.id }
        val resolved = linkedMapOf<PlaybackCollection, CollectionPosition>()
        for (position in positions.sortedBy { it.updatedAtMs }) {
            val track = tracks[position.trackId] ?: continue
            val collection = position.collection.let {
                if (it.kind == CollectionKind.PLAYLIST) it.takeIf { key -> key.id in playlists }
                else sources[it.id]?.let { source -> it.copy(id = source.uri) }
            } ?: continue
            if (contains(library, collection, track) ||
                (BackupEdits.unavailable(track.uri) && contains(library, position.collection, track))) {
                resolved[collection] = position.copy(collection = collection, trackId = track.id)
            }
        }
        return resolved.values.toList()
    }
}
