package cn.qzxy.hotwater

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NearbyDeviceTest {
    @Test fun bindingUsesValidatedServiceDeviceCodeInsteadOfBleMacFallback() {
        val result = NearbyDevicePayload.parseBinding(JSONObject("""{"success":true,"data":{
            "snCode":"aa:bb:cc:dd:ee:ff","deviceName":"宿舍热水器","roomName":"101"}}"""))
        assertEquals("AABBCCDDEEFF", result.snCode)
        assertEquals("宿舍热水器", result.deviceName)
    }

    @Test fun failedLookupAndMissingControlCodeCannotBePaired() {
        listOf("""{"success":false,"data":{"snCode":"AABBCCDDEEFF"}}""",
            """{"success":true,"data":null}""", """{"success":true,"data":{"deviceName":"test"}}""")
            .forEach { assertTrue(runCatching { NearbyDevicePayload.parseBinding(JSONObject(it)) }.isFailure) }
    }

    @Test fun changingHeaterCannotDiscardRunningOrderOrUnconfirmedClose() {
        val current = HotWaterConfig(snCode = "AABBCCDDEEFF")
        assertNotNull(DeviceSwitchPolicy.blockingMessage(current, "112233445566", "test-order", null, emptyList()))
        assertNotNull(DeviceSwitchPolicy.blockingMessage(current, "112233445566", null, UsageSession(100L, null), emptyList()))
        val pending = PendingSettlement(1, "test-order", 100L, null, null, null, null, null, null, false)
        assertNotNull(DeviceSwitchPolicy.blockingMessage(current, "112233445566", null, null, listOf(pending)))
        assertNull(DeviceSwitchPolicy.blockingMessage(current, "112233445566", null, null, listOf(pending.copy(closeConfirmed = true))))
        assertNull(DeviceSwitchPolicy.blockingMessage(current, current.snCode, "test-order", null, emptyList()))
    }
}
