package cn.qzxy.hotwater

import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal
import java.security.MessageDigest

// Protocol and signing reference: Ranxian/muguang (MIT), see THIRD_PARTY_NOTICES.md.
data class RechargePackage(val id: Long, val amount: String, val bonus: String?)
data class RechargeOptions(val alipayEnabled: Boolean, val notice: String?)

interface RechargeRemoteDataSource {
    suspend fun rechargeOptions(config: HotWaterConfig): JSONObject
    suspend fun rechargePackages(config: HotWaterConfig): JSONObject
    suspend fun createRecharge(config: HotWaterConfig, amount: String): JSONObject
    suspend fun rechargeBalance(config: HotWaterConfig): JSONObject
}

internal class RechargeException(message: String) : IOException(message)

internal object RechargeProtocol {
    fun normalizeAmount(raw: String?): String? {
        val text = raw?.trim() ?: return null
        if (!Regex("\\d{1,7}(\\.\\d{1,2})?").matches(text)) return null
        val value = text.toBigDecimalOrNull() ?: return null
        if (value < BigDecimal("0.01") || value > BigDecimal("10000")) return null
        return value.stripTrailingZeros().toPlainString()
    }

    fun auth(config: HotWaterConfig): Map<String, String> {
        require(config.hasAccount()) { "请先登录后再充值" }
        return mapOf(
            "projectId" to config.projectId.toString(), "accountId" to config.accountId.toString(),
            "userId" to config.userId.toString(), "telephone" to config.telephone,
            "telPhone" to config.telephone, "loginCode" to config.loginCode,
            "phoneSystem" to "android", "version" to "6.5.28",
        )
    }

    fun orderForm(config: HotWaterConfig, amount: String): Map<String, String> {
        val normalized = requireNotNull(normalizeAmount(amount)) { "请选择有效充值金额" }
        // Only these four fields are signed; the remaining account fields are added afterwards.
        val signed = sortedMapOf("loginCode" to config.loginCode, "payWay" to "2",
            "rechargeMoney" to normalized, "telPhone" to config.telephone)
        val input = signed.entries.joinToString("") { it.key + it.value } + "sign-kailu="
        val signature = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return auth(config) + signed + ("signature" to signature)
    }

    private fun requireSuccess(payload: JSONObject) {
        if (CredentialExpiryDetector.isExpiredServiceResponse(payload)) throw CredentialExpiredException()
        if (!payload.optBoolean("success") || payload.optInt("errorCode", 0) != 0) {
            val reason = listOf("errorMessage", "message", "msg").firstNotNullOfOrNull {
                payload.opt(it)?.takeIf { value -> value is String && value.isNotBlank() } as? String
            }
            throw RechargeException(reason?.take(200) ?: "充值请求被拒绝，请稍后再试")
        }
    }

    fun options(payload: JSONObject): RechargeOptions {
        requireSuccess(payload)
        val data = payload.optJSONObject("data") ?: throw RechargeException("未获取到支付方式")
        val ways = data.optJSONArray("payWay")
        return RechargeOptions(
            alipayEnabled = ways != null && (0 until ways.length()).any { ways.optString(it).trim() == "2" },
            notice = (data.opt("noSaveMessage") as? String)?.trim()?.takeIf(String::isNotBlank),
        )
    }

    fun balance(payload: JSONObject): Double {
        requireSuccess(payload)
        return HotWaterPayload.balanceYuan(payload)?.takeIf { it.isFinite() }
            ?: throw RechargeException("未获取到余额，请稍后刷新")
    }

    fun packages(payload: JSONObject): List<RechargePackage> {
        requireSuccess(payload)
        val data = payload.optJSONArray("data") ?: throw RechargeException("未获取到充值金额")
        return (0 until data.length()).mapNotNull { index ->
            val row = data.optJSONObject(index) ?: return@mapNotNull null
            val id = row.optLong("typeId", -1).takeIf { it >= 0 } ?: return@mapNotNull null
            val amount = normalizeAmount(row.optString("amount")) ?: return@mapNotNull null
            RechargePackage(id, amount, normalizeAmount(row.optString("givenMoney")))
        }.distinctBy { it.id }
    }

    fun orderInfo(payload: JSONObject): String {
        requireSuccess(payload)
        val data = payload.opt("data")
        val info = when (data) {
            is String -> data
            is JSONObject -> data.opt("orderInfo") as? String
            else -> null
        }?.takeIf(String::isNotBlank) ?: throw RechargeException("未获取到支付宝订单，请稍后再试")
        // Pass the signed order through unchanged. A URL is not an App Pay order.
        if (info.trimStart().startsWith("http", ignoreCase = true) || !info.contains("sign=")) {
            throw RechargeException("服务器未返回有效的支付宝订单")
        }
        return info
    }
}

enum class AlipayResult(val notice: String) {
    SUCCESS("支付宝支付成功，正在刷新余额"),
    PROCESSING("支付处理中，请稍后刷新余额确认到账"),
    CANCELLED("已取消支付"),
    NETWORK_ERROR("网络异常，支付结果尚未确认，请先刷新余额核实"),
    FAILED("支付未完成，可查看支付宝账单后重试"),
    REPEAT("重复的支付请求，请先刷新余额确认是否到账"),
    UNKNOWN("支付结果尚未确认，请先刷新余额或查看支付宝账单，避免重复充值");

    companion object {
        fun fromCode(code: String?) = when (code?.trim()) {
            "9000" -> SUCCESS
            "8000" -> PROCESSING
            "6001" -> CANCELLED
            "6002" -> NETWORK_ERROR
            "4000" -> FAILED
            "5000" -> REPEAT
            else -> UNKNOWN
        }
    }
}
