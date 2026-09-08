package com.vacster.problip.ui

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one-shot midnight delay that rolls the Settings period display over:
 * every day/week/month boundary falls at local midnight, so the exact delay to
 * the next midnight is the only quantity needed. Pure function, fixed instants.
 */
class MidnightRolloverTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun justAfterMidnightWaitsAlmostTheWholeDay() {
        // 2026-09-08T00:01 UTC -> 2026-09-09T00:00 UTC = 23 h 59 m.
        val delay = durationUntilNextLocalMidnight(utc, Instant.parse("2026-09-08T00:01:00Z"))
        assertEquals(23 * 60 + 59, delay.toMinutes())
    }

    @Test
    fun elevenPmWaitsOneHour() {
        val delay = durationUntilNextLocalMidnight(utc, Instant.parse("2026-09-08T23:00:00Z"))
        assertEquals(60, delay.toMinutes())
    }

    @Test
    fun exactlyMidnightWaitsTheFullNextDay() {
        val delay = durationUntilNextLocalMidnight(utc, Instant.parse("2026-09-08T00:00:00Z"))
        assertEquals(24 * 60, delay.toMinutes())
    }

    @Test
    fun theDelayIsAlwaysPositiveAndAtMostOneDay() {
        listOf("T00:00:00Z", "T06:30:00Z", "T12:00:00Z", "T23:59:59.999Z").forEach { t ->
            val d = durationUntilNextLocalMidnight(utc, Instant.parse("2026-09-08$t"))
            assertTrue(d.toMillis() > 0L)
            assertTrue("at most 24h: $d", d.toMillis() <= 24 * 60 * 60 * 1000L)
        }
    }

    @Test
    fun midnightIsLocalNotUtc() {
        // Tallinn (UTC+3 in September 2026): 20:00 UTC is 23:00 local, so the
        // delay to local midnight is exactly one hour.
        val tallinn = ZoneId.of("Europe/Tallinn")
        val delay = durationUntilNextLocalMidnight(tallinn, Instant.parse("2026-09-08T20:00:00Z"))
        assertEquals(60, delay.toMinutes())
    }

    @Test
    fun theEpochMilliOverloadMatchesTheInstantForm() {
        val instant = Instant.parse("2026-09-08T15:45:00Z")
        assertEquals(
            durationUntilNextLocalMidnight(utc, instant),
            durationUntilNextLocalMidnight(utc, instant.toEpochMilli()),
        )
    }
}
