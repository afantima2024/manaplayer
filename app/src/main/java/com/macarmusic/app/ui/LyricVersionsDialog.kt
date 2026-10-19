package com.macarmusic.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.macarmusic.app.R
import com.macarmusic.app.data.DisplayOptions
import com.macarmusic.app.lyrics.LyricChoice
import com.macarmusic.app.lyrics.LyricChoices
import com.macarmusic.app.lyrics.LyricIssue
import com.macarmusic.app.lyrics.LyricOrigin

internal class LyricVersionsDialog(
    private val context: Context,
    private val display: () -> DisplayOptions,
    private val select: (String) -> Unit,
    private val best: (String) -> Unit,
    private val remove: (String) -> Unit,
    dismissed: () -> Unit,
) {
    private var state = LyricChoices("")
    private var menu: PopupMenu? = null
    private var confirmation: AlertDialog? = null
    private val status = TextView(context).apply {
        id = R.id.lyricsVersionsStatus
        textSize = 14f
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
        setPadding(dp(4), dp(4), dp(4), dp(8))
    }
    private val adapter = VersionsAdapter()
    private val body = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), 0, dp(12), 0)
        addView(TextView(context).apply {
            setText(R.string.lyrics_versions_help)
            textSize = 14f
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }, LinearLayout.LayoutParams(-1, -2))
        addView(status, LinearLayout.LayoutParams(-1, -2))
        addView(RecyclerView(context).apply {
            id = R.id.lyricsVersionsList
            layoutManager = LinearLayoutManager(context)
            adapter = this@LyricVersionsDialog.adapter
            itemAnimator = null
        }, LinearLayout.LayoutParams(-1, 0, 1f))
    }
    private val dialog = AccentTheme.dialog(context).setTitle(R.string.lyrics_versions)
        .setView(FrameLayout(context).apply {
            addView(body, FrameLayout.LayoutParams(-1, dp((resources.configuration.screenHeightDp - 240).coerceIn(120, 440))))
        }).setNegativeButton(R.string.close, null).create().apply {
            setOnDismissListener {
                menu?.dismiss()
                confirmation?.dismiss()
                dismissed()
            }
        }

    fun show() = dialog.show()
    fun dismiss() = dialog.dismiss()

    fun update(value: LyricChoices) {
        state = value
        if (value.loading || value.busy) menu?.dismiss()
        val message = when {
            value.loading -> R.string.lyrics_loading
            value.busy -> R.string.lyrics_cache_busy
            value.issue == LyricIssue.CACHE_UNAVAILABLE -> R.string.lyrics_cache_unavailable
            value.issue == LyricIssue.LOCAL_UNREADABLE -> R.string.lyrics_local_failed
            value.issue == LyricIssue.FOLDER_ACCESS_NEEDED -> R.string.lyrics_folder_needed
            value.choices.isEmpty() -> R.string.lyrics_versions_empty
            else -> null
        }
        status.isVisible = message != null
        status.text = message?.let(context::getString).orEmpty()
        dialog.setCancelable(!value.busy)
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)?.isEnabled = !value.busy
        adapter.notifyDataSetChanged()
    }

    private fun actions(anchor: View, choice: LyricChoice, name: String) {
        menu?.dismiss()
        menu = PopupMenu(context, anchor).apply {
            menu.add(0, R.id.lyricsVersionActivate, 0, R.string.lyrics_version_activate)
            menu.add(0, R.id.lyricsVersionToggleBest, 1,
                if (state.bestKey == choice.key) R.string.lyrics_version_unmark_best else R.string.lyrics_version_mark_best)
            menu.add(0, R.id.lyricsVersionDelete, 2, R.string.lyrics_version_delete).isEnabled = choice.cached
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.lyricsVersionActivate -> select(choice.key)
                    R.id.lyricsVersionToggleBest -> best(choice.key)
                    R.id.lyricsVersionDelete -> confirmRemoval(choice, name)
                }
                true
            }
            setOnDismissListener { this@LyricVersionsDialog.menu = null }
            show()
        }
    }

    private fun confirmRemoval(choice: LyricChoice, name: String) {
        confirmation?.dismiss()
        val message = context.getString(R.string.lyrics_version_delete_confirm, name) +
            if (state.currentKey == choice.key) "\n\n${context.getString(R.string.lyrics_version_delete_current)}" else ""
        confirmation = AccentTheme.dialog(context).setTitle(R.string.lyrics_version_delete)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remove) { _, _ -> remove(choice.key) }
            .create().apply {
                setOnDismissListener { confirmation = null }
                show()
            }
    }

    private inner class Holder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val choose = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = R.id.lyricsVersionChoose
            minHeight = dp(56)
            isAllCaps = false
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textSize = 14f
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val best = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = R.id.lyricsVersionBest
            minWidth = dp(48)
            minHeight = dp(48)
            iconSize = dp(24)
            iconPadding = 0
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            setPadding(dp(12), 0, dp(12), 0)
        }
        val source = TextView(context).apply {
            id = R.id.lyricsVersionSource
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            maxLines = 3
            maxWidth = dp(76)
            textSize = 12f
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
        val styler: BlockButtonStyler
        val normalBackground: ColorStateList?
        val normalText: ColorStateList

        init {
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.addView(choose, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(best, LinearLayout.LayoutParams(dp(48), dp(56)).apply { marginStart = dp(4) })
            row.addView(source, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
            AccentTheme.tintControls(row)
            normalBackground = choose.backgroundTintList
            normalText = choose.textColors
            styler = BlockButtonStyler(row)
        }

        fun bind(choice: LyricChoice, position: Int) {
            val name = choice.name.ifBlank { context.getString(R.string.lyrics_version_number, position + 1) }
            val labels = listOfNotNull(
                R.string.lyrics_version_current.takeIf { state.currentKey == choice.key },
                R.string.lyrics_best.takeIf { state.bestKey == choice.key },
                R.string.lyrics_version_saved.takeIf { choice.cached },
            ).joinToString(" / ") { context.getString(it) }
            val heading = if (labels.isBlank()) name else context.getString(R.string.lyrics_version_heading, name, labels)
            val active = state.currentKey == choice.key
            choose.text = SpannableString(listOf(heading, choice.preview).filter(String::isNotBlank).joinToString("\n")).apply {
                if (active) setSpan(StyleSpan(Typeface.BOLD), 0, heading.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            choose.contentDescription = context.getString(R.string.lyrics_version_use, name)
            ViewCompat.setStateDescription(choose, labels)
            choose.isSelected = active
            row.isActivated = active
            choose.isEnabled = !state.loading && !state.busy
            choose.setOnClickListener { select(choice.key) }
            choose.setOnLongClickListener { actions(it, choice, name); true }
            best.setIconResource(if (state.bestKey == choice.key) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
            best.isSelected = state.bestKey == choice.key
            best.contentDescription = context.getString(
                if (state.bestKey == choice.key) R.string.lyrics_best_remove else R.string.lyrics_best_mark, name)
            TooltipCompat.setTooltipText(best, best.contentDescription)
            best.isEnabled = choose.isEnabled
            best.setOnClickListener { best(choice.key) }
            source.setText(when (choice.origin) {
                LyricOrigin.LOCAL_FILE -> R.string.lyrics_source_local
                LyricOrigin.EMBEDDED -> R.string.lyrics_source_embedded
                LyricOrigin.KUGOU -> R.string.lyrics_source_kugou
                LyricOrigin.NETEASE -> R.string.lyrics_source_netease
                LyricOrigin.QQ -> R.string.lyrics_source_qq
                LyricOrigin.KUWO -> R.string.lyrics_source_kuwo
            })
            val options = display()
            styler.apply(options.controlBlocks, options.blockOutline)
            choose.backgroundTintList = if (active) ColorStateList.valueOf(
                AccentTheme.color(choose, com.google.android.material.R.attr.colorPrimaryContainer))
                else if (options.controlBlocks && options.blockOutline.hasBorder) ColorStateList.valueOf(Color.TRANSPARENT)
                else normalBackground
            if (active) choose.setTextColor(AccentTheme.color(choose, com.google.android.material.R.attr.colorOnPrimaryContainer))
            else choose.setTextColor(normalText)
        }
    }

    private inner class VersionsAdapter : RecyclerView.Adapter<Holder>() {
        private val rowIds = mutableMapOf<String, Long>()
        init { setHasStableIds(true) }
        override fun getItemId(position: Int) = rowIds.getOrPut(state.choices[position].key) { rowIds.size.toLong() }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LinearLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2)
        })
        override fun getItemCount() = state.choices.size
        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(state.choices[position], position)
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
