package com.example.boschlaser.shared.command

import com.example.boschlaser.shared.editor.*
import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.storage.*
import com.example.boschlaser.shared.tool.PointerContext
import kotlin.test.*

class WallLengthDimensionTest {
    private fun fixture() = Wall("wall", Vec2(0.0, 0.0), Vec2(3000.0, 0.0), 200.0)
    private fun context(point: Vec2) = PointerContext(point, 20.0, 20.0, 20.0, 20.0, 20.0, 20.0)
    private fun tap(editor: SketchEditor, point: Vec2) {
        editor.pointerDown(point, context(point))
        editor.pointerUp(point, context(point))
    }

    @Test fun sameWallEndsCanBePickedInEitherOrderAndDrivenWithStoredFirstEndFixed() {
        for (reverse in listOf(false, true)) {
            val wall = fixture()
            val original = SketchDocument(walls = mapOf(wall.id to wall))
            val editor = SketchEditor(original)
            editor.setTool(ToolType.DIMENSION)
            tap(editor, if (reverse) wall.end else wall.start)
            tap(editor, if (reverse) wall.start else wall.end)
            assertIs<SketchPreview.Dimension>(editor.state.preview)
            tap(editor, Vec2(1500.0, 600.0))
            val dimension = editor.document.dimensions.values.single()
            assertEquals(3000.0, WallDimensionGeometry.layout(editor.document, dimension)!!.valueMm, .001)
            val oriented = assertNotNull(WallDimensionGeometry.forMovingSelection(dimension, Selection.Wall(wall.id)))
            assertEquals(dimension, oriented)
            assertTrue(editor.execute(DriveWallFaceDistanceCommand(oriented, 3500.125)))
            val changed = editor.document.walls.getValue(wall.id)
            assertEquals(if (reverse) wall.end else wall.start, if (reverse) changed.end else changed.start)
            assertEquals(3500.125, Geometry.length(WallGeometry.referenceLine(changed)), .001)
            assertEquals(3500.125, WallDimensionGeometry.layout(editor.document, dimension)!!.valueMm, .001)
            val project = Project(ProjectManifest("fixture", "Wall length", 1L), editor.document)
            assertEquals(project, ProjectSerializer.decode(ProjectSerializer.encode(project)))
            assertTrue(editor.undo())
            assertEquals(wall, editor.document.walls.getValue(wall.id))
            assertTrue(editor.undo())
            assertEquals(original, editor.document)
            assertTrue(editor.redo())
            assertTrue(editor.redo())
            assertEquals(project.sketch, editor.document)
        }
    }

    @Test fun onlyOppositeEndSectionsOfSameWallAreAllowed() {
        for (first in WallFaceSide.entries) for (second in WallFaceSide.entries) {
            val a = WallFaceReference("wall", first)
            val b = WallFaceReference("wall", second)
            if (setOf(first, second) == setOf(WallFaceSide.START, WallFaceSide.END))
                assertNotNull(WallDimension("dim", a, b, Vec2(0.0, 500.0)))
            else assertFailsWith<IllegalArgumentException> { WallDimension("dim", a, b, Vec2(0.0, 500.0)) }
        }
    }

    @Test fun hostedOpeningAndSharedNodeFollowLengthChangeAndFailureIsAtomic() {
        val wall = fixture()
        val next = Wall("next", wall.end, Vec2(4000.0, 1000.0), 200.0)
        val opening = Opening("door", wall.id, 2500.0, 600.0, OpeningType.DOOR)
        val dimension = WallDimension("dim", WallFaceReference(wall.id, WallFaceSide.START),
            WallFaceReference(wall.id, WallFaceSide.END), Vec2(1500.0, 600.0))
        val initial = SketchDocument(walls = mapOf(wall.id to wall, next.id to next),
            openings = mapOf(opening.id to opening), dimensions = mapOf(dimension.id to dimension))
        val editor = SketchEditor(initial)
        assertFalse(editor.execute(DriveWallDimensionCommand(dimension.id, 2000.0)))
        assertEquals(initial, editor.document)
        assertFalse(editor.canUndo)
        assertTrue(editor.execute(DriveWallDimensionCommand(dimension.id, 3500.0)))
        assertEquals(editor.document.walls.getValue(wall.id).end, editor.document.walls.getValue(next.id).start)
        assertEquals(opening, editor.document.openings.getValue(opening.id))
        val changed = editor.document
        assertFalse(editor.execute(DriveWallDimensionCommand(dimension.id, 50.0)))
        assertEquals(changed, editor.document)
        assertTrue(editor.undo())
        assertEquals(initial, editor.document)
    }

    @Test fun rotatedWallEndFixedPreservesOpeningDistanceAndCannotShortenPastTAttachment() {
        val wall = fixture().copy(end = Vec2(1800.0, 2400.0), alignment = WallAlignment.LEFT)
        val opening = Opening("window", wall.id, 2000.0, 500.0, OpeningType.WINDOW)
        val dimension = WallDimension("dim", WallFaceReference(wall.id, WallFaceSide.END),
            WallFaceReference(wall.id, WallFaceSide.START), Vec2(-600.0, 600.0))
        val initial = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(opening.id to opening),
            dimensions = mapOf(dimension.id to dimension))
        val result = assertNotNull(DriveWallDimensionCommand(dimension.id, 3500.0).apply(initial))
        assertEquals(wall.end, result.walls.getValue(wall.id).end)
        assertEquals(2500.0, result.openings.getValue(opening.id).offset, .001)
        assertEquals(3500.0, WallDimensionGeometry.layout(result, dimension)!!.valueMm, .001)
        val plain = fixture()
        val stem = Wall("stem", Vec2(2500.0, 0.0), Vec2(2500.0, 1000.0), 200.0)
        val forward = dimension.copy(first = dimension.second, second = dimension.first)
        val tee = SketchDocument(walls = mapOf(plain.id to plain, stem.id to stem), dimensions = mapOf(forward.id to forward))
        assertNull(DriveWallDimensionCommand(forward.id, 2000.0).apply(tee))
        assertNotNull(DriveWallDimensionCommand(forward.id, 2800.0).apply(tee))
    }

    @Test fun existingDimensionTicksCanCreateSameWallEndDimension() {
        val wall = fixture()
        val dimension = WallDimension("existing", WallFaceReference(wall.id, WallFaceSide.START),
            WallFaceReference(wall.id, WallFaceSide.END), Vec2(1500.0, 1200.0))
        val initial = SketchDocument(walls = mapOf(wall.id to wall), dimensions = mapOf(dimension.id to dimension))
        val editor = SketchEditor(initial)
        editor.setTool(ToolType.DIMENSION)
        tap(editor, Vec2(0.0, 1200.0))
        tap(editor, Vec2(3000.0, 1200.0))
        assertIs<SketchPreview.Dimension>(editor.state.preview)
        tap(editor, Vec2(1500.0, 1800.0))
        val added = editor.document.dimensions.values.single { it.id != dimension.id }
        assertEquals(dimension.first, added.first)
        assertEquals(dimension.second, added.second)
        assertTrue(editor.execute(DriveWallDimensionCommand(added.id, 4000.0)))
        assertEquals(4000.0, WallDimensionGeometry.layout(editor.document, dimension)!!.valueMm, .001)
    }
}
