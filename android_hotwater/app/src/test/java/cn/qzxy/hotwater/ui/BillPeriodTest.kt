package cn.qzxy.hotwater.ui
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth
class BillPeriodTest {
    @Test fun movingFromPastDecemberToCurrentYearStopsAtCurrentMonth() {
        val today = YearMonth.of(2026, 3)
        assertEquals(today, clampBillPeriod(YearMonth.of(2026, 12), today))
        assertEquals(YearMonth.of(2025, 12), clampBillPeriod(YearMonth.of(2025, 12), today))
        assertEquals(YearMonth.of(2010, 1), clampBillPeriod(YearMonth.of(2009, 12), today))
    }
}
