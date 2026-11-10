package com.omnibox.feature.imagecompress

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ImageCompressUiState(
    val sourceUri: Uri? = null,
    val sourceName: String = "",
    val quality: Int = 82,
    val maxEdge: CompressMaxEdge = CompressMaxEdge.EDGE_1024,
    val format: CompressOutputFormat = CompressOutputFormat.WEBP,
    val preserveAlpha: Boolean = true,
    val isProcessing: Boolean = false,
    val result: CompressResult? = null,
    val error: String? = null,
    val exportMessage: String? = null
)

sealed interface ImageCompressEvent {
    data class ImageSelected(val uri: Uri) : ImageCompressEvent
    data class QualityChanged(val value: Int) : ImageCompressEvent
    data class MaxEdgeChanged(val value: CompressMaxEdge) : ImageCompressEvent
    data class FormatChanged(val value: CompressOutputFormat) : ImageCompressEvent
    data object TogglePreserveAlpha : ImageCompressEvent
    data class ExportTo(val uri: Uri) : ImageCompressEvent
    data object DismissMessage : ImageCompressEvent
}

@HiltViewModel
class ImageCompressViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyDao: ToolHistoryDao
) : ViewModel() {

    private val engine = ImageCompressEngine()
    private var sourceData: ByteArray? = null
    private var compressJob: Job? = null

    private val _uiState = MutableStateFlow(ImageCompressUiState())
    val uiState: StateFlow<ImageCompressUiState> = _uiState.asStateFlow()

    fun onEvent(event: ImageCompressEvent) {
        when (event) {
            is ImageCompressEvent.ImageSelected -> loadImage(event.uri)
            is ImageCompressEvent.QualityChanged -> {
                _uiState.update { it.copy(quality = event.value.coerceIn(1, 100)) }
                recompress()
            }
            is ImageCompressEvent.MaxEdgeChanged -> {
                _uiState.update { it.copy(maxEdge = event.value) }
                recompress()
            }
            is ImageCompressEvent.FormatChanged -> {
                _uiState.update { it.copy(format = event.value) }
                recompress()
            }
            ImageCompressEvent.TogglePreserveAlpha -> {
                _uiState.update { it.copy(preserveAlpha = !it.preserveAlpha) }
                recompress()
            }
            is ImageCompressEvent.ExportTo -> exportTo(event.uri)
            ImageCompressEvent.DismissMessage -> _uiState.update { it.copy(exportMessage = null, error = null) }
        }
    }

    /** SAF 建议文件名，扩展名随真实输出格式变化。 */
    fun exportFileName(): String {
        val s = _uiState.value
        val base = s.sourceName.substringBeforeLast('.', s.sourceName)
            .takeIf { it.isNotBlank() } ?: "omnibox_image"
        return "${base}_compressed_${System.currentTimeMillis()}.${s.format.extension}"
    }

    private fun loadImage(uri: Uri) {
        compressJob?.cancel()
        compressJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    sourceUri = uri,
                    isProcessing = true,
                    result = null,
                    error = null,
                    exportMessage = null
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
                _uiState.update {
                    it.copy(isProcessing = false, error = "无法读取所选文件的输入流，请重新选择图片")
                }
                return@launch
            }
            sourceData = bytes
            val name = withContext(Dispatchers.IO) { queryDisplayName(uri) }
            _uiState.update { it.copy(sourceName = name) }
            val config = currentConfig()
            val outcome = withContext(Dispatchers.IO) { engine.compress(bytes, config) }
            applyOutcome(outcome)
        }
    }

    /** 参数变化后按当前配置实时重算；取消上一条任务避免过期结果覆盖。 */
    private fun recompress() {
        val bytes = sourceData ?: return
        compressJob?.cancel()
        compressJob = viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null, exportMessage = null) }
            val config = currentConfig()
            val outcome = withContext(Dispatchers.IO) { engine.compress(bytes, config) }
            applyOutcome(outcome)
        }
    }

    private fun applyOutcome(outcome: Result<CompressResult>) {
        outcome.fold(
            onSuccess = { result ->
                _uiState.update { it.copy(isProcessing = false, result = result, error = null) }
            },
            onFailure = { cause ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        result = null,
                        error = cause.message ?: "压缩失败"
                    )
                }
            }
        )
    }

    private fun exportTo(uri: Uri) {
        val result = _uiState.value.result ?: return
        viewModelScope.launch {
            val written = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(result.outputBytes) } != null
                } catch (e: Exception) {
                    false
                }
            }
            if (!written) {
                _uiState.update { it.copy(exportMessage = "导出失败：无法写入所选位置") }
                return@launch
            }
            val s = _uiState.value
            historyDao.insert(
                ToolHistoryEntity(
                    toolId = "image_compress",
                    summary = "导出 ${s.sourceName} → ${result.config.format.label} · " +
                        "${Formatters.bytes(result.originalBytes)} → ${Formatters.bytes(result.resultBytes)} · " +
                        "减小 ${Formatters.percent(result.savingsRatio)}",
                    createdAt = System.currentTimeMillis()
                )
            )
            _uiState.update {
                it.copy(
                    exportMessage = "已导出 ${Formatters.bytes(result.resultBytes)} · " +
                        "${result.resultWidth} × ${result.resultHeight}"
                )
            }
        }
    }

    private fun currentConfig(): CompressConfig = _uiState.value.let {
        CompressConfig(
            quality = it.quality,
            maxEdge = it.maxEdge,
            format = it.format,
            preserveAlpha = it.preserveAlpha
        )
    }

    private fun queryDisplayName(uri: Uri): String = try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        if (cursor == null) {
            uri.lastPathSegment ?: "所选图片"
        } else {
            cursor.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) it.getString(idx) ?: (uri.lastPathSegment ?: "所选图片")
                    else uri.lastPathSegment ?: "所选图片"
                } else {
                    uri.lastPathSegment ?: "所选图片"
                }
            }
        }
    } catch (e: Exception) {
        uri.lastPathSegment ?: "所选图片"
    }
}
