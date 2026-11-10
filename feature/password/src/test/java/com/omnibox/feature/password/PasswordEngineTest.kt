// 测试范围：PasswordEngine 的纯 JVM 函数 —— generate / generateBatch / entropyBits / pool /
// poolSize / categories / strengthLabel / strengthProgress / crackEstimate /
// patternWarnings / hasPattern。
// 未覆盖：无。引擎仅依赖 java.security.SecureRandom 与 kotlin.math，全部纯 JVM 可测。
package com.omnibox.feature.password

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log2

class PasswordEngineTest {

    private val default = PasswordConfig()

    @Test
    fun 生成长度等于请求长度() {
        for (len in listOf(1, 8, 16, 32, 128)) {
            val out = PasswordEngine.generate(PasswordConfig(length = len))
            assertEquals("长度=$len 时输出长度不符", len, out.length)
        }
        // forceAllCategories 且分类数超过请求长度时，结果长度仍等于请求长度
        assertEquals(3, PasswordEngine.generate(PasswordConfig(length = 3)).length)
        assertEquals(1, PasswordEngine.generate(PasswordConfig(length = 1)).length)
    }

    @Test
    fun 仅数字字符集时结果只含数字() {
        val cfg = PasswordConfig(
            length = 20, upper = false, lower = false, symbols = false, excludeAmbiguous = false
        )
        assertEquals(10, PasswordEngine.poolSize(cfg))
        repeat(20) {
            val out = PasswordEngine.generate(cfg)
            assertEquals(20, out.length)
            assertTrue(out.matches(Regex("^[0-9]+$")))
        }
    }

    @Test
    fun 小写加数字字符集时结果只含该集合字符() {
        val cfg = PasswordConfig(
            length = 32, upper = false, symbols = false, excludeAmbiguous = false
        )
        repeat(10) {
            assertTrue(PasswordEngine.generate(cfg).matches(Regex("^[a-z0-9]+$")))
        }
    }

    @Test
    fun 排除易混淆字符后池与结果均不含易混淆字符() {
        val cfg = default // excludeAmbiguous = true
        val pool = PasswordEngine.pool(cfg)
        for (c in "l1IO0o") {
            assertFalse("池中不应包含易混淆字符 $c", pool.contains(c))
        }
        // 池大小 = 24(大写去IO) + 24(小写去lo) + 8(数字去01) + 26(符号)
        assertEquals(82, PasswordEngine.poolSize(cfg))
        repeat(50) {
            val out = PasswordEngine.generate(cfg)
            for (c in "l1IO0o") assertFalse("生成结果不应包含 $c", out.contains(c))
        }
    }

    @Test
    fun 熵值等于log2池大小乘长度() {
        assertEquals(16 * log2(82.0), PasswordEngine.entropyBits(default), 1e-6)
        val digitsOnly = PasswordConfig(
            length = 20, upper = false, lower = false, symbols = false, excludeAmbiguous = false
        )
        assertEquals(20 * log2(10.0), PasswordEngine.entropyBits(digitsOnly), 1e-6)
    }

    @Test
    fun 空字符集熵为零且生成为空串() {
        val cfg = PasswordConfig(
            length = 16, upper = false, lower = false, digits = false, symbols = false
        )
        assertEquals(0, PasswordEngine.poolSize(cfg))
        assertEquals(0.0, PasswordEngine.entropyBits(cfg), 0.0)
        assertEquals("", PasswordEngine.generate(cfg))
    }

    @Test
    fun 连续生成200次至少190个不同结果() {
        val results = (1..200).map { PasswordEngine.generate(default) }
        assertTrue(
            "不同结果数=${results.distinct().size}，疑似伪随机被写死",
            results.distinct().size >= 190
        )
    }

    @Test
    fun 批量生成数量被钳制在5到50() {
        assertEquals(7, PasswordEngine.generateBatch(PasswordConfig(batchSize = 7)).size)
        assertEquals(5, PasswordEngine.generateBatch(PasswordConfig(batchSize = 2)).size)
        assertEquals(50, PasswordEngine.generateBatch(PasswordConfig(batchSize = 100)).size)
        PasswordEngine.generateBatch(default).forEach { assertEquals(16, it.length) }
    }

    @Test
    fun Diceware模式输出小写单词连字符串() {
        // length=36 → 词数 = 36/6 = 6 → 6 个词、5 个连字符
        val out = PasswordEngine.generate(PasswordConfig(length = 36, diceware = true))
        assertTrue(out.matches(Regex("^([a-z]+-){5}[a-z]+$")))
    }

    @Test
    fun 强度等级阈值边界() {
        assertEquals("极弱", PasswordEngine.strengthLabel(27.99))
        assertEquals("弱", PasswordEngine.strengthLabel(28.0))
        assertEquals("弱", PasswordEngine.strengthLabel(39.99))
        assertEquals("中等", PasswordEngine.strengthLabel(40.0))
        assertEquals("中等", PasswordEngine.strengthLabel(59.99))
        assertEquals("强", PasswordEngine.strengthLabel(60.0))
        assertEquals("强", PasswordEngine.strengthLabel(79.99))
        assertEquals("密码学安全", PasswordEngine.strengthLabel(80.0))
    }

    @Test
    fun 强度进度钳制到0到1() {
        assertEquals(0.5f, PasswordEngine.strengthProgress(64.0), 1e-6f)
        assertEquals(0.0f, PasswordEngine.strengthProgress(-5.0), 0.0f)
        assertEquals(1.0f, PasswordEngine.strengthProgress(200.0), 0.0f)
    }

    @Test
    fun 破解耗时按对数分档() {
        assertEquals("—", PasswordEngine.crackEstimate(0.0))
        assertEquals("不足 1 年", PasswordEngine.crackEstimate(1.0))
        assertEquals("不足 1 年", PasswordEngine.crackEstimate(70.0))
        assertEquals("约 12 年", PasswordEngine.crackEstimate(76.0))
        assertEquals("约 19.6 万年", PasswordEngine.crackEstimate(90.0))
        assertEquals("约 2.0 亿年", PasswordEngine.crackEstimate(100.0))
        assertEquals("约 10^38 年（离线穷举不可破）", PasswordEngine.crackEstimate(200.0))
    }

    @Test
    fun 模式告警识别重复字典与单一类型() {
        assertEquals(emptyList<String>(), PasswordEngine.patternWarnings(""))
        // "aaaa"：连续重复 + 仅含字母
        val aaa = PasswordEngine.patternWarnings("aaaa")
        assertEquals(2, aaa.size)
        assertTrue(aaa.contains("存在连续重复字符"))
        assertTrue(aaa.contains("仅含字母"))
        // "111222"：连续重复 + 仅含数字
        assertEquals(2, PasswordEngine.patternWarnings("111222").size)
        // "aAa123!"：仅命中字典片段
        assertEquals(listOf("命中常见字典片段"), PasswordEngine.patternWarnings("aAa123!"))
        // 无规律混合口令不告警
        assertEquals(emptyList<String>(), PasswordEngine.patternWarnings("xK9#qW2!"))
        assertFalse(PasswordEngine.hasPattern("xK9#qW2!"))
        assertTrue(PasswordEngine.hasPattern("qwerty"))
    }
}
