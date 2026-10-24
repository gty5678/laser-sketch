package com.example.boschlaser.shared.command

import com.example.boschlaser.shared.geometry.OpeningGeometry
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.Opening
import com.example.boschlaser.shared.model.OpeningType
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall
import com.example.boschlaser.shared.topology.WallEndpoint
import com.example.boschlaser.shared.topology.SketchTopology
import com.example.boschlaser.shared.topology.WallEndpointRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SplitWallCommandTest {
    private val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(4000.0, 0.0))
    @Test
    fun splitKeepsOpeningWorldPositionAndUndoRestoresHost() {
        val door = Opening("door", wall.id, 3000.0, 900.0, OpeningType.DOOR)
        val initial = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(door.id to door))
        val manager = CommandManager(initial)
        assertTrue(manager.execute(SplitWallCommand(wall.id, Vec2(2000.0, 0.0), "second")))
        val updated = manager.document.openings.getValue(door.id)
        assertEquals("second", updated.hostWallId)
        assertEquals(1000.0, updated.offset)
        assertEquals(Vec2(3000.0, 0.0), OpeningGeometry.center(updated, manager.document.walls.getValue("second")))
        manager.undo()
        assertEquals(initial, manager.document)
    }
    @Test
    fun splitThroughDoorOrOffWallOrAtEndpointFailsAtomically() {
        val door = Opening("door", wall.id, 2000.0, 900.0, OpeningType.DOOR)
        val scene = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(door.id to door))
        assertNull(SplitWallCommand(wall.id, Vec2(2000.0, 0.0), "second").apply(scene))
        assertNull(SplitWallCommand(wall.id, Vec2(1000.0, 100.0), "second").apply(scene))
        assertNull(SplitWallCommand(wall.id, wall.start, "second").apply(scene))
        assertNull(SplitWallCommand(wall.id, Vec2(1000.0, 0.0), wall.id).apply(scene))
        assertEquals(1, scene.walls.size)
    }
    @Test
    fun explicitEndpointMergeConnectsBothWallsAndIsUndoable() {
        val second = wall.copy(id = "second", start = Vec2(4200.0, 100.0), end = Vec2(4200.0, 2100.0))
        val initial = SketchDocument(walls = linkedMapOf(wall.id to wall, second.id to second))
        val manager = CommandManager(initial)
        val point = Vec2(4000.0, 0.0)
        assertTrue(manager.execute(JoinWallsCommand(WallEndpointRef(wall.id, WallEndpoint.END),
            WallEndpointRef(second.id, WallEndpoint.START), point)))
        assertEquals(point, manager.document.walls.getValue(second.id).start)
        manager.undo()
        assertEquals(initial, manager.document)
    }
    @Test fun explicitSplitsCreateFourWayJunctionWhileMathematicalCrossingStaysDisconnected() {
        val horizontal = wall.copy(start = Vec2(-2000.0, 0.0), end = Vec2(2000.0, 0.0))
        val vertical = wall.copy(id = "vertical", start = Vec2(0.0, -2000.0), end = Vec2(0.0, 2000.0))
        val original = SketchDocument(walls = linkedMapOf(horizontal.id to horizontal, vertical.id to vertical))
        val origin = Vec2(0.0, 0.0)
        assertNull(SketchTopology(original).junctionAt(origin))
        val manager = CommandManager(original)
        assertTrue(manager.execute(SplitWallCommand(horizontal.id, origin, "horizontal-right")))
        val tee = assertNotNull(SketchTopology(manager.document).junctionAt(origin))
        assertEquals(2, tee.endpoints.size)
        assertEquals(setOf(vertical.id), tee.hostWallIds)
        val afterFirst = manager.document
        assertTrue(manager.execute(SplitWallCommand(vertical.id, origin, "vertical-bottom")))
        val cross = assertNotNull(SketchTopology(manager.document).junctionAt(origin))
        assertEquals(setOf(WallEndpointRef(horizontal.id, WallEndpoint.END),
            WallEndpointRef("horizontal-right", WallEndpoint.START),
            WallEndpointRef(vertical.id, WallEndpoint.END),
            WallEndpointRef("vertical-bottom", WallEndpoint.START)), cross.endpoints)
        assertTrue(cross.hostWallIds.isEmpty())
        val reversed = manager.document.copy(walls = manager.document.walls.entries.toList().asReversed()
            .associate { it.key to it.value })
        assertEquals(cross, SketchTopology(reversed).junctionAt(origin))
        assertTrue(manager.undo())
        assertEquals(afterFirst, manager.document)
        assertTrue(manager.undo())
        assertEquals(original, manager.document)
        assertNull(SketchTopology(manager.document).junctionAt(origin))
        assertTrue(manager.redo())
        assertTrue(manager.redo())
        assertEquals(cross, SketchTopology(manager.document).junctionAt(origin))
    }

    @Test fun endpointMergeWithOpeningConflictKeepsAllWallsAndHistoryUnchanged() {
        val second = wall.copy(id = "second", start = Vec2(4200.0, 100.0), end = Vec2(4200.0, 2100.0))
        val door = Opening("door", wall.id, 3500.0, 900.0, OpeningType.DOOR)
        val original = SketchDocument(walls = linkedMapOf(wall.id to wall, second.id to second),
            openings = mapOf(door.id to door))
        val manager = CommandManager(original)
        assertFalse(manager.execute(JoinWallsCommand(WallEndpointRef(wall.id, WallEndpoint.END),
            WallEndpointRef(second.id, WallEndpoint.START), Vec2(3000.0, 0.0))))
        assertEquals(original, manager.document)
        assertFalse(manager.canUndo)
        assertFalse(manager.canRedo)
        assertFalse(manager.undo())
        assertEquals(original, manager.document)
    }

}
