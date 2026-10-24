package com.example.boschlaser

import com.example.boschlaser.shared.editor.PlacementSettings
import com.example.boschlaser.platform.androidSketchDrawingStyle
import com.example.boschlaser.platform.AndroidCanvasDrawing
import com.example.boschlaser.ui.columnDrawingLayout
import com.example.boschlaser.ui.gridStepForScale
import com.example.boschlaser.ui.SketchCanvas
import com.example.boschlaser.ui.SketchCanvasSnapshot
import com.example.boschlaser.ui.SketchMagnifier
import com.example.boschlaser.ui.MagnifierDrawingStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.example.boschlaser.ui.SketchSceneStyle
import com.example.boschlaser.ui.SketchSceneOptions
import com.example.boschlaser.ui.sketchExportViewport
import com.example.boschlaser.ui.drawSketchScene
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import com.example.boschlaser.shared.editor.WallEditOperation
import com.example.boschlaser.shared.editor.WallEditSession
import com.example.boschlaser.shared.editor.WallEditUpdate
import com.example.boschlaser.shared.editor.ToolType
import com.example.boschlaser.shared.editor.SketchPreview
import com.example.boschlaser.shared.editor.Selection
import com.example.boschlaser.shared.editor.SelectionGestureTarget
import com.example.boschlaser.shared.tool.PointerContext

import com.example.boschlaser.shared.command.TrimWallCommand
import com.example.boschlaser.shared.command.ExtendWallCommand
import com.example.boschlaser.shared.command.StraightenWallCommand
import com.example.boschlaser.shared.command.ChangeWallLengthCommand
import com.example.boschlaser.shared.command.ChangeWallThicknessCommand
import com.example.boschlaser.shared.command.ChangeWallAlignmentCommand
import com.example.boschlaser.shared.command.SketchCommand
import com.example.boschlaser.shared.command.WallEditStatus
import com.example.boschlaser.shared.model.WallAnchor
import com.example.boschlaser.shared.model.WallAlignment
import com.example.boschlaser.shared.command.DeleteWallCommand
import com.example.boschlaser.shared.command.DeleteColumnCommand
import com.example.boschlaser.shared.command.DeleteOpeningCommand
import com.example.boschlaser.shared.command.ResizeColumnCommand
import com.example.boschlaser.shared.command.RotateColumnCommand
import com.example.boschlaser.shared.command.ChangeOpeningPropertiesCommand
import com.example.boschlaser.shared.editor.SketchEditor
import com.example.boschlaser.shared.geometry.ColumnGeometry
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.editor.Viewport
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class SketchMode { SELECT, WALL, RECT_COLUMN, CIRCLE_COLUMN, DOOR, WINDOW, MEASURE, SPLIT, DIMENSION }

enum class WallJoinResult { SUCCESS, SAME_WALL, WALL_NOT_FOUND, PARALLEL, OPENING_CONFLICT }

enum class WallExtendResult { SUCCESS, SAME_WALL, WALL_NOT_FOUND, PARALLEL, ALREADY_REACHES, TARGET_MISSED, OPENING_CONFLICT }

sealed class SketchSelection {
    data class Wall(val wall: SketchWall) : SketchSelection()
    data class Column(val column: SketchColumn) : SketchSelection()
    data class Opening(val opening: SketchOpening) : SketchSelection()
    data class Dimension(val id: String) : SketchSelection()
}

class SketchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AbstractComposeView(context, attrs) {
    companion object {
        const val DEFAULT_GRID_DISPLAY_STEP_MM = 100f
        const val MIN_GRID_DISPLAY_STEP_MM = 10f
        const val MAX_GRID_DISPLAY_STEP_MM = 2000f
        const val DEFAULT_MAGNIFIER_ZOOM = 2.6f
        const val MIN_MAGNIFIER_ZOOM = 1f
        const val MAX_MAGNIFIER_ZOOM = 8f
        private const val COLUMN_TOUCH_TOLERANCE_DP = 14f
        private const val COLUMN_ROTATION_HANDLE_OFFSET_DP = 32f
        private const val COLUMN_ROTATION_HANDLE_RADIUS_DP = 10f
    }

    private val renderGeneration = mutableIntStateOf(0)
    private val editor = SketchEditor()
    private val projection = SketchCoreProjection()
    private val walls get() = projection.state(editor.renderDocument).walls
    private val columns get() = projection.state(editor.renderDocument).columns
    private val openings get() = projection.state(editor.renderDocument).openings
    private val mode get() = SketchMode.valueOf(editor.state.tool.name)
    private val selectedWallId get() = (editor.state.selection as? Selection.Wall)?.id
    private val selectedColumnId get() = (editor.state.selection as? Selection.Column)?.id
    private val selectedOpeningId get() = (editor.state.selection as? Selection.Opening)?.id
    private var magnifierTopOffsetPx = 0f
    private var magnifierZoom = DEFAULT_MAGNIFIER_ZOOM
    private val dragging get() = editor.state.dragging
    private var zoomGesture = false
    private val rotatingColumn get() = editor.state.selectionGesture?.target is SelectionGestureTarget.ColumnRotation
    private var defaultDoorWidth = 900f
    private var defaultWindowWidth = 1200f
    private var currentWallThickness = 240f
    private var currentWallControlLine = SketchWallControlLine.CENTER
    private var gridSnapEnabled = true
    private var gridDisplayStepMm = DEFAULT_GRID_DISPLAY_STEP_MM
    private var automaticDimensionsVisible = true
    private var scale = .12f
    private var offsetX = 0f
    private var offsetY = 0f
    private var initializedCamera = false
    private var multiTouchScale = false
    private var openingConflictOnLastWallEdit = false

    var onSelectionChanged: ((SketchSelection?) -> Unit)? = null
    var onStateChanged: (() -> Unit)? = null
    var onDimensionPlaced: (() -> Unit)? = null
    var onFaceDimensionRequested: ((com.example.boschlaser.shared.model.WallDimension) -> Unit)? = null
    var onWallEditUpdated: ((WallEditUpdate) -> Unit)? = null
    var onWallEditSessionChanged: ((WallEditSession?) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = multiTouchScale

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (!multiTouchScale) return false
            val old = scale
            scale = (scale * detector.scaleFactor).coerceIn(.025f, 1.2f)
            val ratio = scale / old
            offsetX = detector.focusX - (detector.focusX - offsetX) * ratio
            offsetY = detector.focusY - (detector.focusY - offsetY) * ratio
            requestRender()
            return true
        }
    })

    private val sharedDrawing = AndroidCanvasDrawing()
    private val wallTextMeasurer by lazy {
        TextMeasurer(createFontFamilyResolver(context.applicationContext),
            Density(resources.displayMetrics.density, resources.configuration.fontScale), LayoutDirection.Ltr, cacheSize = 128)
    }
    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        scaleDetector.isQuickScaleEnabled = false
        setBackgroundColor(Color.WHITE)
        isFocusable = true
        isClickable = true
    }

    fun setMode(value: SketchMode) {
        editor.setTool(ToolType.valueOf(value.name))

        if (value != SketchMode.SELECT) clearSelection()
        requestRender()
    }

    fun startWallEdit(operation: WallEditOperation) {
        editor.startWallEdit(operation)
        requestRender()
        notifySelection()
    }

    fun stopWallEdit() {
        editor.stopWallEdit()
        requestRender()
    }

    fun setGridSnapEnabled(enabled: Boolean) {
        gridSnapEnabled = enabled
    }

    fun gridDisplayStepMm(): Float = gridDisplayStepMm

    fun showAutomaticDimensions(): Boolean = automaticDimensionsVisible

    fun setShowAutomaticDimensions(visible: Boolean) {
        automaticDimensionsVisible = visible
        requestRender()
    }

    fun setGridDisplayStepMm(stepMm: Float): Boolean {
        if (!stepMm.isFinite() || stepMm !in MIN_GRID_DISPLAY_STEP_MM..MAX_GRID_DISPLAY_STEP_MM) return false
        gridDisplayStepMm = stepMm
        requestRender()
        return true
    }

    fun snapshotState() = projection.state(editor.renderDocument)
    fun snapshotDocument() = editor.document
    internal fun renderDocument() = editor.renderDocument
    internal fun editorState() = editor.state
    internal fun worldToScreen(point: Vec2) = Viewport(scale.toDouble(), Vec2(offsetX.toDouble(), offsetY.toDouble())).worldToScreen(point)

    fun cancelGesture() {
        editor.cancelPointer()

        requestRender()
    }

    fun restoreDocument(document: com.example.boschlaser.shared.model.SketchDocument, adoptWallThickness: Boolean = false) {
        editor.restore(document)
        if (adoptWallThickness) {
            val wall = walls.lastOrNull()
            currentWallThickness = wall?.thickness ?: 240f
            currentWallControlLine = wall?.controlLine ?: SketchWallControlLine.CENTER
        }
        clearSelection()
        requestRender()
    }

    fun setMagnifierTopOffset(offsetPx: Float) {
        magnifierTopOffsetPx = offsetPx
        requestRender()
    }

    fun magnifierZoom(): Float = magnifierZoom

    fun setMagnifierZoom(value: Float): Boolean {
        if (!value.isFinite() || value !in MIN_MAGNIFIER_ZOOM..MAX_MAGNIFIER_ZOOM) return false
        magnifierZoom = value
        requestRender()
        return true
    }

    fun restoreState(state: SketchState, adoptWallThickness: Boolean = false) {
        editor.restore(projection.restore(state))
        if (adoptWallThickness) {
            currentWallThickness = state.walls.lastOrNull()?.thickness ?: 240f
            currentWallControlLine = state.walls.lastOrNull()?.controlLine ?: SketchWallControlLine.CENTER
        }
        clearSelection()
        requestRender()
    }

    fun undo(): Boolean {
        if (!editor.undo()) return false
        clearSelection()
        requestRender()
        onStateChanged?.invoke()
        return true
    }

    fun undoPlacement(): Boolean {
        if (mode != SketchMode.WALL && mode != SketchMode.MEASURE && mode != SketchMode.DIMENSION) return undo()
        val previous = editor.document
        if (!editor.undoPlacement()) return false

        if (editor.document != previous) { clearSelection(); onStateChanged?.invoke() }
        requestRender()
        return true
    }

    fun deleteSelected(): Boolean {
        val command = when {
            editor.state.selection is Selection.Dimension -> com.example.boschlaser.shared.command.DeleteWallDimensionCommand(editor.state.selection!!.id)
            selectedWallId != null -> DeleteWallCommand(selectedWallId!!)
            selectedColumnId != null -> DeleteColumnCommand(selectedColumnId!!)
            selectedOpeningId != null -> DeleteOpeningCommand(selectedOpeningId!!)
            else -> return false
        }
        val deleted = editor.execute(command)
        clearSelection()
        if (deleted) {
            requestRender()
            onStateChanged?.invoke()
        }
        return deleted
    }

    fun selectedWall(): SketchWall? = walls.firstOrNull { it.id == selectedWallId }
    internal fun selectedColumn(): SketchColumn? = columns.firstOrNull { it.id == selectedColumnId }

    fun selectedOpening(): SketchOpening? = openings.firstOrNull { it.id == selectedOpeningId }

    internal fun applyDimension(id: String, valueMm: Double): Boolean {
        val dimension = editor.document.dimensions[id] ?: return false
        val oriented = com.example.boschlaser.shared.geometry.WallDimensionGeometry.forMovingSelection(dimension, editor.state.selection) ?: return false
        return applyWallFaceDistance(oriented, valueMm)
    }

    internal fun applyWallFaceDistance(dimension: com.example.boschlaser.shared.model.WallDimension, valueMm: Double): Boolean {
        if (!com.example.boschlaser.shared.geometry.WallDimensionGeometry.matches(dimension.second, editor.state.selection)) return false
        val command = com.example.boschlaser.shared.command.DriveWallFaceDistanceCommand(dimension, valueMm)
        if (command.apply(editor.document) == null) return false
        val changed = editor.execute(command)
        requestRender()
        notifySelection()
        if (changed) onStateChanged?.invoke()
        return true
    }

    fun lastWallEditHadOpeningConflict(): Boolean = openingConflictOnLastWallEdit

    fun joinWallsAtIntersection(
        firstWallId: String,
        firstPickPoint: SketchPoint,
        secondWallId: String,
        secondPickPoint: SketchPoint,
    ): WallJoinResult {
        val command = TrimWallCommand(firstWallId, firstPickPoint.toVec2(), secondWallId, secondPickPoint.toVec2())
        val status = command.plan(editor.renderDocument).status
        val result = when (status) {
            WallEditStatus.SUCCESS -> WallJoinResult.SUCCESS
            WallEditStatus.SAME_WALL -> WallJoinResult.SAME_WALL
            WallEditStatus.WALL_NOT_FOUND -> WallJoinResult.WALL_NOT_FOUND
            WallEditStatus.PARALLEL -> WallJoinResult.PARALLEL
            else -> WallJoinResult.OPENING_CONFLICT
        }
        if (result != WallJoinResult.SUCCESS) return result
        if (!previewWallEdit(command, recordUndo = true)) return WallJoinResult.OPENING_CONFLICT
        editor.select(Selection.Wall(secondWallId))
        notifySelection()
        changed()
        return result
    }

    fun extendWallToWall(
        firstWallId: String,
        targetWallId: String,
        firstPickPoint: SketchPoint? = null,
    ): WallExtendResult {
        val command = ExtendWallCommand(firstWallId, targetWallId, firstPickPoint?.toVec2())
        val status = command.plan(editor.renderDocument).status
        val result = when (status) {
            WallEditStatus.SUCCESS -> WallExtendResult.SUCCESS
            WallEditStatus.SAME_WALL -> WallExtendResult.SAME_WALL
            WallEditStatus.WALL_NOT_FOUND -> WallExtendResult.WALL_NOT_FOUND
            WallEditStatus.PARALLEL -> WallExtendResult.PARALLEL
            WallEditStatus.ALREADY_REACHES -> WallExtendResult.ALREADY_REACHES
            WallEditStatus.TARGET_MISSED -> WallExtendResult.TARGET_MISSED
            else -> WallExtendResult.OPENING_CONFLICT
        }
        if (result != WallExtendResult.SUCCESS) return result
        if (!previewWallEdit(command, recordUndo = true)) return WallExtendResult.OPENING_CONFLICT
        editor.select(Selection.Wall(firstWallId))
        notifySelection()
        changed()
        return result
    }

    fun applySelectedWallLength(lengthMm: Float, moveStart: Boolean): Boolean =
        applySelectedWallLength(lengthMm.toDouble(), moveStart)

    fun applySelectedWallLength(lengthMm: Double, moveStart: Boolean): Boolean {
        openingConflictOnLastWallEdit = false
        if (!lengthMm.isFinite() || lengthMm <= 10.0) return false
        val wall = selectedWall() ?: return false
        val command = ChangeWallLengthCommand(wall.id, lengthMm,
            if (moveStart) WallAnchor.END else WallAnchor.START)
        if (!previewWallEdit(command, recordUndo = true)) return false
        if (mode == SketchMode.WALL) {
            editor.renderDocument.walls[wall.id]?.let { editor.resumeWallChain(it.end) }
        }
        notifySelection()
        changed()
        return true
    }

    fun straightenSelectedWall(moveStart: Boolean): Boolean {
        val wall = selectedWall() ?: return false
        if (!previewWallEdit(StraightenWallCommand(wall.id, moveStart), recordUndo = true)) return false
        notifySelection()
        changed()
        return true
    }

    fun updateSelectedWallThickness(thicknessMm: Float): Boolean = updateSelectedWallThickness(thicknessMm.toDouble())

    fun updateSelectedWallThickness(thicknessMm: Double): Boolean {
        if (!thicknessMm.isFinite() || thicknessMm !in 50.0..1000.0) return false
        val index = walls.indexOfFirst { it.id == selectedWallId }
        if (index < 0) return false
        val wall = walls[index]
        if (!previewWallEdit(ChangeWallThicknessCommand(wall.id, thicknessMm), recordUndo = true)) return false
        currentWallThickness = thicknessMm.toFloat()
        notifySelection(); changed()
        return true
    }

    fun updateSelectedWallControlLine(controlLine: SketchWallControlLine): Boolean {
        val index = walls.indexOfFirst { it.id == selectedWallId }
        if (index < 0) return false
        val wall = walls[index]
        val fixedControlEnd = editor.renderDocument.walls.getValue(wall.id).end
        currentWallControlLine = controlLine
        if (mode == SketchMode.WALL) {
            editor.resumeWallChain(fixedControlEnd)
        }
        if (wall.controlLine == controlLine) return true
        val alignment = when (controlLine) {
            SketchWallControlLine.CENTER -> WallAlignment.CENTER
            SketchWallControlLine.INNER -> WallAlignment.LEFT
            SketchWallControlLine.OUTER -> WallAlignment.RIGHT
        }
        if (!previewWallEdit(ChangeWallAlignmentCommand(wall.id, alignment), recordUndo = true)) return false
        notifySelection(); changed()
        return true
    }

    fun updateSelectedColumnSize(width: Float, depth: Float): Boolean =
        updateSelectedColumnSize(width.toDouble(), depth.toDouble())

    fun updateSelectedColumnSize(width: Double, depth: Double): Boolean {
        val id = selectedColumnId ?: return false
        return applyColumnProperty(ResizeColumnCommand(id, width, depth))
    }

    fun updateSelectedColumnRotation(rotationDegrees: Float): Boolean =
        updateSelectedColumnRotation(rotationDegrees.toDouble())

    fun updateSelectedColumnRotation(rotationDegrees: Double): Boolean {
        val id = selectedColumnId ?: return false
        return applyColumnProperty(RotateColumnCommand(id, rotationDegrees))
    }

    private fun applyColumnProperty(command: SketchCommand): Boolean {
        if (command.apply(editor.renderDocument) == null) return false
        beginEdit()
        editor.preview(command)
        notifySelection(); changed()
        return true
    }

    fun updateSelectedOpening(width: Float, flip: Boolean = false, flipHinge: Boolean = false): Boolean {
        if (!width.isFinite()) return false
        val old = editor.renderDocument.openings[selectedOpeningId] ?: return false
        // Legacy flip buttons supply a Float projection; keep the unchanged semantic width exact.
        val exactWidth = if (width == old.width.toFloat()) old.width else width.toDouble()
        return updateSelectedOpening(exactWidth, flip, flipHinge)
    }

    fun updateSelectedOpening(width: Double, flip: Boolean = false, flipHinge: Boolean = false): Boolean {
        val id = selectedOpeningId ?: return false
        val command = ChangeOpeningPropertiesCommand(id, width, flip, flipHinge)
        if (command.apply(editor.renderDocument) == null) return false
        beginEdit()
        editor.preview(command)
        notifySelection(); changed()
        return true
    }

    fun placementOpeningWidth(type: SketchOpeningType): Float =
        if (type == SketchOpeningType.DOOR) defaultDoorWidth else defaultWindowWidth

    fun updatePlacementOpeningWidth(type: SketchOpeningType, width: Float): Boolean {
        if (!width.isFinite() || width < 100f || width > 5000f) return false
        if (type == SketchOpeningType.DOOR) defaultDoorWidth = width else defaultWindowWidth = width
        return true
    }

    fun exportBitmap(widthPx: Int = 1600, heightPx: Int = 1200): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val viewport = sketchExportViewport(editor.renderDocument, Vec2(widthPx.toDouble(), heightPx.toDouble()))
        drawScene(canvas, viewport, false, false)
        return bitmap
    }

    fun exportPreviewBitmap(): Bitmap = exportBitmap(720, 540)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!initializedCamera && w > 0 && h > 0) {
            offsetX = w / 2f
            offsetY = h / 2f
            initializedCamera = true
        }
        requestRender()
    }

    private fun requestRender() {
        onWallEditSessionChanged?.invoke(editor.state.wallEditSession)
        renderGeneration.intValue++
        invalidate()
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun Content() {
        SketchCanvas(snapshot = {
            renderGeneration.intValue // Draw-phase observation: geometry edits redraw without rebuilding the host tree.
            val density = Density(resources.displayMetrics.density, resources.configuration.fontScale)
            SketchCanvasSnapshot(editor.renderDocument, editor.state, placementSettings(),
                Viewport(scale.toDouble(), Vec2(offsetX.toDouble(), offsetY.toDouble())), sceneDrawingStyle(density),
                SketchSceneOptions(gridDisplayStepMm.toDouble(), showColumnCenter = dragging && !rotatingColumn,
                    rotationHandleOffsetPx = (COLUMN_ROTATION_HANDLE_OFFSET_DP * density.density).toDouble(),
                    showAutomaticDimensions = automaticDimensionsVisible),
                androidx.compose.ui.graphics.Color(context.getColor(R.color.white)),
                MagnifierDrawingStyle(androidx.compose.ui.graphics.Color(context.getColor(R.color.white)),
                    androidx.compose.ui.graphics.Color(context.getColor(R.color.sketch_magnifier_shadow)),
                    androidx.compose.ui.graphics.Color(context.getColor(R.color.sketch_magnifier_border))),
                editor.state.magnifierTarget?.let { SketchMagnifier(it, magnifierZoom.toDouble(), magnifierTopOffsetPx.toDouble()) })
        }, formatWallLength = { resources.getString(R.string.measurement_distance, formatSketchMillimeters(it)) },
            modifier = Modifier.fillMaxSize().testTag("sketch-canvas").pointerInteropFilter { onTouchEvent(it) })
    }

    private fun drawScene(canvas: Canvas, viewport: Viewport, grid: Boolean, selection: Boolean) {
        val density = Density(resources.displayMetrics.density, resources.configuration.fontScale)
        val state = editor.state
        val style = sceneDrawingStyle(density)
        val options = SketchSceneOptions(gridDisplayStepMm.toDouble(), grid, selection, true,
            dragging && !rotatingColumn, (COLUMN_ROTATION_HANDLE_OFFSET_DP * density.density).toDouble(),
            showAutomaticDimensions = automaticDimensionsVisible)
        sharedDrawing.draw(canvas, density) {
            drawSketchScene(editor.renderDocument, state, placementSettings(),
                viewport, Vec2(width.toDouble(), height.toDouble()),
                style, options, wallTextMeasurer) { resources.getString(R.string.measurement_distance, formatSketchMillimeters(it)) }
        }
    }

    private fun placementSettings() = PlacementSettings(currentWallThickness.toDouble(), when (currentWallControlLine) {
        SketchWallControlLine.CENTER -> WallAlignment.CENTER
        SketchWallControlLine.INNER -> WallAlignment.LEFT
        SketchWallControlLine.OUTER -> WallAlignment.RIGHT
    }, defaultDoorWidth.toDouble(), defaultWindowWidth.toDouble())

    private fun sceneDrawingStyle(density: Density): SketchSceneStyle =
        androidSketchDrawingStyle(context, density, COLUMN_ROTATION_HANDLE_RADIUS_DP * density.density)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            multiTouchScale = false
            zoomGesture = false
        }
        if (event.pointerCount > 1 || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            multiTouchScale = true
        }
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount > 1 || scaleDetector.isInProgress || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            zoomGesture = true
            editor.interruptPointerForViewportChange()

            requestRender()
            return true
        }
        if (zoomGesture) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                zoomGesture = false

                multiTouchScale = false
                requestRender()
            }
            return true
        }
        val world = Viewport(scale.toDouble(), Vec2(offsetX.toDouble(), offsetY.toDouble()))
            .screenToWorld(Vec2(event.x.toDouble(), event.y.toDouble()))
        when (mode) {
            SketchMode.WALL -> handlePlacement(event, world)
            SketchMode.RECT_COLUMN, SketchMode.CIRCLE_COLUMN -> handlePlacement(event, world)
            SketchMode.DOOR, SketchMode.WINDOW, SketchMode.MEASURE, SketchMode.SPLIT, SketchMode.DIMENSION -> handlePlacement(event, world)
            SketchMode.SELECT -> if (editor.state.wallEditSession != null) handleWallPick(event) else handleSelect(event, world)
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            multiTouchScale = false
        }
        return true
    }

    private fun handleWallPick(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_UP) return
        val point = Viewport(scale.toDouble(), Vec2(offsetX.toDouble(), offsetY.toDouble()))
            .screenToWorld(Vec2(event.x.toDouble(), event.y.toDouble()))
        val update = editor.pickWallEdit(point, 20.0 / scale.toDouble()) ?: return
        requestRender()
        notifySelection()
        onWallEditUpdated?.invoke(update)
        if (update.documentChanged) onStateChanged?.invoke()
    }

    private fun handlePlacement(event: MotionEvent, world: Vec2) {
        val previousDimensionFirst = editor.state.dimensionFirst
        val previousDimensionPreview = editor.state.preview as? SketchPreview.Dimension
        editor.placement = placementSettings()
        val context = pointerContext(event)
        val update = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> editor.pointerDown(world, context)
            MotionEvent.ACTION_MOVE -> editor.pointerMove(world, context)
            MotionEvent.ACTION_UP -> editor.pointerUp(world, context)
            MotionEvent.ACTION_CANCEL -> { editor.cancelPointer(); null }
            else -> null
        }

        if (update != null) { offsetX += update.panBy.x.toFloat(); offsetY += update.panBy.y.toFloat() }
        if (update?.failure == com.example.boschlaser.shared.tool.ToolFailure.INVALID_SPLIT) {
            android.widget.Toast.makeText(this.context, R.string.sketch_split_invalid, android.widget.Toast.LENGTH_SHORT).show()
        }
        if (update?.failure == com.example.boschlaser.shared.tool.ToolFailure.INVALID_DIMENSION) {
            android.widget.Toast.makeText(this.context, R.string.sketch_dimension_invalid_faces, android.widget.Toast.LENGTH_SHORT).show()
        }
        if (mode == SketchMode.DIMENSION && event.actionMasked == MotionEvent.ACTION_UP && update?.failure == null && update?.documentChanged != true) {
            val hint = when {
                previousDimensionFirst == null && editor.state.dimensionFirst != null -> R.string.sketch_dimension_pick_second
                previousDimensionPreview == null && editor.state.preview is SketchPreview.Dimension -> R.string.sketch_dimension_place
                else -> null
            }
            hint?.let { android.widget.Toast.makeText(this.context, it, android.widget.Toast.LENGTH_SHORT).show() }
        }
        if (update?.documentChanged == true) {
            if (editor.state.selection is Selection.Dimension) onDimensionPlaced?.invoke()
            when (val selection = editor.state.selection) {
                is Selection.Wall -> selectWall(selection.id)
                is Selection.Column -> selectColumn(selection.id)
                is Selection.Opening -> selectOpening(selection.id)
                is Selection.Dimension -> selectEntity(selection)
                null -> clearSelection()
            }
            onStateChanged?.invoke()
        }
        requestRender()
    }

    private fun handleSelect(event: MotionEvent, world: Vec2) {
        val rotationHandleId = if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            findColumnRotationHandle(event.x, event.y)?.id
        } else null
        val context = pointerContext(event, rotationHandleId)
        val update = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> editor.pointerDown(world, context)
            MotionEvent.ACTION_MOVE -> editor.pointerMove(world, context)
            MotionEvent.ACTION_UP -> editor.pointerUp(world, context)
            MotionEvent.ACTION_CANCEL -> { editor.cancelPointer(); null }
            else -> null
        }
        if (update != null) { offsetX += update.panBy.x.toFloat(); offsetY += update.panBy.y.toFloat() }

        if (event.actionMasked != MotionEvent.ACTION_MOVE) notifySelection()
        update?.editFaceDimension?.let { onFaceDimensionRequested?.invoke(it) }
        if (update?.documentChanged == true) onStateChanged?.invoke()
        requestRender()
    }

    private fun pointerContext(event: MotionEvent, rotationHandleId: String? = null) =
        PointerContext(SketchPoint(event.x, event.y).toVec2(),
            (24f / scale).toDouble(), (20f / scale).toDouble(),
            (COLUMN_TOUCH_TOLERANCE_DP * resources.displayMetrics.density / scale).toDouble(),
            (14f / scale).toDouble(), max(18f / scale, 140f).toDouble(), (12f / scale).toDouble(),
            if (gridSnapEnabled) gridDisplayStepForScale(scale).toDouble() else null,
            rotationHandleColumnId = rotationHandleId, viewportScale = scale.toDouble(),
            dragThreshold = if (mode == SketchMode.DIMENSION || editor.state.selectionGesture?.target is SelectionGestureTarget.Dimension ||
                editor.state.selectionGesture?.target is SelectionGestureTarget.FaceDimension)
                android.view.ViewConfiguration.get(this.context).scaledTouchSlop.toDouble() else 2.0,
            dimensionLabelId = if (event.actionMasked == MotionEvent.ACTION_DOWN) findDimensionLabel(event.x, event.y) else null,
            faceDimensionLabelId = if (event.actionMasked == MotionEvent.ACTION_DOWN) findDimensionLabel(event.x, event.y, temporary = true) else null,
            dimensionPreviewLabelHit = event.actionMasked == MotionEvent.ACTION_DOWN && hitsDimensionPreviewLabel(event.x, event.y))

    private fun hitsDimensionPreviewLabel(x: Float, y: Float): Boolean {
        if (mode != SketchMode.DIMENSION) return false
        val dimension = (editor.state.preview as? SketchPreview.Dimension)?.dimension ?: return false
        val density = Density(resources.displayMetrics.density, resources.configuration.fontScale)
        val style = sceneDrawingStyle(density)
        val bounds = com.example.boschlaser.ui.sketchDimensionLabelBounds(editor.document, dimension,
            Viewport(scale.toDouble(), Vec2(offsetX.toDouble(), offsetY.toDouble())), wallTextMeasurer,
            style.label, density, textGapPx = style.dimension.textGapPx) {
            formatSketchMillimeters(it)
        } ?: return false
        return x.toDouble() in bounds.min.x..bounds.max.x && y.toDouble() in bounds.min.y..bounds.max.y
    }

    private fun findDimensionLabel(x: Float, y: Float, temporary: Boolean = false): String? {
        if (mode != SketchMode.SELECT) return null
        if (temporary && !automaticDimensionsVisible) return null
        val density = Density(resources.displayMetrics.density, resources.configuration.fontScale)
        val style = sceneDrawingStyle(density)
        val viewport = Viewport(scale.toDouble(), Vec2(offsetX.toDouble(), offsetY.toDouble()))
        val dimensions = if (temporary) selectedWallId?.let {
            com.example.boschlaser.shared.geometry.WallDimensionGeometry.forSelectedWall(editor.document, it)
        }.orEmpty() else editor.document.dimensions.values.toList().asReversed()
        return dimensions.firstOrNull { dimension ->
            val editable = temporary || com.example.boschlaser.shared.geometry.WallDimensionGeometry.forMovingSelection(dimension, editor.state.selection) != null
            val bounds = com.example.boschlaser.ui.sketchDimensionLabelBounds(editor.document, dimension, viewport,
                wallTextMeasurer, style.label, density, editable = editable, textGapPx = style.dimension.textGapPx) { formatSketchMillimeters(it) }
            bounds != null && x.toDouble() in bounds.min.x..bounds.max.x && y.toDouble() in bounds.min.y..bounds.max.y
        }?.id
    }

    private fun previewWallEdit(command: SketchCommand, recordUndo: Boolean): Boolean {
        openingConflictOnLastWallEdit = false
        if (command.apply(editor.renderDocument) == null) {
            openingConflictOnLastWallEdit = true
            return false
        }
        if (recordUndo) beginEdit()
        editor.preview(command)
        return true
    }

    private fun findColumnRotationHandle(x: Float, y: Float): SketchColumn? {
        if (mode != SketchMode.SELECT) return null
        val column = columns.firstOrNull { it.id == selectedColumnId }
            ?.takeIf { it.type == SketchColumnType.RECTANGLE }
            ?: return null
        val handle = columnRotationHandleScreen(column, scale, offsetX, offsetY)
        val hitRadius = (COLUMN_ROTATION_HANDLE_RADIUS_DP + 10f) * resources.displayMetrics.density
        return column.takeIf { hypot(x - handle.x, y - handle.y) <= hitRadius }
    }

    private fun columnRotationHandleScreen(
        column: SketchColumn,
        drawingScale: Float,
        ox: Float,
        oy: Float,
    ): SketchPoint {
        val entity = editor.renderDocument.columns[column.id]?.takeIf { columns.firstOrNull { it.id == column.id } == column }
            ?: column.toCoreColumn()
        val layout = columnDrawingLayout(entity, Viewport(drawingScale.toDouble(), Vec2(ox.toDouble(), oy.toDouble())),
            (COLUMN_ROTATION_HANDLE_OFFSET_DP * resources.displayMetrics.density).toDouble())
        return (layout.rotationHandle ?: layout.center).toSketchPoint()
    }

    private fun gridDisplayStepForScale(drawingScale: Float): Float =
        gridStepForScale(gridDisplayStepMm.toDouble(), drawingScale).toFloat()

    internal fun selectEntity(selection: Selection?) {
        editor.select(selection)
        notifySelection()
        requestRender()
    }

    private fun selectWall(id: String) = selectEntity(Selection.Wall(id))
    private fun selectColumn(id: String) = selectEntity(Selection.Column(id))
    private fun selectOpening(id: String) = selectEntity(Selection.Opening(id))
    private fun clearSelection(notify: Boolean = true) {
        editor.select(null)
        if (notify) notifySelection()
        requestRender()
    }

    private fun notifySelection() {
        onSelectionChanged?.invoke(when {
            editor.state.selection is Selection.Dimension -> SketchSelection.Dimension(editor.state.selection!!.id)
            selectedWallId != null -> selectedWall()?.let(SketchSelection::Wall)
            selectedColumnId != null -> columns.firstOrNull { it.id == selectedColumnId }?.let(SketchSelection::Column)
            selectedOpeningId != null -> openings.firstOrNull { it.id == selectedOpeningId }?.let(SketchSelection::Opening)
            else -> null
        })
    }

    private fun beginEdit() = editor.beginEdit()

    private fun changed() {
        val committed = editor.commitEdit()
        requestRender()
        if (committed) onStateChanged?.invoke()
    }

}
