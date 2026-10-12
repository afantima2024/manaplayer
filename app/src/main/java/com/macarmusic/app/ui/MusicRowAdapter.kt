package com.macarmusic.app.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.macarmusic.app.R
import com.macarmusic.app.databinding.RowItemBinding

data class MusicRow(
    val key: String,
    val title: String,
    val subtitle: String,
    val detail: String = "",
    val icon: Int = R.drawable.ic_note,
    val active: Boolean = false,
)

class MusicRowAdapter(
    private val onClick: (MusicRow) -> Unit,
    private val onMore: (MusicRow) -> Unit,
) : ListAdapter<MusicRow, MusicRowAdapter.Holder>(Diff) {
    class Holder(val binding: RowItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(RowItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        with(holder.binding) {
            title.text = row.title
            subtitle.text = row.subtitle
            subtitle.isVisible = row.subtitle.isNotEmpty()
            detail.text = row.detail
            icon.setImageResource(row.icon)
            val textColor = MaterialColors.getColor(
                root,
                if (row.active) com.google.android.material.R.attr.colorPrimary
                else com.google.android.material.R.attr.colorOnSurface,
            )
            title.setTextColor(textColor)
            icon.imageTintList = ColorStateList.valueOf(
                MaterialColors.getColor(root, com.google.android.material.R.attr.colorPrimary),
            )
            more.contentDescription = root.context.getString(R.string.options_for, row.title)
            root.setOnClickListener { onClick(row) }
            root.setOnLongClickListener { onMore(row); true }
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
