package com.macarmusic.app.ui

import android.content.Context
import android.text.TextUtils
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.content.DialogInterface
import com.google.android.material.color.MaterialColors
import com.macarmusic.app.R
import com.macarmusic.app.data.DisplayOptions
import com.macarmusic.app.lyrics.LyricDownloadPhase
import com.macarmusic.app.lyrics.LyricDownloadState

internal class LyricDownloadsDialog(
    private val context: Context,
    private val display: () -> DisplayOptions,
    private val destination: () -> String,
    private val cancel: () -> Unit,
    dismissed: () -> Unit,
) {
    private var state: LyricDownloadState? = null
    private var buttons: BlockButtonStyler? = null
    private fun text(id: Int = android.view.View.NO_ID) = TextView(context).apply {
        this.id = id
        textSize = 16f
        setPadding(0, dp(6), 0, dp(6))
        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
    }
    private val title = text().apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
    private val status = text(R.id.lyricsDownloadStatus)
    private val current = text(R.id.lyricsDownloadCurrent).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
    private val counts = text(R.id.lyricsDownloadCounts)
    private val location = text().apply { textSize = 12f }
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        id = R.id.lyricsDownloadProgress
    }
    private val body = ScrollView(context).apply {
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(8))
            addView(title)
            addView(status)
            addView(progress, LinearLayout.LayoutParams(-1, dp(6)))
            addView(current)
            addView(counts)
            addView(location)
        })
    }
    private val dialog = AccentTheme.dialog(context).setTitle(R.string.lyrics_download)
        .setView(lyricDialogContent(body, (context.resources.configuration.screenHeightDp - 180).coerceIn(160, 240)))
        .setNegativeButton(R.string.cancel, null).setCancelable(false).create().apply {
            setOnDismissListener { dismissed() }
        }

    fun show() {
        dialog.show()
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
            if (state?.running == true) cancel() else dialog.dismiss()
        }
        buttons = BlockButtonStyler.dialogButtons(dialog)
    }

    fun dismiss() = dialog.dismiss()

    fun update(value: LyricDownloadState) {
        state = value
        val options = display()
        title.text = value.title
        status.text = when (value.phase) {
            LyricDownloadPhase.PREPARING -> context.getString(R.string.lyrics_download_preparing, value.found)
            LyricDownloadPhase.DOWNLOADING -> context.getString(R.string.lyrics_download_progress, value.completed, value.total)
            LyricDownloadPhase.CANCELLING -> context.getString(R.string.lyrics_download_cancelling)
            LyricDownloadPhase.COMPLETE -> context.getString(
                if (value.total == 0) R.string.no_tracks_to_play else R.string.lyrics_download_complete)
            LyricDownloadPhase.CANCELLED -> context.getString(R.string.lyrics_download_cancelled)
            LyricDownloadPhase.FAILED -> value.error ?: context.getString(R.string.lyrics_download_search_failed)
        }
        progress.isIndeterminate = value.phase == LyricDownloadPhase.PREPARING ||
            value.phase == LyricDownloadPhase.CANCELLING && value.total == 0
        progress.max = value.total.coerceAtLeast(1)
        progress.progress = value.completed
        current.text = value.current
        counts.text = context.getString(R.string.lyrics_download_counts,
            value.saved, value.kept, value.unmatched, value.failed, value.completed, value.total)
        location.text = context.getString(R.string.lyrics_download_destination, destination())
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).apply {
            setText(if (value.running) R.string.cancel else R.string.close)
            isEnabled = value.phase != LyricDownloadPhase.CANCELLING
        }
        buttons?.apply(options.controlBlocks, options.blockOutline)
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
