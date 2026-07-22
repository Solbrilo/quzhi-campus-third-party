package cn.qzxy.hotwater

import org.json.JSONObject
import java.io.IOException

const val CREDENTIAL_EXPIRED_MESSAGE = "登录凭证已过期，请重新登录"

class CredentialExpiredException : IOException(CREDENTIAL_EXPIRED_MESSAGE)

internal object CredentialExpiryDetector {
    private val authTerms = listOf(
        "登录",
        "登陆",
        "凭证",
        "logincode",
        "token",
        "session",
        "auth",
        "credential",
    )
    private val invalidTerms = listOf(
        "失效",
        "过期",
        "无效",
        "未登录",
        "未登陆",
        "请登录",
        "请登陆",
        "请先登录",
        "请先登陆",
        "重新登录",
        "重新登陆",
        "invalid",
        "expired",
        "unauthorized",
        "login required",
        "please login",
        "authentication required",
    )

    fun isExpiredHttpResponse(statusCode: Int, responseBody: String): Boolean {
        if (statusCode == 401) return true
        return statusCode == 403 && hasExplicitExpiryMessage(responseBody)
    }

    fun isExpiredServiceResponse(response: JSONObject): Boolean {
        val serviceCode = response.intValue("errorCode") ?: response.intValue("code")
        if (serviceCode == 401) return true

        val message = listOf("errorMessage", "message", "msg")
            .map(response::optString)
            .joinToString(" ")
        if (!hasExplicitExpiryMessage(message)) return false

        val hasSuccess = response.has("success") && !response.isNull("success")
        val hasServiceCode = serviceCode != null
        val explicitlyFailed = (hasSuccess && !response.optBoolean("success")) ||
            (hasServiceCode && serviceCode != 0)
        return explicitlyFailed || (!hasSuccess && !hasServiceCode)
    }

    private fun hasExplicitExpiryMessage(raw: String): Boolean {
        val normalized = raw.lowercase()
        return authTerms.any(normalized::contains) && invalidTerms.any(normalized::contains)
    }

    private fun JSONObject.intValue(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
    }
}
