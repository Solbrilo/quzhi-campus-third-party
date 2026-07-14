package cn.qzxy.hotwater

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FeedbackEvent(
    val type: FeedbackType,
    val message: String,
    val id: Long = System.nanoTime(),
) {
    enum class FeedbackType {
        SUCCESS,
        ERROR,
        WARNING,
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = HotWaterConfigStore(application)
    private val repository = HotWaterRepository(HotWaterApi(), store)

    private val _snapshot = MutableStateFlow<DashboardSnapshot?>(null)
    val snapshot: StateFlow<DashboardSnapshot?> = _snapshot.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _config = MutableStateFlow(store.loadConfig())
    val config: StateFlow<HotWaterConfig> = _config.asStateFlow()

    private val _history = MutableStateFlow(store.loadHistory())
    val history: StateFlow<List<OperationHistory>> = _history.asStateFlow()

    private val _feedbackEvent = MutableStateFlow<FeedbackEvent?>(null)
    val feedbackEvent: StateFlow<FeedbackEvent?> = _feedbackEvent.asStateFlow()
    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true
        val preparedConfig = persistRuntimeInfoIfNeeded(store.loadConfig())
        _config.value = preparedConfig
        _snapshot.value = buildPlaceholderSnapshot(preparedConfig)
        syncNotificationState()

        if (preparedConfig.isComplete()) {
            refreshBalance(announce = false)
        } else {
            _statusMessage.value = "请先到配置页填写参数。"
        }
    }

    fun refreshBalance(announce: Boolean = true) {
        runOperation(
            busyMessage = if (announce) "正在刷新余额…" else "正在同步余额…",
            operationType = OperationHistory.OperationType.REFRESH,
            announceSuccess = announce,
        ) { config ->
            repository.check(config)
        }
    }

    fun toggle() {
        val shouldClose = _snapshot.value?.hasCachedOrder == true || !store.loadOrderNo().isNullOrBlank()
        val operationType = if (shouldClose) OperationHistory.OperationType.CLOSE else OperationHistory.OperationType.OPEN
        runOperation(
            busyMessage = if (shouldClose) "正在关闭热水…" else "正在开启热水…",
            operationType = operationType,
            announceSuccess = true,
        ) { config ->
            if (shouldClose) repository.close(config) else repository.open(config)
        }
    }

    fun shouldShowLowBalanceOpenWarning(): Boolean {
        if (_snapshot.value?.hasCachedOrder == true || !store.loadOrderNo().isNullOrBlank()) {
            return false
        }
        val snapshot = _snapshot.value ?: return false
        return snapshot.balanceLabel != "--" && snapshot.balanceYuan < LOW_BALANCE_WARNING_YUAN
    }

    fun clearFeedbackEvent() {
        _feedbackEvent.value = null
    }

    fun saveConfig(rawConfig: HotWaterConfig) {
        val prepared = persistRuntimeInfoIfNeeded(rawConfig)
        store.saveConfig(prepared)
        _config.value = prepared
        _snapshot.value = buildPlaceholderSnapshot(prepared)
        store.saveWidgetState(
            title = "配置已就绪",
            message = "桌面小组件和主按钮现在都可以直接使用。",
            isBusy = false,
        )
        HotWaterWidgetUi.updateAllWidgets(getApplication())
        _statusMessage.value = "配置已保存，正在同步关键数据…"
        _feedbackEvent.value = FeedbackEvent(
            type = FeedbackEvent.FeedbackType.SUCCESS,
            message = "配置已保存",
        )
        if (prepared.isComplete()) {
            refreshBalance(announce = false)
        }
    }

    fun toggleWhenIdle() {
        viewModelScope.launch {
            while (_busy.value) delay(50L)
            toggle()
        }
    }

    fun reportFeedback(type: FeedbackEvent.FeedbackType, message: String) {
        _statusMessage.value = message
        _feedbackEvent.value = FeedbackEvent(type = type, message = message)
    }

    fun getConfig(): HotWaterConfig = persistRuntimeInfoIfNeeded(store.loadConfig())

    private fun runOperation(
        busyMessage: String,
        operationType: OperationHistory.OperationType,
        announceSuccess: Boolean,
        block: suspend (HotWaterConfig) -> OperationResult,
    ) {
        if (_busy.value) return
        val preparedConfig = persistRuntimeInfoIfNeeded(store.loadConfig())
        if (!preparedConfig.isComplete()) {
            _statusMessage.value = "配置还不完整，请先到配置页补全字段。"
            _feedbackEvent.value = FeedbackEvent(
                type = FeedbackEvent.FeedbackType.WARNING,
                message = "配置不完整，请先填写配置"
            )
            return
        }

        _busy.value = true
        _statusMessage.value = busyMessage

        viewModelScope.launch {
            try {
                val result = block(preparedConfig)
                _snapshot.value = result.snapshot ?: buildPlaceholderSnapshot(preparedConfig)
                val summary = HotWaterUiText.firstMeaningfulLine(result.summary)
                _statusMessage.value = summary
                syncWidgetStatus(result.title, result.summary)
                syncNotificationState()
                if (announceSuccess) {
                    val feedbackType = when (result.outcome) {
                        OperationOutcome.SUCCESS -> FeedbackEvent.FeedbackType.SUCCESS
                        OperationOutcome.PENDING, OperationOutcome.WARNING -> FeedbackEvent.FeedbackType.WARNING
                    }
                    _feedbackEvent.value = FeedbackEvent(
                        type = feedbackType,
                        message = summary.ifBlank { "操作已完成" },
                    )
                }

                // 保存成功历史
                val operationSucceeded = result.outcome == OperationOutcome.SUCCESS
                val history = OperationHistory(
                    type = operationType,
                    success = operationSucceeded,
                    message = HotWaterUiText.firstMeaningfulLine(result.summary),
                    consumptionYuan = result.consumptionYuan,
                    usageDurationMillis = result.usageDurationMillis,
                    usageStartedAtMillis = result.usageStartedAtMillis,
                )
                store.saveHistory(history)
                _history.value = store.loadHistory()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _snapshot.value = buildPlaceholderSnapshot(preparedConfig)
                val message = HotWaterUiText.summarizeError(error)
                _statusMessage.value = message
                syncWidgetStatus("操作失败", message)

                // 保存失败历史
                val history = OperationHistory(
                    type = operationType,
                    success = false,
                    message = message,
                )
                store.saveHistory(history)
                _history.value = store.loadHistory()

                // 触发失败反馈
                _feedbackEvent.value = FeedbackEvent(
                    type = FeedbackEvent.FeedbackType.ERROR,
                    message = message,
                )
            } finally {
                _busy.value = false
            }
        }
    }

    private fun persistRuntimeInfoIfNeeded(config: HotWaterConfig): HotWaterConfig {
        val prepared = RuntimeDeviceInfoResolver.enrichConfig(getApplication(), config)
        if (prepared != config) {
            store.saveConfig(prepared)
        }
        return prepared
    }

    private fun buildPlaceholderSnapshot(config: HotWaterConfig): DashboardSnapshot {
        val cachedOrderNo = store.loadOrderNo()
        return DashboardSnapshot(
            balanceYuan = 0.0,
            balanceLabel = "--",
            userLabel = config.telephone.takeIf { it.isNotBlank() }
                ?.let { HotWaterConfig.maskMiddle(it, 3, 4) }
                ?: "未填写账号",
            deviceName = "热水设备待刷新",
            locationLabel = if (config.isComplete()) "点刷新余额后自动识别宿舍位置" else "先完成配置后再自动识别",
            statusLabel = when {
                !config.isComplete() -> "当前配置还不完整。"
                !cachedOrderNo.isNullOrBlank() -> "已缓存订单号，主按钮现在会显示为关。"
                else -> "当前没有缓存订单号，主按钮现在会显示为开。"
            },
            orderNo = cachedOrderNo,
            hasCachedOrder = !cachedOrderNo.isNullOrBlank(),
            isDeviceBusyByOther = false,
        )
    }

    private fun syncWidgetStatus(title: String, summary: String) {
        store.saveWidgetState(
            title = title,
            message = HotWaterUiText.firstMeaningfulLine(summary).ifBlank { "请求已执行。" },
            isBusy = false,
        )
        HotWaterWidgetUi.updateAllWidgets(getApplication())
    }

    private fun syncNotificationState() {
        val hasCachedOrder = !store.loadOrderNo().isNullOrBlank()
        if (hasCachedOrder) {
            HotWaterNotificationService.show(getApplication())
        } else {
            HotWaterNotificationService.dismiss(getApplication())
        }
    }

    companion object {
        private const val LOW_BALANCE_WARNING_YUAN = 5.0
    }
}
