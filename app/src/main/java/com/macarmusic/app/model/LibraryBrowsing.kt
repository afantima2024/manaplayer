package com.macarmusic.app.model

import java.util.Locale

enum class LibraryMode { SONGS, ARTISTS, ALBUMS }

data class LibraryLocation(
    val mode: LibraryMode = LibraryMode.SONGS,
    val group: String? = null,
) : java.io.Serializable

data class LibraryGroup(val key: String, val name: String, val artist: String, val count: Int)

data class LibraryPage(
    val location: LibraryLocation = LibraryLocation(),
    val tracks: List<Track> = emptyList(),
    val groups: List<LibraryGroup> = emptyList(),
    val selectedGroup: LibraryGroup? = null,
) {
    val showingGroups get() = location.mode != LibraryMode.SONGS && location.group == null
}

object LibraryBrowsing {
    fun page(library: Library, query: String, favorites: Boolean, order: SortOrder, location: LibraryLocation): LibraryPage {
        val filtered = AudioFiles.sortAndFilter(library.tracks, query, order, library.favorites.takeIf { favorites })
        if (location.mode == LibraryMode.SONGS) return LibraryPage(location, filtered)
        if (location.group != null) {
            val selected = library.tracks.firstOrNull { key(it, location.mode) == location.group }
            val tracks = filtered.filter { key(it, location.mode) == location.group }
            return LibraryPage(location, tracks, selectedGroup = selected?.let { group(it, location.mode, tracks.size) })
        }
        val groups = linkedMapOf<String, LibraryGroup>()
        for (track in filtered) {
            val key = key(track, location.mode)
            val previous = groups[key]
            groups[key] = previous?.copy(count = previous.count + 1) ?: group(track, location.mode, 1)
        }
        return LibraryPage(location, filtered, groups.values.sortedWith(
            compareBy<LibraryGroup> { normalize(it.name) }.thenBy { normalize(it.artist) }.thenBy { it.key },
        ))
    }

    private fun normalize(value: String) = value.trim().lowercase(Locale.ROOT)

    fun key(track: Track, mode: LibraryMode): String {
        val artist = normalize(track.artist)
        return when (mode) {
            LibraryMode.ARTISTS -> artist
            LibraryMode.ALBUMS -> "${artist.length}:$artist${normalize(track.album)}"
            LibraryMode.SONGS -> track.id
        }
    }

    private fun group(track: Track, mode: LibraryMode, count: Int) = LibraryGroup(
        key(track, mode), if (mode == LibraryMode.ARTISTS) track.artist.trim() else track.album.trim(),
        if (mode == LibraryMode.ALBUMS) track.artist.trim() else "", count,
    )
}
