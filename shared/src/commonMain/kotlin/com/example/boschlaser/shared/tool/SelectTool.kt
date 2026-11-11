package com.example.boschlaser.shared.tool

import com.example.boschlaser.shared.command.*
import com.example.boschlaser.shared.editor.*
import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*
import kotlin.math.*

class SelectTool : SketchTool {
    override fun pointerDown(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        editor.cancelEdit()
        if (editor.state.tool == ToolType.PAN) {
            editor.updateState(editor.state.copy(dragging = false,
                selectionGesture = SelectionGesture(SelectionGestureTarget.Pan, point, context.screenPoint)))
            return ToolUpdate()
        }
        val previousSelection = editor.state.selection
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
        val board = (editor.state.selection as? Selection.Board)?.let { doc.boards[it.id] }
        val boardControl = board?.controls?.indices?.minByOrNull { Geometry.distance(board.controls[it],point) }
            ?.takeIf { Geometry.distance(board.controls[it],point) <= context.endpointTolerance }
        val edge = board?.edgeMidpoints?.indices?.minByOrNull { Geometry.distance(board.edgeMidpoints[it],point) }
            ?.takeIf { Geometry.distance(board.edgeMidpoints[it],point) <= context.endpointTolerance && board.insertedVertex(it) != null }
        val target = when {
            boardControl != null -> SelectionGestureTarget.BoardVertex(board.id, boardControl)
            edge != null -> SelectionGestureTarget.BoardEdge(board.id, edge)
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
                    is HitResult.Board -> Selection.Board(hit.boardId)
                    is HitResult.Opening -> Selection.Opening(hit.openingId)
                    is HitResult.Column -> Selection.Column(hit.columnId)
                    is HitResult.Wall -> Selection.Wall(hit.wallId)
                    else -> null
                })
                when (val selection = editor.state.selection) {
                    is Selection.Board -> SelectionGestureTarget.Board(selection.id)
                    is Selection.Wall -> SelectionGestureTarget.WallBody(selection.id)
                    is Selection.Column -> SelectionGestureTarget.Column(selection.id)
                    is Selection.Opening -> SelectionGestureTarget.Opening(selection.id)
                    is Selection.Dimension -> SelectionGestureTarget.Dimension(selection.id)
                    null -> SelectionGestureTarget.Pan
                }
            }
        }
        // A newly hit entity is only selected on release. Its first drag navigates.
        val tapSelection = editor.state.selection
        val selectOnTap = tapSelection != previousSelection || tapSelection == null
        val effectiveTarget = if (selectOnTap) SelectionGestureTarget.Pan else target
        if (selectOnTap) editor.select(previousSelection)
        if (effectiveTarget != SelectionGestureTarget.Pan && effectiveTarget !is SelectionGestureTarget.FaceDimension) editor.beginEdit()
        if (effectiveTarget is SelectionGestureTarget.BoardEdge) {
            doc.boards[effectiveTarget.id]?.insertedVertex(effectiveTarget.index)?.let { editor.preview(UpdateBoardCommand(it)) }
        }
        editor.updateState(editor.state.copy(selectedBoardVertex = when (effectiveTarget) {
            is SelectionGestureTarget.BoardVertex -> effectiveTarget.index
            is SelectionGestureTarget.BoardEdge -> effectiveTarget.index + 1
            else -> null
        }, dragging = false,
            selectionGesture = SelectionGesture(effectiveTarget, point, context.screenPoint,
                selectOnTap = selectOnTap, tapSelection = tapSelection)))
        return ToolUpdate()
    }
    override fun pointerMove(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        val gesture = editor.state.selectionGesture ?: return ToolUpdate()
        val screenDelta = context.screenPoint - gesture.lastScreen
        val delta = point - gesture.lastWorld
        if (hypot(screenDelta.x, screenDelta.y) > context.dragThreshold) editor.updateState(editor.state.copy(dragging = true))
        if (gesture.target == SelectionGestureTarget.Pan) {
            if (!editor.state.dragging) return ToolUpdate()
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
            is SelectionGestureTarget.BoardEdge -> {
                val original = editor.document.boards[target.id]?.insertedVertex(target.index) ?: return cancelMissingTarget(editor)
                val proposed = original.vertices[target.index+1] + (point - gesture.startWorld)
                val snap = editor.snapEngine.boardVertex(editor.document,proposed,context.snapTolerance,context.gridStep,target.id,
                    original.vertices.filterIndexed { i, _ -> i != target.index+1 })
                original.editedControl(target.index+1, snap.point)?.let { editor.preview(UpdateBoardCommand(it)) }
                editor.updateState(editor.state.copy(snap = snap))
                magnifier = editor.renderDocument.boards[target.id]?.vertices?.getOrNull(target.index+1)
            }
            is SelectionGestureTarget.BoardVertex -> {
                val original = editor.document.boards[target.id] ?: return cancelMissingTarget(editor)
                val proposed = original.controls[target.index] + (point - gesture.startWorld)
                val snap = editor.snapEngine.boardVertex(editor.document,proposed,context.snapTolerance,context.gridStep,target.id,
                    original.controls.filterIndexed { i, _ -> i != target.index })
                original.editedControl(target.index, snap.point)?.let { editor.preview(UpdateBoardCommand(it)) }
                editor.updateState(editor.state.copy(snap = snap))
                magnifier = editor.renderDocument.boards[target.id]?.controls?.getOrNull(target.index)
            }
            is SelectionGestureTarget.Board -> {
                val board = doc.boards[target.id] ?: return cancelMissingTarget(editor)
                editor.preview(UpdateBoardCommand(board.copy(vertices = board.vertices.map { it + delta })))
                magnifier = null
            }
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
                editor.preview(MoveWallEndpointCommand(wall.id, target.start, pointOnGrid, editor.detachMultiWayEndpoints))
                val updated = editor.renderDocument.walls.getValue(wall.id)
                magnifier = if (target.start) updated.start else updated.end
            }
            is SelectionGestureTarget.WallBody -> {
                val wall = doc.walls[target.id] ?: return cancelMissingTarget(editor)
                editor.preview(MoveWallCommand(wall.id, delta))
            }
            is SelectionGestureTarget.Column -> {
                val column = doc.columns[target.id] ?: return cancelMissingTarget(editor)
                // Snapping affects presentation, never the next move's unsnapped position.
                // Use the original center and cumulative pointer motion so small moves can escape.
                val original = editor.document.columns[target.id] ?: return cancelMissingTarget(editor)
                val proposed = original.center + (point - gesture.startWorld)
                val snap = editor.snapEngine.columnCenter(doc, column, proposed, context.columnSnapTolerance)
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
                val accepted = editor.renderDocument.openings.getValue(opening.id)
                val centerLine = WallGeometry.build(editor.renderDocument.walls.getValue(accepted.hostWallId)).centerLine
                magnifier = centerLine.start + Geometry.normalize(centerLine.end - centerLine.start) * accepted.offset
            }
            SelectionGestureTarget.Pan -> Unit
        }
        editor.updateState(editor.state.copy(magnifierTarget = magnifier,
            selectionGesture = gesture.copy(lastWorld = point, lastScreen = context.screenPoint)))
        return ToolUpdate()
    }
    override fun pointerUp(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        val gesture = editor.state.selectionGesture
        val tapped = gesture?.selectOnTap == true && !editor.state.dragging
        val faceDimension = (editor.state.selectionGesture?.target as? SelectionGestureTarget.FaceDimension)
            ?.takeUnless { editor.state.dragging }?.dimension
        val changed = if ((editor.state.dragging || editor.state.selectionGesture?.target is SelectionGestureTarget.BoardEdge) && editor.state.selectionGesture?.target != SelectionGestureTarget.Pan) editor.commitEdit() else { editor.cancelEdit(); false }
        cancel(editor)
        if (tapped) editor.select(gesture?.tapSelection)
        return ToolUpdate(changed, editFaceDimension = faceDimension)
    }
    private fun cancelMissingTarget(editor: SketchEditor): ToolUpdate {
        cancel(editor)
        return ToolUpdate()
    }

    override fun cancel(editor: SketchEditor) {
        if (editor.state.selectionGesture?.target is SelectionGestureTarget.BoardEdge)
            editor.updateState(editor.state.copy(selectedBoardVertex = null))
        editor.cancelEdit()
        editor.updateState(editor.state.copy(dragging = false, snap = null, magnifierTarget = null, selectionGesture = null))
    }
}
