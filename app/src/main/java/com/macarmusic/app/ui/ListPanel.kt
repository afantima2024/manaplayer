package com.macarmusic.app.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.macarmusic.app.R

class ListPanel(context: Context) : LinearLayout(context) {
    val top = LinearLayout(context).apply { orientation = VERTICAL }
    val heading = TextView(context).apply {
        textSize = 16f
        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
        setPadding(dp(16), dp(8), dp(16), dp(8))
    }
    val list = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context)
        clipToPadding = false
        setPadding(0, 0, 0, dp(12))
        itemAnimator = null
    }
    val empty = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 18f
        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
        setPadding(dp(28), dp(16), dp(28), dp(16))
    }

    init {
        orientation = VERTICAL
        addView(top, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(heading, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(FrameLayout(context).apply {
            addView(list, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(empty, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun actionRow(vararg buttons: View): View {
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            buttons.forEach { button ->
                addView(button, LayoutParams(LayoutParams.WRAP_CONTENT, dp(56)).apply { marginEnd = dp(8) })
            }
        }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
            this@ListPanel.top.addView(this)
        }
    }

    fun action(label: Int, click: () -> Unit): MaterialButton =
        MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            setText(label)
            isAllCaps = false
            minHeight = dp(56)
            setOnClickListener { click() }
        }

    fun empty(message: CharSequence, visible: Boolean) {
        empty.text = message
        empty.isVisible = visible
        list.isVisible = !visible
    }

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()
}
