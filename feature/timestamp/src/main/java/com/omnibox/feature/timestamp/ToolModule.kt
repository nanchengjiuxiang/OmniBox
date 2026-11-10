package com.omnibox.feature.timestamp

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
object TimestampToolModule {
    @Provides
    @IntoSet
    fun provideToolDefinition(): ToolDefinition = ToolDefinition(
        id = "timestamp",
        title = "时间戳转换",
        subtitle = "毫秒转化与跨时区",
        badge = "双向计算",
        category = ToolCategory.DEV,
        group = ToolGroup.DEV_TEXT,
        icon = "schedule",
        route = Routes.tool("timestamp"),
        order = 11,
        permissions = listOf(),
        isReady = true,
        supportsWidget = false
    )
}
