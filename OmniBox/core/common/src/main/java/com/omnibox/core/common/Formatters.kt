package com.omnibox.core.common

import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

/** 设计稿中所有体积/熵值/角度文案都由真实计算产生，这里提供统一格式化口径。 */
object Formatters {
    fun bytes(sizeBytes: Long): String {
        if (sizeBytes < 1024L) return "$sizeBytes B"
        val unit = ((log10(sizeBytes.toDouble()) / log10(1024.0)).toInt()).coerceAtMost(4)
        val value = sizeBytes / 1024.0.pow(unit.toDouble())
        val suffix = arrayOf("B", "KB", "MB", "GB", "TB")[unit]
        return String.format(Locale.getDefault(), "%.1f %s", value, suffix)
    }

    fun percent(ratio: Double, digits: Int = 1): String =
        String.format(Locale.getDefault(), "%.${digits}f%%", ratio * 100.0)

    fun decibel(value: Double, digits: Int = 1): String =
        String.format(Locale.getDefault(), "%.${digits}f dBA", value)

    fun degrees(value: Double, digits: Int = 0): String =
        String.format(Locale.getDefault(), "%.${digits}f°", value)

    fun entropyBits(poolSize: Int, length: Int): Double =
        if (poolSize <= 1) 0.0 else length * (Math.log(poolSize.toDouble()) / Math.log(2.0))
}
