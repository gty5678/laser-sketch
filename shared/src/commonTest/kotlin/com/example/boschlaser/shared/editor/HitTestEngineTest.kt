package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.geometry.ColumnGeometry
import com.example.boschlaser.shared.geometry.OpeningGeometry
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.CircularColumn
import com.example.boschlaser.shared.model.Opening
import com.example.boschlaser.shared.model.OpeningType
import com.example.boschlaser.shared.model.RectColumn
import com.example.boschlaser.shared.model.Wall
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.WallAlignment
import com.example.boschlaser.shared.topology.WallEndpoint
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HitTestEngineTest {
    private val engine = HitTestEngine()
    @Test
    fun rotatedRectangleAndCircleUseActualShapeInsteadOfBoundingCircle() {
        val rectangle = RectColumn("column", Vec2(0.0, 0.0), 200.0, 800.0, 90.0)
        assertTrue(engine.hitsColumn(rectangle, Vec2(350.0, 0.0), 14.0))
        assertFalse(engine.hitsColumn(rectangle, Vec2(0.0, 350.0), 14.0))
        val circle = CircularColumn("circle", Vec2(0.0, 0.0), 200.0)
        assertTrue(engine.hitsColumn(circle, Vec2(100.0, 0.0), 14.0))
        assertFalse(engine.hitsColumn(circle, Vec2(100.0, 100.0), 14.0))
        assertEquals(4, ColumnGeometry.corners(rectangle).size)
    }
    @Test
    fun doorHitFollowsLeafAndArcForAllFlipCombinations() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(3000.0, 0.0), 200.0)
        for (flipped in listOf(false, true)) for (hingeFlipped in listOf(false, true)) {
            val door = Opening("door", wall.id, 1500.0, 900.0, OpeningType.DOOR, flipped, hingeFlipped)
            val leaf = OpeningGeometry.symbolSegments(door, wall)[2]
            assertTrue(engine.hitsOpening(door, wall, leaf.end, 14.0))
            val emptySide = if (flipped) Vec2(1500.0, 400.0) else Vec2(1500.0, -400.0)
            assertFalse(engine.hitsOpening(door, wall, emptySide, 14.0))
        }
    }
    @Test
    fun windowHasFourLongitudinalLinesAndTwoJambs() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(3000.0, 0.0), 300.0)
        val window = Opening("window", wall.id, 1500.0, 1200.0, OpeningType.WINDOW)
        assertEquals(6, OpeningGeometry.symbolSegments(window, wall).size)
        assertTrue(engine.hitsOpening(window, wall, Vec2(1500.0, 50.0), 1.0))
        assertFalse(engine.hitsOpening(window, wall, Vec2(1500.0, 500.0), 14.0))
    }
    @Test fun selectedWallLocatorEndpointPrecedesLaterDrawnWallAndBody() {
        val first = Wall("first", Vec2(0.0, 0.0), Vec2(1000.0, 0.0), alignment = WallAlignment.LEFT)
        val second = first.copy(id = "second")
        val document = SketchDocument(walls = listOf(first, second).associateBy { it.id })
        val context = HitTestContext(24.0, 14.0, 14.0, 20.0, first.id)
        val hits = engine.hitTest(document, Vec2(0.0, 0.0), context)
        assertEquals(HitResult.WallEndpoint(first.id, WallEndpoint.START), hits.first())
        assertEquals(HitResult.WallEndpoint(second.id, WallEndpoint.START), hits[1])
        assertEquals(HitResult.WallEndpoint(second.id, WallEndpoint.END),
            engine.hitTest(document, Vec2(1000.0, 0.0), context.copy(selectedWallId = null)).first())
        assertTrue(engine.hitTest(document, Vec2(0.0, 120.0), context).none { it is HitResult.WallEndpoint })
    }

    @Test fun symbolsPrecedeColumnsAndWallBodiesWithIndependentTolerances() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(3000.0, 0.0), 240.0)
        val window = Opening("window", wall.id, 1500.0, 1000.0, OpeningType.WINDOW)
        val firstColumn = RectColumn("first", Vec2(1500.0, 0.0), 100.0, 100.0)
        val secondColumn = firstColumn.copy(id = "second")
        val document = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(window.id to window),
            columns = listOf(firstColumn, secondColumn).associateBy { it.id })
        val context = HitTestContext(24.0, 50.0, 14.0, 20.0)
        assertEquals(listOf(HitResult.Opening(window.id), HitResult.Column(secondColumn.id),
            HitResult.Column(firstColumn.id), HitResult.Wall(wall.id)),
            engine.hitTest(document, Vec2(1500.0, 0.0), context))
        assertEquals(HitResult.Column(secondColumn.id),
            engine.hitTest(document, Vec2(1500.0, 0.0), context.copy(openingTolerance = 0.0)).first())
        assertEquals(listOf(HitResult.Wall(wall.id)),
            engine.hitTest(document, Vec2(1500.0, -139.0), context.copy(openingTolerance = 0.0)))
    }

    @Test fun endpointBoundaryIsInclusiveAndEmptySpaceHasNoHit() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(1000.0, 0.0))
        val document = SketchDocument(walls = mapOf(wall.id to wall))
        val context = HitTestContext(24.0, 14.0, 14.0, 20.0)
        assertEquals(HitResult.WallEndpoint(wall.id, WallEndpoint.START),
            engine.hitTest(document, Vec2(-24.0, 0.0), context).first())
        assertTrue(engine.hitTest(document, Vec2(-24.00001, 0.0), context).none { it is HitResult.WallEndpoint })
        assertTrue(engine.hitTest(document, Vec2(500.0, 1000.0), context).isEmpty())
    }

    @Test fun selectionHitContextRejectsInvalidTolerances() {
        for (invalid in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { HitTestContext(invalid, 0.0, 0.0, 0.0) }
            assertFailsWith<IllegalArgumentException> { HitTestContext(0.0, invalid, 0.0, 0.0) }
            assertFailsWith<IllegalArgumentException> { HitTestContext(0.0, 0.0, invalid, 0.0) }
            assertFailsWith<IllegalArgumentException> { HitTestContext(0.0, 0.0, 0.0, invalid) }
        }
    }

}
