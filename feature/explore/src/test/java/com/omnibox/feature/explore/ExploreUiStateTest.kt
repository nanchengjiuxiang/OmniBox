package com.omnibox.feature.explore

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExploreUiStateTest {

    private val customSites = listOf(
        CustomSite("custom-1", "公司内网", "https://intranet.example.com", ExploreCategory.AI_OFFICE)
    )

    @Test
    fun `entries 是内置注册表加自建站点`() {
        val state = ExploreUiState(customSites = customSites)
        assertThat(state.entries).hasSize(ExploreCatalog.entries.size + 1)
        assertThat(state.entries.last().id).isEqualTo("custom-1")
        assertThat(state.totalCount).isEqualTo(state.entries.size)
    }

    @Test
    fun `visibleEntries 受关键词与分类共同约束`() {
        val state = ExploreUiState(
            query = "豆包",
            category = ExploreCategory.AI_ASSISTANT,
            customSites = customSites
        )
        assertThat(state.visibleEntries.map { it.id }).containsExactly("doubao")
    }

    @Test
    fun `favoriteEntries 按注册表顺序而不是收藏顺序`() {
        val state = ExploreUiState(favorites = setOf("steam", "doubao"))
        assertThat(state.favoriteEntries.map { it.id }).containsExactly("doubao", "steam").inOrder()
    }

    @Test
    fun `recentEntries 保留最近打开顺序并忽略失效 id`() {
        val state = ExploreUiState(
            recentIds = listOf("steam", "ghost-id", "doubao"),
            customSites = customSites
        )
        assertThat(state.recentEntries.map { it.id }).containsExactly("steam", "doubao").inOrder()
    }

    @Test
    fun `countOf 在全部分类下等于总条目数`() {
        val state = ExploreUiState(customSites = customSites)
        assertThat(state.countOf(ExploreCategory.ALL)).isEqualTo(state.totalCount)
        assertThat(state.countOf(ExploreCategory.CUSTOM)).isEqualTo(1)
    }

    @Test
    fun `isFavorite 只认已收藏 id`() {
        val state = ExploreUiState(favorites = setOf("qwen"))
        assertThat(state.isFavorite("qwen")).isTrue()
        assertThat(state.isFavorite("kimi")).isFalse()
    }
}
