package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.storage.ProjectSerializer
import com.example.boschlaser.shared.tool.PointerContext
import kotlin.test.*

class BoardVertexEditingTest {
    private val points = listOf(Vec2(0.0,0.0),Vec2(1000.0,0.0),Vec2(1000.0,1000.0),Vec2(0.0,1000.0))
    private fun board() = Board("polygon",points,BoardFill.WOOD,BoardShape.POLYGON)
    private fun editor() = SketchEditor(SketchDocument(boards = mapOf("polygon" to board()))).also { it.select(Selection.Board("polygon")) }
    private fun context(point: Vec2) = PointerContext(point,20.0,10.0,10.0,10.0,10.0,10.0)
    private fun tap(editor: SketchEditor, point: Vec2) { editor.pointerDown(point,context(point)); editor.pointerUp(point,context(point)) }
    @Test fun tapMidpointAddsVertexOnlyOnReleaseAndUndoRestores() {
        val editor = editor()
        val point = Vec2(500.0,0.0)
        editor.pointerDown(point,context(point))
        assertIs<SelectionGestureTarget.BoardEdge>(editor.state.selectionGesture?.target)
        assertEquals(4,editor.document.boards.getValue("polygon").vertices.size)
        assertEquals(5,editor.renderDocument.boards.getValue("polygon").vertices.size)
        assertFalse(editor.canUndo)
        editor.pointerUp(point,context(point))
        val updated = editor.document.boards.getValue("polygon")
        assertEquals(listOf(points[0],point,points[1],points[2],points[3]),updated.vertices)
        assertEquals(1,editor.state.selectedBoardVertex)
        assertEquals(BoardFill.WOOD,updated.fill)
        assertTrue(editor.undo()); assertEquals(board(),editor.document.boards["polygon"])
        assertNull(editor.state.selectedBoardVertex)
        assertTrue(editor.redo()); assertEquals(updated,editor.document.boards["polygon"])
    }
    @Test fun closingEdgeInsertionAndDragIsOneUndoAndCancellationIsAtomic() {
        val editor = editor()
        val point = Vec2(0.0,500.0); val moved = Vec2(-200.0,500.0)
        editor.pointerDown(point,context(point)); editor.pointerMove(moved,context(moved))
        assertEquals(moved,editor.renderDocument.boards.getValue("polygon").vertices.last())
        editor.cancelPointer()
        assertEquals(board(),editor.renderDocument.boards["polygon"])
        assertFalse(editor.canUndo); assertNull(editor.state.selectedBoardVertex)
        editor.pointerDown(point,context(point)); editor.pointerMove(moved,context(moved)); editor.pointerUp(moved,context(moved))
        assertEquals(5,editor.document.boards.getValue("polygon").vertices.size)
        assertTrue(editor.undo()); assertEquals(board(),editor.document.boards["polygon"])
        assertFalse(editor.canUndo)
    }
    @Test fun selectedVertexCanBeDeletedWithMinimumThreeAndUndoRedoAndSave() {
        val editor = editor()
        tap(editor,points[1])
        assertEquals(1,editor.state.selectedBoardVertex)
        assertTrue(editor.deleteSelectedBoardVertex())
        val triangle = editor.document.boards.getValue("polygon")
        assertEquals(listOf(points[0],points[2],points[3]),triangle.vertices)
        assertNull(editor.state.selectedBoardVertex)
        tap(editor,points[0]); assertFalse(editor.deleteSelectedBoardVertex())
        assertEquals(triangle,editor.document.boards["polygon"])
        val project = Project(ProjectManifest("p","fixture",0),editor.document)
        assertEquals(project,ProjectSerializer.decode(ProjectSerializer.encode(project)))
        assertTrue(editor.undo()); assertEquals(board(),editor.document.boards["polygon"])
        assertTrue(editor.redo()); assertEquals(triangle,editor.document.boards["polygon"])
    }
    @Test fun invalidDeletionAndNonPolygonOperationsAreRejected() {
        assertNull(board().removedVertex(-1)); assertNull(board().insertedVertex(4))
        assertNull(Board("rectangle",points).insertedVertex(0))
        assertNull(Board("rectangle",points).removedVertex(0))
        val thinEdge = Board("thin",listOf(Vec2(0.0,0.0),Vec2(1.0,0.0),Vec2(100.0,100.0)),shape = BoardShape.POLYGON)
        assertNull(thinEdge.insertedVertex(0))
        val concave = Board("concave",listOf(Vec2(0.0,0.0),Vec2(400.0,0.0),Vec2(400.0,400.0),
            Vec2(300.0,100.0),Vec2(100.0,100.0),Vec2(0.0,400.0)),shape = BoardShape.POLYGON)
        assertNull(concave.removedVertex(1))
        assertNotNull(concave.removedVertex(3))
    }
    @Test fun selectingBodyOrAnotherObjectClearsVertexSelection() {
        val editor = editor()
        tap(editor,points[0]); assertEquals(0,editor.state.selectedBoardVertex)
        tap(editor,Vec2(500.0,500.0)); assertNull(editor.state.selectedBoardVertex)
        tap(editor,points[0]); editor.select(null); assertNull(editor.state.selectedBoardVertex)
        assertFalse(editor.deleteSelectedBoardVertex())
    }
}
