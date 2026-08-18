package cn.qzxy.hotwater

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackClientTest {
    @Test
    fun payloadContainsOnlyContractFields() {
        val payload = JSONObject(
            FeedbackClient.buildPayload(
                "1.4.1",
                FeedbackCategory.Bug,
                "feedback@example.com",
                "余额没有刷新",
            ),
        )

        val keys = mutableSetOf<String>()
        val keyIterator = payload.keys()
        while (keyIterator.hasNext()) {
            keys += keyIterator.next()
        }
        assertEquals(
            setOf("schemaVersion", "platform", "appVersion", "category", "email", "message"),
            keys,
        )
        assertEquals(1, payload.getInt("schemaVersion"))
        assertEquals("android", payload.getString("platform"))
        assertEquals("1.4.1", payload.getString("appVersion"))
        assertEquals("bug", payload.getString("category"))
        assertEquals("feedback@example.com", payload.getString("email"))
    }

    @Test
    fun validationCountsUnicodeCodePoints() {
        assertEquals("请至少填写 2 个字。", FeedbackClient.validationError("😀"))
        assertNull(FeedbackClient.validationError("😀好"))
        assertEquals(2, FeedbackClient.countUnicodeCharacters("😀好"))
    }

    @Test
    fun emailMustBePresentAndWellFormed() {
        assertEquals("请填写联系邮箱。", FeedbackClient.emailValidationError("   "))
        assertEquals("请输入有效的邮箱地址。", FeedbackClient.emailValidationError("not-an-email"))
        assertNull(FeedbackClient.emailValidationError(" feedback+test@example.com "))
    }

    @Test
    fun successfulResponseReturnsFeedbackId() {
        val result = FeedbackClient.mapResponse(
            statusCode = 201,
            responseBody = "{\"ok\":true,\"feedbackId\":\"fb_test_123\"}",
            retryAfter = null,
        )

        assertTrue(result is FeedbackSubmitResult.Success)
        assertEquals("fb_test_123", (result as FeedbackSubmitResult.Success).feedbackId)
    }

    @Test
    fun rateLimitUsesRetryAfterSecondsAndHttpDate() {
        val now = 1_800_000_000_000L
        assertEquals(75L, FeedbackClient.parseRetryAfter("75", now))
        val date = ZonedDateTime.parse(
            "Fri, 15 Jan 2027 08:01:00 GMT",
            DateTimeFormatter.RFC_1123_DATE_TIME,
        )
        val retry = FeedbackClient.parseRetryAfter(
            date.format(DateTimeFormatter.RFC_1123_DATE_TIME),
            date.toInstant().toEpochMilli() - 30_000,
        )
        assertEquals(30L, retry)
    }
}
