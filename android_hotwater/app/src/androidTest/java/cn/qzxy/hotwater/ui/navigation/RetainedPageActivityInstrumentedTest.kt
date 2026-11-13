package cn.qzxy.hotwater.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import cn.qzxy.hotwater.designsystem.motion.LocalPageActivity
import cn.qzxy.hotwater.designsystem.motion.rememberMotionEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RetainedPageActivityInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun hiddenPagesKeepInstancesWithoutDrawsOrContinuousMotionAndBothSidesOfDragStayVisible() {
        lateinit var pager: PagerState
        lateinit var scope: CoroutineScope
        val mounts = IntArray(3)
        val draws = IntArray(3)
        val frames = IntArray(3)
        val active = BooleanArray(3)
        val redraw = mutableIntStateOf(0)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            pager = rememberPagerState { 3 }
            scope = rememberCoroutineScope()
            IntentAwarePager(pager, Modifier.fillMaxSize().testTag("pager")) { page ->
                DisposableEffect(Unit) { mounts[page]++; onDispose {} }
                val visible = LocalPageActivity.current.value
                SideEffect { active[page] = visible }
                if (rememberMotionEnabled()) LaunchedEffect(Unit) {
                    while (isActive) withFrameNanos { frames[page]++ }
                }
                Box(Modifier.fillMaxSize().drawBehind {
                    draws[page]++
                    drawRect(if (redraw.intValue % 2 == 0) Color.White else Color.LightGray)
                }) { Text("页面 $page") }
            }
        }
        rule.mainClock.advanceTimeBy(100)
        fun checkOnlyVisiblePageWorks(visible: Int) {
            val previousDraws = draws.clone()
            val previousFrames = frames.clone()
            repeat(4) {
                rule.runOnIdle { redraw.intValue++ }
                rule.mainClock.advanceTimeBy(64)
                rule.waitForIdle()
            }
            rule.runOnIdle {
                assertTrue(draws[visible] > previousDraws[visible])
                assertTrue(frames[visible] > previousFrames[visible])
                (0..2).filter { it != visible }.forEach { hidden ->
                    assertFalse(active[hidden])
                    assertEquals("Hidden page must not draw", previousDraws[hidden], draws[hidden])
                    assertEquals("Hidden page must not animate", previousFrames[hidden], frames[hidden])
                }
                assertArrayEquals(intArrayOf(1, 1, 1), mounts)
            }
        }
        checkOnlyVisiblePageWorks(0)
        listOf(2, 0).forEach { page ->
            rule.runOnIdle { scope.launch { pager.scrollToPage(page) } }
            rule.mainClock.advanceTimeBy(100)
            rule.waitForIdle()
            checkOnlyVisiblePageWorks(page)
        }
        rule.onNodeWithTag("pager").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * .85f, height * .5f))
            moveTo(androidx.compose.ui.geometry.Offset(width * .45f, height * .5f), 300)
        }
        rule.mainClock.advanceTimeBy(64)
        rule.runOnIdle {
            assertTrue(active[0])
            assertTrue(active[1])
            assertFalse(active[2])
        }
        rule.onNodeWithTag("pager").performTouchInput { up() }
    }
}
