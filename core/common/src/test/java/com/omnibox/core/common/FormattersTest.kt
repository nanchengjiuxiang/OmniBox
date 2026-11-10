// 测试范围：Formatters 的纯 JVM 函数 —— bytes / percent / decibel / degrees / entropyBits。
// 说明：格式化内部使用 Locale.getDefault()，本工程运行环境为 zh-CN（小数点 "."）；
// 若在其它小数分隔符的系统上运行，字面量断言需相应调整。
package com.omnibox.core.common

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.log2
import kotlin.math.pow

class FormattersTest {

    @Test
    fun bytes_小于1K直接输出字节数() {
        assertEquals("0 B", Formatters.bytes(0L))
        assertEquals("512 B", Formatters.bytes(512L))
        assertEquals("1023 B", Formatters.bytes(1023L))
    }

    @Test
    fun bytes_KB与MB保留一档小数() {
        assertEquals("1.0 KB", Formatters.bytes(1024L))
        assertEquals("1.5 KB", Formatters.bytes(1536L))
        assertEquals("1.0 MB", Formatters.bytes(1024L * 1024))
        assertEquals("1.5 MB", Formatters.bytes(1024L * 1024 * 3 / 2))
    }

    @Test
    fun bytes_GB_TB与超TB封顶() {
        assertEquals("1.0 GB", Formatters.bytes(1024L * 1024 * 1024))
        assertEquals("1.0 TB", Formatters.bytes(1024L * 1024 * 1024 * 1024))
        // 超过 TB 量级时单位封顶在 TB
        assertEquals("1024.0 TB", Formatters.bytes(1024.0.pow(5).toLong()))
    }

    @Test
    fun percent按位数格式化() {
        assertEquals("12.3%", Formatters.percent(0.1234))
        assertEquals("50%", Formatters.percent(0.5, 0))
        assertEquals("100.00%", Formatters.percent(1.0, 2))
        assertEquals("0.0%", Formatters.percent(0.0))
    }

    @Test
    fun decibel与degrees格式化() {
        assertEquals("45.7 dBA", Formatters.decibel(45.67))
        assertEquals("46 dBA", Formatters.decibel(45.67, 0))
        assertEquals("90°", Formatters.degrees(90.0))
        assertEquals("45.5°", Formatters.degrees(45.54, 1))
        assertEquals("45.6°", Formatters.degrees(45.56, 1))
        assertEquals("-12.5°", Formatters.degrees(-12.5, 1))
    }

    @Test
    fun entropyBits公式与无效入参() {
        assertEquals(8.0, Formatters.entropyBits(2, 8), 1e-9)
        assertEquals(16 * log2(88.0), Formatters.entropyBits(88, 16), 1e-9)
        assertEquals(0.0, Formatters.entropyBits(1, 100), 0.0) // 池大小 ≤ 1 视为无熵
        assertEquals(0.0, Formatters.entropyBits(0, 10), 0.0)
        assertEquals(0.0, Formatters.entropyBits(10, 0), 0.0) // 长度为 0
    }
}
