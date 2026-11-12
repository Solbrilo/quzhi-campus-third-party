package cn.qzxy.hotwater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetDisplayStateTest {
    @Test fun animationOnlyFollowsConfirmedPositionChanges() {
        assertEquals(true, widgetSwitchAnimation(false, true, true, true))
        assertEquals(false, widgetSwitchAnimation(true, false, true, true))
        assertNull(widgetSwitchAnimation(null, true, true, true))
        assertNull(widgetSwitchAnimation(true, true, true, true))
        assertNull(widgetSwitchAnimation(false, true, false, true))
        assertNull(widgetSwitchAnimation(false, true, true, false))
    }
    @Test fun offlineOverridesCachedOrderAndOccupancy() {
        assertEquals(DeviceIndicator.OFFLINE, widgetIndicator(false, true, true))
        assertEquals(DeviceIndicator.OFFLINE, widgetIndicator(false, false, false))
    }

    @Test fun ownOrderIsBlueAndIdleAndOccupiedRemainDistinct() {
        assertEquals(DeviceIndicator.USING, widgetIndicator(true, false, true))
        assertEquals(DeviceIndicator.USING, widgetIndicator(true, true, true))
        assertEquals(DeviceIndicator.OCCUPIED, widgetIndicator(true, true, false))
        assertEquals(DeviceIndicator.IDLE, widgetIndicator(true, false, false))
        assertNotEquals(DeviceIndicator.USING.color, DeviceIndicator.IDLE.color)
    }
}
