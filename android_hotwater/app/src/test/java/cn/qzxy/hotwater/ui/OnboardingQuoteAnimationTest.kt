package cn.qzxy.hotwater.ui

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingQuoteAnimationTest {
    @Test fun phrasesRevealTogetherAndHapticAtEachEntrance() {
        assertEquals(0, welcomeHapticCueCount(179f))
        assertEquals(1, welcomeHapticCueCount(180f))
        assertEquals(1, welcomeHapticCueCount(899f))
        assertEquals(2, welcomeHapticCueCount(900f))
        assertEquals(welcomeCharacterProgress(0, 500f), welcomeCharacterProgress(1, 500f), 0f)
        assertEquals(0f, welcomeCharacterProgress(2, 500f), 0f)
        assertEquals(welcomeCharacterProgress(2, 1200f), welcomeCharacterProgress(5, 1200f), 0f)
        assertEquals(1f, welcomeCharacterProgress(5, 1900f), 0f)
        assertEquals(2, welcomeHapticCueCount(10000f))
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
