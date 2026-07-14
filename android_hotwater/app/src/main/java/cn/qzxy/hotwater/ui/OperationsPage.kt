package cn.qzxy.hotwater.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.DashboardSnapshot
import cn.qzxy.hotwater.designsystem.motion.rememberMotionEnabled
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassConcave
import cn.qzxy.hotwater.designsystem.theme.glassConvex
import kotlinx.coroutines.delay

@Composable
fun OperationsPage(
    snapshot: DashboardSnapshot?,
    busy: Boolean,
    statusMessage: String,
    onToggle: () -> Unit,
    onRefreshBalance: () -> Unit,
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

        // 当前操作状态
        item {
            OperationStatus(
                statusMessage = statusMessage,
                snapshot = snapshot,
                busy = busy,
            )
        }

        // 底部间距
        item {
            Spacer(modifier = Modifier.height(bottomDockClearance()))
        }
    }
}

@Composable
private fun OperationStatus(
    statusMessage: String,
    snapshot: DashboardSnapshot?,
    busy: Boolean,
) {
    val tokens = LocalGlassTokens.current
    val message = statusMessage.ifBlank {
        when {
            snapshot?.isDeviceBusyByOther == true -> "已有他人正在使用这台设备，请稍后刷新或重试。"
            snapshot?.hasCachedOrder == true -> "点击主按钮可关闭热水。"
            else -> "点击主按钮可开启热水。"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassConcave(cornerRadius = 18.dp, tokens = tokens)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = message
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = tokens.accentPrimary,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.size(8.dp))
        }
        Text(
            text = message,
            color = if (busy) tokens.textPrimary else tokens.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BalanceCard(
    snapshot: DashboardSnapshot?,
    busy: Boolean,
    onRefreshBalance: () -> Unit,
) {
    val tokens = LocalGlassTokens.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassConvex(cornerRadius = 28.dp, tokens = tokens)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "可用余额",
                color = tokens.textSecondary,
                fontSize = 11.sp
            )
            RefreshButton(
                onClick = onRefreshBalance,
                enabled = !busy,
            )
        }

        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (snapshot?.balanceLabel == "--") "¥ --" else "¥ ${snapshot?.balanceLabel ?: "--"}",
            color = tokens.textPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        UsageTimer(startedAtMillis = snapshot?.usageStartedAtMillis)

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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val motionEnabled = rememberMotionEnabled()
    val scale by animateFloatAsState(
        targetValue = if (motionEnabled && isPressed && enabled) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (isPressed) Spring.StiffnessHigh else Spring.StiffnessMedium,
        ),
        label = "refresh_button_scale",
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .semantics {
                contentDescription = "刷新余额"
                stateDescription = if (enabled) "可用" else "正在处理"
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "刷新余额",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .scale(scale)
                .then(
                    if (isPressed && enabled) Modifier.glassConcave(cornerRadius = 14.dp, tokens = tokens)
                    else Modifier.glassConvex(cornerRadius = 14.dp, tokens = tokens)
                )
                .clip(RoundedCornerShape(14.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                tint = tokens.textPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val motionEnabled = rememberMotionEnabled()
    val scale by animateFloatAsState(
        targetValue = if (motionEnabled && isPressed && enabled) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (isPressed) Spring.StiffnessHigh else Spring.StiffnessMedium,
        ),
        label = "toggle_button_scale",
    )
    val actionLabel = if (isCloseMode) "关闭热水" else "开启热水"

    Box(
        modifier = Modifier
            .size(230.dp)
            .scale(scale)
            .alpha(if (enabled) 1f else 0.64f)
            .then(
                if (isPressed && enabled) Modifier.glassConcave(cornerRadius = 115.dp, tokens = tokens)
                else Modifier.glassConvex(cornerRadius = 115.dp, tokens = tokens)
            )
            .clip(CircleShape)
            .semantics {
                contentDescription = actionLabel
                stateDescription = when {
                    busy && isCloseMode -> "正在关闭热水"
                    busy -> "正在开启热水"
                    isCloseMode -> "热水已开启"
                    else -> "热水未开启"
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = actionLabel,
            ) {
                onToggle()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isCloseMode) "关" else "开",
            color = tokens.textPrimary,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
