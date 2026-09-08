package com.vacster.problip.stats

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure statistics and reward authority: buckets, rollover, sanitization and
 * the irreversible 100K Premium. All dates are fixed — no test reads the
 * machine's clock.
 */
class BlipStatsTest {

    /** A fixed Monday: 2026-09-07 is a Monday (ISO week 2026-W37). */
    private val monday = LocalDate.of(2026, 9, 7)

    private fun keys(date: LocalDate): PeriodKeys = PeriodKeys.of(date)

    @Test
    fun rewardThresholdIsExactlyOneHundredThousand() {
        assertEquals(100_000L, PREMIUM_REWARD_BLIPS)
    }

    @Test
    fun oneBlipIncrementsEveryCurrentBucket() {
        val record = BlipStatsLogic.afterBlip(BlipStatsRecord(), keys(monday))
        assertEquals(1L, record.todayCount)
        assertEquals(1L, record.weekCount)
        assertEquals(1L, record.monthCount)
        assertEquals(1L, record.totalCount)
    }

    @Test
    fun dayRolloverRestartsOnlyTheDayBucket() {
        var record = BlipStatsRecord()
        val tuesday = monday.plusDays(1)
        repeat(5) { record = BlipStatsLogic.afterBlip(record, keys(monday)) }
        val after = BlipStatsLogic.afterBlip(record, keys(tuesday))
        assertEquals(1L, after.todayCount)
        // Same ISO week and month: those buckets keep accumulating.
        assertEquals(6L, after.weekCount)
        assertEquals(6L, after.monthCount)
        assertEquals(6L, after.totalCount)
    }

    @Test
    fun mondayWeekRolloverRestartsTheWeekBucket() {
        // 2026-09-13 is the Sunday of week 2026-W37; 09-14 starts W38.
        val sunday = LocalDate.of(2026, 9, 13)
        var record = BlipStatsRecord()
        repeat(3) { record = BlipStatsLogic.afterBlip(record, keys(sunday)) }
        val after = BlipStatsLogic.afterBlip(record, keys(sunday.plusDays(1)))
        assertEquals("2026-W38", after.weekKey)
        assertEquals(1L, after.weekCount)
        assertEquals(4L, after.totalCount)
    }

    @Test
    fun sundayAndMondayOfTheSameWeekShareTheKey() {
        assertEquals(
            PeriodKeys.weekKey(LocalDate.of(2026, 9, 7)),
            PeriodKeys.weekKey(LocalDate.of(2026, 9, 13)),
        )
    }

    @Test
    fun monthRolloverRestartsTheMonthBucket() {
        val endOfMonth = LocalDate.of(2026, 9, 30)
        var record = BlipStatsRecord()
        repeat(2) { record = BlipStatsLogic.afterBlip(record, keys(endOfMonth)) }
        val after = BlipStatsLogic.afterBlip(record, keys(LocalDate.of(2026, 10, 1)))
        assertEquals("2026-10", after.monthKey)
        assertEquals(1L, after.monthCount)
        assertEquals(3L, after.totalCount)
    }

    @Test
    fun yearRolloverRestartsDayAndMonthAndCarriesTotal() {
        val dec31 = LocalDate.of(2026, 12, 31)
        var record = BlipStatsRecord()
        repeat(4) { record = BlipStatsLogic.afterBlip(record, keys(dec31)) }
        val jan1 = LocalDate.of(2027, 1, 1)
        val after = BlipStatsLogic.afterBlip(record, keys(jan1))
        assertEquals("2027-01-01", after.dayKey)
        // ISO weeks cross the year boundary: Jan 1 (Fri) is still week 2026-W53,
        // so the week bucket does NOT restart at midnight on Dec 31.
        assertEquals("2026-W53", after.weekKey)
        assertEquals("2027-01", after.monthKey)
        assertEquals(1L, after.todayCount)
        assertEquals(5L, after.weekCount)
        assertEquals(1L, after.monthCount)
        assertEquals(5L, after.totalCount)
    }

    @Test
    fun aStalePeriodDisplaysZeroBeforeTheNextBlip() {
        // Ten blips yesterday, none today: today reads 0 now, becomes 1 later.
        val yesterday = BlipStatsLogic.afterBlip(
            BlipStatsLogic.afterBlip(
                BlipStatsLogic.afterBlip(BlipStatsRecord(), keys(monday.minusDays(1))),
                keys(monday.minusDays(1)),
            ),
            keys(monday.minusDays(1)),
        )
        val display = BlipStatsLogic.snapshot(yesterday, keys(monday))
        assertEquals(0L, display.todayCount)
        assertEquals(3L, display.totalCount)
        // And the first blip of the new period makes it exactly 1.
        val after = BlipStatsLogic.afterBlip(yesterday, keys(monday))
        assertEquals(1L, after.todayCount)
        assertEquals(4L, after.totalCount)
    }

    @Test
    fun negativeOrCorruptPersistedValuesSanitizeToZero() {
        val sanitized = BlipStatsLogic.sanitize(
            BlipStatsRecord(
                dayKey = " 2026-09-07 ",
                todayCount = -5L,
                weekCount = Long.MIN_VALUE,
                monthCount = -1L,
                totalCount = -1000L,
            ),
        )
        assertEquals(0L, sanitized.todayCount)
        assertEquals(0L, sanitized.weekCount)
        assertEquals(0L, sanitized.monthCount)
        assertEquals(0L, sanitized.totalCount)
        assertEquals("2026-09-07", sanitized.dayKey)
    }

    @Test
    fun countsNeverTurnNegativeAcrossSanitizationAndBlips() {
        val corrupt = BlipStatsRecord(todayCount = -1L, totalCount = Long.MAX_VALUE)
        val after = BlipStatsLogic.afterBlip(corrupt, keys(monday))
        assertTrue(after.todayCount >= 0L)
        // Long overflow is coerced, not wrapped.
        assertTrue(after.totalCount > 0L)
    }

    @Test
    fun saturatingIncrementNeverWraps() {
        assertEquals(Long.MAX_VALUE, BlipStatsLogic.incrementSaturating(Long.MAX_VALUE))
        // MAX - 1 + 1 = MAX exactly; never a wrap.
        assertEquals(Long.MAX_VALUE, BlipStatsLogic.incrementSaturating(Long.MAX_VALUE - 1L))
        assertEquals(0L, BlipStatsLogic.incrementSaturating(-1L))
    }

    @Test
    fun todayAtLongMaxStaysLongMaxNotOne() {
        val record = BlipStatsRecord(
            dayKey = PeriodKeys.of(monday).day,
            todayCount = Long.MAX_VALUE,
        )
        val after = BlipStatsLogic.afterBlip(record, keys(monday))
        assertEquals(Long.MAX_VALUE, after.todayCount)
    }

    @Test
    fun weekAtLongMaxStaysLongMaxNotOne() {
        val record = BlipStatsRecord(
            weekKey = PeriodKeys.of(monday).week,
            weekCount = Long.MAX_VALUE,
        )
        val after = BlipStatsLogic.afterBlip(record, keys(monday))
        assertEquals(Long.MAX_VALUE, after.weekCount)
    }

    @Test
    fun monthAtLongMaxStaysLongMaxNotOne() {
        val record = BlipStatsRecord(
            monthKey = PeriodKeys.of(monday).month,
            monthCount = Long.MAX_VALUE,
        )
        val after = BlipStatsLogic.afterBlip(record, keys(monday))
        assertEquals(Long.MAX_VALUE, after.monthCount)
    }

    @Test
    fun totalAtLongMaxStaysLongMaxNotOne() {
        val record = BlipStatsRecord(totalCount = Long.MAX_VALUE)
        val after = BlipStatsLogic.afterBlip(record, keys(monday))
        assertEquals(Long.MAX_VALUE, after.totalCount)
        // Snapshot displays the same saturated value, never a wrapped one.
        assertEquals(Long.MAX_VALUE, BlipStatsLogic.snapshot(after, keys(monday)).totalCount)
    }

    @Test
    fun thresholdRealityHealsAMissingEarnedFlag() {
        // Total at the threshold but the persisted earned flag is absent/false:
        // sanitization must heal the reward, because an earned total cannot
        // read unearned.
        val healed = BlipStatsLogic.sanitize(
            BlipStatsRecord(totalCount = PREMIUM_REWARD_BLIPS, earnedPremium = false),
        )
        assertTrue(healed.earnedPremium)
        // And far past it:
        assertTrue(
            BlipStatsLogic.sanitize(
                BlipStatsRecord(totalCount = 250_000L, earnedPremium = false),
            ).earnedPremium,
        )
    }

    @Test
    fun aTotalBelowTheThresholdDoesNotHealEarned() {
        assertFalse(
            BlipStatsLogic.sanitize(
                BlipStatsRecord(totalCount = 99_999L, earnedPremium = false),
            ).earnedPremium,
        )
    }

    @Test
    fun ninetyNineThousandNineHundredNinetyNineBlipsDoNotEarnPremium() {
        var record = BlipStatsRecord(totalCount = 99_998L)
        record = BlipStatsLogic.afterBlip(record, keys(monday))
        assertEquals(99_999L, record.totalCount)
        assertFalse(record.earnedPremium)
    }

    @Test
    fun theHundredThousandthBlipEarnsPremium() {
        var record = BlipStatsRecord(totalCount = 99_999L)
        record = BlipStatsLogic.afterBlip(record, keys(monday))
        assertEquals(100_000L, record.totalCount)
        assertTrue(record.earnedPremium)
    }

    @Test
    fun beyondTheThresholdRemainsEarned() {
        var record = BlipStatsRecord(totalCount = 100_000L, earnedPremium = true)
        record = BlipStatsLogic.afterBlip(record, keys(monday))
        assertEquals(100_001L, record.totalCount)
        assertTrue(record.earnedPremium)
    }

    @Test
    fun earnedIsIrreversibleThroughRolloverAndSanitization() {
        val earned = BlipStatsRecord(
            dayKey = "2026-01-01",
            todayCount = -50L, // corrupt, but the reward is a separate fact
            totalCount = 100_000L,
            earnedPremium = true,
        )
        // Rollover next year:
        val rolled = BlipStatsLogic.afterBlip(earned, keys(LocalDate.of(2027, 6, 1)))
        assertTrue(rolled.earnedPremium)
        // Sanitization keeps the latch:
        assertTrue(BlipStatsLogic.sanitize(rolled).earnedPremium)
        // Snapshot display keeps it:
        assertTrue(BlipStatsLogic.snapshot(rolled, keys(LocalDate.of(2028, 1, 1))).earnedPremium)
    }

    @Test
    fun anEarnedRecordNeverUnLatchesThroughADifferentClockDay() {
        val earned = BlipStatsLogic.afterBlip(
            BlipStatsRecord(totalCount = 99_999L),
            keys(monday),
        )
        // A blip in every later period keeps it earned, never recomputes it away.
        var current = earned
        var date = monday
        repeat(400) {
            date = date.plusDays(1)
            current = BlipStatsLogic.afterBlip(current, keys(date))
            assertTrue(current.earnedPremium)
        }
        assertEquals(100_400L, current.totalCount)
    }
}
