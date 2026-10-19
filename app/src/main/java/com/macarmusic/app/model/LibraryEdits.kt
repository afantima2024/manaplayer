package com.macarmusic.app.model

class PinnedFolderException : IllegalStateException("Unpin the folder before removing it or its playlist entries")

object LibraryEdits {
    fun selectSources(library: Library, selected: Set<String>): Library {
        require(library.sources.map { it.uri }.containsAll(selected))
        if (library.sources.any { it.pinned && it.uri !in selected }) throw PinnedFolderException()
        return library.copy(sources = library.sources.map { it.copy(selectedForPlayback = it.uri in selected) })
    }

    fun pinSources(library: Library, pinned: Set<String>): Library {
        require(library.sources.map { it.uri }.containsAll(pinned))
        return library.copy(sources = library.sources.map {
            it.copy(pinned = it.uri in pinned, selectedForPlayback = it.selectedForPlayback || it.uri in pinned)
        })
    }

    fun upsertTracks(library: Library, incoming: List<Track>): Library {
        val tracks = library.tracks.associateByTo(linkedMapOf()) { it.id }
        for (track in incoming) {
            val old = tracks[track.id]
            tracks[track.id] = if (old == null) track else track.copy(
                sourceUris = old.sourceUris + track.sourceUris,
                sourceParents = old.sourceParents + track.sourceParents,
                sourcePaths = old.sourcePaths + track.sourcePaths,
                standaloneUri = if (old.standalonePersisted && !track.standalonePersisted)
                    old.standaloneUri else track.standaloneUri ?: old.standaloneUri,
                standalonePersisted = track.standalonePersisted || old.standalonePersisted,
                restoredIds = old.restoredIds + track.restoredIds,
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
        val merged = BackupEdits.reconcileScanned(upsertTracks(library, discovered), setOf(sourceUri))
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

    fun applyScans(library: Library, scans: List<LibraryScan>, completedAt: Long,
        missingIssue: (Int) -> String,
    ): Library {
        val full = scans.filter { it.fullSource }.associateBy { it.sourceUri }
        val found = scans.flatMapTo(hashSetOf()) { it.tracks.map(Track::id) }
        val ids = full.mapValues { (_, scan) -> scan.tracks.mapTo(hashSetOf(), Track::id) }
        val missing = full.keys.associateWith { 0 }.toMutableMap()
        val merged = BackupEdits.reconcileScanned(upsertTracks(library, scans.flatMap { it.tracks }),
            scans.mapTo(hashSetOf()) { it.sourceUri })
        for (track in merged.tracks) {
            for (source in track.sourceUris.keys) {
                if (source in ids && track.id !in ids.getValue(source)) missing[source] = missing.getValue(source) + 1
            }
        }
        return merged.copy(
            tracks = merged.tracks.map { track ->
                if (track.id !in found && track.sourceUris.keys.any(full::containsKey)) track.copy(missing = true) else track
            },
            sources = merged.sources.map { source ->
                full[source.uri]?.let { scan -> source.copy(
                    lastScanMs = completedAt,
                    lastScanCount = scan.tracks.size,
                    unavailable = false,
                    lastIssue = missing.getValue(source.uri).takeIf { it > 0 }?.let(missingIssue),
                ) } ?: source
            },
        )
    }

    fun removeSource(library: Library, sourceUri: String): Library = removeSources(library, setOf(sourceUri))

    fun removeSources(library: Library, sourceUris: Set<String>): Library {
        if (sourceUris.isEmpty()) return library
        if (library.sources.any { it.uri in sourceUris && it.pinned }) throw PinnedFolderException()
        val remaining = library.tracks.mapNotNull { track ->
            if (track.sourceUris.keys.none(sourceUris::contains)) return@mapNotNull track
            val locations = track.sourceUris - sourceUris
            val uri = locations.values.firstOrNull() ?: track.standaloneUri ?: return@mapNotNull null
            track.copy(uri = uri, sourceUris = locations, sourceParents = track.sourceParents - sourceUris,
                sourcePaths = track.sourcePaths - sourceUris)
        }
        return prune(
            library.copy(sources = library.sources.filterNot { it.uri in sourceUris },
                playlists = library.playlists.map { playlist ->
                    playlist.copy(folders = playlist.folders.filterNot { it.sourceUri in sourceUris })
                }),
            remaining,
        )
    }

    fun removeTrack(library: Library, trackId: String): Library {
        requireRemovable(library, setOf(trackId))
        return prune(library, library.tracks.filterNot { it.id == trackId })
    }

    private fun prune(library: Library, remaining: List<Track>): Library {
        val ids = remaining.mapTo(hashSetOf()) { it.id }
        return library.copy(
            tracks = remaining,
            favorites = library.favorites.intersect(ids),
            playlists = library.playlists.map { playlist -> playlist.copy(
                trackIds = playlist.trackIds.filter(ids::contains),
                manualTrackIds = playlist.manualTrackIds?.filter(ids::contains),
                folders = playlist.folders.map { it.copy(trackIds = it.trackIds.filter(ids::contains)) },
            ) },
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
                manualTrackIds = ((it.manualTrackIds ?: it.trackIds) + trackIds.filter(known::contains)).distinct(),
            ) else it
        })
    }

    fun removeFromPlaylist(library: Library, playlistId: String, trackId: String): Library =
        replacePlaylistTracks(library, playlistId,
            requireNotNull(library.playlists.firstOrNull { it.id == playlistId }).trackIds - trackId)

    fun replacePlaylistTracks(library: Library, playlistId: String, trackIds: List<String>): Library {
        val playlist = requireNotNull(library.playlists.firstOrNull { it.id == playlistId })
        val known = library.tracks.mapTo(hashSetOf()) { it.id }
        require(known.containsAll(trackIds))
        val selected = trackIds.toSet()
        requireRemovable(library, playlist.trackIds.toSet() - selected)
        val folderIds = playlist.folders.flatMapTo(hashSetOf()) { it.trackIds }
        return library.copy(playlists = library.playlists.map {
            if (it.id != playlistId) it else it.copy(
                trackIds = trackIds.distinct(),
                manualTrackIds = ((it.manualTrackIds ?: it.trackIds).filter(selected::contains) +
                    trackIds.filterNot(folderIds::contains)).distinct(),
                folders = it.folders.map { folder -> folder.copy(trackIds = folder.trackIds.filter(selected::contains)) },
            )
        })
    }

    fun setPlaylistFolders(library: Library, playlistId: String, folders: List<PlaylistFolder>): Library {
        val playlist = requireNotNull(library.playlists.firstOrNull { it.id == playlistId })
        val sources = library.sources.associateBy { it.uri }
        require(folders.all { it.sourceUri in sources } && folders.distinctBy { it.key }.size == folders.size)
        val keys = folders.mapTo(hashSetOf()) { it.key }
        val resolvedPaths = folders.filter { it.documentId.isNotEmpty() }.mapTo(hashSetOf()) { it.pathKey }
        if (playlist.folders.any { it.key !in keys &&
            !(it.documentId.isEmpty() && it.pathKey in resolvedPaths) && sources[it.sourceUri]?.pinned == true }) {
            throw PinnedFolderException()
        }
        val manual = playlist.manualTrackIds ?: playlist.trackIds
        val ids = (manual + folders.flatMap { it.trackIds }).distinct()
        require(library.tracks.mapTo(hashSetOf()) { it.id }.containsAll(ids))
        requireRemovable(library, playlist.trackIds.toSet() - ids.toSet())
        val selected = ids.toHashSet()
        val ordered = playlist.trackIds.filter(selected::contains) + (ids - playlist.trackIds.toSet())
        return library.copy(playlists = library.playlists.map {
            if (it.id == playlistId) it.copy(trackIds = ordered, folders = folders, manualTrackIds = manual) else it
        })
    }

    fun renamePlaylist(library: Library, playlistId: String, name: String): Library {
        val normalized = name.trim()
        require(normalized.isNotEmpty() && normalized.length <= 80)
        require(library.playlists.any { it.id == playlistId })
        require(library.playlists.none { it.id != playlistId && it.name.equals(normalized, true) })
        return library.copy(playlists = library.playlists.map {
            if (it.id == playlistId) it.copy(name = normalized) else it
        })
    }

    fun deletePlaylist(library: Library, playlistId: String): Library {
        val playlist = requireNotNull(library.playlists.firstOrNull { it.id == playlistId })
        val pinned = library.sources.filter { it.pinned }.mapTo(hashSetOf()) { it.uri }
        if (playlist.folders.any { it.sourceUri in pinned }) throw PinnedFolderException()
        requireRemovable(library, playlist.trackIds.toSet())
        return library.copy(playlists = library.playlists.filterNot { it.id == playlistId })
    }

    fun isPinned(library: Library, track: Track): Boolean =
        library.sources.any { it.pinned && it.uri in track.sourceUris }

    private fun requireRemovable(library: Library, ids: Set<String>) {
        val pinned = library.sources.filter { it.pinned }.mapTo(hashSetOf()) { it.uri }
        if (library.tracks.any { it.id in ids && it.sourceUris.keys.any(pinned::contains) }) {
            throw PinnedFolderException()
        }
    }

    fun playlistTracks(library: Library, playlistId: String): List<Track> {
        val tracks = library.tracks.associateBy { it.id }
        return library.playlists.firstOrNull { it.id == playlistId }?.trackIds
            ?.mapNotNull(tracks::get).orEmpty()
    }
}
