package com.example.boschlaser.shared.command

import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import kotlin.test.*

class OpeningPropertyCommandsTest {
    private val wall = Wall("wall", Vec2(10.123456789, 20.987654321), Vec2(4010.123456789, 20.987654321),
        240.123456789, WallAlignment.RIGHT)
    private val door = Opening("door", wall.id, 700.123456789, 900.123456789, OpeningType.DOOR)
    private val window = Opening("window", wall.id, 3000.987654321, 1200.123456789, OpeningType.WINDOW)
    private val original = SketchDocument(walls = mapOf(wall.id to wall),
        openings = listOf(door, window).associateBy { it.id })

    @Test fun widthAndFlipsPreserveUnchangedSemanticFieldsAndUndoExactly() {
        val manager = CommandManager(original)
        assertTrue(manager.execute(ChangeOpeningPropertiesCommand(door.id, 1000.987654321, flip = true, flipHinge = true)))
        val expected = door.copy(width = 1000.987654321, flipped = true, hingeFlipped = true)
        assertEquals(expected, manager.document.openings[door.id])
        assertEquals(window, manager.document.openings[window.id])
        assertEquals(original.walls, manager.document.walls)
        val changed = manager.document
        assertTrue(manager.undo())
        assertEquals(original, manager.document)
        assertFalse(manager.canUndo)
        assertTrue(manager.redo())
        assertEquals(changed, manager.document)
    }
    @Test fun widerOpeningsClampCentersAtEitherEndWithoutMovingTheHost() {
        for (opening in listOf(door, window)) {
            val updated = ChangeOpeningPropertiesCommand(opening.id, 2000.0).apply(original)!!
            val expectedOffset = if (opening.id == door.id) 1000.0 else 3000.0
            assertEquals(expectedOffset, updated.openings.getValue(opening.id).offset, 1e-9)
            assertEquals(2000.0, updated.openings.getValue(opening.id).width, 1e-9)
            assertEquals(original.walls, updated.walls)
        }
    }
    @Test fun invalidWidthsAndMissingIdsFailWithoutChangingDocumentSelectionOrRedo() {
        val manager = CommandManager(original)
        assertTrue(manager.execute(ChangeOpeningPropertiesCommand(door.id, 1000.0)))
        assertTrue(manager.undo())
        for (width in listOf(99.999, -1.0, 4000.0, 5000.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFalse(manager.execute(ChangeOpeningPropertiesCommand(door.id, width, flip = true)))
            assertEquals(original, manager.document)
            assertFalse(manager.canUndo)
            assertTrue(manager.canRedo)
        }
        assertNull(ChangeOpeningPropertiesCommand("missing", 100.0).apply(original))
        assertFalse(manager.execute(ChangeOpeningPropertiesCommand(door.id, door.width)))
        assertTrue(manager.canRedo)
        assertTrue(manager.redo())
    }
    @Test fun minimumWidthAndFlipOnlyEditsKeepExactCenterAndWidth() {
        assertEquals(door.copy(width = 100.0), ChangeOpeningPropertiesCommand(door.id, 100.0).apply(original)!!.openings[door.id])
        val flipped = ChangeOpeningPropertiesCommand(door.id, door.width, flipHinge = true).apply(original)!!
        assertEquals(door.copy(hingeFlipped = true), flipped.openings[door.id])
        assertEquals(original, ChangeOpeningPropertiesCommand(door.id, door.width, flipHinge = true).apply(flipped))
    }
}
