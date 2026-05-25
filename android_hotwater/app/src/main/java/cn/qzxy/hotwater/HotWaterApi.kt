package cn.qzxy.hotwater

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class HotWaterApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun userInfo(config: HotWaterConfig): JSONObject {
        return get(
            "/user/info",
            mapOf(
                "projectId" to requireInt(config.projectId, "projectId"),
                "userId" to requireInt(config.userId, "userId"),
                "loginCode" to config.loginCode,
            ),
        )
    }

    fun deviceInfo(config: HotWaterConfig): JSONObject {
        return get(
            "/device/info/mac",
            baseQuery(config) + mapOf(
                "macAddress" to config.snCode,
                "phoneSystem" to config.phoneSystem.ifBlank { "Android" },
                "version" to config.version.ifBlank { "0.0.1" },
            ),
        )
    }

    fun balance(config: HotWaterConfig): JSONObject {
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

    fun status(config: HotWaterConfig): JSONObject {
        return postForm("/order/tcpDevice/query/rateOrder/using", buildForm(config))
    }

    fun openHotWater(config: HotWaterConfig): JSONObject {
        return postForm(
            "/order/ali/tcpDevice/downRate/rateOrder",
            buildForm(config).apply {
                put("orderAuthCode", "")
            },
        )
    }

    fun openResult(config: HotWaterConfig, orderNo: String?): JSONObject {
        return postForm("/order/tcpDevice/query/downRateResult", buildForm(config, orderNo))
    }

    fun closeHotWater(config: HotWaterConfig, orderNo: String): JSONObject {
        return postForm("/order/tcpDevice/closeOrder", buildForm(config, orderNo))
    }

    fun closeResult(config: HotWaterConfig, orderNo: String): JSONObject {
        return postForm("/order/tcpDevice/closeOrder/result/query", buildForm(config, orderNo))
    }

    fun consumeResult(config: HotWaterConfig, orderNo: String): JSONObject {
        return postForm("/order/consumeOrder/result/query", buildForm(config, orderNo))
    }

    private fun get(endpoint: String, query: Map<String, Any>): JSONObject {
        val urlBuilder = (BASE_URL + endpoint).toHttpUrl().newBuilder()
        query.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value.toString()) }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .headers(commonHeaders())
            .build()

        return execute(request)
    }

    private fun postForm(endpoint: String, formValues: Map<String, String>): JSONObject {
        val formBodyBuilder = FormBody.Builder()
        formValues.forEach { (key, value) -> formBodyBuilder.add(key, value) }

        val request = Request.Builder()
            .url(BASE_URL + endpoint)
            .post(formBodyBuilder.build())
            .headers(commonHeaders())
            .build()

        return execute(request)
    }

    private fun execute(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: $body")
            }
            return if (body.isBlank()) JSONObject() else JSONObject(body)
        }
    }

    private fun buildForm(config: HotWaterConfig, orderNo: String? = null): MutableMap<String, String> {
        val form = mutableMapOf(
            "accountId" to requireInt(config.accountId, "accountId").toString(),
            "loginCode" to config.loginCode,
            "phoneSystem" to config.phoneSystem.ifBlank { "Android" },
            "projectId" to requireInt(config.projectId, "projectId").toString(),
            "snCode" to config.snCode,
            "telephone" to config.telephone,
            "userId" to requireInt(config.userId, "userId").toString(),
            "version" to config.version.ifBlank { "0.0.1" },
        )
        if (!orderNo.isNullOrBlank()) {
            form["orderNo"] = orderNo
        }
        return form
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
