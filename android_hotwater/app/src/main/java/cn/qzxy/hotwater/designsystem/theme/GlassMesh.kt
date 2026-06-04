package cn.qzxy.hotwater.designsystem.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ═══ Mesh 绘制核心 ═══

/** 在 DrawScope 中绘制 mesh（以 origin 为坐标系偏移） */
private fun DrawScope.drawMesh(tokens: GlassTokens, fullSize: Size, origin: Offset) {
    drawRect(tokens.meshBase)
    val minDim = minOf(fullSize.width, fullSize.height)
    for (spot in tokens.meshSpots) {
        val center = Offset(
            x = spot.xFraction * fullSize.width - origin.x,
            y = spot.yFraction * fullSize.height - origin.y,
        )
        val radius = (spot.radiusFraction * minDim).coerceAtLeast(1f)
        // 径向渐变：光斑色 → 透明
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(spot.color, Color.Transparent),
                center = center,
                radius = radius,
            )
        )
    }
}

// ═══ 全屏 Mesh 背景 ═══

@Composable
fun GlassMeshBackground(tokens: GlassTokens, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawMesh(tokens, fullSize = size, origin = Offset.Zero) }
    )
}

// ═══ 玻璃凸起面 ═══

/**
 * 玻璃凸起面：对位 mesh 透出 + 半透浅色叠加 + 渐变描边 + 顶部内高光 + 外阴影
 * 用于：卡片、按钮默认态、对话框
 */
@Composable
fun Modifier.glassConvex(
    cornerRadius: Dp,
    tokens: GlassTokens = LocalGlassTokens.current,
): Modifier {
    var winOffset by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(cornerRadius)
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val fullSize = with(density) {
        Size(configuration.screenWidthDp.dp.toPx(), configuration.screenHeightDp.dp.toPx())
    }
    val radiusPx = with(density) { cornerRadius.toPx() }

    return this
        .onGloballyPositioned { winOffset = it.positionInWindow() }
        // 外阴影（仅在下方露出一线）
        .drawBehind {
            drawRoundRect(
                color = tokens.outerShadow,
                topLeft = Offset(0f, 2.dp.toPx()),
                size = size,
                cornerRadius = CornerRadius(radiusPx, radiusPx),
            )
        }
        // 裁剪 + mesh + tint
        .clip(shape)
        .drawBehind {
            drawMesh(tokens, fullSize, winOffset)
            drawRect(tokens.tintConvex)
        }
        // 顶部内高光
        .drawWithContent {
            drawContent()
            drawRect(
                color = tokens.innerHighlight,
                size = Size(size.width, 1.dp.toPx()),
            )
        }
        // 渐变边框
        .drawBehind {
            drawRoundRect(
                brush = Brush.linearGradient(listOf(tokens.borderHi, tokens.borderLo)),
                cornerRadius = CornerRadius(radiusPx, radiusPx),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
}

// ═══ 玻璃凹陷面 ═══

/**
 * 玻璃凹陷面：对位 mesh + 暗半透叠加 + 渐变描边
 * 用于：输入框、开关轨道、按钮按下态
 */
@Composable
fun Modifier.glassConcave(
    cornerRadius: Dp,
    tokens: GlassTokens = LocalGlassTokens.current,
): Modifier {
    var winOffset by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(cornerRadius)
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val fullSize = with(density) {
        Size(configuration.screenWidthDp.dp.toPx(), configuration.screenHeightDp.dp.toPx())
    }
    val radiusPx = with(density) { cornerRadius.toPx() }

    return this
        .onGloballyPositioned { winOffset = it.positionInWindow() }
        .clip(shape)
        .drawBehind {
            drawMesh(tokens, fullSize, winOffset)
            drawRect(tokens.tintConcave)
        }
        .drawBehind {
            drawRoundRect(
                brush = Brush.linearGradient(listOf(tokens.borderHi, tokens.borderLo)),
                cornerRadius = CornerRadius(radiusPx, radiusPx),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
}

// ═══ Overlay 变体（弹窗/底部面板用，自带完整 mesh） ═══

@Composable
fun Modifier.glassConvexOverlay(cornerRadius: Dp, tokens: GlassTokens = LocalGlassTokens.current): Modifier {
    var winOffset by remember { mutableStateOf(Offset.Zero) }
    val fullSize = with(LocalDensity.current) { Size(LocalConfiguration.current.screenWidthDp.dp.toPx(), LocalConfiguration.current.screenHeightDp.dp.toPx()) }
    val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    return this
        .onGloballyPositioned { winOffset = it.positionInWindow() }
        .drawBehind { drawRoundRect(color = tokens.outerShadow, topLeft = Offset(0f, 2.dp.toPx()), size = size, cornerRadius = CornerRadius(radiusPx, radiusPx)) }
        .clip(RoundedCornerShape(cornerRadius))
        .drawBehind { drawMesh(tokens, fullSize, winOffset); drawRect(tokens.tintConvex) }
        .drawWithContent { drawContent(); drawRect(color = tokens.innerHighlight, size = Size(size.width, 1.dp.toPx())) }
        .drawBehind { drawRoundRect(brush = Brush.linearGradient(listOf(tokens.borderHi, tokens.borderLo)), cornerRadius = CornerRadius(radiusPx, radiusPx), style = Stroke(1.dp.toPx())) }
}

@Composable
fun Modifier.glassConcaveOverlay(cornerRadius: Dp, tokens: GlassTokens = LocalGlassTokens.current): Modifier {
    var winOffset by remember { mutableStateOf(Offset.Zero) }
    val fullSize = with(LocalDensity.current) { Size(LocalConfiguration.current.screenWidthDp.dp.toPx(), LocalConfiguration.current.screenHeightDp.dp.toPx()) }
    val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    return this
        .onGloballyPositioned { winOffset = it.positionInWindow() }
        .clip(RoundedCornerShape(cornerRadius))
        .drawBehind { drawMesh(tokens, fullSize, winOffset); drawRect(tokens.tintConcave) }
        .drawBehind { drawRoundRect(brush = Brush.linearGradient(listOf(tokens.borderHi, tokens.borderLo)), cornerRadius = CornerRadius(radiusPx, radiusPx), style = Stroke(1.dp.toPx())) }
}
