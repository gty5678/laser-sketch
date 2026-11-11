package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.command.*
import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.storage.ProjectSerializer
import com.example.boschlaser.shared.tool.*
import kotlin.test.*

class BoardToolTest {
    private val vertices = listOf(Vec2(0.0, 0.0), Vec2(1000.0, 0.0), Vec2(1000.0, 1000.0), Vec2(0.0, 1000.0))
    private fun context(p: Vec2) = PointerContext(p, 20.0, 10.0, 10.0, 10.0, 10.0, 10.0)
    private fun tap(editor: SketchEditor, p: Vec2): ToolUpdate {
        editor.pointerDown(p, context(p))
        return editor.pointerUp(p, context(p))
    }
    @Test fun rectangleUsesTwoReleasedCornersAndOneHistoryEntry() {
        val editor = SketchEditor()
        editor.setTool(ToolType.BOARD)
        editor.placement = editor.placement.copy(boardFill = BoardFill.WOOD)
        tap(editor, vertices[0])
        editor.pointerDown(vertices[2], context(vertices[2]))
        assertTrue(editor.document.boards.isEmpty())
        assertFalse(editor.canUndo)
        assertTrue(editor.pointerUp(vertices[2], context(vertices[2])).documentChanged)
        assertEquals(vertices, editor.document.boards.values.single().vertices)
        assertEquals(BoardFill.WOOD, editor.document.boards.values.single().fill)
        assertTrue(editor.undoPlacement())
        assertTrue(editor.document.boards.isEmpty())
        assertTrue(editor.redo())
        assertEquals(1, editor.document.boards.size)
    }
    @Test fun polygonClosesAtFirstPointAndInvalidPolygonDoesNotCommit() {
        val editor = SketchEditor()
        editor.setTool(ToolType.BOARD)
        editor.placement = editor.placement.copy(boardShape = BoardShape.POLYGON)
        vertices.forEach { tap(editor, it) }
        assertTrue(editor.document.boards.isEmpty())
        assertTrue(tap(editor, Vec2(2.0, 2.0)).documentChanged)
        assertEquals(vertices, editor.document.boards.values.single().vertices)
        listOf(vertices[0], vertices[2], vertices[1], vertices[3]).forEach { tap(editor, it) }
        assertEquals(ToolFailure.INVALID_BOARD, tap(editor, vertices[0]).failure)
        assertEquals(1, editor.document.boards.size)
        assertEquals(4, editor.state.boardPoints.size)
        assertTrue(editor.undoPlacement())
        assertEquals(3, editor.state.boardPoints.size)
        editor.setTool(ToolType.SELECT)
        assertTrue(editor.state.boardPoints.isEmpty())
    }
    @Test fun geometryRejectsDegenerateAndCrossedButAllowsConcavePolygons() {
        assertFalse(Board.valid(listOf(Vec2(0.0,0.0),Vec2(10.0,0.0),Vec2(20.0,0.0))))
        assertFalse(Board.valid(listOf(vertices[0],vertices[2],vertices[1],vertices[3])))
        assertFailsWith<IllegalArgumentException> { Vec2(Double.NaN,0.0) }
        assertFalse(Board.valid(listOf(vertices[0],vertices[0],vertices[2])))
        val concave = Board("concave", listOf(vertices[0],vertices[1],Vec2(500.0,500.0),vertices[2],vertices[3]))
        assertTrue(concave.contains(Vec2(200.0,500.0)))
        assertFalse(concave.contains(Vec2(900.0,500.0)))
    }
    @Test fun selectMoveCancelFillDeleteAndUndoPreserveGeometry() {
        val board = Board("board", vertices)
        val editor = SketchEditor(SketchDocument(boards = mapOf(board.id to board)))
        val point = Vec2(500.0,500.0)
        tap(editor,point)
        assertEquals(Selection.Board(board.id),editor.state.selection)
        editor.pointerDown(point,context(point))
        val moved = point + Vec2(100.0,200.0)
        editor.pointerMove(moved,context(moved))
        assertEquals(vertices.map { it + Vec2(100.0,200.0) },editor.renderDocument.boards.getValue(board.id).vertices)
        editor.cancelPointer()
        assertEquals(board,editor.document.boards[board.id])
        editor.pointerDown(point,context(point)); editor.pointerMove(moved,context(moved)); editor.pointerUp(moved,context(moved))
        assertTrue(editor.undo()); assertEquals(board,editor.document.boards[board.id])
        assertTrue(editor.execute(UpdateBoardCommand(board.copy(fill = BoardFill.GRID))))
        assertEquals(vertices,editor.document.boards.getValue(board.id).vertices)
        assertTrue(editor.execute(DeleteBoardCommand(board.id)))
        assertNull(editor.state.selection)
        assertTrue(editor.undo()); assertEquals(BoardFill.GRID,editor.document.boards.getValue(board.id).fill)
    }
    @Test fun allFillsRoundTripAndOldProjectsDefaultToNoBoards() {
        for (fill in BoardFill.entries) {
            val board = Board("b",vertices,fill)
            val project = Project(ProjectManifest("p","fixture",0), SketchDocument(boards = mapOf("b" to board)))
            assertEquals(project,ProjectSerializer.decode(ProjectSerializer.encode(project)))
        }
        val old = Project(ProjectManifest("old","fixture",0))
        val encoded = ProjectSerializer.encode(old)
        assertFalse(encoded.getValue("sketch.json").contains("boards"))
        assertTrue(ProjectSerializer.decode(encoded).sketch.boards.isEmpty())
    }
    @Test fun boardOnlyExportBoundsIncludeEveryVertex() {
        val board = Board("b",vertices.map { it + Vec2(10000.0,10000.0) })
        val bounds = sketchContentBounds(SketchDocument(boards = mapOf("b" to board)))
        assertEquals(Vec2(9200.0,9200.0),bounds.min)
        assertEquals(Vec2(11800.0,11800.0),bounds.max)
    }
}
