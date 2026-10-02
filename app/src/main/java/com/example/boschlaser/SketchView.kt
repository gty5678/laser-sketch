package com.example.boschlaser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

enum class SketchMode { SELECT, WALL, RECT_COLUMN, CIRCLE_COLUMN, DOOR, WINDOW }

enum class WallJoinResult { SUCCESS, SAME_WALL, WALL_NOT_FOUND, PARALLEL, OPENING_CONFLICT }

enum class WallExtendResult { SUCCESS, SAME_WALL, WALL_NOT_FOUND, PARALLEL, ALREADY_REACHES, TARGET_MISSED, OPENING_CONFLICT }

sealed class SketchSelection {
    data class Wall(val wall: SketchWall) : SketchSelection()
    data class Column(val column: SketchColumn) : SketchSelection()
    data class Opening(val opening: SketchOpening) : SketchSelection()
}

class SketchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
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
        private const val COLUMN_ROTATION_SNAP_DEGREES = 15f
    }

    private val walls = mutableListOf<SketchWall>()
    private val columns = mutableListOf<SketchColumn>()
    private val openings = mutableListOf<SketchOpening>()
    private val undo = ArrayDeque<SketchState>()
    private val wallPlacementStarts = ArrayDeque<SketchPoint>()
    private var mode = SketchMode.SELECT
    private var selectedWallId: String? = null
    private var selectedColumnId: String? = null
    private var selectedOpeningId: String? = null
    private var wallStart: SketchPoint? = null
    private var wallPreview: SketchPoint? = null
    private var columnPreviewCenter: SketchPoint? = null
    private var wallChainWasActiveAtDown = false
    private var magnifierTarget: SketchPoint? = null
    private var magnifierTopOffsetPx = 0f
    private var magnifierZoom = DEFAULT_MAGNIFIER_ZOOM
    private var lastWorld = SketchPoint(0f, 0f)
    private var lastScreenX = 0f
    private var lastScreenY = 0f
    private var dragging = false
    private var panning = false
    private var zoomGesture = false
    private var dragEndpoint = -1
    private var rotatingColumn = false
    private var defaultDoorWidth = 900f
    private var defaultWindowWidth = 1200f
    private var currentWallThickness = 240f
    private var currentWallControlLine = SketchWallControlLine.CENTER
    private var gridSnapEnabled = true
    private var gridDisplayStepMm = DEFAULT_GRID_DISPLAY_STEP_MM
    private var scale = .12f
    private var offsetX = 0f
    private var offsetY = 0f
    private var initializedCamera = false
    private var multiTouchScale = false
    private var wallPickMode = false
    private var excludedWallPickId: String? = null
    private var openingConflictOnLastWallEdit = false

    private data class EndpointMove(
        val wallId: String,
        val moveStart: Boolean,
        val newPoint: SketchPoint,
        val measuredLength: Float? = null,
    )

    private data class ConnectedWallEnds(
        val firstJoint: SketchPoint,
        val firstOther: SketchPoint,
        val secondJoint: SketchPoint,
        val secondOther: SketchPoint,
    )

    var onSelectionChanged: ((SketchSelection?) -> Unit)? = null
    var onStateChanged: (() -> Unit)? = null
    var onWallPicked: ((SketchWall, SketchPoint) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = multiTouchScale

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (!multiTouchScale) return false
            val old = scale
            scale = (scale * detector.scaleFactor).coerceIn(.025f, 1.2f)
            val ratio = scale / old
            offsetX = detector.focusX - (detector.focusX - offsetX) * ratio
            offsetY = detector.focusY - (detector.focusY - offsetY) * ratio
            invalidate()
            return true
        }
    })

    private val wallFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(245, 245, 242); style = Paint.Style.FILL }
    private val wallStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(28, 31, 32); style = Paint.Style.STROKE; strokeWidth = 2f }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 122, 92); style = Paint.Style.STROKE; strokeWidth = 4f }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(225, 228, 228); strokeWidth = 1f }
    private val majorGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(200, 205, 205); strokeWidth = 1.5f }
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(29, 94, 145); style = Paint.Style.STROKE; strokeWidth = 3f }
    private val columnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(110, 116, 118); style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(25, 25, 25); textSize = 13f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 122, 92); strokeWidth = 4f; style = Paint.Style.STROKE }
    private val wallCenterLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 122, 92)
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 7f), 0f)
    }
    private val controlPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 122, 92); style = Paint.Style.FILL }
    private val controlPointHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }

    init {
        scaleDetector.isQuickScaleEnabled = false
        setBackgroundColor(Color.WHITE)
        isFocusable = true
        isClickable = true
    }

    fun setMode(value: SketchMode) {
        wallPickMode = false
        excludedWallPickId = null
        mode = value
        wallStart = null
        wallPreview = null
        columnPreviewCenter = null
        wallPlacementStarts.clear()
        magnifierTarget = null
        if (value != SketchMode.SELECT) clearSelection()
        invalidate()
    }

    fun setWallPickMode(enabled: Boolean) {
        wallPickMode = enabled
        excludedWallPickId = null
        if (enabled) {
            mode = SketchMode.SELECT
            clearSelection()
        }
        invalidate()
    }

    fun excludeWallFromNextPick(wallId: String?) {
        excludedWallPickId = wallId
    }

    fun setGridSnapEnabled(enabled: Boolean) {
        gridSnapEnabled = enabled
    }

    fun gridDisplayStepMm(): Float = gridDisplayStepMm

    fun setGridDisplayStepMm(stepMm: Float): Boolean {
        if (!stepMm.isFinite() || stepMm !in MIN_GRID_DISPLAY_STEP_MM..MAX_GRID_DISPLAY_STEP_MM) return false
        gridDisplayStepMm = stepMm
        invalidate()
        return true
    }

    fun snapshotState() = SketchState(walls.toList(), columns.toList(), openings.toList())

    fun setMagnifierTopOffset(offsetPx: Float) {
        magnifierTopOffsetPx = offsetPx
        invalidate()
    }

    fun magnifierZoom(): Float = magnifierZoom

    fun setMagnifierZoom(value: Float): Boolean {
        if (!value.isFinite() || value !in MIN_MAGNIFIER_ZOOM..MAX_MAGNIFIER_ZOOM) return false
        magnifierZoom = value
        invalidate()
        return true
    }

    fun restoreState(state: SketchState, adoptWallThickness: Boolean = false) {
        walls.clear(); walls += state.walls
        columns.clear(); columns += state.columns
        openings.clear(); openings += state.openings.filter { opening -> walls.any { it.id == opening.wallId } }
        if (adoptWallThickness) {
            currentWallThickness = state.walls.lastOrNull()?.thickness ?: 240f
            currentWallControlLine = state.walls.lastOrNull()?.controlLine ?: SketchWallControlLine.CENTER
        }
        clearSelection()
        invalidate()
    }

    fun undo(): Boolean {
        if (undo.isEmpty()) return false
        restoreState(undo.removeLast())
        changed()
        return true
    }

    fun undoPlacement(): Boolean {
        if (mode == SketchMode.WALL) {
            if (wallPlacementStarts.isNotEmpty()) {
                val previousStart = wallPlacementStarts.removeLast()
                if (!undo()) return false
                wallStart = previousStart
                wallPreview = previousStart
                invalidate()
                return true
            }
            if (wallStart != null) {
                wallStart = null
                wallPreview = null
                invalidate()
                return true
            }
        }
        return undo()
    }

    fun deleteSelected(): Boolean {
        pushUndo()
        val changed = when {
            selectedWallId != null -> {
                val id = selectedWallId
                openings.removeAll { it.wallId == id }
                walls.removeAll { it.id == id }
            }
            selectedColumnId != null -> columns.removeAll { it.id == selectedColumnId }
            selectedOpeningId != null -> openings.removeAll { it.id == selectedOpeningId }
            else -> false
        }
        if (!changed && undo.isNotEmpty()) undo.removeLast()
        clearSelection()
        if (changed) changed()
        return changed
    }

    fun selectedWall(): SketchWall? = walls.firstOrNull { it.id == selectedWallId }

    fun selectedOpening(): SketchOpening? = openings.firstOrNull { it.id == selectedOpeningId }

    fun lastWallEditHadOpeningConflict(): Boolean = openingConflictOnLastWallEdit

    fun joinWallsAtIntersection(
        firstWallId: String,
        firstPickPoint: SketchPoint,
        secondWallId: String,
        secondPickPoint: SketchPoint,
    ): WallJoinResult {
        if (firstWallId == secondWallId) return WallJoinResult.SAME_WALL
        val firstIndex = walls.indexOfFirst { it.id == firstWallId }
        val secondIndex = walls.indexOfFirst { it.id == secondWallId }
        if (firstIndex < 0 || secondIndex < 0) return WallJoinResult.WALL_NOT_FOUND
        val first = walls[firstIndex]
        val second = walls[secondIndex]
        val intersection = infiniteWallLineIntersection(first, second) ?: return WallJoinResult.PARALLEL
        val moveFirstStart = shouldMoveWallStartToIntersection(first, intersection, firstPickPoint)
        val moveSecondStart = shouldMoveWallStartToIntersection(second, intersection, secondPickPoint)
        val changes = buildEndpointChanges(
            listOf(
                EndpointMove(first.id, moveFirstStart, intersection),
                EndpointMove(second.id, moveSecondStart, intersection),
            ),
        )
        if (!applyWallChangesPreservingOpenings(changes, recordUndo = true)) {
            return WallJoinResult.OPENING_CONFLICT
        }
        selectedWallId = secondWallId
        notifySelection()
        changed()
        return WallJoinResult.SUCCESS
    }

    fun extendWallToWall(
        firstWallId: String,
        targetWallId: String,
        firstPickPoint: SketchPoint? = null,
    ): WallExtendResult {
        if (firstWallId == targetWallId) return WallExtendResult.SAME_WALL
        val firstIndex = walls.indexOfFirst { it.id == firstWallId }
        val targetIndex = walls.indexOfFirst { it.id == targetWallId }
        if (firstIndex < 0 || targetIndex < 0) return WallExtendResult.WALL_NOT_FOUND
        val first = walls[firstIndex]
        val target = walls[targetIndex]
        val intersection = infiniteWallLineIntersection(first, target) ?: return WallExtendResult.PARALLEL
        val targetPosition = unboundedWallPosition(target, intersection)
        if (targetPosition < -0.0001f || targetPosition > 1.0001f) return WallExtendResult.TARGET_MISSED
        val firstPosition = unboundedWallPosition(first, intersection)
        val moveStart = when {
            firstPosition < -0.0001f -> true
            firstPosition > 1.0001f -> false
            firstPosition <= 0.0001f || firstPosition >= 0.9999f -> return WallExtendResult.ALREADY_REACHES
            firstPickPoint != null -> shouldMoveWallStartToIntersection(first, intersection, firstPickPoint)
            else -> return WallExtendResult.ALREADY_REACHES
        }
        val changes = buildEndpointChanges(listOf(EndpointMove(first.id, moveStart, intersection)))
        if (!applyWallChangesPreservingOpenings(changes, recordUndo = true)) {
            return WallExtendResult.OPENING_CONFLICT
        }
        selectedWallId = first.id
        notifySelection()
        changed()
        return WallExtendResult.SUCCESS
    }

    private fun unboundedWallPosition(wall: SketchWall, point: SketchPoint): Float {
        val (controlStart, controlEnd) = wallControlLinePoints(wall)
        val dx = controlEnd.x - controlStart.x
        val dy = controlEnd.y - controlStart.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared < 1f) return 0f
        return ((point.x - controlStart.x) * dx + (point.y - controlStart.y) * dy) / lengthSquared
    }

    private fun shouldMoveWallStartToIntersection(
        wall: SketchWall,
        intersection: SketchPoint,
        retainedPickPoint: SketchPoint,
    ): Boolean {
        val (controlStart, controlEnd) = wallControlLinePoints(wall)
        val dx = controlEnd.x - controlStart.x
        val dy = controlEnd.y - controlStart.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared < 1f) return false
        val intersectionPosition = (
            (intersection.x - controlStart.x) * dx +
                (intersection.y - controlStart.y) * dy
            ) / lengthSquared
        if (intersectionPosition <= 0f) return true
        if (intersectionPosition >= 1f) return false
        val pickedPosition = (
            (retainedPickPoint.x - controlStart.x) * dx +
                (retainedPickPoint.y - controlStart.y) * dy
            ) / lengthSquared
        return pickedPosition >= intersectionPosition
    }

    fun applySelectedWallLength(lengthMm: Float, moveStart: Boolean): Boolean {
        openingConflictOnLastWallEdit = false
        if (!lengthMm.isFinite() || lengthMm <= 10f) return false
        val index = walls.indexOfFirst { it.id == selectedWallId }
        if (index < 0) return false
        val wall = walls[index]
        val (controlStart, controlEnd) = wallControlLinePoints(wall)
        val dx = controlEnd.x - controlStart.x
        val dy = controlEnd.y - controlStart.y
        val current = hypot(dx, dy)
        if (current < 1f) return false
        val ux = dx / current
        val uy = dy / current
        val newPoint: SketchPoint
        val updated = if (moveStart) {
            newPoint = SketchPoint(controlEnd.x - ux * lengthMm, controlEnd.y - uy * lengthMm)
            wallFromControlLine(wall, newPoint, controlEnd, lengthMm)
        } else {
            newPoint = SketchPoint(controlStart.x + ux * lengthMm, controlStart.y + uy * lengthMm)
            wallFromControlLine(wall, controlStart, newPoint, lengthMm)
        }
        val changes = buildEndpointChanges(
            listOf(EndpointMove(wall.id, moveStart, newPoint, measuredLength = lengthMm)),
        )
        if (!applyWallChangesPreservingOpenings(changes, recordUndo = true)) return false
        if (mode == SketchMode.WALL) {
            wallStart = updated.end
            wallPreview = updated.end
        }
        notifySelection()
        changed()
        return true
    }

    fun straightenSelectedWall(moveStart: Boolean): Boolean {
        val index = walls.indexOfFirst { it.id == selectedWallId }
        if (index < 0) return false
        val wall = walls[index]
        val (controlStart, controlEnd) = wallControlLinePoints(wall)
        val dx = controlEnd.x - controlStart.x
        val dy = controlEnd.y - controlStart.y
        val length = hypot(dx, dy)
        if (length < 1f) return false

        val horizontal = abs(dx) >= abs(dy)
        val direction = if (horizontal) {
            SketchPoint(if (dx >= 0f) 1f else -1f, 0f)
        } else {
            SketchPoint(0f, if (dy >= 0f) 1f else -1f)
        }
        val newPoint: SketchPoint
        if (moveStart) {
            newPoint = SketchPoint(
                controlEnd.x - direction.x * length,
                controlEnd.y - direction.y * length,
            )
        } else {
            newPoint = SketchPoint(
                controlStart.x + direction.x * length,
                controlStart.y + direction.y * length,
            )
        }

        val changes = buildEndpointChanges(listOf(EndpointMove(wall.id, moveStart, newPoint)))
        if (!applyWallChangesPreservingOpenings(changes, recordUndo = true)) return false
        notifySelection()
        changed()
        return true
    }

    fun updateSelectedWallThickness(thicknessMm: Float): Boolean {
        if (!thicknessMm.isFinite() || thicknessMm !in 50f..1000f) return false
        val index = walls.indexOfFirst { it.id == selectedWallId }
        if (index < 0) return false
        val wall = walls[index]
        val (controlStart, controlEnd) = wallControlLinePoints(wall)
        pushUndo()
        walls[index] = wallFromControlLine(
            wall.copy(thickness = thicknessMm),
            controlStart,
            controlEnd,
            wall.measuredLength,
        )
        currentWallThickness = thicknessMm
        notifySelection(); changed()
        return true
    }

    fun updateSelectedWallControlLine(controlLine: SketchWallControlLine): Boolean {
        val index = walls.indexOfFirst { it.id == selectedWallId }
        if (index < 0) return false
        val wall = walls[index]
        val (fixedControlStart, fixedControlEnd) = wallControlLinePoints(wall)
        currentWallControlLine = controlLine
        if (mode == SketchMode.WALL) {
            wallStart = fixedControlEnd
            wallPreview = fixedControlEnd
        }
        if (wall.controlLine == controlLine) return true
        pushUndo()
        walls[index] = wallFromControlLine(
            wall.copy(controlLine = controlLine),
            fixedControlStart,
            fixedControlEnd,
            wall.measuredLength,
        )
        notifySelection(); changed()
        return true
    }

    fun updateSelectedColumnSize(width: Float, depth: Float): Boolean {
        if (!width.isFinite() || !depth.isFinite() || width < 50f || depth < 50f) return false
        val index = columns.indexOfFirst { it.id == selectedColumnId }
        if (index < 0) return false
        pushUndo()
        columns[index] = columns[index].copy(width = width, depth = depth)
        notifySelection(); changed()
        return true
    }

    fun updateSelectedColumnRotation(rotationDegrees: Float): Boolean {
        if (!rotationDegrees.isFinite()) return false
        val index = columns.indexOfFirst { it.id == selectedColumnId }
        if (index < 0 || columns[index].type != SketchColumnType.RECTANGLE) return false
        pushUndo()
        columns[index] = columns[index].copy(rotationDegrees = normalizedDegrees(rotationDegrees))
        notifySelection(); changed()
        return true
    }

    fun updateSelectedOpening(width: Float, flip: Boolean = false, flipHinge: Boolean = false): Boolean {
        if (!width.isFinite() || width < 100f) return false
        val index = openings.indexOfFirst { it.id == selectedOpeningId }
        if (index < 0) return false
        val wall = walls.firstOrNull { it.id == openings[index].wallId } ?: return false
        val wallLength = distance(wall.start, wall.end)
        if (width >= wallLength) return false
        pushUndo()
        val old = openings[index]
        openings[index] = old.copy(
            position = constrainedOpeningPosition(old.position, wallLength, width),
            width = width,
            flipped = if (flip) !old.flipped else old.flipped,
            hingeFlipped = if (flipHinge) !old.hingeFlipped else old.hingeFlipped,
        )
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
        val bounds = contentBounds()
        val margin = 90f
        val exportScale = min((widthPx - margin * 2) / max(1f, bounds.width()), (heightPx - margin * 2) / max(1f, bounds.height()))
            .coerceIn(.03f, .5f)
        val ox = widthPx / 2f - bounds.centerX() * exportScale
        val oy = heightPx / 2f - bounds.centerY() * exportScale
        drawScene(canvas, exportScale, ox, oy, false, false)
        return bitmap
    }

    fun exportPreviewBitmap(): Bitmap = exportBitmap(720, 540)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (!initializedCamera && w > 0 && h > 0) {
            offsetX = w / 2f
            offsetY = h / 2f
            initializedCamera = true
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawScene(canvas, scale, offsetX, offsetY, true, true)
        drawEndpointMagnifier(canvas)
    }

    private fun drawEndpointMagnifier(canvas: Canvas) {
        val target = magnifierTarget ?: return
        val density = resources.displayMetrics.density
        val radius = 72f * density
        val centerX = 16f * density + radius
        val centerY = magnifierTopOffsetPx + 12f * density + radius
        val magnifiedScale = (scale * magnifierZoom).coerceAtMost(1.8f)
        val magnifiedOffsetX = centerX - target.x * magnifiedScale
        val magnifiedOffsetY = centerY - target.y * magnifiedScale
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(55, 0, 0, 0)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 112, 82)
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
        }
        canvas.drawCircle(centerX + 2f * density, centerY + 3f * density, radius + 4f * density, shadowPaint)
        val clip = Path().apply { addCircle(centerX, centerY, radius, Path.Direction.CW) }
        val saveCount = canvas.save()
        canvas.clipPath(clip)
        canvas.drawColor(Color.WHITE)
        drawScene(canvas, magnifiedScale, magnifiedOffsetX, magnifiedOffsetY, true, true)
        canvas.restoreToCount(saveCount)
        canvas.drawCircle(centerX, centerY, radius, borderPaint)
    }

    private fun drawScene(canvas: Canvas, drawingScale: Float, ox: Float, oy: Float, grid: Boolean, selection: Boolean) {
        if (grid) drawGrid(canvas, drawingScale, ox, oy)
        drawWallNetwork(canvas, drawingScale, ox, oy)
        walls.forEach { drawWallDetails(canvas, it, drawingScale, ox, oy, selection && it.id == selectedWallId) }
        columns.forEach { drawColumn(canvas, it, drawingScale, ox, oy, selection && it.id == selectedColumnId) }
        openings.forEach { opening ->
            walls.firstOrNull { it.id == opening.wallId }?.let { wall ->
                drawOpening(canvas, opening, wall, drawingScale, ox, oy, selection && opening.id == selectedOpeningId)
            }
        }
        val showAllWallControls = mode == SketchMode.WALL || (selection && selectedWallId != null)
        if (grid && showAllWallControls) {
            walls.forEach { wall ->
                val (controlStart, controlEnd) = wallControlLinePoints(wall)
                canvas.drawLine(
                    sx(controlStart.x, drawingScale, ox),
                    sy(controlStart.y, drawingScale, oy),
                    sx(controlEnd.x, drawingScale, ox),
                    sy(controlEnd.y, drawingScale, oy),
                    wallCenterLinePaint,
                )
                drawWallControlPoint(canvas, controlStart, drawingScale, ox, oy)
                drawWallControlPoint(canvas, controlEnd, drawingScale, ox, oy)
            }
        }
        if (grid && wallStart != null && wallPreview != null) {
            val start = wallStart!!
            val end = wallPreview!!
            val startX = sx(start.x, drawingScale, ox)
            val startY = sy(start.y, drawingScale, oy)
            if (distance(start, end) < 20f) {
                canvas.drawCircle(startX, startY, 10f, controlPointHaloPaint)
                canvas.drawCircle(startX, startY, 7f, controlPointPaint)
                canvas.drawLine(startX - 14f, startY, startX + 14f, startY, previewPaint)
                canvas.drawLine(startX, startY - 14f, startX, startY + 14f, previewPaint)
            } else {
                val previewWall = SketchWall(
                    id = "preview",
                    start = start,
                    end = end,
                    thickness = currentWallThickness,
                    controlLine = currentWallControlLine,
                ).let { wall -> wallFromControlLine(wall, start, end) }
                drawWall(
                    canvas,
                    previewWall,
                    drawingScale,
                    ox,
                    oy,
                    true,
                )
            }
        }
        if (grid && columnPreviewCenter != null && (mode == SketchMode.RECT_COLUMN || mode == SketchMode.CIRCLE_COLUMN)) {
            val preview = SketchColumn(
                id = "preview",
                type = if (mode == SketchMode.CIRCLE_COLUMN) SketchColumnType.CIRCLE else SketchColumnType.RECTANGLE,
                center = columnPreviewCenter!!,
            )
            drawColumn(canvas, preview, drawingScale, ox, oy, true)
            drawWallControlPoint(canvas, preview.center, drawingScale, ox, oy)
        }
    }

    private fun drawWallControlPoint(canvas: Canvas, point: SketchPoint, drawingScale: Float, ox: Float, oy: Float) {
        val x = sx(point.x, drawingScale, ox)
        val y = sy(point.y, drawingScale, oy)
        canvas.drawCircle(x, y, 8f, controlPointHaloPaint)
        canvas.drawCircle(x, y, 5f, controlPointPaint)
    }

    private fun drawGrid(canvas: Canvas, drawingScale: Float, ox: Float, oy: Float) {
        val stepWorld = gridDisplayStepForScale(drawingScale)
        val majorStepWorld = gridDisplayStepMm * 10f
        val left = -ox / drawingScale
        val right = (width - ox) / drawingScale
        val top = -oy / drawingScale
        val bottom = (height - oy) / drawingScale
        var x = kotlin.math.floor(left / stepWorld).toInt() * stepWorld
        while (x <= right) {
            val major = abs((x / majorStepWorld) - (x / majorStepWorld).roundToInt()) < .01f
            canvas.drawLine(sx(x, drawingScale, ox), 0f, sx(x, drawingScale, ox), height.toFloat(), if (major) majorGridPaint else gridPaint)
            x += stepWorld
        }
        var y = kotlin.math.floor(top / stepWorld).toInt() * stepWorld
        while (y <= bottom) {
            val major = abs((y / majorStepWorld) - (y / majorStepWorld).roundToInt()) < .01f
            canvas.drawLine(0f, sy(y, drawingScale, oy), width.toFloat(), sy(y, drawingScale, oy), if (major) majorGridPaint else gridPaint)
            y += stepWorld
        }
    }

    private fun drawWall(
        canvas: Canvas,
        wall: SketchWall,
        drawingScale: Float,
        ox: Float,
        oy: Float,
        selected: Boolean,
    ) {
        val path = wallBodyPath(wall, drawingScale, ox, oy) ?: return
        canvas.drawPath(path, wallFill)
        canvas.drawPath(path, if (selected) selectedPaint else wallStroke)
        drawWallDetails(canvas, wall, drawingScale, ox, oy, selected)
    }

    private fun drawWallNetwork(canvas: Canvas, drawingScale: Float, ox: Float, oy: Float) {
        var union: Path? = null
        walls.sortedWith(
            compareBy<SketchWall>(
                { min(it.start.x, it.end.x) },
                { min(it.start.y, it.end.y) },
                { max(it.start.x, it.end.x) },
                { max(it.start.y, it.end.y) },
            ),
        ).forEach { wall ->
            val body = wallBodyPath(wall, drawingScale, ox, oy) ?: return@forEach
            val current = union
            if (current == null) union = Path(body)
            else current.op(body, Path.Op.UNION)
        }
        wallJointPaths(drawingScale, ox, oy).forEach { joint ->
            val current = union
            if (current == null) union = Path(joint)
            else current.op(joint, Path.Op.UNION)
        }
        openings.forEach { opening ->
            val wall = walls.firstOrNull { it.id == opening.wallId } ?: return@forEach
            val cutout = openingCutoutPath(opening, wall, drawingScale, ox, oy) ?: return@forEach
            union?.op(cutout, Path.Op.DIFFERENCE)
        }
        union?.let { path ->
            canvas.drawPath(path, wallFill)
            canvas.drawPath(path, wallStroke)
        }
    }

    private fun openingCutoutPath(
        opening: SketchOpening,
        wall: SketchWall,
        drawingScale: Float,
        ox: Float,
        oy: Float,
    ): Path? {
        val dx = wall.end.x - wall.start.x
        val dy = wall.end.y - wall.start.y
        val length = hypot(dx, dy)
        if (length < 1f) return null
        val position = constrainedOpeningPosition(opening.position, length, opening.width)
        val centerX = sx(wall.start.x + dx * position, drawingScale, ox)
        val centerY = sy(wall.start.y + dy * position, drawingScale, oy)
        val ux = dx / length
        val uy = dy / length
        val nx = -uy
        val ny = ux
        val halfOpening = min(opening.width / 2f, length / 2f) * drawingScale
        val halfWall = max(2.5f, wall.thickness * drawingScale / 2f) + 3f
        val firstAlongX = centerX - ux * halfOpening
        val firstAlongY = centerY - uy * halfOpening
        val secondAlongX = centerX + ux * halfOpening
        val secondAlongY = centerY + uy * halfOpening
        return Path().apply {
            moveTo(firstAlongX - nx * halfWall, firstAlongY - ny * halfWall)
            lineTo(secondAlongX - nx * halfWall, secondAlongY - ny * halfWall)
            lineTo(secondAlongX + nx * halfWall, secondAlongY + ny * halfWall)
            lineTo(firstAlongX + nx * halfWall, firstAlongY + ny * halfWall)
            close()
        }
    }

    private fun wallJointPaths(drawingScale: Float, ox: Float, oy: Float): List<Path> {
        val result = mutableListOf<Path>()
        for (firstIndex in walls.indices) {
            for (secondIndex in firstIndex + 1 until walls.size) {
                val first = walls[firstIndex]
                val second = walls[secondIndex]
                val connection = sharedWallEndpoint(first, second) ?: continue
                val firstDx = connection.firstOther.x - connection.firstJoint.x
                val firstDy = connection.firstOther.y - connection.firstJoint.y
                val secondDx = connection.secondOther.x - connection.secondJoint.x
                val secondDy = connection.secondOther.y - connection.secondJoint.y
                val firstLength = hypot(firstDx, firstDy)
                val secondLength = hypot(secondDx, secondDy)
                if (firstLength < 1f || secondLength < 1f) continue
                val rawDot = (
                    firstDx / firstLength * secondDx / secondLength +
                        firstDy / firstLength * secondDy / secondLength
                    ).coerceIn(-1f, 1f)
                // Both vectors point away from their shared endpoint. Keep this directed
                // 0..180° angle: folding it would wrongly treat 105° as 75°.
                val dot = if (abs(rawDot) < .00001f) 0f else rawDot
                val wallAngle = wallEndpointAngle(dot)
                if (wallAngle < Math.toRadians(1.0).toFloat()) continue

                val firstUx = firstDx / firstLength
                val firstUy = firstDy / firstLength
                val secondUx = secondDx / secondLength
                val secondUy = secondDy / secondLength
                val firstNx = -firstUy
                val firstNy = firstUx
                val secondNx = -secondUy
                val secondNy = secondUx
                val firstHalf = max(2.5f, first.thickness * drawingScale / 2f)
                val secondHalf = max(2.5f, second.thickness * drawingScale / 2f)
                val firstJointScreen = SketchPoint(
                    sx(connection.firstJoint.x, drawingScale, ox),
                    sy(connection.firstJoint.y, drawingScale, oy),
                )
                val secondJointScreen = SketchPoint(
                    sx(connection.secondJoint.x, drawingScale, ox),
                    sy(connection.secondJoint.y, drawingScale, oy),
                )
                val firstLeft = SketchPoint(
                    firstJointScreen.x - firstNx * firstHalf,
                    firstJointScreen.y - firstNy * firstHalf,
                )
                val firstRight = SketchPoint(
                    firstJointScreen.x + firstNx * firstHalf,
                    firstJointScreen.y + firstNy * firstHalf,
                )
                val secondLeft = SketchPoint(
                    secondJointScreen.x + secondNx * secondHalf,
                    secondJointScreen.y + secondNy * secondHalf,
                )
                val secondRight = SketchPoint(
                    secondJointScreen.x - secondNx * secondHalf,
                    secondJointScreen.y - secondNy * secondHalf,
                )
                val useExtendedJoin = usesExtendedWallJoin(dot)
                // The first ray is the incoming wall when the two walls are treated as
                // one polyline, so its left/right sides are already reversed above.
                // Matching the same sides is correct for both acute and obtuse joints.
                val firstJoin = if (useExtendedJoin) {
                    lineIntersection(firstLeft, firstUx, firstUy, secondLeft, secondUx, secondUy)
                } else null
                val secondJoin = if (useExtendedJoin) {
                    lineIntersection(firstRight, firstUx, firstUy, secondRight, secondUx, secondUy)
                } else null
                val jointPoints = buildList {
                    add(firstLeft)
                    add(firstRight)
                    add(secondLeft)
                    add(secondRight)
                    firstJoin?.let(::add)
                    secondJoin?.let(::add)
                }
                val jointHull = convexHull(jointPoints)
                if (jointHull.size >= 3) {
                    result += Path().apply {
                        moveTo(jointHull.first().x, jointHull.first().y)
                        jointHull.drop(1).forEach { point -> lineTo(point.x, point.y) }
                        close()
                    }
                }
            }
        }
        return result
    }

    private fun convexHull(points: List<SketchPoint>): List<SketchPoint> {
        val sorted = points.distinct().sortedWith(compareBy<SketchPoint> { it.x }.thenBy { it.y })
        if (sorted.size <= 2) return sorted

        fun cross(origin: SketchPoint, first: SketchPoint, second: SketchPoint): Float =
            (first.x - origin.x) * (second.y - origin.y) -
                (first.y - origin.y) * (second.x - origin.x)

        fun buildHalf(input: List<SketchPoint>): MutableList<SketchPoint> {
            val half = mutableListOf<SketchPoint>()
            input.forEach { point ->
                while (half.size >= 2 && cross(half[half.lastIndex - 1], half.last(), point) <= 0f) {
                    half.removeAt(half.lastIndex)
                }
                half.add(point)
            }
            return half
        }

        val lower = buildHalf(sorted)
        val upper = buildHalf(sorted.asReversed())
        lower.removeAt(lower.lastIndex)
        upper.removeAt(upper.lastIndex)
        return lower + upper
    }

    private fun lineIntersection(
        firstPoint: SketchPoint,
        firstDx: Float,
        firstDy: Float,
        secondPoint: SketchPoint,
        secondDx: Float,
        secondDy: Float,
    ): SketchPoint? {
        val denominator = firstDx * secondDy - firstDy * secondDx
        if (abs(denominator) < .001f) return null
        val betweenX = secondPoint.x - firstPoint.x
        val betweenY = secondPoint.y - firstPoint.y
        val distanceOnFirst = (betweenX * secondDy - betweenY * secondDx) / denominator
        return SketchPoint(
            firstPoint.x + firstDx * distanceOnFirst,
            firstPoint.y + firstDy * distanceOnFirst,
        )
    }

    private fun sharedWallEndpoint(
        first: SketchWall,
        second: SketchWall,
    ): ConnectedWallEnds? {
        val (firstStart, firstEnd) = wallControlLinePoints(first)
        val (secondStart, secondEnd) = wallControlLinePoints(second)
        val firstCandidates = listOf(
            Triple(firstStart, first.start, first.end),
            Triple(firstEnd, first.end, first.start),
        )
        val secondCandidates = listOf(
            Triple(secondStart, second.start, second.end),
            Triple(secondEnd, second.end, second.start),
        )
        firstCandidates.forEach { (firstControl, firstBodyJoint, firstBodyOther) ->
            secondCandidates.forEach { (secondControl, secondBodyJoint, secondBodyOther) ->
                if (distance(firstControl, secondControl) < 12f) {
                    return ConnectedWallEnds(
                        firstBodyJoint,
                        firstBodyOther,
                        secondBodyJoint,
                        secondBodyOther,
                    )
                }
            }
        }
        return null
    }

    private fun wallBodyPath(wall: SketchWall, drawingScale: Float, ox: Float, oy: Float): Path? {
        val (renderStart, renderEnd) = canonicalWallPoints(wall)
        val x1 = sx(renderStart.x, drawingScale, ox); val y1 = sy(renderStart.y, drawingScale, oy)
        val x2 = sx(renderEnd.x, drawingScale, ox); val y2 = sy(renderEnd.y, drawingScale, oy)
        val length = hypot(x2 - x1, y2 - y1)
        if (length < 1f) return null
        val half = max(2.5f, wall.thickness * drawingScale / 2f)
        val ux = (x2 - x1) / length
        val uy = (y2 - y1) / length
        val nx = -uy * half; val ny = ux * half
        val (startLeftExtension, startRightExtension) = endpointIntersectionExtensions(
            wall,
            renderStart,
            SketchPoint(-ux, -uy),
            SketchPoint(-uy, ux),
            drawingScale,
        )
        val (endLeftExtension, endRightExtension) = endpointIntersectionExtensions(
            wall,
            renderEnd,
            SketchPoint(ux, uy),
            SketchPoint(-uy, ux),
            drawingScale,
        )
        return Path().apply {
            moveTo(x1 + nx - ux * startLeftExtension, y1 + ny - uy * startLeftExtension)
            lineTo(x2 + nx + ux * endLeftExtension, y2 + ny + uy * endLeftExtension)
            lineTo(x2 - nx + ux * endRightExtension, y2 - ny + uy * endRightExtension)
            lineTo(x1 - nx - ux * startRightExtension, y1 - ny - uy * startRightExtension)
            close()
        }
    }

    private fun canonicalWallPoints(wall: SketchWall): Pair<SketchPoint, SketchPoint> {
        val startFirst = wall.start.x < wall.end.x ||
            (abs(wall.start.x - wall.end.x) < .001f && wall.start.y <= wall.end.y)
        return if (startFirst) wall.start to wall.end else wall.end to wall.start
    }

    private fun endpointIntersectionExtensions(
        wall: SketchWall,
        point: SketchPoint,
        outwardDirection: SketchPoint,
        bodyNormal: SketchPoint,
        drawingScale: Float,
    ): Pair<Float, Float> {
        var leftExtension = 0f
        var rightExtension = 0f
        val atStart = distance(point, wall.start) <= distance(point, wall.end)
        val (wallControlStart, wallControlEnd) = wallControlLinePoints(wall)
        val controlPoint = if (atStart) wallControlStart else wallControlEnd
        val halfWall = wall.thickness / 2f
        val leftCorner = SketchPoint(
            point.x + bodyNormal.x * halfWall,
            point.y + bodyNormal.y * halfWall,
        )
        val rightCorner = SketchPoint(
            point.x - bodyNormal.x * halfWall,
            point.y - bodyNormal.y * halfWall,
        )
        walls.forEach { other ->
            if (other.id == wall.id) return@forEach
            val (otherStart, otherEnd) = wallControlLinePoints(other)
            val sharesEndpoint = distance(controlPoint, otherStart) < 12f || distance(controlPoint, otherEnd) < 12f
            if (!sharesEndpoint && pointSegmentDistance(controlPoint, otherStart, otherEnd) < 12f) {
                wallCornerExtensionThroughTarget(leftCorner, outwardDirection, other)?.let { required ->
                    leftExtension = max(leftExtension, required * drawingScale)
                }
                wallCornerExtensionThroughTarget(rightCorner, outwardDirection, other)?.let { required ->
                    rightExtension = max(rightExtension, required * drawingScale)
                }
            }
        }
        return leftExtension to rightExtension
    }

    private fun drawWallDetails(canvas: Canvas, wall: SketchWall, drawingScale: Float, ox: Float, oy: Float, selected: Boolean) {
        val x1 = sx(wall.start.x, drawingScale, ox); val y1 = sy(wall.start.y, drawingScale, oy)
        val x2 = sx(wall.end.x, drawingScale, ox); val y2 = sy(wall.end.y, drawingScale, oy)
        val length = hypot(x2 - x1, y2 - y1)
        if (length < 1f) return
        val half = max(2.5f, wall.thickness * drawingScale / 2f)
        val nx = -(y2 - y1) / length * half; val ny = (x2 - x1) / length * half
        val label = wall.measuredLength ?: hypot(wall.end.x - wall.start.x, wall.end.y - wall.start.y)
        val midX = (x1 + x2) / 2f + nx * 1.9f
        val midY = (y1 + y2) / 2f + ny * 1.9f
        canvas.drawText("${label.roundToInt()} mm", midX, midY, textPaint)
        if (selected) {
            val (controlStart, controlEnd) = wallControlLinePoints(wall)
            val controlX1 = sx(controlStart.x, drawingScale, ox)
            val controlY1 = sy(controlStart.y, drawingScale, oy)
            val controlX2 = sx(controlEnd.x, drawingScale, ox)
            val controlY2 = sy(controlEnd.y, drawingScale, oy)
            canvas.drawLine(controlX1, controlY1, controlX2, controlY2, selectedPaint)
            canvas.drawCircle(controlX1, controlY1, 9f, selectedPaint)
            canvas.drawCircle(controlX2, controlY2, 9f, selectedPaint)
        }
    }

    private fun drawColumn(canvas: Canvas, column: SketchColumn, drawingScale: Float, ox: Float, oy: Float, selected: Boolean) {
        val cx = sx(column.center.x, drawingScale, ox); val cy = sy(column.center.y, drawingScale, oy)
        val halfW = max(5f, column.width * drawingScale / 2f)
        val halfD = max(5f, column.depth * drawingScale / 2f)
        if (column.type == SketchColumnType.CIRCLE) canvas.drawCircle(cx, cy, halfW, columnPaint)
        else {
            canvas.save()
            canvas.rotate(column.rotationDegrees, cx, cy)
            canvas.drawRect(cx - halfW, cy - halfD, cx + halfW, cy + halfD, columnPaint)
            canvas.restore()
        }
        if (selected) {
            if (column.type == SketchColumnType.CIRCLE) canvas.drawCircle(cx, cy, halfW + 4f, selectedPaint)
            else {
                canvas.save()
                canvas.rotate(column.rotationDegrees, cx, cy)
                canvas.drawRect(cx - halfW - 4f, cy - halfD - 4f, cx + halfW + 4f, cy + halfD + 4f, selectedPaint)
                canvas.restore()
                if (mode == SketchMode.SELECT) {
                    val handle = columnRotationHandleScreen(column, drawingScale, ox, oy)
                    val radians = Math.toRadians(column.rotationDegrees.toDouble())
                    val topX = cx + sin(radians).toFloat() * halfD
                    val topY = cy - cos(radians).toFloat() * halfD
                    canvas.drawLine(topX, topY, handle.x, handle.y, selectedPaint)
                    canvas.drawCircle(handle.x, handle.y, COLUMN_ROTATION_HANDLE_RADIUS_DP * resources.displayMetrics.density + 3f, controlPointHaloPaint)
                    canvas.drawCircle(handle.x, handle.y, COLUMN_ROTATION_HANDLE_RADIUS_DP * resources.displayMetrics.density, controlPointPaint)
                }
            }
            if (dragging && !rotatingColumn) drawWallControlPoint(canvas, column.center, drawingScale, ox, oy)
        }
    }

    private fun drawOpening(canvas: Canvas, opening: SketchOpening, wall: SketchWall, drawingScale: Float, ox: Float, oy: Float, selected: Boolean) {
        val dx = wall.end.x - wall.start.x; val dy = wall.end.y - wall.start.y
        val length = hypot(dx, dy)
        if (length < 1f) return
        val ux = dx / length; val uy = dy / length
        val position = constrainedOpeningPosition(opening.position, length, opening.width)
        val center = SketchPoint(wall.start.x + dx * position, wall.start.y + dy * position)
        val half = min(opening.width / 2f, length / 2f)
        val a = SketchPoint(center.x - ux * half, center.y - uy * half)
        val b = SketchPoint(center.x + ux * half, center.y + uy * half)
        val paint = if (selected) selectedPaint else symbolPaint
        if (opening.type == SketchOpeningType.WINDOW) {
            val ax = sx(a.x, drawingScale, ox)
            val ay = sy(a.y, drawingScale, oy)
            val bx = sx(b.x, drawingScale, ox)
            val by = sy(b.y, drawingScale, oy)
            val halfWallScreen = max(2.5f, wall.thickness * drawingScale / 2f)
            val thirdOffset = halfWallScreen / 3f
            val innerNx = -uy * thirdOffset
            val innerNy = ux * thirdOffset
            val wallNx = -uy * halfWallScreen
            val wallNy = ux * halfWallScreen
            canvas.drawLine(ax - wallNx, ay - wallNy, bx - wallNx, by - wallNy, wallStroke)
            canvas.drawLine(ax + wallNx, ay + wallNy, bx + wallNx, by + wallNy, wallStroke)
            canvas.drawLine(ax - innerNx, ay - innerNy, bx - innerNx, by - innerNy, paint)
            canvas.drawLine(ax + innerNx, ay + innerNy, bx + innerNx, by + innerNy, paint)
            canvas.drawLine(ax - wallNx, ay - wallNy, ax + wallNx, ay + wallNy, paint)
            canvas.drawLine(bx - wallNx, by - wallNy, bx + wallNx, by + wallNy, paint)
        } else {
            val sideSign = if (opening.flipped) -1f else 1f
            val hingeDirection = if (opening.hingeFlipped) -1f else 1f
            val hinge = if (opening.hingeFlipped) b else a
            val doorWidth = half * 2f
            val leafX = hinge.x - uy * doorWidth * sideSign
            val leafY = hinge.y + ux * doorWidth * sideSign
            val hingeX = sx(hinge.x, drawingScale, ox)
            val hingeY = sy(hinge.y, drawingScale, oy)
            canvas.drawLine(hingeX, hingeY, sx(leafX, drawingScale, ox), sy(leafY, drawingScale, oy), paint)
            val radius = doorWidth * drawingScale
            val rect = RectF(hingeX - radius, hingeY - radius, hingeX + radius, hingeY + radius)
            val startAngle = Math.toDegrees(
                atan2((uy * hingeDirection).toDouble(), (ux * hingeDirection).toDouble()),
            ).toFloat()
            canvas.drawArc(rect, startAngle, 90f * sideSign * hingeDirection, false, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
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
            return true
        }
        if (zoomGesture) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                zoomGesture = false
                dragging = false
                panning = false
                dragEndpoint = -1
                magnifierTarget = null
                multiTouchScale = false
                invalidate()
            }
            return true
        }
        val world = screenToWorld(event.x, event.y)
        when (mode) {
            SketchMode.WALL -> handleWall(event, world)
            SketchMode.RECT_COLUMN, SketchMode.CIRCLE_COLUMN -> handleColumnPlacement(event, world)
            SketchMode.DOOR, SketchMode.WINDOW -> if (event.action == MotionEvent.ACTION_UP) addOpening(world)
            SketchMode.SELECT -> if (wallPickMode) handleWallPick(event) else handleSelect(event, world)
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            multiTouchScale = false
        }
        return true
    }

    private fun handleWallPick(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_UP) return
        val point = screenToWorld(event.x, event.y)
        val wall = walls.asReversed()
            .asSequence()
            .filter { it.id != excludedWallPickId }
            .map { it to pointSegmentDistance(point, it.start, it.end) }
            .filter { (candidate, distance) -> distance <= 20f / scale + candidate.thickness / 2f }
            .minByOrNull { it.second }
            ?.first
            ?: return
        selectWall(wall.id)
        onWallPicked?.invoke(wall, point)
    }

    private fun handleWall(event: MotionEvent, world: SketchPoint) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                wallChainWasActiveAtDown = wallStart != null
                if (wallStart == null) {
                    wallStart = snapToEndpoint(world) ?: snapToGrid(world)
                    wallPreview = wallStart
                } else {
                    val start = wallStart!!
                    wallPreview = snappedDirection(start, snapToEndpoint(world) ?: snapToGrid(world))
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (wallChainWasActiveAtDown) {
                    wallPreview = snappedDirection(wallStart ?: world, snapToEndpoint(world) ?: snapToGrid(world))
                } else {
                    wallStart = snapToEndpoint(world) ?: snapToGrid(world)
                    wallPreview = wallStart
                }
                magnifierTarget = wallPreview
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (!wallChainWasActiveAtDown) {
                    wallStart = snapToEndpoint(world) ?: snapToGrid(world)
                    wallPreview = wallStart
                    magnifierTarget = null
                    invalidate()
                    return
                }
                val start = wallStart ?: return
                val end = snappedDirection(start, snapToEndpoint(world) ?: snapToGrid(world))
                if (distance(start, end) >= 100f) {
                    pushUndo()
                    wallPlacementStarts.addLast(start)
                    val wall = SketchWall(
                        start = start,
                        end = end,
                        thickness = currentWallThickness,
                        controlLine = currentWallControlLine,
                    ).let { candidate -> wallFromControlLine(candidate, start, end) }
                    walls += wall
                    wallStart = end
                    wallPreview = end
                    selectWall(wall.id)
                    changed()
                } else {
                    wallPreview = start
                }
                magnifierTarget = null
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (wallChainWasActiveAtDown) {
                    wallPreview = wallStart
                } else {
                    wallStart = null
                    wallPreview = null
                }
                magnifierTarget = null
                invalidate()
            }
        }
    }

    private fun handleSelect(event: MotionEvent, world: SketchPoint) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastWorld = world
                lastScreenX = event.x
                lastScreenY = event.y
                dragging = false; panning = false; dragEndpoint = -1; rotatingColumn = false
                magnifierTarget = null
                val rotationHandleColumn = findColumnRotationHandle(event.x, event.y)
                val endpointHit = findEndpoint(event.x, event.y)
                val opening = findOpening(event.x, event.y)
                val column = findColumn(event.x, event.y)
                val wall = findWall(event.x, event.y)
                when {
                    rotationHandleColumn != null -> {
                        selectColumn(rotationHandleColumn.id)
                        rotatingColumn = true
                        pushUndo()
                    }
                    endpointHit != null -> { selectWall(endpointHit.first.id); dragEndpoint = endpointHit.second; pushUndo() }
                    opening != null -> { selectOpening(opening.id); pushUndo() }
                    column != null -> { selectColumn(column.id); pushUndo() }
                    wall != null -> { selectWall(wall.id); pushUndo() }
                    else -> { clearSelection(); panning = true }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = world.x - lastWorld.x; val dy = world.y - lastWorld.y
                val screenDx = event.x - lastScreenX
                val screenDy = event.y - lastScreenY
                if (hypot(screenDx, screenDy) > 2f) dragging = true
                if (panning) {
                    offsetX += screenDx
                    offsetY += screenDy
                } else if (dragging && rotatingColumn) {
                    rotateSelectedColumn(world)
                } else if (dragging) moveSelection(dx, dy, world)
                lastWorld = if (panning) screenToWorld(event.x, event.y) else world
                lastScreenX = event.x
                lastScreenY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging && !panning && undo.isNotEmpty()) undo.removeLast()
                if (dragging) changed()
                dragging = false; panning = false; dragEndpoint = -1; rotatingColumn = false
                magnifierTarget = null
                invalidate()
            }
        }
    }

    private fun handleColumnPlacement(event: MotionEvent, world: SketchPoint) {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val preview = SketchColumn(
                    type = if (mode == SketchMode.CIRCLE_COLUMN) SketchColumnType.CIRCLE else SketchColumnType.RECTANGLE,
                    center = world,
                )
                columnPreviewCenter = snappedColumnCenter(preview, world)
                magnifierTarget = columnPreviewCenter
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val preview = SketchColumn(
                    type = if (mode == SketchMode.CIRCLE_COLUMN) SketchColumnType.CIRCLE else SketchColumnType.RECTANGLE,
                    center = world,
                )
                val center = snappedColumnCenter(preview, world)
                addColumn(center)
                columnPreviewCenter = null
                magnifierTarget = null
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                columnPreviewCenter = null
                magnifierTarget = null
                invalidate()
            }
        }
    }

    private fun moveSelection(dx: Float, dy: Float, pointer: SketchPoint) {
        selectedWallId?.let { id ->
            val index = walls.indexOfFirst { it.id == id }
            if (index >= 0) {
                val wall = walls[index]
                if (dragEndpoint >= 0) {
                    val movedControlPoint = snapToGrid(pointer)
                    val oldControlPoint = wallControlLinePoints(wall).let {
                        if (dragEndpoint == 0) it.first else it.second
                    }
                    val connectedWallCount = walls.count { candidate ->
                        val (candidateStart, candidateEnd) = wallControlLinePoints(candidate)
                        distance(candidateStart, oldControlPoint) < 5f ||
                            distance(candidateEnd, oldControlPoint) < 5f
                    }
                    val changes = if (connectedWallCount <= 2) {
                        buildEndpointChanges(
                            listOf(EndpointMove(wall.id, dragEndpoint == 0, movedControlPoint)),
                        )
                    } else {
                        val (controlStart, controlEnd) = wallControlLinePoints(wall)
                        val movedWall = wallFromControlLine(
                            wall,
                            if (dragEndpoint == 0) movedControlPoint else controlStart,
                            if (dragEndpoint == 1) movedControlPoint else controlEnd,
                        )
                        mapOf(wall.id to movedWall)
                    }
                    val applied = applyWallChangesPreservingOpenings(
                        changes,
                        recordUndo = false,
                    )
                    magnifierTarget = if (applied) {
                        walls.getOrNull(index)?.let(::wallControlLinePoints)?.let {
                            if (dragEndpoint == 0) it.first else it.second
                        }
                    } else {
                        oldControlPoint
                    }
                } else {
                    val wallDx = wall.end.x - wall.start.x
                    val wallDy = wall.end.y - wall.start.y
                    val wallLength = hypot(wallDx, wallDy)
                    if (wallLength >= 1f) {
                        val normalX = -wallDy / wallLength
                        val normalY = wallDx / wallLength
                        val normalDistance = dx * normalX + dy * normalY
                        val moveX = normalX * normalDistance
                        val moveY = normalY * normalDistance
                        val movedStart = SketchPoint(wall.start.x + moveX, wall.start.y + moveY)
                        val movedEnd = SketchPoint(wall.end.x + moveX, wall.end.y + moveY)
                        val movedWall = wall.copy(start = movedStart, end = movedEnd)
                        val (movedControlStart, movedControlEnd) = wallControlLinePoints(movedWall)
                        val followerMoves = controlLineFollowerMoves(wall, movedWall)
                        val changes = buildEndpointChanges(
                            listOf(
                                EndpointMove(wall.id, moveStart = true, movedControlStart),
                                EndpointMove(wall.id, moveStart = false, movedControlEnd),
                            ) + followerMoves,
                        )
                        applyWallChangesPreservingOpenings(changes, recordUndo = false)
                    }
                }
            }
        }
        selectedColumnId?.let { id ->
            val index = columns.indexOfFirst { it.id == id }
            if (index >= 0) {
                val column = columns[index]
                val proposedCenter = SketchPoint(column.center.x + dx, column.center.y + dy)
                val movedCenter = snappedColumnCenter(column, proposedCenter)
                columns[index] = column.copy(center = movedCenter)
                magnifierTarget = movedCenter
            }
        }
        selectedOpeningId?.let { id ->
            val index = openings.indexOfFirst { it.id == id }
            if (index >= 0) walls.firstOrNull { it.id == openings[index].wallId }?.let { wall ->
                openings[index] = openings[index].copy(position = projection(pointer, wall, openings[index].width))
            }
        }
        notifySelection()
    }

    private fun addColumn(point: SketchPoint) {
        pushUndo()
        val column = SketchColumn(type = if (mode == SketchMode.CIRCLE_COLUMN) SketchColumnType.CIRCLE else SketchColumnType.RECTANGLE, center = point)
        columns += column; selectColumn(column.id); changed()
    }

    private fun addOpening(point: SketchPoint) {
        val wall = nearestWall(point) ?: return
        val type = if (mode == SketchMode.DOOR) SketchOpeningType.DOOR else SketchOpeningType.WINDOW
        val openingWidth = placementOpeningWidth(type)
        if (openingWidth >= distance(wall.start, wall.end)) return
        pushUndo()
        val opening = SketchOpening(
            type = type,
            wallId = wall.id,
            position = projection(point, wall, openingWidth),
            width = openingWidth,
        )
        openings += opening; selectOpening(opening.id); changed()
    }

    private fun buildEndpointChanges(moves: List<EndpointMove>): Map<String, SketchWall> {
        val explicitWallIds = moves.mapTo(mutableSetOf()) { it.wallId }
        val originals = walls.associateBy { it.id }
        val controlChanges = linkedMapOf<String, Pair<SketchPoint, SketchPoint>>()
        val measuredLengths = mutableMapOf<String, Float?>()
        moves.forEach { move ->
            val original = originals[move.wallId] ?: return@forEach
            val originalControls = wallControlLinePoints(original)
            val oldPoint = if (move.moveStart) originalControls.first else originalControls.second
            val current = controlChanges[move.wallId] ?: originalControls
            controlChanges[move.wallId] = if (move.moveStart) {
                move.newPoint to current.second
            } else {
                current.first to move.newPoint
            }
            measuredLengths[move.wallId] = move.measuredLength
            walls.forEach connectedLoop@{ connected ->
                if (connected.id in explicitWallIds) return@connectedLoop
                val connectedOriginal = wallControlLinePoints(connected)
                val connectedCurrent = controlChanges[connected.id] ?: connectedOriginal
                when {
                    distance(connectedOriginal.first, oldPoint) < 5f -> {
                        controlChanges[connected.id] = move.newPoint to connectedCurrent.second
                        measuredLengths[connected.id] = null
                    }
                    distance(connectedOriginal.second, oldPoint) < 5f -> {
                        controlChanges[connected.id] = connectedCurrent.first to move.newPoint
                        measuredLengths[connected.id] = null
                    }
                }
            }
        }
        return controlChanges.mapValues { (wallId, controls) ->
            wallFromControlLine(
                originals.getValue(wallId),
                controls.first,
                controls.second,
                measuredLengths[wallId],
            )
        }
    }

    /**
     * Finds wall endpoints attached to the interior of a translated wall control line.
     * The attached wall keeps its opposite endpoint, so the moved endpoint follows the
     * new line intersection by extending or shortening along the attached wall's axis.
     */
    private fun controlLineFollowerMoves(
        oldHost: SketchWall,
        newHost: SketchWall,
    ): List<EndpointMove> {
        val result = mutableListOf<EndpointMove>()
        val handledNodes = mutableListOf<SketchPoint>()
        walls.forEach { candidate ->
            if (candidate.id == oldHost.id) return@forEach
            val (candidateStart, candidateEnd) = wallControlLinePoints(candidate)
            val (oldHostStart, oldHostEnd) = wallControlLinePoints(oldHost)
            listOf(true to candidateStart, false to candidateEnd).forEach endpointLoop@{ (moveStart, endpoint) ->
                if (handledNodes.any { distance(it, endpoint) < 5f }) return@endpointLoop
                if (pointSegmentDistance(endpoint, oldHostStart, oldHostEnd) >= 12f) return@endpointLoop
                val hostPosition = unboundedWallPosition(oldHost, endpoint)
                if (hostPosition <= .0001f || hostPosition >= .9999f) return@endpointLoop
                val intersection = infiniteWallLineIntersection(candidate, newHost) ?: return@endpointLoop
                val newHostPosition = unboundedWallPosition(newHost, intersection)
                if (newHostPosition < -.0001f || newHostPosition > 1.0001f) return@endpointLoop
                if (distance(endpoint, intersection) < .001f) return@endpointLoop
                result += EndpointMove(candidate.id, moveStart, intersection)
                handledNodes += endpoint
            }
        }
        return result
    }

    /**
     * Keeps hosted openings at a fixed real-world distance from the endpoint that did not move.
     * A shortening/trim is rejected as one atomic edit if any opening would no longer fit.
     */
    private fun applyWallChangesPreservingOpenings(
        changes: Map<String, SketchWall>,
        recordUndo: Boolean,
    ): Boolean {
        openingConflictOnLastWallEdit = false
        val openingUpdates = mutableMapOf<Int, SketchOpening>()
        changes.forEach { (wallId, newWall) ->
            val oldWall = walls.firstOrNull { it.id == wallId } ?: return@forEach
            val oldLength = distance(oldWall.start, oldWall.end)
            val newLength = distance(newWall.start, newWall.end)
            if (oldLength < 1f || newLength < 1f) {
                openingConflictOnLastWallEdit = true
                return false
            }
            val (oldControlStart, oldControlEnd) = wallControlLinePoints(oldWall)
            val (newControlStart, newControlEnd) = wallControlLinePoints(newWall)
            val startChanged = distance(oldControlStart, newControlStart) >= .001f
            val endChanged = distance(oldControlEnd, newControlEnd) >= .001f
            openings.forEachIndexed { openingIndex, opening ->
                if (opening.wallId != wallId) return@forEachIndexed
                val oldPosition = constrainedOpeningPosition(opening.position, oldLength, opening.width)
                val oldCenterFromStart = oldPosition * oldLength
                val newCenterFromStart = when {
                    startChanged && !endChanged -> newLength - (oldLength - oldCenterFromStart)
                    !startChanged && endChanged -> oldCenterFromStart
                    else -> oldPosition * newLength
                }
                val halfWidth = opening.width / 2f
                if (
                    opening.width >= newLength ||
                    newCenterFromStart < halfWidth - .01f ||
                    newCenterFromStart > newLength - halfWidth + .01f
                ) {
                    openingConflictOnLastWallEdit = true
                    return false
                }
                openingUpdates[openingIndex] = opening.copy(position = newCenterFromStart / newLength)
            }
        }
        if (recordUndo) pushUndo()
        changes.forEach { (wallId, updated) ->
            val index = walls.indexOfFirst { it.id == wallId }
            if (index >= 0) walls[index] = updated
        }
        openingUpdates.forEach { (index, updated) -> openings[index] = updated }
        return true
    }

    private fun findEndpoint(x: Float, y: Float): Pair<SketchWall, Int>? {
        val threshold = 24f / scale
        val point = screenToWorld(x, y)
        selectedWall()?.let { wall ->
            val (controlStart, controlEnd) = wallControlLinePoints(wall)
            if (distance(point, controlStart) <= threshold) return wall to 0
            if (distance(point, controlEnd) <= threshold) return wall to 1
        }
        walls.asReversed().forEach { wall ->
            if (wall.id == selectedWallId) return@forEach
            val (controlStart, controlEnd) = wallControlLinePoints(wall)
            if (distance(point, controlStart) <= threshold) return wall to 0
            if (distance(point, controlEnd) <= threshold) return wall to 1
        }
        return null
    }

    private fun findWall(x: Float, y: Float): SketchWall? {
        val point = screenToWorld(x, y); val threshold = 20f / scale
        return walls.asReversed().firstOrNull { pointSegmentDistance(point, it.start, it.end) <= threshold + it.thickness / 2f }
    }

    private fun findColumn(x: Float, y: Float): SketchColumn? {
        val point = screenToWorld(x, y)
        val tolerance = COLUMN_TOUCH_TOLERANCE_DP * resources.displayMetrics.density / scale
        return columns.asReversed().firstOrNull { column ->
            when (column.type) {
                SketchColumnType.CIRCLE -> distance(point, column.center) <= column.width / 2f + tolerance
                SketchColumnType.RECTANGLE -> {
                    val radians = Math.toRadians(column.rotationDegrees.toDouble())
                    val cosAngle = cos(radians).toFloat()
                    val sinAngle = sin(radians).toFloat()
                    val dx = point.x - column.center.x
                    val dy = point.y - column.center.y
                    val localX = dx * cosAngle + dy * sinAngle
                    val localY = -dx * sinAngle + dy * cosAngle
                    abs(localX) <= column.width / 2f + tolerance &&
                        abs(localY) <= column.depth / 2f + tolerance
                }
            }
        }
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
        val cx = sx(column.center.x, drawingScale, ox)
        val cy = sy(column.center.y, drawingScale, oy)
        val distance = max(5f, column.depth * drawingScale / 2f) +
            COLUMN_ROTATION_HANDLE_OFFSET_DP * resources.displayMetrics.density
        val radians = Math.toRadians(column.rotationDegrees.toDouble())
        return SketchPoint(
            cx + sin(radians).toFloat() * distance,
            cy - cos(radians).toFloat() * distance,
        )
    }

    private fun rotateSelectedColumn(pointer: SketchPoint) {
        val index = columns.indexOfFirst { it.id == selectedColumnId }
        if (index < 0 || columns[index].type != SketchColumnType.RECTANGLE) return
        val column = columns[index]
        val rawDegrees = Math.toDegrees(
            atan2(
                (pointer.y - column.center.y).toDouble(),
                (pointer.x - column.center.x).toDouble(),
            ),
        ).toFloat() + 90f
        val snapped = (rawDegrees / COLUMN_ROTATION_SNAP_DEGREES).roundToInt() * COLUMN_ROTATION_SNAP_DEGREES
        columns[index] = column.copy(rotationDegrees = normalizedDegrees(snapped))
        notifySelection()
    }

    private fun normalizedDegrees(value: Float): Float = ((value % 360f) + 360f) % 360f

    private fun findOpening(x: Float, y: Float): SketchOpening? {
        val point = screenToWorld(x, y)
        val tolerance = 14f / scale
        return openings.asReversed().firstOrNull { opening ->
            walls.firstOrNull { it.id == opening.wallId }?.let { wall ->
                isPointNearOpeningSymbol(point, opening, wall, tolerance)
            } == true
        }
    }

    private fun isPointNearOpeningSymbol(
        point: SketchPoint,
        opening: SketchOpening,
        wall: SketchWall,
        tolerance: Float,
    ): Boolean {
        val dx = wall.end.x - wall.start.x
        val dy = wall.end.y - wall.start.y
        val length = hypot(dx, dy)
        if (length < 1f) return false
        val ux = dx / length
        val uy = dy / length
        val nx = -uy
        val ny = ux
        val position = constrainedOpeningPosition(opening.position, length, opening.width)
        val center = SketchPoint(wall.start.x + dx * position, wall.start.y + dy * position)
        val halfOpening = min(opening.width / 2f, length / 2f)
        val openingWidth = halfOpening * 2f
        val a = SketchPoint(center.x - ux * halfOpening, center.y - uy * halfOpening)
        val b = SketchPoint(center.x + ux * halfOpening, center.y + uy * halfOpening)
        val halfWall = wall.thickness / 2f
        fun offset(base: SketchPoint, normalDistance: Float) = SketchPoint(
            base.x + nx * normalDistance,
            base.y + ny * normalDistance,
        )
        fun nearLine(first: SketchPoint, second: SketchPoint): Boolean =
            pointSegmentDistance(point, first, second) <= tolerance

        if (opening.type == SketchOpeningType.WINDOW) {
            val innerOffset = halfWall / 3f
            return nearLine(offset(a, -halfWall), offset(b, -halfWall)) ||
                nearLine(offset(a, halfWall), offset(b, halfWall)) ||
                nearLine(offset(a, -innerOffset), offset(b, -innerOffset)) ||
                nearLine(offset(a, innerOffset), offset(b, innerOffset)) ||
                nearLine(offset(a, -halfWall), offset(a, halfWall)) ||
                nearLine(offset(b, -halfWall), offset(b, halfWall))
        }

        val sideSign = if (opening.flipped) -1f else 1f
        val hingeDirection = if (opening.hingeFlipped) -1f else 1f
        val hinge = if (opening.hingeFlipped) b else a
        val closedEnd = if (opening.hingeFlipped) a else b
        val leafEnd = offset(hinge, openingWidth * sideSign)
        if (nearLine(hinge, leafEnd) ||
            nearLine(offset(a, -halfWall), offset(a, halfWall)) ||
            nearLine(offset(b, -halfWall), offset(b, halfWall))
        ) return true

        var previous = closedEnd
        for (step in 1..12) {
            val angle = Math.PI.toFloat() * .5f * step / 12f
            val arcPoint = SketchPoint(
                hinge.x + (ux * hingeDirection * cos(angle) + nx * sideSign * sin(angle)) * openingWidth,
                hinge.y + (uy * hingeDirection * cos(angle) + ny * sideSign * sin(angle)) * openingWidth,
            )
            if (nearLine(previous, arcPoint)) return true
            previous = arcPoint
        }
        return false
    }

    private fun nearestWall(point: SketchPoint): SketchWall? = walls.minByOrNull { wall ->
        val (start, end) = wallControlLinePoints(wall)
        pointSegmentDistance(point, start, end)
    }?.takeIf { wall ->
        val (start, end) = wallControlLinePoints(wall)
        pointSegmentDistance(point, start, end) <= 500f
    }

    private fun projection(point: SketchPoint, wall: SketchWall, openingWidth: Float): Float {
        val dx = wall.end.x - wall.start.x; val dy = wall.end.y - wall.start.y
        val length2 = dx * dx + dy * dy
        if (length2 < 1f) return .5f
        val raw = ((point.x - wall.start.x) * dx + (point.y - wall.start.y) * dy) / length2
        return constrainedOpeningPosition(raw, kotlin.math.sqrt(length2), openingWidth)
    }

    private fun constrainedOpeningPosition(position: Float, wallLength: Float, openingWidth: Float): Float {
        if (wallLength < 1f) return .5f
        val halfFraction = (openingWidth / (2f * wallLength)).coerceIn(0f, .5f)
        return position.coerceIn(halfFraction, 1f - halfFraction)
    }

    private fun snappedDirection(start: SketchPoint, raw: SketchPoint): SketchPoint {
        val dx = raw.x - start.x; val dy = raw.y - start.y
        val length = hypot(dx, dy)
        if (length < 1f) return raw
        return when {
            abs(dy) / length < .18f -> SketchPoint(raw.x, start.y)
            abs(dx) / length < .18f -> SketchPoint(start.x, raw.y)
            else -> raw
        }
    }

    private fun snapToEndpoint(point: SketchPoint): SketchPoint? {
        val threshold = max(18f / scale, 140f)
        val endpoint = walls.flatMap { wall ->
            val (start, end) = wallControlLinePoints(wall)
            listOf(start, end)
        }
            .minByOrNull { distance(it, point) }
            ?.takeIf { distance(it, point) <= threshold }
        if (endpoint != null) return endpoint
        val nearest = walls.minByOrNull { wall ->
            val (start, end) = wallControlLinePoints(wall)
            pointSegmentDistance(point, start, end)
        } ?: return null
        val (controlStart, controlEnd) = wallControlLinePoints(nearest)
        val projected = projectToSegment(point, controlStart, controlEnd)
        return projected.takeIf { pointSegmentDistance(point, controlStart, controlEnd) <= threshold }
    }

    private fun snappedColumnCenter(column: SketchColumn, proposedCenter: SketchPoint): SketchPoint {
        val threshold = 12f / scale
        var bestCenterAdjustment: Triple<Float, Float, Float>? = null
        var bestEdgeX: Float? = null
        var bestEdgeY: Float? = null
        walls.forEach { wall ->
            val (controlStart, controlEnd) = wallControlLinePoints(wall)
            val projectedCenter = projectToSegment(proposedCenter, controlStart, controlEnd)
            val centerDistance = distance(proposedCenter, projectedCenter)
            if (centerDistance <= threshold &&
                (bestCenterAdjustment == null || centerDistance < bestCenterAdjustment!!.first)
            ) {
                bestCenterAdjustment = Triple(
                    centerDistance,
                    projectedCenter.x - proposedCenter.x,
                    projectedCenter.y - proposedCenter.y,
                )
            }

            if (column.type != SketchColumnType.RECTANGLE) return@forEach
            val columnRadians = Math.toRadians(column.rotationDegrees.toDouble())
            val columnCos = abs(cos(columnRadians).toFloat())
            val columnSin = abs(sin(columnRadians).toFloat())
            val columnHalfExtentX = columnCos * column.width / 2f + columnSin * column.depth / 2f
            val columnHalfExtentY = columnSin * column.width / 2f + columnCos * column.depth / 2f
            val dx = wall.end.x - wall.start.x
            val dy = wall.end.y - wall.start.y
            val wallLength = hypot(dx, dy)
            if (wallLength < 1f) return@forEach
            val halfWall = wall.thickness / 2f
            if (abs(dy) / wallLength < .02f) {
                val wallCenterY = (wall.start.y + wall.end.y) / 2f
                val wallEdges = floatArrayOf(wallCenterY - halfWall, wallCenterY + halfWall)
                val columnEdges = floatArrayOf(
                    proposedCenter.y - columnHalfExtentY,
                    proposedCenter.y + columnHalfExtentY,
                )
                val wallMinX = min(wall.start.x, wall.end.x)
                val wallMaxX = max(wall.start.x, wall.end.x)
                val columnMinX = proposedCenter.x - columnHalfExtentX
                val columnMaxX = proposedCenter.x + columnHalfExtentX
                if (intervalGap(wallMinX, wallMaxX, columnMinX, columnMaxX) <= threshold) {
                    wallEdges.forEach { wallEdge ->
                        columnEdges.forEach { columnEdge ->
                            val delta = wallEdge - columnEdge
                            if (abs(delta) <= threshold &&
                                (bestEdgeY == null || abs(delta) < abs(bestEdgeY!!))
                            ) {
                                bestEdgeY = delta
                            }
                        }
                    }
                }
            } else if (abs(dx) / wallLength < .02f) {
                val wallCenterX = (wall.start.x + wall.end.x) / 2f
                val wallEdges = floatArrayOf(wallCenterX - halfWall, wallCenterX + halfWall)
                val columnEdges = floatArrayOf(
                    proposedCenter.x - columnHalfExtentX,
                    proposedCenter.x + columnHalfExtentX,
                )
                val wallMinY = min(wall.start.y, wall.end.y)
                val wallMaxY = max(wall.start.y, wall.end.y)
                val columnMinY = proposedCenter.y - columnHalfExtentY
                val columnMaxY = proposedCenter.y + columnHalfExtentY
                if (intervalGap(wallMinY, wallMaxY, columnMinY, columnMaxY) <= threshold) {
                    wallEdges.forEach { wallEdge ->
                        columnEdges.forEach { columnEdge ->
                            val delta = wallEdge - columnEdge
                            if (abs(delta) <= threshold &&
                                (bestEdgeX == null || abs(delta) < abs(bestEdgeX!!))
                            ) {
                                bestEdgeX = delta
                            }
                        }
                    }
                }
            }
        }

        if (bestEdgeX != null && bestEdgeY != null) {
            return SketchPoint(proposedCenter.x + bestEdgeX!!, proposedCenter.y + bestEdgeY!!)
        }

        val edgeAdjustment = when {
            bestEdgeX != null -> Triple(abs(bestEdgeX!!), bestEdgeX!!, 0f)
            bestEdgeY != null -> Triple(abs(bestEdgeY!!), 0f, bestEdgeY!!)
            else -> null
        }
        val adjustment = listOfNotNull(bestCenterAdjustment, edgeAdjustment)
            .minByOrNull { it.first }
            ?: return proposedCenter
        return SketchPoint(proposedCenter.x + adjustment.second, proposedCenter.y + adjustment.third)
    }

    private fun intervalGap(firstMin: Float, firstMax: Float, secondMin: Float, secondMax: Float): Float =
        max(0f, max(firstMin, secondMin) - min(firstMax, secondMax))

    private fun projectToSegment(point: SketchPoint, start: SketchPoint, end: SketchPoint): SketchPoint {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared < 1f) 0f else (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared).coerceIn(0f, 1f)
        return SketchPoint(start.x + dx * t, start.y + dy * t)
    }

    private fun snapToGrid(point: SketchPoint): SketchPoint {
        if (!gridSnapEnabled) return point
        val grid = gridDisplayStepForScale(scale)
        return SketchPoint((point.x / grid).roundToInt() * grid, (point.y / grid).roundToInt() * grid)
    }

    private fun gridDisplayStepForScale(drawingScale: Float): Float = when {
        drawingScale < .05f -> gridDisplayStepMm * 10f
        drawingScale < .12f -> gridDisplayStepMm * 5f
        else -> gridDisplayStepMm
    }

    private fun selectWall(id: String) { selectedWallId = id; selectedColumnId = null; selectedOpeningId = null; notifySelection(); invalidate() }
    private fun selectColumn(id: String) { selectedColumnId = id; selectedWallId = null; selectedOpeningId = null; notifySelection(); invalidate() }
    private fun selectOpening(id: String) { selectedOpeningId = id; selectedWallId = null; selectedColumnId = null; notifySelection(); invalidate() }
    private fun clearSelection(notify: Boolean = true) {
        selectedWallId = null; selectedColumnId = null; selectedOpeningId = null
        if (notify) notifySelection()
        invalidate()
    }

    private fun notifySelection() {
        onSelectionChanged?.invoke(when {
            selectedWallId != null -> selectedWall()?.let(SketchSelection::Wall)
            selectedColumnId != null -> columns.firstOrNull { it.id == selectedColumnId }?.let(SketchSelection::Column)
            selectedOpeningId != null -> openings.firstOrNull { it.id == selectedOpeningId }?.let(SketchSelection::Opening)
            else -> null
        })
    }

    private fun pushUndo() {
        undo.addLast(snapshotState())
        while (undo.size > 50) undo.removeFirst()
    }
    private fun changed() { invalidate(); onStateChanged?.invoke() }

    private fun contentBounds(): RectF {
        val points = walls.flatMap { listOf(it.start, it.end) } + columns.map { it.center }
        if (points.isEmpty()) return RectF(-2500f, -1800f, 2500f, 1800f)
        return RectF(points.minOf { it.x } - 800f, points.minOf { it.y } - 800f, points.maxOf { it.x } + 800f, points.maxOf { it.y } + 800f)
    }

    private fun screenToWorld(x: Float, y: Float) = SketchPoint((x - offsetX) / scale, (y - offsetY) / scale)
    private fun sx(x: Float, drawingScale: Float, ox: Float) = x * drawingScale + ox
    private fun sy(y: Float, drawingScale: Float, oy: Float) = y * drawingScale + oy
    private fun distance(a: SketchPoint, b: SketchPoint) = hypot(a.x - b.x, a.y - b.y)
    private fun pointSegmentDistance(p: SketchPoint, a: SketchPoint, b: SketchPoint): Float {
        val dx = b.x - a.x; val dy = b.y - a.y; val length2 = dx * dx + dy * dy
        if (length2 < 1f) return distance(p, a)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / length2).coerceIn(0f, 1f)
        return distance(p, SketchPoint(a.x + dx * t, a.y + dy * t))
    }
}
