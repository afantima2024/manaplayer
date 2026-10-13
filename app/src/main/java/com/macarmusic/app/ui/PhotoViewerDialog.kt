package com.macarmusic.app.ui

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.macarmusic.app.R

class PhotoViewerDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val image = ZoomImageView(context).apply {
            id = R.id.fullResolutionPhoto
            setImageResource(R.drawable.about_photo)
            contentDescription = getString(R.string.about_photo_help)
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(MaterialToolbar(context).apply {
                setTitle(R.string.about_photo_title)
                setNavigationIcon(R.drawable.ic_close)
                setNavigationContentDescription(R.string.close)
                setNavigationOnClickListener { dismiss() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
            addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(LinearLayout(context).apply {
                for ((id, label) in listOf(R.id.photoZoomOut to R.string.photo_zoom_out,
                    R.id.photoReset to R.string.photo_fit, R.id.photoZoomIn to R.string.photo_zoom_in)) {
                    addView(MaterialButton(context).apply {
                        this.id = id
                        setText(label)
                        isAllCaps = false
                        setOnClickListener {
                            when (id) {
                                R.id.photoZoomIn -> image.zoomTo(image.zoom * 1.5f)
                                R.id.photoZoomOut -> image.zoomTo(image.zoom / 1.5f)
                                else -> image.zoomTo(1f)
                            }
                        }
                    }, LinearLayout.LayoutParams(0, dp(56), 1f))
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        return Dialog(context, R.style.Theme_ManaPlayer).apply {
            setContentView(root)
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            val light = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }
}
