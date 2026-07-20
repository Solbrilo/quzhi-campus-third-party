package cn.qzxy.hotwater

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val QZXY_BASE_URL = "https://v3-api.china-qzxy.cn"
const val QZXY_LOGIN_CLIENT_VERSION = "1.1.11"

data class QzxyLoginCredentials(
    val telephone: String,
    val password: String,
    val phoneSystem: String,
    val version: String,
)

data class QzxyAuthResult(
    val projectId: Int? = null,
    val userId: Int? = null,
    val accountId: Int? = null,
    val loginCode: String = "",
    val telephone: String = "",
) {
    fun mergeInto(existing: HotWaterConfig): HotWaterConfig {
        return existing.copy(
            projectId = projectId ?: existing.projectId,
            userId = userId ?: existing.userId,
            accountId = accountId ?: existing.accountId,
            loginCode = loginCode.trim().takeIf { it.isNotBlank() } ?: existing.loginCode,
            telephone = telephone.trim().takeIf { it.isNotBlank() } ?: existing.telephone,
            snCode = existing.snCode.replace(":", "").uppercase(Locale.ROOT),
        )
    }
}

data class QzxyLoginResult(
    val auth: QzxyAuthResult,
    val phoneSystem: String? = null,
    val version: String? = null,
) {
    /**
     * Device codes are account-bound in practice. Keep one only when this login belongs to the
     * already configured phone number; a different account must choose or enter its own device.
     */
    fun mergeInto(existing: HotWaterConfig): HotWaterConfig {
        val merged = auth.mergeInto(existing)
        val sameAccount = existing.telephone.isBlank() || existing.telephone == merged.telephone
        return merged.copy(
            snCode = if (sameAccount) existing.snCode else "",
            macAddress = if (sameAccount) existing.macAddress else "",
            phoneSystem = phoneSystem?.takeIf { it.isNotBlank() } ?: existing.phoneSystem,
            version = version?.takeIf { it.isNotBlank() } ?: existing.version,
        )
    }
}

interface QzxyLoginRemoteDataSource {
    suspend fun login(credentials: QzxyLoginCredentials): QzxyLoginResult
}

class QzxyLoginApi(
    private val client: OkHttpClient = sharedClient,
) : QzxyLoginRemoteDataSource {
    override suspend fun login(credentials: QzxyLoginCredentials): QzxyLoginResult {
        require(credentials.telephone.isNotBlank()) { "请输入趣智校园手机号" }
        require(credentials.password.isNotBlank()) { "请输入趣智校园密码" }

        val request = Request.Builder()
            .url("$QZXY_BASE_URL/user/login")
            .post(QzxyLoginProtocol.buildRequestBody(credentials))
            .headers(commonHeaders())
            .build()

        return QzxyLoginProtocol.parseResponse(execute(request))
    }

    private suspend fun execute(request: Request): JSONObject {
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (continuation.isCancelled) return
                        try {
                            val body = response.body.string()
                            if (!response.isSuccessful) {
                                continuation.resumeWithException(
                                    IOException("趣智校园登录请求失败 (HTTP ${response.code})"),
                                )
                                return
                            }
                            continuation.resume(if (body.isBlank()) JSONObject() else JSONObject(body))
                        } catch (error: JSONException) {
                            continuation.resumeWithException(
                                IOException("趣智校园返回了无效的登录响应", error),
                            )
                        } catch (error: Exception) {
                            continuation.resumeWithException(error)
                        }
                    }
                }
            })
        }
    }

    private fun commonHeaders() = okhttp3.Headers.Builder()
        .add("Accept", "application/json, text/plain, */*")
        .add(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14; AppleWebKit/537.36 Mobile Safari/537.36)",
        )
        .build()

    companion object {
        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(2, 1, TimeUnit.MINUTES))
                .build()
        }
    }
}

object QzxyLoginProtocol {
    fun encodePassword(password: String): String {
        // The official client sends the uppercase MD5 tail instead of the plaintext password.
        val digest = MessageDigest.getInstance("MD5")
            .digest(password.toByteArray(Charsets.UTF_8))
        val hex = CharArray(digest.size * 2)
        val alphabet = "0123456789ABCDEF"
        digest.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            hex[index * 2] = alphabet[value ushr 4]
            hex[index * 2 + 1] = alphabet[value and 0x0F]
        }
        return String(hex).takeLast(10)
    }

    fun buildRequestBody(credentials: QzxyLoginCredentials): FormBody {
        return FormBody.Builder()
            .add("version", credentials.version.ifBlank { QZXY_LOGIN_CLIENT_VERSION })
            .add("phoneSystem", credentials.phoneSystem.ifBlank { "android" })
            .add("telephone", credentials.telephone.trim())
            .add("password", encodePassword(credentials.password))
            .add("type", "0")
            .build()
    }

    fun parseResponse(response: JSONObject): QzxyLoginResult {
        val hasErrorCode = response.has("errorCode") && !response.isNull("errorCode")
        val errorCode = response.optionalInt("errorCode")
        if (!response.optBoolean("success") || (hasErrorCode && errorCode != 0)) {
            val message = response.optString("errorMessage")
                .ifBlank { response.optString("message") }
                .ifBlank { response.optString("msg") }
                .ifBlank { "趣智校园账号或密码错误，或登录已被拒绝" }
            throw IOException(if (errorCode != null && errorCode != 0) "$message（错误码 $errorCode）" else message)
        }

        val data = response.optJSONObject("data")
            ?: throw IOException("趣智校园登录响应缺少账号信息")
        val account = data.optJSONObject("userAccount")
            ?: throw IOException("趣智校园登录响应缺少账户信息")
        val auth = QzxyAuthResult(
            projectId = account.positiveInt("projectId"),
            userId = data.positiveInt("userId"),
            accountId = account.positiveInt("accountId"),
            loginCode = data.requiredText("loginCode"),
            telephone = data.requiredText("telephone"),
        )
        if (auth.projectId == null || auth.userId == null || auth.accountId == null) {
            throw IOException("趣智校园登录响应缺少项目或账户编号")
        }
        return QzxyLoginResult(
            auth = auth,
            phoneSystem = data.optionalText("phoneSystem"),
            version = data.optionalText("version"),
        )
    }

    private fun JSONObject.positiveInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optionalInt(key)?.takeIf { it > 0 }
    }

    private fun JSONObject.optionalInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return when (val raw = opt(key)) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull()
            else -> null
        }
    }

    private fun JSONObject.requiredText(key: String): String {
        return optionalText(key) ?: throw IOException("趣智校园登录响应缺少 $key")
    }

    private fun JSONObject.optionalText(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return opt(key)?.toString()?.trim()?.takeIf { it.isNotBlank() }
    }
}
