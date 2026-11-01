package com.macarmusic.app.widgets

import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.macarmusic.app.MaCarMusicApp
import com.macarmusic.app.R
import com.macarmusic.app.radio.RadioProgram

@androidx.annotation.OptIn(UnstableApi::class)
enum class HomeWidgetAction {
    TOGGLE, PREVIOUS, NEXT, MUTE;

    val intentAction: String get() = "com.macarmusic.app.widget.$name"
    val command: SessionCommand get() = SessionCommand(intentAction, Bundle.EMPTY)

    fun execute(app: MaCarMusicApp, player: Player): SessionResult {
        val error = when {
            this == MUTE -> null
            RadioProgram.isRadio(player.currentMediaItem?.mediaId) && !app.radio.options.value.enabled -> R.string.radio_disabled
            player.mediaItemCount == 0 -> R.string.no_tracks_to_play
            this == NEXT && !player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) -> R.string.widget_no_next
            this == PREVIOUS && (!player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS) ||
                (RadioProgram.isRadio(player.currentMediaItem?.mediaId) && !player.hasPreviousMediaItem())) -> R.string.seek_unavailable
            else -> null
        }
        if (error != null) return SessionResult(SessionError.ERROR_INVALID_STATE,
            Bundle().apply { putString(ERROR_MESSAGE, app.text(error)) })
        when (this) {
            TOGGLE -> if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) player.pause() else {
                if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
                if (player.playbackState == Player.STATE_IDLE || player.playerError != null) player.prepare()
                player.play()
            }
            PREVIOUS -> if (RadioProgram.isRadio(player.currentMediaItem?.mediaId))
                player.seekToPreviousMediaItem() else player.seekToPrevious()
            NEXT -> player.seekToNextMediaItem()
            MUTE -> app.playbackSettings.setMuted(!app.playbackSettings.muted.value)
        }
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    companion object {
        const val ERROR_MESSAGE = "widgetError"
        fun from(action: String?): HomeWidgetAction? = entries.firstOrNull { it.intentAction == action }
    }
}
