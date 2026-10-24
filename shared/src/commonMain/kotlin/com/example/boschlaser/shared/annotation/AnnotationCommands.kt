package com.example.boschlaser.shared.annotation

fun interface AnnotationCommand { fun apply(document: AnnotationDocument): AnnotationDocument? }
data class AddAnnotationCommand(val annotation: Annotation) : AnnotationCommand {
    override fun apply(document: AnnotationDocument): AnnotationDocument? =
        if (annotation.id in document.annotations) null else document.copy(annotations = document.annotations + (annotation.id to annotation))
}
data class UpdateAnnotationCommand(val annotation: Annotation) : AnnotationCommand {
    override fun apply(document: AnnotationDocument): AnnotationDocument? {
        val old = document.annotations[annotation.id] ?: return null
        if (old.kind != annotation.kind) return null
        return document.copy(annotations = document.annotations + (annotation.id to annotation))
    }
}
data class DeleteAnnotationCommand(val id: String) : AnnotationCommand {
    override fun apply(document: AnnotationDocument): AnnotationDocument? =
        if (id !in document.annotations) null else document.copy(annotations = document.annotations - id)
}
class ClearAnnotationsCommand : AnnotationCommand {
    override fun apply(document: AnnotationDocument) = document.copy(annotations = emptyMap())
}
data class MoveAnnotationCommand(val id: String, val dx: Double, val dy: Double) : AnnotationCommand {
    override fun apply(document: AnnotationDocument): AnnotationDocument? {
        if (!dx.isFinite() || !dy.isFinite()) return null
        val annotation = document.annotations[id] ?: return null
        fun shift(value: Double, min: Double, max: Double): Double = if (-min <= 1.0 - max) value.coerceIn(-min, 1.0 - max) else 0.0
        val x = shift(dx, annotation.points.minOf { it.x }, annotation.points.maxOf { it.x })
        val y = shift(dy, annotation.points.minOf { it.y }, annotation.points.maxOf { it.y })
        return UpdateAnnotationCommand(annotation.withPoints(annotation.points.map { PhotoPoint(it.x + x, it.y + y) })).apply(document)
    }
}
data class MoveAnnotationPointCommand(val id: String, val index: Int, val point: PhotoPoint) : AnnotationCommand {
    override fun apply(document: AnnotationDocument): AnnotationDocument? {
        val annotation = document.annotations[id] ?: return null
        if (index !in annotation.points.indices) return null
        val points = annotation.points.toMutableList()
        points[index] = PhotoPoint(point.x.coerceIn(0.0, 1.0), point.y.coerceIn(0.0, 1.0))
        return UpdateAnnotationCommand(annotation.withPoints(points)).apply(document)
    }
}
data class InsertAreaMidpointCommand(val id: String, val edgeIndex: Int) : AnnotationCommand {
    override fun apply(document: AnnotationDocument): AnnotationDocument? {
        val area = document.annotations[id] as? AreaAnnotation ?: return null
        if (edgeIndex !in area.points.indices) return null
        val first = area.points[edgeIndex]
        val second = area.points[(edgeIndex + 1) % area.points.size]
        val points = area.points.toMutableList().apply { add(edgeIndex + 1, PhotoPoint(midpointCoordinate(first.x, second.x), midpointCoordinate(first.y, second.y))) }
        return UpdateAnnotationCommand(area.copy(points = points)).apply(document)
    }
}
data class DeleteAreaPointCommand(val id: String, val pointIndex: Int) : AnnotationCommand {
    override fun apply(document: AnnotationDocument): AnnotationDocument? {
        val area = document.annotations[id] as? AreaAnnotation ?: return null
        if (pointIndex !in area.points.indices || area.points.size <= 3) return null
        return UpdateAnnotationCommand(area.copy(points = area.points.filterIndexed { index, _ -> index != pointIndex })).apply(document)
    }
}
internal fun Annotation.withPoints(points: List<PhotoPoint>): Annotation = when (this) {
    is DimensionAnnotation -> copy(start = points[0], end = points[1])
    is TextAnnotation -> copy(position = points[0])
    is AngleAnnotation -> copy(vertex = points[0], first = points[1], second = points[2])
    is AreaAnnotation -> copy(points = points.toList())
}

private fun midpointCoordinate(first: Double, second: Double): Double {
    val sum = first + second
    return if (sum.isFinite()) sum / 2.0 else first / 2.0 + second / 2.0
}
