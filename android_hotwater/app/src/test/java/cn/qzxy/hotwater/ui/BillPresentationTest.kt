package cn.qzxy.hotwater.ui

import cn.qzxy.hotwater.OperationHistory
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillPresentationTest {
    private val zoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun monthlyFilterUsesTheCloseSettlementTime() {
        val june = closedRecord(
            id = 1L,
            time = at(2026, 6, 30, 23, 59),
            consumptionYuan = 1.25,
        )
        val july = closedRecord(
            id = 2L,
            time = at(2026, 7, 1, 0, 0),
            consumptionYuan = 0.75,
        )
        val failedClose = closedRecord(
            id = 3L,
            time = at(2026, 7, 2, 0, 0),
            consumptionYuan = 9.0,
            success = false,
        )

        val entries = buildBillEntries(listOf(june, july, failedClose), zoneId)
        val julyEntries = filterBillEntries(entries, BillScope.MONTH, 2026, 7)

        assertEquals(listOf(2L), julyEntries.map { it.history.id })
        assertEquals(listOf(2026), entries.map { it.year }.distinct())
        assertEquals(listOf(7, 6), entries.map { it.month })
    }

    @Test
    fun summaryUsesMilliYuanAndKeepsZeroAsSettled() {
        val entries = buildBillEntries(
            listOf(
                closedRecord(1L, at(2026, 7, 4, 10, 0), 0.001, durationMillis = 1_000L),
                closedRecord(2L, at(2026, 7, 4, 11, 0), 0.009, durationMillis = 2_000L),
                closedRecord(3L, at(2026, 7, 4, 12, 0), 0.0, durationMillis = 3_000L),
                closedRecord(4L, at(2026, 7, 4, 13, 0), null, durationMillis = 4_000L),
            ),
            zoneId,
        )

        val summary = summarizeBillEntries(entries)

        assertEquals(4, summary.recordCount)
        assertEquals(3, summary.settledCount)
        assertEquals(1, summary.pendingCount)
        assertEquals(10L, summary.totalMilliYuan)
        assertEquals(10_000L, summary.totalDurationMillis)
        assertEquals("¥0.01", formatBillAmount(summary.totalMilliYuan))
        assertEquals("< ¥0.01", formatBillAmount(1L))
    }

    @Test
    fun groupingKeepsNewestPeriodsFirstAndUsesStableKeys() {
        val entries = buildBillEntries(
            listOf(
                closedRecord(1L, at(2025, 12, 31, 23, 0), 1.0),
                closedRecord(2L, at(2026, 1, 1, 0, 0), 2.0),
                closedRecord(3L, at(2026, 2, 1, 0, 0), 3.0),
            ),
            zoneId,
        )

        val monthlyGroups = groupBillEntries(entries, BillGrouping.MONTH)
        val yearlyGroups = groupBillEntries(entries, BillGrouping.YEAR)

        assertEquals(
            listOf("month:2026-02", "month:2026-01", "month:2025-12"),
            monthlyGroups.map { it.key },
        )
        assertEquals(listOf("year:2026", "year:2025"), yearlyGroups.map { it.key })
        assertTrue(yearlyGroups.first().entries.all { it.year == 2026 })
    }

    private fun closedRecord(
        id: Long,
        time: Long,
        consumptionYuan: Double?,
        durationMillis: Long = 0L,
        success: Boolean = true,
    ): OperationHistory {
        return OperationHistory(
            id = id,
            type = OperationHistory.OperationType.CLOSE,
            success = success,
            message = "closed",
            timestamp = time,
            consumptionYuan = consumptionYuan,
            usageDurationMillis = durationMillis,
        )
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }
}
