package com.macarmusic.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Checkable
import android.widget.ListView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.ColorUtils
import com.macarmusic.app.MaCarMusicApp

internal class PlayerDialogChoice @JvmOverloads constructor(context: Context, private val mode: Int = ListView.CHOICE_MODE_NONE) :
    AppCompatTextView(context), Checkable {
    private val density = resources.displayMetrics.density
    private val primary = AccentTheme.color(this, com.google.android.material.R.attr.colorPrimary)
    private val surface = ColorDrawable(android.graphics.Color.TRANSPARENT)
    private var checked = false
    private val mark = if (mode == ListView.CHOICE_MODE_NONE) null else ChoiceMark()

    init {
        val options = (context.applicationContext as MaCarMusicApp).displaySettings.options.value
        textSize = 18f
        minHeight = (56 * density).toInt()
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setTextColor(AccentTheme.color(this, com.google.android.material.R.attr.colorOnSurface))
        setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
        compoundDrawablePadding = (12 * density).toInt()
        setCompoundDrawablesRelativeWithIntrinsicBounds(mark, null, null, null)
        background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 48)), surface, null)
        foreground = if (options.controlBlocks) PlayerBlockDrawable(options.blockOutline,
            AccentTheme.color(this, com.google.android.material.R.attr.colorOutline), density) else null
    }

    override fun isChecked() = checked
    override fun toggle() = setChecked(!checked)
    override fun setChecked(value: Boolean) {
        if (checked == value) return
        checked = value
        isSelected = value
        surface.color = if (value) AccentTheme.color(this, com.google.android.material.R.attr.colorPrimaryContainer)
            else android.graphics.Color.TRANSPARENT
        mark?.invalidateSelf()
        refreshDrawableState()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isCheckable = mode != ListView.CHOICE_MODE_NONE
        info.isChecked = checked
    }

    private inner class ChoiceMark : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primary }
        private val tick = Path().apply { moveTo(6f, 12f); lineTo(10f, 16f); lineTo(18f, 8f) }
        override fun getIntrinsicWidth() = (24 * density + 0.5f).toInt()
        override fun getIntrinsicHeight() = intrinsicWidth
        override fun draw(canvas: Canvas) {
            val saved = canvas.save()
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            if (mode == ListView.CHOICE_MODE_SINGLE) {
                canvas.drawCircle(12f, 12f, 9f, paint)
                if (checked) {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(12f, 12f, 5f, paint)
                }
            } else {
                canvas.drawRoundRect(3f, 3f, 21f, 21f, 3f, 3f, paint)
                if (checked) canvas.drawPath(tick, paint)
            }
            canvas.restoreToCount(saved)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
        @Deprecated("Drawable opacity is not used")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
