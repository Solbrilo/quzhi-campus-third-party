package cn.qzxy.hotwater.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.qzxy.hotwater.designsystem.component.GlassButton
import cn.qzxy.hotwater.designsystem.motion.performGlassHapticFeedback
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.LocalGlassEnvironment
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassMaterial

internal const val QuoteLineRevealDurationMillis = 1_300
internal const val QuoteAuthorRevealDurationMillis = 560
internal const val QuoteLineInitialOffsetDp = 20f
internal const val QuoteAuthorInitialOffsetDp = 10f

@Composable
fun OnboardingQuotePage(onNext: () -> Unit) {
    val tokens = LocalGlassTokens.current
    val motionEnabled = LocalGlassEnvironment.current.motionEnabled
    val hapticFeedback = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var pageActive by remember { mutableStateOf(true) }
    var lifecycleActive by remember(lifecycleOwner) {
        mutableStateOf(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
    }
    val quoteOneProgress = remember { Animatable(if (motionEnabled) 0f else 1f) }
    val quoteTwoProgress = remember { Animatable(if (motionEnabled) 0f else 1f) }
    val authorProgress = remember { Animatable(if (motionEnabled) 0f else 1f) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycleActive = quoteLifecycleActiveAfterEvent(
                wasActive = lifecycleActive,
                event = event,
            )
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(motionEnabled, pageActive, lifecycleActive) {
        if (!pageActive) return@LaunchedEffect
        if (!lifecycleActive) {
            val restingProgress = if (motionEnabled) 0f else 1f
            quoteOneProgress.snapTo(restingProgress)
            quoteTwoProgress.snapTo(restingProgress)
            authorProgress.snapTo(restingProgress)
            return@LaunchedEffect
        }
        if (!motionEnabled) {
            quoteOneProgress.snapTo(1f)
            quoteTwoProgress.snapTo(1f)
            authorProgress.snapTo(1f)
            return@LaunchedEffect
        }

        quoteOneProgress.snapTo(0f)
        quoteTwoProgress.snapTo(0f)
        authorProgress.snapTo(0f)
        quoteOneProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = QuoteLineRevealDurationMillis,
                easing = FastOutSlowInEasing,
            ),
        )
        if (!pageActive || !lifecycleActive) return@LaunchedEffect
        hapticFeedback.performGlassHapticFeedback(HapticFeedbackType.SegmentTick)
        if (!pageActive || !lifecycleActive) return@LaunchedEffect
        quoteTwoProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = QuoteLineRevealDurationMillis,
                easing = FastOutSlowInEasing,
            ),
        )
        if (!pageActive || !lifecycleActive) return@LaunchedEffect
        hapticFeedback.performGlassHapticFeedback(HapticFeedbackType.SegmentTick)
        if (!pageActive || !lifecycleActive) return@LaunchedEffect
        authorProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = QuoteAuthorRevealDurationMillis,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Horizontal))
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            QuoteComposition(
                firstLineProgress = quoteOneProgress.value,
                secondLineProgress = quoteTwoProgress.value,
                authorProgress = authorProgress.value,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        GlassButton(
            text = "快速开始",
            onClick = {
                pageActive = false
                onNext()
            },
            modifier = Modifier
                .fillMaxWidth(),
            singleLineText = true,
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = tokens.textPrimary,
                )
            },
        )
    }
}

@Composable
private fun QuoteComposition(
    firstLineProgress: Float,
    secondLineProgress: Float,
    authorProgress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(216.dp)
            .height(350.dp),
    ) {
        AnimatedVerticalTextColumn(
            text = "删繁就简三秋树",
            lineProgress = firstLineProgress,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = 42.dp),
        )
        AnimatedVerticalTextColumn(
            text = "领异标新二月花",
            lineProgress = secondLineProgress,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = -24.dp, y = 42.dp),
        )
        QuoteAuthor(
            progress = authorProgress,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = -78.dp, y = 126.dp),
        )
    }
}

@Composable
private fun AnimatedVerticalTextColumn(
    text: String,
    lineProgress: Float,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalGlassTokens.current
    val revealProgress = lineProgress.coerceIn(0f, 1f)
    Column(
        modifier = modifier
            .graphicsLayer {
                alpha = revealProgress
                translationY = quoteRevealOffsetDp(
                    progress = revealProgress,
                    initialOffsetDp = QuoteLineInitialOffsetDp,
                ).dp.toPx()
            }
            .clearAndSetSemantics {
                contentDescription = text
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        text.forEach { character ->
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = character.toString(),
                    color = tokens.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun QuoteAuthor(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalGlassTokens.current
    Column(
        modifier = modifier
            .width(28.dp)
            .graphicsLayer {
                val revealProgress = progress.coerceIn(0f, 1f)
                alpha = revealProgress
                translationY = quoteRevealOffsetDp(
                    progress = revealProgress,
                    initialOffsetDp = QuoteAuthorInitialOffsetDp,
                ).dp.toPx()
            }
            .clearAndSetSemantics {
                contentDescription = "郑板桥"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(34.dp)
                .background(tokens.textTertiary.copy(alpha = 0.72f)),
        )
        Spacer(modifier = Modifier.height(10.dp))
        "郑板桥".forEach { character ->
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = character.toString(),
                    color = tokens.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}

internal fun quoteRevealOffsetDp(progress: Float, initialOffsetDp: Float): Float =
    (1f - progress.coerceIn(0f, 1f)) * initialOffsetDp

internal fun quoteLifecycleActiveAfterEvent(
    wasActive: Boolean,
    event: Lifecycle.Event,
): Boolean = when (event) {
    Lifecycle.Event.ON_START,
    Lifecycle.Event.ON_RESUME,
    -> true

    Lifecycle.Event.ON_PAUSE,
    Lifecycle.Event.ON_STOP,
    Lifecycle.Event.ON_DESTROY,
    -> false

    else -> wasActive
}

@Composable
fun OnboardingCompletePage(onStart: () -> Unit) {
    val tokens = LocalGlassTokens.current
    val motionEnabled = LocalGlassEnvironment.current.motionEnabled
    val reveal = remember { Animatable(if (motionEnabled) 0f else 1f) }
    LaunchedEffect(motionEnabled) {
        if (motionEnabled) reveal.animateTo(1f, tween(520))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Horizontal))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = reveal.value
                    val scale = 0.88f + reveal.value * 0.12f
                    scaleX = scale
                    scaleY = scale
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .glassMaterial(GlassMaterial.Floating, 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = tokens.accentSuccess,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "恭喜你，完成配置！",
                color = tokens.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(34.dp))
            GlassButton(
                text = "立即开始",
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
