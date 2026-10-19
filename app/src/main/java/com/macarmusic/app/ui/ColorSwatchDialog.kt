package com.macarmusic.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.macarmusic.app.R
import com.macarmusic.app.data.ColorSlotStore
import com.macarmusic.app.model.ColorHex
import com.macarmusic.app.model.ColorSlots
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import java.io.IOException

object ColorSwatchDialog {
    fun show(context: AppCompatActivity, title: String, initial: Int,
        reset: (() -> Unit)? = null, save: (Int) -> Unit,
    ) {
        val store = ColorSlotStore(context)
        val original = try {
            store.read()
        } catch (error: SerializationException) {
            paletteError(context, error)
            return
        } catch (error: IllegalArgumentException) {
            paletteError(context, error)
            return
        }
        var draft = original
        var selected = initial
        var slot = draft.colors.indexOf(initial).takeIf { it >= 0 }
        val content = column(context)
        val preview = preview(context, R.id.colorPreview)
        val edit = MaterialButton(context).apply { id = R.id.colorEditSlot }
        val help = TextView(context).apply { setText(R.string.color_slots_help) }
        content.addView(preview)
        content.addView(edit)
        content.addView(help)
        val grid = object : GridLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val columns = (MeasureSpec.getSize(widthMeasureSpec) / dp(context, 52)).coerceIn(1, 7)
                if (columnCount != columns) {
                    // Clear resolved spans before narrowing the grid, including on Android 6.
                    for (index in 0 until childCount) {
                        val child = getChildAt(index)
                        val params = child.layoutParams as LayoutParams
                        params.rowSpec = spec(UNDEFINED)
                        params.columnSpec = spec(UNDEFINED, 1f)
                        child.layoutParams = params
                    }
                    columnCount = columns
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply { id = R.id.colorGrid }
        content.addView(grid)
        val swatches = mutableListOf<TextView>()
        fun refresh() {
            paintPreview(preview, selected)
            edit.isVisible = slot != null || selected !in ColorSlots.RAINBOW
            edit.setText(if (slot != null) R.string.color_edit_slot else R.string.color_save_current)
            swatches.forEachIndexed { index, view ->
                val value = if (index < 7) ColorSlots.RAINBOW[index] else draft.colors[index - 7]
                view.isSelected = if (slot != null) index == slot!! + 7 else index < 7 && value == selected
                view.text = when {
                    view.isSelected -> "\u2713"
                    value == null -> (index - 6).toString()
                    else -> ""
                }
            }
        }
        lateinit var rebuild: () -> Unit
        fun define(index: Int) {
            rgbEditor(context, draft.colors.getOrNull(index) ?: selected) { color ->
                draft = draft.define(index, color)
                slot = index
                selected = color
                rebuild()
            }
        }
        rebuild = {
            grid.removeAllViews()
            swatches.clear()
            fun add(view: TextView) {
                grid.addView(view, GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f),
                ).apply {
                    width = dp(context, 48); height = dp(context, 48)
                    setMargins(dp(context, 2), dp(context, 4), dp(context, 2), dp(context, 4))
                })
            }
            (ColorSlots.RAINBOW + draft.colors).forEachIndexed { index, value ->
                val customIndex = index - 7
                val swatch = AppCompatTextView(context).apply {
                    tag = if (index < 7) "rainbow:$index" else "custom:$customIndex"
                    gravity = Gravity.CENTER
                    textSize = 24f
                    isClickable = true
                    isFocusable = true
                    val color = value?.or(Color.BLACK)
                    setTextColor(if (color == null) AccentTheme.color(this, com.google.android.material.R.attr.colorOnSurface)
                        else contrasting(color))
                    contentDescription = if (index < 7) context.getString(R.string.color_swatch_description, ColorHex.format(value!!))
                        else context.getString(R.string.color_slot_description, customIndex + 1,
                            value?.let(ColorHex::format) ?: context.getString(R.string.color_slot_empty))
                    val shape = GradientDrawable().apply {
                        setColor(color ?: Color.TRANSPARENT)
                        cornerRadius = dp(context, 4).toFloat()
                        setStroke(dp(context, 1), 0xff808080.toInt())
                    }
                    background = RippleDrawable(ColorStateList.valueOf(0x40808080), shape, null)
                    setOnClickListener {
                        if (value == null) define(customIndex)
                        else {
                            selected = value
                            slot = customIndex.takeIf { it >= 0 }
                            refresh()
                        }
                    }
                }
                swatches.add(swatch)
                add(swatch)
            }
            add(AppCompatTextView(context).apply {
                id = R.id.colorAddSlot
                text = "+"
                textSize = 28f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = context.getString(R.string.color_add_slot)
                background = RippleDrawable(ColorStateList.valueOf(0x40808080),
                    GradientDrawable().apply {
                        setColor(Color.TRANSPARENT)
                        setStroke(dp(context, 1), 0xff808080.toInt())
                    }, null)
                setOnClickListener { define(draft.colors.size) }
            })
            refresh()
        }
        edit.setOnClickListener {
            define(slot ?: draft.colors.indexOf(null).takeIf { it >= 0 } ?: draft.colors.size)
        }
        rebuild()
        val builder = AccentTheme.dialog(context).setTitle(title)
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton(R.string.apply, null)
            .setNegativeButton(R.string.cancel, null)
        if (reset != null) builder.setNeutralButton(R.string.use_default) { _, _ -> reset() }
        val dialog = builder.create()
        var pending: Job? = null
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (pending?.isActive == true) return@setOnClickListener
                pending = context.lifecycleScope.launch {
                    dialog.setCancelable(false)
                    for (button in listOf(-1, -2, -3)) dialog.getButton(button)?.isEnabled = false
                    try {
                        if (draft != original) store.save(draft)
                        save(selected)
                        dialog.dismiss()
                    } catch (error: IOException) {
                        Log.e("ColorSwatchDialog", "Custom palette was not saved", error)
                        help.text = context.getString(R.string.color_slots_save_failed)
                    } finally {
                        dialog.setCancelable(true)
                        for (button in listOf(-1, -2, -3)) dialog.getButton(button)?.isEnabled = true
                    }
                }
            }
        }
        dialog.setOnDismissListener { pending?.cancel() }
        dialog.show()
    }

    private fun rgbEditor(context: Context, initial: Int, accept: (Int) -> Unit) {
        var selected = initial
        var syncing = false
        val content = column(context)
        val preview = preview(context, R.id.colorEditorPreview)
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
        val channels = listOf(R.string.color_red, R.string.color_green, R.string.color_blue)
        val ids = listOf(R.id.colorRed, R.id.colorGreen, R.id.colorBlue)
        val labels = channels.map { TextView(context) }
        val sliders = channels.mapIndexed { index, name ->
            content.addView(labels[index])
            AppCompatSeekBar(context).apply {
                id = ids[index]
                max = 255
                minimumHeight = dp(context, 48)
                contentDescription = context.getString(name)
                content.addView(this)
            }
        }
        fun refresh(updateField: Boolean) {
            syncing = true
            paintPreview(preview, selected)
            sliders.forEachIndexed { index, slider ->
                val value = selected shr (16 - 8 * index) and 255
                slider.progress = value
                labels[index].text = context.getString(R.string.setting_value, context.getString(channels[index]), value.toString())
            }
            if (updateField) field.setText(ColorHex.format(selected))
            syncing = false
        }
        sliders.forEach { slider ->
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (syncing) return
                    selected = (sliders[0].progress shl 16) or (sliders[1].progress shl 8) or sliders[2].progress
                    input.error = null
                    refresh(updateField = true)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
        field.doAfterTextChanged {
            if (!syncing) {
                val value = ColorHex.parse(it.toString())
                input.error = if (value == null) context.getString(R.string.custom_color_invalid) else null
                if (value != null) {
                    selected = value
                    refresh(updateField = false)
                }
            }
        }
        refresh(updateField = false)
        val dialog = AccentTheme.dialog(context).setTitle(R.string.custom_color)
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton(R.string.color_use, null).setNegativeButton(R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val color = ColorHex.parse(field.text.toString())
                if (color == null) input.error = context.getString(R.string.custom_color_invalid)
                else {
                    accept(color)
                    dialog.dismiss()
                }
            }
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
    }

    private fun column(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 24), dp(context, 8), dp(context, 24), dp(context, 16))
    }

    private fun preview(context: Context, viewId: Int) = TextView(context).apply {
        id = viewId
        gravity = Gravity.CENTER
        textSize = 20f
        minHeight = dp(context, 48)
    }

    private fun paintPreview(view: TextView, rgb: Int) {
        val color = rgb or Color.BLACK
        view.setBackgroundColor(color)
        view.setTextColor(contrasting(color))
        view.text = view.context.getString(R.string.setting_value,
            view.context.getString(R.string.selected_color), ColorHex.format(rgb))
    }

    private fun contrasting(color: Int) = if (ColorUtils.calculateLuminance(color) > 0.4) Color.BLACK else Color.WHITE
    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private fun paletteError(context: Context, error: Exception) {
        Log.e("ColorSwatchDialog", "Saved color slots could not be read; preserving them", error)
        AccentTheme.dialog(context).setTitle(R.string.custom_color).setMessage(R.string.color_slots_load_failed)
            .setPositiveButton(R.string.close, null).show()
    }
}
