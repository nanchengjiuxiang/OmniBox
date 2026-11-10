package com.omnibox.feature.imagecompress

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
object ImagecompressToolModule {
    @Provides
    @IntoSet
    fun provideToolDefinition(): ToolDefinition = ToolDefinition(
        id = "image_compress",
        title = "图片无损压缩",
        subtitle = "双重采样保持画质",
        badge = "零 OOM",
        category = ToolCategory.MEDIA,
        group = ToolGroup.MEDIA_HARDWARE,
        icon = "image",
        route = Routes.tool("image_compress"),
        order = 5,
        permissions = listOf(),
        isReady = false,
        supportsWidget = false
    )
}
