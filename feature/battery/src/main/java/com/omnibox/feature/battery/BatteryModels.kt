package com.omnibox.feature.battery

import kotlin.math.abs
import kotlin.math.roundToInt

/** 电池状态的纯逻辑层：换算与格式化不改事实，测不到的字段就是 null。 */

enum class BatteryStatus(val label: String) {
    CHARGING("充电中"),
    DISCHARGING("放电中"),
    FULL("已充满"),
    NOT_CHARGING("未充电"),
    UNKNOWN("未知");

    companion object {
        fun fromInt(v: Int): BatteryStatus = when (v) {
            2 -> CHARGING
            3 -> DISCHARGING
            4 -> NOT_CHARGING
            5 -> FULL
            else -> UNKNOWN
        }
    }
}

enum class PowerSource(val label: String) {
    AC("交流电源"),
    USB("USB"),
    WIRELESS("无线"),
    NONE("电池");

    companion object {
        fun fromInt(v: Int): PowerSource = when (v) {
            1 -> AC
            2 -> USB
            4 -> WIRELESS
            else -> NONE
        }
    }
}

enum class BatteryHealth(val label: String) {
    GOOD("良好 (Good)"),
    OVERHEAT("过热 (Overheat)"),
    DEAD("已损耗 (Dead)"),
    OVER_VOLTAGE("过压 (Over Voltage)"),
    FAILURE("故障 (Failure)"),
    COLD("低温 (Cold)"),
    UNKNOWN("未知");

    companion object {
        fun fromInt(v: Int): BatteryHealth = when (v) {
            2 -> GOOD
            3 -> OVERHEAT
            4 -> DEAD
            5 -> OVER_VOLTAGE
            6 -> FAILURE
            7 -> COLD
            else -> UNKNOWN
        }
    }
}

data class BatterySnapshot(
    val levelPercent: Int,
    val voltageMv: Int,
    /** 归一化后的电流（μA）：充电为正、放电为负。 */
    val currentMicroA: Long,
    val temperatureC: Float,
    val status: BatteryStatus,
    val plugged: PowerSource,
    val technology: String,
    val health: BatteryHealth,
    val chargeCounterMicroAh: Long?,
    val capacityPercent: Int?,
    /** sysfs 才有的深度信息，多数消费机受 SELinux 限制读不到 → null 如实展示。 */
    val cycleCount: Int?,
    val chargeFullMicroAh: Long?,
    val chargeFullDesignMicroAh: Long?,
    val timestampMs: Long
) {
    /** 实时功率 W = V × I。 */
    val powerW: Double
        get() = voltageMv / 1000.0 * currentMicroA / 1_000_000.0

    /** 健康度 SoH = 实测满充 / 出厂设计容量；缺任一即 null。 */
    val stateOfHealthPercent: Int?
        get() {
            val full = chargeFullMicroAh ?: return null
            val design = chargeFullDesignMicroAh ?: return null
            if (design <= 0) return null
            // 新电池 charge_full 可能略高于设计值，健康度封顶 100%，避免出现「损耗 -3%」
            return (full.toDouble() / design * 100).roundToInt().coerceIn(0, 100)
        }

    val capacityLossPercent: Int?
        get() = stateOfHealthPercent?.let { (100 - it).coerceAtLeast(0) }

    /** 充电方向说明（内核电流符号不统一，这里只依据 status）。 */
    val isCharging: Boolean get() = status == BatteryStatus.CHARGING || status == BatteryStatus.FULL
}

/**
 * 功率波形环形缓冲：默认 900 点 × 1Hz ≈ 15 分钟（对齐设计稿「近15分钟」）。
 * 纯 Kotlin，可单测。
 */
data class PowerSample(
    val timestampMs: Long,
    val levelPercent: Int,
    val voltageMv: Int,
    val currentMicroA: Long,
    val temperatureC: Float
) {
    val powerW: Double get() = voltageMv / 1000.0 * currentMicroA / 1_000_000.0
}

class PowerHistory(private val capacity: Int = 900) {

    private val samples = ArrayDeque<PowerSample>()

    fun add(sample: PowerSample) {
        samples.addLast(sample)
        while (samples.size > capacity) samples.removeFirst()
    }

    fun asList(): List<PowerSample> = samples.toList()

    fun clear() = samples.clear()

    val size: Int get() = samples.size

    val peakW: Double? get() = samples.maxOfOrNull { it.powerW }

    val meanW: Double? get() = samples.takeIf { it.isNotEmpty() }?.let {
        it.sumOf { s -> s.powerW } / it.size
    }

    /** 温升斜率（°C/min）：首尾差 / 时长；不足 60s 样本返回 null（诚实缺省）。 */
    fun temperatureSlopePerMinute(): Float? {
        if (samples.size < 2) return null
        val first = samples.first()
        val last = samples.last()
        val minutes = (last.timestampMs - first.timestampMs) / 60_000.0
        if (minutes < 1.0) return null
        return ((last.temperatureC - first.temperatureC) / minutes).toFloat()
    }

    /** 导出充放电日志 CSV（真实采样数据，UTC 时间戳）。 */
    fun toCsv(): String = buildString {
        appendLine("timestamp_ms,datetime_utc,level_percent,voltage_mv,current_ua,power_w,temperature_c")
        val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        iso.timeZone = java.util.TimeZone.getTimeZone("UTC")
        samples.forEach { s ->
            appendLine(
                "${s.timestampMs},${iso.format(java.util.Date(s.timestampMs))}Z," +
                    "${s.levelPercent},${s.voltageMv},${s.currentMicroA}," +
                    String.format(java.util.Locale.US, "%.3f", s.powerW) + "," +
                    String.format(java.util.Locale.US, "%.1f", s.temperatureC)
            )
        }
    }
}

/** 电流归一化：大量内核充电时上报负值 —— 按 status 判定方向，避免「充电显示 -4250mA」。 */
fun normalizeCurrentMicroA(rawMicroA: Long, status: BatteryStatus): Long {
    if (rawMicroA == Long.MIN_VALUE || rawMicroA == 0L) return 0L
    val magnitude = abs(rawMicroA)
    return when (status) {
        BatteryStatus.CHARGING, BatteryStatus.FULL -> magnitude
        BatteryStatus.DISCHARGING -> -magnitude
        else -> rawMicroA
    }
}

fun formatWatts(w: Double): String = String.format("%.1f W", w)

fun formatMilliAmps(microA: Long): String {
    val v = microA / 1000.0
    return if (v >= 0) String.format("+%,.0f mA", v) else String.format("%,.0f mA", v)
}

fun formatCelsius(c: Float): String = String.format("%.1f°C", c)

fun formatMilliAmpHours(microAh: Long?): String =
    microAh?.let { String.format("%,d mAh", it / 1000) } ?: "系统未上报"
