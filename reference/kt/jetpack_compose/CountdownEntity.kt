package com.omnibox.feature.countdown.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "countdown_events")
data class CountdownEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val targetTimestamp: Long,
    val isLunar: Boolean = false,
    val repeatCycle: String = "YEARLY", // YEARLY, MONTHLY, ONCE
    val isPinned: Boolean = false,
    val tag: String = "冲刺",
    val themeColorHex: String = "#6750A4"
)

@Dao
interface CountdownDao {
    @Query("SELECT * FROM countdown_events ORDER BY isPinned DESC, targetTimestamp ASC")
    fun getAllEvents(): Flow<List<CountdownEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CountdownEventEntity): Long

    @Delete
    suspend fun deleteEvent(event: CountdownEventEntity)
}
