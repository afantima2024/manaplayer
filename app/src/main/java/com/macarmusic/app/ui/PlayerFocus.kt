package com.macarmusic.app.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Spinner
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt

private class PlayerFocusOverlay(val decoration: Drawable?, focus: Drawable) :
    LayerDrawable(if (decoration == null) arrayOf(focus) else arrayOf(decoration, focus))

internal fun View.playerIndicatorColor(
    surface: Int = AccentTheme.color(this, com.google.android.material.R.attr.colorSurface),
): Int {
    val primary = AccentTheme.color(this, com.google.android.material.R.attr.colorPrimary)
    return if (ColorUtils.calculateContrast(primary, surface) >= 3) primary
        else AccentTheme.color(this, com.google.android.material.R.attr.colorOnSurface)
}

internal fun View.playerFocusForeground(border: Drawable? = null): Drawable {
    val density = resources.displayMetrics.density
    val surface = AccentTheme.color(this, com.google.android.material.R.attr.colorSurface)
    val color = playerIndicatorColor(surface)
    fun outline(width: Int, stroke: Int) = GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        cornerRadius = 6 * density
        setStroke((width * density).roundToInt().coerceAtLeast(1), stroke)
    }
    val focus = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused),
            InsetDrawable(LayerDrawable(arrayOf(outline(6, surface), outline(3, color))),
                (3 * density).roundToInt()))
        addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
    }
    return PlayerFocusOverlay(if (border is PlayerFocusOverlay) border.decoration else border, focus)
}

internal fun View.installPlayerInputFocus() {
    if (this is CompoundButton) showPlayerInputFocus()
    else if (this is ViewGroup && this !is Spinner) {
        for (index in 0 until childCount) getChildAt(index).installPlayerInputFocus()
    } else if (isFocusable && foreground !is PlayerFocusOverlay) foreground = playerFocusForeground(foreground)
}

internal fun CompoundButton.showPlayerInputFocus(border: Drawable? = foreground) {
    foreground = if (border is PlayerFocusOverlay) border else playerFocusForeground(border)
    val inset = (10 * resources.displayMetrics.density).roundToInt()
    setPaddingRelative(maxOf(paddingStart, inset), paddingTop, maxOf(paddingEnd, inset), paddingBottom)
}
