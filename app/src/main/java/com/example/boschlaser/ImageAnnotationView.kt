package com.example.boschlaser

import com.example.boschlaser.shared.annotation.*
import com.example.boschlaser.shared.annotation.Annotation as CoreAnnotation
import java.util.UUID
import com.example.boschlaser.platform.AndroidCanvasDrawing
import com.example.boschlaser.platform.AndroidAnnotationTextPainter
import com.example.boschlaser.ui.PhotoMagnifierStyle
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.example.boschlaser.ui.AnnotationSceneState
import com.example.boschlaser.ui.AnnotationSceneStyle
import com.example.boschlaser.ui.AnnotationSceneLabels
import com.example.boschlaser.ui.drawAnnotationScene
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.ui.AnnotationControlStyle
import com.example.boschlaser.ui.PhotoDrawingViewport
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import com.example.boschlaser.ui.AnnotationCanvas
import com.example.boschlaser.ui.AnnotationCanvasSnapshot
import com.example.boschlaser.ui.fitPhotoViewport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent

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

typealias AnnotationMode = com.example.boschlaser.shared.annotation.AnnotationMode

sealed class AnnotationSelection {
    data class Dimension(val mark: DimensionMark) : AnnotationSelection()
    data class Text(val mark: TextMark) : AnnotationSelection()
    data class Angle(val mark: AngleMark) : AnnotationSelection()
    data class Area(val mark: AreaMark, val selectedPointIndex: Int) : AnnotationSelection()
}

/** Presentation metadata is explicit; the host never infers behavior from translated text. */
data class AnnotationNotice(val text: String, val showToast: Boolean = false)

class ImageAnnotationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AbstractComposeView(context, attrs) {
    private val renderGeneration = mutableIntStateOf(0)

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    private var bitmap: Bitmap? = null
    private val editor = AnnotationEditor()
    private var projectedDocument: AnnotationDocument? = null
    private var projectedState = AnnotationState()
    private fun renderState(): AnnotationState {
        if (projectedDocument !== editor.renderDocument) {
            projectedDocument = editor.renderDocument
            projectedState = editor.renderDocument.toAnnotationState()
        }
        return projectedState
    }
    private val marks get() = renderState().dimensions
    private val textMarks get() = renderState().texts
    private val angleMarks get() = renderState().angles
    private val areaMarks get() = renderState().areas
    private val imageRect = RectF()
    private val selectedIndex get() = selectedAnnotationIndex(AnnotationKind.DIMENSION)
    private val selectedTextIndex get() = selectedAnnotationIndex(AnnotationKind.TEXT)
    private val selectedAngleIndex get() = selectedAnnotationIndex(AnnotationKind.ANGLE)
    private var magnifierFocusX: Float? = null
    private var magnifierFocusY: Float? = null
    private val selectedAreaIndex get() = selectedAnnotationIndex(AnnotationKind.AREA)
    private val selectedAreaPointIndex get() = editor.state.selection?.takeIf {
        editor.renderDocument.annotations[it.annotationId]?.kind == AnnotationKind.AREA
    }?.pointIndex ?: -1
    private val mode get() = editor.state.mode
    var onMessage: ((AnnotationNotice) -> Unit)? = null
    var onSelectionChanged: ((AnnotationSelection?) -> Unit)? = null
    var onAnnotationCreated: ((AnnotationMode) -> Unit)? = null
    var onCanvasTouched: (() -> Unit)? = null
    var onManipulationStarted: (() -> Unit)? = null
    var defaultDimensionLabel: String = ""

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPainter = AndroidAnnotationTextPainter()
    private val sharedDrawing = AndroidCanvasDrawing()
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 188, 212)
        style = Paint.Style.FILL
    }
    private val selectionHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    fun setBitmap(value: Bitmap) {
        bitmap = value
        editor.restore(AnnotationDocument())
        clearSelection()
        resetMagnifier()
        clearPreview()
        notifySelectionChanged()
        requestLayout()
        requestRender()
    }

    fun snapshotState(): AnnotationState = renderState()
    fun snapshotDocument() = editor.document
    internal fun photoToScreen(point: PhotoPoint): SketchPoint {
        bitmap?.let(::calculateImageRect)
        return SketchPoint(imageRect.left + point.x.toFloat() * imageRect.width(), imageRect.top + point.y.toFloat() * imageRect.height())
    }

    fun cancelGesture() {
        editor.cancelEdit()
        resetMagnifier()
        clearPreview()
        notifySelectionChanged()
        requestRender()
    }

    fun restoreState(state: AnnotationState) = restoreDocument(state.toAnnotationDocument())

    fun restoreDocument(document: AnnotationDocument) {
        editor.restore(document)
        clearSelection()
        resetMagnifier()
        clearPreview()
        notifySelectionChanged()
        requestRender()
    }

    fun updateSelectedLabel(label: String): Boolean {
        if (selectedIndex !in marks.indices) return false
        replaceDimension(selectedIndex, marks[selectedIndex].copy(label = label.trim()))
        requestRender()
        return true
    }

    fun setMode(value: AnnotationMode, preserveSelection: Boolean = false) {
        if (mode == value) return
        editor.setMode(value, preserveSelection)
        resetMagnifier()
        clearPreview()
        notifySelectionChanged()
        requestRender()
    }

    fun updateSelectedText(text: String): Boolean {
        if (selectedTextIndex !in textMarks.indices) return false
        replaceText(selectedTextIndex, textMarks[selectedTextIndex].copy(text = text))
        requestRender()
        return true
    }

    fun updateSelectedAngleLabel(label: String): Boolean {
        if (selectedAngleIndex !in angleMarks.indices) return false
        replaceAngle(selectedAngleIndex, angleMarks[selectedAngleIndex].copy(label = label.trim()))
        requestRender()
        return true
    }

    fun updateSelectedAreaLabel(label: String): Boolean {
        if (selectedAreaIndex !in areaMarks.indices) return false
        replaceArea(selectedAreaIndex, areaMarks[selectedAreaIndex].copy(label = label.trim()))
        requestRender()
        return true
    }

    fun updateSelectedTextSize(textSize: Int): Boolean {
        if (selectedTextIndex !in textMarks.indices) return false
        replaceText(selectedTextIndex, textMarks[selectedTextIndex].copy(textSize = textSize.coerceIn(0, 2)))
        requestRender()
        return true
    }

    fun updateSelectedTextBackground(enabled: Boolean): Boolean {
        if (selectedTextIndex !in textMarks.indices) return false
        replaceText(selectedTextIndex, textMarks[selectedTextIndex].copy(whiteBackground = enabled))
        requestRender()
        return true
    }

    fun updateSelectedLineThickness(thickness: Int): Boolean {
        if (selectedIndex !in marks.indices) return false
        replaceDimension(selectedIndex, marks[selectedIndex].copy(thickness = thickness.coerceIn(0, 2)))
        requestRender()
        return true
    }

    fun updateSelectedColor(color: Int): Boolean {
        when {
            selectedIndex in marks.indices -> replaceDimension(selectedIndex, marks[selectedIndex].copy(color = color))
            selectedTextIndex in textMarks.indices -> replaceText(selectedTextIndex, textMarks[selectedTextIndex].copy(color = color))
            selectedAngleIndex in angleMarks.indices -> replaceAngle(selectedAngleIndex, angleMarks[selectedAngleIndex].copy(color = color))
            selectedAreaIndex in areaMarks.indices -> replaceArea(selectedAreaIndex, areaMarks[selectedAreaIndex].copy(color = color))
            else -> return false
        }
        requestRender()
        return true
    }

    fun undo(): Boolean {
        if (!editor.undo()) return false
        clearSelection()
        requestRender()
        return true
    }

    fun deleteSelected(): Boolean {
        when {
            selectedIndex in marks.indices -> deleteMark(AnnotationKind.DIMENSION, selectedIndex)
            selectedTextIndex in textMarks.indices -> deleteMark(AnnotationKind.TEXT, selectedTextIndex)
            selectedAngleIndex in angleMarks.indices -> deleteMark(AnnotationKind.ANGLE, selectedAngleIndex)
            selectedAreaIndex in areaMarks.indices -> deleteMark(AnnotationKind.AREA, selectedAreaIndex)
            else -> return false
        }
        clearSelection()
        clearPreview()
        notifySelectionChanged()
        notifyMessage(context.getString(R.string.annotation_deleted_hint))
        requestRender()
        return true
    }

    fun deleteSelectedAreaPoint(): Boolean {
        val mark = areaMarks.getOrNull(selectedAreaIndex) ?: return false
        if (selectedAreaPointIndex !in mark.points.indices || mark.points.size <= 3) return false
        val target = annotation(AnnotationKind.AREA, selectedAreaIndex) ?: return false
        val pointIndex = selectedAreaPointIndex
        if (!edit(DeleteAreaPointCommand(target.id, pointIndex))) return false
        selectArea(selectedAreaIndex, pointIndex.coerceAtMost(areaMarks[selectedAreaIndex].points.lastIndex))
        notifySelectionChanged()
        notifyMessage(context.getString(R.string.annotation_area_point_deleted_hint))
        requestRender()
        return true
    }

    fun clearMarks() {
        editor.execute(ClearAnnotationsCommand())
        editor.clearPendingArea()
        clearSelection()
        clearPreview()
        notifySelectionChanged()
        requestRender()
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
        drawScene(canvas, rect, scale, AnnotationSceneState(), showControls = false, showPreviews = false)
    }

    private fun requestRender() {
        renderGeneration.intValue++
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bitmap?.let(::calculateImageRect)
        requestRender()
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun Content() {
        AnnotationCanvas(snapshot = { sizePx ->
            renderGeneration.intValue // Observe edits during drawing without rebuilding the host tree.
            val viewport = bitmap?.let { source ->
                fitPhotoViewport(sizePx, Vec2(source.width.toDouble(), source.height.toDouble())).also {
                    imageRect.set(it.left.toFloat(), it.top.toFloat(), (it.left + it.width).toFloat(), (it.top + it.height).toFloat())
                }
            }
            AnnotationCanvasSnapshot(editor.renderDocument, viewport, drawingSceneState(), annotationSceneStyle(),
                annotationSceneLabels(), androidx.compose.ui.graphics.Color(context.getColor(R.color.white)),
                context.getString(R.string.annotation_photo_empty_hint),
                resources.getDimension(R.dimen.annotation_empty_hint_text_size).toDouble(),
                context.getColor(R.color.annotation_empty_hint_text), photoMagnifierStyle(),
                magnifierFocusX?.let { x -> magnifierFocusY?.let { y -> Vec2(x.toDouble(), y.toDouble()) } })
        }, painter = textPainter, drawPhoto = { viewport ->
            bitmap?.let { source ->
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawBitmap(source, null, RectF(viewport.left.toFloat(), viewport.top.toFloat(),
                        (viewport.left + viewport.width).toFloat(), (viewport.top + viewport.height).toFloat()), imagePaint)
                }
            }
        }, modifier = Modifier.fillMaxWidth()
            .height((resources.getDimension(R.dimen.annotation_canvas_preferred_height) / resources.displayMetrics.density).dp)
            .testTag("annotation-canvas").pointerInteropFilter { onTouchEvent(it) })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        if (bitmap == null) return false
        calculateImageRect(bitmap!!)
        if (imageRect.width() <= 0f || imageRect.height() <= 0f) return false
        if (mode == AnnotationMode.MOVE) return handleMoveTouch(event)
        if (mode == AnnotationMode.AREA) return handleAreaTouch(event)
        if (mode == AnnotationMode.TEXT) return handleTextTouch(event)
        if (mode == AnnotationMode.ANGLE) return handleAngleTouch(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!imageRect.contains(event.x, event.y)) return false
                onCanvasTouched?.invoke()
                if (!editor.beginDimension(normalizedPhotoPoint(event.x, event.y))) return false
                onManipulationStarted?.invoke()
                parent?.requestDisallowInterceptTouchEvent(true)
                requestRender()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!editor.updateDimension(normalizedPhotoPoint(event.x, event.y))) return false
                magnifierFocusX = event.x.coerceIn(imageRect.left, imageRect.right)
                magnifierFocusY = event.y.coerceIn(imageRect.top, imageRect.bottom)
                requestRender()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (editor.state.creation.dimension == null) return false
                magnifierFocusX = null
                magnifierFocusY = null
                parent?.requestDisallowInterceptTouchEvent(false)
                val dragThreshold = 12f * resources.displayMetrics.density
                if (editor.finishDimension(normalizedPhotoPoint(event.x, event.y),
                        Vec2(imageRect.width().toDouble(), imageRect.height().toDouble()), dragThreshold.toDouble(),
                        UUID.randomUUID().toString(), defaultDimensionLabel, context.getColor(R.color.annotation_color_red))) {
                    notifySelectionChanged()
                    notifyMessage(
                        if (defaultDimensionLabel.isBlank()) {
                            context.getString(R.string.annotation_dimension_empty_created_hint)
                        } else {
                            context.getString(R.string.annotation_dimension_default_created_hint, defaultDimensionLabel)
                        },
                    )
                    onAnnotationCreated?.invoke(AnnotationMode.DIMENSION)
                }
                clearPreview()
                requestRender()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                magnifierFocusX = null
                magnifierFocusY = null
                parent?.requestDisallowInterceptTouchEvent(false)
                clearPreview()
                requestRender()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun dimensionPreview(): DimensionAnnotation? = editor.state.creation.dimension?.let {
        DimensionAnnotation("preview", it.start, it.end, "", context.getColor(R.color.annotation_color_red))
    }

    private fun handleMoveTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!imageRect.contains(event.x, event.y)) return false
                onCanvasTouched?.invoke()
                startMoveTarget(event.x, event.y)
                if (editor.state.drag != null) parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                moveTarget(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val drag = editor.state.drag
                if (drag?.started == true) {
                    moveTarget(event.x, event.y)
                    notifyMessage(moveCompletionMessage(drag))
                } else if (drag?.kind == AnnotationKind.AREA && drag.target.pointIndex == null && selectedAreaIndex in areaMarks.indices) {
                    addAreaPointOnDoubleTap(event.x, event.y, event.eventTime)
                }
                editor.commitEdit()
                resetMagnifier()
                parent?.requestDisallowInterceptTouchEvent(false)
                notifySelectionChanged()
                requestRender()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                editor.cancelEdit()
                resetMagnifier()
                notifySelectionChanged()
                parent?.requestDisallowInterceptTouchEvent(false)
                requestRender()
                return true
            }
        }
        return true
    }

    private fun startMoveTarget(x: Float, y: Float) {
        editor.cancelEdit()
        resetMagnifier()
        val target = annotationHitTest().pick(unclampedPhotoPoint(x, y), editor.state.selection)
        if (target == null) clearSelection()
        else editor.beginDrag(target, unclampedPhotoPoint(x, y))
        if (editor.state.drag?.target?.pointIndex != null) {
            magnifierFocusX = x
            magnifierFocusY = y
        }
        notifySelectionChanged()
        requestRender()
    }

    private fun moveTarget(x: Float, y: Float) {
        val drag = editor.state.drag ?: return
        val threshold = resources.getDimension(R.dimen.annotation_move_threshold).toDouble()
        if (!editor.dragTo(unclampedPhotoPoint(x, y), Vec2(imageRect.width().toDouble(), imageRect.height().toDouble()), threshold)) return
        if (!drag.started) onManipulationStarted?.invoke()
        if (drag.target.pointIndex != null) {
            magnifierFocusX = x.coerceIn(imageRect.left, imageRect.right)
            magnifierFocusY = y.coerceIn(imageRect.top, imageRect.bottom)
        }
        requestRender()
    }

    private fun moveCompletionMessage(drag: AnnotationDrag): String {
        val resource = if (drag.target.pointIndex == null) when (drag.kind) {
            AnnotationKind.DIMENSION -> R.string.annotation_move_dimension
            AnnotationKind.TEXT -> R.string.annotation_move_text
            AnnotationKind.ANGLE -> R.string.annotation_move_angle
            AnnotationKind.AREA -> R.string.annotation_move_area
        } else when (drag.kind) {
            AnnotationKind.DIMENSION -> R.string.annotation_move_dimension_endpoint
            AnnotationKind.ANGLE -> R.string.annotation_move_angle_point
            AnnotationKind.AREA -> R.string.annotation_move_area_point
            AnnotationKind.TEXT -> R.string.annotation_move_text
        }
        return context.getString(resource)
    }

    private fun notifyMessage(text: String, showToast: Boolean = false) {
        onMessage?.invoke(AnnotationNotice(text, showToast))
    }

    private fun resetMagnifier() {
        magnifierFocusX = null
        magnifierFocusY = null
    }

    private fun annotation(kind: AnnotationKind, index: Int): CoreAnnotation? =
        editor.renderDocument.annotations.values.filter { it.kind == kind }.getOrNull(index)
    private fun edit(command: AnnotationCommand): Boolean =
        if (editor.hasPreview) editor.preview(command) else editor.execute(command)
    private fun deleteMark(kind: AnnotationKind, index: Int): Boolean =
        annotation(kind, index)?.let { edit(DeleteAnnotationCommand(it.id)) } ?: false
    private fun replaceDimension(index: Int, mark: DimensionMark): Boolean =
        (annotation(AnnotationKind.DIMENSION, index) as? DimensionAnnotation)?.let {
            edit(UpdateAnnotationCommand(it.copy(label = mark.label, colorArgb = mark.color, thicknessPreset = mark.thickness)))
        } ?: false
    private fun replaceText(index: Int, mark: TextMark): Boolean =
        (annotation(AnnotationKind.TEXT, index) as? TextAnnotation)?.let {
            edit(UpdateAnnotationCommand(it.copy(text = mark.text, colorArgb = mark.color, sizePreset = mark.textSize, whiteBackground = mark.whiteBackground)))
        } ?: false
    private fun replaceAngle(index: Int, mark: AngleMark): Boolean =
        (annotation(AnnotationKind.ANGLE, index) as? AngleAnnotation)?.let {
            edit(UpdateAnnotationCommand(it.copy(label = mark.label, colorArgb = mark.color)))
        } ?: false
    private fun replaceArea(index: Int, mark: AreaMark): Boolean =
        (annotation(AnnotationKind.AREA, index) as? AreaAnnotation)?.let {
            edit(UpdateAnnotationCommand(it.copy(label = mark.label, colorArgb = mark.color)))
        } ?: false
    private fun normalizedPhotoPoint(x: Float, y: Float) =
        imageRect.photoViewport().photo(Vec2(x.toDouble(), y.toDouble()), clampToPhoto = true)

    private fun unclampedPhotoPoint(x: Float, y: Float) =
        imageRect.photoViewport().photo(Vec2(x.toDouble(), y.toDouble()))

    private fun calculateImageRect(source: Bitmap) {
        val viewport = fitPhotoViewport(Vec2(width.toDouble(), height.toDouble()), Vec2(source.width.toDouble(), source.height.toDouble()))
        imageRect.set(viewport.left.toFloat(), viewport.top.toFloat(), (viewport.left + viewport.width).toFloat(),
            (viewport.top + viewport.height).toFloat())
    }

    private fun photoMagnifierStyle() = PhotoMagnifierStyle(
        androidx.compose.ui.graphics.Color(context.getColor(R.color.annotation_magnifier_background)),
        androidx.compose.ui.graphics.Color(context.getColor(R.color.white)), androidx.compose.ui.graphics.Color(selectionPaint.color),
        resources.getDimension(R.dimen.annotation_magnifier_border_width), resources.getDimension(R.dimen.annotation_magnifier_crosshair_width),
        resources.getDimension(R.dimen.annotation_magnifier_crosshair_half_length))

    private fun handleAreaTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!imageRect.contains(event.x, event.y)) return false
                onCanvasTouched?.invoke()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val result = editor.placeAreaPoint(normalizedPhotoPoint(event.x, event.y),
                    UUID.randomUUID().toString(), context.getColor(R.color.annotation_color_red))
                when (result) {
                    is AnnotationPlacementResult.Pending -> notifyMessage(if (result.pointCount == 1)
                        context.getString(R.string.annotation_area_first_point_hint) else context.getString(R.string.annotation_area_second_point_hint), showToast = result.pointCount == 1)
                    is AnnotationPlacementResult.Created -> notifyMessage(context.getString(R.string.annotation_area_created_hint))
                    AnnotationPlacementResult.Rejected -> Unit
                }
                notifySelectionChanged()
                if (result is AnnotationPlacementResult.Created) onAnnotationCreated?.invoke(AnnotationMode.AREA)
                requestRender()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                requestRender()
                return true
            }
        }
        return true
    }

    private fun selectedAnnotationIndex(kind: AnnotationKind): Int {
        val id = editor.state.selection?.annotationId ?: return -1
        return editor.renderDocument.annotations.values.filter { it.kind == kind }.indexOfFirst { it.id == id }
    }

    private fun selectAnnotation(kind: AnnotationKind, index: Int, pointIndex: Int? = null) {
        editor.select(annotation(kind, index)?.let { AnnotationSelectionTarget(it.id, pointIndex) })
    }

    private fun selectArea(index: Int, pointIndex: Int? = null) = selectAnnotation(AnnotationKind.AREA, index, pointIndex)
    private fun clearSelection() = editor.select(null)
    internal fun editorState() = editor.state

    private fun addAreaPointOnDoubleTap(x: Float, y: Float, atMillis: Long) {
        val areaId = editor.state.selection?.annotationId ?: return
        val edgeIndex = annotationHitTest().areaEdgeAt(areaId, unclampedPhotoPoint(x, y)) ?: return
        when (editor.tapAreaEdge(areaId, edgeIndex, atMillis,
            resources.getInteger(R.integer.annotation_area_edge_double_tap_window_ms).toLong())) {
            AreaEdgeTapResult.INSERTED -> notifyMessage(context.getString(R.string.annotation_area_point_added))
            AreaEdgeTapResult.PENDING -> notifyMessage(context.getString(R.string.annotation_area_point_add_again))
            AreaEdgeTapResult.REJECTED -> Unit
        }
    }

    private fun handleAngleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!imageRect.contains(event.x, event.y)) return false
                onCanvasTouched?.invoke()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val angleCreated = addPendingAnglePoint(event.x, event.y)
                notifySelectionChanged()
                if (angleCreated) onAnnotationCreated?.invoke(AnnotationMode.ANGLE)
                requestRender()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                requestRender()
                return true
            }
        }
        return true
    }

    private fun addPendingAnglePoint(x: Float, y: Float): Boolean {
        val result = editor.placeAnglePoint(normalizedPhotoPoint(x, y), UUID.randomUUID().toString(),
            context.getColor(R.color.annotation_color_red))
        when (result) {
            is AnnotationPlacementResult.Pending -> notifyMessage(if (result.pointCount == 1)
                context.getString(R.string.annotation_angle_first_point_hint) else context.getString(R.string.annotation_angle_vertex_hint), showToast = true)
            is AnnotationPlacementResult.Created -> notifyMessage(context.getString(R.string.annotation_angle_created_hint), showToast = true)
            AnnotationPlacementResult.Rejected -> Unit
        }
        return result is AnnotationPlacementResult.Created
    }

    private fun handleTextTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!imageRect.contains(event.x, event.y)) return false
                onCanvasTouched?.invoke()
                return true
            }
            MotionEvent.ACTION_UP -> {
                var textCreated = false
                if (imageRect.contains(event.x, event.y)) {
                    textCreated = editor.placeText(normalizedPhotoPoint(event.x, event.y), UUID.randomUUID().toString(),
                        context.getString(R.string.annotation_text_placeholder), context.getColor(R.color.annotation_color_red))
                    if (textCreated) notifyMessage(context.getString(R.string.annotation_text_created_hint))
                }
                notifySelectionChanged()
                if (textCreated) onAnnotationCreated?.invoke(AnnotationMode.TEXT)
                requestRender()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                return true
            }
        }
        return true
    }

    private fun annotationHitTest() = AnnotationHitTest(editor.renderDocument,
        Vec2(imageRect.width().toDouble(), imageRect.height().toDouble()), AnnotationHitTolerances(
            resources.getDimension(R.dimen.annotation_hit_line).toDouble(),
            resources.getDimension(R.dimen.annotation_hit_text).toDouble(),
            resources.getDimension(R.dimen.annotation_hit_control).toDouble(),
            resources.getDimension(R.dimen.annotation_hit_area_vertex).toDouble(),
            resources.getDimension(R.dimen.annotation_hit_area_edge).toDouble()))

    private fun drawingSceneState() = AnnotationSceneState(
        selectedIds = setOfNotNull(editor.state.selection?.annotationId),
        selectedAreaPoint = selectedAreaPointIndex,
        dimensionPreview = dimensionPreview(),
        pendingAngle = editor.state.creation.anglePoints,
        pendingArea = editor.state.creation.areaPoints,
    )

    private fun drawScene(canvas: Canvas, rect: RectF, scale: Float, state: AnnotationSceneState,
        showControls: Boolean = true, showPreviews: Boolean = true, dimensionPreviewBeforeText: Boolean = false) {
        val style = annotationSceneStyle()
        val labels = annotationSceneLabels()
        sharedDrawing.draw(canvas, drawingDensity()) {
            drawAnnotationScene(editor.renderDocument, rect.photoViewport(), scale.toDouble(), state, style, textPainter, labels,
                showControls, showPreviews, dimensionPreviewBeforeText)
        }
    }

    private fun annotationSceneStyle() = AnnotationSceneStyle(annotationControlStyle(),
        androidx.compose.ui.graphics.Color(context.getColor(R.color.annotation_label_background)),
        androidx.compose.ui.graphics.Color(context.getColor(R.color.annotation_color_red)))
    private fun annotationSceneLabels() = AnnotationSceneLabels(context.getString(R.string.annotation_text_placeholder)) {
        context.getString(R.string.annotation_angle_label, it)
    }

    private fun RectF.photoViewport() = PhotoDrawingViewport(left.toDouble(), top.toDouble(), width().toDouble(), height().toDouble())
    private fun drawingDensity() = Density(resources.displayMetrics.density, resources.configuration.fontScale)
    private fun annotationControlStyle() = AnnotationControlStyle(androidx.compose.ui.graphics.Color(selectionPaint.color),
        androidx.compose.ui.graphics.Color(selectionHaloPaint.color), androidx.compose.ui.graphics.Color(context.getColor(R.color.annotation_selected_vertex)))

    private fun clearPreview() = editor.cancelDimension()

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
