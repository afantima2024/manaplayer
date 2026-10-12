package com.macarmusic.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeTextTest {
    @Test fun timeFormattingHandlesLongAudioAndInvalidValues() {
        assertEquals("0:00", TimeText.format(-1))
        assertEquals("1:05", TimeText.format(65_999))
        assertEquals("1:01:01", TimeText.format(3_661_000))
        assertEquals("27:00:00", TimeText.format(97_200_000))
    }
}
