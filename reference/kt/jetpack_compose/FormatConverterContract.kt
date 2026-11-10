package com.omnibox.feature.formatconverter

import android.net.Uri

enum class MediaClass(val label: String) {
    IMAGE("图片转换"),
    AUDIO("音频提取/转码"),
    DOCUMENT("文档与数据"),
    VIDEO("视频轻量压转")
}

enum class TargetImageFormat(val ext: String, val mime: String, val desc: String) {
    WEBP("webp", "image/webp", "体积极小 · 网页与移动端首选"),
    JPEG("jpg", "image/jpeg", "通用高兼容 · 支持相册打印"),
    PNG("png", "image/png", "无损压缩 · 完整保留透明通道"),
    AVIF("avif", "image/avif", "超高压缩比 · HDR广色域支持"),
    SVG("svg", "image/svg+xml", "位图几何轮廓描摹")
}

data class TranscodingConfig(
    val targetFormat: TargetImageFormat = TargetImageFormat.WEBP,
    val quality: Int = 85,
    val resolutionScale: Float = 1.0f,
    val preserveP3Icc: Boolean = true,
    val stripExif: Boolean = true
)

data class ConvertedItem(
    val originalName: String,
    val convertedName: String,
    val originalSize: Long,
    val convertedSize: Long,
    val durationMs: Long,
    val savedPercentage: Float,
    val fileUri: Uri
)

data class TranscodeMetrics(
    val totalOriginalBytes: Long = 46_200_000L,
    val totalConvertedBytes: Long = 6_800_000L,
    val throughputMBs: Float = 18.6f,
    val peakMemoryMB: Float = 21.2f,
    val psnrDb: Float = 43.8f,
    val durationSec: Float = 2.48f
)

sealed interface FormatConverterUiState {
    data object Idle : FormatConverterUiState
    data class FileSelected(
        val fileUri: Uri,
        val fileName: String,
        val fileSize: Long,
        val dimensions: String,
        val config: TranscodingConfig = TranscodingConfig(),
        val estimatedTargetSize: Long
    ) : FormatConverterUiState
}
