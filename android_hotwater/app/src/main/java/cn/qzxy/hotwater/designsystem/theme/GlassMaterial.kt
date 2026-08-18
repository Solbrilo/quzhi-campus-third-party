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

internal data class ResolvedGlassMaterial(
    val surface: Color,
    val fallback: Color,
    val elevation: Dp,
    val blurRadius: Dp,
    val noiseFactor: Float,
    val canBlur: Boolean,
    val highlight: Color,
    val shadow: Color,
)

private data class GlassMaterialMetrics(
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
    val resolved = resolveGlassMaterial(
        material = material,
        tokens = tokens,
        pressed = pressed,
        enabled = enabled,
        glassIntensity = environment.glassIntensity,
    )
    val shape = RoundedCornerShape(cornerRadius)
    val opaqueSurface = if (resolved.canBlur) {
        tokens.fallbackSurface.copy(alpha = 1f)
    } else {
        lerp(
            tokens.fallbackSurface.copy(alpha = 1f),
            resolved.surface.copy(alpha = 1f),
            0.35f,
        )
    }
    val surfaceModifier = if (
        resolved.canBlur &&
        environment.blurAvailable &&
        environment.glassIntensity > 0f
    ) {
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
                    ambientColor = if (environment.preferOpaqueMaterials) {
                        tokens.outerShadow
                    } else {
                        resolved.shadow
                    },
                    spotColor = if (environment.preferOpaqueMaterials) {
                        tokens.outerShadow
                    } else {
                        resolved.shadow
                    },
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
            val highlight = if (environment.preferOpaqueMaterials) {
                if (pressed) {
                    tokens.borderLo.copy(alpha = tokens.borderLo.alpha * 0.72f)
                } else {
                    tokens.innerHighlight
                }
            } else {
                resolved.highlight
            }
            // Panels keep a finer light catch than controls without weakening high-contrast mode.
            val highlightStrokeWidth = if (
                material == GlassMaterial.Panel && !environment.preferOpaqueMaterials
            ) {
                0.65.dp.toPx()
            } else {
                strokeWidth
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
                    start = Offset(
                        cornerRadius.toPx() * 0.55f,
                        if (pressed) size.height - highlightStrokeWidth else highlightStrokeWidth,
                    ),
                    end = Offset(
                        size.width - cornerRadius.toPx() * 0.55f,
                        if (pressed) size.height - highlightStrokeWidth else highlightStrokeWidth,
                    ),
                    strokeWidth = highlightStrokeWidth,
                )
            }
        }
}

internal fun resolveGlassMaterial(
    material: GlassMaterial,
    tokens: GlassTokens,
    pressed: Boolean,
    enabled: Boolean,
    glassIntensity: Float = DEFAULT_GLASS_INTENSITY,
): ResolvedGlassMaterial {
    val normalizedIntensity = sanitizeGlassIntensity(glassIntensity)
    val intensityProfile = resolveGlassIntensityProfile(normalizedIntensity)
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
    val unscaledSurface = if (enabled) {
        pressedSurface
    } else {
        pressedSurface.copy(alpha = pressedSurface.alpha * 0.72f)
    }
    val surface = unscaledSurface.withAlphaScale(intensityProfile.tintAlphaScale)
    val fallback = tokens.fallbackSurface.withAlphaScale(intensityProfile.fallbackAlphaScale)
    val highlightBase = if (pressed) {
        tokens.borderLo.copy(alpha = tokens.borderLo.alpha * 0.72f)
    } else {
        tokens.innerHighlight
    }
    val highlight = highlightBase.withAlphaScale(intensityProfile.highlightAlphaScale)
    val shadow = tokens.outerShadow.withAlphaScale(intensityProfile.shadowAlphaScale)

    val metrics = when (material) {
        GlassMaterial.Panel -> GlassMaterialMetrics(5.dp, 0.dp, 0f, false)
        GlassMaterial.Floating -> GlassMaterialMetrics(
            elevation = 12.dp,
            blurRadius = intensityProfile.floatingBlurRadius,
            noiseFactor = intensityProfile.floatingNoiseFactor,
            canBlur = true,
        )
        GlassMaterial.Control -> GlassMaterialMetrics(3.dp, 0.dp, 0f, false)
        GlassMaterial.EmbeddedControl -> GlassMaterialMetrics(0.dp, 0.dp, 0f, false)
        GlassMaterial.Input -> GlassMaterialMetrics(0.dp, 0.dp, 0f, false)
        GlassMaterial.Overlay -> GlassMaterialMetrics(
            elevation = 18.dp,
            blurRadius = intensityProfile.overlayBlurRadius,
            noiseFactor = intensityProfile.overlayNoiseFactor,
            canBlur = true,
        )
    }
    return ResolvedGlassMaterial(
        surface = surface,
        fallback = fallback,
        elevation = metrics.elevation,
        blurRadius = metrics.blurRadius,
        noiseFactor = metrics.noiseFactor,
        canBlur = metrics.canBlur,
        highlight = highlight,
        shadow = shadow,
    )
}

/** The interpolated values used by all glass surfaces for one strength value. */
internal data class GlassIntensityProfile(
    val floatingBlurRadius: Dp,
    val floatingNoiseFactor: Float,
    val overlayBlurRadius: Dp,
    val overlayNoiseFactor: Float,
    val tintAlphaScale: Float,
    val fallbackAlphaScale: Float,
    val highlightAlphaScale: Float,
    val shadowAlphaScale: Float,
)

/**
 * Resolves the strength using the historical 75% point as an explicit anchor.
 * This preserves the v1.4.1 values while making both ends of the slider useful.
 */
internal fun resolveGlassIntensityProfile(value: Float): GlassIntensityProfile {
    val intensity = sanitizeGlassIntensity(value)
    return GlassIntensityProfile(
        floatingBlurRadius = anchoredDp(intensity, 0f, 22f, 30f),
        floatingNoiseFactor = anchored(intensity, 0f, 0.025f, 0.032f),
        overlayBlurRadius = anchoredDp(intensity, 0f, 28f, 36f),
        overlayNoiseFactor = anchored(intensity, 0f, 0.030f, 0.038f),
        tintAlphaScale = anchored(intensity, 0.72f, 1f, 1.08f),
        fallbackAlphaScale = anchored(intensity, 0.90f, 1f, 1.03f),
        highlightAlphaScale = anchored(intensity, 0f, 1f, 1.15f),
        shadowAlphaScale = anchored(intensity, 0.45f, 1f, 1.10f),
    )
}

private fun anchoredDp(value: Float, atZero: Float, atDefault: Float, atMax: Float): Dp =
    Dp(anchored(value, atZero, atDefault, atMax))

private fun anchored(value: Float, atZero: Float, atDefault: Float, atMax: Float): Float {
    val intensity = sanitizeGlassIntensity(value)
    return when (intensity) {
        0f -> atZero
        DEFAULT_GLASS_INTENSITY -> atDefault
        1f -> atMax
        else -> if (intensity < DEFAULT_GLASS_INTENSITY) {
            atZero + (atDefault - atZero) * (intensity / DEFAULT_GLASS_INTENSITY)
        } else {
            atDefault + (atMax - atDefault) *
                ((intensity - DEFAULT_GLASS_INTENSITY) / (1f - DEFAULT_GLASS_INTENSITY))
        }
    }
}

private fun Color.withAlphaScale(scale: Float): Color =
    if (scale == 1f) this else copy(alpha = (alpha * scale).coerceIn(0f, 1f))
