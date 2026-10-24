package com.example.boschlaser.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.boschlaser.shared.geometry.*
import kotlin.test.*

class DimensionTextPlacementTest {
    private val size = Vec2(60.0, 16.0)
    @Test fun horizontalTextIsAboveContinuousDimensionRegardlessOfReferenceOrder() {
        val line = Segment2(Vec2(0.0, 100.0), Vec2(200.0, 100.0))
        val text = dimensionTextPlacement(line, size, 2.0, Vec2(0.0, 0.0))
        assertEquals(Vec2(100.0, 90.0), text.center)
        assertEquals(0f, text.rotationDegrees)
        assertTrue(text.bounds.max.y < 100.0)
        assertEquals(text, dimensionTextPlacement(Segment2(line.end, line.start), size, 2.0, Vec2(0.0, 0.0)))
    }
    @Test fun verticalTextIsOnLeftFacingLeftAndRotatedHitBoundsFollowIt() {
        for (reverse in listOf(false, true)) {
            val a = Vec2(100.0, 0.0)
            val b = Vec2(100.0, 200.0)
            val text = dimensionTextPlacement(if (reverse) Segment2(b, a) else Segment2(a, b), size, 2.0, Vec2(0.0, 0.0))
            assertEquals(90.0, text.center.x, 1e-8)
            assertEquals(100.0, text.center.y, 1e-8)
            assertEquals(-90f, text.rotationDegrees)
            assertEquals(16.0, text.bounds.max.x - text.bounds.min.x, 1e-8)
            assertEquals(60.0, text.bounds.max.y - text.bounds.min.y, 1e-8)
            assertTrue(text.bounds.max.x < 100.0)
        }
    }
    @Test fun obliqueTextRemainsUprightAndReversalDoesNotChangePlacement() {
        val line = Segment2(Vec2(0.0, 0.0), Vec2(200.0, 100.0))
        val text = dimensionTextPlacement(line, size, 2.0, Vec2(4.0, 4.0))
        val reversed = dimensionTextPlacement(Segment2(line.end, line.start), size, 2.0, Vec2(4.0, 4.0))
        assertTrue(text.rotationDegrees in -90f..90f)
        assertEquals(text.center.x, reversed.center.x, 1e-8)
        assertEquals(text.center.y, reversed.center.y, 1e-8)
        assertEquals(text.rotationDegrees, reversed.rotationDegrees, .001f)
    }
    @Test fun crampedTextMovesBeyondEndRatherThanOverlappingTicks() {
        val text = dimensionTextPlacement(Segment2(Vec2(0.0, 0.0), Vec2(20.0, 0.0)), size, 2.0, Vec2(0.0, 0.0))
        assertTrue(text.bounds.min.x > 20.0)
        assertTrue(text.bounds.max.y < 0.0)
    }
    @Test fun onlyEditableDimensionsUseBoldTypography() {
        val base = TextStyle(fontWeight = FontWeight.Bold)
        assertEquals(FontWeight.Normal, dimensionLabelStyle(base, false).fontWeight)
        assertEquals(FontWeight.Bold, dimensionLabelStyle(base, true).fontWeight)
    }
}
