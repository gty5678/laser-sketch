package com.example.boschlaser.platform

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import com.example.boschlaser.R
import com.example.boschlaser.formatSketchMillimeters
import com.example.boschlaser.ui.*

/** Android resources supply appearance; shared DrawScope owns all drawing and geometry. */
internal fun androidSketchDrawingStyle(context: Context, density: Density, rotationHandleRadiusPx: Float): SketchSceneStyle {
    fun color(id: Int) = Color(context.getColor(id))
    fun dimension(id: Int) = context.resources.getDimension(id)
    val selected = color(R.color.sketch_tool_active)
    val selectedWidth = dimension(R.dimen.sketch_selected_stroke)
    val wall = WallDrawingStyle(color(R.color.sketch_wall_fill), color(R.color.sketch_wall_outline),
        dimension(R.dimen.sketch_wall_stroke))
    val opening = OpeningDrawingStyle(color(R.color.sketch_opening_symbol), dimension(R.dimen.sketch_symbol_stroke),
        wall.outline, wall.outlineWidthPx)
    val controls = ControlDrawingStyle(selected, color(R.color.white))
    return SketchSceneStyle(
        grid = GridDrawingStyle(color(R.color.sketch_grid), dimension(R.dimen.sketch_grid_stroke),
            color(R.color.sketch_major_grid), dimension(R.dimen.sketch_major_grid_stroke)),
        wall = wall,
        selectedWall = wall.copy(outline = selected, outlineWidthPx = selectedWidth),
        opening = opening,
        selectedOpening = opening.copy(symbolColor = selected, symbolWidthPx = selectedWidth),
        column = ColumnDrawingStyle(color(R.color.sketch_column_fill), selected, selectedWidth,
            selected, color(R.color.white), rotationHandleRadiusPx),
        controls = WallControlDrawingStyle(selected, wall.outlineWidthPx,
            PathEffect.dashPathEffect(floatArrayOf(dimension(R.dimen.sketch_control_dash),
                dimension(R.dimen.sketch_control_gap))), controls),
        selectedControls = WallControlDrawingStyle(selected, selectedWidth, null, controls),
        label = TextStyle(color = color(R.color.sketch_dimension_text),
            fontSize = with(density) { dimension(R.dimen.sketch_dimension_text_size).toSp() },
            fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold),
        previewCross = selected,
        previewCrossWidthPx = selectedWidth,
        measurementLabelOffsetPx = dimension(R.dimen.sketch_measurement_label_offset).toDouble(),
        invalidDimensionLabel = context.getString(R.string.sketch_dimension_unavailable),
        dimension = DimensionDrawingStyle(dimension(R.dimen.sketch_dimension_line_width), dimension(R.dimen.sketch_dimension_active_width),
            dimension(R.dimen.sketch_dimension_tick_width), dimension(R.dimen.sketch_dimension_active_tick_width),
            dimension(R.dimen.sketch_dimension_tick_length), dimension(R.dimen.sketch_dimension_extension_gap),
            dimension(R.dimen.sketch_dimension_extension_overrun), dimension(R.dimen.sketch_dimension_text_gap)),
        formatDimensionValue = ::formatSketchMillimeters,
    )
}
