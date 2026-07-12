package cn.qzxy.hotwater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.OperationHistory
import cn.qzxy.hotwater.designsystem.theme.GlassAccent
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassConvex
import java.util.Locale

@Composable
fun RecordsPage(history: List<OperationHistory>) {
    val tokens = LocalGlassTokens.current
    val records = history.filter {
        it.type == OperationHistory.OperationType.CLOSE && it.success
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "消费记录",
                    color = tokens.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "记录已结束的热水使用，金额按余额变化计算。",
                    color = tokens.textSecondary,
                    fontSize = 13.sp,
                )
            }
        }

        if (records.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassConvex(cornerRadius = 24.dp, tokens = tokens)
                        .padding(20.dp),
                ) {
                    Text(
                        text = "暂无消费记录",
                        color = tokens.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "关闭一次热水后，消费金额和开启时长会显示在这里。",
                        color = tokens.textSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
        } else {
            items(records, key = { it.id }) { record ->
                ConsumptionRecordCard(record = record)
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun ConsumptionRecordCard(record: OperationHistory) {
    val tokens = LocalGlassTokens.current
    val amountLabel = record.consumptionYuan?.let { value ->
        String.format(Locale.getDefault(), "¥ %.2f", value)
    } ?: "待同步"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassConvex(cornerRadius = 24.dp, tokens = tokens)
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
                    text = "开始于 ${record.getUsageStartedAtLabel() ?: "待同步"}",
                    color = tokens.textSecondary,
                    fontSize = 12.sp,
                )
            }
            Text(
                text = amountLabel,
                color = GlassAccent.error,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "开启时长",
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
