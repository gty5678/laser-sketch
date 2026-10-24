package com.example.boschlaser.shared.tool

import com.example.boschlaser.shared.command.AddWallCommand
import com.example.boschlaser.shared.editor.*
import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.Wall

class WallTool : SketchTool {
    private var hadAnchor = false
    private val starts = ArrayDeque<Vec2>()
    fun resetChain(editor: SketchEditor) {
        starts.clear()
        editor.updateState(editor.state.copy(wallAnchor = null, preview = null))
    }
    fun resume(editor: SketchEditor, point: Vec2) {
        editor.updateState(editor.state.copy(wallAnchor = point, preview = SketchPreview.Wall(Segment2(point, point))))
    }
    private fun update(editor: SketchEditor, point: Vec2, context: PointerContext) {
        var snap = editor.snapEngine.snap(editor.document, point, SnapContext(context.snapTolerance, context.gridStep))
        val anchor = editor.state.wallAnchor
        if (hadAnchor && anchor != null) {
            val axis = editor.snapEngine.axis(anchor, snap.point)
            if (axis.type != SnapType.NONE) snap = axis
        }
        val start = if (hadAnchor) anchor ?: snap.point else snap.point
        editor.updateState(editor.state.copy(preview = SketchPreview.Wall(Segment2(start, snap.point)),
            snap = snap, magnifierTarget = snap.point))
    }
    override fun pointerDown(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        hadAnchor = editor.state.wallAnchor != null
        update(editor, point, context)
        return ToolUpdate()
    }
    override fun pointerMove(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        update(editor, point, context)
        return ToolUpdate()
    }
    override fun pointerUp(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        update(editor, point, context)
        val line = (editor.state.preview as SketchPreview.Wall).line
        var changed = false
        if (hadAnchor && Geometry.length(line) >= 100.0) {
            val settings = editor.placement
            val wall = Wall(editor.nextId(), line.start, line.end, settings.wallThickness, settings.alignment)
            changed = editor.execute(AddWallCommand(wall))
            if (changed) { starts.addLast(line.start); editor.select(Selection.Wall(wall.id)) }
        }
        val anchor = if (!hadAnchor || changed) line.end else line.start
        resume(editor, anchor)
        editor.updateState(editor.state.copy(magnifierTarget = null, snap = null))
        return ToolUpdate(changed)
    }
    override fun cancel(editor: SketchEditor) {
        val anchor = editor.state.wallAnchor
        editor.updateState(editor.state.copy(preview = anchor?.let { SketchPreview.Wall(Segment2(it, it)) },
            snap = null, magnifierTarget = null))
    }
    fun undoPlacement(editor: SketchEditor): Boolean {
        if (starts.isNotEmpty()) {
            if (!editor.undo()) return false
            resume(editor, starts.removeLast())
            return true
        }
        if (editor.state.wallAnchor != null) { resetChain(editor); return true }
        return false
    }
}
