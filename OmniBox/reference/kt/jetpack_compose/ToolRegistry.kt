package com.omnibox.core.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable

enum class ToolCategory(val title: String) {
    DAILY("日常生活"),
    MEDIA("媒体图像与硬件"),
    DEV("开发者与文本箱")
}

data class ToolDefinition(
    val id: String,
    val title: String,
    val description: String,
    val category: ToolCategory,
    val route: String,
    val badge: String? = null,
    val requiredPermissions: List<String> = emptyList(),
    val isReady: Boolean = true
)

object ToolRegistry {
    val tools: List<ToolDefinition> = listOf(
        // 日常生活
        ToolDefinition("qrcode", "二维码", "扫码与本地高清生成", ToolCategory.DAILY, "tool/qrcode", "CameraX"),
        ToolDefinition("unit_converter", "单位换算", "物理/汇率/计算换算", ToolCategory.DAILY, "tool/unit_converter", "离线引擎"),
        ToolDefinition("random_wheel", "随机转盘抽签", "选择困难决策神器", ToolCategory.DAILY, "tool/random_wheel", "触感振动"),
        ToolDefinition("countdown", "倒计时/纪念日", "日程无常驻通知打扰", ToolCategory.DAILY, "tool/countdown", "本地提醒"),
        
        // 媒体图像与硬件
        ToolDefinition("image_compress", "图片无损压缩", "双重采样保持画质", ToolCategory.MEDIA, "tool/image_compress", "零 OOM"),
        ToolDefinition("exif_clean", "EXIF 隐私擦除", "清除相机参数与GPS", ToolCategory.MEDIA, "tool/exif_clean", "隐私保护"),
        ToolDefinition("compass", "指南针/水平仪", "磁阻与加速度解算", ToolCategory.MEDIA, "tool/compass", "自适应"),
        ToolDefinition("sound_meter", "环境分贝计", "高敏麦克风声压波形", ToolCategory.MEDIA, "tool/sound_meter", "瞬时采样"),
        ToolDefinition("format_converter", "格式转换", "图片/音频/文档本地转码", ToolCategory.MEDIA, "tool/format_converter", "FFmpeg/NDK"),

        // 开发者与文本箱
        ToolDefinition("password", "安全密码生成", "密码学真随机数引擎", ToolCategory.DEV, "tool/password", "Secure"),
        ToolDefinition("timestamp", "时间戳转换", "毫秒转化与跨时区", ToolCategory.DEV, "tool/timestamp", "双向计算"),
        ToolDefinition("text_tools", "文本全能箱", "哈希/Base64/去重", ToolCategory.DEV, "tool/text_tools", "多维处理"),
        ToolDefinition("dev_box", "代码与格式化", "JSON/正则/进制转换", ToolCategory.DEV, "tool/dev_box", "语法格式")
    )

    fun findById(id: String): ToolDefinition? = tools.find { it.id == id }
}
