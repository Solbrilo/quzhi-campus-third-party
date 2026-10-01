package cn.qzxy.hotwater.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppHeaderInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun moreButtonIsA48DpSquare() {
        setHeaderContent()

        composeRule.onNodeWithTag(OverflowMenuButtonTag)
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun messageButtonDescribesAndInvokesTheMessagePage() {
        var moreClicks = 0
        setHeaderContent(onOpenMessages = { moreClicks += 1 })

        composeRule.onNodeWithTag(OverflowMenuButtonTag)
            .assert(hasContentDescription("消息"))
            .performClick()

        composeRule.runOnIdle { assertEquals(1, moreClicks) }
    }

    private fun setHeaderContent(onOpenMessages: () -> Unit = {}) {
        composeRule.setContent {
            GlassTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    AppHeader(onOpenMessages = onOpenMessages)
                }
            }
        }
    }

    private companion object {
        fun hasContentDescription(value: String): SemanticsMatcher =
            SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(value))
    }
}
