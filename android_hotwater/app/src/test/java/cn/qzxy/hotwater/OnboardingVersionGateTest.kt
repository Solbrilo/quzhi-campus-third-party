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
}
