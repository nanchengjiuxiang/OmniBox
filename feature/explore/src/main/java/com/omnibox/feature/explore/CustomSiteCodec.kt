package com.omnibox.feature.explore

/**
 * 用户自建站点的持久化编解码。
 *
 * 存储格式：一行一条，字段用单元分隔符（U+001F）拼接。
 * 选它而不是 JSON 的原因：不引入序列化库、单行损坏不会拖垮整份数据、
 * 且名称里出现 `|` `,` 这类字符时不需要转义。
 */
object CustomSiteCodec {

    private const val FIELD = '\u001F'
    private const val LINE = '\n'

    fun encode(sites: List<CustomSite>): String = sites.joinToString(LINE.toString()) { site ->
        listOf(site.id, site.name, site.url, site.category.name).joinToString(FIELD.toString())
    }

    /**
     * 解码时逐行校验：字段数不对、分类名未知、URL 非法的行直接丢弃，
     * 不允许一条坏数据把整页探索导航变成空白。
     */
    fun decode(raw: String): List<CustomSite> {
        if (raw.isBlank()) return emptyList()
        return raw.split(LINE).mapNotNull { line ->
            val parts = line.split(FIELD)
            if (parts.size != 4) return@mapNotNull null
            val id = parts[0].trim()
            val name = parts[1].trim()
            val url = ExploreCatalog.normalizeUrl(parts[2]) ?: return@mapNotNull null
            val category = runCatching { ExploreCategory.valueOf(parts[3].trim()) }.getOrNull()
                ?: return@mapNotNull null
            if (id.isEmpty() || name.isEmpty() || category == ExploreCategory.ALL) return@mapNotNull null
            CustomSite(id, name, url, category)
        }
    }

    /** 同一站点（忽略协议与末尾斜杠）不允许重复收藏。 */
    fun keyOf(url: String): String = url.trim()
        .removePrefix("https://").removePrefix("http://")
        .removeSuffix("/")
        .lowercase()
}
