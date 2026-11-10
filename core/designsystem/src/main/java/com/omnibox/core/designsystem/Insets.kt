package com.omnibox.core.designsystem

import android.app.Activity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 系统栏避让。
 *
 * 应用走 edge-to-edge 绘制（`MainActivity` 里 `enableEdgeToEdge()`，Android 15 / targetSdk 35
 * 更是强制如此），所以窗口不再自动给内容留出状态栏与手势条的高度。
 * 这些 inset 必须由组件自己消费——否则顶栏标题会顶到时间、电量底下，
 * 底部内容也会被手势条压住。
 *
 * 约定：**只有一处**负责某一侧的 inset。页面用了 `Scaffold` 就传 [OmniScaffoldInsets]，
 * 让 Scaffold 只处理左右（横屏挖孔/刘海），上下交给 [OmniTopBar] 与 [OmniBottomBar]，
 * 避免系统 inset 与组件 padding 叠加出双倍空白。
 */

/** Scaffold 专用：只保留左右安全区，上下由顶栏/底栏自己避让。 */
val OmniScaffoldInsets: WindowInsets
    // WindowInsets.statusBars / safeDrawing 都是 @Composable 取值，getter 必须同样标注
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)

/** 顶栏避让状态栏；inset 叠加在栏高之外，不压缩栏内元素。 */
@Composable
fun Modifier.omniStatusBarInset(): Modifier =
    windowInsetsPadding(WindowInsets.statusBars)

/** 无底栏的页面（13 款工具页）用它在滚动内容底部避让手势条。 */
@Composable
fun Modifier.omniNavBarInset(): Modifier =
    windowInsetsPadding(WindowInsets.navigationBars)

/**
 * 状态栏 / 导航栏图标明暗跟随**应用**主题，而不是跟随系统深色开关。
 *
 * `enableEdgeToEdge()` 默认按系统深色模式决定图标颜色；但 App 内可以强制浅色/深色
 * （设置页的 themeMode），此时系统判断会反过来——白底配白色图标，等于隐形。
 */
@Composable
fun SystemBarIconAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
}
