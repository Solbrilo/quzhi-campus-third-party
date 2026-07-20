package cn.qzxy.hotwater.ui

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingQuoteAnimationTest {
    @Test
    fun quoteLineUsesShortenedRevealDuration() {
        assertEquals(1_300, QuoteLineRevealDurationMillis)
    }

    @Test
    fun quoteLineMovesUpFromItsInitialOffset() {
        assertEquals(
            QuoteLineInitialOffsetDp,
            quoteRevealOffsetDp(0f, QuoteLineInitialOffsetDp),
            0f,
        )
        assertEquals(
            QuoteLineInitialOffsetDp / 2f,
            quoteRevealOffsetDp(0.5f, QuoteLineInitialOffsetDp),
            0f,
        )
        assertEquals(
            0f,
            quoteRevealOffsetDp(1f, QuoteLineInitialOffsetDp),
            0f,
        )
    }

    @Test
    fun revealOffsetClampsProgressAtTimelineBounds() {
        assertEquals(
            QuoteLineInitialOffsetDp,
            quoteRevealOffsetDp(-1f, QuoteLineInitialOffsetDp),
            0f,
        )
        assertEquals(
            0f,
            quoteRevealOffsetDp(2f, QuoteLineInitialOffsetDp),
            0f,
        )
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
