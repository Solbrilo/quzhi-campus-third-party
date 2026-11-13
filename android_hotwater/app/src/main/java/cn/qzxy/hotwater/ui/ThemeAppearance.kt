package cn.qzxy.hotwater.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.sharp.PowerSettingsNew
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.automirrored.sharp.ReceiptLong
import androidx.compose.material.icons.sharp.Tune
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cn.qzxy.hotwater.designsystem.theme.ThemePalette
import cn.qzxy.hotwater.designsystem.theme.WaterAnimation
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

internal fun themeWaterIcon(palette: ThemePalette): ImageVector = when (palette) {
    ThemePalette.TIDE -> Icons.Outlined.Waves
    ThemePalette.EMBER -> Icons.Outlined.WbSunny
    ThemePalette.MONO -> Icons.Sharp.PowerSettingsNew
    else -> Icons.Outlined.WaterDrop
}

internal fun themeNavigationIcon(palette: ThemePalette, index: Int): ImageVector = when (index) {
    0 -> themeWaterIcon(palette)
    1 -> when (palette) {
        ThemePalette.TIDE, ThemePalette.EMBER -> Icons.AutoMirrored.Rounded.ReceiptLong
        ThemePalette.MONO -> Icons.AutoMirrored.Sharp.ReceiptLong
        else -> Icons.AutoMirrored.Outlined.ReceiptLong
    }
    else -> when (palette) {
        ThemePalette.TIDE, ThemePalette.EMBER -> Icons.Rounded.Tune
        ThemePalette.MONO -> Icons.Sharp.Tune
        else -> Icons.Outlined.Settings
    }
}

@Composable
internal fun ThemedActivityRing(animation: WaterAnimation, color: Color, motionEnabled: Boolean) {
    val phase = if (motionEnabled) {
        val transition = rememberInfiniteTransition(label = "themed_water_activity")
        val progress by transition.animateFloat(0f, 1f,
            infiniteRepeatable(tween(if (animation == WaterAnimation.RIPPLE) 2100 else 1600, easing = LinearEasing)),
            label = "water_phase")
        progress
    } else 0.35f
    Canvas(Modifier.size(288.dp)) {
        val radius = size.minDimension / 2f
        when (animation) {
            WaterAnimation.GEAR -> {
                drawCircle(color.copy(alpha = .2f), radius * .92f, style = Stroke(1.dp.toPx()))
                repeat(32) { index ->
                    val angle = (index / 32f + phase / 16f) * 2f * PI.toFloat()
                    val vector = Offset(cos(angle), sin(angle))
                    drawLine(color.copy(alpha = if (index % 4 == 0) .8f else .35f),
                        center + vector * radius * .91f, center + vector * radius * .97f,
                        strokeWidth = if (index % 4 == 0) 3.dp.toPx() else 1.5.dp.toPx())
                }
                drawArc(color, phase * 360f, 52f, false, topLeft = Offset(radius * .06f, radius * .06f),
                    size = Size(radius * 1.88f, radius * 1.88f), style = Stroke(2.dp.toPx()))
            }
            WaterAnimation.RIPPLE -> repeat(3) { index ->
                val wave = (phase + index / 3f) % 1f
                drawCircle(color.copy(alpha = (1f - wave) * 0.4f),
                    radius = radius * (0.81f + wave * 0.18f), style = Stroke(2.dp.toPx()))
            }
            WaterAnimation.HALO -> repeat(24) { index ->
                val angle = (index / 24f + phase * 0.08f) * 2f * PI.toFloat()
                val ray = 0.5f + 0.5f * sin((index / 24f - phase) * 2f * PI.toFloat())
                val inner = radius * 0.91f
                val outer = radius * (0.94f + ray * 0.06f)
                drawLine(color.copy(alpha = 0.16f + ray * 0.5f),
                    center + Offset(cos(angle), sin(angle)) * inner,
                    center + Offset(cos(angle), sin(angle)) * outer,
                    strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
            WaterAnimation.TICK -> {
                drawRoundRect(color.copy(alpha = 0.13f), topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                    size = Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx()),
                    cornerRadius = CornerRadius(58.dp.toPx()), style = Stroke(1.dp.toPx()))
                repeat(4) { edge ->
                    val strength = (1f - ((phase + edge / 4f) % 1f))
                    val middle = radius
                    val inset = 3.dp.toPx()
                    val half = 16.dp.toPx()
                    val (start, end) = when (edge) {
                        0 -> Offset(middle - half, inset) to Offset(middle + half, inset)
                        1 -> Offset(size.width - inset, middle - half) to Offset(size.width - inset, middle + half)
                        2 -> Offset(middle - half, size.height - inset) to Offset(middle + half, size.height - inset)
                        else -> Offset(inset, middle - half) to Offset(inset, middle + half)
                    }
                    drawLine(color.copy(alpha = 0.15f + strength * 0.7f), start, end, 3.dp.toPx(), StrokeCap.Square)
                }
            }
            else -> Unit
        }
    }
}

/** Crossfade only the glyph, keeping the surrounding layout and input state intact. */
@Composable
internal fun ThemedIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val motion = cn.qzxy.hotwater.designsystem.theme.LocalGlassEnvironment.current.motionEnabled
    androidx.compose.animation.Crossfade(imageVector, modifier = modifier,
        animationSpec = tween(if (motion) 240 else 0), label = "theme_icon") { icon ->
        androidx.compose.material3.Icon(icon, contentDescription, tint = tint,
            modifier = Modifier.fillMaxSize())
    }
}
