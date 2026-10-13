package com.macarmusic.app.ui

import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.macarmusic.app.R
import com.macarmusic.app.lyrics.Lyrics
import kotlin.math.abs

class LyricScrollController(
    private val list: RecyclerView,
    private val adapter: LyricsAdapter,
    private val preview: TextView,
    private val guide: View,
    private val seekAndPlay: (String, Long) -> Unit,
) {
    private data class Gesture(
        val trackId: String?,
        val lyrics: Lyrics?,
        val maySeek: Boolean,
        var dragged: Boolean = false,
        var cancelled: Boolean = false,
    )
    private var trackId: String? = null
    private var gesture: Gesture? = null
    private var manualUntil = 0L
    private var dragSeek = false
    private var smooth = true
    private var seekable = false
    private var generation = 0
    private var pendingFollow: Runnable? = null

    init {
        list.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                list.setPadding(list.paddingLeft, list.height / 2, list.paddingRight, list.height / 2)
                if (adapter.activeLine >= 0 && gesture == null && manualUntil == 0L) {
                    scheduleCenter(adapter.activeLine, immediate = true)
                }
            }
        }
        list.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(recyclerView: RecyclerView, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        list.stopScroll()
                        gesture = Gesture(trackId, adapter.lyrics, dragSeek && seekable && adapter.lyrics?.timed == true)
                        manualUntil = Long.MAX_VALUE
                        list.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP -> release(cancelled = false)
                    MotionEvent.ACTION_CANCEL -> release(cancelled = true)
                    MotionEvent.ACTION_POINTER_DOWN -> cancelGesture()
                }
                return false
            }
        })
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    gesture?.dragged = true
                    guide.isVisible = gesture?.cancelled == false && adapter.lyrics?.timed == true
                }
                if (newState == RecyclerView.SCROLL_STATE_IDLE && gesture == null && manualUntil != 0L) {
                    manualUntil = SystemClock.elapsedRealtime() + MANUAL_HOLD_MS
                }
            }
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val drag = gesture
                if (drag?.dragged == true && drag.lyrics?.timed == true && !drag.cancelled) {
                    val time = centeredTime()
                    preview.isVisible = time != null
                    preview.text = time?.let(TimeText::format)
                    preview.contentDescription = time?.let {
                        list.context.getString(if (drag.maySeek) R.string.lyrics_release_to_play else R.string.lyrics_drag_time,
                            TimeText.format(it))
                    }
                }
            }
        })
    }

    fun configure(dragSeek: Boolean, smooth: Boolean) {
        if (this.dragSeek != dragSeek) cancelGesture()
        this.dragSeek = dragSeek
        this.smooth = smooth
    }

    fun submit(lyrics: Lyrics?, trackId: String?) {
        if (adapter.lyrics == lyrics && this.trackId == trackId) return
        stop()
        this.trackId = trackId
        adapter.submit(lyrics)
        list.scrollToPosition(0)
    }

    fun progress(positionMs: Long, seekable: Boolean, force: Boolean = false) {
        this.seekable = seekable
        if (!seekable) cancelGesture()
        val changed = adapter.highlight(positionMs)
        val manualExpired = manualUntil != 0L && SystemClock.elapsedRealtime() >= manualUntil
        if (!list.isShown || adapter.lyrics?.timed != true || SystemClock.elapsedRealtime() < manualUntil) return
        if (force || changed || manualExpired) {
            manualUntil = 0L
            scheduleCenter(adapter.activeLine.coerceAtLeast(0), immediate = force || !smooth)
        }
    }

    fun stop() {
        generation++
        pendingFollow?.let(list::removeCallbacks)
        pendingFollow = null
        cancelGesture()
        gesture = null
        manualUntil = 0L
        list.stopScroll()
        list.parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun cancelGesture() {
        gesture?.cancelled = true
        preview.isVisible = false
        guide.isVisible = false
    }

    private fun release(cancelled: Boolean) {
        val drag = gesture
        val time = centeredTime()
        gesture = null
        preview.isVisible = false
        guide.isVisible = false
        manualUntil = SystemClock.elapsedRealtime() + MANUAL_HOLD_MS
        list.parent?.requestDisallowInterceptTouchEvent(false)
        if (cancelled || drag?.dragged != true || !drag.maySeek || drag.cancelled || time == null) return
        val expectedGeneration = generation
        // Wait until RecyclerView has handled ACTION_UP, then stop its fling at the released line.
        list.post {
            if (generation != expectedGeneration || !list.isShown || !dragSeek || !seekable ||
                trackId != drag.trackId || adapter.lyrics !== drag.lyrics
            ) return@post
            list.stopScroll()
            manualUntil = 0L
            drag.trackId?.let { seekAndPlay(it, time) }
        }
    }

    private fun centeredTime(): Long? {
        val child = list.children.filter { list.getChildAdapterPosition(it) != RecyclerView.NO_POSITION }
            .minByOrNull { abs(it.top + it.height / 2f - list.height / 2f) } ?: return null
        return adapter.lyrics?.lines?.getOrNull(list.getChildAdapterPosition(child))?.timeMs
    }

    private fun scheduleCenter(index: Int, immediate: Boolean) {
        pendingFollow?.let(list::removeCallbacks)
        val expectedGeneration = generation
        val request = object : Runnable {
            override fun run() {
                if (pendingFollow !== this) return
                if (generation != expectedGeneration || adapter.activeLine.coerceAtLeast(0) != index ||
                    index >= adapter.itemCount || gesture != null || manualUntil != 0L || !list.isShown || list.height == 0
                ) {
                    pendingFollow = null
                    return
                }
                // Offscreen item updates may not trigger a layout callback; wait for their frame instead.
                if (list.isLayoutRequested || list.hasPendingAdapterUpdates()) {
                    list.postOnAnimation(this)
                    return
                }
                pendingFollow = null
                center(index, immediate || !smooth)
            }
        }
        pendingFollow = request
        list.postOnAnimation(request)
    }

    private fun center(index: Int, immediate: Boolean) {
        val manager = list.layoutManager as LinearLayoutManager
        val child = manager.findViewByPosition(index)
        if (child != null) {
            val distance = child.top + child.height / 2 - list.height / 2
            if (immediate) list.scrollBy(0, distance)
            else list.smoothScrollBy(0, distance, DecelerateInterpolator(), FOLLOW_MS)
        } else if (immediate) {
            manager.scrollToPositionWithOffset(index, -dp(24))
            scheduleCenter(index, immediate = true)
        } else {
            manager.startSmoothScroll(object : LinearSmoothScroller(list.context) {
                override fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int) =
                    list.height / 2 - (viewStart + viewEnd) / 2
                override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics) = 60f / displayMetrics.densityDpi
                override fun calculateTimeForDeceleration(dx: Int) = FOLLOW_MS
            }.apply { targetPosition = index })
        }
    }

    private fun dp(value: Int) = (value * list.resources.displayMetrics.density).toInt()
    private companion object {
        const val FOLLOW_MS = 400
        const val MANUAL_HOLD_MS = 5_000L
    }
}
