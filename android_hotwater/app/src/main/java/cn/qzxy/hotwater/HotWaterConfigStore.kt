package cn.qzxy.hotwater

import android.content.Context

class HotWaterConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    fun loadOrderNo(): String? {
        return prefs.getString(KEY_ORDER_NO, null)?.takeIf { it.isNotBlank() }
    }

    fun saveOrderNo(orderNo: String) {
        prefs.edit()
            .putString(KEY_ORDER_NO, orderNo)
            .putLong(KEY_ORDER_CLEARED_AT, 0L)
            .apply()
    }

    fun clearOrderNo() {
        prefs.edit()
            .remove(KEY_ORDER_NO)
            .putLong(KEY_ORDER_CLEARED_AT, System.currentTimeMillis())
            .apply()
    }

    fun shouldIgnoreRecoveredOrder(windowMs: Long = ORDER_RECOVERY_GRACE_MS): Boolean {
        val clearedAt = prefs.getLong(KEY_ORDER_CLEARED_AT, 0L)
        return clearedAt > 0L && System.currentTimeMillis() - clearedAt < windowMs
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

    private fun android.content.SharedPreferences.getIntOrNull(key: String): Int? {
        return if (contains(key)) getInt(key, 0) else null
    }

    private fun android.content.SharedPreferences.Editor.putIntIfNotNull(key: String, value: Int?) =
        apply {
            if (value == null) remove(key) else putInt(key, value)
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
        private const val KEY_WIDGET_TITLE = "widget_title"
        private const val KEY_WIDGET_MESSAGE = "widget_message"
        private const val KEY_WIDGET_TONE = "widget_tone"
        private const val KEY_WIDGET_BUSY = "widget_busy"
        private const val KEY_WIDGET_UPDATED_AT = "widget_updated_at"
        private const val ORDER_RECOVERY_GRACE_MS = 12_000L
    }
}
