package cn.qzxy.hotwater

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

class HotWaterWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        HotWaterWidgetUi.updateAllWidgets(context)
        requestStateRefresh(context)
    }

    override fun onEnabled(context: Context) {
        HotWaterWidgetUi.updateAllWidgets(context)
        requestStateRefresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        HotWaterWidgetUi.updateAllWidgets(context)
        requestStateRefresh(context)
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
        if (state.isBusy) {
            store.saveWidgetState(
                title = "请求进行中",
                message = "上一个操作还没完成，请稍等。",
                isBusy = true,
                tone = "busy",
                expectedStateGeneration = stateGeneration,
            )
            HotWaterWidgetUi.updateAllWidgets(context)
            return
        }

        store.saveWidgetState(
            title = "处理中",
            message = when (action) {
                WidgetHotWaterAction.OPEN -> "正在开启热水…"
                WidgetHotWaterAction.CLOSE -> "正在关闭热水…"
                WidgetHotWaterAction.REFRESH -> "正在同步余额和热水器状态…"
            },
            isBusy = true,
            tone = "busy",
            expectedStateGeneration = stateGeneration,
        )
        HotWaterWidgetUi.updateAllWidgets(context)

        val inputData = Data.Builder()
            .putString(HotWaterWidgetActionWorker.KEY_ACTION, action.name)
            .build()

        val workRequest = OneTimeWorkRequest.Builder(HotWaterWidgetActionWorker::class.java)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            workRequest,
        )
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "hotwater_widget_action"
        private const val WORK_TAG = "hotwater_widget"

        fun requestStateRefresh(context: Context) {
            val store = HotWaterConfigStore(context)
            if (!store.loadConfig().isComplete() || store.loadWidgetState().isBusy) return
            val workRequest = OneTimeWorkRequest.Builder(HotWaterWidgetActionWorker::class.java)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(Data.Builder().putString(HotWaterWidgetActionWorker.KEY_ACTION, WidgetHotWaterAction.REFRESH.name).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, workRequest,
            )
        }
        fun cancelPendingActions(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
