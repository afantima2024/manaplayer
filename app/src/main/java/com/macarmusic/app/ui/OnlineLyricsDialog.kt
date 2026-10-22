package com.macarmusic.app.ui

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.macarmusic.app.R
import com.macarmusic.app.data.DisplayOptions
import com.macarmusic.app.lyrics.LyricCandidateId
import com.macarmusic.app.lyrics.LyricSearchRoute
import com.macarmusic.app.lyrics.OnlineLyricIssue
import com.macarmusic.app.lyrics.OnlineLyricSearch
import com.macarmusic.app.lyrics.OnlineLyricsClient

internal class OnlineLyricsDialog(
    private val context: Context,
    private val display: () -> DisplayOptions,
    private val select: (LyricCandidateId) -> Unit,
    dismissed: () -> Unit,
) {
    private var state: OnlineLyricSearch? = null
    private var buttons: BlockButtonStyler? = null
    private val adapter = MatchesAdapter()
    private val song = TextView(context).apply {
        textSize = 14f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(4), 0, dp(4), dp(4))
        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
    }
    private val duration = TextView(context).apply {
        id = R.id.lyricsTrackDuration
        textSize = 14f
        setPadding(dp(4), 0, dp(4), dp(4))
        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
    }
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        id = R.id.onlineLyricsProgress
        max = OnlineLyricsClient.ROUTES.size
        isIndeterminate = false
    }
    private val status = TextView(context).apply {
        id = R.id.onlineLyricsStatus
        textSize = 14f
        setPadding(dp(4), dp(4), dp(4), dp(8))
        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
    }
    private val body = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), 0, dp(12), 0)
        addView(song, LinearLayout.LayoutParams(-1, -2))
        addView(duration, LinearLayout.LayoutParams(-1, -2))
        addView(TextView(context).apply {
            setText(R.string.lyrics_online_help)
            textSize = 14f
            setPadding(dp(4), 0, dp(4), dp(4))
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
        })
        addView(progress, LinearLayout.LayoutParams(-1, dp(4)))
        addView(status, LinearLayout.LayoutParams(-1, -2))
        addView(RecyclerView(context).apply {
            id = R.id.onlineLyricsList
            layoutManager = LinearLayoutManager(context)
            adapter = this@OnlineLyricsDialog.adapter
            itemAnimator = null
        }, LinearLayout.LayoutParams(-1, 0, 1f))
    }
    private val dialog = AccentTheme.dialog(context).setTitle(R.string.lyrics_online)
        .setView(lyricDialogContent(body, (context.resources.configuration.screenHeightDp - 200).coerceIn(160, 440)))
        .setNegativeButton(R.string.close, null).create().apply {
            setOnDismissListener { dismissed() }
        }

    fun show() {
        dialog.show()
        buttons = BlockButtonStyler.dialogButtons(dialog).also {
            val options = display()
            it.apply(options.controlBlocks, options.blockOutline)
        }
    }
    fun dismiss() = dialog.dismiss()

    fun update(value: OnlineLyricSearch) {
        val previous = state
        state = value
        song.text = value.title
        duration.text = context.getString(R.string.lyrics_music_duration,
            value.trackDurationMs.takeIf { it > 0 }?.let(TimeText::format) ?: context.getString(R.string.lyrics_time_unknown))
        val options = display()
        buttons?.apply(options.controlBlocks, options.blockOutline)
        progress.isVisible = value.searching || value.busyKey != null
        progress.progress = value.completed.size
        status.text = buildList {
            add(context.getString(if (value.searching) R.string.lyrics_online_searching else R.string.lyrics_online_complete,
                value.completed.size, OnlineLyricsClient.ROUTES.size, value.candidates.size))
            if (value.failed.isNotEmpty()) add(context.getString(R.string.lyrics_online_unavailable,
                value.failed.joinToString(", ") { context.getString(routeLabel(it)) }))
            when {
                value.busyKey != null -> add(context.getString(R.string.lyrics_online_downloading))
                value.issue != null -> add(context.getString(when (value.issue) {
                    OnlineLyricIssue.NO_LYRICS -> R.string.lyrics_online_no_lyrics
                    OnlineLyricIssue.DOWNLOAD_FAILED -> R.string.lyrics_online_download_failed
                    OnlineLyricIssue.SEARCH_FAILED -> R.string.lyrics_online_search_failed
                }))
                !value.searching && value.candidates.isEmpty() -> add(context.getString(R.string.lyrics_online_empty))
            }
        }.joinToString("\n")
        val oldSize = previous?.candidates?.size ?: 0
        if (previous == null || value.candidates.take(oldSize) == previous.candidates) {
            if (value.candidates.size > oldSize) adapter.notifyItemRangeInserted(oldSize, value.candidates.size - oldSize)
            if (previous?.busyKey != value.busyKey) adapter.notifyItemRangeChanged(0, oldSize)
        } else {
            adapter.notifyItemRangeRemoved(0, oldSize)
            adapter.notifyItemRangeInserted(0, value.candidates.size)
        }
    }

    private inner class Holder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val choose = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = R.id.onlineLyricsChoose
            minHeight = dp(56)
            isAllCaps = false
            textSize = 14f
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val source = TextView(context).apply {
            id = R.id.onlineLyricsSource
            maxWidth = dp(88)
            maxLines = 3
            textSize = 12f
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
        val styler: BlockButtonStyler

        init {
            row.gravity = Gravity.CENTER_VERTICAL
            row.addView(choose, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(source, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
            AccentTheme.tintControls(row)
            styler = BlockButtonStyler(row)
        }

        fun bind(position: Int) {
            val search = checkNotNull(state)
            val candidate = search.candidates[position]
            val title = listOf(candidate.title, candidate.artist).filter(String::isNotBlank).joinToString(" - ")
            choose.text = context.getString(R.string.setting_value, title, context.getString(R.string.lyrics_provider_duration,
                candidate.durationMs.takeIf { it > 0 }?.let(TimeText::format) ?: context.getString(R.string.lyrics_time_unknown)))
            val provider = context.getString(routeLabel(candidate.key.route))
            choose.contentDescription = context.getString(R.string.lyrics_online_use, title, provider)
            choose.isEnabled = search.busyKey == null
            choose.setOnClickListener { select(candidate.key) }
            source.text = provider
            val options = display()
            styler.apply(options.controlBlocks, options.blockOutline)
        }
    }

    private inner class MatchesAdapter : RecyclerView.Adapter<Holder>() {
        private val ids = mutableMapOf<LyricCandidateId, Long>()
        init { setHasStableIds(true) }
        override fun getItemId(position: Int) = ids.getOrPut(checkNotNull(state).candidates[position].key) { ids.size.toLong() }
        override fun getItemCount() = state?.candidates?.size ?: 0
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LinearLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2)
        })
        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(position)
    }

    private fun routeLabel(route: LyricSearchRoute): Int = when (route) {
        LyricSearchRoute.KUGOU_DIRECT -> R.string.provider_kugou_direct
        LyricSearchRoute.KUGOU_MOBILE -> R.string.provider_kugou_mobile
        LyricSearchRoute.QQ -> R.string.provider_qq
        LyricSearchRoute.NETEASE -> R.string.provider_netease
        LyricSearchRoute.KUWO -> R.string.provider_kuwo
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
