package com.omnibox.feature.password

import java.security.SecureRandom
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

data class PasswordConfig(
    val length: Int = 16,
    val upper: Boolean = true,
    val lower: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val excludeAmbiguous: Boolean = true,
    val forceAllCategories: Boolean = true,
    val diceware: Boolean = false,
    val batchMode: Boolean = false,
    val batchSize: Int = 5
)

/**
 * 设计稿承诺的「纯本地 CSPRNG」真实实现：
 * java.security.SecureRandom + 系统熵池，不使用 Math.random，不联网，不写盘。
 */
object PasswordEngine {
    private val rng = SecureRandom()

    private const val AMBIGUOUS = "l1IO0o"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#\$%^&*()-_=+[]{}|;:,.<>?"

    /** 离线 EFF 风格小型助记词表（P2 可替换为完整 EFF 词库 assets 文件）。 */
    private val DICEWARE = listOf(
        "alpha", "bravo", "cipher", "delta", "echo", "foxtrot", "golf", "hotel",
        "india", "juliet", "kilo", "lima", "matrix", "nova", "orbit", "pixel",
        "quartz", "radar", "sierra", "tango", "umbra", "vector", "whisky", "xray",
        "yankee", "zulu", "anchor", "beacon", "crystal", "drift", "ember", "forge"
    )

    fun categories(config: PasswordConfig): List<String> = buildList {
        if (config.upper) add(filter(UPPER, config.excludeAmbiguous))
        if (config.lower) add(filter(LOWER, config.excludeAmbiguous))
        if (config.digits) add(filter(DIGITS, config.excludeAmbiguous))
        if (config.symbols) add(filter(SYMBOLS, config.excludeAmbiguous))
    }.filter { it.isNotEmpty() }

    private fun filter(chars: String, exclude: Boolean): String =
        if (exclude) chars.filterNot { it.toString() in AMBIGUOUS } else chars

    fun pool(config: PasswordConfig): String = categories(config).joinToString("")

    fun poolSize(config: PasswordConfig): Int = pool(config).length

    /** 信息熵 = 长度 × log2(字符集空间)。 */
    fun entropyBits(config: PasswordConfig): Double {
        val n = poolSize(config)
        return if (n <= 1 || config.length <= 0) 0.0
        else config.length * (Math.log(n.toDouble()) / Math.log(2.0))
    }

    fun generate(config: PasswordConfig): String {
        if (config.diceware) return generateDiceware(config)
        val cats = categories(config)
        val all = pool(config)
        if (all.isEmpty()) return ""
        val length = config.length.coerceIn(1, 256)
        val chars = ArrayList<Char>(length)
        if (config.forceAllCategories) {
            cats.forEach { chars.add(it[rng.nextInt(it.length)]) }
        }
        while (chars.size < length) chars.add(all[rng.nextInt(all.length)])
        // Fisher-Yates 洗牌，避免首几位必然来自被强制覆盖的分类
        for (i in chars.size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp
        }
        return chars.take(length).joinToString("")
    }

    private fun generateDiceware(config: PasswordConfig): String {
        val words = maxOf(3, (config.length / 6).coerceIn(3, 8))
        return (0 until words).joinToString("-") { DICEWARE[rng.nextInt(DICEWARE.size)] }
    }

    fun generateBatch(config: PasswordConfig): List<String> =
        (1..config.batchSize.coerceIn(5, 50)).map { generate(config) }

    fun strengthLabel(bits: Double): String = when {
        bits < 28 -> "极弱"
        bits < 40 -> "弱"
        bits < 60 -> "中等"
        bits < 80 -> "强"
        else -> "密码学安全"
    }

    fun strengthProgress(bits: Double): Float = (bits / 128.0).coerceIn(0.0, 1.0).toFloat()

    /** 以 100T/s 离线穷举、平均命中 2^(bits-1) 次估算。用对数避免 Double 溢出。 */
    fun crackEstimate(bits: Double): String {
        if (bits <= 0) return "—"
        val log10Seconds = (bits - 1) * log10(2.0) - 14.0
        val log10Years = log10Seconds - 7.499 // log10(365.25*24*3600)
        return when {
            log10Years < 0 -> "不足 1 年"
            log10Years > 15 -> "约 10^${log10Years.roundToInt()} 年（离线穷举不可破）"
            else -> {
                val years = 10.0.pow(log10Years)
                when {
                    years >= 1e8 -> "约 ${String.format("%.1f", years / 1e8)} 亿年"
                    years >= 1e4 -> "约 ${String.format("%.1f", years / 1e4)} 万年"
                    else -> "约 ${String.format("%.0f", years)} 年"
                }
            }
        }
    }

    /** 字典与规律校验：检测连续重复、顺序字符、连续同类型。 */
    fun patternWarnings(value: String): List<String> {
        val warnings = mutableListOf<String>()
        if (value.isEmpty()) return warnings
        if (value.windowed(3).any { it[0] == it[1] && it[1] == it[2] }) warnings.add("存在连续重复字符")
        if (value.contains(Regex("(?i)(abc|123|qwerty|password|admin)"))) warnings.add("命中常见字典片段")
        if (value.all { it.isLetter() }) warnings.add("仅含字母")
        if (value.all { it.isDigit() }) warnings.add("仅含数字")
        return warnings
    }

    fun hasPattern(value: String): Boolean = patternWarnings(value).isNotEmpty()
}
