package com.example.boschlaser.shared.geometry

import com.example.boschlaser.shared.command.*
import com.example.boschlaser.shared.model.*
import kotlin.test.*

class OpeningOverlapTest {
    private val wall = Wall("host", Vec2(0.0, 0.0), Vec2(6000.0, 0.0))
    private val door = Opening("door", wall.id, 1500.0, 1000.0, OpeningType.DOOR)
    private val window = Opening("window", wall.id, 3000.0, 1000.0, OpeningType.WINDOW)
    private val initial = SketchDocument(walls = mapOf(wall.id to wall),
        openings = mapOf(door.id to door, window.id to window))

    @Test fun intervalsDetectAllDoorStylesAndWindowsButNotTouchingOrOtherHosts() {
        for (style in DoorStyle.entries) {
            val opening = door.copy(doorStyle = style)
            assertTrue(openingsOverlap(opening, window.copy(offset = 1800.0)))
            assertTrue(openingsOverlap(window.copy(offset = 1800.0), opening))
            assertTrue(openingsOverlap(opening, window.copy(offset = 1500.0, width = 200.0)))
            assertFalse(openingsOverlap(opening, window.copy(offset = 2500.0)))
            assertFalse(openingsOverlap(opening, window.copy(offset = 2500.001)))
            assertFalse(openingsOverlap(opening, window.copy(hostWallId = "other", offset = 1500.0)))
            assertFalse(openingsOverlap(opening, opening))
        }
    }

    @Test fun warningIsAdvisoryAndTracksOnlyChangedOpeningGeometry() {
        val manager = CommandManager(initial)
        val overlapping = window.copy(offset = 1800.0)
        assertTrue(manager.execute(UpdateOpeningCommand(overlapping)))
        val moved = manager.document
        assertTrue(hasEditedOpeningOverlap(initial, moved))
        assertFalse(hasEditedOpeningOverlap(moved, moved))
        assertFalse(hasEditedOpeningOverlap(moved, moved.copy(openings = moved.openings +
            (door.id to door.copy(flipped = true, doorStyle = DoorStyle.DOUBLE)))))
        assertFalse(hasEditedOpeningOverlap(moved, initial))
        assertTrue(manager.undo())
        assertEquals(initial, manager.document)
        assertTrue(manager.redo())
        assertEquals(moved, manager.document)
        assertTrue(hasEditedOpeningOverlap(initial, AddOpeningCommand(door.copy(id = "new", offset = 1600.0)).apply(initial)!!))
        assertTrue(hasEditedOpeningOverlap(initial, ChangeOpeningPropertiesCommand(door.id, 2400.0).apply(initial)!!))
        assertTrue(hasEditedOpeningOverlap(moved, UpdateOpeningCommand(overlapping.copy(offset = 1900.0)).apply(moved)!!))
    }
}
