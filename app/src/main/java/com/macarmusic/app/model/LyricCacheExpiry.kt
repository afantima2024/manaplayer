package com.macarmusic.app.model

enum class LyricCacheExpiry(val days: Int) {
    NEVER(0), ONE_DAY(1), SEVEN_DAYS(7), THIRTY_DAYS(30), NINETY_DAYS(90);

    fun hasExpired(savedAtMs: Long, nowMs: Long): Boolean =
        days > 0 && savedAtMs > 0 && nowMs >= savedAtMs && nowMs - savedAtMs >= days * 86_400_000L
}
