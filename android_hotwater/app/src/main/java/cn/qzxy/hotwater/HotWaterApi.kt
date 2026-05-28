package cn.qzxy.hotwater

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
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

class HotWaterApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun userInfo(config: HotWaterConfig): JSONObject {
        return get(
            "/user/info",
            mapOf(
                "projectId" to requireInt(config.projectId, "projectId"),
                "userId" to requireInt(config.userId, "userId"),
                "loginCode" to config.loginCode,
            ),
        )
    }

    suspend fun deviceInfo(config: HotWaterConfig): JSONObject {
        return get(
            "/device/info/mac",
            baseQuery(config) + mapOf(
                "macAddress" to config.snCode,
                "phoneSystem" to config.phoneSystem.ifBlank { "Android" },
                "version" to config.version.ifBlank { "0.0.1" },
            ),
        )
    }

    suspend fun balance(config: HotWaterConfig): JSONObject {
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

    suspend fun status(config: HotWaterConfig): JSONObject {
        return postForm("/order/tcpDevice/query/rateOrder/using", buildForm(config))
    }

    suspend fun openHotWater(config: HotWaterConfig): JSONObject {
        return postForm(
            "/order/ali/tcpDevice/downRate/rateOrder",
            buildForm(config) + ("orderAuthCode" to ""),
        )
    }

    suspend fun openResult(config: HotWaterConfig, orderNo: String?): JSONObject {
        return postForm("/order/tcpDevice/query/downRateResult", buildForm(config, orderNo))
    }

    suspend fun closeHotWater(config: HotWaterConfig, orderNo: String): JSONObject {
        return postForm("/order/tcpDevice/closeOrder", buildForm(config, orderNo))
    }

    suspend fun closeResult(config: HotWaterConfig, orderNo: String): JSONObject {
        return postForm("/order/tcpDevice/closeOrder/result/query", buildForm(config, orderNo))
    }

    suspend fun consumeResult(config: HotWaterConfig, orderNo: String): JSONObject {
        return postForm("/order/consumeOrder/result/query", buildForm(config, orderNo))
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
                                if (!response.isSuccessful) {
                                    val body = response.body?.string().orEmpty()
                                    continuation.resumeWithException(
                                        IOException("HTTP ${response.code}: $body")
                                    )
                                    return
                                }
                                val body = response.body?.string().orEmpty()
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

    companion object {
        private const val BASE_URL = "https://v3-api.china-qzxy.cn"
    }
}
