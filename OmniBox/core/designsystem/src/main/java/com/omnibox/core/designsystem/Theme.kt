package com.omnibox.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 Tonal Utility 主题。
 * Android 12+ 支持动态取色；12 以下回落到 DESIGN.md 的紫色静态色板（计划 §1 PRD）。
 */
@Composable
fun OmniBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> OmniDarkScheme
        else -> OmniLightScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = OmniTypography,
        shapes = OmniShapes,
        content = content
    )
}
