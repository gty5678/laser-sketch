package com.example.boschlaser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

data class DimensionMark(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val label: String,
    val color: Int = Color.rgb(214, 32, 42),
    val thickness: Int = 1,
)

data class TextMark(
    val x: Float,
    val y: Float,
    val text: String,
    val color: Int = Color.rgb(214, 32, 42),
    val textSize: Int = 1,
    val whiteBackground: Boolean = true,
)

data class AngleMark(
    val vertexX: Float,
    val vertexY: Float,
    val firstX: Float,
    val firstY: Float,
    val secondX: Float,
    val secondY: Float,
    val label: String = "",
    val color: Int = Color.rgb(214, 32, 42),
)

data class AreaPoint(val x: Float, val y: Float)

data class AreaMark(
    val points: List<AreaPoint>,
    val label: String = "",
    val color: Int = Color.rgb(214, 32, 42),
)

enum class AnnotationMode { MOVE, DIMENSION, TEXT, ANGLE, AREA }

sealed class AnnotationSelection {
    data class Dimension(val mark: DimensionMark) : AnnotationSelection()
    data class Text(val mark: TextMark) : AnnotationSelection()
    data class Angle(val mark: AngleMark) : AnnotationSelection()
    data class Area(val mark: AreaMark, val selectedPointIndex: Int) : AnnotationSelection()
}

class ImageAnnotationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var bitmap: Bitmap? = null
    private val marks = mutableListOf<DimensionMark>()
    private val textMarks = mutableListOf<TextMark>()
    private val angleMarks = mutableListOf<AngleMark>()
    private val areaMarks = mutableListOf<AreaMark>()
    private val imageRect = RectF()
    private var dragStartX: Float? = null
    private var dragStartY: Float? = null
    private var dragEndX: Float? = null
    private var dragEndY: Float? = null
    private var selectedIndex = -1
    private var selectedTextIndex = -1
    private var selectedAngleIndex = -1
    private var editingEndpoint = -1
    private var endpointMoved = false
    private var magnifierFocusX: Float? = null
    private var magnifierFocusY: Float? = null
    private var draggingTextIndex = -1
    private var textTouchStartX = 0f
    private var textTouchStartY = 0f
    private var textTouchMoved = false
    private var editingAnglePoint = -1
    private var anglePointMoved = false
    private var angleTouchStartX = 0f
    private var angleTouchStartY = 0f
    private val pendingAnglePoints = mutableListOf<Pair<Float, Float>>()
    private val pendingAreaPoints = mutableListOf<AreaPoint>()
    private var selectedAreaIndex = -1
    private var selectedAreaPointIndex = -1
    private var editingAreaPoint = -1
    private var areaPointMoved = false
    private var areaTouchStartX = 0f
    private var areaTouchStartY = 0f
    private var lastAreaEdgeTapAt = 0L
    private var lastAreaEdgeTapArea = -1
    private var lastAreaEdgeTapIndex = -1
    private var activeAreaBuildIndex = -1
    private var mode = AnnotationMode.DIMENSION
    private enum class MoveKind { NONE, DIMENSION, TEXT, ANGLE, AREA }
    private var moveKind = MoveKind.NONE
    private var moveIndex = -1
    private var moveTouchStartX = 0f
    private var moveTouchStartY = 0f
    private var moveStarted = false
    private var moveDimensionOriginal: DimensionMark? = null
    private var moveTextOriginal: TextMark? = null
    private var moveAngleOriginal: AngleMark? = null
    private var moveAreaOriginal: AreaMark? = null

    var onMessage: ((String) -> Unit)? = null
    var onSelectionChanged: ((AnnotationSelection?) -> Unit)? = null
    var onCanvasTouched: (() -> Unit)? = null
    var onManipulationStarted: (() -> Unit)? = null
    var defaultDimensionLabel: String = ""

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(214, 32, 42)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(180, 0, 12)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val textBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val areaFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 188, 212)
        style = Paint.Style.FILL
    }
    private val selectionHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val magnifierBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(32, 34, 37)
        style = Paint.Style.FILL
    }
    private val magnifierBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
    }
    private val magnifierCrosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 188, 212)
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }

    fun setBitmap(value: Bitmap) {
        bitmap = value
        marks.clear()
        textMarks.clear()
        angleMarks.clear()
        areaMarks.clear()
        selectedIndex = -1
        selectedTextIndex = -1
        selectedAngleIndex = -1
        selectedAreaIndex = -1
        selectedAreaPointIndex = -1
        pendingAnglePoints.clear()
        pendingAreaPoints.clear()
        activeAreaBuildIndex = -1
        magnifierFocusX = null
        magnifierFocusY = null
        resetMoveTarget()
        clearPreview()
        notifySelectionChanged()
        requestLayout()
        invalidate()
    }

    fun snapshotState(): AnnotationState = AnnotationState(
        dimensions = marks.toList(),
        texts = textMarks.toList(),
        angles = angleMarks.toList(),
        areas = areaMarks.map { it.copy(points = it.points.toList()) },
    )

    fun restoreState(state: AnnotationState) {
        marks.clear()
        marks += state.dimensions
        textMarks.clear()
        textMarks += state.texts
        angleMarks.clear()
        angleMarks += state.angles
        areaMarks.clear()
        areaMarks += state.areas.filter { it.points.size >= 3 }
        selectedIndex = -1
        selectedTextIndex = -1
        selectedAngleIndex = -1
        selectedAreaIndex = -1
        selectedAreaPointIndex = -1
        pendingAnglePoints.clear()
        pendingAreaPoints.clear()
        activeAreaBuildIndex = -1
        resetMoveTarget()
        clearPreview()
        notifySelectionChanged()
        invalidate()
    }

    fun updateSelectedLabel(label: String): Boolean {
        if (selectedIndex !in marks.indices) return false
        marks[selectedIndex] = marks[selectedIndex].copy(label = label.trim())
        invalidate()
        return true
    }

    fun setMode(value: AnnotationMode) {
        if (mode == value) {
            if (value == AnnotationMode.AREA && activeAreaBuildIndex in areaMarks.indices) {
                activeAreaBuildIndex = -1
                selectedAreaPointIndex = -1
                notifySelectionChanged()
                onMessage?.invoke("面积绘制已完成；现在可双击边线添加中间控制点")
                invalidate()
            }
            return
        }
        mode = value
        selectedIndex = -1
        selectedTextIndex = -1
        selectedAngleIndex = -1
        selectedAreaIndex = -1
        selectedAreaPointIndex = -1
        pendingAnglePoints.clear()
        pendingAreaPoints.clear()
        activeAreaBuildIndex = -1
        magnifierFocusX = null
        magnifierFocusY = null
        resetMoveTarget()
        clearPreview()
        notifySelectionChanged()
        invalidate()
    }

    fun updateSelectedText(text: String): Boolean {
        if (selectedTextIndex !in textMarks.indices) return false
        textMarks[selectedTextIndex] = textMarks[selectedTextIndex].copy(text = text)
        invalidate()
        return true
    }

    fun updateSelectedAngleLabel(label: String): Boolean {
        if (selectedAngleIndex !in angleMarks.indices) return false
        angleMarks[selectedAngleIndex] = angleMarks[selectedAngleIndex].copy(label = label.trim())
        invalidate()
        return true
    }

    fun updateSelectedAreaLabel(label: String): Boolean {
        if (selectedAreaIndex !in areaMarks.indices) return false
        areaMarks[selectedAreaIndex] = areaMarks[selectedAreaIndex].copy(label = label.trim())
        invalidate()
        return true
    }

    fun updateSelectedTextSize(textSize: Int): Boolean {
        if (selectedTextIndex !in textMarks.indices) return false
        textMarks[selectedTextIndex] = textMarks[selectedTextIndex].copy(textSize = textSize.coerceIn(0, 2))
        invalidate()
        return true
    }

    fun updateSelectedTextBackground(enabled: Boolean): Boolean {
        if (selectedTextIndex !in textMarks.indices) return false
        textMarks[selectedTextIndex] = textMarks[selectedTextIndex].copy(whiteBackground = enabled)
        invalidate()
        return true
    }

    fun updateSelectedLineThickness(thickness: Int): Boolean {
        if (selectedIndex !in marks.indices) return false
        marks[selectedIndex] = marks[selectedIndex].copy(thickness = thickness.coerceIn(0, 2))
        invalidate()
        return true
    }

    fun updateSelectedColor(color: Int): Boolean {
        when {
            selectedIndex in marks.indices -> marks[selectedIndex] = marks[selectedIndex].copy(color = color)
            selectedTextIndex in textMarks.indices -> textMarks[selectedTextIndex] =
                textMarks[selectedTextIndex].copy(color = color)
            selectedAngleIndex in angleMarks.indices -> angleMarks[selectedAngleIndex] =
                angleMarks[selectedAngleIndex].copy(color = color)
            selectedAreaIndex in areaMarks.indices -> areaMarks[selectedAreaIndex] =
                areaMarks[selectedAreaIndex].copy(color = color)
            else -> return false
        }
        invalidate()
        return true
    }

    fun undo(): Boolean {
        if (marks.isEmpty()) return false
        marks.removeAt(marks.lastIndex)
        selectedIndex = marks.lastIndex
        notifySelectionChanged()
        invalidate()
        return true
    }

    fun deleteSelected(): Boolean {
        when {
            selectedIndex in marks.indices -> marks.removeAt(selectedIndex)
            selectedTextIndex in textMarks.indices -> textMarks.removeAt(selectedTextIndex)
            selectedAngleIndex in angleMarks.indices -> angleMarks.removeAt(selectedAngleIndex)
            selectedAreaIndex in areaMarks.indices -> {
                if (activeAreaBuildIndex == selectedAreaIndex) activeAreaBuildIndex = -1
                areaMarks.removeAt(selectedAreaIndex)
            }
            else -> return false
        }
        selectedIndex = -1
        selectedTextIndex = -1
        selectedAngleIndex = -1
        selectedAreaIndex = -1
        selectedAreaPointIndex = -1
        clearPreview()
        notifySelectionChanged()
        onMessage?.invoke("已删除选中的标注")
        invalidate()
        return true
    }

    fun deleteSelectedAreaPoint(): Boolean {
        val mark = areaMarks.getOrNull(selectedAreaIndex) ?: return false
        if (selectedAreaPointIndex !in mark.points.indices || mark.points.size <= 3) return false
        val updatedPoints = mark.points.toMutableList().apply { removeAt(selectedAreaPointIndex) }
        selectedAreaPointIndex = selectedAreaPointIndex.coerceAtMost(updatedPoints.lastIndex)
        areaMarks[selectedAreaIndex] = mark.copy(points = updatedPoints)
        notifySelectionChanged()
        onMessage?.invoke("已删除面积控制点")
        invalidate()
        return true
    }

    fun clearMarks() {
        marks.clear()
        textMarks.clear()
        angleMarks.clear()
        areaMarks.clear()
        selectedIndex = -1
        selectedTextIndex = -1
        selectedAngleIndex = -1
        selectedAreaIndex = -1
        selectedAreaPointIndex = -1
        pendingAreaPoints.clear()
        activeAreaBuildIndex = -1
        clearPreview()
        notifySelectionChanged()
        invalidate()
    }

    fun hasImage(): Boolean = bitmap != null
    fun hasMarks(): Boolean = marks.isNotEmpty() || textMarks.isNotEmpty() || angleMarks.isNotEmpty() || areaMarks.isNotEmpty()

    fun exportBitmap(): Bitmap? {
        val source = bitmap ?: return null
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, imagePaint)
        val fullRect = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
        drawAnnotationsForExport(canvas, fullRect, source.width / 500f)
        return result
    }

    fun exportPreviewBitmap(maxDimension: Int = 720): Bitmap? {
        val source = bitmap ?: return null
        val scale = minOf(1f, maxDimension / maxOf(source.width, source.height).toFloat())
        val previewWidth = maxOf(1, (source.width * scale).toInt())
        val previewHeight = maxOf(1, (source.height * scale).toInt())
        val result = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val previewRect = RectF(0f, 0f, previewWidth.toFloat(), previewHeight.toFloat())
        canvas.drawBitmap(source, null, previewRect, imagePaint)
        drawAnnotationsForExport(canvas, previewRect, previewWidth / 500f)
        return result
    }

    private fun drawAnnotationsForExport(canvas: Canvas, rect: RectF, scale: Float) {
        areaMarks.forEach { drawAreaMark(canvas, it, rect, scale, false) }
        marks.forEach { drawMark(canvas, it, rect, scale) }
        textMarks.forEach { drawTextMark(canvas, it, rect, scale) }
        angleMarks.forEach { drawAngleMark(canvas, it, rect, scale, false) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minimumHeight = (360 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(minimumHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(32, 34, 37))
        val source = bitmap
        if (source == null) {
            textPaint.color = Color.LTGRAY
            textPaint.textSize = 18f * resources.displayMetrics.scaledDensity
            canvas.drawText("请先拍照或从图库选择图片", width / 2f, height / 2f, textPaint)
            textPaint.color = Color.rgb(180, 0, 12)
            return
        }
        calculateImageRect(source)
        canvas.drawBitmap(source, null, imageRect, imagePaint)
        val displayScale = max(1f, imageRect.width() / 500f)
        areaMarks.forEachIndexed { index, mark ->
            drawAreaMark(
                canvas,
                mark,
                imageRect,
                displayScale,
                (index == selectedAreaIndex || index == activeAreaBuildIndex) && editingAreaPoint < 0,
            )
        }
        marks.forEachIndexed { index, mark ->
            drawMark(canvas, mark, imageRect, displayScale)
            if (index == selectedIndex && editingEndpoint < 0) {
                drawSelectionHandles(canvas, mark, imageRect, displayScale)
            }
        }
        textMarks.forEach { mark ->
            drawTextMark(canvas, mark, imageRect, displayScale)
        }
        angleMarks.forEachIndexed { index, mark ->
            drawAngleMark(
                canvas,
                mark,
                imageRect,
                displayScale,
                index == selectedAngleIndex && editingAnglePoint < 0,
            )
        }
        drawPendingAngle(canvas, imageRect, displayScale)
        drawPendingArea(canvas, imageRect, displayScale)
        val startX = dragStartX
        val startY = dragStartY
        val endX = dragEndX
        val endY = dragEndY
        if (startX != null && startY != null && endX != null && endY != null) {
            drawMark(canvas, normalizedMark(startX, startY, endX, endY, ""), imageRect, displayScale)
        }
        drawMagnifier(canvas, source, displayScale)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null) return false
        calculateImageRect(bitmap!!)
        if (mode == AnnotationMode.MOVE) return handleMoveTouch(event)
        if (mode == AnnotationMode.AREA) return handleAreaTouch(event)
        if (mode == AnnotationMode.TEXT) return handleTextTouch(event)
        if (mode == AnnotationMode.ANGLE) return handleAngleTouch(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!imageRect.contains(event.x, event.y)) return false
                onCanvasTouched?.invoke()
                editingEndpoint = endpointAt(event.x, event.y)
                if (editingEndpoint >= 0) {
                    endpointMoved = false
                    magnifierFocusX = event.x
                    magnifierFocusY = event.y
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                    return true
                }
                dragStartX = event.x.coerceIn(imageRect.left, imageRect.right)
                dragStartY = event.y.coerceIn(imageRect.top, imageRect.bottom)
                dragEndX = dragStartX
                dragEndY = dragStartY
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (editingEndpoint >= 0) {
                    if (!endpointMoved) {
                        endpointMoved = true
                        onManipulationStarted?.invoke()
                    }
                    magnifierFocusX = event.x.coerceIn(imageRect.left, imageRect.right)
                    magnifierFocusY = event.y.coerceIn(imageRect.top, imageRect.bottom)
                    moveSelectedEndpoint(editingEndpoint, event.x, event.y)
                    return true
                }
                if (dragStartX == null) return false
                dragEndX = event.x.coerceIn(imageRect.left, imageRect.right)
                dragEndY = event.y.coerceIn(imageRect.top, imageRect.bottom)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (editingEndpoint >= 0) {
                    moveSelectedEndpoint(editingEndpoint, event.x, event.y)
                    editingEndpoint = -1
                    magnifierFocusX = null
                    magnifierFocusY = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                    notifySelectionChanged()
                    endpointMoved = false
                    onMessage?.invoke("尺寸线端点已调整")
                    invalidate()
                    return true
                }
                val sx = dragStartX ?: return false
                val sy = dragStartY ?: return false
                val ex = event.x.coerceIn(imageRect.left, imageRect.right)
                val ey = event.y.coerceIn(imageRect.top, imageRect.bottom)
                val dx = ex - sx
                val dy = ey - sy
                val dragThreshold = 12f * resources.displayMetrics.density
                if (dx * dx + dy * dy > dragThreshold * dragThreshold) {
                    marks += normalizedMark(sx, sy, ex, ey, defaultDimensionLabel)
                    selectedIndex = marks.lastIndex
                    selectedTextIndex = -1
                    selectedAngleIndex = -1
                    selectedAreaIndex = -1
                    selectedAreaPointIndex = -1
                    notifySelectionChanged()
                    onMessage?.invoke(
                        if (defaultDimensionLabel.isBlank()) {
                            "已添加空尺寸线；可填写距离或选择测量记录"
                        } else {
                            "已添加尺寸线，默认使用最新测量值 $defaultDimensionLabel"
                        },
                    )
                } else {
                    selectNearestMark(ex, ey)
                }
                clearPreview()
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                editingEndpoint = -1
                endpointMoved = false
                magnifierFocusX = null
                magnifierFocusY = null
                parent?.requestDisallowInterceptTouchEvent(false)
                clearPreview()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleMoveTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!imageRect.contains(event.x, event.y)) return false
                onCanvasTouched?.invoke()
                moveTouchStartX = event.x
                moveTouchStartY = event.y
                moveStarted = false
                val textIndex = textMarkAt(event.x, event.y)
                val dimensionIndex = dimensionMarkAt(event.x, event.y)
                val angleIndex = angleMarkAt(event.x, event.y)
                val areaIndex = areaMarkAt(event.x, event.y)
                when {
                    textIndex >= 0 -> {
                        moveKind = MoveKind.TEXT
                        moveIndex = textIndex
                        moveTextOriginal = textMarks[textIndex]
                    }
                    dimensionIndex >= 0 -> {
                        moveKind = MoveKind.DIMENSION
                        moveIndex = dimensionIndex
                        moveDimensionOriginal = marks[dimensionIndex]
                    }
                    angleIndex >= 0 -> {
                        moveKind = MoveKind.ANGLE
                        moveIndex = angleIndex
                        moveAngleOriginal = angleMarks[angleIndex]
                    }
                    areaIndex >= 0 -> {
                        moveKind = MoveKind.AREA
                        moveIndex = areaIndex
                        moveAreaOriginal = areaMarks[moveIndex]
                    }
                    else -> resetMoveTarget()
                }
                if (moveKind != MoveKind.NONE) parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (moveKind == MoveKind.NONE) return true
                val dx = event.x - moveTouchStartX
                val dy = event.y - moveTouchStartY
                val threshold = 4f * resources.displayMetrics.density
                if (moveStarted || dx * dx + dy * dy > threshold * threshold) {
                    if (!moveStarted) onManipulationStarted?.invoke()
                    moveStarted = true
                    moveWholeTarget(event.x, event.y)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (moveKind != MoveKind.NONE && moveStarted) {
                    moveWholeTarget(event.x, event.y)
                    onMessage?.invoke(
                        when (moveKind) {
                            MoveKind.DIMENSION -> "尺寸标注位置已调整"
                            MoveKind.TEXT -> "文字位置已调整"
                            MoveKind.ANGLE -> "角度标注位置已调整"
                            MoveKind.AREA -> "面积标注位置已调整"
                            MoveKind.NONE -> ""
                        },
                    )
                }
                resetMoveTarget()
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                resetMoveTarget()
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return true
    }

    private fun moveWholeTarget(x: Float, y: Float) {
        val rawDx = (x - moveTouchStartX) / imageRect.width()
        val rawDy = (y - moveTouchStartY) / imageRect.height()
        when (moveKind) {
            MoveKind.DIMENSION -> moveDimensionOriginal?.let { original ->
                val dx = rawDx.coerceIn(-minOf(original.startX, original.endX), 1f - maxOf(original.startX, original.endX))
                val dy = rawDy.coerceIn(-minOf(original.startY, original.endY), 1f - maxOf(original.startY, original.endY))
                if (moveIndex in marks.indices) {
                    marks[moveIndex] = original.copy(
                        startX = original.startX + dx,
                        startY = original.startY + dy,
                        endX = original.endX + dx,
                        endY = original.endY + dy,
                    )
                }
            }
            MoveKind.TEXT -> moveTextOriginal?.let { original ->
                if (moveIndex in textMarks.indices) {
                    textMarks[moveIndex] = original.copy(
                        x = (original.x + rawDx).coerceIn(0f, 1f),
                        y = (original.y + rawDy).coerceIn(0f, 1f),
                    )
                }
            }
            MoveKind.ANGLE -> moveAngleOriginal?.let { original ->
                val minX = minOf(original.vertexX, original.firstX, original.secondX)
                val maxX = maxOf(original.vertexX, original.firstX, original.secondX)
                val minY = minOf(original.vertexY, original.firstY, original.secondY)
                val maxY = maxOf(original.vertexY, original.firstY, original.secondY)
                val dx = rawDx.coerceIn(-minX, 1f - maxX)
                val dy = rawDy.coerceIn(-minY, 1f - maxY)
                if (moveIndex in angleMarks.indices) {
                    angleMarks[moveIndex] = original.copy(
                        vertexX = original.vertexX + dx,
                        vertexY = original.vertexY + dy,
                        firstX = original.firstX + dx,
                        firstY = original.firstY + dy,
                        secondX = original.secondX + dx,
                        secondY = original.secondY + dy,
                    )
                }
            }
            MoveKind.AREA -> moveAreaOriginal?.let { original ->
                val minX = original.points.minOf { it.x }
                val maxX = original.points.maxOf { it.x }
                val minY = original.points.minOf { it.y }
                val maxY = original.points.maxOf { it.y }
                val dx = rawDx.coerceIn(-minX, 1f - maxX)
                val dy = rawDy.coerceIn(-minY, 1f - maxY)
                if (moveIndex in areaMarks.indices) {
                    areaMarks[moveIndex] = original.copy(
                        points = original.points.map { AreaPoint(it.x + dx, it.y + dy) },
                    )
                }
            }
            MoveKind.NONE -> Unit
        }
        invalidate()
    }

    private fun resetMoveTarget() {
        moveKind = MoveKind.NONE
        moveIndex = -1
        moveStarted = false
        moveDimensionOriginal = null
        moveTextOriginal = null
        moveAngleOriginal = null
        moveAreaOriginal = null
        magnifierFocusX = null
        magnifierFocusY = null
    }

    private fun normalizedMark(sx: Float, sy: Float, ex: Float, ey: Float, label: String) = DimensionMark(
        (sx - imageRect.left) / imageRect.width(),
        (sy - imageRect.top) / imageRect.height(),
        (ex - imageRect.left) / imageRect.width(),
        (ey - imageRect.top) / imageRect.height(),
        label,
    )

    private fun calculateImageRect(source: Bitmap) {
        val scale = minOf(width / source.width.toFloat(), height / source.height.toFloat())
        val drawWidth = source.width * scale
        val drawHeight = source.height * scale
        val left = (width - drawWidth) / 2f
        val top = 0f
        imageRect.set(left, top, left + drawWidth, top + drawHeight)
    }

    private fun drawMark(canvas: Canvas, mark: DimensionMark, rect: RectF, scale: Float) {
        val x1 = rect.left + mark.startX * rect.width()
        val y1 = rect.top + mark.startY * rect.height()
        val x2 = rect.left + mark.endX * rect.width()
        val y2 = rect.top + mark.endY * rect.height()
        linePaint.color = mark.color
        textPaint.color = mark.color
        linePaint.strokeWidth = when (mark.thickness) {
            0 -> max(2f, 1.4f * scale)
            2 -> max(5f, 4f * scale)
            else -> max(3f, 2.4f * scale)
        }
        canvas.drawLine(x1, y1, x2, y2, linePaint)

        val angle = atan2(y2 - y1, x2 - x1)
        drawPerpendicularEndLine(canvas, x1, y1, angle, scale, linePaint)
        drawPerpendicularEndLine(canvas, x2, y2, angle, scale, linePaint)
        drawArchitecturalTick(canvas, x1, y1, angle, scale, linePaint)
        drawArchitecturalTick(canvas, x2, y2, angle, scale, linePaint)

        if (mark.label.isNotBlank()) {
            val midX = (x1 + x2) / 2f
            val midY = (y1 + y2) / 2f
            textPaint.textSize = max(24f, 18f * scale)
            val padding = max(8f, 6f * scale)
            val textWidth = textPaint.measureText(mark.label)
            val metrics = textPaint.fontMetrics
            val textOffset = max(13f, 10f * scale)
            var rotation = Math.toDegrees(angle.toDouble()).toFloat()
            if (rotation > 90f || rotation < -90f) rotation += 180f
            val baseline = -textOffset - metrics.descent
            val background = RectF(
                -textWidth / 2f - padding,
                baseline + metrics.ascent - padding / 2f,
                textWidth / 2f + padding,
                baseline + metrics.descent + padding / 2f,
            )
            val saveCount = canvas.save()
            canvas.translate(midX, midY)
            canvas.rotate(rotation)
            canvas.drawRoundRect(background, padding, padding, textBackgroundPaint)
            canvas.drawText(mark.label, 0f, baseline, textPaint)
            canvas.restoreToCount(saveCount)
        }
    }

    private fun drawTextMark(canvas: Canvas, mark: TextMark, rect: RectF, scale: Float) {
        val x = rect.left + mark.x * rect.width()
        val y = rect.top + mark.y * rect.height()
        textPaint.color = mark.color
        textPaint.textSize = when (mark.textSize) {
            0 -> max(18f, 17f * scale)
            2 -> max(28f, 28f * scale)
            else -> max(22f, 22f * scale)
        }
        val shownText = mark.text.ifBlank { "文字" }
        val padding = max(8f, 6f * scale)
        val metrics = textPaint.fontMetrics
        val textWidth = textPaint.measureText(shownText)
        val bounds = RectF(
            x - textWidth / 2f - padding,
            y + metrics.ascent - padding,
            x + textWidth / 2f + padding,
            y + metrics.descent + padding,
        )
        if (mark.whiteBackground) canvas.drawRoundRect(bounds, padding, padding, textBackgroundPaint)
        canvas.drawText(shownText, x, y, textPaint)
    }

    private fun drawMagnifier(canvas: Canvas, source: Bitmap, displayScale: Float) {
        val focusX = magnifierFocusX ?: return
        val focusY = magnifierFocusY ?: return
        val density = resources.displayMetrics.density
        val margin = 12f * density
        val lensSize = minOf(128f * density, width - margin * 2f, height - margin * 2f)
        if (lensSize <= 0f) return
        val lens = RectF(
            width - margin - lensSize,
            margin,
            width - margin,
            margin + lensSize,
        )
        val centerX = lens.centerX()
        val centerY = lens.centerY()
        val radius = lensSize / 2f
        canvas.drawCircle(centerX, centerY, radius, magnifierBackgroundPaint)

        val clipPath = Path().apply { addCircle(centerX, centerY, radius, Path.Direction.CW) }
        val saveCount = canvas.save()
        canvas.clipPath(clipPath)
        canvas.translate(centerX, centerY)
        canvas.scale(2.35f, 2.35f)
        canvas.translate(-focusX, -focusY)
        canvas.drawBitmap(source, null, imageRect, imagePaint)
        areaMarks.forEach { drawAreaMark(canvas, it, imageRect, displayScale, false) }
        marks.forEach { mark ->
            drawMark(canvas, mark, imageRect, displayScale)
        }
        textMarks.forEach { drawTextMark(canvas, it, imageRect, displayScale) }
        angleMarks.forEach { drawAngleMark(canvas, it, imageRect, displayScale, false) }
        canvas.restoreToCount(saveCount)

        magnifierBorderPaint.strokeWidth = 3f * density
        canvas.drawCircle(centerX, centerY, radius, magnifierBorderPaint)
        val crosshairSize = 9f * density
        canvas.drawLine(centerX - crosshairSize, centerY, centerX + crosshairSize, centerY, magnifierCrosshairPaint)
        canvas.drawLine(centerX, centerY - crosshairSize, centerX, centerY + crosshairSize, magnifierCrosshairPaint)
    }

    private fun drawAngleMark(
        canvas: Canvas,
        mark: AngleMark,
        rect: RectF,
        scale: Float,
        selected: Boolean,
    ) {
        val vx = rect.left + mark.vertexX * rect.width()
        val vy = rect.top + mark.vertexY * rect.height()
        val x1 = rect.left + mark.firstX * rect.width()
        val y1 = rect.top + mark.firstY * rect.height()
        val x2 = rect.left + mark.secondX * rect.width()
        val y2 = rect.top + mark.secondY * rect.height()
        linePaint.color = mark.color
        textPaint.color = mark.color
        linePaint.strokeWidth = max(3f, 2.4f * scale)
        canvas.drawLine(vx, vy, x1, y1, linePaint)
        canvas.drawLine(vx, vy, x2, y2, linePaint)

        var startAngle = Math.toDegrees(atan2(y1 - vy, x1 - vx).toDouble()).toFloat()
        val endAngle = Math.toDegrees(atan2(y2 - vy, x2 - vx).toDouble()).toFloat()
        var sweep = (endAngle - startAngle + 360f) % 360f
        if (sweep > 180f) {
            startAngle = endAngle
            sweep = 360f - sweep
        }
        val firstLength = hypot(x1 - vx, y1 - vy)
        val secondLength = hypot(x2 - vx, y2 - vy)
        val radius = (minOf(firstLength, secondLength) * 0.32f).coerceAtLeast(max(22f, 16f * scale))
        val arcRect = RectF(vx - radius, vy - radius, vx + radius, vy + radius)
        canvas.drawArc(arcRect, startAngle, sweep, false, linePaint)

        if (mark.label.isNotBlank()) {
            val middleAngle = Math.toRadians((startAngle + sweep / 2f).toDouble())
            val labelRadius = radius + max(20f, 14f * scale)
            val labelX = vx + cos(middleAngle).toFloat() * labelRadius
            val labelY = vy + sin(middleAngle).toFloat() * labelRadius
            val label = "${mark.label}°"
            textPaint.textSize = max(22f, 18f * scale)
            val padding = max(7f, 5f * scale)
            val metrics = textPaint.fontMetrics
            val textWidth = textPaint.measureText(label)
            val background = RectF(
                labelX - textWidth / 2f - padding,
                labelY + metrics.ascent - padding,
                labelX + textWidth / 2f + padding,
                labelY + metrics.descent + padding,
            )
            canvas.drawRoundRect(background, padding, padding, textBackgroundPaint)
            canvas.drawText(label, labelX, labelY, textPaint)
        }

        if (selected) drawAngleHandles(canvas, vx, vy, x1, y1, x2, y2, scale)
    }

    private fun drawAngleHandles(
        canvas: Canvas,
        vx: Float,
        vy: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        scale: Float,
    ) {
        val radius = max(9f, 7f * scale)
        arrayOf(vx to vy, x1 to y1, x2 to y2).forEach { (x, y) ->
            canvas.drawCircle(x, y, radius + max(3f, 2f * scale), selectionHaloPaint)
            canvas.drawCircle(x, y, radius, selectionPaint)
        }
    }

    private fun drawAreaMark(canvas: Canvas, mark: AreaMark, rect: RectF, scale: Float, selected: Boolean) {
        if (mark.points.size < 3) return
        val path = Path().apply {
            val first = mark.points.first()
            moveTo(rect.left + first.x * rect.width(), rect.top + first.y * rect.height())
            mark.points.drop(1).forEach { point ->
                lineTo(rect.left + point.x * rect.width(), rect.top + point.y * rect.height())
            }
            close()
        }
        areaFillPaint.color = Color.argb(48, Color.red(mark.color), Color.green(mark.color), Color.blue(mark.color))
        canvas.drawPath(path, areaFillPaint)
        linePaint.color = mark.color
        linePaint.strokeWidth = max(3f, 2.4f * scale)
        canvas.drawPath(path, linePaint)
        if (mark.label.isNotBlank()) {
            val centerX = mark.points.sumOf { it.x.toDouble() }.toFloat() / mark.points.size
            val centerY = mark.points.sumOf { it.y.toDouble() }.toFloat() / mark.points.size
            val labelX = rect.left + centerX * rect.width()
            val labelY = rect.top + centerY * rect.height()
            textPaint.color = mark.color
            textPaint.textSize = max(24f, 18f * scale)
            val padding = max(8f, 6f * scale)
            val metrics = textPaint.fontMetrics
            val textWidth = textPaint.measureText(mark.label)
            val background = RectF(
                labelX - textWidth / 2f - padding,
                labelY + metrics.ascent - padding / 2f,
                labelX + textWidth / 2f + padding,
                labelY + metrics.descent + padding / 2f,
            )
            canvas.drawRoundRect(background, padding, padding, textBackgroundPaint)
            canvas.drawText(mark.label, labelX, labelY, textPaint)
        }
        if (selected) {
            val radius = max(9f, 7f * scale)
            mark.points.forEachIndexed { index, point ->
                val x = rect.left + point.x * rect.width()
                val y = rect.top + point.y * rect.height()
                canvas.drawCircle(x, y, radius + max(3f, 2f * scale), selectionHaloPaint)
                selectionPaint.color = if (index == selectedAreaPointIndex) Color.rgb(255, 152, 0) else Color.rgb(0, 188, 212)
                canvas.drawCircle(x, y, radius, selectionPaint)
            }
            selectionPaint.color = Color.rgb(0, 188, 212)
        }
    }

    private fun drawPendingArea(canvas: Canvas, rect: RectF, scale: Float) {
        if (pendingAreaPoints.isEmpty()) return
        linePaint.color = Color.rgb(214, 32, 42)
        linePaint.strokeWidth = max(3f, 2.4f * scale)
        val path = Path()
        pendingAreaPoints.forEachIndexed { index, point ->
            val x = rect.left + point.x * rect.width()
            val y = rect.top + point.y * rect.height()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
        val radius = max(9f, 7f * scale)
        pendingAreaPoints.forEach { point ->
            val x = rect.left + point.x * rect.width()
            val y = rect.top + point.y * rect.height()
            canvas.drawCircle(x, y, radius + max(3f, 2f * scale), selectionHaloPaint)
            canvas.drawCircle(x, y, radius, selectionPaint)
        }
    }

    private fun handleAreaTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!imageRect.contains(event.x, event.y)) return false
                onCanvasTouched?.invoke()
                areaTouchStartX = event.x
                areaTouchStartY = event.y
                areaPointMoved = false
                val pointHit = if (pendingAreaPoints.isEmpty()) findAreaPointAt(event.x, event.y) else null
                editingAreaPoint = pointHit?.second ?: -1
                if (pointHit != null) {
                    selectedAreaIndex = pointHit.first
                    selectedAreaPointIndex = pointHit.second
                    clearOtherSelectionsForArea()
                    magnifierFocusX = event.x
                    magnifierFocusY = event.y
                    parent?.requestDisallowInterceptTouchEvent(true)
                    notifySelectionChanged()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (editingAreaPoint < 0) return true
                val dx = event.x - areaTouchStartX
                val dy = event.y - areaTouchStartY
                val threshold = 4f * resources.displayMetrics.density
                if (areaPointMoved || dx * dx + dy * dy > threshold * threshold) {
                    if (!areaPointMoved) onManipulationStarted?.invoke()
                    areaPointMoved = true
                    magnifierFocusX = event.x.coerceIn(imageRect.left, imageRect.right)
                    magnifierFocusY = event.y.coerceIn(imageRect.top, imageRect.bottom)
                    moveAreaPoint(editingAreaPoint, event.x, event.y)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (editingAreaPoint >= 0) {
                    if (areaPointMoved) moveAreaPoint(editingAreaPoint, event.x, event.y)
                    editingAreaPoint = -1
                    areaPointMoved = false
                    magnifierFocusX = null
                    magnifierFocusY = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                    notifySelectionChanged()
                    invalidate()
                    return true
                }

                if (pendingAreaPoints.isNotEmpty()) {
                    pendingAreaPoints += normalizedAreaPoint(event.x, event.y)
                    if (pendingAreaPoints.size >= 3) {
                        areaMarks += AreaMark(pendingAreaPoints.toList())
                        pendingAreaPoints.clear()
                        selectedAreaIndex = areaMarks.lastIndex
                        activeAreaBuildIndex = selectedAreaIndex
                        selectedAreaPointIndex = -1
                        clearOtherSelectionsForArea()
                        onMessage?.invoke("面积已自动闭合；可继续点击添加更多控制点，切换模式后完成")
                    } else {
                        onMessage?.invoke("继续点击第三个点，放置后会自动闭合")
                    }
                    notifySelectionChanged()
                    invalidate()
                    return true
                }

                if (activeAreaBuildIndex in areaMarks.indices) {
                    val edgeIndex = areaEdgeAt(activeAreaBuildIndex, event.x, event.y)
                    val now = System.currentTimeMillis()
                    if (edgeIndex >= 0) {
                        if (
                            activeAreaBuildIndex == lastAreaEdgeTapArea && edgeIndex == lastAreaEdgeTapIndex &&
                            now - lastAreaEdgeTapAt <= 450L
                        ) {
                            insertAreaPointAtEdge(activeAreaBuildIndex, edgeIndex)
                            lastAreaEdgeTapAt = 0L
                            onMessage?.invoke("已在所选边线的正中间添加控制点")
                        } else {
                            lastAreaEdgeTapAt = now
                            lastAreaEdgeTapArea = activeAreaBuildIndex
                            lastAreaEdgeTapIndex = edgeIndex
                            onMessage?.invoke("再次点击同一条边，在边线正中间添加控制点")
                        }
                        selectedAreaIndex = activeAreaBuildIndex
                        if (lastAreaEdgeTapAt != 0L) selectedAreaPointIndex = -1
                        clearOtherSelectionsForArea()
                        notifySelectionChanged()
                        invalidate()
                        return true
                    }
                    val mark = areaMarks[activeAreaBuildIndex]
                    val points = mark.points + normalizedAreaPoint(event.x, event.y)
                    areaMarks[activeAreaBuildIndex] = mark.copy(points = points)
                    selectedAreaIndex = activeAreaBuildIndex
                    selectedAreaPointIndex = points.lastIndex
                    lastAreaEdgeTapAt = 0L
                    clearOtherSelectionsForArea()
                    onMessage?.invoke("已添加第 ${points.size} 个控制点，面积保持自动闭合")
                    notifySelectionChanged()
                    invalidate()
                    return true
                }

                val hitArea = areaMarkAt(event.x, event.y)
                if (hitArea >= 0) {
                    selectedAreaIndex = hitArea
                    selectedAreaPointIndex = -1
                    clearOtherSelectionsForArea()
                    val edgeIndex = areaEdgeAt(hitArea, event.x, event.y)
                    val now = System.currentTimeMillis()
                    if (
                        edgeIndex >= 0 && hitArea == lastAreaEdgeTapArea && edgeIndex == lastAreaEdgeTapIndex &&
                        now - lastAreaEdgeTapAt <= 450L
                    ) {
                        insertAreaPointAtEdge(hitArea, edgeIndex)
                        lastAreaEdgeTapAt = 0L
                        onMessage?.invoke("已在边线中间添加控制点")
                    } else {
                        lastAreaEdgeTapAt = now
                        lastAreaEdgeTapArea = hitArea
                        lastAreaEdgeTapIndex = edgeIndex
                        onMessage?.invoke("已选中面积；双击边线可添加控制点")
                    }
                } else {
                    selectedAreaIndex = -1
                    selectedAreaPointIndex = -1
                    clearOtherSelectionsForArea()
                    pendingAreaPoints += normalizedAreaPoint(event.x, event.y)
                    onMessage?.invoke("已放置第一个面积控制点，请继续点击")
                }
                notifySelectionChanged()
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                editingAreaPoint = -1
                areaPointMoved = false
                magnifierFocusX = null
                magnifierFocusY = null
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return true
    }

    private fun normalizedAreaPoint(x: Float, y: Float) = AreaPoint(
        (x.coerceIn(imageRect.left, imageRect.right) - imageRect.left) / imageRect.width(),
        (y.coerceIn(imageRect.top, imageRect.bottom) - imageRect.top) / imageRect.height(),
    )

    private fun clearOtherSelectionsForArea() {
        selectedIndex = -1
        selectedTextIndex = -1
        selectedAngleIndex = -1
    }

    private fun areaPointAt(x: Float, y: Float): Int {
        val mark = areaMarks.getOrNull(selectedAreaIndex) ?: return -1
        val tolerance = 44f * resources.displayMetrics.density
        return mark.points.indices.minByOrNull { index ->
            val point = mark.points[index]
            val pointX = imageRect.left + point.x * imageRect.width()
            val pointY = imageRect.top + point.y * imageRect.height()
            (x - pointX) * (x - pointX) + (y - pointY) * (y - pointY)
        }?.takeIf { index ->
            val point = mark.points[index]
            val pointX = imageRect.left + point.x * imageRect.width()
            val pointY = imageRect.top + point.y * imageRect.height()
            (x - pointX) * (x - pointX) + (y - pointY) * (y - pointY) <= tolerance * tolerance
        } ?: -1
    }

    private fun findAreaPointAt(x: Float, y: Float): Pair<Int, Int>? {
        if (selectedAreaIndex in areaMarks.indices) {
            val pointIndex = areaPointAt(x, y)
            if (pointIndex >= 0) return selectedAreaIndex to pointIndex
        }
        for (areaIndex in areaMarks.indices.reversed()) {
            if (areaIndex == selectedAreaIndex) continue
            val mark = areaMarks[areaIndex]
            val tolerance = 44f * resources.displayMetrics.density
            val pointIndex = mark.points.indices.minByOrNull { index ->
                val point = mark.points[index]
                val pointX = imageRect.left + point.x * imageRect.width()
                val pointY = imageRect.top + point.y * imageRect.height()
                (x - pointX) * (x - pointX) + (y - pointY) * (y - pointY)
            } ?: continue
            val point = mark.points[pointIndex]
            val pointX = imageRect.left + point.x * imageRect.width()
            val pointY = imageRect.top + point.y * imageRect.height()
            if ((x - pointX) * (x - pointX) + (y - pointY) * (y - pointY) <= tolerance * tolerance) {
                return areaIndex to pointIndex
            }
        }
        return null
    }

    private fun moveAreaPoint(pointIndex: Int, x: Float, y: Float) {
        val mark = areaMarks.getOrNull(selectedAreaIndex) ?: return
        if (pointIndex !in mark.points.indices) return
        val points = mark.points.toMutableList()
        points[pointIndex] = normalizedAreaPoint(x, y)
        areaMarks[selectedAreaIndex] = mark.copy(points = points)
        invalidate()
    }

    private fun areaMarkAt(x: Float, y: Float): Int {
        for (index in areaMarks.indices.reversed()) {
            val mark = areaMarks[index]
            if (areaEdgeAt(index, x, y) >= 0 || isPointInsideArea(mark, x, y)) return index
        }
        return -1
    }

    private fun areaEdgeAt(areaIndex: Int, x: Float, y: Float): Int {
        val mark = areaMarks.getOrNull(areaIndex) ?: return -1
        val tolerance = 28f * resources.displayMetrics.density
        var bestEdge = -1
        var bestDistance = Float.MAX_VALUE
        mark.points.indices.forEach { index ->
            val first = mark.points[index]
            val second = mark.points[(index + 1) % mark.points.size]
            val distance = pointToSegmentDistanceSquared(
                x,
                y,
                imageRect.left + first.x * imageRect.width(),
                imageRect.top + first.y * imageRect.height(),
                imageRect.left + second.x * imageRect.width(),
                imageRect.top + second.y * imageRect.height(),
            )
            if (distance < bestDistance) {
                bestDistance = distance
                bestEdge = index
            }
        }
        return if (bestDistance <= tolerance * tolerance) bestEdge else -1
    }

    private fun isPointInsideArea(mark: AreaMark, x: Float, y: Float): Boolean {
        var inside = false
        var previous = mark.points.lastIndex
        mark.points.indices.forEach { current ->
            val currentX = imageRect.left + mark.points[current].x * imageRect.width()
            val currentY = imageRect.top + mark.points[current].y * imageRect.height()
            val previousX = imageRect.left + mark.points[previous].x * imageRect.width()
            val previousY = imageRect.top + mark.points[previous].y * imageRect.height()
            if ((currentY > y) != (previousY > y) &&
                x < (previousX - currentX) * (y - currentY) / (previousY - currentY) + currentX
            ) inside = !inside
            previous = current
        }
        return inside
    }

    private fun insertAreaPointAtEdge(areaIndex: Int, edgeIndex: Int) {
        val mark = areaMarks.getOrNull(areaIndex) ?: return
        val first = mark.points[edgeIndex]
        val second = mark.points[(edgeIndex + 1) % mark.points.size]
        val points = mark.points.toMutableList()
        val insertionIndex = edgeIndex + 1
        points.add(insertionIndex, AreaPoint((first.x + second.x) / 2f, (first.y + second.y) / 2f))
        areaMarks[areaIndex] = mark.copy(points = points)
        selectedAreaPointIndex = insertionIndex
    }

    private fun handleAngleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!imageRect.contains(event.x, event.y)) return false
                onCanvasTouched?.invoke()
                angleTouchStartX = event.x
                angleTouchStartY = event.y
                editingAnglePoint = anglePointAt(event.x, event.y)
                anglePointMoved = false
                if (editingAnglePoint >= 0) {
                    magnifierFocusX = event.x
                    magnifierFocusY = event.y
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (editingAnglePoint < 0) return true
                val dx = event.x - angleTouchStartX
                val dy = event.y - angleTouchStartY
                val threshold = 4f * resources.displayMetrics.density
                if (anglePointMoved || dx * dx + dy * dy > threshold * threshold) {
                    if (!anglePointMoved) onManipulationStarted?.invoke()
                    anglePointMoved = true
                    magnifierFocusX = event.x.coerceIn(imageRect.left, imageRect.right)
                    magnifierFocusY = event.y.coerceIn(imageRect.top, imageRect.bottom)
                    moveAnglePoint(editingAnglePoint, event.x, event.y)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (editingAnglePoint >= 0) {
                    if (anglePointMoved) moveAnglePoint(editingAnglePoint, event.x, event.y)
                    editingAnglePoint = -1
                    anglePointMoved = false
                    magnifierFocusX = null
                    magnifierFocusY = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                    notifySelectionChanged()
                    invalidate()
                    return true
                }
                if (pendingAnglePoints.isEmpty() && selectNearestAngle(event.x, event.y)) {
                    notifySelectionChanged()
                    invalidate()
                    return true
                }
                addPendingAnglePoint(event.x, event.y)
                notifySelectionChanged()
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                editingAnglePoint = -1
                anglePointMoved = false
                magnifierFocusX = null
                magnifierFocusY = null
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return true
    }

    private fun addPendingAnglePoint(x: Float, y: Float) {
        pendingAnglePoints +=
            ((x.coerceIn(imageRect.left, imageRect.right) - imageRect.left) / imageRect.width()) to
            ((y.coerceIn(imageRect.top, imageRect.bottom) - imageRect.top) / imageRect.height())
        selectedIndex = -1
        selectedTextIndex = -1
        selectedAngleIndex = -1
        selectedAreaIndex = -1
        selectedAreaPointIndex = -1
        when (pendingAnglePoints.size) {
            1 -> onMessage?.invoke("已放置第一个端点，请放置角度中心点")
            2 -> onMessage?.invoke("已放置中心点，请放置另一个端点")
            3 -> {
                val first = pendingAnglePoints[0]
                val vertex = pendingAnglePoints[1]
                val second = pendingAnglePoints[2]
                angleMarks += AngleMark(
                    vertexX = vertex.first,
                    vertexY = vertex.second,
                    firstX = first.first,
                    firstY = first.second,
                    secondX = second.first,
                    secondY = second.second,
                )
                pendingAnglePoints.clear()
                selectedAngleIndex = angleMarks.lastIndex
                selectedAreaIndex = -1
                selectedAreaPointIndex = -1
                onMessage?.invoke("角度标注已完成，请输入角度")
            }
        }
    }

    private fun drawPendingAngle(canvas: Canvas, rect: RectF, scale: Float) {
        if (pendingAnglePoints.isEmpty()) return
        val points = pendingAnglePoints.map { point ->
            rect.left + point.first * rect.width() to rect.top + point.second * rect.height()
        }
        linePaint.color = Color.rgb(214, 32, 42)
        linePaint.strokeWidth = max(3f, 2.4f * scale)
        if (points.size >= 2) {
            val first = points[0]
            val vertex = points[1]
            canvas.drawLine(first.first, first.second, vertex.first, vertex.second, linePaint)
        }
        val radius = max(9f, 7f * scale)
        points.forEach { (pointX, pointY) ->
            canvas.drawCircle(pointX, pointY, radius + max(3f, 2f * scale), selectionHaloPaint)
            canvas.drawCircle(pointX, pointY, radius, selectionPaint)
        }
    }

    private fun selectNearestAngle(x: Float, y: Float): Boolean {
        val bestIndex = angleMarkAt(x, y)
        if (bestIndex < 0) return false
        selectedAngleIndex = bestIndex
        selectedIndex = -1
        selectedTextIndex = -1
        selectedAreaIndex = -1
        selectedAreaPointIndex = -1
        onMessage?.invoke("已选中角度标注")
        return true
    }

    private fun angleMarkAt(x: Float, y: Float): Int {
        var bestIndex = -1
        var bestDistance = Float.MAX_VALUE
        angleMarks.forEachIndexed { index, mark ->
            val vx = imageRect.left + mark.vertexX * imageRect.width()
            val vy = imageRect.top + mark.vertexY * imageRect.height()
            val x1 = imageRect.left + mark.firstX * imageRect.width()
            val y1 = imageRect.top + mark.firstY * imageRect.height()
            val x2 = imageRect.left + mark.secondX * imageRect.width()
            val y2 = imageRect.top + mark.secondY * imageRect.height()
            val distance = minOf(
                pointToSegmentDistanceSquared(x, y, vx, vy, x1, y1),
                pointToSegmentDistanceSquared(x, y, vx, vy, x2, y2),
            )
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        val tolerance = 32f * resources.displayMetrics.density
        return if (bestDistance <= tolerance * tolerance) bestIndex else -1
    }

    private fun anglePointAt(x: Float, y: Float): Int {
        val mark = angleMarks.getOrNull(selectedAngleIndex) ?: return -1
        val points = arrayOf(
            imageRect.left + mark.vertexX * imageRect.width() to imageRect.top + mark.vertexY * imageRect.height(),
            imageRect.left + mark.firstX * imageRect.width() to imageRect.top + mark.firstY * imageRect.height(),
            imageRect.left + mark.secondX * imageRect.width() to imageRect.top + mark.secondY * imageRect.height(),
        )
        val tolerance = 28f * resources.displayMetrics.density
        val toleranceSquared = tolerance * tolerance
        return points.indices.minByOrNull { index ->
            val (pointX, pointY) = points[index]
            (x - pointX) * (x - pointX) + (y - pointY) * (y - pointY)
        }?.takeIf { index ->
            val (pointX, pointY) = points[index]
            (x - pointX) * (x - pointX) + (y - pointY) * (y - pointY) <= toleranceSquared
        } ?: -1
    }

    private fun moveAnglePoint(point: Int, x: Float, y: Float) {
        val mark = angleMarks.getOrNull(selectedAngleIndex) ?: return
        val normalizedX = (x.coerceIn(imageRect.left, imageRect.right) - imageRect.left) / imageRect.width()
        val normalizedY = (y.coerceIn(imageRect.top, imageRect.bottom) - imageRect.top) / imageRect.height()
        angleMarks[selectedAngleIndex] = when (point) {
            0 -> mark.copy(vertexX = normalizedX, vertexY = normalizedY)
            1 -> mark.copy(firstX = normalizedX, firstY = normalizedY)
            else -> mark.copy(secondX = normalizedX, secondY = normalizedY)
        }
        invalidate()
    }

    private fun handleTextTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!imageRect.contains(event.x, event.y)) return false
                onCanvasTouched?.invoke()
                textTouchStartX = event.x
                textTouchStartY = event.y
                textTouchMoved = false
                draggingTextIndex = textMarkAt(event.x, event.y)
                if (draggingTextIndex >= 0) {
                    selectedTextIndex = draggingTextIndex
                    selectedIndex = -1
                    selectedAngleIndex = -1
                    selectedAreaIndex = -1
                    selectedAreaPointIndex = -1
                    parent?.requestDisallowInterceptTouchEvent(true)
                    notifySelectionChanged()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingTextIndex < 0) return true
                val dx = event.x - textTouchStartX
                val dy = event.y - textTouchStartY
                val threshold = 4f * resources.displayMetrics.density
                if (textTouchMoved || dx * dx + dy * dy > threshold * threshold) {
                    if (!textTouchMoved) onManipulationStarted?.invoke()
                    textTouchMoved = true
                    moveTextMark(draggingTextIndex, event.x, event.y)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (draggingTextIndex >= 0) {
                    if (textTouchMoved) {
                        moveTextMark(draggingTextIndex, event.x, event.y)
                        onMessage?.invoke("文字位置已调整")
                    } else {
                        onMessage?.invoke("已选中文字标注")
                    }
                    draggingTextIndex = -1
                    parent?.requestDisallowInterceptTouchEvent(false)
                } else if (imageRect.contains(event.x, event.y)) {
                    textMarks += TextMark(
                        x = (event.x - imageRect.left) / imageRect.width(),
                        y = (event.y - imageRect.top) / imageRect.height(),
                        text = "文字",
                    )
                    selectedTextIndex = textMarks.lastIndex
                    selectedIndex = -1
                    selectedAngleIndex = -1
                    selectedAreaIndex = -1
                    selectedAreaPointIndex = -1
                    onMessage?.invoke("已添加文字，请在下方输入内容")
                }
                notifySelectionChanged()
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                draggingTextIndex = -1
                textTouchMoved = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    private fun moveTextMark(index: Int, x: Float, y: Float) {
        val mark = textMarks.getOrNull(index) ?: return
        textMarks[index] = mark.copy(
            x = (x.coerceIn(imageRect.left, imageRect.right) - imageRect.left) / imageRect.width(),
            y = (y.coerceIn(imageRect.top, imageRect.bottom) - imageRect.top) / imageRect.height(),
        )
        invalidate()
    }

    private fun textMarkAt(x: Float, y: Float): Int {
        val tolerance = 44f * resources.displayMetrics.density
        var bestIndex = -1
        var bestDistance = Float.MAX_VALUE
        textMarks.forEachIndexed { index, mark ->
            val markX = imageRect.left + mark.x * imageRect.width()
            val markY = imageRect.top + mark.y * imageRect.height()
            val distance = (x - markX) * (x - markX) + (y - markY) * (y - markY)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return if (bestDistance <= tolerance * tolerance) bestIndex else -1
    }

    private fun drawArchitecturalTick(
        canvas: Canvas,
        x: Float,
        y: Float,
        lineAngle: Float,
        scale: Float,
        paint: Paint,
    ) {
        // Architectural dimensions use a short 45-degree oblique stroke.
        val halfLength = max(12f, 9f * scale)
        val tickAngle = lineAngle - Math.PI.toFloat() / 4f
        val dx = cos(tickAngle) * halfLength
        val dy = sin(tickAngle) * halfLength
        canvas.drawLine(x - dx, y - dy, x + dx, y + dy, paint)
    }

    private fun drawPerpendicularEndLine(
        canvas: Canvas,
        x: Float,
        y: Float,
        lineAngle: Float,
        scale: Float,
        paint: Paint,
    ) {
        val halfLength = max(21f, 16f * scale)
        val perpendicular = lineAngle + Math.PI.toFloat() / 2f
        val dx = cos(perpendicular) * halfLength
        val dy = sin(perpendicular) * halfLength
        canvas.drawLine(x - dx, y - dy, x + dx, y + dy, paint)
    }

    private fun selectNearestMark(x: Float, y: Float) {
        selectedIndex = dimensionMarkAt(x, y)
        selectedTextIndex = -1
        selectedAngleIndex = -1
        selectedAreaIndex = -1
        selectedAreaPointIndex = -1
        notifySelectionChanged()
        onMessage?.invoke(
            if (selectedIndex >= 0) "已选中尺寸线，可填写距离或选择测量记录"
            else "未选中尺寸线；请点按尺寸线附近",
        )
    }

    private fun dimensionMarkAt(x: Float, y: Float): Int {
        var bestIndex = -1
        var bestDistance = Float.MAX_VALUE
        marks.forEachIndexed { index, mark ->
            val x1 = imageRect.left + mark.startX * imageRect.width()
            val y1 = imageRect.top + mark.startY * imageRect.height()
            val x2 = imageRect.left + mark.endX * imageRect.width()
            val y2 = imageRect.top + mark.endY * imageRect.height()
            val distance = pointToSegmentDistanceSquared(x, y, x1, y1, x2, y2)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        val tolerance = 32f * resources.displayMetrics.density
        return if (bestDistance <= tolerance * tolerance) bestIndex else -1
    }

    private fun endpointAt(x: Float, y: Float): Int {
        val mark = marks.getOrNull(selectedIndex) ?: return -1
        val startX = imageRect.left + mark.startX * imageRect.width()
        val startY = imageRect.top + mark.startY * imageRect.height()
        val endX = imageRect.left + mark.endX * imageRect.width()
        val endY = imageRect.top + mark.endY * imageRect.height()
        val tolerance = 28f * resources.displayMetrics.density
        val toleranceSquared = tolerance * tolerance
        val startDistance = (x - startX) * (x - startX) + (y - startY) * (y - startY)
        val endDistance = (x - endX) * (x - endX) + (y - endY) * (y - endY)
        return when {
            startDistance <= toleranceSquared && startDistance <= endDistance -> 0
            endDistance <= toleranceSquared -> 1
            else -> -1
        }
    }

    private fun moveSelectedEndpoint(endpoint: Int, x: Float, y: Float) {
        val mark = marks.getOrNull(selectedIndex) ?: return
        val normalizedX = ((x.coerceIn(imageRect.left, imageRect.right) - imageRect.left) / imageRect.width())
        val normalizedY = ((y.coerceIn(imageRect.top, imageRect.bottom) - imageRect.top) / imageRect.height())
        marks[selectedIndex] = if (endpoint == 0) {
            mark.copy(startX = normalizedX, startY = normalizedY)
        } else {
            mark.copy(endX = normalizedX, endY = normalizedY)
        }
        invalidate()
    }

    private fun pointToSegmentDistanceSquared(
        px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float,
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0f) 0f else (((px - x1) * dx + (py - y1) * dy) / lengthSquared).coerceIn(0f, 1f)
        val nearestX = x1 + t * dx
        val nearestY = y1 + t * dy
        val offsetX = px - nearestX
        val offsetY = py - nearestY
        return offsetX * offsetX + offsetY * offsetY
    }

    private fun drawSelectionHandles(canvas: Canvas, mark: DimensionMark, rect: RectF, scale: Float) {
        val radius = max(9f, 7f * scale)
        val points = arrayOf(
            rect.left + mark.startX * rect.width() to rect.top + mark.startY * rect.height(),
            rect.left + mark.endX * rect.width() to rect.top + mark.endY * rect.height(),
        )
        points.forEach { (x, y) ->
            canvas.drawCircle(x, y, radius + max(3f, 2f * scale), selectionHaloPaint)
            canvas.drawCircle(x, y, radius, selectionPaint)
        }
    }

    private fun clearPreview() {
        dragStartX = null
        dragStartY = null
        dragEndX = null
        dragEndY = null
    }

    private fun notifySelectionChanged() {
        val selection = when {
            selectedIndex in marks.indices -> AnnotationSelection.Dimension(marks[selectedIndex])
            selectedTextIndex in textMarks.indices -> AnnotationSelection.Text(textMarks[selectedTextIndex])
            selectedAngleIndex in angleMarks.indices -> AnnotationSelection.Angle(angleMarks[selectedAngleIndex])
            selectedAreaIndex in areaMarks.indices -> AnnotationSelection.Area(
                areaMarks[selectedAreaIndex],
                selectedAreaPointIndex,
            )
            else -> null
        }
        onSelectionChanged?.invoke(selection)
    }
}
