package cn.qzxy.hotwater

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

open class HotWaterWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        HotWaterWidgetUi.updateAllWidgets(context, forceIds = appWidgetIds.toSet())
        requestStateRefresh(context)
    }

    override fun onEnabled(context: Context) {
        requestStateRefresh(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        context.getSharedPreferences("hotwater_widget_presentation", Context.MODE_PRIVATE).edit().apply {
            appWidgetIds.forEach { remove("content_$it"); remove("layout_$it"); remove("opened_$it"); remove("generation_$it") }
        }.apply()
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        HotWaterWidgetUi.updateAllWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            HotWaterWidgetUi.ACTION_OPEN -> {
                enqueueAction(context, WidgetHotWaterAction.OPEN)
                return
            }

            HotWaterWidgetUi.ACTION_CLOSE -> {
                enqueueAction(context, WidgetHotWaterAction.CLOSE)
                return
            }

            HotWaterWidgetUi.ACTION_REFRESH -> {
                enqueueAction(context, WidgetHotWaterAction.REFRESH)
                return
            }
        }

        super.onReceive(context, intent)
    }

    private fun enqueueAction(context: Context, action: WidgetHotWaterAction) {
        val store = HotWaterConfigStore(context)
        val stateGeneration = store.loadStateGeneration()
        val state = store.loadWidgetState()
        if (WidgetActionPolicy.isBusy(state)) {
            HotWaterWidgetUi.updateAllWidgets(context)
            return
        }
        val inputData = Data.Builder()
            .putString(HotWaterWidgetActionWorker.KEY_ACTION, action.name)
            .putLong(HotWaterWidgetActionWorker.KEY_GENERATION, stateGeneration)
            .putLong(HotWaterWidgetActionWorker.KEY_QUEUED_AT, System.currentTimeMillis())
            .build()
        // User commands must fail promptly offline, never silently run hours later.
        val workRequest = OneTimeWorkRequest.Builder(HotWaterWidgetActionWorker::class.java)
            .setInputData(inputData)
            .apply {
                // Pre-S expedited work requires a foreground service; ordinary work is safer here.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                }
            }
            .addTag(WORK_TAG)
            .build()

        val saved = store.saveWidgetState(
            title = "处理中",
            message = when (action) {
                WidgetHotWaterAction.OPEN -> "正在开启热水…"
                WidgetHotWaterAction.CLOSE -> "正在关闭热水…"
                WidgetHotWaterAction.REFRESH -> "正在同步余额和热水器状态…"
            },
            isBusy = true,
            tone = "busy",
            expectedStateGeneration = stateGeneration,
            requestId = workRequest.id.toString(),
            skipIfBusy = true,
        )
        if (!saved) return
        HotWaterWidgetUi.updateAllWidgets(context)
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(REFRESH_WORK_NAME)
        runCatching {
            manager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
        }.onFailure {
            store.saveWidgetState(
                "请重试", "暂时无法开始操作，请稍后再试。", false, "warning",
                expectedStateGeneration = stateGeneration, expectedRequestId = workRequest.id.toString(),
            )
            HotWaterWidgetUi.updateAllWidgets(context)
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "hotwater_widget_action"
        private const val REFRESH_WORK_NAME = "hotwater_widget_refresh"
        private const val WORK_TAG = "hotwater_widget"

        fun requestStateRefresh(context: Context) {
            val refreshPrefs = context.getSharedPreferences("hotwater_widget_presentation", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (now - refreshPrefs.getLong("last_refresh_request", 0L) in 0 until 60_000L) return
            val store = HotWaterConfigStore(context)
            if (!store.loadConfig().isComplete() || WidgetActionPolicy.isBusy(store.loadWidgetState())) return
            refreshPrefs.edit().putLong("last_refresh_request", now).apply()
            val workRequest = OneTimeWorkRequest.Builder(HotWaterWidgetActionWorker::class.java)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(Data.Builder()
                    .putString(HotWaterWidgetActionWorker.KEY_ACTION, WidgetHotWaterAction.REFRESH.name)
                    .putLong(HotWaterWidgetActionWorker.KEY_GENERATION, store.loadStateGeneration())
                    .putBoolean(HotWaterWidgetActionWorker.KEY_BACKGROUND, true)
                    .build())
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                REFRESH_WORK_NAME, ExistingWorkPolicy.KEEP, workRequest,
            )
        }
        fun cancelPendingActions(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(REFRESH_WORK_NAME)
        }
    }
}

/** Shares the same guarded action queue and persisted water state. */
class HotWaterSwitchWidgetProvider : HotWaterWidgetProvider()
