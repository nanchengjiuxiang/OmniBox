package com.omnibox.feature.explore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 站点图标加载：先查内存，再查磁盘缓存，最后请求 `https://<host>/favicon.ico`。
 *
 * 只做这一件事，所以没有引入 Coil/Glide：多一个图片库就多一套协程调度与磁盘策略，
 * 而这里需要的只是「32dp 的小图标，失败就显示首字母」。
 * 任何异常（DNS 失败、超时、非图片响应）都返回 null，由 UI 退化到字母头像。
 */
@Singleton
class FaviconStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val memory = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val cacheDir: File by lazy { File(context.cacheDir, "favicons").apply { mkdirs() } }

    suspend fun load(host: String): Bitmap? = withContext(Dispatchers.IO) {
        if (host.isBlank()) return@withContext null
        memory.get(host)?.let { return@withContext it }

        val file = File(cacheDir, safeName(host) + ".png")
        if (file.exists()) {
            decode(file.readBytes())?.let {
                memory.put(host, it)
                return@withContext it
            }
            file.delete()
        }

        val bytes = fetch("https://$host/favicon.ico") ?: return@withContext null
        val bitmap = decode(bytes) ?: return@withContext null
        memory.put(host, bitmap)
        runCatching { file.writeBytes(bytes) }
        bitmap
    }

    private fun fetch(url: String): ByteArray? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 4000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) OmniBox/1.0")
            setRequestProperty("Accept", "image/*")
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            val length = connection.contentLength
            if (length > MAX_BYTES) return@runCatching null
            val buffer = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val chunk = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(chunk)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_BYTES) return@runCatching null
                    buffer.write(chunk, 0, read)
                }
            }
            buffer.toByteArray()
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun decode(bytes: ByteArray): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (bounds.outWidth / sample > 128 || bounds.outHeight / sample > 128) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }.getOrNull()

    private fun safeName(host: String): String =
        host.lowercase().replace(Regex("[^a-z0-9._-]"), "_")

    private companion object {
        const val MAX_BYTES = 256 * 1024
    }
}
