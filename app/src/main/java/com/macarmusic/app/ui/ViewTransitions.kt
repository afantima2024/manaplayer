package com.macarmusic.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.macarmusic.app.model.TransitionStyle
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.delay
import kotlin.coroutines.resume

object ViewTransitions {
    private val backgroundInterpolator = AccelerateDecelerateInterpolator()

    fun reset(view: View) {
        view.alpha = 1f
        view.translationX = 0f
        view.scaleX = 1f
        view.scaleY = 1f
    }

    fun entrance(view: View, style: TransitionStyle, durationMs: Long, outgoing: View? = null): AnimatorSet {
        reset(view)
        val effects = mutableListOf<Animator>()
        when (style) {
            TransitionStyle.FADE -> effects.add(ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f))
            TransitionStyle.SLIDE -> {
                val width = maxOf(view.width, (view.parent as? View)?.width ?: 0).toFloat().coerceAtLeast(1f)
                effects.add(ObjectAnimator.ofFloat(view, View.TRANSLATION_X, width, 0f))
                outgoing?.let { effects.add(ObjectAnimator.ofFloat(it, View.TRANSLATION_X, 0f, -width)) }
            }
            TransitionStyle.ZOOM -> {
                effects.add(ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f))
                effects.add(ObjectAnimator.ofFloat(view, View.SCALE_X, 1.08f, 1f))
                effects.add(ObjectAnimator.ofFloat(view, View.SCALE_Y, 1.08f, 1f))
            }
            TransitionStyle.NONE -> Unit
        }
        return AnimatorSet().apply {
            playTogether(effects)
            duration = durationMs
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    suspend fun play(animator: Animator) = suspendCancellableCoroutine { continuation ->
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        })
        continuation.invokeOnCancellation { animator.cancel() }
        animator.start()
    }

    suspend fun playBackground(view: View, style: TransitionStyle, durationMs: Long, outgoing: View) {
        if (style == TransitionStyle.NONE || durationMs <= 0 || !ValueAnimator.areAnimatorsEnabled()) return
        val duration = (durationMs * Settings.Global.getFloat(view.context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f)).toLong()
        if (duration <= 0) return
        val started = SystemClock.uptimeMillis()
        val width = maxOf(view.width, (view.parent as? View)?.width ?: 0).toFloat().coerceAtLeast(1f)
        while (true) {
            val elapsed = SystemClock.uptimeMillis() - started
            val fraction = backgroundInterpolator.getInterpolation((elapsed.toFloat() / duration).coerceIn(0f, 1f))
            when (style) {
                TransitionStyle.FADE -> view.alpha = fraction
                TransitionStyle.SLIDE -> {
                    view.translationX = width * (1 - fraction)
                    outgoing.translationX = -width * fraction
                }
                TransitionStyle.ZOOM -> {
                    view.alpha = fraction
                    view.scaleX = 1.08f - 0.08f * fraction
                    view.scaleY = view.scaleX
                }
                TransitionStyle.NONE -> Unit
            }
            if (elapsed >= duration) return
            // Backgrounds do not need to redraw at the display's full refresh rate.
            delay(minOf(34L, duration - elapsed))
        }
    }
}
