// 测试范围：CountdownEngine 全部纯函数 —— parse / format / formatDate / formatTime /
// remaining / passedDays / nextOccurrence / progressPercent / quickPreset / plusDays。
// 说明：引擎内部使用系统默认时区（zone 为 private，无法显式注入），但所有被测函数的
// 时间入参均为显式值；测试一律用 parse() 构造时刻，使「写入与读出」经过同一时区，
// 断言结果与默认时区无关，且不依赖 System.currentTimeMillis()。
// 注意：parse 使用 SMART 解析器，"2025-02-30" 会被调整为 2 月末而非报错，
// 因此非法日期用例选月份越界与格式错误（这些才会真正返回 null）。
package com.omnibox.feature.countdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountdownEngineTest {

    private fun at(date: String, time: String): Long = CountdownEngine.parse(date, time)!!

    @Test
    fun parse与format系列往返() {
        val ms = at("2025-06-15", "08:30")
        assertEquals("2025-06-15", CountdownEngine.formatDate(ms))
        assertEquals("08:30", CountdownEngine.formatTime(ms))
        assertEquals("2025年6月15日 08:30", CountdownEngine.format(ms))
    }

    @Test
    fun parse非法输入返回null() {
        assertNull(CountdownEngine.parse("2025-13-01", "08:00")) // 月份越界
        assertNull(CountdownEngine.parse("2025/06/15", "08:00")) // 分隔符错误
        assertNull(CountdownEngine.parse("2025-06-15", "25:00")) // 小时越界
        assertNull(CountdownEngine.parse("2025-06-15", "8:30"))  // HH 要求两位
        assertNull(CountdownEngine.parse("abc", "08:00"))
    }

    @Test
    fun remaining按天时分秒分解() {
        // 2 天 4 小时 5 分 6 秒
        val target = 2L * 86_400_000 + 4L * 3_600_000 + 5L * 60_000 + 6_000
        val r = CountdownEngine.remaining(0L, target)
        assertEquals(2L, r.days)
        assertEquals(4L, r.hours)
        assertEquals(5L, r.minutes)
        assertEquals(6L, r.seconds)
    }

    @Test
    fun remaining目标已过或相等时钳制为零() {
        val target = 100_000L
        val past = CountdownEngine.remaining(500_000L, target)
        assertEquals(0L, past.days)
        assertEquals(0L, past.hours)
        assertEquals(0L, past.minutes)
        assertEquals(0L, past.seconds)
        val zero = CountdownEngine.remaining(target, target)
        assertEquals(0L, zero.days)
        assertEquals(0L, zero.seconds)
    }

    @Test
    fun passedDays向下取整且不为负() {
        val target = 0L
        assertEquals(2L, CountdownEngine.passedDays(2L * 86_400_000 + 1_000, target))
        assertEquals(0L, CountdownEngine.passedDays(86_399_000, target)) // 不足一天
        assertEquals(0L, CountdownEngine.passedDays(-1_000, target))     // 目标未到
    }

    @Test
    fun progressPercent钳制到0到100() {
        assertEquals(50, CountdownEngine.progressPercent(0L, 100L, 50L))
        assertEquals(100, CountdownEngine.progressPercent(0L, 100L, 150L)) // 超前
        assertEquals(0, CountdownEngine.progressPercent(0L, 100L, -5L))    // 未开始
        assertEquals(100, CountdownEngine.progressPercent(100L, 100L, 50L)) // 跨度为 0
        assertEquals(100, CountdownEngine.progressPercent(200L, 100L, 50L)) // 起点晚于终点
    }

    @Test
    fun plusDays按固定毫秒计算() {
        assertEquals(259_200_000L, CountdownEngine.plusDays(0L, 3))
        assertEquals(100L, CountdownEngine.plusDays(100L, 0))
        assertEquals(100L + 2L * 86_400_000, CountdownEngine.plusDays(100L, 2))
    }

    @Test
    fun 非年度重复返回原时间戳() {
        val target = at("2025-03-08", "12:00")
        assertEquals(target, CountdownEngine.nextOccurrence(target, false, at("2026-01-01", "00:00")))
    }

    @Test
    fun 同年未到的周年返回自身() {
        val target = at("2025-03-08", "12:00")
        assertEquals(target, CountdownEngine.nextOccurrence(target, true, at("2025-01-01", "00:00")))
        // 恰好等于目标时刻也算「未过」(candidate >= now)
        assertEquals(target, CountdownEngine.nextOccurrence(target, true, target))
    }

    @Test
    fun 已过的周年推到下一年() {
        val target = at("2025-03-08", "12:00")
        assertEquals(
            at("2026-03-08", "12:00"),
            CountdownEngine.nextOccurrence(target, true, at("2025-06-01", "00:00"))
        )
        // 当天已过（晚 1 小时）同样推到下一年
        assertEquals(
            at("2026-03-08", "12:00"),
            CountdownEngine.nextOccurrence(target, true, target + 3_600_000)
        )
    }

    @Test
    fun 跨多年推到最近的未来周年() {
        val target = at("2023-03-08", "12:00")
        assertEquals(
            at("2026-03-08", "12:00"),
            CountdownEngine.nextOccurrence(target, true, at("2026-01-01", "00:00"))
        )
        assertEquals(
            at("2027-03-08", "12:00"),
            CountdownEngine.nextOccurrence(target, true, at("2026-05-01", "00:00"))
        )
    }

    @Test
    fun 闰日周年在非闰年降级到2月28() {
        val leap = at("2024-02-29", "10:30")
        assertEquals(
            at("2025-02-28", "10:30"),
            CountdownEngine.nextOccurrence(leap, true, at("2024-07-01", "00:00"))
        )
        // 2025-02-28 10:30 前一分钟 → 未过
        assertEquals(
            at("2025-02-28", "10:30"),
            CountdownEngine.nextOccurrence(leap, true, at("2025-02-28", "10:29"))
        )
        // 2025-02-28 10:30 后一分钟 → 推到 2026（同样非闰年，2 月 28）
        assertEquals(
            at("2026-02-28", "10:30"),
            CountdownEngine.nextOccurrence(leap, true, at("2025-02-28", "10:31"))
        )
    }

    @Test
    fun quickPreset按日历加天数() {
        val now = at("2025-01-31", "12:00")
        assertEquals(
            "2025-03-02" to "12:00",
            CountdownEngine.quickPreset(CountdownEngine.DatePreset.PLUS_30, now)
        )
        assertEquals(
            "2025-05-11" to "12:00",
            CountdownEngine.quickPreset(CountdownEngine.DatePreset.PLUS_100, now)
        )
        assertEquals(
            "2026-01-31" to "12:00",
            CountdownEngine.quickPreset(CountdownEngine.DatePreset.NEXT_YEAR, now)
        )
    }

    @Test
    fun quickPreset年底预设当天九点前后切换年份() {
        assertEquals(
            "2025-12-31" to "09:00",
            CountdownEngine.quickPreset(CountdownEngine.DatePreset.YEAR_END, at("2025-03-01", "12:00"))
        )
        assertEquals(
            "2025-12-31" to "09:00",
            CountdownEngine.quickPreset(CountdownEngine.DatePreset.YEAR_END, at("2025-12-31", "08:00"))
        )
        // 当年 12-31 09:00 已过 → 次年年底
        assertEquals(
            "2026-12-31" to "09:00",
            CountdownEngine.quickPreset(CountdownEngine.DatePreset.YEAR_END, at("2025-12-31", "10:00"))
        )
    }
}
