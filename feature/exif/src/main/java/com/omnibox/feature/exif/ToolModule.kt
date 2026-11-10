package com.omnibox.feature.exif

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
object ExifToolModule {
    @Provides
    @IntoSet
    fun provideToolDefinition(): ToolDefinition = ToolDefinition(
        id = "exif",
        title = "EXIF 隐私擦除",
        subtitle = "清除相机参数与GPS",
        badge = "隐私保护",
        category = ToolCategory.MEDIA,
        group = ToolGroup.MEDIA_HARDWARE,
        icon = "location_off",
        route = Routes.tool("exif"),
        order = 6,
        permissions = listOf(),
        isReady = true,
        supportsWidget = false
    )
}
