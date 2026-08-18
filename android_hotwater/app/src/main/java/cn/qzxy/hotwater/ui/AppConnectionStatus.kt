package cn.qzxy.hotwater.ui

internal enum class AppConnectionStatus(
    val label: String?,
    val accessibilityDescription: String?,
) {
    USER_OFFLINE(
        label = "用户未联网",
        accessibilityDescription = "当前设备没有可用的互联网连接",
    ),
    DEVICE_OFFLINE(
        label = "热水器不在线",
        accessibilityDescription = "热水器状态已确认不在线",
    ),
    DEVICE_ONLINE(
        label = "热水器在线",
        accessibilityDescription = "热水器状态已确认在线",
    ),
    UNKNOWN(
        label = null,
        accessibilityDescription = null,
    ),
}

internal fun resolveAppConnectionStatus(
    networkValidated: Boolean,
    deviceOnline: Boolean?,
): AppConnectionStatus {
    return when {
        !networkValidated -> AppConnectionStatus.USER_OFFLINE
        deviceOnline == false -> AppConnectionStatus.DEVICE_OFFLINE
        deviceOnline == true -> AppConnectionStatus.DEVICE_ONLINE
        else -> AppConnectionStatus.UNKNOWN
    }
}
