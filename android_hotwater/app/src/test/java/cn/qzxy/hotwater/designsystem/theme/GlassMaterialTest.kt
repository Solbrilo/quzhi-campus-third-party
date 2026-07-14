package cn.qzxy.hotwater.designsystem.theme

import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassMaterialTest {
    @Test
    fun intensityProfileKeepsTheZeroDefaultAndMaximumAnchors() {
        val zero = resolveGlassIntensityProfile(0f)
        val baseline = resolveGlassIntensityProfile(DEFAULT_GLASS_INTENSITY)
        val maximum = resolveGlassIntensityProfile(1f)

        assertEquals(0.dp, zero.floatingBlurRadius)
        assertEquals(0f, zero.floatingNoiseFactor, 0f)
        assertEquals(0.dp, zero.overlayBlurRadius)
        assertEquals(0f, zero.overlayNoiseFactor, 0f)
        assertEquals(0.72f, zero.tintAlphaScale, 0.0001f)
        assertEquals(0.90f, zero.fallbackAlphaScale, 0.0001f)
        assertEquals(0f, zero.highlightAlphaScale, 0f)
        assertEquals(0.45f, zero.shadowAlphaScale, 0.0001f)

        assertEquals(22.dp, baseline.floatingBlurRadius)
        assertEquals(0.025f, baseline.floatingNoiseFactor, 0.0001f)
        assertEquals(28.dp, baseline.overlayBlurRadius)
        assertEquals(0.030f, baseline.overlayNoiseFactor, 0.0001f)
        assertEquals(1f, baseline.tintAlphaScale, 0f)
        assertEquals(1f, baseline.fallbackAlphaScale, 0f)
        assertEquals(1f, baseline.highlightAlphaScale, 0f)
        assertEquals(1f, baseline.shadowAlphaScale, 0f)

        assertEquals(30.dp, maximum.floatingBlurRadius)
        assertEquals(0.032f, maximum.floatingNoiseFactor, 0.0001f)
        assertEquals(36.dp, maximum.overlayBlurRadius)
        assertEquals(0.038f, maximum.overlayNoiseFactor, 0.0001f)
        assertEquals(1.08f, maximum.tintAlphaScale, 0.0001f)
        assertEquals(1.03f, maximum.fallbackAlphaScale, 0.0001f)
        assertEquals(1.15f, maximum.highlightAlphaScale, 0.0001f)
        assertEquals(1.10f, maximum.shadowAlphaScale, 0.0001f)
    }

    @Test
    fun intensityProfileIsMonotonicAcrossTheSlider() {
        val values = listOf(0f, 0.25f, 0.5f, DEFAULT_GLASS_INTENSITY, 0.875f, 1f)
            .map(::resolveGlassIntensityProfile)
        values.zipWithNext().forEach { (previous, next) ->
            assertTrue(next.floatingBlurRadius >= previous.floatingBlurRadius)
            assertTrue(next.overlayBlurRadius >= previous.overlayBlurRadius)
            assertTrue(next.tintAlphaScale >= previous.tintAlphaScale)
            assertTrue(next.fallbackAlphaScale >= previous.fallbackAlphaScale)
            assertTrue(next.highlightAlphaScale >= previous.highlightAlphaScale)
            assertTrue(next.shadowAlphaScale >= previous.shadowAlphaScale)
        }
    }

    @Test
    fun onlyFloatingAndOverlayMaterialsUseHaze() {
        val hazeMaterials = setOf(GlassMaterial.Floating, GlassMaterial.Overlay)
        GlassMaterial.values().forEach { material ->
            val resolved = resolveGlassMaterial(
                material = material,
                tokens = LightGlassTokens,
                pressed = false,
                enabled = true,
            )
            assertEquals(material in hazeMaterials, resolved.canBlur)
        }
    }

    @Test
    fun baselinePreservesHistoricalLightAndDarkSurfaceValues() {
        listOf(LightGlassTokens, DarkGlassTokens).forEach { tokens ->
            GlassMaterial.values().forEach { material ->
                val resolved = resolveGlassMaterial(
                    material = material,
                    tokens = tokens,
                    pressed = false,
                    enabled = true,
                )
                val expectedSurface = when (material) {
                    GlassMaterial.Panel -> tokens.panelSurface
                    GlassMaterial.Floating -> tokens.floatingSurface
                    GlassMaterial.Control -> tokens.controlSurface
                    GlassMaterial.EmbeddedControl -> tokens.embeddedControlSurface
                    GlassMaterial.Input -> tokens.inputSurface
                    GlassMaterial.Overlay -> tokens.overlaySurface
                }
                assertEquals(expectedSurface, resolved.surface)
                assertEquals(tokens.fallbackSurface, resolved.fallback)
            }
        }
    }

    @Test
    fun pressedAndDisabledBranchesKeepTheirBaselineMixing() {
        val material = GlassMaterial.Floating
        val tokens = DarkGlassTokens
        val pressed = resolveGlassMaterial(material, tokens, pressed = true, enabled = true)
        val expectedPressed = lerp(tokens.floatingSurface, tokens.pressedSurface, 0.28f)
        assertEquals(expectedPressed, pressed.surface)

        val disabled = resolveGlassMaterial(material, tokens, pressed = false, enabled = false)
        val expectedDisabled = tokens.floatingSurface.copy(
            alpha = tokens.floatingSurface.alpha * 0.72f,
        )
        assertEquals(expectedDisabled, disabled.surface)
        assertFalse(disabled.surface.alpha.isNaN())
    }
}
