package com.omnibox.feature.randomwheel

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
object RandomwheelToolModule {
    @Provides
    @IntoSet
    fun provideToolDefinition(): ToolDefinition = ToolDefinition(
        id = "random_wheel",
        title = "随机转盘抽签",
        subtitle = "选择困难决策神器",
        badge = "触感振动",
        category = ToolCategory.DAILY,
        group = ToolGroup.DAILY,
        icon = "casino",
        route = Routes.tool("random_wheel"),
        order = 3,
        permissions = listOf(),
        isReady = false,
        supportsWidget = false
    )
}
