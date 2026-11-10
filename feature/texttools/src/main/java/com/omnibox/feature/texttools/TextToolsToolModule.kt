package com.omnibox.feature.texttools

import com.omnibox.core.model.Routes
import com.omnibox.core.model.ToolCategory
import com.omnibox.core.model.ToolDefinition
import com.omnibox.core.model.ToolGroup
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 工具注册（计划 §6.4）：首页/搜索/收藏/设置的计数全部由这里驱动。
 * isReady=true —— P1 已实现真实功能。
 */
@Module
@InstallIn(SingletonComponent::class)
object TextToolsToolModule {
    @Provides
    @IntoSet
    fun provideToolDefinition(): ToolDefinition = ToolDefinition(
        id = "text_tools",
        title = "文本全能箱",
        subtitle = "哈希/Base64/去重",
        badge = "多维处理",
        category = ToolCategory.DEV,
        group = ToolGroup.DEV_TEXT,
        icon = "text_fields",
        route = Routes.tool("text_tools"),
        order = 12,
        permissions = listOf(),
        isReady = true,
        supportsWidget = false
    )
}
