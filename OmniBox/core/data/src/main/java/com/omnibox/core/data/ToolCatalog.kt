package com.omnibox.core.data

import com.omnibox.core.model.ToolCategory
import com.omnibox.core.model.ToolDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工具注册表（计划 §6.4）。
 * 13 个 feature 模块通过 Hilt @IntoSet 贡献 ToolDefinition，
 * 首页 / 收藏 / 搜索 / 设置 / 小组件全部从这里取数。
 */
@Singleton
class ToolCatalog @Inject constructor(
    registered: Set<ToolDefinition>
) {
    private val _tools = MutableStateFlow(registered.sortedBy { it.order })
    val tools: Flow<List<ToolDefinition>> = _tools.asStateFlow()

    val snapshot: List<ToolDefinition> get() = _tools.value

    val totalCount: Int get() = _tools.value.size

    fun countOf(category: ToolCategory): Int = when (category) {
        ToolCategory.ALL -> totalCount
        else -> _tools.value.count { it.category == category }
    }

    fun byId(id: String): ToolDefinition? = _tools.value.find { it.id == id }

    fun search(query: String): List<ToolDefinition> {
        val q = query.trim()
        if (q.isEmpty()) return _tools.value
        return _tools.value.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.subtitle.contains(q, ignoreCase = true) ||
                it.badge.contains(q, ignoreCase = true) ||
                it.id.contains(q, ignoreCase = true)
        }
    }
}
