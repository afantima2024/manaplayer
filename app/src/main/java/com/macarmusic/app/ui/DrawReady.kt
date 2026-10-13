package com.macarmusic.app.ui

import android.view.View
import android.view.ViewTreeObserver
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal suspend fun View.awaitDraw() = suspendCancellableCoroutine { continuation ->
    var observer: ViewTreeObserver? = null
    var posted = false
    lateinit var listener: ViewTreeObserver.OnDrawListener
    lateinit var attachment: View.OnAttachStateChangeListener
    fun removeListener() {
        observer?.takeIf { it.isAlive }?.removeOnDrawListener(listener)
        if (viewTreeObserver !== observer && viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnDrawListener(listener)
        }
        removeOnAttachStateChangeListener(attachment)
    }
    fun observeAttached() {
        if (!continuation.isActive) return
        observer = viewTreeObserver
        observer?.addOnDrawListener(listener)
        invalidate()
    }
    listener = ViewTreeObserver.OnDrawListener {
        if (!posted) {
            posted = true
            // Posting from onDraw lets this traversal finish; pre-draw alone is too early.
            post {
                removeListener()
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }
    attachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = observeAttached()
        override fun onViewDetachedFromWindow(view: View) {
            observer?.takeIf { it.isAlive }?.removeOnDrawListener(listener)
            observer = null
        }
    }
    // Android 6 drops floating OnDrawListeners when its observer merges at attachment.
    addOnAttachStateChangeListener(attachment)
    if (isAttachedToWindow) observeAttached()
    continuation.invokeOnCancellation { post { removeListener() } }
}
