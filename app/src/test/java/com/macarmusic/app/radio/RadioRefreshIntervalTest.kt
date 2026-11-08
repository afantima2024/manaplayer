package com.macarmusic.app.radio

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class RadioRefreshIntervalTest {
    private val now = TimeUnit.DAYS.toMillis(1000)
    private val subscription = RadioSubscription("00000000-0000-0000-0000-000000000151",
        "Saved list", "https://example.com/radio.m3u")

    @Test fun manualAndOlderSettingsNeverScheduleAnUpdate() {
        assertEquals(RadioRefreshInterval.MANUAL, Json.decodeFromString<RadioFavorites>("{}").refreshInterval)
        assertTrue(RadioRefreshInterval.MANUAL.due(listOf(subscription), 0, now).isEmpty())
        assertEquals(listOf(RadioRefreshInterval.MANUAL, RadioRefreshInterval.MONTHLY,
            RadioRefreshInterval.WEEKLY, RadioRefreshInterval.DAILY), RadioRefreshInterval.entries)
    }

    @Test fun automaticIntervalsUseExactInclusiveAgeBoundaries() {
        for ((interval, days) in listOf(RadioRefreshInterval.MONTHLY to 30,
            RadioRefreshInterval.WEEKLY to 7, RadioRefreshInterval.DAILY to 1)) {
            val dueAt = now - TimeUnit.DAYS.toMillis(days.toLong())
            val due = subscription.copy(checkedAtMs = dueAt)
            assertEquals(listOf(due), interval.due(listOf(due), 0, now))
            assertTrue(interval.due(listOf(due.copy(checkedAtMs = dueAt + 1)), 0, now).isEmpty())
            assertEquals(listOf(subscription), interval.due(listOf(subscription), 0, now))
        }
    }

    @Test fun disabledAndRecentlyCheckedListsAreNotDownloaded() {
        val disabled = subscription.copy(enabled = false)
        val recent = subscription.copy(id = "00000000-0000-0000-0000-000000000152", checkedAtMs = now - 1)
        assertEquals(listOf(subscription), RadioRefreshInterval.DAILY.due(listOf(disabled, recent, subscription), 0, now))
        assertTrue(RadioRefreshInterval.WEEKLY.due(emptyList(), 0, now).isEmpty())
    }

    @Test fun failedAndCancelledAttemptsDoNotRetryOnEachLaunchOrSettingChange() {
        for ((interval, days) in listOf(RadioRefreshInterval.MONTHLY to 30,
            RadioRefreshInterval.WEEKLY to 7, RadioRefreshInterval.DAILY to 1)) {
            val dueAt = now - TimeUnit.DAYS.toMillis(days.toLong())
            assertTrue(interval.due(listOf(subscription), dueAt + 1, now).isEmpty())
            assertEquals(listOf(subscription), interval.due(listOf(subscription), dueAt, now))
        }
        val failed = subscription.copy(checkedAtMs = now,
            failure = RadioSubscriptionFailure(RadioSubscriptionProblem.NETWORK))
        assertTrue(RadioRefreshInterval.DAILY.due(listOf(failed), 0, now).isEmpty())
        assertTrue(RadioRefreshInterval.MONTHLY.due(listOf(subscription), now - TimeUnit.DAYS.toMillis(7), now).isEmpty())
    }

    @Test fun clockRollbackDoesNotTriggerRepeatedDownloads() {
        assertTrue(RadioRefreshInterval.DAILY.due(listOf(subscription.copy(checkedAtMs = now + 1)), 0, now).isEmpty())
        assertTrue(RadioRefreshInterval.DAILY.due(listOf(subscription), now + 1, now).isEmpty())
        assertTrue(RadioRefreshInterval.DAILY.due(listOf(subscription), Long.MAX_VALUE, now).isEmpty())
    }

    @Test fun refreshChoiceRoundTripsAndInvalidChoicesAreRejected() {
        for (interval in RadioRefreshInterval.entries) {
            val settings = RadioFavorites(refreshInterval = interval)
            assertEquals(settings, Json.decodeFromString<RadioFavorites>(Json.encodeToString(settings)))
        }
        assertThrows(SerializationException::class.java) {
            Json.decodeFromString<RadioFavorites>("""{"refreshInterval":"EVERY_SECOND"}""")
        }
    }
}
