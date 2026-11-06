package cn.qzxy.hotwater.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A single animated geometry frame shared by surfaces, clips, borders and controls. */
@Immutable
data class ThemeGeometry(val cornerScale: Float = 1f, val powerCorner: Dp = 114.dp) {
    fun corner(radius: Dp): Dp = if (radius >= 48.dp) radius else radius * cornerScale

    fun insetCorner(outerRadius: Dp, inset: Dp): Dp = (corner(outerRadius) - inset).coerceAtLeast(0.dp)
}

val LocalThemeGeometry = staticCompositionLocalOf { ThemeGeometry() }

internal fun lerpThemeGeometry(start: ThemeGeometry, end: ThemeGeometry, fraction: Float): ThemeGeometry =
    ThemeGeometry(
        cornerScale = start.cornerScale + (end.cornerScale - start.cornerScale) * fraction,
        powerCorner = start.powerCorner + (end.powerCorner - start.powerCorner) * fraction,
    )
