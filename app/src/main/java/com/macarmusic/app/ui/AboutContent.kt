package com.macarmusic.app.ui

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.text.inSpans
import androidx.core.view.ViewCompat
import com.google.android.material.color.MaterialColors
import com.macarmusic.app.R
import com.macarmusic.app.lyrics.LyricSearchRoute
import com.macarmusic.app.lyrics.OnlineLyricsClient
import com.macarmusic.app.model.BlockOutline

object AboutContent {
    private fun paragraphs(context: Context, vararg resources: Int) = SpannableStringBuilder().apply {
        resources.forEachIndexed { index, resource ->
            if (index > 0) append("\n\n")
            append(context.getText(resource))
        }
    }

    private fun SpannableStringBuilder.section(context: Context, title: Int, body: CharSequence) {
        if (isNotEmpty()) append("\n\n")
        inSpans(StyleSpan(Typeface.BOLD), RelativeSizeSpan(1.25f)) {
            append(context.getText(title))
        }
        append("\n")
        append(body)
    }

    private fun details(context: Context, vararg sections: Pair<Int, Int>) = SpannableStringBuilder().apply {
        sections.forEach { (title, body) -> section(context, title, context.getText(body)) }
    }

    private fun settingsHelp(context: Context) = details(context,
        R.string.autoplay_on_launch to R.string.autoplay_on_launch_help,
        R.string.keep_screen_on to R.string.keep_screen_on_help,
        R.string.notice_duration to R.string.notice_duration_help,
        R.string.recovery_details to R.string.recovery_help,
        R.string.directional_browsing to R.string.directional_browsing_help,
        R.string.mini_player_transparency to R.string.mini_player_transparency_help,
        R.string.navigation_buttons to R.string.navigation_buttons_help,
        R.string.radio_enabled to R.string.radio_enabled_help,
        R.string.appearance_logo to R.string.appearance_help,
        R.string.primary_control_color to R.string.primary_control_color_help,
        R.string.control_blocks to R.string.control_blocks_help,
        R.string.background_choose to R.string.background_help,
        R.string.background_interval to R.string.slideshow_help,
        R.string.home_widgets to R.string.widgets_help,
        R.string.backup_export to R.string.backup_help,
        R.string.category_playback to R.string.playback_settings_help,
        R.string.spectrum to R.string.spectrum_help,
        R.string.lyrics to R.string.lyrics_help,
    ).apply {
        val providers = OnlineLyricsClient.ROUTES.mapIndexed { index, provider ->
            context.getString(R.string.lyrics_provider_step, index + 1, context.getString(
                when (provider) {
                    LyricSearchRoute.KUGOU_DIRECT -> R.string.provider_kugou_direct
                    LyricSearchRoute.KUGOU_MOBILE -> R.string.provider_kugou_mobile
                    LyricSearchRoute.NETEASE -> R.string.provider_netease
                    LyricSearchRoute.QQ -> R.string.provider_qq
                    LyricSearchRoute.KUWO -> R.string.provider_kuwo
                },
            ))
        }.joinToString("\n")
        section(context, R.string.lyrics_provider_order_title, context.getString(R.string.lyrics_provider_order, providers))
        section(context, R.string.select_folders, context.getText(R.string.multiple_folders_help))
    }

    fun radioHelp(context: Context): CharSequence = paragraphs(context, R.string.radio_help,
        R.string.radio_subscription_help, R.string.radio_station_logos_help, R.string.radio_bundled_defaults_help).apply {
        append("\n\n")
        append(details(context,
            R.string.radio_refresh_interval to R.string.radio_refresh_help,
            R.string.radio_navigation to R.string.radio_navigation_help,
            R.string.radio_open_player to R.string.radio_open_player_help,
            R.string.radio_manage_groups to R.string.radio_groups_help,
            R.string.radio_buffer to R.string.radio_buffer_help,
            R.string.radio_retries to R.string.radio_retries_help,
            R.string.radio_show_status to R.string.radio_status_help,
            R.string.radio_pin_color to R.string.radio_pin_color_help,
            R.string.radio_download_pinned_logos to R.string.radio_pinned_logos_help,
            R.string.radio_export to R.string.radio_export_help,
        ))
    }

    fun configureBorder(parent: LinearLayout, enabled: Boolean, outline: BlockOutline) {
        val framed = enabled && outline.hasBorder
        val density = parent.resources.displayMetrics.density
        parent.foreground = if (framed) PlayerBlockDrawable(outline,
            MaterialColors.getColor(parent, com.google.android.material.R.attr.colorOutline), density) else null
        val inset = if (framed) ((if (outline.style == com.macarmusic.app.model.BlockStyle.CHARACTERS) 12 else 8) * density).toInt() else 0
        parent.setPadding(inset, inset, inset, inset)
    }

    fun populate(parent: LinearLayout) {
        if (parent.childCount > 0) return
        val context = parent.context
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val density = parent.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val heading = TextView(context).apply {
            id = R.id.aboutHeading
            setText(R.string.app_name)
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            ViewCompat.setAccessibilityHeading(this, true)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
        }
        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(heading)
            addView(TextView(context).apply {
                id = R.id.aboutVersion
                text = context.getString(R.string.about_version,
                    requireNotNull(info.versionName), PackageInfoCompat.getLongVersionCode(info))
                textSize = 14f
                setPadding(0, dp(4), 0, 0)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
        }
        val logo = ImageView(context).apply {
            id = R.id.aboutLogo
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(Branding.icon(context))
            contentDescription = context.getString(R.string.app_logo)
        }
        val header = object : LinearLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val compact = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED &&
                    MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight <
                    heading.paint.measureText(heading.text.toString()) + dp(92)
                val direction = if (compact) VERTICAL else HORIZONTAL
                if (orientation != direction) {
                    orientation = direction
                    (labels.layoutParams as LayoutParams).apply {
                        width = if (compact) ViewGroup.LayoutParams.MATCH_PARENT else 0
                        weight = if (compact) 0f else 1f
                    }
                    (logo.layoutParams as LayoutParams).apply {
                        marginStart = if (compact) 0 else dp(12)
                        topMargin = if (compact) dp(8) else 0
                        gravity = if (compact) Gravity.CENTER_HORIZONTAL else Gravity.NO_GRAVITY
                    }
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(logo, LinearLayout.LayoutParams(dp(80), dp(80)).apply { marginStart = dp(12) })
        }
        parent.addView(header)
        parent.addView(TextView(context).apply {
            id = R.id.aboutBody
            textSize = 16f
            setLineSpacing(dp(3).toFloat(), 1.08f)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            text = paragraphs(context, R.string.about_intro, R.string.playback_mode_help, R.string.about_body,
                R.string.about_system_controls, R.string.about_lyric_cache).apply {
                append("\n\n")
                append(settingsHelp(context))
                append("\n\n")
                inSpans(StyleSpan(Typeface.BOLD), RelativeSizeSpan(1.25f)) {
                    append(context.getText(R.string.radio_help_title))
                }
                append("\n\n")
                append(radioHelp(context))
            }
        })
    }
}
