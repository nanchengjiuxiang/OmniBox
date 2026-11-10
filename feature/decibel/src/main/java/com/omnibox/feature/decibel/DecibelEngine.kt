package com.omnibox.feature.decibel

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/** 声压换算的纯算法常量与公式。 */
object DecibelMath {
    const val SAMPLE_RATE = 44100
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    /** 0 dBFS → 90 dB SPL 的参考映射（把归一化 dBFS 抬到常见声压量级）。 */
    const val REFERENCE_DB = 90.0
    const val CALIBRATION_MIN = -30.0
    const val CALIBRATION_MAX = 30.0
    const val SPL_MIN = 0.0
    const val SPL_MAX = 140.0

    /** 一帧 PCM_16BIT 的均方根振幅。 */
    fun rms(buffer: ShortArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) {
            val s = buffer[i].toDouble()
            sum += s * s
        }
        return sqrt(sum / count.coerceAtLeast(1))
    }

    /** dBFS = 20·log10(rms / 32768)，静音时以 1/32768 为下限避免 log10(0)。 */
    fun dbfs(rms: Double): Double = 20.0 * log10(max(rms, 1.0) / 32768.0)

    /** 估算声压级：dBFS + 参考基准 + 用户校准偏移，并夹到可显示区间。 */
    fun spl(dbfs: Double, calibrationOffsetDb: Double): Double =
        (dbfs + REFERENCE_DB + calibrationOffsetDb).coerceIn(SPL_MIN, SPL_MAX)

    /** 等效连续声级 Leq = 10·log10(Σ10^(L/10) / N)。 */
    fun leq(energySum: Double, count: Long): Double =
        if (count <= 0L || energySum <= 0.0) 0.0 else 10.0 * log10(energySum / count)
}

enum class NoiseLevel(val label: String, val range: String, val advice: String) {
    VERY_QUIET("极静", "0–30 dB", "助眠环境，几乎无干扰"),
    QUIET("安静", "30–50 dB", "专注思考，理想办公"),
    MODERATE("一般", "50–70 dB", "日常交谈 / 室内空调运转"),
    NOISY("扰人", "70–90 dB", "长时间暴露容易引发听觉疲劳"),
    HARMFUL("需防护", ">90 dB", "危险级声级，需即刻防护")
}

fun noiseLevelOf(spl: Double): NoiseLevel = when {
    spl < 30.0 -> NoiseLevel.VERY_QUIET
    spl < 50.0 -> NoiseLevel.QUIET
    spl < 70.0 -> NoiseLevel.MODERATE
    spl < 90.0 -> NoiseLevel.NOISY
    else -> NoiseLevel.HARMFUL
}

/** WHO / 常见噪声对照：这些是对照基准常量，不是当前实测值。 */
data class NoiseReference(
    val dbLabel: String,
    val title: String,
    val desc: String,
    val centerDb: Double,
    val icon: String
)

val WHO_REFERENCES = listOf(
    NoiseReference("20 dB", "微风落叶 · 极安静", "旷野深夜、轻柔呼吸声", 20.0, "nature"),
    NoiseReference("40 dB", "静音图书馆 · 舒适安逸", "住宅区夜间、低速冰箱蜂鸣", 40.0, "local_library"),
    NoiseReference("60 dB", "日常交谈 · 正常活动", "办公室环境、常规对话距离", 60.0, "forum"),
    NoiseReference("80 dB", "闹市交通 · 长期嘈杂", "主干道车流、吸尘器工作音", 80.0, "directions_car"),
    NoiseReference("100+ dB", "工业噪音 · 听力受损警示", "电锯轰鸣、靠近舞台音箱 (>15分钟需防护)", 100.0, "warning")
)

/** 单帧真实计算结果。 */
data class DecibelFrame(val dbfs: Double, val spl: Double, val rms: Double)

data class DecibelMetrics(
    val minDb: Double = 0.0,
    val peakDb: Double = 0.0,
    val movingAvgDb: Double = 0.0,
    val leqDb: Double = 0.0,
    val sampleCount: Long = 0L,
    val recent: List<Double> = emptyList()
)

/** 统计量累积器：瞬时 / 滑动平均 / Leq / 峰值保持 / 最小 / 近期波形。 */
class DecibelAccumulator {
    companion object {
        const val RECENT_SIZE = 60
        private const val EMA_ALPHA = 0.2
    }

    private var count = 0L
    private var energySum = 0.0
    private var moving = 0.0
    private var min = Double.POSITIVE_INFINITY
    private var peak = 0.0
    private val recent = ArrayDeque<Double>()

    fun add(spl: Double) {
        count++
        energySum += 10.0.pow(spl / 10.0)
        moving = if (count == 1L) spl else moving * (1.0 - EMA_ALPHA) + spl * EMA_ALPHA
        if (spl < min) min = spl
        if (spl > peak) peak = spl
        recent.addLast(spl)
        if (recent.size > RECENT_SIZE) recent.removeFirst()
    }

    fun reset() {
        count = 0L
        energySum = 0.0
        moving = 0.0
        min = Double.POSITIVE_INFINITY
        peak = 0.0
        recent.clear()
    }

    fun snapshot(): DecibelMetrics = DecibelMetrics(
        minDb = if (min == Double.POSITIVE_INFINITY) 0.0 else min,
        peakDb = peak,
        movingAvgDb = moving,
        leqDb = DecibelMath.leq(energySum, count),
        sampleCount = count,
        recent = recent.toList()
    )
}

/**
 * AudioRecord 平台封装：循环读取 PCM_16BIT，计算 RMS → dBFS → 估算声压级。
 * 音频字节只在内存中的 short[] 里瞬时存在，绝不写盘、绝不联网。
 * 由调用方在 Dispatchers.IO 上以协程循环驱动，停止时 stop + release。
 */
class DecibelEngine {

    @Volatile
    private var isRecording = false
    private var audioRecord: AudioRecord? = null

    fun isRecording(): Boolean = isRecording

    suspend fun record(
        calibration: () -> Double,
        onFrame: (DecibelFrame) -> Unit
    ) {
        if (isRecording) return
        val minBuffer = AudioRecord.getMinBufferSize(
            DecibelMath.SAMPLE_RATE, DecibelMath.CHANNEL_CONFIG, DecibelMath.ENCODING
        )
        val bufferSize = maxOf(minBuffer, 2048)
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                DecibelMath.SAMPLE_RATE,
                DecibelMath.CHANNEL_CONFIG,
                DecibelMath.ENCODING,
                bufferSize
            )
        } catch (e: SecurityException) {
            throw IllegalStateException("麦克风权限未授予，无法采样", e)
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("AudioRecord 初始化失败（设备可能无可用麦克风）")
        }
        audioRecord = record
        isRecording = true
        val buffer = ShortArray(bufferSize)
        try {
            record.startRecording()
            while (isRecording && currentCoroutineContext().isActive) {
                val readCount = record.read(buffer, 0, bufferSize)
                when {
                    readCount > 0 -> {
                        val rms = DecibelMath.rms(buffer, readCount)
                        val dbfs = DecibelMath.dbfs(rms)
                        val spl = DecibelMath.spl(dbfs, calibration())
                        onFrame(DecibelFrame(dbfs, spl, rms))
                    }
                    readCount == AudioRecord.ERROR_INVALID_OPERATION ||
                        readCount == AudioRecord.ERROR_BAD_VALUE -> break
                }
            }
        } catch (e: SecurityException) {
            // 系统在运行期拒绝麦克风访问：明确报错而非静默吞掉
            throw IllegalStateException("麦克风访问被系统拒绝", e)
        } finally {
            isRecording = false
            runCatching { record.stop() }
            record.release()
            audioRecord = null
        }
    }

    fun stop() {
        isRecording = false
    }
}
