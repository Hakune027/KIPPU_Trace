package com.kippu.trace.utils

import android.content.Context
import com.kippu.trace.R
import com.kippu.trace.model.AnniversaryType
import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

data class RelativeTimeResult(
    val years: Int = 0,
    val months: Int = 0,
    val weeks: Int = 0,
    val days: Int = 0
)

data class DetailedTimeResult(
    val hours: Long = 0,
    val minutes: Long = 0,
    val seconds: Long = 0
)

data class CalendarAnniversaryResult(
    val years: Int = 0,
    val months: Int = 0,
    val weeks: Int = 0
)

data class AnniversaryCounterText(
    val prefix: String,
    val value: String,
    val suffix: String,
)

data class AnniversaryTextResult(
    val text: String,
    val counters: List<AnniversaryCounterText> = emptyList(),
)

object TimeUtils {

    // 正确处理时区

    /**
     * 依据 日期变更时间 计算当前所属的天。
     * 若切换时间设为 T 分钟（相对 0 点），则 `now - T` 再取本地日期，
     * rolloverMinutes = 0 时退化为 LocalDate.now()。
     */
    fun getEffectiveToday(
        nowMillis: Long = System.currentTimeMillis(),
        rolloverMinutes: Int = 0,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalDate {
        return Instant.ofEpochMilli(nowMillis)
            .atZone(zone)
            .minusMinutes(rolloverMinutes.toLong())
            .toLocalDate()
    }

    // 两个日历日之间相差的天数
    fun getDayCount(today: LocalDate, targetDate: LocalDate): Long {
        return abs(ChronoUnit.DAYS.between(today, targetDate))
    }

    // 返回严格晚于 nowMillis 的下一个 日期变更时间 时间戳
    fun nextRolloverMillis(
        nowMillis: Long = System.currentTimeMillis(),
        rolloverMinutes: Int = 0,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        var trigger = now.toLocalDate()
            .atTime(rolloverMinutes / 60, rolloverMinutes % 60)
            .atZone(zone)
        if (!trigger.isAfter(now)) {
            trigger = trigger.plusDays(1)
        }
        return trigger.toInstant().toEpochMilli()
    }

    fun getRelativeTime(
        targetDateMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        rolloverMinutes: Int = 0,
    ): RelativeTimeResult {
        val today = getEffectiveToday(nowMillis, rolloverMinutes)
        return getRelativeTime(targetDateMillis, today)
    }

    fun getRelativeTime(targetDateMillis: Long, today: LocalDate): RelativeTimeResult {
        val targetDate = Instant.ofEpochMilli(targetDateMillis)
            .atZone(ZoneId.of("UTC"))
            .toLocalDate()
        
        val start = if (today.isBefore(targetDate)) today else targetDate
        val end = if (today.isBefore(targetDate)) targetDate else today
        
        val period = Period.between(start, end)
        
        val totalDays = period.days
        val weeks = totalDays / 7
        val days = totalDays % 7
        
        return RelativeTimeResult(
            years = period.years,
            months = period.months,
            weeks = weeks,
            days = days
        )
    }

    fun formatRelativeTime(context: Context, result: RelativeTimeResult): String {
        val parts = mutableListOf<String>()
        if (result.years > 0) parts.add(context.getString(R.string.time_years, result.years))
        if (result.months > 0) parts.add(context.getString(R.string.time_months, result.months))
        if (result.weeks > 0) parts.add(context.getString(R.string.time_weeks, result.weeks))
        if (result.days > 0) parts.add(context.getString(R.string.time_days_unit, result.days))

        if (parts.isEmpty()) return context.getString(R.string.time_today)
        return parts.joinToString(context.getString(R.string.time_separator))
    }

    // 获取详情页实时时分秒

    fun getDetailedTime(targetDateMillis: Long): DetailedTimeResult {
        val systemZone = ZoneId.systemDefault()
        
        // 当前本地时间
        val now = ZonedDateTime.now(systemZone)
        
        // 将 DatePicker 的 UTC 午夜视为本地午夜
        val targetMidnight = Instant.ofEpochMilli(targetDateMillis)
            .atZone(ZoneId.of("UTC"))
            .toLocalDate()
            .atStartOfDay(systemZone)
            
        val duration = if (now.isBefore(targetMidnight)) {
            Duration.between(now, targetMidnight)
        } else {
            Duration.between(targetMidnight, now)
        }
        
        val totalSeconds = duration.seconds
        val hours = (totalSeconds % 86400) / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return DetailedTimeResult(hours, minutes, seconds)
    }

    // 将 自 0 点起算的分钟数 格式化为 HH:mm
    fun formatMinutesOfDay(minutes: Int): String {
        val safe = minutes.coerceIn(0, 1439)
        return String.format(Locale.getDefault(), "%02d:%02d", safe / 60, safe % 60)
    }

    // 累计模式下的纪念日显示文字；返回 null 表示用普通天数
    fun getAnniversaryText(
        context: Context,
        event: DateEvent,
        today: LocalDate = getEffectiveToday(rolloverMinutes = event.dayChangeMinutes),
    ): AnniversaryTextResult? {
        if (event.mode != DisplayMode.ACCUMULATE) return null
        return when (event.anniversaryType) {
            AnniversaryType.CUSTOM_DAYS -> {
                val count = AnniversaryUtils.customCount(event.targetDate, event.customDays, today)
                if (count <= 0) {
                    null
                } else {
                    event.anniversaryMessage.takeIf { it.isNotBlank() }?.let(::AnniversaryTextResult)
                        ?: run {
                            val daysToken = event.customDays.toString()
                            val prefix = context.getString(R.string.anniversary_custom_prefix)
                                .replace("{days}", daysToken)
                            val countText = count.toString()
                            val suffix = context.getString(R.string.anniversary_custom_suffix)
                                .replace("{days}", daysToken)
                            AnniversaryTextResult(
                                text = prefix + countText + suffix,
                                counters = listOf(AnniversaryCounterText(prefix, countText, suffix)),
                            )
                        }
                }
            }
            AnniversaryType.CALENDAR -> {
                val result = AnniversaryUtils.calendar(event.targetDate, today)
                val counters = buildList {
                    if (event.showYear && result.years > 0) {
                        add(AnniversaryCounterText(
                            context.getString(R.string.anniversary_counter_year_prefix),
                            result.years.toString(),
                            context.getString(R.string.anniversary_counter_year_suffix),
                        ))
                    }
                    if (event.showMonth && result.months > 0) {
                        add(AnniversaryCounterText(
                            context.getString(R.string.anniversary_counter_month_prefix),
                            result.months.toString(),
                            context.getString(R.string.anniversary_counter_month_suffix),
                        ))
                    }
                    if (event.showWeek && result.weeks > 0) {
                        add(AnniversaryCounterText(
                            context.getString(R.string.anniversary_counter_week_prefix),
                            result.weeks.toString(),
                            context.getString(R.string.anniversary_counter_week_suffix),
                        ))
                    }
                }
                if (counters.isEmpty()) {
                    null
                } else {
                    event.anniversaryMessage.takeIf { it.isNotBlank() }
                        ?.let(::AnniversaryTextResult)
                        ?: AnniversaryTextResult(
                            text = counters.joinToString(context.getString(R.string.time_separator)) {
                                it.prefix + it.value + it.suffix
                            },
                            counters = counters,
                        )
                }
            }
            AnniversaryType.NONE -> null
        }
    }
}
