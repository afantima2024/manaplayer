package com.macarmusic.app.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.macarmusic.app.R
import com.macarmusic.app.model.PlaybackEdits

@androidx.annotation.OptIn(UnstableApi::class)
object SystemMediaControls {
    val back = SessionCommand("com.macarmusic.app.BACK_10_SECONDS", Bundle.EMPTY)
    val forward = SessionCommand("com.macarmusic.app.FORWARD_10_SECONDS", Bundle.EMPTY)

    fun buttons(context: Context, enabled: Boolean): List<CommandButton> = listOf(
        CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setCustomIconResId(R.drawable.ic_rewind)
            .setDisplayName(context.getString(R.string.rewind))
            .setSessionCommand(back)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .setEnabled(enabled).build(),
        CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
            .setCustomIconResId(R.drawable.ic_forward)
            .setDisplayName(context.getString(R.string.forward))
            .setSessionCommand(forward)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .setEnabled(enabled).build(),
    )

    fun offset(command: SessionCommand): Long? = when (command.customAction) {
        back.customAction -> -10_000
        forward.customAction -> 10_000
        else -> null
    }

    fun canSeek(player: Player): Boolean = player.duration > 0 && player.isCurrentMediaItemSeekable &&
        player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)

    fun seek(player: Player, deltaMs: Long): Boolean {
        if (!canSeek(player)) return false
        val position = PlaybackEdits.seekPosition(player.currentPosition, player.duration, deltaMs) ?: return false
        player.seekTo(position)
        return true
    }
}
