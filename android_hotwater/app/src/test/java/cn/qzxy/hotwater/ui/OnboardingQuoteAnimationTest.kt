package cn.qzxy.hotwater.ui

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingQuoteAnimationTest {
    @Test fun phrasesRevealRightToLeftBeforeTheAttributionAndCueThreeHaptics() {
        assertEquals(0, welcomeHapticCueCount(15f))
        assertEquals(1, welcomeHapticCueCount(16f))
        assertEquals(1, welcomeHapticCueCount(179f))
        assertEquals(2, welcomeHapticCueCount(180f))
        assertEquals(2, welcomeHapticCueCount(419f))
        assertEquals(3, welcomeHapticCueCount(420f))
        assertEquals(0f, welcomeCharacterProgress(0, 0, 0f), 0f)
        assertEquals(1f, welcomeCharacterProgress(0, 5, 200f), 0f)
        assertEquals(0f, welcomeCharacterProgress(1, 0, 179f), 0f)
        assertEquals(1f, welcomeCharacterProgress(1, 5, 380f), 0f)
        assertEquals(0f, welcomeAttributionProgress(419f), 0f)
        assertEquals(1f, welcomeAttributionProgress(840f), 0f)
        assertEquals("沐芳莫弹冠，浴兰莫振衣。——李白《沐浴子》", WelcomeQuote)
        assertEquals(3, welcomeHapticCueCount(10000f))
    }

    @Test
    fun pauseAndStopDeactivateQuoteAnimation() {
        assertFalse(
            quoteLifecycleActiveAfterEvent(
                wasActive = true,
                event = Lifecycle.Event.ON_PAUSE,
            ),
        )
        assertFalse(
            quoteLifecycleActiveAfterEvent(
                wasActive = true,
                event = Lifecycle.Event.ON_STOP,
            ),
        )
    }

    @Test
    fun startAndResumeReactivateQuoteAnimation() {
        assertTrue(
            quoteLifecycleActiveAfterEvent(
                wasActive = false,
                event = Lifecycle.Event.ON_START,
            ),
        )
        assertTrue(
            quoteLifecycleActiveAfterEvent(
                wasActive = false,
                event = Lifecycle.Event.ON_RESUME,
            ),
        )
    }
}
