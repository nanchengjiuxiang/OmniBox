package com.omnibox.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolHistoryDao {
    @Query("SELECT * FROM tool_history ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<ToolHistoryEntity>>

    @Query("SELECT * FROM tool_history WHERE toolId = :toolId ORDER BY createdAt DESC")
    fun observeByTool(toolId: String): Flow<List<ToolHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ToolHistoryEntity): Long

    @Query("DELETE FROM tool_history")
    suspend fun clearAll()

    @Query("DELETE FROM tool_history WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface FavoriteToolDao {
    @Query("SELECT * FROM favorite_tools ORDER BY position ASC")
    fun observeAll(): Flow<List<FavoriteToolEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: FavoriteToolEntity)

    @Query("DELETE FROM favorite_tools WHERE toolId = :toolId")
    suspend fun remove(toolId: String)
}

@Dao
interface QrHistoryDao {
    @Query("SELECT * FROM qr_scan_history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<QrScanHistoryEntity>>

    @Insert
    suspend fun insert(entry: QrScanHistoryEntity): Long

    @Query("DELETE FROM qr_scan_history")
    suspend fun clearAll()
}

@Dao
interface CountdownDao {
    @Query("SELECT * FROM countdown_events ORDER BY targetEpochMillis ASC")
    fun observeAll(): Flow<List<CountdownEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: CountdownEventEntity): Long

    @Query("DELETE FROM countdown_events WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface WheelDao {
    @Query("SELECT * FROM wheel_presets ORDER BY updatedAt DESC")
    fun observePresets(): Flow<List<WheelPresetEntity>>

    @Query("SELECT * FROM wheel_items WHERE presetId = :presetId ORDER BY position ASC")
    fun observeItems(presetId: Long): Flow<List<WheelItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreset(preset: WheelPresetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: WheelItemEntity): Long

    @Query("DELETE FROM wheel_items WHERE id = :id")
    suspend fun deleteItem(id: Long)
}

@Dao
interface TranscodeDao {
    @Query("SELECT * FROM transcode_tasks ORDER BY createdAt DESC")
    fun observeTasks(): Flow<List<TranscodeTaskEntity>>

    @Query("SELECT * FROM conversion_outputs WHERE taskId = :taskId")
    fun observeOutputs(taskId: Long): Flow<List<ConversionOutputEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TranscodeTaskEntity): Long

    @Insert
    suspend fun insertOutput(output: ConversionOutputEntity): Long

    /**
     * 进程被杀后，内存里的转码协程不可能恢复，但任务记录会停在 running/queued。
     * 应用下次启动时把它们标记为失败，避免报告页出现永远转圈的僵尸任务。
     */
    @Query("UPDATE transcode_tasks SET status = 'failed', finishedAt = :now WHERE status IN ('queued', 'running')")
    suspend fun failInterrupted(now: Long): Int
}

@Dao
interface ToolUsageDao {
    @Query("SELECT * FROM tool_usage_stats ORDER BY lastUsedAt DESC")
    fun observeAll(): Flow<List<ToolUsageStatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stat: ToolUsageStatEntity)

    @Query("SELECT * FROM tool_usage_stats WHERE toolId = :toolId")
    suspend fun find(toolId: String): ToolUsageStatEntity?
}

@Dao
interface ModuleSettingDao {
    @Query("SELECT * FROM tool_module_settings")
    fun observeAll(): Flow<List<ToolModuleSettingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: ToolModuleSettingEntity)

    @Query("SELECT * FROM tool_module_settings WHERE toolId = :toolId")
    suspend fun find(toolId: String): ToolModuleSettingEntity?
}
