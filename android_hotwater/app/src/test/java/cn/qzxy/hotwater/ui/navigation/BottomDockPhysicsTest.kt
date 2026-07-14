package cn.qzxy.hotwater.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun rubberBandUsesAppleProgressiveResistanceFormula() {
        val expected = (100f * 300f * 0.55f) / (300f + 0.55f * 100f)

        assertEquals(expected, bottomDockRubberBandDistance(100f, 300f), 0.0001f)
        assertEquals(-expected, bottomDockRubberBandDistance(-100f, 300f), 0.0001f)
        assertEquals(120f, bottomDockRubberBandPosition(120f, 0f, 200f, 300f, false), 0f)
        assertEquals(200f + expected, bottomDockRubberBandPosition(300f, 0f, 200f, 300f, false), 0.0001f)
        assertEquals(200f, bottomDockRubberBandPosition(300f, 0f, 200f, 300f, true), 0f)
    }

    @Test
    fun velocityProjectionUsesExponentialDecay() {
        assertEquals(99f, bottomDockProjectedDistance(1_000f), 0.001f)
        assertEquals(-99f, bottomDockProjectedDistance(-1_000f), 0.001f)
        assertEquals(0f, bottomDockProjectedDistance(0f), 0f)
    }

    @Test
    fun projectedSnapUsesReleaseVelocityAndPreservesHandoffVelocity() {
        val forward = bottomDockProjectedSnapTarget(
            currentPhysicalOffsetPx = 120f,
            releasePhysicalVelocityPxPerSecond = 1_000f,
            tabWidthPx = 100f,
            isRtl = false,
            reducedMotion = false,
        )

        assertEquals(2, forward.logicalIndex)
        assertEquals(200f, forward.targetPhysicalOffsetPx, 0f)
        assertEquals(219f, forward.projectedPhysicalOffsetPx, 0.001f)
        assertEquals(1_000f, forward.initialPhysicalVelocityPxPerSecond, 0f)
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
        assertEquals(-1_500f, reversed.initialPhysicalVelocityPxPerSecond, 0f)
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
