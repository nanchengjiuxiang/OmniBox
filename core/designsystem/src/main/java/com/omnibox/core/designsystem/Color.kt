package com.omnibox.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 取自设计基线 tonal_utility/DESIGN.md 的 front matter token。
 * 计划 §3 裁定：主色以 HTML / front matter 的 #4F378A 为准，
 * 正文里出现的 #6750A4 视为笔误（它实际是 primary-container 的角色值）。
 */
object OmniColor {
    val Primary = Color(0xFF4F378A)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFF6750A4)
    val OnPrimaryContainer = Color(0xFFE0D2FF)
    val InversePrimary = Color(0xFFCFBCFF)

    val Secondary = Color(0xFF625B71)
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFE8DEF9)
    val OnSecondaryContainer = Color(0xFF686177)

    val Tertiary = Color(0xFF633B48)
    val OnTertiary = Color(0xFFFFFFFF)
    val TertiaryContainer = Color(0xFF7D5260)
    val OnTertiaryContainer = Color(0xFFFFCBDA)

    val Surface = Color(0xFFFDF8FF)
    val SurfaceDim = Color(0xFFDDD8E3)
    val SurfaceBright = Color(0xFFFDF8FF)
    val SurfaceContainerLowest = Color(0xFFFFFFFF)
    val SurfaceContainerLow = Color(0xFFF7F1FC)
    val SurfaceContainer = Color(0xFFF1ECF7)
    val SurfaceContainerHigh = Color(0xFFECE6F1)
    val SurfaceContainerHighest = Color(0xFFE6E0EB)
    val OnSurface = Color(0xFF1C1B22)
    val OnSurfaceVariant = Color(0xFF494551)
    val Outline = Color(0xFF7A7484)
    val OutlineVariant = Color(0xFFCAC4D0)
    val InverseSurface = Color(0xFF312F37)
    val InverseOnSurface = Color(0xFFF4EFF9)

    val Error = Color(0xFFB3261E)
    val OnError = Color(0xFFFFFFFF)
    val Success = Color(0xFF2E7D5B)
    val Warning = Color(0xFFB26A00)
}

val OmniLightScheme = lightColorScheme(
    primary = OmniColor.Primary,
    onPrimary = OmniColor.OnPrimary,
    primaryContainer = OmniColor.PrimaryContainer,
    onPrimaryContainer = OmniColor.OnPrimaryContainer,
    inversePrimary = OmniColor.InversePrimary,
    secondary = OmniColor.Secondary,
    onSecondary = OmniColor.OnSecondary,
    secondaryContainer = OmniColor.SecondaryContainer,
    onSecondaryContainer = OmniColor.OnSecondaryContainer,
    tertiary = OmniColor.Tertiary,
    onTertiary = OmniColor.OnTertiary,
    tertiaryContainer = OmniColor.TertiaryContainer,
    onTertiaryContainer = OmniColor.OnTertiaryContainer,
    background = OmniColor.Surface,
    onBackground = OmniColor.OnSurface,
    surface = OmniColor.Surface,
    onSurface = OmniColor.OnSurface,
    surfaceVariant = OmniColor.SurfaceContainerHighest,
    onSurfaceVariant = OmniColor.OnSurfaceVariant,
    surfaceContainer = OmniColor.SurfaceContainer,
    surfaceContainerLow = OmniColor.SurfaceContainerLow,
    surfaceContainerHigh = OmniColor.SurfaceContainerHigh,
    surfaceContainerHighest = OmniColor.SurfaceContainerHighest,
    outline = OmniColor.Outline,
    outlineVariant = OmniColor.OutlineVariant,
    inverseSurface = OmniColor.InverseSurface,
    inverseOnSurface = OmniColor.InverseOnSurface,
    error = OmniColor.Error,
    onError = OmniColor.OnError
)

val OmniDarkScheme = darkColorScheme(
    primary = OmniColor.InversePrimary,
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378A),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4358),
    onSecondaryContainer = Color(0xFFE8DEF9),
    tertiary = Color(0xFFEEB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD9E3),
    background = Color(0xFF14131A),
    onBackground = Color(0xFFE6E0EB),
    surface = Color(0xFF14131A),
    onSurface = Color(0xFFE6E0EB),
    surfaceVariant = Color(0xFF494551),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF494551),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)
