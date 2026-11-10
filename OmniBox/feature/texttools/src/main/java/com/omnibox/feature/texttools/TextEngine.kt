package com.omnibox.feature.texttools

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

/** 纯本地文本算法：哈希 / Base64 / URL / 排版清洗 / JSON / 进制 / 正则，零网络、零依赖。 */
object TextEngine {

    private fun ByteArray.hex(): String = joinToString("") { String.format("%02x", it.toInt() and 0xff) }

    fun digest(algorithm: String, input: String): String = runCatching {
        MessageDigest.getInstance(algorithm)
            .digest(input.toByteArray(Charsets.UTF_8)).hex()
    }.getOrDefault("")

    fun hashes(input: String): Map<String, String> = linkedMapOf(
        "MD5 (32-Bit)" to digest("MD5", input),
        "SHA-1" to digest("SHA-1", input),
        "SHA-256" to digest("SHA-256", input),
        "SHA-512" to digest("SHA-512", input)
    )

    fun stats(input: String): Triple<Int, Int, Int> = Triple(
        input.length,
        if (input.isEmpty()) 0 else input.lines().size,
        input.toByteArray(Charsets.UTF_8).size
    )

    fun base64(input: String, encode: Boolean): String = runCatching {
        if (encode) Base64.getEncoder().encodeToString(input.toByteArray(Charsets.UTF_8))
        else String(Base64.getDecoder().decode(input.trim()), Charsets.UTF_8)
    }.getOrElse { "解码失败：${it.message ?: "非法 Base64"}" }

    fun url(input: String, encode: Boolean): String = runCatching {
        if (encode) URLEncoder.encode(input, "UTF-8")
        else URLDecoder.decode(input, "UTF-8")
    }.getOrElse { "转换失败：${it.message ?: "非法编码"}" }

    enum class CleanOp { TRIM_LINES, REMOVE_BLANK, FULLWIDTH_TO_HALF, UPPER, LOWER, TITLE, UNIQUE_LINES }

    fun clean(input: String, op: CleanOp): String = when (op) {
        CleanOp.TRIM_LINES -> input.lines().joinToString("\n") { it.trim() }
        CleanOp.REMOVE_BLANK -> input.lines().filter { it.isNotBlank() }.joinToString("\n")
        CleanOp.FULLWIDTH_TO_HALF -> input.map { c ->
            when {
                c.code == 0x3000 -> ' '
                c.code in 0xFF01..0xFF5E -> (c.code - 0xFEE0).toChar()
                else -> c
            }
        }.joinToString("")
        CleanOp.UPPER -> input.uppercase(Locale.getDefault())
        CleanOp.LOWER -> input.lowercase(Locale.getDefault())
        CleanOp.TITLE -> input.lowercase(Locale.getDefault())
            .split(" ").joinToString(" ") { it.replaceFirstChar { ch -> ch.titlecase(Locale.getDefault()) } }
        // 去重复行：保留首次出现顺序；末尾换行会被 lines() 拆出一个空串，
        // 去重后 join 会多出一个尾随换行，这里统一去掉，避免结果比原文多一行空行
        CleanOp.UNIQUE_LINES -> input.lines().distinct().joinToString("\n").trimEnd('\n')
    }

    const val SAMPLE = "OmniBox 离线工具箱\n\n支持哈希摘要、Base64 与文本清洗。\n支持哈希摘要、Base64 与文本清洗。\n"

    /** JSON 美化 / 校验（使用系统内置 org.json，不引入第三方解析库）。 */
    fun prettyJson(input: String, indent: Int = 2): Pair<String, String?> = runCatching {
        val t = input.trim()
        val formatted = when {
            t.startsWith("{") -> JSONObject(t).toString(indent)
            t.startsWith("[") -> JSONArray(t).toString(indent)
            else -> return Pair("", "输入不是 JSON 对象或数组")
        }
        Pair(formatted, null)
    }.getOrElse { Pair("", "语法错误：${it.message ?: "解析失败"}") }

    const val JSON_SAMPLE =
        "{\"service\":\"local-matrix\",\"secure\":true,\"timestamp\":1711929600," +
            "\"nodes\":[\"alpha\",\"bravo\"],\"details\":{\"code\":200,\"message\":\"操作成功\"}}"

    data class BaseResult(val decimal: String, val hex: String, val binary: String, val octal: String)

    fun convertBase(input: String, radix: Int, bitWidth: Int): BaseResult? {
        val cleaned = input.trim().removePrefix("0x").removePrefix("0X").removePrefix("#")
        val value = cleaned.toLongOrNull(radix) ?: return null
        val mask = when (bitWidth) {
            8 -> 0xFFL
            16 -> 0xFFFFL
            32 -> 0xFFFFFFFFL
            else -> -1L
        }
        val v = value and mask
        return BaseResult(
            decimal = v.toString(),
            hex = v.toULong().toString(16).uppercase(Locale.getDefault()),
            binary = java.lang.Long.toBinaryString(v),
            octal = java.lang.Long.toOctalString(v)
        )
    }

    data class RegexResult(val matches: List<String>, val groups: List<List<String>>, val error: String?)

    fun regex(pattern: String, flags: Set<RegexOption>, test: String): RegexResult = runCatching {
        val r = Regex(pattern, flags)
        val found = r.findAll(test).toList()
        RegexResult(
            matches = found.map { it.value },
            groups = found.map { it.groupValues.drop(1) },
            error = null
        )
    }.getOrElse { RegexResult(emptyList(), emptyList(), "正则错误：${it.message ?: "非法表达式"}") }

    const val REGEX_SAMPLE =
        "你好，开发者！请联络 dev-support@offline.local 或者管理员 admin_ops+sec@system.corp 探讨技术。"

    fun urlParams(url: String): List<Pair<String, String>> = runCatching {
        val uri = Uri.parse(url)
        uri.queryParameterNames.sorted().map { key -> key to (uri.getQueryParameter(key).orEmpty()) }
    }.getOrDefault(emptyList())

    const val URL_SAMPLE =
        "https://console.cloud.internal/cluster/deploy?env=production&clusterId=node-asia-09" +
            "&filter_tags=%E6%A0%B8%E5%BF%83%E6%9C%8D%E5%8A%A1&retry=true#overview"
}
