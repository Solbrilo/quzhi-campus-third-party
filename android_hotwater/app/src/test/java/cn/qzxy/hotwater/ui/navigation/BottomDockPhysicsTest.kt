package cn.qzxy.hotwater.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos

class BottomDockPhysicsTest {
    @Test
    fun regionMappingUsesThreeEqualRegionsAndClampsEdges() {
        assertEquals(0, bottomDockRegionAt(-20f, 300f, isRtl = false))
        assertEquals(0, bottomDockRegionAt(99.9f, 300f, isRtl = false))
        assertEquals(1, bottomDockRegionAt(100f, 300f, isRtl = false))
        assertEquals(1, bottomDockRegionAt(199.9f, 300f, isRtl = false))
        assertEquals(2, bottomDockRegionAt(200f, 300f, isRtl = false))
        assertEquals(2, bottomDockRegionAt(320f, 300f, isRtl = false))
    }

    @Test
    fun rtlConvertsPhysicalRegionsToLogicalOrder() {
        assertEquals(0, bottomDockRegionAt(300f, 300f, isRtl = true))
        assertEquals(0, bottomDockRegionAt(200.1f, 300f, isRtl = true))
        assertEquals(1, bottomDockRegionAt(200f, 300f, isRtl = true))
        assertEquals(1, bottomDockRegionAt(100.1f, 300f, isRtl = true))
        assertEquals(2, bottomDockRegionAt(100f, 300f, isRtl = true))
        assertEquals(2, bottomDockRegionAt(0f, 300f, isRtl = true))
    }

    @Test
    fun boundaryHysteresisSwitchesImmediatelyThenResistsReversal() {
        var state = BottomDockRegionState(logicalIndex = 0)

        state = updateBottomDockRegion(state, 100.1f, 300f, false, 10f)
        assertEquals(BottomDockRegionState(1, 1), state)

        state = updateBottomDockRegion(state, 99f, 300f, false, 10f)
        assertEquals(BottomDockRegionState(1, 1), state)

        state = updateBottomDockRegion(state, 90f, 300f, false, 10f)
        assertEquals(BottomDockRegionState(0, -1), state)

        state = updateBottomDockRegion(state, 101f, 300f, false, 10f)
        assertEquals(BottomDockRegionState(0, -1), state)

        state = updateBottomDockRegion(state, 110f, 300f, false, 10f)
        assertEquals(BottomDockRegionState(1, 1), state)
    }

    @Test
    fun horizontalDragThresholdRejectsVerticalIntent() {
        assertFalse(isBottomDockHorizontalDrag(9.9f, 0f, 10f))
        assertFalse(isBottomDockHorizontalDrag(12f, 13f, 10f))
        assertTrue(isBottomDockHorizontalDrag(-12f, 4f, 10f))
    }

    @Test
    fun horizontalPageSwipesMoveOneTabRespectingRtlAndEdges() {
        assertEquals(1, bottomDockSwipeTarget(0, -80f, 64f, isRtl = false))
        assertEquals(0, bottomDockSwipeTarget(1, 80f, 64f, isRtl = false))
        assertEquals(null, bottomDockSwipeTarget(0, -80f, 64f, isRtl = true))
        assertEquals(2, bottomDockSwipeTarget(1, 80f, 64f, isRtl = true))
        assertEquals(null, bottomDockSwipeTarget(1, 40f, 64f, isRtl = false))
        assertEquals(null, bottomDockSwipeTarget(0, 80f, 64f, isRtl = false))
    }

    @Test
    fun rubberBandUsesAppleProgressiveResistanceFormula() {
        val expected = (100f * 300f * 0.55f) / (300f + 0.55f * 100f)

        assertEquals(expected, bottomDockRubberBandDistance(100f, 300f), 0.0001f)
        assertEquals(-expected, bottomDockRubberBandDistance(-100f, 300f), 0.0001f)
        assertEquals(120f, bottomDockRubberBandPosition(120f, 0f, 200f, 300f, false), 0f)
        assertEquals(200f + expected, bottomDockRubberBandPosition(300f, 0f, 200f, 300f, false), 0.0001f)
        assertEquals(200f, bottomDockRubberBandPosition(300f, 0f, 200f, 300f, true), 0f)
    }

    @Test
    fun magneticMapKeepsTabAnchorsAndRegionBoundariesFixed() {
        listOf(0f, 50f, 100f, 150f, 200f).forEach { offsetPx ->
            assertEquals(
                offsetPx,
                bottomDockMagneticPositionForOffset(offsetPx, tabWidthPx = 100f),
                0.0001f,
            )
        }
    }

    @Test
    fun magneticMapIsSymmetricAndAttractsTowardRegionCenter() {
        val left = bottomDockMagneticMap(localPosition = -0.25f, widthPx = 100f)
        val right = bottomDockMagneticMap(localPosition = 0.25f, widthPx = 100f)

        assertEquals(0f, bottomDockMagneticMap(0f, widthPx = 100f), 0f)
        assertEquals(-right, left, 0.0001f)
        assertTrue(right in 0f..25f)
        assertEquals(
            200f,
            bottomDockMagneticPositionForOffset(75f, 100f) +
                bottomDockMagneticPositionForOffset(125f, 100f),
            0.0001f,
        )
    }

    @Test
    fun magneticDerivativeIsPointFourFiveAtCentersAndOnePointFiveFiveAtBoundaries() {
        assertEquals(0.45f, bottomDockMagneticDerivative(0f), 0.0001f)
        assertEquals(1.55f, bottomDockMagneticDerivative(-0.5f), 0.0001f)
        assertEquals(1.55f, bottomDockMagneticDerivative(0.5f), 0.0001f)
        assertEquals(0.45f, bottomDockMagneticVelocityScaleForOffset(100f, 100f), 0.0001f)
        assertEquals(1.55f, bottomDockMagneticVelocityScaleForOffset(50f, 100f), 0.0001f)
    }

    @Test
    fun magneticInverseRecoversRawOffsetsAfterSixteenBisections() {
        listOf(0f, 12.5f, 49.9f, 50f, 50.1f, 75f, 100f, 125f, 150f, 187.5f, 200f)
            .forEach { rawOffsetPx ->
                val mappedOffsetPx = bottomDockMagneticPositionForOffset(rawOffsetPx, 100f)

                assertEquals(
                    rawOffsetPx,
                    bottomDockMagneticInverseOffset(mappedOffsetPx, 100f),
                    0.002f,
                )
            }
    }

    @Test
    fun presentationCombinesMagnetRubberBandAndReducedMotionBypass() {
        val magnetic = bottomDockPresentedOffset(25f, 100f, 300f, reducedMotion = false)
        val expectedOvershoot = bottomDockRubberBandPosition(
            positionPx = 300f,
            minPx = 0f,
            maxPx = 200f,
            dimensionPx = 300f,
            reducedMotion = false,
        )

        assertTrue(magnetic in 0f..25f)
        assertEquals(expectedOvershoot, bottomDockPresentedOffset(300f, 100f, 300f, false), 0f)
        assertEquals(-20f, bottomDockMagneticPositionForOffset(-20f, 100f), 0f)
        assertEquals(220f, bottomDockMagneticInverseOffset(220f, 100f), 0f)
        assertEquals(1f, bottomDockMagneticVelocityScaleForOffset(-20f, 100f), 0f)
        assertEquals(25f, bottomDockPresentedOffset(25f, 100f, 300f, true), 0f)
        assertEquals(200f, bottomDockPresentedOffset(300f, 100f, 300f, true), 0f)
    }

    @Test
    fun velocityProjectionUsesExponentialDecay() {
        assertEquals(99f, bottomDockProjectedDistance(1_000f), 0.001f)
        assertEquals(-99f, bottomDockProjectedDistance(-1_000f), 0.001f)
        assertEquals(0f, bottomDockProjectedDistance(0f), 0f)
    }

    @Test
    fun projectedSnapScalesReleaseVelocityThroughMagneticDerivative() {
        val rawOffsetPx = 120f
        val displayedOffsetPx = bottomDockMagneticPositionForOffset(rawOffsetPx, 100f)
        val forward = bottomDockProjectedSnapTarget(
            currentPhysicalOffsetPx = displayedOffsetPx,
            releasePhysicalVelocityPxPerSecond = 1_000f,
            tabWidthPx = 100f,
            isRtl = false,
            reducedMotion = false,
            currentRawPhysicalOffsetPx = rawOffsetPx,
        )
        val expectedScale = 1f - 0.55f * cos((2.0 * PI * 0.2).toFloat())
        val expectedVelocity = 1_000f * expectedScale

        assertEquals(2, forward.logicalIndex)
        assertEquals(200f, forward.targetPhysicalOffsetPx, 0f)
        assertEquals(
            displayedOffsetPx + bottomDockProjectedDistance(expectedVelocity),
            forward.projectedPhysicalOffsetPx,
            0.001f,
        )
        assertEquals(expectedVelocity, forward.initialPhysicalVelocityPxPerSecond, 0.001f)
    }

    @Test
    fun releaseVelocityUsesCenterAndBoundaryMagneticScales() {
        val atCenter = bottomDockProjectedSnapTarget(100f, 1_000f, 100f, false, false)
        val atBoundary = bottomDockProjectedSnapTarget(50f, 1_000f, 100f, false, false)

        assertEquals(450f, atCenter.initialPhysicalVelocityPxPerSecond, 0.001f)
        assertEquals(1_550f, atBoundary.initialPhysicalVelocityPxPerSecond, 0.001f)
        assertEquals(144.55f, atCenter.projectedPhysicalOffsetPx, 0.001f)
        assertEquals(203.45f, atBoundary.projectedPhysicalOffsetPx, 0.001f)
    }

    @Test
    fun fastReversalUsesVelocitySignInsteadOfReleasePosition() {
        val reversed = bottomDockProjectedSnapTarget(
            currentPhysicalOffsetPx = 160f,
            releasePhysicalVelocityPxPerSecond = -1_500f,
            tabWidthPx = 100f,
            isRtl = false,
            reducedMotion = false,
        )
        val continued = bottomDockProjectedSnapTarget(
            currentPhysicalOffsetPx = 160f,
            releasePhysicalVelocityPxPerSecond = 1_500f,
            tabWidthPx = 100f,
            isRtl = false,
            reducedMotion = false,
        )

        assertEquals(0, reversed.logicalIndex)
        assertEquals(0f, reversed.targetPhysicalOffsetPx, 0f)
        assertEquals(
            -1_500f * bottomDockMagneticVelocityScaleForOffset(160f, 100f),
            reversed.initialPhysicalVelocityPxPerSecond,
            0.001f,
        )
        assertEquals(2, continued.logicalIndex)
        assertEquals(200f, continued.targetPhysicalOffsetPx, 0f)
    }

    @Test
    fun projectedTargetsClampToBoundsAndMapRtlLogicalIndex() {
        val beyondRight = bottomDockProjectedSnapTarget(195f, 10_000f, 100f, true, false)
        val beyondLeft = bottomDockProjectedSnapTarget(5f, -10_000f, 100f, true, false)

        assertTrue(beyondRight.projectedPhysicalOffsetPx > 200f)
        assertEquals(200f, beyondRight.targetPhysicalOffsetPx, 0f)
        assertEquals(0, beyondRight.logicalIndex)
        assertTrue(beyondLeft.projectedPhysicalOffsetPx < 0f)
        assertEquals(0f, beyondLeft.targetPhysicalOffsetPx, 0f)
        assertEquals(2, beyondLeft.logicalIndex)
    }

    @Test
    fun reducedMotionSkipsProjectionAndVelocityHandoff() {
        val target = bottomDockProjectedSnapTarget(
            currentPhysicalOffsetPx = 160f,
            releasePhysicalVelocityPxPerSecond = -10_000f,
            tabWidthPx = 100f,
            isRtl = false,
            reducedMotion = true,
        )

        assertEquals(2, target.logicalIndex)
        assertEquals(200f, target.targetPhysicalOffsetPx, 0f)
        assertEquals(160f, target.projectedPhysicalOffsetPx, 0f)
        assertEquals(0f, target.initialPhysicalVelocityPxPerSecond, 0f)
    }
}
