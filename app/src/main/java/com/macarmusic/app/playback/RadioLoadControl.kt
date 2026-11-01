package com.macarmusic.app.playback

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import com.macarmusic.app.radio.RadioProgram
import com.macarmusic.app.radio.RadioTuning

@androidx.annotation.OptIn(UnstableApi::class)
internal class RadioLoadControl(private val options: () -> RadioTuning) : DefaultLoadControl() {
    private val period = Timeline.Period()
    private val window = Timeline.Window()

    private fun radio(parameters: LoadControl.Parameters): Boolean {
        val index = parameters.timeline.getIndexOfPeriod(parameters.mediaPeriodId.periodUid)
        if (index == C.INDEX_UNSET) return false
        parameters.timeline.getPeriod(index, period)
        return RadioProgram.isRadio(parameters.timeline.getWindow(period.windowIndex, window).mediaItem.mediaId)
    }

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean =
        if (!radio(parameters)) super.shouldContinueLoading(parameters)
        else parameters.bufferedDurationUs < options().bufferSeconds * 1_000_000L &&
            allocator.totalBytesAllocated < MAX_RADIO_BYTES

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        if (!radio(parameters)) return super.shouldStartPlayback(parameters)
        val configured = options().startSeconds * 1_000_000L
        // A short HLS live window cannot supply the entire user-selected target.
        val required = RadioBuffer.startUs(configured, parameters.targetLiveOffsetUs)
        return parameters.bufferedDurationUs / parameters.playbackSpeed >= required ||
            allocator.totalBytesAllocated >= MAX_RADIO_BYTES
    }

    companion object { const val MAX_RADIO_BYTES = 8 * 1024 * 1024 }
}

internal object RadioBuffer {
    fun startUs(configuredUs: Long, liveOffsetUs: Long): Long =
        if (liveOffsetUs == C.TIME_UNSET) configuredUs else minOf(configuredUs, (liveOffsetUs / 2).coerceAtLeast(0))
}
