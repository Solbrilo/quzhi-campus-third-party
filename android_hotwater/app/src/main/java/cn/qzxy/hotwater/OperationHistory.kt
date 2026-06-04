package cn.qzxy.hotwater

data class OperationHistory(
    val id: Long = System.currentTimeMillis(),
    val type: OperationType,
    val success: Boolean,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
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
}
