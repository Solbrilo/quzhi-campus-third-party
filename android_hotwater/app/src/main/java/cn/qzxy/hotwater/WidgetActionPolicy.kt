package cn.qzxy.hotwater

internal object WidgetActionPolicy {
    const val OPERATION_TIMEOUT_MS = 120_000L
    const val MAX_BUSY_AGE_MS = 180_000L
    const val MAX_QUEUE_AGE_MS = 120_000L

    fun isBusy(state: HotWaterConfigStore.WidgetState, now: Long = System.currentTimeMillis()): Boolean =
        state.isBusy && now - state.updatedAtMillis in 0L..MAX_BUSY_AGE_MS

    fun canExecute(queuedGeneration: Long, currentGeneration: Long, queuedAt: Long, now: Long): Boolean =
        queuedGeneration == currentGeneration && queuedAt > 0L && now - queuedAt in 0L..MAX_QUEUE_AGE_MS
}
