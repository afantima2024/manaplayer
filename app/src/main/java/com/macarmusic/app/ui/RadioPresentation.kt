package com.macarmusic.app.ui

import com.macarmusic.app.radio.RadioProgram

internal object RadioPresentation {
    fun stationId(
        mediaId: String?,
        savedId: String?,
        active: Boolean,
        controllerConnected: Boolean,
        playbackReady: Boolean,
    ): String? = mediaId?.takeIf(RadioProgram::isRadio)
        // Service readiness can arrive before the controller receives its restored timeline.
        ?: savedId.takeIf { active && (!controllerConnected || !playbackReady || mediaId == null) }

    fun radioControls(active: Boolean, preparing: Boolean, stationAvailable: Boolean): Boolean =
        active || preparing || stationAvailable
}
