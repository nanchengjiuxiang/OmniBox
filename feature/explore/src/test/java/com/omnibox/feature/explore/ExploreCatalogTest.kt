package com.omnibox.feature.explore

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExploreCatalogTest {

    @Test
    fun `hostOf 去掉协议与 www 前缀`() {
        assertThat(ExploreCatalog.hostOf("https://www.doubao.com/chat")).isEqualTo("doubao.com")
        assertThat(ExploreCatalog.hostOf("http://kimi.moonshot.cn")).isEqualTo("kimi.moonshot.cn")
    }

    @Test
    fun `hostOf 遇到非法输入时原样返回而不是抛异常`() {
        assertThat(ExploreCatalog.hostOf("not a url")).isEqualTo("not a url")
    }

    @Test
    fun `normalizeUrl 省略协议时补 https`() {
        assertThat(ExploreCatalog.normalizeUrl("example.com")).isEqualTo("https://example.com")
        assertThat(ExploreCatalog.normalizeUrl("  example.com/a  ")).isEqualTo("https://example.com/a")
    }

    @Test
    fun `normalizeUrl 保留显式 http`() {
        assertThat(ExploreCatalog.normalizeUrl("http://example.com")).isEqualTo("http://example.com")
    }

    @Test
    fun `normalizeUrl 拒绝非法输入`() {
        val invalid = listOf(
            "",
            "   ",
            "ftp://example.com",
            "javascript:alert(1)",
            "example",
            "https://.com",
            "https://a..b",
            "https://exa mple.com"
        )
        invalid.forEach { input ->
            assertThat(ExploreCatalog.normalizeUrl(input)).isNull()
        }
    }

    @Test
    fun `suggestName 用域名主体做默认名称`() {
        assertThat(ExploreCatalog.suggestName("https://www.my-tools.com")).isEqualTo("My tools")
        assertThat(ExploreCatalog.suggestName("")).isEmpty()
    }

    @Test
    fun `空关键词返回全部分类下的条目`() {
        val all = ExploreCatalog.filter(ExploreCatalog.entries, "", ExploreCategory.ALL)
        assertThat(all).hasSize(ExploreCatalog.entries.size)
    }

    @Test
    fun `关键词匹配名称 简介 域名 分类与标签且忽略大小写`() {
        val byName = ExploreCatalog.filter(ExploreCatalog.entries, "kimi", ExploreCategory.ALL)
        assertThat(byName.map { it.id }).contains("kimi")

        val byTagline = ExploreCatalog.filter(ExploreCatalog.entries, "长文本", ExploreCategory.ALL)
        assertThat(byTagline).isNotEmpty()

        val byHost = ExploreCatalog.filter(ExploreCatalog.entries, "bilibili", ExploreCategory.ALL)
        assertThat(byHost.single().id).isEqualTo("bilibili")

        val byCategoryLabel = ExploreCatalog.filter(ExploreCatalog.entries, "音乐", ExploreCategory.ALL)
        assertThat(byCategoryLabel.map { it.id }).containsAtLeast("netease", "lxmusic")

        val byTag = ExploreCatalog.filter(ExploreCatalog.entries, "开源", ExploreCategory.ALL)
        assertThat(byTag.map { it.id }).containsAtLeast("kazumi", "musicfree")
    }

    @Test
    fun `分类过滤只返回该分类条目`() {
        val audio = ExploreCatalog.filter(ExploreCatalog.entries, "", ExploreCategory.AUDIO)
        assertThat(audio).isNotEmpty()
        assertThat(audio.all { it.category == ExploreCategory.AUDIO }).isTrue()
        assertThat(audio.map { it.id }).containsAtLeast("lxmusic", "musicfree", "netease")
    }

    @Test
    fun `我添加的分类只返回自定义站点`() {
        val custom = ExploreEntry(
            id = "custom-1",
            name = "内部工具",
            tagline = "intranet.local",
            url = "https://intranet.local",
            category = ExploreCategory.AI_OFFICE,
            custom = true
        )
        val pool = ExploreCatalog.entries + custom
        val result = ExploreCatalog.filter(pool, "", ExploreCategory.CUSTOM)
        assertThat(result).containsExactly(custom)
    }

    @Test
    fun `关键词与分类是且的关系`() {
        val hit = ExploreCatalog.filter(ExploreCatalog.entries, "哔哩", ExploreCategory.VIDEO)
        assertThat(hit.map { it.id }).containsExactly("bilibili")

        val miss = ExploreCatalog.filter(ExploreCatalog.entries, "哔哩", ExploreCategory.AUDIO)
        assertThat(miss).isEmpty()
    }

    @Test
    fun `countOf 与 filter 结果一致`() {
        ExploreCatalog.chipOrder.forEach { category ->
            assertThat(ExploreCatalog.countOf(ExploreCatalog.entries, category))
                .isEqualTo(ExploreCatalog.filter(ExploreCatalog.entries, "", category).size)
        }
    }

    @Test
    fun `注册表里的 id 与站点唯一`() {
        val ids = ExploreCatalog.entries.map { it.id }
        val urls = ExploreCatalog.entries.map { CustomSiteCodec.keyOf(it.url) }
        assertThat(ids).containsNoDuplicates()
        assertThat(urls).containsNoDuplicates()
    }

    @Test
    fun `注册表里的每个地址都是规范化的 http(s) 链接`() {
        ExploreCatalog.entries.forEach { entry ->
            assertThat(entry.url).startsWith("https://")
            assertThat(ExploreCatalog.normalizeUrl(entry.url)).isEqualTo(entry.url)
            assertThat(entry.host).contains(".")
        }
    }

    @Test
    fun `chipOrder 覆盖全部枚举且只出现一次`() {
        assertThat(ExploreCatalog.chipOrder).containsNoDuplicates()
        assertThat(ExploreCatalog.chipOrder).hasSize(ExploreCategory.entries.size)
        assertThat(ExploreCatalog.chipOrder.first()).isEqualTo(ExploreCategory.ALL)
        assertThat(ExploreCatalog.chipOrder.last()).isEqualTo(ExploreCategory.CUSTOM)
    }

    @Test
    fun `每个分类在注册表里都至少有一个真实条目`() {
        ExploreCategory.entries
            .filter { it != ExploreCategory.ALL && it != ExploreCategory.CUSTOM }
            .forEach { category ->
                assertThat(
                    ExploreCatalog.entries.count { it.category == category }
                ).isAtLeast(1)
            }
    }
}
