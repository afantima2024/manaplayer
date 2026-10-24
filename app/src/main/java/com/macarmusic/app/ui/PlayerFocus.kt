package com.macarmusic.app.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import kotlin.math.roundToInt

internal fun View.playerFocusForeground(border: Drawable? = null): Drawable {
    val density = resources.displayMetrics.density
    val focus = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused),
            InsetDrawable(GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = 6 * density
                setStroke((2 * density).roundToInt().coerceAtLeast(1),
                    AccentTheme.color(this@playerFocusForeground, com.google.android.material.R.attr.colorPrimary))
            }, (2 * density).roundToInt()))
        addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
    }
    return if (border == null) focus else LayerDrawable(arrayOf(border, focus))
}
