package com.vacster.problip.stats

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields

/**
 * The reward threshold: this many lifetime successful blips unlock Premium
 * locally, permanently. One hundred thousand — not one hundred million.
 */
const val PREMIUM_REWARD_BLIPS: Long = 100_000L

/** The display model the UI reads: the four current buckets plus the reward flag. */
data class BlipStats(
    val todayCount: Long = 0L,
    val weekCount: Long = 0L,
    val monthCount: Long = 0L,
    val totalCount: Long = 0L,
    val earnedPremium: Boolean = false,
)

/**
 * The identity of the current local calendar periods. TODAY is the local day,
 * THIS WEEK is Monday–Sunday (ISO), THIS MONTH is the local calendar month.
 * Keys are compared as strings, so a stored key that is not the current key
 * means the stored bucket belongs to a finished period.
 */
data class PeriodKeys(
    val day: String,
    val week: String,
    val month: String,
) {
    companion object {
        private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

        fun of(clock: Clock): PeriodKeys = of(LocalDate.now(clock))

        /** The current periods under a calendar authority read. */
        fun of(now: CalendarNow): PeriodKeys = of(now.instant.atZone(now.zone).toLocalDate())

        fun of(date: LocalDate): PeriodKeys = PeriodKeys(
            day = date.format(DAY_FORMAT),
            week = weekKey(date),
            month = date.format(MONTH_FORMAT),
        )

        /** ISO-8601 week key, e.g. "2026-W37": Monday is day one, Sunday day seven. */
        fun weekKey(date: LocalDate): String = "%04d-W%02d".format(
            date.get(IsoFields.WEEK_BASED_YEAR),
            date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
        )
    }
}

/**
 * Calendar authority (CORE-002): the current instant and the CURRENT zone are
 * read together every time current period keys are needed, so a timezone
 * change while the process lives is honoured by the very next key resolution
 * without recreating any component. Production resolves
 * [ZoneId.systemDefault] dynamically; tests inject deterministic behaviour.
 */
fun interface CalendarAuthority {
    /** The instant + zone the current [PeriodKeys] resolve against. */
    fun now(): CalendarNow
}

/** One read of the calendar authority: a point in time on a zone. */
data class CalendarNow(val instant: Instant, val zone: ZoneId)

/** The production authority: real time, the live device zone, no caching. */
fun systemCalendarAuthority(): CalendarAuthority = CalendarAuthority {
    CalendarNow(Instant.now(), ZoneId.systemDefault())
}

/**
 * The persisted record: one aggregate per bucket plus the period each bucket
 * belongs to. Timestamps are never stored — four counters and three keys are
 * the whole footprint, and an app update or process death cannot reconstruct
 * what was never written.
 */
data class BlipStatsRecord(
    val dayKey: String = "",
    val todayCount: Long = 0L,
    val weekKey: String = "",
    val weekCount: Long = 0L,
    val monthKey: String = "",
    val monthCount: Long = 0L,
    val totalCount: Long = 0L,
    val earnedPremium: Boolean = false,
)

/**
 * Pure statistics and reward authority: the only place a count moves, a period
 * rolls over, or the 100K reward is granted.
 *
 * Rollover is lazily applied — a bucket whose stored key differs from the
 * current key reads as zero and is rewritten on the first blip of the new
 * period — so no timer, no WorkManager and no midnight alarm exists. TOTAL
 * never rolls over and [PREMIUM_REWARD_BLIPS] is irreversible: once
 * [BlipStatsRecord.earnedPremium] is true, nothing in this object can clear it.
 */
object BlipStatsLogic {

    /**
     * Persisted values are untrusted input: junk and negatives read as zero, and
     * threshold reality heals the reward — a record whose total is already at or
     * past [PREMIUM_REWARD_BLIPS] can never read unearned, even if the persisted
     * earned flag was lost or written false.
     */
    fun sanitize(record: BlipStatsRecord): BlipStatsRecord {
        val total = record.totalCount.coerceAtLeast(0L)
        return record.copy(
            dayKey = record.dayKey.trim(),
            todayCount = record.todayCount.coerceAtLeast(0L),
            weekKey = record.weekKey.trim(),
            weekCount = record.weekCount.coerceAtLeast(0L),
            monthKey = record.monthKey.trim(),
            monthCount = record.monthCount.coerceAtLeast(0L),
            totalCount = total,
            // Irreversible on purpose: an already-earned reward survives any re-read.
            earnedPremium = record.earnedPremium || total >= PREMIUM_REWARD_BLIPS,
        )
    }

    /**
     * A saturating increment: Long.MAX_VALUE stays Long.MAX_VALUE. Overflow must
     * never wrap a lifetime counter back to a small number.
     */
    internal fun incrementSaturating(value: Long): Long =
        if (value == Long.MAX_VALUE) Long.MAX_VALUE else value + 1L

    /**
     * The record after ONE real successful blip at [keys]. Each period bucket
     * restarts at 1 when its stored key is no longer current; TOTAL only ever
     * grows, and the reward latches permanently at [PREMIUM_REWARD_BLIPS].
     * Every counter saturates instead of wrapping.
     */
    fun afterBlip(record: BlipStatsRecord, keys: PeriodKeys): BlipStatsRecord {
        val current = sanitize(record)
        val today = if (current.dayKey == keys.day) {
            incrementSaturating(current.todayCount)
        } else {
            1L
        }
        val week = if (current.weekKey == keys.week) {
            incrementSaturating(current.weekCount)
        } else {
            1L
        }
        val month = if (current.monthKey == keys.month) {
            incrementSaturating(current.monthCount)
        } else {
            1L
        }
        val total = incrementSaturating(current.totalCount.coerceAtLeast(0L))
        return current.copy(
            dayKey = keys.day,
            todayCount = today,
            weekKey = keys.week,
            weekCount = week,
            monthKey = keys.month,
            monthCount = month,
            totalCount = total,
            earnedPremium = current.earnedPremium || total >= PREMIUM_REWARD_BLIPS,
        )
    }

    /**
     * What the UI shows NOW: a bucket whose stored period is over displays zero
     * immediately, before the first blip of the new period arrives. TOTAL never
     * rolls over and the reward never un-latches.
     */
    fun snapshot(record: BlipStatsRecord, keys: PeriodKeys): BlipStats {
        val current = sanitize(record)
        return BlipStats(
            todayCount = if (current.dayKey == keys.day) current.todayCount else 0L,
            weekCount = if (current.weekKey == keys.week) current.weekCount else 0L,
            monthCount = if (current.monthKey == keys.month) current.monthCount else 0L,
            totalCount = current.totalCount,
            earnedPremium = current.earnedPremium,
        )
    }
}
