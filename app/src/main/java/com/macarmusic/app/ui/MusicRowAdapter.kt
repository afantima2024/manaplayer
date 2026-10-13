package com.macarmusic.app.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.macarmusic.app.R
import com.macarmusic.app.databinding.RowItemBinding

data class MusicRow(
    val key: String,
    val title: String,
    val subtitle: String,
    val detail: String = "",
    val icon: Int = R.drawable.ic_note,
    val active: Boolean = false,
    val selected: Boolean? = null,
)

class MusicRowAdapter(
    private val onClick: (MusicRow) -> Unit,
    private val onMore: (MusicRow) -> Unit,
    private val onSelect: ((MusicRow) -> Unit)? = null,
    private val compact: Boolean = false,
) : ListAdapter<MusicRow, MusicRowAdapter.Holder>(Diff) {
    class Holder(val binding: RowItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = RowItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        AccentTheme.tintControls(binding.root)
        if (compact) {
            val size = (48 * parent.resources.displayMetrics.density).toInt()
            binding.root.minimumHeight = size
            binding.root.setPaddingRelative(binding.root.paddingStart, 0, binding.root.paddingEnd, 0)
            binding.title.maxLines = 1
            binding.subtitle.maxLines = 1
            binding.more.layoutParams.width = size
            binding.more.layoutParams.height = size
        }
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        with(holder.binding) {
            title.text = row.title
            subtitle.text = row.subtitle
            subtitle.isVisible = row.subtitle.isNotEmpty()
            detail.text = row.detail
            icon.setImageResource(row.icon)
            val textColor = AccentTheme.color(
                root,
                if (row.active || row.selected == true) com.google.android.material.R.attr.colorPrimary
                else com.google.android.material.R.attr.colorOnSurface,
            )
            title.setTextColor(textColor)
            icon.imageTintList = ColorStateList.valueOf(
                AccentTheme.color(root, com.google.android.material.R.attr.colorPrimary),
            )
            more.contentDescription = root.context.getString(R.string.options_for, row.title)
            more.isVisible = row.selected == null
            selection.isVisible = row.selected != null
            selection.isChecked = row.selected == true
            selection.contentDescription = root.context.getString(R.string.select_song, row.title)
            selection.setOnClickListener { onClick(row) }
            root.isSelected = row.selected == true
            root.setOnClickListener { onClick(row) }
            root.setOnLongClickListener { (onSelect ?: onMore)(row); true }
            more.setOnClickListener { onMore(row) }
        }
    }

    private companion object {
        val Diff = object : DiffUtil.ItemCallback<MusicRow>() {
            override fun areItemsTheSame(oldItem: MusicRow, newItem: MusicRow) = oldItem.key == newItem.key
            override fun areContentsTheSame(oldItem: MusicRow, newItem: MusicRow) = oldItem == newItem
        }
    }
}
