package com.omnibox.feature.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibox.core.common.Formatters
import com.omnibox.core.data.ToolCatalog
import com.omnibox.core.data.local.ModuleSettingDao
import com.omnibox.core.data.local.ToolHistoryDao
import com.omnibox.core.data.local.ToolHistoryEntity
import com.omnibox.core.data.local.ToolUsageDao
import com.omnibox.core.data.preferences.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShellUiState(
    val themeMode: String = "system",
    val dynamicColor: Boolean = true,
    val hapticLevel: String = "normal",
    val outputFormat: String = "WEBP",
    val streamDecode: Boolean = true,
    val stripExif: Boolean = true,
    val keepOriginal: Boolean = true,
    val forceAllCategories: Boolean = true,
    val retentionDays: Int = 30,
    val historyLimit: Int = 1000
)

data class StorageInfo(val cacheBytes: Long, val databaseBytes: Long) {
    val totalBytes: Long get() = cacheBytes + databaseBytes
}

@HiltViewModel
class ShellViewModel @Inject constructor(
    val catalog: ToolCatalog,
    val preferences: PreferencesRepository,
    val moduleSettings: ModuleSettingDao,
    val usageDao: ToolUsageDao,
    val historyDao: ToolHistoryDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val groupA = combine(
        preferences.themeMode,
        preferences.dynamicColor,
        preferences.hapticLevel,
        preferences.outputFormat
    ) { theme, dynamic, haptic, format ->
        ShellUiState(themeMode = theme, dynamicColor = dynamic, hapticLevel = haptic, outputFormat = format)
    }
    private val groupB = combine(
        preferences.streamDecode,
        preferences.stripExif,
        preferences.keepOriginal,
        preferences.forceAllCategories
    ) { stream, strip, keep, force -> listOf(stream, strip, keep, force) }
    private val groupC = combine(preferences.historyRetentionDays, preferences.historyLimit) { d, l -> d to l }

    val uiState: StateFlow<ShellUiState> = combine(groupA, groupB, groupC) { a, b, c ->
        a.copy(
            streamDecode = b[0], stripExif = b[1], keepOriginal = b[2], forceAllCategories = b[3],
            retentionDays = c.first, historyLimit = c.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShellUiState())

    /** 本周启动频次由 Room tool_usage_stats 实时求和，不写死设计稿的「42 次」。 */
    val totalLaunches: StateFlow<Int> = usageDao.observeAll()
        .map { list -> list.sumOf { it.launchCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setTheme(v: String) = viewModelScope.launch { preferences.setThemeMode(v) }
    fun setDynamic(v: Boolean) = viewModelScope.launch { preferences.setDynamicColor(v) }
    fun setHapticLevel(v: String) = viewModelScope.launch { preferences.setHapticLevel(v) }
    fun setOutputFormat(v: String) = viewModelScope.launch { preferences.setOutputFormat(v) }
    fun setStreamDecode(v: Boolean) = viewModelScope.launch { preferences.setStreamDecode(v) }
    fun setStripExif(v: Boolean) = viewModelScope.launch { preferences.setStripExif(v) }
    fun setKeepOriginal(v: Boolean) = viewModelScope.launch { preferences.setKeepOriginal(v) }
    fun setForceAllCategories(v: Boolean) = viewModelScope.launch { preferences.setForceAllCategories(v) }
    fun setRetention(v: Int) = viewModelScope.launch { preferences.setHistoryRetentionDays(v) }
    fun resetAll() = viewModelScope.launch { preferences.resetAll() }

    /** 设置页「本地占有空间总计」：真实扫描应用私有缓存与 Room 数据库文件。 */
    fun storageInfo(): StorageInfo {
        val cache = runCatching {
            context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
        val db = runCatching { context.getDatabasePath("omnibox.db").takeIf { it.exists() }?.length() ?: 0L }
            .getOrDefault(0L)
        return StorageInfo(cache, db)
    }

    fun clearCache(): Long {
        val before = storageInfo().cacheBytes
        runCatching { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
        return (before - storageInfo().cacheBytes).coerceAtLeast(0L)
    }

    /** 权限审计：读取系统真实授权状态，而非设计稿里的静态「已按需授权」。 */
    fun permissionStatuses(): List<Pair<String, Boolean>> = listOf(
        "相机权限状态" to isGranted(Manifest.permission.CAMERA),
        "麦克风权限状态" to isGranted(Manifest.permission.RECORD_AUDIO)
    )

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** 导出为 JSON 结构日志（离线，写应用私有目录，由调用方决定是否分享）。 */
    fun exportHistoryJson(entries: List<ToolHistoryEntity>): String = buildString {
        append("[\n")
        entries.forEachIndexed { i, e ->
            append("  {\"toolId\":\"").append(e.toolId).append("\",\"summary\":\"")
                .append(e.summary.replace("\"", "\\\"")).append("\",\"createdAt\":").append(e.createdAt).append("}")
            if (i != entries.lastIndex) append(",")
            append("\n")
        }
        append("]")
    }

    fun storageLabel(bytes: Long): String = Formatters.bytes(bytes)
}
