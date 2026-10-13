package com.macarmusic.app.ui

import android.app.Dialog
import android.os.Bundle
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.macarmusic.app.R

class AboutDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
            addView(TextView(context).apply {
                id = R.id.aboutHeading
                setText(R.string.about)
                textSize = 22f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(AppCompatImageButton(context).apply {
                id = R.id.aboutPhoto
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(4), dp(4), dp(4), dp(4))
                setImageResource(R.drawable.about_photo)
                contentDescription = getString(R.string.about_photo_open)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnClickListener { PhotoViewerDialog().show(parentFragmentManager, "about-photo") }
            }, LinearLayout.LayoutParams(dp(96), dp(104)))
        }
        val message = getString(R.string.device_system_info, Build.VERSION.RELEASE, Build.VERSION.SDK_INT,
            Build.MANUFACTURER, Build.MODEL) + "\n\n" + getString(R.string.about_body)
        return MaterialAlertDialogBuilder(context).setCustomTitle(header).setMessage(message)
            .setPositiveButton(R.string.close, null).create()
    }
}
