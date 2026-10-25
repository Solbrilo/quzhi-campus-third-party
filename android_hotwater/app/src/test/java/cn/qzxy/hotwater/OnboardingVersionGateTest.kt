package cn.qzxy.hotwater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingVersionGateTest {
    @Test
    fun `shows onboarding for first install and any version change`() {
        assertTrue(OnboardingVersionGate.shouldShow(null, 19))
        assertTrue(OnboardingVersionGate.shouldShow(18, 19))
        assertTrue(OnboardingVersionGate.shouldShow(20, 19))
    }

    @Test
    fun `does not show onboarding after the current version is completed`() {
        assertFalse(OnboardingVersionGate.shouldShow(19, 19))
    }

    @Test
    fun `release without upgrade welcome skips existing installations but keeps first install onboarding`() {
        assertFalse(OnboardingVersionGate.shouldShow(29, 30, skipUpgradeForRelease = true))
        assertTrue(OnboardingVersionGate.shouldShow(null, 30, skipUpgradeForRelease = true))
    }

    @Test
    fun `later releases retain their normal upgrade onboarding`() {
        assertTrue(OnboardingVersionGate.shouldShow(30, 31))
    }
}
