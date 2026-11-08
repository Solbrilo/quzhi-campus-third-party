package cn.qzxy.hotwater.ui

internal enum class AppConnectionStatus(val label: String, val accessibilityDescription: String) {
    OFFLINE("离线", "未联网或无法连接趣智校园服务器"),
    CONNECTED("已连接", "已连接趣智校园服务器"),
    USING("正在使用", "当前账户正在使用热水"),
    DEVICE_BUSY("设备使用中", "热水器正在被其他用户使用"),
}

internal fun resolveAppConnectionStatus(
    networkValidated: Boolean,
    serverReachable: Boolean,
    deviceBusyByOther: Boolean = false,
    usingHotWater: Boolean = false,
): AppConnectionStatus = when {
    !networkValidated || !serverReachable -> AppConnectionStatus.OFFLINE
    deviceBusyByOther -> AppConnectionStatus.DEVICE_BUSY
    usingHotWater -> AppConnectionStatus.USING
    else -> AppConnectionStatus.CONNECTED
}
