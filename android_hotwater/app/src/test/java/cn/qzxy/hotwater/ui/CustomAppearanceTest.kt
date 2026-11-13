package cn.qzxy.hotwater.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cn.qzxy.hotwater.designsystem.theme.CustomThemeSettings
import cn.qzxy.hotwater.designsystem.theme.ThemePalette
import cn.qzxy.hotwater.designsystem.theme.WaterAnimation
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.resolveGlassMaterial
import org.junit.Assert.*
import org.junit.Test

class CustomAppearanceTest {
    @Test fun darkImagesStillHaveDistinctReadableLightAndDarkAppearances() {
        for (background in listOf(0xFF07111C, 0xFF34586C, 0xFFF4D5B8)) {
            val settings = CustomThemeSettings(background = background, image = true)
            val light = settings.tokens(false)
            val dark = settings.tokens(true)
            assertTrue(light.panelSurface.luminance() > .60f)
            assertTrue(dark.panelSurface.luminance() < .08f)
            fun contrast(a: Color, b: Color) = (maxOf(a.luminance(), b.luminance()) + .05f) /
                (minOf(a.luminance(), b.luminance()) + .05f)
            assertTrue(contrast(light.textPrimary, light.fallbackSurface) > 7f)
            assertTrue(contrast(dark.textPrimary, dark.fallbackSurface) > 7f)
        }
    }

    @Test fun cardBlurChangesRadiusWithoutChangingTintOrReadableInk() {
        val settings = CustomThemeSettings(image = true)
        for (dark in listOf(false, true)) {
            val soft = settings.copy(cardBlur = 0f).tokens(dark)
            val solid = settings.copy(cardBlur = 32f).tokens(dark)
            val clearMaterial = resolveGlassMaterial(GlassMaterial.Panel, soft, false, true, cardBlur = 0f)
            val blurredMaterial = resolveGlassMaterial(GlassMaterial.Panel, solid, false, true, cardBlur = 32f)
            assertEquals(clearMaterial.surface, blurredMaterial.surface)
            assertEquals(0f, clearMaterial.blurRadius.value, 0f)
            assertEquals(32f, blurredMaterial.blurRadius.value, 0f)
            assertTrue(blurredMaterial.canBlur)
            assertEquals(soft.textPrimary, solid.textPrimary)
        }
        assertEquals(ThemePalette.SKY, ThemePalette.fromStorageValue("brass"))
        assertEquals(WaterAnimation.GEAR, WaterAnimation.fromStorageValue("brass"))
    }
}
