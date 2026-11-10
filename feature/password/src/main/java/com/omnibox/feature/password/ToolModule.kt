package com.omnibox.feature.password

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
object PasswordToolModule {
    @Provides
    @IntoSet
    fun provideToolDefinition(): ToolDefinition = ToolDefinition(
        id = "password",
        title = "安全密码生成",
        subtitle = "密码学真随机数引擎",
        badge = "Secure",
        category = ToolCategory.DEV,
        group = ToolGroup.DEV_TEXT,
        icon = "lock",
        route = Routes.tool("password"),
        order = 10,
        permissions = listOf(),
        isReady = true,
        supportsWidget = false
    )
}
