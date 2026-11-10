package com.omnibox.feature.displaylab

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 屏幕检测的纯逻辑层：所有数值都来自 DisplayManager / Settings 的真实读数，
 * 由 [DisplayLabViewModel] 组装，本类只做「不改事实」的换算与格式化。
 */
data class DisplaySpecs(
    /** 机型（Build.MODEL），代替设计稿写死的 "AMOLED 主显示模组"。 */
    val model: String,
    val widthPx: Int,
    val heightPx: Int,
    /** 系统上报的物理 DPI（多数设备是近似值，如实展示，不粉饰）。 */
    val physicalXDpi: Float,
    val physicalYDpi: Float,
    val refreshRateHz: Float,
    /** 支持的全部刷新率档位（去重排序）。 */
    val supportedRefreshRates: List<Float>,
    val hdrTypes: List<Int>,
    val wideColorGamut: Boolean,
    /** 当前亮度 0..1（API 31+ 读浮点，旧机型按 0..255 换算）。 */
    val brightnessFraction: Float?,
    /** 自适应亮度开关的系统状态。 */
    val adaptiveBrightness: Boolean,
    val modeCount: Int
) {
    val diagonalInches: Float
        get() = if (physicalXDpi <= 0f || physicalYDpi <= 0f) 0f
        else sqrt((widthPx / physicalXDpi).pow2() + (heightPx / physicalYDpi).pow2())

    val ppi: Float
        get() = if (physicalXDpi <= 0f || physicalYDpi <= 0f) 0f
        else sqrt(physicalXDpi.pow2() + physicalYDpi.pow2())

    val resolutionLabel: String get() = "$widthPx × $heightPx"

    val diagonalLabel: String
        get() = if (diagonalInches <= 0f) "未知" else String.format("%.1f\"", diagonalInches)

    val ppiLabel: String get() = if (ppi <= 0f) "PPI 未知" else "${ppi.toInt()} PPI"

    val refreshLabel: String
        get() = String.format("%.0f Hz", refreshRateHz)

    /** 亮度百分制；null 表示系统未上报（部分 ROM 锁死读取）。 */
    val brightnessPercent: Int? get() = brightnessFraction?.let { (it.coerceIn(0f, 1f) * 100).toInt() }
}

private fun Float.pow2(): Float = this * this

/** Display.HdrCapabilities 常量 → 用户可读名。未知类型如实标「未知」。 */
object HdrLabels {
    private val names = mapOf(
        1 to "Dolby Vision",
        2 to "HDR10",
        3 to "HLG",
        4 to "HDR10+"
    )

    fun label(type: Int): String = names[type] ?: "HDR 类型 $type"

    fun labels(types: IntArray): List<String> = types.distinct().sorted().map(::label)
}

/**
 * 触控遥测（纯逻辑，无 Android 依赖，可单测）。
 *
 * - 轮询间隔：相邻两次触摸事件送达的间隔（nanoTime 差），滚动中位数。
 *   这是「事件到达我们手里的节奏」，不是触控 IC 的真实采样率 —— 页面上如实措辞。
 * - 接触面积：touchMajor/touchMinor 像素 → 物理英寸 → 椭圆面积 cm²。
 *   设备不上报（返回 0）时给 null，UI 显示「—」，不编数。
 */
class TouchTracker(private val intervalWindow: Int = 64) {

    var maxConcurrentPointers: Int = 0
        private set

    var lastPressure: Float = 0f
        private set

    var lastMajorPx: Float = 0f
        private set

    var lastMinorPx: Float = 0f
        private set

    var hasAreaData: Boolean = false
        private set

    private val intervalsMs = ArrayDeque<Float>()
    // -1L 表示「尚无上一事件」；不能用 0L —— nanoTime 完全可能从 0 起步（测试与真实时钟皆可能）
    private var lastEventNanos: Long = -1L

    fun onEvent(
        eventNanos: Long,
        pressedPointerCount: Int,
        pressure: Float,
        touchMajorPx: Float,
        touchMinorPx: Float
    ) {
        if (pressedPointerCount > maxConcurrentPointers) maxConcurrentPointers = pressedPointerCount
        if (lastEventNanos >= 0L && eventNanos > lastEventNanos) {
            intervalsMs.addLast((eventNanos - lastEventNanos) / 1_000_000f)
            while (intervalsMs.size > intervalWindow) intervalsMs.removeFirst()
        }
        lastEventNanos = eventNanos
        lastPressure = pressure
        lastMajorPx = touchMajorPx
        lastMinorPx = touchMinorPx
        hasAreaData = hasAreaData || (touchMajorPx > 0f && touchMinorPx > 0f)
    }

    /** 事件间隔中位数（ms）；不足两次事件时返回 null。 */
    val pollingIntervalMs: Float?
        get() = medianOrNull(intervalsMs.toList())

    fun reset() {
        maxConcurrentPointers = 0
        lastPressure = 0f
        lastMajorPx = 0f
        lastMinorPx = 0f
        hasAreaData = false
        intervalsMs.clear()
        lastEventNanos = -1L
    }
}

/** 椭圆接触面积（cm²）；主/次轴像素 → 英寸 → cm²。任一轴缺失或 DPI 非法返回 null。 */
fun contactAreaCm2(touchMajorPx: Float, touchMinorPx: Float, xDpi: Float, yDpi: Float): Float? {
    if (touchMajorPx <= 0f || touchMinorPx <= 0f || xDpi <= 0f || yDpi <= 0f) return null
    val majorIn = touchMajorPx / xDpi
    val minorIn = touchMinorPx / yDpi
    val areaIn2 = PI.toFloat() * (majorIn / 2f) * (minorIn / 2f)
    return areaIn2 * 6.4516f
}

fun medianOrNull(values: List<Float>): Float? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
}

/** 压感归一化展示：getPressure() 约定 0..1，部分设备恒返回 1.0 —— 原样展示即可。 */
fun pressureLabel(pressure: Float): String =
    if (pressure <= 0f) "—" else String.format("%.2f P", pressure.coerceIn(0f, 1f))

fun areaLabel(areaCm2: Float?): String =
    areaCm2?.let { String.format("%.2f cm²", it) } ?: "—"

fun intervalLabel(ms: Float?): String =
    ms?.let { String.format("%.1f ms", it) } ?: "—"

/** 坏点巡检的五基色（设计稿：红绿蓝黑白）。 */
val DeadPixelColors = listOf(
    0xFFD32F2F.toInt() to "红",
    0xFF388E3C.toInt() to "绿",
    0xFF1976D2.toInt() to "蓝",
    0xFF000000.toInt() to "黑",
    0xFFFFFFFF.toInt() to "白"
)

/** 256 阶灰阶的某一阶颜色。 */
fun grayLevel(level: Int): Int {
    val v = level.coerceIn(0, 255)
    return (0xFF shl 24) or (v shl 16) or (v shl 8) or v
}

/** 温升/功率无数据时的占位（诚实显示，不造数）。 */
const val NO_DATA_LABEL = "—"
