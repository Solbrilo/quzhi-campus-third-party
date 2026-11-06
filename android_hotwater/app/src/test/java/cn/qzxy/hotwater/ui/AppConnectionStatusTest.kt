package cn.qzxy.hotwater.ui
import org.junit.Assert.assertEquals
import org.junit.Test
class AppConnectionStatusTest {
    @Test fun noNetworkOrUnreachableServerOverridesCachedBusyState() {
        assertEquals(AppConnectionStatus.OFFLINE, resolveAppConnectionStatus(false, true, true))
        assertEquals(AppConnectionStatus.OFFLINE, resolveAppConnectionStatus(true, false, true))
    }
    @Test fun connectedServerShowsBusyOnlyForAnotherUsersSession() {
        assertEquals(AppConnectionStatus.DEVICE_BUSY, resolveAppConnectionStatus(true, true, true))
        assertEquals(AppConnectionStatus.CONNECTED, resolveAppConnectionStatus(true, true, false))
    }
}
