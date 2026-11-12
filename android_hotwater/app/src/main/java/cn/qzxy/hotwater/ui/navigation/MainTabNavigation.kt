package cn.qzxy.hotwater.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import cn.qzxy.hotwater.designsystem.motion.performGlassHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Commands only move the pager. Settling never writes back a new navigation command. */
@Stable
internal class MainTabNavigation(private val pager: PagerState, private val scope: CoroutineScope) {
    private var scrollJob: Job? = null
    var swipeFeedbackPage: Int? by mutableStateOf(null)
        private set
    var dragging by mutableStateOf(false)
        private set

    fun dragInteraction(interaction: DragInteraction) {
        when (interaction) {
            is DragInteraction.Start -> {
                swipeFeedbackPage = pager.currentPage
                dragging = true
            }
            is DragInteraction.Stop -> dragging = false
            is DragInteraction.Cancel -> { dragging = false; consumeSwipe() }
        }
    }

    fun consumeSwipe() { swipeFeedbackPage = null }
    fun acknowledgePage(page: Int) { swipeFeedbackPage = page }

    fun select(page: Int, animated: Boolean) {
        // Dock taps/drags already provide feedback; only a pager gesture gets another tick.
        consumeSwipe()
        scrollJob?.cancel()
        scrollJob = scope.launch {
            if (animated) pager.animateScrollToPage(page, animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow,
            )) else pager.scrollToPage(page)
        }
    }
}

@Composable
internal fun rememberMainTabNavigation(pager: PagerState): MainTabNavigation {
    val scope = rememberCoroutineScope()
    val navigation = remember(pager, scope) { MainTabNavigation(pager, scope) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(pager, navigation) {
        pager.interactionSource.interactions.collect { if (it is DragInteraction) navigation.dragInteraction(it) }
    }
    LaunchedEffect(pager, navigation, haptic) {
        snapshotFlow {
            navigation.swipeFeedbackPage?.let {
                Triple(it, pager.currentPage, !navigation.dragging && !pager.isScrollInProgress)
            }
        }.collect { transition ->
            if (transition != null) {
                // Follow the same currentPage used by the dock's selected tab, while
                // the finger/fling is still moving. Never wait for the settle animation.
                if (transition.first != transition.second) {
                    navigation.acknowledgePage(transition.second)
                    haptic.performGlassHapticFeedback(HapticFeedbackType.SegmentTick)
                }
                if (transition.third) navigation.consumeSwipe()
            }
        }
    }
    return navigation
}
