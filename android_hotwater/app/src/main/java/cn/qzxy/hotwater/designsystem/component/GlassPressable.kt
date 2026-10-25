package cn.qzxy.hotwater.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.qzxy.hotwater.designsystem.theme.GlassTokens
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.LocalGlassEnvironment
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassMaterial
import cn.qzxy.hotwater.designsystem.motion.performGlassHapticFeedback

private const val PressedScale = 0.97f

@Composable
fun GlassPressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    role: Role = if (selected != null) Role.Tab else Role.Button,
    onClickLabel: String? = null,
    material: GlassMaterial? = GlassMaterial.Control,
    cornerRadius: Dp = 22.dp,
    tokens: GlassTokens = LocalGlassTokens.current,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentAlignment: Alignment = Alignment.Center,
    disabledAlpha: Float = 0.52f,
    pressedScale: Float = PressedScale,
    hapticFeedback: (() -> HapticFeedbackType?)? = {
        HapticFeedbackType.SegmentTick
    },
    content: @Composable BoxScope.() -> Unit,
) {
    val environment = LocalGlassEnvironment.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }

    LaunchedEffect(isPressed, enabled, environment.motionEnabled) {
        when {
            !enabled || !environment.motionEnabled -> scale.snapTo(1f)
            isPressed && pressedScale <= 1f -> scale.snapTo(pressedScale)
            isPressed -> scale.animateTo(
                targetValue = pressedScale,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = 520f,
                ),
            )
            else -> scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = if (pressedScale > 1f) 0.62f else Spring.DampingRatioNoBouncy,
                    stiffness = if (pressedScale > 1f) 460f else 400f,
                ),
            )
        }
    }

    val presentationScale = when {
        !enabled || !environment.motionEnabled -> 1f
        isPressed && pressedScale <= 1f -> pressedScale
        else -> scale.value
    }
    val commitClick = {
        if (selected != true) {
            hapticFeedback?.invoke()?.let(haptic::performGlassHapticFeedback)
        }
        onClick()
    }
    val inputModifier = if (selected != null) {
        Modifier.selectable(
            selected = selected,
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            role = role,
            onClick = commitClick,
        )
    } else {
        Modifier.clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            role = role,
            onClickLabel = onClickLabel,
            onClick = commitClick,
        )
    }

    val selectionColor by animateColorAsState(
        if (selected == true) tokens.accentPrimary.copy(alpha = 0.20f) else Color.Transparent,
        tween(if (environment.motionEnabled) 180 else 0), label = "selectionTint",
    )
    val selectionBorder by animateColorAsState(
        if (selected == true) tokens.accentPrimary.copy(alpha = 0.8f) else Color.Transparent,
        tween(if (environment.motionEnabled) 180 else 0), label = "selectionBorder",
    )
    val surfaceModifier = if (material != null) {
        Modifier.glassMaterial(
            material = if (selected != null) GlassMaterial.Control else material,
            cornerRadius = cornerRadius,
            pressed = isPressed && enabled,
            enabled = enabled,
            tokens = tokens,
        ).then(if (selected != null) Modifier
            .background(selectionColor, RoundedCornerShape(cornerRadius))
            .border(1.5.dp, selectionBorder, RoundedCornerShape(cornerRadius)) else Modifier)
    } else {
        Modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                color = if (isPressed && enabled) {
                    tokens.pressedSurface.copy(alpha = 0.18f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
            )
    }

    Box(
        contentAlignment = contentAlignment,
        modifier = modifier
            .scale(presentationScale)
            .alpha(if (enabled) 1f else disabledAlpha)
            .then(surfaceModifier)
            .then(inputModifier)
            .padding(contentPadding),
        content = content,
    )
}
