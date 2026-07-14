package cn.qzxy.hotwater.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.qzxy.hotwater.designsystem.theme.DEFAULT_GLASS_INTENSITY
import cn.qzxy.hotwater.designsystem.theme.GlassTheme
import cn.qzxy.hotwater.designsystem.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GlassIntensityPanelInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sliderExposesContinuousRangeAndCurrentPercentage() {
        setPanel(initialIntensity = DEFAULT_GLASS_INTENSITY)

        composeRule.onNodeWithTag(GlassIntensitySliderTag)
            .assert(hasContentDescription(GlassIntensitySliderDescription))
            .assert(hasStateDescription("75%"))
            .assertRangeInfoEquals(
                ProgressBarRangeInfo(
                    current = DEFAULT_GLASS_INTENSITY,
                    range = 0f..1f,
                    steps = 0,
                ),
            )
        composeRule.onNodeWithTag(GlassIntensityValueTag).assertTextEquals("75%")
    }

    @Test
    fun touchGestureReportsPreviewBeforeCommit() {
        var latestIntensity = -1f
        val callbackOrder = mutableListOf<String>()
        setPanel(
            onChangeObserved = {
                latestIntensity = it
                callbackOrder += "preview"
            },
            onFinishedObserved = { callbackOrder += "commit" },
        )

        composeRule.onNodeWithTag(GlassIntensitySliderTag).performTouchInput {
            down(center)
            moveTo(Offset(width * 0.42f, height / 2f))
            up()
        }

        composeRule.runOnIdle {
            assertTrue(latestIntensity in 0f..1f)
            assertTrue(callbackOrder.isNotEmpty())
            assertEquals("preview", callbackOrder.first())
            assertEquals("commit", callbackOrder.last())
            assertEquals(1, callbackOrder.count { it == "commit" })
        }
    }

    @Test
    fun resetRestoresDefaultAndThenDisablesItself() {
        var resetCount = 0
        setPanel(
            initialIntensity = 0.2f,
            onResetObserved = { resetCount++ },
        )

        composeRule.onNodeWithTag(GlassIntensityResetTag)
            .assert(hasContentDescription(GlassIntensityResetDescription))
            .performClick()

        composeRule.onNodeWithTag(GlassIntensityValueTag).assertTextEquals("75%")
        composeRule.onNodeWithTag(GlassIntensityResetTag).assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(1, resetCount)
        }
    }

    @Test
    fun defaultIntensityDisablesReset() {
        setPanel(initialIntensity = DEFAULT_GLASS_INTENSITY)

        composeRule.onNodeWithTag(GlassIntensityResetTag)
            .assert(hasStateDescription("当前已是默认强度 75%"))
            .assertIsNotEnabled()
    }

    @Test
    fun narrowWidthAndEnlargedTextStayInsidePanel() {
        setPanel(
            initialIntensity = 1f,
            width = 220.dp,
            fontScale = 1.3f,
        )

        val panelBounds = composeRule.onNodeWithTag(GlassIntensityPanelTag)
            .fetchSemanticsNode()
            .boundsInRoot
        val titleBounds = composeRule.onNodeWithText("液态玻璃效果", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val valueBounds = composeRule.onNodeWithTag(GlassIntensityValueTag)
            .fetchSemanticsNode()
            .boundsInRoot
        val sliderBounds = composeRule.onNodeWithTag(GlassIntensitySliderTag)
            .fetchSemanticsNode()
            .boundsInRoot
        val resetBounds = composeRule.onNodeWithTag(GlassIntensityResetTag)
            .fetchSemanticsNode()
            .boundsInRoot

        listOf(titleBounds, valueBounds, sliderBounds, resetBounds).forEach { bounds ->
            assertTrue(bounds.left >= panelBounds.left - 1f)
            assertTrue(bounds.right <= panelBounds.right + 1f)
            assertTrue(bounds.top >= panelBounds.top - 1f)
            assertTrue(bounds.bottom <= panelBounds.bottom + 1f)
        }
        assertTrue(titleBounds.right <= valueBounds.left + 1f)
        assertTrue(sliderBounds.right <= resetBounds.left + 1f)
        with(composeRule.density) {
            assertEquals(48.dp.toPx(), resetBounds.width, 1f)
            assertEquals(48.dp.toPx(), resetBounds.height, 1f)
        }
    }

    private fun setPanel(
        initialIntensity: Float = DEFAULT_GLASS_INTENSITY,
        width: Dp = 320.dp,
        fontScale: Float = 1f,
        onChangeObserved: (Float) -> Unit = {},
        onFinishedObserved: () -> Unit = {},
        onResetObserved: () -> Unit = {},
    ) {
        composeRule.setContent {
            var intensity by remember { mutableFloatStateOf(initialIntensity) }
            val deviceDensity = LocalDensity.current

            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = deviceDensity.density,
                    fontScale = fontScale,
                ),
            ) {
                GlassTheme(themeMode = ThemeMode.LIGHT) {
                    Box(modifier = Modifier.width(width)) {
                        GlassIntensityPanel(
                            glassIntensity = intensity,
                            onGlassIntensityChange = { value ->
                                intensity = value
                                onChangeObserved(value)
                            },
                            onGlassIntensityChangeFinished = onFinishedObserved,
                            onResetGlassIntensity = {
                                intensity = DEFAULT_GLASS_INTENSITY
                                onResetObserved()
                            },
                        )
                    }
                }
            }
        }
    }
}
