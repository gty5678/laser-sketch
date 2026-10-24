package com.example.boschlaser.shared.tool

import com.example.boschlaser.shared.editor.SketchEditor
import com.example.boschlaser.shared.geometry.Vec2

/** World-space tolerances are supplied by each platform's viewport adapter. */
data class PointerContext(
    val screenPoint: Vec2,
    val endpointTolerance: Double,
    val wallTolerance: Double,
    val columnTolerance: Double,
    val openingTolerance: Double,
    val snapTolerance: Double,
    val columnSnapTolerance: Double,
    val gridStep: Double? = null,
    val rotationHandleColumnId: String? = null,
    val dragThreshold: Double = 2.0,
    val viewportScale: Double = 1.0,
    val dimensionLabelId: String? = null,
    val faceDimensionLabelId: String? = null,
    val dimensionPreviewLabelHit: Boolean = false,
) {
    init {
        require(listOf(endpointTolerance, wallTolerance, columnTolerance, openingTolerance,
            snapTolerance, columnSnapTolerance, dragThreshold).all { it.isFinite() && it >= 0.0 })
        require(viewportScale.isFinite() && viewportScale > 0.0)
        require(gridStep == null || gridStep.isFinite() && gridStep > 0.0)
    }
}
enum class ToolFailure { INVALID_SPLIT, INVALID_DIMENSION }

data class ToolUpdate(val documentChanged: Boolean = false, val panBy: Vec2 = Vec2(0.0, 0.0),
    val failure: ToolFailure? = null,
    val editFaceDimension: com.example.boschlaser.shared.model.WallDimension? = null)

interface SketchTool {
    fun pointerDown(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate
    fun pointerMove(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate
    fun pointerUp(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate
    fun cancel(editor: SketchEditor)
}
