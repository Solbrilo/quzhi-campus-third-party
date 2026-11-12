package cn.qzxy.hotwater.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

internal const val WelcomeChinese = "至繁归于至简"
internal const val WelcomeEnglish = "Simplicity is the ultimate sophistication."
private const val WelcomeDurationMillis = 1_900
private const val WelcomeSecondPhraseMillis = 900
internal fun welcomeCharacterProgress(index: Int, elapsed: Float): Float =
    ((elapsed - if (index < 2) 180f else WelcomeSecondPhraseMillis.toFloat()) / 650f).coerceIn(0f, 1f)

internal fun welcomeHapticCueCount(elapsed: Float): Int = when {
    elapsed < 180f -> 0
    elapsed < WelcomeSecondPhraseMillis -> 1
    else -> 2
}

@Composable
fun OnboardingQuotePage(onNext: () -> Unit, isUpgrade: Boolean = false) {
    val tokens = LocalGlassTokens.current
    val motionEnabled = LocalGlassEnvironment.current.motionEnabled
    val haptic by rememberUpdatedState(LocalHapticFeedback.current)
    val lifecycleOwner = LocalLifecycleOwner.current
    var pageActive by remember { mutableStateOf(true) }
    var lifecycleActive by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    val timeline = remember { Animatable(0f) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycleActive = quoteLifecycleActiveAfterEvent(lifecycleActive, event)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(motionEnabled, pageActive, lifecycleActive) {
        if (!pageActive) return@LaunchedEffect
        timeline.snapTo(0f)
        if (motionEnabled && lifecycleActive) {
            var deliveredCueCount = 0
            timeline.animateTo(WelcomeDurationMillis.toFloat(),
                tween(WelcomeDurationMillis, easing = LinearEasing)) {
                val dueCount = welcomeHapticCueCount(value)
                if (dueCount > deliveredCueCount) {
                    // Follow rendered frames; a delayed frame must not trigger a burst of vibrations.
                    deliveredCueCount = dueCount
                    if (pageActive && lifecycleActive) haptic.performGlassHapticFeedback(
                        if (dueCount == 1) HapticFeedbackType.Confirm else HapticFeedbackType.SegmentTick,
                    )
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 24.dp, vertical = 18.dp)) {
        Text("趣智校园热水", Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
            color = tokens.textTertiary, fontSize = 12.sp, letterSpacing = 2.sp)
        val availableScale = ((maxHeight - 110.dp) / 440.dp).coerceIn(.25f, 1f)
        Box(Modifier.align(Alignment.Center).fillMaxWidth().graphicsLayer {
            scaleX = availableScale; scaleY = availableScale
        }, contentAlignment = Alignment.Center) {
            WelcomeChineseColumn(timeline.value, static = !motionEnabled)
        }
        GlassButton(text = if (isUpgrade) "看看新变化" else "快速开始",
            onClick = { pageActive = false; onNext() }, singleLineText = true,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            icon = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = tokens.textPrimary) })
    }
}

@Composable
private fun WelcomeChineseColumn(elapsed: Float, static: Boolean = false, compact: Boolean = false) {
    val tokens = LocalGlassTokens.current
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clearAndSetSemantics { contentDescription = WelcomeChinese }) {
        WelcomeChinese.forEachIndexed { index, character ->
            val progress = if (static) 1f else FastOutSlowInEasing.transform(welcomeCharacterProgress(index, elapsed))
            Box(Modifier.size(if (compact) 36.dp else 52.dp).graphicsLayer {
                alpha = progress
                translationY = (-14f * (1f - progress)).dp.toPx()
            }, contentAlignment = Alignment.Center) {
                Text(character.toString(), color = tokens.textPrimary,
                    fontSize = if (compact) 26.sp else 36.sp, fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Light)
            }
        }
    }
}

internal fun quoteLifecycleActiveAfterEvent(wasActive: Boolean, event: Lifecycle.Event): Boolean = when (event) {
    Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> true
    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> false
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
