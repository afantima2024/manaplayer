package com.macarmusic.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton

class PlayerTextButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    style: Int = com.google.android.material.R.attr.materialButtonStyle,
) : MaterialButton(context, attrs, style) {
    private val balance = ColorDrawable(Color.TRANSPARENT)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        balanceIcon()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        balanceIcon()
    }

    private fun balanceIcon() {
        val leading = icon
        val labeled = leading != null && text.isNotEmpty()
        val drawables = compoundDrawablesRelative
        if (!labeled) {
            if (drawables[2] === balance) setCompoundDrawablesRelative(drawables[0], null, null, null)
            return
        }
        if (iconGravity != ICON_GRAVITY_START) iconGravity = ICON_GRAVITY_START
        val size = (iconSize.takeIf { it > 0 } ?: requireNotNull(leading).intrinsicWidth).coerceAtLeast(0)
        val resized = balance.bounds.width() != size
        balance.setBounds(0, 0, size, 1)
        // Equal compound space keeps the label centered independently of the leading icon.
        if (resized || drawables[0] !== leading || drawables[2] !== balance)
            setCompoundDrawablesRelative(leading, null, balance, null)
    }
}
