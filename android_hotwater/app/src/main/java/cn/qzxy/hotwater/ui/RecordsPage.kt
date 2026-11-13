package cn.qzxy.hotwater.ui

import androidx.compose.ui.platform.testTag

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import cn.qzxy.hotwater.designsystem.component.GlassButton
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
    loggedIn: Boolean = true,
    credentialsExpired: Boolean = false,
    onLogin: () -> Unit = {},
    onLoad: (BillQuery, Boolean) -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val now = YearMonth.now()
    var scope by rememberSaveable { mutableStateOf(BillScope.MONTH) }
    var selectedYear by rememberSaveable { mutableIntStateOf(now.year) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(now.monthValue) }
    val grouping = BillGrouping.MONTH
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
    LaunchedEffect(query, accountKey, active, loggedIn, credentialsExpired) { if (active && loggedIn && !credentialsExpired) onLoad(query, false) }
    LaunchedEffect(query, grouping, accountKey) { collapsedGroupKeys = emptyList() }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().testTag("bill_list"), state = listState, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("账单", color = tokens.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                if (loggedIn && !credentialsExpired) GlassPressable(onClick = { onLoad(query, true) }, enabled = !state.loading,
                    material = GlassMaterial.Control, cornerRadius = 16.dp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                    Text("刷新", color = tokens.accentPrimary, fontSize = 14.sp)
                }
            }
        }
        if (credentialsExpired || !loggedIn) {
            item(key = "bill_login") {
                ScrollCardItem(listState, "bill_login") {
                Column(Modifier.fillMaxWidth().glassMaterial(GlassMaterial.Panel, 24.dp).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (credentialsExpired) "登录已失效" else "登录后查看账单",
                        color = tokens.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(if (credentialsExpired) "请重新登录趣智校园，继续查看和同步账单。" else "登录趣智校园后，你的用水账单将在这里显示。",
                        color = tokens.textSecondary, fontSize = 14.sp)
                    GlassButton(text = if (credentialsExpired) "重新登录" else "去登录", onClick = onLogin,
                        modifier = Modifier.fillMaxWidth())
                }
                }
            }
        } else {
            item(key = "bill_filters") {
                ScrollCardItem(listState, "bill_filters") {
                BillFilters(scope, { scope = it }, selectedYear, selectedMonth, now) { period ->
                    selectedYear = period.year
                    selectedMonth = period.monthValue
                }
                }
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
                item(key = "bill_sync_message") {
                    ScrollCardItem(listState, "bill_sync_message") {
                    Column(Modifier.fillMaxWidth().glassMaterial(GlassMaterial.Panel, 18.dp).padding(16.dp)) {
                        Text(state.message, color = tokens.accentWarning, fontSize = 13.sp)
                        if (state.failedMonths.isNotEmpty()) Text(
                            "未同步月份：" + state.failedMonths.joinToString { it.toString() },
                            color = tokens.textSecondary, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                    }
                }
            }
            if (entries.isNotEmpty()) {
                item(key = "bill_summary") {
                    ScrollCardItem(listState, "bill_summary") {
                    BillSummaryCard(
                        title = if (scope == BillScope.YEAR) "${selectedYear}年度汇总" else "${selectedYear}年${selectedMonth}月汇总",
                        summary = summary,
                    )
                    }
                }
                groups.forEach { group ->
                    item(key = group.key) {
                        ScrollCardItem(listState, group.key) {
                        BillGroupSection(group, group.key in collapsedGroupKeys) {
                            collapsedGroupKeys = if (group.key in collapsedGroupKeys) collapsedGroupKeys - group.key else collapsedGroupKeys + group.key
                        }
                        }
                    }
                }
            } else if (matchingState && state.loaded && !state.loading) {
                item(key = "bill_empty") { ScrollCardItem(listState, "bill_empty") { EmptyFilteredBillState() } }
            }
        }
        item { Spacer(Modifier.height(bottomDockClearance())) }
    }
}

@Composable
private fun BillFilters(
    scope: BillScope,
    onScopeChange: (BillScope) -> Unit,
    selectedYear: Int,
    selectedMonth: Int,
    now: YearMonth,
    onPeriodChange: (YearMonth) -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("bill_filters").glassMaterial(GlassMaterial.Panel, 22.dp).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BillSegmentedControl(
            selectedIndex = if (scope == BillScope.MONTH) 0 else 1,
            labels = listOf("月账单", "年汇总"),
            onSelected = { onScopeChange(if (it == 0) BillScope.MONTH else BillScope.YEAR) },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BillPicker("年份", "${selectedYear}年",
                (now.year downTo 2010).map { BillPickerOption("$it", "${it}年") },
                { onPeriodChange(clampBillPeriod(YearMonth.of(it.key.toInt(), selectedMonth), now)) },
                Modifier.weight(1f))
            if (scope == BillScope.MONTH) BillPicker("月份", "${selectedMonth}月",
                (1..if (selectedYear == now.year) now.monthValue else 12).map { BillPickerOption("$it", "${it}月") },
                { onPeriodChange(YearMonth.of(selectedYear, it.key.toInt())) }, Modifier.weight(1f))
        }
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
        FilterMenu(expanded, { expanded = false }, options, { it.label }, onSelected)
    }
}

@Composable
private fun BillSummaryCard(
    title: String,
    summary: BillSummary,
) {
    val tokens = LocalGlassTokens.current
    CollapsingGlassCard(header = {
        Text(title, color = tokens.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }) {
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
            BillMetric(
                label = "用水量合计",
                value = when {
                    summary.waterRecordCount == 0 -> "待同步"
                    summary.waterRecordCount < summary.recordCount -> "≥ ${formatWaterVolume(summary.totalWaterMilliLiters)}"
                    else -> formatWaterVolume(summary.totalWaterMilliLiters)
                },
                modifier = Modifier.weight(1f),
            )
        }
        if (summary.waterRecordCount < summary.recordCount) {
            Text(
                text = "${summary.recordCount - summary.waterRecordCount} 笔暂时没有可用水量",
                color = tokens.accentWarning,
                fontSize = 12.sp,
            )
        }
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
        androidx.compose.foundation.layout.BoxWithConstraints {
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val measured = measurer.measure(value, androidx.compose.material3.LocalTextStyle.current.copy(
                fontSize = 17.sp, fontWeight = FontWeight.Bold), softWrap = false).size.width
            val available = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
            Text(
                text = value,
                color = tokens.textPrimary,
                fontSize = (17f * (available / measured.coerceAtLeast(1)).coerceAtMost(1f)).coerceAtLeast(10f).sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
            )
        }
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

    CollapsingGlassCard(headerPadding = PaddingValues(0.dp),
        bodyPadding = PaddingValues(0.dp), header = {
        BillGroupHeader(group, collapsed, onClick)
    }) {
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
                    text = buildString {
                        append("${group.summary.recordCount} 笔 · ${formatBillAmount(group.summary.totalMilliYuan)}")
                        if (group.summary.waterRecordCount > 0) {
                            append(" · ${if (group.summary.waterRecordCount < group.summary.recordCount) "≥ " else ""}")
                            append(formatWaterVolume(group.summary.totalWaterMilliLiters))
                        }
                    },
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
    val waterVolume = billWaterVolume(bill)
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
        Text(
            text = waterVolume?.let {
                "用水量：${formatWaterVolume(it.milliLiters)}${if (it.estimated) "（估算）" else ""}"
            } ?: "用水量：待同步",
            color = if (waterVolume?.estimated == true) tokens.textSecondary else if (waterVolume == null) tokens.accentWarning else tokens.accentPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        if (bill.orderNo != null || bill.orderId.isNotBlank()) {
            Text("订单：${bill.orderNo ?: bill.orderId}", color = tokens.textTertiary, fontSize = 11.sp)
        }
    }
}
