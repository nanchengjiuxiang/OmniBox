package com.omnibox.feature.password

import java.security.SecureRandom
import kotlin.math.log2

data class PasswordConfig(
    val length: Int = 16,
    val includeUpper: Boolean = true,
    val includeLower: Boolean = true,
    val includeDigits: Boolean = true,
    val includeSymbols: Boolean = true,
    val excludeAmbiguous: Boolean = true
)

class PasswordGeneratorEngine {
    private val upperChars = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private val lowerChars = "abcdefghijkmnopqrstuvwxyz"
    private val digitChars = "23456789"
    private val symbolChars = "!@#$%^&*()-_=+[]{}|;:,.<>?"
    private val random = SecureRandom()

    fun generate(config: PasswordConfig): Pair<String, Float> {
        val pool = StringBuilder().apply {
            if (config.includeUpper) append(upperChars)
            if (config.includeLower) append(lowerChars)
            if (config.includeDigits) append(digitChars)
            if (config.includeSymbols) append(symbolChars)
        }.toString()

        if (pool.isEmpty()) return "" to 0f

        val result = StringBuilder()
        for (i in 0 until config.length) {
            val idx = random.nextInt(pool.length)
            result.append(pool[idx])
        }

        // 计算香农信息熵 (Entropy = L * log2(N))
        val entropy = config.length * log2(pool.length.toDouble()).toFloat()
        return result.toString() to entropy
    }
}
