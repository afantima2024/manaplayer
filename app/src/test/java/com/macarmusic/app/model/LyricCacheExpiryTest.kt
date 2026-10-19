package com.macarmusic.app.model

import org.junit.Assert.*
import org.junit.Test

class LyricCacheExpiryTest {
    @Test fun everyFinitePresetExpiresAtItsExactBoundary() {
        val saved = 1_000L
        for (expiry in LyricCacheExpiry.entries.filter { it.days > 0 }) {
            val boundary = saved + expiry.days * 86_400_000L
            assertFalse(expiry.hasExpired(saved, boundary - 1))
            assertTrue(expiry.hasExpired(saved, boundary))
            assertTrue(expiry.hasExpired(saved, boundary + 1))
        }
    }

    @Test fun neverDisablesAgeExpiryWithoutAClockOverflow() {
        assertFalse(LyricCacheExpiry.NEVER.hasExpired(1, Long.MAX_VALUE))
        assertTrue(LyricCacheExpiry.ONE_DAY.hasExpired(1, Long.MAX_VALUE))
    }

    @Test fun unknownDatesAndClockRollbackCannotDeleteCachedLyrics() {
        for (expiry in LyricCacheExpiry.entries) {
            assertFalse(expiry.hasExpired(0, Long.MAX_VALUE))
            assertFalse(expiry.hasExpired(-1, Long.MAX_VALUE))
            assertFalse(expiry.hasExpired(100, 99))
            assertFalse(expiry.hasExpired(Long.MAX_VALUE, Long.MIN_VALUE))
        }
    }
}
