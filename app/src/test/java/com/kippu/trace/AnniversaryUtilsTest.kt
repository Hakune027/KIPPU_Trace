package com.kippu.trace

import com.kippu.trace.model.*
import com.kippu.trace.utils.AnniversaryUtils
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

class AnniversaryUtilsTest {
    private fun day(value: String) = LocalDate.parse(value)
    private fun millis(value: String) = AnniversaryUtils.millis(day(value))
    private fun event(date: String, repeat: RepeatMode) = DateEvent(
        title = "Test", targetDate = millis(date), isFuture = true,
        mode = DisplayMode.COUNT_DOWN, repeatMode = repeat,
    )

    @Test fun customMilestonesOnlyTriggerOnPositiveMultiples() {
        val start = millis("2026-01-01")
        for (offset in listOf(-1, 0, 99, 101, 199)) {
            assertEquals(0L, AnniversaryUtils.customCount(start, 100, day("2026-01-01").plusDays(offset.toLong())))
        }
        assertEquals(1L, AnniversaryUtils.customCount(start, 100, day("2026-04-11")))
        assertEquals(2L, AnniversaryUtils.customCount(start, 100, day("2026-07-20")))
        assertEquals(0L, AnniversaryUtils.customCount(start, 0, day("2026-07-20")))
    }

    @Test fun calendarMatchesExactDatesAndReportsOverlaps() {
        val start = millis("2026-11-16")
        val anniversary = AnniversaryUtils.calendar(start, day("2027-11-16"))
        assertEquals(1, anniversary.years)
        assertEquals(12, anniversary.months)
        assertEquals(0, AnniversaryUtils.calendar(start, day("2027-11-17")).years)
        assertEquals(0, AnniversaryUtils.calendar(start, day("2027-11-17")).months)
        assertEquals(1, AnniversaryUtils.calendar(start, day("2026-11-23")).weeks)
        assertEquals(0, AnniversaryUtils.calendar(start, day("2026-11-24")).weeks)
        assertEquals(1, AnniversaryUtils.calendar(millis("2024-02-29"), day("2025-02-28")).years)
        assertEquals(1, AnniversaryUtils.calendar(millis("2026-01-31"), day("2026-02-28")).months)
    }

    @Test fun dueDayStaysCountdownAndExpiredNormalEventBecomesAccumulated() {
        val event = event("2026-09-17", RepeatMode.WEEKLY)
        assertEquals(event, AnniversaryUtils.advance(event, day("2026-09-17")))
        assertEquals(event, AnniversaryUtils.advance(event, day("2026-09-16")))
        val normal = event.copy(repeatMode = RepeatMode.NONE)
        val accumulatedNormal = AnniversaryUtils.advance(normal, day("2030-01-01"))
        assertEquals(DisplayMode.ACCUMULATE, accumulatedNormal.mode)
        assertFalse(accumulatedNormal.isFuture)
        assertEquals(normal.targetDate, accumulatedNormal.targetDate)
        val accumulated = event.copy(mode = DisplayMode.ACCUMULATE)
        assertEquals(accumulated, AnniversaryUtils.advance(accumulated, day("2030-01-01")))
    }

    @Test fun monthlyRepeatsPreserveAnchorAcrossShortMonths() {
        val january = event("2026-01-31", RepeatMode.MONTHLY)
        val february = AnniversaryUtils.advance(january, day("2026-02-01"))
        assertEquals(millis("2026-02-28"), february.targetDate)
        val march = AnniversaryUtils.advance(february, day("2026-03-01"))
        assertEquals(millis("2026-03-31"), march.targetDate)
        assertEquals(january.targetDate, march.repeatAnchorDate)
        assertEquals(millis("2027-10-31"), AnniversaryUtils.advance(march, day("2027-10-15")).targetDate)
    }

    @Test fun calendarIntervalsSupportTwoMonthsAndHalfYears() {
        val everyTwoMonths = event("2026-01-31", RepeatMode.MONTHLY).copy(repeatInterval = 2)
        assertEquals(
            millis("2026-03-31"),
            AnniversaryUtils.advance(everyTwoMonths, day("2026-02-01")).targetDate,
        )
        assertEquals(
            millis("2026-07-31"),
            AnniversaryUtils.advance(everyTwoMonths, day("2026-06-01")).targetDate,
        )

        val everyHalfYear = event("2026-01-31", RepeatMode.MONTHLY).copy(repeatInterval = 6)
        assertEquals(
            millis("2026-07-31"),
            AnniversaryUtils.advance(everyHalfYear, day("2026-02-01")).targetDate,
        )
        assertEquals(
            millis("2027-01-31"),
            AnniversaryUtils.advance(everyHalfYear, day("2026-08-01")).targetDate,
        )
    }

    @Test fun weeklyAndYearlyIntervalsUseTheConfiguredMultiplier() {
        val everyTwoWeeks = event("2026-09-01", RepeatMode.WEEKLY).copy(repeatInterval = 2)
        assertEquals(millis("2026-09-29"), AnniversaryUtils.advance(everyTwoWeeks, day("2026-09-15")).targetDate)

        val everyTwoYears = event("2024-02-29", RepeatMode.YEARLY).copy(repeatInterval = 2)
        assertEquals(millis("2026-02-28"), AnniversaryUtils.advance(everyTwoYears, day("2025-01-01")).targetDate)
    }

    @Test fun leapDayRestoredInNextLeapYear() {
        val start = event("2024-02-29", RepeatMode.YEARLY)
        val next = AnniversaryUtils.advance(start, day("2025-02-01"))
        assertEquals(millis("2025-02-28"), next.targetDate)
        assertEquals(millis("2028-02-29"), AnniversaryUtils.advance(next, day("2028-02-01")).targetDate)
    }

    @Test fun catchesUpToStrictlyFutureDateAndIsIdempotent() {
        val weekly = event("2026-09-01", RepeatMode.WEEKLY)
        val next = AnniversaryUtils.advance(weekly, day("2026-09-15"))
        assertEquals(millis("2026-09-22"), next.targetDate)
        assertEquals(next, AnniversaryUtils.advance(next, day("2026-09-15")))
        val custom = weekly.copy(repeatMode = RepeatMode.CUSTOM_DAYS, repeatCustomDays = 10)
        assertEquals(millis("2026-10-11"), AnniversaryUtils.advance(custom, day("2026-10-01")).targetDate)
        val invalid = custom.copy(repeatCustomDays = 0)
        assertEquals(invalid, AnniversaryUtils.advance(invalid, day("2026-10-01")))
    }

    @Test fun utcPickerDatesDoNotShiftInNegativeTimeZones() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(day("2026-09-17"), AnniversaryUtils.date(millis("2026-09-17")))
            assertEquals(millis("2026-09-24"), AnniversaryUtils.advance(
                event("2026-09-17", RepeatMode.WEEKLY), day("2026-09-18")).targetDate)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
