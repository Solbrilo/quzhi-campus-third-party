package cn.qzxy.hotwater.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.platform.app.InstrumentationRegistry
import cn.qzxy.hotwater.designsystem.theme.GlassTheme
import cn.qzxy.hotwater.designsystem.theme.ThemeMode
import cn.qzxy.hotwater.designsystem.theme.ThemePalette
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UpdateWelcomeInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun showsWelcomeThenHalfSheetAndRequiresExplicitConfirmation() {
        var confirmations = 0
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            GlassTheme(themeMode = ThemeMode.DARK, palette = ThemePalette.JADE) {
                UpdateWelcomePage { confirmations++ }
            }
        }
        rule.onNodeWithText("快速开始").assertIsDisplayed()
        rule.onNodeWithTag("update_welcome_continue").assertDoesNotExist()
        rule.onNodeWithText("快速开始").performClick()
        rule.onNodeWithTag("update_welcome_continue").assertIsDisplayed()
        rule.onNodeWithText("灵动液态玻璃").assertIsDisplayed()
        rule.onNodeWithText("四套全新主题").assertIsDisplayed()
        val metrics = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        val height = rule.onNodeWithTag("update_welcome_sheet").fetchSemanticsNode().boundsInRoot.height
        assertTrue("The introduction should occupy about half the screen", height / metrics.heightPixels in 0.4f..0.6f)
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("update_welcome_continue").assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, confirmations) }
        rule.onNodeWithText("太棒了").performClick()
        rule.runOnIdle { assertEquals(1, confirmations) }
    }
}
