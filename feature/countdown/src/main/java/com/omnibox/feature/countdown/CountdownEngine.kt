package com.omnibox.feature.countdown

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * 纯算法层：时间解析、剩余时间、年度重复周年推算。
 * minSdk 26 原生支持 java.time，无需 desugaring。
 */
object CountdownEngine {

    private const val DAY_MILLIS = 86_400_000L
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val displayFmt = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA)

    data class Remaining(val days: Long, val hours: Long, val minutes: Long, val seconds: Long)

    enum class DatePreset(val label: String) {
        PLUS_30("+30天"), PLUS_100("+100天"), YEAR_END("今年年底"), NEXT_YEAR("次年今日")
    }

    /** 解析 yyyy-MM-dd 与 HH:mm（本机时区），格式非法返回 null。 */
    fun parse(dateText: String, timeText: String): Long? = try {
        val date = LocalDate.parse(dateText.trim(), dateFmt)
        val time = LocalTime.parse(timeText.trim(), timeFmt)
        LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }

    fun format(epochMillis: Long): String =
        displayFmt.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    fun formatDate(epochMillis: Long): String =
        dateFmt.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    fun formatTime(epochMillis: Long): String =
        timeFmt.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    fun remaining(now: Long, target: Long): Remaining {
        val diff = (target - now).coerceAtLeast(0L)
        return Remaining(
            days = diff / DAY_MILLIS,
            hours = (diff % DAY_MILLIS) / 3_600_000L,
            minutes = (diff % 3_600_000L) / 60_000L,
            seconds = (diff % 60_000L) / 1_000L
        )
    }

    fun passedDays(now: Long, target: Long): Long =
        (now - target).coerceAtLeast(0L) / DAY_MILLIS

    /**
     * repeatYearly=true 时返回「下一个周年」的真实时间戳：
     * 用 MonthDay.atYear 处理 2 月 29 日（非闰年自动落到 2 月 28 日），保留原始时刻与时区。
     */
    fun nextOccurrence(targetEpochMillis: Long, repeatYearly: Boolean, now: Long): Long {
        if (!repeatYearly) return targetEpochMillis
        val targetZdt = Instant.ofEpochMilli(targetEpochMillis).atZone(zone)
        val monthDay = MonthDay.from(targetZdt.toLocalDate())
        val time = targetZdt.toLocalTime()
        val nowYear = Instant.ofEpochMilli(now).atZone(zone).year
        for (year in nowYear..nowYear + 2) {
            val candidate = LocalDateTime.of(monthDay.atYear(year), time)
                .atZone(zone).toInstant().toEpochMilli()
            if (candidate >= now) return candidate
        }
        return LocalDateTime.of(monthDay.atYear(nowYear + 2), time)
            .atZone(zone).toInstant().toEpochMilli()
    }

    /** 以 Room 中的 createdAt 为起始、目标时间为终点的真实推进百分比（0..100）。 */
    fun progressPercent(createdAt: Long, target: Long, now: Long): Int {
        val span = target - createdAt
        if (span <= 0L) return 100
        return (((now - createdAt).toDouble() / span.toDouble()) * 100.0)
            .toInt().coerceIn(0, 100)
    }

    fun quickPreset(kind: DatePreset, now: Long): Pair<String, String> {
        val zdt = Instant.ofEpochMilli(now).atZone(zone)
        val epoch = when (kind) {
            DatePreset.PLUS_30 -> zdt.plusDays(30).toInstant().toEpochMilli()
            DatePreset.PLUS_100 -> zdt.plusDays(100).toInstant().toEpochMilli()
            DatePreset.YEAR_END -> {
                var year = zdt.year
                var end = LocalDateTime.of(year, 12, 31, 9, 0).atZone(zone).toInstant().toEpochMilli()
                if (end <= now) {
                    year += 1
                    end = LocalDateTime.of(year, 12, 31, 9, 0).atZone(zone).toInstant().toEpochMilli()
                }
                end
            }
            DatePreset.NEXT_YEAR -> zdt.plusYears(1).toInstant().toEpochMilli()
        }
        return formatDate(epoch) to formatTime(epoch)
    }

    fun defaultDateText(now: Long): Pair<String, String> = quickPreset(DatePreset.PLUS_30, now)

    fun plusDays(now: Long, days: Long): Long = now + days * DAY_MILLIS
}
