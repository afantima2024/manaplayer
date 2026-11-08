package com.macarmusic.app.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProblemTest {
    private val problem = PlaybackProblem("station", "https://radio.example/one", "Station", "IO_ERROR", true)

    @Test fun sameItemRetainsItsRecoveryAcrossUiChanges() {
        assertTrue(problem.matches("station", "https://radio.example/one"))
    }

    @Test fun anotherStationDoesNotInheritTheFailure() {
        assertFalse(problem.matches("another", "https://radio.example/one"))
    }

    @Test fun anotherAddressDoesNotInheritTheFailure() {
        assertFalse(problem.matches("station", "https://radio.example/two"))
    }

    @Test fun clearingTheQueueClearsItsRecovery() {
        assertFalse(problem.matches(null, null))
    }
}
