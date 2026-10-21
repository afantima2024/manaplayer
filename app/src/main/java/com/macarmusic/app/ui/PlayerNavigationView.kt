package com.macarmusic.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.ViewCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.material.color.MaterialColors
import com.macarmusic.app.R
import com.macarmusic.app.model.BlockOutline
import com.macarmusic.app.model.BlockStyle
import kotlin.math.ceil

class PlayerNavigationView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {
    val artwork = AppCompatImageButton(context).apply {
        id = R.id.navigationArtwork
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(dp(8), dp(8), dp(8), dp(8))
        setImageResource(R.drawable.ic_launcher)
        contentDescription = context.getString(R.string.navigation_artwork)
        setOnClickListener { listener?.invoke(R.id.nav_player) }
        val value = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        setBackgroundResource(value.resourceId)
    }
    private data class Item(val view: LinearLayout, val icon: ImageView, val label: TextView)
    private val items = mutableListOf<Item>()
    private val row = LinearLayout(context)
    private var vertical: Boolean? = null
    private val labelsBesideIcons get() = vertical == false &&
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private var listener: ((Int) -> Unit)? = null
    private var blockOutline: BlockOutline? = null
    private val gap get() = resources.getDimensionPixelSize(R.dimen.player_gap)
    private val itemInset get() = dp(if (blockOutline?.style == BlockStyle.CHARACTERS) 12 else 4)
    private val labelPaint by lazy {
        Paint().apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
            typeface = Typeface.DEFAULT_BOLD
        }
    }
    private val widestLabel by lazy { ceil(items.maxOf { labelPaint.measureText(it.label.text.toString()) }).toInt() }
    private val labelHeight by lazy { ceil(labelPaint.fontSpacing).toInt() }
    private val inlineWidth get() = itemInset * 2 + dp(24 + 8) + widestLabel
    private val itemHeight get() = if (labelsBesideIcons) maxOf(dp(48), itemInset * 2 + maxOf(dp(24), labelHeight))
        else maxOf(dp(80), itemInset * 2 + dp(24 + 2) + labelHeight * 2)

    var selectedItemId: Int = R.id.nav_player
        set(value) {
            if (field == value) return
            require(items.any { it.view.id == value })
            field = value
            renderSelection()
        }
    var isItemActiveIndicatorEnabled: Boolean = true
        set(value) { field = value; renderSelection() }

    init {
        row.addView(artwork)
        val menu = PopupMenu(context, this).apply { inflate(R.menu.navigation) }.menu
        for (index in 0 until menu.size()) {
            val entry = menu.getItem(index)
            val icon = AppCompatImageView(context).apply {
                setImageDrawable(entry.icon)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val label = AppCompatTextView(context).apply {
                text = entry.title
                textSize = 12f
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val button = LinearLayout(context).apply {
                id = entry.itemId
                isBaselineAligned = false
                gravity = Gravity.CENTER
                minimumWidth = dp(48)
                minimumHeight = dp(64)
                setPadding(dp(2), dp(4), dp(2), dp(4))
                isFocusable = true
                contentDescription = entry.title
                addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))
                addView(label)
                setOnClickListener { listener?.invoke(entry.itemId) }
            }
            ViewCompat.setAccessibilityDelegate(button, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.Button"
                    info.isSelected = host.isSelected
                }
            })
            items.add(Item(button, icon, label))
            row.addView(button)
        }
        configure(vertical = false)
        renderSelection()
    }

    fun setOnItemSelectedListener(value: (Int) -> Unit) { listener = value }

    fun configureBlocks(enabled: Boolean, outline: BlockOutline) {
        val next = outline.takeIf { enabled && it.hasBorder }
        if (blockOutline == next) return
        blockOutline = next
        renderSelection()
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val sideWidth = resources.getDimensionPixelSize(R.dimen.navigation_side_width)
        val widthSpec = if (vertical == true)
            MeasureSpec.makeMeasureSpec(resolveSize(sideWidth, widthMeasureSpec), MeasureSpec.EXACTLY) else widthMeasureSpec
        val heightSpec = if (vertical == false)
            MeasureSpec.makeMeasureSpec(resolveSize(itemHeight, heightMeasureSpec), MeasureSpec.EXACTLY) else heightMeasureSpec
        if (vertical == true && MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
            val height = maxOf(itemHeight,
                (MeasureSpec.getSize(heightMeasureSpec) - gap * (row.childCount - 1)) / row.childCount)
            for (index in 0 until row.childCount) {
                val child = row.getChildAt(index)
                if (child.layoutParams.height != height)
                    child.layoutParams = child.layoutParams.apply { this.height = height }
            }
        } else if (vertical == false && MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED) {
            val available = MeasureSpec.getSize(widthSpec) - gap * (row.childCount - 1)
            val width = if (labelsBesideIcons) maxOf(inlineWidth, (available - dp(48)) / items.size)
                else maxOf(dp(48), available / row.childCount)
            for (index in 0 until row.childCount) {
                val child = row.getChildAt(index)
                val itemWidth = if (index == 0 && labelsBesideIcons) dp(48) else width
                if (child.layoutParams.width != itemWidth)
                    child.layoutParams = child.layoutParams.apply { this.width = itemWidth }
            }
        }
        for (item in items) {
            val width = if (vertical == true) MeasureSpec.getSize(widthSpec) else item.view.layoutParams.width
            val height = if (vertical == true) item.view.layoutParams.height else itemHeight
            val icon = if (item.view.isSelected) dp(36) else dp(24)
            val minimumContent = if (labelsBesideIcons) maxOf(icon, item.label.lineHeight)
                else icon + dp(2) + item.label.lineHeight
            val verticalInset = minOf(itemInset, ((height - minimumContent) / 2).coerceAtLeast(0))
            item.view.setPadding(itemInset, verticalInset, itemInset, verticalInset)
            item.label.maxWidth = (width - itemInset * 2 - if (labelsBesideIcons) icon + dp(8) else 0).coerceAtLeast(1)
            if (!labelsBesideIcons) {
                val room = height - verticalInset * 2 - icon - dp(2)
                item.label.maxLines = if (room >= ceil(item.label.paint.fontSpacing * 2).toInt()) 2 else 1
            }
        }
        super.onMeasure(widthSpec, heightSpec)
    }

    fun configure(vertical: Boolean) {
        if (this.vertical == vertical) return
        this.vertical = vertical
        val besideIcons = labelsBesideIcons
        items.forEach {
            it.view.orientation = if (besideIcons) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            it.view.minimumHeight = dp(48)
            it.label.gravity = Gravity.CENTER
            it.label.maxLines = if (besideIcons) 1 else 2
            it.label.layoutParams = if (besideIcons) {
                LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) }
            } else LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
            TooltipCompat.setTooltipText(it.view, it.label.text)
        }
        (row.parent as? android.view.ViewGroup)?.removeView(row)
        removeAllViews()
        row.orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        for (index in 0 until row.childCount) {
            row.getChildAt(index).layoutParams = if (vertical) {
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(72)).apply {
                    bottomMargin = if (index == row.childCount - 1) 0 else gap
                }
            } else LinearLayout.LayoutParams(dp(48), LayoutParams.MATCH_PARENT).apply {
                marginEnd = if (index == row.childCount - 1) 0 else gap
            }
        }
        if (vertical) {
            addView(ScrollView(context).apply {
                isFillViewport = false
                isVerticalScrollBarEnabled = false
                addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        } else addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun renderSelection() {
        val primary = AccentTheme.color(this, com.google.android.material.R.attr.colorPrimary)
        val foreground = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        val container = AccentTheme.color(this, com.google.android.material.R.attr.colorPrimaryContainer)
        val ripple = ColorStateList.valueOf((primary and 0xffffff) or 0x33000000)
        val outlineColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline)
        fun border() = blockOutline?.let { PlayerBlockDrawable(it, outlineColor, resources.displayMetrics.density) }
        artwork.foreground = border()
        val characterInset = if (blockOutline?.style == BlockStyle.CHARACTERS) dp(12) else 0
        val artworkInset = maxOf(dp(8), characterInset)
        artwork.setPadding(artworkInset, artworkInset, artworkInset, artworkInset)
        items.forEach { item ->
            item.view.setPadding(itemInset, itemInset, itemInset, itemInset)
            val selected = item.view.id == selectedItemId
            item.view.isSelected = selected
            val iconSize = dp(if (selected) 36 else 24)
            if (item.icon.layoutParams.width != iconSize) {
                item.icon.layoutParams = item.icon.layoutParams.apply { width = iconSize; height = iconSize }
            }
            item.label.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (selected) 18f else 12f)
            val color = if (selected) primary else foreground
            item.icon.imageTintList = ColorStateList.valueOf(color)
            item.label.setTextColor(color)
            item.label.typeface = if (selected) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            val fill = if (selected && isItemActiveIndicatorEnabled) GradientDrawable().apply {
                setColor(container)
                cornerRadius = dp(8).toFloat()
            } else null
            item.view.background = RippleDrawable(ripple, fill, android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE))
            item.view.foreground = border()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
