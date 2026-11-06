package cn.qzxy.hotwater

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Only real QZXY responses count as connectivity; cancelled/older requests cannot restore it. */
internal object QzxyConnectionState {
    private val mutableReachable = MutableStateFlow(false)
    val reachable = mutableReachable.asStateFlow()
    private var sequence = 0L

    @Synchronized fun beginRequest(): Long = ++sequence

    @Synchronized fun complete(request: Long, reachable: Boolean) {
        if (request == sequence) mutableReachable.value = reachable
    }

    @Synchronized fun disconnected() {
        sequence++
        mutableReachable.value = false
    }
}
