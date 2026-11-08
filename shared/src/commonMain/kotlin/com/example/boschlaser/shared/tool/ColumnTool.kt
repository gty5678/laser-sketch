package com.example.boschlaser.shared.tool

import com.example.boschlaser.shared.command.AddColumnCommand
import com.example.boschlaser.shared.editor.*
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*

class ColumnTool(private val circular: Boolean) : SketchTool {
    private fun column(editor: SketchEditor, id: String, center: Vec2): Column = with(editor.placement) {
        if (circular) CircularColumn(id, center, columnWidth, columnFill)
        else RectColumn(id, center, columnWidth, columnDepth, columnRotation, columnFill)
    }
    private fun update(editor: SketchEditor, point: Vec2, context: PointerContext) {
        val snap = editor.snapEngine.columnCenter(editor.document, column(editor, "preview", point), point, context.columnSnapTolerance)
        editor.updateState(editor.state.copy(preview = SketchPreview.Column(column(editor, "preview", snap.point)),
            snap = snap, magnifierTarget = snap.point))
    }
    override fun pointerDown(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        update(editor, point, context); return ToolUpdate()
    }
    override fun pointerMove(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        update(editor, point, context); return ToolUpdate()
    }
    override fun pointerUp(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        update(editor, point, context)
        val center = (editor.state.preview as SketchPreview.Column).column.center
        val column = column(editor, editor.nextId(), center)
        val changed = editor.execute(AddColumnCommand(column))
        if (changed) editor.select(Selection.Column(column.id))
        cancel(editor)
        return ToolUpdate(changed)
    }
    override fun cancel(editor: SketchEditor) {
        editor.updateState(editor.state.copy(preview = null, snap = null, magnifierTarget = null))
    }
}
