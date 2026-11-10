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
 * isReady=true —— 真实功能已接入（P2 完成）；权限被拒时走设计稿的降级页，不伪造结果。
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
        permissions = listOf("android.permission.ACCESS_FINE_LOCATION"),
        isReady = true,
        supportsWidget = false
    )
}
