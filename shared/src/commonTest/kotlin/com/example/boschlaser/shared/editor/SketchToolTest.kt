package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.tool.PointerContext
import kotlin.test.*

class SketchToolTest {
    private fun context(point: Vec2) = PointerContext(point, 24.0, 20.0, 10.0, 14.0, 20.0, 12.0)
    private fun tap(editor: SketchEditor, point: Vec2) {
        editor.pointerDown(point, context(point)); editor.pointerUp(point, context(point))
    }
    @Test fun wallChainSeparatesAnchorPreviewAndOneUndoPerSegment() {
        var id = 0
        val editor = SketchEditor(idFactory = { (++id).toString() })
        editor.setTool(ToolType.WALL)
        tap(editor, Vec2(0.0, 0.0))
        assertFalse(editor.canUndo)
        val end = Vec2(2000.0, 0.0)
        editor.pointerDown(end, context(end))
        assertTrue(editor.document.walls.isEmpty())
        editor.cancelPointer()
        assertEquals(Vec2(0.0, 0.0), editor.state.wallAnchor)
        tap(editor, end)
        assertEquals(1, editor.document.walls.size)
        assertEquals(end, editor.state.wallAnchor)
        assertTrue(editor.undoPlacement())
        assertTrue(editor.document.walls.isEmpty())
        assertEquals(Vec2(0.0, 0.0), editor.state.wallAnchor)
        assertTrue(editor.undoPlacement())
        assertNull(editor.state.wallAnchor)
    }
    @Test fun cancellingFirstWallAnchorAndColumnPreviewCreatesNoHistory() {
        val editor = SketchEditor()
        editor.setTool(ToolType.WALL)
        editor.pointerDown(Vec2(500.0, 500.0), context(Vec2(500.0, 500.0)))
        editor.cancelPointer()
        assertNull(editor.state.wallAnchor)
        editor.setTool(ToolType.RECT_COLUMN)
        editor.pointerDown(Vec2(500.0, 500.0), context(Vec2(500.0, 500.0)))
        assertNotNull(editor.state.preview)
        assertTrue(editor.document.columns.isEmpty())
        editor.cancelPointer()
        assertNull(editor.state.preview)
        assertFalse(editor.canUndo)
    }
    @Test fun openingsClampToHostAndRejectOversizedPlacement() {
        val wall = Wall("host", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
        val editor = SketchEditor(SketchDocument(walls = mapOf(wall.id to wall)))
        editor.setTool(ToolType.DOOR)
        tap(editor, Vec2(-100.0, 0.0))
        assertEquals(450.0, editor.document.openings.values.single().offset)
        editor.placement = editor.placement.copy(windowWidth = 2000.0)
        editor.setTool(ToolType.WINDOW)
        tap(editor, Vec2(1000.0, 0.0))
        assertEquals(1, editor.document.openings.size)
    }
    @Test fun selectionDragIsAtomicAndCancelRestoresDocument() {
        val column = RectColumn("column", Vec2(1000.0, 1000.0), 400.0, 400.0)
        val initial = SketchDocument(columns = mapOf(column.id to column))
        val editor = SketchEditor(initial)
        val start = column.center
        val end = Vec2(1500.0, 1300.0)
        editor.pointerDown(start, context(start))
        editor.pointerMove(end, context(end))
        assertEquals(initial, editor.document)
        assertEquals(end, editor.renderDocument.columns.getValue(column.id).center)
        editor.cancelPointer()
        assertEquals(initial, editor.renderDocument)
        assertFalse(editor.canUndo)
        editor.pointerDown(start, context(start))
        editor.pointerMove(end, context(end))
        assertTrue(editor.pointerUp(end, context(end)).documentChanged)
        assertTrue(editor.undo())
        assertEquals(initial, editor.document)
        assertFalse(editor.canUndo)
    }
    @Test fun subThresholdMoveDoesNotChangeSelectionOrHistory() {
        val column = RectColumn("column", Vec2(1000.0, 1000.0), 400.0, 400.0)
        val initial = SketchDocument(columns = mapOf(column.id to column))
        val editor = SketchEditor(initial)
        editor.pointerDown(column.center, context(column.center))
        val end = column.center + Vec2(1.0, 0.0)
        editor.pointerMove(end, context(end))
        assertFalse(editor.pointerUp(end, context(end)).documentChanged)
        assertEquals(initial, editor.document)
    }
    @Test fun panReturnsScreenDeltaWithoutMutatingDocument() {
        val editor = SketchEditor()
        val start = Vec2(100.0, 100.0)
        editor.pointerDown(start, context(start).copy(viewportScale = 0.5))
        val first = editor.pointerMove(Vec2(120.0, 100.0), context(Vec2(110.0, 100.0)).copy(viewportScale = 0.5))
        assertEquals(Vec2(10.0, 0.0), first.panBy)
        val second = editor.pointerMove(Vec2(120.0, 100.0), context(Vec2(120.0, 100.0)).copy(viewportScale = 0.5))
        assertEquals(Vec2(10.0, 0.0), second.panBy)
        assertFalse(editor.pointerUp(start, context(start)).documentChanged)
        assertFalse(editor.canUndo)
    }
    @Test fun selectedWallEndpointWinsOverTopmostWallAndFollowsConnectedEndpoint() {
        val first = Wall("first", Vec2(0.0, 0.0), Vec2(1000.0, 0.0))
        val second = Wall("second", Vec2(1000.0, 0.0), Vec2(1000.0, 1000.0))
        val editor = SketchEditor(SketchDocument(walls = linkedMapOf(first.id to first, second.id to second)))
        editor.select(Selection.Wall(first.id))
        editor.pointerDown(first.end, context(first.end))
        assertEquals(Selection.Wall(first.id), editor.state.selection)
        val end = Vec2(1300.0, 100.0)
        editor.pointerMove(end, context(end))
        editor.pointerUp(end, context(end))
        assertEquals(end, editor.document.walls.getValue(first.id).end)
        assertEquals(end, editor.document.walls.getValue(second.id).start)
        assertTrue(editor.undo())
        assertEquals(first.end, editor.document.walls.getValue(second.id).start)
    }
    @Test fun rotationHandleUsesFifteenDegreeStepsAndOneCommit() {
        val column = RectColumn("column", Vec2(1000.0, 1000.0), 400.0, 400.0)
        val editor = SketchEditor(SketchDocument(columns = mapOf(column.id to column)))
        val start = Vec2(1000.0, 700.0)
        editor.pointerDown(start, context(start).copy(rotationHandleColumnId = column.id))
        val end = Vec2(1300.0, 1000.0)
        editor.pointerMove(end, context(end))
        assertEquals(0.0, (editor.document.columns.getValue(column.id) as RectColumn).rotationDegrees)
        assertNull(editor.state.magnifierTarget)
        editor.pointerUp(end, context(end))
        assertEquals(90.0, (editor.document.columns.getValue(column.id) as RectColumn).rotationDegrees)
        assertTrue(editor.undo())
        assertFalse(editor.canUndo)
    }
}
