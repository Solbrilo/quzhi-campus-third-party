package cn.qzxy.hotwater.ui

import cn.qzxy.hotwater.HotWaterConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigFormSyncTest {
    @Test
    fun loginRefreshFillsAccountFieldsWhenTheRestoredFormIsEmpty() {
        val config = HotWaterConfig(
            projectId = 12,
            userId = 34,
            accountId = 56,
            loginCode = "login-code",
            telephone = "13800138000",
        )
        val refreshed = refresh(config = config)

        assertEquals("12", refreshed.projectId)
        assertEquals("34", refreshed.userId)
        assertEquals("56", refreshed.accountId)
        assertEquals("login-code", refreshed.loginCode)
        assertEquals("13800138000", refreshed.telephone)
        assertEquals("", refreshed.snCode)
        assertFalse(refreshed.snCodeDirty)
    }

    @Test
    fun loginRefreshUsesAuthoritativeAccountFieldsAndKeepsUnsavedSnCode() {
        val refreshed = refresh(
            config = HotWaterConfig(
                projectId = 12,
                userId = 34,
                accountId = 56,
                loginCode = "login-code",
                telephone = "13800138000",
                snCode = "AABBCCDDEEFF",
            ),
            currentSnCode = "c4:7f0eda85d8",
            snCodeDirty = true,
        )

        assertEquals("12", refreshed.projectId)
        assertEquals("34", refreshed.userId)
        assertEquals("56", refreshed.accountId)
        assertEquals("login-code", refreshed.loginCode)
        assertEquals("13800138000", refreshed.telephone)
        assertEquals("C47F0EDA85D8", refreshed.snCode)
        assertTrue(refreshed.snCodeDirty)
    }

    @Test
    fun differentAccountDropsUnsavedSnCodeDraft() {
        val refreshed = refreshConfigFormFromConfig(
            config = HotWaterConfig(
                telephone = "13900139000",
                loginCode = "new-login-code",
            ),
            currentSnCode = "AABBCCDDEEFF",
            snCodeDirty = true,
            preserveSnCodeDraft = shouldKeepSnCodeDraft(
                snCodeDirty = true,
                draftSourceTelephone = "13800138000",
                refreshedTelephone = "13900139000",
            ),
        )

        assertEquals("", refreshed.snCode)
        assertFalse(refreshed.snCodeDirty)
    }

    @Test
    fun firstLoginKeepsAnUnownedSnCodeDraft() {
        assertTrue(
            shouldKeepSnCodeDraft(
                snCodeDirty = true,
                draftSourceTelephone = "",
                refreshedTelephone = "13800138000",
            ),
        )
    }

    @Test
    fun matchingSnCodeDraftIsNoLongerDirty() {
        val refreshed = refresh(
            config = HotWaterConfig(snCode = "AABBCCDDEEFF"),
            currentSnCode = "aabb:ccddeeff",
            snCodeDirty = true,
        )

        assertEquals("AABBCCDDEEFF", refreshed.snCode)
        assertFalse(refreshed.snCodeDirty)
    }

    @Test
    fun fingerprintOnlyChangesWhenDisplayedConfigFieldsChange() {
        val base = HotWaterConfig(
            projectId = 1,
            userId = 2,
            accountId = 3,
            loginCode = "code",
            telephone = "13800138000",
            snCode = "aabb:ccddeeff",
            phoneSystem = "android",
        )

        assertEquals(
            configFormFingerprint(base),
            configFormFingerprint(base.copy(phoneSystem = "harmonyos")),
        )
        assertTrue(
            configFormFingerprint(base) != configFormFingerprint(base.copy(loginCode = "new-code")),
        )
    }

    @Test
    fun unchangedConfigDoesNotReplaceAnUnsavedFormDraft() {
        val fingerprint = configFormFingerprint(HotWaterConfig())

        assertFalse(shouldRefreshConfigForm(fingerprint, fingerprint))
        assertTrue(shouldRefreshConfigForm(fingerprint, configFormFingerprint(
            HotWaterConfig(loginCode = "new-code"),
        )))
    }

    private fun refresh(
        config: HotWaterConfig,
        currentSnCode: String = "",
        snCodeDirty: Boolean = false,
    ): ConfigFormRefresh = refreshConfigFormFromConfig(
        config = config,
        currentSnCode = currentSnCode,
        snCodeDirty = snCodeDirty,
    )
}
