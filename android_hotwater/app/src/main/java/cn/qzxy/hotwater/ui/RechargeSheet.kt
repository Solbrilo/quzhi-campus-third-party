package cn.qzxy.hotwater.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import cn.qzxy.hotwater.RechargeState
import cn.qzxy.hotwater.designsystem.component.GlassButton
import cn.qzxy.hotwater.designsystem.component.GlassPressable
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.LocalThemeGeometry
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RechargeSheet(
    state: RechargeState,
    balance: Double?,
    refreshingBalance: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSelect: (Long) -> Unit,
    onSubmit: () -> Unit,
    onRefreshBalance: () -> Unit,
    onLogin: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val currentBusy by rememberUpdatedState(state.busy)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !currentBusy })
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maximumHeight = screenHeight * 0.85f
    val height = (screenHeight * 0.56f).coerceIn(320.dp.coerceAtMost(maximumHeight), maximumHeight)
    val corner = LocalThemeGeometry.current.corner(28.dp)
    val scope = rememberCoroutineScope()
    var dismissing by remember { mutableStateOf(false) }
    val dismiss: ((() -> Unit)) -> Unit = { after ->
        if (!currentBusy && !dismissing) {
            dismissing = true
            scope.launch {
                try { sheetState.hide(); if (!sheetState.isVisible) after() }
                finally { dismissing = false }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        shape = RoundedCornerShape(topStart = corner, topEnd = corner),
        containerColor = tokens.meshBase, contentColor = tokens.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = tokens.textTertiary.copy(alpha = 0.4f)) },
    ) {
        Column(Modifier.fillMaxWidth().sheetContentGestures().height(height).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("账户充值", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("余额  ¥${balance?.let { String.format(Locale.CHINA, "%.2f", it) } ?: "—"}",
                        fontSize = 13.sp, color = tokens.textSecondary)
                }
                GlassPressable(onClick = { dismiss(onDismiss) }, enabled = !state.busy && !dismissing, cornerRadius = 24.dp,
                    modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭充值", tint = tokens.textSecondary)
                }
            }

            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    state.loginRequired -> Text("登录已失效或尚未登录，请登录后充值。", color = tokens.textSecondary)
                    state.loading -> Row(Modifier.padding(vertical = 24.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = tokens.accentPrimary, strokeWidth = 2.dp)
                        Text("正在读取充值金额…", color = tokens.textSecondary)
                    }
                    state.error != null -> Text(state.error, color = tokens.accentWarning)
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("选择金额", fontSize = 14.sp, modifier = Modifier.weight(1f), color = tokens.textSecondary)
                            Icon(Icons.Outlined.AccountBalanceWallet, null, Modifier.size(16.dp), tint = tokens.accentPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text("支付宝", fontSize = 13.sp, color = tokens.textSecondary)
                        }
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.packages.chunked(3).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    row.forEach { option ->
                                        GlassPressable(
                                            onClick = { onSelect(option.id) }, enabled = !state.busy,
                                            selected = state.selectedId == option.id, role = Role.RadioButton,
                                            material = GlassMaterial.EmbeddedControl, cornerRadius = 18.dp,
                                            modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("¥${option.amount}", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                                    color = if (state.selectedId == option.id) tokens.accentPrimary else tokens.textPrimary)
                                                option.bonus?.let { Text("赠 ¥$it", fontSize = 11.sp, color = tokens.textSecondary) }
                                            }
                                        }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
                state.schoolNotice?.let { Text(it, color = tokens.textSecondary, fontSize = 12.sp) }
                state.notice?.let { Text(it, color = tokens.accentPrimary, fontSize = 13.sp, lineHeight = 20.sp) }
                state.balanceError?.let { Text(it, color = tokens.accentWarning, fontSize = 12.sp) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassButton(
                    text = when {
                        state.loginRequired -> "去登录"
                        state.error != null -> "重新加载"
                        state.placing -> "正在创建订单…"
                        state.paying -> "等待支付宝支付结果…"
                        state.selectedPackage != null -> "支付宝充值 ¥${state.selectedPackage!!.amount}"
                        else -> "请选择充值金额"
                    },
                    onClick = when {
                        state.loginRequired -> ({ dismiss(onLogin) })
                        state.error != null -> onRetry
                        else -> onSubmit
                    },
                    enabled = sheetState.currentValue == SheetValue.Expanded && !state.loading && !state.busy &&
                        (state.loginRequired || state.error != null || state.canSubmit),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("付款后自动更新余额", fontSize = 11.sp, color = tokens.textTertiary, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRefreshBalance, enabled = !refreshingBalance && !state.loginRequired && !state.busy) {
                        Text(if (refreshingBalance) "刷新中…" else "刷新余额", fontSize = 12.sp, color = tokens.accentPrimary)
                    }
                }
            }
        }
    }
}
