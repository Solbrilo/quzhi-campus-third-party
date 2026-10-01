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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.BillQuery
import cn.qzxy.hotwater.ServerBillsState
import java.time.YearMonth
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
fun RecordsPage(
    state: ServerBillsState,
    accountKey: String,
    active: Boolean = true,
    onLoad: (BillQuery, Boolean) -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val now = YearMonth.now()
    var scope by rememberSaveable { mutableStateOf(BillScope.MONTH) }
    var selectedYear by rememberSaveable { mutableIntStateOf(now.year) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(now.monthValue) }
    var grouping by rememberSaveable { mutableStateOf(BillGrouping.MONTH) }
    var collapsedGroupKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val query = BillQuery(selectedYear, if (scope == BillScope.MONTH) selectedMonth else null)
    val matchingState = state.query == query
    val entries = remember(state.bills, matchingState, query) {
        filterBillEntries(
            entries = buildBillEntries(if (matchingState) state.bills else emptyList()),
            scope = scope,
            year = selectedYear,
            month = selectedMonth,
        )
    }
    val summary = remember(entries) { summarizeBillEntries(entries) }
    val groups = remember(entries, grouping) { groupBillEntries(entries, grouping) }
    LaunchedEffect(query, accountKey, active) { if (active) onLoad(query, true) }
    LaunchedEffect(query, grouping, accountKey) { collapsedGroupKeys = emptyList() }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("账单", color = tokens.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                GlassPressable(onClick = { onLoad(query, true) }, enabled = !state.loading,
                    material = GlassMaterial.Control, cornerRadius = 16.dp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                    Text("刷新", color = tokens.accentPrimary, fontSize = 14.sp)
                }
            }
        }
        item {
            BillFilters(scope, { scope = it }, (now.year downTo 2010).toList(), selectedYear,
                { selectedYear = it }, selectedMonth, { selectedMonth = it }, grouping, { grouping = it })
        }
        if (state.loading || !matchingState) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = tokens.accentPrimary, strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp))
                    Text("正在同步服务器账单…", color = tokens.textSecondary, fontSize = 13.sp)
                }
            }
        }
        if (matchingState && state.message.isNotBlank()) {
            item {
                Column(Modifier.fillMaxWidth().glassMaterial(GlassMaterial.Panel, 18.dp).padding(16.dp)) {
                    Text(state.message, color = tokens.accentWarning, fontSize = 13.sp)
                    if (state.failedMonths.isNotEmpty()) Text(
                        "未同步月份：" + state.failedMonths.joinToString { it.toString() },
                        color = tokens.textSecondary, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        if (entries.isNotEmpty()) {
            item {
                BillSummaryCard(
                    title = if (scope == BillScope.YEAR) "${selectedYear}年度汇总" else "${selectedYear}年${selectedMonth}月汇总",
                    summary = summary,
                )
            }
            groups.forEach { group ->
                item(key = group.key) {
                    BillGroupSection(group, group.key in collapsedGroupKeys) {
                        collapsedGroupKeys = if (group.key in collapsedGroupKeys) collapsedGroupKeys - group.key else collapsedGroupKeys + group.key
                    }
                }
            }
        } else if (matchingState && state.loaded && !state.loading) {
            item { EmptyFilteredBillState() }
        }
        item { Spacer(Modifier.height(bottomDockClearance())) }
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            containerColor = tokens.fallbackSurface,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderHi.copy(alpha = 0.55f)),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            color = tokens.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = tokens.textPrimary,
                        leadingIconColor = tokens.textSecondary,
                        trailingIconColor = tokens.textSecondary,
                        disabledTextColor = tokens.textTertiary,
                        disabledLeadingIconColor = tokens.textTertiary,
                        disabledTrailingIconColor = tokens.textTertiary,
                    ),
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
                label = "消费笔数",
                value = "${summary.recordCount} 笔",
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "${summary.settledCount} 笔已获取金额 · 以服务器账单为准",
            color = tokens.textSecondary,
            fontSize = 12.sp,
        )
        if (summary.pendingCount > 0) {
            Text(
                text = "${summary.pendingCount} 笔未返回有效金额，未计入合计",
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
        modifier = Modifier
            .fillMaxWidth()
            .glassMaterial(GlassMaterial.Panel, 18.dp),
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
                modifier = Modifier.fillMaxWidth(),
            ) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = LocalGlassTokens.current.borderLo,
                )
                group.entries.forEachIndexed { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = LocalGlassTokens.current.borderLo,
                        )
                    }
                    ConsumptionRecordRow(entry = entry)
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
        material = null,
        cornerRadius = 0.dp,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
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
private fun ConsumptionRecordRow(entry: BillEntry) {
    val tokens = LocalGlassTokens.current
    val bill = entry.bill
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(bill.description, color = tokens.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(bill.consumeDate.ifBlank { "服务器未提供消费时间" }, color = tokens.textSecondary, fontSize = 12.sp)
            }
            Text(bill.amountMilliYuan?.let(::formatBillAmount) ?: "待同步",
                color = if (bill.amountMilliYuan == null) tokens.accentWarning else tokens.accentError,
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (bill.orderNo != null || bill.orderId.isNotBlank()) {
            Text("订单：${bill.orderNo ?: bill.orderId}", color = tokens.textTertiary, fontSize = 11.sp)
        }
    }
}
