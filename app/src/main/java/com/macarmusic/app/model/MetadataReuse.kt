package com.macarmusic.app.model

object MetadataReuse {
    fun stamp(entry: DocumentEntry): FileStamp? {
        val size = entry.sizeBytes?.takeIf { it > 0 } ?: return null
        val modified = entry.modifiedMs?.takeIf { it > 0 } ?: return null
        return FileStamp(size, modified)
    }

    // The caller looks up the candidate by canonical document identity, not by filename.
    fun unchanged(track: Track?, entry: DocumentEntry): Boolean =
        track != null && !track.missing && !entry.isDirectory && !entry.isVirtual &&
            track.fileName == entry.name && track.mimeType == entry.mimeType &&
            track.metadataStamp != null && track.metadataStamp == stamp(entry)
}
