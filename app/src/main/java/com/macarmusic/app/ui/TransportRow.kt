package com.macarmusic.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.children
import com.macarmusic.app.R
import kotlin.math.ceil

class TransportRow @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    var buttonHeightDp: Int = 64
    private val gap get() = resources.getDimensionPixelSize(R.dimen.player_gap)
    private var columns = 1
    private var cellWidth = 0
    private var cellHeight = 0
    internal var buttonGapPx = 0
        private set

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val buttons = children.filter { it.visibility != GONE }.toList()
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val available = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        buttonGapPx = if (buttons.size > 1 && available >= buttons.size * dp(48))
            minOf(gap, (available - buttons.size * dp(48)) / (buttons.size - 1)) else gap
        val capacity = ((available + buttonGapPx) / (dp(48) + buttonGapPx)).coerceAtLeast(1)
        val rows = ceil(buttons.size.toDouble() / capacity).toInt().coerceAtLeast(1)
        columns = ceil(buttons.size.toDouble() / rows).toInt().coerceAtLeast(1)
        cellWidth = ((available - (columns - 1) * buttonGapPx) / columns).coerceAtLeast(0)
        cellHeight = dp(buttonHeightDp)
        buttons.forEach { it.measure(MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(cellHeight, MeasureSpec.EXACTLY)) }
        setMeasuredDimension(resolveSize(width, widthMeasureSpec),
            resolveSize(rows * cellHeight + (rows - 1) * buttonGapPx + paddingTop + paddingBottom, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val buttons = children.filter { it.visibility != GONE }.toList()
        buttons.forEachIndexed { index, button ->
            val row = index / columns
            val count = minOf(columns, buttons.size - row * columns)
            val start = paddingLeft + (width - paddingLeft - paddingRight - count * cellWidth - (count - 1) * buttonGapPx) / 2
            val x = start + (index % columns) * (cellWidth + buttonGapPx)
            val y = paddingTop + row * (cellHeight + buttonGapPx)
            button.layout(x, y, x + cellWidth, y + cellHeight)
        }
    }

    override fun focusSearch(focused: View, direction: Int): View? {
        val index = indexOfChild(focused)
        if (index >= 0 && (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT)) {
            val step = if (direction == View.FOCUS_LEFT) -1 else 1
            var next = index + step
            while (next in 0 until childCount) {
                val candidate = getChildAt(next)
                if (candidate.visibility == VISIBLE) {
                    if (candidate.top != focused.top) break
                    if (candidate.isEnabled && candidate.isFocusable) return candidate
                }
                next += step
            }
        }
        return super.focusSearch(focused, direction)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
