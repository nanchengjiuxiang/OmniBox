// 测试范围：TextEngine 的纯 JVM 函数 —— digest / hashes / base64 / url / stats / clean /
// convertBase / regex（依赖 java.security.MessageDigest、java.util.Base64、java.net，
// 均为纯 JVM 实现）。
// 未覆盖：prettyJson（运行期依赖 org.json，JVM 单测下是 android stub，行为不可靠）、
// urlParams（内部调用 android.net.Uri.parse，纯 JVM 会抛 not-mocked 异常）。
package com.omnibox.feature.texttools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEngineTest {

    // ---------- 哈希标准向量 ----------

    @Test
    fun MD5标准向量_空串与abc() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", TextEngine.digest("MD5", ""))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", TextEngine.digest("MD5", "abc"))
    }

    @Test
    fun SHA1标准向量_空串与abc() {
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", TextEngine.digest("SHA-1", ""))
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", TextEngine.digest("SHA-1", "abc"))
    }

    @Test
    fun SHA256标准向量_空串与abc() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            TextEngine.digest("SHA-256", "")
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            TextEngine.digest("SHA-256", "abc")
        )
    }

    @Test
    fun 不支持的算法返回空串() {
        assertEquals("", TextEngine.digest("SHA-3", "abc"))
    }

    @Test
    fun hashes聚合四种摘要() {
        val map = TextEngine.hashes("abc")
        assertEquals(4, map.size)
        assertEquals("900150983cd24fb0d6963f7d28e17f72", map["MD5 (32-Bit)"])
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", map["SHA-1"])
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", map["SHA-256"])
        assertTrue("SHA-512 应为 128 位十六进制", map["SHA-512"]!!.matches(Regex("^[0-9a-f]{128}$")))
    }

    // ---------- Base64 ----------

    @Test
    fun Base64编解码往返含中文UTF8() {
        assertEquals("YWJj", TextEngine.base64("abc", true))
        assertEquals("abc", TextEngine.base64("YWJj", false))
        for (text in listOf("OmniBox 万能工具箱！", "hello\nworld", "数字12345")) {
            assertEquals("往返失败: $text", text, TextEngine.base64(TextEngine.base64(text, true), false))
        }
    }

    @Test
    fun Base64中文标准向量() {
        assertEquals("5L2g5aW9", TextEngine.base64("你好", true))
        assertEquals("你好", TextEngine.base64("5L2g5aW9", false))
    }

    @Test
    fun Base64非法输入返回解码失败提示() {
        val out = TextEngine.base64("!!!", false)
        assertTrue(out.startsWith("解码失败"))
    }

    // ---------- URL 编解码 ----------

    @Test
    fun URL编解码中文与空格() {
        assertEquals("%E4%BD%A0%E5%A5%BD+world", TextEngine.url("你好 world", true))
        assertEquals("你好 world", TextEngine.url("%E4%BD%A0%E5%A5%BD+world", false))
    }

    // ---------- 字符统计 ----------

    @Test
    fun stats字符数行数UTF8字节数() {
        assertEquals(Triple(3, 1, 3), TextEngine.stats("abc"))
        assertEquals(Triple(2, 1, 6), TextEngine.stats("你好")) // 中文每字 3 字节
        assertEquals(Triple(5, 3, 5), TextEngine.stats("a\nb\nc"))
        assertEquals(Triple(0, 0, 0), TextEngine.stats(""))
    }

    // ---------- 排版清洗 ----------

    @Test
    fun 清洗_逐行去首尾空格() {
        assertEquals(
            "a\nb\n\nc",
            TextEngine.clean("  a \n b \n\n  c ", TextEngine.CleanOp.TRIM_LINES)
        )
    }

    @Test
    fun 清洗_去空行与纯空白行() {
        assertEquals(
            "a\nb\nc",
            TextEngine.clean("a\n\nb\n \nc\n", TextEngine.CleanOp.REMOVE_BLANK)
        )
    }

    @Test
    fun 清洗_全角转半角() {
        assertEquals(
            "ABC123 !",
            TextEngine.clean("ＡＢＣ１２３　！", TextEngine.CleanOp.FULLWIDTH_TO_HALF)
        )
    }

    @Test
    fun 清洗_去重复行() {
        assertEquals("a\nb", TextEngine.clean("a\nb\na\nb\n", TextEngine.CleanOp.UNIQUE_LINES))
    }

    @Test
    fun 清洗_大小写与标题格式() {
        assertEquals("HELLO KOTLIN", TextEngine.clean("hello Kotlin", TextEngine.CleanOp.UPPER))
        assertEquals("hello", TextEngine.clean("HeLLo", TextEngine.CleanOp.LOWER))
        assertEquals("Hello World Tool", TextEngine.clean("hello world tool", TextEngine.CleanOp.TITLE))
    }

    // ---------- 进制转换 ----------

    @Test
    fun 进制转换_十进制255四种表示() {
        val r = TextEngine.convertBase("255", 10, 8)!!
        assertEquals("255", r.decimal)
        assertEquals("FF", r.hex)
        assertEquals("11111111", r.binary)
        assertEquals("377", r.octal)
    }

    @Test
    fun 进制转换_带前缀与32位掩码() {
        val r = TextEngine.convertBase("0x1F", 16, 8)!!
        assertEquals("31", r.decimal)
        assertEquals("1F", r.hex)
        val big = TextEngine.convertBase("4294967295", 10, 32)!!
        assertEquals("FFFFFFFF", big.hex)
        assertEquals("11111111111111111111111111111111", big.binary)
    }

    @Test
    fun 进制转换_负数按位宽回绕() {
        val r = TextEngine.convertBase("-1", 10, 8)!!
        assertEquals("255", r.decimal)
        assertEquals("FF", r.hex)
        assertEquals("11111111", r.binary)
    }

    @Test
    fun 进制转换_非法输入返回null() {
        assertNull(TextEngine.convertBase("xyz", 10, 8))
        assertNull(TextEngine.convertBase("123x", 16, 8))
    }

    // ---------- 正则 ----------

    @Test
    fun 正则匹配与捕获组() {
        val r = TextEngine.regex("\\d+", emptySet(), "a1b22c333")
        assertNull(r.error)
        assertEquals(listOf("1", "22", "333"), r.matches)

        val g = TextEngine.regex("(\\d)(\\d)", emptySet(), "x1234y")
        assertEquals(listOf("12", "34"), g.matches)
        assertEquals(listOf(listOf("1", "2"), listOf("3", "4")), g.groups)
    }

    @Test
    fun 正则语法错误返回错误信息() {
        val r = TextEngine.regex("(", emptySet(), "x")
        assertNotNull(r.error)
        assertTrue(r.matches.isEmpty())
    }
}
