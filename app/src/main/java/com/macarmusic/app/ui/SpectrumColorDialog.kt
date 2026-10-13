package com.macarmusic.app.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.macarmusic.app.R
import java.util.Locale

object SpectrumColorDialog {
    fun show(context: Context, title: String, initial: Int, save: (Int) -> Unit) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val components = intArrayOf(initial shr 16 and 255, initial shr 8 and 255, initial and 255)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(16))
        }
        val preview = TextView(context).apply {
            id = R.id.colorPreview
            gravity = Gravity.CENTER
            textSize = 20f
            minHeight = dp(48)
        }
        content.addView(preview)
        fun refresh() {
            val color = Color.rgb(components[0], components[1], components[2])
            preview.setBackgroundColor(color)
            preview.setTextColor(if (ColorUtils.calculateLuminance(color) > 0.4) Color.BLACK else Color.WHITE)
            preview.text = String.format(Locale.ROOT, "#%06X", color and 0xffffff)
        }
        val names = listOf(R.string.color_red, R.string.color_green, R.string.color_blue)
        val ids = listOf(R.id.colorRed, R.id.colorGreen, R.id.colorBlue)
        names.forEachIndexed { index, name ->
            val label = TextView(context).apply {
                textSize = 16f
                labelFor = ids[index]
                setPadding(0, dp(8), 0, 0)
            }
            fun labelValue() { label.text = context.getString(R.string.color_component, context.getString(name), components[index]) }
            labelValue()
            content.addView(label)
            content.addView(SeekBar(context).apply {
                id = ids[index]
                max = 255
                progress = components[index]
                contentDescription = context.getString(name)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        components[index] = progress
                        labelValue()
                        refresh()
                    }
                })
            })
        }
        refresh()
        MaterialAlertDialogBuilder(context).setTitle(title)
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton(R.string.apply) { _, _ ->
                save((components[0] shl 16) or (components[1] shl 8) or components[2])
            }
            .setNegativeButton(R.string.cancel, null).show()
    }
}
