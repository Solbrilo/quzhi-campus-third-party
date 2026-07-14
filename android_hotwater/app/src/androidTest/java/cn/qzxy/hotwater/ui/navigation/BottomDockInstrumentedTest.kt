package cn.qzxy.hotwater.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cn.qzxy.hotwater.testutil.RecordingHapticFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BottomDockInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedItemExposesTabRoleAndSelectedSemantics() {
        setDockContent(initialSelectedIndex = 1)

        composeRule.onNodeWithTag(tabTag(1))
            .assertIsSelected()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Role,
                    Role.Tab,
                ),
            )
    }

    @Test
    fun clickingUnselectedTabNavigatesAndEmitsOneSegmentTick() {
        val haptic = RecordingHapticFeedback()
        val selections = mutableListOf<DockSelection>()
        setDockContent(haptic = haptic, selections = selections)

        composeRule.onNodeWithTag(tabTag(2)).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(DockSelection(2, DockSelectionSource.Click)), selections)
            assertEquals(listOf(HapticFeedbackType.SegmentTick), haptic.events)
        }
        composeRule.onNodeWithTag(tabTag(2)).assertIsSelected()
    }

    @Test
    fun clickingSelectedTabIsANoOpWithoutHapticFeedback() {
        val haptic = RecordingHapticFeedback()
        val selections = mutableListOf<DockSelection>()
        setDockContent(
            initialSelectedIndex = 1,
            haptic = haptic,
            selections = selections,
        )

        composeRule.onNodeWithTag(tabTag(1)).performClick()

        composeRule.runOnIdle {
            assertTrue(selections.isEmpty())
            assertTrue(haptic.events.isEmpty())
        }
    }

    @Test
    fun movementBelowTenDpRemainsAClick() {
        val haptic = RecordingHapticFeedback()
        val selections = mutableListOf<DockSelection>()
        val dragStates = mutableListOf<Boolean>()
        setDockContent(
            haptic = haptic,
            selections = selections,
            dragStates = dragStates,
        )
        val nineDpPx = with(composeRule.density) { 9.dp.toPx() }

        composeRule.onNodeWithTag(tabTag(1)).performTouchInput {
            down(center)
            moveBy(Offset(nineDpPx, 0f))
            up()
        }

        composeRule.runOnIdle {
            assertEquals(listOf(DockSelection(1, DockSelectionSource.Click)), selections)
            assertTrue(dragStates.isEmpty())
            assertEquals(listOf(HapticFeedbackType.SegmentTick), haptic.events)
        }
    }

    @Test
    fun horizontalDragSelectsEachCrossedRegionAndTracksDragState() {
        val haptic = RecordingHapticFeedback()
        val selections = mutableListOf<DockSelection>()
        val dragStates = mutableListOf<Boolean>()
        setDockContent(
            haptic = haptic,
            selections = selections,
            dragStates = dragStates,
        )

        composeRule.onNodeWithTag(DockTag).performTouchInput {
            val y = height / 2f
            val first = Offset(width / 6f, y)
            val second = Offset(width / 2f, y)
            val third = Offset(width * 5f / 6f, y)
            down(first)
            moveTo(second)
            moveTo(third)
            up()
        }

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    DockSelection(1, DockSelectionSource.Drag),
                    DockSelection(2, DockSelectionSource.Drag),
                ),
                selections,
            )
            assertEquals(listOf(true, false), dragStates)
            assertEquals(
                listOf(
                    HapticFeedbackType.SegmentTick,
                    HapticFeedbackType.SegmentTick,
                ),
                haptic.events,
            )
        }
    }

    @Test
    fun fastReverseRetargetsTheActiveDragWithoutAnExtraClick() {
        val haptic = RecordingHapticFeedback()
        val selections = mutableListOf<DockSelection>()
        val dragStates = mutableListOf<Boolean>()
        setDockContent(
            haptic = haptic,
            selections = selections,
            dragStates = dragStates,
        )

        composeRule.onNodeWithTag(DockTag).performTouchInput {
            val y = height / 2f
            val first = Offset(width / 6f, y)
            val third = Offset(width * 5f / 6f, y)
            down(first)
            moveTo(third)
            moveTo(first)
            up()
        }

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    DockSelection(2, DockSelectionSource.Drag),
                    DockSelection(0, DockSelectionSource.Drag),
                ),
                selections,
            )
            assertEquals(listOf(true, false), dragStates)
            assertEquals(2, haptic.events.size)
            assertTrue(haptic.events.all { it == HapticFeedbackType.SegmentTick })
        }
    }

    @Test
    fun fastReleaseProjectsToNextSnapPointWithoutCrossingRawRegion() {
        val haptic = RecordingHapticFeedback()
        val selections = mutableListOf<DockSelection>()
        val dragStates = mutableListOf<Boolean>()
        setDockContent(
            haptic = haptic,
            selections = selections,
            dragStates = dragStates,
        )
        val twelveDpPx = with(composeRule.density) { 12.dp.toPx() }

        composeRule.onNodeWithTag(DockTag).performTouchInput {
            val start = Offset(width / 6f, height / 2f)
            down(start)
            moveBy(Offset(twelveDpPx, 0f), delayMillis = 200L)
            moveBy(Offset(width / 20f, 0f), delayMillis = 16L)
            moveBy(Offset(width / 20f, 0f), delayMillis = 16L)
            up()
        }

        composeRule.runOnIdle {
            assertEquals(listOf(DockSelection(1, DockSelectionSource.Drag)), selections)
            assertEquals(listOf(true, false), dragStates)
            assertEquals(listOf(HapticFeedbackType.SegmentTick), haptic.events)
        }
        composeRule.onNodeWithTag(tabTag(1)).assertIsSelected()
    }

    @Test
    fun settlingIndicatorCanBeRegrabbedAndRetargetedInReverse() {
        val haptic = RecordingHapticFeedback()
        val selections = mutableListOf<DockSelection>()
        val dragStates = mutableListOf<Boolean>()
        val twelveDpPx = with(composeRule.density) { 12.dp.toPx() }

        setDockContent(
            haptic = haptic,
            selections = selections,
            dragStates = dragStates,
        )
        composeRule.mainClock.autoAdvance = false

        try {
            composeRule.onNodeWithTag(DockTag).performTouchInput {
                val start = Offset(width / 6f, height / 2f)
                down(start)
                moveBy(Offset(twelveDpPx, 0f), delayMillis = 200L)
                moveBy(Offset(width / 20f, 0f), delayMillis = 16L)
                moveBy(Offset(width / 20f, 0f), delayMillis = 16L)
                up()
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                assertEquals(1, selections.last().index)
            }
            composeRule.mainClock.advanceTimeBy(32L)
            composeRule.waitForIdle()

            val tabZeroCenterX = composeRule.onNodeWithTag(tabTag(0))
                .fetchSemanticsNode()
                .boundsInRoot
                .center
                .x
            val tabOneCenterX = composeRule.onNodeWithTag(tabTag(1))
                .fetchSemanticsNode()
                .boundsInRoot
                .center
                .x
            val dockLeft = composeRule.onNodeWithTag(DockTag)
                .fetchSemanticsNode()
                .boundsInRoot
                .left
            val settlingCenterX = composeRule.onNodeWithTag(IndicatorTag)
                .fetchSemanticsNode()
                .boundsInRoot
                .center
                .x

            assertTrue(settlingCenterX > tabZeroCenterX)
            assertTrue(settlingCenterX < tabOneCenterX - 1f)
            composeRule.onNodeWithTag(DockTag).performTouchInput {
                val y = height / 2f
                down(Offset(settlingCenterX - dockLeft, y))
                moveBy(Offset(-twelveDpPx, 0f), delayMillis = 120L)
                moveTo(Offset(width / 6f, y), delayMillis = 300L)
                advanceEventTime(300L)
                up()
            }
            composeRule.runOnIdle {
                assertEquals(0, selections.last().index)
            }
            composeRule.mainClock.advanceTimeBy(1_000L)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(0, selections.last().index)
            assertTrue(selections.all { it.source == DockSelectionSource.Drag })
            assertEquals(listOf(true, false, true, false), dragStates)
            assertTrue(haptic.events.all { it == HapticFeedbackType.SegmentTick })
        }
        composeRule.onNodeWithTag(tabTag(0)).assertIsSelected()
        val tabZeroCenterX = composeRule.onNodeWithTag(tabTag(0))
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .x
        val settledCenterX = composeRule.onNodeWithTag(IndicatorTag)
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .x
        assertEquals(tabZeroCenterX, settledCenterX, 1f)
    }

    @Test
    fun rtlAlignsLogicalZeroWithIndicatorOnRightAndMapsClickAndDrag() {
        val haptic = RecordingHapticFeedback()
        val selections = mutableListOf<DockSelection>()
        setDockContent(
            haptic = haptic,
            selections = selections,
            layoutDirection = LayoutDirection.Rtl,
        )

        val dockBounds = composeRule.onNodeWithTag(DockTag).fetchSemanticsNode().boundsInRoot
        val tabZeroBounds = composeRule.onNodeWithTag(tabTag(0)).fetchSemanticsNode().boundsInRoot
        val tabTwoBounds = composeRule.onNodeWithTag(tabTag(2)).fetchSemanticsNode().boundsInRoot
        val indicatorBounds = composeRule
            .onNodeWithTag(IndicatorTag)
            .fetchSemanticsNode()
            .boundsInRoot
        val rightThirdStart = dockBounds.left + dockBounds.width * 2f / 3f
        val leftThirdEnd = dockBounds.left + dockBounds.width / 3f

        assertTrue(tabZeroBounds.center.x >= rightThirdStart)
        assertTrue(indicatorBounds.center.x >= rightThirdStart)
        assertTrue(tabTwoBounds.center.x <= leftThirdEnd)
        assertEquals(tabZeroBounds.center.x, indicatorBounds.center.x, 1f)

        composeRule.onNodeWithTag(tabTag(2)).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(DockSelection(2, DockSelectionSource.Click)), selections)
            selections.clear()
            haptic.events.clear()
        }

        composeRule.onNodeWithTag(DockTag).performTouchInput {
            val y = height / 2f
            val left = Offset(width / 6f, y)
            val middle = Offset(width / 2f, y)
            val right = Offset(width * 5f / 6f, y)
            down(left)
            moveTo(middle)
            moveTo(right)
            up()
        }

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    DockSelection(1, DockSelectionSource.Drag),
                    DockSelection(0, DockSelectionSource.Drag),
                ),
                selections,
            )
            assertTrue(selections.none { it.source == DockSelectionSource.Click })
        }
    }

    @Test
    fun pointerCancellationClosesActiveDragWithoutClick() {
        val haptic = RecordingHapticFeedback()
        val selections = mutableListOf<DockSelection>()
        val dragStates = mutableListOf<Boolean>()
        setDockContent(
            haptic = haptic,
            selections = selections,
            dragStates = dragStates,
        )
        val twelveDpPx = with(composeRule.density) { 12.dp.toPx() }

        composeRule.onNodeWithTag(DockTag).performTouchInput {
            val start = Offset(width / 6f, height / 2f)
            down(start)
            moveBy(Offset(twelveDpPx, 0f))
            cancel()
        }

        composeRule.runOnIdle {
            assertEquals(listOf(true, false), dragStates)
            assertTrue(selections.isEmpty())
            assertTrue(haptic.events.isEmpty())
        }
    }

    private fun setDockContent(
        initialSelectedIndex: Int = 0,
        haptic: RecordingHapticFeedback = RecordingHapticFeedback(),
        selections: MutableList<DockSelection> = mutableListOf(),
        dragStates: MutableList<Boolean> = mutableListOf(),
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        composeRule.setContent {
            var selectedIndex by remember { mutableIntStateOf(initialSelectedIndex) }
            CompositionLocalProvider(
                LocalHapticFeedback provides haptic,
                LocalLayoutDirection provides layoutDirection,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                    ) {
                        BottomDock(
                            labels = listOf("Operations", "Records", "Settings"),
                            selectedIndex = selectedIndex,
                            onSelected = { index, source ->
                                selections += DockSelection(index, source)
                                selectedIndex = index
                            },
                            onDragStateChanged = dragStates::add,
                        )
                    }
                }
            }
        }
    }

    private data class DockSelection(
        val index: Int,
        val source: DockSelectionSource,
    )

    private companion object {
        const val DockTag = "bottom_dock"
        const val IndicatorTag = "bottom_dock_indicator"

        fun tabTag(index: Int) = "bottom_dock_tab_$index"
    }
}
