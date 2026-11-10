package com.omnibox.core.designsystem

import androidx.compose.ui.unit.dp

/** 8dp 栅格；DESIGN.md 的三档响应式断点。 */
object OmniDimens {
    val Grid1 = 8.dp
    val Grid2 = 16.dp
    val Grid3 = 24.dp
    val Grid4 = 32.dp
    val ScreenPadding = 16.dp
    val CardPadding = 20.dp
    val IconSize = 24.dp
    val ToolIconBox = 48.dp
    val BottomBarHeight = 80.dp
    val TopBarHeight = 64.dp

    const val BREAKPOINT_COMPACT = 600   // 手机
    const val BREAKPOINT_EXPANDED = 840  // 桌面/平板：切换为左侧导航栏
}
