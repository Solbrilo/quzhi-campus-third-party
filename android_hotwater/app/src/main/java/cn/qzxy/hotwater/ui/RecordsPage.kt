package cn.qzxy.hotwater.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.OperationHistory
import cn.qzxy.hotwater.designsystem.component.GlassPressable
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.LocalGlassEnvironment
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassMaterial

private data class BillPickerOption(
    val key: String,
    val label: String,
)

@Composable
fun RecordsPage(history: List<OperationHistory>) {
    val tokens = LocalGlassTokens.current
    val allEntries = remember(history) { buildBillEntries(history) }
    val availableYears = remember(allEntries) {
        allEntries.map(BillEntry::year).distinct().sortedDescending()
    }
    var scope by rememberSaveable { mutableStateOf(BillScope.MONTH) }
    var selectedYear by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedMonth by rememberSaveable { mutableStateOf<Int?>(null) }
    var grouping by rememberSaveable { mutableStateOf(BillGrouping.MONTH) }
    var collapsedGroupKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var appliedCollapseDefaultsKey by rememberSaveable { mutableStateOf("") }

    val activeYear = selectedYear?.takeIf { it in availableYears } ?: availableYears.firstOrNull()
    val activeMonth = selectedMonth?.takeIf { it in 1..12 }
        ?: allEntries.firstOrNull { it.year == activeYear }?.month
        ?: 1
    val filteredEntries = remember(allEntries, scope, activeYear, activeMonth) {
        filterBillEntries(
            entries = allEntries,
            scope = scope,
            year = activeYear,
            month = activeMonth,
        )
    }
    val summary = remember(filteredEntries) { summarizeBillEntries(filteredEntries) }
    val groups = remember(filteredEntries, grouping) {
        groupBillEntries(filteredEntries, grouping)
    }
    val collapseDefaultsKey = grouping.name + ":" + groups.joinToString(separator = "|") { it.key }

    LaunchedEffect(collapseDefaultsKey) {
        if (appliedCollapseDefaultsKey != collapseDefaultsKey) {
            collapsedGroupKeys = groups.drop(1).map(BillGroup::key)
            appliedCollapseDefaultsKey = collapseDefaultsKey
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "账单",
                    color = tokens.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "按结算时间查看月度或年度用水汇总。",
                    color = tokens.textSecondary,
                    fontSize = 13.sp,
                )
            }
        }

        if (allEntries.isEmpty()) {
            item { EmptyBillState() }
        } else {
            item {
                BillFilters(
                    scope = scope,
                    onScopeChange = { scope = it },
                    availableYears = availableYears,
                    activeYear = activeYear,
                    onYearChange = { selectedYear = it },
                    selectedMonth = activeMonth,
                    onMonthChange = { selectedMonth = it },
                    grouping = grouping,
                    onGroupingChange = { grouping = it },
                )
            }
            item {
                BillSummaryCard(
                    title = when (scope) {
                        BillScope.YEAR -> "${activeYear ?: "--"} 年度汇总"
                        BillScope.MONTH -> "${activeYear ?: "--"}年${activeMonth}月汇总"
                    },
                    summary = summary,
                )
            }

            if (filteredEntries.isEmpty()) {
                item { EmptyFilteredBillState() }
            } else {
                groups.forEach { group ->
                    val collapsed = group.key in collapsedGroupKeys
                    item(key = "bill_group_${group.key}") {
                        BillGroupSection(
                            group = group,
                            collapsed = collapsed,
                            onClick = {
                                collapsedGroupKeys = if (collapsed) {
                                    collapsedGroupKeys - group.key
                                } else {
                                    collapsedGroupKeys + group.key
                                }
                            },
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(bottomDockClearance())) }
    }
}

@Composable
private fun BillFilters(
    scope: BillScope,
    onScopeChange: (BillScope) -> Unit,
    availableYears: List<Int>,
    activeYear: Int?,
    onYearChange: (Int) -> Unit,
    selectedMonth: Int,
    onMonthChange: (Int) -> Unit,
    grouping: BillGrouping,
    onGroupingChange: (BillGrouping) -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val yearOptions = availableYears.map { year ->
        BillPickerOption(key = year.toString(), label = "${year}年")
    }
    val monthOptions = (1..12).map { month ->
        BillPickerOption(key = month.toString(), label = "${month}月")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassMaterial(GlassMaterial.Panel, 18.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "账期筛选",
            color = tokens.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        BillSegmentedControl(
            selectedIndex = if (scope == BillScope.YEAR) 0 else 1,
            labels = listOf("年度", "月度"),
            onSelected = { index ->
                onScopeChange(if (index == 0) BillScope.YEAR else BillScope.MONTH)
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BillPicker(
                label = "年份",
                selectedLabel = activeYear?.let { "${it}年" } ?: "暂无数据",
                options = yearOptions,
                onSelected = { onYearChange(it.key.toInt()) },
                modifier = Modifier.weight(1f),
            )
            if (scope == BillScope.MONTH) {
                BillPicker(
                    label = "月份",
                    selectedLabel = "${selectedMonth}月",
                    options = monthOptions,
                    onSelected = { onMonthChange(it.key.toInt()) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = "折叠方式",
            color = tokens.textSecondary,
            fontSize = 12.sp,
        )
        BillSegmentedControl(
            selectedIndex = if (grouping == BillGrouping.YEAR) 0 else 1,
            labels = listOf("按年", "按月"),
            onSelected = { index ->
                onGroupingChange(if (index == 0) BillGrouping.YEAR else BillGrouping.MONTH)
            },
        )
    }
}

@Composable
private fun BillSegmentedControl(
    selectedIndex: Int,
    labels: List<String>,
    onSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val tokens = LocalGlassTokens.current
            GlassPressable(
                onClick = { onSelected(index) },
                selected = selected,
                material = if (selected) GlassMaterial.Panel else GlassMaterial.EmbeddedControl,
                cornerRadius = 12.dp,
                contentPadding = PaddingValues(vertical = 10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = label,
                    color = if (selected) tokens.accentPrimary else tokens.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun BillPicker(
    label: String,
    selectedLabel: String,
    options: List<BillPickerOption>,
    onSelected: (BillPickerOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalGlassTokens.current
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        GlassPressable(
            onClick = { expanded = true },
            material = GlassMaterial.EmbeddedControl,
            cornerRadius = 12.dp,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        color = tokens.textSecondary,
                        fontSize = 11.sp,
                    )
                    Text(
                        text = selectedLabel,
                        color = tokens.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "${label}选择",
                    tint = tokens.textSecondary,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun BillSummaryCard(
    title: String,
    summary: BillSummary,
) {
    val tokens = LocalGlassTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassMaterial(GlassMaterial.Panel, 18.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            color = tokens.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BillMetric(
                label = "金额合计",
                value = formatBillAmount(summary.totalMilliYuan),
                modifier = Modifier.weight(1f),
            )
            BillMetric(
                label = "用水时长",
                value = formatBillDuration(summary.totalDurationMillis),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "${summary.recordCount} 笔用水，已结算 ${summary.settledCount} 笔",
            color = tokens.textSecondary,
            fontSize = 12.sp,
        )
        if (summary.pendingCount > 0) {
            Text(
                text = "${summary.pendingCount} 笔金额待同步，未计入金额合计",
                color = tokens.accentWarning,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun BillMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalGlassTokens.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            color = tokens.textSecondary,
            fontSize = 12.sp,
        )
        Text(
            text = value,
            color = tokens.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BillGroupSection(
    group: BillGroup,
    collapsed: Boolean,
    onClick: () -> Unit,
) {
    val motionEnabled = LocalGlassEnvironment.current.motionEnabled
    val expandDuration = if (motionEnabled) 220 else 0
    val collapseDuration = if (motionEnabled) 180 else 0

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        BillGroupHeader(
            group = group,
            collapsed = collapsed,
            onClick = onClick,
        )
        AnimatedVisibility(
            visible = !collapsed,
            enter = expandVertically(
                animationSpec = tween(durationMillis = expandDuration),
                expandFrom = Alignment.Top,
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = if (motionEnabled) 150 else 0,
                    delayMillis = if (motionEnabled) 40 else 0,
                ),
            ),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = collapseDuration),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(
                animationSpec = tween(durationMillis = if (motionEnabled) 120 else 0),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                group.entries.forEach { entry ->
                    ConsumptionRecordCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun BillGroupHeader(
    group: BillGroup,
    collapsed: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val motionEnabled = LocalGlassEnvironment.current.motionEnabled
    val expanded = !collapsed
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = if (motionEnabled) 200 else 0),
        label = "billGroupChevronRotation",
    )
    GlassPressable(
        onClick = onClick,
        onClickLabel = if (expanded) "收起${group.label}" else "展开${group.label}",
        material = GlassMaterial.Control,
        cornerRadius = 14.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                stateDescription = if (expanded) "已展开" else "已收起"
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.label,
                    color = tokens.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${group.summary.recordCount} 笔 · ${formatBillAmount(group.summary.totalMilliYuan)}",
                    color = tokens.textSecondary,
                    fontSize = 12.sp,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = if (expanded) "收起${group.label}" else "展开${group.label}",
                tint = tokens.textSecondary,
                modifier = Modifier.rotate(chevronRotation),
            )
        }
    }
}

@Composable
private fun EmptyBillState() {
    val tokens = LocalGlassTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassMaterial(GlassMaterial.Panel, 18.dp)
            .padding(20.dp),
    ) {
        Text(
            text = "暂无账单",
            color = tokens.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "关闭一次热水后，消费金额和用水时长会显示在这里。",
            color = tokens.textSecondary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun EmptyFilteredBillState() {
    val tokens = LocalGlassTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassMaterial(GlassMaterial.Panel, 18.dp)
            .padding(20.dp),
    ) {
        Text(
            text = "该账期暂无用水记录",
            color = tokens.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "可以切换年份或月份查看其他账期。",
            color = tokens.textSecondary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ConsumptionRecordCard(entry: BillEntry) {
    val tokens = LocalGlassTokens.current
    val record = entry.history
    val amountLabel = record.getConsumptionAmountLabel() ?: "待同步"
    val timeLabel = record.getUsageStartedAtLabel()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassMaterial(GlassMaterial.Panel, 18.dp)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "热水消费",
                    color = tokens.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (timeLabel == null) {
                        "结算于 ${record.getFormattedDate()}"
                    } else {
                        "开始于 $timeLabel"
                    },
                    color = tokens.textSecondary,
                    fontSize = 12.sp,
                )
            }
            Text(
                text = amountLabel,
                color = if (record.consumptionYuan == null) tokens.accentWarning else tokens.accentError,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "用水时长",
                color = tokens.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = record.getUsageDurationLabel() ?: "待同步",
                color = tokens.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
