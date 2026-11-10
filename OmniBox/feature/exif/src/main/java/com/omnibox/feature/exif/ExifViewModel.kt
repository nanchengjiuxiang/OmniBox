package com.omnibox.feature.exif

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibox.core.common.Formatters
import com.omnibox.core.data.local.ToolHistoryDao
import com.omnibox.core.data.local.ToolHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class ExifPage { REPORT, CONFIG }

enum class ExifConfigField {
    GPS, DEVICE, TIME, SOFTWARE, DIMENSIONS, DEEP_CLEAN, KEEP_PHOTOGRAPHY
}

data class ExifUiState(
    val page: ExifPage = ExifPage.REPORT,
    val sourceUri: Uri? = null,
    val sourceName: String = "",
    val config: ExifCleanConfig = ExifCleanConfig(),
    val scan: ExifScan? = null,
    val cleanResult: ExifCleanResult? = null,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

sealed interface ExifEvent {
    data class ImageSelected(val uri: Uri) : ExifEvent
    data class PageChanged(val page: ExifPage) : ExifEvent
    data class Toggle(val field: ExifConfigField) : ExifEvent
    data object ResetDefaults : ExifEvent
    data object Clean : ExifEvent
    data class ExportTo(val uri: Uri) : ExifEvent
    data object DismissMessage : ExifEvent
}

@HiltViewModel
class ExifViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyDao: ToolHistoryDao
) : ViewModel() {

    private val engine = ExifEngine()
    private var sourceData: ByteArray? = null

    private val _uiState = MutableStateFlow(ExifUiState())
    val uiState: StateFlow<ExifUiState> = _uiState.asStateFlow()

    fun onEvent(event: ExifEvent) {
        when (event) {
            is ExifEvent.ImageSelected -> loadImage(event.uri)
            is ExifEvent.PageChanged -> _uiState.update { it.copy(page = event.page) }
            is ExifEvent.Toggle -> toggle(event.field)
            ExifEvent.ResetDefaults -> _uiState.update {
                it.copy(config = ExifCleanConfig(), cleanResult = null)
            }
            ExifEvent.Clean -> clean()
            is ExifEvent.ExportTo -> exportTo(event.uri)
            ExifEvent.DismissMessage -> _uiState.update { it.copy(message = null, error = null) }
        }
    }

    fun exportFileName(): String {
        val base = _uiState.value.sourceName.substringBeforeLast('.', _uiState.value.sourceName)
            .takeIf { it.isNotBlank() } ?: "omnibox_photo"
        return "${base}_clean_${System.currentTimeMillis()}.jpg"
    }

    private fun loadImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    sourceUri = uri,
                    sourceName = "",
                    scan = null,
                    cleanResult = null,
                    isProcessing = true,
                    error = null,
                    message = null,
                    page = ExifPage.REPORT
                )
            }
            val bytes = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } catch (e: Exception) {
                    null
                }
            }
            if (bytes == null || bytes.isEmpty()) {
                _uiState.update { it.copy(isProcessing = false, error = "无法读取所选文件的输入流") }
                return@launch
            }
            sourceData = bytes
            val name = withContext(Dispatchers.IO) { queryDisplayName(uri) }
            val outcome = withContext(Dispatchers.IO) { engine.scan(bytes) }
            outcome.fold(
                onSuccess = { scan ->
                    _uiState.update {
                        it.copy(isProcessing = false, scan = scan, sourceName = name, error = null)
                    }
                },
                onFailure = { cause ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            sourceName = name,
                            error = cause.message ?: "EXIF 读取失败"
                        )
                    }
                }
            )
        }
    }

    private fun toggle(field: ExifConfigField) {
        _uiState.update { state ->
            val c = state.config
            val next = when (field) {
                ExifConfigField.GPS -> c.copy(stripGps = !c.stripGps)
                ExifConfigField.DEVICE -> c.copy(stripDevice = !c.stripDevice)
                ExifConfigField.TIME -> c.copy(stripTime = !c.stripTime)
                ExifConfigField.SOFTWARE -> c.copy(stripSoftware = !c.stripSoftware)
                ExifConfigField.DIMENSIONS -> c.copy(stripDimensions = !c.stripDimensions)
                ExifConfigField.DEEP_CLEAN -> c.copy(deepClean = !c.deepClean)
                ExifConfigField.KEEP_PHOTOGRAPHY -> c.copy(keepPhotographyParams = !c.keepPhotographyParams)
            }
            // 配置变更后旧的验证报告失效，需重新生成
            state.copy(config = next, cleanResult = null)
        }
    }

    private fun clean() {
        val bytes = sourceData
        if (bytes == null) {
            _uiState.update { it.copy(error = "请先选择一张图片") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null, message = null) }
            val config = _uiState.value.config
            val outcome = withContext(Dispatchers.IO) {
                engine.clean(context.cacheDir, bytes, config)
            }
            outcome.fold(
                onSuccess = { result ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            cleanResult = result,
                            page = ExifPage.REPORT,
                            message = "已生成纯净副本：${Formatters.bytes(result.resultBytes)}"
                        )
                    }
                },
                onFailure = { cause ->
                    _uiState.update {
                        it.copy(isProcessing = false, error = cause.message ?: "擦除失败")
                    }
                }
            )
        }
    }

    private fun exportTo(uri: Uri) {
        val result = _uiState.value.cleanResult ?: return
        viewModelScope.launch {
            val written = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(result.outputBytes) } != null
                } catch (e: Exception) {
                    false
                }
            }
            if (!written) {
                _uiState.update { it.copy(message = "导出失败：无法写入所选位置") }
                return@launch
            }
            val s = _uiState.value
            historyDao.insert(
                ToolHistoryEntity(
                    toolId = "exif_clean",
                    summary = "擦除 ${s.sourceName} · ${result.before.sensitiveCount} 项敏感元数据 → " +
                        "${result.after.sensitiveCount} 项残留 · ${Formatters.bytes(result.resultBytes)}",
                    createdAt = System.currentTimeMillis()
                )
            )
            _uiState.update {
                it.copy(message = "已导出纯净副本 · ${Formatters.bytes(result.resultBytes)}")
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String = try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        if (cursor == null) {
            uri.lastPathSegment ?: "所选照片"
        } else {
            cursor.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) it.getString(idx) ?: (uri.lastPathSegment ?: "所选照片")
                    else uri.lastPathSegment ?: "所选照片"
                } else {
                    uri.lastPathSegment ?: "所选照片"
                }
            }
        }
    } catch (e: Exception) {
        uri.lastPathSegment ?: "所选照片"
    }
}
