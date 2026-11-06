package cn.qzxy.hotwater
import org.junit.Assert.*
import org.junit.Test
class UpdatePromptPolicyTest {
    @Test fun ignoredVersionStaysSilentUntilStrictlyNewerRelease() {
        assertFalse(shouldPromptForUpdate("3.2.0", "3.2.0", false))
        assertFalse(shouldPromptForUpdate("3.1.9", "3.2.0", false))
        assertTrue(shouldPromptForUpdate("3.10.0", "3.2.0", false))
        assertTrue(shouldPromptForUpdate("4.0.0", "3.10.0", false))
    }
    @Test fun manualCheckAndFirstCheckCanShowRelease() {
        assertTrue(shouldPromptForUpdate("3.2.0", "3.2.0", true))
        assertTrue(shouldPromptForUpdate("3.2.0", null, false))
    }
}
