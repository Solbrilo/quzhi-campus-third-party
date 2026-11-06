package cn.qzxy.hotwater.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

private val PageEasing = CubicBezierEasing(0.22f, 0.72f, 0f, 1f)

internal fun subpageEnter(motionEnabled: Boolean): EnterTransition =
    if (motionEnabled) slideInHorizontally(tween(320, easing = PageEasing)) { it } + fadeIn(tween(160))
    else EnterTransition.None

internal fun subpageExit(motionEnabled: Boolean): ExitTransition =
    if (motionEnabled) slideOutHorizontally(tween(280, easing = PageEasing)) { it } + fadeOut(tween(220))
    else ExitTransition.None

internal fun subpageTransition(forward: Boolean, motionEnabled: Boolean): ContentTransform =
    if (forward) {
        (subpageEnter(motionEnabled) togetherWith
            if (motionEnabled) fadeOut(tween(180)) else ExitTransition.None)
            .apply { targetContentZIndex = 1f }
    } else {
        ((if (motionEnabled) fadeIn(tween(180)) else EnterTransition.None) togetherWith
            subpageExit(motionEnabled)).apply { targetContentZIndex = -1f }
    }
