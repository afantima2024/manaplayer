package com.macarmusic.app.ui

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.macarmusic.app.R
import com.macarmusic.app.databinding.RowItemBinding
import com.macarmusic.app.model.BlockOutline

data class MusicRow(
    val key: String,
    val title: String,
    val subtitle: String,
    val detail: String = "",
    val icon: Int = R.drawable.ic_note,
    val active: Boolean = false,
    val selected: Boolean? = null,
    val inlineSubtitle: Boolean = false,
)

class MusicRowAdapter(
    private val onClick: (MusicRow) -> Unit,
    private val onMore: (MusicRow) -> Unit,
    private val onSelect: ((MusicRow) -> Unit)? = null,
    private val compact: Boolean = false,
) : ListAdapter<MusicRow, MusicRowAdapter.Holder>(Diff) {
    private var blocks = false to BlockOutline()
    class Holder(val binding: RowItemBinding) : RecyclerView.ViewHolder(binding.root) {
        val buttons = BlockButtonStyler(binding.root)
    }

    fun configureBlocks(enabled: Boolean, outline: BlockOutline) {
        if (blocks == (enabled to outline)) return
        blocks = enabled to outline
        notifyItemRangeChanged(0, itemCount, blocks)
    }

    override fun getItemViewType(position: Int) = if (getItem(position).inlineSubtitle) INLINE_ROW else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = RowItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val inlineSubtitle = viewType == INLINE_ROW
        AccentTheme.tintControls(binding.root)
        if (compact || inlineSubtitle) {
            val size = (48 * parent.resources.displayMetrics.density).toInt()
            binding.root.minimumHeight = size
            binding.root.setPaddingRelative(binding.root.paddingStart, 0, binding.root.paddingEnd, 0)
            binding.title.maxLines = 1
            binding.subtitle.maxLines = 1
            binding.more.layoutParams.width = size
            binding.more.layoutParams.height = size
        }
        if (inlineSubtitle) {
            binding.title.typeface = Typeface.create(binding.title.typeface, Typeface.NORMAL)
            binding.more.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart = (8 * parent.resources.displayMetrics.density).toInt()
            }
        }
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.buttons.apply(blocks.first, blocks.second)
        val row = getItem(position)
        with(holder.binding) {
            title.text = if (row.inlineSubtitle) buildSpannedString {
                bold { append(row.title) }
                if (row.subtitle.isNotEmpty()) {
                    append("  ")
                    inSpans(ForegroundColorSpan(subtitle.currentTextColor), RelativeSizeSpan(subtitle.textSize / title.textSize)) {
                        append(row.subtitle)
                    }
                }
            } else row.title
            subtitle.text = row.subtitle
            subtitle.isVisible = !row.inlineSubtitle && row.subtitle.isNotEmpty()
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
        const val INLINE_ROW = 1
        val Diff = object : DiffUtil.ItemCallback<MusicRow>() {
            override fun areItemsTheSame(oldItem: MusicRow, newItem: MusicRow) = oldItem.key == newItem.key
            override fun areContentsTheSame(oldItem: MusicRow, newItem: MusicRow) = oldItem == newItem
        }
    }
}
