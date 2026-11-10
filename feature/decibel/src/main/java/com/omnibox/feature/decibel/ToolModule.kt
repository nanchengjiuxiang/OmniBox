package com.omnibox.feature.decibel

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
object DecibelToolModule {
    @Provides
    @IntoSet
    fun provideToolDefinition(): ToolDefinition = ToolDefinition(
        id = "decibel",
        title = "环境分贝计",
        subtitle = "高敏麦克风声压波形",
        badge = "瞬时采样",
        category = ToolCategory.SENSOR,
        group = ToolGroup.MEDIA_HARDWARE,
        icon = "graphic_eq",
        route = Routes.tool("decibel"),
        order = 8,
        permissions = listOf("android.permission.RECORD_AUDIO"),
        isReady = false,
        supportsWidget = false
    )
}
