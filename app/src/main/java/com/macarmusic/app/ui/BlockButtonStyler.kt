package com.macarmusic.app.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
import android.widget.ImageButton
import androidx.core.view.children
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.macarmusic.app.model.BlockOutline
import com.macarmusic.app.model.BlockStyle
import com.macarmusic.app.R

class BlockButtonStyler(root: ViewGroup) {
    private class Original(val view: View) {
        val background = view.background
        val foreground = view.foreground
        val padding = Rect(view.paddingStart, view.paddingTop, view.paddingEnd, view.paddingBottom)
        val button = view as? MaterialButton
        val shape = button?.shapeAppearanceModel
        val tint = button?.backgroundTintList
        val stroke = button?.strokeWidth
        val insetTop = button?.insetTop
        val insetBottom = button?.insetBottom
        val margins = view.layoutParams as ViewGroup.MarginLayoutParams
        val topMargin = margins.topMargin
        val bottomMargin = margins.bottomMargin
    }

    private val controls = buildList {
        fun collect(view: View) {
            if (view is MaterialButton || view is Spinner || view is ImageButton) add(Original(view))
            else if (view is ViewGroup) view.children.forEach(::collect)
        }
        collect(root)
    }
    private var applied: Pair<Boolean, BlockOutline>? = null
    private val density = root.resources.displayMetrics.density
    private val color = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOutline)
    private val ripple = ColorStateList.valueOf(
        MaterialColors.getColor(root, com.google.android.material.R.attr.colorControlHighlight))

    fun apply(enabled: Boolean, outline: BlockOutline) {
        if (applied == (enabled to outline)) return
        val framed = enabled && outline.hasBorder
        val inset = ((if (outline.style == BlockStyle.CHARACTERS) 12 else 8) * density).toInt()
        for (original in controls) {
            val view = original.view
            val border = if (framed) PlayerBlockDrawable(outline, color, density) else null
            val button = original.button
            if (button != null) {
                button.strokeWidth = if (framed) 0 else requireNotNull(original.stroke)
                button.backgroundTintList = if (framed) ColorStateList.valueOf(Color.TRANSPARENT) else original.tint
                button.shapeAppearanceModel = if (framed) requireNotNull(original.shape).toBuilder()
                    .setAllCornerSizes((if (outline.hasArc) outline.arcDp else 6) * density).build()
                    else requireNotNull(original.shape)
                button.insetTop = if (framed) 0 else requireNotNull(original.insetTop)
                button.insetBottom = if (framed) 0 else requireNotNull(original.insetBottom)
            } else if (view is Spinner) {
                view.background = if (framed) {
                    val arrow = requireNotNull(AppCompatResources.getDrawable(view.context, R.drawable.ic_expand_more)).mutate()
                    arrow.setTint(AccentTheme.color(view, com.google.android.material.R.attr.colorPrimary))
                    val indicator = LayerDrawable(arrayOf(arrow)).apply {
                        setLayerSize(0, (24 * density).toInt(), (24 * density).toInt())
                        setLayerGravity(0, Gravity.END or Gravity.CENTER_VERTICAL)
                        setLayerInsetRelative(0, 0, 0, inset, 0)
                    }
                    RippleDrawable(ripple, indicator, ColorDrawable(Color.WHITE))
                } else original.background
            }
            view.foreground = border ?: original.foreground
            val padding = original.padding
            view.setPaddingRelative(
                if (framed) maxOf(padding.left, inset) else padding.left,
                if (framed) maxOf(padding.top, inset) else padding.top,
                if (framed) maxOf(padding.right, inset + if (view is Spinner) (24 * density).toInt() else 0) else padding.right,
                if (framed) maxOf(padding.bottom, inset) else padding.bottom,
            )
            val spacing = if (view is ImageButton) 0 else (4 * density).toInt()
            original.margins.topMargin = if (framed) maxOf(original.topMargin, spacing) else original.topMargin
            original.margins.bottomMargin = if (framed) maxOf(original.bottomMargin, spacing) else original.bottomMargin
            view.layoutParams = original.margins
        }
        applied = enabled to outline
    }
}
