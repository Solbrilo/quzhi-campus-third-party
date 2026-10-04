package cn.qzxy.hotwater.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class ThemePaletteTest {
    @Test fun defaultAndInvalidPreferencesPreserveExistingAppearance() {
        assertEquals(ThemePalette.SKY, ThemePalette.fromStorageValue(null))
        assertEquals(ThemePalette.SKY, ThemePalette.fromStorageValue("unknown"))
        assertEquals(LightGlassTokens, ThemePalette.SKY.tokens(false))
        assertEquals(DarkGlassTokens, ThemePalette.SKY.tokens(true))
        ThemePalette.entries.forEach { assertEquals(it, ThemePalette.fromStorageValue(it.storageValue)) }
    }

    @Test fun everyPaletteHasReadableDarkAndLightColors() {
        ThemePalette.entries.forEach { palette ->
            listOf(false, true).forEach { dark ->
                val colors = palette.tokens(dark)
                assertTrue("$palette $dark primary text", contrast(colors.textPrimary, colors.meshBase) >= 7f)
                assertTrue("$palette $dark secondary text", contrast(colors.textSecondary, colors.meshBase) >= 4.5f)
                assertTrue("$palette $dark accent text", contrast(colors.accentPrimary, colors.meshBase) >= 4.5f)
            }
            assertTrue(palette.tokens(true).meshBase.luminance() < palette.tokens(false).meshBase.luminance())
        }
    }

    @Test fun transitionIncludesSurfacesTextAndAccent() {
        val a = ThemePalette.JADE.tokens(true)
        val b = ThemePalette.IRIS.tokens(false)
        assertEquals(a, lerpGlassTokens(a, b, 0f))
        assertEquals(b, lerpGlassTokens(a, b, 1f))
        val middle = lerpGlassTokens(a, b, 0.5f)
        assertNotEquals(a.meshBase, middle.meshBase)
        assertNotEquals(b.textPrimary, middle.textPrimary)
        assertNotEquals(a.accentPrimary, middle.accentPrimary)
    }

    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance(); val y = b.luminance()
        return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
    }
}
