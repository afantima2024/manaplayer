package com.macarmusic.app.ui

import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.pm.PackageInfoCompat
import com.google.android.material.color.MaterialColors
import com.macarmusic.app.R
import com.macarmusic.app.model.BlockOutline

object AboutContent {
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
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    id = R.id.aboutHeading
                    setText(R.string.app_name)
                    textSize = 22f
                    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
                })
                addView(TextView(context).apply {
                    id = R.id.aboutVersion
                    text = context.getString(R.string.about_version,
                        requireNotNull(info.versionName), PackageInfoCompat.getLongVersionCode(info))
                    textSize = 14f
                    setPadding(0, dp(4), 0, 0)
                    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(ImageView(context).apply {
                id = R.id.aboutLogo
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageResource(R.drawable.ic_launcher)
                contentDescription = context.getString(R.string.app_logo)
            }, LinearLayout.LayoutParams(dp(80), dp(80)).apply { marginStart = dp(12) })
        }
        parent.addView(header)
        parent.addView(TextView(context).apply {
            id = R.id.aboutBody
            textSize = 16f
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            text = context.getString(R.string.about_body) + "\n\n" + context.getString(R.string.about_system_controls) +
                "\n\n" + context.getString(R.string.about_lyric_cache)
        })
    }
}
