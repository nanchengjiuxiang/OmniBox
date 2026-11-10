package com.omnibox.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.omnibox.feature.home.HomeDashboardScreen
import com.omnibox.feature.formatconverter.FormatConverterScreen
import com.omnibox.feature.formatconverter.TranscodingProgressScreen
import com.omnibox.feature.formatconverter.ConversionReportScreen
import com.omnibox.feature.qrcode.QrScanScreen
import com.omnibox.feature.qrcode.QrGenerateScreen
import com.omnibox.feature.imagecompress.ImageCompressScreen
import com.omnibox.feature.compass.CompassScreen
import com.omnibox.feature.soundmeter.SoundMeterScreen
import com.omnibox.feature.history.GlobalToolHistoryScreen
import com.omnibox.feature.settings.GlobalToolSettingsScreen

sealed class ScreenRoute(val route: String) {
    data object Home : ScreenRoute("home")
    data object GlobalHistory : ScreenRoute("global_history")
    data object GlobalSettings : ScreenRoute("global_settings")
    data object ToolQrScan : ScreenRoute("tool/qrcode")
    data object ToolQrGenerate : ScreenRoute("tool/qrcode_generate")
    data object ToolFormatConverter : ScreenRoute("tool/format_converter")
    data object ToolTranscodeProgress : ScreenRoute("tool/format_converter/progress")
    data object ToolTranscodeReport : ScreenRoute("tool/format_converter/report")
    data object ToolImageCompress : ScreenRoute("tool/image_compress")
    data object ToolCompass : ScreenRoute("tool/compass")
    data object ToolSoundMeter : ScreenRoute("tool/sound_meter")
}

@Composable
fun OmniBoxNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = ScreenRoute.Home.route,
        modifier = modifier
    ) {
        // 主基座
        composable(ScreenRoute.Home.route) {
            HomeDashboardScreen(
                onNavigateToTool = { route -> navController.navigate(route) },
                onNavigateToHistory = { navController.navigate(ScreenRoute.GlobalHistory.route) },
                onNavigateToSettings = { navController.navigate(ScreenRoute.GlobalSettings.route) }
            )
        }

        // 全局历史与设置
        composable(ScreenRoute.GlobalHistory.route) {
            GlobalToolHistoryScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(ScreenRoute.GlobalSettings.route) {
            GlobalToolSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }

        // 格式转换系列页面
        composable(ScreenRoute.ToolFormatConverter.route) {
            FormatConverterScreen(
                onNavigateBack = { navController.popBackStack() },
                onStartTranscoding = { navController.navigate(ScreenRoute.ToolTranscodeProgress.route) },
                onNavigateHistory = { navController.navigate(ScreenRoute.GlobalHistory.route) },
                onNavigateSettings = { navController.navigate(ScreenRoute.GlobalSettings.route) }
            )
        }
        composable(ScreenRoute.ToolTranscodeProgress.route) {
            TranscodingProgressScreen(
                onNavigateBack = { navController.popBackStack() },
                onFinishReport = {
                    navController.popBackStack()
                    navController.navigate(ScreenRoute.ToolTranscodeReport.route)
                }
            )
        }
        composable(ScreenRoute.ToolTranscodeReport.route) {
            ConversionReportScreen(
                onNavigateBack = { navController.popBackStack(ScreenRoute.ToolFormatConverter.route, false) },
                onNewTask = { navController.popBackStack(ScreenRoute.ToolFormatConverter.route, false) }
            )
        }

        // 二维码
        composable(ScreenRoute.ToolQrScan.route) {
            QrScanScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToGenerator = { navController.navigate(ScreenRoute.ToolQrGenerate.route) }
            )
        }
        composable(ScreenRoute.ToolQrGenerate.route) {
            QrGenerateScreen(onNavigateBack = { navController.popBackStack() })
        }

        // 其它工具
        composable(ScreenRoute.ToolImageCompress.route) {
            ImageCompressScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(ScreenRoute.ToolCompass.route) {
            CompassScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(ScreenRoute.ToolSoundMeter.route) {
            SoundMeterScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
