package com.macarmusic.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.text.StaticLayout
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ScrollView
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.button.MaterialButton
import com.macarmusic.app.R
import com.macarmusic.app.model.ButtonIconStyle

object AppearanceChoices {
    data class Choice(val id: Int, val label: Int, val icon: Int, val style: ButtonIconStyle? = null)

    val buttonThemes = listOf(
        Choice(R.id.iconsMonochrome, R.string.appearance_icons_mono, R.drawable.ic_play, ButtonIconStyle.MONOCHROME),
        Choice(R.id.iconsColorful, R.string.appearance_icons_colorful, R.drawable.ic_play, ButtonIconStyle.COLORFUL),
        Choice(R.id.iconsOcean, R.string.appearance_icons_ocean, R.drawable.ic_play, ButtonIconStyle.OCEAN),
        Choice(R.id.iconsAurora, R.string.appearance_icons_aurora, R.drawable.ic_play, ButtonIconStyle.AURORA),
        Choice(R.id.iconsSunset, R.string.appearance_icons_sunset, R.drawable.ic_play, ButtonIconStyle.SUNSET),
        Choice(R.id.iconsOrchid, R.string.appearance_icons_orchid, R.drawable.ic_play, ButtonIconStyle.ORCHID),
        Choice(R.id.iconsLogo, R.string.appearance_icons_logo, R.drawable.ic_play, ButtonIconStyle.LOGO),
        Choice(R.id.iconsCopilotAurora, R.string.appearance_icons_copilot_aurora, R.drawable.ic_play, ButtonIconStyle.COPILOT_AURORA),
        Choice(R.id.iconsCopilotSunrise, R.string.appearance_icons_copilot_sunrise, R.drawable.ic_play, ButtonIconStyle.COPILOT_SUNRISE),
    )

    fun show(context: Context, title: Int, choices: List<Choice>, selected: Int,
        reset: (() -> Unit)? = null, choose: (Int) -> Unit) {
        require(choices.isNotEmpty())
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
        val configuration = context.resources.configuration
        val grid = ChoiceGrid(context)
        val buttons = choices.mapIndexed { index, choice ->
            MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = choice.id
                setText(choice.label)
                isCheckable = true
                isChecked = index == selected
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_TOP
                if (choice.style != null) PlayerIcons.button(this, choice.icon,
                    color = AccentTheme.color(this, com.google.android.material.R.attr.colorOnSurface),
                    iconStyle = choice.style)
                else {
                    iconTint = null
                    icon = AppCompatResources.getDrawable(context, choice.icon)
                }
                iconPadding = dp(8)
                gravity = Gravity.CENTER
                minWidth = 0
                minimumWidth = 0
                insetTop = 0
                insetBottom = 0
                setPadding(dp(4), dp(8), dp(4), dp(8))
                shapeAppearanceModel = shapeAppearanceModel.toBuilder().setAllCornerSizes(dp(8).toFloat()).build()
                maxLines = 3
                isAllCaps = false
                if (isChecked) strokeColor = ColorStateList.valueOf(playerIndicatorColor())
                strokeWidth = dp(if (isChecked) 3 else 1)
                grid.addView(this)
            }
        }
        val dialog = AccentTheme.dialog(context).setTitle(title)
            .setView(lyricDialogContent(object : ScrollView(context) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    grid.viewportHeight = MeasureSpec.getSize(heightMeasureSpec)
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                }
            }.apply { addView(grid) }, (configuration.screenHeightDp - 160).coerceIn(144, 520)))
            .setNegativeButton(R.string.cancel, null)
            .apply { if (reset != null) setNeutralButton(R.string.radio_station_logo_auto) { _, _ -> reset() } }
            .show()
        dialog.focusOn(buttons.getOrNull(selected)
            ?: if (reset != null) dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL) else buttons.first())
        buttons.forEachIndexed { index, button -> button.setOnClickListener { choose(index); dialog.dismiss() } }
    }

    private class ChoiceGrid(context: Context) : GridLayout(context) {
        var viewportHeight = 0
        private var arrangedWidth = -1
        private var arrangedHeight = -1
        private var arrangedScale = -1f
        private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

        init {
            id = R.id.appearanceGrid
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = (View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
            val scale = resources.configuration.fontScale
            if (available == arrangedWidth && viewportHeight == arrangedHeight && scale == arrangedScale) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }
            arrangedWidth = available
            arrangedHeight = viewportHeight
            arrangedScale = scale
            val minimum = (dp(if (childCount <= 2) 120 else 88) * scale.coerceAtLeast(1f)).toInt()
            val columns = minOf(childCount, 3, (available / minimum).coerceAtLeast(1))
            val rows = (childCount + columns - 1) / columns
            val cellWidth = available / columns - dp(8)
            val cellHeight = ((viewportHeight - paddingTop - paddingBottom) / rows - dp(8)).coerceIn(dp(96), dp(160))
            if (columnCount != columns) {
                for (index in 0 until childCount) getChildAt(index).layoutParams = LayoutParams()
                columnCount = columns
            }
            for (index in 0 until childCount) {
                val button = getChildAt(index) as MaterialButton
                val textSize = if (cellWidth < dp(112)) 14f else 16f
                button.textSize = textSize
                val textWidth = (cellWidth - button.paddingLeft - button.paddingRight).coerceAtLeast(dp(24))
                val longestWord = button.text.split(' ', '\n').maxOf { button.paint.measureText(it) }
                if (longestWord > textWidth) button.textSize = (textSize * textWidth / longestWord).coerceAtLeast(12f)
                val labelHeight = StaticLayout.Builder.obtain(button.text, 0, button.text.length, button.paint, textWidth)
                    .setIncludePad(button.includeFontPadding).setMaxLines(button.maxLines).build().height
                button.iconSize = minOf(dp(64), cellWidth / 2,
                    cellHeight - labelHeight - dp(24)).coerceAtLeast(dp(28))
                button.minHeight = cellHeight
                button.layoutParams = LayoutParams(spec(index / columns, FILL), spec(index % columns, 1f)).apply {
                    width = 0
                    height = LayoutParams.WRAP_CONTENT
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
