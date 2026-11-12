package cn.qzxy.hotwater.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cn.qzxy.hotwater.designsystem.theme.GlassTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ScrollCardInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun pinnedTitleStaysAtSameCoordinateAcrossFastScrollJumps() {
        lateinit var scroll: androidx.compose.foundation.ScrollState
        lateinit var scope: CoroutineScope
        rule.setContent {
            scroll = rememberScrollState()
            scope = rememberCoroutineScope()
            GlassTheme {
                ScrollViewport(Modifier.fillMaxWidth().height(360.dp).testTag("viewport")) {
                    Column(Modifier.fillMaxSize().cardVerticalScroll(scroll)) {
                        CollapsingGlassCard(header = { Text("固定标题", Modifier.testTag("title")) }) {
                            Spacer(Modifier.height(1200.dp))
                        }
                        Spacer(Modifier.height(400.dp))
                    }
                }
            }
        }
        val initialTop = rule.onNodeWithTag("title").fetchSemanticsNode().boundsInRoot.top
        listOf(90, 560, 180, 780, 290).forEach { offset ->
            rule.runOnIdle { scope.launch { scroll.scrollTo(offset) } }
            rule.waitForIdle()
            assertEquals(initialTop, rule.onNodeWithTag("title").fetchSemanticsNode().boundsInRoot.top, 1f)
        }
    }
}
