package com.macarmusic.app.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.macarmusic.app.model.BlockOutline
import com.macarmusic.app.model.BlockStyle
import kotlin.math.ceil
import kotlin.math.max

class PlayerBlockDrawable(
    private val outline: BlockOutline,
    private val color: Int,
    private val density: Float,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = color }
    private val path = Path()
    private val box = RectF()
    private val measure = PathMeasure()
    private val point = FloatArray(2)
    private val dash = DashPathEffect(floatArrayOf(6 * density, 4 * density), 0f)

    override fun draw(canvas: Canvas) {
        val characters = outline.style == BlockStyle.CHARACTERS
        val inset = if (characters) 6 * density else density / 2
        box.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset)
        if (box.width() <= 0 || box.height() <= 0) return
        path.reset()
        val radius = (if (outline.style == BlockStyle.ARC) 18 else 6) * density
        path.addRoundRect(box, radius, radius, Path.Direction.CW)
        paint.strokeWidth = density
        paint.pathEffect = if (outline.style == BlockStyle.DASHED) dash else null
        if (!characters) {
            paint.style = Paint.Style.STROKE
            canvas.drawPath(path, paint)
        } else {
            paint.style = Paint.Style.FILL
            paint.textSize = 10 * density
            paint.textAlign = Paint.Align.CENTER
            measure.setPath(path, false)
            val count = ceil(measure.length / max(12 * density, paint.measureText(outline.character) + 3 * density))
                .toInt().coerceIn(1, 1_000)
            val baseline = -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2
            repeat(count) { index ->
                measure.getPosTan(index * measure.length / count, point, null)
                canvas.drawText(outline.character, point[0], point[1] + baseline, paint)
            }
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    override fun getConstantState(): ConstantState = object : ConstantState() {
        override fun newDrawable() = PlayerBlockDrawable(outline, color, density)
        override fun getChangingConfigurations() = 0
    }
}
