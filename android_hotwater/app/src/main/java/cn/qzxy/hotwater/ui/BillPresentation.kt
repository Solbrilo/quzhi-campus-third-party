package cn.qzxy.hotwater.ui

import cn.qzxy.hotwater.HotWaterPayload
import cn.qzxy.hotwater.OperationHistory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId

internal enum class BillScope {
    YEAR,
    MONTH,
}

internal enum class BillGrouping {
    YEAR,
    MONTH,
}

internal data class BillEntry(
    val history: OperationHistory,
    val endedAtMillis: Long,
    val year: Int,
    val month: Int,
)

internal data class BillSummary(
    val recordCount: Int,
    val settledCount: Int,
    val pendingCount: Int,
    val totalMilliYuan: Long,
    val totalDurationMillis: Long,
)

internal data class BillGroup(
    val key: String,
    val label: String,
    val entries: List<BillEntry>,
    val summary: BillSummary,
)

internal fun buildBillEntries(
    history: List<OperationHistory>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<BillEntry> {
    return history.asSequence()
        .filter { it.type == OperationHistory.OperationType.CLOSE && it.success }
        .mapNotNull { record ->
            if (record.timestamp <= 0L) return@mapNotNull null
            val dateTime = runCatching {
                Instant.ofEpochMilli(record.timestamp).atZone(zoneId)
            }.getOrNull() ?: return@mapNotNull null
            BillEntry(
                history = record,
                endedAtMillis = record.timestamp,
                year = dateTime.year,
                month = dateTime.monthValue,
            )
        }
        .sortedWith(
            compareByDescending<BillEntry> { it.endedAtMillis }
                .thenByDescending { it.history.id },
        )
        .toList()
}

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
    var totalDurationMillis = 0L

    entries.forEach { entry ->
        val milliYuan = entry.history.consumptionYuan
            ?.let(HotWaterPayload::toMilliYuan)
            ?.takeIf { it >= 0L }
        if (milliYuan == null) {
            pendingCount += 1
        } else {
            settledCount += 1
            totalMilliYuan = saturatingAdd(totalMilliYuan, milliYuan)
        }
        totalDurationMillis = saturatingAdd(
            totalDurationMillis,
            (entry.history.usageDurationMillis ?: 0L).coerceAtLeast(0L),
        )
    }

    return BillSummary(
        recordCount = entries.size,
        settledCount = settledCount,
        pendingCount = pendingCount,
        totalMilliYuan = totalMilliYuan,
        totalDurationMillis = totalDurationMillis,
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

internal fun formatBillDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun saturatingAdd(left: Long, right: Long): Long {
    return if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
}
