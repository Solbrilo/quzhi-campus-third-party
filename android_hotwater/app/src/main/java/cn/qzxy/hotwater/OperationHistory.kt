package cn.qzxy.hotwater

import java.math.BigDecimal
import java.math.RoundingMode

data class OperationHistory(
    val id: Long = System.currentTimeMillis(),
    val type: OperationType,
    val success: Boolean,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val consumptionYuan: Double? = null,
    val usageDurationMillis: Long? = null,
    val usageStartedAtMillis: Long? = null,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    enum class OperationType(val label: String) {
        OPEN("开热水"),
        CLOSE("关热水"),
        REFRESH("刷新余额"),
    }

    fun getFormattedTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    fun getFormattedDate(): String {
        val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    fun getStatusText(): String {
        return if (success) "成功" else "失败"
    }

    fun getConsumptionLabel(): String? {
        return getConsumptionAmountLabel()?.let { "消费金额 $it" }
            ?: takeIf { type == OperationType.CLOSE }?.let { "消费金额 待同步" }
    }

    fun getConsumptionAmountLabel(): String? {
        return consumptionYuan?.let(::formatConsumptionAmount)
    }

    fun getUsageDurationLabel(): String? {
        return usageDurationMillis?.let { formatDuration(it) }
    }

    fun getUsageStartedAtLabel(): String? {
        val startedAtMillis = usageStartedAtMillis ?: return null
        val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(startedAtMillis))
    }

    private fun formatDuration(durationMillis: Long): String {
        val totalSeconds = (durationMillis / 1_000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            hours > 0L -> "%d:%02d:%02d".format(hours, minutes, seconds)
            else -> "%02d:%02d".format(minutes, seconds)
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2

        internal fun formatConsumptionAmount(value: Double): String {
            val milliYuan = HotWaterPayload.toMilliYuan(value) ?: return "待同步"
            return if (milliYuan in 1L..9L) {
                "< ¥0.01"
            } else {
                val yuan = BigDecimal.valueOf(milliYuan)
                    .movePointLeft(3)
                    .setScale(2, RoundingMode.HALF_UP)
                "¥$yuan"
            }
        }

        internal fun migrateConsumptionYuan(
            sourceSchemaVersion: Int,
            type: OperationType,
            consumptionYuan: Double?,
        ): Double? {
            val isUnverifiedLegacyZero = sourceSchemaVersion < CURRENT_SCHEMA_VERSION &&
                type == OperationType.CLOSE &&
                consumptionYuan?.let(HotWaterPayload::toMilliYuan) == 0L
            return consumptionYuan.takeUnless { isUnverifiedLegacyZero }
        }
    }
}

fun HotWaterConfigStore.saveOperationResultHistory(
    type: OperationHistory.OperationType,
    result: OperationResult,
): OperationHistory {
    val acceptedClose = type == OperationHistory.OperationType.CLOSE && result.historyId != null
    val history = OperationHistory(
        id = result.historyId ?: System.currentTimeMillis(),
        type = type,
        success = result.outcome == OperationOutcome.SUCCESS || acceptedClose,
        message = HotWaterUiText.firstMeaningfulLine(result.summary),
        consumptionYuan = result.consumptionYuan,
        usageDurationMillis = result.usageDurationMillis,
        usageStartedAtMillis = result.usageStartedAtMillis,
    )
    saveHistory(history)
    return history
}
