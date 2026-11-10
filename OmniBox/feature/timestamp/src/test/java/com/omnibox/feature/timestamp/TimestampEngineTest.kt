// 测试范围：TimestampEngine.parse / format / dateToEpochMillis / secondsOf / constants。
// 这些函数均接收显式时间戳与时区参数（java.time 纯 JVM），测试用固定 epoch 常量，
// 不依赖运行时刻。
// 未覆盖：nowMillis / currentDisplay / diff —— 内部取 System.currentTimeMillis() 的真实值，
// 结果随运行时刻变化，无法确定性断言。
package com.omnibox.feature.timestamp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimestampEngineTest {

    private val epochMillis = 1_700_000_000_000L // 2023-11-14 22:13:20 UTC

    @Test
    fun 十位秒时间戳解析为秒级单位() {
        val p = TimestampEngine.parse("1700000000", "UTC")!!
        assertEquals(epochMillis, p.epochMillis)
        assertEquals(TimestampEngine.Unit.SECONDS, p.unit)
        assertEquals("2023-11-14 22:13:20", p.local)
    }

    @Test
    fun 十三位毫秒时间戳按量级自动识别() {
        val p = TimestampEngine.parse("1700000000000", "UTC")!!
        assertEquals(epochMillis, p.epochMillis)
        assertEquals(TimestampEngine.Unit.MILLIS, p.unit)
        assertEquals("2023-11-14 22:13:20", p.local)
    }

    @Test
    fun 强制指定单位按毫秒解释原始数值() {
        val p = TimestampEngine.parse("1700000000", "UTC", forced = TimestampEngine.Unit.MILLIS)!!
        assertEquals(1_700_000_000L, p.epochMillis)
        assertEquals(TimestampEngine.Unit.MILLIS, p.unit)
    }

    @Test
    fun 负数秒级时间戳() {
        val p = TimestampEngine.parse("-86400", "UTC")!!
        assertEquals(-86_400_000L, p.epochMillis)
        assertEquals(TimestampEngine.Unit.SECONDS, p.unit)
    }

    @Test
    fun 非数字输入返回null() {
        assertNull(TimestampEngine.parse("abc", "UTC"))
        assertNull(TimestampEngine.parse("", "UTC"))
        assertNull(TimestampEngine.parse("2023-11-14", "UTC"))
    }

    @Test
    fun UTC与东八区差异精确到毫秒() {
        // 同一时刻：北京时间 2023-11-15 06:13:20.000 == UTC 2023-11-14 22:13:20.000
        assertEquals(
            epochMillis,
            TimestampEngine.dateToEpochMillis(2023, 11, 15, 6, 13, 20, "Asia/Shanghai")
        )
        assertEquals(
            epochMillis,
            TimestampEngine.dateToEpochMillis(2023, 11, 14, 22, 13, 20, "UTC")
        )
        val utc = TimestampEngine.format(epochMillis, "UTC")
        val sh = TimestampEngine.format(epochMillis, "Asia/Shanghai")
        assertEquals("2023-11-14 22:13:20", utc.local)
        assertEquals("2023-11-14 22:13:20.000", utc.full)
        assertEquals("2023-11-15 06:13:20", sh.local)
        assertEquals("2023-11-15 06:13:20.000", sh.full)
    }

    @Test
    fun epoch零点各字段() {
        val p = TimestampEngine.format(0L, "UTC")
        assertEquals("1970-01-01 00:00:00", p.local)
        assertEquals("1970-01-01 00:00:00.000", p.full)
        assertEquals("1970-01-01T00:00:00Z", p.iso)
    }

    @Test
    fun 闰日2024年2月29日边界() {
        assertEquals(
            1_709_164_800_000L,
            TimestampEngine.dateToEpochMillis(2024, 2, 29, 0, 0, 0, "UTC")
        )
        assertEquals("2024-02-29 00:00:00", TimestampEngine.parse("1709164800", "UTC")!!.local)
    }

    @Test
    fun 非法日期与时刻返回null() {
        assertNull(TimestampEngine.dateToEpochMillis(2023, 2, 29, 0, 0, 0, "UTC")) // 非闰年
        assertNull(TimestampEngine.dateToEpochMillis(2023, 13, 1, 0, 0, 0, "UTC")) // 月份越界
        assertNull(TimestampEngine.dateToEpochMillis(2023, 1, 1, 25, 0, 0, "UTC")) // 小时越界
    }

    @Test
    fun dateToEpoch与format的javaTime往返一致() {
        val ms = TimestampEngine.dateToEpochMillis(2023, 6, 15, 8, 30, 45, "Asia/Shanghai")!!
        assertEquals("2023-06-15 08:30:45", TimestampEngine.format(ms, "Asia/Shanghai").local)
        assertEquals("2023-06-15 00:30:45", TimestampEngine.format(ms, "UTC").local)
        // 毫秒值 → parse（13 位自动识别为 MILLIS）→ 毫秒值
        assertEquals(ms, TimestampEngine.parse(ms.toString(), "UTC")!!.epochMillis)
    }

    @Test
    fun 秒提取向零取整() {
        assertEquals(1234L, TimestampEngine.secondsOf(1_234_567L))
        assertEquals(1L, TimestampEngine.secondsOf(1_999L))
        assertEquals(0L, TimestampEngine.secondsOf(-500L))
    }

    @Test
    fun 时间常量速查表() {
        val day = TimestampEngine.constants.first { it.first.startsWith("1 整天") }
        assertEquals(86_400L, day.second)
        assertEquals(6, TimestampEngine.constants.size)
    }
}
