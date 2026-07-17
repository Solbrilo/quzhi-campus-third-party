package cn.qzxy.hotwater

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    val usageStartedAtMillis: Long? = null,
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
        reconcilePendingSettlements(config)
        val snapshot = refreshSnapshot(config)
        OperationResult(
            title = "余额已刷新",
            summary = buildSummary(snapshot),
            details = "",
            snapshot = snapshot,
            outcome = OperationOutcome.SUCCESS,
        )
    }

    suspend fun open(
        config: HotWaterConfig,
        pollAttempts: Int = 5,
        pollDelayMs: Long = 2_000L,
    ): OperationResult = withContext(Dispatchers.IO) {
        require(config.isComplete()) { "Please complete all required config fields first." }
        reconcilePendingSettlements(config)

        val detailBuilder = StringBuilder()
        val currentStatus = api.status(config)
        detailBuilder.appendLine("[Current Status]")
        detailBuilder.appendLine(currentStatus.toString(2))
        if (isDeviceBusyByOther(currentStatus)) {
            return@withContext buildDeviceBusyResult(config, currentStatus, detailBuilder)
        }
        val existingOrderNo = findNestedValue(currentStatus, "orderNo")
            ?.toString()
            ?.takeIf { it.isNotBlank() }
        if (existingOrderNo != null) {
            store.saveOrderNo(existingOrderNo)
            val snapshot = tryApi { refreshSnapshotWithStatus(config, currentStatus) }
                .getOrElse {
                    buildFallbackSnapshot(
                        config = config,
                        balancePayload = null,
                        statusLabel = "热水已经开启，无需重复操作。",
                        orderNo = existingOrderNo,
                    )
                }
            return@withContext OperationResult(
                title = "热水已经开启",
                summary = "检测到当前账号已有用水订单。\n余额 ${snapshot.balanceLabel} 元",
                details = detailBuilder.toString().trim(),
                snapshot = snapshot,
                outcome = OperationOutcome.SUCCESS,
            )
        }
        if (isConfirmedWithoutActiveOrder(currentStatus)) {
            store.clearUsageSession()
        }

        // The platform reserves 5 yuan as soon as water is opened. Capture the real
        // balance before that reservation so a later balance delta remains meaningful.
        val balanceBeforeOpenYuan = tryApi {
            HotWaterPayload.balanceYuan(api.balance(config))
        }.getOrNull()

        detailBuilder.appendLine()
        detailBuilder.appendLine("Sending open request...")
        val openResponse = api.openHotWater(config)
        detailBuilder.appendLine()
        detailBuilder.appendLine("[Open Response]")
        detailBuilder.appendLine(openResponse.toString(2))

        if (isDeviceBusyByOther(openResponse)) {
            return@withContext buildDeviceBusyResult(config, openResponse, detailBuilder)
        }

        var orderNo = findNestedValue(openResponse, "orderNo")?.toString()
        var success = false
        var finalOpenResult: JSONObject? = null
        var busyByOtherPayload: JSONObject? = null

        for (index in 0 until pollAttempts) {
            delay(pollDelayMs)
            val openResult = api.openResult(config, orderNo)
            finalOpenResult = openResult
            detailBuilder.appendLine()
            detailBuilder.appendLine("[Open Result #${index + 1}]")
            detailBuilder.appendLine(openResult.toString(2))

            if (isDeviceBusyByOther(openResult)) {
                busyByOtherPayload = openResult
                break
            }

            if (orderNo.isNullOrBlank()) {
                orderNo = findNestedValue(openResult, "orderNo")?.toString()
            }

            val isSuccess = findNestedValue(openResult, "isSuccess")
            if (isSuccess == true) {
                success = true
                break
            }

            if (openResult.optInt("errorCode", -1) == 0 && !openResult.isNull("data")) {
                success = true
                break
            }
        }

        if (busyByOtherPayload != null) {
            return@withContext buildDeviceBusyResult(config, busyByOtherPayload, detailBuilder)
        }

        var consumeResult: JSONObject? = null
        if (!orderNo.isNullOrBlank()) {
            store.saveOrderNo(orderNo)
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
            )
        }.getOrElse {
            buildFallbackSnapshot(
                config = config,
                balancePayload = null,
                statusLabel = if (success) "热水已开启，可以直接关闭。" else "开热水请求已发送，等待设备确认。",
                orderNo = store.loadOrderNo() ?: orderNo,
            )
        }
        val snapshotWithUsageSession = ensureUsageSession(
            snapshot,
            preferredStartBalanceYuan = balanceBeforeOpenYuan,
            allowSnapshotBalanceBaseline = balanceBeforeOpenYuan != null,
        )

        val summary = when {
            success -> "热水已开启。\n余额 ${snapshotWithUsageSession.balanceLabel} 元"
            finalOpenResult != null -> "开热水请求已发送。\n余额 ${snapshotWithUsageSession.balanceLabel} 元"
            else -> buildSummary(snapshotWithUsageSession)
        }

        OperationResult(
            title = if (success) "开热水完成" else "开热水请求已发送",
            summary = summary,
            details = detailBuilder.toString().trim(),
            snapshot = snapshotWithUsageSession,
            outcome = if (success) OperationOutcome.SUCCESS else OperationOutcome.PENDING,
        )
    }

    suspend fun close(
        config: HotWaterConfig,
        pollAttempts: Int = 5,
        pollDelayMs: Long = 2_000L,
        settlementRefreshAttempts: Int = 6,
        settlementRefreshDelayMs: Long = 1_200L,
    ): OperationResult = withContext(Dispatchers.IO) {
        require(config.isComplete()) { "Please complete all required config fields first." }
        reconcilePendingSettlements(config)

        val usageSession = store.loadUsageSession()
        val detailBuilder = StringBuilder()
        val status = api.status(config)
        var orderNo = findNestedValue(status, "orderNo")?.toString()
        detailBuilder.appendLine("[Current Status]")
        detailBuilder.appendLine(status.toString(2))

        if (isDeviceBusyByOther(status)) {
            return@withContext buildDeviceBusyResult(config, status, detailBuilder)
        }

        if (orderNo.isNullOrBlank()) {
            orderNo = store.loadOrderNo()
        }

        if (orderNo.isNullOrBlank()) {
            throw IllegalStateException(
                "No active orderNo found. Open hot water once in the app or import a newer capture file first.\n\n$detailBuilder",
            )
        }
        val activeOrderNo = orderNo

        val queriedActiveBalanceYuan = tryApi {
            HotWaterPayload.balanceYuan(api.balance(config))
        }.getOrNull()
        val activeBalanceYuan = queriedActiveBalanceYuan ?: usageSession?.let {
            HotWaterSettlement.balanceBeforePreauthorization(it)
        }?.minus(HOT_WATER_PREAUTHORIZATION_YUAN)
        val startBalanceYuan = usageSession?.let(HotWaterSettlement::balanceBeforePreauthorization)
            ?: activeBalanceYuan?.plus(HOT_WATER_PREAUTHORIZATION_YUAN)
        val closedAtMillis = System.currentTimeMillis()
        val historyId = closedAtMillis
        val usageDurationMillis = usageSession?.let { closedAtMillis - it.startedAtMillis }

        detailBuilder.appendLine()
        detailBuilder.appendLine("Sending close request, orderNo=$activeOrderNo")
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
            store.savePendingSettlement(
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
                ),
            )
            store.clearOrderNo()
            store.clearUsageSession()
        }

        // Persist the settlement context before any delayed polling. If the caller is
        // cancelled after closeOrder was accepted, a later refresh can still finish it.
        if (closeAccepted) persistPendingSettlement()

        for (index in 0 until pollAttempts) {
            delay(pollDelayMs)
            val closeResult = tryApi { api.closeResult(config, activeOrderNo) }.getOrNull()
            if (closeResult != null) {
                if (isConfirmedResponse(closeResult)) {
                    provisionalConsumptionYuan = HotWaterPayload.consumptionYuan(closeResult)
                        ?: provisionalConsumptionYuan
                }
                zeroConsumptionReported = zeroConsumptionReported ||
                    reportsZeroConsumption(closeResult)
                detailBuilder.appendLine()
                detailBuilder.appendLine("[Close Result #${index + 1}]")
                detailBuilder.appendLine(closeResult.toString(2))
                if (isConfirmedResponse(closeResult)) {
                    closeConfirmed = true
                    closeAccepted = true
                    persistPendingSettlement()
                }
            } else {
                detailBuilder.appendLine()
                detailBuilder.appendLine("[Close Result #${index + 1}] unavailable")
            }

            val consumeResult = tryApi { api.consumeResult(config, activeOrderNo) }.getOrNull()
            if (consumeResult != null) {
                detailBuilder.appendLine()
                detailBuilder.appendLine("[Consume Result #${index + 1}]")
                detailBuilder.appendLine(consumeResult.toString(2))
                finalConsumptionYuan = finalConsumptionFrom(consumeResult)
                    ?: finalConsumptionYuan
                zeroConsumptionReported = zeroConsumptionReported ||
                    reportsZeroConsumption(consumeResult)
            }

            if (closeAccepted || closeConfirmed) persistPendingSettlement()
            if (closeConfirmed && finalConsumptionYuan != null) break
        }

        val closeStatusLabel = if (closeConfirmed) {
            "热水已关闭。"
        } else if (closeAccepted) {
            "关热水请求已受理，消费金额可能仍在同步。"
        } else {
            "关热水请求已发送，等待设备确认。"
        }

        val usageEnded = closeAccepted || closeConfirmed
        val settlementRefresh = if (usageEnded) {
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
        zeroConsumptionReported = settlementRefresh.zeroConsumptionReported
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
            tryApi { refreshSnapshot(config) }.getOrElse {
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
            if (consumptionYuan != null) {
                store.removePendingSettlement(activeOrderNo)
            } else {
                persistPendingSettlement()
            }
        }

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
            outcome = if (usageEnded) OperationOutcome.SUCCESS else OperationOutcome.PENDING,
        )
    }

    private suspend fun refreshSnapshot(
        config: HotWaterConfig,
        preferredUsageStartBalanceYuan: Double? = null,
        allowSnapshotUsageBaseline: Boolean = true,
    ): DashboardSnapshot {
        val userInfo = api.userInfo(config)
        val deviceInfo = api.deviceInfo(config)
        val balance = api.balance(config)
        val status = api.status(config)
        return ensureUsageSession(
            buildSnapshot(config, userInfo, deviceInfo, balance, status),
            preferredUsageStartBalanceYuan,
            allowSnapshotUsageBaseline,
        )
    }

    private suspend fun refreshSnapshotWithStatus(
        config: HotWaterConfig,
        statusPayload: JSONObject,
    ): DashboardSnapshot {
        val userInfo = api.userInfo(config)
        val deviceInfo = api.deviceInfo(config)
        val balance = api.balance(config)
        return ensureUsageSession(buildSnapshot(config, userInfo, deviceInfo, balance, statusPayload))
    }

    private suspend fun buildDeviceBusyResult(
        config: HotWaterConfig,
        statusPayload: JSONObject,
        detailBuilder: StringBuilder,
    ): OperationResult {
        store.clearOrderNo()
        store.clearUsageSession()
        val snapshot = tryApi { refreshSnapshotWithStatus(config, statusPayload) }.getOrElse {
            buildFallbackSnapshot(
                config = config,
                balancePayload = null,
                statusLabel = DEVICE_BUSY_BY_OTHER_MESSAGE,
                orderNo = null,
                isDeviceBusyByOther = true,
            )
        }
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

    private suspend fun reconcilePendingSettlements(config: HotWaterConfig) {
        val pendingSettlements = store.loadPendingSettlements()
        val latestPending = pendingSettlements.maxWithOrNull(
            compareBy<PendingSettlement> { it.closedAtMillis }
                .thenBy { it.historyId },
        )
        pendingSettlements
            .filter { it.orderNo != latestPending?.orderNo && it.balanceFallbackAllowed }
            .forEach { pending ->
                store.savePendingSettlement(pending.copy(balanceFallbackAllowed = false))
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

        pendingSettlements.forEach { pending ->
            var finalConsumptionYuan = pending.finalConsumptionYuan
            var zeroConsumptionReported = pending.zeroConsumptionReported
            if (finalConsumptionYuan == null) {
                val consumeResult = tryApi {
                    api.consumeResult(config, pending.orderNo)
                }.getOrNull()
                finalConsumptionYuan = consumeResult?.let(::finalConsumptionFrom)
                zeroConsumptionReported = zeroConsumptionReported ||
                    (consumeResult?.let(::reportsZeroConsumption) == true)
            }

            if (finalConsumptionYuan != null) {
                store.updateHistorySettlement(pending, finalConsumptionYuan)
                store.removePendingSettlement(pending.orderNo)
                return@forEach
            }

            // A wallet balance is account-wide state, not an immutable order result.
            // Only the newest, still-fresh settlement may use it as a fallback, and
            // only after both local and remote state confirm there is no newer usage.
            if (!canUseCurrentBalance(pending)) {
                if (pending.zeroConsumptionReported != zeroConsumptionReported) {
                    store.savePendingSettlement(
                        pending.copy(zeroConsumptionReported = zeroConsumptionReported),
                    )
                }
                return@forEach
            }

            val balanceYuan = tryApi {
                HotWaterPayload.balanceYuan(api.balance(config))
            }.getOrNull()
            val settledBalanceYuan = balanceYuan?.takeIf {
                HotWaterSettlement.hasSettledBalanceChanged(pending.activeBalanceYuan, it)
            }
            val zeroBalanceConfirmed = zeroConsumptionReported &&
                HotWaterSettlement.balancesMatch(pending.startBalanceYuan, balanceYuan)
            val resolvedConsumptionYuan = if (zeroBalanceConfirmed) {
                0.0
            } else {
                settledBalanceYuan?.let {
                    HotWaterSettlement.resolveConsumptionYuan(
                        provisionalConsumptionYuan = pending.provisionalConsumptionYuan,
                        startBalanceYuan = pending.startBalanceYuan,
                        settledBalanceYuan = it,
                    )
                }
            }
            if (resolvedConsumptionYuan != null) {
                store.updateHistorySettlement(pending, resolvedConsumptionYuan)
                store.removePendingSettlement(pending.orderNo)
            } else if (
                pending.finalConsumptionYuan != finalConsumptionYuan ||
                pending.zeroConsumptionReported != zeroConsumptionReported
            ) {
                store.savePendingSettlement(
                    pending.copy(
                        finalConsumptionYuan = finalConsumptionYuan,
                        zeroConsumptionReported = zeroConsumptionReported,
                    ),
                )
            }
        }
    }

    private fun buildSnapshot(
        config: HotWaterConfig,
        userInfo: JSONObject,
        deviceInfo: JSONObject,
        balancePayload: JSONObject,
        statusPayload: JSONObject,
    ): DashboardSnapshot {
        val userData = userInfo.optJSONObject("data") ?: JSONObject()
        val deviceData = deviceInfo.optJSONObject("data") ?: JSONObject()
        val busyByOther = isDeviceBusyByOther(statusPayload)

        val extractedOrderNo = findNestedValue(statusPayload, "orderNo")?.toString()?.takeIf { it.isNotBlank() }
        if (busyByOther) {
            store.clearOrderNo()
        } else if (!extractedOrderNo.isNullOrBlank()) {
            if (store.shouldIgnoreRecoveredOrder()) {
                // Ignore a briefly stale orderNo right after a close request clears local state.
            } else {
                store.saveOrderNo(extractedOrderNo)
            }
        }

        val cachedOrderNo = if (busyByOther) null else store.loadOrderNo()
        val balanceYuan = HotWaterPayload.balanceYuan(balancePayload)
        val locationLabel = listOf(
            deviceData.optString("areaName"),
            deviceData.optString("buildingName"),
            deviceData.optString("roomName"),
        )
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { deviceData.optString("deviceLocation", "位置待刷新") }

        val statusLabel = when {
            busyByOther -> DEVICE_BUSY_BY_OTHER_MESSAGE
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
            usageStartedAtMillis = if (cachedOrderNo.isNullOrBlank()) null else store.loadUsageSession()?.startedAtMillis,
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
    ): DashboardSnapshot {
        val normalizedOrderNo = orderNo?.takeIf { it.isNotBlank() }
        val parsedBalanceYuan = HotWaterPayload.balanceYuan(balancePayload)
        return DashboardSnapshot(
            balanceYuan = parsedBalanceYuan,
            balanceLabel = parsedBalanceYuan?.let(::formatYuan) ?: "--",
            userLabel = HotWaterConfig.maskMiddle(config.telephone, 3, 4),
            deviceName = "热水设备已连接",
            locationLabel = "打开应用后可自动刷新宿舍位置",
            statusLabel = statusLabel,
            orderNo = normalizedOrderNo,
            hasCachedOrder = !normalizedOrderNo.isNullOrBlank(),
            isDeviceBusyByOther = isDeviceBusyByOther,
            usageStartedAtMillis = if (normalizedOrderNo.isNullOrBlank()) null else store.loadUsageSession()?.startedAtMillis,
        )
    }

    private fun ensureUsageSession(
        snapshot: DashboardSnapshot,
        preferredStartBalanceYuan: Double? = null,
        allowSnapshotBalanceBaseline: Boolean = true,
    ): DashboardSnapshot {
        if (!snapshot.hasCachedOrder || snapshot.isDeviceBusyByOther) {
            return snapshot.copy(usageStartedAtMillis = null)
        }
        val startBalanceYuan = preferredStartBalanceYuan
            ?: snapshot.balanceYuan.takeIf { allowSnapshotBalanceBaseline }
        val session = store.startUsageSessionIfAbsent(
            startBalanceYuan,
            startBalanceIsBeforePreauthorization = preferredStartBalanceYuan != null,
        )
        return snapshot.copy(usageStartedAtMillis = session.startedAtMillis)
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
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun isAcceptedResponse(payload: JSONObject): Boolean {
        return payload.optBoolean("success") || payload.optInt("errorCode", -1) == 0
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

    companion object {
        private const val DEVICE_BUSY_ERROR_CODE = 307
        private const val DEVICE_BUSY_BY_OTHER_MESSAGE = "已有他人正在使用这台设备，请稍后再试。"
        private const val MIN_SETTLEMENT_REFRESH_ATTEMPTS = 2
        private const val HOT_WATER_PREAUTHORIZATION_YUAN = 5.0
        private const val MAX_PENDING_BALANCE_FALLBACK_AGE_MS = 10 * 60 * 1_000L
    }

    private fun isConfirmedWithoutActiveOrder(payload: JSONObject): Boolean {
        return payload.optBoolean("success") &&
            payload.optInt("errorCode", -1) == 0 &&
            findNestedValue(payload, "orderNo")?.toString().isNullOrBlank()
    }
}
