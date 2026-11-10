package com.omnibox.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 计划 §7 的本地表结构。
 * 隐私红线：密码本体、音频原始字节、相机帧、原图二进制一律不落盘。
 */

@Entity(tableName = "tool_history")
data class ToolHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolId: String,
    val summary: String,
    val createdAt: Long
)

@Entity(tableName = "favorite_tools")
data class FavoriteToolEntity(
    @PrimaryKey val toolId: String,
    val pinnedAt: Long,
    val position: Int
)

@Entity(tableName = "qr_scan_history")
data class QrScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val format: String,
    val source: String, // camera / image / generated
    val createdAt: Long
)

@Entity(tableName = "countdown_events")
data class CountdownEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val targetEpochMillis: Long,
    val repeatYearly: Boolean,
    val notifyEnabled: Boolean,
    val colorToken: String,
    val createdAt: Long
)

@Entity(tableName = "wheel_presets")
data class WheelPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val updatedAt: Long
)

@Entity(tableName = "wheel_items")
data class WheelItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetId: Long,
    val label: String,
    val weight: Int,
    val colorToken: String,
    val position: Int
)

@Entity(tableName = "transcode_tasks")
data class TranscodeTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceName: String,
    val targetFormat: String,
    val status: String, // queued / running / succeeded / failed
    val progress: Int,
    val createdAt: Long,
    val finishedAt: Long? = null
)

@Entity(tableName = "conversion_outputs")
data class ConversionOutputEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val fileName: String,
    val sourceBytes: Long,
    val outputBytes: Long,
    val mimeType: String
)

@Entity(tableName = "tool_usage_stats")
data class ToolUsageStatEntity(
    @PrimaryKey val toolId: String,
    val launchCount: Int,
    val lastUsedAt: Long
)

@Entity(tableName = "tool_module_settings")
data class ToolModuleSettingEntity(
    @PrimaryKey val toolId: String,
    val enabled: Boolean,
    val showOnHome: Boolean
)
