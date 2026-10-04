package cn.qzxy.hotwater.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import cn.qzxy.hotwater.designsystem.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ThemePaletteInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun dropdownSwitchesEveryPaletteAndThemeTransitionKeepsContentMounted() {
        val palette = mutableStateOf(ThemePalette.SKY)
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val strength = mutableFloatStateOf(0.75f)
        var mounts = 0
        var visibleTokens = LightGlassTokens
        rule.setContent {
            GlassTheme(mode.value, strength.floatValue, palette.value) {
                DisposableEffect(Unit) { mounts++; onDispose {} }
                val tokens = LocalGlassTokens.current
                SideEffect { visibleTokens = tokens }
                Column {
                    ThemePaletteSelector(palette.value) { palette.value = it }
                    Text("主题测试", color = tokens.textPrimary)
                }
            }
        }
        ThemePalette.entries.forEach { next ->
            rule.onNodeWithTag("theme_palette_selector").performClick()
            rule.onNodeWithTag("theme_palette_${next.storageValue}").performClick()
            rule.runOnIdle { assertEquals(next, palette.value) }
            rule.runOnIdle { mode.value = ThemeMode.DARK; strength.floatValue = 0f }
            rule.waitForIdle()
            rule.runOnIdle { assertEquals(next.tokens(true), visibleTokens); assertEquals(1, mounts) }
            rule.runOnIdle { mode.value = ThemeMode.LIGHT; strength.floatValue = 0.75f }
            rule.waitForIdle()
        }
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { mode.value = ThemeMode.DARK }
        rule.mainClock.advanceTimeBy(160)
        rule.runOnUiThread {
            assertNotEquals(palette.value.tokens(false).meshBase, visibleTokens.meshBase)
            assertNotEquals(palette.value.tokens(true).meshBase, visibleTokens.meshBase)
            assertEquals(1, mounts)
        }
        rule.mainClock.autoAdvance = true
    }
}
