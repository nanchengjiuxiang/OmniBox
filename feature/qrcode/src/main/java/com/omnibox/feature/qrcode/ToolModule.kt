package com.omnibox.feature.qrcode

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
object QrcodeToolModule {
    @Provides
    @IntoSet
    fun provideToolDefinition(): ToolDefinition = ToolDefinition(
        id = "qrcode",
        title = "二维码",
        subtitle = "扫码与本地高清生成",
        badge = "CameraX",
        category = ToolCategory.DAILY,
        group = ToolGroup.DAILY,
        icon = "qr_code_2",
        route = Routes.tool("qrcode"),
        order = 1,
        permissions = listOf("android.permission.CAMERA"),
        isReady = true,
        supportsWidget = true
    )
}
