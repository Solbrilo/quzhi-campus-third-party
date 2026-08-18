package cn.qzxy.hotwater.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppConnectionStatusTest {
    @Test
    fun userOfflineTakesPriorityOverTheLastKnownDeviceState() {
        assertEquals(
            AppConnectionStatus.USER_OFFLINE,
            resolveAppConnectionStatus(networkValidated = false, deviceOnline = true),
        )
        assertEquals(
            AppConnectionStatus.USER_OFFLINE,
            resolveAppConnectionStatus(networkValidated = false, deviceOnline = false),
        )
    }

    @Test
    fun reportsExplicitOfflineDeviceAfterNetworkIsValidated() {
        assertEquals(
            AppConnectionStatus.DEVICE_OFFLINE,
            resolveAppConnectionStatus(networkValidated = true, deviceOnline = false),
        )
    }

    @Test
    fun reportsExplicitOnlineDeviceAfterNetworkIsValidated() {
        assertEquals(
            AppConnectionStatus.DEVICE_ONLINE,
            resolveAppConnectionStatus(networkValidated = true, deviceOnline = true),
        )
    }

    @Test
    fun hidesTheIndicatorUntilTheDeviceStateIsKnown() {
        assertEquals(
            AppConnectionStatus.UNKNOWN,
            resolveAppConnectionStatus(networkValidated = true, deviceOnline = null),
        )
    }
}
