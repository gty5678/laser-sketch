package com.example.boschlaser.shared.tool

import com.example.boschlaser.shared.command.*
import com.example.boschlaser.shared.editor.*
import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*
import kotlin.math.*

class SelectTool : SketchTool {
    override fun pointerDown(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        editor.cancelEdit()
        val doc = editor.document
        val rotation = context.rotationHandleColumnId?.takeIf { doc.columns[it] is RectColumn }
        val dimension = context.dimensionLabelId?.let { doc.dimensions[it] }
            ?: WallDimensionGeometry.hit(doc, point, context.wallTolerance)
        val driveDimension = dimension?.takeIf { it.id == context.dimensionLabelId }
            ?.let { WallDimensionGeometry.forMovingSelection(it, editor.state.selection) }
        val faceDimension = (editor.state.selection as? Selection.Wall)?.let { selected ->
            WallDimensionGeometry.forSelectedWall(doc, selected.id).firstOrNull {
                it.id == context.faceDimensionLabelId
            }
        }
        val hit = editor.hitTestEngine.hitTest(doc, point, HitTestContext(context.endpointTolerance,
            context.openingTolerance, context.columnTolerance, context.wallTolerance,
            (editor.state.selection as? Selection.Wall)?.id)).firstOrNull()
        val target = when {
            rotation != null -> {
                editor.select(Selection.Column(rotation))
                SelectionGestureTarget.ColumnRotation(rotation)
            }
            driveDimension != null -> SelectionGestureTarget.FaceDimension(driveDimension)
            dimension != null && (context.dimensionLabelId == dimension.id || hit !is HitResult.WallEndpoint) -> {
                editor.select(Selection.Dimension(dimension.id))
                SelectionGestureTarget.Dimension(dimension.id)
            }
            faceDimension != null -> SelectionGestureTarget.FaceDimension(faceDimension)
            hit is HitResult.WallEndpoint -> {
                editor.select(Selection.Wall(hit.wallId))
                SelectionGestureTarget.WallEndpoint(hit.wallId,
                    hit.endpoint == com.example.boschlaser.shared.topology.WallEndpoint.START)
            }
            else -> {
                editor.select(when (hit) {
                    is HitResult.Opening -> Selection.Opening(hit.openingId)
                    is HitResult.Column -> Selection.Column(hit.columnId)
                    is HitResult.Wall -> Selection.Wall(hit.wallId)
                    else -> null
                })
                when (val selection = editor.state.selection) {
                    is Selection.Wall -> SelectionGestureTarget.WallBody(selection.id)
                    is Selection.Column -> SelectionGestureTarget.Column(selection.id)
                    is Selection.Opening -> SelectionGestureTarget.Opening(selection.id)
                    is Selection.Dimension -> SelectionGestureTarget.Dimension(selection.id)
                    null -> SelectionGestureTarget.Pan
                }
            }
        }
        if (target != SelectionGestureTarget.Pan && target !is SelectionGestureTarget.FaceDimension) editor.beginEdit()
        editor.updateState(editor.state.copy(dragging = false,
            selectionGesture = SelectionGesture(target, point, context.screenPoint)))
        return ToolUpdate()
    }
    override fun pointerMove(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        val gesture = editor.state.selectionGesture ?: return ToolUpdate()
        val screenDelta = context.screenPoint - gesture.lastScreen
        val delta = point - gesture.lastWorld
        if (hypot(screenDelta.x, screenDelta.y) > context.dragThreshold) editor.updateState(editor.state.copy(dragging = true))
        if (gesture.target == SelectionGestureTarget.Pan) {
            editor.updateState(editor.state.copy(selectionGesture = gesture.copy(
                lastWorld = point - screenDelta * (1.0 / context.viewportScale), lastScreen = context.screenPoint)))
            return ToolUpdate(panBy = screenDelta)
        }
        if (!editor.state.dragging) {
            // Keep the down position until the threshold is crossed. Small move events
            // must accumulate, and the first preview must include the full displacement.
            return ToolUpdate()
        }
        val doc = editor.renderDocument
        var magnifier: Vec2? = point
        when (val target = gesture.target) {
            is SelectionGestureTarget.FaceDimension -> magnifier = null
            is SelectionGestureTarget.Dimension -> {
                val dimension = doc.dimensions[target.id] ?: return cancelMissingTarget(editor)
                editor.preview(SketchCommand { it.copy(dimensions = it.dimensions +
                    (dimension.id to dimension.copy(position = dimension.position + delta))) })
                magnifier = null
            }
            is SelectionGestureTarget.ColumnRotation -> {
                val rotation = doc.columns[target.id] as? RectColumn ?: return cancelMissingTarget(editor)
                val angle = ((atan2(point.y - rotation.center.y, point.x - rotation.center.x) * 180.0 / PI + 90.0) / 15.0).roundToInt() * 15.0
                editor.preview(UpdateColumnCommand(rotation.copy(rotationDegrees = (angle % 360.0 + 360.0) % 360.0)))
                magnifier = null
            }
            is SelectionGestureTarget.WallEndpoint -> {
                val wall = doc.walls[target.id] ?: return cancelMissingTarget(editor)
                val pointOnGrid = context.gridStep?.let { editor.snapEngine.grid(point, it) } ?: point
                editor.preview(MoveWallEndpointCommand(wall.id, target.start, pointOnGrid, true))
                val updated = editor.renderDocument.walls.getValue(wall.id)
                magnifier = if (target.start) updated.start else updated.end
            }
            is SelectionGestureTarget.WallBody -> {
                val wall = doc.walls[target.id] ?: return cancelMissingTarget(editor)
                editor.preview(MoveWallCommand(wall.id, delta))
            }
            is SelectionGestureTarget.Column -> {
                val column = doc.columns[target.id] ?: return cancelMissingTarget(editor)
                val snap = editor.snapEngine.columnCenter(doc, column, column.center + delta, context.columnSnapTolerance)
                val moved = when (column) { is RectColumn -> column.copy(center = snap.point); is CircularColumn -> column.copy(center = snap.point) }
                editor.preview(UpdateColumnCommand(moved))
                magnifier = snap.point
                editor.updateState(editor.state.copy(snap = snap))
            }
            is SelectionGestureTarget.Opening -> {
                val opening = doc.openings[target.id] ?: return cancelMissingTarget(editor)
                val line = WallGeometry.referenceLine(doc.walls.getValue(opening.hostWallId))
                val length = Geometry.length(line)
                val offset = (Geometry.parameter(point, line) * length).coerceIn(opening.width / 2.0, length - opening.width / 2.0)
                editor.preview(UpdateOpeningCommand(opening.copy(offset = offset)))
                magnifier = null
            }
            SelectionGestureTarget.Pan -> Unit
        }
        editor.updateState(editor.state.copy(magnifierTarget = magnifier,
            selectionGesture = gesture.copy(lastWorld = point, lastScreen = context.screenPoint)))
        return ToolUpdate()
    }
    override fun pointerUp(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        val faceDimension = (editor.state.selectionGesture?.target as? SelectionGestureTarget.FaceDimension)
            ?.takeUnless { editor.state.dragging }?.dimension
        val changed = if (editor.state.dragging && editor.state.selectionGesture?.target != SelectionGestureTarget.Pan) editor.commitEdit() else { editor.cancelEdit(); false }
        cancel(editor)
        return ToolUpdate(changed, editFaceDimension = faceDimension)
    }
    private fun cancelMissingTarget(editor: SketchEditor): ToolUpdate {
        cancel(editor)
        return ToolUpdate()
    }

    override fun cancel(editor: SketchEditor) {
        editor.cancelEdit()
        editor.updateState(editor.state.copy(dragging = false, snap = null, magnifierTarget = null, selectionGesture = null))
    }
}
