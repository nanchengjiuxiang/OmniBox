package com.omnibox.feature.countdown

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
object CountdownToolModule {
    @Provides
    @IntoSet
    fun provideToolDefinition(): ToolDefinition = ToolDefinition(
        id = "countdown",
        title = "倒计时/纪念日",
        subtitle = "日程无常驻通知打扰",
        badge = "本地提醒",
        category = ToolCategory.DAILY,
        group = ToolGroup.DAILY,
        icon = "event",
        route = Routes.tool("countdown"),
        order = 4,
        permissions = listOf("android.permission.POST_NOTIFICATIONS"),
        isReady = true,
        supportsWidget = true
    )
}
