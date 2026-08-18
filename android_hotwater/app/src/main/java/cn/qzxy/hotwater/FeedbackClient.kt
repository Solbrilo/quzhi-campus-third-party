package cn.qzxy.hotwater

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

enum class FeedbackCategory(val apiValue: String, val label: String) {
    Bug("bug", "功能问题"),
    Suggestion("suggestion", "功能建议"),
    Experience("experience", "使用体验"),
    Other("other", "其他"),
}

sealed interface FeedbackSubmitResult {
    data class Success(val feedbackId: String) : FeedbackSubmitResult
    data class RateLimited(val retryAfterSeconds: Long?) : FeedbackSubmitResult
    data class Rejected(val message: String) : FeedbackSubmitResult
    data class Failed(val message: String) : FeedbackSubmitResult
}

class FeedbackClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val endpoint: String = ENDPOINT,
) {
    suspend fun submit(
        appVersion: String,
        category: FeedbackCategory,
        email: String,
        message: String,
    ): FeedbackSubmitResult {
        val normalizedMessage = message.trim()
        val normalizedEmail = email.trim()
        validationError(normalizedMessage)?.let { error ->
            return FeedbackSubmitResult.Rejected(error)
        }
        emailValidationError(normalizedEmail)?.let { error ->
            return FeedbackSubmitResult.Rejected(error)
        }

        val request = Request.Builder()
            .url(endpoint)
            .post(
                buildPayload(appVersion, category, normalizedEmail, normalizedMessage)
                    .toRequestBody(JSON_MEDIA_TYPE),
            )
            .header("Accept", "application/json")
            .build()

        return execute(request)
    }

    private suspend fun execute(request: Request): FeedbackSubmitResult =
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: java.io.IOException) {
                    if (continuation.isActive) {
                        continuation.resume(
                            FeedbackSubmitResult.Failed(
                                "提交失败，请检查网络后手动重试。反馈内容已保留。",
                            ),
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use { received ->
                            mapResponse(
                                statusCode = received.code,
                                responseBody = received.body.string(),
                                retryAfter = received.header("Retry-After"),
                            )
                        }
                    } catch (_: Exception) {
                        FeedbackSubmitResult.Failed(
                            "提交失败，反馈内容已保留，请稍后手动重试。",
                        )
                    }
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
            })
        }

    internal companion object {
        private const val ENDPOINT = "https://nebularive.top/api/hotwater-feedback.php"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()

        internal fun countUnicodeCharacters(value: String): Int =
            value.codePointCount(0, value.length)

        internal fun validationError(message: String): String? {
            val length = countUnicodeCharacters(message)
            return when {
                length < 2 -> "请至少填写 2 个字。"
                length > 2000 -> "反馈内容不能超过 2000 个字。"
                else -> null
            }
        }

        internal fun emailValidationError(email: String): String? {
            val normalizedEmail = email.trim()
            return when {
                normalizedEmail.isEmpty() -> "请填写联系邮箱。"
                normalizedEmail.length > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matches(normalizedEmail) ->
                    "请输入有效的邮箱地址。"
                else -> null
            }
        }

        internal fun buildPayload(
            appVersion: String,
            category: FeedbackCategory,
            email: String,
            message: String,
        ): String = JSONObject()
            .put("schemaVersion", 1)
            .put("platform", "android")
            .put("appVersion", appVersion)
            .put("category", category.apiValue)
            .put("email", email)
            .put("message", message)
            .toString()

        internal fun mapResponse(
            statusCode: Int,
            responseBody: String,
            retryAfter: String?,
            nowMillis: Long = System.currentTimeMillis(),
        ): FeedbackSubmitResult {
            if (statusCode == 201) {
                val feedbackId = runCatching {
                    JSONObject(responseBody).optString("feedbackId").trim()
                }.getOrDefault("")
                return if (feedbackId.isNotEmpty()) {
                    FeedbackSubmitResult.Success(feedbackId)
                } else {
                    FeedbackSubmitResult.Failed("服务器未返回有效反馈编号，反馈内容已保留。")
                }
            }
            if (statusCode == 429) {
                return FeedbackSubmitResult.RateLimited(
                    parseRetryAfter(retryAfter, nowMillis),
                )
            }
            val message = when (statusCode) {
                400, 413, 415, 422 -> "反馈信息未通过校验，请检查后手动重试。"
                403 -> "当前请求无法提交，请稍后手动重试。"
                500, 503 -> "反馈服务暂时不可用，内容已保留，请稍后手动重试。"
                else -> "提交失败（HTTP $statusCode），内容已保留，请稍后手动重试。"
            }
            return FeedbackSubmitResult.Failed(message)
        }

        internal fun parseRetryAfter(
            value: String?,
            nowMillis: Long = System.currentTimeMillis(),
        ): Long? {
            val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            normalized.toLongOrNull()?.let { return it.coerceAtLeast(0) }
            return runCatching {
                val retryAt = ZonedDateTime.parse(
                    normalized,
                    DateTimeFormatter.RFC_1123_DATE_TIME,
                ).toInstant().toEpochMilli()
                ceil((retryAt - nowMillis).coerceAtLeast(0) / 1000.0).toLong()
            }.getOrNull()
        }

        private const val MAX_EMAIL_LENGTH = 254
        private val EMAIL_PATTERN = Regex(
            "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$",
            RegexOption.IGNORE_CASE,
        )
    }
}
