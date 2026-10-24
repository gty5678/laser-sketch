package com.example.boschlaser.shared.annotation

import com.example.boschlaser.shared.geometry.Vec2
import kotlin.math.hypot

/** Tolerances are screen pixels supplied by the platform, not photo fractions or document units. */
data class AnnotationHitTolerances(
    val linePx: Double,
    val textPx: Double,
    val controlPx: Double,
    val areaVertexPx: Double,
    val areaEdgePx: Double,
) {
    init { require(listOf(linePx, textPx, controlPx, areaVertexPx, areaEdgePx).all { it.isFinite() && it >= 0 }) }
}

/** Read-only picking against original Double geometry; pixel scaling may differ on the two axes. */
class AnnotationHitTest(private val document: AnnotationDocument, private val photoSizePx: Vec2,
    private val tolerances: AnnotationHitTolerances) {
    init { require(photoSizePx.x > 0 && photoSizePx.y > 0) }

    fun pick(point: PhotoPoint, selection: AnnotationSelectionTarget? = null): AnnotationSelectionTarget? {
        val selected = document.annotations[selection?.annotationId]
        if (selected is DimensionAnnotation || selected is AngleAnnotation) {
            pointAt(selected, point, tolerances.controlPx)?.let { return AnnotationSelectionTarget(selected.id, it) }
        }
        if (selected is AreaAnnotation) {
            pointAt(selected, point, tolerances.areaVertexPx)?.let { return AnnotationSelectionTarget(selected.id, it) }
        }
        val areas = document.annotations.values.filterIsInstance<AreaAnnotation>()
        for (area in areas.asReversed()) {
            if (area.id == selected?.id) continue
            pointAt(area, point, tolerances.areaVertexPx)?.let { return AnnotationSelectionTarget(area.id, it) }
        }
        nearest<TextAnnotation>(tolerances.textPx) { distance(it.position, point) }?.let { return AnnotationSelectionTarget(it.id) }
        nearest<DimensionAnnotation>(tolerances.linePx) { segmentDistance(it.start, it.end, point) }?.let { return AnnotationSelectionTarget(it.id) }
        nearest<AngleAnnotation>(tolerances.linePx) {
            minOf(segmentDistance(it.vertex, it.first, point), segmentDistance(it.vertex, it.second, point))
        }?.let { return AnnotationSelectionTarget(it.id) }
        for (area in areas.asReversed()) {
            if (areaEdgeAt(area.id, point) != null || inside(area, point)) return AnnotationSelectionTarget(area.id)
        }
        return null
    }

    fun areaEdgeAt(areaId: String, point: PhotoPoint): Int? {
        val area = document.annotations[areaId] as? AreaAnnotation ?: return null
        var index: Int? = null
        var best = Double.POSITIVE_INFINITY
        area.points.indices.forEach { candidate ->
            val distance = segmentDistance(area.points[candidate], area.points[(candidate + 1) % area.points.size], point)
            if (distance < best) { index = candidate; best = distance }
        }
        return index.takeIf { best <= tolerances.areaEdgePx }
    }

    private inline fun <reified T : Annotation> nearest(tolerance: Double,
        distanceTo: (T) -> Double): T? {
        var best = Double.POSITIVE_INFINITY
        var selected: T? = null
        document.annotations.values.filterIsInstance<T>().forEach { annotation ->
            val distance = distanceTo(annotation)
            if (distance < best) { best = distance; selected = annotation }
        }
        return selected.takeIf { best <= tolerance }
    }

    private fun pointAt(annotation: Annotation, point: PhotoPoint, tolerance: Double): Int? {
        var index: Int? = null
        var best = Double.POSITIVE_INFINITY
        annotation.points.forEachIndexed { candidate, position ->
            val distance = distance(position, point)
            if (distance < best) { index = candidate; best = distance }
        }
        return index.takeIf { best <= tolerance }
    }

    private fun distance(a: PhotoPoint, b: PhotoPoint) = hypot((a.x - b.x) * photoSizePx.x, (a.y - b.y) * photoSizePx.y)

    private fun segmentDistance(start: PhotoPoint, end: PhotoPoint, point: PhotoPoint): Double {
        val ax = (start.x - point.x) * photoSizePx.x
        val ay = (start.y - point.y) * photoSizePx.y
        val bx = (end.x - point.x) * photoSizePx.x
        val by = (end.y - point.y) * photoSizePx.y
        val dx = bx - ax
        val dy = by - ay
        val length = hypot(dx, dy)
        if (length == 0.0) return hypot(ax, ay)
        if (!length.isFinite()) return minOf(hypot(ax, ay), hypot(bx, by))
        val ux = dx / length
        val uy = dy / length
        val projection = (-ax * ux - ay * uy).coerceIn(0.0, length)
        return hypot(ax + ux * projection, ay + uy * projection)
    }

    private fun inside(area: AreaAnnotation, point: PhotoPoint): Boolean {
        var inside = false
        var previous = area.points.last()
        for (current in area.points) {
            if ((current.y > point.y) != (previous.y > point.y)) {
                val fraction = (point.y - current.y) / (previous.y - current.y)
                if (point.x < current.x * (1 - fraction) + previous.x * fraction) inside = !inside
            }
            previous = current
        }
        return inside
    }
}
