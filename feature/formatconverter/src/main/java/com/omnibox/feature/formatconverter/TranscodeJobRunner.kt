package com.omnibox.feature.formatconverter

import android.content.Context
import com.omnibox.core.data.local.ConversionOutputEntity
import com.omnibox.core.data.local.ToolHistoryDao
import com.omnibox.core.data.local.ToolHistoryEntity
import com.omnibox.core.data.local.TranscodeDao
import com.omnibox.core.data.local.TranscodeTaskEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 转码任务在应用中唯一的运行状态。
 * [finishedTicket] 每结束一次任务自增，界面据此判断「本次任务刚完成」而不是被历史任务干扰。
 */
data class TranscodeJobState(
    val taskId: Long = 0L,
    val running: Boolean = false,
    val progress: Int = 0,
    val processed: Int = 0,
    val total: Int = 0,
    val currentName: String = "",
    val queue: List<QueueItem> = emptyList(),
    val finishedTicket: Int = 0,
    val finishedStatus: String = "",
    val finishedDurationMs: Long = 0L
)

/**
 * 批量转码的执行者。故意放在 ViewModel 之外：
 * 用户切到别的工具、甚至把界面返回栈清空，`viewModelScope` 会被取消，
 * 而这里用的是应用级作用域，配合 [TranscodeService] 的前台服务可以继续跑完。
 */
@Singleton
class TranscodeJobRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transcodeDao: TranscodeDao,
    private val historyDao: ToolHistoryDao
) {

    private val engine = FormatConverterEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(TranscodeJobState())
    val state: StateFlow<TranscodeJobState> = _state.asStateFlow()

    private var job: Job? = null
    private var ticket = 0

    init {
        // 上次进程被杀留下的 running/queued 记录不可能再继续，标记失败而不是永久转圈
        scope.launch {
            runCatching { transcodeDao.failInterrupted(System.currentTimeMillis()) }
        }
    }

    /** 产物缓存文件名同时被 ViewModel 的导出逻辑使用，改这里要一起改。 */
    fun cacheFileFor(taskId: Long, fileName: String): File =
        File(context.cacheDir, "omnibox_out_${taskId}_${fileName.replace('/', '_')}")

    fun start(files: List<SourceFileInfo>, format: TargetFormat, quality: Int) {
        if (files.isEmpty()) return
        if (job?.isActive == true) return
        TranscodeService.start(context)
        job = scope.launch { run(files, format, quality) }
    }

    fun cancel() {
        job?.cancel()
    }

    private suspend fun run(files: List<SourceFileInfo>, format: TargetFormat, quality: Int) {
        val startedAt = System.currentTimeMillis()
        val task = TranscodeTaskEntity(
            sourceName = "${files.size} 个文件",
            targetFormat = format.label,
            status = "queued",
            progress = 0,
            createdAt = startedAt
        )
        val taskId = transcodeDao.upsertTask(task)
        val queue = files.map { QueueItem(it.name, "排队中", it.bytes, 0L) }.toMutableList()

        _state.value = TranscodeJobState(
            taskId = taskId,
            running = true,
            total = files.size,
            queue = queue.toList()
        )

        var success = 0
        var fail = 0
        try {
            transcodeDao.upsertTask(task.copy(id = taskId, status = "running", progress = 0))

            files.forEachIndexed { index, info ->
                currentCoroutineContext().ensureActive()
                queue[index] = queue[index].copy(status = "处理中")
                _state.value = TranscodeJobState(
                    taskId = taskId,
                    running = true,
                    progress = (index * 100) / files.size,
                    processed = index,
                    total = files.size,
                    currentName = info.name,
                    queue = queue.toList()
                )

                val bytes = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(info.uri)?.use { it.readBytes() }
                    } catch (e: Exception) {
                        null
                    }
                }
                val outcome = if (bytes == null || bytes.isEmpty()) {
                    Result.failure<ConversionOutcome>(IllegalStateException("无法读取源文件输入流"))
                } else {
                    withContext(Dispatchers.IO) { engine.convert(bytes, TranscodeConfig(format, quality)) }
                }

                val fileName = outputName(info.name, format, index)
                outcome.fold(
                    onSuccess = { o ->
                        withContext(Dispatchers.IO) { cacheFileFor(taskId, fileName).writeBytes(o.outputBytes) }
                        transcodeDao.insertOutput(
                            ConversionOutputEntity(
                                taskId = taskId,
                                fileName = fileName,
                                sourceBytes = info.bytes,
                                outputBytes = o.resultBytes,
                                mimeType = format.mimeType
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

                val progress = ((index + 1) * 100) / files.size
                transcodeDao.upsertTask(task.copy(id = taskId, status = "running", progress = progress))
                _state.value = TranscodeJobState(
                    taskId = taskId,
                    running = true,
                    progress = progress,
                    processed = index + 1,
                    total = files.size,
                    currentName = info.name,
                    queue = queue.toList()
                )
            }

            val status = if (fail == files.size) "failed" else "succeeded"
            val finishedAt = System.currentTimeMillis()
            transcodeDao.upsertTask(task.copy(id = taskId, status = status, progress = 100, finishedAt = finishedAt))
            historyDao.insert(
                ToolHistoryEntity(
                    toolId = "format_converter",
                    summary = "转码 ${files.size} 个文件 → ${format.label} · 成功 $success / 失败 $fail",
                    createdAt = finishedAt
                )
            )
            finish(taskId, status, finishedAt - startedAt, 100, queue)
        } catch (cancel: CancellationException) {
            val finishedAt = System.currentTimeMillis()
            runCatching {
                transcodeDao.upsertTask(
                    task.copy(
                        id = taskId,
                        status = "failed",
                        progress = _state.value.progress,
                        finishedAt = finishedAt
                    )
                )
                historyDao.insert(
                    ToolHistoryEntity(
                        toolId = "format_converter",
                        summary = "转码 ${files.size} 个文件 → ${format.label} · 已取消（成功 $success / 失败 $fail）",
                        createdAt = finishedAt
                    )
                )
            }
            finish(taskId, "cancelled", finishedAt - startedAt, _state.value.progress, queue)
            throw cancel
        }
    }

    private fun finish(
        taskId: Long,
        status: String,
        durationMs: Long,
        progress: Int,
        queue: List<QueueItem>
    ) {
        ticket++
        _state.value = TranscodeJobState(
            taskId = taskId,
            running = false,
            progress = progress,
            processed = queue.count { it.status != "排队中" },
            total = queue.size,
            currentName = "",
            queue = queue,
            finishedTicket = ticket,
            finishedStatus = status,
            finishedDurationMs = durationMs
        )
        TranscodeService.stop(context)
    }

    private fun outputName(name: String, format: TargetFormat, index: Int): String {
        val base = name.substringBeforeLast('.', name).takeIf { it.isNotBlank() } ?: "image"
        return "${base}_${index}.${format.extension}"
    }
}
