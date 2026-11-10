package com.omnibox.feature.imagecompress

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/** 输出编码格式，全部由 Bitmap.CompressFormat 真实支持。 */
enum class CompressOutputFormat(val label: String, val extension: String, val mimeType: String) {
    WEBP("WebP 格式", "webp", "image/webp"),
    JPEG("JPEG 兼容", "jpg", "image/jpeg"),
    PNG("PNG 无损", "png", "image/png")
}

/** 目标最大边长；ORIGINAL 表示不做下采样。 */
enum class CompressMaxEdge(val label: String, val pixels: Int?) {
    EDGE_512("512", 512),
    EDGE_1024("1024", 1024),
    EDGE_2048("2048", 2048),
    ORIGINAL("原尺寸", null)
}

data class CompressConfig(
    val quality: Int = 82,
    val maxEdge: CompressMaxEdge = CompressMaxEdge.EDGE_1024,
    val format: CompressOutputFormat = CompressOutputFormat.WEBP,
    val preserveAlpha: Boolean = true
)

/**
 * 一次真实压缩的全部结果。界面上的每一个数值都来自这里：
 * 原始字节数 / 输出字节数 / 像素尺寸 / 采样倍率 / 耗时均为实测值。
 * 两张预览位图被限制在 900px 以内，避免大图在 UI 层反复全分辨率驻留。
 */
class CompressResult(
    val outputBytes: ByteArray,
    val originalBytes: Long,
    val originalMime: String,
    val originalWidth: Int,
    val originalHeight: Int,
    val resultWidth: Int,
    val resultHeight: Int,
    val inSampleSize: Int,
    val durationMs: Long,
    val config: CompressConfig,
    val originalPreview: Bitmap,
    val resultPreview: Bitmap
) {
    val savedBytes: Long get() = originalBytes - outputBytes.size.toLong()
    val savingsRatio: Double
        get() = if (originalBytes > 0L) savedBytes.toDouble() / originalBytes.toDouble() else 0.0
    val resultBytes: Long get() = outputBytes.size.toLong()
}

/**
 * 防 OOM 三段式压缩管道：
 * 1) inJustDecodeBounds 只解析边界，不分配像素缓冲；
 * 2) 按目标最大边长算出 2 的幂 inSampleSize；
 * 3) 二次 decode 得到可处理位图，压缩进 ByteArrayOutputStream。
 * 超大位图分配失败时以更高采样率 + RGB_565 重试，仍失败则抛出明确错误。
 */
class ImageCompressEngine {

    fun compress(sourceBytes: ByteArray, config: CompressConfig): Result<CompressResult> = try {
        Result.success(compressInternal(sourceBytes, config))
    } catch (t: OutOfMemoryError) {
        Result.failure(
            IllegalStateException("内存不足：图片过大，无法分配位图，请降低「最大边长」后重试", t)
        )
    } catch (t: Throwable) {
        Result.failure(IllegalStateException(t.message ?: "压缩失败", t))
    }

    private fun compressInternal(sourceBytes: ByteArray, config: CompressConfig): CompressResult {
        val started = System.currentTimeMillis()

        // ---- 第 1 段：只读取边界 ----
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(ByteArrayInputStream(sourceBytes), null, bounds)
        val originalWidth = bounds.outWidth
        val originalHeight = bounds.outHeight
        require(originalWidth > 0 && originalHeight > 0) {
            "无法识别图片的分辨率，所选文件可能不是受支持的图片格式"
        }
        val originalMime = bounds.outMimeType ?: "image/*"

        // ---- 第 2 段：目标尺寸 + inSampleSize（2 的幂） ----
        val longest = max(originalWidth, originalHeight)
        val edge = config.maxEdge.pixels
        val ratio = if (edge == null) 1f else min(1f, edge.toFloat() / longest.toFloat())
        val targetWidth = (originalWidth * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (originalHeight * ratio).toInt().coerceAtLeast(1)
        val sampleSize = calculateInSampleSize(originalWidth, originalHeight, targetWidth, targetHeight)

        // ---- 第 3 段：带采样解码（OOM 时降级重试） ----
        val keepAlpha = config.preserveAlpha && config.format != CompressOutputFormat.JPEG
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = if (keepAlpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        }
        val decoded = try {
            BitmapFactory.decodeStream(ByteArrayInputStream(sourceBytes), null, decodeOptions)
        } catch (oom: OutOfMemoryError) {
            val retryOptions = BitmapFactory.Options().apply {
                inSampleSize = (sampleSize * 2).coerceAtMost(16)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            try {
                BitmapFactory.decodeStream(ByteArrayInputStream(sourceBytes), null, retryOptions)
            } catch (again: OutOfMemoryError) {
                null
            }
        } ?: throw OutOfMemoryError("两次采样解码均失败，位图分配超出可用内存")

        // 精确微调到目标边长（采样率只能取 2 的幂，需再校准一次比例）
        val scaled = if (decoded.width > targetWidth || decoded.height > targetHeight) {
            val s = min(
                targetWidth.toFloat() / decoded.width,
                targetHeight.toFloat() / decoded.height
            )
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * s).toInt().coerceAtLeast(1),
                (decoded.height * s).toInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }

        // JPEG 无透明通道：先合成白色背景；用户取消保留透明度时同样铺白
        val flatten = config.format == CompressOutputFormat.JPEG || !config.preserveAlpha
        val toCompress = if (flatten && scaled.hasAlpha()) {
            val flat = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(flat)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(scaled, 0f, 0f, null)
            flat
        } else {
            scaled
        }

        val compressFormat = when (config.format) {
            CompressOutputFormat.WEBP -> Bitmap.CompressFormat.WEBP
            CompressOutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
            CompressOutputFormat.PNG -> Bitmap.CompressFormat.PNG
        }
        val buffer = ByteArrayOutputStream()
        val ok = toCompress.compress(compressFormat, config.quality.coerceIn(1, 100), buffer)
        if (!ok) throw IllegalStateException("当前系统编码器不支持 ${config.format.label}")
        val outputBytes = buffer.toByteArray()

        // 预览位图单独限量生成，界面只持有小尺寸副本
        val originalPreview = boundedCopy(toCompress)
        val resultPreview = previewFromBytes(outputBytes)

        return CompressResult(
            outputBytes = outputBytes,
            originalBytes = sourceBytes.size.toLong(),
            originalMime = originalMime,
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            resultWidth = toCompress.width,
            resultHeight = toCompress.height,
            inSampleSize = sampleSize,
            durationMs = System.currentTimeMillis() - started,
            config = config,
            originalPreview = originalPreview,
            resultPreview = resultPreview
        )
    }

    /** 计算最适二次幂采样率，保证解码后的像素数受控。 */
    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var sample = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / sample >= reqHeight && halfWidth / sample >= reqWidth && sample < 32) {
                sample *= 2
            }
        }
        return sample.coerceAtLeast(1)
    }

    /** 复制一份不超过 maxEdge 的预览位图，避免把处理用的大位图暴露给 UI。 */
    private fun boundedCopy(src: Bitmap, maxEdge: Int = 900): Bitmap {
        val longest = max(src.width, src.height)
        return if (longest <= maxEdge) {
            Bitmap.createBitmap(src)
        } else {
            val s = maxEdge.toFloat() / longest
            Bitmap.createScaledBitmap(
                src,
                (src.width * s).toInt().coerceAtLeast(1),
                (src.height * s).toInt().coerceAtLeast(1),
                true
            )
        }
    }

    /** 从压缩结果字节流中采样解码出预览位图。 */
    private fun previewFromBytes(bytes: ByteArray, maxEdge: Int = 900): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / sample > maxEdge && sample < 16) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IllegalStateException("压缩结果无法解码出预览图")
    }
}
