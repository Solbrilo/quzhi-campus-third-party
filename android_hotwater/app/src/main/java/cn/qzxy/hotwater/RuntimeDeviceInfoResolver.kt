package cn.qzxy.hotwater

import android.content.Context
import android.os.Build

data class RuntimeDeviceInfo(
    val systemLabel: String,
    val phoneSystem: String,
    val version: String,
    val macAddressLabel: String,
)

object RuntimeDeviceInfoResolver {
    private const val MASKED_MAC = "系统已隐藏"
    private const val DEFAULT_VERSION = "0.0.1"
    private const val DEFAULT_PHONE_SYSTEM = "Android"

    fun resolve(context: Context): RuntimeDeviceInfo {
        val manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { Build.BRAND.orEmpty() }
        val model = Build.MODEL.orEmpty()
        val systemLabel = listOf(manufacturer, model)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
            .ifBlank { "Android 设备" }
            .let { "$it · Android ${Build.VERSION.RELEASE.orEmpty().ifBlank { Build.VERSION.SDK_INT.toString() }}" }

        return RuntimeDeviceInfo(
            systemLabel = systemLabel,
            phoneSystem = manufacturer.ifBlank { DEFAULT_PHONE_SYSTEM },
            version = Build.VERSION.RELEASE.orEmpty().ifBlank { DEFAULT_VERSION },
            macAddressLabel = MASKED_MAC,
        )
    }

    fun enrichConfig(context: Context, config: HotWaterConfig): HotWaterConfig {
        val runtimeInfo = resolve(context)
        return config.copy(
            phoneSystem = config.phoneSystem.ifBlank { runtimeInfo.phoneSystem },
            version = config.version.ifBlank { runtimeInfo.version },
        )
    }
}
