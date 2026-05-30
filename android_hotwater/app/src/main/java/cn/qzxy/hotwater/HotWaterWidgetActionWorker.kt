package cn.qzxy.hotwater

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

enum class WidgetHotWaterAction {
    OPEN,
    CLOSE,
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
            }.also { result ->
                val deviceBusyByOther = result.snapshot?.isDeviceBusyByOther == true
                store.saveWidgetState(
                    title = if (deviceBusyByOther) {
                        "设备正在使用中"
                    } else when (action) {
                        WidgetHotWaterAction.OPEN -> "开热水完成"
                        WidgetHotWaterAction.CLOSE -> "关热水完成"
                    },
                    message = HotWaterUiText.summarizeWidgetResult(action, result),
                    isBusy = false,
                    tone = if (deviceBusyByOther) "warning" else "success",
                )
                HotWaterWidgetUi.updateAllWidgets(applicationContext)
                if (!store.loadOrderNo().isNullOrBlank()) {
                    HotWaterNotificationService.show(applicationContext)
                } else {
                    HotWaterNotificationService.dismiss(applicationContext)
                }
            }
            Result.success()
        } catch (error: Exception) {
            store.saveWidgetState(
                title = when (action) {
                    WidgetHotWaterAction.OPEN -> "开热水失败"
                    WidgetHotWaterAction.CLOSE -> "关热水失败"
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
    }
}
