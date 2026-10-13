package com.macarmusic.app.ui

import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.fragment.app.FragmentManager
import com.google.android.material.color.MaterialColors
import com.macarmusic.app.R

object AboutContent {
    fun populate(parent: LinearLayout, fragments: FragmentManager) {
        if (parent.childCount > 0) return
        val context = parent.context
        val density = parent.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            addView(TextView(context).apply {
                id = R.id.aboutHeading
                setText(R.string.app_name)
                textSize = 22f
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(AppCompatImageButton(context).apply {
                id = R.id.aboutPhoto
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(4), dp(4), dp(4), dp(4))
                setImageResource(R.drawable.about_photo)
                contentDescription = context.getString(R.string.about_photo_open)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnClickListener { PhotoViewerDialog().show(fragments, "about-photo") }
            }, LinearLayout.LayoutParams(dp(96), dp(104)))
        }
        parent.addView(header)
        parent.addView(TextView(context).apply {
            id = R.id.aboutBody
            textSize = 16f
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            text = context.getString(R.string.device_system_info, Build.VERSION.RELEASE, Build.VERSION.SDK_INT,
                Build.MANUFACTURER, Build.MODEL) + "\n\n" + context.getString(R.string.about_body)
        })
    }
}
