package com.omnibox.feature.formatconverter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** 目标图片格式；AVIF / SVG 等不在本实现范围内，界面不做虚假可选项。 */
enum class TargetFormat(val label: String, val desc: String, val extension: String, val mimeType: String) {
    WEBP("WebP", "体积极小 · 网页与移动端首选", "webp", "image/webp"),
    JPEG("JPEG", "通用高兼容 · 支持相册打印", "jpg", "image/jpeg"),
    PNG("PNG", "无损压缩 · 完整保留透明通道", "png", "image/png")
}

data class TranscodeConfig(
    val targetFormat: TargetFormat = TargetFormat.WEBP,
    val quality: Int = 85
)

/** 一张图片的真实转换结果，所有数值来自实测。 */
class ConversionOutcome(
    val outputBytes: ByteArray,
    val sourceBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String
) {
    val resultBytes: Long get() = outputBytes.size.toLong()
}

/**
 * 真实图片格式互转引擎：BitmapFactory 解码 → Bitmap.compress() 编码。
 * JPEG 无 alpha 通道时先合成白色背景；PNG 保留 ARGB_8888 透明通道；
 * 大图带采样解码，避免一次性全分辨率分配。
 */
class FormatConverterEngine {

    fun convert(bytes: ByteArray, config: TranscodeConfig): Result<ConversionOutcome> = try {
        Result.success(convertInternal(bytes, config))
    } catch (t: OutOfMemoryError) {
        Result.failure(IllegalStateException("内存不足：图片过大，无法分配位图", t))
    } catch (t: Throwable) {
        Result.failure(IllegalStateException(t.message ?: "转码失败", t))
    }

    private fun convertInternal(bytes: ByteArray, config: TranscodeConfig): ConversionOutcome {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        require(w > 0 && h > 0) { "无法识别图片的分辨率，可能不是受支持的图片格式" }

        // 限制解码边长，防止超大图一次性进入应用堆
        var sample = 1
        val longest = max(w, h)
        while (longest / sample > 4096 && sample < 16) sample *= 2
        val keepAlpha = config.targetFormat != TargetFormat.JPEG
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = if (keepAlpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        }
        val decoded = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        } catch (oom: OutOfMemoryError) {
            val retry = BitmapFactory.Options().apply {
                inSampleSize = (sample * 2).coerceAtMost(16)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, retry)
            } catch (again: OutOfMemoryError) {
                null
            }
        } ?: throw OutOfMemoryError("两次采样解码均失败")

        // JPEG 需要白色背景合成透明像素；PNG / WebP 保留 alpha
        val toEncode = if (!keepAlpha && decoded.hasAlpha()) {
            val flat = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply {
                drawColor(Color.WHITE)
                drawBitmap(decoded, 0f, 0f, null)
            }
            flat
        } else {
            decoded
        }

        val format = when (config.targetFormat) {
            TargetFormat.WEBP -> Bitmap.CompressFormat.WEBP
            TargetFormat.JPEG -> Bitmap.CompressFormat.JPEG
            TargetFormat.PNG -> Bitmap.CompressFormat.PNG
        }
        val buffer = ByteArrayOutputStream()
        val ok = toEncode.compress(format, config.quality.coerceIn(1, 100), buffer)
        if (!ok) throw IllegalStateException("系统编码器不支持 ${config.targetFormat.label}")

        return ConversionOutcome(
            outputBytes = buffer.toByteArray(),
            sourceBytes = bytes.size.toLong(),
            width = toEncode.width,
            height = toEncode.height,
            mimeType = config.targetFormat.mimeType
        )
    }
}
