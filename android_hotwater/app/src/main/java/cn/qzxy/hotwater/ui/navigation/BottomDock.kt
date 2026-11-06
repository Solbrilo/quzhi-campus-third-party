package cn.qzxy.hotwater.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import cn.qzxy.hotwater.designsystem.component.GlassPressable
import cn.qzxy.hotwater.designsystem.motion.performGlassHapticFeedback
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.LocalGlassEnvironment
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassMaterial
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal enum class DockSelectionSource {
    Click,
    Drag,
}

private const val DockSpringStiffness = 400f
private const val MomentumDampingRatio = 0.82f
private const val MaxReleaseVelocityInDockWidths = 6f
private const val BoundaryVelocityRetention = 0.3f
private val DockHeight = 54.dp
private val DockInset = 3.dp
private val DockLensHeight = DockHeight - DockInset * 2

@Composable
internal fun BottomDock(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (index: Int, source: DockSelectionSource) -> Unit,
    onDragStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(labels.size == BOTTOM_DOCK_TAB_COUNT)
    require(selectedIndex in labels.indices)

    val geometry = cn.qzxy.hotwater.designsystem.theme.LocalThemeGeometry.current
    val outerShape = androidx.compose.foundation.shape.RoundedCornerShape(geometry.corner(DockHeight / 2))
    val innerShape = androidx.compose.foundation.shape.RoundedCornerShape(geometry.insetCorner(DockHeight / 2, DockInset))
    val palette = LocalGlassTokens.current
    val environment = LocalGlassEnvironment.current
    // Record the dock surface and labels separately, never the lens that consumes them.
    // Sources and consumers are both detached when liquid glass is unavailable or at zero.
    val dockContent = rememberLayerBackdrop()
    val dockSurface = rememberLayerBackdrop()
    val lensBackdrop = rememberCombinedBackdrop(
        environment.backgroundBackdrop ?: dockContent,
        environment.contentBackdrop ?: dockContent,
        dockSurface,
        dockContent,
    )
    val darkFraction by animateFloatAsState(
        if (environment.darkTheme) 1f else 0f, tween(if (environment.motionEnabled) 360 else 0),
        label = "dockTheme",
    )
    // Retain the translucent finish, while tinting it with the selected palette.
    val tokens = palette.copy(
        floatingSurface = lerp(Color(0x78FFFFFF), palette.accentPrimary.copy(alpha = 0.28f), darkFraction),
        embeddedControlSurface = lerp(Color(0x80FFFFFF), palette.controlSurface.copy(alpha = 0.26f), darkFraction),
        borderHi = lerp(Color(0xE6FFFFFF), Color(0x85FFFFFF), darkFraction),
        borderLo = palette.accentPrimary.copy(alpha = 0.19f),
        innerHighlight = lerp(Color(0xC2FFFFFF), Color(0x75FFFFFF), darkFraction),
        fallbackSurface = palette.fallbackSurface,
    )
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val latestSelectedIndex by rememberUpdatedState(selectedIndex)
    val scope = rememberCoroutineScope()
    var contentWidthPx by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    val lensExpansion by animateFloatAsState(
        targetValue = if ((isPressed || isDragging) && environment.motionEnabled && environment.glassIntensity > 0f) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 460f), label = "dockLensExpansion",
    )
    val dockScale by animateFloatAsState(
        targetValue = if ((isPressed || isDragging) && environment.motionEnabled) 1.018f else 1f,
        animationSpec = spring(dampingRatio = 0.66f, stiffness = 480f), label = "dockPressScale",
    )
    var settlingJob by remember { mutableStateOf<Job?>(null) }
    var settlingLogicalIndex by remember { mutableStateOf<Int?>(null) }
    var settlingRtl by remember { mutableStateOf<Boolean?>(null) }
    val initialPhysicalIndex = logicalToPhysicalIndex(selectedIndex, isRtl)
    val indicatorPosition = remember { Animatable(initialPhysicalIndex.toFloat()) }
    val dropletPulse = remember { Animatable(0f) }
    var previousSelection by remember { mutableIntStateOf(selectedIndex) }
    var dragIndicatorPosition by remember { mutableFloatStateOf(initialPhysicalIndex.toFloat()) }
    val dragThresholdPx = with(density) { BOTTOM_DOCK_DRAG_THRESHOLD_DP.dp.toPx() }
    val hysteresisPx = with(density) { BOTTOM_DOCK_BOUNDARY_HYSTERESIS_DP.dp.toPx() }
    val tabWidthPx = contentWidthPx.takeIf { it > 0 }?.div(BOTTOM_DOCK_TAB_COUNT.toFloat()) ?: 0f

    LaunchedEffect(selectedIndex, environment.motionEnabled, environment.glassIntensity) {
        val changed = previousSelection != selectedIndex
        previousSelection = selectedIndex
        if (!environment.motionEnabled || environment.glassIntensity <= 0f) {
            dropletPulse.snapTo(0f)
        } else if (changed) {
            dropletPulse.animateTo(1f, tween(durationMillis = 100))
            dropletPulse.animateTo(0f, spring(dampingRatio = 0.48f, stiffness = 380f))
        } else {
            dropletPulse.animateTo(0f)
        }
    }

    LaunchedEffect(selectedIndex, isRtl, isDragging) {
        if (isDragging) return@LaunchedEffect
        val activeInternalSettle = settlingJob?.isActive == true &&
            settlingLogicalIndex == selectedIndex &&
            settlingRtl == isRtl
        if (activeInternalSettle) return@LaunchedEffect

        settlingJob?.cancel()
        settlingLogicalIndex = null
        settlingRtl = null
        val target = logicalToPhysicalIndex(selectedIndex, isRtl).toFloat()
        if (environment.motionEnabled) {
            indicatorPosition.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = DockSpringStiffness,
                ),
            )
        } else {
            indicatorPosition.snapTo(target)
        }
    }

    Box(
        modifier = modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth(0.86f)
            .height(DockHeight)
            .graphicsLayer { scaleX = dockScale; scaleY = dockScale }
            .testTag("bottom_dock"),
    ) {
        // The glass background is a sibling, so its clip never cuts off the enlarged lens.
        Box(Modifier.matchParentSize()
            .then(if (environment.liquidAvailable) Modifier.layerBackdrop(dockSurface) else Modifier)
            .glassMaterial(
                material = GlassMaterial.Floating,
                cornerRadius = DockHeight / 2,
                shape = outerShape,
                tokens = tokens, liquidRim = true, liquidTintScale = 0.65f,
                backdropBlurScale = 0.72f,
            ))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(DockInset)
                .onSizeChanged { contentWidthPx = it.width }
                .pointerInput(tabWidthPx, isRtl, environment.motionEnabled) {
                    if (tabWidthPx <= 0f) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        val velocityTracker = VelocityTracker().apply {
                            addPosition(down.uptimeMillis, down.position)
                        }
                        var dragStarted = false
                        var dragFinished = false
                        var dragOriginPosition: Offset = down.position
                        var startIndicatorOffsetPx = 0f
                        var rawDragIndicatorOffsetPx = 0f
                        var regionState = BottomDockRegionState(latestSelectedIndex)
                        var lastEmittedIndex = latestSelectedIndex

                        fun finishDrag(cancelled: Boolean) {
                            if (!dragStarted || dragFinished) return
                            dragFinished = true
                            val velocityX = if (cancelled) {
                                0f
                            } else {
                                val maximumVelocity =
                                    contentWidthPx * MaxReleaseVelocityInDockWidths
                                velocityTracker.calculateVelocity().x.coerceIn(
                                    minimumValue = -maximumVelocity,
                                    maximumValue = maximumVelocity,
                                )
                            }
                            val target = if (cancelled) {
                                val physical = logicalToPhysicalIndex(regionState.logicalIndex, isRtl)
                                BottomDockSnapTarget(
                                    logicalIndex = regionState.logicalIndex,
                                    targetPhysicalOffsetPx = physical * tabWidthPx,
                                    projectedPhysicalOffsetPx = dragIndicatorPosition * tabWidthPx,
                                    initialPhysicalVelocityPxPerSecond = 0f,
                                )
                            } else {
                                bottomDockProjectedSnapTarget(
                                    currentPhysicalOffsetPx = dragIndicatorPosition * tabWidthPx,
                                    releasePhysicalVelocityPxPerSecond = velocityX,
                                    tabWidthPx = tabWidthPx,
                                    isRtl = isRtl,
                                    reducedMotion = !environment.motionEnabled,
                                    currentRawPhysicalOffsetPx = rawDragIndicatorOffsetPx,
                                )
                            }

                            if (target.logicalIndex != lastEmittedIndex) {
                                lastEmittedIndex = target.logicalIndex
                                haptic.performGlassHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelected(target.logicalIndex, DockSelectionSource.Drag)
                            }
                            regionState = BottomDockRegionState(target.logicalIndex)

                            settlingJob?.cancel()
                            settlingLogicalIndex = target.logicalIndex
                            settlingRtl = isRtl
                            val targetNormalized = target.targetPhysicalOffsetPx / tabWidthPx
                            val maxOffsetPx = tabWidthPx * (BOTTOM_DOCK_TAB_COUNT - 1)
                            val handoffVelocity = when {
                                target.targetPhysicalOffsetPx <= 0f &&
                                    target.initialPhysicalVelocityPxPerSecond < 0f ->
                                    target.initialPhysicalVelocityPxPerSecond * BoundaryVelocityRetention

                                target.targetPhysicalOffsetPx >= maxOffsetPx &&
                                    target.initialPhysicalVelocityPxPerSecond > 0f ->
                                    target.initialPhysicalVelocityPxPerSecond * BoundaryVelocityRetention

                                else -> target.initialPhysicalVelocityPxPerSecond
                            }
                            settlingJob = scope.launch {
                                indicatorPosition.snapTo(dragIndicatorPosition)
                                if (environment.motionEnabled && !cancelled) {
                                    indicatorPosition.animateTo(
                                        targetValue = targetNormalized,
                                        animationSpec = spring(
                                            dampingRatio = MomentumDampingRatio,
                                            stiffness = DockSpringStiffness,
                                        ),
                                        initialVelocity = handoffVelocity / tabWidthPx,
                                    )
                                } else {
                                    indicatorPosition.snapTo(targetNormalized)
                                }
                            }
                            isDragging = false
                            onDragStateChanged(false)
                        }

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    finishDrag(cancelled = true)
                                    break
                                }
                                velocityTracker.addPosition(change.uptimeMillis, change.position)

                                if (change.changedToUpIgnoreConsumed()) {
                                    finishDrag(cancelled = false)
                                    break
                                }
                                if (!change.pressed) {
                                    finishDrag(cancelled = true)
                                    break
                                }

                                val intentDelta = change.position - down.position
                                if (!dragStarted && kotlin.math.abs(intentDelta.y) > dragThresholdPx &&
                                    kotlin.math.abs(intentDelta.y) > kotlin.math.abs(intentDelta.x)) {
                                    break
                                }
                                if (!dragStarted && isBottomDockHorizontalDrag(
                                        deltaXPx = intentDelta.x,
                                        deltaYPx = intentDelta.y,
                                        thresholdPx = dragThresholdPx,
                                    )
                                ) {
                                    dragStarted = true
                                    settlingJob?.cancel()
                                    settlingLogicalIndex = null
                                    settlingRtl = null
                                    // The displayed position is magnetic space; invert the current
                                    // frame to keep the raw pointer coordinate continuous when a
                                    // settle is re-grabbed.
                                    val displayedOffsetPx = indicatorPosition.value * tabWidthPx
                                    dragIndicatorPosition = indicatorPosition.value
                                    startIndicatorOffsetPx = if (environment.motionEnabled) {
                                        bottomDockMagneticInverseOffset(
                                            mappedOffsetPx = displayedOffsetPx,
                                            tabWidthPx = tabWidthPx,
                                        )
                                    } else {
                                        displayedOffsetPx
                                    }
                                    rawDragIndicatorOffsetPx = startIndicatorOffsetPx
                                    dragOriginPosition = Offset(
                                        x = down.position.x + if (intentDelta.x >= 0f) {
                                            dragThresholdPx
                                        } else {
                                            -dragThresholdPx
                                        },
                                        y = down.position.y,
                                    )
                                    regionState = BottomDockRegionState(
                                        logicalIndex = bottomDockRegionAt(
                                            physicalPositionPx = startIndicatorOffsetPx + tabWidthPx / 2f,
                                            widthPx = contentWidthPx.toFloat(),
                                            isRtl = isRtl,
                                        ),
                                    )
                                    settlingJob = scope.launch { indicatorPosition.stop() }
                                    isDragging = true
                                    onDragStateChanged(true)
                                    if (regionState.logicalIndex != lastEmittedIndex) {
                                        lastEmittedIndex = regionState.logicalIndex
                                        haptic.performGlassHapticFeedback(HapticFeedbackType.SegmentTick)
                                        onSelected(regionState.logicalIndex, DockSelectionSource.Drag)
                                    }
                                }

                                if (dragStarted) {
                                    change.consume()
                                    val dragDelta = change.position - dragOriginPosition
                                    val rawOffsetPx = startIndicatorOffsetPx + dragDelta.x
                                    rawDragIndicatorOffsetPx = rawOffsetPx
                                    val presentedOffsetPx = bottomDockPresentedOffset(
                                        rawOffsetPx = rawOffsetPx,
                                        tabWidthPx = tabWidthPx,
                                        contentWidthPx = contentWidthPx.toFloat(),
                                        reducedMotion = !environment.motionEnabled,
                                    )
                                    dragIndicatorPosition = presentedOffsetPx / tabWidthPx
                                    // Region transitions are based on the raw position, before
                                    // magnetic presentation (and before rubber-band distortion).
                                    val rawIndicatorCenterPx = rawOffsetPx + tabWidthPx / 2f
                                    val updatedRegion = updateBottomDockRegion(
                                        state = regionState,
                                        physicalPositionPx = rawIndicatorCenterPx,
                                        widthPx = contentWidthPx.toFloat(),
                                        isRtl = isRtl,
                                        hysteresisPx = hysteresisPx,
                                    )
                                    regionState = updatedRegion
                                    if (updatedRegion.logicalIndex != lastEmittedIndex) {
                                        lastEmittedIndex = updatedRegion.logicalIndex
                                        haptic.performGlassHapticFeedback(HapticFeedbackType.SegmentTick)
                                        onSelected(updatedRegion.logicalIndex, DockSelectionSource.Drag)
                                    }
                                }
                            }
                        } finally {
                            isPressed = false
                            finishDrag(cancelled = true)
                        }
                    }
                },
            contentAlignment = AbsoluteAlignment.TopLeft,
        ) {
            Box(
                modifier = Modifier
                    // On capable devices the lens draws above the captured labels.
                    // The fallback keeps the labels in front for maximum readability.
                    .zIndex(if (environment.liquidAvailable) 1f else -1f)
                    .absoluteOffset {
                        IntOffset(
                            x = (
                                (if (isDragging) dragIndicatorPosition else indicatorPosition.value) *
                                    tabWidthPx
                                ).roundToInt(),
                            y = 0,
                        )
                    }
                    .fillMaxWidth(1f / BOTTOM_DOCK_TAB_COUNT)
                    .height(DockLensHeight)
                    .graphicsLayer {
                        val deformation = dropletPulse.value * environment.glassIntensity
                        // Uniform scaling retains the capsule's curvature while it lifts.
                        val scale = 1f + 0.31f * lensExpansion + 0.04f * deformation
                        scaleX = scale
                        scaleY = scale
                    }
                    .glassMaterial(
                        material = GlassMaterial.EmbeddedControl,
                        cornerRadius = DockLensHeight / 2,
                        shape = innerShape,
                        tokens = tokens, liquidRim = true, liquidTintScale = 0.30f - 0.18f * lensExpansion,
                        sampleContent = true, lensExpansion = lensExpansion,
                        backdrop = lensBackdrop, backdropBlurScale = 0f,
                        refractionScale = lensExpansion,
                    )
                    .drawWithCache {
                        val sheen = Brush.radialGradient(
                            colors = listOf(tokens.innerHighlight, Color.Transparent),
                            center = Offset(size.width * 0.3f, 0f),
                            radius = size.width.coerceAtLeast(1f) * 0.75f,
                        )
                        onDrawWithContent {
                            drawContent()
                            if (!environment.preferOpaqueMaterials && environment.glassIntensity > 0f) {
                                drawRect(sheen, alpha = environment.glassIntensity *
                                    (0.24f + 0.2f * dropletPulse.value.coerceIn(0f, 1f)))
                            }
                        }
                    }
                    .testTag("bottom_dock_indicator"),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (environment.liquidAvailable) Modifier.layerBackdrop(dockContent) else Modifier)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                labels.forEachIndexed { index, label ->
                    val selected = index == selectedIndex
                    GlassPressable(
                        onClick = {
                            if (!selected) {
                                onSelected(index, DockSelectionSource.Click)
                            }
                        },
                        selected = selected,
                        role = Role.Tab,
                        material = null,
                        cornerRadius = DockLensHeight / 2,
                        shape = innerShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(DockLensHeight)
                            .testTag("bottom_dock_tab_$index"),
                        pressedScale = 1.10f,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            val tint = if (selected) tokens.accentPrimary else tokens.textSecondary
                            cn.qzxy.hotwater.ui.ThemedIcon(
                                imageVector = cn.qzxy.hotwater.ui.themeNavigationIcon(
                                    cn.qzxy.hotwater.designsystem.theme.LocalThemePalette.current, index),
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(21.dp).testTag("bottom_dock_icon_$index"),
                            )
                            Text(
                                text = label,
                                color = tint,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                maxLines = 1,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun logicalToPhysicalIndex(logicalIndex: Int, isRtl: Boolean): Int {
    return if (isRtl) BOTTOM_DOCK_TAB_COUNT - 1 - logicalIndex else logicalIndex
}
