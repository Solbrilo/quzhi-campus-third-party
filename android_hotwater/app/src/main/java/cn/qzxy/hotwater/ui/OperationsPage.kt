package cn.qzxy.hotwater.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.DashboardSnapshot
import cn.qzxy.hotwater.OperationHistory
import cn.qzxy.hotwater.designsystem.component.GlassButton
import cn.qzxy.hotwater.designsystem.theme.GlassAccent
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassConcave
import cn.qzxy.hotwater.designsystem.theme.glassConvex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OperationsPage(
    snapshot: DashboardSnapshot?,
    busy: Boolean,
    statusMessage: String,
    history: List<OperationHistory>,
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
                val buttonColor = if (snapshot?.hasCachedOrder == true) GlassAccent.error else GlassAccent.primary

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

        // 提示文字
        item {
            Text(
                text = when {
                    snapshot?.isDeviceBusyByOther == true -> "已有他人正在使用这台设备，请稍后刷新或重试。"
                    snapshot?.hasCachedOrder == true -> "检测到缓存订单号，点击大按钮将尝试关热水。"
                    else -> "当前没有缓存订单号，点击大按钮将尝试开热水。"
                },
                color = tokens.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        // 历史记录卡片
        item {
            HistoryCard(history = history)
        }

        // 底部间距
        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
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
                    GlassAccent.primary.copy(alpha = 0.15f),
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
        color = GlassAccent.primary,
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
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    val scaleAnim = remember { Animatable(1f) }

    Box(
        modifier = Modifier
            .height(28.dp)
            .scale(scaleAnim.value)
            .then(
                if (isPressed) Modifier.glassConcave(cornerRadius = 12.dp, tokens = tokens)
                else Modifier.glassConvex(cornerRadius = 12.dp, tokens = tokens)
            )
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            scope.launch { scaleAnim.snapTo(0.90f) }
                            tryAwaitRelease()
                            isPressed = false
                            scope.launch {
                                delay(60)
                                scaleAnim.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
                            }
                        },
                        onTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                    )
                }
            }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "刷新",
            color = tokens.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LoadingRing(color: androidx.compose.ui.graphics.Color) {
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

    // 呼吸效果（透明度变化）
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

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
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    val scaleAnim = remember { Animatable(1f) }

    Box(
        modifier = Modifier
            .size(230.dp)
            .scale(scaleAnim.value)
            .then(
                if (isPressed) Modifier.glassConcave(cornerRadius = 115.dp, tokens = tokens)
                else Modifier.glassConvex(cornerRadius = 115.dp, tokens = tokens)
            )
            .clip(CircleShape)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            scope.launch { scaleAnim.snapTo(0.95f) }
                            tryAwaitRelease()
                            isPressed = false
                            scope.launch {
                                delay(60)
                                scaleAnim.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
                            }
                        },
                        onTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggle()
                        }
                    )
                }
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

@Composable
private fun HistoryCard(history: List<OperationHistory>) {
    val tokens = LocalGlassTokens.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassConvex(cornerRadius = 28.dp, tokens = tokens)
            .padding(14.dp)
    ) {
        Text(
            text = "最近操作",
            color = tokens.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        if (history.isEmpty()) {
            Text(
                text = "暂无操作记录",
                color = tokens.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                textAlign = TextAlign.Center
            )
        } else {
            Spacer(modifier = Modifier.height(10.dp))
            history.forEach { item ->
                HistoryItem(item = item)
                if (item != history.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(item: OperationHistory) {
    val tokens = LocalGlassTokens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.tintConvex, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (item.success) GlassAccent.success.copy(alpha = 0.15f)
                    else GlassAccent.error.copy(alpha = 0.15f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (item.success) "✓" else "✗",
                color = if (item.success) GlassAccent.success else GlassAccent.error,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.type.label,
                color = tokens.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = item.message,
                color = tokens.textSecondary,
                fontSize = 12.sp,
                maxLines = 1
            )
            item.getConsumptionLabel()?.let { consumptionLabel ->
                Text(
                    text = consumptionLabel,
                    color = GlassAccent.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            item.getUsageDurationLabel()?.let { durationLabel ->
                Text(
                    text = "开启时长 $durationLabel",
                    color = tokens.textTertiary,
                    fontSize = 12.sp,
                )
            }
        }

        Text(
            text = item.getFormattedTime(),
            color = tokens.textTertiary,
            fontSize = 12.sp
        )
    }
}
