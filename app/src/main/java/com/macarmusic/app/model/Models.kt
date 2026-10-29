package com.macarmusic.app.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val uri: String,
    val fileName: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    val trackNumber: String = "",
    val year: String = "",
    val genre: String = "",
    val mimeType: String = "",
    val sourceUris: Map<String, String> = emptyMap(),
    val standaloneUri: String? = null,
    val standalonePersisted: Boolean = false,
    val missing: Boolean = false,
    val sourceParents: Map<String, String> = emptyMap(),
    val sourcePaths: Map<String, String> = emptyMap(),
    val metadataStamp: FileStamp? = null,
    val metadataFromIndex: Boolean = false,
    val restoredIds: Set<String> = emptySet(),
    val metadataPending: Boolean = false,
)

@Serializable
data class FileStamp(val sizeBytes: Long, val modifiedMs: Long) {
    init { require(sizeBytes > 0 && modifiedMs > 0) }
}

@Serializable
data class FolderSource(
    val uri: String,
    val name: String,
    val persisted: Boolean,
    val lastScanMs: Long = 0,
    val lastScanCount: Int = 0,
    val unavailable: Boolean = false,
    val lastIssue: String? = null,
    val selectedForPlayback: Boolean = true,
    val pinned: Boolean = false,
    val needsReconnect: Boolean = false,
    val restoredFrom: Set<String> = emptySet(),
)

@Serializable
data class LocalPlaylist(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList(),
    val folders: List<PlaylistFolder> = emptyList(),
    val manualTrackIds: List<String>? = null,
)

@Serializable
data class PlaylistFolder(
    val sourceUri: String,
    val documentId: String,
    val name: String,
    val trackIds: List<String> = emptyList(),
    val relativePath: String = "",
) {
    val key: Pair<String, String> get() = sourceUri to documentId.ifEmpty { "\u0000$relativePath" }
    val pathKey: Pair<String, String> get() = sourceUri to relativePath
    fun matches(location: BrowserLocation): Boolean = key == location.key ||
        (documentId.isEmpty() && pathKey == location.pathKey)
    fun location() = BrowserLocation(sourceUri, documentId, name, relativePath)
}

@Serializable
data class Library(
    val version: Int = 1,
    val sources: List<FolderSource> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val playlists: List<LocalPlaylist> = emptyList(),
)

@Serializable
data class PlaybackSnapshot(
    val version: Int = 1,
    val queue: List<Track> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = 0,
    val collection: PlaybackCollection? = null,
)

enum class SortOrder { TITLE, ARTIST, ALBUM, FILE_NAME }

data class DocumentEntry(
    val id: String,
    val uri: String,
    val name: String,
    val mimeType: String,
    val isDirectory: Boolean,
    val isVirtual: Boolean,
    val sizeBytes: Long? = null,
    val modifiedMs: Long? = null,
    val flags: Int = 0,
)

data class BrowserLocation(
    val sourceUri: String,
    val documentId: String,
    val name: String,
    val relativePath: String = "",
) {
    val key: Pair<String, String> get() = sourceUri to documentId.ifEmpty { "\u0000$relativePath" }
    val pathKey: Pair<String, String> get() = sourceUri to relativePath
}

data class BrowserState(
    val path: List<BrowserLocation> = emptyList(),
    val entries: List<DocumentEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class ScanProgress(val folderName: String, val found: Int = 0)

data class MetadataProgress(val completed: Int, val total: Int)

data class ScanResult(
    val tracks: List<Track>,
    val metadataWarnings: Int,
    val missingCount: Int,
    val reusedMetadata: Int = 0,
    val indexedMetadata: Int = 0,
)

data class LibraryScan(val sourceUri: String, val tracks: List<Track>, val fullSource: Boolean = true)

data class Notice(val id: Long, val text: String, val isError: Boolean = true)
