package com.example.boschlaser.shared.geometry

import com.example.boschlaser.shared.editor.Viewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GeometryTest {
    @Test
    fun intersectsInfiniteLinesButRejectsIntersectionOutsideSegments() {
        val first = Segment2(Vec2(0.0, 0.0), Vec2(100.0, 0.0))
        val second = Segment2(Vec2(150.0, -100.0), Vec2(150.0, 100.0))
        assertEquals(Vec2(150.0, 0.0), Geometry.lineIntersection(
            Line2(first.start, first.end - first.start), Line2(second.start, second.end - second.start)))
        assertNull(Geometry.segmentIntersection(first, second))
        assertEquals(Vec2(50.0, 0.0), Geometry.segmentIntersection(first,
            Segment2(Vec2(50.0, -100.0), Vec2(50.0, 100.0))))
    }
    @Test
    fun parallelCollinearAndDegenerateLinesHaveNoUniqueIntersection() {
        val first = Line2(Vec2(0.0, 0.0), Vec2(100.0, 0.0))
        assertNull(Geometry.lineIntersection(first, first.copy(origin = Vec2(0.0, 50.0))))
        assertNull(Geometry.lineIntersection(first, first))
        assertNull(Geometry.lineIntersection(first, Line2(Vec2(0.0, 0.0), Vec2(0.0, 0.0))))
    }
    @Test
    fun projectionIsUnboundedButClosestPointIsClamped() {
        val line = Line2(Vec2(10.0, 20.0), Vec2(1.0, 0.0))
        val segment = Segment2(line.origin, Vec2(100.0, 20.0))
        assertEquals(Vec2(150.0, 20.0), Geometry.project(Vec2(150.0, 50.0), line))
        assertEquals(segment.end, Geometry.closestPoint(Vec2(150.0, 50.0), segment))
        assertEquals(30.0, Geometry.distanceToSegment(Vec2(50.0, 50.0), segment), 1e-6)
        assertEquals(segment.start, Geometry.closestPoint(Vec2(50.0, 50.0), Segment2(segment.start, segment.start)))
    }
    @Test
    fun offsetUsesUnitNormalForDiagonalSegments() {
        val segment = Segment2(Vec2(0.0, 0.0), Vec2(300.0, 400.0))
        val shifted = Geometry.offset(segment, 100.0)
        assertEquals(Vec2(-80.0, 60.0), shifted.start)
        assertEquals(500.0, Geometry.length(shifted), 1e-6)
    }
    @Test
    fun convexHullOmitsDuplicatesAndInteriorPoints() {
        val corners = listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(100.0, 100.0), Vec2(0.0, 100.0))
        assertEquals(corners, Geometry.convexHull(corners.reversed() + corners + Vec2(50.0, 50.0)))
    }
    @Test
    fun viewportRoundTripAndInvalidValues() {
        val viewport = Viewport(0.12, Vec2(50.0, 100.0))
        val point = Vec2(-1500.0, 4200.0)
        val restored = viewport.screenToWorld(viewport.worldToScreen(point))
        assertEquals(point.x, restored.x, 1e-6)
        assertEquals(point.y, restored.y, 1e-6)
        assertFailsWith<IllegalArgumentException> { Viewport(0.0, Vec2(0.0, 0.0)) }
        assertFailsWith<IllegalArgumentException> { Vec2(Double.NaN, 0.0) }
        assertFailsWith<IllegalArgumentException> { Geometry.normalize(Vec2(0.0, 0.0)) }
    }
}
