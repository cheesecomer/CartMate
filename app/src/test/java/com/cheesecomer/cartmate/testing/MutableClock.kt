package com.cheesecomer.cartmate.testing

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** テストから進められる Clock */
class MutableClock(
    var now: Instant = Instant.parse("2026-10-22T10:00:00Z"),
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}
