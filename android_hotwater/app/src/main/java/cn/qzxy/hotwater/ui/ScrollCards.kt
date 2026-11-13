package cn.qzxy.hotwater.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import cn.qzxy.hotwater.designsystem.theme.*

internal class ScrollViewportPosition {
    var coordinates: LayoutCoordinates? = null
    var contentCoordinates: LayoutCoordinates? = null
    var scrollState: ScrollState? = null
    var itemOffset: (() -> Int?)? = null

    fun insetFrom(card: LayoutCoordinates?): Float {
        val viewport = coordinates ?: return 0f
        if (!viewport.isAttached || card?.isAttached != true) return 0f
        val content = contentCoordinates
        if (content?.isAttached == true) {
            val scroll = scrollState?.value ?: itemOffset?.invoke()?.let { -it }
            if (scroll != null) return (scroll + card.localPositionOf(content, Offset.Zero).y).coerceAtLeast(0f)
        }
        return card.localPositionOf(viewport, Offset.Zero).y.coerceAtLeast(0f)
    }
}

/** Content coordinates do not move relative to cards; ScrollState supplies this frame's motion. */
@Composable
internal fun Modifier.cardVerticalScroll(state: ScrollState): Modifier {
    val viewport = LocalScrollViewport.current
    viewport?.scrollState = state
    return verticalScroll(state).onPlaced { viewport?.contentCoordinates = it }
}

/** A lazy item gets its offset from the list's current measure result, not a later global callback. */
@Composable
internal fun ScrollCardItem(state: LazyListState, key: Any, content: @Composable () -> Unit) {
    val parent = LocalScrollViewport.current
    val viewport = remember(state, key) { ScrollViewportPosition() }
    viewport.coordinates = parent?.coordinates
    viewport.itemOffset = { state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.offset }
    CompositionLocalProvider(LocalScrollViewport provides viewport) {
        Box(Modifier.onPlaced {
            viewport.contentCoordinates = it
            viewport.coordinates = parent?.coordinates ?: it
        }) { content() }
    }
}
internal val LocalScrollViewport = staticCompositionLocalOf<ScrollViewportPosition?> { null }

@Composable
internal fun ScrollViewport(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val position = remember { ScrollViewportPosition() }
    // Stretch transforms the viewport after layout, separating the pinned title from its clip.
    // Keep normal drag/fling physics, but don't deform the cards at either scroll boundary.
    CompositionLocalProvider(LocalScrollViewport provides position, LocalOverscrollFactory provides null) {
        Box(modifier.onPlaced { position.coordinates = it }, content = content)
    }
}

/** Retain a curved top edge as a card scrolls under the page header. */
@Composable
internal fun Modifier.roundedScrollEdge(radius: androidx.compose.ui.unit.Dp): Modifier {
    val viewport = LocalScrollViewport.current ?: return this
    // Lazy placement may skip unchanged children. For panels outside a bound item,
    // update only drawing from global position, never a cached placement-time clip.
    if (viewport.scrollState == null && viewport.itemOffset == null) {
        var top by remember { mutableFloatStateOf(0f) }
        return onGloballyPositioned { top = it.positionInRoot().y }.drawWithContent {
            val viewportTop = viewport.coordinates?.takeIf { it.isAttached }?.positionInRoot()?.y ?: top
            val inset = (viewportTop - top).coerceIn(0f, size.height)
            val r = minOf(radius.toPx(), (size.height - inset) / 2, size.width / 2)
            clipPath(Path().apply { addRoundRect(RoundRect(0f, inset, size.width, size.height, CornerRadius(r))) }) {
                this@drawWithContent.drawContent()
            }
        }
    }
    val geometry = remember { PinnedCardGeometry() }
    return layout { measurable, constraints ->
        val child = measurable.measure(constraints)
        layout(child.width, child.height) {
            geometry.offset = viewport.insetFrom(coordinates).roundToInt().coerceIn(0, child.height)
            child.placeRelative(0, 0)
        }
    }.drawWithContent {
        val inset = geometry.offset.toFloat()
        val r = minOf(radius.toPx(), (size.height - inset) / 2, size.width / 2)
        clipPath(Path().apply { addRoundRect(RoundRect(0f, inset, size.width, size.height, CornerRadius(r))) }) {
            this@drawWithContent.drawContent()
        }
    }
}

/** The body slides under the title, then the remaining rounded title card scrolls away. */
@Composable
internal fun CollapsingGlassCard(
    modifier: Modifier = Modifier,
    headerPadding: PaddingValues = PaddingValues(18.dp),
    bodyPadding: PaddingValues = PaddingValues(start = 18.dp, end = 18.dp, bottom = 18.dp),
    header: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val viewport = LocalScrollViewport.current
    val tokens = LocalGlassTokens.current
    val radius = LocalThemeGeometry.current.corner(24.dp)
    val customMaterial = LocalThemePalette.current == ThemePalette.CUSTOM
    // Placement computes the header and drawing offset together. Only drawing observes
    // the state; no post-layout callback schedules a second placement on the next frame.
    val geometry = remember { PinnedCardGeometry() }
    Layout(modifier = modifier.fillMaxWidth()
        .drawWithContent {
            val offset = geometry.offset.toFloat()
            val path = Path().apply { addRoundRect(RoundRect(0f, offset, size.width, size.height, CornerRadius(radius.toPx()))) }
            clipPath(path) {
                // Preset cards keep their original flat surface. Only custom cards
                // need background blur, rather than a full-card refraction graph.
                if (!customMaterial) drawRoundRect(tokens.fallbackSurface,
                    topLeft = Offset(0f, offset), size = androidx.compose.ui.geometry.Size(size.width, size.height - offset),
                    cornerRadius = CornerRadius(radius.toPx()))
                this@drawWithContent.drawContent()
                drawRoundRect(tokens.borderHi.copy(alpha = .35f),
                    topLeft = androidx.compose.ui.geometry.Offset(.5.dp.toPx(), offset + .5.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width - 1.dp.toPx(), (size.height - offset - 1.dp.toPx()).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius((radius.toPx() - .5.dp.toPx()).coerceAtLeast(0f)),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }
        }.then(if (customMaterial) Modifier.glassMaterial(GlassMaterial.Panel, 24.dp, clipScrollEdge = false) else Modifier), content = {
        Column(Modifier.padding(bodyPadding).drawWithContent {
            clipRect(top = geometry.offset.toFloat()) { this@drawWithContent.drawContent() }
        }, verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        Box(Modifier.fillMaxWidth()) {
            // Keep clipped body controls from receiving taps through a pinned title.
            // This sibling leaves interactive header actions (for example history) usable.
            Box(Modifier.matchParentSize().clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null, onClick = {},
            ))
            Box(Modifier.padding(headerPadding)) { header() }
        }
    }) { children, constraints ->
        val body = children[0].measure(constraints.copy(minHeight = 0))
        val heading = children[1].measure(constraints.copy(minHeight = 0))
        layout(constraints.maxWidth, body.height + heading.height) {
            val offset = (viewport?.insetFrom(coordinates) ?: 0f).roundToInt().coerceIn(0, body.height)
            geometry.offset = offset
            body.placeRelative(0, heading.height)
            heading.placeRelative(0, offset)
        }
    }
}

private class PinnedCardGeometry { var offset by mutableIntStateOf(0) }
