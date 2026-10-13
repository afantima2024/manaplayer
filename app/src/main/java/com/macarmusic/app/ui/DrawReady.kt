package com.macarmusic.app.ui

import android.view.View
import android.view.ViewTreeObserver
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal suspend fun View.awaitDraw() = suspendCancellableCoroutine { continuation ->
    val observer = viewTreeObserver
    var posted = false
    lateinit var listener: ViewTreeObserver.OnDrawListener
    fun removeListener() {
        if (observer.isAlive) observer.removeOnDrawListener(listener)
        if (viewTreeObserver !== observer && viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnDrawListener(listener)
        }
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
    observer.addOnDrawListener(listener)
    continuation.invokeOnCancellation { post { removeListener() } }
    invalidate()
}
