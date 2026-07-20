package cn.qzxy.hotwater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HotWaterConfigManagerTest {
    @Test
    fun exportFileNameIsPortableAndDoesNotContainAccountData() {
        val fileName = HotWaterConfigManager.generateExportFileName()

        assertEquals("hotwater_config.json", fileName)
        assertTrue(fileName.all { it.isLetterOrDigit() || it == '_' || it == '.' || it == '-' })
    }

    @Test
    fun exportedMetadataUsesCurrentBuildVersion() {
        val config = HotWaterConfig(
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

        val exported = HotWaterConfigManager.exportConfig(config)

        assertEquals(BuildConfig.VERSION_NAME, exported.getString("appVersion"))
    }
}
