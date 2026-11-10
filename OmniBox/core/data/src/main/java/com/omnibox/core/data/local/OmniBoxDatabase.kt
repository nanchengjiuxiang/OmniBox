package com.omnibox.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        ToolHistoryEntity::class,
        FavoriteToolEntity::class,
        QrScanHistoryEntity::class,
        CountdownEventEntity::class,
        WheelPresetEntity::class,
        WheelItemEntity::class,
        TranscodeTaskEntity::class,
        ConversionOutputEntity::class,
        ToolUsageStatEntity::class,
        ToolModuleSettingEntity::class
    ],
    version = 1,
    exportSchema = false // P2 接入 schema 归档后再改为 true 并配置 room.schemaLocation
)
abstract class OmniBoxDatabase : RoomDatabase() {
    abstract fun toolHistoryDao(): ToolHistoryDao
    abstract fun favoriteToolDao(): FavoriteToolDao
    abstract fun qrHistoryDao(): QrHistoryDao
    abstract fun countdownDao(): CountdownDao
    abstract fun wheelDao(): WheelDao
    abstract fun transcodeDao(): TranscodeDao
    abstract fun toolUsageDao(): ToolUsageDao
    abstract fun moduleSettingDao(): ModuleSettingDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OmniBoxDatabase =
        Room.databaseBuilder(context, OmniBoxDatabase::class.java, "omnibox.db").build()

    @Provides fun provideToolHistoryDao(db: OmniBoxDatabase): ToolHistoryDao = db.toolHistoryDao()
    @Provides fun provideFavoriteDao(db: OmniBoxDatabase): FavoriteToolDao = db.favoriteToolDao()
    @Provides fun provideQrHistoryDao(db: OmniBoxDatabase): QrHistoryDao = db.qrHistoryDao()
    @Provides fun provideCountdownDao(db: OmniBoxDatabase): CountdownDao = db.countdownDao()
    @Provides fun provideWheelDao(db: OmniBoxDatabase): WheelDao = db.wheelDao()
    @Provides fun provideTranscodeDao(db: OmniBoxDatabase): TranscodeDao = db.transcodeDao()
    @Provides fun provideUsageDao(db: OmniBoxDatabase): ToolUsageDao = db.toolUsageDao()
    @Provides fun provideModuleSettingDao(db: OmniBoxDatabase): ModuleSettingDao = db.moduleSettingDao()
}
