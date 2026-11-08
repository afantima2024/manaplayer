package com.macarmusic.app.radio

import android.os.Bundle
import androidx.media3.session.SessionCommand

object RadioCommands {
    val play = SessionCommand("com.macarmusic.app.PLAY_CNR", Bundle.EMPTY)
    val music = SessionCommand("com.macarmusic.app.RETURN_TO_MUSIC", Bundle.EMPTY)
    val startup = SessionCommand("com.macarmusic.app.STARTUP_PLAYBACK", Bundle.EMPTY)
    const val STATION = "station"
    const val SOURCE = "source"
    const val SOURCE_URI = "sourceUri"
    const val RESUME = "resume"
}
