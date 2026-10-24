package com.example.boschlaser.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.boschlaser.shared.editor.Viewport
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.CircularColumn
import com.example.boschlaser.shared.model.Column
import com.example.boschlaser.shared.model.RectColumn
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Screen layout is shared by rendering and the platform rotation-handle hit adapter. */
data class ColumnDrawingLayout(val center: Vec2, val halfWidth: Double, val halfDepth: Double,
    val rotationDegrees: Double, val stemStart: Vec2?, val rotationHandle: Vec2?)

fun columnDrawingLayout(column: Column, viewport: Viewport, handleOffsetPx: Double): ColumnDrawingLayout {
    require(handleOffsetPx.isFinite() && handleOffsetPx >= 0.0)
    val center = viewport.worldToScreen(column.center)
    return when (column) {
        is CircularColumn -> {
            val radius = max(5.0, column.diameter * viewport.scale / 2.0)
            ColumnDrawingLayout(center, radius, radius, 0.0, null, null)
        }
        is RectColumn -> {
            val halfWidth = max(5.0, column.width * viewport.scale / 2.0)
            val halfDepth = max(5.0, column.depth * viewport.scale / 2.0)
            val radians = column.rotationDegrees * PI / 180.0
            val direction = Vec2(sin(radians), -cos(radians))
            ColumnDrawingLayout(center, halfWidth, halfDepth, column.rotationDegrees,
                center + direction * halfDepth, center + direction * (halfDepth + handleOffsetPx))
        }
    }
}

data class ColumnDrawingStyle(val fill: Color, val selection: Color, val selectionWidthPx: Float,
    val control: Color, val halo: Color, val handleRadiusPx: Float)

/** Drawing never changes the column or performs selection; controls are supplied by the host. */
fun DrawScope.drawSketchColumn(column: Column, viewport: Viewport, selected: Boolean, showRotation: Boolean,
    showCenter: Boolean, handleOffsetPx: Double, style: ColumnDrawingStyle) {
    val layout = columnDrawingLayout(column, viewport, handleOffsetPx)
    fun Vec2.offset() = Offset(x.toFloat(), y.toFloat())
    val center = layout.center.offset()
    val halfWidth = layout.halfWidth.toFloat()
    val halfDepth = layout.halfDepth.toFloat()
    if (column is CircularColumn) {
        drawCircle(style.fill, halfWidth, center)
        if (selected) drawCircle(style.selection, halfWidth + 4f, center, style = Stroke(style.selectionWidthPx))
    } else {
        rotate(layout.rotationDegrees.toFloat(), center) {
            drawRect(style.fill, center - Offset(halfWidth, halfDepth), Size(halfWidth * 2, halfDepth * 2))
            if (selected) drawRect(style.selection, center - Offset(halfWidth + 4f, halfDepth + 4f),
                Size((halfWidth + 4f) * 2, (halfDepth + 4f) * 2), style = Stroke(style.selectionWidthPx))
        }
        if (selected && showRotation) {
            val handle = layout.rotationHandle!!.offset()
            drawLine(style.selection, layout.stemStart!!.offset(), handle, strokeWidth = style.selectionWidthPx)
            drawCircle(style.halo, style.handleRadiusPx + 3f, handle)
            drawCircle(style.control, style.handleRadiusPx, handle)
        }
    }
    if (selected && showCenter) {
        drawCircle(style.halo, 8f, center)
        drawCircle(style.control, 5f, center)
    }
}
