package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.command.AddWallCommand
import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.tool.PointerContext
import com.example.boschlaser.shared.tool.ToolFailure
import kotlin.test.*

class MeasureAndSplitToolTest {
    private fun context(point: Vec2) = PointerContext(point, 24.0, 20.0, 10.0, 14.0, 20.0, 12.0)
    private fun tap(editor: SketchEditor, point: Vec2): com.example.boschlaser.shared.tool.ToolUpdate {
        editor.pointerDown(point, context(point))
        return editor.pointerUp(point, context(point))
    }
    @Test fun bothMeasurementPointsFollowMagnifierAndAreConfirmedOnlyOnRelease() {
        val editor = SketchEditor()
        editor.setTool(ToolType.MEASURE)
        val down = Vec2(100.0, 200.0)
        val start = Vec2(300.0, 400.0)
        editor.pointerDown(down, context(down))
        assertNull(editor.state.preview)
        assertEquals(down, editor.state.magnifierTarget)
        editor.pointerMove(start, context(start))
        assertNull(editor.state.preview)
        assertEquals(start, editor.state.magnifierTarget)
        editor.pointerUp(start, context(start))
        val first = assertIs<SketchPreview.Measurement>(editor.state.preview)
        assertEquals(start, first.line.start)
        assertEquals(start, first.line.end)
        assertFalse(first.complete)
        assertNull(editor.state.magnifierTarget)
        val end = Vec2(1200.0, 800.0)
        editor.pointerDown(down, context(down))
        editor.pointerMove(end, context(end))
        val second = assertIs<SketchPreview.Measurement>(editor.state.preview)
        assertEquals(start, second.line.start)
        assertEquals(end, second.line.end)
        assertFalse(second.complete)
        assertEquals(end, editor.state.magnifierTarget)
        val release = end + Vec2(10.0, 20.0)
        editor.pointerUp(release, context(release))
        val completed = assertIs<SketchPreview.Measurement>(editor.state.preview)
        assertTrue(completed.complete)
        assertEquals(release, completed.line.end)
        assertNull(editor.state.magnifierTarget)
        assertEquals(SketchDocument(), editor.document)
        assertFalse(editor.canUndo)
    }

    @Test fun firstPointMagnifierUsesSnapAndCancelOrZoomDoesNotLeaveAnAnchor() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
        val editor = SketchEditor(SketchDocument(walls = mapOf(wall.id to wall)))
        editor.setTool(ToolType.MEASURE)
        val point = Vec2(10.0, 10.0)
        editor.pointerDown(point, context(point))
        assertEquals(wall.start, editor.state.magnifierTarget)
        assertEquals(wall.start, editor.state.snap?.point)
        editor.interruptPointerForViewportChange()
        assertNull(editor.state.preview)
        assertNull(editor.state.magnifierTarget)
        assertNull(editor.state.snap)
        editor.pointerDown(point, context(point))
        editor.cancelPointer()
        assertNull(editor.state.preview)
        assertNull(editor.state.magnifierTarget)
        assertFalse(editor.canUndo)
    }
    @Test fun twoTapMeasurementKeepsDoublePrecisionAndNeverChangesHistory() {
        val editor = SketchEditor()
        editor.setTool(ToolType.MEASURE)
        val start = Vec2(16777216.123456, 30.0)
        val end = start + Vec2(3000.125, 4000.5)
        assertFalse(tap(editor, start).documentChanged)
        editor.pointerMove(end, context(end))
        assertFalse((editor.state.preview as SketchPreview.Measurement).complete)
        assertFalse(tap(editor, end).documentChanged)
        val result = editor.state.preview as SketchPreview.Measurement
        assertTrue(result.complete)
        assertEquals(start, result.line.start)
        assertEquals(Geometry.distance(start, end), Geometry.length(result.line), 1e-9)
        assertEquals(SketchDocument(), editor.document)
        assertFalse(editor.canUndo)
        assertNull(editor.state.magnifierTarget)
        tap(editor, Vec2(50.0, 60.0))
        val next = editor.state.preview as SketchPreview.Measurement
        assertFalse(next.complete)
        assertEquals(Vec2(50.0, 60.0), next.line.start)
    }
    @Test fun measurementSnapsEndpointsAndGridAndCancelOrToolChangeClearsIt() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
        val editor = SketchEditor(SketchDocument(walls = mapOf(wall.id to wall)))
        editor.setTool(ToolType.MEASURE)
        tap(editor, Vec2(10.0, 10.0))
        val end = Vec2(980.0, 1020.0)
        val context = context(end).copy(gridStep = 100.0)
        editor.pointerDown(end, context)
        editor.pointerUp(end, context)
        val line = (editor.state.preview as SketchPreview.Measurement).line
        assertEquals(wall.start, line.start)
        assertEquals(Vec2(1000.0, 1000.0), line.end)
        editor.cancelPointer()
        assertNull(editor.state.preview)
        tap(editor, wall.start)
        editor.setTool(ToolType.SPLIT)
        assertNull(editor.state.preview)
        assertFalse(editor.canUndo)
    }
    @Test fun measurementUndoClearsOnlyMeasurementAndPreservesExistingDocumentHistory() {
        val editor = SketchEditor()
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
        assertTrue(editor.execute(AddWallCommand(wall)))
        editor.setTool(ToolType.MEASURE)
        tap(editor, wall.start)
        tap(editor, wall.end)
        val before = editor.document
        assertTrue(editor.undoPlacement())
        assertNull(editor.state.preview)
        assertEquals(before, editor.document)
        assertTrue(editor.canUndo)
        assertFalse(editor.undoPlacement())
        assertEquals(before, editor.document)
    }
    @Test fun splitProjectsPhysicalBodyClickToNonCentralLocatorAndRehostsOpeningAtomically() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(4000.0, 0.0), 240.0, WallAlignment.LEFT,
            measuredLength = 4000.125)
        val opening = Opening("door", wall.id, 3000.0, 900.0, OpeningType.DOOR)
        val initial = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(opening.id to opening))
        val editor = SketchEditor(initial, idFactory = { "second" })
        editor.setTool(ToolType.SPLIT)
        assertTrue(tap(editor, Vec2(2000.125, 180.0)).documentChanged)
        assertEquals(listOf("wall", "second"), editor.document.walls.keys.toList())
        assertEquals(2000.125, editor.document.walls.getValue("wall").end.x, 1e-9)
        assertEquals(0.0, editor.document.walls.getValue("wall").end.y, 1e-9)
        assertEquals(editor.document.walls.getValue("wall").end, editor.document.walls.getValue("second").start)
        assertEquals("second", editor.document.openings.getValue("door").hostWallId)
        assertEquals(999.875, editor.document.openings.getValue("door").offset, 1e-9)
        assertNull(editor.document.walls.getValue("wall").measuredLength)
        assertEquals(Selection.Wall("second"), editor.state.selection)
        val split = editor.document
        assertTrue(editor.undo())
        assertEquals(initial, editor.document)
        assertFalse(editor.canUndo)
        assertTrue(editor.redo())
        assertEquals(split, editor.document)
    }
    @Test fun splitRejectsOpeningAndShortSegmentWithoutAnyDocumentOrHistoryChange() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(4000.0, 0.0))
        val opening = Opening("door", wall.id, 2000.0, 900.0, OpeningType.DOOR)
        val initial = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(opening.id to opening))
        val editor = SketchEditor(initial, idFactory = { "second" })
        editor.setTool(ToolType.SPLIT)
        for (point in listOf(Vec2(2000.0, 0.0), Vec2(99.0, 0.0), Vec2(3901.0, 0.0))) {
            val update = tap(editor, point)
            assertEquals(ToolFailure.INVALID_SPLIT, update.failure)
            assertFalse(update.documentChanged)
            assertEquals(initial, editor.document)
            assertFalse(editor.canUndo)
            assertFalse(editor.canRedo)
        }
        assertNull(tap(editor, Vec2(2000.0, 1000.0)).failure)
        assertEquals(initial, editor.document)
    }
    @Test fun splitCancelAndMoveDoNotCommitAndExactlyHundredMillimetersIsAllowed() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(1000.0, 0.0))
        val initial = SketchDocument(walls = mapOf(wall.id to wall))
        val editor = SketchEditor(initial, idFactory = { "second" })
        editor.setTool(ToolType.SPLIT)
        val point = Vec2(100.0, 0.0)
        editor.pointerDown(point, context(point))
        editor.pointerMove(point, context(point))
        editor.cancelPointer()
        assertEquals(initial, editor.document)
        assertFalse(editor.canUndo)
        assertTrue(tap(editor, point).documentChanged)
        assertEquals(100.0, Geometry.distance(editor.document.walls.getValue("wall").start,
            editor.document.walls.getValue("wall").end))
    }
    @Test fun splitFailurePreservesSelectionAndRedoFromPreviousCommand() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
        val original = SketchDocument(walls = mapOf(wall.id to wall))
        val editor = SketchEditor(original, idFactory = { "second" })
        val another = wall.copy(id = "another", start = Vec2(0.0, 2000.0), end = Vec2(2000.0, 2000.0))
        assertTrue(editor.execute(AddWallCommand(another)))
        assertTrue(editor.undo())
        editor.setTool(ToolType.SPLIT)
        editor.select(Selection.Wall(wall.id))
        assertEquals(ToolFailure.INVALID_SPLIT, tap(editor, Vec2(50.0, 0.0)).failure)
        assertEquals(original, editor.document)
        assertEquals(Selection.Wall(wall.id), editor.state.selection)
        assertFalse(editor.canUndo)
        assertTrue(editor.canRedo)
        assertTrue(editor.redo())
        assertEquals(another, editor.document.walls[another.id])
    }
    @Test fun measurementCanSnapMidpointsAndTrueIntersectionsWithoutConnectingWalls() {
        val horizontal = Wall("horizontal", Vec2(0.0, 0.0), Vec2(4000.0, 0.0))
        val vertical = Wall("vertical", Vec2(1000.0, -2000.0), Vec2(1000.0, 2000.0))
        val original = SketchDocument(walls = listOf(horizontal, vertical).associateBy { it.id })
        val editor = SketchEditor(original)
        editor.setTool(ToolType.MEASURE)
        tap(editor, Vec2(2005.0, 5.0))
        tap(editor, Vec2(1005.0, 5.0))
        val line = (editor.state.preview as SketchPreview.Measurement).line
        assertEquals(Vec2(2000.0, 0.0), line.start)
        assertEquals(Vec2(1000.0, 0.0), line.end)
        assertEquals(original, editor.document)
        assertNull(com.example.boschlaser.shared.topology.SketchTopology(editor.document).junctionAt(line.end))
        assertFalse(editor.canUndo)
    }

}
