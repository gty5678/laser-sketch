package com.example.boschlaser.shared.command

import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.*

/** Exercise the public command path across directions and all semantic locator alignments. */
class HostedOpeningEditMatrixTest {
    private fun assertPoint(expected: Vec2, actual: Vec2, label: String) {
        assertEquals(expected.x, actual.x, 1e-7, "$label x")
        assertEquals(expected.y, actual.y, 1e-7, "$label y")
    }
    private fun unit(degrees: Int) = Vec2(cos(degrees * PI / 180.0), sin(degrees * PI / 180.0))
    private fun normal(direction: Vec2) = Vec2(-direction.y, direction.x)
    private fun fixture(direction: Vec2, alignment: WallAlignment): Wall =
        Wall("wall", Vec2(100.123456789, -200.987654321),
            Vec2(100.123456789, -200.987654321) + direction * 4000.0, 240.125, alignment)
    private fun physicalOffset(wall: Wall) = when (wall.alignment) {
        WallAlignment.CENTER -> 0.0
        WallAlignment.LEFT -> wall.thickness / 2.0
        WallAlignment.RIGHT -> -wall.thickness / 2.0
    }

    @Test fun lengthEditsPreserveFixedEndDistancesOnMainAndConnectedWallsWithExactUndo() {
        for (degrees in 0 until 360 step 15) for (alignment in WallAlignment.entries) for (anchor in WallAnchor.entries) {
            val direction = unit(degrees)
            val wall = fixture(direction, alignment)
            val junction = if (anchor == WallAnchor.START) wall.end else wall.start
            val connected = Wall("connected", junction, junction + normal(direction) * 2500.0, 180.25, alignment)
            val door = Opening("door", wall.id, 1200.125, 600.25, OpeningType.DOOR)
            val window = Opening("window", connected.id, 900.125, 500.25, OpeningType.WINDOW)
            val original = SketchDocument(walls = listOf(wall, connected).associateBy { it.id },
                openings = listOf(door, window).associateBy { it.id })
            val history = CommandManager(original)
            val label = "$degrees $alignment $anchor"
            assertTrue(history.execute(ChangeWallLengthCommand(wall.id, 5000.0, anchor)), label)
            val changed = history.document
            val edited = changed.walls.getValue(wall.id)
            val follower = changed.walls.getValue(connected.id)
            val fixed = if (anchor == WallAnchor.START) wall.start else wall.end
            assertEquals(fixed, if (anchor == WallAnchor.START) edited.start else edited.end, label)
            assertPoint(if (anchor == WallAnchor.START) edited.end else edited.start, follower.start, label)
            assertEquals(connected.end, follower.end, label)
            assertEquals(5000.0, Geometry.length(WallGeometry.referenceLine(edited)), 1e-7, label)
            val expectedOffset = door.offset + if (anchor == WallAnchor.END) 1000.0 else 0.0
            val editedDoor = changed.openings.getValue(door.id)
            assertEquals(expectedOffset, editedDoor.offset, 1e-7, label)
            assertEquals(door.width, editedDoor.width, label)
            assertPoint(edited.start + direction * expectedOffset + normal(direction) * physicalOffset(edited),
                OpeningGeometry.center(editedDoor, edited), label)
            val followerWindow = changed.openings.getValue(window.id)
            assertEquals(2500.0 - window.offset,
                Geometry.length(WallGeometry.referenceLine(follower)) - followerWindow.offset, 1e-7, label)
            assertEquals(window.width, followerWindow.width, label)
            assertTrue(history.undo(), label)
            assertEquals(original, history.document, label)
            assertFalse(history.canUndo, label)
            assertTrue(history.redo(), label)
            assertEquals(changed, history.document, label)
        }
    }

    @Test fun rotatedEndpointKeepsOpeningDistanceAndWidthOnEveryLocatorAlignment() {
        for (degrees in 0 until 360 step 15) for (alignment in WallAlignment.entries) for (moveStart in listOf(false, true)) {
            val wall = fixture(unit(0), alignment)
            val opening = Opening("opening", wall.id, 1200.125, 600.25, OpeningType.DOOR)
            val original = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(opening.id to opening))
            val direction = unit(degrees)
            val target = if (moveStart) wall.end - direction * 5000.0 else wall.start + direction * 5000.0
            val changed = MoveWallEndpointCommand(wall.id, moveStart, target).apply(original)!!
            val edited = changed.walls.getValue(wall.id)
            val editedOpening = changed.openings.getValue(opening.id)
            val expectedOffset = opening.offset + if (moveStart) 1000.0 else 0.0
            assertEquals(expectedOffset, editedOpening.offset, 1e-7)
            assertEquals(opening.width, editedOpening.width)
            assertEquals(alignment, edited.alignment)
            assertPoint(edited.start + direction * expectedOffset + normal(direction) * physicalOffset(edited),
                OpeningGeometry.center(editedOpening, edited), "$degrees $alignment $moveStart")
            assertEquals(if (moveStart) wall.end else wall.start, if (moveStart) edited.end else edited.start)
        }
    }

    @Test fun conflictOnConnectedWallRejectsWholeEndpointEditAndKeepsRedoForAllDirections() {
        for (degrees in 0 until 360 step 15) for (alignment in WallAlignment.entries) {
            val direction = unit(degrees)
            val wall = fixture(direction, alignment)
            val connected = Wall("connected", wall.end, wall.end + normal(direction) * 2500.0, 180.0, alignment)
            val opening = Opening("window", connected.id, 900.0, 600.0, OpeningType.WINDOW)
            val original = SketchDocument(walls = listOf(wall, connected).associateBy { it.id },
                openings = mapOf(opening.id to opening))
            val history = CommandManager(original)
            assertTrue(history.execute(ChangeWallThicknessCommand(wall.id, 300.0)))
            assertTrue(history.undo())
            assertFalse(history.execute(MoveWallEndpointCommand(wall.id, false, wall.end + normal(direction) * 1700.0)))
            assertEquals(original, history.document)
            assertFalse(history.canUndo)
            assertTrue(history.canRedo)
            assertTrue(history.redo())
            assertEquals(300.0, history.document.walls.getValue(wall.id).thickness)
            assertEquals(connected, history.document.walls.getValue(connected.id))
            assertEquals(opening, history.document.openings.getValue(opening.id))
        }
    }

    @Test fun translatingTeeHostPreservesStemDirectionAndFixedEndOpeningDistance() {
        for (degrees in 0 until 360 step 15) for (alignment in WallAlignment.entries) {
            val direction = unit(degrees)
            val wall = fixture(direction, alignment)
            val contact = wall.start + direction * 2000.0
            val stem = Wall("stem", contact, contact + normal(direction) * 2500.0, 180.0, alignment)
            val opening = Opening("window", stem.id, 900.0, 600.0, OpeningType.WINDOW)
            val original = SketchDocument(walls = listOf(wall, stem).associateBy { it.id }, openings = mapOf(opening.id to opening))
            val history = CommandManager(original)
            assertTrue(history.execute(MoveWallCommand(wall.id, direction * 700.0 + normal(direction) * 200.0)))
            val changed = history.document
            val host = changed.walls.getValue(wall.id)
            val follower = changed.walls.getValue(stem.id)
            assertPoint(wall.start + normal(direction) * 200.0, host.start, "host")
            assertPoint(contact + normal(direction) * 200.0, follower.start, "contact")
            assertEquals(stem.end, follower.end)
            assertPoint(normal(direction), Geometry.normalize(follower.end - follower.start), "stem direction")
            assertEquals(2500.0 - opening.offset, Geometry.length(WallGeometry.referenceLine(follower)) -
                changed.openings.getValue(opening.id).offset, 1e-7)
            assertTrue(history.undo())
            assertEquals(original, history.document)
            assertTrue(history.redo())
            assertEquals(changed, history.document)
        }
    }
    @Test fun trimUsesEachWallsOwnLocatorAcrossDirectionsAndAlignmentPairs() {
        for (degrees in 0 until 360 step 15) for (firstAlignment in WallAlignment.entries) for (secondAlignment in WallAlignment.entries) {
            val direction = unit(degrees)
            val cross = normal(direction)
            val wall = fixture(direction, firstAlignment)
            val intersection = wall.start + direction * 2000.0
            val boundary = Wall("boundary", intersection - cross * 2000.0, intersection + cross * 2000.0,
                180.25, secondAlignment)
            val door = Opening("door", wall.id, 700.125, 600.25, OpeningType.DOOR)
            val window = Opening("window", boundary.id, 700.125, 600.25, OpeningType.WINDOW)
            val original = SketchDocument(walls = listOf(wall, boundary).associateBy { it.id },
                openings = listOf(door, window).associateBy { it.id })
            val history = CommandManager(original)
            val command = TrimWallCommand(wall.id, wall.start + direction * 500.0,
                boundary.id, intersection - cross * 1000.0)
            assertEquals(WallEditStatus.SUCCESS, command.plan(original).status)
            assertTrue(history.execute(command))
            val changed = history.document
            val first = changed.walls.getValue(wall.id)
            val second = changed.walls.getValue(boundary.id)
            assertPoint(intersection, first.end, "first locator")
            assertPoint(intersection, second.end, "second locator")
            assertEquals(wall.start, first.start)
            assertEquals(boundary.start, second.start)
            assertEquals(firstAlignment, first.alignment)
            assertEquals(secondAlignment, second.alignment)
            assertEquals(original.openings, changed.openings)
            assertTrue(history.undo())
            assertEquals(original, history.document)
            assertFalse(history.canUndo)
            assertTrue(history.redo())
            assertEquals(changed, history.document)
        }
    }

    @Test fun extendAndInteriorTrimLeaveBoundaryAndItsOpeningsExactlyUnchanged() {
        for (degrees in 0 until 360 step 15) for (firstAlignment in WallAlignment.entries) for (secondAlignment in WallAlignment.entries) {
            val direction = unit(degrees)
            val cross = normal(direction)
            val base = fixture(direction, firstAlignment)
            val intersection = base.start + direction * 2000.0
            val boundary = Wall("boundary", intersection - cross * 2000.0, intersection + cross * 2000.0,
                180.25, secondAlignment)
            val window = Opening("window", boundary.id, 2500.125, 600.25, OpeningType.WINDOW)
            for (interiorTrim in listOf(false, true)) {
                val wall = if (interiorTrim) base else base.copy(end = base.start + direction * 1000.0)
                val door = Opening("door", wall.id, if (interiorTrim) 3000.125 else 500.125, 600.25, OpeningType.DOOR)
                val original = SketchDocument(walls = listOf(wall, boundary).associateBy { it.id },
                    openings = listOf(door, window).associateBy { it.id })
                val history = CommandManager(original)
                val command = ExtendWallCommand(wall.id, boundary.id,
                    if (interiorTrim) wall.start + direction * 3500.0 else null)
                assertEquals(WallEditStatus.SUCCESS, command.plan(original).status)
                assertTrue(history.execute(command))
                val changed = history.document
                val edited = changed.walls.getValue(wall.id)
                assertPoint(intersection, if (interiorTrim) edited.start else edited.end, "locator intersection")
                assertEquals(if (interiorTrim) wall.end else wall.start, if (interiorTrim) edited.end else edited.start)
                assertEquals(boundary, changed.walls[boundary.id])
                assertEquals(window, changed.openings[window.id])
                assertEquals(door.width, changed.openings.getValue(door.id).width)
                assertEquals(if (interiorTrim) 1000.125 else door.offset, changed.openings.getValue(door.id).offset, 1e-7)
                assertTrue(history.undo())
                assertEquals(original, history.document)
                assertTrue(history.redo())
                assertEquals(changed, history.document)
            }
        }
    }

}
