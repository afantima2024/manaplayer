package com.macarmusic.app.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton

class PlayerIconButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatImageButton(context, attrs) {
    override fun onDraw(canvas: Canvas) {
        if (!isSelected) return super.onDraw(canvas)
        val saved = canvas.save()
        canvas.scale(1.5f, 1.5f, width / 2f, height / 2f)
        super.onDraw(canvas)
        canvas.restoreToCount(saved)
    }
}
