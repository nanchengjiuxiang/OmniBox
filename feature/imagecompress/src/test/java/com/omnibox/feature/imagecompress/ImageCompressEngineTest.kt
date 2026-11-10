// 测试范围：inSampleSize 的 2 的幂采样策略 —— 主代码中 calculateInSampleSize 为 private，
// 此处通过 java.lang.reflect 访问（实例化 ImageCompressEngine 不触碰 android.graphics，
// 该方法本身为纯整数运算）；另覆盖 CompressMaxEdge / CompressOutputFormat / CompressConfig
// 的枚举值与默认值。
// 未覆盖：compress / compressInternal / boundedCopy / previewFromBytes / CompressResult ——
// 依赖 BitmapFactory / Bitmap / Canvas，纯 JVM 上不可执行（方法体调用 android.*）。
package com.omnibox.feature.imagecompress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

class ImageCompressEngineTest {

    private lateinit var engine: ImageCompressEngine
    private lateinit var method: Method

    @Before
    fun setUp() {
        engine = ImageCompressEngine()
        method = ImageCompressEngine::class.java.getDeclaredMethod(
            "calculateInSampleSize",
            Int::class.java, Int::class.java, Int::class.java, Int::class.java
        )
        method.isAccessible = true
    }

    private fun sample(w: Int, h: Int, reqW: Int, reqH: Int): Int =
        method.invoke(engine, w, h, reqW, reqH) as Int

    @Test
    fun 采样结果始终是2的幂且在1到32之间() {
        val sizes = listOf(
            4000 to 3000, 2048 to 2048, 8192 to 6000, 1100 to 1100,
            900 to 700, 1920 to 1080, 512 to 512, 100 to 100
        )
        for ((w, h) in sizes) {
            val s = sample(w, h, 1024, 768)
            assertTrue("w=$w,h=$h 时 s=$s 超出范围", s in 1..32)
            assertTrue("w=$w,h=$h 时 s=$s 不是 2 的幂", (s and (s - 1)) == 0)
        }
    }

    @Test
    fun 原图大于等于目标时采样后边长不小于目标() {
        val cases = listOf(
            Triple(4000, 3000, 1024 to 768),
            Triple(2048, 2048, 512 to 512),
            Triple(8192, 6000, 800 to 600),
            Triple(1100, 1100, 1024 to 1024)
        )
        for ((w, h, req) in cases) {
            val s = sample(w, h, req.first, req.second)
            assertTrue("w=$w,s=$s", w / s >= req.first)
            assertTrue("h=$h,s=$s", h / s >= req.second)
        }
    }

    @Test
    fun 采样率已是满足约束的最小2的幂() {
        val cases = listOf(
            Triple(4000, 3000, 1024 to 768),
            Triple(2048, 2048, 512 to 512),
            Triple(8192, 6000, 800 to 600),
            Triple(1100, 1100, 1024 to 1024),
            Triple(1920, 1080, 1024 to 768)
        )
        for ((w, h, req) in cases) {
            val s = sample(w, h, req.first, req.second)
            // 再翻倍必然违反「采样后边长 ≥ 目标」，除非已到 32 的上限
            assertTrue(
                "w=$w,h=$h 时 s=$s 还能再减半",
                s == 32 || w / (2 * s) < req.first || h / (2 * s) < req.second
            )
        }
    }

    @Test
    fun 原图不大于目标时采样率为1() {
        assertEquals(1, sample(400, 300, 1024, 768))
        assertEquals(1, sample(512, 512, 512, 512))
        assertEquals(1, sample(900, 700, 1024, 768))
    }

    @Test
    fun 精确整除场景的采样率() {
        assertEquals(4, sample(2048, 2048, 512, 512))
        assertEquals(2, sample(1024, 1024, 512, 512))
        assertEquals(2, sample(4000, 3000, 1024, 768))
        assertEquals(8, sample(8192, 6000, 800, 600))
    }

    @Test
    fun 采样率上限为32() {
        assertEquals(32, sample(8192, 8192, 100, 100))
    }

    @Test
    fun 枚举像素目标与MIME真实值() {
        assertEquals(512, CompressMaxEdge.EDGE_512.pixels)
        assertEquals(1024, CompressMaxEdge.EDGE_1024.pixels)
        assertEquals(2048, CompressMaxEdge.EDGE_2048.pixels)
        assertNull(CompressMaxEdge.ORIGINAL.pixels) // 原尺寸表示不下采样
        assertEquals("image/jpeg", CompressOutputFormat.JPEG.mimeType)
        assertEquals("jpg", CompressOutputFormat.JPEG.extension)
        assertEquals("image/webp", CompressOutputFormat.WEBP.mimeType)
        assertEquals("image/png", CompressOutputFormat.PNG.mimeType)
    }

    @Test
    fun 默认压缩配置() {
        val cfg = CompressConfig()
        assertEquals(82, cfg.quality)
        assertEquals(CompressMaxEdge.EDGE_1024, cfg.maxEdge)
        assertEquals(CompressOutputFormat.WEBP, cfg.format)
        assertTrue(cfg.preserveAlpha)
    }
}
