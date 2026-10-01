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
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface HotWaterRemoteDataSource {
    suspend fun userInfo(config: HotWaterConfig): JSONObject
    suspend fun deviceInfo(config: HotWaterConfig): JSONObject
    suspend fun deviceInfoForMac(config: HotWaterConfig, macAddress: String): JSONObject =
        deviceInfo(config.copy(snCode = macAddress))
    suspend fun balance(config: HotWaterConfig): JSONObject
    suspend fun status(config: HotWaterConfig): JSONObject
    suspend fun openHotWater(config: HotWaterConfig): JSONObject
    suspend fun openResult(config: HotWaterConfig, orderNo: String?): JSONObject
    suspend fun closeHotWater(config: HotWaterConfig, orderNo: String): JSONObject
    suspend fun closeResult(config: HotWaterConfig, orderNo: String): JSONObject
    suspend fun consumeResult(config: HotWaterConfig, orderNo: String): JSONObject
}

class HotWaterApi : HotWaterRemoteDataSource, ServerBillRemoteDataSource, ServerMessageRemoteDataSource {
    companion object {
        private const val BASE_URL = "https://v3-api.china-qzxy.cn"

        // 共享的 OkHttpClient 单例，复用连接池
        private val sharedClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(5, 2, TimeUnit.MINUTES))
                .build()
        }
    }

    private val client = sharedClient

    override suspend fun userInfo(config: HotWaterConfig): JSONObject {
        return get(
            "/user/info",
            mapOf(
                "projectId" to requireInt(config.projectId, "projectId"),
                "userId" to requireInt(config.userId, "userId"),
                "loginCode" to config.loginCode,
            ),
        )
    }

    override suspend fun deviceInfo(config: HotWaterConfig): JSONObject =
        deviceInfoForMac(config, config.snCode)

    override suspend fun deviceInfoForMac(
        config: HotWaterConfig,
        macAddress: String,
    ): JSONObject {
        return get(
            "/device/info/mac",
            baseQuery(config) + mapOf(
                "macAddress" to macAddress,
                "phoneSystem" to config.phoneSystem.ifBlank { "Android" },
                "version" to config.version.ifBlank { "0.0.1" },
            ),
        )
    }

    override suspend fun balance(config: HotWaterConfig): JSONObject {
        return get(
            "/account/wallet/money",
            mapOf(
                "projectId" to requireInt(config.projectId, "projectId"),
                "userId" to requireInt(config.userId, "userId"),
                "loginCode" to config.loginCode,
                "accountId" to requireInt(config.accountId, "accountId"),
                "telPhone" to config.telephone,
                "phoneSystem" to config.phoneSystem.ifBlank { "Android" },
                "version" to config.version.ifBlank { "0.0.1" },
            ),
        )
    }

    override suspend fun status(config: HotWaterConfig): JSONObject {
        return postForm("/order/tcpDevice/query/rateOrder/using", buildForm(config))
    }

    override suspend fun openHotWater(config: HotWaterConfig): JSONObject {
        return postForm(
            "/order/ali/tcpDevice/downRate/rateOrder",
            buildForm(config) + ("orderAuthCode" to ""),
        )
    }

    override suspend fun openResult(config: HotWaterConfig, orderNo: String?): JSONObject {
        return postForm("/order/tcpDevice/query/downRateResult", buildForm(config, orderNo))
    }

    override suspend fun closeHotWater(config: HotWaterConfig, orderNo: String): JSONObject {
        return postForm("/order/tcpDevice/closeOrder", buildForm(config, orderNo))
    }

    override suspend fun closeResult(config: HotWaterConfig, orderNo: String): JSONObject {
        return postForm("/order/tcpDevice/closeOrder/result/query", buildForm(config, orderNo))
    }

    override suspend fun consumeResult(config: HotWaterConfig, orderNo: String): JSONObject {
        return postForm("/order/consumeOrder/result/query", buildForm(config, orderNo))
    }

    override suspend fun messageList(config: HotWaterConfig): JSONObject = get(
        "/project/push/list",
        baseQuery(config) + mapOf(
            "telephone" to config.telephone,
            "phoneSystem" to config.phoneSystem.ifBlank { "android" },
        ),
    )

    override suspend fun billList(config: HotWaterConfig, month: String): JSONObject {
        return get(
            "/order/query/account/bill/list",
            baseQuery(config) + mapOf(
                "month" to month,
                "billRequestType" to 2,
                "telPhone" to config.telephone,
                "phoneSystem" to config.phoneSystem.ifBlank { "android" },
                "version" to config.version.ifBlank { QZXY_LOGIN_CLIENT_VERSION },
            ),
        )
    }

    private suspend fun get(endpoint: String, query: Map<String, Any>): JSONObject {
        val urlBuilder = (BASE_URL + endpoint).toHttpUrl().newBuilder()
        query.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value.toString()) }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .headers(commonHeaders())
            .build()

        return execute(request)
    }

    private suspend fun postForm(endpoint: String, formValues: Map<String, String>): JSONObject {
        val formBodyBuilder = FormBody.Builder()
        formValues.forEach { (key, value) -> formBodyBuilder.add(key, value) }

        val request = Request.Builder()
            .url(BASE_URL + endpoint)
            .post(formBodyBuilder.build())
            .headers(commonHeaders())
            .build()

        return execute(request)
    }

    private suspend fun execute(request: Request): JSONObject {
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!continuation.isCancelled) {
                            try {
                                val body = response.body?.string().orEmpty()
                                if (
                                    CredentialExpiryDetector.isExpiredHttpResponse(
                                        response.code,
                                        body,
                                    )
                                ) {
                                    continuation.resumeWithException(CredentialExpiredException())
                                    return
                                }
                                if (!response.isSuccessful) {
                                    continuation.resumeWithException(
                                        IOException("HTTP ${response.code}: $body")
                                    )
                                    return
                                }
                                val json = if (body.isBlank()) {
                                    JSONObject()
                                } else {
                                    try {
                                        JSONObject(body)
                                    } catch (e: JSONException) {
                                        continuation.resumeWithException(
                                            IOException("服务器返回了非 JSON 数据: ${body.take(200)}", e)
                                        )
                                        return
                                    }
                                }
                                if (CredentialExpiryDetector.isExpiredServiceResponse(json)) {
                                    continuation.resumeWithException(CredentialExpiredException())
                                    return
                                }
                                continuation.resume(json)
                            } catch (e: Exception) {
                                continuation.resumeWithException(e)
                            }
                        }
                    }
                }
            })
        }
    }

    private fun buildForm(config: HotWaterConfig, orderNo: String? = null): Map<String, String> {
        return buildMap {
            put("accountId", requireInt(config.accountId, "accountId").toString())
            put("loginCode", config.loginCode)
            put("phoneSystem", config.phoneSystem.ifBlank { "Android" })
            put("projectId", requireInt(config.projectId, "projectId").toString())
            put("snCode", config.snCode)
            put("telephone", config.telephone)
            put("userId", requireInt(config.userId, "userId").toString())
            put("version", config.version.ifBlank { "0.0.1" })
            if (!orderNo.isNullOrBlank()) {
                put("orderNo", orderNo)
            }
        }
    }

    private fun baseQuery(config: HotWaterConfig): Map<String, Any> {
        return mapOf(
            "projectId" to requireInt(config.projectId, "projectId"),
            "userId" to requireInt(config.userId, "userId"),
            "loginCode" to config.loginCode,
            "accountId" to requireInt(config.accountId, "accountId"),
            "telephone" to config.telephone,
        )
    }

    private fun requireInt(value: Int?, fieldName: String): Int {
        return value ?: error("$fieldName is missing")
    }

    private fun commonHeaders() = okhttp3.Headers.Builder()
        .add("Accept", "application/json, text/plain, */*")
        .add(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14; AppleWebKit/537.36 Mobile Safari/537.36)",
        )
        .build()
}
