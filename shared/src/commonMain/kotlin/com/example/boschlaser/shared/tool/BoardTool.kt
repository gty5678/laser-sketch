package com.example.boschlaser.shared.tool
import com.example.boschlaser.shared.command.AddBoardCommand
import com.example.boschlaser.shared.editor.*
import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*

class BoardTool : SketchTool {
    private fun vertices(editor: SketchEditor, point: Vec2): List<Vec2> {
        val points = editor.state.boardPoints
        if (editor.placement.boardShape == BoardShape.POLYGON || points.isEmpty()) return points + point
        val a = points.first()
        if (editor.placement.boardShape == BoardShape.CIRCLE) return Board.circle(a, Geometry.distance(a,point))
        return listOf(a, Vec2(point.x,a.y), point, Vec2(a.x,point.y))
    }
    override fun pointerDown(editor: SketchEditor, point: Vec2, context: PointerContext) = pointerMove(editor,point,context)
    override fun pointerMove(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        val snap = editor.snapEngine.boardVertex(editor.document,point,context.snapTolerance,context.gridStep,
            draftVertices = editor.state.boardPoints.dropLast(1))
        editor.updateState(editor.state.copy(preview = SketchPreview.Board(vertices(editor,snap.point)), snap = snap, magnifierTarget = snap.point))
        return ToolUpdate()
    }
    override fun pointerUp(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        pointerMove(editor,point,context)
        val accepted = editor.state.snap?.point ?: point
        val points = editor.state.boardPoints
        val closing = editor.placement.boardShape != BoardShape.POLYGON && points.isNotEmpty() ||
            points.size >= 3 && Geometry.distance(points.first(),accepted) <= context.endpointTolerance
        if (closing) {
            val polygon = if (editor.placement.boardShape != BoardShape.POLYGON) vertices(editor,accepted) else points
            if (!Board.valid(polygon)) return ToolUpdate(failure = ToolFailure.INVALID_BOARD)
            val changed = editor.execute(AddBoardCommand(Board(editor.nextId(),polygon,editor.placement.boardFill,editor.placement.boardShape)))
            cancel(editor)
            return ToolUpdate(changed)
        }
        if (points.isEmpty() || Geometry.distance(points.last(),accepted) >= 1.0)
            editor.updateState(editor.state.copy(boardPoints = points + accepted, preview = SketchPreview.Board(points + accepted), magnifierTarget = null))
        return ToolUpdate()
    }
    override fun cancel(editor: SketchEditor) { editor.updateState(editor.state.copy(boardPoints = emptyList(), preview = null, magnifierTarget = null, snap = null)) }
}
