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
                    assertEquals("处理中", view.findViewById<TextView>(R.id.widget_open_button).text.toString())
                    assertTrue(view.findViewById<TextView>(R.id.widget_close_button).isEnabled)
                    store.saveWidgetState("已就绪", "可以使用", false, "success")
                    HotWaterWidgetUi.buildRemoteViews(context, manager, 0, size).reapply(context, view)
                    assertEquals("开启热水", view.findViewById<TextView>(R.id.widget_open_button).text.toString())
                }
            }
        } finally { store.clearAll() }
    }
}
