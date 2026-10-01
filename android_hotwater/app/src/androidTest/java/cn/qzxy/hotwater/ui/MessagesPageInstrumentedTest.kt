package cn.qzxy.hotwater.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import cn.qzxy.hotwater.ServerMessage
import cn.qzxy.hotwater.ServerMessagesState
import cn.qzxy.hotwater.designsystem.theme.GlassTheme
import cn.qzxy.hotwater.designsystem.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MessagesPageInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun detailSlidesRightOnBackAndRestoresListPosition() {
        val selected = mutableStateOf<String?>(null)
        val messages = (1..30).map { index ->
            ServerMessage(
                id = index.toString(), title = "通知 $index",
                publishedAt = "2026-10-01", summaryHtml = "消息摘要",
                contentHtml = "<p>消息正文</p>",
            )
        }
        composeRule.setContent {
            GlassTheme(themeMode = ThemeMode.LIGHT, glassIntensity = 0f) {
                MessagesPage(
                    state = ServerMessagesState(messages = messages, loggedIn = true),
                    selectedRevision = selected.value,
                    onSelect = { selected.value = it.revision },
                    onRefresh = {}, onMarkAllRead = {}, onLogin = {},
                    onBack = { selected.value = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(19)
        composeRule.onNodeWithText("通知 20").assertIsDisplayed()
        val listItemTop = composeRule.onNodeWithText("通知 20")
            .fetchSemanticsNode().boundsInRoot.top
        val listHeaderLeft = composeRule.onNodeWithText("消息")
            .fetchSemanticsNode().boundsInRoot.left
        composeRule.onNodeWithText("通知 20").performClick()
        composeRule.onNodeWithText("消息详情").assertIsDisplayed()
        val detailHeaderLeft = composeRule.onNodeWithText("消息详情")
            .fetchSemanticsNode().boundsInRoot.left

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.mainClock.advanceTimeBy(120)
        assertTrue(composeRule.onNodeWithText("消息详情")
            .fetchSemanticsNode().boundsInRoot.left > detailHeaderLeft)
        assertEquals(listHeaderLeft, composeRule.onNodeWithText("消息")
            .fetchSemanticsNode().boundsInRoot.left, 1f)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("通知 20").assertIsDisplayed()
        assertEquals(listItemTop, composeRule.onNodeWithText("通知 20")
            .fetchSemanticsNode().boundsInRoot.top, 1f)
    }
}
