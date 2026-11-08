package cn.qzxy.hotwater.ui

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingQuoteAnimationTest {
    @Test
    fun charactersAppearFromTopToBottomBeforeEnglish() {
        assertTrue(welcomeCharacterProgress(0, 500f) > 0f)
        assertEquals(0f, welcomeCharacterProgress(5, 500f), 0f)
        assertEquals(1f, welcomeCharacterProgress(5, 1900f), 0f)
        assertEquals(0f, welcomeEnglishProgress(0, 1900f), 0f)
        assertTrue(welcomeEnglishProgress(0, 2600f) > welcomeEnglishProgress(2, 2600f))
        assertEquals(1f, welcomeEnglishProgress(2, 3600f), 0f)
    }

    @Test
    fun progressRemainsValidBeforeAndAfterTimeline() {
        assertEquals(0f, welcomeCharacterProgress(0, -1f), 0f)
        assertEquals(1f, welcomeCharacterProgress(0, 10000f), 0f)
        assertEquals(0f, welcomeEnglishProgress(0, -1f), 0f)
        assertEquals(1f, welcomeEnglishProgress(2, 10000f), 0f)
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
