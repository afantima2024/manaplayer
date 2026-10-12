package com.macarmusic.app.model

object LibraryEdits {
    fun selectSources(library: Library, selected: Set<String>): Library {
        require(library.sources.map { it.uri }.containsAll(selected))
        return library.copy(sources = library.sources.map { it.copy(selectedForPlayback = it.uri in selected) })
    }

    fun upsertTracks(library: Library, incoming: List<Track>): Library {
        val tracks = library.tracks.associateByTo(linkedMapOf()) { it.id }
        for (track in incoming) {
            val old = tracks[track.id]
            tracks[track.id] = if (old == null) track else track.copy(
                sourceUris = old.sourceUris + track.sourceUris,
                standaloneUri = if (old.standalonePersisted && !track.standalonePersisted)
                    old.standaloneUri else track.standaloneUri ?: old.standaloneUri,
                standalonePersisted = track.standalonePersisted || old.standalonePersisted,
            )
        }
        return library.copy(tracks = tracks.values.toList())
    }

    fun applyScan(
        library: Library,
        sourceUri: String,
        discovered: List<Track>,
        completedAt: Long,
        issue: String?,
    ): Library {
        val ids = discovered.mapTo(hashSetOf()) { it.id }
        val merged = upsertTracks(library, discovered)
        // A disconnected provider can return an empty cursor. Never erase saved references on rescan.
        return merged.copy(
            tracks = merged.tracks.map {
                if (sourceUri in it.sourceUris && it.id !in ids) it.copy(missing = true) else it
            },
            sources = merged.sources.map {
                if (it.uri == sourceUri) it.copy(
                    lastScanMs = completedAt,
                    lastScanCount = discovered.size,
                    unavailable = false,
                    lastIssue = issue,
                ) else it
            },
        )
    }

    fun removeSource(library: Library, sourceUri: String): Library {
        val remaining = library.tracks.mapNotNull { track ->
            if (sourceUri !in track.sourceUris) return@mapNotNull track
            val locations = track.sourceUris - sourceUri
            val uri = locations.values.firstOrNull() ?: track.standaloneUri ?: return@mapNotNull null
            track.copy(uri = uri, sourceUris = locations)
        }
        return prune(
            library.copy(sources = library.sources.filterNot { it.uri == sourceUri }),
            remaining,
        )
    }

    fun removeTrack(library: Library, trackId: String): Library =
        prune(library, library.tracks.filterNot { it.id == trackId })

    private fun prune(library: Library, remaining: List<Track>): Library {
        val ids = remaining.mapTo(hashSetOf()) { it.id }
        return library.copy(
            tracks = remaining,
            favorites = library.favorites.intersect(ids),
            playlists = library.playlists.map { it.copy(trackIds = it.trackIds.filter(ids::contains)) },
        )
    }

    fun toggleFavorite(library: Library, trackId: String): Library {
        require(library.tracks.any { it.id == trackId })
        return library.copy(
            favorites = if (trackId in library.favorites) library.favorites - trackId
            else library.favorites + trackId,
        )
    }

    fun createPlaylist(library: Library, id: String, name: String): Library {
        val normalized = name.trim()
        require(normalized.isNotEmpty() && normalized.length <= 80)
        require(library.playlists.none { it.name.equals(normalized, ignoreCase = true) || it.id == id })
        return library.copy(playlists = library.playlists + LocalPlaylist(id, normalized))
    }

    fun addToPlaylist(library: Library, playlistId: String, trackIds: List<String>): Library {
        require(library.playlists.any { it.id == playlistId })
        val known = library.tracks.mapTo(hashSetOf()) { it.id }
        return library.copy(playlists = library.playlists.map {
            if (it.id == playlistId) it.copy(
                trackIds = (it.trackIds + trackIds.filter(known::contains)).distinct(),
            ) else it
        })
    }

    fun removeFromPlaylist(library: Library, playlistId: String, trackId: String): Library =
        library.copy(playlists = library.playlists.map {
            if (it.id == playlistId) it.copy(trackIds = it.trackIds - trackId) else it
        })

    fun playlistTracks(library: Library, playlistId: String): List<Track> {
        val tracks = library.tracks.associateBy { it.id }
        return library.playlists.firstOrNull { it.id == playlistId }?.trackIds
            ?.mapNotNull(tracks::get).orEmpty()
    }
}
