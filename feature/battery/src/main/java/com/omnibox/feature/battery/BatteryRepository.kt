package com.omnibox.feature.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 电池数据仓库：BatteryManager（系统广播 + 属性接口）+ sysfs 深度信息，
 * 全部只读、全部本地，不需要任何权限。
 */
@Singleton
class BatteryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val batteryManager = context.getSystemService(BatteryManager::class.java)
    private val batteryChangedFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

    /** 一次完整快照：粘性广播 + 属性接口 + sysfs 三路合并。 */
    fun current(): BatterySnapshot {
        val intent = context.registerReceiver(null, batteryChangedFilter)
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

        val statusInt = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val status = BatteryStatus.fromInt(statusInt)
        val plugged = PowerSource.fromInt(intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0)
        val health = BatteryHealth.fromInt(intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, 1) ?: 1)
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val temperatureC = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE).let { if (it == Int.MIN_VALUE) Float.NaN else it / 10f }
        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty()
            .ifBlank { "未知" }

        val rawCurrent = batteryManager
            ?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != Long.MIN_VALUE } ?: 0L
        val currentMicroA = normalizeCurrentMicroA(rawCurrent, status)

        val chargeCounter = batteryManager
            ?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.takeIf { it > 0 }
        val capacity = batteryManager
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }

        return BatterySnapshot(
            levelPercent = percent,
            voltageMv = voltageMv,
            currentMicroA = currentMicroA,
            temperatureC = temperatureC,
            status = status,
            plugged = plugged,
            technology = technology,
            health = health,
            chargeCounterMicroAh = chargeCounter,
            capacityPercent = capacity,
            cycleCount = readSysfsInt("cycle_count"),
            chargeFullMicroAh = readSysfsLong("charge_full"),
            chargeFullDesignMicroAh = readSysfsLong("charge_full_design"),
            timestampMs = System.currentTimeMillis()
        )
    }

    /**
     * sysfs 深度读取：循环次数 / 实测满充 / 设计容量。
     * 很多设备 SELinux 禁止应用读 /sys/class/power_supply —— 读不到就返回 null，
     * 界面显示「系统未上报」，绝不编数。
     */
    private fun readSysfsLong(name: String): Long? {
        for (base in SYSFS_BASES) {
            val text = runCatching { File("$base/$name").readText().trim() }.getOrNull()
            val value = text?.toLongOrNull()
            if (value != null && value > 0) return value
        }
        return null
    }

    private fun readSysfsInt(name: String): Int? = readSysfsLong(name)?.toInt()

    companion object {
        private val SYSFS_BASES = listOf(
            "/sys/class/power_supply/battery",
            "/sys/class/power_supply/bms"
        )
    }
}
