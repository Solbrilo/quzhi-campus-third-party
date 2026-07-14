package cn.qzxy.hotwater.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import cn.qzxy.hotwater.testutil.RecordingHapticFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GlassPressableInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ordinaryClickInvokesActionAndEmitsSegmentTick() {
        val haptic = RecordingHapticFeedback()
        var clicked = false

        composeRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptic) {
                GlassPressable(
                    onClick = { clicked = true },
                    modifier = Modifier
                        .size(width = 120.dp, height = 48.dp)
                        .testTag(PressableTag),
                ) {}
            }
        }

        composeRule.onNodeWithTag(PressableTag).performClick()

        composeRule.runOnIdle {
            assertTrue(clicked)
            assertEquals(listOf(HapticFeedbackType.SegmentTick), haptic.events)
        }
    }

    @Test
    fun disabledPressableIgnoresTouchAndDoesNotEmitHapticFeedback() {
        val haptic = RecordingHapticFeedback()
        var clicked = false

        composeRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptic) {
                GlassPressable(
                    onClick = { clicked = true },
                    enabled = false,
                    modifier = Modifier
                        .size(width = 120.dp, height = 48.dp)
                        .testTag(PressableTag),
                ) {}
            }
        }

        composeRule.onNodeWithTag(PressableTag)
            .assertIsNotEnabled()
            .performTouchInput { click() }

        composeRule.runOnIdle {
            assertFalse(clicked)
            assertTrue(haptic.events.isEmpty())
        }
    }

    @Test
    fun dynamicToggleFeedbackDescribesTheCommittedState() {
        val haptic = RecordingHapticFeedback()
        var toggled by mutableStateOf(false)

        composeRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptic) {
                GlassPressable(
                    onClick = { toggled = !toggled },
                    hapticFeedback = {
                        if (toggled) {
                            HapticFeedbackType.ToggleOff
                        } else {
                            HapticFeedbackType.ToggleOn
                        }
                    },
                    modifier = Modifier
                        .size(width = 120.dp, height = 48.dp)
                        .testTag(PressableTag),
                ) {}
            }
        }

        composeRule.onNodeWithTag(PressableTag).performClick()
        composeRule.onNodeWithTag(PressableTag).performClick()

        composeRule.runOnIdle {
            assertFalse(toggled)
            assertEquals(
                listOf(
                    HapticFeedbackType.ToggleOn,
                    HapticFeedbackType.ToggleOff,
                ),
                haptic.events,
            )
        }
    }

    private companion object {
        const val PressableTag = "glass_pressable_under_test"
    }
}
