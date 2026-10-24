package com.example.boschlaser.shared.command

import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.editor.*
import com.example.boschlaser.shared.storage.*
import kotlin.test.*

class WallEndDimensionTest {
    @Test fun pickingDoorJambFirstStillAllowsBothShortEndsOfItsHostWall() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(5000.0, 0.0), 200.0)
        val door = Opening("door", wall.id, 1200.0, 800.0, OpeningType.DOOR)
        val document = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(door.id to door))
        val first = OpeningFrameReference(door.id, OpeningFrameSide.START)
        for ((point, side) in listOf(wall.start to WallFaceSide.START, wall.end to WallFaceSide.END)) {
            val reference = assertNotNull(WallDimensionGeometry.pickFace(document, point, 20.0, first))
            assertEquals(WallFaceReference(wall.id, side), reference)
            val dimension = WallDimension("dim", first, reference, Vec2(600.0, 500.0))
            assertNotNull(AddWallDimensionCommand(dimension).apply(document))
            assertEquals(first, WallDimensionGeometry.pickFace(document, Vec2(800.0, 0.0), 20.0, reference))
        }
        // Android converts Float screen coordinates back to Double world coordinates.
        assertEquals(first, WallDimensionGeometry.pickFace(document, Vec2(800.001, 0.0), 20.0))
        assertEquals(WallFaceReference(wall.id, WallFaceSide.START),
            WallDimensionGeometry.pickFace(document, Vec2(.001, 0.0), 20.0))
        assertEquals(WallFaceReference(wall.id, WallFaceSide.CENTER),
            WallDimensionGeometry.pickFace(document, Vec2(600.0, 0.0), 20.0))
    }

    @Test fun endSectionsFollowRotationAndControlLineAlignmentWithoutChangingLongSides() {
        for (direction in listOf(Vec2(4000.0, 0.0), Vec2(0.0, 4000.0), Vec2(2400.0, 3200.0))) {
            for (alignment in WallAlignment.entries) {
                val wall = Wall("wall", Vec2(100.0, 200.0), Vec2(100.0, 200.0) + direction, 240.0, alignment = alignment)
                val document = SketchDocument(walls = mapOf(wall.id to wall))
                val shape = WallGeometry.build(wall)
                for ((side, expected) in listOf(
                    WallFaceSide.START to Segment2(shape.leftEdge.start, shape.rightEdge.start),
                    WallFaceSide.END to Segment2(shape.leftEdge.end, shape.rightEdge.end))) {
                    val face = assertNotNull(WallDimensionGeometry.face(document, WallFaceReference(wall.id, side)))
                    assertEquals(expected, face)
                    assertEquals(240.0, Geometry.length(face), .001)
                    assertEquals(0.0, (face.end - face.start).dot(direction), .001)
                    assertEquals(WallFaceReference(wall.id, side), WallDimensionGeometry.pickFace(document,
                        (face.start + face.end) * .5, 1.0))
                }
                assertEquals(shape.centerLine, WallDimensionGeometry.face(document, WallFaceReference(wall.id, WallFaceSide.CENTER)))
                assertTrue(WallDimensionGeometry.forSelectedWall(document, wall.id).isEmpty())
            }
        }
    }

    @Test fun doorAndWindowDriveFromEitherHostEndAndPersistWithUndoAndAtomicFailure() {
        for (type in OpeningType.entries) for (end in listOf(WallFaceSide.START, WallFaceSide.END)) {
            val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(5000.0, 0.0), 200.0)
            val opening = Opening("opening", wall.id, 1200.0, 800.0, type)
            val wallRef = WallFaceReference(wall.id, end)
            val frame = OpeningFrameReference(opening.id, if (end == WallFaceSide.START) OpeningFrameSide.START else OpeningFrameSide.END)
            // Stored order can put the moving opening first; selection decides which object moves.
            val dimension = WallDimension("dim", frame, wallRef, Vec2(600.0, 500.0))
            val original = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(opening.id to opening),
                dimensions = mapOf(dimension.id to dimension))
            val editor = SketchEditor(original)
            val oriented = assertNotNull(WallDimensionGeometry.forMovingSelection(dimension, Selection.Opening(opening.id)))
            val oldValue = WallDimensionGeometry.layout(original, dimension)!!.valueMm
            assertTrue(editor.execute(DriveWallFaceDistanceCommand(oriented, oldValue + 125.125)))
            assertEquals(opening.offset + if (end == WallFaceSide.START) 125.125 else -125.125,
                editor.document.openings.getValue(opening.id).offset, .001)
            assertEquals(original.walls, editor.document.walls)
            assertEquals(original.dimensions, editor.document.dimensions)
            assertEquals(oldValue + 125.125, WallDimensionGeometry.layout(editor.document, dimension)!!.valueMm, .001)
            val project = Project(ProjectManifest("fixture", "Wall end fixture", 1L), editor.document)
            assertEquals(project, ProjectSerializer.decode(ProjectSerializer.encode(project)))
            val moved = editor.document
            assertFalse(editor.execute(DriveWallFaceDistanceCommand(oriented, 10000.0)))
            assertEquals(moved, editor.document)
            // Moving the host would carry its supposedly fixed opening too; reject atomically.
            assertNull(DriveWallDimensionCommand(dimension.id, oldValue + 250.0).apply(original))
            assertTrue(editor.undo())
            assertEquals(original, editor.document)
            assertTrue(editor.redo())
            assertEquals(moved, editor.document)
        }
    }
}
