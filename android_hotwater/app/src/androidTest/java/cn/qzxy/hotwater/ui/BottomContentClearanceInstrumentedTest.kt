package cn.qzxy.hotwater.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import cn.qzxy.hotwater.designsystem.theme.GlassTheme
import cn.qzxy.hotwater.designsystem.theme.ThemeMode
import cn.qzxy.hotwater.ui.navigation.BottomDock
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BottomContentClearanceInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun finalContentScrollsCompletelyAboveDockWithBreathingRoom() {
        composeRule.setContent {
            var selectedIndex by remember { mutableIntStateOf(0) }

            GlassTheme(themeMode = ThemeMode.LIGHT) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Spacer(modifier = Modifier.height(1_200.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag(FinalContentTag),
                        )
                        Spacer(
                            modifier = Modifier
                                .height(bottomDockClearance())
                                .testTag(ClearanceTag),
                        )
                    }

                    BottomDock(
                        labels = listOf("操作", "记录", "配置"),
                        selectedIndex = selectedIndex,
                        onSelected = { index, _ -> selectedIndex = index },
                        onDragStateChanged = {},
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                                ),
                            )
                            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(ClearanceTag).performScrollTo()

        val finalContentBounds = composeRule
            .onNodeWithTag(FinalContentTag)
            .fetchSemanticsNode()
            .boundsInRoot
        val dockBounds = composeRule
            .onNodeWithTag("bottom_dock")
            .fetchSemanticsNode()
            .boundsInRoot
        val breathingRoomPx = with(composeRule.density) { 4.dp.toPx() }

        assertTrue(
            "Final content bottom ${finalContentBounds.bottom} must stay at least 4dp " +
                "above dock top ${dockBounds.top}",
            finalContentBounds.bottom <= dockBounds.top - breathingRoomPx + 1f,
        )
    }

    private companion object {
        const val FinalContentTag = "final_scroll_content"
        const val ClearanceTag = "bottom_dock_clearance"
    }
}
