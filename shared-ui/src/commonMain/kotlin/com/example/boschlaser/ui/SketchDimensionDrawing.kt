package com.example.boschlaser.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import com.example.boschlaser.shared.editor.Viewport
import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*
import kotlin.math.*

/** Screen-space appearance, supplied by Android resources in the host. */
data class DimensionDrawingStyle(val lineWidthPx: Float = 1f, val activeWidthPx: Float = 2f,
    val tickWidthPx: Float = 2f, val activeTickWidthPx: Float = 3f, val tickLengthPx: Float = 8f,
    val extensionGapPx: Float = 2f, val extensionOverrunPx: Float = 6f, val textGapPx: Float = 2f)

data class DimensionTextPlacement(val center: Vec2, val rotationDegrees: Float, val bounds: Rect2)

/** Horizontal text above the line; vertical text on its left, heading left. */
fun dimensionTextPlacement(line: Segment2, textSize: Vec2, gap: Double, padding: Vec2): DimensionTextPlacement {
    var angle = atan2(line.end.y - line.start.y, line.end.x - line.start.x)
    if (angle >= PI / 2.0) angle -= PI
    if (angle < -PI / 2.0) angle += PI
    val direction = Vec2(cos(angle), sin(angle))
    val anchor = if (Geometry.length(line) < textSize.x + gap * 2.0) {
        val outer = if (line.end.dot(direction) >= line.start.dot(direction)) line.end else line.start
        outer + direction * (textSize.x / 2.0 + gap)
    } else (line.start + line.end) * .5
    val center = anchor + Vec2(direction.y, -direction.x) * (textSize.y / 2.0 + gap)
    val half = textSize * .5 + padding
    val extent = Vec2(abs(direction.x) * half.x + abs(direction.y) * half.y,
        abs(direction.y) * half.x + abs(direction.x) * half.y)
    return DimensionTextPlacement(center, (angle * 180.0 / PI).toFloat(), Rect2(center - extent, center + extent))
}

fun dimensionLabelStyle(style: TextStyle, editable: Boolean): TextStyle =
    style.copy(fontWeight = if (editable) FontWeight.Bold else FontWeight.Normal)

/** The rotated text rectangle and small transparent touch margins match drawing. */
fun sketchDimensionLabelBounds(document: SketchDocument, dimension: WallDimension, viewport: Viewport,
    measurer: TextMeasurer, labelStyle: TextStyle, density: Density, editable: Boolean = false,
    textGapPx: Float = 2 * density.density, format: (Double) -> String): Rect2? {
    val layout = WallDimensionGeometry.layout(document, dimension) ?: return null
    val measured = measurer.measure(format(layout.valueMm), dimensionLabelStyle(labelStyle, editable),
        softWrap = false, maxLines = 1, density = density)
    return dimensionTextPlacement(Segment2(viewport.worldToScreen(layout.line.start), viewport.worldToScreen(layout.line.end)),
        Vec2(measured.size.width.toDouble(), measured.size.height.toDouble()), textGapPx.toDouble(),
        Vec2(4.0 * density.density, 4.0 * density.density)).bounds
}

/** Thin continuous dimensions, medium 45-degree ticks and transparent text above the line. */
fun DrawScope.drawSketchDimension(document: SketchDocument, dimension: WallDimension, viewport: Viewport,
    style: SketchSceneStyle, selected: Boolean, measurer: TextMeasurer, format: (Double) -> String, editable: Boolean = false) {
    val layout = WallDimensionGeometry.layout(document, dimension)
    if (layout == null) {
        drawSketchWallLabel(WallLengthLabelLayout(viewport.worldToScreen(dimension.position), 0.0),
            style.invalidDimensionLabel, measurer, dimensionLabelStyle(style.label, false))
        return
    }
    val start = viewport.worldToScreen(layout.line.start)
    val end = viewport.worldToScreen(layout.line.end)
    if (Geometry.distance(start, end) < 1.0) return
    val appearance = style.dimension
    val direction = Geometry.normalize(end - start)
    val normal = Vec2(-direction.y, direction.x)
    val color = if (selected || editable) style.selectedControls.line else style.label.color
    val width = if (editable) appearance.activeWidthPx else appearance.lineWidthPx
    fun line(a: Vec2, b: Vec2, stroke: Float = width) = drawLine(color,
        Offset(a.x.toFloat(), a.y.toFloat()), Offset(b.x.toFloat(), b.y.toFloat()), stroke)
    val tick = (direction - normal) * (appearance.tickLengthPx / (2.0 * sqrt(2.0)))
    val tickWidth = if (editable) appearance.activeTickWidthPx else appearance.tickWidthPx
    line(start - tick, start + tick, tickWidth)
    line(end - tick, end + tick, tickWidth)
    fun witness(world: Vec2, target: Vec2) {
        val source = viewport.worldToScreen(world)
        if (Geometry.distance(source, target) <= appearance.extensionGapPx) return
        val unit = Geometry.normalize(target - source)
        line(source + unit * appearance.extensionGapPx.toDouble(), target + unit * appearance.extensionOverrunPx.toDouble())
    }
    witness(layout.firstWitness, start)
    witness(layout.secondWitness, end)
    line(start, end)
    val label = dimensionLabelStyle(style.label, editable).copy(color = color)
    val measured = measurer.measure(format(layout.valueMm), label, softWrap = false, maxLines = 1, density = this)
    val text = dimensionTextPlacement(Segment2(start, end), Vec2(measured.size.width.toDouble(), measured.size.height.toDouble()),
        appearance.textGapPx.toDouble(), Vec2(0.0, 0.0))
    val center = Offset(text.center.x.toFloat(), text.center.y.toFloat())
    withTransform({ rotate(text.rotationDegrees, center) }) {
        drawText(measured, topLeft = center - Offset(measured.size.width / 2f, measured.size.height / 2f))
    }
}
