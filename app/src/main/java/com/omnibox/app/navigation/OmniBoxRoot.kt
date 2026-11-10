package com.omnibox.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.omnibox.BuildConfig
import com.omnibox.core.designsystem.*
import com.omnibox.core.model.Routes
import com.omnibox.feature.compass.CompassRoute
import com.omnibox.feature.countdown.CountdownRoute
import com.omnibox.feature.decibel.DecibelRoute
import com.omnibox.feature.explore.ExploreRoute
import com.omnibox.feature.exif.ExifRoute
import com.omnibox.feature.formatconverter.FormatConverterRoute
import com.omnibox.feature.home.*
import com.omnibox.feature.imagecompress.ImageCompressRoute
import com.omnibox.feature.password.PasswordRoute
import com.omnibox.feature.qrcode.QrcodeRoute
import com.omnibox.feature.randomwheel.RandomWheelRoute
import com.omnibox.feature.texttools.DevToolsRoute
import com.omnibox.feature.texttools.TextToolsRoute
import com.omnibox.feature.timestamp.TimestampRoute
import com.omnibox.feature.unitconverter.UnitConverterRoute

@Composable
fun OmniBoxRoot(startToolId: String? = null) {
    val navController = rememberNavController()
    val themeVm: ThemeViewModel = hiltViewModel()
    val mode by themeVm.themeMode.collectAsState()
    val dynamic by themeVm.dynamicColor.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    OmniBoxTheme(darkTheme = dark, dynamicColor = dynamic) {
        // 状态栏/导航栏图标明暗跟随 App 主题，避免强制浅色时图标「隐形」
        SystemBarIconAppearance(darkTheme = dark)
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AppNavHost(navController, startToolId)
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, startToolId: String?) {
    val start = if (startToolId.isNullOrBlank()) Routes.HOME else Routes.tool(startToolId)
    NavHost(navController = navController, startDestination = start) {

        composable(Routes.HOME) {
            HomeRoute(
                onOpenTool = { navController.navigate(it.route) },
                onNavigate = { navController.switchTab(it) },
                onSearch = { q -> navController.navigate(Routes.search(q)) }
            )
        }
        composable(Routes.EXPLORE) {
            ExploreRoute(onNavigate = { navController.switchTab(it) })
        }
        composable(Routes.FAVORITES) {
            FavoritesRoute(
                onOpenTool = { navController.navigate(it.route) },
                onNavigate = { navController.switchTab(it) },
                onBack = { navController.switchTab(Routes.HOME) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsRoute(
                versionName = BuildConfig.VERSION_DISPLAY,
                onNavigate = { navController.switchTab(it) },
                onBack = { navController.switchTab(Routes.HOME) }
            )
        }
        composable(Routes.TOOL_SETTINGS) {
            ToolSettingsRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_HISTORY) {
            ToolHistoryRoute(onBack = { navController.popBackStack() })
        }
        // 无参入口（收藏页的搜索按钮）
        composable(Routes.SEARCH_RESULT) {
            SearchResultRoute(
                query = "",
                onOpenTool = { navController.navigate(it.route) },
                onQueryChange = { },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.SEARCH_PATTERN,
            arguments = listOf(navArgument("q") {
                type = NavType.StringType
                defaultValue = ""
            })
        ) { entry ->
            SearchResultRoute(
                query = entry.arguments?.getString("q").orEmpty().trim(),
                onOpenTool = { navController.navigate(it.route) },
                onQueryChange = { /* 输入即时在本地过滤，无需重新导航 */ },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.TOOL_PATTERN,
            arguments = listOf(navArgument("tool_id") {
                type = NavType.StringType
                defaultValue = ""
            })
        ) { entry ->
            val toolId = entry.arguments?.getString("tool_id").orEmpty()
            when (toolId) {
                "password" -> PasswordRoute(
                    onBack = { navController.popBackStack() },
                    onOpenHistory = { navController.navigate(Routes.TOOL_HISTORY) }
                )
                "timestamp" -> TimestampRoute(onBack = { navController.popBackStack() })
                "unit_converter" -> UnitConverterRoute(onBack = { navController.popBackStack() })
                "text_tools" -> TextToolsRoute(
                    onBack = { navController.popBackStack() },
                    onOpenDevTools = { navController.navigate(Routes.tool("dev_tools")) }
                )
                "dev_tools" -> DevToolsRoute(onBack = { navController.popBackStack() })
                "qrcode" -> QrcodeRoute(onBack = { navController.popBackStack() })
                "image_compress" -> ImageCompressRoute(onBack = { navController.popBackStack() })
                "exif" -> ExifRoute(onBack = { navController.popBackStack() })
                "format_converter" -> FormatConverterRoute(onBack = { navController.popBackStack() })
                "compass" -> CompassRoute(onBack = { navController.popBackStack() })
                "decibel" -> DecibelRoute(onBack = { navController.popBackStack() })
                "random_wheel" -> RandomWheelRoute(onBack = { navController.popBackStack() })
                "countdown" -> CountdownRoute(onBack = { navController.popBackStack() })
                else -> PendingToolScreen(toolId) { navController.popBackStack() }
            }
        }
    }
}

/**
 * 排期中的工具占位页：明确告知状态，不伪造控件、不显示假数据。
 * 对应《开发计划》§11 的 P2–P7 里程碑，接入真实实现后逐个移除分支。
 */
/**
 * 底部 Tab 之间的切换：以 HOME 为锚点弹栈，避免「工具 → 探索 → 常用 → 探索」把返回栈越堆越长。
 * launchSingleTop + restoreState 保证重复点同一个 Tab 不会重复入栈，也不会丢页面状态。
 */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { inclusive = false }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun PendingToolScreen(toolId: String, onBack: () -> Unit) {
    val appVm: AppViewModel = hiltViewModel()
    val tool = appVm.catalog.byId(toolId)
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .omniStatusBarInset().omniNavBarInset(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OmniTopBar(
            title = tool?.title ?: toolId,
            subtitle = tool?.subtitle ?: "OmniBox",
            navigationIcon = OmniIcon.of("arrow_back"),
            onNavigationClick = onBack
        )
        Spacer(Modifier.weight(1f))
        Icon(OmniIcon.of(tool?.icon ?: "widgets"), contentDescription = null,
            modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("该模块按排期实现中", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("设计稿已归档，真实功能将在对应里程碑接入；此页不提供模拟交互。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(Modifier.height(20.dp))
        OmniPrimaryButton("返回工具箱", onClick = onBack, icon = "arrow_back")
        Spacer(Modifier.weight(1f))
    }
}
