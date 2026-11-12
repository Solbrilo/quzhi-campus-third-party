package cn.qzxy.hotwater

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class WidgetStateInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    // Isolated encrypted preferences; never touch a developer or user's saved account.
    private val context = object : ContextWrapper(instrumentation.targetContext) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences("widget_regression_" + name, mode)
    }

    @Test fun largerWidgetsKeepStatusAtTopLeftAndActionsAtBottom() {
        val manager = AppWidgetManager.getInstance(context)
        val density = context.resources.displayMetrics.density
        instrumentation.runOnMainSync {
            listOf(140 to 205, 180 to 270, 280 to 420, 205 to 140,
                270 to 180, 270 to 270, 400 to 280, 400 to 400).forEach { (width, height) ->
                val layout = HotWaterWidgetUi.widgetLayoutSize(context, width, height)
                assertEquals(HotWaterWidgetUi.WidgetLayoutSize.TALL, layout)
                val view = HotWaterWidgetUi.buildRemoteViews(context, manager, 0, layout, height).apply(context, FrameLayout(context))
                val w = (width * density).toInt(); val h = (height * density).toInt()
                view.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
                view.layout(0, 0, w, h)
                val status = view.findViewById<android.view.View>(R.id.widget_expanded_status_dot)
                val open = view.findViewById<android.view.View>(R.id.widget_open_button)
                val close = view.findViewById<android.view.View>(R.id.widget_close_button)
                fun bounds(child: android.view.View) = android.graphics.Rect(0, 0, child.width, child.height).also {
                    (view as android.view.ViewGroup).offsetDescendantRectToMyCoords(child, it)
                }
                val statusBounds = bounds(status); val openBounds = bounds(open); val closeBounds = bounds(close)
                assertTrue(statusBounds.left < w / 4)
                assertTrue(statusBounds.top < h / 4)
                assertTrue(openBounds.top > h / 2)
                assertEquals(openBounds.top, closeBounds.top)
                assertTrue(kotlin.math.abs(h - (12 * density).toInt() - openBounds.bottom) <= 2)
                val titleBounds = bounds(view.findViewById(R.id.widget_expanded_title))
                assertTrue(titleBounds.top >= statusBounds.bottom)
                assertTrue(titleBounds.bottom <= openBounds.top)
                if (width == 140) {
                    val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                    view.draw(android.graphics.Canvas(bitmap))
                    java.io.File(context.cacheDir, "widget-tall-review.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        }
    }

    @Test fun olderRequestsAndBackgroundRefreshCannotOverwriteActiveCommand() {
        val store = HotWaterConfigStore(context)
        store.clearAll()
        try {
            val generation = store.loadStateGeneration()
            assertTrue(store.saveWidgetState("处理中", "正在关水", true, "busy", generation, requestId = "new"))
            assertFalse(store.saveWidgetState("旧请求失败", "", false, "error", generation, expectedRequestId = "old"))
            assertFalse(store.saveWidgetState("后台刷新", "", false, "success", generation, skipIfBusy = true))
            assertEquals("new", store.loadWidgetState().requestId)
            assertTrue(store.saveWidgetState("已关闭", "", false, "success", generation, expectedRequestId = "new"))
            assertFalse(store.saveWidgetState("中断清理", "", false, "warning", generation, expectedRequestId = "new"))
            assertEquals("已关闭", store.loadWidgetState().title)
            store.clearActiveUsageState()
            assertFalse(store.saveWidgetState("旧设备", "", false, "error", generation))
            assertEquals("", store.loadWidgetState().title)
        } finally { store.clearAll() }
    }

    @Test fun allWidgetSizesApplyAndReapplyRemoteViewsWithoutLauncherErrors() {
        val store = HotWaterConfigStore(context)
        store.clearAll()
        try {
            store.saveConfig(HotWaterConfig(projectId = 1, userId = 2, accountId = 3,
                telephone = "13800138000", loginCode = "fixture-only", snCode = "AABBCCDDEEFF",
                phoneSystem = "android", version = "1.1.11"))
            val manager = AppWidgetManager.getInstance(context)
            instrumentation.runOnMainSync {
                HotWaterWidgetUi.WidgetLayoutSize.entries.forEach { size ->
                    store.saveWidgetState("处理中", "正在同步", true, "busy", requestId = "fixture")
                    val views = HotWaterWidgetUi.buildRemoteViews(context, manager, 0, size)
                    val view = views.apply(context, FrameLayout(context))
                    if (size != HotWaterWidgetUi.WidgetLayoutSize.DASHBOARD) {
                        assertEquals("处理中", view.findViewById<TextView>(R.id.widget_open_button).text.toString())
                        assertTrue(view.findViewById<TextView>(R.id.widget_close_button).isEnabled)
                    } else {
                        assertNotNull(view.findViewById<android.widget.ViewFlipper>(R.id.widget_thumb_off))
                        assertEquals("开启热水", view.findViewById<TextView>(R.id.widget_switch_right).contentDescription)
                    }
                    store.saveWidgetState("已就绪", "可以使用", false, "success")
                    HotWaterWidgetUi.buildRemoteViews(context, manager, 0, size).reapply(context, view)
                    if (size != HotWaterWidgetUi.WidgetLayoutSize.DASHBOARD) {
                        assertEquals("开启热水", view.findViewById<TextView>(R.id.widget_open_button).text.toString())
                    } else {
                        assertNotNull(view.findViewById<android.widget.ViewFlipper>(R.id.widget_thumb_off))
                    }
                }
            }
        } finally { store.clearAll() }
    }
    @Test fun sliderAppliesBothDirectionsAndBusyStateWithoutChangingConfirmedPosition() {
        val store = HotWaterConfigStore(context)
        store.clearAll()
        try {
            store.saveConfig(HotWaterConfig(projectId = 1, userId = 2, accountId = 3,
                telephone = "13800138000", loginCode = "fixture-only", snCode = "AABBCCDDEEFF",
                phoneSystem = "android", version = "1.1.11"))
            instrumentation.runOnMainSync {
                val parent = FrameLayout(context)
                val off = HotWaterWidgetUi.buildSwitchRemoteViews(context, 0).apply(context, parent)
                assertEquals("开启热水", off.findViewById<TextView>(R.id.widget_switch_right).contentDescription)
                assertTrue(off.contentDescription.endsWith("热水已关"))
                store.saveWidgetState("处理中", "正在开启", true, "busy", requestId = "fixture")
                HotWaterWidgetUi.buildSwitchRemoteViews(context, 0).reapply(context, off)
                assertTrue(off.contentDescription.endsWith("热水已关"))
                store.saveOrderNo("fixture-order")
                store.saveWidgetState("已开启", "", false, "success")
                val artwork = off.findViewById<android.widget.ViewFlipper>(R.id.widget_thumb_on)
                HotWaterWidgetUi.buildSwitchRemoteViews(context, 0, animate = true).reapply(context, off)
                assertSame(artwork, off.findViewById(R.id.widget_thumb_on))
                assertEquals("关闭热水", off.findViewById<TextView>(R.id.widget_switch_left).contentDescription)
                assertTrue(off.contentDescription.endsWith("热水已开"))
                assertNotNull(artwork.currentView.animation)
                assertEquals(340L, artwork.currentView.animation.duration)
                HotWaterWidgetUi.buildSwitchRemoteViews(context, 0).reapply(context, off)
                assertSame(artwork, off.findViewById(R.id.widget_thumb_on))
            }
        } finally { store.clearAll() }
    }

    @Test fun cachedBalanceSurvivesTemporaryFailureButNeverCrossesAccountGeneration() {
        val store = HotWaterConfigStore(context)
        store.clearAll()
        try {
            val generation = store.loadStateGeneration()
            val snapshot = DashboardSnapshot(26.8, "26.80", "", "", "", "", null, false, isDeviceOnline = true)
            assertTrue(store.saveWidgetSnapshot(snapshot, generation))
            assertEquals("26.80", store.loadWidgetBalance())
            assertTrue(store.saveWidgetSnapshot(snapshot.copy(balanceYuan = null, balanceLabel = "--", isDeviceOnline = false), generation))
            assertEquals("26.80", store.loadWidgetBalance())
            assertFalse(store.loadWidgetOnline())
            store.clearActiveUsageState()
            assertFalse(store.saveWidgetSnapshot(snapshot, generation))
            assertEquals("--", store.loadWidgetBalance())
            assertFalse(store.loadWidgetOnline())
        } finally { store.clearAll() }
    }

}
