package com.macarmusic.app.data

import com.macarmusic.app.model.Library
import com.macarmusic.app.model.PlaybackEdits
import com.macarmusic.app.model.PlaybackSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object LibraryCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(library: Library): String = json.encodeToString(library)

    fun decodeLibrary(text: String): Library {
        val library = json.decodeFromString<Library>(text)
        require(library.version == 1) { "Unsupported library version" }
        require(library.tracks.map { it.id }.distinct().size == library.tracks.size) { "Duplicate track IDs" }
        require(library.sources.map { it.uri }.distinct().size == library.sources.size) { "Duplicate source IDs" }
        require(library.playlists.map { it.id }.distinct().size == library.playlists.size) { "Duplicate playlist IDs" }
        return library
    }

    fun encode(snapshot: PlaybackSnapshot): String = json.encodeToString(snapshot)

    fun decodePlayback(text: String): PlaybackSnapshot =
        PlaybackEdits.normalized(json.decodeFromString(text))
}
