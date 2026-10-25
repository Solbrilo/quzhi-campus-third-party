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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
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
    liquidRim: Boolean = false,
    liquidTintScale: Float = 1f,
    sampleContent: Boolean = false,
    lensExpansion: Float = 0f,
    backdrop: Backdrop? = null,
    backdropBlurScale: Float = 1f,
    refractionScale: Float = 1f,
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
    val liquidBackdrop = backdrop ?: if (material == GlassMaterial.Floating || sampleContent) environment.floatingBackdrop
        else environment.backgroundBackdrop
    // The theme disables the entire capture graph at zero, including its sources.
    val surfaceModifier = if (environment.liquidAvailable && liquidBackdrop != null) {
        val intensity = environment.glassIntensity
        val expansion = lensExpansion.coerceIn(0f, 1f)
        Modifier.drawBackdrop(
            backdrop = liquidBackdrop,
            shape = { shape },
            effects = {
                if (intensity > 0f && size.isSpecified && size.width > 0f && size.height > 0f) {
                    if (backdropBlurScale > 0f) {
                        blur(((1f + intensity * 5f * (1f - expansion * 0.6f)) * backdropBlurScale).dp.toPx())
                    }
                    val refraction = refractionScale.coerceIn(0f, 1f)
                    // Omit the lens entirely at rest, preserving the captured text geometry.
                    if (refraction > 0f) {
                        lens((5f + intensity * (13f + expansion * 5f)).dp.toPx(),
                            (intensity * (24f + expansion * 12f) * refraction).dp.toPx(),
                            depthEffect = true, chromaticAberration = intensity * refraction > 0.85f)
                    }
                }
            },
            highlight = if (liquidRim) ({ if (intensity > 0f) Highlight.Default else null }) else null,
            shadow = null,
            onDrawBackdrop = { drawBackdrop ->
                if (intensity > 0f) {
                    withTransform({ scale(1f + expansion * 0.16f, 1f + expansion * 0.16f, center) }) {
                        drawBackdrop()
                    }
                }
            },
            onDrawSurface = {
                drawRect(if (intensity > 0f) resolved.surface.withAlphaScale(liquidTintScale)
                    else if (resolved.canBlur) resolved.fallback else resolved.surface)
            },
        )
    } else if (
        resolved.canBlur &&
        environment.blurAvailable
    ) {
        Modifier.hazeEffect(state = environment.hazeState) {
            blurEnabled = environment.glassIntensity > 0f
            blurRadius = (resolved.blurRadius * backdropBlurScale).coerceAtLeast(0.1.dp)
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
                val strength = 0.45f + 0.55f * environment.glassIntensity
                listOf(
                    tokens.borderHi.withAlphaScale(strength),
                    tokens.borderHi.withAlphaScale(strength * 0.45f),
                    tokens.borderLo.withAlphaScale(0.5f),
                    tokens.borderHi.withAlphaScale(strength * 0.3f),
                    tokens.borderLo,
                )
            }
            val border = Brush.linearGradient(
                colors = if (pressed) {
                    borderColors.reversed()
                } else {
                    borderColors
                },
                start = Offset.Zero,
                end = Offset(size.width * 0.65f, size.height),
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
            // Inset the stroke so the shape clip does not cut away its outer half.
            val inset = strokeWidth / 2f
            val edgeSize = Size((size.width - strokeWidth).coerceAtLeast(0f),
                (size.height - strokeWidth).coerceAtLeast(0f))
            val curve = (cornerRadius.toPx() - inset).coerceIn(0f,
                minOf(edgeSize.width, edgeSize.height) / 2f)
            val radius = CornerRadius(curve, curve)
            val right = size.width - inset
            val bottom = size.height - inset
            val upperRim = Path().apply {
                moveTo(inset, inset + curve)
                if (curve > 0f) arcTo(Rect(inset, inset, inset + curve * 2f, inset + curve * 2f),
                    180f, 90f, false)
                lineTo(right - curve, inset)
                if (curve > 0f) arcTo(Rect(right - curve * 2f, inset, right, inset + curve * 2f),
                    270f, 90f, false)
            }
            val lowerRim = Path().apply {
                moveTo(inset, bottom - curve)
                if (curve > 0f) arcTo(Rect(inset, bottom - curve * 2f, inset + curve * 2f, bottom),
                    180f, -90f, false)
                lineTo(right - curve, bottom)
                if (curve > 0f) arcTo(Rect(right - curve * 2f, bottom - curve * 2f, right, bottom),
                    90f, -90f, false)
            }
            val rimLight = Brush.linearGradient(
                0f to highlight.withAlphaScale(0.12f),
                0.22f to highlight,
                0.58f to highlight.withAlphaScale(0.6f),
                1f to highlight.withAlphaScale(0.04f),
                start = Offset.Zero,
                end = Offset(size.width, 0f),
            )

            onDrawWithContent {
                drawContent()
                drawRoundRect(
                    brush = border,
                    topLeft = Offset(inset, inset),
                    size = edgeSize,
                    cornerRadius = radius,
                    style = Stroke(width = strokeWidth),
                )
                if (environment.preferOpaqueMaterials) drawLine(
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
                else {
                    drawPath(if (pressed) lowerRim else upperRim, rimLight,
                        style = Stroke(width = highlightStrokeWidth, cap = StrokeCap.Round))
                    drawPath(if (pressed) upperRim else lowerRim,
                        resolved.shadow.withAlphaScale(0.35f * environment.glassIntensity),
                        style = Stroke(width = highlightStrokeWidth, cap = StrokeCap.Round))
                }
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
