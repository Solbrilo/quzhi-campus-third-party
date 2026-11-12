package cn.qzxy.hotwater.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalTime
import kotlinx.coroutines.delay

internal fun greetingForHour(hour: Int): String = when (hour) {
    in 5..8 -> "早上好！"
    in 9..11 -> "上午好！"
    in 12..13 -> "中午好！"
    in 14..17 -> "下午好！"
    else -> "晚上好！"
}

@Composable
internal fun currentTimeGreeting(): String {
    var greeting by remember { mutableStateOf(greetingForHour(LocalTime.now().hour)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { greeting = greetingForHour(LocalTime.now().hour) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = LocalTime.now()
                greeting = greetingForHour(now.hour)
                // Re-read device local time (including timezone changes) at each minute boundary.
                delay((60 - now.second) * 1000L - now.nano / 1_000_000L)
            }
        }
    }
    return greeting
}
