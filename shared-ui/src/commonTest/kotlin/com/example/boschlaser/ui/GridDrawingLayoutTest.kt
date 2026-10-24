package com.example.boschlaser.ui

import com.example.boschlaser.shared.editor.Viewport
import com.example.boschlaser.shared.geometry.Vec2
import kotlin.test.*

class GridDrawingLayoutTest {
    @Test fun zoomThresholdsRetainTheOriginalScreenScalePolicy() {
        for ((scale, expected) in listOf(.049f to 1000.0, .05f to 500.0, .119f to 500.0, .12f to 100.0, 1f to 100.0)) {
            assertEquals(expected, gridStepForScale(100.0, scale), 0.0)
        }
    }

    @Test fun negativeViewportOriginAndMajorLinesKeepTheirWorldPositions() {
        val viewport = Viewport(1.0, Vec2(23.0, -17.0))
        val lines = gridDrawingLines(viewport, Vec2(250.0, 180.0), 10.0).toList()
        val vertical = lines.filter { it.start.x == it.end.x }
        val horizontal = lines.filter { it.start.y == it.end.y }
        assertEquals(-7.0, vertical.first().start.x, 1e-9)
        assertEquals(243.0, vertical.last().start.x, 1e-9)
        assertEquals(listOf(23.0, 123.0, 223.0), vertical.filter { it.major }.map { it.start.x })
        assertEquals(listOf(83.0), horizontal.filter { it.major }.map { it.start.y })
        assertEquals(10.0, vertical[1].start.x - vertical[0].start.x, 1e-9)
        assertTrue(vertical.all { it.start.y == 0.0 && it.end.y == 180.0 })
        assertTrue(horizontal.all { it.start.x == 0.0 && it.end.x == 250.0 })
    }

    @Test fun fractionalSpacingDoesNotAccumulateDrift() {
        val viewport = Viewport(.5, Vec2(-1.25, 2.5))
        val lines = gridDrawingLines(viewport, Vec2(1000.0, 100.0), 10.25).filter { it.start.x == it.end.x }.toList()
        for ((first, second) in lines.zipWithNext()) assertEquals(5.125, second.start.x - first.start.x, 1e-9)
        assertEquals(10.25 * .5, lines[1].start.x - lines[0].start.x, 1e-9)
    }

    @Test fun invalidSpacingScaleAndBoundsAreRejected() {
        for (value in listOf(0.0, -10.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { gridStepForScale(value, 1f) }
        }
        assertFailsWith<IllegalArgumentException> { gridStepForScale(100.0, 0f) }
        assertFailsWith<IllegalArgumentException> { gridDrawingLines(Viewport(1.0, Vec2(0.0, 0.0)), Vec2(-1.0, 100.0), 100.0) }
    }
}
