package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.tool.PointerContext
import kotlin.test.*

class SelectionNavigationTest {
    private val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(4000.0, 0.0))
    private val column = RectColumn("column", Vec2(6000.0, 1000.0), 400.0, 400.0)
    private val opening = Opening("door", wall.id, 2000.0, 800.0, OpeningType.DOOR, doorStyle = DoorStyle.OPENING)
    private val board = Board("board", listOf(Vec2(8000.0, 0.0), Vec2(10000.0, 0.0),
        Vec2(10000.0, 2000.0), Vec2(8000.0, 2000.0)))
    private val initial = SketchDocument(walls = mapOf(wall.id to wall), columns = mapOf(column.id to column),
        openings = mapOf(opening.id to opening), boards = mapOf(board.id to board))
    private fun context(point: Vec2) = PointerContext(point, 10.0, 10.0, 10.0, 10.0, 10.0, 0.0, dragThreshold = 8.0)
    private fun targets() = listOf(Selection.Wall(wall.id) to Vec2(500.0, 0.0),
        Selection.Wall(wall.id) to wall.start, Selection.Column(column.id) to column.center,
        Selection.Opening(opening.id) to Vec2(2000.0, 0.0), Selection.Board(board.id) to Vec2(9000.0, 1000.0))

    @Test fun firstDragPansEveryUnselectedEntityWithoutChangingSelectionOrHistory() {
        for ((_, start) in targets()) for (previous in listOf(null, Selection.Column("other"))) {
            val other = RectColumn("other", Vec2(-5000.0, 1000.0), 400.0, 400.0)
            val document = initial.copy(columns = initial.columns + (other.id to other))
            val editor = SketchEditor(document)
            editor.select(previous)
            editor.pointerDown(start, context(start))
            assertEquals(previous, editor.state.selection)
            assertEquals(SelectionGestureTarget.Pan, editor.state.selectionGesture?.target)
            val end = start + Vec2(40.0, 50.0)
            assertEquals(Vec2(40.0, 50.0), editor.pointerMove(end, context(end)).panBy)
            assertFalse(editor.pointerUp(end, context(end)).documentChanged)
            assertEquals(previous, editor.state.selection)
            assertEquals(document, editor.renderDocument)
            assertNull(editor.state.magnifierTarget)
            assertFalse(editor.canUndo)
        }
    }

    @Test fun tapSelectsOnReleaseAndNextDragEdits() {
        for ((selection, start) in targets()) {
            val editor = SketchEditor(initial)
            editor.pointerDown(start, context(start))
            assertNull(editor.state.selection)
            editor.pointerUp(start, context(start))
            assertEquals(selection, editor.state.selection)
            assertFalse(editor.canUndo)
            editor.pointerDown(start, context(start))
            assertNotEquals(SelectionGestureTarget.Pan, editor.state.selectionGesture?.target)
            val end = start + Vec2(100.0, 100.0)
            editor.pointerMove(end, context(end))
            assertTrue(editor.pointerUp(end, context(end)).documentChanged)
            assertTrue(editor.undo())
            assertEquals(initial, editor.document)
            assertFalse(editor.canUndo)
        }
    }

    @Test fun panToolAlwaysNavigatesEvenOnSelectedControlPoints() {
        for ((selection, start) in targets()) {
            val editor = SketchEditor(initial)
            editor.setTool(ToolType.PAN)
            editor.select(selection)
            editor.pointerDown(start, context(start))
            assertEquals(SelectionGestureTarget.Pan, editor.state.selectionGesture?.target)
            val end = start + Vec2(100.0, 100.0)
            assertEquals(Vec2(100.0, 100.0), editor.pointerMove(end, context(end)).panBy)
            editor.pointerUp(end, context(end))
            assertEquals(initial, editor.document)
            assertEquals(selection, editor.state.selection)
            assertFalse(editor.canUndo)
        }
    }

    @Test fun secondFingerCancelsEditAndRemainingFingerCannotResumeIt() {
        for ((selection, start) in targets()) {
            val editor = SketchEditor(initial)
            editor.select(selection)
            editor.pointerDown(start, context(start))
            val end = start + Vec2(100.0, 100.0)
            editor.pointerMove(end, context(end))
            assertNotEquals(initial, editor.renderDocument)
            editor.interruptPointerForViewportChange()
            assertEquals(initial, editor.renderDocument)
            assertNull(editor.state.selectionGesture)
            assertNull(editor.state.magnifierTarget)
            editor.pointerMove(end, context(end))
            assertFalse(editor.pointerUp(end, context(end)).documentChanged)
            assertFalse(editor.canUndo)
        }
    }

    @Test fun slowPanAccumulatesFullDisplacementAndBlankTapClearsSelection() {
        val editor = SketchEditor(initial)
        editor.select(Selection.Column(column.id))
        val start = Vec2(-2000.0, -2000.0)
        editor.pointerDown(start, context(start))
        for (step in 1..8) {
            val point = start + Vec2(step.toDouble(), 0.0)
            assertEquals(Vec2(0.0, 0.0), editor.pointerMove(point, context(point)).panBy)
        }
        val end = start + Vec2(9.0, 0.0)
        assertEquals(Vec2(9.0, 0.0), editor.pointerMove(end, context(end)).panBy)
        editor.pointerUp(end, context(end))
        assertEquals(Selection.Column(column.id), editor.state.selection)
        editor.pointerDown(start, context(start))
        editor.pointerUp(start, context(start))
        assertNull(editor.state.selection)
        assertFalse(editor.canUndo)
    }
}
