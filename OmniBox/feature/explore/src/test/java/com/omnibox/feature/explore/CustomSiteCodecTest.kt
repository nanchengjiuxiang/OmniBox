package com.omnibox.feature.explore

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CustomSiteCodecTest {

    private val sites = listOf(
        CustomSite("custom-a", "我的博客", "https://blog.example.com", ExploreCategory.READING),
        CustomSite("custom-b", "团队 wiki", "https://wiki.example.com", ExploreCategory.AI_OFFICE)
    )

    @Test
    fun `编解码往返后内容一致`() {
        val restored = CustomSiteCodec.decode(CustomSiteCodec.encode(sites))
        assertThat(restored).isEqualTo(sites)
    }

    @Test
    fun `空字符串解码为空列表`() {
        assertThat(CustomSiteCodec.decode("")).isEmpty()
        assertThat(CustomSiteCodec.decode("   ")).isEmpty()
    }

    @Test
    fun `字段数量不对的行被丢弃`() {
        val raw = "custom-a\u001F只有两个字段\n" + CustomSiteCodec.encode(sites)
        val restored = CustomSiteCodec.decode(raw)
        assertThat(restored).hasSize(2)
        assertThat(restored.map { it.id }).containsExactly("custom-a", "custom-b")
    }

    @Test
    fun `非法 URL 的行被丢弃`() {
        val raw = "custom-x\u001F坏站点\u001Fjavascript:alert(1)\u001FAI_OFFICE"
        assertThat(CustomSiteCodec.decode(raw)).isEmpty()
    }

    @Test
    fun `未知分类与 ALL 分类的行被丢弃`() {
        val unknown = "custom-x\u001F站点\u001Fhttps://a.example.com\u001FNOT_A_CATEGORY"
        val all = "custom-y\u001F站点\u001Fhttps://b.example.com\u001FALL"
        assertThat(CustomSiteCodec.decode(unknown)).isEmpty()
        assertThat(CustomSiteCodec.decode(all)).isEmpty()
    }

    @Test
    fun `名称为空的行被丢弃`() {
        val raw = "custom-x\u001F \u001Fhttps://a.example.com\u001FAI_OFFICE"
        assertThat(CustomSiteCodec.decode(raw)).isEmpty()
    }

    @Test
    fun `缺少协议的 URL 会被规范化后保留`() {
        val raw = "custom-x\u001F站点\u001Fexample.com\u001FAI_OFFICE"
        val restored = CustomSiteCodec.decode(raw)
        assertThat(restored.single().url).isEqualTo("https://example.com")
    }

    @Test
    fun `keyOf 忽略协议 大小写与末尾斜杠`() {
        assertThat(CustomSiteCodec.keyOf("https://Example.com/")).isEqualTo("example.com")
        assertThat(CustomSiteCodec.keyOf("http://example.com")).isEqualTo("example.com")
        assertThat(CustomSiteCodec.keyOf("https://example.com/path/")).isEqualTo("example.com/path")
    }

    @Test
    fun `toEntry 把自定义站点标记为 custom 并展示域名`() {
        val entry = sites.first().toEntry()
        assertThat(entry.custom).isTrue()
        assertThat(entry.tag).isEqualTo("自定义")
        assertThat(entry.host).isEqualTo("blog.example.com")
    }
}
