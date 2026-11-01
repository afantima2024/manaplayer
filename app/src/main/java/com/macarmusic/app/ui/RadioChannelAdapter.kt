package com.macarmusic.app.ui

import android.graphics.Typeface
import android.content.res.ColorStateList
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.macarmusic.app.R
import com.macarmusic.app.radio.RadioGroup
import com.macarmusic.app.radio.RadioGroups
import com.macarmusic.app.radio.RadioProgram

data class RadioChannelRow(val group: RadioGroup, val station: RadioProgram? = null, val expanded: Boolean = false) {
    val key get() = station?.id ?: "group:${group.id}"
}

internal class RadioChannelRows(stations: List<RadioProgram>) {
    private val groups = stations.groupBy(RadioGroups::of).entries.sortedBy { it.key.id }

    fun visible(expandedGroup: String? = null): List<RadioChannelRow> = buildList {
        for ((group, programs) in groups) {
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
) : ListAdapter<RadioChannelRow, RadioChannelAdapter.Holder>(Diff) {
    class Holder(val text: MaterialButton) : RecyclerView.ViewHolder(text)
    private var selected: String? = null
    private var pins = emptySet<String>()
    private var pinColor = 0
    fun selection(id: String?, pinnedIds: Set<String>, color: Int) {
        if (id == selected && pins == pinnedIds && pinColor == color) return
        val changed = mutableSetOf<String>()
        if (id != selected) {
            selected?.let(changed::add)
            id?.let(changed::add)
        }
        changed.addAll(pins - pinnedIds)
        changed.addAll(pinnedIds - pins)
        if (color != pinColor) changed.addAll(pinnedIds)
        selected = id
        pins = pinnedIds
        pinColor = color
        currentList.forEachIndexed { index, row ->
            if (row.station?.id in changed) notifyItemChanged(index)
        }
    }
    override fun getItemViewType(position: Int) = if (getItem(position).station == null) 0 else 1
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val inset = (12 * context.resources.displayMetrics.density).toInt()
        val text = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            isAllCaps = false
            isFocusable = true
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            textSize = if (viewType == 0) 17f else 16f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            minHeight = (56 * context.resources.displayMetrics.density).toInt()
            insetTop = 0
            insetBottom = 0
            if (viewType == 0) {
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(AccentTheme.color(this, com.google.android.material.R.attr.colorPrimary))
                iconGravity = MaterialButton.ICON_GRAVITY_END
                iconSize = (24 * context.resources.displayMetrics.density).toInt()
                ViewCompat.setAccessibilityHeading(this, true)
            }
        }
        val vertical = if (viewType == 0) inset else (8 * context.resources.displayMetrics.density).toInt()
        text.setPadding(inset, vertical, inset, vertical)
        text.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return Holder(text).also { holder ->
            fun row() = holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(::getItem)
            if (viewType == 0) {
                text.setOnClickListener { row()?.let { onToggleGroup(it.group) } }
            } else {
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
    }
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        val station = row.station
        val context = holder.text.context
        val title = station?.title(context) ?: row.group.title(context)
        holder.text.text = if (station != null && station.sources.size > 1) context.getString(
            R.string.radio_list_station, title, station.sources.size) else title
        holder.text.contentDescription = title
        holder.text.isSelected = station != null && station.id == selected
        if (station == null) {
            holder.text.setIconResource(if (row.expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
            ViewCompat.setStateDescription(holder.text, context.getString(
                if (row.expanded) R.string.radio_group_expanded else R.string.radio_group_collapsed))
            ViewCompat.replaceAccessibilityAction(holder.text, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                context.getString(if (row.expanded) R.string.radio_collapse_group else R.string.radio_expand_group)) { view, _ ->
                view.performClick()
            }
        } else {
            val pinned = station.id in pins
            val active = station.id == selected
            holder.text.setTextColor(if (pinned) pinColor else AccentTheme.color(holder.text,
                if (active) com.google.android.material.R.attr.colorPrimary else com.google.android.material.R.attr.colorOnSurface))
            holder.text.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (pinned) {
                holder.text.setIconResource(R.drawable.ic_pin)
                holder.text.iconTint = ColorStateList.valueOf(pinColor)
                holder.text.iconSize = (20 * holder.text.resources.displayMetrics.density).toInt()
            } else holder.text.icon = null
            ViewCompat.setStateDescription(holder.text, listOfNotNull(
                context.getString(R.string.radio_selected_station).takeIf { active },
                context.getString(R.string.radio_pinned).takeIf { pinned },
            ).joinToString(", ").ifEmpty { null })
        }
    }
    companion object {
        private val Diff = object : DiffUtil.ItemCallback<RadioChannelRow>() {
            override fun areItemsTheSame(oldItem: RadioChannelRow, newItem: RadioChannelRow) = oldItem.key == newItem.key
            override fun areContentsTheSame(oldItem: RadioChannelRow, newItem: RadioChannelRow) = oldItem == newItem
        }
    }
}
