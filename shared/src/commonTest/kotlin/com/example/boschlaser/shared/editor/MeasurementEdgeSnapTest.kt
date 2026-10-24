package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.tool.PointerContext
import kotlin.math.sqrt
import kotlin.test.*

class MeasurementEdgeSnapTest {
    private fun context(point: Vec2) = PointerContext(point, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0)
    private fun assertSnap(document: SketchDocument, input: Vec2, expected: Vec2, id: String) {
        val editor = SketchEditor(document)
        editor.setTool(ToolType.MEASURE)
        editor.pointerDown(input, context(input))
        val snap = assertNotNull(editor.state.snap)
        assertEquals(expected.x, snap.point.x, .001)
        assertEquals(expected.y, snap.point.y, .001)
        assertEquals(id, snap.targetId)
        assertEquals(snap.point, editor.state.magnifierTarget)
        assertNull(editor.state.preview)
        editor.pointerUp(input, context(input))
        val first = assertIs<SketchPreview.Measurement>(editor.state.preview)
        assertEquals(snap.point, first.line.start)
        assertFalse(first.complete)
        assertNull(editor.state.magnifierTarget)
        assertEquals(document, editor.document)
        assertFalse(editor.canUndo)
    }

    @Test fun wallPhysicalCornersShortEndsAndFacesAreMeasurementTargets() {
        for (alignment in WallAlignment.entries) {
            val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(5000.0, 0.0), 200.0, alignment)
            val doc = SketchDocument(walls = mapOf(wall.id to wall))
            val shape = WallGeometry.build(wall)
            for (point in listOf(shape.leftEdge.start, (shape.leftEdge.start + shape.leftEdge.end) * .5,
                (shape.leftEdge.end + shape.rightEdge.end) * .5)) {
                assertSnap(doc, point + Vec2(3.0, 4.0), point, wall.id)
            }
            val edgePoint = shape.leftEdge.start + Vec2(1700.0, 0.0)
            assertSnap(doc, edgePoint + Vec2(0.0, 4.0), edgePoint, wall.id)
        }
    }

    @Test fun doorAndWindowFramesSnapCornersJambMidpointsAndEdgeProjections() {
        for (type in OpeningType.entries) {
            val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(5000.0, 0.0), 200.0)
            val opening = Opening("opening", wall.id, 1800.0, 800.0, type)
            val doc = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(opening.id to opening))
            assertSnap(doc, Vec2(1403.0, -97.0), Vec2(1400.0, -100.0), opening.id)
            assertSnap(doc, Vec2(1403.0, 3.0), Vec2(1400.0, 0.0), opening.id)
            assertSnap(doc, Vec2(1403.0, 40.0), Vec2(1400.0, 40.0), opening.id)
        }
    }

    @Test fun rotatedRectColumnSnapsItsActualCornersMidpointsAndFaces() {
        val column = RectColumn("column", Vec2(1000.0, 1000.0), 800.0, 600.0, rotationDegrees = 30.0)
        val doc = SketchDocument(columns = mapOf(column.id to column))
        val points = ColumnGeometry.corners(column)
        for (point in listOf(points[0], (points[0] + points[1]) * .5, points[0] * .7 + points[1] * .3)) {
            val input = point + Geometry.normalize(points[0] - column.center) * 4.0
            // An arbitrary edge location uses a perpendicular displacement, unlike a corner or midpoint.
            val expected = if (point == points[0] || point == (points[0] + points[1]) * .5) point
                else Geometry.closestPoint(input, Segment2(points[0], points[1]))
            assertSnap(doc, input, expected, column.id)
        }
    }

    @Test fun circularColumnSnapsCardinalPointsAndTrueCircumferenceWithoutBoundingBoxEdges() {
        val column = CircularColumn("circle", Vec2(1000.0, 1000.0), 1000.0)
        val doc = SketchDocument(columns = mapOf(column.id to column))
        assertSnap(doc, Vec2(1503.0, 1004.0), Vec2(1500.0, 1000.0), column.id)
        val radial = Vec2(1.0, 1.0) * (1.0 / sqrt(2.0))
        assertSnap(doc, column.center + radial * 506.0, column.center + radial * 500.0, column.id)
        val editor = SketchEditor(doc)
        editor.setTool(ToolType.MEASURE)
        val boxCorner = Vec2(1500.0, 1500.0)
        editor.pointerDown(boxCorner, context(boxCorner))
        assertEquals(SnapType.NONE, editor.state.snap?.type)
    }

    @Test fun secondPointUsesSamePhysicalSnapAndOutsideToleranceFallsBackWithoutChangingWallTool() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(5000.0, 0.0), 200.0)
        val doc = SketchDocument(walls = mapOf(wall.id to wall))
        val editor = SketchEditor(doc)
        editor.setTool(ToolType.MEASURE)
        val first = Vec2(1700.0, 104.0)
        editor.pointerDown(first, context(first))
        editor.pointerUp(first, context(first))
        val second = Vec2(3300.0, -103.0)
        editor.pointerDown(second, context(second))
        assertEquals(Vec2(3300.0, -100.0), editor.state.magnifierTarget)
        val release = Vec2(3400.0, -104.0)
        editor.pointerUp(release, context(release))
        val result = assertIs<SketchPreview.Measurement>(editor.state.preview)
        assertEquals(1700.0, result.line.start.x, .001)
        assertEquals(100.0, result.line.start.y, .001)
        assertEquals(3400.0, result.line.end.x, .001)
        assertEquals(-100.0, result.line.end.y, .001)
        assertTrue(result.complete)
        assertFalse(editor.canUndo)
        val free = Vec2(1700.0, 160.0)
        editor.pointerDown(free, context(free))
        assertEquals(SnapType.NONE, editor.state.snap?.type)
        val grid = context(free).copy(gridStep = 100.0)
        editor.pointerMove(free, grid)
        assertEquals(Vec2(1700.0, 200.0), editor.state.snap?.point)
        assertEquals(SnapType.GRID, editor.state.snap?.type)
        val locator = SnapEngine().snap(doc, Vec2(1700.0, 4.0), SnapContext(12.0))
        assertEquals(1700.0, locator.point.x, .001)
        assertEquals(0.0, locator.point.y, .001)
        assertEquals(SnapType.CONTROL_LINE, locator.type)
        assertEquals(doc, editor.document)
    }
}
