package cn.qzxy.hotwater.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import cn.qzxy.hotwater.BillQuery
import cn.qzxy.hotwater.ServerBill
import cn.qzxy.hotwater.ServerBillsState
import cn.qzxy.hotwater.designsystem.theme.GlassTheme
import cn.qzxy.hotwater.designsystem.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth
import kotlin.math.abs

class BillFilterScrollInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun filterRendersAgainAfterScrollingAwayAndBack() {
        val month = YearMonth.now()
        val bills = (1..20).map { ServerBill("bill-$it", "$it", null, "$month-01 10:00:00",
            80, "热水器 $it", null, month) }
        rule.setContent {
            GlassTheme(themeMode = ThemeMode.LIGHT, glassIntensity = 0f) {
                ScrollViewport(Modifier.fillMaxSize()) {
                    RecordsPage(ServerBillsState(BillQuery(month.year, month.monthValue), bills, loaded = true),
                        "fixture", onLoad = { _, _ -> })
                }
            }
        }
        rule.onNodeWithTag("bill_list").performScrollToIndex(1)
        val before = rule.onNodeWithTag("bill_filters").captureToImage().toPixelMap()
        rule.onNodeWithTag("bill_list").performScrollToIndex(3)
        rule.onNodeWithTag("bill_list").performScrollToIndex(1)
        val after = rule.onNodeWithTag("bill_filters").captureToImage().toPixelMap()
        var difference = 0f
        var samples = 0
        for (y in 0 until before.height step 3) for (x in 0 until before.width step 3) {
            val a = before[x, y]; val b = after[x, y]
            difference += abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
            samples++
        }
        assertTrue("Filter must remain painted after scrolling back", difference / (samples * 3) < .025f)
    }
}
