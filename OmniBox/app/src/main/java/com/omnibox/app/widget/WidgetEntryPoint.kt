package com.omnibox.app.widget

import androidx.compose.ui.graphics.Color
import com.omnibox.core.data.local.CountdownDao
import com.omnibox.core.data.local.QrHistoryDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 小组件渲染在 AppWidgetService 里，拿不到 Activity 的 ViewModel 作用域，
 * 因此用 Hilt EntryPoint 取 DAO。这里是小组件读取真实数据的唯一入口。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun countdownDao(): CountdownDao
    fun qrHistoryDao(): QrHistoryDao
}

/** 小组件通用的品牌配色，与 core:designsystem 的 light scheme 对齐。 */
internal object WidgetColors {
    val SURFACE = Color(0xFFFDF8FF)
    val SURFACE_CONTAINER = Color(0xFFF1ECF7)
    val PRIMARY = Color(0xFF4F378A)
    val ON_SURFACE = Color(0xFF1C1B22)
    val ON_SURFACE_VARIANT = Color(0xFF494551)
    val OUTLINE_VARIANT = Color(0xFFCAC4D0)
}
