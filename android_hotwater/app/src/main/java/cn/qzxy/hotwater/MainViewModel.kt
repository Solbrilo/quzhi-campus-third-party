package cn.qzxy.hotwater

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

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

data class QzxyLoginState(
    val loading: Boolean = false,
    val success: Boolean = false,
    val message: String = "",
)

data class SnCodeSaveState(
    val loading: Boolean = false,
    val success: Boolean = false,
    val snCode: String = "",
    val message: String = "",
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private enum class BusyOwner {
        OPERATION,
        QZXY_LOGIN,
        SN_CODE_SAVE,
    }

    private val store = HotWaterConfigStore(application)
    private val repository = HotWaterRepository(HotWaterApi(), store)
    private val qzxyLoginApi: QzxyLoginRemoteDataSource = QzxyLoginApi()

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

    private val _qzxyLoginState = MutableStateFlow(QzxyLoginState())
    val qzxyLoginState: StateFlow<QzxyLoginState> = _qzxyLoginState.asStateFlow()

    private val _snCodeSaveState = MutableStateFlow(SnCodeSaveState())
    val snCodeSaveState: StateFlow<SnCodeSaveState> = _snCodeSaveState.asStateFlow()

    private val _credentialExpired = MutableStateFlow(store.requiresReauthentication())
    val credentialExpired: StateFlow<Boolean> = _credentialExpired.asStateFlow()

    private var initialized = false
    private var qzxyLoginJob: Job? = null
    private var qzxyLoginGeneration: Long = 0L
    private var operationJob: Job? = null
    private var operationGeneration: Long = 0L
    private var snCodeSaveJob: Job? = null
    private var snCodeSaveGeneration: Long = 0L
    private var settlementSyncJob: Job? = null
    private var busyOwner: BusyOwner? = null

    fun initialize() {
        if (initialized) return
        initialized = true
        val preparedConfig = persistRuntimeInfoIfNeeded(store.loadConfig())
        _config.value = preparedConfig
        _snapshot.value = buildPlaceholderSnapshot(preparedConfig)
        syncNotificationState()

        if (store.requiresReauthentication()) {
            showCredentialExpiryPrompt()
        } else if (preparedConfig.isComplete()) {
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
        return snapshot.balanceYuan?.let { it < LOW_BALANCE_WARNING_YUAN } == true
    }

    fun clearFeedbackEvent() {
        _feedbackEvent.value = null
    }

    fun saveConfig(rawConfig: HotWaterConfig) {
        settlementSyncJob?.cancel()
        settlementSyncJob = null
        val previous = persistRuntimeInfoIfNeeded(store.loadConfig())
        val prepared = persistRuntimeInfoIfNeeded(rawConfig)
        if (previous.hasDifferentIdentityFrom(prepared)) {
            ++operationGeneration
            operationJob?.cancel()
            if (busyOwner == BusyOwner.OPERATION) releaseBusy(BusyOwner.OPERATION)
            store.clearActiveUsageState()
        }
        store.saveConfig(prepared)
        if (prepared.isComplete()) {
            // A complete imported/manual configuration is an explicit replacement for an expired
            // credential. Keep the user in the app and let the next request validate it normally.
            store.clearCredentialsExpired()
            _credentialExpired.value = false
        }
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

    fun onAppForegrounded() {
        if (store.requiresReauthentication()) {
            showCredentialExpiryPrompt()
            return
        }
        refreshBalance(announce = false)
    }

    fun saveSnCode(snCode: String) {
        val normalized = snCode.trim().replace(":", "").uppercase(Locale.ROOT)
        if (normalized.isBlank()) {
            _snCodeSaveState.value = SnCodeSaveState(message = "设备码不能为空")
            return
        }
        val previousOperation = operationJob
        val previousSave = snCodeSaveJob
        val generation = ++operationGeneration
        val saveGeneration = ++snCodeSaveGeneration
        if (_qzxyLoginState.value.loading) {
            ++qzxyLoginGeneration
            qzxyLoginJob?.cancel()
            qzxyLoginJob = null
            _qzxyLoginState.value = QzxyLoginState()
        }
        previousOperation?.cancel()
        previousSave?.cancel()
        settlementSyncJob?.cancel()
        settlementSyncJob = null
        acquireBusy(BusyOwner.SN_CODE_SAVE)
        _snCodeSaveState.value = SnCodeSaveState(loading = true, snCode = normalized)
        val saveJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            var shouldRefresh = false
            var persisted = false
            try {
                previousOperation?.join()
                previousSave?.join()
                if (generation != operationGeneration || saveGeneration != snCodeSaveGeneration) {
                    return@launch
                }
                val existing = persistRuntimeInfoIfNeeded(store.loadConfig())
                val changed = existing.snCode != normalized
                if (changed) {
                    store.clearActiveUsageState()
                }
                val prepared = existing.copy(snCode = normalized)
                store.saveConfig(prepared)
                _config.value = prepared
                _snapshot.value = buildPlaceholderSnapshot(prepared)
                syncNotificationState()
                syncWidgetStatus("设备码已绑定", "设备二维码已保存。")
                _statusMessage.value = "设备码已绑定"
                _feedbackEvent.value = FeedbackEvent(
                    type = FeedbackEvent.FeedbackType.SUCCESS,
                    message = "设备码已绑定",
                )
                shouldRefresh = prepared.isComplete()
                persisted = true
                _snCodeSaveState.value = SnCodeSaveState(
                    success = true,
                    snCode = normalized,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == operationGeneration && saveGeneration == snCodeSaveGeneration) {
                    val message = HotWaterUiText.summarizeError(error)
                    _statusMessage.value = message
                    _feedbackEvent.value = FeedbackEvent(
                        type = FeedbackEvent.FeedbackType.ERROR,
                        message = message,
                    )
                    _snCodeSaveState.value = SnCodeSaveState(
                        snCode = normalized,
                        message = message,
                    )
                }
                return@launch
            } finally {
                if (generation == operationGeneration && saveGeneration == snCodeSaveGeneration) {
                    releaseBusy(BusyOwner.SN_CODE_SAVE)
                    if (operationJob === previousOperation) operationJob = null
                    snCodeSaveJob = null
                }
            }
            if (
                persisted &&
                generation == operationGeneration &&
                saveGeneration == snCodeSaveGeneration &&
                shouldRefresh
            ) {
                refreshBalance(announce = false)
            }
        }
        snCodeSaveJob = saveJob
        saveJob.start()
    }

    fun clearSnCodeSaveState() {
        if (!_snCodeSaveState.value.loading) {
            _snCodeSaveState.value = SnCodeSaveState()
        }
    }

    fun clearQzxyLoginState() {
        _qzxyLoginState.value = if (_credentialExpired.value) {
            QzxyLoginState(message = CREDENTIAL_EXPIRED_MESSAGE)
        } else {
            QzxyLoginState()
        }
    }

    fun cancelQzxyLogin() {
        qzxyLoginGeneration++
        qzxyLoginJob?.cancel()
        qzxyLoginJob = null
        releaseBusy(BusyOwner.QZXY_LOGIN)
        clearQzxyLoginState()
    }

    /**
     * Leaves the expired-credential screen without clearing the persisted marker. This lets the
     * user import a replacement configuration; an operation that still needs the old credential
     * will show the login screen again.
     */
    fun skipCredentialExpiry() {
        qzxyLoginGeneration++
        qzxyLoginJob?.cancel()
        qzxyLoginJob = null
        releaseBusy(BusyOwner.QZXY_LOGIN)
        _credentialExpired.value = false
        _qzxyLoginState.value = QzxyLoginState()
        _statusMessage.value = "已跳过登录，可在配置页导入配置。"
    }

    /**
     * Clears every user-owned value persisted by the Android app.
     *
     * The caller is responsible for clearing activity-owned preferences such as
     * the theme and onboarding marker. This method handles the view-model's
     * encrypted account store, feedback history, runtime jobs and all observable
     * state so a cancelled request cannot repopulate data after confirmation.
     */
    fun clearAllUserData(): Boolean {
        val appContext = getApplication<Application>()

        // Invalidate every in-flight operation before removing its backing state.
        ++operationGeneration
        ++qzxyLoginGeneration
        ++snCodeSaveGeneration
        operationJob?.cancel()
        operationJob = null
        qzxyLoginJob?.cancel()
        qzxyLoginJob = null
        snCodeSaveJob?.cancel()
        snCodeSaveJob = null
        settlementSyncJob?.cancel()
        settlementSyncJob = null
        HotWaterSettlementSyncScheduler.cancel(appContext)
        HotWaterWidgetProvider.cancelPendingActions(appContext)
        busyOwner = null
        _busy.value = false

        val storeCleared = store.clearAll()
        val feedbackStoreCleared = runCatching {
            FeedbackHistoryStore(appContext).clear()
        }.getOrDefault(false)
        val allStoresCleared = storeCleared && feedbackStoreCleared

        val emptyConfig = HotWaterConfig()
        _config.value = emptyConfig
        _history.value = emptyList()
        _snapshot.value = buildPlaceholderSnapshot(emptyConfig)
        _credentialExpired.value = false
        _qzxyLoginState.value = QzxyLoginState()
        _snCodeSaveState.value = SnCodeSaveState()
        _feedbackEvent.value = null
        _statusMessage.value = if (allStoresCleared) {
            "已清空所有信息，请重新配置。"
        } else {
            "未能完整清空本机信息，请重试。"
        }

        // Refresh surfaces after the persisted state is gone. The notification
        // service will stop itself because there is no active order anymore.
        HotWaterNotificationService.dismiss(appContext)
        HotWaterWidgetUi.updateAllWidgets(appContext)
        return allStoresCleared
    }

    fun loginToQzxy(telephone: String, password: String) {
        if (_qzxyLoginState.value.loading) return
        if (telephone.isBlank() || password.isBlank()) {
            _qzxyLoginState.value = QzxyLoginState(message = "请输入趣智校园手机号和密码")
            reportFeedback(FeedbackEvent.FeedbackType.WARNING, "请输入趣智校园手机号和密码")
            return
        }

        HotWaterSettlementSyncScheduler.cancel(getApplication())

        val previousOperation = operationJob
        val previousSave = snCodeSaveJob
        ++operationGeneration
        ++snCodeSaveGeneration
        previousOperation?.cancel()
        previousSave?.cancel()
        settlementSyncJob?.cancel()
        settlementSyncJob = null
        _snCodeSaveState.value = SnCodeSaveState()
        val generation = ++qzxyLoginGeneration
        acquireBusy(BusyOwner.QZXY_LOGIN)
        _qzxyLoginState.value = QzxyLoginState(loading = true)
        _statusMessage.value = "正在登录趣智校园并获取账号配置..."
        val loginJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            var shouldRefresh = false
            try {
                previousOperation?.join()
                previousSave?.join()
                if (generation != qzxyLoginGeneration) return@launch
                if (operationJob === previousOperation) operationJob = null
                if (snCodeSaveJob === previousSave) snCodeSaveJob = null
                val result = qzxyLoginApi.login(
                    QzxyLoginCredentials(
                        telephone = telephone,
                        password = password,
                        phoneSystem = "android",
                        version = QZXY_LOGIN_CLIENT_VERSION,
                    ),
                )
                if (generation != qzxyLoginGeneration) return@launch
                val existing = persistRuntimeInfoIfNeeded(store.loadConfig())
                val prepared = persistRuntimeInfoIfNeeded(result.mergeInto(existing))
                if (existing.hasDifferentIdentityFrom(prepared)) {
                    store.clearActiveUsageState()
                }
                HotWaterSettlementSyncScheduler.cancel(getApplication())
                store.saveConfig(prepared)
                store.clearCredentialsExpired()
                _credentialExpired.value = false
                _config.value = prepared
                _snapshot.value = buildPlaceholderSnapshot(prepared)
                syncNotificationState()
                if (store.loadPendingSettlements().isNotEmpty()) {
                    HotWaterSettlementSyncScheduler.enqueue(getApplication())
                }

                if (prepared.snCode.isBlank()) {
                    val message = "登录成功，账号字段已自动填充；还需填写或选择该账号的 snCode。"
                    _statusMessage.value = message
                    syncWidgetStatus("等待补充设备码", message)
                    _feedbackEvent.value = FeedbackEvent(FeedbackEvent.FeedbackType.WARNING, message)
                    _qzxyLoginState.value = QzxyLoginState(success = true, message = message)
                } else {
                    _statusMessage.value = "趣智校园登录成功，账号和设备配置已保存。"
                    syncWidgetStatus("登录配置已保存", "正在刷新账户信息...")
                    _feedbackEvent.value = FeedbackEvent(
                        FeedbackEvent.FeedbackType.SUCCESS,
                        "趣智校园登录成功，已自动填充配置",
                    )
                    _qzxyLoginState.value = QzxyLoginState(
                        success = true,
                        message = "趣智校园登录成功，已自动填充配置",
                    )
                    shouldRefresh = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation != qzxyLoginGeneration) return@launch
                val message = HotWaterUiText.summarizeError(error)
                _statusMessage.value = message
                _feedbackEvent.value = FeedbackEvent(FeedbackEvent.FeedbackType.ERROR, message)
                _qzxyLoginState.value = QzxyLoginState(message = message)
            } finally {
                if (generation == qzxyLoginGeneration) {
                    releaseBusy(BusyOwner.QZXY_LOGIN)
                    if (_qzxyLoginState.value.loading) {
                        _qzxyLoginState.value = _qzxyLoginState.value.copy(loading = false)
                    }
                    qzxyLoginJob = null
                }
            }
            if (generation == qzxyLoginGeneration && shouldRefresh) refreshBalance(announce = false)
        }
        qzxyLoginJob = loginJob
        loginJob.start()
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
        if (store.requiresReauthentication()) {
            showCredentialExpiryPrompt()
            return
        }
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

        settlementSyncJob?.cancel()
        settlementSyncJob = null
        acquireBusy(BusyOwner.OPERATION)
        _statusMessage.value = busyMessage

        val generation = ++operationGeneration
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = block(preparedConfig)
                if (generation != operationGeneration) return@launch
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

                store.saveOperationResultHistory(operationType, result)
                _history.value = store.loadHistory()
                if (store.loadPendingSettlements().isNotEmpty()) {
                    HotWaterSettlementSyncScheduler.enqueue(getApplication())
                }
                if (
                    operationType == OperationHistory.OperationType.CLOSE &&
                    result.historyId != null
                ) {
                    schedulePostCloseSync(
                        config = preparedConfig,
                        operationGenerationAtClose = generation,
                        historyId = result.historyId,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation != operationGeneration) return@launch
                if (error is CredentialExpiredException) {
                    showCredentialExpiryPrompt()
                    return@launch
                }
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
                if (generation == operationGeneration) {
                    releaseBusy(BusyOwner.OPERATION)
                    operationJob = null
                }
            }
        }
        operationJob = job
        job.start()
    }

    private fun schedulePostCloseSync(
        config: HotWaterConfig,
        operationGenerationAtClose: Long,
        historyId: Long,
    ) {
        settlementSyncJob?.cancel()
        settlementSyncJob = viewModelScope.launch {
            for (delayMs in POST_CLOSE_SYNC_DELAYS_MS) {
                delay(delayMs)
                if (operationGenerationAtClose != operationGeneration) return@launch

                val result = try {
                    repository.check(config)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: CredentialExpiredException) {
                    showCredentialExpiryPrompt()
                    return@launch
                } catch (_: Exception) {
                    continue
                }
                if (operationGenerationAtClose != operationGeneration) return@launch

                result.snapshot?.let { _snapshot.value = it }
                _history.value = store.loadHistory()
                syncNotificationState()
                if (store.loadPendingSettlements().none { it.historyId == historyId }) {
                    return@launch
                }
            }
        }
    }

    private fun showCredentialExpiryPrompt() {
        store.markCredentialsExpired()
        val expiredConfig = persistRuntimeInfoIfNeeded(store.loadConfig())
        _config.value = expiredConfig
        _snapshot.value = buildPlaceholderSnapshot(expiredConfig)
        _credentialExpired.value = true
        _statusMessage.value = CREDENTIAL_EXPIRED_MESSAGE
        _qzxyLoginState.value = QzxyLoginState(message = CREDENTIAL_EXPIRED_MESSAGE)
        _feedbackEvent.value = FeedbackEvent(
            type = FeedbackEvent.FeedbackType.WARNING,
            message = CREDENTIAL_EXPIRED_MESSAGE,
        )
    }

    private fun acquireBusy(owner: BusyOwner) {
        busyOwner = owner
        _busy.value = true
    }

    private fun releaseBusy(owner: BusyOwner) {
        if (busyOwner == owner) {
            busyOwner = null
            _busy.value = false
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
            balanceYuan = null,
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
        private val POST_CLOSE_SYNC_DELAYS_MS = longArrayOf(
            350L,
            900L,
            1_800L,
            3_200L,
        )
    }
}
