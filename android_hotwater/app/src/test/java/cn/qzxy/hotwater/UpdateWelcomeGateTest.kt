package cn.qzxy.hotwater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateWelcomeGateTest {
    @Test fun completedOlderInstallGetsWelcome() {
        assertTrue(UpdateWelcomeGate.isUpgrade("3.1.2", 30, 29, 100, 200))
    }

    @Test fun freshInstallDoesNotGetWelcome() {
        assertFalse(UpdateWelcomeGate.isUpgrade("3.1.2", 30, null, 100, 100))
    }

    @Test fun upgradeWithUnfinishedOnboardingGetsWelcome() {
        assertTrue(UpdateWelcomeGate.isUpgrade("3.1.2", 30, null, 100, 200))
    }

    @Test fun sameVersionAndDowngradesDoNotGetWelcome() {
        assertFalse(UpdateWelcomeGate.isUpgrade("3.1.2", 30, 30, 100, 200))
        assertFalse(UpdateWelcomeGate.isUpgrade("3.1.2", 30, 31, 100, 200))
    }

    @Test fun otherReleasesDoNotGetThisWelcome() {
        assertFalse(UpdateWelcomeGate.isUpgrade("3.1.1", 29, 28, 100, 200))
        assertFalse(UpdateWelcomeGate.isUpgrade("3.1.0", 28, 27, 100, 200))
    }

    @Test fun unknownOrInvalidInstallHistoryIsNotAnUpgrade() {
        assertFalse(UpdateWelcomeGate.isUpgrade("3.1.2", 30, null, 0, 200))
        assertFalse(UpdateWelcomeGate.isUpgrade("3.1.2", 30, 0, 100, 200))
        assertFalse(UpdateWelcomeGate.isUpgrade("3.1.2", 30, null, 200, 100))
    }
}
