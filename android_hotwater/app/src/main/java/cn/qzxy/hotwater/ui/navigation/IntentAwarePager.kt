package cn.qzxy.hotwater.ui.navigation

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import cn.qzxy.hotwater.designsystem.motion.LocalPageActivity

/** Keep the small, fixed page set alive: nested settings content must not enter slot reuse. */
@Composable
internal fun IntentAwarePager(
    state: PagerState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pageSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable PagerScope.(Int) -> Unit,
) {
    val original = LocalViewConfiguration.current
    val pagerConfiguration = remember(original) {
        object : androidx.compose.ui.platform.ViewConfiguration by original {
            override val touchSlop: Float = original.touchSlop * 1.5f
        }
    }
    val scope = rememberCoroutineScope()
    val parentActivity = LocalPageActivity.current
    var verticalGesture by remember { mutableStateOf(false) }
    fun settleInterruptedPage() {
        if (abs(state.currentPageOffsetFraction) > .001f) scope.launch {
            state.animateScrollToPage(state.currentPage,
                animationSpec = androidx.compose.animation.core.tween(160))
        }
    }
    val directionLock = Modifier.pointerInput(state, original.touchSlop) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            verticalGesture = false
            scope.launch { state.stopScroll(MutatePriority.UserInput) }
            var decided = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pointer = event.changes.firstOrNull { it.id == down.id }
                if (!decided && pointer != null) {
                    val delta = pointer.position - down.position
                    if (delta.getDistance() >= original.touchSlop * .75f) {
                        verticalGesture = abs(delta.x) < abs(delta.y) * 1.5f
                        decided = true
                        if (verticalGesture) settleInterruptedPage()
                    }
                }
            } while (event.changes.any { it.pressed })
            if (!decided) settleInterruptedPage()
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides pagerConfiguration) {
        HorizontalPager(state, modifier.then(directionLock), key = { it },
            userScrollEnabled = enabled && !verticalGesture,
            // There are only three main tabs (four showcase pages). Retain their nested
            // scroll/AnimatedContent composition instead of reusing it across pages;
            // otherwise Compose can throw "Cannot disable reuse from root" on return.
            beyondViewportPageCount = (state.pageCount - 1).coerceAtLeast(0), pageSpacing = pageSpacing,
            flingBehavior = PagerDefaults.flingBehavior(state, pagerSnapDistance = PagerSnapDistance.atMost(2))) { page ->
            // Only the visibility boundary is observed in composition, not every
            // scroll offset. Both pages stay active while they are visible in a drag.
            val visible = remember(state, page, parentActivity) {
                derivedStateOf {
                    parentActivity.value && abs(state.currentPage - page + state.currentPageOffsetFraction) < 1f
                }
            }
            CompositionLocalProvider(LocalViewConfiguration provides original, LocalPageActivity provides visible) {
                // Keep composition/state without recording hidden backdrop and shadow layers.
                Box(Modifier.fillMaxSize().drawWithContent { if (visible.value) drawContent() }) {
                    content(page)
                }
            }
        }
    }
}
