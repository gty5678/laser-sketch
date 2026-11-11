package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.command.*
import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.storage.ProjectSerializer
import com.example.boschlaser.shared.tool.PointerContext
import kotlin.test.*

class BoardShapeEditingTest {
    private val square = listOf(Vec2(0.0,0.0),Vec2(1000.0,0.0),Vec2(1000.0,1000.0),Vec2(0.0,1000.0))
    private fun context(p: Vec2) = PointerContext(p,20.0,10.0,10.0,10.0,10.0,10.0)
    private fun tap(editor: SketchEditor, p: Vec2) { editor.pointerDown(p,context(p)); editor.pointerUp(p,context(p)) }
    @Test fun circleIsCreatedFromCenterAndRadiusAndUsesTrueCircleHit() {
        val editor = SketchEditor()
        editor.setTool(ToolType.BOARD)
        editor.placement = editor.placement.copy(boardShape = BoardShape.CIRCLE)
        tap(editor,Vec2(1000.0,1000.0))
        editor.pointerDown(Vec2(1500.0,1000.0),context(Vec2(1500.0,1000.0)))
        assertTrue(editor.document.boards.isEmpty())
        editor.pointerUp(Vec2(1500.0,1000.0),context(Vec2(1500.0,1000.0)))
        val circle = editor.document.boards.values.single()
        assertEquals(BoardShape.CIRCLE,circle.shape)
        assertEquals(Vec2(1000.0,1000.0),circle.center)
        assertEquals(500.0,circle.radius,1e-6)
        assertTrue(circle.contains(Vec2(1000.0,1499.0)))
        assertFalse(circle.contains(Vec2(1490.0,1490.0)))
        assertTrue(editor.undo()); assertTrue(editor.document.boards.isEmpty())
        assertTrue(editor.redo()); assertEquals(circle,editor.document.boards[circle.id])
    }
    @Test fun rectangleCornerKeepsOppositeFixedAndUndoRestores() {
        val original = Board("board",square)
        val editor = SketchEditor(SketchDocument(boards = mapOf(original.id to original)))
        editor.select(Selection.Board(original.id))
        val down = Vec2(3.0,4.0)
        editor.pointerDown(down,context(down))
        assertIs<SelectionGestureTarget.BoardVertex>(editor.state.selectionGesture?.target)
        val moved = Vec2(-197.0,-296.0)
        editor.pointerMove(moved,context(moved))
        val preview = editor.renderDocument.boards.getValue(original.id)
        assertEquals(Vec2(-200.0,-300.0),preview.vertices[0])
        assertEquals(square[2],preview.vertices[2])
        assertEquals(original,editor.document.boards[original.id])
        editor.pointerUp(moved,context(moved))
        assertEquals(BoardShape.RECTANGLE,editor.document.boards.getValue(original.id).shape)
        assertTrue(editor.undo()); assertEquals(original,editor.document.boards[original.id])
        assertTrue(editor.redo()); assertEquals(preview,editor.document.boards[original.id])
    }
    @Test fun polygonCornerCanChangeShapeAndInvalidMoveIsAtomic() {
        val original = Board("polygon",square,shape = BoardShape.POLYGON)
        val editor = SketchEditor(SketchDocument(boards = mapOf(original.id to original)))
        editor.select(Selection.Board(original.id))
        val down = square[1]
        editor.pointerDown(down,context(down))
        val valid = Vec2(800.0,200.0)
        editor.pointerMove(valid,context(valid))
        assertEquals(valid,editor.renderDocument.boards.getValue(original.id).vertices[1])
        editor.cancelPointer()
        assertEquals(original,editor.renderDocument.boards[original.id])
        assertFalse(editor.canUndo)
        editor.pointerDown(down,context(down))
        val invalid = Vec2(-500.0,500.0)
        editor.pointerMove(invalid,context(invalid))
        assertEquals(original,editor.renderDocument.boards[original.id])
        editor.pointerUp(invalid,context(invalid))
        assertFalse(editor.canUndo)
    }
    @Test fun circleRadiusHandleKeepsCenterAndMaterial() {
        val original = Board("circle",Board.circle(Vec2(0.0,0.0),500.0),BoardFill.WOOD,BoardShape.CIRCLE)
        val editor = SketchEditor(SketchDocument(boards = mapOf(original.id to original)))
        editor.select(Selection.Board(original.id))
        val down = Vec2(500.0,0.0); val moved = Vec2(800.0,0.0)
        editor.pointerDown(down,context(down)); editor.pointerMove(moved,context(moved)); editor.pointerUp(moved,context(moved))
        val updated = editor.document.boards.getValue(original.id)
        assertEquals(original.center,updated.center)
        assertEquals(800.0,updated.radius,1e-6)
        assertEquals(BoardFill.WOOD,updated.fill)
        assertTrue(editor.undo()); assertEquals(original,editor.document.boards[original.id])
    }
    @Test fun everyShapeAndMaterialRoundTripsAndCanBeConverted() {
        for (shape in BoardShape.entries) for (fill in BoardFill.entries) {
            val board = Board("board",square,fill).withShape(shape)
            val project = Project(ProjectManifest("p","fixture",0),SketchDocument(boards = mapOf(board.id to board)))
            assertEquals(project,ProjectSerializer.decode(ProjectSerializer.encode(project)))
            assertEquals(fill,board.fill)
            assertEquals(shape,board.shape)
        }
        val thin = Board("thin",listOf(Vec2(0.0,0.0),Vec2(1.0,0.0),Vec2(1.0,1000.0),Vec2(0.0,1000.0)))
        assertEquals(thin,thin.withShape(BoardShape.CIRCLE))
    }
}
