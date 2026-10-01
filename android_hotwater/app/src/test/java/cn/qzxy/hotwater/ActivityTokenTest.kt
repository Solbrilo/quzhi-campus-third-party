package cn.qzxy.hotwater

import org.junit.Assert.*
import org.junit.Test

class ActivityTokenTest {
    @Test fun tokenDeduplicatesOneInstallationPerDayAndRotatesAcrossDays() {
        val seed = ByteArray(32) { it.toByte() }
        val first = ActivityToken.forDay(seed, "2026-10-01")
        assertEquals(first, ActivityToken.forDay(seed, "2026-10-01"))
        assertTrue(first.matches(Regex("[a-f0-9]{64}")))
        assertNotEquals(first, ActivityToken.forDay(seed, "2026-10-02"))
        assertNotEquals(first, ActivityToken.forDay(ByteArray(32) { 7 }, "2026-10-01"))
    }
}
