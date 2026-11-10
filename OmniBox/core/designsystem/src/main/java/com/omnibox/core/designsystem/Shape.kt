package com.omnibox.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** DESIGN.md：Tactile Softness，最大 28px 圆角 + 胶囊状交互锚点。 */
object OmniShape {
    val Pill = RoundedCornerShape(percent = 50)
    val Card = RoundedCornerShape(28.dp)
    val CardSmall = RoundedCornerShape(16.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Input = RoundedCornerShape(12.dp)
    val Chip = RoundedCornerShape(percent = 50)
}

val OmniShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
