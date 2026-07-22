package cn.qzxy.hotwater

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialExpiryDetectorTest {
    @Test
    fun `business 401 expires credentials even when success is true`() {
        val response = JSONObject()
            .put("success", true)
            .put("errorCode", 401)
            .put("errorMessage", "用户名或密码不正确")

        assertTrue(CredentialExpiryDetector.isExpiredServiceResponse(response))
    }

    @Test
    fun `string code 401 is also recognized`() {
        val response = JSONObject()
            .put("success", false)
            .put("code", "401")

        assertTrue(CredentialExpiryDetector.isExpiredServiceResponse(response))
    }

    @Test
    fun `generic business 403 is not treated as credential expiry`() {
        val response = JSONObject()
            .put("success", false)
            .put("errorCode", 403)
            .put("errorMessage", "账号已被锁定")

        assertFalse(CredentialExpiryDetector.isExpiredServiceResponse(response))
    }

    @Test
    fun `business 403 with explicit session expiry is recognized`() {
        val response = JSONObject()
            .put("success", false)
            .put("errorCode", 403)
            .put("errorMessage", "登录凭证已过期，请重新登录")

        assertTrue(CredentialExpiryDetector.isExpiredServiceResponse(response))
    }

    @Test
    fun `http classification keeps non-auth 403 separate`() {
        assertTrue(CredentialExpiryDetector.isExpiredHttpResponse(401, ""))
        assertFalse(CredentialExpiryDetector.isExpiredHttpResponse(403, "账号已被锁定"))
        assertTrue(
            CredentialExpiryDetector.isExpiredHttpResponse(
                403,
                "loginCode invalid, login required",
            ),
        )
    }
}
