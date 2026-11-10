package com.omnibox.feature.imagecompress

import android.net.Uri

/**
 * 封装格式
 */
enum class CompressFormat(val extension: String, val mimeType: String) {
    WEBP("webp", "image/webp"),
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png")
}

/**
 * 分辨率下采样选项
 */
enum class ResolutionScale(val ratio: Float, val label: String) {
    ORIGINAL(1.0f, "原图 (100%)"),
    SCALE_75(0.75f, "2K (75%)"),
    SCALE_1080P(0.5f, "1080P (50%)"),
    AUTO(0.6f, "自适应")
}

/**
 * 压缩后性能与画质指标
 */
data class CompressMetrics(
    val originalSize: Long = 0L,
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val compressedSize: Long = 0L,
    val compressedWidth: Int = 0,
    val compressedHeight: Int = 0,
    val peakMemoryBytes: Long = 0L,      // 堆内存峰值（字节）
    val durationMs: Long = 0L,           // 耗时（毫秒）
    val psnrDb: Float = 42.1f,           // 峰值信噪比（客观画质评分）
    val savingsPercent: Float = 0f       // 节省体积百分比
)

/**
 * 防 OOM 与隐私策略配置
 */
data class CompressConfig(
    val quality: Int = 82,                              // 1..100
    val format: CompressFormat = CompressFormat.WEBP,
    val resolutionScale: ResolutionScale = ResolutionScale.SCALE_1080P,
    val autoInSampleSize: Boolean = true,              // 阶乘二次幂采样，防分配超大位图
    val stripGpsLocation: Boolean = true,               // 抹除 GPS 隐私
    val keepPhotographyExif: Boolean = true,            // 保留 ISO/光圈/快门等参数
    val preserveDisplayP3Color: Boolean = true          // 广色域 ICC Profile 保真
)

sealed interface ImageCompressUiState {
    data object Empty : ImageCompressUiState

    data class Processing(
        val sourceUri: Uri,
        val progress: Float = 0f,
        val stage: String = "正在计算采样率与加载头部元数据..."
    ) : ImageCompressUiState

    data class Ready(
        val sourceUri: Uri,
        val compressedUri: Uri? = null,
        val config: CompressConfig = CompressConfig(),
        val metrics: CompressMetrics = CompressMetrics(),
        val isSaving: Boolean = false
    ) : ImageCompressUiState
}

sealed interface ImageCompressUiEvent {
    data class PickImage(val uri: Uri) : ImageCompressUiEvent
    data class UpdateQuality(val quality: Int) : ImageCompressUiEvent
    data class UpdateFormat(val format: CompressFormat) : ImageCompressUiEvent
    data class UpdateResolution(val scale: ResolutionScale) : ImageCompressUiEvent
    data class ToggleAutoInSampleSize(val enabled: Boolean) : ImageCompressUiEvent
    data class ToggleStripGps(val enabled: Boolean) : ImageCompressUiEvent
    data class ToggleKeepExif(val enabled: Boolean) : ImageCompressUiEvent
    data class TogglePreserveP3(val enabled: Boolean) : ImageCompressUiEvent
    data object SaveToGallery : ImageCompressUiEvent
    data object ShareImage : ImageCompressUiEvent
    data object Reset : ImageCompressUiEvent
}

sealed interface ImageCompressUiEffect {
    data class ShowSnackbar(val message: String) : ImageCompressUiEffect
    data class TriggerHapticFeedback(val isStrong: Boolean = false) : ImageCompressUiEffect
    data class ShareIntent(val uri: Uri, val mimeType: String) : ImageCompressUiEffect
}
