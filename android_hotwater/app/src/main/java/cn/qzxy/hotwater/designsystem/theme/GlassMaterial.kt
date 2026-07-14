package cn.qzxy.hotwater.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

enum class GlassMaterial {
    Panel,
    Floating,
    Control,
    EmbeddedControl,
    Input,
    Overlay,
}

private data class ResolvedGlassMaterial(
    val surface: Color,
    val fallback: Color,
    val elevation: Dp,
    val blurRadius: Dp,
    val noiseFactor: Float,
    val canBlur: Boolean,
)

@Composable
fun Modifier.glassMaterial(
    material: GlassMaterial,
    cornerRadius: Dp,
    pressed: Boolean = false,
    enabled: Boolean = true,
    tokens: GlassTokens = LocalGlassTokens.current,
): Modifier {
    val environment = LocalGlassEnvironment.current
    val resolved = resolveGlassMaterial(material, tokens, pressed, enabled)
    val shape = RoundedCornerShape(cornerRadius)
    val opaqueSurface = if (resolved.canBlur) {
        resolved.fallback
    } else {
        lerp(
            tokens.fallbackSurface,
            resolved.surface.copy(alpha = 1f),
            0.35f,
        )
    }
    val surfaceModifier = if (resolved.canBlur && environment.blurAvailable) {
        Modifier.hazeEffect(state = environment.hazeState) {
            blurEnabled = true
            blurRadius = resolved.blurRadius
            noiseFactor = resolved.noiseFactor
            backgroundColor = tokens.meshBase
            tints = listOf(HazeTint(resolved.surface))
            fallbackTint = HazeTint(resolved.fallback)
        }
    } else {
        Modifier.background(
            color = if (environment.preferOpaqueMaterials) {
                opaqueSurface
            } else if (resolved.canBlur) {
                resolved.fallback
            } else {
                resolved.surface
            },
        )
    }

    return this
        .then(
            if (resolved.elevation > 0.dp) {
                Modifier.shadow(
                    elevation = resolved.elevation,
                    shape = shape,
                    clip = false,
                    ambientColor = tokens.outerShadow,
                    spotColor = tokens.outerShadow,
                )
            } else {
                Modifier
            }
        )
        .clip(shape)
        .then(surfaceModifier)
        .drawWithCache {
            val radius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
            val strokeWidth = if (environment.preferOpaqueMaterials) {
                2.dp.toPx()
            } else {
                1.dp.toPx()
            }
            val borderColors = if (environment.preferOpaqueMaterials) {
                listOf(
                    tokens.textPrimary.copy(alpha = 0.72f),
                    tokens.textPrimary.copy(alpha = 0.34f),
                )
            } else {
                listOf(tokens.borderHi, tokens.borderLo)
            }
            val border = Brush.linearGradient(
                colors = if (pressed) {
                    borderColors.reversed()
                } else {
                    borderColors
                },
            )
            val highlight = if (pressed) {
                tokens.borderLo.copy(alpha = tokens.borderLo.alpha * 0.72f)
            } else {
                tokens.innerHighlight
            }

            onDrawWithContent {
                drawContent()
                drawRoundRect(
                    brush = border,
                    cornerRadius = radius,
                    style = Stroke(width = strokeWidth),
                )
                drawLine(
                    color = highlight,
                    start = Offset(cornerRadius.toPx() * 0.55f, if (pressed) size.height - strokeWidth else strokeWidth),
                    end = Offset(size.width - cornerRadius.toPx() * 0.55f, if (pressed) size.height - strokeWidth else strokeWidth),
                    strokeWidth = strokeWidth,
                )
            }
        }
}

private fun resolveGlassMaterial(
    material: GlassMaterial,
    tokens: GlassTokens,
    pressed: Boolean,
    enabled: Boolean,
): ResolvedGlassMaterial {
    val baseSurface = when (material) {
        GlassMaterial.Panel -> tokens.panelSurface
        GlassMaterial.Floating -> tokens.floatingSurface
        GlassMaterial.Control -> tokens.controlSurface
        GlassMaterial.EmbeddedControl -> tokens.embeddedControlSurface
        GlassMaterial.Input -> tokens.inputSurface
        GlassMaterial.Overlay -> tokens.overlaySurface
    }
    val pressedAmount = when (material) {
        GlassMaterial.Control, GlassMaterial.EmbeddedControl -> 0.46f
        GlassMaterial.Floating, GlassMaterial.Overlay -> 0.28f
        GlassMaterial.Panel, GlassMaterial.Input -> 0.18f
    }
    val pressedSurface = if (pressed) {
        lerp(baseSurface, tokens.pressedSurface, pressedAmount)
    } else {
        baseSurface
    }
    val surface = if (enabled) {
        pressedSurface
    } else {
        pressedSurface.copy(alpha = pressedSurface.alpha * 0.72f)
    }

    return when (material) {
        GlassMaterial.Panel -> ResolvedGlassMaterial(surface, tokens.fallbackSurface, 5.dp, 0.dp, 0f, false)
        GlassMaterial.Floating -> ResolvedGlassMaterial(surface, tokens.fallbackSurface, 12.dp, 22.dp, 0.025f, true)
        GlassMaterial.Control -> ResolvedGlassMaterial(surface, tokens.fallbackSurface, 3.dp, 0.dp, 0f, false)
        GlassMaterial.EmbeddedControl -> ResolvedGlassMaterial(surface, tokens.fallbackSurface, 0.dp, 0.dp, 0f, false)
        GlassMaterial.Input -> ResolvedGlassMaterial(surface, tokens.fallbackSurface, 0.dp, 0.dp, 0f, false)
        GlassMaterial.Overlay -> ResolvedGlassMaterial(surface, tokens.fallbackSurface, 18.dp, 28.dp, 0.03f, true)
    }
}
