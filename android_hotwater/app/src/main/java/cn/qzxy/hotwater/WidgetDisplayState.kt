package cn.qzxy.hotwater

/** Only visible fields participate; request IDs and refresh timestamps never repaint a widget. */
internal data class WidgetDisplayState(
    val opened: Boolean,
    val busy: Boolean,
    val ready: Boolean,
    val balance: String,
    val indicator: DeviceIndicator,
)

internal enum class DeviceIndicator(val label: String, val color: Int) {
    IDLE("设备空闲", 0xFF37A474.toInt()),
    OFFLINE("离线", 0xFFD95757.toInt()),
    OCCUPIED("设备占用", 0xFFE4AF3F.toInt()),
    USING("正在使用", 0xFF438DE0.toInt()),
}

internal fun widgetIndicator(online: Boolean, occupied: Boolean, opened: Boolean): DeviceIndicator = when {
    !online -> DeviceIndicator.OFFLINE
    opened -> DeviceIndicator.USING
    occupied -> DeviceIndicator.OCCUPIED
    else -> DeviceIndicator.IDLE
}

/** Null means leave the current animation alone during ordinary status/balance refreshes. */
internal fun widgetSwitchAnimation(previousOpened: Boolean?, opened: Boolean, confirmed: Boolean, motionEnabled: Boolean): Boolean? =
    opened.takeIf { previousOpened != null && previousOpened != opened && confirmed && motionEnabled }
