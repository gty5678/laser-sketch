package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.tool.*
import kotlin.test.*

class BoardFinishAndSnapTest {
    private val points = listOf(Vec2(0.0,0.0),Vec2(1000.0,0.0),Vec2(1000.0,1000.0),Vec2(0.0,1000.0))
    private fun context(p: Vec2, grid: Double? = null) = PointerContext(p,20.0,10.0,10.0,10.0,10.0,10.0,gridStep = grid)
    private fun tap(editor: SketchEditor, p: Vec2) { editor.pointerDown(p,context(p)); editor.pointerUp(p,context(p)) }
    private fun polygonEditor() = SketchEditor().also {
        it.setTool(ToolType.BOARD); it.placement = it.placement.copy(boardShape = BoardShape.POLYGON)
    }
    @Test fun okClosesUnclosedPolygonAndRecordsOneUndo() {
        val editor = polygonEditor()
        points.take(3).forEach { tap(editor,it) }
        assertTrue(editor.document.boards.isEmpty())
        assertTrue(editor.finishBoardPlacement().documentChanged)
        val board = editor.document.boards.values.single()
        assertEquals(points.take(3),board.vertices)
        assertTrue(editor.state.boardPoints.isEmpty()); assertNull(editor.state.preview)
        assertFalse(editor.finishBoardPlacement().documentChanged)
        assertTrue(editor.undo()); assertTrue(editor.document.boards.isEmpty()); assertFalse(editor.canUndo)
        assertTrue(editor.redo()); assertEquals(board,editor.document.boards[board.id])
    }
    @Test fun okDiscardsIncompleteDraftsAndRejectsInvalidOutlineAtomically() {
        for (size in 0..2) {
            val editor = polygonEditor()
            points.take(size).forEach { tap(editor,it) }
            assertNull(editor.finishBoardPlacement().failure)
            assertTrue(editor.state.boardPoints.isEmpty()); assertFalse(editor.canUndo)
        }
        val editor = polygonEditor()
        listOf(Vec2(0.0,0.0),Vec2(1000.0,0.0),Vec2(2000.0,0.0)).forEach { tap(editor,it) }
        assertEquals(ToolFailure.INVALID_BOARD,editor.finishBoardPlacement().failure)
        assertEquals(3,editor.state.boardPoints.size); assertFalse(editor.canUndo)
    }
    @Test fun boardVertexSnapUsesVerticesEdgesCircleAndOptionalGrid() {
        val rectangle = Board("rect",points)
        val doc = SketchDocument(boards = mapOf(rectangle.id to rectangle))
        val snapper = SnapEngine()
        assertEquals(points[0],snapper.boardVertex(doc,Vec2(3.0,4.0),10.0,null).point)
        assertEquals(Vec2(1000.0,402.0),snapper.boardVertex(doc,Vec2(1007.0,402.0),10.0,null).point)
        assertEquals(Vec2(100.0,200.0),snapper.boardVertex(SketchDocument(),Vec2(123.0,167.0),10.0,100.0).point)
        assertEquals(Vec2(123.0,167.0),snapper.boardVertex(SketchDocument(),Vec2(123.0,167.0),10.0,null).point)
        val circle = Board("circle",Board.circle(Vec2(0.0,0.0),500.0),shape = BoardShape.CIRCLE)
        val result = snapper.boardVertex(SketchDocument(boards = mapOf(circle.id to circle)),Vec2(354.0,354.0),10.0,null)
        assertEquals(SnapType.OBJECT_EDGE,result.type)
        assertEquals(500.0,Geometry.distance(circle.center,result.point),1e-6)
        assertEquals(Vec2(0.0,0.0),snapper.boardVertex(SketchDocument(),Vec2(3.0,4.0),10.0,null,draftVertices = listOf(Vec2(0.0,0.0))).point)
    }
    @Test fun placementPreviewAndReleaseUseTheSameSnappedPoint() {
        val target = Board("target",points)
        val editor = SketchEditor(SketchDocument(boards = mapOf(target.id to target)))
        editor.setTool(ToolType.BOARD); editor.placement = editor.placement.copy(boardShape = BoardShape.POLYGON)
        val raw = Vec2(3.0,4.0)
        editor.pointerDown(raw,context(raw))
        assertEquals(points[0],editor.state.magnifierTarget)
        assertEquals(points[0],(editor.state.preview as SketchPreview.Board).vertices.single())
        editor.pointerUp(raw,context(raw))
        assertEquals(listOf(points[0]),editor.state.boardPoints)
        assertEquals(1,editor.document.boards.size)
    }
    @Test fun draggingSnapsButCanEscapeOriginalVertexAndCancel() {
        val original = Board("moving",points,shape = BoardShape.POLYGON)
        val target = Board("target",points.map { it - Vec2(1500.0,1500.0) })
        val editor = SketchEditor(SketchDocument(boards = mapOf(original.id to original,target.id to target)))
        editor.select(Selection.Board(original.id))
        val down = points[0]; val near = Vec2(-497.0,-496.0)
        editor.pointerDown(down,context(down)); editor.pointerMove(near,context(near))
        assertEquals(Vec2(-500.0,-500.0),editor.renderDocument.boards.getValue(original.id).vertices[0])
        assertEquals(Vec2(-500.0,-500.0),editor.state.magnifierTarget)
        editor.cancelPointer(); assertEquals(original,editor.renderDocument.boards[original.id]); assertFalse(editor.canUndo)
        editor.pointerDown(down,context(down))
        val escape = Vec2(-6.0,-8.0)
        editor.pointerMove(escape,context(escape))
        assertEquals(escape,editor.renderDocument.boards.getValue(original.id).vertices[0])
        editor.pointerUp(escape,context(escape)); assertTrue(editor.undo())
        assertEquals(original,editor.document.boards[original.id])
    }
    @Test fun boardVerticesSnapToPhysicalWallCorners() {
        val wall = Wall("wall",Vec2(0.0,0.0),Vec2(2000.0,0.0),100.0)
        val result = SnapEngine().boardVertex(SketchDocument(walls = mapOf(wall.id to wall)),Vec2(2.0,-48.0),10.0,null)
        assertEquals(Vec2(0.0,-50.0),result.point)
    }
    @Test fun boardVerticesPreferWallFaceIntersectionsToProjections() {
        val horizontal = Wall("horizontal", Vec2(-1000.0, 0.0), Vec2(1000.0, 0.0), 100.0)
        for (startY in listOf(-1000.0, 0.0)) {
            val vertical = Wall("vertical", Vec2(0.0, startY), Vec2(0.0, 1000.0), 200.0)
            for (alignment in WallAlignment.entries) {
                val wall = vertical.copy(alignment = alignment)
                val x = WallGeometry.build(wall).leftEdge.start.x
                val expected = Vec2(x, 50.0)
                val document = SketchDocument(walls = listOf(horizontal, wall).associateBy { it.id })
                val result = SnapEngine().boardVertex(document, expected + Vec2(3.0, 4.0), 10.0, 100.0)
                assertEquals(SnapType.INTERSECTION, result.type)
                assertEquals(expected.x, result.point.x, 1e-6)
                assertEquals(expected.y, result.point.y, 1e-6)
            }
        }
    }
    @Test fun boardVerticesDoNotSnapToDistantWallExtensions() {
        val horizontal = Wall("horizontal", Vec2(-1000.0, 0.0), Vec2(1000.0, 0.0), 100.0)
        val vertical = Wall("vertical", Vec2(0.0, 500.0), Vec2(0.0, 1000.0), 200.0)
        val document = SketchDocument(walls = listOf(horizontal, vertical).associateBy { it.id })
        val result = SnapEngine().boardVertex(document, Vec2(-97.0, 54.0), 10.0, null)
        assertEquals(SnapType.OBJECT_EDGE, result.type)
        assertEquals(Vec2(-97.0, 50.0), result.point)
    }

    @Test fun boardPlacementAndVertexDragUseWallJunction() {
        val walls = listOf(
            Wall("h", Vec2(-1000.0, 0.0), Vec2(1000.0, 0.0), 100.0),
            Wall("v", Vec2(0.0, -1000.0), Vec2(0.0, 1000.0), 200.0),
        ).associateBy { it.id }
        val raw = Vec2(-97.0, 54.0)
        val expected = Vec2(-100.0, 50.0)
        val editor = SketchEditor(SketchDocument(walls = walls))
        editor.setTool(ToolType.BOARD)
        editor.placement = editor.placement.copy(boardShape = BoardShape.POLYGON)
        editor.pointerDown(raw, context(raw))
        assertEquals(expected, editor.state.magnifierTarget)
        editor.pointerUp(raw, context(raw))
        assertEquals(listOf(expected), editor.state.boardPoints)
        val board = Board("moving", points.map { it + Vec2(2000.0, 2000.0) }, shape = BoardShape.POLYGON)
        val editing = SketchEditor(SketchDocument(walls = walls, boards = mapOf(board.id to board)))
        editing.select(Selection.Board(board.id))
        editing.pointerDown(board.vertices[0], context(board.vertices[0]))
        editing.pointerMove(raw, context(raw))
        assertEquals(expected, editing.state.magnifierTarget)
        assertEquals(expected, editing.renderDocument.boards.getValue(board.id).vertices[0])
        editing.pointerUp(raw, context(raw))
        assertEquals(expected, editing.document.boards.getValue(board.id).vertices[0])
        assertTrue(editing.undo())
        assertEquals(board, editing.document.boards[board.id])
    }
    @Test fun boardVerticesSnapToConnectedOuterWallCorner() {
        val walls = listOf(
            Wall("h", Vec2(0.0, 0.0), Vec2(1000.0, 0.0), 100.0),
            Wall("v", Vec2(0.0, 0.0), Vec2(0.0, 1000.0), 100.0),
        ).associateBy { it.id }
        val result = SnapEngine().boardVertex(SketchDocument(walls = walls), Vec2(-47.0, -46.0), 10.0, null)
        assertEquals(Vec2(-50.0, -50.0), result.point)
    }

}
