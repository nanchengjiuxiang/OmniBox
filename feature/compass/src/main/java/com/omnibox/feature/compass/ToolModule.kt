package com.omnibox.feature.compass

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
 * isReady=false —— 按排期在后续里程碑接入真实实现；当前点击给出明确状态提示，不做假交互。
 */
@Module
@InstallIn(SingletonComponent::class)
object CompassToolModule {
    @Provides
    @IntoSet
    fun provideToolDefinition(): ToolDefinition = ToolDefinition(
        id = "compass",
        title = "指南针/水平仪",
        subtitle = "磁阻与加速度解算",
        badge = "自适应",
        category = ToolCategory.SENSOR,
        group = ToolGroup.MEDIA_HARDWARE,
        icon = "explore",
        route = Routes.tool("compass"),
        order = 7,
        permissions = listOf(),
        isReady = false,
        supportsWidget = false
    )
}
