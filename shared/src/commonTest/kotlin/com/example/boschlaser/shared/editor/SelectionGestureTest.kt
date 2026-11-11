package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.command.DeleteColumnCommand
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.RectColumn
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall
import com.example.boschlaser.shared.tool.PointerContext
import kotlin.test.*

class SelectionGestureTest {
    @Test fun multiWayEndpointModeCanPreserveTheWholeConnectionOrDetachOnlyTheSelectedWall() {
        val walls = listOf(Wall("a", Vec2(0.0, 0.0), Vec2(2000.0, 0.0)),
            Wall("b", Vec2(0.0, 0.0), Vec2(0.0, 2000.0)),
            Wall("c", Vec2(0.0, 0.0), Vec2(-2000.0, 0.0)))
        val initial = SketchDocument(walls = walls.associateBy { it.id })
        for (count in 2..3) for (detach in listOf(true, false)) {
            val initial = initial.copy(walls = walls.take(count).associateBy { it.id })
            val editor = SketchEditor(initial)
            editor.detachMultiWayEndpoints = detach
            editor.select(Selection.Wall("a"))
            editor.pointerDown(Vec2(0.0, 0.0), context(Vec2(0.0, 0.0)))
            editor.pointerMove(Vec2(100.0, 100.0), context(Vec2(100.0, 100.0)))
            assertTrue(editor.pointerUp(Vec2(100.0, 100.0), context(Vec2(100.0, 100.0))).documentChanged)
            assertEquals(Vec2(100.0, 100.0), editor.document.walls.getValue("a").start)
            assertEquals(if (detach) Vec2(0.0, 0.0) else Vec2(100.0, 100.0), editor.document.walls.getValue("b").start)
            if (count == 3) assertEquals(if (detach) Vec2(0.0, 0.0) else Vec2(100.0, 100.0), editor.document.walls.getValue("c").start)
            assertTrue(editor.undo())
            assertEquals(initial, editor.document)
            assertFalse(editor.canUndo)
        }
    }

    @Test fun slowWallDragAccumulatesMotionAndKeepsTheFullDisplacement() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
        val editor = SketchEditor(SketchDocument(walls = mapOf(wall.id to wall)))
        val start = Vec2(1000.0, 0.0)
        editor.select(Selection.Wall(wall.id))
        editor.pointerDown(start, context(start))
        for (step in 1..8) {
            val point = start + Vec2(0.0, step.toDouble())
            editor.pointerMove(point, context(point))
        }
        assertTrue(editor.state.dragging)
        assertEquals(Vec2(0.0, 8.0), editor.renderDocument.walls.getValue(wall.id).start)
        assertEquals(wall, editor.document.walls.getValue(wall.id))
        val end = start + Vec2(0.0, 8.0)
        assertTrue(editor.pointerUp(end, context(end)).documentChanged)
        assertTrue(editor.undo())
        assertEquals(wall, editor.document.walls.getValue(wall.id))
        assertFalse(editor.canUndo)
    }

    @Test fun motionWithinDragThresholdDoesNotEditWall() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
        val editor = SketchEditor(SketchDocument(walls = mapOf(wall.id to wall)))
        val start = Vec2(1000.0, 0.0)
        editor.select(Selection.Wall(wall.id))
        editor.pointerDown(start, context(start))
        listOf(1.0, -1.0, 2.0, 0.0).forEach {
            val point = start + Vec2(0.0, it)
            editor.pointerMove(point, context(point))
        }
        assertFalse(editor.pointerUp(start, context(start)).documentChanged)
        assertEquals(wall, editor.document.walls.getValue(wall.id))
        assertFalse(editor.canUndo)
    }
    @Test fun slowColumnDragEscapesCornerSnapAndMatchesOneMove() {
        val horizontal = Wall("horizontal", Vec2(0.0, 0.0), Vec2(2000.0, 0.0), 200.0)
        val vertical = Wall("vertical", Vec2(0.0, 0.0), Vec2(0.0, 2000.0), 200.0)
        val column = RectColumn("column", Vec2(200.0, 200.0), 200.0, 200.0)
        val initial = SketchDocument(walls = mapOf(horizontal.id to horizontal, vertical.id to vertical),
            columns = mapOf(column.id to column))
        fun moveContext(point: Vec2) = context(point).copy(columnSnapTolerance = 30.0)
        // Grab away from the center: movement must preserve the original finger offset.
        val start = column.center + Vec2(20.0, 10.0)
        val editor = SketchEditor(initial)
        editor.select(Selection.Column(column.id))
        editor.pointerDown(start, moveContext(start))
        assertEquals(SelectionGestureTarget.Column(column.id), editor.state.selectionGesture?.target)
        for (step in 1..10) {
            val point = start + Vec2(step * 5.0, step * 5.0)
            editor.pointerMove(point, moveContext(point))
            if (step == 2) assertEquals(column.center, editor.renderDocument.columns.getValue(column.id).center)
        }
        val end = start + Vec2(50.0, 50.0)
        assertEquals(Vec2(250.0, 250.0), editor.renderDocument.columns.getValue(column.id).center)
        val singleMove = SketchEditor(initial)
        singleMove.select(Selection.Column(column.id))
        singleMove.pointerDown(start, moveContext(start))
        singleMove.pointerMove(end, moveContext(end))
        assertEquals(singleMove.renderDocument, editor.renderDocument)
        assertEquals(initial, editor.document)
        assertTrue(editor.pointerUp(end, moveContext(end)).documentChanged)
        assertTrue(editor.undo())
        assertEquals(initial, editor.document)
        assertFalse(editor.canUndo)
        singleMove.cancelPointer()
        assertEquals(initial, singleMove.renderDocument)
        assertFalse(singleMove.canUndo)
    }

    private fun context(point: Vec2, rotation: String? = null) =
        PointerContext(point, 10.0, 10.0, 10.0, 10.0, 10.0, 0.0, rotationHandleColumnId = rotation)
    private fun column(id: String, x: Double) = RectColumn(id, Vec2(x, 300.123456789), 100.0, 100.0)

    @Test fun columnDragKeepsStableTargetAndDoublePointerStateDespiteSelectionChange() {
        val first = column("first", 300.123456789)
        val second = column("second", 1000.0)
        val editor = SketchEditor(SketchDocument(columns = mapOf(first.id to first, second.id to second)))
        editor.select(Selection.Column(first.id))
        editor.pointerDown(first.center, context(first.center))
        assertEquals(SelectionGestureTarget.Column(first.id), editor.state.selectionGesture?.target)
        assertEquals(first.center, editor.state.selectionGesture?.lastWorld)
        editor.select(Selection.Column(second.id))
        val moved = first.center + Vec2(30.123456789, 40.987654321)
        editor.pointerMove(moved, context(moved))
        assertEquals(moved, editor.renderDocument.columns.getValue(first.id).center)
        assertEquals(second, editor.renderDocument.columns.getValue(second.id))
        assertEquals(moved, editor.state.magnifierTarget)
        assertTrue(editor.state.dragging)
        assertEquals(moved, editor.state.selectionGesture?.lastScreen)
        assertTrue(editor.pointerUp(moved, context(moved)).documentChanged)
        assertNull(editor.state.selectionGesture)
        assertNull(editor.state.magnifierTarget)
        assertFalse(editor.state.dragging)
        assertTrue(editor.undo())
        assertEquals(first, editor.document.columns.getValue(first.id))
    }

    @Test fun cancellingPreviewRestoresDocumentAndClearsAllSelectionGesturePresentation() {
        val first = column("first", 300.0)
        val initial = SketchDocument(columns = mapOf(first.id to first))
        val editor = SketchEditor(initial)
        editor.select(Selection.Column(first.id))
        editor.pointerDown(first.center, context(first.center))
        val point = first.center + Vec2(40.0, 50.0)
        editor.pointerMove(point, context(point))
        assertNotEquals(initial, editor.renderDocument)
        editor.cancelPointer()
        assertEquals(initial, editor.renderDocument)
        assertNull(editor.state.selectionGesture)
        assertNull(editor.state.magnifierTarget)
        assertNull(editor.state.snap)
        assertFalse(editor.state.dragging)
        assertFalse(editor.canUndo)
        assertEquals(Selection.Column(first.id), editor.state.selection)
    }

    @Test fun rotationTargetIsExplicitAndChangingToolsClearsIt() {
        val first = column("first", 300.0)
        val editor = SketchEditor(SketchDocument(columns = mapOf(first.id to first)))
        val handle = first.center + Vec2(0.0, -100.0)
        editor.select(Selection.Column(first.id))
        editor.pointerDown(handle, context(handle, first.id))
        assertEquals(SelectionGestureTarget.ColumnRotation(first.id), editor.state.selectionGesture?.target)
        val point = first.center + Vec2(100.0, 0.0)
        editor.pointerMove(point, context(point))
        assertEquals(90.0, (editor.renderDocument.columns.getValue(first.id) as RectColumn).rotationDegrees)
        assertNull(editor.state.magnifierTarget)
        editor.setTool(ToolType.WALL)
        assertNull(editor.state.selectionGesture)
        assertFalse(editor.state.dragging)
        assertEquals(first, editor.document.columns.getValue(first.id))
    }

    @Test fun endpointAndWallBodyTargetsAreDistinct() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(2000.0, 0.0), 200.0)
        val editor = SketchEditor(SketchDocument(walls = mapOf(wall.id to wall)))
        editor.select(Selection.Wall(wall.id))
        editor.pointerDown(wall.start, context(wall.start))
        assertEquals(SelectionGestureTarget.WallEndpoint(wall.id, true), editor.state.selectionGesture?.target)
        editor.cancelPointer()
        editor.pointerDown(wall.end, context(wall.end))
        assertEquals(SelectionGestureTarget.WallEndpoint(wall.id, false), editor.state.selectionGesture?.target)
        editor.cancelPointer()
        val midpoint = Vec2(1000.0, 0.0)
        editor.pointerDown(midpoint, context(midpoint))
        assertEquals(SelectionGestureTarget.WallBody(wall.id), editor.state.selectionGesture?.target)
    }

    @Test fun panUsesScreenDeltaAndNeverCreatesHistoryOrMagnifier() {
        val editor = SketchEditor()
        val start = Vec2(100.0, 200.0)
        editor.pointerDown(start, context(start))
        assertEquals(SelectionGestureTarget.Pan, editor.state.selectionGesture?.target)
        val screenPoint = Vec2(125.0, 240.0)
        val result = editor.pointerMove(Vec2(150.0, 280.0), context(screenPoint).copy(viewportScale = 0.5))
        assertEquals(Vec2(25.0, 40.0), result.panBy)
        assertEquals(Vec2(100.0, 200.0), editor.state.selectionGesture?.lastWorld)
        assertEquals(screenPoint, editor.state.selectionGesture?.lastScreen)
        assertNull(editor.state.magnifierTarget)
        assertFalse(editor.pointerUp(screenPoint, context(screenPoint)).documentChanged)
        assertNull(editor.state.selectionGesture)
        assertFalse(editor.canUndo)
    }

    @Test fun commitAndCancelEditClearGestureWithoutASecondPointerRelease() {
        val first = column("first", 300.0)
        val editor = SketchEditor(SketchDocument(columns = mapOf(first.id to first)))
        editor.select(Selection.Column(first.id))
        editor.pointerDown(first.center, context(first.center))
        val point = first.center + Vec2(20.0, 30.0)
        editor.pointerMove(point, context(point))
        assertTrue(editor.commitEdit())
        assertNull(editor.state.selectionGesture)
        assertFalse(editor.state.dragging)
        editor.pointerDown(point, context(point))
        editor.cancelEdit()
        assertNull(editor.state.selectionGesture)
        assertFalse(editor.state.dragging)
    }

    @Test fun permanentCommandCancelsPreviewAndCannotLeaveADanglingGestureTarget() {
        val first = column("first", 300.0)
        val editor = SketchEditor(SketchDocument(columns = mapOf(first.id to first)))
        editor.select(Selection.Column(first.id))
        editor.pointerDown(first.center, context(first.center))
        val point = first.center + Vec2(20.0, 30.0)
        editor.pointerMove(point, context(point))
        assertTrue(editor.execute(DeleteColumnCommand(first.id)))
        assertNull(editor.state.selectionGesture)
        assertNull(editor.state.selection)
        assertNull(editor.state.magnifierTarget)
        editor.pointerMove(point, context(point))
        assertTrue(editor.document.columns.isEmpty())
        assertTrue(editor.undo())
        assertEquals(first, editor.document.columns.getValue(first.id))
    }

    @Test fun moveWithoutPointerDownCannotEditTheCurrentSelection() {
        val first = column("first", 300.0)
        val editor = SketchEditor(SketchDocument(columns = mapOf(first.id to first)))
        editor.select(Selection.Column(first.id))
        val point = first.center + Vec2(20.0, 30.0)
        editor.pointerMove(point, context(point))
        assertEquals(first, editor.renderDocument.columns.getValue(first.id))
        assertFalse(editor.state.dragging)
        assertNull(editor.state.selectionGesture)
    }
}
