package com.macarmusic.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.text.Layout
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.macarmusic.app.lyrics.LyricKaraoke
import com.macarmusic.app.lyrics.LyricPlayback

class KaraokeTextView(context: Context) : AppCompatTextView(context) {
    private var enabled = false
    private var overlayColor = 0
    private var startMs: Long? = null
    private var endMs: Long? = null
    private var playback = LyricPlayback()
    private var initialized = false
    private var frameScheduled = false
    private val visibleBounds = Rect()
    private val overlayPath = Path()
    private var geometryLayout: Layout? = null
    private var offsets = FloatArray(0)
    private var totals = FloatArray(0)
    private val frame = object : Runnable {
        override fun run() {
            frameScheduled = false
            if (isShown && windowVisibility == VISIBLE) invalidate()
        }
    }

    init { initialized = true }

    fun configure(enabled: Boolean, color: Int, startMs: Long?, endMs: Long?, playback: LyricPlayback) {
        val show = enabled && text.isNotBlank()
        if (!show && !this.enabled) return
        this.enabled = show
        overlayColor = color or 0xff000000.toInt()
        this.startMs = startMs
        this.endMs = endMs
        this.playback = playback
        if (!shouldAnimate()) cancelFrame()
        invalidate()
    }

    fun stopKaraoke() {
        if (!enabled && !frameScheduled) return
        enabled = false
        cancelFrame()
        invalidate()
    }

    private fun shouldAnimate(): Boolean = enabled && playback.advancing && startMs != null && endMs != null &&
        endMs!! > startMs!! && playback.positionAt(SystemClock.uptimeMillis()) < endMs!! &&
        isAttachedToWindow && isShown && windowVisibility == VISIBLE && getLocalVisibleRect(visibleBounds)

    private fun cancelFrame() {
        if (frameScheduled) removeCallbacks(frame)
        frameScheduled = false
    }

    override fun onDetachedFromWindow() {
        cancelFrame()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (initialized && visibility != VISIBLE) cancelFrame()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (initialized && visibility != VISIBLE) cancelFrame()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        geometryLayout = null
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        geometryLayout = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!enabled) return
        val fraction = LyricKaraoke.fraction(startMs, endMs, playback.positionAt(SystemClock.uptimeMillis()))
        val textLayout = layout ?: return
        if (fraction > 0f) drawOverlay(canvas, textLayout, fraction)
        if (!frameScheduled && shouldAnimate()) {
            frameScheduled = true
            postDelayed(frame, FRAME_MS)
        }
    }

    private fun drawOverlay(canvas: Canvas, textLayout: Layout, fraction: Float) {
        if (geometryLayout !== textLayout) {
            offsets = FloatArray(textLayout.lineCount)
            totals = FloatArray(textLayout.lineCount)
            var first = 0
            var width = 0f
            for (line in 0 until textLayout.lineCount) {
                offsets[line] = width
                width += (textLayout.getLineRight(line) - textLayout.getLineLeft(line)).coerceAtLeast(0f)
                val end = textLayout.getLineEnd(line)
                // Wrapped text sweeps in reading order; translations at the same timestamp sweep together.
                if (line == textLayout.lineCount - 1 || end > 0 && text[end - 1] == '\n') {
                    for (paragraphLine in first..line) totals[paragraphLine] = width
                    first = line + 1
                    width = 0f
                }
            }
            geometryLayout = textLayout
        }
        val saved = canvas.save()
        canvas.translate(totalPaddingLeft.toFloat(), totalPaddingTop.toFloat())
        canvas.getClipBounds(visibleBounds)
        overlayPath.rewind()
        val first = textLayout.getLineForVertical(visibleBounds.top)
        val last = textLayout.getLineForVertical(visibleBounds.bottom)
        for (line in first..last) {
            val left = textLayout.getLineLeft(line)
            val right = textLayout.getLineRight(line)
            val fill = (totals[line] * fraction - offsets[line]).coerceIn(0f, (right - left).coerceAtLeast(0f))
            if (fill <= 0f) continue
            val rtl = textLayout.getParagraphDirection(line) < 0
            overlayPath.addRect(if (rtl) right - fill else left, textLayout.getLineTop(line).toFloat(),
                if (rtl) right else left + fill, textLayout.getLineBottom(line).toFloat(), Path.Direction.CW)
        }
        canvas.clipPath(overlayPath)
        val original = paint.color
        paint.color = overlayColor
        textLayout.draw(canvas)
        paint.color = original
        canvas.restoreToCount(saved)
    }

    companion object {
        private const val FRAME_MS = 34L
    }
}
