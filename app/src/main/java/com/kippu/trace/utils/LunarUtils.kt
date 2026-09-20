package com.kippu.trace.utils

import android.content.Context
import android.icu.util.Calendar
import android.icu.util.ChineseCalendar
import android.icu.util.TimeZone
import com.kippu.trace.R
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class LunarDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val isLeapMonth: Boolean = false,
)

data class LunarMonth(
    val month: Int,
    val isLeap: Boolean = false,
)

/** Lunar calendar conversion backed by Android's ICU implementation (API 24+). */
object LunarUtils {
    const val MIN_YEAR = 1901
    const val MAX_YEAR = 2100
    private val utc = TimeZone.getTimeZone("UTC")

    fun fromMillis(millis: Long): LunarDate {
        val calendar = calendar().apply { timeInMillis = millis }
        return LunarDate(
            year = calendar.get(Calendar.EXTENDED_YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
            isLeapMonth = calendar.get(Calendar.IS_LEAP_MONTH) == 1,
        )
    }

    fun toMillis(date: LunarDate): Long {
        val safeYear = date.year.coerceIn(MIN_YEAR, MAX_YEAR)
        val month = LunarMonth(date.month.coerceIn(1, 12), date.isLeapMonth)
            .takeIf { it in monthsOf(safeYear) }
            ?: LunarMonth(date.month.coerceIn(1, 12))
        val maxDay = daysInMonth(safeYear, month)
        return calendar().apply {
            clear()
            set(Calendar.EXTENDED_YEAR, safeYear)
            set(Calendar.MONTH, month.month - 1)
            set(Calendar.IS_LEAP_MONTH, if (month.isLeap) 1 else 0)
            set(Calendar.DAY_OF_MONTH, date.day.coerceIn(1, maxDay))
        }.timeInMillis
    }

    fun monthsOf(year: Int): List<LunarMonth> = buildList {
        for (month in 1..12) {
            add(LunarMonth(month))
            if (isValidMonth(year, LunarMonth(month, isLeap = true))) {
                add(LunarMonth(month, isLeap = true))
            }
        }
    }

    fun daysInMonth(year: Int, month: LunarMonth): Int {
        val calendar = calendar().apply {
            clear()
            set(Calendar.EXTENDED_YEAR, year)
            set(Calendar.MONTH, month.month - 1)
            set(Calendar.IS_LEAP_MONTH, if (month.isLeap) 1 else 0)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun format(context: Context, millis: Long): String {
        val date = fromMillis(millis)
        val month = if (date.isLeapMonth) {
            context.getString(R.string.lunar_leap_month, date.month)
        } else {
            date.month.toString()
        }
        return context.getString(R.string.lunar_date_format, date.year, month, date.day)
    }

    fun formatCompact(context: Context, millis: Long): String {
        val date = fromMillis(millis)
        val month = if (date.isLeapMonth) {
            context.getString(R.string.lunar_leap_month, date.month)
        } else {
            date.month.toString()
        }
        return context.getString(R.string.lunar_date_compact_format, date.year, month, date.day)
    }

    /** Finds the next occurrence of the same lunar month/day after [today]. */
    fun nextYearly(anchorMillis: Long, interval: Int, today: LocalDate): LocalDate {
        val anchor = fromMillis(anchorMillis)
        var year = anchor.year + interval.coerceAtLeast(1)
        var candidate = resolveAnnualDate(anchor, year)
        while (!candidate.isAfter(today)) {
            year += interval.coerceAtLeast(1)
            candidate = resolveAnnualDate(anchor, year)
        }
        return candidate
    }

    /** Finds the next lunar-month occurrence after [today]. */
    fun nextMonthly(anchorMillis: Long, interval: Int, today: LocalDate): LocalDate {
        val anchor = fromMillis(anchorMillis)
        var currentMillis = anchorMillis
        do {
            currentMillis = plusMonths(currentMillis, interval.coerceAtLeast(1), anchor.day)
        } while (!AnniversaryUtils.date(currentMillis).isAfter(today))
        return AnniversaryUtils.date(currentMillis)
    }

    fun calendarAnniversary(startMillis: Long, today: LocalDate): CalendarAnniversaryResult {
        val start = AnniversaryUtils.date(startMillis)
        if (!today.isAfter(start)) return CalendarAnniversaryResult()

        val startLunar = fromMillis(startMillis)
        val todayLunar = fromMillis(AnniversaryUtils.millis(today))
        val elapsedYears = todayLunar.year - startLunar.year
        val years = if (
            elapsedYears > 0 && resolveAnnualDate(startLunar, todayLunar.year) == today
        ) elapsedYears else 0

        var cursorMillis = startMillis
        var elapsedMonths = 0
        while (true) {
            val next = plusMonths(cursorMillis, 1, startLunar.day)
            if (AnniversaryUtils.date(next).isAfter(today)) break
            cursorMillis = next
            elapsedMonths++
        }
        val months = if (elapsedMonths > 0 && AnniversaryUtils.date(cursorMillis) == today) elapsedMonths else 0
        val days = ChronoUnit.DAYS.between(start, today)
        return CalendarAnniversaryResult(
            years = years,
            months = months,
            weeks = if (start.dayOfWeek == today.dayOfWeek) (days / 7).toInt() else 0,
        )
    }

    private fun resolveAnnualDate(anchor: LunarDate, year: Int): LocalDate {
        val month = LunarMonth(anchor.month, anchor.isLeapMonth)
            .takeIf { it in monthsOf(year) }
            ?: LunarMonth(anchor.month)
        return AnniversaryUtils.date(
            toMillis(
                LunarDate(
                    year = year,
                    month = month.month,
                    day = anchor.day.coerceAtMost(daysInMonth(year, month)),
                    isLeapMonth = month.isLeap,
                )
            )
        )
    }

    private fun plusMonths(millis: Long, months: Int, preferredDay: Int): Long {
        val calendar = calendar().apply {
            timeInMillis = millis
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, months)
        }
        calendar.set(
            Calendar.DAY_OF_MONTH,
            preferredDay.coerceAtMost(calendar.getActualMaximum(Calendar.DAY_OF_MONTH)),
        )
        return calendar.timeInMillis
    }

    private fun isValidMonth(year: Int, month: LunarMonth): Boolean {
        val calendar = calendar().apply {
            clear()
            set(Calendar.EXTENDED_YEAR, year)
            set(Calendar.MONTH, month.month - 1)
            set(Calendar.IS_LEAP_MONTH, if (month.isLeap) 1 else 0)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        calendar.timeInMillis
        return calendar.get(Calendar.EXTENDED_YEAR) == year &&
            calendar.get(Calendar.MONTH) == month.month - 1 &&
            calendar.get(Calendar.IS_LEAP_MONTH) == if (month.isLeap) 1 else 0
    }

    private fun calendar() = ChineseCalendar().apply { timeZone = utc }
}
