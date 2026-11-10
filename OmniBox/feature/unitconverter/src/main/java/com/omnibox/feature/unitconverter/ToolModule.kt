package com.omnibox.feature.unitconverter

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
object UnitconverterToolModule {
    @Provides
    @IntoSet
    fun provideToolDefinition(): ToolDefinition = ToolDefinition(
        id = "unit_converter",
        title = "单位换算",
        subtitle = "物理/汇率/计算换算",
        badge = "离线引擎",
        category = ToolCategory.DAILY,
        group = ToolGroup.DAILY,
        icon = "swap_horiz",
        route = Routes.tool("unit_converter"),
        order = 2,
        permissions = listOf(),
        isReady = true,
        supportsWidget = false
    )
}
