package com.omnibox.feature.randomwheel

import com.omnibox.core.data.local.WheelItemEntity
import java.security.SecureRandom

/**
 * 纯算法层：密码学加权抽样 + 转盘几何。
 * 不依赖 Android / Compose，所有角度约定与 Compose Canvas 一致：0° = 右侧，正角度 = 顺时针。
 */
object RandomWheelEngine {

    private val secureRandom = SecureRandom()

    /** 一次真实抽样结果；index 为传入 items 的下标。 */
    data class Pick(
        val index: Int,
        val label: String,
        val weight: Int,
        val probability: Double,
        val sectorStartAngle: Float,
        val sectorSweepAngle: Float,
        val sectorCenterAngle: Float,
        val timestampMillis: Long
    )

    fun totalWeight(items: List<WheelItemEntity>): Int =
        items.sumOf { if (it.weight > 0) it.weight else 0 }

    /**
     * 扇区几何：第 0 个扇区从正上方（-90°）起顺时针排布。
     * 返回每个扇区的 (startAngle, sweepAngle)；权重为 0 的项占 0° 扇区。
     */
    fun sectors(items: List<WheelItemEntity>): List<Pair<Float, Float>> {
        val total = totalWeight(items)
        var start = -90f
        return items.map { item ->
            val sweep = if (total <= 0) 0f
            else 360f * (if (item.weight > 0) item.weight else 0) / total.toFloat()
            val pair = start to sweep
            start += sweep
            pair
        }
    }

    fun sectorCenterAngle(items: List<WheelItemEntity>, index: Int): Float {
        val (start, sweep) = sectors(items).getOrElse(index) { -90f to 0f }
        return start + sweep / 2f
    }

    /** SecureRandom 加权抽样；eligible 之外或权重 <= 0 的项永不中签。 */
    fun weightedPick(items: List<WheelItemEntity>, eligible: Set<Int>): Int? {
        val candidates = items.indices.filter { it in eligible && items[it].weight > 0 }
        if (candidates.isEmpty()) return null
        val total = candidates.sumOf { items[it].weight }
        if (total <= 0) return null
        var ticket = secureRandom.nextInt(total)
        for (i in candidates) {
            ticket -= items[i].weight
            if (ticket < 0) return i
        }
        return candidates.last()
    }

    /**
     * 连续抽取 count 次（每次都是一次独立的真实加权抽样）。
     * allowRepeat=false 时排除最近 3 次结果、且本批次内不重复；可抽集合耗尽即停止，不伪造结果。
     */
    fun draw(
        items: List<WheelItemEntity>,
        allowRepeat: Boolean,
        count: Int,
        recentLabels: List<String>
    ): List<Pick> {
        val times = count.coerceIn(1, 10)
        val geometry = sectors(items)
        val total = totalWeight(items)
        if (total <= 0) return emptyList()
        val recent = recentLabels.takeLast(3).toSet()
        val blocked = if (allowRepeat) emptySet()
        else items.indices.filter { items[it].label in recent }.toSet()
        val used = mutableSetOf<Int>()
        val picks = mutableListOf<Pick>()
        for (step in 0 until times) {
            val excluded = if (allowRepeat) emptySet() else used
            val eligible = items.indices.toSet() - blocked - excluded
            val index = weightedPick(items, eligible) ?: break
            used.add(index)
            val item = items[index]
            val (start, sweep) = geometry[index]
            picks.add(
                Pick(
                    index = index,
                    label = item.label,
                    weight = item.weight,
                    probability = item.weight.toDouble() / total.toDouble(),
                    sectorStartAngle = start,
                    sectorSweepAngle = sweep,
                    sectorCenterAngle = start + sweep / 2f,
                    timestampMillis = System.currentTimeMillis()
                )
            )
        }
        return picks
    }

    /**
     * 计算最终旋转角，使中选扇区中心落在顶部指针（-90°）处。
     * Canvas 顺时针旋转 rotation 后，扇区中心角度变为 center + rotation，
     * 要求 (center + rotation) ≡ -90 (mod 360)，再叠加整圈数形成真实的旋转动画。
     */
    fun targetRotation(currentRotation: Float, centerAngle: Float, fullTurns: Int = 5): Float {
        val targetBase = ((-90f - centerAngle) % 360f + 360f) % 360f
        val currentMod = ((currentRotation % 360f) + 360f) % 360f
        val delta = ((targetBase - currentMod) % 360f + 360f) % 360f
        return currentRotation + fullTurns.coerceAtLeast(1) * 360f + delta
    }
}
