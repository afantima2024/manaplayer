package com.macarmusic.app.model

import java.util.Locale

object AudioFiles {
    private val querySeparator = Regex("\\s+")
    private class SearchWord(val text: String) {
        private val initials = text.takeIf { it.all { character -> character in 'a'..'z' } }?.let(PinyinInitials::Query)

        fun matches(value: String): Boolean =
            value.lowercase(Locale.ROOT).contains(text) || initials?.matches(value) == true
    }

    val extensions = setOf("mp3", "aac", "m4a", "m4b", "flac", "wav", "wave", "ogg", "oga", "opus")
    private val mimeTypes = setOf(
        "audio/mpeg", "audio/mp3", "audio/x-mp3", "audio/mp4", "audio/x-m4a",
        "audio/aac", "audio/x-aac", "audio/aac-adts", "audio/flac", "audio/x-flac",
        "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave",
        "audio/ogg", "application/ogg", "audio/opus",
    )

    fun accepts(name: String, mimeType: String?): Boolean {
        val mime = mimeType.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return mime in mimeTypes || extension in extensions
    }

    fun titleFromFileName(name: String): String =
        name.substringBeforeLast('.', name).ifBlank { name }

    fun cleanTag(value: String?): String =
        value.orEmpty().replace("\u0000", "").trim().take(2048)

    fun sortAndFilter(
        tracks: List<Track>,
        query: String,
        order: SortOrder,
        favorites: Set<String>? = null,
    ): List<Track> {
        val words = query.trim().lowercase(Locale.ROOT).split(querySeparator).filter(String::isNotEmpty).map(::SearchWord)
        val filtered = tracks.filter { track ->
            (favorites == null || track.id in favorites) && words.all { word ->
                word.matches(track.title) || word.matches(track.artist) || word.matches(track.album) ||
                    word.matches(track.fileName) || word.matches(track.genre)
            }
        }
        val byTitle = compareBy<Track> { it.title.lowercase(Locale.ROOT) }.thenBy { it.id }
        return when (order) {
            SortOrder.TITLE -> filtered.sortedWith(byTitle)
            SortOrder.ARTIST -> filtered.sortedWith(
                compareBy<Track> { it.artist.lowercase(Locale.ROOT) }.then(byTitle),
            )
            SortOrder.ALBUM -> filtered.sortedWith(
                compareBy<Track> { it.album.lowercase(Locale.ROOT) }
                    .thenBy { it.trackNumber.substringBefore('/').toIntOrNull() ?: Int.MAX_VALUE }
                    .then(byTitle),
            )
            SortOrder.FILE_NAME -> filtered.sortedWith(
                compareBy<Track> { it.fileName.lowercase(Locale.ROOT) }.thenBy { it.id },
            )
        }
    }
}
