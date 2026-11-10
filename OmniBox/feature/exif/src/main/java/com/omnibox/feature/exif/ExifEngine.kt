package com.omnibox.feature.exif

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/** 隐私风险等级，全部由实际读到的 tag 决定。 */
enum class ExifRiskLevel(val label: String) {
    HIGH("隐私危险"),
    MEDIUM("指纹特征"),
    LOW("低风险"),
    NONE("无风险"),
    ABSENT("无此元数据")
}

/** 诊断清单分组，对应 exif_risk_report 的报告卡片。 */
enum class ExifCategory(val label: String, val icon: String) {
    GPS("精确地理定位 (GPS)", "near_me"),
    DEVICE("拍摄硬件及序列特征", "phone_android"),
    TIME("原始拍摄时间戳", "schedule"),
    SOFTWARE("软件 / 作者标签", "edit"),
    LENS("镜头 / 曝光参数", "photo_camera"),
    DIMENSION("尺寸与方向信息", "straighten"),
    THUMBNAIL("内嵌缩略图", "image")
}

/** 单个真实 tag 的读取结果；value == null 表示「无此元数据」，绝不编造。 */
data class ExifRiskItem(
    val category: ExifCategory,
    val tagLabel: String,
    val value: String?,
    val riskLevel: ExifRiskLevel
) {
    val present: Boolean get() = value != null
    val displayValue: String get() = value ?: "无此元数据"
}

/** 一次真实扫描的全部结果。 */
class ExifScan(
    val items: List<ExifRiskItem>,
    val rawTags: Map<String, String>,
    val presentCount: Int,
    val sensitiveCount: Int,
    val safeCount: Int,
    val width: Int,
    val height: Int,
    val sourceBytes: Long,
    val thumbnailPresent: Boolean,
    val artParams: List<Pair<String, String>>
)

/** exif_clean_config 的脱敏开关；deepClean 会覆盖所有保留项。 */
data class ExifCleanConfig(
    val stripGps: Boolean = true,
    val stripDevice: Boolean = true,
    val stripTime: Boolean = true,
    val stripSoftware: Boolean = true,
    val stripDimensions: Boolean = true,
    val deepClean: Boolean = false,
    val keepPhotographyParams: Boolean = true
)

/** 擦除前后对比报告的载体；after 来自对输出字节流的二次 ExifInterface 读取。 */
class ExifCleanResult(
    val outputBytes: ByteArray,
    val before: ExifScan,
    val after: ExifScan,
    val keptTagCount: Int,
    val removedRiskCount: Int
) {
    val resultBytes: Long get() = outputBytes.size.toLong()
    val verifiedClean: Boolean get() = after.sensitiveCount == 0
}

private data class TagSpec(
    val tag: String,
    val label: String,
    val category: ExifCategory,
    val risk: ExifRiskLevel
)

/** 诊断读取的全部 tag，均为 AndroidX ExifInterface 真实支持的常量。 */
private val TAGS: List<TagSpec> = listOf(
    TagSpec(ExifInterface.TAG_GPS_LATITUDE, "纬度 (GPSLatitude)", ExifCategory.GPS, ExifRiskLevel.HIGH),
    TagSpec(ExifInterface.TAG_GPS_LATITUDE_REF, "纬度参考 (N/S)", ExifCategory.GPS, ExifRiskLevel.MEDIUM),
    TagSpec(ExifInterface.TAG_GPS_LONGITUDE, "经度 (GPSLongitude)", ExifCategory.GPS, ExifRiskLevel.HIGH),
    TagSpec(ExifInterface.TAG_GPS_LONGITUDE_REF, "经度参考 (E/W)", ExifCategory.GPS, ExifRiskLevel.MEDIUM),
    TagSpec(ExifInterface.TAG_GPS_ALTITUDE, "海拔高程", ExifCategory.GPS, ExifRiskLevel.MEDIUM),
    TagSpec(ExifInterface.TAG_GPS_DATESTAMP, "GPS 定位日期", ExifCategory.GPS, ExifRiskLevel.MEDIUM),
    TagSpec(ExifInterface.TAG_DATETIME_ORIGINAL, "原始拍摄时间", ExifCategory.TIME, ExifRiskLevel.HIGH),
    TagSpec(ExifInterface.TAG_DATETIME, "文件修改时间", ExifCategory.TIME, ExifRiskLevel.MEDIUM),
    TagSpec(ExifInterface.TAG_MAKE, "制造商 (Make)", ExifCategory.DEVICE, ExifRiskLevel.MEDIUM),
    TagSpec(ExifInterface.TAG_MODEL, "设备型号 (Model)", ExifCategory.DEVICE, ExifRiskLevel.MEDIUM),
    TagSpec(ExifInterface.TAG_BODY_SERIAL_NUMBER, "机身序列号", ExifCategory.DEVICE, ExifRiskLevel.HIGH),
    TagSpec(ExifInterface.TAG_CAMERA_OWNER_NAME, "机主姓名", ExifCategory.DEVICE, ExifRiskLevel.HIGH),
    TagSpec(ExifInterface.TAG_SOFTWARE, "处理软件", ExifCategory.SOFTWARE, ExifRiskLevel.LOW),
    TagSpec(ExifInterface.TAG_ARTIST, "作者 / 版权", ExifCategory.SOFTWARE, ExifRiskLevel.MEDIUM),
    TagSpec(ExifInterface.TAG_USER_COMMENT, "用户注释", ExifCategory.SOFTWARE, ExifRiskLevel.MEDIUM),
    TagSpec(ExifInterface.TAG_LENS_MODEL, "镜头型号", ExifCategory.LENS, ExifRiskLevel.LOW),
    TagSpec(ExifInterface.TAG_LENS_SERIAL_NUMBER, "镜头序列号", ExifCategory.LENS, ExifRiskLevel.HIGH),
    TagSpec(ExifInterface.TAG_FOCAL_LENGTH, "等效焦距", ExifCategory.LENS, ExifRiskLevel.NONE),
    TagSpec(ExifInterface.TAG_F_NUMBER, "光圈孔径", ExifCategory.LENS, ExifRiskLevel.NONE),
    TagSpec(ExifInterface.TAG_EXPOSURE_TIME, "快门速度", ExifCategory.LENS, ExifRiskLevel.NONE),
    TagSpec(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "感光度 ISO", ExifCategory.LENS, ExifRiskLevel.NONE),
    TagSpec(ExifInterface.TAG_ORIENTATION, "方向 (Orientation)", ExifCategory.DIMENSION, ExifRiskLevel.LOW),
    TagSpec(ExifInterface.TAG_IMAGE_WIDTH, "图像宽度", ExifCategory.DIMENSION, ExifRiskLevel.LOW),
    TagSpec(ExifInterface.TAG_IMAGE_LENGTH, "图像高度", ExifCategory.DIMENSION, ExifRiskLevel.LOW)
)

/**
 * EXIF 读取 / 擦除引擎。
 * 擦除采用「解码为 Bitmap → 重新编码输出」：新 JPEG 天然不含原始 EXIF，
 * 因此绝不会用 saveAttributes() 改动用户原图；随后再读一次输出字节流做前后对比验证。
 */
class ExifEngine {

    /** 从原始字节流读取真实元数据；读不到的 tag 一律显示「无此元数据」。 */
    fun scan(bytes: ByteArray): Result<ExifScan> = try {
        Result.success(scanInternal(bytes))
    } catch (t: Throwable) {
        Result.failure(IllegalStateException("无法读取 EXIF：${t.message ?: "文件可能不是受支持的图片格式"}", t))
    }

    private fun scanInternal(bytes: ByteArray): ExifScan {
        val exif = ExifInterface(ByteArrayInputStream(bytes))
        val rawTags = LinkedHashMap<String, String>()
        val items = TAGS.map { spec ->
            val raw = try {
                exif.getAttribute(spec.tag)?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                null
            }
            if (raw != null) rawTags[spec.tag] = raw
            ExifRiskItem(spec.category, spec.label, raw, if (raw == null) ExifRiskLevel.ABSENT else spec.risk)
        }
        val thumbnailPresent = try {
            exif.hasThumbnail()
        } catch (e: Exception) {
            false
        }
        val thumbnailItem = ExifRiskItem(
            ExifCategory.THUMBNAIL,
            "内嵌 EXIF 缩略图",
            if (thumbnailPresent) "存在（可能缓存原始画面）" else null,
            if (thumbnailPresent) ExifRiskLevel.MEDIUM else ExifRiskLevel.ABSENT
        )
        val all = items + thumbnailItem
        val (w, h) = decodeBounds(bytes)
        val artParams = listOf(
            "快门速度" to (rawTags[ExifInterface.TAG_EXPOSURE_TIME] ?: "无此元数据"),
            "光圈孔径" to (rawTags[ExifInterface.TAG_F_NUMBER] ?: "无此元数据"),
            "感光度" to (rawTags[ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY] ?: "无此元数据"),
            "等效焦距" to (rawTags[ExifInterface.TAG_FOCAL_LENGTH] ?: "无此元数据"),
            "白平衡" to (rawTags[ExifInterface.TAG_WHITE_BALANCE] ?: "无此元数据")
        )
        return ExifScan(
            items = all,
            rawTags = rawTags,
            presentCount = all.count { it.present },
            sensitiveCount = all.count { it.present && (it.riskLevel == ExifRiskLevel.HIGH || it.riskLevel == ExifRiskLevel.MEDIUM) },
            safeCount = all.count { it.present && (it.riskLevel == ExifRiskLevel.LOW || it.riskLevel == ExifRiskLevel.NONE) },
            width = w,
            height = h,
            sourceBytes = bytes.size.toLong(),
            thumbnailPresent = thumbnailPresent,
            artParams = artParams
        )
    }

    /**
     * 重新编码擦除。config 决定哪些「无害 / 用户选择保留」的 tag 会被写回到我们自己的产物文件；
     * 敏感类别默认全部不写回。写回失败时保留纯无 EXIF 结果，验证报告始终依据真实输出字节流。
     */
    fun clean(cacheDir: File, bytes: ByteArray, config: ExifCleanConfig): Result<ExifCleanResult> = try {
        Result.success(cleanInternal(cacheDir, bytes, config))
    } catch (t: OutOfMemoryError) {
        Result.failure(IllegalStateException("内存不足：图片过大，无法解码重写", t))
    } catch (t: Throwable) {
        Result.failure(IllegalStateException("擦除失败：${t.message ?: "未知错误"}", t))
    }

    private fun cleanInternal(cacheDir: File, bytes: ByteArray, config: ExifCleanConfig): ExifCleanResult {
        val before = scanInternal(bytes)

        // 重新编码：解码 → 白底合成 → JPEG 编码，产物天然不含原始 EXIF
        val decoded = decodeSampled(bytes)
        val flat = if (decoded.hasAlpha()) {
            val b = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
            Canvas(b).apply {
                drawColor(Color.WHITE)
                drawBitmap(decoded, 0f, 0f, null)
            }
            b
        } else {
            decoded
        }
        val buffer = ByteArrayOutputStream()
        if (!flat.compress(Bitmap.CompressFormat.JPEG, 95, buffer)) {
            throw IllegalStateException("系统 JPEG 编码器不可用")
        }
        val cleanBytes = buffer.toByteArray()

        // 用户选择保留的 tag 写回「我们自己的产物」，绝不触碰原图
        val keepCategories = LinkedHashSet<ExifCategory>()
        if (!config.deepClean) {
            if (!config.stripGps) keepCategories += ExifCategory.GPS
            if (!config.stripDevice) keepCategories += ExifCategory.DEVICE
            if (!config.stripTime) keepCategories += ExifCategory.TIME
            if (!config.stripSoftware) keepCategories += ExifCategory.SOFTWARE
            if (!config.stripDimensions) keepCategories += ExifCategory.DIMENSION
            if (config.keepPhotographyParams) keepCategories += ExifCategory.LENS
        }
        val toWrite = TAGS.filter { it.category in keepCategories }
            .mapNotNull { spec -> before.rawTags[spec.tag]?.let { spec.tag to it } }

        var finalBytes = cleanBytes
        if (toWrite.isNotEmpty()) {
            val tmp = File(cacheDir, "omnibox_exif_${System.currentTimeMillis()}.jpg")
            try {
                tmp.writeBytes(cleanBytes)
                val outExif = ExifInterface(tmp.absolutePath)
                toWrite.forEach { (tag, value) ->
                    try {
                        outExif.setAttribute(tag, value)
                    } catch (e: Exception) {
                        // 个别 tag 不允许写入时跳过，不影响产物有效性
                    }
                }
                outExif.saveAttributes()
                finalBytes = tmp.readBytes()
            } catch (e: Exception) {
                finalBytes = cleanBytes
            } finally {
                tmp.delete()
            }
        }

        // 对最终输出字节流再读一次，作为真实的前后对比报告
        val after = scanInternal(finalBytes)
        return ExifCleanResult(
            outputBytes = finalBytes,
            before = before,
            after = after,
            keptTagCount = after.presentCount,
            removedRiskCount = (before.sensitiveCount - after.sensitiveCount).coerceAtLeast(0)
        )
    }

    private fun decodeBounds(bytes: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.outWidth to options.outHeight
    }

    /** 带采样解码，防止大图重写时一次性分配全分辨率位图。 */
    private fun decodeSampled(bytes: ByteArray, maxEdge: Int = 2048): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        require(w > 0 && h > 0) { "无法解码所选图片" }
        var sample = 1
        val longest = max(w, h)
        while (longest / sample > maxEdge && sample < 16) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (oom: OutOfMemoryError) {
            val retry = BitmapFactory.Options().apply {
                inSampleSize = (sample * 2).coerceAtMost(16)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, retry)
        } ?: throw OutOfMemoryError("位图解码失败")
    }
}
