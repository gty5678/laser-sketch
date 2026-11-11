package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.command.CommandManager
import com.example.boschlaser.shared.command.SketchCommand
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.tool.ColumnTool
import com.example.boschlaser.shared.tool.OpeningTool
import com.example.boschlaser.shared.tool.PointerContext
import com.example.boschlaser.shared.tool.SelectTool
import com.example.boschlaser.shared.tool.SketchTool
import com.example.boschlaser.shared.tool.ToolUpdate
import com.example.boschlaser.shared.tool.MeasureTool
import com.example.boschlaser.shared.tool.SplitTool
import com.example.boschlaser.shared.tool.WallTool
import kotlin.random.Random

/** Owns the permanent document. Gesture previews are isolated until one atomic commit. */
class SketchEditor(initial: SketchDocument = SketchDocument(), historyLimit: Int = 50,
    private val idFactory: () -> String = { Random.nextBytes(16).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') } }) {
    private val commands = CommandManager(initial, historyLimit)
    private var preview: SketchDocument? = null
    private val lastMovedWallEndpoints = mutableMapOf<String, Boolean>()

    /** Session preference; translations do not choose between two moving endpoints. */
    fun wallFreeEndpointIsStart(wallId: String): Boolean = lastMovedWallEndpoints[wallId] ?: false

    private fun rememberMovedEndpoints(before: SketchDocument) {
        for ((id, wall) in document.walls) {
            val previous = before.walls[id] ?: continue
            val startMoved = previous.start != wall.start
            val endMoved = previous.end != wall.end
            if (startMoved != endMoved) lastMovedWallEndpoints[id] = startMoved
        }
    }
    val document get() = commands.document
    val renderDocument get() = preview ?: document
    val hasPreview get() = preview != null
    val canUndo get() = commands.canUndo
    val canRedo get() = commands.canRedo
    val snapEngine = SnapEngine()
    val hitTestEngine = HitTestEngine()
    var state = EditorState()
        private set
    var placement = PlacementSettings()
    var continuousWallDrawing: Boolean = true
    private val wallTool = WallTool()
    private val tools: Map<ToolType, SketchTool> = mapOf(
        ToolType.BOARD to com.example.boschlaser.shared.tool.BoardTool(),
        ToolType.SELECT to SelectTool(), ToolType.PAN to SelectTool(), ToolType.WALL to wallTool,
        ToolType.RECT_COLUMN to ColumnTool(false), ToolType.CIRCLE_COLUMN to ColumnTool(true),
        ToolType.DOOR to OpeningTool(false), ToolType.WINDOW to OpeningTool(true),
        ToolType.MEASURE to MeasureTool(), ToolType.SPLIT to SplitTool(),
        ToolType.DIMENSION to com.example.boschlaser.shared.tool.DimensionTool(),
    )
    internal fun nextId() = idFactory()
    fun setAngularDimension(enabled: Boolean) {
        cancelPointer()
        state = state.copy(angularDimension = enabled)
    }

    internal fun updateState(updated: EditorState) { state = updated }
    fun select(selection: Selection?) {
        val valid = SelectionManager.valid(renderDocument, selection)
        val board = (valid as? Selection.Board)?.let { renderDocument.boards[it.id] }
        state = state.copy(selection = valid, selectedBoardVertex = state.selectedBoardVertex?.takeIf {
            valid == state.selection && board?.shape == com.example.boschlaser.shared.model.BoardShape.POLYGON && it in board.vertices.indices
        })
    }
    /** OK closes the confirmed polygon; incomplete one/two-point drafts are discarded. */
    fun finishBoardPlacement(): com.example.boschlaser.shared.tool.ToolUpdate {
        if (state.tool != ToolType.BOARD) return com.example.boschlaser.shared.tool.ToolUpdate()
        var changed = false
        val points = state.boardPoints
        if (placement.boardShape == com.example.boschlaser.shared.model.BoardShape.POLYGON && points.size >= 3) {
            if (!com.example.boschlaser.shared.model.Board.valid(points)) return com.example.boschlaser.shared.tool.ToolUpdate(
                failure = com.example.boschlaser.shared.tool.ToolFailure.INVALID_BOARD)
            changed = execute(com.example.boschlaser.shared.command.AddBoardCommand(
                com.example.boschlaser.shared.model.Board(nextId(),points,placement.boardFill,placement.boardShape)))
        }
        cancelPointer()
        return com.example.boschlaser.shared.tool.ToolUpdate(changed)
    }
    fun deleteSelectedBoardVertex(): Boolean {
        val selected = state.selection as? Selection.Board ?: return false
        val index = state.selectedBoardVertex ?: return false
        val board = document.boards[selected.id] ?: return false
        val updated = board.removedVertex(index) ?: return false
        val changed = execute(com.example.boschlaser.shared.command.UpdateBoardCommand(updated))
        if (changed) state = state.copy(selectedBoardVertex = null)
        return changed
    }
    fun setTool(tool: ToolType) {
        cancelPointer()
        wallTool.resetChain(this)
        state = state.copy(tool = tool, selectedBoardVertex = null, selection = if (tool == ToolType.SELECT) state.selection else null,
            preview = null, snap = null, wallAnchor = null, wallEditSession = null)
    }
    fun startWallEdit(operation: WallEditOperation) {
        setTool(ToolType.SELECT)
        state = state.copy(selection = null, wallEditSession = WallEditSession(operation))
    }
    var detachMultiWayEndpoints: Boolean = true
    fun previewWallEdit(point: Vec2, tolerance: Double): Boolean {
        cancelEdit()
        val session = state.wallEditSession ?: return false
        val first = session.first ?: return false
        val second = pickWallForEdit(point, tolerance) ?: return false
        val plan = when (session.operation) {
            WallEditOperation.TRIM -> com.example.boschlaser.shared.command.TrimWallCommand(first.wallId, first.point, second, point).plan(document)
            WallEditOperation.EXTEND -> com.example.boschlaser.shared.command.ExtendWallCommand(first.wallId, second, first.point).plan(document)
        }
        val command = plan.command ?: return false
        val updated = command.apply(document) ?: return false
        val removed = document.walls.values.flatMap { before ->
            val after = updated.walls[before.id] ?: return@flatMap emptyList()
            val line = com.example.boschlaser.shared.geometry.Segment2(before.start, before.end)
            buildList {
                if (before.start != after.start && com.example.boschlaser.shared.geometry.Geometry.parameter(after.start, line) in 0.0..1.0)
                    add(com.example.boschlaser.shared.geometry.Segment2(before.start, after.start))
                if (before.end != after.end && com.example.boschlaser.shared.geometry.Geometry.parameter(after.end, line) in 0.0..1.0)
                    add(com.example.boschlaser.shared.geometry.Segment2(after.end, before.end))
            }
        }
        state = state.copy(wallEditRemovedParts = removed)
        return preview(command)
    }
    fun stopWallEdit() { cancelEdit(); state = state.copy(wallEditSession = null) }
    fun pickWallEdit(point: Vec2, tolerance: Double): WallEditUpdate? {
        if (state.wallEditSession == null) return null
        cancelEdit()
        val wallId = pickWallForEdit(point, tolerance) ?: return null
        return applyWallEditPick(wallId, point)
    }
    fun pickWallEdit(wallId: String, point: Vec2): WallEditUpdate? {
        cancelEdit()
        return applyWallEditPick(wallId, point)
    }
    fun resumeWallChain(point: Vec2) { wallTool.resume(this, point) }
    fun pointerDown(point: Vec2, context: PointerContext) = tools.getValue(state.tool).pointerDown(this, point, context)
    fun pointerMove(point: Vec2, context: PointerContext) = tools.getValue(state.tool).pointerMove(this, point, context)
    fun pointerUp(point: Vec2, context: PointerContext) = tools.getValue(state.tool).pointerUp(this, point, context)
    fun cancelPointer() {
        tools.getValue(state.tool).cancel(this)
        cancelEdit()
        state = state.copy(magnifierTarget = null, dragging = false)
    }

    /** View navigation interrupts the touch, but keeps the dimension's multi-step creation session. */
    fun interruptPointerForViewportChange() {
        if (state.tool != ToolType.DIMENSION) {
            cancelPointer()
            return
        }
        cancelEdit()
        state = state.copy(snap = null, magnifierTarget = null, dragging = false, dimensionGesture = null)
    }
    fun undoPlacement(): Boolean = when (state.tool) {
        ToolType.BOARD -> if (state.boardPoints.isNotEmpty()) {
            val points = state.boardPoints.dropLast(1)
            state = state.copy(boardPoints = points, preview = points.takeIf { it.isNotEmpty() }?.let { SketchPreview.Board(it) })
            true
        } else undo()
        ToolType.WALL -> wallTool.undoPlacement(this)
        ToolType.MEASURE -> {
            val hadMeasurement = state.preview is SketchPreview.Measurement
            cancelPointer()
            hadMeasurement
        }
        ToolType.DIMENSION -> if (state.dimensionFirst != null) { cancelPointer(); true } else undo()
        else -> undo()
    }

    fun beginEdit() {
        if (preview == null) preview = document
    }
    fun preview(command: SketchCommand): Boolean {
        val current = renderDocument
        val updated = command.apply(current) ?: return false
        if (updated == current) return false
        preview = updated
        return true
    }
    fun commitEdit(): Boolean {
        val approved = preview
        preview = null
        clearSelectionGesture()
        if (approved == null) return false
        val before = document
        val changed = commands.execute(SketchCommand { approved })
        if (changed) rememberMovedEndpoints(before)
        select(state.selection)
        return changed
    }
    fun cancelEdit() {
        state = state.copy(wallEditRemovedParts = emptyList())
        preview = null
        clearSelectionGesture()
        select(state.selection)
    }
    private fun clearSelectionGesture() {
        if (state.selectionGesture != null) {
            state = state.copy(selectionGesture = null, dragging = false, snap = null, magnifierTarget = null)
        }
    }
    fun execute(command: SketchCommand): Boolean {
        cancelEdit()
        val before = document
        val changed = commands.execute(command)
        if (changed) rememberMovedEndpoints(before)
        select(state.selection)
        return changed
    }
    fun undo(): Boolean { cancelEdit(); val changed = commands.undo(); select(null); return changed }
    fun redo(): Boolean { cancelEdit(); val changed = commands.redo(); select(null); return changed }
    fun restore(document: SketchDocument) {
        cancelPointer()
        commands.replace(document)
        lastMovedWallEndpoints.clear()
        wallTool.resetChain(this)
        state = state.copy(selection = null, preview = null, wallAnchor = null, wallEditSession = null)
    }
}
