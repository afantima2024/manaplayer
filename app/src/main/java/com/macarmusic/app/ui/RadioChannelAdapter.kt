package com.macarmusic.app.ui

import android.graphics.Typeface
import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.macarmusic.app.R
import com.macarmusic.app.model.BlockOutline
import com.macarmusic.app.radio.RadioGroup
import com.macarmusic.app.radio.RadioGroups
import com.macarmusic.app.radio.RadioProgram
import com.macarmusic.app.radio.RadioSection

data class RadioChannelRow(val group: RadioGroup, val station: RadioProgram? = null, val expanded: Boolean = false,
    val section: RadioSection? = null) {
    val key get() = section?.let { "section:$it" } ?: station?.id ?: "group:${group.id}"
}

internal class RadioChannelRows(stations: List<RadioProgram>) {
    private val groups = RadioGroups.grouped(stations)

    fun visible(expandedGroup: String? = null, hiddenGroups: Set<String> = emptySet()): List<RadioChannelRow> = buildList {
        var section: RadioSection? = null
        for ((group, programs) in groups) {
            if (group.id in hiddenGroups) continue
            if (section != group.section) {
                section = group.section
                add(RadioChannelRow(RadioGroup("section:$section", section.label, section = section), section = section))
            }
            val expanded = group.id == expandedGroup
            add(RadioChannelRow(group, expanded = expanded))
            if (expanded) programs.forEach { add(RadioChannelRow(group, it)) }
        }
    }
}

class RadioChannelAdapter(
    private val onPlay: (RadioProgram) -> Unit,
    private val onOptions: (RadioProgram) -> Unit,
    private val onToggleGroup: (RadioGroup) -> Unit,
    private val onGroupOptions: (RadioGroup) -> Unit,
) : ListAdapter<RadioChannelRow, RadioChannelAdapter.Holder>(Diff), PlayerFocusItems {
    override fun focusKey(position: Int) = "channel:${getItem(position).key}"
    sealed class Holder(view: View) : RecyclerView.ViewHolder(view) {
        class Button(val text: MaterialButton) : Holder(text) {
            val styler = BlockButtonStyler(text, preserveMargins = true)
        }
        class Section(view: View, val text: TextView) : Holder(view)
    }
    private var selected: String? = null
    private var pins = emptySet<String>()
    private var pinColor = 0
    private var highlightColor: Int? = null
    private var blocks = false to BlockOutline()

    fun configureBlocks(enabled: Boolean, outline: BlockOutline) {
        if (blocks == (enabled to outline)) return
        blocks = enabled to outline
        notifyItemRangeChanged(0, itemCount, blocks)
    }

    fun selection(id: String?, pinnedIds: Set<String>, color: Int, highlight: Int?) {
        if (id == selected && pins == pinnedIds && pinColor == color && highlightColor == highlight) return
        val changed = mutableSetOf<String>()
        if (id != selected) {
            selected?.let(changed::add)
            id?.let(changed::add)
        }
        changed.addAll(pins - pinnedIds)
        changed.addAll(pinnedIds - pins)
        if (color != pinColor) changed.addAll(pinnedIds)
        if (highlight != highlightColor) id?.let(changed::add)
        selected = id
        pins = pinnedIds
        pinColor = color
        highlightColor = highlight
        currentList.forEachIndexed { index, row ->
            if (row.station?.id in changed) notifyItemChanged(index)
        }
    }
    override fun getItemViewType(position: Int) = when {
        getItem(position).section != null -> 2
        getItem(position).station == null -> 0
        else -> 1
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val inset = (12 * context.resources.displayMetrics.density).toInt()
        if (viewType == 2) {
            val title = TextView(context).apply {
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(inset, inset / 2, inset, inset / 2)
                setTextColor(AccentTheme.color(this, com.google.android.material.R.attr.colorPrimary))
                ViewCompat.setAccessibilityHeading(this, true)
            }
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, inset, 0, 0)
                addView(View(context).apply {
                    setBackgroundColor(AccentTheme.color(this, com.google.android.material.R.attr.colorPrimary))
                }, LinearLayout.LayoutParams(-1, (2 * context.resources.displayMetrics.density).toInt()))
                addView(title, LinearLayout.LayoutParams(-1, -2))
                layoutParams = RecyclerView.LayoutParams(-1, -2)
            }
            return Holder.Section(container, title)
        }
        if (viewType == 0) {
            val text = groupButton(context).apply { layoutParams = RecyclerView.LayoutParams(-1, -2) }
            return Holder.Button(text).also { holder ->
                fun options() = holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                    ?.let { onGroupOptions(getItem(it).group) }
                text.setOnClickListener {
                    holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                        ?.let { onToggleGroup(getItem(it).group) }
                }
                text.setOnLongClickListener { options(); true }
                text.setOnKeyListener { _, key, event ->
                    if (event.action != KeyEvent.ACTION_DOWN || !event.hasNoModifiers()) return@setOnKeyListener false
                    when (key) {
                        KeyEvent.KEYCODE_MENU -> {
                            if (event.repeatCount == 0) options()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (event.repeatCount == 0) holder.bindingAdapterPosition
                                .takeIf { it != RecyclerView.NO_POSITION }?.let(::getItem)?.let { row ->
                                    if (row.expanded != (key == KeyEvent.KEYCODE_DPAD_RIGHT)) onToggleGroup(row.group)
                                }
                            true
                        }
                        else -> false
                    }
                }
                ViewCompat.replaceAccessibilityAction(text, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                    context.getString(R.string.radio_manage_groups)) { view, _ -> view.performLongClick() }
            }
        }
        val text = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            isAllCaps = false
            isFocusable = true
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            textSize = 16f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            minHeight = (56 * context.resources.displayMetrics.density).toInt()
            insetTop = 0
            insetBottom = 0
        }
        val vertical = (8 * context.resources.displayMetrics.density).toInt()
        text.setPadding(inset, vertical, inset, vertical)
        text.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return Holder.Button(text).also { holder ->
            fun row() = holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(::getItem)
            fun station() = row()?.station
            text.setOnClickListener { station()?.let(onPlay) }
            text.setOnLongClickListener { station()?.let(onOptions); true }
            text.setOnKeyListener { _, key, event ->
                if (key == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    station()?.let(onOptions); true
                } else false
            }
        }
    }
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        holder.itemView.setTag(R.id.playerFocusKey, focusKey(position))
        if (holder is Holder.Section) {
            holder.text.setText(checkNotNull(row.section).label)
            return
        }
        check(holder is Holder.Button)
        val station = row.station
        val active = station != null && station.id == selected
        val highlight = highlightColor?.takeIf { active }
        holder.styler.apply(blocks.first, blocks.second, highlight)
        val context = holder.text.context
        val title = station?.title(context) ?: row.group.title(context)
        holder.text.text = title
        holder.text.contentDescription = title
        holder.text.isSelected = active
        if (station == null) {
            bindGroup(holder.text, row)
        } else {
            val pinned = station.id in pins
            val ink = if (highlight != null) AccentTheme.primaryControlInk(holder.text, highlight) else if (pinned) pinColor
                else AccentTheme.color(holder.text, com.google.android.material.R.attr.colorOnSurface)
            holder.text.setTextColor(ink)
            holder.text.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (pinned) {
                PlayerIcons.button(holder.text, R.drawable.ic_pin, ink)
                holder.text.iconSize = (20 * holder.text.resources.displayMetrics.density).toInt()
            } else {
                holder.text.icon = null
                holder.text.setTag(R.id.playerIconSpec, null)
            }
            ViewCompat.setStateDescription(holder.text, listOfNotNull(
                context.getString(R.string.radio_selected_station).takeIf { active },
                context.getString(R.string.radio_pinned).takeIf { pinned },
            ).joinToString(", ").ifEmpty { null })
        }
    }
    companion object {
        internal fun groupButton(context: Context) =
            MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                val density = context.resources.displayMetrics.density
                isAllCaps = false
                isFocusable = true
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                textSize = 17f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                minHeight = (56 * density).toInt()
                insetTop = 0
                insetBottom = 0
                setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(AccentTheme.color(this, com.google.android.material.R.attr.colorPrimary))
                iconGravity = MaterialButton.ICON_GRAVITY_END
                iconSize = (24 * density).toInt()
                ViewCompat.setAccessibilityHeading(this, true)
            }

        internal fun bindGroup(button: MaterialButton, row: RadioChannelRow) {
            button.text = row.group.title(button.context)
            button.contentDescription = button.text
            PlayerIcons.button(button, if (row.expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
            ViewCompat.setStateDescription(button, button.context.getString(
                if (row.expanded) R.string.radio_group_expanded else R.string.radio_group_collapsed))
            ViewCompat.replaceAccessibilityAction(button, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                button.context.getString(if (row.expanded) R.string.radio_collapse_group else R.string.radio_expand_group)) { view, _ ->
                view.performClick()
            }
        }

        private val Diff = object : DiffUtil.ItemCallback<RadioChannelRow>() {
            override fun areItemsTheSame(oldItem: RadioChannelRow, newItem: RadioChannelRow) = oldItem.key == newItem.key
            override fun areContentsTheSame(oldItem: RadioChannelRow, newItem: RadioChannelRow) = oldItem == newItem
        }
    }
}
