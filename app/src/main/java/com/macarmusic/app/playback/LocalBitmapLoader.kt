package com.macarmusic.app.playback

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.macarmusic.app.data.ArtworkRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException

@androidx.annotation.OptIn(UnstableApi::class)
class LocalBitmapLoader(
    private val artwork: ArtworkRepository,
    private val scope: CoroutineScope,
) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean =
        mimeType in setOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp")

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = load {
        artwork.load(uri.toString()).bitmap ?: throw IOException("No embedded cover")
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = load {
        if (data.size > 8 * 1024 * 1024) throw IOException("Embedded cover exceeds the size limit")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Invalid embedded cover")
        var sample = 1
        while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
        BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }) ?: throw IOException("Cannot decode embedded cover")
    }

    private fun load(block: suspend () -> Bitmap): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        val job = scope.launch(Dispatchers.IO) {
            try {
                future.set(block())
            } catch (error: CancellationException) {
                future.cancel(false)
                throw error
            } catch (error: IOException) {
                future.setException(error)
            } catch (error: IllegalArgumentException) {
                future.setException(error)
            }
        }
        future.addListener({ if (future.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
        return future
    }
}
