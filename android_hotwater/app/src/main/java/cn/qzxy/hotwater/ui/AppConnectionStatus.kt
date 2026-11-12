package cn.qzxy.hotwater.ui

enum class AppConnectionStatus(val label: String, val accessibilityDescription: String) {
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

internal fun AppConnectionStatus.indicatorColor(): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(when (this) {
    AppConnectionStatus.OFFLINE -> 0xFFD95757
    AppConnectionStatus.CONNECTED -> 0xFF37A474
    AppConnectionStatus.USING -> 0xFF438DE0
    AppConnectionStatus.DEVICE_BUSY -> 0xFFE4AF3F
})
