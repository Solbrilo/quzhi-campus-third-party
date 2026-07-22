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
    val closeConfirmed: Boolean = false,
    val stateGeneration: Long = UNASSIGNED_STATE_GENERATION,
)

private const val UNASSIGNED_STATE_GENERATION = -1L

interface HotWaterSessionStore {
    fun loadStateGeneration(): Long
    fun loadOrderNo(): String?
    fun saveOrderNo(orderNo: String)
    fun saveOrderNoForGeneration(orderNo: String, expectedStateGeneration: Long): Boolean
    fun saveOrderNoUnlessPending(orderNo: String, expectedStateGeneration: Long? = null): Boolean
    fun clearOrderNo()
    fun clearActiveUsageForGeneration(expectedStateGeneration: Long): Boolean
    fun clearUsageSessionForGeneration(expectedStateGeneration: Long): Boolean
    fun loadUsageSession(): UsageSession?
    fun startUsageSessionIfAbsent(
        startBalanceYuan: Double?,
        startBalanceIsBeforePreauthorization: Boolean = false,
        expectedStateGeneration: Long? = null,
    ): UsageSession?
    fun clearUsageSession()
    fun shouldIgnoreRecoveredOrder(windowMs: Long = 12_000L): Boolean
    fun loadPendingSettlements(): List<PendingSettlement>
    fun savePendingSettlement(settlement: PendingSettlement)
    fun savePendingSettlementAndEndUsage(
        settlement: PendingSettlement,
        expectedStateGeneration: Long,
    ): Boolean
    fun removePendingSettlement(orderNo: String, expectedStateGeneration: Long? = null)
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
        synchronized(PERSISTED_STATE_LOCK) {
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
    }

    fun requiresReauthentication(): Boolean {
        return prefs.getBoolean(KEY_REQUIRES_REAUTHENTICATION, false)
    }

    fun markCredentialsExpired(expectedLoginCode: String? = null): Boolean = synchronized(PERSISTED_STATE_LOCK) {
        if (expectedLoginCode != null && prefs.getString(KEY_LOGIN_CODE, "").orEmpty() != expectedLoginCode) {
            return@synchronized false
        }
        prefs.edit()
            .putBoolean(KEY_REQUIRES_REAUTHENTICATION, true)
            .remove(KEY_LOGIN_CODE)
            .apply()
        true
    }

    fun clearCredentialsExpired() {
        synchronized(PERSISTED_STATE_LOCK) {
            prefs.edit()
                .remove(KEY_REQUIRES_REAUTHENTICATION)
                .apply()
        }
    }

    override fun loadStateGeneration(): Long = synchronized(PERSISTED_STATE_LOCK) {
        currentStateGeneration()
    }

    override fun loadOrderNo(): String? = synchronized(PERSISTED_STATE_LOCK) {
        prefs.getString(KEY_ORDER_NO, null)?.takeIf { it.isNotBlank() }
    }

    override fun saveOrderNo(orderNo: String) {
        prefs.edit()
            .putString(KEY_ORDER_NO, orderNo)
            .putLong(KEY_ORDER_CLEARED_AT, 0L)
            .apply()
    }

    override fun saveOrderNoForGeneration(
        orderNo: String,
        expectedStateGeneration: Long,
    ): Boolean = synchronized(PERSISTED_STATE_LOCK) {
        if (orderNo.isBlank() || currentStateGeneration() != expectedStateGeneration) {
            return@synchronized false
        }
        prefs.edit()
            .putString(KEY_ORDER_NO, orderNo)
            .putLong(KEY_ORDER_CLEARED_AT, 0L)
            .apply()
        true
    }

    override fun saveOrderNoUnlessPending(
        orderNo: String,
        expectedStateGeneration: Long?,
    ): Boolean = synchronized(PERSISTED_STATE_LOCK) {
        if (orderNo.isBlank()) return@synchronized false
        if (expectedStateGeneration != null && currentStateGeneration() != expectedStateGeneration) {
            return@synchronized false
        }
        val belongsToPendingClose = loadPendingSettlements().any { it.orderNo == orderNo }
        val clearedAtMillis = prefs.getLong(KEY_ORDER_CLEARED_AT, 0L)
        val recentlyCleared = clearedAtMillis > 0L &&
            System.currentTimeMillis() - clearedAtMillis < ORDER_RECOVERY_GRACE_MS
        if (belongsToPendingClose || recentlyCleared) return@synchronized false

        prefs.edit()
            .putString(KEY_ORDER_NO, orderNo)
            .putLong(KEY_ORDER_CLEARED_AT, 0L)
            .apply()
        true
    }

    override fun clearActiveUsageForGeneration(expectedStateGeneration: Long): Boolean =
        synchronized(PERSISTED_STATE_LOCK) {
            if (currentStateGeneration() != expectedStateGeneration) return@synchronized false
            prefs.edit()
                .remove(KEY_ORDER_NO)
                .putLong(KEY_ORDER_CLEARED_AT, System.currentTimeMillis())
                .remove(KEY_USAGE_STARTED_AT)
                .remove(KEY_USAGE_START_BALANCE_BITS)
                .remove(KEY_USAGE_START_BALANCE_IS_BEFORE_PREAUTHORIZATION)
                .apply()
            true
        }

    override fun clearUsageSessionForGeneration(expectedStateGeneration: Long): Boolean =
        synchronized(PERSISTED_STATE_LOCK) {
            if (currentStateGeneration() != expectedStateGeneration) return@synchronized false
            prefs.edit()
                .remove(KEY_USAGE_STARTED_AT)
                .remove(KEY_USAGE_START_BALANCE_BITS)
                .remove(KEY_USAGE_START_BALANCE_IS_BEFORE_PREAUTHORIZATION)
                .apply()
            true
        }

    /** Clears state that belongs to an old account or device before applying a new login. */
    fun clearActiveUsageState() = synchronized(PERSISTED_STATE_LOCK) {
        val nextGeneration = currentStateGeneration() + 1L
        prefs.edit()
            .remove(KEY_ORDER_NO)
            .putLong(KEY_ORDER_CLEARED_AT, System.currentTimeMillis())
            .remove(KEY_USAGE_STARTED_AT)
            .remove(KEY_USAGE_START_BALANCE_BITS)
            .remove(KEY_USAGE_START_BALANCE_IS_BEFORE_PREAUTHORIZATION)
            .remove(KEY_PENDING_SETTLEMENTS)
            .putLong(KEY_RUNTIME_STATE_GENERATION, nextGeneration)
            .apply()
    }

    override fun clearOrderNo() {
        prefs.edit()
            .remove(KEY_ORDER_NO)
            .putLong(KEY_ORDER_CLEARED_AT, System.currentTimeMillis())
            .apply()
    }

    override fun loadUsageSession(): UsageSession? = synchronized(PERSISTED_STATE_LOCK) {
        val startedAtMillis = prefs.getLong(KEY_USAGE_STARTED_AT, 0L)
        if (startedAtMillis <= 0L) return@synchronized null
        val startBalanceYuan = if (prefs.contains(KEY_USAGE_START_BALANCE_BITS)) {
            Double.fromBits(prefs.getLong(KEY_USAGE_START_BALANCE_BITS, 0L))
                .takeIf { it.isFinite() && it >= 0.0 }
        } else {
            null
        }
        UsageSession(
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
        expectedStateGeneration: Long?,
    ): UsageSession? = synchronized(PERSISTED_STATE_LOCK) {
        if (expectedStateGeneration != null && currentStateGeneration() != expectedStateGeneration) {
            return@synchronized null
        }
        val existing = loadUsageSession()
        if (existing?.startBalanceYuan != null) return@synchronized existing
        if (existing != null && startBalanceYuan == null) return@synchronized existing

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
        session
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

    override fun loadPendingSettlements(): List<PendingSettlement> = synchronized(PERSISTED_STATE_LOCK) {
        val json = prefs.getString(KEY_PENDING_SETTLEMENTS, null) ?: return@synchronized emptyList()
        val currentGeneration = currentStateGeneration()
        try {
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
                    closeConfirmed = item.optBoolean("closeConfirmed", false),
                    stateGeneration = item.optLong("stateGeneration", currentGeneration),
                ).takeIf { it.stateGeneration == currentGeneration }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun savePendingSettlement(settlement: PendingSettlement) {
        synchronized(PERSISTED_STATE_LOCK) {
            val currentGeneration = currentStateGeneration()
            if (
                settlement.stateGeneration != UNASSIGNED_STATE_GENERATION &&
                settlement.stateGeneration != currentGeneration
            ) {
                return@synchronized
            }
            val normalizedSettlement = settlement.copy(stateGeneration = currentGeneration)
            writePendingSettlements(mergePendingSettlementUnlocked(normalizedSettlement))
        }
    }

    override fun savePendingSettlementAndEndUsage(
        settlement: PendingSettlement,
        expectedStateGeneration: Long,
    ): Boolean = synchronized(PERSISTED_STATE_LOCK) {
        if (currentStateGeneration() != expectedStateGeneration) return@synchronized false
        val normalizedSettlement = settlement.copy(stateGeneration = expectedStateGeneration)
        val retainedSettlements = mergePendingSettlementUnlocked(normalizedSettlement)
        val editor = prefs.edit()
        if (retainedSettlements.isEmpty()) {
            editor.remove(KEY_PENDING_SETTLEMENTS)
        } else {
            editor.putString(KEY_PENDING_SETTLEMENTS, serializePendingSettlements(retainedSettlements))
        }
        editor
            .remove(KEY_ORDER_NO)
            .putLong(KEY_ORDER_CLEARED_AT, System.currentTimeMillis())
            .remove(KEY_USAGE_STARTED_AT)
            .remove(KEY_USAGE_START_BALANCE_BITS)
            .remove(KEY_USAGE_START_BALANCE_IS_BEFORE_PREAUTHORIZATION)
            .apply()
        true
    }

    private fun mergePendingSettlementUnlocked(settlement: PendingSettlement): List<PendingSettlement> {
        val existingSettlements = loadPendingSettlements()
        val isExistingSettlement = existingSettlements.any { it.orderNo == settlement.orderNo }
        return existingSettlements
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
    }

    override fun removePendingSettlement(orderNo: String, expectedStateGeneration: Long?) {
        synchronized(PERSISTED_STATE_LOCK) {
            if (expectedStateGeneration != null && expectedStateGeneration != currentStateGeneration()) {
                return@synchronized
            }
            val settlements = loadPendingSettlements().filterNot { it.orderNo == orderNo }
            writePendingSettlements(settlements)
        }
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

    fun saveHistory(history: OperationHistory) {
        synchronized(PERSISTED_STATE_LOCK) {
            val historyList = loadHistory().toMutableList()
            val existing = historyList.firstOrNull { it.id == history.id }
            val mergedHistory = if (history.consumptionYuan == null && existing?.consumptionYuan != null) {
                history.copy(consumptionYuan = existing.consumptionYuan)
            } else {
                history
            }
            historyList.removeAll { it.id == history.id }
            historyList.add(0, mergedHistory) // 添加到开头

            // Keep settled bills independently from short-lived diagnostic operations.
            writeHistory(historyList)
        }
    }

    fun loadHistory(): List<OperationHistory> = synchronized(PERSISTED_STATE_LOCK) {
        val historyJson = prefs.getString(KEY_HISTORY, null) ?: return@synchronized emptyList()

        try {
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
            val retainedHistory = retainHistory(history)
            if (migrated || retainedHistory != history) writeHistory(retainedHistory)
            retainedHistory
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun updateHistorySettlement(
        settlement: PendingSettlement,
        consumptionYuan: Double,
    ) {
        synchronized(PERSISTED_STATE_LOCK) {
            if (
                settlement.stateGeneration != UNASSIGNED_STATE_GENERATION &&
                settlement.stateGeneration != currentStateGeneration()
            ) {
                return@synchronized
            }
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
            writeHistory(updated)
        }
    }

    fun clearHistory() {
        synchronized(PERSISTED_STATE_LOCK) {
            prefs.edit()
                .remove(KEY_HISTORY)
                .apply()
        }
    }

    private fun writeHistory(history: List<OperationHistory>) {
        val retainedHistory = retainHistory(history)
        val jsonArray = JSONArray()
        retainedHistory.forEach { item ->
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

    private fun retainHistory(history: List<OperationHistory>): List<OperationHistory> {
        val newestFirst = history
            .sortedWith(
                compareByDescending<OperationHistory> { it.timestamp }
                    .thenByDescending { it.id },
            )
            .distinctBy(OperationHistory::id)
        val billHistory = newestFirst
            .asSequence()
            .filter(::isBillHistory)
            .take(MAX_BILL_HISTORY_SIZE)
            .toList()
        val diagnosticHistory = newestFirst
            .asSequence()
            .filterNot(::isBillHistory)
            .take(MAX_DIAGNOSTIC_HISTORY_SIZE)
            .toList()
        return (billHistory + diagnosticHistory).sortedWith(
            compareByDescending<OperationHistory> { it.timestamp }
                .thenByDescending { it.id },
        )
    }

    private fun isBillHistory(history: OperationHistory): Boolean {
        return history.type == OperationHistory.OperationType.CLOSE && history.success
    }

    private fun writePendingSettlements(settlements: List<PendingSettlement>) {
        if (settlements.isEmpty()) {
            prefs.edit().remove(KEY_PENDING_SETTLEMENTS).apply()
            return
        }
        prefs.edit()
            .putString(KEY_PENDING_SETTLEMENTS, serializePendingSettlements(settlements))
            .apply()
    }

    private fun serializePendingSettlements(settlements: List<PendingSettlement>): String {
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
                put("closeConfirmed", settlement.closeConfirmed)
                put("stateGeneration", settlement.stateGeneration)
            })
        }
        return array.toString()
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

    private fun currentStateGeneration(): Long {
        return prefs.getLong(KEY_RUNTIME_STATE_GENERATION, 0L)
    }

    companion object {
        private val PERSISTED_STATE_LOCK = Any()
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
        private const val KEY_REQUIRES_REAUTHENTICATION = "requires_reauthentication"
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
        private const val KEY_RUNTIME_STATE_GENERATION = "runtime_state_generation"
        private const val ORDER_RECOVERY_GRACE_MS = 12_000L
        private const val MAX_BILL_HISTORY_SIZE = 1_200
        private const val MAX_DIAGNOSTIC_HISTORY_SIZE = 60
        private const val MAX_PENDING_SETTLEMENTS = 10
    }
}
