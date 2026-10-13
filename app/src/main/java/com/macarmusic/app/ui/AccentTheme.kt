package com.macarmusic.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
import com.macarmusic.app.MaCarMusicApp
import com.macarmusic.app.R
import com.macarmusic.app.model.ThemeAccent

object AccentTheme {
    private val overlays = mapOf(
        ThemeAccent.BLUE to R.style.ThemeOverlay_ManaPlayer_Accent_Blue,
        ThemeAccent.INDIGO to R.style.ThemeOverlay_ManaPlayer_Accent_Indigo,
        ThemeAccent.PURPLE to R.style.ThemeOverlay_ManaPlayer_Accent_Purple,
        ThemeAccent.PINK to R.style.ThemeOverlay_ManaPlayer_Accent_Pink,
        ThemeAccent.RED to R.style.ThemeOverlay_ManaPlayer_Accent_Red,
        ThemeAccent.ORANGE to R.style.ThemeOverlay_ManaPlayer_Accent_Orange,
        ThemeAccent.GREEN to R.style.ThemeOverlay_ManaPlayer_Accent_Green,
        ThemeAccent.TEAL to R.style.ThemeOverlay_ManaPlayer_Accent_Teal,
    )
    private val colors = mapOf(
        ThemeAccent.DEFAULT to R.color.primary,
        ThemeAccent.BLUE to R.color.accent_blue,
        ThemeAccent.INDIGO to R.color.accent_indigo,
        ThemeAccent.PURPLE to R.color.accent_purple,
        ThemeAccent.PINK to R.color.accent_pink,
        ThemeAccent.RED to R.color.accent_red,
        ThemeAccent.ORANGE to R.color.accent_orange,
        ThemeAccent.GREEN to R.color.accent_green,
        ThemeAccent.TEAL to R.color.accent_teal,
    )

    fun apply(context: Context, accent: ThemeAccent) {
        overlays[accent]?.let { context.theme.applyStyle(it, true) }
    }

    fun swatches(context: Context): Map<ThemeAccent, Int> =
        colors.mapValues { context.getColor(it.value) and 0xffffff }

    fun color(view: View, attribute: Int): Int {
        val custom = customColor(view.context) ?: return MaterialColors.getColor(view, attribute)
        return when (attribute) {
            com.google.android.material.R.attr.colorPrimary,
            com.google.android.material.R.attr.colorSecondary -> custom
            com.google.android.material.R.attr.colorOnPrimary,
            com.google.android.material.R.attr.colorOnSecondary -> contrasting(custom)
            com.google.android.material.R.attr.colorPrimaryContainer,
            com.google.android.material.R.attr.colorSecondaryContainer -> ColorUtils.blendARGB(
                MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurface), custom, 0.18f)
            com.google.android.material.R.attr.colorOnPrimaryContainer,
            com.google.android.material.R.attr.colorOnSecondaryContainer ->
                MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface)
            else -> MaterialColors.getColor(view, attribute)
        }
    }

    // Public tint APIs support older Android versions without runtime resource overlays.
    fun tintControls(root: View) {
        val primary = customColor(root.context) ?: return
        val surface = MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurface)
        val onSurface = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurface)
        val nativePrimary = MaterialColors.getColor(root, com.google.android.material.R.attr.colorPrimary)
        val enabledState = intArrayOf(android.R.attr.state_enabled)
        val disabled = ColorUtils.blendARGB(surface, onSurface, 0.38f)
        fun enabled(value: Int) = ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(disabled, value))
        fun checked(selected: Int, other: Int) = ColorStateList(arrayOf(
            intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(disabled, selected, other))
        val foreground = enabled(primary)
        val ripple = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 48))
        fun visit(view: View) {
            when (view) {
                is MaterialButton -> {
                    val category = SettingsCategory.pages.any { it.buttonId == view.id }
                    val filled = view.backgroundTintList?.getColorForState(enabledState, 0)
                        .let { it == nativePrimary || it == primary }
                    view.setTextColor(if (category) checked(primary, onSurface)
                        else if (filled) enabled(contrasting(primary)) else foreground)
                    if (view.iconTint != null) view.iconTint = if (filled) enabled(contrasting(primary)) else foreground
                    if (filled) view.backgroundTintList = foreground
                    if (category) view.strokeColor = checked(primary, Color.TRANSPARENT)
                    view.rippleColor = ripple
                }
                is MaterialSwitch -> {
                    view.trackTintList = checked(primary,
                        MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceVariant))
                    view.thumbTintList = checked(contrasting(primary), onSurface)
                }
                is CompoundButton -> view.buttonTintList = checked(primary, onSurface)
                is CheckedTextView -> view.checkMarkTintList = checked(primary, onSurface)
                is SeekBar -> {
                    view.thumbTintList = foreground
                    view.progressTintList = foreground
                    view.progressBackgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 64))
                }
                is TextInputLayout -> {
                    view.hintTextColor = foreground
                    view.boxStrokeColor = primary
                }
                is Spinner -> view.backgroundTintList = foreground
                is EditText -> {
                    view.backgroundTintList = foreground
                    if (Build.VERSION.SDK_INT >= 29) view.textCursorDrawable?.setTint(primary)
                }
                is TextView -> if (view.textColors.getColorForState(enabledState, 0) == nativePrimary)
                    view.setTextColor(foreground)
                is ImageView -> if (view.imageTintList?.getColorForState(enabledState, 0) == nativePrimary)
                    view.imageTintList = foreground
            }
            if (view.id == R.id.lyricDragLine) view.setBackgroundColor(primary)
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(root)
    }

    fun dialog(context: Context): MaterialAlertDialogBuilder = object : MaterialAlertDialogBuilder(context) {
        override fun create(): AlertDialog = super.create().also { dialog ->
            if (customColor(context) != null) dialog.window?.decorView?.doOnLayout { tintControls(it) }
        }
    }

    private fun customColor(context: Context): Int? =
        (context.applicationContext as? MaCarMusicApp)?.displaySettings?.options?.value?.customAccentColor?.or(Color.BLACK)

    private fun contrasting(color: Int): Int =
        if (ColorUtils.calculateContrast(Color.WHITE, color) >= ColorUtils.calculateContrast(Color.BLACK, color))
            Color.WHITE else Color.BLACK
}
