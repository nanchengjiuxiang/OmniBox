package com.omnibox.feature.battery

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BatteryModelsTest {

    private fun snapshot(
        level: Int = 84,
        voltageMv: Int = 4180,
        currentMicroA: Long = 4_250_000L,
        tempC: Float = 33.6f,
        status: BatteryStatus = BatteryStatus.CHARGING,
        chargeFull: Long? = 3_760_000L,
        design: Long? = 4_000_000L,
        cycles: Int? = 218
    ) = BatterySnapshot(
        levelPercent = level,
        voltageMv = voltageMv,
        currentMicroA = currentMicroA,
        temperatureC = tempC,
        status = status,
        plugged = PowerSource.USB,
        technology = "Li-poly",
        health = BatteryHealth.GOOD,
        chargeCounterMicroAh = 3_500_000L,
        capacityPercent = 84,
        cycleCount = cycles,
        chargeFullMicroAh = chargeFull,
        chargeFullDesignMicroAh = design,
        timestampMs = 1_700_000_000_000L
    )

    @Test
    fun `功率等于电压乘电流`() {
        // 4.18V × 4.25A = 17.765W
        assertThat(snapshot().powerW).isWithin(0.001).of(17.765)
    }

    @Test
    fun `健康度与损耗率由满充与设计容量计算`() {
        val s = snapshot()
        assertThat(s.stateOfHealthPercent).isEqualTo(94)
        assertThat(s.capacityLossPercent).isEqualTo(6)
        // 缺设计容量 → null（诚实缺省）
        assertThat(snapshot(design = null).stateOfHealthPercent).isNull()
        assertThat(snapshot(design = 0L).stateOfHealthPercent).isNull()
    }

    @Test
    fun `状态码映射`() {
        assertThat(BatteryStatus.fromInt(2)).isEqualTo(BatteryStatus.CHARGING)
        assertThat(BatteryStatus.fromInt(5)).isEqualTo(BatteryStatus.FULL)
        assertThat(BatteryStatus.fromInt(99)).isEqualTo(BatteryStatus.UNKNOWN)
        assertThat(PowerSource.fromInt(4)).isEqualTo(PowerSource.WIRELESS)
        assertThat(PowerSource.fromInt(0)).isEqualTo(PowerSource.NONE)
        assertThat(BatteryHealth.fromInt(3)).isEqualTo(BatteryHealth.OVERHEAT)
    }

    @Test
    fun `电流方向按状态归一化`() {
        // 大量内核充电上报负值
        assertThat(normalizeCurrentMicroA(-4_250_000L, BatteryStatus.CHARGING)).isEqualTo(4_250_000L)
        // 放电却上报正值 → 取负
        assertThat(normalizeCurrentMicroA(800_000L, BatteryStatus.DISCHARGING)).isEqualTo(-800_000L)
        // 非充非放（如已充满）保持原样
        assertThat(normalizeCurrentMicroA(120_000L, BatteryStatus.FULL)).isEqualTo(120_000L)
        assertThat(normalizeCurrentMicroA(0L, BatteryStatus.CHARGING)).isEqualTo(0L)
    }

    @Test
    fun `环形缓冲超容量丢弃最旧样本`() {
        val history = PowerHistory(capacity = 3)
        repeat(5) { i ->
            history.add(
                PowerSample(
                    timestampMs = i * 1000L, levelPercent = i, voltageMv = 4000,
                    currentMicroA = 1000, temperatureC = 30f
                )
            )
        }
        assertThat(history.size).isEqualTo(3)
        assertThat(history.asList().first().timestampMs).isEqualTo(2_000L)
    }

    @Test
    fun `峰值均值与温升斜率`() {
        val history = PowerHistory()
        // 2 分钟内 30°C → 31°C，斜率 0.5°C/min
        history.add(PowerSample(0L, 50, 4000, 1_000_000, 30f))     // 4W
        history.add(PowerSample(60_000L, 60, 4000, 2_000_000, 30.5f)) // 8W
        history.add(PowerSample(120_000L, 70, 4000, 3_000_000, 31f)) // 12W
        assertThat(history.peakW).isEqualTo(12.0)
        assertThat(history.meanW).isEqualTo(8.0)
        assertThat(history.temperatureSlopePerMinute()).isWithin(0.001f).of(0.5f)
    }

    @Test
    fun `不足一分钟的样本不给出斜率`() {
        val history = PowerHistory()
        history.add(PowerSample(0L, 50, 4000, 1_000_000, 30f))
        history.add(PowerSample(30_000L, 50, 4000, 1_000_000, 31f))
        assertThat(history.temperatureSlopePerMinute()).isNull()
    }

    @Test
    fun `CSV 导出包含表头与全部采样`() {
        val history = PowerHistory()
        history.add(PowerSample(1_700_000_000_000L, 84, 4180, 4_250_000, 33.6f))
        val csv = history.toCsv()
        val lines = csv.trim().lines()
        assertThat(lines).hasSize(2)
        assertThat(lines[0]).isEqualTo(
            "timestamp_ms,datetime_utc,level_percent,voltage_mv,current_ua,power_w,temperature_c"
        )
        assertThat(lines[1]).startsWith("1700000000000,")
        assertThat(lines[1]).contains(",84,4180,4250000,17.765,33.6")
    }

    @Test
    fun `格式化输出`() {
        assertThat(formatWatts(17.765)).isEqualTo("17.8 W")
        assertThat(formatMilliAmps(4_250_000L)).isEqualTo("+4,250 mA")
        assertThat(formatMilliAmps(-800_000L)).isEqualTo("-800 mA")
        assertThat(formatCelsius(33.6f)).isEqualTo("33.6°C")
        assertThat(formatMilliAmpHours(null)).isEqualTo("系统未上报")
        assertThat(formatMilliAmpHours(4_000_000L)).isEqualTo("4,000 mAh")
    }
}
