package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.geometry.Segment2
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.Column as SketchColumn
import com.example.boschlaser.shared.model.WallAlignment

enum class ToolType { SELECT, WALL, RECT_COLUMN, CIRCLE_COLUMN, DOOR, WINDOW, MEASURE, SPLIT, DIMENSION }
sealed interface Selection {
    val id: String
    data class Wall(override val id: String) : Selection
    data class Column(override val id: String) : Selection
    data class Opening(override val id: String) : Selection
    data class Dimension(override val id: String) : Selection
}
sealed interface SketchPreview {
    data class Measurement(val line: Segment2, val complete: Boolean = false) : SketchPreview
    data class Wall(val line: Segment2) : SketchPreview
    data class Column(val column: SketchColumn) : SketchPreview
    data class Dimension(val dimension: com.example.boschlaser.shared.model.WallDimension) : SketchPreview
}
sealed interface SelectionGestureTarget {
    data object Pan : SelectionGestureTarget
    data class WallEndpoint(val id: String, val start: Boolean) : SelectionGestureTarget
    data class WallBody(val id: String) : SelectionGestureTarget
    data class Column(val id: String) : SelectionGestureTarget
    data class ColumnRotation(val id: String) : SelectionGestureTarget
    data class Opening(val id: String) : SelectionGestureTarget
    data class Dimension(val id: String) : SelectionGestureTarget
    data class FaceDimension(val dimension: com.example.boschlaser.shared.model.WallDimension) : SelectionGestureTarget
}

/** Stable target and last pointer positions belong to the editor, never a platform View. */
data class SelectionGesture(
    val target: SelectionGestureTarget,
    val lastWorld: Vec2,
    val lastScreen: Vec2,
)

data class DimensionGesture(val startScreen: Vec2, val lastScreen: Vec2, val startWorld: Vec2, val previewBefore: SketchPreview.Dimension?,
    val movingDimension: Boolean = false, val dimensionMoved: Boolean = false)

data class EditorState(
    val tool: ToolType = ToolType.SELECT,
    val selection: Selection? = null,
    val preview: SketchPreview? = null,
    val snap: SnapResult? = null,
    val wallAnchor: Vec2? = null,
    val magnifierTarget: Vec2? = null,
    val dragging: Boolean = false,
    val selectionGesture: SelectionGesture? = null,
    val wallEditSession: WallEditSession? = null,
    val dimensionFirst: com.example.boschlaser.shared.model.DimensionReference? = null,
    val dimensionGesture: DimensionGesture? = null,
)
data class PlacementSettings(
    val wallThickness: Double = 240.0,
    val alignment: WallAlignment = WallAlignment.CENTER,
    val doorWidth: Double = 900.0,
    val windowWidth: Double = 1200.0,
) {
    init {
        require(wallThickness.isFinite() && wallThickness in 50.0..1000.0)
        require(doorWidth.isFinite() && doorWidth in 100.0..5000.0)
        require(windowWidth.isFinite() && windowWidth in 100.0..5000.0)
    }
}
