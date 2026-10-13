package com.macarmusic.app.ui

import android.content.Context
import android.content.res.ColorStateList
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
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.material.color.MaterialColors
import com.macarmusic.app.R
import com.macarmusic.app.model.BlockOutline
import com.macarmusic.app.model.BlockStyle

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
    private var listener: ((Int) -> Unit)? = null
    private var blockOutline: BlockOutline? = null
    private val gap get() = resources.getDimensionPixelSize(R.dimen.player_gap)

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
            val label = TextView(context).apply {
                text = entry.title
                gravity = Gravity.CENTER
                textSize = 12f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setAutoSizeTextTypeUniformWithConfiguration(10, 12, 1, TypedValue.COMPLEX_UNIT_SP)
                includeFontPadding = false
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val button = LinearLayout(context).apply {
                id = entry.itemId
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumWidth = dp(48)
                minimumHeight = dp(64)
                setPadding(dp(2), dp(4), dp(2), dp(4))
                isFocusable = true
                contentDescription = entry.title
                addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))
                addView(label, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(2)
                })
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
        val next = outline.takeIf { enabled }
        if (blockOutline == next) return
        blockOutline = next
        renderSelection()
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (vertical == true && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            val itemHeight = maxOf(dp(64),
                (MeasureSpec.getSize(heightMeasureSpec) - gap * (row.childCount - 1)) / row.childCount)
            val lines = if (itemHeight < dp(80)) 1 else 2
            items.forEach { if (it.label.maxLines != lines) it.label.maxLines = lines }
            for (index in 0 until row.childCount) row.getChildAt(index).layoutParams.height = itemHeight
        } else if (vertical == false && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            items.forEach { if (it.label.maxLines != 2) it.label.maxLines = 2 }
            val itemWidth = maxOf(dp(48),
                (MeasureSpec.getSize(widthMeasureSpec) - gap * (row.childCount - 1)) / row.childCount)
            for (index in 0 until row.childCount) row.getChildAt(index).layoutParams.width = itemWidth
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun configure(vertical: Boolean) {
        if (this.vertical == vertical) return
        this.vertical = vertical
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
        val primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
        val foreground = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        val container = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer)
        val ripple = ColorStateList.valueOf((primary and 0xffffff) or 0x33000000)
        val outlineColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline)
        fun border() = blockOutline?.let { PlayerBlockDrawable(it, outlineColor, resources.displayMetrics.density) }
        artwork.foreground = border()
        val characterInset = if (blockOutline?.style == BlockStyle.CHARACTERS) dp(12) else 0
        val artworkInset = maxOf(dp(8), characterInset)
        artwork.setPadding(artworkInset, artworkInset, artworkInset, artworkInset)
        items.forEach { item ->
            val inset = maxOf(dp(4), characterInset)
            item.view.setPadding(inset, inset, inset, inset)
            val selected = item.view.id == selectedItemId
            item.view.isSelected = selected
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
