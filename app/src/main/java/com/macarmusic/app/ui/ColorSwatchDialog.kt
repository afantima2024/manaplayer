package com.macarmusic.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.ColorUtils
import androidx.core.widget.doAfterTextChanged
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.macarmusic.app.R
import com.macarmusic.app.model.ColorHex

object ColorSwatchDialog {
    private val colors = listOf(
        0xffffff, 0xe0e0e0, 0xa0a0a0, 0x606060, 0x303030, 0x000000,
        0xf44336, 0xff7043, 0xff9828, 0xffdf69, 0xfcd34d, 0x8bc34a,
        0x3dcf74, 0x00bfa5, 0x00bcd4, 0x29b6f6, 0x448aff, 0x536dfe,
        0x7c4dff, 0xab47bc, 0xec407a, 0xf04c44, 0xed4529, 0xffb74d,
        0xffccbc, 0xffe082, 0xdce775, 0xa5d6a7, 0x80cbc4, 0x80deea,
        0x90caf9, 0xb39ddb, 0xce93d8, 0xf48fb1, 0x9d174d, 0x991b1b,
        0x9a3412, 0x854d0e, 0x166534, 0x115e59, 0x075985, 0x1e40af,
        0x3730a3, 0x6b21a8, 0x86198f, 0x795548, 0x607d8b, 0x37474f,
    )

    fun show(context: Context, title: String, initial: Int, palette: List<Int> = colors,
        reset: (() -> Unit)? = null, customInitial: Boolean = false, save: (color: Int, custom: Boolean) -> Unit,
    ) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        var selected = initial
        var custom = customInitial
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
        val field = TextInputEditText(context).apply {
            id = R.id.customColorInput
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
            setText(ColorHex.format(initial))
        }
        val input = TextInputLayout(context).apply {
            id = R.id.customColorLayout
            hint = context.getString(R.string.custom_color)
            helperText = context.getString(R.string.custom_color_help)
            addView(field)
        }
        content.addView(input)
        val swatches = linkedMapOf<Int, TextView>()
        val grid = object : GridLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val columns = (MeasureSpec.getSize(widthMeasureSpec) / dp(52)).coerceIn(1, 8)
                if (columnCount != columns) {
                    // GridLayout retains resolved spans from earlier, wider measurement passes.
                    for (index in 0 until childCount) {
                        val child = getChildAt(index)
                        val params = child.layoutParams as GridLayout.LayoutParams
                        params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        child.layoutParams = params
                    }
                    columnCount = columns
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply { id = R.id.colorGrid }
        fun refresh() {
            val color = selected or Color.BLACK
            preview.setBackgroundColor(color)
            preview.setTextColor(if (ColorUtils.calculateLuminance(color) > 0.4) Color.BLACK else Color.WHITE)
            preview.setText(R.string.selected_color)
            swatches.forEach { (value, view) ->
                view.isSelected = value == selected
                view.text = if (view.isSelected) "\u2713" else ""
            }
        }
        field.doAfterTextChanged {
            custom = true
            val value = ColorHex.parse(it.toString())
            input.error = if (value == null) context.getString(R.string.custom_color_invalid) else null
            if (value != null) {
                selected = value
                refresh()
            }
        }
        (listOf(initial) + palette).distinct().forEach { value ->
            val color = value or Color.BLACK
            val swatch = AppCompatTextView(context).apply {
                tag = value
                gravity = Gravity.CENTER
                textSize = 24f
                isClickable = true
                isFocusable = true
                minWidth = dp(48)
                minHeight = dp(48)
                setTextColor(if (ColorUtils.calculateLuminance(color) > 0.4) Color.BLACK else Color.WHITE)
                contentDescription = context.getString(R.string.color_swatch_description,
                    ColorHex.format(value))
                val shape = GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = dp(4).toFloat()
                    setStroke(dp(1), 0xff808080.toInt())
                }
                background = RippleDrawable(ColorStateList.valueOf(0x40808080), shape, null)
                setOnClickListener {
                    field.setText(ColorHex.format(value))
                    custom = false
                }
            }
            swatches[value] = swatch
            grid.addView(swatch, GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f),
            ).apply { width = dp(48); height = dp(48); setMargins(dp(2), dp(4), dp(2), dp(4)) })
        }
        content.addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        refresh()
        val builder = AccentTheme.dialog(context).setTitle(title)
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton(R.string.apply, null)
            .setNegativeButton(R.string.cancel, null)
        if (reset != null) builder.setNeutralButton(R.string.use_default) { _, _ -> reset() }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = ColorHex.parse(field.text.toString())
                if (value == null) {
                    input.error = context.getString(R.string.custom_color_invalid)
                    field.requestFocus()
                    field.requestRectangleOnScreen(android.graphics.Rect(0, 0, field.width, field.height), true)
                } else {
                    save(value, custom)
                    dialog.dismiss()
                }
            }
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
    }
}
