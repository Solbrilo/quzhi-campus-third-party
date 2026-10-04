package cn.qzxy.hotwater

import android.app.Application
import android.content.ComponentName
import android.service.quicksettings.TileService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit

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

data class SmsCodeState(
    val loading: Boolean = false,
    val sent: Boolean = false,
    val remainingSeconds: Int = 0,
    val message: String = "",
)

data class NearbyScanState(
    val scanning: Boolean = false,
    val message: String = "",
)

data class NearbyPairState(
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
        NEARBY_PAIR,
    }

    private val store = HotWaterConfigStore(application)
    private val repository = HotWaterRepository(HotWaterApi(), store)
    private val qzxyLoginApi: QzxyLoginRemoteDataSource = QzxyLoginApi()
    private val billsController = ServerBillsController(viewModelScope, HotWaterApi()) { showCredentialExpiryPrompt() }
    val serverBills = billsController.state

    fun loadServerBills(query: BillQuery, force: Boolean = false) {
        if (store.requiresReauthentication()) { showCredentialExpiryPrompt(); return }
        billsController.load(store.loadConfig(), query, force)
    }

    private val messagePreferences = MessagePreferences(application)
    private val messagesController = ServerMessagesController(viewModelScope, HotWaterApi(), messagePreferences) {
        showCredentialExpiryPrompt()
    }
    val serverMessages = messagesController.state

    fun loadMessages(announce: Boolean = false) {
        if (store.requiresReauthentication()) return
        messagesController.load(store.loadConfig(), announce)
    }
    fun markMessageRead(message: ServerMessage) = messagesController.markRead(message)
    fun markAllMessagesRead() = messagesController.markAllRead()
    fun dismissMessageAnnouncement() = messagesController.dismissAnnouncement()

    fun logout() {
        if (_busy.value) return
        viewModelScope.launch {
            HotWaterRepository.withDeviceControl {
                if (_busy.value) return@withDeviceControl
                if (!store.loadOrderNo().isNullOrBlank() || store.loadUsageSession() != null ||
                    store.loadPendingSettlements().any { !it.closeConfirmed }) {
                    reportFeedback(FeedbackEvent.FeedbackType.WARNING, "请先关水并确认结束，再退出登录")
                    return@withDeviceControl
                }
                ++operationGeneration
                operationJob?.cancel()
                cancelQzxyLogin()
                cancelNearbyPairing()
                stopNearbyScan()
                ++snCodeSaveGeneration
                snCodeSaveJob?.cancel()
                ++smsCodeGeneration
                smsCodeJob?.cancel()
                settlementSyncJob?.cancel()
                activeStateSyncJob?.cancel()
                HotWaterSettlementSyncScheduler.cancel(getApplication())
                HotWaterWidgetProvider.cancelPendingActions(getApplication())
                if (!store.clearAll()) {
                    reportFeedback(FeedbackEvent.FeedbackType.ERROR, "退出登录失败，请重试")
                    return@withDeviceControl
                }
                billsController.clear()
                messagesController.clear()
                _config.value = HotWaterConfig()
                _snapshot.value = buildPlaceholderSnapshot(_config.value)
                _credentialExpired.value = false
                _qzxyLoginState.value = QzxyLoginState()
                _nearbyDevices.value = emptyList()
                _nearbyPairState.value = NearbyPairState()
                _snCodeSaveState.value = SnCodeSaveState()
                syncNotificationState()
                syncWidgetStatus("尚未登录", "请打开应用登录")
                reportFeedback(FeedbackEvent.FeedbackType.SUCCESS, "已退出登录")
            }
        }
    }

    private val _snapshot = MutableStateFlow<DashboardSnapshot?>(null)
    val snapshot: StateFlow<DashboardSnapshot?> = _snapshot.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _config = MutableStateFlow(store.loadConfig())
    val config: StateFlow<HotWaterConfig> = _config.asStateFlow()

    private val _feedbackEvent = MutableStateFlow<FeedbackEvent?>(null)
    val feedbackEvent: StateFlow<FeedbackEvent?> = _feedbackEvent.asStateFlow()

    private val _qzxyLoginState = MutableStateFlow(QzxyLoginState())
    val qzxyLoginState: StateFlow<QzxyLoginState> = _qzxyLoginState.asStateFlow()

    private val _smsCodeState = MutableStateFlow(SmsCodeState())
    val smsCodeState: StateFlow<SmsCodeState> = _smsCodeState.asStateFlow()

    private val _nearbyDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val nearbyDevices: StateFlow<List<NearbyDevice>> = _nearbyDevices.asStateFlow()

    private val _nearbyScanState = MutableStateFlow(NearbyScanState())
    val nearbyScanState: StateFlow<NearbyScanState> = _nearbyScanState.asStateFlow()

    private val _nearbyPairState = MutableStateFlow(NearbyPairState())
    val nearbyPairState: StateFlow<NearbyPairState> = _nearbyPairState.asStateFlow()

    private val _snCodeSaveState = MutableStateFlow(SnCodeSaveState())
    val snCodeSaveState: StateFlow<SnCodeSaveState> = _snCodeSaveState.asStateFlow()

    private val _credentialExpired = MutableStateFlow(store.requiresReauthentication())
    val credentialExpired: StateFlow<Boolean> = _credentialExpired.asStateFlow()

    private var initialized = false
    private var qzxyLoginJob: Job? = null
    private var qzxyLoginGeneration: Long = 0L
    private var smsCodeJob: Job? = null
    private var smsCodeGeneration: Long = 0L
    private var nearbyScanner: NearbyDeviceScanner? = null
    private var nearbyScanGeneration = 0L
    private var nearbyScanOwner: Any? = null
    private var nearbyLookupJob: Job? = null
    private var nearbyPairJob: Job? = null
    private var nearbyPairGeneration: Long = 0L
    private val nearbyDeviceApi: HotWaterRemoteDataSource = HotWaterApi()
    private var operationJob: Job? = null
    private var operationGeneration: Long = 0L
    private var snCodeSaveJob: Job? = null
    private var snCodeSaveGeneration: Long = 0L
    private var settlementSyncJob: Job? = null
    private var activeStateSyncJob: Job? = null
    private var appInForeground = false
    private var busyOwner: BusyOwner? = null

    fun initialize() {
        if (initialized) return
        initialized = true
        val preparedConfig = persistRuntimeInfoIfNeeded(store.loadConfig())
        _config.value = preparedConfig
        messagesController.bind(preparedConfig)
        loadMessages(announce = true)
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

    fun refreshBalance(
        announce: Boolean = true,
        recordHistory: Boolean = true,
    ) {
        runOperation(
            busyMessage = if (announce) "正在刷新余额…" else "正在同步余额…",
            operationType = OperationHistory.OperationType.REFRESH,
            announceSuccess = announce,
            recordHistory = recordHistory,
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
        cancelNearbyPairing()
        stopNearbyScan()
        invalidateLoginRequest()
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
        billsController.bind(prepared)
        if (prepared.isComplete()) {
            // A complete imported/manual configuration is an explicit replacement for an expired
            // credential. Keep the user in the app and let the next request validate it normally.
            store.clearCredentialsExpired()
            _credentialExpired.value = false
        }
        _config.value = prepared
        messagesController.bind(prepared)
        loadMessages(announce = true)
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
        appInForeground = true
        loadMessages(announce = true)
        if (store.requiresReauthentication()) {
            activeStateSyncJob?.cancel()
            activeStateSyncJob = null
            showCredentialExpiryPrompt()
            return
        }
        refreshBalance(announce = false)
        ensureActiveStateSyncLoop()
    }

    fun onAppBackgrounded() {
        appInForeground = false
        activeStateSyncJob?.cancel()
        activeStateSyncJob = null
        stopNearbyScan()
    }

    fun sendVerificationCode(telephone: String) {
        val normalized = telephone.trim()
        if (_smsCodeState.value.loading || _smsCodeState.value.remainingSeconds > 0) return
        if (!QzxyLoginProtocol.isValidTelephone(normalized)) {
            _smsCodeState.value = SmsCodeState(message = "请输入 11 位手机号")
            return
        }
        smsCodeJob?.cancel()
        val generation = ++smsCodeGeneration
        _smsCodeState.value = SmsCodeState(loading = true)
        smsCodeJob = viewModelScope.launch {
            try {
                qzxyLoginApi.sendVerificationCode(normalized)
                if (generation != smsCodeGeneration) return@launch
                _smsCodeState.value = SmsCodeState(sent = true, remainingSeconds = SMS_CODE_COOLDOWN_SECONDS)
                for (remaining in (SMS_CODE_COOLDOWN_SECONDS - 1) downTo 0) {
                    delay(1_000L)
                    if (generation != smsCodeGeneration) return@launch
                    _smsCodeState.value = _smsCodeState.value.copy(remainingSeconds = remaining)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == smsCodeGeneration) {
                    _smsCodeState.value = SmsCodeState(message = HotWaterUiText.summarizeError(error))
                }
            } finally {
                if (generation == smsCodeGeneration) smsCodeJob = null
            }
        }
    }

    fun startNearbyScan(owner: Any) {
        stopNearbyScan()
        nearbyScanOwner = owner
        val generation = ++nearbyScanGeneration
        val lookupJob = kotlinx.coroutines.SupervisorJob(viewModelScope.coroutineContext[Job])
        nearbyLookupJob = lookupJob
        val lookupScope = kotlinx.coroutines.CoroutineScope(viewModelScope.coroutineContext + lookupJob)
        val lookupPermits = kotlinx.coroutines.sync.Semaphore(3)
        val lookupConfig = store.loadConfig()
        val queriedAddresses = mutableSetOf<String>()
        _nearbyDevices.value = emptyList()
        _nearbyScanState.value = NearbyScanState(scanning = true)
        val scanner = NearbyDeviceScanner(
            context = getApplication(),
            onDeviceFound = { device ->
                if (generation == nearbyScanGeneration) {
                    val previous = _nearbyDevices.value.firstOrNull { it.normalizedMac == device.normalizedMac }
                    _nearbyDevices.value = (_nearbyDevices.value.filterNot { it.normalizedMac == device.normalizedMac } +
                        device.copy(binding = previous?.binding)).sortedByDescending { it.rssi }
                    if (lookupConfig.hasAccount() && queriedAddresses.size < 40 && queriedAddresses.add(device.normalizedMac)) {
                        lookupScope.launch {
                            val binding = try {
                                lookupPermits.withPermit {
                                    NearbyDevicePayload.parseBinding(nearbyDeviceApi.deviceInfoForMac(lookupConfig, device.macAddress))
                                }
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { null }
                            if (generation == nearbyScanGeneration && store.loadConfig().accountRequestKey() == lookupConfig.accountRequestKey()) {
                                _nearbyDevices.value = _nearbyDevices.value.map {
                                    if (it.normalizedMac == device.normalizedMac) it.copy(binding = binding) else it
                                }
                            }
                        }
                    }
                }
            },
            onFinished = {
                if (generation == nearbyScanGeneration) _nearbyScanState.value = _nearbyScanState.value.copy(scanning = false)
            },
            onError = { message ->
                if (generation == nearbyScanGeneration) _nearbyScanState.value = NearbyScanState(message = message)
            },
        )
        nearbyScanner = scanner
        scanner.start()
    }

    fun stopNearbyScan(owner: Any? = null) {
        if (owner != null && nearbyScanOwner !== owner) return
        nearbyScanOwner = null
        nearbyScanGeneration++
        nearbyLookupJob?.cancel()
        nearbyLookupJob = null
        nearbyScanner?.stop()
        nearbyScanner = null
        if (_nearbyScanState.value.scanning) {
            _nearbyScanState.value = _nearbyScanState.value.copy(scanning = false)
        }
    }

    fun clearNearbyPairState() {
        if (!_nearbyPairState.value.loading) _nearbyPairState.value = NearbyPairState()
    }

    fun cancelNearbyPairing() {
        nearbyPairGeneration++
        nearbyPairJob?.cancel()
        nearbyPairJob = null
        _nearbyPairState.value = NearbyPairState()
        releaseBusy(BusyOwner.NEARBY_PAIR)
    }

    fun pairNearbyDevice(device: NearbyDevice) {
        val code = device.macAddress
        if (_busy.value || _nearbyPairState.value.loading) return
        val normalized = QrCodeParser.normalizeSnCode(code)
        if (normalized == null) { _nearbyPairState.value = NearbyPairState(message = "设备码格式无效"); return }
        val baseConfig = store.loadConfig()
        if (!baseConfig.hasAccount()) {
            _nearbyPairState.value = NearbyPairState(message = "请先登录趣智校园，或使用扫码配对")
            return
        }
        val stateGeneration = store.loadStateGeneration()
        val generation = ++nearbyPairGeneration
        stopNearbyScan()
        acquireBusy(BusyOwner.NEARBY_PAIR)
        _nearbyPairState.value = NearbyPairState(loading = true)
        nearbyPairJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val binding = NearbyDevicePayload.parseBinding(nearbyDeviceApi.deviceInfoForMac(baseConfig, code))
                val committed = HotWaterRepository.withDeviceControl {
                    if (generation != nearbyPairGeneration || stateGeneration != store.loadStateGeneration() ||
                        store.loadConfig() != baseConfig) return@withDeviceControl false
                    DeviceSwitchPolicy.blockingMessage(baseConfig, binding.snCode, store.loadOrderNo(),
                        store.loadUsageSession(), store.loadPendingSettlements())?.let { error(it) }
                    if (baseConfig.snCode == binding.snCode) return@withDeviceControl true
                    settlementSyncJob?.cancel()
                    settlementSyncJob = null
                    HotWaterSettlementSyncScheduler.cancel(getApplication())
                    HotWaterWidgetProvider.cancelPendingActions(getApplication())
                    // macAddress is the PHONE's runtime MAC in this project, not the heater's BLE MAC.
                    val prepared = baseConfig.copy(snCode = binding.snCode)
                    if (baseConfig.hasDifferentIdentityFrom(prepared)) store.clearActiveUsageState()
                    store.saveConfig(prepared)
                    ++operationGeneration
                    _config.value = prepared
                    messagesController.bind(prepared)
                    _snapshot.value = buildPlaceholderSnapshot(prepared).copy(deviceName = binding.deviceName,
                        locationLabel = binding.locationLabel.ifBlank { "位置待刷新" })
                    syncNotificationState()
                    syncWidgetStatus("设备已绑定", binding.deviceName)
                    true
                }
                if (generation != nearbyPairGeneration) return@launch
                if (committed) {
                    val message = "已绑定 ${binding.deviceName}"
                    // Login may have left a warning feedback event saying that snCode is still
                    // missing. Replace it at the moment the nearby-device binding is committed,
                    // otherwise that stale warning reappears when the main page is shown.
                    _statusMessage.value = message
                    _feedbackEvent.value = FeedbackEvent(
                        type = FeedbackEvent.FeedbackType.SUCCESS,
                        message = message,
                    )
                    _nearbyPairState.value = NearbyPairState(success = true, message = message)
                } else {
                    _nearbyPairState.value = NearbyPairState(message = "账号或设备配置已更新，请重新选择")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: CredentialExpiredException) {
                if (generation == nearbyPairGeneration) { _nearbyPairState.value = NearbyPairState(); showCredentialExpiryPrompt() }
            } catch (error: Exception) {
                if (generation == nearbyPairGeneration) _nearbyPairState.value = NearbyPairState(message = HotWaterUiText.summarizeError(error))
            } finally {
                if (generation == nearbyPairGeneration) { releaseBusy(BusyOwner.NEARBY_PAIR); nearbyPairJob = null }
            }
            if (generation == nearbyPairGeneration && _nearbyPairState.value.success && _config.value.isComplete()) {
                refreshBalance(announce = false)
            }
        }.also { it.start() }
    }

    fun saveSnCode(snCode: String) {
        cancelNearbyPairing()
        val normalized = QrCodeParser.normalizeSnCode(snCode)
        if (normalized == null) {
            _snCodeSaveState.value = SnCodeSaveState(message = "设备码应为 12 位十六进制字符")
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
                HotWaterRepository.withDeviceControl {
                    if (generation != operationGeneration || saveGeneration != snCodeSaveGeneration) {
                        throw CancellationException("Device binding superseded")
                    }
                    val existing = persistRuntimeInfoIfNeeded(store.loadConfig())
                    DeviceSwitchPolicy.blockingMessage(existing, normalized, store.loadOrderNo(),
                        store.loadUsageSession(), store.loadPendingSettlements())?.let { error(it) }
                    val changed = existing.snCode != normalized
                    if (changed) {
                        HotWaterWidgetProvider.cancelPendingActions(getApplication())
                        HotWaterSettlementSyncScheduler.cancel(getApplication())
                        store.clearActiveUsageState()
                    }
                    val prepared = existing.copy(snCode = normalized)
                    store.saveConfig(prepared)
                    _config.value = prepared
                    messagesController.bind(prepared)
                    _snapshot.value = buildPlaceholderSnapshot(prepared)
                    syncNotificationState()
                    syncWidgetStatus("设备码已绑定", "设备二维码已保存。")
                    shouldRefresh = prepared.isComplete()
                }
                _statusMessage.value = "设备码已绑定"
                _feedbackEvent.value = FeedbackEvent(
                    type = FeedbackEvent.FeedbackType.SUCCESS,
                    message = "设备码已绑定",
                )
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

    fun invalidateLoginRequest() {
        qzxyLoginGeneration++
        qzxyLoginJob?.cancel()
        qzxyLoginJob = null
        releaseBusy(BusyOwner.QZXY_LOGIN)
        clearQzxyLoginState()
    }

    fun cancelQzxyLogin() {
        invalidateLoginRequest()
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
        smsCodeGeneration++
        smsCodeJob?.cancel()
        smsCodeJob = null
        nearbyPairGeneration++
        nearbyPairJob?.cancel()
        nearbyPairJob = null
        stopNearbyScan()
        snCodeSaveJob?.cancel()
        snCodeSaveJob = null
        settlementSyncJob?.cancel()
        settlementSyncJob = null
        activeStateSyncJob?.cancel()
        activeStateSyncJob = null
        HotWaterSettlementSyncScheduler.cancel(appContext)
        HotWaterWidgetProvider.cancelPendingActions(appContext)
        busyOwner = null
        _busy.value = false

        val storeCleared = store.clearAll()
        val feedbackStoreCleared = runCatching {
            FeedbackHistoryStore(appContext).clear()
        }.getOrDefault(false)
        val messagesCleared = messagePreferences.clear()
        val allStoresCleared = storeCleared && feedbackStoreCleared && messagesCleared

        billsController.clear()
        messagesController.clear()
        _smsCodeState.value = SmsCodeState()
        _nearbyDevices.value = emptyList()
        _nearbyPairState.value = NearbyPairState()
        _nearbyScanState.value = NearbyScanState()
        val emptyConfig = HotWaterConfig()
        _config.value = emptyConfig
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
        loginToQzxy(QzxyLoginMethod.PASSWORD, telephone, password)
    }

    fun loginToQzxy(method: QzxyLoginMethod, telephone: String, credential: String) {
        if (_qzxyLoginState.value.loading) return
        val normalizedTelephone = telephone.trim()
        val normalizedCredential = if (method == QzxyLoginMethod.SMS) credential.trim() else credential
        val invalidMessage = when {
            normalizedTelephone.isBlank() -> "请输入趣智校园手机号"
            method == QzxyLoginMethod.SMS && !QzxyLoginProtocol.isValidTelephone(normalizedTelephone) -> "请输入有效的 11 位手机号"
            method == QzxyLoginMethod.SMS && !Regex("^[0-9]{6}$").matches(normalizedCredential) -> "请输入 6 位短信验证码"
            method == QzxyLoginMethod.PASSWORD && normalizedCredential.isBlank() -> "请输入趣智校园密码"
            else -> null
        }
        if (invalidMessage != null) {
            _qzxyLoginState.value = QzxyLoginState(message = invalidMessage)
            reportFeedback(FeedbackEvent.FeedbackType.WARNING, invalidMessage)
            return
        }

        cancelNearbyPairing()
        stopNearbyScan()
        billsController.clear()
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
                        telephone = normalizedTelephone,
                        password = if (method == QzxyLoginMethod.PASSWORD) normalizedCredential else "",
                        smsCode = if (method == QzxyLoginMethod.SMS) normalizedCredential else "",
                        method = method,
                        phoneSystem = "android",
                        version = if (method == QzxyLoginMethod.SMS) {
                            QZXY_SMS_LOGIN_CLIENT_VERSION
                        } else {
                            QZXY_LOGIN_CLIENT_VERSION
                        },
                    ),
                )
                if (generation != qzxyLoginGeneration) return@launch
                val existing = persistRuntimeInfoIfNeeded(store.loadConfig())
                val prepared = RuntimeDeviceInfoResolver.enrichConfig(getApplication(), result.mergeInto(existing))
                if (existing.hasDifferentIdentityFrom(prepared)) store.clearActiveUsageState()
                HotWaterSettlementSyncScheduler.cancel(getApplication())
                store.saveConfig(prepared)
                billsController.bind(prepared)
                store.clearCredentialsExpired()
                loadMessages(announce = true)
                _credentialExpired.value = false
                _config.value = prepared
                messagesController.bind(prepared)
                _snapshot.value = buildPlaceholderSnapshot(prepared)
                syncNotificationState()
                if (store.loadPendingSettlements().isNotEmpty()) {
                    HotWaterSettlementSyncScheduler.enqueue(getApplication())
                }

                if (prepared.snCode.isBlank()) {
                    val message = "登录成功，账号字段已自动填充；还需选择或扫码绑定设备。"
                    _statusMessage.value = message
                    syncWidgetStatus("等待绑定设备", message)
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
        recordHistory: Boolean = true,
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

                if (recordHistory || result.automaticClose) {
                    val historyType = if (result.automaticClose) {
                        OperationHistory.OperationType.CLOSE
                    } else {
                        operationType
                    }
                    store.saveOperationResultHistory(historyType, result)
                }
                if ((operationType == OperationHistory.OperationType.CLOSE || result.automaticClose) && serverBills.value.loaded) {
                    loadServerBills(serverBills.value.query, force = true)
                }
                if (store.loadPendingSettlements().isNotEmpty()) {
                    HotWaterSettlementSyncScheduler.enqueue(getApplication())
                }
                if (
                    (operationType == OperationHistory.OperationType.CLOSE || result.automaticClose) &&
                    result.historyId != null
                ) {
                    schedulePostCloseSync(
                        config = preparedConfig,
                        operationGenerationAtClose = generation,
                        historyId = result.historyId,
                    )
                }
                ensureActiveStateSyncLoop()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation != operationGeneration) return@launch
                if (error is CredentialExpiredException) {
                    showCredentialExpiryPrompt()
                    return@launch
                }
                if (recordHistory) {
                    _snapshot.value = buildPlaceholderSnapshot(preparedConfig)
                }
                val message = HotWaterUiText.summarizeError(error)
                if (recordHistory) {
                    _statusMessage.value = message
                    syncWidgetStatus("操作失败", message)
                    // 保存失败历史
                    val history = OperationHistory(
                        type = operationType,
                        success = false,
                        message = message,
                    )
                    store.saveHistory(history)
                    // 触发失败反馈
                    _feedbackEvent.value = FeedbackEvent(
                        type = FeedbackEvent.FeedbackType.ERROR,
                        message = message,
                    )
                }
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
                syncNotificationState()
                if (store.loadPendingSettlements().none { it.historyId == historyId }) {
                    if (serverBills.value.loaded) loadServerBills(serverBills.value.query, force = true)
                    return@launch
                }
            }
        }
    }

    private fun showCredentialExpiryPrompt() {
        messagesController.clear()
        billsController.clear()
        cancelNearbyPairing()
        stopNearbyScan()
        activeStateSyncJob?.cancel()
        activeStateSyncJob = null
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
            skipIfBusy = true,
        )
        HotWaterWidgetUi.updateAllWidgets(getApplication())
        TileService.requestListeningState(
            getApplication(),
            ComponentName(getApplication(), HotWaterTileService::class.java),
        )
    }

    private fun syncNotificationState() {
        val hasCachedOrder = !store.loadOrderNo().isNullOrBlank()
        if (hasCachedOrder) {
            HotWaterNotificationService.show(getApplication())
        } else {
            HotWaterNotificationService.dismiss(getApplication())
        }
    }

    private fun ensureActiveStateSyncLoop() {
        if (!appInForeground || store.requiresReauthentication()) {
            return
        }
        if (store.loadOrderNo().isNullOrBlank()) {
            activeStateSyncJob?.cancel()
            activeStateSyncJob = null
            return
        }
        if (activeStateSyncJob?.isActive == true) {
            return
        }
        activeStateSyncJob = viewModelScope.launch {
            try {
                while (appInForeground && !store.loadOrderNo().isNullOrBlank()) {
                    delay(ACTIVE_STATE_SYNC_INTERVAL_MS)
                    if (!appInForeground || store.loadOrderNo().isNullOrBlank()) {
                        break
                    }
                    refreshBalance(announce = false, recordHistory = false)
                }
            } finally {
                if (activeStateSyncJob === currentCoroutineContext()[Job]) {
                    activeStateSyncJob = null
                }
            }
        }
    }

    override fun onCleared() {
        stopNearbyScan()
        super.onCleared()
    }

    companion object {
        private const val LOW_BALANCE_WARNING_YUAN = 5.0
        private const val SMS_CODE_COOLDOWN_SECONDS = 60
        private const val ACTIVE_STATE_SYNC_INTERVAL_MS = 30_000L
        private val POST_CLOSE_SYNC_DELAYS_MS = longArrayOf(
            350L,
            900L,
            1_800L,
            3_200L,
        )
    }
}
