package cn.qzxy.hotwater

import org.junit.Assert.*
import org.junit.Test

class WidgetArtworkSizeTest {
    @Test fun threeByTwoUsesClassicLayoutEvenOnNarrowGrids() {
        listOf(205f to 140f, 270f to 180f, 400f to 280f).forEach { (w, h) ->
            assertTrue(usesWideTwoRowWidget(w, h))
            assertFalse(usesDashboardWidget(w, h, 800))
        }
        assertFalse(usesWideTwoRowWidget(180f, 76f))
        assertFalse(usesWideTwoRowWidget(180f, 180f))
    }
    @Test fun narrowTwoByThreeNeverUsesDashboardOrHorizontalControls() {
        listOf(140f to 205f, 180f to 270f, 280f to 420f).forEach { (width, height) ->
            assertTrue(usesTallWidget(width, height))
            assertFalse(usesDashboardWidget(width, height, 800))
        }
        assertFalse(usesTallWidget(180f, 180f))
        assertFalse(usesTallWidget(280f, 90f))
    }
    @Test fun twoByTwoUsesDashboardButLargerBoundsKeepOriginalLayout() {
        assertTrue(usesDashboardWidget(180f, 180f, 411))
        assertFalse(usesDashboardWidget(260f, 180f, 411))
        assertFalse(usesDashboardWidget(180f, 260f, 411))
        assertTrue(usesDashboardWidget(280f, 280f, 800))
        assertFalse(usesDashboardWidget(400f, 280f, 800))
        listOf(260f to 64f, 180f to 260f, 260f to 180f, 320f to 320f, 380f to 64f).forEach { (w, h) ->
            assertFalse("Larger grid $w × $h must never use a switch", usesDashboardWidget(w, h, 411))
        }
    }
}
