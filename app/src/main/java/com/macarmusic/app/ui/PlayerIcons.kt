package com.macarmusic.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import com.google.android.material.button.MaterialButton
import com.macarmusic.app.MaCarMusicApp
import com.macarmusic.app.R
import com.macarmusic.app.model.ButtonIconStyle

object PlayerIcons {
    private class Applied(val resource: Int, val style: ButtonIconStyle, val color: Int, val drawable: Drawable)
    private val colors = mapOf(
        R.drawable.ic_play to R.drawable.ic_play_color,
        R.drawable.ic_pause to R.drawable.ic_pause_color,
        R.drawable.ic_stop to R.drawable.ic_stop_color,
        R.drawable.ic_previous to R.drawable.ic_previous_color,
        R.drawable.ic_next to R.drawable.ic_next_color,
        R.drawable.ic_rewind to R.drawable.ic_rewind_color,
        R.drawable.ic_forward to R.drawable.ic_forward_color,
        R.drawable.ic_shuffle to R.drawable.ic_shuffle_color,
        R.drawable.ic_repeat to R.drawable.ic_repeat_color,
        R.drawable.ic_repeat_one to R.drawable.ic_repeat_one_color,
        R.drawable.ic_volume_on to R.drawable.ic_volume_on_color,
        R.drawable.ic_volume_off to R.drawable.ic_volume_off_color,
        R.drawable.ic_favorite to R.drawable.ic_favorite_color,
        R.drawable.ic_favorite_border to R.drawable.ic_favorite_border_color,
        R.drawable.ic_pin to R.drawable.ic_pin_color,
        R.drawable.ic_radio to R.drawable.ic_radio_color,
        R.drawable.ic_folder to R.drawable.ic_folder_color,
        R.drawable.ic_library to R.drawable.ic_library_color,
        R.drawable.ic_playlist to R.drawable.ic_playlist_color,
        R.drawable.ic_queue to R.drawable.ic_queue_color,
        R.drawable.ic_note to R.drawable.ic_note_color,
        R.drawable.ic_lyrics_next to R.drawable.ic_lyrics_next_color,
        R.drawable.ic_lyrics_timing to R.drawable.ic_lyrics_timing_color,
        R.drawable.ic_lyrics_versions to R.drawable.ic_lyrics_versions_color,
        R.drawable.ic_info to R.drawable.ic_info_color,
        R.drawable.ic_more to R.drawable.ic_more_color,
        R.drawable.ic_settings to R.drawable.ic_settings_color,
        R.drawable.ic_close to R.drawable.ic_close_color,
        R.drawable.ic_back to R.drawable.ic_back_color,
        R.drawable.ic_expand_less to R.drawable.ic_expand_less_color,
        R.drawable.ic_expand_more to R.drawable.ic_expand_more_color,
    )
    private val staticIcons = mapOf(
        R.id.previous to R.drawable.ic_previous, R.id.next to R.drawable.ic_next,
        R.id.rewind to R.drawable.ic_rewind, R.id.forward to R.drawable.ic_forward,
        R.id.shuffle to R.drawable.ic_shuffle, R.id.miniNext to R.drawable.ic_next,
        R.id.details to R.drawable.ic_info, R.id.dismissNotice to R.drawable.ic_close,
        R.id.more to R.drawable.ic_more, R.id.lyricVersions to R.drawable.ic_lyrics_versions,
        R.id.nextLyrics to R.drawable.ic_lyrics_next, R.id.lyricsTiming to R.drawable.ic_lyrics_timing,
        com.google.android.material.R.id.text_input_end_icon to R.drawable.ic_close,
    )

    private fun style(context: Context) =
        (context.applicationContext as MaCarMusicApp).displaySettings.options.value.buttonIcons

    fun tint(context: Context, enabled: Int): ColorStateList {
        val surface = AccentTheme.color(context, com.google.android.material.R.attr.colorSurface)
        val foreground = AccentTheme.color(context, com.google.android.material.R.attr.colorOnSurface)
        val mixed = ColorUtils.blendARGB(surface, foreground, 0.38f)
        val grey = (Color.red(mixed) * 0.299 + Color.green(mixed) * 0.587 + Color.blue(mixed) * 0.114).toInt()
        return ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(Color.rgb(grey, grey, grey), enabled))
    }

    fun drawable(context: Context, @DrawableRes resource: Int,
        iconStyle: ButtonIconStyle = style(context),
        color: Int = AccentTheme.color(context, com.google.android.material.R.attr.colorPrimary)): Drawable {
        if (iconStyle == ButtonIconStyle.MONOCHROME || resource !in colors) {
            return requireNotNull(AppCompatResources.getDrawable(context, resource)).mutate().apply {
                setTintList(tint(context, color))
            }
        }
        val disabled = requireNotNull(AppCompatResources.getDrawable(context, resource)).mutate().apply {
            setTint(tint(context, color).getColorForState(intArrayOf(-android.R.attr.state_enabled), color))
        }
        val overlay = when (iconStyle) {
            ButtonIconStyle.OCEAN -> R.style.ThemeOverlay_ManaPlayer_Icons_Ocean
            ButtonIconStyle.AURORA -> R.style.ThemeOverlay_ManaPlayer_Icons_Aurora
            ButtonIconStyle.SUNSET -> R.style.ThemeOverlay_ManaPlayer_Icons_Sunset
            ButtonIconStyle.ORCHID -> R.style.ThemeOverlay_ManaPlayer_Icons_Orchid
            ButtonIconStyle.LOGO -> R.style.ThemeOverlay_ManaPlayer_Icons_Logo
            ButtonIconStyle.COPILOT_AURORA -> R.style.ThemeOverlay_ManaPlayer_Icons_CopilotAurora
            ButtonIconStyle.COPILOT_SUNRISE -> R.style.ThemeOverlay_ManaPlayer_Icons_CopilotSunrise
            else -> R.style.ThemeOverlay_ManaPlayer_Icons_Original
        }
        val enabled = requireNotNull(AppCompatResources.getDrawable(ContextThemeWrapper(context, overlay),
            colors.getValue(resource))).mutate()
        return StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), disabled)
            addState(intArrayOf(), enabled)
        }
    }

    fun image(view: ImageView, @DrawableRes resource: Int,
        color: Int = AccentTheme.color(view, com.google.android.material.R.attr.colorPrimary),
        iconStyle: ButtonIconStyle = style(view.context)) {
        val previous = view.getTag(R.id.playerIconSpec) as? Applied
        if (previous != null && previous.resource == resource && previous.style == iconStyle &&
            previous.color == color && previous.drawable === view.drawable) return
        val drawable = drawable(view.context, resource, iconStyle, color)
        val colorful = iconStyle != ButtonIconStyle.MONOCHROME && resource in colors
        ImageViewCompat.setImageTintMode(view, if (colorful) PorterDuff.Mode.MULTIPLY else PorterDuff.Mode.SRC_IN)
        ImageViewCompat.setImageTintList(view, tint(view.context, if (colorful) Color.WHITE else color))
        view.setImageDrawable(drawable)
        view.setTag(R.id.playerIconSpec, Applied(resource, iconStyle, color, drawable))
    }

    fun button(view: MaterialButton, @DrawableRes resource: Int,
        color: Int = AccentTheme.color(view, com.google.android.material.R.attr.colorPrimary),
        iconStyle: ButtonIconStyle = style(view.context)) {
        val previous = view.getTag(R.id.playerIconSpec) as? Applied
        if (previous != null && previous.resource == resource && previous.style == iconStyle &&
            previous.color == color && previous.drawable === view.icon) return
        val drawable = drawable(view.context, resource, iconStyle, color)
        val colorful = iconStyle != ButtonIconStyle.MONOCHROME && resource in colors
        view.iconTintMode = if (colorful) PorterDuff.Mode.MULTIPLY else PorterDuff.Mode.SRC_IN
        view.iconTint = tint(view.context, if (colorful) Color.WHITE else color)
        view.icon = drawable
        view.setTag(R.id.playerIconSpec, Applied(resource, iconStyle, color, drawable))
    }

    fun refresh(root: View) {
        val previous = root.getTag(R.id.playerIconSpec) as? Applied
        val resource = previous?.resource ?: staticIcons[root.id]
        if (resource != null) {
            val color = previous?.color ?: AccentTheme.color(root, com.google.android.material.R.attr.colorPrimary)
            when (root) {
                is ImageView -> image(root, resource, color)
                is MaterialButton -> button(root, resource, color)
            }
        }
        if (root is ViewGroup) for (index in 0 until root.childCount) refresh(root.getChildAt(index))
    }
}
