package cn.qzxy.hotwater

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
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
const val QZXY_SMS_LOGIN_CLIENT_VERSION = "6.5.24"

enum class QzxyLoginMethod {
    SMS,
    PASSWORD,
}

data class QzxyLoginCredentials(
    val telephone: String,
    val password: String = "",
    val phoneSystem: String = "android",
    val version: String = QZXY_LOGIN_CLIENT_VERSION,
    val method: QzxyLoginMethod = QzxyLoginMethod.PASSWORD,
    val smsCode: String = "",
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
    suspend fun sendVerificationCode(telephone: String)
}

class QzxyLoginApi(
    private val client: OkHttpClient = sharedClient,
) : QzxyLoginRemoteDataSource {
    override suspend fun login(credentials: QzxyLoginCredentials): QzxyLoginResult {
        require(credentials.telephone.isNotBlank()) { "请输入趣智校园手机号" }
        val sms = credentials.method == QzxyLoginMethod.SMS
        val request = Request.Builder()
            .url("$QZXY_BASE_URL${if (sms) "/user/registerAndLogin" else "/user/login"}")
            .post(if (sms) buildSmsBody(credentials) else buildPasswordBody(credentials))
            .headers(commonHeaders())
            .build()
        return QzxyLoginProtocol.parseResponse(execute(request), credentials.telephone.trim())
    }

    override suspend fun sendVerificationCode(telephone: String) {
        val normalized = telephone.trim()
        require(QzxyLoginProtocol.isValidTelephone(normalized)) { "请输入有效的 11 位手机号" }
        val url = ("$QZXY_BASE_URL/user/verification/code/get").toHttpUrl().newBuilder()
            .addQueryParameter("telephone", normalized)
            .addQueryParameter("secret", QzxyLoginProtocol.smsSecret(normalized))
            .addQueryParameter("typeId", "3")
            .addQueryParameter("platform", "1")
            .build()
        val request = Request.Builder().url(url).get().headers(commonHeaders()).build()
        QzxyLoginProtocol.requireSuccessfulResponse(execute(request))
    }

    private fun buildPasswordBody(credentials: QzxyLoginCredentials): FormBody {
        require(credentials.password.isNotBlank()) { "请输入趣智校园密码" }
        return QzxyLoginProtocol.buildRequestBody(credentials)
    }

    private fun buildSmsBody(credentials: QzxyLoginCredentials): FormBody =
        QzxyLoginProtocol.buildSmsLoginRequestBody(credentials)

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
                            val body = response.body?.string().orEmpty()
                            if (!response.isSuccessful) {
                                continuation.resumeWithException(IOException("趣智校园请求失败 (HTTP ${response.code})"))
                                return
                            }
                            continuation.resume(if (body.isBlank()) JSONObject() else JSONObject(body))
                        } catch (error: JSONException) {
                            continuation.resumeWithException(IOException("趣智校园返回了无效的响应", error))
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
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 14; AppleWebKit/537.36 Mobile Safari/537.36)")
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
        val digest = MessageDigest.getInstance("MD5").digest(password.toByteArray(Charsets.UTF_8))
        val hex = CharArray(digest.size * 2)
        val alphabet = "0123456789ABCDEF"
        digest.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            hex[index * 2] = alphabet[value ushr 4]
            hex[index * 2 + 1] = alphabet[value and 0x0F]
        }
        return String(hex).takeLast(10)
    }

    fun isValidTelephone(telephone: String): Boolean = Regex("^1[3-9][0-9]{9}$").matches(telephone.trim())

    fun smsSecret(telephone: String): String {
        val normalized = telephone.trim()
        require(isValidTelephone(normalized)) { "请输入有效的 11 位手机号" }
        val raw = normalized.take(3) + normalized.takeLast(4) + "klcx"
        val digest = MessageDigest.getInstance("MD5").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
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

    fun buildSmsLoginRequestBody(credentials: QzxyLoginCredentials): FormBody {
        require(isValidTelephone(credentials.telephone)) { "请输入有效的 11 位手机号" }
        require(Regex("^[0-9]{6}$").matches(credentials.smsCode.trim())) { "请输入 6 位短信验证码" }
        return FormBody.Builder()
            .add("telephone", credentials.telephone.trim())
            .add("smsCode", credentials.smsCode.trim())
            .add("type", "5")
            .add("phoneSystem", credentials.phoneSystem.ifBlank { "android" })
            .add("version", credentials.version.ifBlank { QZXY_SMS_LOGIN_CLIENT_VERSION })
            .build()
    }

    fun parseResponse(response: JSONObject, requestedTelephone: String? = null): QzxyLoginResult {
        requireSuccessfulResponse(response)
        val data = response.optJSONObject("data")
            ?: throw IOException("趣智校园登录响应缺少账号信息")
        val account = data.optJSONObject("userAccount")
            ?: throw IOException("趣智校园登录响应缺少账户信息")
        val auth = QzxyAuthResult(
            projectId = account.positiveInt("projectId"),
            userId = data.positiveInt("userId"),
            accountId = account.positiveInt("accountId"),
            loginCode = data.requiredText("loginCode"),
            telephone = data.optionalText("telephone")
                ?: requestedTelephone?.trim()?.takeIf(::isValidTelephone)
                ?: throw IOException("趣智校园登录响应缺少 telephone"),
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

    fun requireSuccessfulResponse(response: JSONObject) {
        val hasErrorCode = response.has("errorCode") && !response.isNull("errorCode")
        val errorCode = response.optionalInt("errorCode")
        if (!response.optBoolean("success") || (hasErrorCode && errorCode != 0)) {
            val message = response.optionalText("errorMessage")
                ?: response.optionalText("message")
                ?: response.optionalText("msg")
                ?: "趣智校园请求被拒绝"
            throw IOException(if (errorCode != null && errorCode != 0) "$message（错误码 $errorCode）" else message)
        }
    }

    private fun JSONObject.positiveInt(key: String): Int? = optionalInt(key)?.takeIf { it > 0 }

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
