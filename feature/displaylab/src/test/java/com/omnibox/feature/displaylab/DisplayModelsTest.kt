package com.omnibox.feature.displaylab

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DisplayModelsTest {

    @Test
    fun `ppi 与对角线尺寸由物理 DPI 计算`() {
        val specs = DisplaySpecs(
            model = "Test",
            widthPx = 2712,
            heightPx = 1220,
            physicalXDpi = 446f,
            physicalYDpi = 446f,
            refreshRateHz = 120f,
            supportedRefreshRates = listOf(60f, 120f),
            hdrTypes = listOf(2, 1),
            wideColorGamut = true,
            brightnessFraction = 0.29f,
            adaptiveBrightness = true,
            modeCount = 2
        )
        assertThat(specs.ppi).isWithin(0.5f).of(630.8f) // sqrt(446² + 446²)
        // 对角线 = sqrt((2712/446)² + (1220/446)²) ≈ 6.67"
        assertThat(specs.diagonalInches).isWithin(0.01f).of(6.67f)
        assertThat(specs.resolutionLabel).isEqualTo("2712 × 1220")
        assertThat(specs.refreshLabel).isEqualTo("120 Hz")
        assertThat(specs.brightnessPercent).isEqualTo(29)
    }

    @Test
    fun `DPI 缺失时不产生垃圾值`() {
        val specs = DisplaySpecs(
            model = "T", widthPx = 1080, heightPx = 2400,
            physicalXDpi = 0f, physicalYDpi = 0f,
            refreshRateHz = 60f, supportedRefreshRates = listOf(60f),
            hdrTypes = emptyList(), wideColorGamut = false,
            brightnessFraction = null, adaptiveBrightness = false, modeCount = 1
        )
        assertThat(specs.ppi).isEqualTo(0f)
        assertThat(specs.diagonalLabel).isEqualTo("未知")
        assertThat(specs.brightnessPercent).isNull()
    }

    @Test
    fun `HDR 类型映射到可读名并去重排序`() {
        assertThat(HdrLabels.labels(intArrayOf(4, 2, 2, 3, 1)))
            .containsExactly("Dolby Vision", "HDR10", "HLG", "HDR10+").inOrder()
        assertThat(HdrLabels.label(99)).isEqualTo("HDR 类型 99")
    }

    @Test
    fun `灰阶 256 级颜色单调且端点正确`() {
        assertThat(grayLevel(0)).isEqualTo(0xFF000000.toInt())
        assertThat(grayLevel(255)).isEqualTo(0xFFFFFFFF.toInt())
        assertThat(grayLevel(128)).isEqualTo(0xFF808080.toInt())
    }

    @Test
    fun `五基色坏点巡检包含红绿蓝黑白`() {
        assertThat(DeadPixelColors.map { it.second })
            .containsExactly("红", "绿", "蓝", "黑", "白").inOrder()
    }
}
