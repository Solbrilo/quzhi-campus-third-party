package cn.qzxy.hotwater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotWaterConfigIdentityTest {
    private val base = HotWaterConfig(
        projectId = 1,
        userId = 2,
        accountId = 3,
        loginCode = "login-code",
        telephone = "13800138000",
        snCode = "C47F0EDA85D8",
        macAddress = "001122334455",
        phoneSystem = "android",
        version = "1.1.11",
    )

    @Test
    fun `account or heater field changes identity`() {
        val changedConfigs = listOf(
            base.copy(projectId = 9),
            base.copy(userId = 9),
            base.copy(accountId = 9),
            base.copy(telephone = "13900139000"),
            base.copy(snCode = "001122AABBCC"),
        )

        changedConfigs.forEach { changed ->
            assertTrue(base.hasDifferentIdentityFrom(changed))
        }
    }

    @Test
    fun `runtime metadata does not change account or heater identity`() {
        val updatedRuntime = base.copy(
            loginCode = "rotated-login-code",
            macAddress = "AABBCCDDEEFF",
            phoneSystem = "harmony",
            version = "2.0.0",
        )

        assertFalse(base.hasDifferentIdentityFrom(updatedRuntime))
    }
}
