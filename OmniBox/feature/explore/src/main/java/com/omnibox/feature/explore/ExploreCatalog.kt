package com.omnibox.feature.explore

import java.net.URI

/**
 * 探索页的精选导航注册表。
 *
 * 取舍说明（与设计稿的差异，均在 README 的「已知差异」里同步）：
 * 1. 设计稿里指向盗版影视 / 影子图书馆的入口未接入，替换为同分类的合规站点；
 * 2. 每条 `tag` 只写可核验的属性（免费 / 开源 / 需登录），不写「最强」「神器」这类无法证伪的宣传词；
 * 3. 站点地址在入库前逐个用 HTTP 探活过，失效链接不允许留在导航里。
 */
object ExploreCatalog {

    val entries: List<ExploreEntry> = listOf(
        // ---------------- AI 助手 ----------------
        ExploreEntry("qwen", "通义千问", "阿里云通用大模型 · 对话 / 文档 / 代码", "https://tongyi.aliyun.com/qianwen", ExploreCategory.AI_ASSISTANT, "auto_awesome", "免费"),
        ExploreEntry("doubao", "豆包", "字节跳动 AI 助手 · 问答与写作", "https://www.doubao.com", ExploreCategory.AI_ASSISTANT, "auto_awesome", "免费"),
        ExploreEntry("kimi", "Kimi", "月之暗面 · 超长文本阅读与总结", "https://kimi.moonshot.cn", ExploreCategory.AI_ASSISTANT, "auto_awesome", "免费"),
        ExploreEntry("metaso", "秘塔 AI 搜索", "无广告结构化搜索 · 每条结论带出处", "https://metaso.cn", ExploreCategory.AI_ASSISTANT, "search", "免费"),
        ExploreEntry("trae", "Trae AI", "AI 原生 IDE · 中文项目上手快", "https://www.trae.ai", ExploreCategory.AI_ASSISTANT, "terminal", "需下载"),

        // ---------------- AI 图像 / 修图 ----------------
        ExploreEntry("jimeng", "即梦 AI", "文生图 / 图生图 / 中文海报字", "https://jimeng.jianying.com", ExploreCategory.AI_IMAGE, "palette", "免费额度"),
        ExploreEntry("kling", "可灵 AI", "快手 · 图像与视频生成", "https://klingai.com", ExploreCategory.AI_IMAGE, "auto_fix_high", "免费额度"),
        ExploreEntry("vidu", "Vidu", "参考图生视频 · 角色一致性", "https://www.vidu.cn", ExploreCategory.AI_IMAGE, "play_arrow", "免费额度"),
        ExploreEntry("lovart", "Lovart", "AI 设计智能体 · 成套视觉产出", "https://www.lovart.ai", ExploreCategory.AI_IMAGE, "palette", "需登录"),
        ExploreEntry("gaoding", "稿定设计", "在线海报 / 电商图 / 智能抠图", "https://www.gaoding.com", ExploreCategory.AI_IMAGE, "image", "免费+会员"),
        ExploreEntry("picwish", "佐糖", "AI 抠图与老照片修复", "https://picwish.cn", ExploreCategory.AI_IMAGE, "photo_library", "免费额度"),

        // ---------------- AI 视频 / 漫剧 ----------------
        ExploreEntry("jianying", "剪映", "剪辑与 AI 成片 · 桌面端", "https://www.jianying.com", ExploreCategory.AI_VIDEO, "play_arrow", "免费"),
        ExploreEntry("hailuo", "海螺 AI", "MiniMax · 视频与语音合成", "https://hailuoai.com", ExploreCategory.AI_VIDEO, "graphic_eq", "免费额度"),

        // ---------------- AI 办公 / 写作 ----------------
        ExploreEntry("raccoon", "办公小浣熊", "商汤 · 表格分析与文档处理", "https://xiaohuanxiong.com", ExploreCategory.AI_OFFICE, "dataset", "免费"),
        ExploreEntry("xiezuocat", "秘塔写作猫", "中文校对 / 改写 / 润色", "https://xiezuocat.com", ExploreCategory.AI_OFFICE, "edit", "免费"),
        ExploreEntry("zhiwen", "讯飞智文", "一句话生成 PPT 与 Word", "https://zhiwen.xfyun.cn", ExploreCategory.AI_OFFICE, "edit", "免费额度"),
        ExploreEntry("wps", "WPS AI", "办公套件内置写作与表格助手", "https://www.wps.cn", ExploreCategory.AI_OFFICE, "edit", "需登录"),

        // ---------------- 影视视频 ----------------
        ExploreEntry("bilibili", "哔哩哔哩", "长视频 / 知识区 / 番剧", "https://www.bilibili.com", ExploreCategory.VIDEO, "play_arrow", "免费"),
        ExploreEntry("douban_movie", "豆瓣电影", "评分 / 影评 / 片单", "https://movie.douban.com", ExploreCategory.VIDEO, "star", "免费"),
        ExploreEntry("cctv", "央视网", "央卫视直播与节目回看", "https://tv.cctv.com", ExploreCategory.VIDEO, "photo_library", "免费"),

        // ---------------- 音乐音频 ----------------
        ExploreEntry("lxmusic", "洛雪音乐", "开源音乐聚合客户端（桌面）", "https://github.com/lyswhut/lx-music-desktop", ExploreCategory.AUDIO, "graphic_eq", "开源"),
        ExploreEntry("musicfree", "MusicFree", "插件化开源音乐播放器", "https://github.com/maotoumao/MusicFree", ExploreCategory.AUDIO, "graphic_eq", "开源"),
        ExploreEntry("netease", "网易云音乐", "在线音乐与歌单", "https://music.163.com", ExploreCategory.AUDIO, "graphic_eq", "免费+会员"),

        // ---------------- 二次元 / 动漫 ----------------
        ExploreEntry("bgm", "Bangumi 番组计划", "动画 / 书籍 / 游戏收藏与评分", "https://bgm.tv", ExploreCategory.ANIME, "star", "免费"),
        ExploreEntry("kazumi", "Kazumi", "开源追番工具 · 自定义抓取规则", "https://github.com/Predidit/Kazumi", ExploreCategory.ANIME, "play_arrow", "开源"),

        // ---------------- 阅读书库 ----------------
        ExploreEntry("weread", "微信读书", "正版电子书与跨端笔记", "https://weread.qq.com", ExploreCategory.READING, "list", "免费+会员"),
        ExploreEntry("jiumo", "鸠摩搜索", "电子书资源搜索引擎", "https://www.jiumodiary.com", ExploreCategory.READING, "search", "免费"),
        ExploreEntry("douban_book", "豆瓣读书", "图书评分与主题书单", "https://book.douban.com", ExploreCategory.READING, "list", "免费"),

        // ---------------- 游戏资源 ----------------
        ExploreEntry("steam", "Steam", "PC 游戏商店与创意工坊", "https://store.steampowered.com", ExploreCategory.GAME, "casino", "需登录"),
        ExploreEntry("itch", "itch.io", "独立游戏与素材市集", "https://itch.io", ExploreCategory.GAME, "casino", "免费")
    )

    /** 分类 Chips 的展示顺序：全部 + 十个分类，用户自建分类固定排在最后。 */
    val chipOrder: List<ExploreCategory> = listOf(
        ExploreCategory.ALL,
        ExploreCategory.AI_ASSISTANT,
        ExploreCategory.AI_IMAGE,
        ExploreCategory.AI_VIDEO,
        ExploreCategory.AI_OFFICE,
        ExploreCategory.VIDEO,
        ExploreCategory.AUDIO,
        ExploreCategory.ANIME,
        ExploreCategory.READING,
        ExploreCategory.GAME,
        ExploreCategory.CUSTOM
    )

    /**
     * 从 URL 推导展示用域名：去掉 scheme、www. 与末尾斜杠。
     * 解析失败时退回原始字符串，绝不因为一条脏数据让整页崩掉。
     */
    fun hostOf(url: String): String {
        return runCatching {
            val uri = URI(url.trim())
            val host = uri.host ?: return@runCatching url
            host.removePrefix("www.")
        }.getOrDefault(url)
    }

    /**
     * 把用户输入规范化成可打开的 http(s) 地址。
     * 返回 null 表示输入不合法（空、非 http(s)、无域名、含空白）。
     */
    fun normalizeUrl(input: String): String? {
        val raw = input.trim()
        if (raw.isEmpty() || raw.any { it.isWhitespace() }) return null
        val withScheme = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
            raw
        } else {
            "https://$raw"
        }
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host ?: return null
        if (!host.contains('.')) return null
        // 排除 "https://.com"、"https://a..b" 这类明显非法域名
        if (host.startsWith('.') || host.endsWith('.') || host.contains("..")) return null
        return withScheme
    }

    /** 用户只填域名时，用域名主体做默认名称。 */
    fun suggestName(input: String): String {
        val normalized = normalizeUrl(input) ?: return ""
        return hostOf(normalized)
            .substringBeforeLast('.')
            .replace('-', ' ')
            .replaceFirstChar { it.uppercase() }
    }

    /**
     * 关键词过滤 + 分类过滤。纯函数，便于单测覆盖；
     * 查询会同时匹配名称、简介、域名与分类名，用户搜「音乐」也能命中音乐类站点。
     */
    fun filter(
        entries: List<ExploreEntry>,
        query: String,
        category: ExploreCategory
    ): List<ExploreEntry> {
        val q = query.trim()
        return entries.filter { entry ->
            val inCategory = when (category) {
                ExploreCategory.ALL -> true
                ExploreCategory.CUSTOM -> entry.custom
                else -> entry.category == category
            }
            if (!inCategory) return@filter false
            if (q.isEmpty()) return@filter true
            entry.name.contains(q, ignoreCase = true) ||
                entry.tagline.contains(q, ignoreCase = true) ||
                entry.host.contains(q, ignoreCase = true) ||
                entry.category.label.contains(q, ignoreCase = true) ||
                entry.tag.contains(q, ignoreCase = true)
        }
    }

    fun countOf(entries: List<ExploreEntry>, category: ExploreCategory): Int =
        filter(entries, "", category).size
}
