package com.example.boschlaser.shared.annotation

/** Coordinates are fractions of the source photo, independent of display size and millimetres. */
data class PhotoPoint(val x: Double, val y: Double) {
    init { require(x.isFinite() && y.isFinite()) }
}
enum class AnnotationKind { DIMENSION, TEXT, ANGLE, AREA }
sealed interface Annotation {
    val id: String
    val kind: AnnotationKind
    val colorArgb: Int
    val points: List<PhotoPoint>
}
data class DimensionAnnotation(override val id: String, val start: PhotoPoint, val end: PhotoPoint,
    val label: String, override val colorArgb: Int, val thicknessPreset: Int = 1) : Annotation {
    init { require(id.isNotBlank()) }
    override val kind get() = AnnotationKind.DIMENSION
    override val points get() = listOf(start, end)
}
data class TextAnnotation(override val id: String, val position: PhotoPoint, val text: String,
    override val colorArgb: Int, val sizePreset: Int = 1, val whiteBackground: Boolean = true) : Annotation {
    init { require(id.isNotBlank()) }
    override val kind get() = AnnotationKind.TEXT
    override val points get() = listOf(position)
}
data class AngleAnnotation(override val id: String, val vertex: PhotoPoint, val first: PhotoPoint,
    val second: PhotoPoint, val label: String, override val colorArgb: Int) : Annotation {
    init { require(id.isNotBlank()) }
    override val kind get() = AnnotationKind.ANGLE
    override val points get() = listOf(vertex, first, second)
}
data class AreaAnnotation(override val id: String, override val points: List<PhotoPoint>,
    val label: String, override val colorArgb: Int) : Annotation {
    init { require(id.isNotBlank() && points.size >= 3) }
    override val kind get() = AnnotationKind.AREA
}
data class AnnotationDocument(val annotations: Map<String, Annotation> = emptyMap(), val photoAssetId: String? = null) {
    init {
        require(annotations.all { (id, annotation) -> id == annotation.id })
        require(photoAssetId == null || photoAssetId.isNotBlank())
    }
}
