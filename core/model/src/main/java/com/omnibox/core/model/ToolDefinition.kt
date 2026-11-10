package com.omnibox.core.model

/**
 * 工具注册表条目。
 * 首页、收藏、搜索、设置、小组件全部从同一注册表取数，
 * 保证「界面上的工具数量」与「真实可用的功能数量」永远一致（计划 §6.4）。
 */
data class ToolDefinition(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val category: ToolCategory,
    /** 首页分组标题，按设计稿的三段式分组。 */
    val group: ToolGroup,
    /** Material Symbols 名称，由 core:designsystem 的 OmniIcon 映射到 Compose 矢量图标。 */
    val icon: String,
    val route: String,
    /** 首页固定展示顺序，与设计稿一致；注册表按此字段排序。 */
    val order: Int = 0,
    val permissions: List<String> = emptyList(),
    /** false = 该功能仍在排期中，界面按设计稿显示但点击给出明确提示，不作假交互。 */
    val isReady: Boolean = true,
    val supportsWidget: Boolean = false
)

enum class ToolCategory(val label: String) {
    ALL("全部"),
    DAILY("日常生活"),
    MEDIA("媒体图像"),
    SENSOR("传感器硬件"),
    DEV("开发调试")
}

enum class ToolGroup(val label: String) {
    DAILY("日常生活"),
    MEDIA_HARDWARE("媒体图像与硬件"),
    DEV_TEXT("开发者与文本箱")
}

/** 稳定的页面 ID，与《OmniBox-Android开发计划.md》附录 A 一一对应。 */
object ScreenId {
    const val HOME = "home"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val TOOL_SETTINGS = "tool_settings"
    const val TOOL_HISTORY = "tool_history"
    const val SEARCH_RESULT = "search_result"
    const val QR_SCAN_ACTIVE = "qr_scan_active"
    const val QR_PERMISSION = "qr_permission"
    const val QR_GENERATE = "qr_generate"
    const val QR_EXPORT_CONFIG = "qr_export_config"
    const val QR_HISTORY = "qr_history"
    const val IMAGE_COMPRESS = "image_compress"
    const val EXIF_RISK_REPORT = "exif_risk_report"
    const val EXIF_CLEAN_CONFIG = "exif_clean_config"
    const val FORMAT_CONVERTER = "format_converter"
    const val TRANSCODING_PROGRESS = "transcoding_progress"
    const val CONVERSION_REPORT = "conversion_report"
    const val TEXT_TOOLS = "text_tools"
    const val TEXT_TOOLS_EMPTY = "text_tools_empty"
    const val DEV_TOOLS = "dev_tools"
    const val COMPASS_ACTIVE = "compass_active"
    const val COMPASS_DEGRADED = "compass_degraded"
    const val SOUND_METER = "sound_meter"
    const val SOUND_PERMISSION = "sound_permission"
    const val PASSWORD = "password"
    const val UNIT_CONVERTER = "unit_converter"
    const val TIMESTAMP = "timestamp"
    const val RANDOM_WHEEL = "random_wheel"
    const val WHEEL_EDITOR = "wheel_editor"
    const val COUNTDOWN_EMPTY = "countdown_empty"
    const val COUNTDOWN_LIST = "countdown_list"
    const val COUNTDOWN_EDITOR = "countdown_editor"
}

object Routes {
    const val HOME = "home"
    /** 探索 · 精选导航：外链目录与收藏，独立于本地工具注册表。 */
    const val EXPLORE = "explore"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val TOOL_SETTINGS = "tool_settings"
    const val TOOL_HISTORY = "tool_history"
    const val SEARCH = "search_result"
    /** 无参搜索页入口（收藏页右上角搜索按钮直达）。与 SEARCH 同值，保留独立常量便于语义区分。 */
    const val SEARCH_RESULT = "search_result"
    const val SEARCH_PATTERN = "search_result?q={q}"
    fun search(q: String): String =
        "$SEARCH?q=${q.trim().replace("/", "／").replace("&", "＆").ifEmpty { " " }}"
    fun tool(id: String) = "tool/$id"
    const val TOOL_PATTERN = "tool/{tool_id}"
}
