package com.omnibox.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "omnibox_prefs")

/** 设置页每个开关都对应一个真实持久化键，不存在"能点但不生效"的控件。 */
@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")           // system / light / dark
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HAPTIC = booleanPreferencesKey("haptic_feedback")
        val DEFAULT_CATEGORY = intPreferencesKey("default_category")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val EXCLUDE_AMBIGUOUS = booleanPreferencesKey("exclude_ambiguous")
        val DEFAULT_PASSWORD_LENGTH = intPreferencesKey("default_password_length")
        val TIMESTAMP_TIMEZONE = stringPreferencesKey("timestamp_timezone")
        val FIRST_LAUNCH_DONE = booleanPreferencesKey("first_launch_done")
        val OUTPUT_FORMAT = stringPreferencesKey("output_format")           // WEBP / JPEG / PNG
        val STREAM_DECODE = booleanPreferencesKey("stream_decode")
        val STRIP_EXIF = booleanPreferencesKey("strip_exif")
        val KEEP_ORIGINAL = booleanPreferencesKey("keep_original")
        val HAPTIC_LEVEL = stringPreferencesKey("haptic_level")             // subtle / normal / firm / off
        val HISTORY_RETENTION_DAYS = intPreferencesKey("history_retention_days")
        val HISTORY_LIMIT = intPreferencesKey("history_limit")
        val ROUNDING_MODE = stringPreferencesKey("rounding_mode")
        val FORCE_ALL_CATEGORIES = booleanPreferencesKey("force_all_categories")
    }

    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "system" }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: true }
    val haptic: Flow<Boolean> = context.dataStore.data.map { it[Keys.HAPTIC] ?: true }
    val defaultPasswordLength: Flow<Int> = context.dataStore.data.map { it[Keys.DEFAULT_PASSWORD_LENGTH] ?: 16 }
    val timezoneId: Flow<String> = context.dataStore.data.map { it[Keys.TIMESTAMP_TIMEZONE] ?: "Asia/Shanghai" }
    val outputFormat: Flow<String> = context.dataStore.data.map { it[Keys.OUTPUT_FORMAT] ?: "WEBP" }
    val streamDecode: Flow<Boolean> = context.dataStore.data.map { it[Keys.STREAM_DECODE] ?: true }
    val stripExif: Flow<Boolean> = context.dataStore.data.map { it[Keys.STRIP_EXIF] ?: true }
    val keepOriginal: Flow<Boolean> = context.dataStore.data.map { it[Keys.KEEP_ORIGINAL] ?: true }
    val hapticLevel: Flow<String> = context.dataStore.data.map { it[Keys.HAPTIC_LEVEL] ?: "normal" }
    val historyRetentionDays: Flow<Int> = context.dataStore.data.map { it[Keys.HISTORY_RETENTION_DAYS] ?: 30 }
    val historyLimit: Flow<Int> = context.dataStore.data.map { it[Keys.HISTORY_LIMIT] ?: 1000 }
    val forceAllCategories: Flow<Boolean> = context.dataStore.data.map { it[Keys.FORCE_ALL_CATEGORIES] ?: true }

    suspend fun setThemeMode(value: String) { context.dataStore.edit { it[Keys.THEME_MODE] = value } }
    suspend fun setDynamicColor(value: Boolean) { context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = value } }
    suspend fun setHaptic(value: Boolean) { context.dataStore.edit { it[Keys.HAPTIC] = value } }
    suspend fun setDefaultPasswordLength(value: Int) { context.dataStore.edit { it[Keys.DEFAULT_PASSWORD_LENGTH] = value } }
    suspend fun setTimezone(value: String) { context.dataStore.edit { it[Keys.TIMESTAMP_TIMEZONE] = value } }
    suspend fun setFirstLaunchDone() { context.dataStore.edit { it[Keys.FIRST_LAUNCH_DONE] = true } }
    suspend fun setOutputFormat(value: String) { context.dataStore.edit { it[Keys.OUTPUT_FORMAT] = value } }
    suspend fun setStreamDecode(value: Boolean) { context.dataStore.edit { it[Keys.STREAM_DECODE] = value } }
    suspend fun setStripExif(value: Boolean) { context.dataStore.edit { it[Keys.STRIP_EXIF] = value } }
    suspend fun setKeepOriginal(value: Boolean) { context.dataStore.edit { it[Keys.KEEP_ORIGINAL] = value } }
    suspend fun setHapticLevel(value: String) { context.dataStore.edit { it[Keys.HAPTIC_LEVEL] = value } }
    suspend fun setHistoryRetentionDays(value: Int) { context.dataStore.edit { it[Keys.HISTORY_RETENTION_DAYS] = value } }
    suspend fun setHistoryLimit(value: Int) { context.dataStore.edit { it[Keys.HISTORY_LIMIT] = value } }
    suspend fun setForceAllCategories(value: Boolean) { context.dataStore.edit { it[Keys.FORCE_ALL_CATEGORIES] = value } }

    /** 恢复所有工具默认设置：清空本仓库管理的全部键。 */
    suspend fun resetAll() { context.dataStore.edit { it.clear() } }
}
