package com.example.boschlaser.shared.command

import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.Opening
import com.example.boschlaser.shared.model.OpeningType
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall
import com.example.boschlaser.shared.model.WallAnchor
import com.example.boschlaser.shared.topology.SketchTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WallEditingCommandsTest {
    private val horizontal = Wall("horizontal", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
    private val vertical = Wall("vertical", Vec2(1000.0, -1000.0), Vec2(1000.0, 1000.0))
    private fun document(vararg walls: Wall) = SketchDocument(walls = walls.associateBy { it.id })

    @Test
    fun mathematicalCrossingDoesNotBecomeEndpointJunction() {
        assertNull(SketchTopology(document(horizontal, vertical)).junctionAt(Vec2(1000.0, 0.0)))
        val connected = vertical.copy(start = horizontal.end)
        assertEquals(2, SketchTopology(document(horizontal, connected)).junctionAt(horizontal.end)!!.endpoints.size)
        val stem = vertical.copy(start = Vec2(1000.0, 0.0))
        val tee = SketchTopology(document(horizontal, stem)).junctionAt(stem.start)!!
        assertEquals(setOf(horizontal.id), tee.hostWallIds)
        assertEquals(1, tee.endpoints.size)
    }
    @Test
    fun trimClickSelectsRetainedHalfOnEachWall() {
        val scene = document(horizontal, vertical)
        val left = TrimWallCommand(horizontal.id, Vec2(100.0, 0.0), vertical.id, Vec2(1000.0, 500.0)).apply(scene)!!
        assertEquals(Vec2(1000.0, 0.0), left.walls.getValue(horizontal.id).end)
        assertEquals(Vec2(1000.0, 0.0), left.walls.getValue(vertical.id).start)
        val right = TrimWallCommand(horizontal.id, Vec2(1900.0, 0.0), vertical.id, Vec2(1000.0, -500.0)).apply(scene)!!
        assertEquals(Vec2(1000.0, 0.0), right.walls.getValue(horizontal.id).start)
        assertEquals(Vec2(1000.0, 0.0), right.walls.getValue(vertical.id).end)
    }
    @Test
    fun extendKeepsBoundaryAndSupportsExtensionAndInteriorTrim() {
        val first = horizontal.copy(end = Vec2(500.0, 0.0))
        val extended = ExtendWallCommand(first.id, vertical.id).apply(document(first, vertical))!!
        assertEquals(Vec2(1000.0, 0.0), extended.walls.getValue(first.id).end)
        assertEquals(vertical, extended.walls.getValue(vertical.id))
        val trimmed = ExtendWallCommand(horizontal.id, vertical.id, Vec2(1900.0, 0.0)).apply(document(horizontal, vertical))!!
        assertEquals(Vec2(1000.0, 0.0), trimmed.walls.getValue(horizontal.id).start)
        assertEquals(vertical, trimmed.walls.getValue(vertical.id))
        assertEquals(WallEditStatus.ALREADY_REACHES, ExtendWallCommand(first.id, vertical.id).plan(extended).status)
    }
    @Test
    fun parallelMissedAndHostedOpeningConflictsAreAtomicFailures() {
        val parallel = horizontal.copy(id = "parallel", start = Vec2(0.0, 500.0), end = Vec2(2000.0, 500.0))
        assertEquals(WallEditStatus.PARALLEL, ExtendWallCommand(horizontal.id, parallel.id).plan(document(horizontal, parallel)).status)
        val missed = vertical.copy(start = Vec2(1000.0, 500.0), end = Vec2(1000.0, 1500.0))
        assertEquals(WallEditStatus.TARGET_MISSED, ExtendWallCommand(horizontal.id, missed.id).plan(document(horizontal, missed)).status)
        val door = Opening("door", horizontal.id, 1500.0, 900.0, OpeningType.DOOR)
        val scene = document(horizontal, vertical).copy(openings = mapOf(door.id to door))
        assertEquals(WallEditStatus.OPENING_CONFLICT, ExtendWallCommand(horizontal.id, vertical.id, Vec2(100.0, 0.0)).plan(scene).status)
        assertNull(TrimWallCommand(horizontal.id, Vec2(100.0, 0.0), vertical.id, Vec2(1000.0, 500.0)).apply(scene))
        assertEquals(horizontal, scene.walls.getValue(horizontal.id))
        assertEquals(door, scene.openings.getValue(door.id))
    }
    @Test
    fun twoWallEndpointDragFollowsButThreeWayDragCanDetachOnlySelectedWall() {
        val connected = vertical.copy(start = horizontal.end)
        val point = Vec2(2200.0, 200.0)
        val pair = MoveWallEndpointCommand(horizontal.id, false, point, detachMultiWay = true).apply(document(horizontal, connected))!!
        assertEquals(point, pair.walls.getValue(connected.id).start)
        val third = horizontal.copy(id = "third", start = horizontal.end, end = Vec2(3000.0, 1000.0))
        val detached = MoveWallEndpointCommand(horizontal.id, false, point, detachMultiWay = true).apply(document(horizontal, connected, third))!!
        assertEquals(point, detached.walls.getValue(horizontal.id).end)
        assertEquals(connected, detached.walls.getValue(connected.id))
        assertEquals(third, detached.walls.getValue(third.id))
    }
    @Test
    fun translatingHostMovesTNodeWithoutRotatingStems() {
        val stem = vertical.copy(start = Vec2(1000.0, 0.0), end = Vec2(1500.0, 1000.0))
        val joined = horizontal.copy(id = "joined", start = stem.start, end = Vec2(500.0, 1000.0))
        val moved = MoveWallCommand(horizontal.id, Vec2(600.0, 200.0)).apply(document(horizontal, stem, joined))!!
        assertEquals(Vec2(0.0, 200.0), moved.walls.getValue(horizontal.id).start)
        assertEquals(Vec2(1100.0, 200.0), moved.walls.getValue(stem.id).start)
        assertEquals(moved.walls.getValue(stem.id).start, moved.walls.getValue(joined.id).start)
        assertEquals(stem.end, moved.walls.getValue(stem.id).end)
    }
    @Test
    fun structuralLengthChangeStillPropagatesThroughMultiWayJunction() {
        val connected = vertical.copy(start = horizontal.end)
        val third = horizontal.copy(id = "third", start = horizontal.end, end = Vec2(3000.0, 1000.0))
        val result = ChangeWallLengthCommand(horizontal.id, 2500.0, WallAnchor.START).apply(document(horizontal, connected, third))!!
        assertEquals(Vec2(2500.0, 0.0), result.walls.getValue(connected.id).start)
        assertEquals(Vec2(2500.0, 0.0), result.walls.getValue(third.id).start)
        assertNotNull(SketchTopology(result).junctionAt(Vec2(2500.0, 0.0)))
        assertEquals(SketchTopology(document(horizontal, connected, third)).junctionAt(horizontal.end)!!.id,
            SketchTopology(result).junctionAt(Vec2(2500.0, 0.0))!!.id)
    }
}
