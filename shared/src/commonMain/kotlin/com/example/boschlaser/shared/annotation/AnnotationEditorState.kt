package com.example.boschlaser.shared.annotation

/** Stable entity identity; list indices are derived only by platform compatibility adapters. */
data class AnnotationSelectionTarget(val annotationId: String, val pointIndex: Int? = null)

data class AnnotationEditorState(
    val mode: AnnotationMode = AnnotationMode.DIMENSION,
    val selection: AnnotationSelectionTarget? = null,
    val creation: AnnotationCreationState = AnnotationCreationState(),
    val drag: AnnotationDrag? = null,
    val areaEdgeTap: AreaEdgeTap? = null,
)

/** Creation previews are normalized photo geometry, never temporary document entities. */
data class DimensionPlacement(val start: PhotoPoint, val end: PhotoPoint)

data class AnnotationCreationState(
    val dimension: DimensionPlacement? = null,
    val anglePoints: List<PhotoPoint> = emptyList(),
    val areaPoints: List<PhotoPoint> = emptyList(),
)

sealed interface AnnotationPlacementResult {
    data class Pending(val pointCount: Int) : AnnotationPlacementResult
    data class Created(val annotationId: String) : AnnotationPlacementResult
    data object Rejected : AnnotationPlacementResult
}

/** Gesture identity stays valid when a platform projection changes its list indices. */
data class AnnotationDrag(
    val target: AnnotationSelectionTarget,
    val kind: AnnotationKind,
    val start: PhotoPoint,
    val started: Boolean = false,
)

/** Remember the actual edge so deletion, replacement, or vertex edits cannot retarget a second tap. */
data class AreaEdgeTap(
    val areaId: String,
    val edgeIndex: Int,
    val atMillis: Long,
    val start: PhotoPoint,
    val end: PhotoPoint,
)

enum class AreaEdgeTapResult { PENDING, INSERTED, REJECTED }
