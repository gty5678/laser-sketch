package com.example.boschlaser.ui

import com.example.boschlaser.shared.annotation.AngleAnnotation
import com.example.boschlaser.shared.annotation.PhotoPoint
import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.test.*

class AnnotationAngleDrawingLayoutTest {
    private val viewport = PhotoDrawingViewport(17.5, 29.25, 800.0, 600.0)
    private val vertex = PhotoPoint(.5, .5)
    private fun point(angle: Int, length: Double = 200.0) = PhotoPoint(.5 + cos(angle * PI / 180) * length / 800,
        .5 + sin(angle * PI / 180) * length / 600)

    @Test fun minorArcsMeetBothRaysAcrossAllFifteenDegreeDirections() {
        for (first in 0 until 360 step 15) for (second in 0 until 360 step 15) {
            val layout = angleDrawingLayout(AngleAnnotation("fixture", vertex, point(first), point(second), "", -65536), viewport, 1.0)
            val difference = abs(first - second).toDouble()
            assertEquals(minOf(difference, 360 - difference), layout.sweepDegrees, 1e-8)
            assertEquals(64.0, layout.radiusPx, 1e-8)
            val start = layout.startDegrees * PI / 180
            val end = (layout.startDegrees + layout.sweepDegrees) * PI / 180
            val arcEnds = listOf(Vec2(cos(start), sin(start)), Vec2(cos(end), sin(end)))
            val rays = listOf(Geometry.normalize(layout.first - layout.vertex), Geometry.normalize(layout.second - layout.vertex))
            assertTrue(arcEnds.all { arc -> rays.any { Geometry.distance(arc, it) < 1e-8 } })
            assertEquals(84.0, Geometry.distance(layout.vertex, layout.labelBaselineCenter), 1e-8)
        }
    }

    @Test fun labelsStayOnTheMinorSectorWhenRaysAreSwappedOrCrossZeroDegrees() {
        val mark = AngleAnnotation("fixture", vertex, point(345), point(15), "30", -65536)
        val layout = angleDrawingLayout(mark, viewport, 1.0)
        val reversed = angleDrawingLayout(mark.copy(first = mark.second, second = mark.first), viewport, 1.0)
        assertEquals(30.0, layout.sweepDegrees, 1e-8)
        assertEquals(layout.labelBaselineCenter.x, reversed.labelBaselineCenter.x, 1e-8)
        assertEquals(layout.labelBaselineCenter.y, reversed.labelBaselineCenter.y, 1e-8)
        assertEquals(layout.vertex.y, layout.labelBaselineCenter.y, 1e-8)
        assertTrue(layout.labelBaselineCenter.x > layout.vertex.x)
    }

    @Test fun radiusUsesTheShorterScreenRayAndKeepsMinimumSizeForDegeneratePoints() {
        val mark = AngleAnnotation("fixture", vertex, point(0, 300.0), point(90, 50.0), "", -65536)
        for (scale in listOf(1.0, 2.0, 4.0)) {
            val layout = angleDrawingLayout(mark, viewport, scale)
            assertEquals(max(22.0, 16 * scale), layout.radiusPx, 1e-8)
            assertEquals(max(3.0, 2.4 * scale), layout.lineWidthPx, 1e-8)
        }
        val coincident = angleDrawingLayout(mark.copy(first = vertex, second = vertex), viewport, 1.0)
        assertEquals(22.0, coincident.radiusPx, 0.0)
        assertEquals(0.0, coincident.sweepDegrees, 0.0)
        assertEquals(coincident.vertex.x + 42.0, coincident.labelBaselineCenter.x, 1e-9)
        val stretched = angleDrawingLayout(mark.copy(first = PhotoPoint(.8, .5), second = PhotoPoint(.8, .8)),
            PhotoDrawingViewport(10.0, 20.0, 200.0, 400.0), 1.0)
        assertEquals(atan2(120.0, 60.0) * 180 / PI, stretched.sweepDegrees, 1e-9)
    }

    @Test fun invalidScalesAreRejectedWithoutChangingTheAnnotation() {
        val mark = AngleAnnotation("fixture", vertex, point(0), point(90), "", -65536)
        for (scale in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { angleDrawingLayout(mark, viewport, scale) }
        }
        assertEquals(point(0), mark.first)
        assertEquals(point(90), mark.second)
    }
}
