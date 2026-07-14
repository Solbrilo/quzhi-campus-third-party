package cn.qzxy.hotwater.designsystem.motion

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.junit.Assert.assertEquals
import org.junit.Test

class GlassHapticsTest {
    @Test
    fun api34KeepsModernSemanticTypes() {
        assertEquals(
            HapticFeedbackType.ToggleOn,
            compatibleHapticFeedbackType(HapticFeedbackType.ToggleOn, 34),
        )
        assertEquals(
            HapticFeedbackType.SegmentTick,
            compatibleHapticFeedbackType(HapticFeedbackType.SegmentTick, 34),
        )
    }

    @Test
    fun api30To33FallsBackForSegmentAndToggleOnly() {
        assertEquals(
            HapticFeedbackType.VirtualKey,
            compatibleHapticFeedbackType(HapticFeedbackType.SegmentTick, 33),
        )
        assertEquals(
            HapticFeedbackType.VirtualKey,
            compatibleHapticFeedbackType(HapticFeedbackType.ToggleOff, 30),
        )
        assertEquals(
            HapticFeedbackType.Confirm,
            compatibleHapticFeedbackType(HapticFeedbackType.Confirm, 30),
        )
        assertEquals(
            HapticFeedbackType.Reject,
            compatibleHapticFeedbackType(HapticFeedbackType.Reject, 30),
        )
    }

    @Test
    fun api26UsesOnlyLegacySafeFeedback() {
        assertEquals(
            HapticFeedbackType.VirtualKey,
            compatibleHapticFeedbackType(HapticFeedbackType.ToggleOn, 26),
        )
        assertEquals(
            HapticFeedbackType.ContextClick,
            compatibleHapticFeedbackType(HapticFeedbackType.Confirm, 26),
        )
        assertEquals(
            HapticFeedbackType.LongPress,
            compatibleHapticFeedbackType(HapticFeedbackType.Reject, 26),
        )
    }
}
