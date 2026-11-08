package com.example.boschlaser.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import com.example.boschlaser.shared.editor.EditorState
import com.example.boschlaser.shared.editor.PlacementSettings
import com.example.boschlaser.shared.editor.Selection
import com.example.boschlaser.shared.editor.SketchPreview
import com.example.boschlaser.shared.editor.ToolType
import com.example.boschlaser.shared.editor.Viewport
import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.WallDimensionGeometry
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall

data class SketchSceneStyle(
    val grid: GridDrawingStyle,
    val wall: WallDrawingStyle,
    val selectedWall: WallDrawingStyle,
    val opening: OpeningDrawingStyle,
    val selectedOpening: OpeningDrawingStyle,
    val column: ColumnDrawingStyle,
    val controls: WallControlDrawingStyle,
    val selectedControls: WallControlDrawingStyle,
    val label: TextStyle,
    val previewCross: Color,
    val previewCrossWidthPx: Float,
    val measurementLabelOffsetPx: Double,
    val invalidDimensionLabel: String = "",
    val dimension: DimensionDrawingStyle = DimensionDrawingStyle(),
    val formatDimensionValue: ((Double) -> String)? = null,
    val fixedReferenceLabel: String = "",
    val movingReferenceLabel: String = "",
)

data class SketchSceneOptions(
    val gridStepMm: Double,
    val showGrid: Boolean = true,
    val showSelection: Boolean = true,
    val showColumnRotation: Boolean = true,
    val showColumnCenter: Boolean = false,
    val rotationHandleOffsetPx: Double,
    val showAutomaticDimensions: Boolean = true,
)

/** One rendering order for the screen, magnifier and export. All products are disposable. */
fun DrawScope.drawSketchScene(document: SketchDocument, state: EditorState, placement: PlacementSettings,
    viewport: Viewport, sizePx: Vec2, style: SketchSceneStyle, options: SketchSceneOptions,
    measurer: TextMeasurer, formatWallLength: (Double) -> String) {
    drawSketchScene(document, state, placement, viewport, sizePx, style, options, measurer, formatWallLength, null)
}

internal fun DrawScope.drawSketchScene(document: SketchDocument, state: EditorState, placement: PlacementSettings,
    viewport: Viewport, sizePx: Vec2, style: SketchSceneStyle, options: SketchSceneOptions,
    measurer: TextMeasurer, formatWallLength: (Double) -> String, geometryCache: SketchWallGeometryCache?) {
    val selection = state.selection.takeIf { options.showSelection }
    if (options.showGrid) drawSketchGrid(viewport, sizePx, options.gridStepMm, style.grid)
    drawSketchWallNetwork(document, viewport, style.wall, geometryCache)
    (selection as? Selection.Wall)?.let { document.walls[it.id] }?.let {
        drawSketchWallControls(it, viewport, true, style.selectedControls)
        val selectedLine = com.example.boschlaser.shared.geometry.WallGeometry.referenceLine(it)
        document.walls.values.filter { other -> other.id != it.id && (
            Geometry.distanceToSegment(other.start, selectedLine) < .1 ||
            Geometry.distanceToSegment(other.end, selectedLine) < .1 ||
            Geometry.distanceToSegment(it.start, com.example.boschlaser.shared.geometry.WallGeometry.referenceLine(other)) < .1 ||
            Geometry.distanceToSegment(it.end, com.example.boschlaser.shared.geometry.WallGeometry.referenceLine(other)) < .1)
        }.forEach { connected -> drawSketchWallControls(connected, viewport, false, style.controls) }
    }
    document.columns.values.forEach {
        drawSketchColumn(it, viewport, selection == Selection.Column(it.id),
            options.showColumnRotation && state.tool == ToolType.SELECT, options.showColumnCenter,
            options.rotationHandleOffsetPx, style.column)
    }
    document.openings.values.forEach { opening ->
        document.walls[opening.hostWallId]?.let { host ->
            drawSketchOpening(opening, host, viewport,
                if (selection == Selection.Opening(opening.id)) style.selectedOpening else style.opening)
        }
    }
    document.dimensions.values.forEach {
        val editable = state.tool == ToolType.SELECT && state.wallEditSession == null && WallDimensionGeometry.forMovingSelection(it, selection) != null &&
            WallDimensionGeometry.layout(document, it) != null
        drawSketchDimension(document, it, viewport, style, selection == Selection.Dimension(it.id), measurer,
            style.formatDimensionValue ?: formatWallLength, editable)
    }
    if (!options.showGrid) return
    state.wallEditRemovedParts.forEach { removed ->
        val start = viewport.worldToScreen(removed.start)
        val end = viewport.worldToScreen(removed.end)
        drawLine(style.selectedControls.line, Offset(start.x.toFloat(), start.y.toFloat()),
            Offset(end.x.toFloat(), end.y.toFloat()), style.selectedControls.widthPx,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
    }
    if (state.tool == ToolType.DIMENSION) {
        // Expose otherwise invisible center axes only while choosing references.
        WallDimensionGeometry.references(document).forEach { reference ->
            WallDimensionGeometry.face(document, reference)?.let { line ->
                val start = viewport.worldToScreen(line.start)
                val end = viewport.worldToScreen(line.end)
                drawLine(style.selectedControls.line.copy(alpha = .35f),
                    Offset(start.x.toFloat(), start.y.toFloat()), Offset(end.x.toFloat(), end.y.toFloat()),
                    style.controls.widthPx, pathEffect = style.selectedControls.pathEffect)
            }
        }
    }
    if (options.showAutomaticDimensions && selection is Selection.Wall && state.tool == ToolType.SELECT) {
        WallDimensionGeometry.forSelectedWall(document, selection.id).forEach {
            drawSketchDimension(document, it, viewport, style, true, measurer, style.formatDimensionValue ?: formatWallLength, editable = true)
        }
    }
    val dimensionReferences = (selection as? Selection.Dimension)?.let { document.dimensions[it.id] }
        ?.let { listOf(it.first, it.second) }
        ?: (state.preview as? SketchPreview.Dimension)?.dimension?.let { listOf(it.first, it.second) }
        ?: listOfNotNull(state.dimensionFirst)
    dimensionReferences.forEachIndexed { index, reference ->
        WallDimensionGeometry.face(document, reference)?.let { edge ->
            val start = viewport.worldToScreen(edge.start)
            val end = viewport.worldToScreen(edge.end)
            drawLine(style.selectedControls.line, Offset(start.x.toFloat(), start.y.toFloat()),
                Offset(end.x.toFloat(), end.y.toFloat()), style.selectedControls.widthPx * 2)
            drawSketchWallLabel(WallLengthLabelLayout((start + end) * .5, 0.0),
                if (index == 0) style.fixedReferenceLabel else style.movingReferenceLabel, measurer, style.label)
        }
    }
    if (state.tool == ToolType.WALL || selection is Selection.Wall) {
        document.walls.values.forEach { drawSketchWallControls(it, viewport, false, style.controls) }
    }
    when (val preview = state.preview) {
        is SketchPreview.Dimension -> drawSketchDimension(document, preview.dimension, viewport, style, true, measurer,
            style.formatDimensionValue ?: formatWallLength)
        is SketchPreview.Measurement -> {
            val start = viewport.worldToScreen(preview.line.start)
            val end = viewport.worldToScreen(preview.line.end)
            drawLine(style.selectedControls.line, Offset(start.x.toFloat(), start.y.toFloat()),
                Offset(end.x.toFloat(), end.y.toFloat()), style.selectedControls.widthPx,
                pathEffect = style.selectedControls.pathEffect)
            drawSketchControlPoint(preview.line.start, viewport, style.selectedControls.point)
            drawSketchControlPoint(preview.line.end, viewport, style.selectedControls.point)
            drawSketchWallLabel(WallLengthLabelLayout((start + end) * .5 + Vec2(0.0, -style.measurementLabelOffsetPx),
                Geometry.length(preview.line)), formatWallLength(Geometry.length(preview.line)), measurer, style.label)
        }
        is SketchPreview.Wall -> {
            if (Geometry.length(preview.line) < 20.0) {
                drawSketchWallStart(preview.line.start, viewport, style.controls.point, style.previewCross, style.previewCrossWidthPx)
            } else {
                val wall = Wall("preview", preview.line.start, preview.line.end, placement.wallThickness, placement.alignment, fill = placement.wallFill)
                drawSketchWallBody(wall, document.walls.values, viewport, style.selectedWall)
                drawSketchWallControls(wall, viewport, true, style.selectedControls)
            }
        }
        is SketchPreview.Column -> if (state.tool == ToolType.RECT_COLUMN || state.tool == ToolType.CIRCLE_COLUMN) {
            drawSketchColumn(preview.column, viewport, true, false, options.showColumnCenter,
                options.rotationHandleOffsetPx, style.column)
            drawSketchControlPoint(preview.column.center, viewport, style.controls.point)
        }
        null -> Unit
    }
    if (state.tool == ToolType.DIMENSION || (state.tool == ToolType.MEASURE && state.preview == null)) {
        state.snap?.let { drawSketchControlPoint(it.point, viewport, style.selectedControls.point) }
    }
}
