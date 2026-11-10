package com.omnibox.feature.qrcode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibox.core.data.local.QrHistoryDao
import com.omnibox.core.data.local.QrScanHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class QrcodeTab(val label: String, val icon: String) {
    SCAN("扫码识别", "qr_code_scanner"),
    GENERATE("离线生成", "qr_code_2"),
    EXPORT_CONFIG("导出配置", "tune"),
    HISTORY("本地历史", "history")
}

enum class QrHistoryFilter(val label: String, val icon: String, val key: String) {
    ALL("全部", "list", "all"),
    URL("网址外链", "link", "url"),
    WIFI("Wi-Fi 网络", "wifi", "wifi"),
    TEXT("纯文本", "notes", "text"),
    CONTACT("名片通讯录", "contacts", "contact")
}

data class QrHistoryItem(
    val id: Long,
    val content: String,
    val format: String,
    val source: String,
    val createdAt: Long
)

data class QrScanResult(val content: String, val format: String, val source: String)

data class QrcodeUiState(
    val tab: QrcodeTab = QrcodeTab.SCAN,
    val cameraGranted: Boolean = false,
    val shouldShowRationale: Boolean = false,
    val permanentlyDenied: Boolean = false,
    val scanActive: Boolean = true,
    val torchOn: Boolean = false,
    val zoomRatio: Float = 1f,
    val continuousScan: Boolean = false,
    val scanResult: QrScanResult? = null,
    val genConfig: QrGenConfig = QrGenConfig(),
    val generatedBitmap: Bitmap? = null,
    val generating: Boolean = false,
    val history: List<QrHistoryItem> = emptyList(),
    val historyFilter: QrHistoryFilter = QrHistoryFilter.ALL,
    val message: String? = null
)

sealed interface QrcodeEvent {
    data class SwitchTab(val tab: QrcodeTab) : QrcodeEvent
    data class PermissionResult(
        val granted: Boolean,
        val shouldShowRationale: Boolean,
        val permanentlyDenied: Boolean
    ) : QrcodeEvent
    data class Frame(val frame: YuvFrame) : QrcodeEvent
    data object ResumeScan : QrcodeEvent
    data object ToggleTorch : QrcodeEvent
    data class SetZoom(val ratio: Float) : QrcodeEvent
    data object ToggleContinuous : QrcodeEvent
    data class GenTextChanged(val text: String) : QrcodeEvent
    data class SetEcLevel(val level: QrEcLevel) : QrcodeEvent
    data class SetExportSize(val size: Int) : QrcodeEvent
    data class SetForeground(val argb: Int) : QrcodeEvent
    data object ToggleTransparentBg : QrcodeEvent
    data class SetQuietZone(val zone: Int) : QrcodeEvent
    data object Generate : QrcodeEvent
    data object ApplyExportConfig : QrcodeEvent
    data object ResetGenDefaults : QrcodeEvent
    data class ExportPng(val uri: Uri) : QrcodeEvent
    data class PickImage(val uri: Uri) : QrcodeEvent
    data class SetHistoryFilter(val filter: QrHistoryFilter) : QrcodeEvent
    data object ClearHistory : QrcodeEvent
    data class ExportHistoryCsv(val uri: Uri) : QrcodeEvent
    data class CopyToGenerate(val text: String) : QrcodeEvent
    data class ShowMessage(val text: String) : QrcodeEvent
    data object ClearMessage : QrcodeEvent
}

@HiltViewModel
class QrcodeViewModel @Inject constructor(
    private val qrHistoryDao: QrHistoryDao,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(QrcodeUiState())
    val uiState: StateFlow<QrcodeUiState> = _uiState.asStateFlow()

    /** CONFLATED：只保留最新一帧，天然背压，避免解码耗时期间帧堆积。 */
    private val frameChannel = Channel<YuvFrame>(Channel.CONFLATED)

    @Volatile
    private var guardReset = false

    init {
        // 单条 IO 协程顺序消费相机帧并做 ZXing 解码，主线程零阻塞。
        viewModelScope.launch(Dispatchers.IO) {
            var lastContent: String? = null
            var lastAt = 0L
            for (frame in frameChannel) {
                if (guardReset) {
                    lastContent = null
                    lastAt = 0L
                    guardReset = false
                }
                if (!_uiState.value.scanActive) continue
                val result = QrcodeEngine.decodeYuv(
                    frame.bytes, frame.width, frame.height, frame.rowStride, frame.rotationDegrees
                ) ?: continue
                val now = System.currentTimeMillis()
                if (result.content == lastContent && now - lastAt < 2500L) continue
                lastContent = result.content
                lastAt = now
                publishScan(result, "camera")
                if (_uiState.value.continuousScan) {
                    delay(1600)
                    _uiState.update { it.copy(scanActive = true) }
                }
            }
        }
        // Room 历史真实流入状态。
        viewModelScope.launch {
            qrHistoryDao.observeAll()
                .map { rows ->
                    rows.map {
                        QrHistoryItem(it.id, it.content, it.format, it.source, it.createdAt)
                    }
                }
                .collect { items -> _uiState.update { it.copy(history = items) } }
        }
    }

    fun onEvent(event: QrcodeEvent) {
        when (event) {
            is QrcodeEvent.SwitchTab -> _uiState.update { it.copy(tab = event.tab) }
            is QrcodeEvent.PermissionResult -> _uiState.update {
                it.copy(
                    cameraGranted = event.granted,
                    shouldShowRationale = event.shouldShowRationale,
                    permanentlyDenied = event.permanentlyDenied,
                    scanActive = true,
                    scanResult = if (event.granted) it.scanResult else null
                )
            }
            is QrcodeEvent.Frame -> frameChannel.trySend(event.frame)
            QrcodeEvent.ResumeScan -> {
                guardReset = true
                _uiState.update { it.copy(scanActive = true, scanResult = null) }
            }
            QrcodeEvent.ToggleTorch -> _uiState.update { it.copy(torchOn = !it.torchOn) }
            is QrcodeEvent.SetZoom -> _uiState.update { it.copy(zoomRatio = event.ratio.coerceIn(1f, 5f)) }
            QrcodeEvent.ToggleContinuous -> _uiState.update { it.copy(continuousScan = !it.continuousScan) }
            is QrcodeEvent.GenTextChanged -> _uiState.update {
                it.copy(genConfig = it.genConfig.copy(text = event.text.take(1000)))
            }
            is QrcodeEvent.SetEcLevel -> _uiState.update {
                it.copy(genConfig = it.genConfig.copy(ecLevel = event.level))
            }
            is QrcodeEvent.SetExportSize -> _uiState.update {
                it.copy(genConfig = it.genConfig.copy(exportSize = event.size))
            }
            is QrcodeEvent.SetForeground -> _uiState.update {
                it.copy(genConfig = it.genConfig.copy(fgArgb = event.argb))
            }
            QrcodeEvent.ToggleTransparentBg -> _uiState.update {
                it.copy(genConfig = it.genConfig.copy(bgTransparent = !it.genConfig.bgTransparent))
            }
            is QrcodeEvent.SetQuietZone -> _uiState.update {
                it.copy(genConfig = it.genConfig.copy(quietZone = event.zone))
            }
            QrcodeEvent.Generate -> generate()
            QrcodeEvent.ApplyExportConfig -> {
                _uiState.update { it.copy(tab = QrcodeTab.GENERATE) }
                generate()
            }
            QrcodeEvent.ResetGenDefaults -> {
                _uiState.update {
                    it.copy(genConfig = QrGenConfig(text = it.genConfig.text))
                }
                generate()
            }
            is QrcodeEvent.ExportPng -> exportPng(event.uri)
            is QrcodeEvent.PickImage -> pickImage(event.uri)
            is QrcodeEvent.SetHistoryFilter -> _uiState.update { it.copy(historyFilter = event.filter) }
            QrcodeEvent.ClearHistory -> viewModelScope.launch(Dispatchers.IO) {
                qrHistoryDao.clearAll()
                setMessage("本地识读历史已清空")
            }
            is QrcodeEvent.ExportHistoryCsv -> exportHistoryCsv(event.uri)
            is QrcodeEvent.CopyToGenerate -> {
                _uiState.update {
                    it.copy(
                        tab = QrcodeTab.GENERATE,
                        genConfig = it.genConfig.copy(text = event.text.take(1000))
                    )
                }
                generate()
            }
            is QrcodeEvent.ShowMessage -> _uiState.update { it.copy(message = event.text) }
            QrcodeEvent.ClearMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    /** 将一次真实识读写入界面状态并持久化到 Room（source 由调用方给定）。 */
    private suspend fun publishScan(result: QrDecodeResult, source: String) {
        _uiState.update {
            it.copy(
                tab = QrcodeTab.SCAN,
                scanActive = false,
                scanResult = QrScanResult(result.content, result.format, source),
                message = "识别成功 · 已写入本地历史"
            )
        }
        qrHistoryDao.insert(
            QrScanHistoryEntity(
                content = result.content,
                format = result.format,
                source = source,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun generate() {
        val config = _uiState.value.genConfig
        if (config.text.isBlank()) {
            setMessage("请先输入要编码的内容")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(generating = true) }
            val bitmap = try {
                QrcodeEngine.encode(config)
            } catch (e: Exception) {
                _uiState.update { it.copy(generating = false) }
                setMessage("生成失败：${e.message ?: "内容过长，请降低容错等级或增大尺寸"}")
                return@launch
            }
            _uiState.update {
                it.copy(generating = false, generatedBitmap = bitmap)
            }
            qrHistoryDao.insert(
                QrScanHistoryEntity(
                    content = config.text,
                    format = "QR_CODE",
                    source = "generated",
                    createdAt = System.currentTimeMillis()
                )
            )
            setMessage("已生成 ${config.exportSize}px 二维码 · 记入历史")
        }
    }

    /** 系统相册选图 → RGBLuminanceSource 离线解码，失败明确报错。 */
    private fun pickImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap: Bitmap? = try {
                appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
            if (bitmap == null) {
                setMessage("无法读取所选图片，请换一张重试")
                return@launch
            }
            val result = QrcodeEngine.decodeBitmap(bitmap)
            if (result == null) {
                setMessage("未在所选图片中识别出有效二维码 / 条码")
            } else {
                publishScan(result, "image")
            }
        }
    }

    /** CreateDocument(image/png) 回调：把内存位图真实写入用户选定位置。 */
    private fun exportPng(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = _uiState.value.generatedBitmap
            if (bitmap == null) {
                setMessage("请先生成二维码再导出")
                return@launch
            }
            val ok = try {
                appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: false
            } catch (e: Exception) {
                false
            }
            setMessage(if (ok) "PNG 已保存到所选位置" else "导出失败：无法写入目标文件")
        }
    }

    /** 从 Room 真实记录生成 CSV（时间戳 / 格式 / 来源 / 原文），UTF-8。 */
    private fun exportHistoryCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val rows = _uiState.value.history
            val csv = buildString {
                append("createdAt,content,format,source\n")
                rows.forEach { r ->
                    append(r.createdAt).append(',')
                    append('"').append(r.content.replace("\"", "\"\"")).append('"').append(',')
                    append(r.format).append(',').append(r.source).append('\n')
                }
            }
            val ok = try {
                appContext.contentResolver.openOutputStream(uri)?.use {
                    it.write(csv.toByteArray(Charsets.UTF_8))
                } != null
            } catch (e: Exception) {
                false
            }
            setMessage(if (ok) "已导出 ${rows.size} 条记录 (CSV)" else "导出失败：无法写入目标文件")
        }
    }

    private fun setMessage(text: String) {
        _uiState.update { it.copy(message = text) }
    }
}
