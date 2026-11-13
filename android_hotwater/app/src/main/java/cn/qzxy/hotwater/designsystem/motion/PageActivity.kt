package cn.qzxy.hotwater.designsystem.motion

import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf

/** Retained pages keep their state, but only visible pages need visual work. */
internal val LocalPageActivity = staticCompositionLocalOf<State<Boolean>> {
    object : State<Boolean> { override val value = true }
}
