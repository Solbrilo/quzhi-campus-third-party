package cn.qzxy.hotwater

import org.junit.Assert.*
import org.junit.Test

class WidgetActionPolicyTest {
    private val now = 1_000_000L
    private fun state(age: Long, busy: Boolean = true) = HotWaterConfigStore.WidgetState(
        title = "", message = "", tone = "busy", isBusy = busy, updatedAtMillis = now - age,
    )

    @Test fun activeWorkIsDebouncedButCrashedOrClockShiftedWorkRecovers() {
        assertTrue(WidgetActionPolicy.isBusy(state(30_000), now))
        assertFalse(WidgetActionPolicy.isBusy(state(WidgetActionPolicy.MAX_BUSY_AGE_MS + 1), now))
        assertFalse(WidgetActionPolicy.isBusy(state(-1), now))
        assertFalse(WidgetActionPolicy.isBusy(state(1, busy = false), now))
    }

    @Test fun queuedCommandCannotFollowAccountOrDeviceSwitch() {
        assertTrue(WidgetActionPolicy.canExecute(4, 4, now - 1000, now))
        assertFalse(WidgetActionPolicy.canExecute(4, 5, now - 1000, now))
        assertFalse(WidgetActionPolicy.canExecute(-1, 5, now - 1000, now))
    }

    @Test fun expiredCommandsNeverExecuteWhenNetworkReturnsLater() {
        assertFalse(WidgetActionPolicy.canExecute(4, 4, 0, now))
        assertFalse(WidgetActionPolicy.canExecute(4, 4, now + 1, now))
        assertFalse(WidgetActionPolicy.canExecute(4, 4, now - WidgetActionPolicy.MAX_QUEUE_AGE_MS - 1, now))
    }
}
