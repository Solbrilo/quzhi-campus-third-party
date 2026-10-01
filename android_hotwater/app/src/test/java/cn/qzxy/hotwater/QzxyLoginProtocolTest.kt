package cn.qzxy.hotwater

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class QzxyLoginProtocolTest {
    @Test
    fun `encodes password and request body like the official client`() {
        assertEquals("7D28E17F72", QzxyLoginProtocol.encodePassword("abc"))

        val body = QzxyLoginProtocol.buildRequestBody(
            QzxyLoginCredentials(
                telephone = " 13800138000 ",
                password = "abc",
                phoneSystem = "android",
                version = QZXY_LOGIN_CLIENT_VERSION,
            ),
        )

        assertEquals(
            listOf("version", "phoneSystem", "telephone", "password", "type"),
            (0 until body.size).map(body::name),
        )
        assertEquals(
            listOf(QZXY_LOGIN_CLIENT_VERSION, "android", "13800138000", "7D28E17F72", "0"),
            (0 until body.size).map(body::value),
        )
    }

    @Test
    fun `parses login response and keeps device code for the same account`() {
        val result = QzxyLoginProtocol.parseResponse(
            JSONObject(
                """{
                    "success": true,
                    "data": {
                        "userId": 12345,
                        "telephone": "13800138000",
                        "loginCode": "new-login-code",
                        "phoneSystem": "android",
                        "version": "1.2.3",
                        "userAccount": {"projectId": 678, "accountId": 90}
                    }
                }""".trimIndent(),
            ),
        )
        val existing = HotWaterConfig(telephone = "13800138000", snCode = "AABBCC", macAddress = "AA:BB:CC")

        val merged = result.mergeInto(existing)

        assertEquals(678, merged.projectId)
        assertEquals(12345, merged.userId)
        assertEquals(90, merged.accountId)
        assertEquals("new-login-code", merged.loginCode)
        assertEquals("AABBCC", merged.snCode)
        assertEquals("android", merged.phoneSystem)
    }

    @Test
    fun `clears device code when a different account logs in`() {
        val result = QzxyLoginProtocol.parseResponse(
            JSONObject(
                """{
                    "success": true,
                    "data": {
                        "userId": 2,
                        "telephone": "13900139000",
                        "loginCode": "new-login-code",
                        "userAccount": {"projectId": 3, "accountId": 4}
                    }
                }""".trimIndent(),
            ),
        )

        val merged = result.mergeInto(HotWaterConfig(telephone = "13800138000", snCode = "AABBCC"))

        assertTrue(merged.snCode.isBlank())
        assertTrue(merged.macAddress.isBlank())
    }

    @Test
    fun `rejects a malformed service error code`() {
        val error = runCatching {
            QzxyLoginProtocol.parseResponse(
                JSONObject("""{"success":true,"errorCode":"not-a-number","data":{}}"""),
            )
        }.exceptionOrNull()

        assertTrue(error is IOException)
    }

    @Test
    fun `rejects a nonzero service error code even when success is true`() {
        val error = runCatching {
            QzxyLoginProtocol.parseResponse(
                JSONObject(
                    """{"success":true,"errorCode":401,"errorMessage":"用户名或密码不正确"}""",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals("用户名或密码不正确（错误码 401）", error?.message)
    }
    @Test
    fun `sms request contains only verification credentials and matches reference protocol`() {
        val credentials = QzxyLoginCredentials(
            telephone = " 13800138000 ", smsCode = " 123456 ",
            method = QzxyLoginMethod.SMS, version = QZXY_SMS_LOGIN_CLIENT_VERSION,
        )
        val body = QzxyLoginProtocol.buildSmsLoginRequestBody(credentials)
        val fields = (0 until body.size).associate { body.name(it) to body.value(it) }
        assertEquals("13800138000", fields["telephone"])
        assertEquals("123456", fields["smsCode"])
        assertEquals("5", fields["type"])
        assertEquals("6.5.24", fields["version"])
        assertTrue("password" !in fields)
        assertEquals("f000554d9cb90c44bd0ae2ded9a847aa", QzxyLoginProtocol.smsSecret("13800138000"))
    }

    @Test
    fun `sms login uses requested phone when service omits telephone`() {
        val response = JSONObject("""{"success":true,"data":{"userId":2,"loginCode":"test-session",
            "userAccount":{"projectId":3,"accountId":4}}}""")
        assertEquals("13800138000", QzxyLoginProtocol.parseResponse(response, "13800138000").auth.telephone)
        assertTrue(runCatching { QzxyLoginProtocol.parseResponse(response) }.isFailure)
    }

    @Test
    fun `invalid sms inputs fail locally`() {
        assertTrue(runCatching { QzxyLoginProtocol.smsSecret("abcdefghijk") }.isFailure)
        assertTrue(runCatching { QzxyLoginProtocol.buildSmsLoginRequestBody(
            QzxyLoginCredentials(telephone = "13800138000", smsCode = "12")) }.isFailure)
    }

}
