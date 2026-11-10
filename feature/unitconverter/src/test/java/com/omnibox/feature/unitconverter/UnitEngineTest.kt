// 测试范围：UnitEngine 的纯 JVM 函数 —— convert / format / ratio / references /
// defaultFrom / defaultTo / units 单位表。全部基于 java.math.BigDecimal，纯 JVM。
// 说明：引擎的 convert 对非法输入没有 Result/null 通道（BigDecimal 入参本身不会解析失败，
// 除零不可能发生），唯一 null 出现在 format(null)——按真实行为断言为 "—"；
// 负数输入（如负温度）按数学定义正常换算。
package com.omnibox.feature.unitconverter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal

class UnitEngineTest {

    private fun unit(kind: UnitKind, id: String): UnitDef =
        UnitEngine.units.getValue(kind).first { it.id == id }

    private fun conv(kind: UnitKind, fromId: String, toId: String, value: String): Double =
        UnitEngine.convert(BigDecimal(value), unit(kind, fromId), unit(kind, toId))!!.toDouble()

    private fun roundTrip(kind: UnitKind, fromId: String, toId: String, value: String): Double {
        val step = UnitEngine.convert(BigDecimal(value), unit(kind, fromId), unit(kind, toId))!!
        val back = UnitEngine.convert(step, unit(kind, toId), unit(kind, fromId))!!
        return back.toDouble()
    }

    @Test
    fun 长度往返误差小于1e减9() {
        assertEquals(123.456, roundTrip(UnitKind.LENGTH, "m", "ft", "123.456"), 1e-9)
        assertEquals(100.0, roundTrip(UnitKind.LENGTH, "km", "mi", "100"), 1e-9)
        assertEquals(12.0, roundTrip(UnitKind.LENGTH, "in", "cm", "12"), 1e-9)
        assertEquals(500.0, roundTrip(UnitKind.LENGTH, "mm", "yd", "500"), 1e-9)
    }

    @Test
    fun 重量往返误差小于1e减9() {
        assertEquals(75.0, roundTrip(UnitKind.WEIGHT, "kg", "lb", "75"), 1e-9)
        assertEquals(1.0, roundTrip(UnitKind.WEIGHT, "kg", "oz", "1"), 1e-9)
        assertEquals(0.5, roundTrip(UnitKind.WEIGHT, "t", "g", "0.5"), 1e-9)
    }

    @Test
    fun 温度绝对基准_摄氏转开尔文与华氏() {
        assertEquals(273.15, conv(UnitKind.TEMPERATURE, "c", "k", "0"), 1e-9)   // 0℃ = 273.15K
        assertEquals(373.15, conv(UnitKind.TEMPERATURE, "c", "k", "100"), 1e-9) // 100℃ = 373.15K
        assertEquals(32.0, conv(UnitKind.TEMPERATURE, "c", "f", "0"), 1e-9)     // 0℃ = 32℉
        assertEquals(212.0, conv(UnitKind.TEMPERATURE, "c", "f", "100"), 1e-9)  // 100℃ = 212℉
    }

    @Test
    fun 温度反向换算与绝对零度() {
        assertEquals(0.0, conv(UnitKind.TEMPERATURE, "f", "c", "32"), 1e-9)
        assertEquals(100.0, conv(UnitKind.TEMPERATURE, "f", "c", "212"), 1e-9)
        assertEquals(-273.15, conv(UnitKind.TEMPERATURE, "k", "c", "0"), 1e-9) // 0K = -273.15℃
    }

    @Test
    fun 华氏摄氏在负40度重合() {
        assertEquals(-40.0, conv(UnitKind.TEMPERATURE, "c", "f", "-40"), 1e-9)
        assertEquals(-40.0, conv(UnitKind.TEMPERATURE, "f", "c", "-40"), 1e-9)
    }

    @Test
    fun 温度往返误差小于1e减9() {
        assertEquals(25.0, roundTrip(UnitKind.TEMPERATURE, "c", "f", "25"), 1e-9)
        assertEquals(98.6, roundTrip(UnitKind.TEMPERATURE, "f", "k", "98.6"), 1e-9)
    }

    @Test
    fun 数据存储二进制约数() {
        assertEquals(8.0, conv(UnitKind.DATA, "b", "bit", "1"), 1e-9)
        assertEquals(1024.0, conv(UnitKind.DATA, "kb", "b", "1"), 1e-9)
        assertEquals(1073741824.0, conv(UnitKind.DATA, "gb", "b", "1"), 1e-9)
    }

    @Test
    fun 负数与巨大输入仍返回结果() {
        assertEquals(268.15, conv(UnitKind.TEMPERATURE, "c", "k", "-5"), 1e-9)
        val huge = UnitEngine.convert(
            BigDecimal("1e30"), unit(UnitKind.LENGTH, "m"), unit(UnitKind.LENGTH, "km")
        )
        assertNotNull(huge)
    }

    @Test
    fun format精度档位与空值占位() {
        assertEquals("3.14", UnitEngine.format(BigDecimal("3.14159265"), 2))
        assertEquals("3.1416", UnitEngine.format(BigDecimal("3.14159265"), 4)) // HALF_EVEN 进位
        assertEquals("3", UnitEngine.format(BigDecimal("3.14159265"), 0))
        assertEquals("2.5", UnitEngine.format(BigDecimal("2.500"), 2)) // 去尾随零
        assertEquals("—", UnitEngine.format(null, 3))                  // null 的真实占位
    }

    @Test
    fun 两级比例透视() {
        assertEquals("100", UnitEngine.ratio(unit(UnitKind.LENGTH, "m"), unit(UnitKind.LENGTH, "cm")))
        assertEquals("1000", UnitEngine.ratio(unit(UnitKind.LENGTH, "km"), unit(UnitKind.LENGTH, "m")))
        assertEquals("0.001", UnitEngine.ratio(unit(UnitKind.WEIGHT, "g"), unit(UnitKind.WEIGHT, "kg")))
    }

    @Test
    fun 速查基准按单位表实时计算() {
        assertEquals(
            Triple("1 米 (m) =", "0.001", "千米 (km)"),
            UnitEngine.references(UnitKind.LENGTH).first()
        )
        assertEquals(
            Triple("1 千克 (kg) =", "1000", "克 (g)"),
            UnitEngine.references(UnitKind.WEIGHT).first()
        )
    }

    @Test
    fun 默认单位选择() {
        assertEquals("m", UnitEngine.defaultFrom(UnitKind.LENGTH).id)
        assertEquals("km", UnitEngine.defaultTo(UnitKind.LENGTH).id)
        assertEquals("c", UnitEngine.defaultFrom(UnitKind.TEMPERATURE).id)
        assertEquals("f", UnitEngine.defaultTo(UnitKind.TEMPERATURE).id)
    }
}
