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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

@HiltViewModel
class FormatConverterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transcodeDao: TranscodeDao,
    private val historyDao: ToolHistoryDao
) : ViewModel() {

    private val engine = FormatConverterEngine()
    private val sourceBytesCache = mutableMapOf<String, ByteArray>()
    private val outputFiles = mutableMapOf<String, File>()

    private var convertJob: Job? = null
    private var outputsJob: Job? = null
    private var activeTask: TranscodeTaskEntity? = null

    private val _uiState = MutableStateFlow(FormatConverterUiState())
    val uiState: StateFlow<FormatConverterUiState> = _uiState.asStateFlow()

    init {
        // 历史任务列表始终来自 Room 查询结果
        viewModelScope.launch {
            transcodeDao.observeTasks().collect { list ->
                _uiState.update { it.copy(tasks = list) }
            }
        }
    }

    fun onEvent(event: FormatConverterEvent) {
        when (event) {
            is FormatConverterEvent.FilesSelected -> loadFiles(event.uris)
            FormatConverterEvent.ClearFiles -> {
                sourceBytesCache.clear()
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

    private fun loadFiles(uris: List<Uri>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = false, error = null, message = null) }
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
        val infos = s.fileInfos
        if (infos.isEmpty()) {
            _uiState.update { it.copy(error = "请先选择至少一张图片") }
            return
        }
        convertJob?.cancel()
        convertJob = viewModelScope.launch {
            val now = System.currentTimeMillis()
            val task = TranscodeTaskEntity(
                sourceName = "${infos.size} 个文件",
                targetFormat = s.targetFormat.label,
                status = "queued",
                progress = 0,
                createdAt = now
            )
            val taskId = transcodeDao.upsertTask(task)
            activeTask = task.copy(id = taskId)
            viewTask(taskId, ConverterPage.PROGRESS, "", 0L, 0)
            _uiState.update { it.copy(isRunning = true, processedCount = 0, totalCount = infos.size, progressPercent = 0) }

            // queued → running
            transcodeDao.upsertTask(task.copy(id = taskId, status = "running", progress = 0))
            activeTask = task.copy(id = taskId, status = "running")

            val startedAt = System.currentTimeMillis()
            val queue = infos.map { QueueItem(it.name, "排队中", it.bytes, 0L) }.toMutableList()
            var success = 0
            var fail = 0

            infos.forEachIndexed { index, info ->
                if (!isActive) return@launch
                _uiState.update {
                    it.copy(
                        currentFileName = info.name,
                        processedCount = index,
                        totalCount = infos.size,
                        queueItems = queue.toList()
                    )
                }
                queue[index] = queue[index].copy(status = "处理中")

                val cached = sourceBytesCache[info.uri.toString()]
                val bytes = cached ?: withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(info.uri)?.use { it.readBytes() }
                    } catch (e: Exception) {
                        null
                    }
                }
                val outcome = if (bytes == null || bytes.isEmpty()) {
                    Result.failure<ConversionOutcome>(IllegalStateException("无法读取源文件输入流"))
                } else {
                    withContext(Dispatchers.IO) {
                        engine.convert(bytes, TranscodeConfig(s.targetFormat, s.quality))
                    }
                }

                val fileName = outputName(info.name, s.targetFormat, index)
                outcome.fold(
                    onSuccess = { o ->
                        val cacheFile = File(context.cacheDir, cacheName(taskId, fileName))
                        withContext(Dispatchers.IO) { cacheFile.writeBytes(o.outputBytes) }
                        outputFiles[fileName] = cacheFile
                        transcodeDao.insertOutput(
                            ConversionOutputEntity(
                                taskId = taskId,
                                fileName = fileName,
                                sourceBytes = info.bytes,
                                outputBytes = o.resultBytes,
                                mimeType = s.targetFormat.mimeType
                            )
                        )
                        queue[index] = QueueItem(info.name, "已完成", info.bytes, o.resultBytes)
                        success++
                    },
                    onFailure = { cause ->
                        val reason = cause.message ?: "未知错误"
                        transcodeDao.insertOutput(
                            ConversionOutputEntity(
                                taskId = taskId,
                                fileName = fileName,
                                sourceBytes = info.bytes,
                                outputBytes = 0L,
                                mimeType = "error/$reason"
                            )
                        )
                        queue[index] = QueueItem(info.name, "失败", info.bytes, 0L, reason)
                        fail++
                    }
                )

                val progress = ((index + 1) * 100) / infos.size
                transcodeDao.upsertTask(
                    task.copy(id = taskId, status = "running", progress = progress)
                )
                activeTask = task.copy(id = taskId, status = "running", progress = progress)
                _uiState.update {
                    it.copy(
                        processedCount = index + 1,
                        progressPercent = progress,
                        queueItems = queue.toList()
                    )
                }
            }

            val status = if (fail == infos.size) "failed" else "succeeded"
            val finishedAt = System.currentTimeMillis()
            transcodeDao.upsertTask(
                task.copy(id = taskId, status = status, progress = 100, finishedAt = finishedAt)
            )
            activeTask = task.copy(id = taskId, status = status, progress = 100, finishedAt = finishedAt)
            val duration = finishedAt - startedAt
            historyDao.insert(
                ToolHistoryEntity(
                    toolId = "format_converter",
                    summary = "转码 ${infos.size} 个文件 → ${s.targetFormat.label} · 成功 $success / 失败 $fail",
                    createdAt = finishedAt
                )
            )
            _uiState.update {
                it.copy(
                    isRunning = false,
                    taskStatus = status,
                    taskDurationMs = duration,
                    progressPercent = 100,
                    currentFileName = "",
                    page = ConverterPage.REPORT
                )
            }
        }
    }

    private fun cancelConversion() {
        convertJob?.cancel()
        val base = activeTask
        val id = _uiState.value.currentTaskId
        if (base != null && id != null) {
            viewModelScope.launch {
                transcodeDao.upsertTask(
                    base.copy(
                        status = "failed",
                        progress = _uiState.value.progressPercent,
                        finishedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        _uiState.update {
            it.copy(isRunning = false, message = "已取消当前任务，已完成的产物保留在报告中")
        }
    }

    private fun viewHistoryTask(taskId: Long) {
        val task = _uiState.value.tasks.find { it.id == taskId }
        val duration = task?.let { (it.finishedAt ?: it.createdAt) - it.createdAt } ?: 0L
        viewTask(
            taskId = taskId,
            page = ConverterPage.REPORT,
            status = task?.status ?: "",
            durationMs = duration,
            progress = task?.progress ?: 0
        )
    }

    /** 切换当前查看的任务，并从 Room 收集该任务的产物清单。 */
    private fun viewTask(
        taskId: Long,
        page: ConverterPage,
        status: String,
        durationMs: Long,
        progress: Int
    ) {
        outputsJob?.cancel()
        outputsJob = viewModelScope.launch {
            transcodeDao.observeOutputs(taskId).collect { list ->
                _uiState.update { it.copy(outputs = list) }
            }
        }
        _uiState.update {
            it.copy(
                currentTaskId = taskId,
                page = page,
                outputs = emptyList(),
                taskStatus = status,
                taskDurationMs = durationMs,
                progressPercent = progress,
                isRunning = false,
                currentFileName = "",
                queueItems = emptyList(),
                error = null
            )
        }
    }

    private fun writeExport(uri: Uri) {
        val fileName = _uiState.value.pendingExportFileName
        if (fileName == null) {
            _uiState.update { it.copy(message = "未选择要导出的产物") }
            return
        }
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                val file = resolveOutputFile(fileName)
                if (file != null && file.exists()) file.readBytes() else null
            }
            if (bytes == null) {
                _uiState.update {
                    it.copy(message = "该产物的本地缓存已失效，请重新转换后再导出")
                }
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

    private fun resolveOutputFile(fileName: String): File? {
        outputFiles[fileName]?.let { return it }
        val taskId = _uiState.value.currentTaskId ?: return null
        return File(context.cacheDir, cacheName(taskId, fileName))
    }

    private fun cacheName(taskId: Long, fileName: String): String =
        "omnibox_out_${taskId}_${fileName.replace('/', '_')}"

    private fun outputName(name: String, format: TargetFormat, index: Int): String {
        val base = name.substringBeforeLast('.', name).takeIf { it.isNotBlank() } ?: "image"
        return "${base}_${index}.${format.extension}"
    }

    private fun readSourceInfo(uri: Uri): SourceFileInfo {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } ?: ByteArray(0)
        sourceBytesCache[uri.toString()] = bytes
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
