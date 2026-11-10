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
object DevToolsToolModule {
    @Provides
    @IntoSet
    fun provideToolDefinition(): ToolDefinition = ToolDefinition(
        id = "dev_tools",
        title = "代码与格式化",
        subtitle = "JSON/正则/进制转换",
        badge = "语法格式",
        category = ToolCategory.DEV,
        group = ToolGroup.DEV_TEXT,
        icon = "data_object",
        route = Routes.tool("dev_tools"),
        order = 13,
        permissions = listOf(),
        isReady = true,
        supportsWidget = false
    )
}
