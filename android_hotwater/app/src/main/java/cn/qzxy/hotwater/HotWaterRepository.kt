package cn.qzxy.hotwater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class DashboardSnapshot(
    val balanceYuan: Double,
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
)

class HotWaterRepository(
    private val api: HotWaterApi,
    private val store: HotWaterConfigStore,
) {
    suspend fun check(config: HotWaterConfig): OperationResult = withContext(Dispatchers.IO) {
        require(config.isComplete()) { "Please complete all required config fields first." }
        val snapshot = refreshSnapshot(config)
        OperationResult(
            title = "余额已刷新",
            summary = buildSummary(snapshot),
            details = "",
            snapshot = snapshot,
        )
    }

    suspend fun open(
        config: HotWaterConfig,
        pollAttempts: Int = 5,
        pollDelayMs: Long = 2_000L,
    ): OperationResult = withContext(Dispatchers.IO) {
        require(config.isComplete()) { "Please complete all required config fields first." }

        val detailBuilder = StringBuilder()
        val currentStatus = api.status(config)
        detailBuilder.appendLine("[Current Status]")
        detailBuilder.appendLine(currentStatus.toString(2))
        if (isDeviceBusyByOther(currentStatus)) {
            return@withContext buildDeviceBusyResult(config, currentStatus, detailBuilder)
        }

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
            consumeResult = api.consumeResult(config, orderNo)
            detailBuilder.appendLine()
            detailBuilder.appendLine("[Consume Result]")
            detailBuilder.appendLine(consumeResult.toString(2))
        }

        val snapshot = runCatching { refreshSnapshot(config) }.getOrElse {
            buildFallbackSnapshot(
                config = config,
                balancePayload = consumeResult,
                statusLabel = if (success) "热水已开启，可以直接关闭。" else "开热水请求已发送，等待设备确认。",
                orderNo = store.loadOrderNo() ?: orderNo,
            )
        }
        val snapshotWithUsageSession = ensureUsageSession(snapshot)

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
        )
    }

    suspend fun close(
        config: HotWaterConfig,
        pollAttempts: Int = 5,
        pollDelayMs: Long = 2_000L,
    ): OperationResult = withContext(Dispatchers.IO) {
        require(config.isComplete()) { "Please complete all required config fields first." }

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

        detailBuilder.appendLine()
        detailBuilder.appendLine("Sending close request, orderNo=$orderNo")
        val closeResponse = api.closeHotWater(config, orderNo)
        detailBuilder.appendLine()
        detailBuilder.appendLine("[Close Response]")
        detailBuilder.appendLine(closeResponse.toString(2))

        val closeAccepted = closeResponse.optBoolean("success") || closeResponse.optInt("errorCode", -1) == 0
        if (closeAccepted) {
            store.clearOrderNo()
        }

        var success = false
        for (index in 0 until pollAttempts) {
            delay(pollDelayMs)
            val closeResult = api.closeResult(config, orderNo)
            detailBuilder.appendLine()
            detailBuilder.appendLine("[Close Result #${index + 1}]")
            detailBuilder.appendLine(closeResult.toString(2))
            if (closeResult.optBoolean("success") && closeResult.optInt("errorCode", -1) == 0) {
                success = true
                store.clearOrderNo()
                break
            }
        }

        val closeStatusLabel = if (success) {
            "热水已关闭。"
        } else if (closeAccepted) {
            "关热水请求已发送，稍后可刷新余额确认状态。"
        } else {
            "关热水请求已发送，等待设备确认。"
        }

        val usageEnded = closeAccepted || success
        val refreshedSnapshot = if (usageEnded) {
            runCatching { refreshSnapshotAfterClose(config, closeStatusLabel) }.getOrNull()
        } else {
            null
        }
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
            runCatching { refreshSnapshot(config) }.getOrElse {
                buildFallbackSnapshot(
                    config = config,
                    balancePayload = null,
                    statusLabel = closeStatusLabel,
                    orderNo = store.loadOrderNo(),
                )
            }
        }

        val usageDurationMillis = if (usageEnded) {
            usageSession?.let { System.currentTimeMillis() - it.startedAtMillis }
        } else {
            null
        }
        val consumptionYuan = if (usageEnded && refreshedSnapshot != null) {
            usageSession?.let { (it.startBalanceYuan - refreshedSnapshot.balanceYuan).coerceAtLeast(0.0) }
        } else {
            null
        }
        if (usageEnded) {
            store.clearUsageSession()
        }

        OperationResult(
            title = if (success) "关热水完成" else "关热水请求已发送",
            summary = if (success) "热水已关闭。\n余额 ${snapshot.balanceLabel} 元" else buildSummary(snapshot),
            details = detailBuilder.toString().trim(),
            snapshot = snapshot,
            consumptionYuan = consumptionYuan,
            usageDurationMillis = usageDurationMillis,
            usageStartedAtMillis = if (usageEnded) usageSession?.startedAtMillis else null,
        )
    }

    private suspend fun refreshSnapshot(config: HotWaterConfig): DashboardSnapshot {
        val userInfo = api.userInfo(config)
        val deviceInfo = api.deviceInfo(config)
        val balance = api.balance(config)
        val status = api.status(config)
        return ensureUsageSession(buildSnapshot(config, userInfo, deviceInfo, balance, status))
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
        val snapshot = runCatching { refreshSnapshotWithStatus(config, statusPayload) }.getOrElse {
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
        )
    }

    private suspend fun refreshSnapshotAfterClose(
        config: HotWaterConfig,
        forcedStatusLabel: String,
    ): DashboardSnapshot {
        val userInfo = api.userInfo(config)
        val deviceInfo = api.deviceInfo(config)
        val balance = api.balance(config)
        return buildSnapshotWithoutStatus(config, userInfo, deviceInfo, balance, forcedStatusLabel)
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
        val balanceYuan = extractMoneyYuan(balancePayload)
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
            balanceLabel = formatYuan(balanceYuan),
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
        val balanceYuan = extractMoneyYuan(balancePayload)
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
            balanceLabel = formatYuan(balanceYuan),
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
        return DashboardSnapshot(
            balanceYuan = extractMoneyYuan(balancePayload),
            balanceLabel = formatYuan(extractMoneyYuan(balancePayload)),
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

    private fun ensureUsageSession(snapshot: DashboardSnapshot): DashboardSnapshot {
        if (!snapshot.hasCachedOrder || snapshot.isDeviceBusyByOther) {
            return snapshot.copy(usageStartedAtMillis = null)
        }
        val session = store.startUsageSessionIfAbsent(snapshot.balanceYuan)
        return snapshot.copy(usageStartedAtMillis = session.startedAtMillis)
    }

    private fun buildSummary(snapshot: DashboardSnapshot): String {
        return "${snapshot.statusLabel}\n余额 ${snapshot.balanceLabel} 元"
    }

    private fun formatYuan(value: Double): String = "%.2f".format(value)

    private fun findNestedValue(value: Any?, keyName: String): Any? {
        return when (value) {
            is JSONObject -> {
                if (value.has(keyName) && !value.isNull(keyName)) {
                    value.get(keyName)
                } else {
                    value.keys().asSequence()
                        .mapNotNull { childKey -> findNestedValue(value.opt(childKey), keyName) }
                        .firstOrNull()
                }
            }

            is JSONArray -> {
                (0 until value.length())
                    .mapNotNull { index -> findNestedValue(value.opt(index), keyName) }
                    .firstOrNull()
            }

            else -> null
        }
    }

    private fun extractMoneyYuan(value: Any?, primaryKey: String = "accountRealMoney"): Double {
        if (value == null) return 0.0

        val stringCandidates = listOf("${primaryKey}Str", "money", "consumeMoneyStr")
        for (key in stringCandidates) {
            val candidate = findNestedValue(value, key)?.toString()
            if (!candidate.isNullOrBlank()) {
                candidate.toDoubleOrNull()?.let { return it }
            }
        }

        val rawValue = findNestedValue(value, primaryKey)?.toString()?.toDoubleOrNull() ?: return 0.0
        return rawValue / 1000.0
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
    }
}
