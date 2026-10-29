package com.macarmusic.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.macarmusic.app.R
import com.macarmusic.app.model.AudioFiles
import com.macarmusic.app.model.DocumentEntry
import com.macarmusic.app.model.MetadataReuse
import com.macarmusic.app.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

data class MetadataResult(val track: Track, val warning: Boolean)

class MetadataReader(private val context: Context, private val documents: DocumentAccess) {
    private val reader = Mutex()

    fun reference(entry: DocumentEntry, sourceUri: String? = null, standalonePersisted: Boolean = false,
        identity: String? = null,
    ): Track {
        val uri = Uri.parse(entry.uri)
        documents.requireContentUri(uri)
        if (entry.isVirtual) throw IOException(context.getString(R.string.virtual_file_unsupported))
        if (!AudioFiles.accepts(entry.name, entry.mimeType)) {
            throw IOException(context.getString(R.string.unsupported_file, entry.name))
        }
        return Track(
            id = identity ?: documents.identity(uri),
            uri = entry.uri,
            fileName = entry.name,
            title = AudioFiles.titleFromFileName(entry.name),
            mimeType = entry.mimeType,
            sourceUris = sourceUri?.let { mapOf(it to entry.uri) }.orEmpty(),
            standaloneUri = if (sourceUri == null) entry.uri else null,
            standalonePersisted = standalonePersisted,
        )
    }

    suspend fun read(entry: DocumentEntry, sourceUri: String? = null,
        standalonePersisted: Boolean = false,
        identity: String? = null,
    ): MetadataResult = withContext(Dispatchers.IO) {
        reader.withLock { readNow(entry, sourceUri, standalonePersisted, identity) }
    }

    private fun readNow(entry: DocumentEntry, sourceUri: String?, standalonePersisted: Boolean, identity: String?): MetadataResult {
        val fallback = reference(entry, sourceUri, standalonePersisted, identity)
        val uri = Uri.parse(entry.uri)
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException(context.getString(R.string.document_unavailable))
        return descriptor.use {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(it.fileDescriptor)
                fun tag(key: Int) = AudioFiles.cleanTag(retriever.extractMetadata(key))
                MetadataResult(
                    fallback.copy(
                        title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE).ifBlank { fallback.title },
                        artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                        album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                        durationMs = tag(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull()
                            ?.coerceAtLeast(0) ?: 0,
                        trackNumber = tag(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                        year = tag(MediaMetadataRetriever.METADATA_KEY_YEAR),
                        genre = tag(MediaMetadataRetriever.METADATA_KEY_GENRE),
                        metadataStamp = MetadataReuse.stamp(entry),
                    ),
                    warning = false,
                )
            } catch (error: SecurityException) {
                throw error
            } catch (error: RuntimeException) {
                // Android's native retriever reports unsupported/malformed tags as RuntimeException.
                Log.w(TAG, "Metadata unavailable for ${entry.name}; retaining the playable reference", error)
                MetadataResult(fallback, warning = true)
            } finally {
                try {
                    retriever.release()
                } catch (error: RuntimeException) {
                    Log.w(TAG, "Metadata retriever release failed", error)
                }
            }
        }
    }

    private companion object {
        const val TAG = "MetadataReader"
    }
}
