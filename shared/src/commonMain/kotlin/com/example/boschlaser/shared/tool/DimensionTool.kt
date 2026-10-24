package com.example.boschlaser.shared.tool

import com.example.boschlaser.shared.command.AddWallDimensionCommand
import com.example.boschlaser.shared.editor.*
import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.WallDimension
import com.example.boschlaser.shared.model.canDimensionWith

/** Pick parallel references (including opposite ends of one wall), then the dimension line position. */
class DimensionTool : SketchTool {
    override fun pointerDown(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        val preview = editor.state.preview as? SketchPreview.Dimension
        val movingDimension = preview != null && (context.dimensionPreviewLabelHit ||
            WallDimensionGeometry.layout(editor.document, preview.dimension)?.let {
                Geometry.distanceToSegment(point, it.line) <= context.wallTolerance
            } == true)
        editor.updateState(editor.state.copy(dimensionGesture = DimensionGesture(context.screenPoint, context.screenPoint,
            point, preview, movingDimension), dragging = false))
        return if (preview == null) pointerMove(editor, point, context) else ToolUpdate()
    }

    override fun pointerMove(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        val gesture = editor.state.dimensionGesture
        if (gesture != null && !gesture.movingDimension &&
            (editor.state.dragging || Geometry.distance(gesture.startScreen, context.screenPoint) > context.dragThreshold)) {
            editor.updateState(editor.state.copy(dragging = true, preview = gesture.previewBefore,
                snap = null, magnifierTarget = null, dimensionGesture = gesture.copy(lastScreen = context.screenPoint)))
            return ToolUpdate(panBy = context.screenPoint - gesture.lastScreen)
        }
        // A blank press is undecided until release or pan; it must never teleport the preview.
        if (gesture?.previewBefore != null && !gesture.movingDimension) return ToolUpdate()
        if (gesture?.movingDimension == true) {
            if (!gesture.dimensionMoved && Geometry.distance(gesture.startScreen, context.screenPoint) <= context.dragThreshold)
                return ToolUpdate()
            editor.updateState(editor.state.copy(dimensionGesture = gesture.copy(dimensionMoved = true)))
        }
        val preview = editor.state.preview as? SketchPreview.Dimension
        val candidate = if (gesture?.movingDimension == true) {
            gesture.previewBefore!!.dimension.position + (point - gesture.startWorld)
        } else point
        val endpoint = WallDimensionGeometry.snapEndpoint(editor.document, candidate, context.snapTolerance,
            if (preview == null) editor.state.dimensionFirst else null)
        val position = endpoint?.point ?: candidate
        editor.updateState(editor.state.copy(
            preview = preview?.copy(dimension = preview.dimension.copy(position = position)),
            snap = endpoint?.let { SnapResult(it.point, SnapType.ENDPOINT, it.dimensionId) },
            magnifierTarget = endpoint?.point))
        return ToolUpdate()
    }

    override fun pointerUp(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        val update = if (editor.state.dimensionGesture != null) pointerMove(editor, point, context) else ToolUpdate()
        val dimensionMoved = editor.state.dimensionGesture?.dimensionMoved == true
        editor.updateState(editor.state.copy(dimensionGesture = null))
        if (editor.state.dragging) {
            editor.updateState(editor.state.copy(dragging = false, snap = null, magnifierTarget = null))
            return update
        }
        val preview = editor.state.preview as? SketchPreview.Dimension
        val endpoint = WallDimensionGeometry.snapEndpoint(editor.document, point, context.snapTolerance,
            if (preview == null) editor.state.dimensionFirst else null)
        val position = endpoint?.point ?: point
        if (preview != null) {
            val dimension = if (dimensionMoved) preview.dimension else preview.dimension.copy(position = position)
            val changed = editor.execute(AddWallDimensionCommand(dimension))
            cancel(editor)
            if (changed) editor.select(Selection.Dimension(dimension.id))
            return ToolUpdate(changed)
        }
        val first = editor.state.dimensionFirst
        val reference = endpoint?.reference ?: WallDimensionGeometry.pickFace(editor.document, point, context.wallTolerance, first)
            ?: return if (first != null && WallDimensionGeometry.pickFace(editor.document, point, context.wallTolerance) != null)
                ToolUpdate(failure = ToolFailure.INVALID_DIMENSION) else ToolUpdate()
        if (first == null) {
            editor.updateState(editor.state.copy(dimensionFirst = reference, snap = null, magnifierTarget = null))
            return ToolUpdate()
        }
        if (!first.canDimensionWith(reference)) return ToolUpdate(failure = ToolFailure.INVALID_DIMENSION)
        val dimension = WallDimension(editor.nextId(), first, reference, position)
        val layout = WallDimensionGeometry.layout(editor.document, dimension)
        if (layout == null || layout.valueMm < 1.0) return ToolUpdate(failure = ToolFailure.INVALID_DIMENSION)
        editor.updateState(editor.state.copy(preview = SketchPreview.Dimension(dimension), snap = null, magnifierTarget = null))
        return ToolUpdate()
    }

    override fun cancel(editor: SketchEditor) {
        editor.updateState(editor.state.copy(dimensionFirst = null, preview = null, snap = null, magnifierTarget = null,
            dimensionGesture = null, dragging = false))
    }
}
