package cn.qzxy.hotwater.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.qzxy.hotwater.designsystem.theme.GlassTheme
import cn.qzxy.hotwater.ui.navigation.IntentAwarePager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GestureIntentInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun verticalGestureAfterPageSwipeDoesNotChangePage() {
        lateinit var pager: PagerState
        val scrolls = mutableMapOf<Int, androidx.compose.foundation.ScrollState>()
        rule.setContent {
            pager = rememberPagerState { 3 }
            IntentAwarePager(pager, Modifier.fillMaxSize().testTag("pager")) { index ->
                val scroll = rememberScrollState()
                SideEffect { scrolls[index] = scroll }
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    repeat(60) { Text("第 $index 页 · 内容 $it", Modifier.height(50.dp)) }
                }
            }
        }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("pager").performTouchInput { swipeLeft(durationMillis = 250) }
        rule.mainClock.advanceTimeBy(32)
        // Catch the next touch while the prior horizontal animation may still be running.
        rule.onNodeWithTag("pager").performTouchInput {
            down(Offset(width * .68f, height * .78f))
            moveBy(Offset(-3f, -25f), 24)
            moveBy(Offset(-width * .23f, -height * .40f), 250)
            up()
        }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(1, pager.settledPage)
            assertEquals(0f, pager.currentPageOffsetFraction, .001f)
            assertTrue("Vertical scroll should reach content", scrolls.getValue(1).value > 0)
        }
    }

    @Test fun sheetContentScrollAndBoundaryFlingDoNotDismissSheet() {
        var dismissed = false
        rule.setContent {
            GlassTheme {
                HalfPageSheet("内容", { dismissed = true }) {
                    Column(Modifier.fillMaxSize().testTag("sheet_content").verticalScroll(rememberScrollState())) {
                        repeat(50) { Text("内容 $it", Modifier.height(42.dp)) }
                    }
                }
            }
        }
        rule.onNodeWithTag("sheet_content").performTouchInput { swipeDown(durationMillis = 180) }
        rule.onNodeWithTag("sheet_content").performTouchInput { swipeUp(durationMillis = 180) }
        rule.onNodeWithTag("sheet_content").performTouchInput { swipeDown(durationMillis = 180) }
        rule.waitForIdle()
        rule.runOnIdle { assertFalse("Only the handle may dismiss on a swipe", dismissed) }
        rule.onNodeWithTag("sheet_content").assertIsDisplayed()
    }
}
