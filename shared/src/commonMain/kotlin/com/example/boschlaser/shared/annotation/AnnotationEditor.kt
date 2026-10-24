package com.example.boschlaser.shared.annotation

import com.example.boschlaser.shared.geometry.Vec2
import kotlin.math.hypot

/** A separate document owner: annotation editing never mutates the sketch or the photo bitmap. */
class AnnotationEditor(initial: AnnotationDocument = AnnotationDocument(), private val historyLimit: Int = 50) {
    init { require(historyLimit > 0) }
    var document = initial.detached()
        private set
    var state = AnnotationEditorState()
        private set
    private var draft: AnnotationDocument? = null
    val renderDocument get() = draft ?: document
    val hasPreview get() = draft != null
    private val undo = ArrayDeque<Delta>()
    private val redo = ArrayDeque<Delta>()
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    fun select(target: AnnotationSelectionTarget?) {
        state = state.copy(selection = target)
        validateState()
    }
    fun setMode(mode: AnnotationMode, preserveSelection: Boolean = false) {
        if (state.mode == mode) return
        cancelEdit()
        state = state.copy(mode = mode, selection = if (preserveSelection) state.selection else null,
            creation = AnnotationCreationState())
    }
    fun beginDimension(point: PhotoPoint): Boolean {
        if (state.mode != AnnotationMode.DIMENSION) return false
        val start = point.clamped()
        state = state.copy(creation = state.creation.copy(dimension = DimensionPlacement(start, start)))
        return true
    }
    fun updateDimension(point: PhotoPoint): Boolean {
        val dimension = state.creation.dimension ?: return false
        state = state.copy(creation = state.creation.copy(dimension = dimension.copy(end = point.clamped())))
        return true
    }
    fun cancelDimension() {
        state = state.copy(creation = state.creation.copy(dimension = null))
    }
    /** A screen-space threshold is supplied by the host, but the decision uses original Double geometry. */
    fun finishDimension(point: PhotoPoint, photoSizePx: Vec2, minimumLengthPx: Double,
        id: String, label: String, colorArgb: Int): Boolean {
        require(photoSizePx.x > 0 && photoSizePx.y > 0 && minimumLengthPx.isFinite() && minimumLengthPx >= 0)
        val dimension = state.creation.dimension ?: return false
        val end = point.clamped()
        val dx = (end.x - dimension.start.x) * photoSizePx.x
        val dy = (end.y - dimension.start.y) * photoSizePx.y
        if (hypot(dx, dy) <= minimumLengthPx) {
            cancelDimension()
            return false
        }
        if (id.isBlank() || id in document.annotations) return false
        if (!execute(AddAnnotationCommand(DimensionAnnotation(id, dimension.start, end, label, colorArgb)))) return false
        state = state.copy(selection = AnnotationSelectionTarget(id), creation = state.creation.copy(dimension = null))
        return true
    }
    fun placeAnglePoint(point: PhotoPoint, id: String, colorArgb: Int): AnnotationPlacementResult {
        if (state.mode != AnnotationMode.ANGLE) return AnnotationPlacementResult.Rejected
        val points = state.creation.anglePoints + point.clamped()
        if (points.size < 3) {
            state = state.copy(selection = null, creation = state.creation.copy(anglePoints = points))
            return AnnotationPlacementResult.Pending(points.size)
        }
        if (id.isBlank() || id in document.annotations) return AnnotationPlacementResult.Rejected
        val angle = AngleAnnotation(id, points[1], points[0], points[2], "", colorArgb)
        if (!execute(AddAnnotationCommand(angle))) return AnnotationPlacementResult.Rejected
        state = state.copy(selection = AnnotationSelectionTarget(id), creation = state.creation.copy(anglePoints = emptyList()))
        return AnnotationPlacementResult.Created(id)
    }
    fun placeAreaPoint(point: PhotoPoint, id: String, colorArgb: Int): AnnotationPlacementResult {
        if (state.mode != AnnotationMode.AREA) return AnnotationPlacementResult.Rejected
        val points = state.creation.areaPoints + point.clamped()
        if (points.size < 3) {
            state = state.copy(creation = state.creation.copy(areaPoints = points))
            return AnnotationPlacementResult.Pending(points.size)
        }
        if (id.isBlank() || id in document.annotations) return AnnotationPlacementResult.Rejected
        if (!execute(AddAnnotationCommand(AreaAnnotation(id, points, "", colorArgb)))) return AnnotationPlacementResult.Rejected
        state = state.copy(selection = AnnotationSelectionTarget(id), creation = state.creation.copy(areaPoints = emptyList()))
        return AnnotationPlacementResult.Created(id)
    }
    fun placeText(point: PhotoPoint, id: String, text: String, colorArgb: Int): Boolean {
        if (state.mode != AnnotationMode.TEXT || id.isBlank() || id in document.annotations) return false
        if (!execute(AddAnnotationCommand(TextAnnotation(id, point.clamped(), text, colorArgb)))) return false
        state = state.copy(selection = AnnotationSelectionTarget(id))
        return true
    }
    fun clearPendingArea() {
        state = state.copy(creation = state.creation.copy(areaPoints = emptyList()))
    }
    fun beginDrag(target: AnnotationSelectionTarget, point: PhotoPoint): Boolean {
        if (state.mode != AnnotationMode.MOVE) return false
        val annotation = document.annotations[target.annotationId] ?: return false
        if (target.pointIndex != null && target.pointIndex !in annotation.points.indices) return false
        cancelEdit()
        beginEdit()
        state = state.copy(selection = target, drag = AnnotationDrag(target, annotation.kind, point))
        return true
    }
    /** True once the drag crosses the supplied screen threshold, including clamped no-op movement. */
    fun dragTo(point: PhotoPoint, photoSizePx: Vec2, minimumLengthPx: Double): Boolean {
        require(photoSizePx.x > 0 && photoSizePx.y > 0 && minimumLengthPx.isFinite() && minimumLengthPx >= 0)
        val drag = state.drag ?: return false
        val dx = point.x - drag.start.x
        val dy = point.y - drag.start.y
        if (!dx.isFinite() || !dy.isFinite()) return false
        if (!drag.started && hypot(dx * photoSizePx.x, dy * photoSizePx.y) <= minimumLengthPx) return false
        val pointIndex = drag.target.pointIndex
        if (pointIndex == null) previewFromStart(MoveAnnotationCommand(drag.target.annotationId, dx, dy))
        else preview(MoveAnnotationPointCommand(drag.target.annotationId, pointIndex, point))
        state = state.copy(drag = drag.copy(started = true))
        return true
    }
    fun tapAreaEdge(areaId: String, edgeIndex: Int, atMillis: Long, windowMillis: Long): AreaEdgeTapResult {
        require(atMillis >= 0 && windowMillis >= 0)
        if (state.mode != AnnotationMode.MOVE) return AreaEdgeTapResult.REJECTED
        val area = renderDocument.annotations[areaId] as? AreaAnnotation ?: return AreaEdgeTapResult.REJECTED
        if (edgeIndex !in area.points.indices) return AreaEdgeTapResult.REJECTED
        val start = area.points[edgeIndex]
        val end = area.points[(edgeIndex + 1) % area.points.size]
        val previous = state.areaEdgeTap
        if (previous != null && previous.areaId == areaId && previous.edgeIndex == edgeIndex &&
            previous.start == start && previous.end == end && atMillis >= previous.atMillis &&
            atMillis - previous.atMillis <= windowMillis) {
            val command = InsertAreaMidpointCommand(areaId, edgeIndex)
            val inserted = if (hasPreview) preview(command) else execute(command)
            if (!inserted) return AreaEdgeTapResult.REJECTED
            state = state.copy(selection = AnnotationSelectionTarget(areaId, edgeIndex + 1), areaEdgeTap = null)
            return AreaEdgeTapResult.INSERTED
        }
        state = state.copy(areaEdgeTap = AreaEdgeTap(areaId, edgeIndex, atMillis, start, end))
        return AreaEdgeTapResult.PENDING
    }
    private fun validateState() {
        val selected = state.selection
        if (selected != null) {
            val annotation = renderDocument.annotations[selected.annotationId]
            val valid = when {
                annotation == null -> null
                selected.pointIndex != null && selected.pointIndex !in annotation.points.indices -> selected.copy(pointIndex = null)
                else -> selected
            }
            if (valid != selected) state = state.copy(selection = valid)
        }
        val drag = state.drag
        if (drag != null) {
            val annotation = renderDocument.annotations[drag.target.annotationId]
            if (annotation == null || annotation.kind != drag.kind ||
                (drag.target.pointIndex != null && drag.target.pointIndex !in annotation.points.indices)) {
                state = state.copy(drag = null)
            }
        }
        val tap = state.areaEdgeTap
        if (tap != null) {
            val area = renderDocument.annotations[tap.areaId] as? AreaAnnotation
            if (area == null || tap.edgeIndex !in area.points.indices ||
                area.points[tap.edgeIndex] != tap.start || area.points[(tap.edgeIndex + 1) % area.points.size] != tap.end) {
                state = state.copy(areaEdgeTap = null)
            }
        }
    }
    fun beginEdit() { if (draft == null) draft = document }
    fun preview(command: AnnotationCommand): Boolean {
        val updated = command.apply(renderDocument) ?: return false
        if (updated == renderDocument) return false
        draft = updated.detached()
        validateState()
        return true
    }
    /** Absolute displacement from gesture start avoids accumulating successive pointer events. */
    fun previewFromStart(command: AnnotationCommand): Boolean {
        val updated = command.apply(document) ?: return false
        if (updated == renderDocument) return false
        draft = updated.detached()
        validateState()
        return true
    }
    fun cancelEdit() {
        draft = null
        state = state.copy(drag = null)
        validateState()
    }
    fun commitEdit(): Boolean {
        val updated = draft
        draft = null
        state = state.copy(drag = null)
        if (updated == null) return false
        return publish(updated)
    }
    fun execute(command: AnnotationCommand): Boolean {
        cancelEdit()
        return publish(command.apply(document) ?: return false)
    }
    private fun publish(updated: AnnotationDocument): Boolean {
        val frozen = updated.detached()
        if (frozen == document) return false
        undo.addLast(Delta.between(document, frozen))
        if (undo.size > historyLimit) undo.removeFirst()
        redo.clear()
        document = frozen
        validateState()
        return true
    }
    fun undo(): Boolean {
        cancelEdit()
        val change = undo.removeLastOrNull() ?: return false
        document = change.apply(document, false)
        validateState()
        redo.addLast(change)
        return true
    }
    fun redo(): Boolean {
        cancelEdit()
        val change = redo.removeLastOrNull() ?: return false
        document = change.apply(document, true)
        validateState()
        undo.addLast(change)
        return true
    }
    fun restore(document: AnnotationDocument) {
        cancelEdit(); this.document = document.detached(); undo.clear(); redo.clear()
        state = state.copy(selection = null, creation = AnnotationCreationState(), areaEdgeTap = null)
    }
    private data class Change(val before: Annotation?, val after: Annotation?)
    private data class Delta(val changes: Map<String, Change>, val order: Pair<List<String>, List<String>>?,
        val photo: Pair<String?, String?>) {
        fun apply(document: AnnotationDocument, forward: Boolean): AnnotationDocument {
            val values = document.annotations.toMutableMap()
            changes.forEach { (id, change) ->
                val value = if (forward) change.after else change.before
                if (value == null) values.remove(id) else values[id] = value
            }
            val keys = order?.let { if (forward) it.second else it.first }
            return AnnotationDocument(keys?.associateWith { values.getValue(it) } ?: values.toMap(),
                if (forward) photo.second else photo.first)
        }
        companion object {
            fun between(before: AnnotationDocument, after: AnnotationDocument) = Delta(
                (before.annotations.keys + after.annotations.keys).filter { before.annotations[it] != after.annotations[it] }
                    .associateWith { Change(before.annotations[it], after.annotations[it]) },
                if (before.annotations.keys.toList() == after.annotations.keys.toList()) null
                    else before.annotations.keys.toList() to after.annotations.keys.toList(),
                before.photoAssetId to after.photoAssetId,
            )
        }
    }
}
private fun AnnotationDocument.detached() = copy(annotations = annotations.mapValues { (_, annotation) ->
    if (annotation is AreaAnnotation) annotation.copy(points = annotation.points.toList()) else annotation
})

private fun PhotoPoint.clamped() = PhotoPoint(x.coerceIn(0.0, 1.0), y.coerceIn(0.0, 1.0))
