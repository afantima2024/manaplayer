package com.macarmusic.app.model

data class IndexedTags(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    val track: Long = 0,
    val year: String = "",
    val genre: String = "",
) {
    fun toTrack(entry: DocumentEntry): Track = Track(
        id = entry.uri,
        uri = entry.uri,
        fileName = entry.name,
        title = clean(title).ifBlank { AudioFiles.titleFromFileName(entry.name) },
        artist = clean(artist),
        album = clean(album),
        durationMs = durationMs.coerceAtLeast(0),
        // MediaStore encodes disc * 1000 + track, unlike the retriever's track text.
        trackNumber = (track % 1000).takeIf { it > 0 }?.toString().orEmpty(),
        year = clean(year).takeUnless { it == "0" }.orEmpty(),
        genre = clean(genre),
        mimeType = entry.mimeType,
        metadataStamp = MetadataReuse.stamp(entry),
        metadataFromIndex = true,
    )

    private fun clean(value: String) = AudioFiles.cleanTag(value).takeUnless { it.equals("<unknown>", true) }.orEmpty()
}
