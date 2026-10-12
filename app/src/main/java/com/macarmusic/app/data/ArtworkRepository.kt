package com.macarmusic.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.macarmusic.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

data class CoverResult(val bitmap: Bitmap?, val warning: String? = null)

class ArtworkRepository(private val context: Context) {
    private val mutex = Mutex()
    private val cache = object : LruCache<String, CoverResult>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: CoverResult): Int = value.bitmap?.byteCount ?: 128
    }

    suspend fun load(uri: String): CoverResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            cache.get(uri)?.let { return@withLock it }
            val result = extract(Uri.parse(uri))
            cache.put(uri, result)
            result
        }
    }

    fun clear() = cache.evictAll()

    private fun extract(uri: Uri): CoverResult {
        if (uri.scheme != "content") return CoverResult(null, context.getString(R.string.local_documents_only))
        val retriever = MediaMetadataRetriever()
        return try {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IOException(context.getString(R.string.document_unavailable))
            descriptor.use {
                retriever.setDataSource(it.fileDescriptor)
                val bytes = retriever.embeddedPicture ?: return CoverResult(null)
                if (bytes.size > 8 * 1024 * 1024) {
                    return CoverResult(null, context.getString(R.string.artwork_too_large))
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    return CoverResult(null, context.getString(R.string.artwork_invalid))
                }
                var sample = 1
                while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                CoverResult(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
            }
        } catch (error: IOException) {
            failed(error)
        } catch (error: SecurityException) {
            failed(error)
        } catch (error: RuntimeException) {
            // Some platform decoders cannot extract artwork from otherwise playable audio.
            failed(error)
        } finally {
            try {
                retriever.release()
            } catch (error: RuntimeException) {
                Log.w(TAG, "Artwork retriever release failed", error)
            }
        }
    }

    private fun failed(error: Exception): CoverResult {
        Log.w(TAG, "Embedded artwork unavailable", error)
        return CoverResult(null, context.getString(R.string.artwork_unavailable))
    }

    private companion object {
        const val TAG = "ArtworkRepository"
    }
}
