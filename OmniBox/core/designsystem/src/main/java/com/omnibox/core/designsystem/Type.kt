package com.omnibox.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * DESIGN.md 的 15 级 Roboto Flex 字阶。
 * 基线导出包不含字体二进制文件；待把 Roboto Flex 变量字体放入
 * core/designsystem/src/main/res/font/roboto_flex.ttf 后，
 * 将 OmniFontFamily 替换为 FontFamily(Font(R.font.roboto_flex)) 即可，字阶数值无需改动。
 */
val OmniFontFamily: FontFamily = FontFamily.Default

val OmniTypography = Typography(
    displayLarge = TextStyle(fontFamily = OmniFontFamily, fontSize = 57.sp, fontWeight = FontWeight.W400, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = OmniFontFamily, fontSize = 45.sp, fontWeight = FontWeight.W400, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = OmniFontFamily, fontSize = 36.sp, fontWeight = FontWeight.W400, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = OmniFontFamily, fontSize = 32.sp, fontWeight = FontWeight.W500, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = OmniFontFamily, fontSize = 28.sp, fontWeight = FontWeight.W500, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = OmniFontFamily, fontSize = 24.sp, fontWeight = FontWeight.W600, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = OmniFontFamily, fontSize = 22.sp, fontWeight = FontWeight.W600, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = OmniFontFamily, fontSize = 16.sp, fontWeight = FontWeight.W600, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = OmniFontFamily, fontSize = 14.sp, fontWeight = FontWeight.W600, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = OmniFontFamily, fontSize = 16.sp, fontWeight = FontWeight.W400, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = OmniFontFamily, fontSize = 14.sp, fontWeight = FontWeight.W400, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = OmniFontFamily, fontSize = 12.sp, fontWeight = FontWeight.W400, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = OmniFontFamily, fontSize = 14.sp, fontWeight = FontWeight.W600, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = OmniFontFamily, fontSize = 12.sp, fontWeight = FontWeight.W600, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = OmniFontFamily, fontSize = 11.sp, fontWeight = FontWeight.W500, lineHeight = 16.sp)
)
