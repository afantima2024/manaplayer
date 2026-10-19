package com.macarmusic.app.model

import com.macarmusic.app.data.BackupManifest
import java.security.MessageDigest

data class BackupMerge(val library: Library, val positions: List<CollectionPosition>)

object BackupEdits {
    const val RESTORED_AUTHORITY = "com.macarmusic.app.restored"
    private const val PREFIX = "content://$RESTORED_AUTHORITY/"
    fun unavailable(uri: String) = uri.startsWith(PREFIX)

    private fun reference(origin: String, kind: String, original: String): String {
        if (unavailable(original)) return original
        val digest = MessageDigest.getInstance("SHA-256").digest("$origin\u0000$original".toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        return "$PREFIX$kind/$name"
    }

    fun merge(current: Library, backup: BackupManifest, currentOrigin: String): BackupMerge {
        val sourceMap = linkedMapOf<String, FolderSource>()
        val sources = current.sources.associateByTo(linkedMapOf()) { it.uri }
        val sourceAliases = current.sources.flatMap { source -> source.restoredFrom.map { it to source } }.toMap()
        for (source in backup.library.sources) {
            val pending = reference(backup.origin, "folder", source.uri)
            val existing = if (backup.origin == currentOrigin) sources[source.uri] else null
            val target = existing ?: sourceAliases[pending] ?: sources[pending]
            val restored = target?.copy(pinned = target.pinned || source.pinned,
                selectedForPlayback = target.selectedForPlayback || source.pinned) ?: source.copy(
                uri = pending, persisted = false, unavailable = true, lastIssue = null, needsReconnect = true, restoredFrom = emptySet())
            sources[restored.uri] = restored
            sourceMap[source.uri] = restored
        }
        val tracks = current.tracks.associateByTo(linkedMapOf()) { it.id }
        val aliases = current.tracks.flatMap { track -> track.restoredIds.map { it to track } }.groupBy({ it.first }, { it.second })
        val paths = current.tracks.flatMap { track -> track.sourcePaths.map { (source, path) -> (source to path) to track } }
            .groupBy({ it.first }, { it.second })
        val names = current.tracks.flatMap { track -> track.sourceUris.keys.map { (it to track.fileName) to track } }
            .groupBy({ it.first }, { it.second })
        val ids = linkedMapOf<String, String>()
        for (track in backup.library.tracks) {
            val pending = reference(backup.origin, "track", track.id)
            val existing = if (backup.origin == currentOrigin) tracks[track.id]?.takeIf {
                it.fileName == track.fileName && (it.uri == track.uri ||
                    track.sourcePaths.any { (source, path) -> it.sourcePaths[source] == path })
            } else null
            val candidates = track.sourceUris.keys.flatMap { source ->
                val target = sourceMap.getValue(source)
                if (target.needsReconnect) emptyList() else track.sourcePaths[source]?.let {
                    paths[target.uri to it].orEmpty()
                } ?: names[target.uri to track.fileName].orEmpty()
            }.distinctBy { it.id }
            val target = existing ?: aliases[pending]?.singleOrNull() ?: tracks[pending] ?: candidates.singleOrNull()
            val restored = target?.copy(restoredIds = target.restoredIds + pending) ?: track.copy(
                id = pending, uri = pending, missing = true, standalonePersisted = false,
                standaloneUri = pending.takeIf { track.sourceUris.isEmpty() },
                sourceUris = track.sourceUris.keys.associate { sourceMap.getValue(it).uri to pending },
                sourceParents = emptyMap(),
                sourcePaths = track.sourcePaths.mapKeys { sourceMap.getValue(it.key).uri },
                metadataStamp = null, metadataFromIndex = false, restoredIds = track.restoredIds + pending,
            )
            tracks[restored.id] = restored
            ids[track.id] = restored.id
        }
        val playlists = current.playlists.associateByTo(linkedMapOf()) { it.id }
        for (playlist in backup.library.playlists) {
            val previous = playlists[playlist.id]
            val folders = playlist.folders.map { folder ->
                val target = sourceMap.getValue(folder.sourceUri)
                folder.copy(sourceUri = target.uri, trackIds = folder.trackIds.map(ids::getValue),
                    documentId = if (backup.origin == currentOrigin && target.uri == folder.sourceUri) folder.documentId else "")
            }
            val allFolders = linkedMapOf<Pair<String, String>, PlaylistFolder>()
            (previous?.folders.orEmpty() + folders).forEach { folder ->
                val key = folder.sourceUri to folder.relativePath
                val old = allFolders[key]
                allFolders[key] = if (old == null) folder else old.copy(trackIds = (old.trackIds + folder.trackIds).distinct())
            }
            playlists[playlist.id] = playlist.copy(
                name = previous?.name ?: uniqueName(playlist.name, playlists.values.map { it.name }),
                trackIds = (previous?.trackIds.orEmpty() + playlist.trackIds.map(ids::getValue)).distinct(),
                manualTrackIds = ((previous?.manualTrackIds ?: previous?.trackIds).orEmpty() +
                    (playlist.manualTrackIds ?: playlist.trackIds).map(ids::getValue)).distinct(),
                folders = allFolders.values.toList(),
            )
        }
        val merged = current.copy(sources = sources.values.toList(), tracks = tracks.values.toList(),
            favorites = current.favorites + backup.library.favorites.map(ids::getValue), playlists = playlists.values.toList())
        return BackupMerge(merged, backup.positions.map {
            it.copy(trackId = ids.getValue(it.trackId), collection = if (it.collection.kind == CollectionKind.FOLDER)
                it.collection.copy(id = sourceMap.getValue(it.collection.id).uri) else it.collection)
        })
    }

    fun reconnect(library: Library, replacements: Map<String, Track>, source: Pair<String, String>? = null): Library {
        val known = library.tracks.associateBy { it.id }
        require(known.keys.containsAll(replacements.keys))
        if (source != null) require(source.first != source.second &&
            library.sources.any { it.uri == source.first } &&
            library.sources.any { it.uri == source.second && !it.needsReconnect })
        val remapped = replacements.map { (id, replacement) ->
            val old = known.getValue(id)
            val others = old.sourceUris.keys - listOfNotNull(source?.first).toSet()
            replacement.copy(
                sourceUris = old.sourceUris.filterKeys(others::contains) + replacement.sourceUris.filterKeys { it != source?.first },
                sourcePaths = old.sourcePaths.filterKeys(others::contains) + replacement.sourcePaths.filterKeys { it != source?.first },
                sourceParents = old.sourceParents.filterKeys(others::contains) + replacement.sourceParents.filterKeys { it != source?.first },
                restoredIds = old.restoredIds + replacement.restoredIds + id,
            )
        }
        fun id(value: String) = replacements[value]?.id ?: value
        val updated = LibraryEdits.upsertTracks(library.copy(tracks = library.tracks.filterNot { it.id in replacements }), remapped)
        val remaining = source != null && updated.tracks.any { source.first in it.sourceUris }
        return updated.copy(
            favorites = updated.favorites.mapTo(linkedSetOf(), ::id),
            playlists = updated.playlists.map { playlist -> playlist.copy(
                trackIds = playlist.trackIds.map(::id).distinct(),
                manualTrackIds = playlist.manualTrackIds?.map(::id)?.distinct(),
                folders = combineFolders(playlist.folders.map { folder -> folder.copy(trackIds = folder.trackIds.map(::id).distinct(),
                    sourceUri = if (folder.sourceUri == source?.first) source.second else folder.sourceUri,
                    documentId = if (folder.sourceUri == source?.first) "" else folder.documentId) }),
            ) },
            sources = updated.sources.mapNotNull { value ->
                when {
                    value.uri == source?.first && !remaining -> null
                    value.uri == source?.second -> value.copy(
                        pinned = value.pinned || library.sources.any { it.uri == source.first && it.pinned },
                        selectedForPlayback = value.selectedForPlayback || library.sources.any { it.uri == source.first && it.pinned },
                        restoredFrom = value.restoredFrom + source.first)
                    else -> value
                }
            },
        )
    }

    fun matches(library: Library, oldSource: String, newSource: String): Map<String, Track> {
        require(oldSource != newSource)
        val candidates = library.tracks.filter { newSource in it.sourceUris && !unavailable(it.uri) }
        val paths = candidates.groupBy { it.sourcePaths[newSource] }
        val names = candidates.groupBy { it.fileName }
        return library.tracks.filter { oldSource in it.sourceUris }.mapNotNull { old ->
            val path = old.sourcePaths[oldSource]
            val match = (if (path != null) paths[path] else names[old.fileName])?.singleOrNull()
            match?.let { old.id to it }
        }.toMap()
    }

    fun reconcileScanned(library: Library, scannedSources: Set<String>): Library {
        val pending = library.tracks.filter { unavailable(it.uri) && it.sourceUris.keys.any(scannedSources::contains) }
        if (pending.isEmpty()) return library
        val actual = library.tracks.filterNot { unavailable(it.uri) || it.missing }
        val paths = actual.flatMap { track -> track.sourcePaths.map { (source, path) -> (source to path) to track } }
            .groupBy({ it.first }, { it.second })
        val names = actual.flatMap { track -> track.sourceUris.keys.map { (it to track.fileName) to track } }
            .groupBy({ it.first }, { it.second })
        val replacements = pending.mapNotNull { old ->
            val candidates = old.sourceUris.keys.filter(scannedSources::contains).flatMap { source ->
                old.sourcePaths[source]?.let { paths[source to it].orEmpty() } ?: names[source to old.fileName].orEmpty()
            }.distinctBy { it.id }
            candidates.singleOrNull()?.let { old.id to it }
        }.toMap()
        return if (replacements.isEmpty()) library else reconnect(library, replacements)
    }

    private fun combineFolders(folders: List<PlaylistFolder>): List<PlaylistFolder> {
        val merged = linkedMapOf<Pair<String, String>, PlaylistFolder>()
        folders.forEach { folder ->
            val key = folder.sourceUri to folder.relativePath
            val old = merged[key]
            merged[key] = if (old == null) folder else old.copy(
                documentId = old.documentId.ifEmpty { folder.documentId },
                trackIds = (old.trackIds + folder.trackIds).distinct())
        }
        return merged.values.toList()
    }

    private fun uniqueName(name: String, existing: List<String>): String {
        if (existing.none { it.equals(name, true) }) return name
        var number = 2
        while (true) {
            val suffix = " ($number)"
            val value = name.take(80 - suffix.length) + suffix
            if (existing.none { it.equals(value, true) }) return value
            number++
        }
    }
}
