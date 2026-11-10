package com.omnibox.feature.displaylab

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TouchTrackerTest {

    @Test
    fun `事件间隔取滚动中位数`() {
        val tracker = TouchTracker()
        // 8ms、12ms、16ms 三次间隔 → 中位数 12
        tracker.onEvent(0L, 1, 0.5f, 0f, 0f)
        tracker.onEvent(8_000_000L, 1, 0.5f, 0f, 0f)
        tracker.onEvent(20_000_000L, 1, 0.5f, 0f, 0f)
        tracker.onEvent(36_000_000L, 1, 0.5f, 0f, 0f)
        assertThat(tracker.pollingIntervalMs).isWithin(0.01f).of(12f)
    }

    @Test
    fun `并发指针数取历史最大`() {
        val tracker = TouchTracker()
        tracker.onEvent(0L, 1, 0.5f, 0f, 0f)
        tracker.onEvent(1_000_000L, 10, 0.5f, 0f, 0f)
        tracker.onEvent(2_000_000L, 3, 0.5f, 0f, 0f)
        assertThat(tracker.maxConcurrentPointers).isEqualTo(10)
    }

    @Test
    fun `乱序时间戳不会产生负间隔`() {
        val tracker = TouchTracker()
        tracker.onEvent(10_000_000L, 1, 0.5f, 0f, 0f)
        tracker.onEvent(5_000_000L, 1, 0.5f, 0f, 0f) // 时钟回退，应忽略
        assertThat(tracker.pollingIntervalMs).isNull()
    }

    @Test
    fun `reset 清空全部状态`() {
        val tracker = TouchTracker()
        tracker.onEvent(0L, 2, 0.7f, 3f, 2f)
        tracker.onEvent(5_000_000L, 2, 0.7f, 3f, 2f)
        tracker.reset()
        assertThat(tracker.maxConcurrentPointers).isEqualTo(0)
        assertThat(tracker.pollingIntervalMs).isNull()
        assertThat(tracker.lastPressure).isEqualTo(0f)
        assertThat(tracker.hasAreaData).isFalse()
    }

    @Test
    fun `椭圆接触面积换算为平方厘米`() {
        // 30px × 20px 椭圆，400dpi：英寸轴 = 0.075 × 0.05 → 面积 π×0.0375×0.25 in² → cm²
        val area = contactAreaCm2(30f, 20f, 400f, 400f)!!
        // π/4 × 0.075 × 0.05 × 6.4516 ≈ 0.0190 cm²
        assertThat(area).isWithin(0.0005f).of(0.0190f)
    }

    @Test
    fun `缺轴或非法 DPI 时面积返回 null`() {
        assertThat(contactAreaCm2(0f, 20f, 400f, 400f)).isNull()
        assertThat(contactAreaCm2(30f, 20f, 0f, 400f)).isNull()
        assertThat(contactAreaCm2(-1f, 20f, 400f, 400f)).isNull()
    }

    @Test
    fun `压感与间隔的诚实展示`() {
        assertThat(pressureLabel(0f)).isEqualTo("—")
        assertThat(pressureLabel(0.623f)).isEqualTo("0.62 P")
        assertThat(pressureLabel(1.5f)).isEqualTo("1.00 P") // 越界截断而非造假
        assertThat(intervalLabel(null)).isEqualTo("—")
        assertThat(intervalLabel(7.24f)).isEqualTo("7.2 ms")
        assertThat(areaLabel(null)).isEqualTo("—")
    }
}
