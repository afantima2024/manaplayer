package com.macarmusic.app.ui

import com.macarmusic.app.model.PlaybackTimeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeTextTest {
    @Test fun timeFormattingHandlesLongAudioAndInvalidValues() {
        assertEquals("0:00", TimeText.format(-1))
        assertEquals("1:05", TimeText.format(65_999))
        assertEquals("1:01:01", TimeText.format(3_661_000))
        assertEquals("27:00:00", TimeText.format(97_200_000))
    }

    @Test fun miniPlayerUsesOneStableTotalForBothTimeModes() {
        assertEquals("0:15 / 1:00", TimeText.progress(15_000, 60_000, PlaybackTimeMode.ELAPSED, "--:--"))
        assertEquals("-0:45 / 1:00", TimeText.progress(15_000, 60_000, PlaybackTimeMode.REMAINING, "--:--"))
    }

    @Test fun mainTimeCanToggleWithoutRepeatingOrChangingTotal() {
        assertEquals("0:15", TimeText.position(15_000, 60_000, PlaybackTimeMode.ELAPSED, "--:--"))
        assertEquals("-0:45", TimeText.position(15_000, 60_000, PlaybackTimeMode.REMAINING, "--:--"))
        assertEquals("--:--", TimeText.position(15_000, null, PlaybackTimeMode.REMAINING, "--:--"))
    }

    @Test fun remainingTimeClampsAtTrackBoundaries() {
        assertEquals("-0:00 / 1:00", TimeText.progress(65_000, 60_000, PlaybackTimeMode.REMAINING, "--:--"))
        assertEquals("-1:00 / 1:00", TimeText.progress(-5_000, 60_000, PlaybackTimeMode.REMAINING, "--:--"))
    }

    @Test fun unknownDurationDoesNotInventARemainingTime() {
        assertEquals("--:-- / --:--", TimeText.progress(5_000, null, PlaybackTimeMode.REMAINING, "--:--"))
        assertEquals("0:05 / --:--", TimeText.progress(5_000, 0, PlaybackTimeMode.ELAPSED, "--:--"))
    }
}
