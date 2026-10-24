package com.example.boschlaser.shared.tool

import com.example.boschlaser.shared.editor.SketchEditor
import com.example.boschlaser.shared.editor.SketchPreview
import com.example.boschlaser.shared.editor.SnapContext
import com.example.boschlaser.shared.geometry.Segment2
import com.example.boschlaser.shared.geometry.Vec2

/** Preview each snapped point under the magnifier; release confirms it without document history. */
class MeasureTool : SketchTool {
    private fun update(editor: SketchEditor, point: Vec2, context: PointerContext) {
        val measurement = editor.state.preview as? SketchPreview.Measurement
        if (measurement?.complete == true) return
        val snap = editor.snapEngine.snap(editor.document, point,
            SnapContext(context.snapTolerance, context.gridStep, midpoints = true, intersections = true, objectEdges = true))
        editor.updateState(editor.state.copy(preview = measurement?.copy(line = Segment2(measurement.line.start, snap.point)),
            snap = snap, magnifierTarget = snap.point))
    }
    override fun pointerDown(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        if ((editor.state.preview as? SketchPreview.Measurement)?.complete == true) cancel(editor)
        update(editor, point, context)
        return ToolUpdate()
    }
    override fun pointerMove(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        update(editor, point, context)
        return ToolUpdate()
    }
    override fun pointerUp(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        val measurement = editor.state.preview as? SketchPreview.Measurement
        if (measurement == null) {
            val snap = editor.snapEngine.snap(editor.document, point,
                SnapContext(context.snapTolerance, context.gridStep, midpoints = true, intersections = true, objectEdges = true))
            editor.updateState(editor.state.copy(preview = SketchPreview.Measurement(Segment2(snap.point, snap.point))))
        } else {
            update(editor, point, context)
            val current = editor.state.preview as SketchPreview.Measurement
            editor.updateState(editor.state.copy(preview = current.copy(complete = true)))
        }
        editor.updateState(editor.state.copy(snap = null, magnifierTarget = null))
        return ToolUpdate()
    }
    override fun cancel(editor: SketchEditor) {
        editor.updateState(editor.state.copy(preview = null, snap = null, magnifierTarget = null))
    }
}
