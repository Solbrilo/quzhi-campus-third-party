package cn.qzxy.hotwater.designsystem.motion

import android.os.Build
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

fun HapticFeedback.performGlassHapticFeedback(type: HapticFeedbackType) {
    performHapticFeedback(
        compatibleHapticFeedbackType(
            requested = type,
            sdkInt = Build.VERSION.SDK_INT,
        ),
    )
}

internal fun compatibleHapticFeedbackType(
    requested: HapticFeedbackType,
    sdkInt: Int,
): HapticFeedbackType {
    if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return requested

    return when (requested) {
        HapticFeedbackType.SegmentTick,
        HapticFeedbackType.SegmentFrequentTick,
        HapticFeedbackType.ToggleOn,
        HapticFeedbackType.ToggleOff,
        HapticFeedbackType.GestureEnd,
        HapticFeedbackType.GestureThresholdActivate,
        -> HapticFeedbackType.VirtualKey

        HapticFeedbackType.Confirm -> if (sdkInt >= Build.VERSION_CODES.R) {
            requested
        } else {
            HapticFeedbackType.ContextClick
        }

        HapticFeedbackType.Reject -> if (sdkInt >= Build.VERSION_CODES.R) {
            requested
        } else {
            HapticFeedbackType.LongPress
        }

        else -> requested
    }
}
