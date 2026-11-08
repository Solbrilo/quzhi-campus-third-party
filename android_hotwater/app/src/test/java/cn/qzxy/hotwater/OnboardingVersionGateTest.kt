package cn.qzxy.hotwater

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingVersionGateTest {
    @Test fun `first install retains account setup even without an experience receipt`() {
        assertEquals(InstallationIntroduction.FIRST_INSTALL, OnboardingVersionGate.introduction(null, 31, false))
    }

    @Test fun `upgrades restore the welcome and feature sheet including the previously skipped release`() {
        assertEquals(InstallationIntroduction.UPDATE, OnboardingVersionGate.introduction(29, 31, false))
        assertEquals(InstallationIntroduction.UPDATE, OnboardingVersionGate.introduction(30, 31, false))
        assertEquals(InstallationIntroduction.UPDATE, OnboardingVersionGate.introduction(31, 32, false))
    }

    @Test fun `existing current version users see the new experience exactly once`() {
        assertEquals(InstallationIntroduction.UPDATE, OnboardingVersionGate.introduction(31, 31, false))
        assertEquals(InstallationIntroduction.NONE, OnboardingVersionGate.introduction(31, 31, true))
    }

    @Test fun `receipt cannot suppress a different version introduction`() {
        assertEquals(InstallationIntroduction.UPDATE, OnboardingVersionGate.introduction(30, 31, true))
    }
}
