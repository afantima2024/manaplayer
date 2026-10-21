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
import android.widget.ScrollView
import android.widget.TextView
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
    private var menu: AlertDialog? = null
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

    private fun actions(choice: LyricChoice, name: String) {
        menu?.dismiss()
        val content = LinearLayout(context).apply {
            id = R.id.lyricsVersionActions
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(8))
        }
        fun addAction(viewId: Int, label: Int, enabled: Boolean = true, action: () -> Unit) {
            content.addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = viewId
                setText(label)
                isAllCaps = false
                minHeight = dp(48)
                isEnabled = enabled
                setOnClickListener { menu?.dismiss(); action() }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
        addAction(R.id.lyricsVersionActivate, R.string.lyrics_version_activate) { select(choice.key) }
        addAction(R.id.lyricsVersionToggleBest,
            if (state.bestKey == choice.key) R.string.lyrics_version_unmark_best else R.string.lyrics_version_mark_best) {
            best(choice.key)
        }
        addAction(R.id.lyricsVersionDelete, R.string.lyrics_version_delete, choice.cached) { confirmRemoval(choice, name) }
        AccentTheme.tintControls(content)
        val options = display()
        BlockButtonStyler(content).apply(options.controlBlocks, options.blockOutline)
        val actionsDialog = AccentTheme.dialog(context).setTitle(name)
            .setView(ScrollView(context).apply { addView(content) })
            .setNegativeButton(R.string.cancel, null).create()
        actionsDialog.setOnDismissListener {
            if (menu === actionsDialog) menu = null
        }
        menu = actionsDialog
        actionsDialog.show()
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
        val more = iconButton(R.id.lyricsVersionMore).apply {
            setIconResource(R.drawable.ic_more)
        }
        val best = iconButton(R.id.lyricsVersionBest)
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
            row.addView(more, LinearLayout.LayoutParams(dp(48), dp(56)).apply { marginStart = dp(4) })
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
            val hold = View.OnLongClickListener { actions(choice, name); true }
            choose.setOnLongClickListener(hold)
            row.isEnabled = choose.isEnabled
            row.setOnLongClickListener(hold)
            more.isEnabled = choose.isEnabled
            more.contentDescription = context.getString(R.string.options_for, name)
            more.setOnClickListener { actions(choice, name) }
            more.setOnLongClickListener(hold)
            best.setIconResource(if (state.bestKey == choice.key) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
            best.isSelected = state.bestKey == choice.key
            best.contentDescription = context.getString(
                if (state.bestKey == choice.key) R.string.lyrics_best_remove else R.string.lyrics_best_mark, name)
            best.isEnabled = choose.isEnabled
            best.setOnClickListener { best(choice.key) }
            best.setOnLongClickListener(hold)
            source.isEnabled = choose.isEnabled
            source.setOnLongClickListener(hold)
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

    private fun iconButton(viewId: Int) =
        MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = viewId
            minWidth = dp(48)
            minHeight = dp(48)
            iconSize = dp(24)
            iconPadding = 0
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            setPadding(dp(12), 0, dp(12), 0)
        }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
