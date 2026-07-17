package cn.qzxy.hotwater

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

data class UsageSession(
    val startedAtMillis: Long,
    val startBalanceYuan: Double?,
    val startBalanceIsBeforePreauthorization: Boolean = false,
)

data class PendingSettlement(
    val historyId: Long,
    val orderNo: String,
    val closedAtMillis: Long,
    val usageStartedAtMillis: Long?,
    val usageDurationMillis: Long?,
    val startBalanceYuan: Double?,
    val activeBalanceYuan: Double?,
    val provisionalConsumptionYuan: Double?,
    val finalConsumptionYuan: Double?,
    val zeroConsumptionReported: Boolean,
    val balanceFallbackAllowed: Boolean = true,
)

interface HotWaterSessionStore {
    fun loadOrderNo(): String?
    fun saveOrderNo(orderNo: String)
    fun clearOrderNo()
    fun loadUsageSession(): UsageSession?
    fun startUsageSessionIfAbsent(
        startBalanceYuan: Double?,
        startBalanceIsBeforePreauthorization: Boolean = false,
    ): UsageSession
    fun clearUsageSession()
    fun shouldIgnoreRecoveredOrder(windowMs: Long = 12_000L): Boolean
    fun loadPendingSettlements(): List<PendingSettlement>
    fun savePendingSettlement(settlement: PendingSettlement)
    fun removePendingSettlement(orderNo: String)
    fun updateHistorySettlement(settlement: PendingSettlement, consumptionYuan: Double)
}

class HotWaterConfigStore(context: Context) : HotWaterSessionStore {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    data class WidgetState(
        val title: String,
        val message: String,
        val tone: String,
        val isBusy: Boolean,
        val updatedAtMillis: Long,
    )

    fun loadConfig(): HotWaterConfig {
        return HotWaterConfig(
            projectId = prefs.getIntOrNull(KEY_PROJECT_ID),
            userId = prefs.getIntOrNull(KEY_USER_ID),
            accountId = prefs.getIntOrNull(KEY_ACCOUNT_ID),
            loginCode = prefs.getString(KEY_LOGIN_CODE, "").orEmpty(),
            telephone = prefs.getString(KEY_TELEPHONE, "").orEmpty(),
            snCode = prefs.getString(KEY_SN_CODE, "").orEmpty(),
            macAddress = prefs.getString(KEY_MAC_ADDRESS, "").orEmpty(),
            phoneSystem = prefs.getString(KEY_PHONE_SYSTEM, "").orEmpty(),
            version = prefs.getString(KEY_VERSION, "").orEmpty(),
        )
    }

    fun saveConfig(config: HotWaterConfig) {
        prefs.edit()
            .putIntIfNotNull(KEY_PROJECT_ID, config.projectId)
            .putIntIfNotNull(KEY_USER_ID, config.userId)
            .putIntIfNotNull(KEY_ACCOUNT_ID, config.accountId)
            .putString(KEY_LOGIN_CODE, config.loginCode)
            .putString(KEY_TELEPHONE, config.telephone)
            .putString(KEY_SN_CODE, config.snCode)
            .putString(KEY_MAC_ADDRESS, config.macAddress)
            .putString(KEY_PHONE_SYSTEM, config.phoneSystem)
            .putString(KEY_VERSION, config.version)
            .apply()
    }

    override fun loadOrderNo(): String? {
        return prefs.getString(KEY_ORDER_NO, null)?.takeIf { it.isNotBlank() }
    }

    override fun saveOrderNo(orderNo: String) {
        prefs.edit()
            .putString(KEY_ORDER_NO, orderNo)
            .putLong(KEY_ORDER_CLEARED_AT, 0L)
            .apply()
    }

    override fun clearOrderNo() {
        prefs.edit()
            .remove(KEY_ORDER_NO)
            .putLong(KEY_ORDER_CLEARED_AT, System.currentTimeMillis())
            .apply()
    }

    override fun loadUsageSession(): UsageSession? {
        val startedAtMillis = prefs.getLong(KEY_USAGE_STARTED_AT, 0L)
        if (startedAtMillis <= 0L) return null
        val startBalanceYuan = if (prefs.contains(KEY_USAGE_START_BALANCE_BITS)) {
            Double.fromBits(prefs.getLong(KEY_USAGE_START_BALANCE_BITS, 0L))
                .takeIf { it.isFinite() && it >= 0.0 }
        } else {
            null
        }
        return UsageSession(
            startedAtMillis = startedAtMillis,
            startBalanceYuan = startBalanceYuan,
            startBalanceIsBeforePreauthorization = prefs.getBoolean(
                KEY_USAGE_START_BALANCE_IS_BEFORE_PREAUTHORIZATION,
                false,
            ),
        )
    }

    override fun startUsageSessionIfAbsent(
        startBalanceYuan: Double?,
        startBalanceIsBeforePreauthorization: Boolean,
    ): UsageSession {
        val existing = loadUsageSession()
        if (existing?.startBalanceYuan != null) return existing
        if (existing != null && startBalanceYuan == null) return existing

        val session = UsageSession(
            startedAtMillis = existing?.startedAtMillis ?: System.currentTimeMillis(),
            startBalanceYuan = startBalanceYuan,
            startBalanceIsBeforePreauthorization = startBalanceIsBeforePreauthorization,
        )
        prefs.edit().apply {
            putLong(KEY_USAGE_STARTED_AT, session.startedAtMillis)
            if (startBalanceYuan == null) {
                remove(KEY_USAGE_START_BALANCE_BITS)
                remove(KEY_USAGE_START_BALANCE_IS_BEFORE_PREAUTHORIZATION)
            } else {
                putLong(KEY_USAGE_START_BALANCE_BITS, startBalanceYuan.toBits())
                putBoolean(
                    KEY_USAGE_START_BALANCE_IS_BEFORE_PREAUTHORIZATION,
                    session.startBalanceIsBeforePreauthorization,
                )
            }
        }.apply()
        return session
    }

    override fun clearUsageSession() {
        prefs.edit()
            .remove(KEY_USAGE_STARTED_AT)
            .remove(KEY_USAGE_START_BALANCE_BITS)
            .remove(KEY_USAGE_START_BALANCE_IS_BEFORE_PREAUTHORIZATION)
            .apply()
    }

    override fun shouldIgnoreRecoveredOrder(windowMs: Long): Boolean {
        val clearedAt = prefs.getLong(KEY_ORDER_CLEARED_AT, 0L)
        return clearedAt > 0L && System.currentTimeMillis() - clearedAt < windowMs
    }

    override fun loadPendingSettlements(): List<PendingSettlement> {
        val json = prefs.getString(KEY_PENDING_SETTLEMENTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val orderNo = item.optString("orderNo").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                PendingSettlement(
                    historyId = item.optLong("historyId").takeIf { it > 0L }
                        ?: return@mapNotNull null,
                    orderNo = orderNo,
                    closedAtMillis = item.optLong("closedAtMillis"),
                    usageStartedAtMillis = item.optLongOrNull("usageStartedAtMillis"),
                    usageDurationMillis = item.optLongOrNull("usageDurationMillis"),
                    startBalanceYuan = item.optFiniteDoubleOrNull("startBalanceYuan"),
                    activeBalanceYuan = item.optFiniteDoubleOrNull("activeBalanceYuan"),
                    provisionalConsumptionYuan = item.optFiniteDoubleOrNull(
                        "provisionalConsumptionYuan",
                    ),
                    finalConsumptionYuan = item.optFiniteDoubleOrNull("finalConsumptionYuan"),
                    zeroConsumptionReported = item.optBoolean("zeroConsumptionReported", false),
                    balanceFallbackAllowed = item.optBoolean("balanceFallbackAllowed", true),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    override fun savePendingSettlement(settlement: PendingSettlement) {
        val existingSettlements = loadPendingSettlements()
        val isExistingSettlement = existingSettlements.any { it.orderNo == settlement.orderNo }
        val retainedSettlements = existingSettlements
            .filterNot { it.orderNo == settlement.orderNo }
            .map { existing ->
                if (isExistingSettlement) existing else existing.copy(balanceFallbackAllowed = false)
            }
            .toMutableList()
            .apply {
                add(
                    if (isExistingSettlement) {
                        settlement
                    } else {
                        settlement.copy(balanceFallbackAllowed = true)
                    },
                )
            }
            .takeLast(MAX_PENDING_SETTLEMENTS)
        writePendingSettlements(retainedSettlements)
    }

    @Synchronized
    override fun removePendingSettlement(orderNo: String) {
        val settlements = loadPendingSettlements().filterNot { it.orderNo == orderNo }
        writePendingSettlements(settlements)
    }

    fun loadWidgetState(): WidgetState {
        return WidgetState(
            title = prefs.getString(KEY_WIDGET_TITLE, "").orEmpty(),
            message = prefs.getString(KEY_WIDGET_MESSAGE, "").orEmpty(),
            tone = prefs.getString(KEY_WIDGET_TONE, "idle").orEmpty(),
            isBusy = prefs.getBoolean(KEY_WIDGET_BUSY, false),
            updatedAtMillis = prefs.getLong(KEY_WIDGET_UPDATED_AT, 0L),
        )
    }

    fun saveWidgetState(title: String, message: String, isBusy: Boolean, tone: String = "idle") {
        prefs.edit()
            .putString(KEY_WIDGET_TITLE, title)
            .putString(KEY_WIDGET_MESSAGE, message)
            .putString(KEY_WIDGET_TONE, tone)
            .putBoolean(KEY_WIDGET_BUSY, isBusy)
            .putLong(KEY_WIDGET_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun SharedPreferences.getIntOrNull(key: String): Int? {
        return if (contains(key)) getInt(key, 0) else null
    }

    private fun SharedPreferences.Editor.putIntIfNotNull(key: String, value: Int?) =
        apply {
            if (value == null) remove(key) else putInt(key, value)
        }

    @Synchronized
    fun saveHistory(history: OperationHistory) {
        val historyList = loadHistory().toMutableList()
        val existing = historyList.firstOrNull { it.id == history.id }
        val mergedHistory = if (history.consumptionYuan == null && existing?.consumptionYuan != null) {
            history.copy(consumptionYuan = existing.consumptionYuan)
        } else {
            history
        }
        historyList.removeAll { it.id == history.id }
        historyList.add(0, mergedHistory) // 添加到开头

        // 只保留最近 20 条
        writeHistory(historyList.take(MAX_HISTORY_SIZE))
    }

    @Synchronized
    fun loadHistory(): List<OperationHistory> {
        val historyJson = prefs.getString(KEY_HISTORY, null) ?: return emptyList()

        return try {
            val jsonArray = JSONArray(historyJson)
            var migrated = false
            val history = (0 until jsonArray.length()).mapNotNull { index ->
                val jsonObject = jsonArray.getJSONObject(index)
                val type = OperationHistory.OperationType.valueOf(jsonObject.getString("type"))
                val sourceSchemaVersion = jsonObject.optInt("schemaVersion", 1)
                val rawConsumption = jsonObject.optFiniteDoubleOrNull("consumptionYuan")
                val consumption = OperationHistory.migrateConsumptionYuan(
                    sourceSchemaVersion,
                    type,
                    rawConsumption,
                )
                if (rawConsumption != consumption) {
                    migrated = true
                }
                if (sourceSchemaVersion < OperationHistory.CURRENT_SCHEMA_VERSION) {
                    migrated = true
                }
                OperationHistory(
                    id = jsonObject.getLong("id"),
                    type = type,
                    success = jsonObject.getBoolean("success"),
                    message = jsonObject.getString("message"),
                    timestamp = jsonObject.getLong("timestamp"),
                    consumptionYuan = consumption,
                    usageDurationMillis = jsonObject.optLongOrNull("usageDurationMillis"),
                    usageStartedAtMillis = jsonObject.optLongOrNull("usageStartedAtMillis"),
                )
            }
            if (migrated) writeHistory(history)
            history
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    override fun updateHistorySettlement(
        settlement: PendingSettlement,
        consumptionYuan: Double,
    ) {
        val normalizedConsumption = HotWaterPayload.normalizeYuan(consumptionYuan)
        val history = loadHistory().toMutableList()
        var found = false
        val updated = history.map { item ->
            if (item.id == settlement.historyId) {
                found = true
                item.copy(consumptionYuan = normalizedConsumption)
            } else {
                item
            }
        }.toMutableList()
        if (!found) {
            updated.add(
                0,
                OperationHistory(
                    id = settlement.historyId,
                    type = OperationHistory.OperationType.CLOSE,
                    success = true,
                    message = "热水已关闭。",
                    timestamp = settlement.closedAtMillis,
                    consumptionYuan = normalizedConsumption,
                    usageDurationMillis = settlement.usageDurationMillis,
                    usageStartedAtMillis = settlement.usageStartedAtMillis,
                ),
            )
        }
        writeHistory(updated.take(MAX_HISTORY_SIZE))
    }

    fun clearHistory() {
        prefs.edit()
            .remove(KEY_HISTORY)
            .apply()
    }

    private fun writeHistory(history: List<OperationHistory>) {
        val jsonArray = JSONArray()
        history.forEach { item ->
            jsonArray.put(JSONObject().apply {
                put("schemaVersion", OperationHistory.CURRENT_SCHEMA_VERSION)
                put("id", item.id)
                put("type", item.type.name)
                put("success", item.success)
                put("message", item.message)
                put("timestamp", item.timestamp)
                putNullable("consumptionYuan", item.consumptionYuan)
                putNullable("usageDurationMillis", item.usageDurationMillis)
                putNullable("usageStartedAtMillis", item.usageStartedAtMillis)
            })
        }
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }

    private fun writePendingSettlements(settlements: List<PendingSettlement>) {
        if (settlements.isEmpty()) {
            prefs.edit().remove(KEY_PENDING_SETTLEMENTS).apply()
            return
        }
        val array = JSONArray()
        settlements.forEach { settlement ->
            array.put(JSONObject().apply {
                put("historyId", settlement.historyId)
                put("orderNo", settlement.orderNo)
                put("closedAtMillis", settlement.closedAtMillis)
                putNullable("usageStartedAtMillis", settlement.usageStartedAtMillis)
                putNullable("usageDurationMillis", settlement.usageDurationMillis)
                putNullable("startBalanceYuan", settlement.startBalanceYuan)
                putNullable("activeBalanceYuan", settlement.activeBalanceYuan)
                putNullable("provisionalConsumptionYuan", settlement.provisionalConsumptionYuan)
                putNullable("finalConsumptionYuan", settlement.finalConsumptionYuan)
                put("zeroConsumptionReported", settlement.zeroConsumptionReported)
                put("balanceFallbackAllowed", settlement.balanceFallbackAllowed)
            })
        }
        prefs.edit().putString(KEY_PENDING_SETTLEMENTS, array.toString()).apply()
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        return takeIf { has(key) && !isNull(key) }?.optLong(key)
    }

    private fun JSONObject.optFiniteDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key).takeIf { it.isFinite() }
    }

    companion object {
        private const val PREFS_NAME = "hotwater_store"
        private const val KEY_PROJECT_ID = "project_id"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_LOGIN_CODE = "login_code"
        private const val KEY_TELEPHONE = "telephone"
        private const val KEY_SN_CODE = "sn_code"
        private const val KEY_MAC_ADDRESS = "mac_address"
        private const val KEY_PHONE_SYSTEM = "phone_system"
        private const val KEY_VERSION = "version"
        private const val KEY_ORDER_NO = "order_no"
        private const val KEY_ORDER_CLEARED_AT = "order_cleared_at"
        private const val KEY_USAGE_STARTED_AT = "usage_started_at"
        private const val KEY_USAGE_START_BALANCE_BITS = "usage_start_balance_bits"
        private const val KEY_USAGE_START_BALANCE_IS_BEFORE_PREAUTHORIZATION =
            "usage_start_balance_is_before_preauthorization"
        private const val KEY_WIDGET_TITLE = "widget_title"
        private const val KEY_WIDGET_MESSAGE = "widget_message"
        private const val KEY_WIDGET_TONE = "widget_tone"
        private const val KEY_WIDGET_BUSY = "widget_busy"
        private const val KEY_WIDGET_UPDATED_AT = "widget_updated_at"
        private const val KEY_HISTORY = "operation_history"
        private const val KEY_PENDING_SETTLEMENTS = "pending_settlements"
        private const val ORDER_RECOVERY_GRACE_MS = 12_000L
        private const val MAX_HISTORY_SIZE = 20
        private const val MAX_PENDING_SETTLEMENTS = 10
    }
}
