package com.macarmusic.app.playback

internal class StartupPlayback {
    var pending = false
        private set

    // Retain with the Activity's ViewModel, not the service process or saved instance state.
    inner class ActivityLaunch {
        private var created = false

        fun onCreate(normalLaunch: Boolean, enabled: Boolean) {
            if (created) return
            created = true
            launch(normalLaunch, enabled)
        }
    }

    fun launch(normalLaunch: Boolean, enabled: Boolean) {
        if (normalLaunch) pending = enabled
    }

    fun cancel() { pending = false }

    fun consume(enabled: Boolean): Boolean {
        val resume = pending && enabled
        pending = false
        return resume
    }

    companion object { const val OPEN_PLAYER_ONLY = "com.macarmusic.app.OPEN_PLAYER_ONLY" }
}
