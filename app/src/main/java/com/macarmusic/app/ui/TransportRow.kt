package com.macarmusic.app.ui

import android.content.Context
import android.util.AttributeSet
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val buttons = children.filter { it.visibility != GONE }.toList()
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val available = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val capacity = ((available + gap) / (dp(48) + gap)).coerceAtLeast(1)
        val rows = ceil(buttons.size.toDouble() / capacity).toInt().coerceAtLeast(1)
        columns = ceil(buttons.size.toDouble() / rows).toInt().coerceAtLeast(1)
        cellWidth = ((available - (columns - 1) * gap) / columns).coerceAtLeast(0)
        cellHeight = dp(buttonHeightDp)
        buttons.forEach { it.measure(MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(cellHeight, MeasureSpec.EXACTLY)) }
        setMeasuredDimension(resolveSize(width, widthMeasureSpec),
            resolveSize(rows * cellHeight + (rows - 1) * gap + paddingTop + paddingBottom, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val buttons = children.filter { it.visibility != GONE }.toList()
        buttons.forEachIndexed { index, button ->
            val row = index / columns
            val count = minOf(columns, buttons.size - row * columns)
            val start = paddingLeft + (width - paddingLeft - paddingRight - count * cellWidth - (count - 1) * gap) / 2
            val x = start + (index % columns) * (cellWidth + gap)
            val y = paddingTop + row * (cellHeight + gap)
            button.layout(x, y, x + cellWidth, y + cellHeight)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
