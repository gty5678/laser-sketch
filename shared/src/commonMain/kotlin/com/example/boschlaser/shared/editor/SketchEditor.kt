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
    private val wallTool = WallTool()
    private val tools: Map<ToolType, SketchTool> = mapOf(
        ToolType.SELECT to SelectTool(), ToolType.WALL to wallTool,
        ToolType.RECT_COLUMN to ColumnTool(false), ToolType.CIRCLE_COLUMN to ColumnTool(true),
        ToolType.DOOR to OpeningTool(false), ToolType.WINDOW to OpeningTool(true),
        ToolType.MEASURE to MeasureTool(), ToolType.SPLIT to SplitTool(),
        ToolType.DIMENSION to com.example.boschlaser.shared.tool.DimensionTool(),
    )
    internal fun nextId() = idFactory()
    internal fun updateState(updated: EditorState) { state = updated }
    fun select(selection: Selection?) { state = state.copy(selection = SelectionManager.valid(renderDocument, selection)) }
    fun setTool(tool: ToolType) {
        cancelPointer()
        wallTool.resetChain(this)
        state = state.copy(tool = tool, selection = if (tool == ToolType.SELECT) state.selection else null,
            preview = null, snap = null, wallAnchor = null, wallEditSession = null)
    }
    fun startWallEdit(operation: WallEditOperation) {
        setTool(ToolType.SELECT)
        state = state.copy(selection = null, wallEditSession = WallEditSession(operation))
    }
    fun stopWallEdit() { state = state.copy(wallEditSession = null) }
    fun pickWallEdit(point: Vec2, tolerance: Double): WallEditUpdate? {
        if (state.wallEditSession == null) return null
        val wallId = pickWallForEdit(point, tolerance) ?: return null
        return applyWallEditPick(wallId, point)
    }
    fun pickWallEdit(wallId: String, point: Vec2): WallEditUpdate? = applyWallEditPick(wallId, point)
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
        val changed = commands.execute(SketchCommand { approved })
        select(state.selection)
        return changed
    }
    fun cancelEdit() {
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
        val changed = commands.execute(command)
        select(state.selection)
        return changed
    }
    fun undo(): Boolean { cancelEdit(); val changed = commands.undo(); select(null); return changed }
    fun redo(): Boolean { cancelEdit(); val changed = commands.redo(); select(null); return changed }
    fun restore(document: SketchDocument) {
        cancelPointer()
        commands.replace(document)
        wallTool.resetChain(this)
        state = state.copy(selection = null, preview = null, wallAnchor = null, wallEditSession = null)
    }
}
