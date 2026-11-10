package com.omnibox.feature.formatconverter

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibox.core.common.Formatters
import com.omnibox.core.data.local.ConversionOutputEntity
import com.omnibox.core.data.local.ToolHistoryDao
import com.omnibox.core.data.local.ToolHistoryEntity
import com.omnibox.core.data.local.TranscodeDao
import com.omnibox.core.data.local.TranscodeTaskEntity
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

enum class ConverterPage { SETUP, PROGRESS, REPORT, HISTORY }

/** 选中文件的真实信息。 */
data class SourceFileInfo(
    val uri: Uri,
    val name: String,
    val bytes: Long,
    val width: Int,
    val height: Int
)

/** 进度屏的逐文件状态，数值来自真实处理过程。 */
data class QueueItem(
    val name: String,
    val status: String,
    val sourceBytes: Long,
    val outputBytes: Long,
    val reason: String? = null
)

data class FormatConverterUiState(
    val page: ConverterPage = ConverterPage.SETUP,
    val fileInfos: List<SourceFileInfo> = emptyList(),
    val targetFormat: TargetFormat = TargetFormat.WEBP,
    val quality: Int = 85,
    val tasks: List<TranscodeTaskEntity> = emptyList(),
    val outputs: List<ConversionOutputEntity> = emptyList(),
    val currentTaskId: Long? = null,
    val progressPercent: Int = 0,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val currentFileName: String = "",
    val queueItems: List<QueueItem> = emptyList(),
    val isRunning: Boolean = false,
    val taskStatus: String = "",
    val taskDurationMs: Long = 0L,
    val pendingExportFileName: String? = null,
    val error: String? = null,
    val message: String? = null
)

sealed interface FormatConverterEvent {
    data class FilesSelected(val uris: List<Uri>) : FormatConverterEvent
    data object ClearFiles : FormatConverterEvent
    data class FormatChanged(val value: TargetFormat) : FormatConverterEvent
    data class QualityChanged(val value: Int) : FormatConverterEvent
    data class PageChanged(val page: ConverterPage) : FormatConverterEvent
    data object StartConversion : FormatConverterEvent
    data object CancelConversion : FormatConverterEvent
    data class ViewTask(val taskId: Long) : FormatConverterEvent
    data class RequestExport(val fileName: String) : FormatConverterEvent
    data class ExportResult(val uri: Uri) : FormatConverterEvent
    data object DismissMessage : FormatConverterEvent
}

/**
 * 界面状态的所有者。真正的转码在 [TranscodeJobRunner]（应用级作用域 + 前台服务）里跑，
 * 本 ViewModel 只负责选文件、参数与把 runner/ Room 的状态投影成 UI 状态。
 * 这样返回上一级、切到别的工具都不会中断批量任务。
 */
@HiltViewModel
class FormatConverterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transcodeDao: TranscodeDao,
    private val historyDao: ToolHistoryDao,
    private val runner: TranscodeJobRunner
) : ViewModel() {

    private var outputsJob: Job? = null
    private var lastTicket = 0

    private val _uiState = MutableStateFlow(FormatConverterUiState())
    val uiState: StateFlow<FormatConverterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            transcodeDao.observeTasks().collect { list ->
                _uiState.update { it.copy(tasks = list) }
            }
        }
        viewModelScope.launch {
            runner.state.collect { state -> applyJobState(state) }
        }
    }

    fun onEvent(event: FormatConverterEvent) {
        when (event) {
            is FormatConverterEvent.FilesSelected -> loadFiles(event.uris)
            FormatConverterEvent.ClearFiles -> {
                _uiState.update { it.copy(fileInfos = emptyList(), error = null) }
            }
            is FormatConverterEvent.FormatChanged ->
                _uiState.update { it.copy(targetFormat = event.value) }
            is FormatConverterEvent.QualityChanged ->
                _uiState.update { it.copy(quality = event.value.coerceIn(1, 100)) }
            is FormatConverterEvent.PageChanged ->
                _uiState.update { it.copy(page = event.page) }
            FormatConverterEvent.StartConversion -> startConversion()
            FormatConverterEvent.CancelConversion -> cancelConversion()
            is FormatConverterEvent.ViewTask -> viewHistoryTask(event.taskId)
            is FormatConverterEvent.RequestExport ->
                _uiState.update { it.copy(pendingExportFileName = event.fileName) }
            is FormatConverterEvent.ExportResult -> writeExport(event.uri)
            FormatConverterEvent.DismissMessage ->
                _uiState.update { it.copy(message = null, error = null) }
        }
    }

    /** runner 只有一个写入者，这里只做投影：运行中同步进度，刚结束则跳报告页。 */
    private fun applyJobState(state: TranscodeJobState) {
        if (state.finishedTicket != lastTicket) {
            lastTicket = state.finishedTicket
            _uiState.update {
                it.copy(
                    isRunning = false,
                    currentTaskId = state.taskId,
                    page = ConverterPage.REPORT,
                    progressPercent = 100,
                    taskStatus = state.finishedStatus,
                    taskDurationMs = state.finishedDurationMs,
                    currentFileName = "",
                    queueItems = state.queue,
                    message = if (state.finishedStatus == "cancelled") {
                        "已取消当前任务，已完成的产物保留在报告中"
                    } else {
                        null
                    }
                )
            }
            if (state.taskId != 0L) subscribeOutputs(state.taskId)
            return
        }
        if (!state.running) return
        _uiState.update {
            it.copy(
                isRunning = true,
                currentTaskId = state.taskId,
                page = ConverterPage.PROGRESS,
                progressPercent = state.progress,
                processedCount = state.processed,
                totalCount = state.total,
                currentFileName = state.currentName,
                queueItems = state.queue,
                error = null
            )
        }
    }

    private fun loadFiles(uris: List<Uri>) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null, message = null) }
            val infos = withContext(Dispatchers.IO) {
                uris.take(9).map { readSourceInfo(it) }
            }
            _uiState.update {
                it.copy(
                    fileInfos = infos,
                    page = ConverterPage.SETUP,
                    message = if (infos.isEmpty()) "未选择任何文件" else "已载入 ${infos.size} 个文件"
                )
            }
        }
    }

    private fun startConversion() {
        val s = _uiState.value
        if (s.fileInfos.isEmpty()) {
            _uiState.update { it.copy(error = "请先选择至少一张图片") }
            return
        }
        if (s.isRunning) {
            _uiState.update { it.copy(message = "已有任务在转码，请等待完成或先取消") }
            return
        }
        // 先切页面给即时反馈，真实进度由 runner 的 StateFlow 推上来
        _uiState.update {
            it.copy(
                page = ConverterPage.PROGRESS,
                isRunning = true,
                progressPercent = 0,
                processedCount = 0,
                totalCount = s.fileInfos.size,
                currentFileName = s.fileInfos.first().name,
                queueItems = s.fileInfos.map { info -> QueueItem(info.name, "排队中", info.bytes, 0L) },
                message = null,
                error = null
            )
        }
        runner.start(s.fileInfos, s.targetFormat, s.quality)
    }

    private fun cancelConversion() {
        runner.cancel()
        _uiState.update { it.copy(isRunning = false) }
    }

    private fun viewHistoryTask(taskId: Long) {
        val task = _uiState.value.tasks.find { it.id == taskId }
        val duration = task?.let { (it.finishedAt ?: it.createdAt) - it.createdAt } ?: 0L
        _uiState.update {
            it.copy(
                currentTaskId = taskId,
                page = ConverterPage.REPORT,
                outputs = emptyList(),
                taskStatus = task?.status ?: "",
                taskDurationMs = duration,
                progressPercent = task?.progress ?: 0,
                isRunning = false,
                currentFileName = "",
                queueItems = emptyList(),
                error = null
            )
        }
        subscribeOutputs(taskId)
    }

    /** 产物清单始终来自 Room 查询，不依赖内存里的临时 map。 */
    private fun subscribeOutputs(taskId: Long) {
        outputsJob?.cancel()
        outputsJob = viewModelScope.launch {
            transcodeDao.observeOutputs(taskId).collect { list ->
                _uiState.update { it.copy(outputs = list) }
            }
        }
    }

    private fun writeExport(uri: Uri) {
        val s = _uiState.value
        val fileName = s.pendingExportFileName
        if (fileName == null) {
            _uiState.update { it.copy(message = "未选择要导出的产物") }
            return
        }
        val taskId = s.currentTaskId
        if (taskId == null) {
            _uiState.update { it.copy(message = "未关联到转码任务，无法定位产物") }
            return
        }
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                val file = runner.cacheFileFor(taskId, fileName)
                if (file.exists()) file.readBytes() else null
            }
            if (bytes == null) {
                _uiState.update { it.copy(message = "该产物的本地缓存已失效，请重新转换后再导出") }
                return@launch
            }
            val written = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
                } catch (e: Exception) {
                    false
                }
            }
            if (!written) {
                _uiState.update { it.copy(message = "导出失败：无法写入所选位置") }
                return@launch
            }
            historyDao.insert(
                ToolHistoryEntity(
                    toolId = "format_converter",
                    summary = "导出产物 $fileName · ${Formatters.bytes(bytes.size.toLong())}",
                    createdAt = System.currentTimeMillis()
                )
            )
            _uiState.update {
                it.copy(message = "已导出 $fileName · ${Formatters.bytes(bytes.size.toLong())}")
            }
        }
    }

    private fun readSourceInfo(uri: Uri): SourceFileInfo {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } ?: ByteArray(0)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return SourceFileInfo(
            uri = uri,
            name = queryDisplayName(uri),
            bytes = bytes.size.toLong(),
            width = bounds.outWidth,
            height = bounds.outHeight
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
