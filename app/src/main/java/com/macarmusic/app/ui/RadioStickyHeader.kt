package com.macarmusic.app.ui

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.core.view.doOnNextLayout
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.macarmusic.app.R
import com.macarmusic.app.model.BlockOutline
import com.macarmusic.app.radio.RadioGroup
import kotlin.math.min

internal class RadioStickyHeader(
    private val list: RecyclerView,
    private val channels: RadioChannelAdapter,
    private val pinCount: () -> Int,
    private val toggle: (RadioGroup) -> Unit,
    private val options: (RadioGroup) -> Unit,
) {
    private var rows: List<RadioChannelRow>? = null
    private var expanded: RadioChannelRow? = null
    private var header = -1
    private var end = -1
    private var collapseAnchor: String? = null
    private var restoreCollapseFocus = false
    private var focusObserver: ViewTreeObserver? = null
    private val styler by lazy { BlockButtonStyler(button, preserveMargins = true) }
    private val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, focused ->
        if (focused != null && !list.isInTouchMode) list.post {
            if (button.isVisible && focused.hasFocus() && list.isAttachedToWindow) {
                list.findContainingItemView(focused)?.let { row ->
                    val covered = (button.bottom + button.translationY + list.paddingTop - row.top).toInt()
                    if (covered > 0) list.scrollBy(0, -covered)
                }
            }
        }
    }
    private val button = RadioChannelAdapter.groupButton(list.context).apply {
        id = R.id.radioStickyGroup
        visibility = View.INVISIBLE
        backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface))
        elevation = 4 * resources.displayMetrics.density
        setOnClickListener {
            expanded?.let {
                collapseAnchor = it.group.id
                restoreCollapseFocus = hasFocus()
                isEnabled = false
                toggle(it.group)
            }
        }
        setOnLongClickListener { expanded?.let { options(it.group) }; true }
        setOnKeyListener { _, key, event ->
            if (event.action != KeyEvent.ACTION_DOWN || !event.hasNoModifiers()) return@setOnKeyListener false
            when (key) {
                KeyEvent.KEYCODE_MENU -> {
                    if (event.repeatCount == 0) expanded?.let { options(it.group) }
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.repeatCount == 0 && key == KeyEvent.KEYCODE_DPAD_LEFT) performClick()
                    true
                }
                else -> false
            }
        }
        ViewCompat.replaceAccessibilityAction(this, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
            context.getString(R.string.radio_manage_groups)) { view, _ -> view.performLongClick() }
    }

    init {
        val inset = (8 * list.resources.displayMetrics.density).toInt()
        (list.parent as FrameLayout).addView(button, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            setMargins(inset, 0, inset, 0)
        })
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) { update() }
        })
        list.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update() }
        button.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update() }
        list.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) { observeFocus() }
            override fun onViewDetachedFromWindow(view: View) {
                focusObserver?.takeIf { it.isAlive }?.removeOnGlobalFocusChangeListener(focusListener)
                focusObserver = null
            }
        })
        if (list.isAttachedToWindow) observeFocus()
    }

    private fun observeFocus() {
        focusObserver = list.viewTreeObserver.also { it.addOnGlobalFocusChangeListener(focusListener) }
    }

    fun configureBlocks(enabled: Boolean, outline: BlockOutline) {
        styler.apply(enabled, outline)
        button.backgroundTintList = ColorStateList.valueOf(
            MaterialColors.getColor(button, com.google.android.material.R.attr.colorSurface))
    }

    fun rowsChanged() {
        val current = channels.currentList
        if (rows !== current) {
            rows = current
            header = current.indexOfFirst { it.expanded }
            expanded = current.getOrNull(header)
            end = header
            while (current.getOrNull(end + 1)?.station != null) end++
            expanded?.let { RadioChannelAdapter.bindGroup(button, it) }
            button.isEnabled = true
            collapseAnchor?.let { id ->
                val position = current.indexOfFirst { it.section == null && it.station == null && it.group.id == id }
                if (position >= 0) (list.layoutManager as GridLayoutManager)
                    .scrollToPositionWithOffset(pinCount() + position, 0)
                if (position >= 0 && restoreCollapseFocus) list.doOnNextLayout {
                    if (list.isShown && !list.isInTouchMode && channels.currentList.getOrNull(position)?.group?.id == id)
                        list.layoutManager?.findViewByPosition(pinCount() + position)?.requestFocus()
                }
                restoreCollapseFocus = false
                collapseAnchor = null
            }
        }
        update()
    }

    private fun update() {
        val layout = list.layoutManager as? GridLayoutManager ?: return
        val first = layout.findFirstVisibleItemPosition()
        val position = pinCount() + header
        val last = pinCount() + end
        val row = layout.findViewByPosition(position)
        val visible = list.isShown && expanded != null && first != RecyclerView.NO_POSITION && first <= last &&
            (first > position || first == position && row != null && row.top < list.paddingTop)
        button.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        button.translationY = if (visible) layout.findViewByPosition(last + 1)
            ?.let { min(0, it.top - button.height).toFloat() } ?: 0f else 0f
    }
}
