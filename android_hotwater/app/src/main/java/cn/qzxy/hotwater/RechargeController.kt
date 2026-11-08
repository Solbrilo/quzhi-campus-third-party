package cn.qzxy.hotwater

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RechargeState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val placing: Boolean = false,
    val paying: Boolean = false,
    val loginRequired: Boolean = false,
    val packages: List<RechargePackage> = emptyList(),
    val selectedId: Long? = null,
    val schoolNotice: String? = null,
    val error: String? = null,
    val notice: String? = null,
    val paymentRequestId: Long? = null,
    val balanceYuan: Double? = null,
    val refreshingBalance: Boolean = false,
    val balanceError: String? = null,
) {
    val selectedPackage get() = packages.firstOrNull { it.id == selectedId }
    val busy get() = placing || paying
    val canSubmit get() = !loading && !busy && !loginRequired && selectedPackage != null
}

/** Orders and credentials stay in memory. Late results can never pay for a different account. */
internal class RechargeController(
    private val scope: CoroutineScope,
    private val remote: RechargeRemoteDataSource,
    private val currentConfig: () -> HotWaterConfig,
    private val stateGeneration: () -> Long,
    private val credentialsExpired: () -> Boolean,
    private val onCredentialsExpired: () -> Unit,
    private val onBalanceUpdated: (Double) -> Unit,
) {
    private val mutableState = MutableStateFlow(RechargeState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var job: Job? = null
    private var balanceJob: Job? = null
    private var balanceGeneration = 0L
    private var sessionKey = ""
    private var storeGeneration = -1L
    private var orderInfo: String? = null
    private var paymentId: Long? = null

    private fun isCurrent(expected: Long = generation): Boolean = expected == generation &&
        sessionKey == currentConfig().accountRequestKey() && storeGeneration == stateGeneration() &&
        !credentialsExpired()

    fun synchronizeAccount() {
        if (mutableState.value.visible && !mutableState.value.loginRequired && !isCurrent()) clear()
    }

    fun open() {
        synchronizeAccount()
        if (mutableState.value.visible) return
        load()
    }

    fun load() {
        if (mutableState.value.busy) return
        clear()
        val config = currentConfig()
        if (!config.hasAccount() || credentialsExpired()) {
            mutableState.value = RechargeState(visible = true, loginRequired = true)
            return
        }
        sessionKey = config.accountRequestKey()
        storeGeneration = stateGeneration()
        val expected = generation
        mutableState.value = RechargeState(visible = true, loading = true)
        job = scope.launch {
            try {
                val options = RechargeProtocol.options(remote.rechargeOptions(config))
                if (!isCurrent(expected)) return@launch
                if (!options.alipayEnabled) {
                    mutableState.value = RechargeState(visible = true, schoolNotice = options.notice,
                        error = "本校暂未开通支付宝充值")
                    return@launch
                }
                val packages = RechargeProtocol.packages(remote.rechargePackages(config))
                if (!isCurrent(expected)) return@launch
                mutableState.value = RechargeState(visible = true, packages = packages,
                    selectedId = packages.firstOrNull()?.id, schoolNotice = options.notice,
                    error = if (packages.isEmpty()) "暂时没有可选充值金额，请稍后重试" else null)
                refreshBalance()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!isCurrent(expected)) return@launch
                if (error is CredentialExpiredException) {
                    clear()
                    onCredentialsExpired()
                    mutableState.value = RechargeState(visible = true, loginRequired = true)
                } else mutableState.value = mutableState.value.copy(loading = false,
                    error = (error as? RechargeException)?.message ?: "无法读取充值信息，请检查网络后重试")
            }
        }
    }

    fun select(id: Long) {
        val state = mutableState.value
        if (state.busy || state.packages.none { it.id == id }) return
        mutableState.value = state.copy(selectedId = id, notice = null)
    }

    fun submit() {
        if (!isCurrent()) { synchronizeAccount(); return }
        val state = mutableState.value
        if (!state.visible || !state.canSubmit) return
        val amount = state.selectedPackage?.amount ?: return
        val config = currentConfig()
        val expected = ++generation
        mutableState.value = state.copy(placing = true, notice = null)
        job = scope.launch {
            try {
                val info = RechargeProtocol.orderInfo(remote.createRecharge(config, amount))
                if (!isCurrent(expected)) return@launch
                orderInfo = info
                paymentId = expected
                mutableState.value = mutableState.value.copy(placing = false, paying = true,
                    paymentRequestId = expected, notice = "正在打开支付宝…")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!isCurrent(expected)) return@launch
                if (error is CredentialExpiredException) {
                    clear()
                    onCredentialsExpired()
                    mutableState.value = RechargeState(visible = true, loginRequired = true)
                } else mutableState.value = mutableState.value.copy(placing = false,
                    notice = (error as? RechargeException)?.message ?: "未能获取支付订单，请检查网络后重试")
            }
        }
    }

    /** Atomically consume once before invoking the SDK, including after activity recreation. */
    fun claimPayment(id: Long): String? {
        if (!isCurrent(id) || paymentId != id) { synchronizeAccount(); return null }
        val info = orderInfo ?: return null
        orderInfo = null
        mutableState.value = mutableState.value.copy(paymentRequestId = null)
        return info
    }

    fun completePayment(id: Long, result: AlipayResult) {
        if (!isCurrent(id) || paymentId != id) return
        paymentId = null
        orderInfo = null
        mutableState.value = mutableState.value.copy(paying = false, paymentRequestId = null, notice = result.notice)
        // Returning from Alipay never credits a local balance, even for resultStatus=9000.
        refreshBalance()
    }

    fun refreshBalance() {
        if (!mutableState.value.visible || !isCurrent() || !currentConfig().hasAccount()) return
        balanceJob?.cancel()
        val request = ++balanceGeneration
        val config = currentConfig()
        mutableState.value = mutableState.value.copy(refreshingBalance = true, balanceError = null)
        balanceJob = scope.launch {
            try {
                val balance = RechargeProtocol.balance(remote.rechargeBalance(config))
                if (request != balanceGeneration || !isCurrent()) return@launch
                mutableState.value = mutableState.value.copy(balanceYuan = balance, refreshingBalance = false)
                onBalanceUpdated(balance)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (request != balanceGeneration || !isCurrent()) return@launch
                if (error is CredentialExpiredException) {
                    clear()
                    onCredentialsExpired()
                    mutableState.value = RechargeState(visible = true, loginRequired = true)
                } else mutableState.value = mutableState.value.copy(refreshingBalance = false,
                    balanceError = "余额暂未更新，请稍后刷新确认到账")
            }
        }
    }

    fun close() { if (!mutableState.value.busy) clear() }

    fun clear() {
        ++generation
        job?.cancel()
        job = null
        ++balanceGeneration
        balanceJob?.cancel()
        balanceJob = null
        orderInfo = null
        paymentId = null
        mutableState.value = RechargeState()
    }
}
