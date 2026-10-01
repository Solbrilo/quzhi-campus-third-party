package cn.qzxy.hotwater

import org.json.JSONObject
import java.io.IOException

internal object NearbyDevicePayload {
    fun parseBinding(payload: JSONObject): NearbyDeviceBinding {
        QzxyLoginProtocol.requireSuccessfulResponse(payload)
        val data = payload.optJSONObject("data") ?: throw IOException("未找到这台热水器，请重新扫描")
        // Never infer a control code from a radio address after an unsuccessful or incomplete lookup.
        val code = listOf("snCode", "deviceSnCode").firstNotNullOfOrNull { key ->
            if (data.isNull(key)) null else QrCodeParser.normalizeSnCode(data.optString(key))
        } ?: throw IOException("这台设备未返回兼容的热水器设备码，请使用扫码配对")
        val name = text(data, "deviceName").ifBlank { "校园热水器" }
        val location = listOf("areaName", "buildingName", "roomName").map { text(data, it) }
            .filter(String::isNotBlank).joinToString(" ").ifBlank { text(data, "deviceLocation") }
        return NearbyDeviceBinding(code, name, location)
    }
    private fun text(data: JSONObject, key: String): String =
        if (data.isNull(key)) "" else data.optString(key).trim()
}

internal object DeviceSwitchPolicy {
    fun blockingMessage(current: HotWaterConfig, targetSnCode: String, orderNo: String?,
        session: UsageSession?, pending: List<PendingSettlement>): String? {
        if (current.snCode == targetSnCode || current.snCode.isBlank()) return null
        return when {
            !orderNo.isNullOrBlank() || session != null -> "当前热水器仍在使用，请先关水再切换设备"
            pending.any { !it.closeConfirmed } -> "当前设备正在确认关水，请刷新状态后再切换"
            else -> null
        }
    }
}
