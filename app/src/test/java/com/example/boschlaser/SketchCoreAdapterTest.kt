package com.example.boschlaser

import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.Segment2
import com.example.boschlaser.shared.model.WallAlignment
import com.example.boschlaser.shared.model.WallAnchor
import com.example.boschlaser.shared.command.ChangeWallLengthCommand
import com.example.boschlaser.shared.editor.SketchEditor
import org.junit.Assert.assertEquals
import org.junit.Test

class SketchCoreAdapterTest {
    @Test
    fun openingProjectionIsInvalidatedByHostLengthAndUndoRestoresLegacyValues() {
        val state = SketchState(
            walls = listOf(SketchWall("wall", SketchPoint(0f, 0f), SketchPoint(2000f, 0f))),
            openings = listOf(SketchOpening("door", SketchOpeningType.DOOR, "wall", .35f, 900f)),
        )
        val projection = SketchCoreProjection()
        val editor = SketchEditor(projection.restore(state))
        assertEquals(state, projection.state(editor.document))
        editor.execute(ChangeWallLengthCommand("wall", 3000.0, WallAnchor.START))
        assertEquals(700f / 3000f, projection.state(editor.document).openings.single().position, .0001f)
        editor.undo()
        assertEquals(state, projection.state(editor.document))
    }

    @Test
    fun legacySideControlLinesRoundTripWithoutMovingCenterOrOpening() {
        for (control in SketchWallControlLine.entries) {
            val wall = SketchWall("wall", SketchPoint(100f, 200f), SketchPoint(1300f, 1800f), 240f,
                controlLine = control)
            val opening = SketchOpening("door", SketchOpeningType.DOOR, wall.id, .35f, 500f,
                flipped = true, hingeFlipped = true)
            val state = SketchState(walls = listOf(wall), openings = listOf(opening))
            val document = state.toDocument()
            val restored = document.toSketchState()
            assertEquals(wall.start.x, restored.walls.single().start.x, .001f)
            assertEquals(wall.start.y, restored.walls.single().start.y, .001f)
            assertEquals(wall.end.x, restored.walls.single().end.x, .001f)
            assertEquals(wall.end.y, restored.walls.single().end.y, .001f)
            assertEquals(control, restored.walls.single().controlLine)
            assertEquals(700.0, document.openings.getValue("door").offset, .001)
            assertEquals(opening, restored.openings.single())
        }
    }
    @Test
    fun legacyInnerLineMeansBodyOnLeftOfReferenceLine() {
        val wall = SketchWall("wall", SketchPoint(0f, 0f), SketchPoint(2000f, 0f), 200f,
            controlLine = SketchWallControlLine.INNER)
        val shared = wall.toCoreWall()
        assertEquals(WallAlignment.LEFT, shared.alignment)
        assertEquals(-100.0, shared.start.y, .001)
        assertEquals(2000.0, Geometry.length(Segment2(shared.start, shared.end)), .001)
    }
    @Test
    fun roundTripPreservesColumnAngleAndBothDoorFlips() {
        val state = SketchState(
            walls = listOf(SketchWall("wall", SketchPoint(0f, 0f), SketchPoint(3000f, 0f))),
            columns = listOf(SketchColumn("column", SketchColumnType.RECTANGLE,
                SketchPoint(500f, 500f), 400f, 600f, 37f)),
            openings = listOf(SketchOpening("door", SketchOpeningType.DOOR, "wall", .5f,
                flipped = true, hingeFlipped = true)),
        )
        assertEquals(state, state.toDocument().toSketchState())
    }
    @Test
    fun orphanOpeningsAreDroppedAtCompatibilityBoundary() {
        val state = SketchState(openings = listOf(SketchOpening("door", SketchOpeningType.DOOR, "missing", .5f)))
        assertEquals(emptyMap<String, Any>(), state.toDocument().openings)
    }
}
