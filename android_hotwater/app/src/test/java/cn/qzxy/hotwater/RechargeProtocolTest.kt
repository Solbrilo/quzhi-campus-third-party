package cn.qzxy.hotwater

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RechargeProtocolTest {
    private val config = HotWaterConfig(1, 2, 3, "test-session", "test-phone")

    @Test fun amountsUseExactYuanAndRejectInvalidValues() {
        assertEquals("10.5", RechargeProtocol.normalizeAmount("010.50"))
        assertEquals("10", RechargeProtocol.normalizeAmount("10.00"))
        listOf(null, "0", "-1", "+10", "1e3", "0.001", "10000.01", "NaN").forEach {
            assertNull(RechargeProtocol.normalizeAmount(it))
        }
    }

    @Test fun orderUsesReferenceSignatureAndOnlyFourSigningFields() {
        val form = RechargeProtocol.orderForm(config, "10.00")
        assertEquals("10", form["rechargeMoney"])
        assertEquals("2", form["payWay"])
        assertEquals("test-phone", form["telPhone"])
        assertEquals("test-phone", form["telephone"])
        assertEquals("a3a9e08d4d5e3a312f91fa3524eb7316", form["signature"])
        assertEquals(form["signature"], RechargeProtocol.orderForm(config.copy(projectId = 5, userId = 6,
            accountId = 7, snCode = "AABBCCDDEEFF"), "10")["signature"])
        assertNotEquals(form["signature"], RechargeProtocol.orderForm(config.copy(loginCode = "rotated"), "10")["signature"])
    }

    @Test fun onlyServerEnabledAlipayAndValidPackagesAreAvailable() {
        assertTrue(RechargeProtocol.options(JSONObject("""{"success":true,"data":{"payWay":[2,"26"]}}""")).alipayEnabled)
        assertFalse(RechargeProtocol.options(JSONObject("""{"success":true,"data":{"payWay":["26"]}}""")).alipayEnabled)
        val packages = RechargeProtocol.packages(JSONObject("""{"success":true,"data":[
            {"typeId":1,"amount":10.0,"givenMoney":2.5},
            {"typeId":2,"amount":null}, {"typeId":3,"amount":"1e3"},
            {"typeId":4,"amount":0}, {"amount":20}] }"""))
        assertEquals(listOf(RechargePackage(1, "10", "2.5")), packages)
    }

    @Test fun signedOrderIsPassedUnchangedAndRejectedResponsesCannotLaunch() {
        val info = "app_id=test&sign=a%2Bb%3D&biz_content=%7B%7D"
        assertEquals(info, RechargeProtocol.orderInfo(JSONObject().put("success", true).put("data", info)))
        assertEquals(info, RechargeProtocol.orderInfo(JSONObject().put("success", true)
            .put("data", JSONObject().put("orderInfo", info))))
        listOf(
            """{"success":false,"errorCode":170,"errorMessage":"签名错误","data":"sign=x"}""",
            """{"success":true,"errorCode":170,"data":"sign=x"}""",
            """{"success":true,"data":"https://example.com/?sign=x"}""",
            """{"success":true,"data":null}""",
        ).forEach { assertTrue(runCatching { RechargeProtocol.orderInfo(JSONObject(it)) }.isFailure) }
    }

    @Test fun uncertainPaymentIsNeverReportedAsFailure() {
        assertEquals(AlipayResult.SUCCESS, AlipayResult.fromCode("9000"))
        assertEquals(AlipayResult.CANCELLED, AlipayResult.fromCode("6001"))
        listOf("8000", "6002", "6004", null).forEach {
            assertNotEquals(AlipayResult.FAILED, AlipayResult.fromCode(it))
        }
    }
}
