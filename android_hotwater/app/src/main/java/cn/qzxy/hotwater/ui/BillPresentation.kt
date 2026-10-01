package cn.qzxy.hotwater.ui

import cn.qzxy.hotwater.ServerBill
import java.math.BigDecimal
import java.math.RoundingMode

internal enum class BillScope {
    YEAR,
    MONTH,
}

internal enum class BillGrouping {
    YEAR,
    MONTH,
}

internal data class BillEntry(
    val bill: ServerBill,
    val endedAtMillis: Long,
    val year: Int,
    val month: Int,
)

internal data class BillSummary(
    val recordCount: Int,
    val settledCount: Int,
    val pendingCount: Int,
    val totalMilliYuan: Long,
)

internal data class BillGroup(
    val key: String,
    val label: String,
    val entries: List<BillEntry>,
    val summary: BillSummary,
)

internal fun buildBillEntries(bills: List<ServerBill>): List<BillEntry> = bills
    .distinctBy { it.id }
    .map { BillEntry(it, it.timestamp ?: 0L, it.period.year, it.period.monthValue) }
    .sortedWith(compareByDescending<BillEntry> { it.year }.thenByDescending { it.month }.thenByDescending { it.endedAtMillis })

internal fun filterBillEntries(
    entries: List<BillEntry>,
    scope: BillScope,
    year: Int?,
    month: Int,
): List<BillEntry> {
    val selectedYear = year ?: return emptyList()
    return entries.filter { entry ->
        entry.year == selectedYear &&
            (scope != BillScope.MONTH || entry.month == month)
    }
}

internal fun groupBillEntries(
    entries: List<BillEntry>,
    grouping: BillGrouping,
): List<BillGroup> {
    val groups = linkedMapOf<String, MutableList<BillEntry>>()
    entries.forEach { entry ->
        val key = when (grouping) {
            BillGrouping.YEAR -> "year:${entry.year}"
            BillGrouping.MONTH -> "month:${entry.year}-${entry.month.toString().padStart(2, '0')}"
        }
        groups.getOrPut(key) { mutableListOf() }.add(entry)
    }
    return groups.map { (key, groupEntries) ->
        val first = groupEntries.first()
        BillGroup(
            key = key,
            label = when (grouping) {
                BillGrouping.YEAR -> "${first.year}年"
                BillGrouping.MONTH -> "${first.year}年${first.month}月"
            },
            entries = groupEntries,
            summary = summarizeBillEntries(groupEntries),
        )
    }
}

internal fun summarizeBillEntries(entries: List<BillEntry>): BillSummary {
    var settledCount = 0
    var pendingCount = 0
    var totalMilliYuan = 0L

    entries.forEach { entry ->
        val milliYuan = entry.bill.amountMilliYuan
            ?.takeIf { it >= 0L }
        if (milliYuan == null) {
            pendingCount += 1
        } else {
            settledCount += 1
            totalMilliYuan = saturatingAdd(totalMilliYuan, milliYuan)
        }
    }

    return BillSummary(
        recordCount = entries.size,
        settledCount = settledCount,
        pendingCount = pendingCount,
        totalMilliYuan = totalMilliYuan,
    )
}

internal fun formatBillAmount(totalMilliYuan: Long): String {
    return if (totalMilliYuan in 1L..9L) {
        "< ¥0.01"
    } else {
        val yuan = BigDecimal.valueOf(totalMilliYuan)
            .movePointLeft(3)
            .setScale(2, RoundingMode.HALF_UP)
        "¥$yuan"
    }
}

private fun saturatingAdd(left: Long, right: Long): Long {
    return if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
}
