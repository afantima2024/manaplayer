package com.macarmusic.app.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.drawable.Drawable
import com.macarmusic.app.model.BlockOutline
import com.macarmusic.app.model.BlockStyle
import kotlin.math.ceil
import kotlin.math.max

class PlayerBlockDrawable(
    private val outline: BlockOutline,
    private val color: Int,
    private val density: Float,
    private val divider: Boolean = false,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = color }
    private val path = Path()
    private val shadowPath = Path()
    private val box = RectF()
    private val measure = PathMeasure()
    private val point = FloatArray(2)
    private val dash = DashPathEffect(floatArrayOf(6 * density, 4 * density), 0f)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(Color.red(this@PlayerBlockDrawable.color) / 3,
            Color.green(this@PlayerBlockDrawable.color) / 3, Color.blue(this@PlayerBlockDrawable.color) / 3)
        style = Paint.Style.FILL
    }
    private var characterPoints = FloatArray(0)
    private var characterCount = 0
    private var baseline = 0f

    override fun onBoundsChange(bounds: Rect) {
        if (!outline.hasBorder) return
        val characters = outline.style == BlockStyle.CHARACTERS
        val inset = if (characters) 6 * density else density / 2
        val shadow = if (outline.shadow && !divider) 4 * density else 0f
        box.set(bounds.left + inset, bounds.top + inset, bounds.right - inset - shadow, bounds.bottom - inset - shadow)
        path.reset()
        shadowPath.reset()
        if (box.width() <= 0 || box.height() <= 0) return
        val radius = minOf((if (outline.hasArc) outline.arcDp else 6) * density, box.width() / 2, box.height() / 2)
        if (divider) {
            val bend = if (outline.hasArc) minOf(outline.arcDp * density, box.height() / 2) else 0f
            path.moveTo(box.left, box.centerY() - bend)
            path.quadTo(box.left, box.centerY(), box.left + bend, box.centerY())
            path.lineTo(box.right - bend, box.centerY())
            path.quadTo(box.right, box.centerY(), box.right, box.centerY() - bend)
        } else {
            path.addRoundRect(box, radius, radius, Path.Direction.CW)
            if (outline.shadow) {
                shadowPath.set(path)
                shadowPath.offset(shadow, shadow)
                shadowPath.op(path, Path.Op.DIFFERENCE)
            }
        }
        paint.strokeWidth = density
        paint.pathEffect = if (outline.style == BlockStyle.DASHED || outline.style == BlockStyle.DASHED_ARC) dash else null
        if (characters) {
            paint.textSize = 10 * density
            paint.textAlign = Paint.Align.CENTER
            measure.setPath(path, false)
            characterCount = ceil(measure.length / max(12 * density, paint.measureText(outline.character) + 3 * density))
                .toInt().coerceIn(1, 1_000)
            if (characterPoints.size < characterCount * 2) characterPoints = FloatArray(characterCount * 2)
            baseline = -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2
            repeat(characterCount) { index ->
                measure.getPosTan(index * measure.length / characterCount, point, null)
                characterPoints[index * 2] = point[0]
                characterPoints[index * 2 + 1] = point[1]
            }
        }
    }

    override fun draw(canvas: Canvas) {
        if (!outline.hasBorder || box.width() <= 0 || box.height() <= 0) return
        if (outline.shadow && !divider) canvas.drawPath(shadowPath, shadowPaint)
        val characters = outline.style == BlockStyle.CHARACTERS
        if (!characters) {
            paint.style = Paint.Style.STROKE
            canvas.drawPath(path, paint)
        } else {
            paint.style = Paint.Style.FILL
            repeat(characterCount) { index ->
                canvas.drawText(outline.character, characterPoints[index * 2], characterPoints[index * 2 + 1] + baseline, paint)
            }
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; shadowPaint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        shadowPaint.colorFilter = colorFilter
        invalidateSelf()
    }
    @Suppress("DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    override fun getConstantState(): ConstantState = object : ConstantState() {
        override fun newDrawable() = PlayerBlockDrawable(outline, color, density, divider)
        override fun getChangingConfigurations() = 0
    }
}
