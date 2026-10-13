package com.macarmusic.app.lyrics

import android.content.Context
import android.net.Uri
import android.util.Log
import com.macarmusic.app.data.BoundedInput
import com.macarmusic.app.data.MediaStoreAccess
import com.macarmusic.app.data.MusicRepository
import com.macarmusic.app.model.Track
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.util.ArrayDeque

class LocalLyricsLoader(private val context: Context, private val music: MusicRepository) {
    suspend fun load(track: Track): LyricResult {
        var failed = false
        for ((source, _) in track.sourceUris.entries.sortedBy { it.value != track.uri }) {
            if (MediaStoreAccess.folder(Uri.parse(source)) != null) continue
            try {
                val parent = track.sourceParents[source] ?: findParent(source, track) ?: continue
                val entries = music.documents.children(source, parent, includeLyrics = true)
                for (entry in LyricFiles.matching(track, entries).take(4)) {
                    try {
                        val coroutine = currentCoroutineContext()
                        val bytes = context.contentResolver.openInputStream(Uri.parse(entry.uri))?.use {
                            BoundedInput.read(it, LyricsParser.MAX_BYTES) { coroutine.ensureActive() }
                        } ?: throw IOException("Lyric document is unavailable")
                        val lyrics = LyricsParser.parse(LyricsParser.decode(bytes))
                            ?: throw IOException("Local lyric file is empty")
                        return LyricResult(lyrics, LyricOrigin.LOCAL_FILE)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: IOException) {
                        failed = true
                        Log.w(TAG, "A local lyric file could not be read", error)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                failed = true
                Log.w(TAG, "Local lyric folder is unavailable", error)
            } catch (error: SecurityException) {
                failed = true
                Log.w(TAG, "Local lyric permission was denied", error)
            } catch (error: IllegalArgumentException) {
                failed = true
                Log.w(TAG, "Local lyric provider returned an invalid document", error)
            } catch (error: IllegalStateException) {
                failed = true
                Log.w(TAG, "Local lyric provider is unavailable", error)
            }
        }
        try {
            currentCoroutineContext().ensureActive()
            music.documents.requireContentUri(Uri.parse(track.uri))
            val input = context.contentResolver.openInputStream(Uri.parse(track.uri))
                ?: throw IOException("Audio document is unavailable for lyric lookup")
            val text = input.use(Id3Lyrics::read)
                ?: return LyricResult(issue = localIssue(failed, track))
            val lyrics = LyricsParser.parse(text)
            if (lyrics != null) return LyricResult(lyrics, LyricOrigin.EMBEDDED,
                issue = if (failed) LyricIssue.LOCAL_UNREADABLE else null)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            failed = true
            Log.w(TAG, "Embedded lyric data could not be read", error)
        } catch (error: SecurityException) {
            failed = true
            Log.w(TAG, "Embedded lyric permission was denied", error)
        } catch (error: IllegalArgumentException) {
            failed = true
            Log.w(TAG, "Embedded lyric document is invalid", error)
        } catch (error: IllegalStateException) {
            failed = true
            Log.w(TAG, "Embedded lyric provider is unavailable", error)
        }
        return LyricResult(issue = localIssue(failed, track))
    }

    private fun localIssue(failed: Boolean, track: Track): LyricIssue? = when {
        failed -> LyricIssue.LOCAL_UNREADABLE
        track.sourceUris.isEmpty() || track.sourceUris.keys.all {
            MediaStoreAccess.folder(Uri.parse(it)) != null
        } -> LyricIssue.FOLDER_ACCESS_NEEDED
        else -> null
    }

    private suspend fun findParent(source: String, track: Track): String? {
        val folders = ArrayDeque<Pair<String, Int>>().apply { add(music.documents.rootId(source) to 0) }
        val visited = hashSetOf<String>()
        var seen = 0
        while (folders.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (folder, depth) = folders.removeFirst()
            if (!visited.add(folder)) continue
            if (depth > 64 || seen > 100_000) throw IOException("Rescan the music folder to locate its lyric files")
            for (entry in music.documents.children(source, folder)) {
                if (++seen > 100_000) throw IOException("Rescan the music folder to locate its lyric files")
                if (entry.isDirectory) folders.add(entry.id to depth + 1)
                else if (music.documents.identity(Uri.parse(entry.uri)) == track.id) {
                    // Upgrade old libraries without interpreting provider-owned document IDs as paths.
                    music.update { library -> library.copy(tracks = library.tracks.map {
                        if (it.id == track.id && source in it.sourceUris)
                            it.copy(sourceParents = it.sourceParents + (source to folder)) else it
                    }) }
                    return folder
                }
            }
        }
        return null
    }

    private companion object { const val TAG = "LocalLyrics" }
}
