package cn.qzxy.hotwater.ui

import cn.qzxy.hotwater.HotWaterConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingSessionResumeTest {
    private val configuredSession = HotWaterConfig(
        projectId = 1,
        userId = 2,
        accountId = 3,
        loginCode = "login-code",
        telephone = "13800138000",
        snCode = "C47F0EDA85D8",
    )

    @Test
    fun `existing complete configuration resumes after version onboarding`() {
        assertEquals(
            OnboardingResumeDestination.MAIN,
            resolveOnboardingResumeDestination(configuredSession),
        )
    }

    @Test
    fun `existing account missing sn code resumes at nearby devices`() {
        assertEquals(
            OnboardingResumeDestination.NEARBY,
            resolveOnboardingResumeDestination(configuredSession.copy(snCode = "")),
        )
    }

    @Test
    fun `new install without account configuration continues at login`() {
        assertEquals(
            OnboardingResumeDestination.LOGIN,
            resolveOnboardingResumeDestination(HotWaterConfig()),
        )
        assertEquals(
            OnboardingResumeDestination.LOGIN,
            resolveOnboardingResumeDestination(configuredSession.copy(loginCode = "")),
        )
    }
}
