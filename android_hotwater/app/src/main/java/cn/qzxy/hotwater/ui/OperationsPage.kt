package cn.qzxy.hotwater.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.DashboardSnapshot
import cn.qzxy.hotwater.designsystem.component.GlassPressable
import cn.qzxy.hotwater.designsystem.motion.rememberMotionEnabled
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassMaterial
import kotlinx.coroutines.delay

@Composable
fun OperationsPage(
    snapshot: DashboardSnapshot?,
    busy: Boolean,
    onToggle: () -> Unit,
    onRefreshBalance: () -> Unit,
    onOpenRecharge: () -> Unit,
) {
    val tokens = LocalGlassTokens.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 余额卡片
        item {
            BalanceCard(
                snapshot = snapshot,
                busy = busy,
                onRefreshBalance = onRefreshBalance,
                onOpenRecharge = onOpenRecharge,
            )
        }

        // 模式提示
        item {
            Text(
                text = when {
                    snapshot?.isDeviceBusyByOther == true -> "当前模式 · 等待设备空闲"
                    snapshot?.hasCachedOrder == true -> "当前模式 · 关闭热水"
                    else -> "当前模式 · 开启热水"
                },
                color = tokens.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        // 圆形开/关按钮
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                val buttonColor = if (snapshot?.hasCachedOrder == true) tokens.accentError else tokens.accentPrimary

                // 加载环（仅在 busy 时显示）
                if (busy) {
                    LoadingRing(color = buttonColor)
                }

                // 主按钮
                ToggleButton(
                    isCloseMode = snapshot?.hasCachedOrder == true,
                    busy = busy,
                    enabled = !busy,
                    onToggle = onToggle,
                )
            }
        }

        // 底部间距
        item {
            Spacer(modifier = Modifier.height(bottomDockClearance()))
        }
    }
}

@Composable
private fun BalanceCard(
    snapshot: DashboardSnapshot?,
    busy: Boolean,
    onRefreshBalance: () -> Unit,
    onOpenRecharge: () -> Unit,
) {
    val tokens = LocalGlassTokens.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassMaterial(
                material = GlassMaterial.Panel,
                cornerRadius = 28.dp,
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "可用余额",
                    color = tokens.textSecondary,
                    fontSize = 11.sp,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (snapshot?.balanceLabel == "--") "¥ --" else "¥ ${snapshot?.balanceLabel ?: "--"}",
                    color = tokens.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                UsageTimer(startedAtMillis = snapshot?.usageStartedAtMillis)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RechargeButton(onClick = onOpenRecharge)
                RefreshButton(
                    onClick = onRefreshBalance,
                    enabled = !busy,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .background(
                    tokens.accentPrimary.copy(alpha = 0.15f),
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = snapshot?.statusLabel ?: "当前配置还不完整。",
                color = tokens.textPrimary,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "宿舍位置：${snapshot?.locationLabel ?: "先完成配置再刷新"}",
            color = tokens.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "设备：${snapshot?.deviceName ?: "热水设备待刷新"}",
            color = tokens.textSecondary,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "账号：${snapshot?.userLabel ?: "未填写账号"}",
            color = tokens.textSecondary,
            fontSize = 11.sp
        )
    }
}
@Composable
private fun UsageTimer(startedAtMillis: Long?) {
    if (startedAtMillis == null) {
        return
    }

    val tokens = LocalGlassTokens.current
    val elapsedMillis by produceState(
        initialValue = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L),
        key1 = startedAtMillis,
    ) {
        while (true) {
            value = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L)
            delay(1_000L)
        }
    }

    Text(
        text = "开启时长 ${formatUsageDuration(elapsedMillis)}",
        color = tokens.accentPrimary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
}

private fun formatUsageDuration(durationMillis: Long): String {
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

@Composable
private fun RefreshButton(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val tokens = LocalGlassTokens.current
    GlassPressable(
        onClick = onClick,
        enabled = enabled,
        role = Role.Button,
        onClickLabel = "刷新余额",
        material = GlassMaterial.Control,
        cornerRadius = 14.dp,
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = "刷新余额"
                stateDescription = if (enabled) "可用" else "正在处理"
            },
    ) {
        Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = null,
            tint = tokens.textPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun RechargeButton(onClick: () -> Unit) {
    val tokens = LocalGlassTokens.current
    GlassPressable(
        onClick = onClick,
        role = Role.Button,
        onClickLabel = "前往支付宝充值",
        material = GlassMaterial.Control,
        cornerRadius = 14.dp,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = "充值" },
    ) {
        Icon(
            imageVector = Icons.Outlined.AccountBalanceWallet,
            contentDescription = null,
            tint = tokens.accentPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun LoadingRing(color: androidx.compose.ui.graphics.Color) {
    val motionEnabled = rememberMotionEnabled()

    if (!motionEnabled) {
        LoadingRingCanvas(color = color, rotation = 0f, alpha = 0.24f)
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "loading")

    // 忽快忽慢的旋转动画（更快的加速度）
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1500,
                easing = CubicBezierEasing(0.2f, 0f, 0.0f, 1f) // 更强的加速度变化
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    LoadingRingCanvas(color = color, rotation = rotation, alpha = alpha)
}

@Composable
private fun LoadingRingCanvas(
    color: androidx.compose.ui.graphics.Color,
    rotation: Float,
    alpha: Float,
) {

    // 10 个等长弧线，每个 20°，间隔 16°
    val arcCount = 10
    val arcSweep = 20f
    val arcGap = 16f

    Box(
        modifier = Modifier
            .size(260.dp)
            .rotate(rotation),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 14.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)

            for (i in 0 until arcCount) {
                val startAngle = i * (arcSweep + arcGap)
                drawArc(
                    color = color.copy(alpha = alpha),
                    startAngle = startAngle,
                    sweepAngle = arcSweep,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                )
            }
        }
    }
}

@Composable
private fun ToggleButton(
    isCloseMode: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val actionLabel = if (isCloseMode) "关闭热水" else "开启热水"

    GlassPressable(
        onClick = onToggle,
        enabled = enabled,
        role = Role.Switch,
        onClickLabel = actionLabel,
        material = GlassMaterial.Control,
        cornerRadius = 115.dp,
        hapticFeedback = { null },
        modifier = Modifier
            .size(230.dp)
            .semantics {
                contentDescription = actionLabel
                toggleableState = ToggleableState(isCloseMode)
                stateDescription = when {
                    busy && isCloseMode -> "正在关闭热水"
                    busy -> "正在开启热水"
                    isCloseMode -> "热水已开启"
                    else -> "热水未开启"
                }
            },
    ) {
        Text(
            text = if (isCloseMode) "关" else "开",
            color = tokens.textPrimary,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
