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
import android.content.DialogInterface
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
    private var menu: PlayerDialog? = null
    private var confirmation: PlayerDialog? = null
    private var buttons: BlockButtonStyler? = null
    private val duration = TextView(context).apply {
        id = R.id.lyricsTrackDuration
        textSize = 14f
        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
        setPadding(dp(4), dp(4), dp(4), dp(8))
    }
    private val status = TextView(context).apply {
        id = R.id.lyricsVersionsStatus
        textSize = 14f
        maxLines = 4
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
        setPadding(dp(4), dp(4), dp(4), dp(8))
    }
    private val adapter = VersionsAdapter()
    private val body = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), 0, dp(12), 0)
        addView(duration, LinearLayout.LayoutParams(-1, -2))
        addView(status, LinearLayout.LayoutParams(-1, -2))
        addView(RecyclerView(context).apply {
            id = R.id.lyricsVersionsList
            layoutManager = LinearLayoutManager(context)
            adapter = this@LyricVersionsDialog.adapter
            itemAnimator = null
        }, LinearLayout.LayoutParams(-1, 0, 1f))
    }
    private val dialog = AccentTheme.dialog(context).setTitle(R.string.lyrics_versions)
        .setView(lyricDialogContent(body, (context.resources.configuration.screenHeightDp - 240).coerceIn(120, 440)))
        .setNegativeButton(R.string.close, null).create().apply {
            setOnDismissListener {
                menu?.dismiss()
                confirmation?.dismiss()
                dismissed()
            }
        }

    fun show() {
        dialog.show()
        buttons = BlockButtonStyler.dialogButtons(dialog).also {
            val options = display()
            it.apply(options.controlBlocks, options.blockOutline)
        }
    }
    fun dismiss() = dialog.dismiss()

    fun update(value: LyricChoices) {
        state = value
        duration.text = context.getString(R.string.lyrics_music_duration,
            value.trackDurationMs.takeIf { it > 0 }?.let(TimeText::format) ?: context.getString(R.string.lyrics_time_unknown))
        val options = display()
        buttons?.apply(options.controlBlocks, options.blockOutline)
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
        BlockButtonStyler.dialogButtons(actionsDialog).apply(options.controlBlocks, options.blockOutline)
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
                val options = display()
                BlockButtonStyler.dialogButtons(this).apply(options.controlBlocks, options.blockOutline)
            }
    }

    private inner class Holder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val choose = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = R.id.lyricsVersionChoose
            minHeight = dp(56)
            isAllCaps = false
            maxLines = 3
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
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            textSize = 12f
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
        val time = TextView(context).apply {
            id = R.id.lyricsVersionTime
            textSize = 12f
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
        private val footer = object : LinearLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
                val oneLine = time.paint.measureText(time.text.toString()) +
                    source.paint.measureText(source.text.toString()) + dp(8)
                val stacked = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && oneLine > available
                val next = if (stacked) VERTICAL else HORIZONTAL
                if (orientation != next) {
                    orientation = next
                    time.layoutParams = LayoutParams(if (stacked) -1 else 0, -2, if (stacked) 0f else 1f)
                    source.layoutParams = LayoutParams(-2, -2).apply {
                        gravity = Gravity.END
                        marginStart = if (stacked) 0 else dp(8)
                    }
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(time, LinearLayout.LayoutParams(0, -2, 1f))
            addView(source, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        }
        val cell = object : FrameLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                footer.setPaddingRelative(choose.paddingStart, 0, choose.paddingEnd, 0)
                footer.measure(getChildMeasureSpec(widthMeasureSpec, 0, -1),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                choose.setPaddingRelative(choose.paddingStart, choose.paddingTop, choose.paddingEnd,
                    footer.measuredHeight + dp(8) + choose.insetBottom)
                (footer.layoutParams as FrameLayout.LayoutParams).bottomMargin =
                    (choose.layoutParams as FrameLayout.LayoutParams).bottomMargin + dp(4) + choose.insetBottom
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            id = R.id.lyricsVersionCell
            addView(choose, FrameLayout.LayoutParams(-1, -2))
            addView(footer, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        }
        val styler: BlockButtonStyler
        val normalBackground: ColorStateList?
        val normalText: ColorStateList

        init {
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.addView(more, LinearLayout.LayoutParams(dp(48), -1))
            row.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
            row.addView(best, LinearLayout.LayoutParams(dp(48), -1).apply { marginStart = dp(4) })
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
            source.setOnClickListener { select(choice.key) }
            source.setOnLongClickListener(hold)
            time.isEnabled = choose.isEnabled
            time.setOnClickListener { select(choice.key) }
            time.setOnLongClickListener(hold)
            time.text = choice.lastTimestampMs?.let {
                context.getString(R.string.lyrics_last_timestamp, TimeText.format(it))
            } ?: context.getString(R.string.lyrics_untimed)
            source.setText(when (choice.origin) {
                LyricOrigin.LOCAL_FILE -> R.string.lyrics_source_local
                LyricOrigin.EMBEDDED -> R.string.lyrics_source_embedded
                LyricOrigin.KUGOU -> R.string.lyrics_source_kugou
                LyricOrigin.NETEASE -> R.string.lyrics_source_netease
                LyricOrigin.QQ -> R.string.lyrics_source_qq
                LyricOrigin.KUWO -> R.string.lyrics_source_kuwo
            })
            ViewCompat.setStateDescription(choose, listOf(labels, time.text, source.text).filter { it.isNotBlank() }.joinToString(", "))
            val options = display()
            styler.apply(options.controlBlocks, options.blockOutline)
            choose.backgroundTintList = if (active) ColorStateList.valueOf(
                AccentTheme.color(choose, com.google.android.material.R.attr.colorPrimaryContainer))
                else if (options.controlBlocks && options.blockOutline.hasBorder) ColorStateList.valueOf(Color.TRANSPARENT)
                else normalBackground
            if (active) choose.setTextColor(AccentTheme.color(choose, com.google.android.material.R.attr.colorOnPrimaryContainer))
            else choose.setTextColor(normalText)
            val detailColor = if (active) choose.currentTextColor
                else MaterialColors.getColor(choose, com.google.android.material.R.attr.colorOnSurfaceVariant)
            source.setTextColor(detailColor)
            time.setTextColor(detailColor)
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
