package com.macarmusic.app.model

data class MetadataUpdate(val requested: Track, val result: Track)

object MetadataEdits {
    fun entry(track: Track) = DocumentEntry(track.id, track.uri, track.fileName, track.mimeType,
        isDirectory = false, isVirtual = false, sizeBytes = track.metadataStamp?.sizeBytes,
        modifiedMs = track.metadataStamp?.modifiedMs)

    fun matches(current: Track?, requested: Track): Boolean =
        current != null && current.metadataPending && !current.missing &&
            current.id == requested.id && current.uri == requested.uri &&
            current.fileName == requested.fileName && current.mimeType == requested.mimeType &&
            current.metadataStamp == requested.metadataStamp

    fun complete(library: Library, updates: List<MetadataUpdate>): Library {
        val byId = updates.associateBy { it.requested.id }
        var changed = false
        val tracks = library.tracks.map { current ->
            val update = byId[current.id]
            if (update == null || !matches(current, update.requested)) current
            else {
                changed = true
                update.result.copy(
                    id = current.id, uri = current.uri, fileName = current.fileName, mimeType = current.mimeType,
                    sourceUris = current.sourceUris, sourceParents = current.sourceParents,
                    sourcePaths = current.sourcePaths, standaloneUri = current.standaloneUri,
                    standalonePersisted = current.standalonePersisted, restoredIds = current.restoredIds,
                    metadataPending = false, missing = current.missing,
                )
            }
        }
        return if (changed) library.copy(tracks = tracks) else library
    }
}
