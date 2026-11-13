package cn.qzxy.hotwater.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeLeft
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalHapticFeedback
import cn.qzxy.hotwater.testutil.RecordingHapticFeedback
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.swipeUp
import cn.qzxy.hotwater.designsystem.theme.*
import cn.qzxy.hotwater.ui.*

class MainTabNavigationInstrumentedTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var pager: PagerState

    @Test fun realSettingsSurvivesRepeatedHomeReturnsAndScrolling() {
        rule.setContent {
            GlassTheme(ThemeMode.LIGHT) {
                pager = rememberPagerState { 3 }
                val navigation = rememberMainTabNavigation(pager)
                val holder = rememberSaveableStateHolder()
                Box(Modifier.fillMaxSize()) {
                    IntentAwarePager(pager, Modifier.fillMaxSize().testTag("pager")) { page ->
                        ScrollViewport(Modifier.fillMaxSize().padding(20.dp)) {
                            holder.SaveableStateProvider(page) {
                                when (page) {
                                    0 -> OperationsPage(null, false, {}, {}, {}, {})
                                    2 -> MorePage(
                                        destination = MorePageDestination.OVERVIEW,
                                        motionEnabled = true, checkResult = null, checking = false,
                                        installing = false, releaseNotes = null,
                                        releaseNotesLoading = false, releaseNotesError = null,
                                        themeMode = ThemeMode.LIGHT, onThemeModeChange = {},
                                        glassIntensity = DEFAULT_GLASS_INTENSITY,
                                        onGlassIntensityChange = {}, onGlassIntensityChangeFinished = {},
                                        onResetGlassIntensity = {}, onCheckForUpdate = {},
                                        onLoadReleaseNotes = {}, onNavigate = {}, onClearAllUserData = {},
                                        onOpenExternal = {}, onBack = {}, embedded = true,
                                    )
                                    else -> Text("账单")
                                }
                            }
                        }
                    }
                    BottomDock(listOf("用水", "账单", "设置"), pager.currentPage,
                        onSelected = { page, _ -> navigation.select(page, true) }, onDragStateChanged = {},
                        pagePosition = { pager.currentPage + pager.currentPageOffsetFraction },
                        modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        rule.mainClock.autoAdvance = false
        repeat(6) {
            rule.onNodeWithTag("bottom_dock_tab_2").performClick()
            rule.mainClock.advanceTimeBy(2000)
            rule.runOnIdle { assertEquals(2, pager.settledPage) }
            rule.onNodeWithTag("pager").performTouchInput { swipeUp(durationMillis = 180) }
            rule.mainClock.advanceTimeBy(300)
            rule.onNodeWithTag("bottom_dock_tab_0").performClick()
            rule.mainClock.advanceTimeBy(2000)
            rule.runOnIdle { assertEquals(0, pager.settledPage) }
        }
        rule.onNodeWithTag("bottom_dock_tab_2").performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.runOnIdle { assertEquals(2, pager.settledPage) }
    }

    private fun content(haptic: RecordingHapticFeedback = RecordingHapticFeedback()) {
        rule.setContent {
            pager = rememberPagerState { 3 }
            CompositionLocalProvider(LocalHapticFeedback provides haptic) {
            val navigation = rememberMainTabNavigation(pager)
            Box(Modifier.fillMaxSize()) {
                IntentAwarePager(pager, modifier = Modifier.fillMaxSize().testTag("pager")) { Text("Page $it") }
                BottomDock(listOf("用水", "账单", "设置"), pager.currentPage,
                    onSelected = { page, _ -> navigation.select(page, true) }, onDragStateChanged = {},
                    pagePosition = { pager.currentPage + pager.currentPageOffsetFraction },
                    modifier = Modifier.align(Alignment.BottomCenter))
            }
            }
        }
        rule.mainClock.autoAdvance = false
    }

    @Test fun swipeTicksOnceAndDockClickDoesNotAddAnotherPagerTick() {
        val haptic = RecordingHapticFeedback()
        content(haptic)
        rule.onNodeWithTag("pager").performTouchInput { swipeLeft(durationMillis = 300) }
        rule.mainClock.advanceTimeBy(2000)
        rule.runOnIdle {
            assertEquals(1, pager.settledPage)
            assertEquals(1, haptic.events.size)
        }
        rule.onNodeWithTag("bottom_dock_tab_2").performClick()
        rule.mainClock.advanceTimeBy(2000)
        rule.runOnIdle { assertEquals(2, haptic.events.size) }
        rule.onNodeWithTag("pager").performTouchInput { swipeLeft(durationMillis = 300) }
        rule.mainClock.advanceTimeBy(1000)
        rule.runOnIdle { assertEquals(2, haptic.events.size) }
    }

    @Test fun swipeTicksWhenCurrentPageChangesBeforeFingerRelease() {
        val haptic = RecordingHapticFeedback()
        content(haptic)
        rule.onNodeWithTag("pager").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * .9f, height * .4f))
            moveTo(androidx.compose.ui.geometry.Offset(width * .25f, height * .4f), 300)
        }
        rule.mainClock.advanceTimeBy(32)
        rule.runOnIdle {
            assertEquals(1, pager.currentPage)
            assertTrue(pager.isScrollInProgress)
            assertEquals(1, haptic.events.size)
        }
        rule.onNodeWithTag("pager").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(2000)
        rule.runOnIdle {
            assertEquals(1, pager.settledPage)
            assertEquals(1, haptic.events.size)
        }
    }

    @Test fun rapidReversalsAndRetappingCurrentPageHonorLatestSelection() {
        content()
        listOf(2, 0, 1, 2, 0, 2, 1).forEach { page ->
            rule.onNodeWithTag("bottom_dock_tab_$page").performClick()
            rule.mainClock.advanceTimeBy(64)
        }
        rule.mainClock.advanceTimeBy(2000)
        rule.runOnIdle {
            assertEquals(1, pager.settledPage)
            assertEquals(0f, pager.currentPageOffsetFraction, .001f)
            assertFalse(pager.isScrollInProgress)
        }
        rule.onNodeWithTag("bottom_dock_tab_2").performClick()
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithTag("bottom_dock_tab_1").performClick()
        rule.mainClock.advanceTimeBy(2000)
        rule.runOnIdle { assertEquals(1, pager.settledPage) }
    }

    @Test fun swipeCanInterruptTabAnimationAndNextTabRemainsUsable() {
        content()
        rule.onNodeWithTag("bottom_dock_tab_2").performClick()
        rule.mainClock.advanceTimeBy(160)
        rule.onNodeWithTag("pager").performTouchInput { swipeRight(durationMillis = 240) }
        rule.mainClock.advanceTimeBy(2000)
        rule.runOnIdle { assertFalse(pager.isScrollInProgress) }
        rule.onNodeWithTag("bottom_dock_tab_2").performClick()
        rule.mainClock.advanceTimeBy(2000)
        rule.runOnIdle { assertEquals(2, pager.settledPage) }
    }
}
