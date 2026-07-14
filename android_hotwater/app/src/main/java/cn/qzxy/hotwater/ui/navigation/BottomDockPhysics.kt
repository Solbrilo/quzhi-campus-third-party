package cn.qzxy.hotwater.ui.navigation

import kotlin.math.abs
import kotlin.math.roundToInt

internal const val BOTTOM_DOCK_TAB_COUNT = 3
internal const val BOTTOM_DOCK_DRAG_THRESHOLD_DP = 10f
internal const val BOTTOM_DOCK_BOUNDARY_HYSTERESIS_DP = 10f
internal const val BOTTOM_DOCK_RUBBER_BAND_CONSTANT = 0.55f
internal const val BOTTOM_DOCK_DECELERATION_RATE = 0.99f

internal data class BottomDockRegionState(
    val logicalIndex: Int,
    val lastTransitionDirection: Int = 0,
) {
    init {
        require(logicalIndex in 0 until BOTTOM_DOCK_TAB_COUNT)
        require(lastTransitionDirection in -1..1)
    }
}

internal data class BottomDockSnapTarget(
    val logicalIndex: Int,
    val targetPhysicalOffsetPx: Float,
    val projectedPhysicalOffsetPx: Float,
    val initialPhysicalVelocityPxPerSecond: Float,
)

internal fun isBottomDockHorizontalDrag(
    deltaXPx: Float,
    deltaYPx: Float,
    thresholdPx: Float,
): Boolean {
    require(deltaXPx.isFinite())
    require(deltaYPx.isFinite())
    require(thresholdPx.isFinite() && thresholdPx >= 0f)

    return abs(deltaXPx) >= thresholdPx && abs(deltaXPx) > abs(deltaYPx)
}

internal fun bottomDockLogicalPosition(
    physicalPositionPx: Float,
    widthPx: Float,
    isRtl: Boolean,
): Float {
    require(physicalPositionPx.isFinite())
    require(widthPx.isFinite() && widthPx > 0f)

    val clampedPosition = physicalPositionPx.coerceIn(0f, widthPx)
    return if (isRtl) widthPx - clampedPosition else clampedPosition
}

internal fun bottomDockRegionAt(
    physicalPositionPx: Float,
    widthPx: Float,
    isRtl: Boolean,
): Int {
    val logicalPosition = bottomDockLogicalPosition(
        physicalPositionPx = physicalPositionPx,
        widthPx = widthPx,
        isRtl = isRtl,
    )
    val regionWidth = widthPx / BOTTOM_DOCK_TAB_COUNT
    return (logicalPosition / regionWidth)
        .toInt()
        .coerceIn(0, BOTTOM_DOCK_TAB_COUNT - 1)
}

/**
 * Selects a region immediately on the first boundary crossing. A direction reversal must
 * travel [hysteresisPx] beyond that boundary before it can select the previous region.
 * Start each pointer gesture with a fresh state so an existing selected tab is not sticky.
 */
internal fun updateBottomDockRegion(
    state: BottomDockRegionState,
    physicalPositionPx: Float,
    widthPx: Float,
    isRtl: Boolean,
    hysteresisPx: Float,
): BottomDockRegionState {
    require(hysteresisPx.isFinite() && hysteresisPx >= 0f)

    val logicalPosition = bottomDockLogicalPosition(
        physicalPositionPx = physicalPositionPx,
        widthPx = widthPx,
        isRtl = isRtl,
    )
    val rawIndex = bottomDockRegionAt(
        physicalPositionPx = physicalPositionPx,
        widthPx = widthPx,
        isRtl = isRtl,
    )
    if (rawIndex == state.logicalIndex) return state

    val transitionDirection = if (rawIndex > state.logicalIndex) 1 else -1
    val reversesDirection = state.lastTransitionDirection != 0 &&
        transitionDirection != state.lastTransitionDirection
    if (reversesDirection) {
        val regionWidth = widthPx / BOTTOM_DOCK_TAB_COUNT
        val boundary = if (transitionDirection > 0) {
            (state.logicalIndex + 1) * regionWidth
        } else {
            state.logicalIndex * regionWidth
        }
        val crossedHysteresis = if (transitionDirection > 0) {
            logicalPosition >= boundary + hysteresisPx
        } else {
            logicalPosition <= boundary - hysteresisPx
        }
        if (!crossedHysteresis) return state
    }

    return BottomDockRegionState(
        logicalIndex = rawIndex,
        lastTransitionDirection = transitionDirection,
    )
}

internal fun bottomDockRubberBandDistance(
    overshootPx: Float,
    dimensionPx: Float,
    constant: Float = BOTTOM_DOCK_RUBBER_BAND_CONSTANT,
): Float {
    require(overshootPx.isFinite())
    require(dimensionPx.isFinite() && dimensionPx > 0f)
    require(constant.isFinite() && constant > 0f)

    return (overshootPx * dimensionPx * constant) /
        (dimensionPx + constant * abs(overshootPx))
}

internal fun bottomDockRubberBandPosition(
    positionPx: Float,
    minPx: Float,
    maxPx: Float,
    dimensionPx: Float,
    reducedMotion: Boolean,
): Float {
    require(positionPx.isFinite())
    require(minPx.isFinite())
    require(maxPx.isFinite() && maxPx >= minPx)
    require(dimensionPx.isFinite() && dimensionPx > 0f)

    if (reducedMotion) return positionPx.coerceIn(minPx, maxPx)

    return when {
        positionPx < minPx -> minPx + bottomDockRubberBandDistance(
            overshootPx = positionPx - minPx,
            dimensionPx = dimensionPx,
        )
        positionPx > maxPx -> maxPx + bottomDockRubberBandDistance(
            overshootPx = positionPx - maxPx,
            dimensionPx = dimensionPx,
        )
        else -> positionPx
    }
}

internal fun bottomDockProjectedDistance(
    velocityPxPerSecond: Float,
    decelerationRate: Float = BOTTOM_DOCK_DECELERATION_RATE,
): Float {
    require(velocityPxPerSecond.isFinite())
    require(decelerationRate.isFinite() && decelerationRate > 0f && decelerationRate < 1f)

    return (velocityPxPerSecond / 1_000f) *
        decelerationRate / (1f - decelerationRate)
}

internal fun bottomDockProjectedSnapTarget(
    currentPhysicalOffsetPx: Float,
    releasePhysicalVelocityPxPerSecond: Float,
    tabWidthPx: Float,
    isRtl: Boolean,
    reducedMotion: Boolean,
): BottomDockSnapTarget {
    require(currentPhysicalOffsetPx.isFinite())
    require(releasePhysicalVelocityPxPerSecond.isFinite())
    require(tabWidthPx.isFinite() && tabWidthPx > 0f)

    val maxOffsetPx = tabWidthPx * (BOTTOM_DOCK_TAB_COUNT - 1)
    val projectedOffsetPx = if (reducedMotion) {
        currentPhysicalOffsetPx.coerceIn(0f, maxOffsetPx)
    } else {
        currentPhysicalOffsetPx + bottomDockProjectedDistance(
            velocityPxPerSecond = releasePhysicalVelocityPxPerSecond,
        )
    }
    val physicalIndex = (projectedOffsetPx.coerceIn(0f, maxOffsetPx) / tabWidthPx)
        .roundToInt()
        .coerceIn(0, BOTTOM_DOCK_TAB_COUNT - 1)
    val logicalIndex = if (isRtl) {
        BOTTOM_DOCK_TAB_COUNT - 1 - physicalIndex
    } else {
        physicalIndex
    }

    return BottomDockSnapTarget(
        logicalIndex = logicalIndex,
        targetPhysicalOffsetPx = physicalIndex * tabWidthPx,
        projectedPhysicalOffsetPx = projectedOffsetPx,
        initialPhysicalVelocityPxPerSecond = if (reducedMotion) {
            0f
        } else {
            releasePhysicalVelocityPxPerSecond
        },
    )
}
