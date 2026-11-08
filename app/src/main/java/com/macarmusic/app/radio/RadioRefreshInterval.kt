package com.macarmusic.app.radio

import com.macarmusic.app.R
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

@Serializable
enum class RadioRefreshInterval(val label: Int, days: Long) {
    MANUAL(R.string.radio_refresh_manual, 0),
    MONTHLY(R.string.radio_refresh_monthly, 30),
    WEEKLY(R.string.radio_refresh_weekly, 7),
    DAILY(R.string.radio_refresh_daily, 1);

    private val intervalMs = TimeUnit.DAYS.toMillis(days)

    fun due(subscriptions: List<RadioSubscription>, lastAttemptAtMs: Long, nowMs: Long): List<RadioSubscription> {
        require(lastAttemptAtMs >= 0 && nowMs >= 0)
        if (this == MANUAL || !elapsed(lastAttemptAtMs, nowMs)) return emptyList()
        return subscriptions.filter { it.enabled && elapsed(it.checkedAtMs, nowMs) }
    }

    private fun elapsed(lastMs: Long, nowMs: Long) =
        lastMs == 0L || nowMs >= lastMs && nowMs - lastMs >= intervalMs
}
