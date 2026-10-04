package cn.qzxy.hotwater

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

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
        val stateGeneration = store.loadStateGeneration()
        val background = inputData.getBoolean(KEY_BACKGROUND, false)
        val requestId = id.toString()
        val queuedGeneration = inputData.getLong(KEY_GENERATION, -1L)
        val validRequest = queuedGeneration == stateGeneration && (background ||
            WidgetActionPolicy.canExecute(queuedGeneration, stateGeneration,
                inputData.getLong(KEY_QUEUED_AT, 0L), System.currentTimeMillis()))
        if (!validRequest) {
            if (!background) store.saveWidgetState(
                "请重新操作", "上次请求已过期，请刷新后重试。", false, "warning",
                expectedStateGeneration = stateGeneration, expectedRequestId = requestId,
            )
            HotWaterWidgetUi.updateAllWidgets(applicationContext)
            return Result.success()
        }
        if (background && WidgetActionPolicy.isBusy(store.loadWidgetState())) return Result.success()
        if (!background && store.loadWidgetState().requestId != requestId) return Result.success()
        val config = RuntimeDeviceInfoResolver.enrichConfig(applicationContext, store.loadConfig())
        val actionName = inputData.getString(KEY_ACTION)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Missing widget action"))

        val action = runCatching { WidgetHotWaterAction.valueOf(actionName) }.getOrElse {
            store.saveWidgetState(
                "操作失败",
                "无法识别的小组件动作。",
                false,
                "error",
                expectedStateGeneration = stateGeneration,
                expectedRequestId = if (background) null else requestId, skipIfBusy = background,
            )
            HotWaterWidgetUi.updateAllWidgets(applicationContext)
            return Result.failure(workDataOf(KEY_ERROR to "Invalid widget action"))
        }

        if (!config.isComplete()) {
            store.saveWidgetState(
                title = "配置未完成",
                message = "先打开应用填写配置参数。",
                isBusy = false,
                tone = "warning",
                expectedStateGeneration = stateGeneration,
                expectedRequestId = if (background) null else requestId, skipIfBusy = background,
            )
            HotWaterWidgetUi.updateAllWidgets(applicationContext)
            return Result.failure(workDataOf(KEY_ERROR to "Config incomplete"))
        }

        val repository = HotWaterRepository(HotWaterApi(), store, expectedStateGeneration = stateGeneration)
        return try {
            withTimeout(WidgetActionPolicy.OPERATION_TIMEOUT_MS) {
                when (action) {
                    WidgetHotWaterAction.OPEN -> repository.open(config)
                    WidgetHotWaterAction.CLOSE -> repository.close(config)
                    WidgetHotWaterAction.REFRESH -> repository.check(config)
                }
            }.also { result ->
                if (store.loadStateGeneration() != stateGeneration) return@also
                when (action) {
                    WidgetHotWaterAction.OPEN -> store.saveOperationResultHistory(
                        type = OperationHistory.OperationType.OPEN,
                        result = result,
                        expectedStateGeneration = stateGeneration,
                    )

                    WidgetHotWaterAction.CLOSE -> store.saveOperationResultHistory(
                        type = OperationHistory.OperationType.CLOSE,
                        result = result,
                        expectedStateGeneration = stateGeneration,
                    )

                    WidgetHotWaterAction.REFRESH -> if (result.automaticClose) {
                        store.saveOperationResultHistory(
                            type = OperationHistory.OperationType.CLOSE,
                            result = result,
                            expectedStateGeneration = stateGeneration,
                        )
                    }
                }
                if ((action == WidgetHotWaterAction.CLOSE || result.automaticClose) &&
                    result.historyId != null) {
                    runCatching { HotWaterSettlementSyncScheduler.enqueue(applicationContext) }
                }
                if (store.loadStateGeneration() != stateGeneration) return@also
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
                        deviceBusyByOther || result.outcome != OperationOutcome.SUCCESS || result.automaticClose
                    ) {
                        "warning"
                    } else {
                        "success"
                    },
                    expectedStateGeneration = stateGeneration,
                    expectedRequestId = if (background) null else requestId, skipIfBusy = background,
                )
                HotWaterWidgetUi.updateAllWidgets(applicationContext)
                // System surfaces must not turn a successful device operation into a failure.
                runCatching {
                    if (!store.loadOrderNo().isNullOrBlank()) {
                        HotWaterNotificationService.show(applicationContext)
                    } else {
                        HotWaterNotificationService.dismiss(applicationContext)
                    }
                }
                runCatching { refreshTile(applicationContext) }
            }
            Result.success()
        } catch (timeout: TimeoutCancellationException) {
            if (!background) store.saveWidgetState(
                "等待确认", "操作确认超时，请刷新查看热水器状态。", false, "warning",
                expectedStateGeneration = stateGeneration, expectedRequestId = requestId,
            )
            Result.failure()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: CredentialExpiredException) {
            if (store.loadStateGeneration() == stateGeneration && store.markCredentialsExpired(config.loginCode)) {
                store.saveWidgetState(
                    "请重新登录", CREDENTIAL_EXPIRED_MESSAGE, false, "warning",
                    expectedStateGeneration = stateGeneration,
                )
            }
            Result.failure()
        } catch (error: Exception) {
            if (!background) store.saveWidgetState(
                title = when (action) {
                    WidgetHotWaterAction.OPEN -> "开热水失败"
                    WidgetHotWaterAction.CLOSE -> "关热水失败"
                    WidgetHotWaterAction.REFRESH -> "刷新失败"
                },
                message = HotWaterUiText.summarizeError(error),
                isBusy = false,
                tone = "error",
                expectedStateGeneration = stateGeneration,
                expectedRequestId = requestId,
            )
            HotWaterWidgetUi.updateAllWidgets(applicationContext)
            Result.failure(workDataOf(KEY_ERROR to (error.message ?: "Widget action failed")))
        } finally {
            if (!background) store.saveWidgetState(
                "请刷新状态", "上次操作已中断，请确认热水器状态。", false, "warning",
                expectedStateGeneration = stateGeneration, expectedRequestId = requestId,
            )
            HotWaterWidgetUi.updateAllWidgets(applicationContext)
        }
    }

    companion object {
        const val KEY_ACTION = "widget_action"
        const val KEY_ERROR = "widget_error"
        const val KEY_GENERATION = "widget_generation"
        const val KEY_QUEUED_AT = "widget_queued_at"
        const val KEY_BACKGROUND = "widget_background"

        private fun refreshTile(context: Context) {
            TileService.requestListeningState(
                context,
                ComponentName(context, HotWaterTileService::class.java),
            )
        }
    }
}
