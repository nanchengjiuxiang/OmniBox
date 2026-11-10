package com.omnibox.feature.displaylab

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
 * 工具注册（P10）：屏幕检测与触控采样。
 * 显示参数 / 坏点 / 灰阶 / 棋盘 / 多指触控全部真实实现，无权限依赖、纯本地。
 */
@Module
@InstallIn(SingletonComponent::class)
object DisplayLabToolModule {
    @Provides
    @IntoSet
    fun provideToolDefinition(): ToolDefinition = ToolDefinition(
        id = "display_lab",
        title = "屏幕检测",
        subtitle = "坏点 / 灰阶 / 触控采样诊断",
        badge = "全屏测试",
        category = ToolCategory.SENSOR,
        group = ToolGroup.MEDIA_HARDWARE,
        icon = "smartphone",
        route = Routes.tool("display_lab"),
        order = 14,
        permissions = emptyList(),
        isReady = true,
        supportsWidget = false
    )
}
