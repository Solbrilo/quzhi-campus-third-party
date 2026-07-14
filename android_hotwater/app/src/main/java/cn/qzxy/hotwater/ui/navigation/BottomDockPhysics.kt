package cn.qzxy.hotwater.ui.navigation

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

internal const val BOTTOM_DOCK_TAB_COUNT = 3
internal const val BOTTOM_DOCK_DRAG_THRESHOLD_DP = 10f
internal const val BOTTOM_DOCK_BOUNDARY_HYSTERESIS_DP = 10f
internal const val BOTTOM_DOCK_RUBBER_BAND_CONSTANT = 0.55f
internal const val BOTTOM_DOCK_DECELERATION_RATE = 0.99f
internal const val BOTTOM_DOCK_MAGNETIC_STRENGTH = 0.55f
internal const val BOTTOM_DOCK_MAGNETIC_INVERSE_ITERATIONS = 16

private const val BottomDockTwoPi = 6.2831855f
private const val BottomDockMagneticHalfWidth = 0.5f

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

/**
 * Maps a local position in one tab region through the continuous magnetic field.
 *
 * The local coordinate is centered on the tab (`0f`) and spans `[-0.5f, 0.5f]` at
 * the region boundaries. This makes the map odd, keeps every boundary fixed, and
 * gives a velocity scale of `0.45` at the tab center and `1.55` at either boundary.
 */
internal fun bottomDockMagneticMap(
    localPosition: Float,
    widthPx: Float = 1f,
): Float {
    require(localPosition.isFinite())
    require(widthPx.isFinite() && widthPx > 0f)

    return widthPx * (
        localPosition -
            BOTTOM_DOCK_MAGNETIC_STRENGTH / BottomDockTwoPi *
            sin(BottomDockTwoPi * localPosition)
        )
}

internal fun bottomDockMagneticDerivative(localPosition: Float): Float {
    require(localPosition.isFinite())

    return 1f - BOTTOM_DOCK_MAGNETIC_STRENGTH *
        cos(BottomDockTwoPi * localPosition)
}

/** Alias that makes the velocity interpretation explicit at call sites. */
internal fun bottomDockMagneticVelocityScale(localPosition: Float): Float {
    return bottomDockMagneticDerivative(localPosition)
}

/**
 * Inverts [bottomDockMagneticMap] with a fixed number of bisection steps. The map is
 * strictly monotonic because its derivative is bounded below by `1 - 0.55`. The
 * returned value is the local normalized coordinate supplied to the forward map.
 */
internal fun bottomDockMagneticInverse(
    mappedPosition: Float,
    widthPx: Float = 1f,
    iterations: Int = BOTTOM_DOCK_MAGNETIC_INVERSE_ITERATIONS,
): Float {
    require(mappedPosition.isFinite())
    require(widthPx.isFinite() && widthPx > 0f)
    require(iterations > 0)

    val target = (mappedPosition / widthPx).coerceIn(
        -BottomDockMagneticHalfWidth,
        BottomDockMagneticHalfWidth,
    )
    if (target == -BottomDockMagneticHalfWidth || target == BottomDockMagneticHalfWidth) {
        return target
    }

    var low = -BottomDockMagneticHalfWidth
    var high = BottomDockMagneticHalfWidth
    repeat(iterations) {
        val middle = (low + high) / 2f
        if (bottomDockMagneticMap(middle) < target) {
            low = middle
        } else {
            high = middle
        }
    }
    return (low + high) / 2f
}

/**
 * Applies the magnetic map to a tab-center position. [physicalRegionIndex] is selected
 * from the untransformed/raw position and is intentionally supplied by the caller so
 * selection never depends on the presented magnetic position.
 */
internal fun bottomDockMagneticPosition(
    positionPx: Float,
    tabWidthPx: Float,
    physicalRegionIndex: Int,
): Float {
    require(positionPx.isFinite())
    require(tabWidthPx.isFinite() && tabWidthPx > 0f)
    require(physicalRegionIndex in 0 until BOTTOM_DOCK_TAB_COUNT)

    val regionCenterPx = (physicalRegionIndex + 0.5f) * tabWidthPx
    val localPosition = (positionPx - regionCenterPx) / tabWidthPx
    if (localPosition < -BottomDockMagneticHalfWidth ||
        localPosition > BottomDockMagneticHalfWidth
    ) {
        // The rubber-band path owns positions outside the dock bounds.
        return positionPx
    }
    return regionCenterPx + bottomDockMagneticMap(localPosition, tabWidthPx)
}

internal fun bottomDockMagneticInversePosition(
    mappedPositionPx: Float,
    tabWidthPx: Float,
    physicalRegionIndex: Int,
): Float {
    require(mappedPositionPx.isFinite())
    require(tabWidthPx.isFinite() && tabWidthPx > 0f)
    require(physicalRegionIndex in 0 until BOTTOM_DOCK_TAB_COUNT)

    val regionCenterPx = (physicalRegionIndex + 0.5f) * tabWidthPx
    val localPosition = (mappedPositionPx - regionCenterPx) / tabWidthPx
    if (localPosition < -BottomDockMagneticHalfWidth ||
        localPosition > BottomDockMagneticHalfWidth
    ) {
        return mappedPositionPx
    }
    return regionCenterPx + bottomDockMagneticInverse(
        mappedPosition = localPosition,
        widthPx = 1f,
    ) * tabWidthPx
}

internal fun bottomDockPhysicalRegionAt(
    centerPositionPx: Float,
    tabWidthPx: Float,
): Int {
    require(centerPositionPx.isFinite())
    require(tabWidthPx.isFinite() && tabWidthPx > 0f)

    return floor(centerPositionPx / tabWidthPx)
        .toInt()
        .coerceIn(0, BOTTOM_DOCK_TAB_COUNT - 1)
}

internal fun bottomDockMagneticPositionForOffset(
    offsetPx: Float,
    tabWidthPx: Float,
): Float {
    require(offsetPx.isFinite())
    require(tabWidthPx.isFinite() && tabWidthPx > 0f)

    val maxOffsetPx = tabWidthPx * (BOTTOM_DOCK_TAB_COUNT - 1)
    if (offsetPx < 0f || offsetPx > maxOffsetPx) return offsetPx

    val physicalRegion = bottomDockPhysicalRegionAt(
        centerPositionPx = offsetPx + tabWidthPx / 2f,
        tabWidthPx = tabWidthPx,
    )
    val mappedCenter = bottomDockMagneticPosition(
        positionPx = offsetPx + tabWidthPx / 2f,
        tabWidthPx = tabWidthPx,
        physicalRegionIndex = physicalRegion,
    )
    return mappedCenter - tabWidthPx / 2f
}

internal fun bottomDockMagneticInverseOffset(
    mappedOffsetPx: Float,
    tabWidthPx: Float,
): Float {
    require(mappedOffsetPx.isFinite())
    require(tabWidthPx.isFinite() && tabWidthPx > 0f)

    val maxOffsetPx = tabWidthPx * (BOTTOM_DOCK_TAB_COUNT - 1)
    if (mappedOffsetPx < 0f || mappedOffsetPx > maxOffsetPx) return mappedOffsetPx

    val physicalRegion = bottomDockPhysicalRegionAt(
        centerPositionPx = mappedOffsetPx + tabWidthPx / 2f,
        tabWidthPx = tabWidthPx,
    )
    val rawCenter = bottomDockMagneticInversePosition(
        mappedPositionPx = mappedOffsetPx + tabWidthPx / 2f,
        tabWidthPx = tabWidthPx,
        physicalRegionIndex = physicalRegion,
    )
    return rawCenter - tabWidthPx / 2f
}

internal fun bottomDockMagneticVelocityScaleForOffset(
    rawOffsetPx: Float,
    tabWidthPx: Float,
): Float {
    require(rawOffsetPx.isFinite())
    require(tabWidthPx.isFinite() && tabWidthPx > 0f)

    val maxOffsetPx = tabWidthPx * (BOTTOM_DOCK_TAB_COUNT - 1)
    if (rawOffsetPx < 0f || rawOffsetPx > maxOffsetPx) return 1f

    val centerPositionPx = rawOffsetPx + tabWidthPx / 2f
    val physicalRegion = bottomDockPhysicalRegionAt(centerPositionPx, tabWidthPx)
    val regionCenterPx = (physicalRegion + 0.5f) * tabWidthPx
    val localPosition = (centerPositionPx - regionCenterPx) / tabWidthPx
    return if (localPosition < -BottomDockMagneticHalfWidth ||
        localPosition > BottomDockMagneticHalfWidth
    ) {
        1f
    } else {
        bottomDockMagneticDerivative(localPosition)
    }
}

/**
 * Presents a raw indicator offset while preserving the existing rubber-band behavior. The
 * magnetic field is only applied inside the dock bounds; outside them the rubber-band owns
 * the coordinate and, in reduced-motion mode, the value is clamped as before.
 */
internal fun bottomDockPresentedOffset(
    rawOffsetPx: Float,
    tabWidthPx: Float,
    contentWidthPx: Float,
    reducedMotion: Boolean,
): Float {
    require(rawOffsetPx.isFinite())
    require(tabWidthPx.isFinite() && tabWidthPx > 0f)
    require(contentWidthPx.isFinite() && contentWidthPx > 0f)

    val maxOffsetPx = tabWidthPx * (BOTTOM_DOCK_TAB_COUNT - 1)
    if (reducedMotion || rawOffsetPx < 0f || rawOffsetPx > maxOffsetPx) {
        return bottomDockRubberBandPosition(
            positionPx = rawOffsetPx,
            minPx = 0f,
            maxPx = maxOffsetPx,
            dimensionPx = contentWidthPx,
            reducedMotion = reducedMotion,
        )
    }
    return bottomDockMagneticPositionForOffset(
        offsetPx = rawOffsetPx,
        tabWidthPx = tabWidthPx,
    )
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
    currentRawPhysicalOffsetPx: Float = currentPhysicalOffsetPx,
): BottomDockSnapTarget {
    require(currentPhysicalOffsetPx.isFinite())
    require(releasePhysicalVelocityPxPerSecond.isFinite())
    require(tabWidthPx.isFinite() && tabWidthPx > 0f)
    require(currentRawPhysicalOffsetPx.isFinite())

    val maxOffsetPx = tabWidthPx * (BOTTOM_DOCK_TAB_COUNT - 1)
    val magneticVelocityScale = if (reducedMotion) {
        1f
    } else {
        bottomDockMagneticVelocityScaleForOffset(
            rawOffsetPx = currentRawPhysicalOffsetPx,
            tabWidthPx = tabWidthPx,
        )
    }
    val presentedVelocityPxPerSecond =
        releasePhysicalVelocityPxPerSecond * magneticVelocityScale
    val projectedOffsetPx = if (reducedMotion) {
        currentPhysicalOffsetPx.coerceIn(0f, maxOffsetPx)
    } else {
        currentPhysicalOffsetPx + bottomDockProjectedDistance(
            velocityPxPerSecond = presentedVelocityPxPerSecond,
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
            presentedVelocityPxPerSecond
        },
    )
}
