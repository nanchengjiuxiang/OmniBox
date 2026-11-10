package com.omnibox.feature.explore

/**
 * 探索页分类。与设计稿的分类 Chips 一一对应；
 * 「全部」只作为筛选态存在，不参与条目的分类归属。
 */
enum class ExploreCategory(val label: String, val icon: String) {
    ALL("全部", "explore"),
    AI_ASSISTANT("AI 助手", "auto_awesome"),
    AI_IMAGE("AI 图像/修图", "palette"),
    AI_VIDEO("AI 视频/漫剧", "play_arrow"),
    AI_OFFICE("AI 办公/写作", "edit"),
    VIDEO("影视视频", "photo_library"),
    AUDIO("音乐音频", "graphic_eq"),
    ANIME("二次元/动漫", "star"),
    READING("阅读书库", "list"),
    GAME("游戏资源", "casino"),
    CUSTOM("我添加的", "person")
}

/**
 * 一条精选导航条目。
 *
 * @param host 卡片副标题里展示的站点域名，由 [ExploreCatalog.hostOf] 从 url 推导；
 * @param tag 右上角小徽章（免费 / 开源 / 需登录 等），来自真实站点属性，不写「热门」这类无法验证的营销词。
 * @param custom 用户自己提交的站点，允许删除，其余条目只读。
 */
data class ExploreEntry(
    val id: String,
    val name: String,
    val tagline: String,
    val url: String,
    val category: ExploreCategory,
    val icon: String = "link",
    val tag: String = "",
    val custom: Boolean = false
) {
    val host: String get() = ExploreCatalog.hostOf(url)
    /** 首字母用于 favicon 缺失时的字母头像。 */
    val initial: String
        get() = name.trim().firstOrNull()?.uppercase()
            ?: name.trim().take(1)
}

/** 用户提交的站点，持久化格式与 [ExploreEntry] 解耦，避免 URL 规则变更破坏旧数据。 */
data class CustomSite(
    val id: String,
    val name: String,
    val url: String,
    val category: ExploreCategory
) {
    fun toEntry(): ExploreEntry = ExploreEntry(
        id = id,
        name = name,
        tagline = ExploreCatalog.hostOf(url),
        url = url,
        category = category,
        icon = "link",
        tag = "自定义",
        custom = true
    )
}
