package com.macarmusic.app.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton

internal const val SELECTED_CONTROL_SCALE = 1.2f

class PlayerIconButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatImageButton(context, attrs) {
    override fun onDraw(canvas: Canvas) {
        if (!isSelected) return super.onDraw(canvas)
        val saved = canvas.save()
        canvas.scale(SELECTED_CONTROL_SCALE, SELECTED_CONTROL_SCALE, width / 2f, height / 2f)
        super.onDraw(canvas)
        canvas.restoreToCount(saved)
    }
}
