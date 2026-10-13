package com.macarmusic.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors
import com.macarmusic.app.R
import com.macarmusic.app.model.SpectrumOptions
import com.macarmusic.app.model.FireStyle
import com.macarmusic.app.model.SpectrumPalette
import com.macarmusic.app.model.SpectrumStyle
import com.macarmusic.app.model.SpectrumColors
import com.macarmusic.app.playback.SpectrumAnalyzer
import com.macarmusic.app.playback.SpectrumEnvelope
import com.macarmusic.app.playback.SpectrumFrame
import kotlin.math.max
import kotlin.math.ceil
import kotlin.math.roundToInt

class SpectrumView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val envelope = SpectrumEnvelope()
    private val silence = FloatArray(SpectrumAnalyzer.BAND_COUNT)
    private var levels = silence
    private var options = SpectrumOptions()
    private var active = false
    private var lastFrame = 0L
    private var lastDraw = 0L
    private var redrawPosted = false
    private val redraw = object : Runnable {
        override fun run() {
            redrawPosted = false
            if (!canAnimate()) return
            val remaining = 1_000L / options.refreshRate - (SystemClock.elapsedRealtime() - lastDraw)
            if (remaining > 0) scheduleRedraw(remaining) else invalidate()
        }
    }
    private var startedAt = 0L
    private var sampleRate = 0
    private var gradient: LinearGradient? = null
    private var gradientTop = Float.NaN
    private var gradientBottom = Float.NaN
    private val barMesh by lazy { BarMesh() }
    private val legacyGradientMatrix by lazy { Matrix() }
    private val density = resources.displayMetrics.density
    private val textSize = 12 * resources.displayMetrics.scaledDensity
    private val foreground get() = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
    private val accent get() = AccentTheme.color(this, com.google.android.material.R.attr.colorPrimary)
    private val themeColors by lazy { (accent and 0xffffff).let { SpectrumColors(it, it, it) } }

    fun configure(value: SpectrumOptions) {
        if (options == value) return
        cancelRedraw()
        options = value
        gradient = null
        invalidate()
    }

    fun setPlaybackActive(value: Boolean) {
        if (active == value) return
        cancelRedraw()
        active = value
        startedAt = SystemClock.elapsedRealtime()
        clear()
        invalidate()
    }

    fun submit(frame: SpectrumFrame) {
        if (!active || !frame.hasAudio) {
            clear()
        } else {
            levels = frame.levels
            sampleRate = frame.sampleRate
            lastFrame = SystemClock.elapsedRealtime()
        }
    }

    private fun clear() {
        levels = silence
        lastFrame = 0
        lastDraw = 0
        sampleRate = 0
        envelope.reset()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        gradient = null
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) invalidate() else cancelRedraw()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) invalidate() else cancelRedraw()
    }

    override fun onDetachedFromWindow() {
        cancelRedraw()
        super.onDetachedFromWindow()
    }

    private fun canAnimate() = active && isShown && isAttachedToWindow && windowVisibility == VISIBLE

    private fun scheduleRedraw(delay: Long = 1_000L / options.refreshRate) {
        // Other UI invalidations must not create parallel animation loops.
        if (!redrawPosted && canAnimate()) redrawPosted = postDelayed(redraw, delay)
    }

    private fun cancelRedraw() {
        if (redrawPosted) removeCallbacks(redraw)
        redrawPosted = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.elapsedRealtime()
        val seconds = if (lastDraw == 0L) 0f else ((now - lastDraw) / 1_000f).coerceIn(0f, 0.25f)
        lastDraw = now
        val fresh = active && lastFrame > 0 && now - lastFrame <= 500
        envelope.update(if (fresh) levels else silence, seconds, options)
        val left = paddingLeft + 4 * density
        val right = width - paddingRight - 4 * density
        val top = paddingTop + textSize + 8 * density
        val bottom = height - paddingBottom - textSize - 8 * density
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = foreground
        paint.textSize = textSize
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(context.getString(R.string.spectrum), left, paddingTop + textSize, paint)
        canvas.drawText(context.getString(R.string.spectrum_low_frequency), left, height - paddingBottom - 2 * density, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(context.getString(R.string.spectrum_high_frequency,
            (minOf(20_000, sampleRate.takeIf { it > 0 }?.div(2) ?: 20_000) / 1_000f).toInt()),
            right, height - paddingBottom - 2 * density, paint)
        if (bottom > top && right > left) {
            if (gradient == null || gradientTop != top || gradientBottom != bottom) {
                gradient = colors(top, bottom)
                gradientTop = top
                gradientBottom = bottom
            }
            paint.shader = gradient
            paint.color = accent
            paint.strokeWidth = 2 * density
            val step = (right - left) / options.bands
            val group = SpectrumAnalyzer.BAND_COUNT / options.bands
            val fire = options.style == SpectrumStyle.BARS && options.palette == SpectrumPalette.FIRE
            val bars = options.style == SpectrumStyle.BARS
            val legacyBars = bars && Build.VERSION.SDK_INT < 29
            val barColors = when (options.palette) {
                SpectrumPalette.FIRE -> options.fireColors
                SpectrumPalette.CLASSIC -> options.classicColors
                SpectrumPalette.THEME -> themeColors
            }
            val peakColor = foreground
            val referenceColor = (MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurfaceVariant) and 0xffffff) or 0x64000000
            if (bars && !legacyBars) barMesh.clear()
            path.reset()
            for (band in 0 until options.bands) {
                var value = 0f
                var peak = 0f
                for (index in band * group until (band + 1) * group) {
                    value = max(value, envelope.bars[index])
                    peak = max(peak, envelope.peaks[index])
                }
                val x = left + band * step
                val y = bottom - value * (bottom - top)
                val barRight = (x + step - max(density, step * 0.2f)).coerceAtLeast(x + 1f)
                if (legacyBars) {
                    drawLegacyBar(canvas, x, barRight, top, bottom, value, peak, referenceColor, peakColor, fire)
                } else if (bars && options.rendering == FireStyle.LED) {
                    addSegmentedBar(x, barRight, top, bottom, value, peak, referenceColor, peakColor, barColors, fire)
                } else if (bars) {
                    barMesh.gradient(x, y, barRight, bottom, if (fire) y else top, bottom, barColors)
                    if (options.peaks && peak > 0) {
                        val peakY = bottom - peak * (bottom - top)
                        barMesh.rect(x, peakY - density, barRight, peakY + density, peakColor, peakColor)
                    }
                } else {
                    if (band == 0) path.moveTo(x + step / 2, y) else path.lineTo(x + step / 2, y)
                }
                if (!bars && options.peaks && peak > 0) {
                    val peakY = bottom - peak * (bottom - top)
                    val shader = paint.shader
                    paint.shader = null
                    paint.color = foreground
                    canvas.drawLine(x, peakY, barRight, peakY, paint)
                    paint.shader = shader
                    paint.color = accent
                }
            }
            if (legacyBars) gradient?.setLocalMatrix(null)
            else if (bars) barMesh.draw(canvas)
            if (options.style == SpectrumStyle.LINE) {
                paint.style = Paint.Style.STROKE
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.FILL
            }
            if (lastFrame == 0L || (active && now - lastFrame > 2_000)) {
                paint.shader = null
                paint.color = foreground
                paint.textAlign = Paint.Align.CENTER
                val status = when {
                    !active -> R.string.paused
                    now - max(startedAt, lastFrame) > 2_000 -> R.string.spectrum_no_data
                    else -> R.string.spectrum_waiting
                }
                canvas.drawText(context.getString(status),
                    width / 2f, (top + bottom) / 2, paint)
            }
        }
        scheduleRedraw()
    }

    private fun drawLegacyBar(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float,
        level: Float, peak: Float, referenceColor: Int, peakColor: Int, perBar: Boolean,
    ) {
        // Legacy vertex rendering is absent on hardware and loses precision on software canvases.
        val led = options.rendering == FireStyle.LED
        val step = (bottom - top) / FIRE_SEGMENTS
        val gap = max(1f, step * 0.25f).coerceAtMost(step * 0.5f)
        val count = ceil(level * FIRE_SEGMENTS).toInt().coerceIn(0, FIRE_SEGMENTS)
        val x = if (led) left.roundToInt().toFloat() else left
        val end = if (led) right.roundToInt().toFloat() else right
        val litTop = if (led) (bottom - count * step + gap).roundToInt().toFloat()
            else bottom - level * (bottom - top)
        path.reset()
        if (led) {
            for (row in 0 until count) {
                val base = bottom - row * step
                path.addRect(x, (base - step + gap).roundToInt().toFloat(), end,
                    base.roundToInt().toFloat(), Path.Direction.CW)
            }
        } else if (level > 0) path.addRect(x, litTop, end, bottom, Path.Direction.CW)
        paint.shader = null
        if (led) {
            paint.color = referenceColor
            canvas.drawRect(x, bottom - max(1f, density), end, bottom, paint)
        }
        if (!path.isEmpty) {
            legacyGradientMatrix.setScale(1f, if (perBar) (bottom - litTop) / (bottom - top) else 1f, 0f, bottom)
            gradient?.setLocalMatrix(legacyGradientMatrix)
            paint.color = android.graphics.Color.WHITE
            paint.shader = gradient
            canvas.drawPath(path, paint)
        }
        paint.shader = null
        if (options.peaks && peak > 0) {
            paint.color = peakColor
            if (led) {
                val row = (ceil(peak * FIRE_SEGMENTS).toInt() - 1).coerceIn(0, FIRE_SEGMENTS - 1)
                val y = (bottom - (row + 1) * step + gap).roundToInt().toFloat()
                canvas.drawRect(x, y, end, y + max(1f, density).coerceAtMost(step - gap), paint)
            } else {
                val y = bottom - peak * (bottom - top)
                canvas.drawRect(x, y - density, end, y + density, paint)
            }
        }
        paint.color = android.graphics.Color.WHITE
        paint.shader = gradient
    }

    private fun addSegmentedBar(left: Float, right: Float, top: Float, bottom: Float, level: Float, peak: Float,
        referenceColor: Int, peakColor: Int, colors: SpectrumColors, perBar: Boolean,
    ) {
        val step = (bottom - top) / FIRE_SEGMENTS
        val gap = max(1f, step * 0.25f).coerceAtMost(step * 0.5f)
        val count = ceil(level * FIRE_SEGMENTS).toInt().coerceIn(0, FIRE_SEGMENTS)
        val x = left.roundToInt().toFloat()
        val end = right.roundToInt().toFloat()
        barMesh.rect(x, bottom - max(1f, density), end, bottom, referenceColor, referenceColor)
        val litTop = (bottom - count * step + gap).roundToInt().toFloat()
        for (row in 0 until count) {
            val base = bottom - row * step
            barMesh.gradient(x, (base - step + gap).roundToInt().toFloat(), end,
                base.roundToInt().toFloat(), if (perBar) litTop else top, bottom, colors)
        }
        if (options.peaks && peak > 0) {
            val row = (ceil(peak * FIRE_SEGMENTS).toInt() - 1).coerceIn(0, FIRE_SEGMENTS - 1)
            val y = (bottom - (row + 1) * step + gap).roundToInt().toFloat()
            barMesh.rect(x, y, end, y + max(1f, density).coerceAtMost(step - gap), peakColor, peakColor)
        }
    }

    private class BarMesh {
        private val capacity = SpectrumAnalyzer.BAND_COUNT * (FIRE_SEGMENTS + 3)
        private val vertices = FloatArray(capacity * 8)
        private val colors = IntArray(capacity * 4)
        private val indices = ShortArray(capacity * 6) { index ->
            (index / 6 * 4 + when (index % 6) { 0, 3 -> 0; 1 -> 1; 2, 4 -> 2; else -> 3 }).toShort()
        }
        private val paint = Paint().apply { color = android.graphics.Color.WHITE; isAntiAlias = false }
        private var count = 0

        fun clear() { count = 0 }

        fun gradient(left: Float, top: Float, right: Float, bottom: Float, barTop: Float,
            barBottom: Float, palette: SpectrumColors,
        ) {
            if (bottom <= top || barBottom <= barTop) return
            val middle = (barTop + barBottom) / 2
            val upper = palette.colorAt((barBottom - top) / (barBottom - barTop)) or 0xff000000.toInt()
            val lower = palette.colorAt((barBottom - bottom) / (barBottom - barTop)) or 0xff000000.toInt()
            if (top < middle && bottom > middle) {
                val center = palette.middle or 0xff000000.toInt()
                rect(left, top, right, middle, upper, center)
                rect(left, middle, right, bottom, center, lower)
            } else rect(left, top, right, bottom, upper, lower)
        }

        fun rect(left: Float, top: Float, right: Float, bottom: Float, upper: Int, lower: Int) {
            if (bottom <= top || right <= left) return
            check(count < capacity)
            val offset = count * 8
            vertices[offset] = left
            vertices[offset + 1] = top
            vertices[offset + 2] = right
            vertices[offset + 3] = top
            vertices[offset + 4] = right
            vertices[offset + 5] = bottom
            vertices[offset + 6] = left
            vertices[offset + 7] = bottom
            colors[count * 4] = upper
            colors[count * 4 + 1] = upper
            colors[count * 4 + 2] = lower
            colors[count * 4 + 3] = lower
            count++
        }

        fun draw(canvas: Canvas) {
            if (count > 0) {
                // One batch on API 29+ canvases.
                canvas.drawVertices(Canvas.VertexMode.TRIANGLES, count * 8, vertices, 0,
                    null, 0, colors, 0, indices, 0, count * 6, paint)
            }
        }
    }

    private fun colors(top: Float, bottom: Float): LinearGradient {
        val palette = when (options.palette) {
            SpectrumPalette.CLASSIC -> options.classicColors
            SpectrumPalette.FIRE -> options.fireColors
            SpectrumPalette.THEME -> return LinearGradient(0f, top, 0f, bottom, accent, accent, Shader.TileMode.CLAMP)
        }
        val colors = intArrayOf(palette.high, palette.middle, palette.low)
            .map { 0xff000000.toInt() or it }.toIntArray()
        return LinearGradient(0f, top, 0f, bottom, colors, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }

    private companion object { const val FIRE_SEGMENTS = 20 }
}
