package cn.qzxy.hotwater

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

data class DashboardSnapshot(
    val balanceYuan: Double?,
    val balanceLabel: String,
    val userLabel: String,
    val deviceName: String,
    val locationLabel: String,
    val statusLabel: String,
    val orderNo: String?,
    val hasCachedOrder: Boolean,
    val isDeviceBusyByOther: Boolean = false,
    val isDeviceOnline: Boolean? = null,
    val usageStartedAtMillis: Long? = null,
    val autoClosedOrderNo: String? = null,
    val autoClosedHistoryId: Long? = null,
    val autoClosedUsageStartedAtMillis: Long? = null,
    val autoClosedUsageDurationMillis: Long? = null,
)

data class OperationResult(
    val title: String,
    val summary: String,
    val details: String,
    val snapshot: DashboardSnapshot? = null,
    val consumptionYuan: Double? = null,
    val usageDurationMillis: Long? = null,
    val usageStartedAtMillis: Long? = null,
    val historyId: Long? = null,
    val automaticClose: Boolean = false,
    val outcome: OperationOutcome,
)

enum class OperationOutcome {
    SUCCESS,
    PENDING,
    WARNING,
}

class HotWaterRepository(
    private val api: HotWaterRemoteDataSource,
    private val store: HotWaterSessionStore,
) {
    suspend fun check(config: HotWaterConfig): OperationResult = withContext(Dispatchers.IO) {
        require(config.isComplete()) { "Please complete all required config fields first." }
        if (!store.isConfigCurrent(config)) throw staleOperationCancellation()
        val stateGeneration = store.loadStateGeneration()
        // Probe authentication before entering the device workflow lock so an in-flight close
        // does not hide credential expiry behind a potentially long device request.
        val userInfo = api.userInfo(config)
        DEVICE_CONTROL_MUTEX.withLock {
            if (!store.isConfigCurrent(config)) throw staleOperationCancellation()
            reconcilePendingSettlements(config, stateGeneration)
            val refreshedSnapshot = refreshSnapshot(
                config,
                prefetchedUserInfo = userInfo,
                expectedStateGeneration = stateGeneration,
            )
            // buildSnapshot may have converted a server-side auto-closed session into a pending
            // settlement. Reconcile that newly-created bill in the same refresh so the UI and the
            // background worker see one consistent state immediately.
            if (refreshedSnapshot.autoClosedOrderNo != null) {
                reconcilePendingSettlements(config, stateGeneration)
            }
            val snapshot = guardPendingSettlementBalance(refreshedSnapshot)
            ensureStateGeneration(stateGeneration)
            OperationResult(
                title = if (snapshot.autoClosedOrderNo != null) "热水已自动关闭" else "余额已刷新",
                summary = buildSummary(snapshot),
                details = "",
                snapshot = snapshot,
                historyId = snapshot.autoClosedHistoryId,
                usageDurationMillis = snapshot.autoClosedUsageDurationMillis,
                usageStartedAtMillis = snapshot.autoClosedUsageStartedAtMillis,
                automaticClose = snapshot.autoClosedOrderNo != null,
                outcome = OperationOutcome.SUCCESS,
            )
        }
    }

    suspend fun open(
        config: HotWaterConfig,
        pollAttempts: Int = 5,
        pollDelayMs: Long = 2_000L,
    ): OperationResult = DEVICE_CONTROL_MUTEX.withLock {
        openLocked(config, pollAttempts, pollDelayMs)
    }

    private suspend fun openLocked(
        config: HotWaterConfig,
        pollAttempts: Int,
        pollDelayMs: Long,
    ): OperationResult = withContext(Dispatchers.IO) {
        require(config.isComplete()) { "Please complete all required config fields first." }
        if (!store.isConfigCurrent(config)) throw staleOperationCancellation()
        val stateGeneration = store.loadStateGeneration()
        reconcilePendingSettlements(config, stateGeneration)

        val nowMillis = System.currentTimeMillis()
        val unconfirmedClose = store.loadPendingSettlements()
            .filter { pending ->
                val ageMillis = nowMillis - pending.closedAtMillis
                !pending.closeConfirmed &&
                    pending.finalConsumptionYuan == null &&
                    ageMillis in 0L..MAX_UNCONFIRMED_CLOSE_GATE_MS
            }
            .maxByOrNull(PendingSettlement::closedAtMillis)
        if (unconfirmedClose != null) {
            ensureStateGeneration(stateGeneration)
            val snapshot = buildFallbackSnapshot(
                config = config,
                balancePayload = null,
                statusLabel = "关水确认中，请稍后再试。",
                orderNo = null,
            )
            ensureStateGeneration(stateGeneration)
            return@withContext OperationResult(
                title = "关水确认中",
                summary = "关水确认中，请稍后再试。",
                details = "Waiting for close confirmation, orderNo=${unconfirmedClose.orderNo}",
                snapshot = snapshot,
                outcome = OperationOutcome.PENDING,
            )
        }

        val detailBuilder = StringBuilder()
        val currentStatus = api.status(config)
        detailBuilder.appendLine("[Current Status]")
        detailBuilder.appendLine(currentStatus.toString(2))
        if (isDeviceBusyByOther(currentStatus)) {
            return@withContext buildDeviceBusyResult(config, currentStatus, detailBuilder, stateGeneration)
        }
        val existingOrderNo = findNestedValue(currentStatus, "orderNo")
            ?.toString()
            ?.takeIf { it.isNotBlank() }
        if (existingOrderNo != null) {
            if (!store.saveOrderNoUnlessPending(existingOrderNo, stateGeneration)) {
                ensureStateGeneration(stateGeneration)
                val snapshot = buildFallbackSnapshot(
                    config = config,
                    balancePayload = null,
                    statusLabel = "关水后的账单仍在同步，请稍后再试。",
                    orderNo = null,
                )
                return@withContext OperationResult(
                    title = "账单同步中",
                    summary = "关水后的账单仍在同步，请稍后再试。",
                    details = detailBuilder.toString().trim(),
                    snapshot = snapshot,
                    outcome = OperationOutcome.PENDING,
                )
            }
            val snapshot = tryApi {
                refreshSnapshotWithStatus(config, currentStatus, stateGeneration)
            }
                .getOrElse {
                    buildFallbackSnapshot(
                        config = config,
                        balancePayload = null,
                        statusLabel = "热水已经开启，无需重复操作。",
                        orderNo = existingOrderNo,
                    )
                }
            ensureStateGeneration(stateGeneration)
            return@withContext OperationResult(
                title = "热水已经开启",
                summary = "检测到当前账号已有用水订单。\n余额 ${snapshot.balanceLabel} 元",
                details = detailBuilder.toString().trim(),
                snapshot = snapshot,
                outcome = OperationOutcome.SUCCESS,
            )
        }
        if (isConfirmedWithoutActiveOrder(currentStatus)) {
            if (!store.clearUsageSessionForGeneration(stateGeneration)) {
                throw staleOperationCancellation()
            }
        }

        // The platform reserves 5 yuan as soon as water is opened. Capture the real
        // balance before that reservation so a later balance delta remains meaningful.
        val balanceBeforeOpenYuan = tryApi {
            HotWaterPayload.balanceYuan(api.balance(config))
        }.getOrNull()

        detailBuilder.appendLine()
        detailBuilder.appendLine("Sending open request...")
        ensureStateGeneration(stateGeneration)
        val openResponse = api.openHotWater(config)
        detailBuilder.appendLine()
        detailBuilder.appendLine("[Open Response]")
        detailBuilder.appendLine(openResponse.toString(2))

        if (isDeviceBusyByOther(openResponse)) {
            return@withContext buildDeviceBusyResult(config, openResponse, detailBuilder, stateGeneration)
        }

        var orderNo = findNestedValue(openResponse, "orderNo")?.toString()
        var success = false
        var finalOpenResult: JSONObject? = null
        var busyByOtherPayload: JSONObject? = null
        var deviceOnlineOverride: Boolean? = null
        var offlineProbeSeen = false
        var offlineProbeOrderNo: String? = null

        for (index in 0 until pollAttempts) {
            ensureStateGeneration(stateGeneration)
            delay(pollDelayMs)
            ensureStateGeneration(stateGeneration)
            val openResult = api.openResult(config, orderNo)
            finalOpenResult = openResult
            detailBuilder.appendLine()
            detailBuilder.appendLine("[Open Result #${index + 1}]")
            detailBuilder.appendLine(openResult.toString(2))

            val responseOrderNo = findNestedValue(openResult, "orderNo")
                ?.toString()
                ?.takeIf { it.isNotBlank() }
            when (val probeState = openResultOnlineState(openResult, orderNo)) {
                false -> {
                    deviceOnlineOverride = false
                    offlineProbeSeen = true
                    if (offlineProbeOrderNo.isNullOrBlank()) {
                        offlineProbeOrderNo = orderNo ?: responseOrderNo
                    }
                }
                true -> {
                    // A positive result must be a confirmed result for this request. A generic
                    // online-looking field cannot clear an earlier communication failure.
                    val matchesOfflineProbe = !offlineProbeSeen ||
                        (!offlineProbeOrderNo.isNullOrBlank() &&
                            responseOrderNo == offlineProbeOrderNo)
                    val confirmedResult = isConfirmedOpenResult(openResult, orderNo)
                    if (confirmedResult && matchesOfflineProbe) {
                        deviceOnlineOverride = true
                    }
                }
                null -> Unit
            }

            if (isDeviceBusyByOther(openResult)) {
                busyByOtherPayload = openResult
                break
            }

            if (orderNo.isNullOrBlank()) {
                orderNo = responseOrderNo
            }

            if (isOpenResultSuccessful(openResult, orderNo)) {
                success = true
                break
            }
        }

        if (busyByOtherPayload != null) {
            return@withContext buildDeviceBusyResult(config, busyByOtherPayload, detailBuilder, stateGeneration)
        }

        var consumeResult: JSONObject? = null
        if (!orderNo.isNullOrBlank()) {
            if (!store.saveOrderNoForGeneration(orderNo, stateGeneration)) {
                throw staleOperationCancellation()
            }
            consumeResult = tryApi { api.consumeResult(config, orderNo) }.getOrNull()
            if (consumeResult != null) {
                detailBuilder.appendLine()
                detailBuilder.appendLine("[Consume Result]")
                detailBuilder.appendLine(consumeResult.toString(2))
            }
        }

        val snapshot = tryApi {
            refreshSnapshot(
                config,
                preferredUsageStartBalanceYuan = balanceBeforeOpenYuan,
                allowSnapshotUsageBaseline = balanceBeforeOpenYuan != null,
                expectedStateGeneration = stateGeneration,
                deviceOnlineOverride = deviceOnlineOverride,
            )
        }.getOrElse {
            buildFallbackSnapshot(
                config = config,
                balancePayload = null,
                statusLabel = if (success) "热水已开启，可以直接关闭。" else "开热水请求已发送，等待设备确认。",
                orderNo = store.loadOrderNo() ?: orderNo,
                deviceOnlineOverride = deviceOnlineOverride,
            )
        }
        val snapshotWithUsageSession = ensureUsageSession(
            snapshot,
            preferredStartBalanceYuan = balanceBeforeOpenYuan,
            allowSnapshotBalanceBaseline = balanceBeforeOpenYuan != null,
            expectedStateGeneration = stateGeneration,
        )
        ensureStateGeneration(stateGeneration)

        val deviceOffline = snapshotWithUsageSession.isDeviceOnline == false
        val summary = when {
            deviceOffline -> "$DEVICE_OFFLINE_STATUS_MESSAGE\n余额 ${snapshotWithUsageSession.balanceLabel} 元"
            success -> "热水已开启。\n余额 ${snapshotWithUsageSession.balanceLabel} 元"
            finalOpenResult != null -> "开热水请求已发送。\n余额 ${snapshotWithUsageSession.balanceLabel} 元"
            else -> buildSummary(snapshotWithUsageSession)
        }

        OperationResult(
            title = when {
                deviceOffline -> "热水器不在线"
                success -> "开热水完成"
                else -> "开热水请求已发送"
            },
            summary = summary,
            details = detailBuilder.toString().trim(),
            snapshot = snapshotWithUsageSession,
            outcome = when {
                deviceOffline -> OperationOutcome.WARNING
                success -> OperationOutcome.SUCCESS
                else -> OperationOutcome.PENDING
            },
        )
    }

    suspend fun close(
        config: HotWaterConfig,
        pollAttempts: Int = 5,
        pollDelayMs: Long = 600L,
        settlementRefreshAttempts: Int = 0,
        settlementRefreshDelayMs: Long = 0L,
    ): OperationResult = DEVICE_CONTROL_MUTEX.withLock {
        SETTLEMENT_RECONCILIATION_MUTEX.withLock {
            closeLocked(
                config,
                pollAttempts,
                pollDelayMs,
                settlementRefreshAttempts,
                settlementRefreshDelayMs,
            )
        }
    }

    private suspend fun closeLocked(
        config: HotWaterConfig,
        pollAttempts: Int,
        pollDelayMs: Long,
        settlementRefreshAttempts: Int,
        settlementRefreshDelayMs: Long,
    ): OperationResult = withContext(Dispatchers.IO) {
        require(config.isComplete()) { "Please complete all required config fields first." }
        if (!store.isConfigCurrent(config)) throw staleOperationCancellation()
        val stateGeneration = store.loadStateGeneration()

        val usageSession = store.loadUsageSession()
        val detailBuilder = StringBuilder()
        val cachedOrderNo = store.loadOrderNo()
        if (!cachedOrderNo.isNullOrBlank()) {
            findPendingClose(cachedOrderNo)?.let { pending ->
                ensureStateGeneration(stateGeneration)
                return@withContext buildPendingCloseResult(config, pending, detailBuilder)
            }
        }
        val (status, queriedActiveBalanceYuan) = coroutineScope {
            val balance = async {
                tryApi { HotWaterPayload.balanceYuan(api.balance(config)) }.getOrNull()
            }
            api.status(config) to balance.await()
        }
        var orderNo = findNestedValue(status, "orderNo")?.toString()
        detailBuilder.appendLine("[Current Status]")
        detailBuilder.appendLine(status.toString(2))

        if (isDeviceBusyByOther(status)) {
            return@withContext buildDeviceBusyResult(config, status, detailBuilder, stateGeneration)
        }

        if (orderNo.isNullOrBlank()) {
            orderNo = cachedOrderNo
        }

        findPendingClose(orderNo)?.let { pending ->
            ensureStateGeneration(stateGeneration)
            return@withContext buildPendingCloseResult(config, pending, detailBuilder)
        }

        if (orderNo.isNullOrBlank()) {
            findPendingClose(null)?.let { pending ->
                ensureStateGeneration(stateGeneration)
                return@withContext buildPendingCloseResult(config, pending, detailBuilder)
            }
            throw IllegalStateException(
                "No active orderNo found. Open hot water once in the app or import a newer capture file first.\n\n$detailBuilder",
            )
        }
        val activeOrderNo = orderNo

        val storedStartBalanceYuan = usageSession?.let(
            HotWaterSettlement::balanceBeforePreauthorization,
        )
        val activeBalanceYuan = storedStartBalanceYuan
            ?.let { queriedActiveBalanceYuan ?: it.minus(HOT_WATER_PREAUTHORIZATION_YUAN) }
            ?: queriedActiveBalanceYuan
        val startBalanceYuan = storedStartBalanceYuan
            ?: activeBalanceYuan?.plus(HOT_WATER_PREAUTHORIZATION_YUAN)
        val closedAtMillis = System.currentTimeMillis()
        val historyId = closedAtMillis
        val usageDurationMillis = usageSession?.let { closedAtMillis - it.startedAtMillis }

        if (
            HotWaterSettlement.canAutoCloseUsage(
                orderNo = activeOrderNo,
                session = usageSession,
                remoteNoActiveOrder = isConfirmedWithoutActiveOrder(status),
                nowMillis = closedAtMillis,
                minimumAgeMillis = MIN_AUTO_CLOSE_USAGE_AGE_MS,
            )
        ) {
            return@withContext buildAutomaticCloseResult(
                config = config,
                orderNo = activeOrderNo,
                usageSession = usageSession!!,
                closedAtMillis = closedAtMillis,
                expectedStateGeneration = stateGeneration,
            )
        }

        detailBuilder.appendLine()
        detailBuilder.appendLine("Sending close request, orderNo=$activeOrderNo")
        ensureStateGeneration(stateGeneration)
        val closeResponse = api.closeHotWater(config, activeOrderNo)
        detailBuilder.appendLine()
        detailBuilder.appendLine("[Close Response]")
        detailBuilder.appendLine(closeResponse.toString(2))

        var closeAccepted = isAcceptedResponse(closeResponse)
        var closeConfirmed = false
        var provisionalConsumptionYuan = HotWaterPayload.consumptionYuan(closeResponse)
            .takeIf { isConfirmedResponse(closeResponse) }
        var finalConsumptionYuan: Double? = null
        var zeroConsumptionReported = reportsZeroConsumption(closeResponse)

        fun persistPendingSettlement() {
            if (!closeAccepted && !closeConfirmed) return
            val persisted = store.savePendingSettlementAndEndUsage(
                PendingSettlement(
                    historyId = historyId,
                    orderNo = activeOrderNo,
                    closedAtMillis = closedAtMillis,
                    usageStartedAtMillis = usageSession?.startedAtMillis,
                    usageDurationMillis = usageDurationMillis,
                    startBalanceYuan = startBalanceYuan,
                    activeBalanceYuan = activeBalanceYuan,
                    provisionalConsumptionYuan = provisionalConsumptionYuan,
                    finalConsumptionYuan = finalConsumptionYuan,
                    zeroConsumptionReported = zeroConsumptionReported,
                    closeConfirmed = closeConfirmed,
                    stateGeneration = stateGeneration,
                ),
                expectedStateGeneration = stateGeneration,
            )
            if (!persisted) throw staleOperationCancellation()
        }

        // closeOrder success means the server accepted the command. Persist the bill
        // immediately and let background reconciliation confirm the device and amount.
        if (closeAccepted) persistPendingSettlement()

        if (!closeAccepted) {
            for (index in 0 until pollAttempts) {
                ensureStateGeneration(stateGeneration)
                if (pollDelayMs > 0L) delay(pollDelayMs)
                ensureStateGeneration(stateGeneration)
                val closeResult = tryApi { api.closeResult(config, activeOrderNo) }.getOrNull()
                if (closeResult == null) {
                    detailBuilder.appendLine()
                    detailBuilder.appendLine("[Close Result #${index + 1}] unavailable")
                    continue
                }

                detailBuilder.appendLine()
                detailBuilder.appendLine("[Close Result #${index + 1}]")
                detailBuilder.appendLine(closeResult.toString(2))
                if (isConfirmedResponse(closeResult)) {
                    closeConfirmed = true
                    closeAccepted = true
                    provisionalConsumptionYuan = HotWaterPayload.consumptionYuan(closeResult)
                        ?: provisionalConsumptionYuan
                    zeroConsumptionReported = zeroConsumptionReported ||
                        reportsZeroConsumption(closeResult)
                    persistPendingSettlement()
                    break
                }
            }
        }

        var closeStatusLabel = if (closeConfirmed) {
            "热水已关闭。"
        } else if (closeAccepted) {
            "关热水请求已受理，消费金额可能仍在同步。"
        } else {
            "关热水请求已发送，等待设备确认。"
        }

        val usageEnded = closeAccepted || closeConfirmed
        val settlementRefresh = if (usageEnded && settlementRefreshAttempts > 0) {
            refreshSettlementAfterClose(
                config = config,
                orderNo = activeOrderNo,
                forcedStatusLabel = closeStatusLabel,
                activeBalanceYuan = activeBalanceYuan,
                startBalanceYuan = startBalanceYuan,
                initialFinalConsumptionYuan = finalConsumptionYuan,
                initialZeroConsumptionReported = zeroConsumptionReported,
                refreshAttempts = settlementRefreshAttempts,
                refreshDelayMs = settlementRefreshDelayMs,
                detailBuilder = detailBuilder,
            )
        } else {
            SettlementRefreshResult()
        }
        finalConsumptionYuan = settlementRefresh.finalConsumptionYuan ?: finalConsumptionYuan
        zeroConsumptionReported = zeroConsumptionReported || settlementRefresh.zeroConsumptionReported
        if (
            !closeConfirmed &&
            (finalConsumptionYuan != null || settlementRefresh.settledBalanceYuan != null)
        ) {
            closeConfirmed = true
            closeStatusLabel = "热水已关闭。"
        }
        val refreshedSnapshot = settlementRefresh.snapshot
        val snapshot = if (usageEnded) {
            refreshedSnapshot ?: run {
                buildFallbackSnapshot(
                    config = config,
                    balancePayload = null,
                    statusLabel = closeStatusLabel,
                    orderNo = null,
                )
            }
        } else {
            tryApi {
                refreshSnapshot(config, expectedStateGeneration = stateGeneration)
            }.getOrElse {
                buildFallbackSnapshot(
                    config = config,
                    balancePayload = null,
                    statusLabel = closeStatusLabel,
                    orderNo = store.loadOrderNo(),
                )
            }
        }

        val consumptionYuan = if (usageEnded) {
            finalConsumptionYuan ?: settlementRefresh.settledBalanceYuan?.let {
                HotWaterSettlement.resolveConsumptionYuan(
                    provisionalConsumptionYuan = provisionalConsumptionYuan,
                    startBalanceYuan = startBalanceYuan,
                    settledBalanceYuan = it,
                )
            }
        } else {
            null
        }
        if (usageEnded) {
            val balanceGuardRequired = finalConsumptionYuan != null &&
                startBalanceYuan != null &&
                settlementRefresh.settledBalanceYuan == null
            if (consumptionYuan != null && !balanceGuardRequired) {
                store.removePendingSettlement(activeOrderNo, stateGeneration)
            } else {
                persistPendingSettlement()
            }
        }
        ensureStateGeneration(stateGeneration)

        OperationResult(
            title = when {
                closeConfirmed -> "关热水完成"
                closeAccepted -> "关热水已受理"
                else -> "关热水请求已发送"
            },
            summary = if (usageEnded) "$closeStatusLabel\n余额 ${snapshot.balanceLabel} 元" else buildSummary(snapshot),
            details = detailBuilder.toString().trim(),
            snapshot = snapshot,
            consumptionYuan = consumptionYuan,
            usageDurationMillis = if (usageEnded) usageDurationMillis else null,
            usageStartedAtMillis = if (usageEnded) usageSession?.startedAtMillis else null,
            historyId = if (usageEnded) historyId else null,
            outcome = if (closeConfirmed) OperationOutcome.SUCCESS else OperationOutcome.PENDING,
        )
    }

    private fun findPendingClose(orderNo: String?): PendingSettlement? {
        val candidates = store.loadPendingSettlements()
        return if (orderNo.isNullOrBlank()) {
            candidates.maxWithOrNull(
                compareBy<PendingSettlement> { it.closedAtMillis }
                    .thenBy { it.historyId },
            )
        } else {
            candidates.firstOrNull { it.orderNo == orderNo }
        }
    }

    private fun buildPendingCloseResult(
        config: HotWaterConfig,
        pending: PendingSettlement,
        detailBuilder: StringBuilder,
    ): OperationResult {
        val confirmed = pending.closeConfirmed || pending.finalConsumptionYuan != null
        val statusLabel = if (confirmed) {
            "热水已关闭，余额仍在同步。"
        } else {
            "关热水请求已受理，正在等待设备确认。"
        }
        val snapshot = buildFallbackSnapshot(
            config = config,
            balancePayload = null,
            statusLabel = statusLabel,
            orderNo = null,
        )
        return OperationResult(
            title = if (confirmed) "关热水完成" else "关热水已受理",
            summary = "$statusLabel\n余额 -- 元",
            details = detailBuilder.toString().trim(),
            snapshot = snapshot,
            consumptionYuan = pending.finalConsumptionYuan,
            usageDurationMillis = pending.usageDurationMillis,
            usageStartedAtMillis = pending.usageStartedAtMillis,
            historyId = pending.historyId,
            outcome = if (confirmed) OperationOutcome.SUCCESS else OperationOutcome.PENDING,
        )
    }

    private fun buildAutomaticCloseResult(
        config: HotWaterConfig,
        orderNo: String,
        usageSession: UsageSession,
        closedAtMillis: Long,
        expectedStateGeneration: Long,
    ): OperationResult {
        val usageDurationMillis = (closedAtMillis - usageSession.startedAtMillis).coerceAtLeast(0L)
        val persisted = store.savePendingSettlementAndEndUsage(
            PendingSettlement(
                historyId = closedAtMillis,
                orderNo = orderNo,
                closedAtMillis = closedAtMillis,
                usageStartedAtMillis = usageSession.startedAtMillis,
                usageDurationMillis = usageDurationMillis,
                startBalanceYuan = HotWaterSettlement.balanceBeforePreauthorization(usageSession),
                activeBalanceYuan = HotWaterSettlement.activeBalanceYuan(usageSession),
                provisionalConsumptionYuan = null,
                finalConsumptionYuan = null,
                zeroConsumptionReported = false,
                closeConfirmed = true,
                stateGeneration = expectedStateGeneration,
            ),
            expectedStateGeneration = expectedStateGeneration,
        )
        if (!persisted) {
            throw staleOperationCancellation()
        }

        val snapshot = buildFallbackSnapshot(
            config = config,
            balancePayload = null,
            statusLabel = "检测到热水已自动关闭，账单正在同步。",
            orderNo = null,
        )
        return OperationResult(
            title = "热水已自动关闭",
            summary = "检测到热水已自动关闭，账单正在同步。\n余额 ${snapshot.balanceLabel} 元",
            details = "服务端已确认当前没有活动订单，未重复发送关水请求。",
            snapshot = snapshot,
            usageDurationMillis = usageDurationMillis,
            usageStartedAtMillis = usageSession.startedAtMillis,
            historyId = closedAtMillis,
            automaticClose = true,
            outcome = OperationOutcome.SUCCESS,
        )
    }

    private suspend fun refreshSnapshot(
        config: HotWaterConfig,
        preferredUsageStartBalanceYuan: Double? = null,
        allowSnapshotUsageBaseline: Boolean = true,
        prefetchedUserInfo: JSONObject? = null,
        expectedStateGeneration: Long,
        deviceOnlineOverride: Boolean? = null,
    ): DashboardSnapshot {
        val userInfo = prefetchedUserInfo ?: api.userInfo(config)
        val deviceInfo = api.deviceInfo(config)
        val balance = api.balance(config)
        val status = api.status(config)
        return ensureUsageSession(
            buildSnapshot(
                config,
                userInfo,
                deviceInfo,
                balance,
                status,
                expectedStateGeneration,
                deviceOnlineOverride,
            ),
            preferredUsageStartBalanceYuan,
            allowSnapshotUsageBaseline,
            expectedStateGeneration,
        )
    }

    private suspend fun refreshSnapshotWithStatus(
        config: HotWaterConfig,
        statusPayload: JSONObject,
        expectedStateGeneration: Long,
        deviceOnlineOverride: Boolean? = null,
    ): DashboardSnapshot {
        val userInfo = api.userInfo(config)
        val deviceInfo = api.deviceInfo(config)
        val balance = api.balance(config)
        return ensureUsageSession(
            buildSnapshot(
                config,
                userInfo,
                deviceInfo,
                balance,
                statusPayload,
                expectedStateGeneration,
                deviceOnlineOverride,
            ),
            expectedStateGeneration = expectedStateGeneration,
        )
    }

    private suspend fun buildDeviceBusyResult(
        config: HotWaterConfig,
        statusPayload: JSONObject,
        detailBuilder: StringBuilder,
        expectedStateGeneration: Long,
    ): OperationResult {
        if (!store.clearActiveUsageForGeneration(expectedStateGeneration)) {
            throw staleOperationCancellation()
        }
        val snapshot = tryApi {
            refreshSnapshotWithStatus(config, statusPayload, expectedStateGeneration)
        }.getOrElse {
            buildFallbackSnapshot(
                config = config,
                balancePayload = null,
                statusLabel = DEVICE_BUSY_BY_OTHER_MESSAGE,
                orderNo = null,
                isDeviceBusyByOther = true,
            )
        }
        ensureStateGeneration(expectedStateGeneration)
        return OperationResult(
            title = "设备正在使用中",
            summary = "$DEVICE_BUSY_BY_OTHER_MESSAGE\n余额 ${snapshot.balanceLabel} 元",
            details = detailBuilder.toString().trim(),
            snapshot = snapshot,
            outcome = OperationOutcome.WARNING,
        )
    }

    private data class SettlementRefreshResult(
        val snapshot: DashboardSnapshot? = null,
        val settledBalanceYuan: Double? = null,
        val finalConsumptionYuan: Double? = null,
        val zeroConsumptionReported: Boolean = false,
    )

    private suspend fun refreshSettlementAfterClose(
        config: HotWaterConfig,
        orderNo: String,
        forcedStatusLabel: String,
        activeBalanceYuan: Double?,
        startBalanceYuan: Double?,
        initialFinalConsumptionYuan: Double?,
        initialZeroConsumptionReported: Boolean,
        refreshAttempts: Int,
        refreshDelayMs: Long,
        detailBuilder: StringBuilder,
    ): SettlementRefreshResult {
        val userInfo = tryApi { api.userInfo(config) }.getOrDefault(JSONObject())
        val deviceInfo = tryApi { api.deviceInfo(config) }.getOrDefault(JSONObject())
        var latestSnapshot: DashboardSnapshot? = null
        var finalConsumptionYuan = initialFinalConsumptionYuan
        var zeroConsumptionReported = initialZeroConsumptionReported

        // Both endpoints can lag independently. Poll the final order amount and the
        // wallet together so an exact 5-yuan use is distinguishable from preauthorization.
        repeat(refreshAttempts.coerceAtLeast(MIN_SETTLEMENT_REFRESH_ATTEMPTS)) { index ->
            if (refreshDelayMs > 0L) {
                delay(refreshDelayMs)
            }

            val consumeResult = tryApi { api.consumeResult(config, orderNo) }.getOrNull()
            if (consumeResult != null) {
                detailBuilder.appendLine()
                detailBuilder.appendLine("[Settlement Result #${index + 1}]")
                detailBuilder.appendLine(consumeResult.toString(2))
                finalConsumptionYuan = finalConsumptionFrom(consumeResult)
                    ?: finalConsumptionYuan
                zeroConsumptionReported = zeroConsumptionReported ||
                    reportsZeroConsumption(consumeResult)
            }

            val balance = tryApi { api.balance(config) }.getOrNull() ?: return@repeat
            latestSnapshot = buildSnapshotWithoutStatus(
                config,
                userInfo,
                deviceInfo,
                balance,
                forcedStatusLabel,
            )

            val finalBalanceConfirmed = finalConsumptionYuan?.let { consumption ->
                startBalanceYuan?.minus(consumption)?.let { expectedBalanceYuan ->
                    HotWaterSettlement.balancesMatch(
                        expectedBalanceYuan,
                        latestSnapshot.balanceYuan,
                    )
                }
            } == true
            val zeroBalanceConfirmed = finalConsumptionYuan == null &&
                zeroConsumptionReported &&
                HotWaterSettlement.balancesMatch(startBalanceYuan, latestSnapshot.balanceYuan)
            val balanceDeltaConfirmed = finalConsumptionYuan == null &&
                HotWaterSettlement.hasSettledBalanceChanged(
                    activeBalanceYuan,
                    latestSnapshot.balanceYuan,
                )
            if (finalBalanceConfirmed || zeroBalanceConfirmed || balanceDeltaConfirmed) {
                return SettlementRefreshResult(
                    snapshot = latestSnapshot,
                    settledBalanceYuan = latestSnapshot.balanceYuan,
                    finalConsumptionYuan = if (zeroBalanceConfirmed) 0.0 else finalConsumptionYuan,
                    zeroConsumptionReported = zeroConsumptionReported,
                )
            }
        }

        val canUseLatestUnverifiedBalance = activeBalanceYuan == null &&
            (finalConsumptionYuan == null || startBalanceYuan == null)
        return SettlementRefreshResult(
            snapshot = if (canUseLatestUnverifiedBalance) {
                latestSnapshot
            } else {
                null
            },
            settledBalanceYuan = null,
            finalConsumptionYuan = finalConsumptionYuan,
            zeroConsumptionReported = zeroConsumptionReported,
        )
    }

    private suspend fun reconcilePendingSettlements(
        config: HotWaterConfig,
        expectedStateGeneration: Long,
    ) {
        withTimeoutOrNull(MAX_RECONCILIATION_LOCK_MS) {
            SETTLEMENT_RECONCILIATION_MUTEX.withLock {
                reconcilePendingSettlementsLocked(config, expectedStateGeneration)
            }
        }
    }

    private suspend fun reconcilePendingSettlementsLocked(
        config: HotWaterConfig,
        expectedStateGeneration: Long,
    ) {
        ensureStateGeneration(expectedStateGeneration)
        val pendingSettlements = store.loadPendingSettlements()
        ensureStateGeneration(expectedStateGeneration)
        if (pendingSettlements.any { it.stateGeneration != expectedStateGeneration }) {
            throw staleOperationCancellation()
        }
        val latestPending = pendingSettlements.maxWithOrNull(
            compareBy<PendingSettlement> { it.closedAtMillis }
                .thenBy { it.historyId },
        )
        val normalizedPendingSettlements = pendingSettlements.map { pending ->
            if (pending.orderNo != latestPending?.orderNo && pending.balanceFallbackAllowed) {
                pending.copy(balanceFallbackAllowed = false).also(store::savePendingSettlement)
            } else {
                pending
            }
        }
        val reconciliationStartedAtMillis = System.currentTimeMillis()
        var remoteHasNoActiveOrder: Boolean? = null

        suspend fun canUseCurrentBalance(pending: PendingSettlement): Boolean {
            if (!pending.balanceFallbackAllowed) return false
            if (pending.orderNo != latestPending?.orderNo) return false
            val ageMillis = reconciliationStartedAtMillis - pending.closedAtMillis
            if (ageMillis !in 0L..MAX_PENDING_BALANCE_FALLBACK_AGE_MS) return false
            if (store.loadOrderNo() != null || store.loadUsageSession() != null) return false

            if (remoteHasNoActiveOrder == null) {
                remoteHasNoActiveOrder = tryApi { api.status(config) }
                    .getOrNull()
                    ?.let(::isConfirmedWithoutActiveOrder)
                    ?: false
            }
            return remoteHasNoActiveOrder == true
        }

        normalizedPendingSettlements.forEach { pending ->
            var finalConsumptionYuan = pending.finalConsumptionYuan
            var zeroConsumptionReported = pending.zeroConsumptionReported
            var closeConfirmed = pending.closeConfirmed
            var provisionalConsumptionYuan = pending.provisionalConsumptionYuan

            if (!closeConfirmed) {
                val closeResult = tryApi {
                    api.closeResult(config, pending.orderNo)
                }.getOrNull()
                if (closeResult?.let(::isConfirmedResponse) == true) {
                    closeConfirmed = true
                    provisionalConsumptionYuan = HotWaterPayload.consumptionYuan(closeResult)
                        ?: provisionalConsumptionYuan
                    zeroConsumptionReported = zeroConsumptionReported ||
                        reportsZeroConsumption(closeResult)
                }
            }

            if (finalConsumptionYuan == null) {
                val consumeResult = tryApi {
                    api.consumeResult(config, pending.orderNo)
                }.getOrNull()
                finalConsumptionYuan = consumeResult?.let(::finalConsumptionFrom)
                zeroConsumptionReported = zeroConsumptionReported ||
                    (consumeResult?.let(::reportsZeroConsumption) == true)
            }

            if (finalConsumptionYuan != null) {
                store.updateHistorySettlement(
                    pending.copy(closeConfirmed = true),
                    finalConsumptionYuan,
                )
                closeConfirmed = true
                val ageMillis = reconciliationStartedAtMillis - pending.closedAtMillis
                if (
                    pending.startBalanceYuan == null ||
                    ageMillis !in 0L..MAX_PENDING_BALANCE_FALLBACK_AGE_MS
                ) {
                    store.removePendingSettlement(pending.orderNo, pending.stateGeneration)
                    return@forEach
                }
            }

            // A wallet balance is account-wide state, not an immutable order result.
            // Only the newest, still-fresh settlement may use it as a fallback, and
            // only after both local and remote state confirm there is no newer usage.
            if (!canUseCurrentBalance(pending)) {
                if (
                    pending.zeroConsumptionReported != zeroConsumptionReported ||
                    pending.closeConfirmed != closeConfirmed ||
                    pending.provisionalConsumptionYuan != provisionalConsumptionYuan ||
                    pending.finalConsumptionYuan != finalConsumptionYuan
                ) {
                    store.savePendingSettlement(
                        pending.copy(
                            provisionalConsumptionYuan = provisionalConsumptionYuan,
                            finalConsumptionYuan = finalConsumptionYuan,
                            zeroConsumptionReported = zeroConsumptionReported,
                            closeConfirmed = closeConfirmed,
                        ),
                    )
                }
                return@forEach
            }
            closeConfirmed = true

            val balanceYuan = tryApi {
                HotWaterPayload.balanceYuan(api.balance(config))
            }.getOrNull()
            val finalBalanceConfirmed = finalConsumptionYuan?.let { consumption ->
                pending.startBalanceYuan?.minus(consumption)?.let { expectedBalanceYuan ->
                    HotWaterSettlement.balancesMatch(expectedBalanceYuan, balanceYuan)
                }
            } == true
            if (finalBalanceConfirmed) {
                store.removePendingSettlement(pending.orderNo, pending.stateGeneration)
                return@forEach
            }
            val settledBalanceYuan = balanceYuan?.takeIf {
                HotWaterSettlement.hasSettledBalanceChanged(pending.activeBalanceYuan, it)
            }
            val zeroBalanceConfirmed = zeroConsumptionReported &&
                HotWaterSettlement.balancesMatch(pending.startBalanceYuan, balanceYuan)
            val resolvedConsumptionYuan = if (finalConsumptionYuan != null) {
                null
            } else if (zeroBalanceConfirmed) {
                0.0
            } else {
                settledBalanceYuan?.let {
                    HotWaterSettlement.resolveConsumptionYuan(
                        provisionalConsumptionYuan = provisionalConsumptionYuan,
                        startBalanceYuan = pending.startBalanceYuan,
                        settledBalanceYuan = it,
                    )
                }
            }
            if (resolvedConsumptionYuan != null) {
                store.updateHistorySettlement(pending, resolvedConsumptionYuan)
                store.removePendingSettlement(pending.orderNo, pending.stateGeneration)
            } else if (
                pending.finalConsumptionYuan != finalConsumptionYuan ||
                pending.zeroConsumptionReported != zeroConsumptionReported ||
                pending.closeConfirmed != closeConfirmed ||
                pending.provisionalConsumptionYuan != provisionalConsumptionYuan
            ) {
                store.savePendingSettlement(
                    pending.copy(
                        provisionalConsumptionYuan = provisionalConsumptionYuan,
                        finalConsumptionYuan = finalConsumptionYuan,
                        zeroConsumptionReported = zeroConsumptionReported,
                        closeConfirmed = closeConfirmed,
                    ),
                )
            }
        }
    }

    private fun guardPendingSettlementBalance(snapshot: DashboardSnapshot): DashboardSnapshot {
        if (snapshot.hasCachedOrder || snapshot.balanceYuan == null) return snapshot
        val pending = store.loadPendingSettlements()
            .filter(PendingSettlement::balanceFallbackAllowed)
            .filter { pending ->
                val ageMillis = System.currentTimeMillis() - pending.closedAtMillis
                ageMillis in 0L..MAX_PENDING_BALANCE_FALLBACK_AGE_MS
            }
            .maxWithOrNull(
                compareBy<PendingSettlement> { it.closedAtMillis }
                    .thenBy { it.historyId },
            )
            ?: return snapshot
        if (store.loadOrderNo() != null || store.loadUsageSession() != null) return snapshot

        val expectedBalanceYuan = pending.finalConsumptionYuan?.let { consumption ->
            pending.startBalanceYuan?.minus(consumption)
        }
        val balanceStillStale = when {
            expectedBalanceYuan != null -> !HotWaterSettlement.balancesMatch(
                expectedBalanceYuan,
                snapshot.balanceYuan,
            )
            pending.activeBalanceYuan != null -> HotWaterSettlement.balancesMatch(
                pending.activeBalanceYuan,
                snapshot.balanceYuan,
            )
            else -> false
        }
        if (!balanceStillStale) return snapshot

        val statusLabel = if (pending.finalConsumptionYuan != null) {
            "消费金额已确认，余额仍在同步。"
        } else if (pending.closeConfirmed) {
            "热水已关闭，账单和余额正在同步。"
        } else {
            "关热水请求已受理，正在等待设备确认。"
        }
        return snapshot.copy(
            balanceYuan = null,
            balanceLabel = "--",
            statusLabel = statusLabel,
        )
    }

    private fun buildSnapshot(
        config: HotWaterConfig,
        userInfo: JSONObject,
        deviceInfo: JSONObject,
        balancePayload: JSONObject,
        statusPayload: JSONObject,
        expectedStateGeneration: Long,
        deviceOnlineOverride: Boolean? = null,
    ): DashboardSnapshot {
        ensureStateGeneration(expectedStateGeneration)
        val userData = userInfo.optJSONObject("data") ?: JSONObject()
        val deviceData = deviceInfo.optJSONObject("data") ?: JSONObject()
        val busyByOther = isDeviceBusyByOther(statusPayload)

        val extractedOrderNo = findNestedValue(statusPayload, "orderNo")?.toString()?.takeIf { it.isNotBlank() }
        var autoClosedOrderNo: String? = null
        var autoClosedHistoryId: Long? = null
        var autoClosedUsageStartedAtMillis: Long? = null
        var autoClosedUsageDurationMillis: Long? = null
        if (busyByOther) {
            if (!store.clearActiveUsageForGeneration(expectedStateGeneration)) {
                throw staleOperationCancellation()
            }
        } else if (!extractedOrderNo.isNullOrBlank()) {
            // The store rechecks both pending settlements and the close grace period
            // under one process-wide lock before persisting a recovered order.
            if (!store.saveOrderNoUnlessPending(extractedOrderNo, expectedStateGeneration)) {
                ensureStateGeneration(expectedStateGeneration)
            }
        } else if (isConfirmedWithoutActiveOrder(statusPayload)) {
            val cachedOrderNo = store.loadOrderNo()
            val usageSession = store.loadUsageSession()
            val hasPendingSettlement = cachedOrderNo?.let { orderNo ->
                store.loadPendingSettlements().any { it.orderNo == orderNo }
            } == true
            val nowMillis = System.currentTimeMillis()
            if (
                !hasPendingSettlement &&
                HotWaterSettlement.canAutoCloseUsage(
                    orderNo = cachedOrderNo,
                    session = usageSession,
                    remoteNoActiveOrder = true,
                    nowMillis = nowMillis,
                    minimumAgeMillis = MIN_AUTO_CLOSE_USAGE_AGE_MS,
                )
            ) {
                val activeOrderNo = cachedOrderNo!!
                val session = usageSession!!
                val persisted = store.savePendingSettlementAndEndUsage(
                    PendingSettlement(
                        historyId = nowMillis,
                        orderNo = activeOrderNo,
                        closedAtMillis = nowMillis,
                        usageStartedAtMillis = session.startedAtMillis,
                        usageDurationMillis = (nowMillis - session.startedAtMillis).coerceAtLeast(0L),
                        startBalanceYuan = HotWaterSettlement.balanceBeforePreauthorization(session),
                        activeBalanceYuan = HotWaterSettlement.activeBalanceYuan(session),
                        provisionalConsumptionYuan = null,
                        finalConsumptionYuan = null,
                        zeroConsumptionReported = false,
                        closeConfirmed = true,
                        stateGeneration = expectedStateGeneration,
                    ),
                    expectedStateGeneration = expectedStateGeneration,
                )
                if (!persisted) {
                    throw staleOperationCancellation()
                }
                autoClosedOrderNo = activeOrderNo
                autoClosedHistoryId = nowMillis
                autoClosedUsageStartedAtMillis = session.startedAtMillis
                autoClosedUsageDurationMillis = (nowMillis - session.startedAtMillis).coerceAtLeast(0L)
            }
        }

        val cachedOrderNo = if (busyByOther) null else store.loadOrderNo()
        ensureStateGeneration(expectedStateGeneration)
        val balanceYuan = HotWaterPayload.balanceYuan(balancePayload)
        val locationLabel = listOf(
            deviceData.optString("areaName"),
            deviceData.optString("buildingName"),
            deviceData.optString("roomName"),
        )
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { deviceData.optString("deviceLocation", "位置待刷新") }

        val deviceOnline = deviceOnlineState(statusPayload, deviceOnlineOverride)
        val statusLabel = when {
            deviceOnline == false -> DEVICE_OFFLINE_STATUS_MESSAGE
            busyByOther -> DEVICE_BUSY_BY_OTHER_MESSAGE
            autoClosedOrderNo != null -> "检测到热水已自动关闭，账单正在同步。"
            !cachedOrderNo.isNullOrBlank() -> "已缓存订单号，点击主按钮将尝试关热水。"
            statusPayload.optBoolean("success") -> "当前没有缓存订单号，点击主按钮将尝试开热水。"
            statusPayload.optString("errorMessage").isNotBlank() -> statusPayload.optString("errorMessage")
            else -> "状态待确认，建议先刷新余额。"
        }

        return DashboardSnapshot(
            balanceYuan = balanceYuan,
            balanceLabel = balanceYuan?.let(::formatYuan) ?: "--",
            userLabel = HotWaterConfig.maskMiddle(
                userData.optString("telephone", config.telephone),
                left = 3,
                right = 4,
            ),
            deviceName = deviceData.optString("deviceName", "热水设备待识别").ifBlank { "热水设备待识别" },
            locationLabel = locationLabel,
            statusLabel = statusLabel,
            orderNo = cachedOrderNo,
            hasCachedOrder = !cachedOrderNo.isNullOrBlank(),
            isDeviceBusyByOther = busyByOther,
            isDeviceOnline = deviceOnline,
            usageStartedAtMillis = if (cachedOrderNo.isNullOrBlank()) null else store.loadUsageSession()?.startedAtMillis,
            autoClosedOrderNo = autoClosedOrderNo,
            autoClosedHistoryId = autoClosedHistoryId,
            autoClosedUsageStartedAtMillis = autoClosedUsageStartedAtMillis,
            autoClosedUsageDurationMillis = autoClosedUsageDurationMillis,
        )
    }

    private fun buildSnapshotWithoutStatus(
        config: HotWaterConfig,
        userInfo: JSONObject,
        deviceInfo: JSONObject,
        balancePayload: JSONObject,
        forcedStatusLabel: String,
    ): DashboardSnapshot {
        val userData = userInfo.optJSONObject("data") ?: JSONObject()
        val deviceData = deviceInfo.optJSONObject("data") ?: JSONObject()
        val balanceYuan = HotWaterPayload.balanceYuan(balancePayload)
        val locationLabel = listOf(
            deviceData.optString("areaName"),
            deviceData.optString("buildingName"),
            deviceData.optString("roomName"),
        )
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { deviceData.optString("deviceLocation", "位置待刷新") }

        return DashboardSnapshot(
            balanceYuan = balanceYuan,
            balanceLabel = balanceYuan?.let(::formatYuan) ?: "--",
            userLabel = HotWaterConfig.maskMiddle(
                userData.optString("telephone", config.telephone),
                left = 3,
                right = 4,
            ),
            deviceName = deviceData.optString("deviceName", "热水设备待识别").ifBlank { "热水设备待识别" },
            locationLabel = locationLabel,
            statusLabel = forcedStatusLabel,
            orderNo = null,
            hasCachedOrder = false,
            isDeviceBusyByOther = false,
            usageStartedAtMillis = null,
        )
    }

    private fun buildFallbackSnapshot(
        config: HotWaterConfig,
        balancePayload: JSONObject?,
        statusLabel: String,
        orderNo: String?,
        isDeviceBusyByOther: Boolean = false,
        deviceOnlineOverride: Boolean? = null,
    ): DashboardSnapshot {
        val normalizedOrderNo = orderNo?.takeIf { it.isNotBlank() }
        val parsedBalanceYuan = HotWaterPayload.balanceYuan(balancePayload)
        val resolvedStatusLabel = if (deviceOnlineOverride == false) {
            DEVICE_OFFLINE_STATUS_MESSAGE
        } else {
            statusLabel
        }
        return DashboardSnapshot(
            balanceYuan = parsedBalanceYuan,
            balanceLabel = parsedBalanceYuan?.let(::formatYuan) ?: "--",
            userLabel = HotWaterConfig.maskMiddle(config.telephone, 3, 4),
            deviceName = "热水设备已连接",
            locationLabel = "打开应用后可自动刷新宿舍位置",
            statusLabel = resolvedStatusLabel,
            orderNo = normalizedOrderNo,
            hasCachedOrder = !normalizedOrderNo.isNullOrBlank(),
            isDeviceBusyByOther = isDeviceBusyByOther,
            isDeviceOnline = deviceOnlineOverride ?: isDeviceBusyByOther.takeIf { it },
            usageStartedAtMillis = if (normalizedOrderNo.isNullOrBlank()) null else store.loadUsageSession()?.startedAtMillis,
        )
    }

    private fun ensureUsageSession(
        snapshot: DashboardSnapshot,
        preferredStartBalanceYuan: Double? = null,
        allowSnapshotBalanceBaseline: Boolean = true,
        expectedStateGeneration: Long,
    ): DashboardSnapshot {
        if (!snapshot.hasCachedOrder || snapshot.isDeviceBusyByOther) {
            return snapshot.copy(usageStartedAtMillis = null)
        }
        val startBalanceYuan = preferredStartBalanceYuan
            ?: snapshot.balanceYuan.takeIf { allowSnapshotBalanceBaseline }
        val session = store.startUsageSessionIfAbsent(
            startBalanceYuan,
            startBalanceIsBeforePreauthorization = preferredStartBalanceYuan != null,
            expectedStateGeneration = expectedStateGeneration,
        ) ?: throw staleOperationCancellation()
        return snapshot.copy(usageStartedAtMillis = session.startedAtMillis)
    }

    private fun ensureStateGeneration(expectedStateGeneration: Long) {
        if (store.loadStateGeneration() != expectedStateGeneration) {
            throw staleOperationCancellation()
        }
    }

    private fun staleOperationCancellation(): CancellationException {
        return CancellationException("Hot-water account state changed during the operation.")
    }

    private fun buildSummary(snapshot: DashboardSnapshot): String {
        return "${snapshot.statusLabel}\n余额 ${snapshot.balanceLabel} 元"
    }

    private fun formatYuan(value: Double): String = "%.2f".format(value)

    private fun findNestedValue(value: Any?, keyName: String): Any? {
        return HotWaterPayload.findNestedValue(value, keyName)
    }

    private suspend fun <T> tryApi(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (expired: CredentialExpiredException) {
            throw expired
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun isAcceptedResponse(payload: JSONObject): Boolean {
        val hasErrorCode = payload.has("errorCode") && !payload.isNull("errorCode")
        return payload.optBoolean("success") &&
            (!hasErrorCode || payload.optInt("errorCode", -1) == 0)
    }

    private fun isConfirmedResponse(payload: JSONObject): Boolean {
        return payload.optBoolean("success") && payload.optInt("errorCode", -1) == 0
    }

    private fun finalConsumptionFrom(payload: JSONObject): Double? {
        return HotWaterPayload.consumptionYuan(payload).takeIf { isConfirmedResponse(payload) }
    }

    private fun reportsZeroConsumption(payload: JSONObject): Boolean {
        if (!isConfirmedResponse(payload)) return false
        val amount = HotWaterPayload.consumptionYuanIncludingZero(payload) ?: return false
        return HotWaterPayload.toMilliYuan(amount) == 0L
    }

    private fun isDeviceBusyByOther(payload: JSONObject): Boolean {
        if (payload.optInt("errorCode", -1) != DEVICE_BUSY_ERROR_CODE) {
            return false
        }
        val errorMessage = payload.optString("errorMessage")
        val data = payload.optJSONObject("data")
        val isOwner = data?.takeIf { it.has("isOwner") && !it.isNull("isOwner") }?.optBoolean("isOwner")
        return when (isOwner) {
            true -> false
            false -> true
            null -> errorMessage.contains("设备正在使用")
        }
    }

    private fun deviceOnlineState(
        payload: JSONObject,
        override: Boolean? = null,
    ): Boolean? {
        override?.let { return it }
        if (isExplicitDeviceOffline(payload)) return false
        reportedDeviceOnlineState(payload)?.let { return it }
        return isDeviceBusyByOther(payload).takeIf { it }
    }

    private fun openResultOnlineState(
        payload: JSONObject,
        expectedOrderNo: String?,
    ): Boolean? {
        val errorCode = payload.optInt("errorCode", -1)
        val errorMessage = payload.optString("errorMessage")
        if (
            isExplicitDeviceOffline(payload) ||
            (
                errorCode == OPEN_RESULT_NETWORK_ERROR_CODE &&
                    errorMessage.contains(OPEN_RESULT_NETWORK_SLOW_MESSAGE)
                )
        ) {
            return false
        }
        reportedDeviceOnlineState(payload)?.let { return it }
        return if (isConfirmedOpenResult(payload, expectedOrderNo)) true else null
    }

    private fun isOpenResultSuccessful(
        payload: JSONObject,
        expectedOrderNo: String?,
    ): Boolean = isConfirmedOpenResult(payload, expectedOrderNo)

    private fun isExplicitDeviceOffline(payload: JSONObject): Boolean {
        val errorMessage = payload.optString("errorMessage")
        return payload.optInt("errorCode", -1) == DEVICE_OFFLINE_ERROR_CODE ||
            errorMessage.contains(DEVICE_OFFLINE_MESSAGE) ||
            errorMessage.contains(DEVICE_OFFLINE_MESSAGE_ALT)
    }

    private fun isConfirmedOpenResult(payload: JSONObject, expectedOrderNo: String?): Boolean {
        val data = payload.optJSONObject("data") ?: return false
        val responseOrderNo = data.optString("orderNo").trim()
        if (responseOrderNo.isBlank()) return false
        if (!expectedOrderNo.isNullOrBlank() && responseOrderNo != expectedOrderNo) return false
        return payload.optBoolean("success") &&
            payload.has("errorCode") &&
            !payload.isNull("errorCode") &&
            payload.optInt("errorCode", -1) == 0 &&
            isValidOpenResultData(data)
    }

    private fun isValidOpenResultData(data: JSONObject): Boolean {
        return data.has("state") &&
            !data.isNull("state") &&
            data.optInt("state", Int.MIN_VALUE) == 1 &&
            data.has("result") &&
            !data.isNull("result") &&
            data.optInt("result", Int.MIN_VALUE) == 0
    }

    private fun reportedDeviceOnlineState(payload: JSONObject): Boolean? {
        ONLINE_STATE_KEYS.forEach { key ->
            val rawValue = findNestedValue(payload, key) ?: return@forEach
            when (rawValue) {
                is Boolean -> return rawValue
                is Number -> return rawValue.toInt() != 0
                else -> when (rawValue.toString().trim().lowercase()) {
                    "online", "connected", "true", "1" -> return true
                    "offline", "disconnected", "false", "0" -> return false
                }
            }
        }
        return null
    }

    companion object {
        private const val DEVICE_BUSY_ERROR_CODE = 307
        private const val DEVICE_OFFLINE_ERROR_CODE = 306
        private const val OPEN_RESULT_NETWORK_ERROR_CODE = 1
        private const val DEVICE_OFFLINE_MESSAGE = "设备不在线"
        private const val DEVICE_OFFLINE_MESSAGE_ALT = "设备离线"
        private const val OPEN_RESULT_NETWORK_SLOW_MESSAGE = "网络通讯慢"
        private const val DEVICE_OFFLINE_STATUS_MESSAGE = "热水器不在线，请确认寝室供电后重试。"
        private const val DEVICE_BUSY_BY_OTHER_MESSAGE = "已有他人正在使用这台设备，请稍后再试。"
        private const val MIN_SETTLEMENT_REFRESH_ATTEMPTS = 2
        private const val HOT_WATER_PREAUTHORIZATION_YUAN = 5.0
        private const val MAX_PENDING_BALANCE_FALLBACK_AGE_MS = 10 * 60 * 1_000L
        private const val MAX_UNCONFIRMED_CLOSE_GATE_MS = 15_000L
        // The server may briefly report no order while an opening request is settling.
        // Five minutes matches the observed idle auto-close window and avoids turning a
        // transient status response into a local close while the user is still using water.
        private const val MIN_AUTO_CLOSE_USAGE_AGE_MS = 5 * 60 * 1_000L
        private const val MAX_RECONCILIATION_LOCK_MS = 1_500L
        // onlineStatusId is static device metadata; the offline capture kept it at 1.
        private val ONLINE_STATE_KEYS = listOf(
            "isOnline",
            "online",
            "deviceOnline",
            "onlineStatus",
            "tcpDeviceOnline",
        )
        internal suspend fun <T> withDeviceControl(block: suspend () -> T): T = DEVICE_CONTROL_MUTEX.withLock { block() }
        private val DEVICE_CONTROL_MUTEX = Mutex()
        private val SETTLEMENT_RECONCILIATION_MUTEX = Mutex()
    }

    private fun isConfirmedWithoutActiveOrder(payload: JSONObject): Boolean {
        return payload.optBoolean("success") &&
            payload.optInt("errorCode", -1) == 0 &&
            findNestedValue(payload, "orderNo")?.toString().isNullOrBlank()
    }
}
