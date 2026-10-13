package com.macarmusic.app.ui

import android.content.Context
import android.graphics.Matrix
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomImageView(context: Context) : AppCompatImageView(context) {
    var zoom = 1f
        private set
    private var fit = 1f
    private var xOffset = 0f
    private var yOffset = 0f
    private val transform = Matrix()
    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomTo(zoom * detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent) = true
        override fun onDoubleTap(event: MotionEvent): Boolean {
            zoomTo(if (zoom > 1f) 1f else 2f, event.x, event.y)
            return true
        }
        override fun onScroll(first: MotionEvent?, current: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!scaler.isInProgress) {
                xOffset -= distanceX
                yOffset -= distanceY
                updateMatrix()
            }
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
        isClickable = true
        isFocusable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val image = drawable ?: return
        if (image.intrinsicWidth <= 0 || image.intrinsicHeight <= 0) return
        fit = minOf(w.toFloat() / image.intrinsicWidth, h.toFloat() / image.intrinsicHeight)
        zoom = 1f
        xOffset = 0f
        yOffset = 0f
        updateMatrix()
    }

    fun zoomTo(value: Float, focusX: Float = width / 2f, focusY: Float = height / 2f) {
        require(value.isFinite())
        val next = value.coerceIn(1f, 8f)
        val ratio = next / zoom
        xOffset = focusX - (focusX - xOffset) * ratio
        yOffset = focusY - (focusY - yOffset) * ratio
        zoom = next
        updateMatrix()
    }

    private fun updateMatrix() {
        val image = drawable ?: return
        val scale = fit * zoom
        val w = image.intrinsicWidth * scale
        val h = image.intrinsicHeight * scale
        xOffset = if (w <= width) (width - w) / 2 else xOffset.coerceIn(width - w, 0f)
        yOffset = if (h <= height) (height - h) / 2 else yOffset.coerceIn(height - h, 0f)
        transform.setScale(scale, scale)
        transform.postTranslate(xOffset, yOffset)
        imageMatrix = transform
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaler.onTouchEvent(event)
        gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
