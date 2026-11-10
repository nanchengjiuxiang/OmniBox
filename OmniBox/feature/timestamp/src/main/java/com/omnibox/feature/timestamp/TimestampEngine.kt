package com.omnibox.feature.timestamp

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

/** 设计稿 _3（内容实为时间戳工具，标题模板残留 "Base64 Encoder Detail" 不作为需求依据）。 */
object TimestampEngine {

    val timeZones = listOf(
        "Asia/Shanghai" to "北京时间 (UTC+8)",
        "UTC" to "标准格林威治 (UTC+0)",
        "America/New_York" to "纽约时区 (EST/EDT)",
        "Europe/London" to "伦敦时间 (GMT/BST)",
        "Asia/Tokyo" to "东京时间 (UTC+9)"
    )

    val constants = listOf(
        "1 分钟 (Minute)" to 60L,
        "1 小时 (Hour)" to 3_600L,
        "1 整天 (Day)" to 86_400L,
        "1 周 (7 Days)" to 604_800L,
        "30 天常规月" to 2_592_000L,
        "1 自然年 (365d)" to 31_536_000L
    )

    private val LOCAL_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val FULL_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val ISO_FMT = DateTimeFormatter.ISO_INSTANT

    enum class Unit { SECONDS, MILLIS }

    data class Parsed(
        val epochMillis: Long,
        val unit: Unit,
        val local: String,
        val full: String,
        val iso: String,
        val weekDay: String
    )

    fun nowMillis(): Long = System.currentTimeMillis()

    fun currentDisplay(zoneId: String): String {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis()), ZoneId.of(zoneId))
        return zdt.format(FULL_FMT)
    }

    /** 输入既可为 10 位秒，也可为 13 位毫秒；长度与量级双重判定。 */
    fun parse(input: String, zoneId: String, forced: Unit? = null): Parsed? {
        val raw = input.trim().replace(".", "").toLongOrNull() ?: return null
        val unit = forced ?: if (abs(raw) >= 100_000_000_000L) Unit.MILLIS else Unit.SECONDS
        val millis = if (unit == Unit.MILLIS) raw else raw * 1000L
        return format(millis, zoneId, unit)
    }

    fun format(epochMillis: Long, zoneId: String, unit: Unit = Unit.SECONDS): Parsed {
        val zone = ZoneId.of(zoneId)
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)
        val week = zdt.get(WeekFields.ISO.weekOfYear())
        val dayName = zdt.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
        return Parsed(
            epochMillis = epochMillis,
            unit = unit,
            local = zdt.format(LOCAL_FMT),
            full = zdt.format(FULL_FMT),
            iso = ISO_FMT.format(Instant.ofEpochMilli(epochMillis)),
            weekDay = "第${week}周 · $dayName"
        )
    }

    fun dateToEpochMillis(
        year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, zoneId: String
    ): Long? = runCatching {
        LocalDateTime.of(year, month, day, hour, minute, second)
            .atZone(ZoneId.of(zoneId)).toInstant().toEpochMilli()
    }.getOrNull()

    data class Diff(
        val relative: String,
        val days: Long,
        val hours: Long,
        val workDays: Long,
        val totalSeconds: Long,
        val spanText: String
    )

    /** 时间差与跨度推演：全部基于 Duration 真实计算。 */
    fun diff(epochMillis: Long): Diff {
        val now = nowMillis()
        val delta = Duration.ofMillis(abs(epochMillis - now))
        val days = delta.toDays()
        val hours = delta.toHours()
        val totalSeconds = delta.seconds
        val workDays = (days * 5 / 7)
        val relative = if (epochMillis < now) "约 ${humanSpan(delta)}前" else "约 ${humanSpan(delta)}后"
        val span = "${days} 天 ${(hours - days * 24)} 小时"
        return Diff(relative, days, hours, workDays, totalSeconds, span)
    }

    private fun humanSpan(d: Duration): String = when {
        d.toDays() >= 365 -> "${d.toDays() / 365} 年"
        d.toDays() >= 30 -> "${d.toDays() / 30} 个月"
        d.toDays() >= 1 -> "${d.toDays()} 天"
        d.toHours() >= 1 -> "${d.toHours()} 小时"
        else -> "${d.toMinutes()} 分钟"
    }

    fun secondsOf(epochMillis: Long): Long = epochMillis / 1000L
}
