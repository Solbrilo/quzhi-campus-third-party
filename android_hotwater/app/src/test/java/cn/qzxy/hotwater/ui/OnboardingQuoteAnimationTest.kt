package cn.qzxy.hotwater.ui

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingQuoteAnimationTest {
    @Test fun secondPhraseFollowsTheFirstBySixHundredMillis() {
        assertEquals(0f, welcomeCharacterProgress(0, 0, 120f), 0f)
        assertTrue(welcomeCharacterProgress(0, 0, 121f) > 0f)
        assertEquals(0f, welcomeCharacterProgress(1, 0, 720f), 0f)
        assertTrue(welcomeCharacterProgress(1, 0, 721f) > 0f)
        assertEquals(welcomeCharacterProgress(0, 0, 500f), welcomeCharacterProgress(1, 0, 1100f), 0f)
    }

    @Test fun eachPhraseUnfoldsOverNineHundredMillisIncludingItsPunctuation() {
        assertTrue(welcomeCharacterProgress(0, 5, 1019f) < 1f)
        assertEquals(1f, welcomeCharacterProgress(0, 5, 1020f), 0f)
        assertTrue(welcomeCharacterProgress(1, 5, 1619f) < 1f)
        assertEquals(1f, welcomeCharacterProgress(1, 5, 1620f), 0f)
    }

    @Test fun attributionWaitsSixHundredMillisAfterThePoemThenFadesInGently() {
        assertEquals(1f, welcomeCharacterProgress(1, 5, 1620f), 0f)
        assertEquals(0f, welcomeAttributionProgress(2220f), 0f)
        assertTrue(welcomeAttributionProgress(2221f) > 0f)
        assertTrue(welcomeAttributionProgress(3019f) < 1f)
        assertEquals(1f, welcomeAttributionProgress(3020f), 0f)
    }

    @Test fun threeHapticCuesFollowThePhraseAndAttributionEntrances() {
        assertEquals(0, welcomeHapticCueCount(119f))
        assertEquals(1, welcomeHapticCueCount(120f))
        assertEquals(1, welcomeHapticCueCount(719f))
        assertEquals(2, welcomeHapticCueCount(720f))
        assertEquals(2, welcomeHapticCueCount(2219f))
        assertEquals(3, welcomeHapticCueCount(2220f))
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
