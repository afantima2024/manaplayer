package com.macarmusic.app.ui

import android.os.SystemClock
import android.widget.ImageView
import androidx.core.view.isVisible
import com.macarmusic.app.data.BackgroundRepository
import com.macarmusic.app.data.DisplayOptions
import com.macarmusic.app.model.TransitionStyle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import java.io.IOException

class BackgroundSlideshow(
    private val image: ImageView,
    private val incoming: ImageView,
    private val repository: BackgroundRepository,
    private val onIndex: (Int) -> Unit,
    private val onError: (Exception) -> Unit,
) {
    suspend fun show(options: DisplayOptions, startIndex: Int) {
        resetIncoming()
        if (!options.customTheme || options.backgroundImages.isEmpty()) {
            image.setImageDrawable(null)
            image.isVisible = false
            return
        }
        try {
            var index = startIndex.coerceIn(options.backgroundImages.indices)
            image.setImageBitmap(repository.render(options, index))
            image.isVisible = true
            onIndex(index)
            if (options.backgroundImages.size == 1) awaitCancellation()
            var nextAt = SystemClock.elapsedRealtime() + options.slideshow.intervalMs
            while (currentCoroutineContext().isActive) {
                delay((nextAt - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                val next = (index + 1) % options.backgroundImages.size
                val bitmap = repository.render(options, next)
                currentCoroutineContext().ensureActive()
                val started = SystemClock.elapsedRealtime()
                incoming.setImageBitmap(bitmap)
                incoming.isVisible = true
                if (options.slideshow.transition != TransitionStyle.NONE && options.slideshow.animationMs > 0) {
                    ViewTransitions.playBackground(incoming, options.slideshow.transition, options.slideshow.animationMs, image)
                }
                image.setImageBitmap(bitmap)
                resetIncoming()
                index = next
                onIndex(index)
                nextAt = started + options.slideshow.intervalMs
            }
        } catch (error: IOException) {
            onError(error)
        } catch (error: SecurityException) {
            onError(error)
        } finally {
            resetIncoming()
        }
    }

    private fun resetIncoming() {
        incoming.isVisible = false
        incoming.setImageDrawable(null)
        ViewTransitions.reset(incoming)
        ViewTransitions.reset(image)
    }
}
