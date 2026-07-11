package cn.qzxy.hotwater

data class OperationHistory(
    val id: Long = System.currentTimeMillis(),
    val type: OperationType,
    val success: Boolean,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val consumptionYuan: Double? = null,
    val usageDurationMillis: Long? = null,
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
        return consumptionYuan?.let { "消费金额 ¥%.2f".format(it) }
            ?: takeIf { type == OperationType.CLOSE }?.let { "消费金额 待同步" }
    }

    fun getUsageDurationLabel(): String? {
        return usageDurationMillis?.let { formatDuration(it) }
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
}
