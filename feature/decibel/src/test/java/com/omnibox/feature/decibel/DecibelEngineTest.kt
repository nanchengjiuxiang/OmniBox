// 测试范围：DecibelMath 的纯公式（rms / dbfs / spl / leq）、noiseLevelOf 分级、
// DecibelAccumulator 统计累积器。DecibelMath 对 AudioFormat 的引用均为 const（编译期内联），
// 运行期加载不触碰 android.*。
// 未覆盖：DecibelEngine.record / stop / isRecording —— 依赖 android.media.AudioRecord
//（麦克风采样），纯 JVM 不可执行。
package com.omnibox.feature.decibel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.pow
import kotlin.math.sqrt

class DecibelEngineTest {

    private lateinit var acc: DecibelAccumulator

    @Before
    fun setUp() {
        acc = DecibelAccumulator()
    }

    @Test
    fun rms_短缓冲按数学定义() {
        assertEquals(sqrt(12.5), DecibelMath.rms(shortArrayOf(3, 4), 2), 1e-12)
        assertEquals(0.0, DecibelMath.rms(ShortArray(512), 512), 0.0)
        assertEquals(0.0, DecibelMath.rms(ShortArray(0), 0), 0.0) // 空缓冲防除零
    }

    @Test
    fun dbfs_满幅约等于0dBFS() {
        val full = ShortArray(1024) { 32767 }
        val rms = DecibelMath.rms(full, full.size)
        assertEquals(32767.0, rms, 1e-9)
        assertEquals(0.0, DecibelMath.dbfs(rms), 0.05)
    }

    @Test
    fun dbfs_半幅约负6点02dB() {
        assertEquals(-6.0206, DecibelMath.dbfs(16384.0), 0.05)
        val half = ShortArray(1024) { 16384 }
        assertEquals(-6.0206, DecibelMath.dbfs(DecibelMath.rms(half, half.size)), 0.05)
    }

    @Test
    fun dbfs_全零缓冲返回有限下限而非负无穷或NaN() {
        val silent = ShortArray(1024)
        val dbfs = DecibelMath.dbfs(DecibelMath.rms(silent, silent.size))
        assertFalse(dbfs.isInfinite())
        assertFalse(dbfs.isNaN())
        // 引擎以 1/32768 为振幅下限：20·log10(1/32768) ≈ -90.309 dBFS
        assertEquals(-90.309, dbfs, 0.01)
    }

    @Test
    fun spl_参考基准与校准偏移叠加() {
        assertEquals(90.0, DecibelMath.spl(0.0, 0.0), 1e-9)
        assertEquals(100.0, DecibelMath.spl(0.0, 10.0), 1e-9)
        assertEquals(80.0, DecibelMath.spl(0.0, -10.0), 1e-9)
    }

    @Test
    fun spl_范围钳制到0到140() {
        assertEquals(0.0, DecibelMath.spl(-120.0, -30.0), 1e-9) // 90-120-30=-60 → 钳到 0
        assertEquals(140.0, DecibelMath.spl(0.0, 60.0), 1e-9)    // 90+60=150 → 钳到 140
    }

    @Test
    fun spl_校准偏移保持单调() {
        for (dbfs in listOf(-80.0, -40.0, 0.0, 1.0)) {
            assertTrue(DecibelMath.spl(dbfs, -10.0) < DecibelMath.spl(dbfs, 0.0))
            assertTrue(DecibelMath.spl(dbfs, 0.0) < DecibelMath.spl(dbfs, 10.0))
        }
    }

    @Test
    fun leq_相同电平的能量平均等于该电平() {
        assertEquals(85.0, DecibelMath.leq(2.0 * 10.0.pow(8.5), 2L), 0.1)
        assertEquals(60.0, DecibelMath.leq(10.0 * 10.0.pow(6.0), 10L), 0.1)
    }

    @Test
    fun leq_无效输入返回零() {
        assertEquals(0.0, DecibelMath.leq(0.0, 5L), 0.0)
        assertEquals(0.0, DecibelMath.leq(-1.0, 5L), 0.0)
        assertEquals(0.0, DecibelMath.leq(100.0, 0L), 0.0)
    }

    @Test
    fun 噪声分级边界() {
        assertEquals(NoiseLevel.VERY_QUIET, noiseLevelOf(-1.0))
        assertEquals(NoiseLevel.VERY_QUIET, noiseLevelOf(29.99))
        assertEquals(NoiseLevel.QUIET, noiseLevelOf(30.0))
        assertEquals(NoiseLevel.QUIET, noiseLevelOf(49.99))
        assertEquals(NoiseLevel.MODERATE, noiseLevelOf(50.0))
        assertEquals(NoiseLevel.MODERATE, noiseLevelOf(69.99))
        assertEquals(NoiseLevel.NOISY, noiseLevelOf(70.0))
        assertEquals(NoiseLevel.NOISY, noiseLevelOf(89.99))
        assertEquals(NoiseLevel.HARMFUL, noiseLevelOf(90.0))
        assertEquals(NoiseLevel.HARMFUL, noiseLevelOf(140.0))
    }

    @Test
    fun 累加器min峰值与leq统计() {
        repeat(10) { acc.add(80.0) }
        var s = acc.snapshot()
        assertEquals(10L, s.sampleCount)
        assertEquals(80.0, s.leqDb, 0.1)
        assertEquals(80.0, s.minDb, 1e-9)
        assertEquals(80.0, s.peakDb, 1e-9)
        acc.add(70.0)
        acc.add(90.0)
        s = acc.snapshot()
        assertEquals(12L, s.sampleCount)
        assertEquals(70.0, s.minDb, 1e-9)
        assertEquals(90.0, s.peakDb, 1e-9)
    }

    @Test
    fun 累加器recent窗口最多保留60帧() {
        repeat(70) { acc.add(50.0 + it * 0.1) }
        val s = acc.snapshot()
        assertEquals(60, s.recent.size)
        assertEquals(70L, s.sampleCount)
    }

    @Test
    fun 累加器reset清空全部状态() {
        acc.add(80.0)
        acc.reset()
        val s = acc.snapshot()
        assertEquals(0L, s.sampleCount)
        assertEquals(0.0, s.leqDb, 0.0)
        assertEquals(0.0, s.minDb, 0.0)
        assertTrue(s.recent.isEmpty())
    }
}
