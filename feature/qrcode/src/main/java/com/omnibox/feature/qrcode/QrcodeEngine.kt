package com.omnibox.feature.qrcode

import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

/** 相机 ImageAnalysis 的一帧 Y 灰度数据（只在内存中流转，绝不落盘）。 */
data class YuvFrame(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val rowStride: Int,
    val rotationDegrees: Int
)

/** 一次成功识读的真实结果。 */
data class QrDecodeResult(val content: String, val format: String)

enum class QrEcLevel(
    val label: String,
    val percent: String,
    val desc: String,
    val zxingLevel: ErrorCorrectionLevel
) {
    L("L 级", "7% 容错", "码点最稀疏，适合超长文本与外链", ErrorCorrectionLevel.L),
    M("M 级", "15% 容错", "标准平衡，普通扫码日常通用", ErrorCorrectionLevel.M),
    Q("Q 级", "25% 容错", "工业级冗余，抵御轻微磨损折痕", ErrorCorrectionLevel.Q),
    H("H 级", "30% 极高", "极高冗余，支持嵌入居中微标 (推荐)", ErrorCorrectionLevel.H)
}

/** 生成参数：所有取值都由界面控件实时驱动，最终交给 ZXing 真实编码。 */
data class QrGenConfig(
    val text: String = "",
    val ecLevel: QrEcLevel = QrEcLevel.H,
    val exportSize: Int = 1024,
    val fgArgb: Int = QR_FOREGROUND_OPTIONS.first().argb,
    val bgTransparent: Boolean = false,
    val quietZone: Int = 2
)

data class QrColorOption(val label: String, val hex: String, val argb: Int)

val QR_FOREGROUND_OPTIONS = listOf(
    QrColorOption("原生紫", "#4F378A", 0xFF4F378A.toInt()),
    QrColorOption("墨黑", "#1C1B22", 0xFF1C1B22.toInt()),
    QrColorOption("深海蓝", "#1A4FA0", 0xFF1A4FA0.toInt()),
    QrColorOption("朱砂红", "#B3261E", 0xFFB3261E.toInt()),
    QrColorOption("森绿", "#1B6B3A", 0xFF1B6B3A.toInt())
)

val QR_WHITE_ARGB: Int = 0xFFFFFFFF.toInt()

val QR_SUPPORTED_FORMAT_LABELS = listOf(
    "QR Code", "Data Matrix", "Aztec", "PDF417", "Code 128", "Code 39", "Code 93",
    "EAN-13", "EAN-8", "UPC-A", "UPC-E", "ITF", "Codabar"
)

val QR_PRESET_SAMPLES = listOf(
    "网址链接 (URL)" to "https://github.com/android/nowinandroid",
    "普通文本" to "OmniBox 万能工具箱 · 纯离线",
    "WiFi 密码" to "WIFI:S:OmniBox_5G;T:WPA;P:pass1234;;",
    "个人名片" to "BEGIN:VCARD\nVERSION:3.0\nFN:陈工程师\nTEL:138-0013-8000\nEND:VCARD",
    "电子邮件" to "mailto:chen@work.local?subject=Hello"
)

/**
 * 二维码纯算法 / 平台封装：ZXing 编解码 + Bitmap 栅格化。
 * 不含任何 UI、网络或文件写入逻辑；相机帧与生成位图都只在调用方内存中存在。
 */
object QrcodeEngine {

    /** 限定 QR 与常见一维 / 二维格式，避免误识与无谓算力开销。 */
    private val decodeHints: Map<DecodeHintType, Any> by lazy {
        EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(
                DecodeHintType.POSSIBLE_FORMATS,
                listOf(
                    BarcodeFormat.QR_CODE,
                    BarcodeFormat.DATA_MATRIX,
                    BarcodeFormat.AZTEC,
                    BarcodeFormat.PDF_417,
                    BarcodeFormat.CODE_128,
                    BarcodeFormat.CODE_39,
                    BarcodeFormat.CODE_93,
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A,
                    BarcodeFormat.UPC_E,
                    BarcodeFormat.ITF,
                    BarcodeFormat.CODABAR
                )
            )
            put(DecodeHintType.TRY_HARDER, true)
            put(DecodeHintType.CHARACTER_SET, "UTF-8")
        }
    }

    private fun newReader(): MultiFormatReader = MultiFormatReader().apply { setHints(decodeHints) }

    /**
     * 解码相机 Y 平面。rowStride 常大于 width（存在行尾 padding），必须先压紧成
     * width×height 的连续矩阵；再依次尝试 4 个旋转方向兼容竖持设备与一维条码。
     */
    fun decodeYuv(
        yBytes: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        rotationDegrees: Int
    ): QrDecodeResult? {
        if (width <= 0 || height <= 0) return null
        var data = compactY(yBytes, width, height, rowStride)
        var w = width
        var h = height
        val baseRotation = when (rotationDegrees) {
            90 -> 1
            180 -> 2
            270 -> 3
            else -> 0
        }
        repeat(baseRotation) {
            val rotated = rotate90(data, w, h)
            data = rotated.first; w = rotated.second; h = rotated.third
        }
        repeat(4) {
            tryDecodeYuv(data, w, h)?.let { return it }
            val rotated = rotate90(data, w, h)
            data = rotated.first; w = rotated.second; h = rotated.third
        }
        return null
    }

    private fun compactY(src: ByteArray, width: Int, height: Int, rowStride: Int): ByteArray {
        val out = ByteArray(width * height)
        for (row in 0 until height) {
            val srcPos = row * rowStride
            if (srcPos >= src.size) break
            val len = minOf(width, src.size - srcPos)
            System.arraycopy(src, srcPos, out, row * width, len)
        }
        return out
    }

    /** 返回 (旋转后的字节, 新宽, 新高)，顺时针 90°。 */
    private fun rotate90(src: ByteArray, w: Int, h: Int): Triple<ByteArray, Int, Int> {
        val nw = h
        val nh = w
        val out = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val nx = h - 1 - y
                val ny = x
                out[ny * nw + nx] = src[y * w + x]
            }
        }
        return Triple(out, nw, nh)
    }

    private fun tryDecodeYuv(data: ByteArray, w: Int, h: Int): QrDecodeResult? {
        if (data.size < w * h) return null
        val source = PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
        val binary = BinaryBitmap(HybridBinarizer(source))
        return try {
            val result = newReader().decode(binary)
            QrDecodeResult(result.text, result.barcodeFormat.name)
        } catch (e: ReaderException) {
            null
        }
    }

    /** 从相册位图解码：RGBLuminanceSource + 多旋转尝试，失败返回 null 由上层明确报错。 */
    fun decodeBitmap(bitmap: Bitmap): QrDecodeResult? {
        var current: Bitmap? = bitmap
        repeat(4) {
            current?.let { tryDecodePixels(it)?.let { r -> return r } }
            current = current?.let { b ->
                val matrix = Matrix().apply { postRotate(90f) }
                Bitmap.createBitmap(b, 0, 0, b.width, b.height, matrix, false)
            }
        }
        return null
    }

    private fun tryDecodePixels(bitmap: Bitmap): QrDecodeResult? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val source = RGBLuminanceSource(w, h, pixels)
        val binary = BinaryBitmap(HybridBinarizer(source))
        return try {
            val result = newReader().decode(binary)
            QrDecodeResult(result.text, result.barcodeFormat.name)
        } catch (e: ReaderException) {
            null
        }
    }

    /** ZXing QRCodeWriter 真实编码 → BitMatrix → ARGB_8888 Bitmap。 */
    @Throws(WriterException::class)
    fun encode(config: QrGenConfig): Bitmap {
        val hints = HashMap<EncodeHintType, Any>().apply {
            put(EncodeHintType.ERROR_CORRECTION, config.ecLevel.zxingLevel)
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, config.quietZone)
        }
        val matrix: BitMatrix = QRCodeWriter().encode(
            config.text,
            BarcodeFormat.QR_CODE,
            config.exportSize,
            config.exportSize,
            hints
        )
        return matrixToBitmap(matrix, config)
    }

    private fun matrixToBitmap(matrix: BitMatrix, config: QrGenConfig): Bitmap {
        val w = matrix.width
        val h = matrix.height
        val fg = config.fgArgb
        val bg = if (config.bgTransparent) 0 else QR_WHITE_ARGB
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                pixels[row + x] = if (matrix.get(x, y)) fg else bg
            }
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }

    /** 按内容对历史做真实分类（与 qr_scan_history.source 无关，仅用于筛选展示）。 */
    fun classify(content: String): String = when {
        content.startsWith("WIFI:", ignoreCase = true) -> "wifi"
        content.startsWith("BEGIN:VCARD", ignoreCase = true) -> "contact"
        content.startsWith("http://", ignoreCase = true) ||
            content.startsWith("https://", ignoreCase = true) ||
            content.startsWith("mailto:", ignoreCase = true) -> "url"
        else -> "text"
    }
}
