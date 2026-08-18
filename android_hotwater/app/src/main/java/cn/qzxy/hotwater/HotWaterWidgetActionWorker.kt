package cn.qzxy.hotwater

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException

enum class WidgetHotWaterAction {
    OPEN,
    CLOSE,
    REFRESH,
}

class HotWaterWidgetActionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val store = HotWaterConfigStore(applicationContext)
        val config = RuntimeDeviceInfoResolver.enrichConfig(applicationContext, store.loadConfig())
        val actionName = inputData.getString(KEY_ACTION)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Missing widget action"))

        val action = runCatching { WidgetHotWaterAction.valueOf(actionName) }.getOrElse {
            store.saveWidgetState("操作失败", "无法识别的小组件动作。", false, "error")
            HotWaterWidgetUi.updateAllWidgets(applicationContext)
            return Result.failure(workDataOf(KEY_ERROR to "Invalid widget action"))
        }

        if (!config.isComplete()) {
            store.saveWidgetState(
                title = "配置未完成",
                message = "先打开应用填写配置参数。",
                isBusy = false,
                tone = "warning",
            )
            HotWaterWidgetUi.updateAllWidgets(applicationContext)
            return Result.failure(workDataOf(KEY_ERROR to "Config incomplete"))
        }

        val repository = HotWaterRepository(HotWaterApi(), store)
        return try {
            when (action) {
                WidgetHotWaterAction.OPEN -> repository.open(config)
                WidgetHotWaterAction.CLOSE -> repository.close(config)
                WidgetHotWaterAction.REFRESH -> repository.check(config)
            }.also { result ->
                when (action) {
                    WidgetHotWaterAction.OPEN -> store.saveOperationResultHistory(
                        type = OperationHistory.OperationType.OPEN,
                        result = result,
                    )

                    WidgetHotWaterAction.CLOSE -> store.saveOperationResultHistory(
                        type = OperationHistory.OperationType.CLOSE,
                        result = result,
                    )

                    WidgetHotWaterAction.REFRESH -> Unit
                }
                if (action == WidgetHotWaterAction.CLOSE && result.historyId != null) {
                    HotWaterSettlementSyncScheduler.enqueue(applicationContext)
                }
                val deviceBusyByOther = result.snapshot?.isDeviceBusyByOther == true
                store.saveWidgetState(
                    title = if (deviceBusyByOther) {
                        "设备正在使用中"
                    } else {
                        result.title
                    },
                    message = if (action == WidgetHotWaterAction.REFRESH) {
                        result.summary.replace('\n', ' ').replace("  ", " ")
                    } else {
                        HotWaterUiText.summarizeWidgetResult(action, result)
                    },
                    isBusy = false,
                    tone = if (
                        deviceBusyByOther || result.outcome != OperationOutcome.SUCCESS
                    ) {
                        "warning"
                    } else {
                        "success"
                    },
                )
                HotWaterWidgetUi.updateAllWidgets(applicationContext)
                if (!store.loadOrderNo().isNullOrBlank()) {
                    HotWaterNotificationService.show(applicationContext)
                } else {
                    HotWaterNotificationService.dismiss(applicationContext)
                }
                refreshTile(applicationContext)
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            store.saveWidgetState(
                title = when (action) {
                    WidgetHotWaterAction.OPEN -> "开热水失败"
                    WidgetHotWaterAction.CLOSE -> "关热水失败"
                    WidgetHotWaterAction.REFRESH -> "刷新失败"
                },
                message = HotWaterUiText.summarizeError(error),
                isBusy = false,
                tone = "error",
            )
            HotWaterWidgetUi.updateAllWidgets(applicationContext)
            Result.failure(workDataOf(KEY_ERROR to (error.message ?: "Widget action failed")))
        }
    }

    companion object {
        const val KEY_ACTION = "widget_action"
        const val KEY_ERROR = "widget_error"

        private fun refreshTile(context: Context) {
            TileService.requestListeningState(
                context,
                ComponentName(context, HotWaterTileService::class.java),
            )
        }
    }
}
