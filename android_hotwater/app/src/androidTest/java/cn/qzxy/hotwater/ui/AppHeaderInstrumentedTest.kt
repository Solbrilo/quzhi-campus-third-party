package cn.qzxy.hotwater.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cn.qzxy.hotwater.designsystem.theme.GlassTheme
import cn.qzxy.hotwater.designsystem.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppHeaderInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun overflowAndMenuActionsAreAllFortyEightDpSquares() {
        setHeaderContent(initialExpanded = true)

        listOf(
            OverflowMenuButtonTag,
            OverflowAboutButtonTag,
            OverflowThemeButtonTag,
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag)
                .assertWidthIsEqualTo(48.dp)
                .assertHeightIsEqualTo(48.dp)
        }
        composeRule.onNodeWithTag(OverflowMenuHostTag)
            .assertWidthIsEqualTo(112.dp)
    }

    @Test
    fun overflowButtonDescribesCollapsedAndExpandedState() {
        setHeaderContent()

        composeRule.onNodeWithTag(OverflowMenuButtonTag)
            .assert(hasStateDescription("菜单已收起"))
            .performClick()
            .assert(hasStateDescription("菜单已展开"))

        composeRule.onNodeWithTag(OverflowMenuHostTag).assertExists()
    }

    @Test
    fun themeActionCyclesAllModesUpdatesSemanticsAndKeepsMenuOpen() {
        val themeChanges = mutableListOf<ThemeMode>()
        setHeaderContent(themeChanges = themeChanges)

        composeRule.onNodeWithTag(OverflowMenuButtonTag).performClick()
        assertThemeState("当前系统主题，下一模式为明亮主题")

        composeRule.onNodeWithTag(OverflowThemeButtonTag).performClick()
        assertThemeState("当前明亮主题，下一模式为暗夜主题")

        composeRule.onNodeWithTag(OverflowThemeButtonTag).performClick()
        assertThemeState("当前暗夜主题，下一模式为系统主题")

        composeRule.onNodeWithTag(OverflowThemeButtonTag).performClick()
        assertThemeState("当前系统主题，下一模式为明亮主题")

        composeRule.runOnIdle {
            assertEquals(
                listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM),
                themeChanges,
            )
        }
    }

    @Test
    fun aboutActionInvokesCallbackAndHostClosesMenu() {
        var aboutClicks = 0
        setHeaderContent(onAbout = { aboutClicks += 1 })

        composeRule.onNodeWithTag(OverflowMenuButtonTag).performClick()
        composeRule.onNodeWithTag(OverflowAboutButtonTag).performClick()

        composeRule.runOnIdle { assertEquals(1, aboutClicks) }
        composeRule.onNodeWithTag(OverflowMenuHostTag).assertDoesNotExist()
        composeRule.onNodeWithTag(OverflowMenuButtonTag)
            .assert(hasStateDescription("菜单已收起"))
    }

    private fun assertThemeState(stateDescription: String) {
        composeRule.onNodeWithTag(OverflowMenuHostTag).assertExists()
        composeRule.onNodeWithTag(OverflowMenuButtonTag)
            .assert(hasStateDescription("菜单已展开"))
        composeRule.onNodeWithTag(OverflowThemeButtonTag)
            .assert(hasStateDescription(stateDescription))
    }

    private fun setHeaderContent(
        initialThemeMode: ThemeMode = ThemeMode.SYSTEM,
        initialExpanded: Boolean = false,
        themeChanges: MutableList<ThemeMode> = mutableListOf(),
        onAbout: () -> Unit = {},
    ) {
        composeRule.setContent {
            var themeMode by remember { mutableStateOf(initialThemeMode) }
            var expanded by remember { mutableStateOf(initialExpanded) }

            GlassTheme(themeMode = themeMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    AppHeader(
                        themeMode = themeMode,
                        onThemeModeChange = { mode ->
                            themeChanges += mode
                            themeMode = mode
                        },
                        overflowExpanded = expanded,
                        onOverflowExpandedChange = { expanded = it },
                        onOpenAbout = {
                            onAbout()
                            expanded = false
                        },
                    )
                }
            }
        }
    }

    private companion object {
        fun hasStateDescription(value: String): SemanticsMatcher =
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)
    }
}
