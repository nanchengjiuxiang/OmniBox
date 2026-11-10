package com.omnibox.feature.battery

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
 * 工具注册（P10）：电池健康与充电功率。
 * BatteryManager / sysfs 真实读取 + 本地提醒前台服务；无定位、无网络、免 Root。
 */
@Module
@InstallIn(SingletonComponent::class)
object BatteryToolModule {
    @Provides
    @IntoSet
    fun provideToolDefinition(): ToolDefinition = ToolDefinition(
        id = "battery",
        title = "电池健康",
        subtitle = "充电功率与健康度监测",
        badge = "实时监测",
        category = ToolCategory.SENSOR,
        group = ToolGroup.MEDIA_HARDWARE,
        icon = "battery_full",
        route = Routes.tool("battery"),
        order = 15,
        permissions = emptyList(),
        isReady = true,
        supportsWidget = false
    )
}
