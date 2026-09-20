package com.kippu.trace.utils

import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.model.RepeatMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** DatePicker stores calendar dates as UTC midnight, independently of the device zone. */
object AnniversaryUtils {
    fun date(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    fun millis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun customCount(target: Long, interval: Int, today: LocalDate = LocalDate.now()): Long {
        val days = ChronoUnit.DAYS.between(date(target), today)
        return if (interval > 0 && days > 0 && days % interval == 0L) days / interval else 0
    }

    fun calendar(target: Long, today: LocalDate = LocalDate.now()): CalendarAnniversaryResult {
        val start = date(target)
        if (!today.isAfter(start)) return CalendarAnniversaryResult()
        val years = today.year - start.year
        val months = (today.year - start.year) * 12 + today.monthValue - start.monthValue
        val days = ChronoUnit.DAYS.between(start, today)
        return CalendarAnniversaryResult(
            years = if (years > 0 && start.plusYears(years.toLong()) == today) years else 0,
            months = if (months > 0 && start.plusMonths(months.toLong()) == today) months else 0,
            weeks = if (start.dayOfWeek == today.dayOfWeek) (days / 7).toInt() else 0,
        )
    }

    fun calendar(event: DateEvent, today: LocalDate = LocalDate.now()): CalendarAnniversaryResult {
        return if (event.isLunar) LunarUtils.calendarAnniversary(event.targetDate, today)
        else calendar(event.targetDate, today)
    }

    /** Keep the due day visible as today; convert normal countdowns or advance repeating ones after expiry. */
    fun advance(event: DateEvent, today: LocalDate = LocalDate.now()): DateEvent {
        if (event.mode != DisplayMode.COUNT_DOWN || !date(event.targetDate).isBefore(today)) return event
        if (event.repeatMode == RepeatMode.NONE) {
            return event.copy(
                mode = DisplayMode.ACCUMULATE,
                isFuture = false,
                repeatAnchorDate = null,
            )
        }
        if (event.repeatMode == RepeatMode.CUSTOM_DAYS && event.repeatCustomDays <= 0) return event
        if (event.repeatMode != RepeatMode.CUSTOM_DAYS && event.repeatInterval <= 0) return event
        val anchorMillis = event.repeatAnchorDate ?: event.targetDate
        val anchor = date(anchorMillis)
        val interval = event.repeatInterval.toLong()
        val next = when (event.repeatMode) {
            RepeatMode.YEARLY -> {
                if (event.isLunar) {
                    LunarUtils.nextYearly(anchorMillis, event.repeatInterval, today)
                } else {
                    val elapsed = (today.year - anchor.year).toLong().coerceAtLeast(0)
                    val count = elapsed / interval
                    anchor.plusYears(count * interval).let {
                        if (it.isAfter(today)) it else anchor.plusYears((count + 1) * interval)
                    }
                }
            }
            RepeatMode.MONTHLY -> {
                if (event.isLunar) {
                    LunarUtils.nextMonthly(anchorMillis, event.repeatInterval, today)
                } else {
                    val elapsed = ChronoUnit.MONTHS.between(anchor.withDayOfMonth(1), today.withDayOfMonth(1)).coerceAtLeast(0)
                    val count = elapsed / interval
                    anchor.plusMonths(count * interval).let {
                        if (it.isAfter(today)) it else anchor.plusMonths((count + 1) * interval)
                    }
                }
            }
            RepeatMode.WEEKLY, RepeatMode.CUSTOM_DAYS -> {
                val days = if (event.repeatMode == RepeatMode.WEEKLY) 7L * interval else event.repeatCustomDays.toLong()
                val count = ChronoUnit.DAYS.between(anchor, today) / days + 1
                anchor.plusDays(count * days)
            }
            RepeatMode.NONE -> return event
        }
        return event.copy(targetDate = millis(next), isFuture = true, repeatAnchorDate = anchorMillis)
    }
}
