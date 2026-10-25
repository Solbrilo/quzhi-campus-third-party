package cn.qzxy.hotwater.ui

import cn.qzxy.hotwater.ServerBill
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class BillPresentationTest {
    private val zoneId = ZoneId.of("Asia/Shanghai")

    @Test fun monthlyFilterUsesServerConsumptionDate() {
        val entries = buildBillEntries(listOf(bill("june", 2026, 6, 1250), bill("july", 2026, 7, 750)))
        assertEquals(listOf("july"), filterBillEntries(entries, BillScope.MONTH, 2026, 7).map { it.bill.id })
        assertEquals(listOf(7, 6), entries.map { it.month })
    }

    @Test fun summaryAddsServerAmountsExactlyAndKeepsUnknownSeparateFromZero() {
        val summary = summarizeBillEntries(buildBillEntries(listOf(
            bill("1", 2026, 7, 1), bill("2", 2026, 7, 9),
            bill("3", 2026, 7, 0), bill("4", 2026, 7, null))))
        assertEquals(4, summary.recordCount)
        assertEquals(3, summary.settledCount)
        assertEquals(1, summary.pendingCount)
        assertEquals(10L, summary.totalMilliYuan)
        assertEquals("¥0.01", formatBillAmount(summary.totalMilliYuan))
        assertEquals("< ¥0.01", formatBillAmount(1))
    }

    @Test fun yearViewIncludesAllLoadedMonthsAndUsesStableGroups() {
        val entries = buildBillEntries(listOf(bill("1", 2025, 12, 1000), bill("2", 2026, 1, 2000), bill("3", 2026, 2, 3000)))
        assertEquals(listOf("month:2026-02", "month:2026-01", "month:2025-12"), groupBillEntries(entries, BillGrouping.MONTH).map { it.key })
        val yearly = filterBillEntries(entries, BillScope.YEAR, 2026, 2)
        assertEquals(2, yearly.size)
        assertTrue(groupBillEntries(yearly, BillGrouping.YEAR).single().entries.all { it.year == 2026 })
    }

    @Test fun estimatedWaterUsesTheConfiguredPriceAndServerVolumeWins() {
        assertEquals(95_000L, estimateWaterMilliLiters(2_565L))
        assertEquals(7_000L, estimateWaterMilliLiters(189L))
        assertEquals("95 L", formatWaterVolume(95_000L))
        assertEquals("7.5 L", formatWaterVolume(7_500L))

        val apiVolume = bill("server-volume", 2026, 7, 2_565).copy(waterMilliLiters = 120_000L)
        assertEquals(BillWaterVolume(120_000L, estimated = false), billWaterVolume(apiVolume))
    }

    @Test fun billSummaryIncludesAvailableAndEstimatedWaterVolumes() {
        val records = buildBillEntries(listOf(
            bill("95l", 2026, 7, 2_565),
            bill("7l", 2026, 7, 189),
            bill("unknown", 2026, 7, null),
        ))
        val summary = summarizeBillEntries(records)
        assertEquals(102_000L, summary.totalWaterMilliLiters)
        assertEquals(2, summary.waterRecordCount)
        assertEquals(2, summary.estimatedWaterRecordCount)
    }

    @Test fun duplicateResponsesDoNotDoubleTheTotal() {
        val record = bill("same-order", 2026, 7, 120)
        assertEquals(120L, summarizeBillEntries(buildBillEntries(listOf(record, record))).totalMilliYuan)
    }

    private fun bill(id: String, year: Int, month: Int, amount: Long?) = ServerBill(
        id = id, orderId = id, orderNo = null, consumeDate = "$year-$month-01 10:00:00",
        amountMilliYuan = amount, description = "热水消费",
        timestamp = LocalDateTime.of(year, month, 1, 10, 0).atZone(zoneId).toInstant().toEpochMilli(),
        period = YearMonth.of(year, month),
    )
}
