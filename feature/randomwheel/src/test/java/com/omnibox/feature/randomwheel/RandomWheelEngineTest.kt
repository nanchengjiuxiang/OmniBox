// 测试范围：RandomWheelEngine 全部纯函数 —— totalWeight / sectors / sectorCenterAngle /
// weightedPick / draw / targetRotation（内部仅依赖 java.security.SecureRandom 与
// core:data 的 WheelItemEntity 纯数据类，Room 注解为 CLASS 保留，无运行期依赖）。
// 说明：引擎没有「角度→索引」反查函数，转盘落点几何用 sectorCenterAngle +
// targetRotation 的不变式覆盖；draw 的 timestampMillis 字段取
// System.currentTimeMillis()，不做断言。加权分布用 SecureRandom 真实抽样，不 mock。
package com.omnibox.feature.randomwheel

import com.omnibox.core.data.local.WheelItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomWheelEngineTest {

    private fun item(label: String, weight: Int) = WheelItemEntity(
        id = 0L, presetId = 1L, label = label, weight = weight, colorToken = "blue", position = 0
    )

    @Test
    fun 加权抽样分布与理论概率偏差小于两个百分点() {
        val items = listOf(item("A", 1), item("B", 3)) // 固定权重 1:3
        val n = 20_000
        var countA = 0
        repeat(n) {
            val idx = RandomWheelEngine.weightedPick(items, setOf(0, 1))!!
            if (idx == 0) countA++
        }
        assertEquals("A 的频率应为 0.25", 0.25, countA.toDouble() / n, 0.02)
        assertEquals("B 的频率应为 0.75", 0.75, (n - countA).toDouble() / n, 0.02)
    }

    @Test
    fun 零权重项永不中签() {
        val items = listOf(item("A", 0), item("B", 5))
        repeat(500) {
            assertEquals(1, RandomWheelEngine.weightedPick(items, setOf(0, 1))!!)
        }
    }

    @Test
    fun 空列表与全零权重返回null() {
        assertNull(RandomWheelEngine.weightedPick(emptyList(), emptySet()))
        assertNull(RandomWheelEngine.weightedPick(listOf(item("A", 0), item("B", 0)), setOf(0, 1)))
        assertEquals(0, RandomWheelEngine.totalWeight(listOf(item("A", 0), item("B", 0))))
    }

    @Test
    fun eligible排除后无候选返回null() {
        val items = listOf(item("A", 1), item("B", 3))
        assertNull(RandomWheelEngine.weightedPick(items, setOf(9)))
        assertNull(RandomWheelEngine.weightedPick(items, emptySet()))
    }

    @Test
    fun 抽样索引始终落在0到size减1() {
        val items = listOf(item("A", 2), item("B", 1), item("C", 4), item("D", 3))
        repeat(1000) {
            val idx = RandomWheelEngine.weightedPick(items, items.indices.toSet())!!
            assertTrue(idx in items.indices)
        }
    }

    @Test
    fun 扇区几何按权重从正上方顺时针排布() {
        val sectors = RandomWheelEngine.sectors(listOf(item("A", 1), item("B", 3)))
        assertEquals(2, sectors.size)
        assertEquals(-90f, sectors[0].first, 1e-4f)
        assertEquals(90f, sectors[0].second, 1e-4f)
        assertEquals(0f, sectors[1].first, 1e-4f)
        assertEquals(270f, sectors[1].second, 1e-4f)
    }

    @Test
    fun 零权重项占零度扇区且不改变后续起点() {
        val sectors = RandomWheelEngine.sectors(listOf(item("A", 0), item("B", 4), item("C", 0)))
        assertEquals(-90f, sectors[0].first, 1e-4f)
        assertEquals(0f, sectors[0].second, 1e-4f)
        assertEquals(-90f, sectors[1].first, 1e-4f)
        assertEquals(360f, sectors[1].second, 1e-4f)
        assertEquals(270f, sectors[2].first, 1e-4f)
        assertEquals(0f, sectors[2].second, 1e-4f)
    }

    @Test
    fun 扇区中心角与越界索引回退() {
        val items = listOf(item("A", 1), item("B", 3))
        assertEquals(-45f, RandomWheelEngine.sectorCenterAngle(items, 0), 1e-4f)
        assertEquals(135f, RandomWheelEngine.sectorCenterAngle(items, 1), 1e-4f)
        assertEquals(-90f, RandomWheelEngine.sectorCenterAngle(items, 99), 1e-4f)
    }

    @Test
    fun 旋转角把中选扇区中心对准顶部指针() {
        // 中心角 -45°、当前 0°、5 整圈：目标 = 0 + 5*360 + 315 = 2115
        assertEquals(2115f, RandomWheelEngine.targetRotation(0f, -45f, 5), 1e-3f)
        // 不变式：(中心角 + 旋转角) mod 360 == -90°(≡270°)
        for (center in listOf(0f, 45f, 90f, 135f, 180f, 270f, 359f)) {
            val rot = RandomWheelEngine.targetRotation(37.5f, center, 2)
            val norm = ((center + rot) % 360f + 360f) % 360f
            assertEquals("center=$center", 270f, norm, 1e-3f)
        }
    }

    @Test
    fun 旋转角至少转一整圈() {
        val current = 100f
        val rot = RandomWheelEngine.targetRotation(current, 0f, 0) // fullTurns=0 也强制至少 1 圈
        assertTrue(rot - current >= 360f)
    }

    @Test
    fun 不允许重复时批次内索引不重复且耗尽即停() {
        val items = listOf(item("A", 1), item("B", 1), item("C", 1))
        val picks = RandomWheelEngine.draw(items, allowRepeat = false, count = 5, recentLabels = emptyList())
        assertEquals("可抽集合耗尽即停止", 3, picks.size)
        assertEquals(3, picks.map { it.index }.distinct().size)
    }

    @Test
    fun recentLabels中的标签被排除() {
        val items = listOf(item("A", 5), item("B", 1), item("C", 1))
        repeat(20) {
            val picks = RandomWheelEngine.draw(items, allowRepeat = false, count = 1, recentLabels = listOf("A"))
            assertEquals(1, picks.size)
            assertTrue(picks[0].label != "A")
        }
    }

    @Test
    fun draw结果字段与概率计算() {
        val items = listOf(item("A", 1), item("B", 3))
        val picks = RandomWheelEngine.draw(items, allowRepeat = true, count = 4, recentLabels = emptyList())
        assertEquals(4, picks.size)
        for (p in picks) {
            assertTrue(p.index == 0 || p.index == 1)
            assertEquals(items[p.index].weight / 4.0, p.probability, 1e-9)
            assertEquals(items[p.index].label, p.label)
        }
    }

    @Test
    fun 总权重为零时draw返回空列表() {
        val picks = RandomWheelEngine.draw(
            listOf(item("A", 0)), allowRepeat = true, count = 3, recentLabels = emptyList()
        )
        assertTrue(picks.isEmpty())
    }
}
