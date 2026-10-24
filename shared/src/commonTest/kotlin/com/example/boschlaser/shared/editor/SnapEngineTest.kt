package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.CircularColumn
import com.example.boschlaser.shared.model.RectColumn
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SnapEngineTest {
    private val engine = SnapEngine()
    private val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(1000.0, 0.0), 100.0)
    private val document = SketchDocument(walls = mapOf(wall.id to wall))
    @Test
    fun endpointPriorityAndControlLineProjectionHaveBoundedTolerance() {
        assertEquals(SnapResult(Vec2(1000.0, 0.0), SnapType.ENDPOINT, wall.id),
            engine.snap(document, Vec2(995.0, 10.0), SnapContext(20.0)))
        assertEquals(SnapResult(Vec2(450.0, 0.0), SnapType.CONTROL_LINE, wall.id),
            engine.snap(document, Vec2(450.0, 10.0), SnapContext(20.0)))
        assertEquals(SnapType.NONE, engine.snap(document, Vec2(450.0, 100.0), SnapContext(20.0)).type)
    }
    @Test
    fun midpointAndIntersectionBeatContinuousLineProjection() {
        assertEquals(Vec2(500.0, 0.0), engine.snap(document, Vec2(510.0, 0.0),
            SnapContext(20.0, midpoints = true)).point)
        val crossing = wall.copy(id = "crossing", start = Vec2(600.0, -500.0), end = Vec2(600.0, 500.0))
        val scene = document.copy(walls = document.walls + (crossing.id to crossing))
        assertEquals(SnapType.INTERSECTION, engine.snap(scene, Vec2(590.0, 5.0),
            SnapContext(20.0, intersections = true)).type)
    }
    @Test
    fun gridAxesAndAngleStepWorkWithoutAnyWalls() {
        assertEquals(Vec2(-100.0, 200.0), engine.grid(Vec2(-150.0, 151.0), 100.0))
        assertEquals(SnapType.HORIZONTAL, engine.axis(Vec2(0.0, 0.0), Vec2(1000.0, 100.0)).type)
        assertEquals(SnapType.VERTICAL, engine.axis(Vec2(0.0, 0.0), Vec2(100.0, 1000.0)).type)
        val result = engine.snap(SketchDocument(), Vec2(1000.0, 600.0),
            SnapContext(20.0, origin = Vec2(0.0, 0.0), angleStepDegrees = 15.0))
        assertEquals(30.0, atan2(result.point.y, result.point.x) * 180.0 / PI, 1e-6)
        assertFailsWith<IllegalArgumentException> { SnapContext(-1.0) }
        assertFailsWith<IllegalArgumentException> { engine.grid(Vec2(0.0, 0.0), 0.0) }
    }
    @Test
    fun columnsStayFreeUnlessACenterOrFaceIsNearbyAndCornerAlignmentIsOrderIndependent() {
        val column = RectColumn("column", Vec2(300.0, 300.0), 100.0, 100.0)
        val horizontal = wall.copy(start = Vec2(500.0, 500.0), end = Vec2(800.0, 500.0))
        val vertical = wall.copy(id = "vertical", start = Vec2(500.0, 500.0), end = Vec2(500.0, 800.0))
        for (walls in listOf(listOf(horizontal, vertical), listOf(vertical, horizontal))) {
            val scene = SketchDocument(walls = walls.associateBy { it.id })
            assertEquals(Vec2(600.0, 600.0), engine.columnCenter(scene, column, Vec2(602.0, 602.0), 20.0).point)
            assertEquals(Vec2(313.0, 317.0), engine.columnCenter(scene, column, Vec2(313.0, 317.0), 20.0).point)
        }
        val circle = CircularColumn("circle", Vec2(0.0, 0.0), 100.0)
        assertEquals(SnapType.NONE, engine.columnCenter(document, circle, Vec2(500.0, 102.0), 20.0).type)
    }
    @Test
    fun rotatedColumnBoundsParticipateInWallFaceAlignment() {
        val column = RectColumn("column", Vec2(0.0, 0.0), 100.0, 300.0, 90.0)
        assertEquals(Vec2(500.0, 100.0), engine.columnCenter(document, column, Vec2(500.0, 102.0), 20.0).point)
    }
    @Test fun gridDoesNotClampCoordinatesToIntRange() {
        val point = Vec2(2147483648.123, -2147483649.123)
        val expected = Vec2(2147483648.125, -2147483649.125)
        assertEquals(expected, engine.grid(point, .125))
        assertEquals(SnapResult(expected, SnapType.GRID), engine.snap(SketchDocument(), point,
            SnapContext(0.0, gridStep = .125)))
    }

    @Test fun gridRetainsHalfStepsTowardPositiveInfinityForBothSigns() {
        assertEquals(Vec2(200.0, -100.0), engine.grid(Vec2(150.0, -150.0), 100.0))
        assertEquals(Vec2(100.0, -100.0), engine.grid(Vec2(149.9999, -149.9999), 100.0))
        assertEquals(Vec2(200.0, -200.0), engine.grid(Vec2(150.0001, -150.0001), 100.0))
        assertEquals(Vec2(0.0, 0.0), engine.grid(Vec2(-0.0, -50.0), 100.0))
    }

    @Test fun exactLargeGridMultiplesDoNotMoveDuringRounding() {
        val point = Vec2(4503599627370497.0, -4503599627370497.0)
        assertEquals(point, engine.grid(point, 1.0))
    }

    @Test fun unrepresentableGridCalculationKeepsFiniteInputInsteadOfThrowingOrSaturating() {
        val point = Vec2(1e300, -1e300)
        assertEquals(point, engine.grid(point, Double.MIN_VALUE))
        val huge = Vec2(Double.MAX_VALUE, -Double.MAX_VALUE)
        assertEquals(huge, engine.grid(huge, 1e308))
    }

}
