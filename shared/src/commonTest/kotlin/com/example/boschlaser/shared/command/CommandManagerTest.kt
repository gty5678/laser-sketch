package com.example.boschlaser.shared.command

import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.Opening
import com.example.boschlaser.shared.model.OpeningType
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall
import com.example.boschlaser.shared.model.WallAnchor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandManagerTest {
    private val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
    private val door = Opening("door", wall.id, 700.0, 900.0, OpeningType.DOOR)
    private val initial = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(door.id to door))

    @Test
    fun addDeleteUndoRedoRestoreHostedObjects() {
        val manager = CommandManager()
        assertTrue(manager.execute(AddWallCommand(wall)))
        assertTrue(manager.execute(AddOpeningCommand(door)))
        assertEquals(initial, manager.document)
        assertTrue(manager.execute(DeleteWallCommand(wall.id)))
        assertTrue(manager.document.openings.isEmpty())
        assertTrue(manager.undo())
        assertEquals(initial, manager.document)
        assertTrue(manager.redo())
        assertTrue(manager.document.walls.isEmpty())
        assertTrue(manager.document.openings.isEmpty())
    }
    @Test
    fun undoDeletionPreservesDrawingAndHitOrder() {
        val second = wall.copy(id = "second")
        val manager = CommandManager(initial.copy(walls = linkedMapOf(wall.id to wall, second.id to second)))
        manager.execute(DeleteWallCommand(wall.id))
        manager.undo()
        assertEquals(listOf(wall.id, second.id), manager.document.walls.keys.toList())
    }
    @Test
    fun changeLengthPreservesDistanceAndUndoRestoresExactData() {
        val manager = CommandManager(initial)
        assertTrue(manager.execute(ChangeWallLengthCommand(wall.id, 3000.0, WallAnchor.END)))
        assertEquals(1700.0, manager.document.openings.getValue(door.id).offset)
        assertTrue(manager.undo())
        assertEquals(initial, manager.document)
        assertTrue(manager.redo())
        assertEquals(1700.0, manager.document.openings.getValue(door.id).offset)
    }
    @Test
    fun connectedWallOpeningConflictRejectsWholeEditWithoutHistory() {
        val connected = Wall("connected", Vec2(2000.0, 0.0), Vec2(2000.0, 2000.0))
        val window = Opening("window", connected.id, 700.0, 500.0, OpeningType.WINDOW)
        val document = initial.copy(walls = initial.walls + (connected.id to connected),
            openings = initial.openings + (window.id to window))
        val manager = CommandManager(document)
        assertFalse(manager.execute(UpdateWallsCommand(mapOf(
            wall.id to wall.copy(end = Vec2(2000.0, 1000.0)),
            connected.id to connected.copy(start = Vec2(2000.0, 1000.0)),
        ))))
        assertEquals(document, manager.document)
        assertFalse(manager.canUndo)
    }
    @Test
    fun failedAndNoopCommandsDoNotEraseRedoOrConsumeHistory() {
        val manager = CommandManager(initial)
        assertTrue(manager.execute(ChangeWallThicknessCommand(wall.id, 300.0)))
        assertTrue(manager.undo())
        assertFalse(manager.execute(ChangeWallLengthCommand(wall.id, 1000.0, WallAnchor.START)))
        assertFalse(manager.execute(UpdateWallsCommand(mapOf(wall.id to wall))))
        assertTrue(manager.canRedo)
        assertTrue(manager.redo())
        assertEquals(300.0, manager.document.walls.getValue(wall.id).thickness)
    }
    @Test
    fun newEditAfterUndoClearsRedoAndHistoryIsBounded() {
        val manager = CommandManager(initial, historyLimit = 1)
        manager.execute(ChangeWallThicknessCommand(wall.id, 300.0))
        manager.execute(ChangeWallThicknessCommand(wall.id, 400.0))
        assertTrue(manager.undo())
        assertFalse(manager.undo())
        assertEquals(300.0, manager.document.walls.getValue(wall.id).thickness)
        assertTrue(manager.execute(ChangeWallThicknessCommand(wall.id, 500.0)))
        assertFalse(manager.canRedo)
        manager.replace(initial)
        assertFalse(manager.canUndo)
        assertEquals(initial, manager.document)
    }
}
