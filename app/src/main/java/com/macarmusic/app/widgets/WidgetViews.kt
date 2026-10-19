package com.macarmusic.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.macarmusic.app.R
import com.macarmusic.app.model.WidgetPlayback
import com.macarmusic.app.model.WidgetPresentation
import com.macarmusic.app.ui.MainActivity
import com.macarmusic.app.ui.TimeText

object WidgetViews {
    fun full(context: Context, kind: HomeWidgetKind, id: Int, state: WidgetPlayback,
        cover: Bitmap?, lyric: String, transparency: Int = 50): RemoteViews = RemoteViews(context.packageName, kind.layout).apply {
        setInt(R.id.widget_background, "setImageAlpha", WidgetPresentation.backgroundAlpha(transparency))
        val open = openPlayer(context)
        setOnClickPendingIntent(R.id.widget_root, open)
        setContentDescription(R.id.widget_root, state.track?.let {
            listOf(it.title, it.artist).filter(String::isNotBlank).joinToString(" - ")
        } ?: context.getString(R.string.widget_select_music))
        setImageViewResource(R.id.widget_play, if (state.playing) R.drawable.ic_pause else R.drawable.ic_play)
        setContentDescription(R.id.widget_play, context.getString(if (state.playing) R.string.pause else R.string.play))
        setOnClickPendingIntent(R.id.widget_play, if (state.track == null) open
            else action(context, kind, id, HomeWidgetAction.TOGGLE))
        if (kind == HomeWidgetKind.PLAY) return@apply

        setTextViewText(R.id.widget_title, state.track?.title?.ifBlank { state.track.fileName }?.take(200)
            ?: context.getString(R.string.widget_select_music))
        setOnClickPendingIntent(R.id.widget_title, open)
        setContentDescription(R.id.widget_previous, context.getString(R.string.previous))
        setContentDescription(R.id.widget_next, context.getString(R.string.next))
        setBoolean(R.id.widget_previous, "setEnabled", state.previous)
        setBoolean(R.id.widget_next, "setEnabled", state.next)
        setInt(R.id.widget_previous, "setImageAlpha", if (state.previous) 255 else 90)
        setInt(R.id.widget_next, "setImageAlpha", if (state.next) 255 else 90)
        setOnClickPendingIntent(R.id.widget_previous, action(context, kind, id, HomeWidgetAction.PREVIOUS))
        setOnClickPendingIntent(R.id.widget_next, action(context, kind, id, HomeWidgetAction.NEXT))
        setImageViewResource(R.id.widget_mute, if (state.muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on)
        setContentDescription(R.id.widget_mute, context.getString(if (state.muted) R.string.unmute_player else R.string.mute_player))
        setOnClickPendingIntent(R.id.widget_mute, action(context, kind, id, HomeWidgetAction.MUTE))
        setOnClickPendingIntent(R.id.widget_progress, open)
        setOnClickPendingIntent(R.id.widget_time, open)
        if (kind == HomeWidgetKind.ARTWORK) {
            setViewVisibility(R.id.widget_artwork, View.VISIBLE)
            if (cover != null) setImageViewBitmap(R.id.widget_artwork, cover)
            else setImageViewResource(R.id.widget_artwork, R.drawable.ic_launcher)
        }
        if (kind == HomeWidgetKind.LYRICS) {
            setOnClickPendingIntent(R.id.widget_lyric, open)
            setOnClickPendingIntent(R.id.widget_lyric_next, open)
        }
        progress(context, this, state)
        if (kind == HomeWidgetKind.LYRICS) lyrics(this, lyric)
    }

    fun dynamic(context: Context, kind: HomeWidgetKind, state: WidgetPlayback, lyric: String): RemoteViews =
        RemoteViews(context.packageName, kind.layout).apply {
            progress(context, this, state)
            if (kind == HomeWidgetKind.LYRICS) lyrics(this, lyric)
        }

    private fun lyrics(views: RemoteViews, text: String) {
        val next = text.substringAfter('\n', "")
        views.setTextViewText(R.id.widget_lyric, text.substringBefore('\n'))
        views.setTextViewText(R.id.widget_lyric_next, next)
        views.setViewVisibility(R.id.widget_lyric_next, if (next.isEmpty()) View.GONE else View.VISIBLE)
    }

    private fun progress(context: Context, views: RemoteViews, state: WidgetPlayback) {
        val time = if (state.failed) context.getString(R.string.widget_playback_failed)
            else context.getString(R.string.widget_progress_time, TimeText.format(state.positionMs),
                state.durationMs.takeIf { it > 0 }?.let(TimeText::format) ?: context.getString(R.string.empty_time))
        views.setTextViewText(R.id.widget_time, time)
        views.setContentDescription(R.id.widget_progress, context.getString(R.string.widget_progress_description, time))
        views.setProgressBar(R.id.widget_progress, WidgetPresentation.PROGRESS_MAX,
            WidgetPresentation.progress(state.positionMs, state.durationMs), false)
    }

    private fun openPlayer(context: Context): PendingIntent = PendingIntent.getActivity(context, 100,
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun action(context: Context, kind: HomeWidgetKind, id: Int, action: HomeWidgetAction): PendingIntent =
        PendingIntent.getBroadcast(context, id,
            Intent(context, kind.provider).setAction(action.intentAction)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
