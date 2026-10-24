package com.example.boschlaser.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.boschlaser.shared.editor.Viewport
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.Wall

data class ControlDrawingStyle(val control: Color, val halo: Color)
data class WallControlDrawingStyle(val line: Color, val widthPx: Float,
    val pathEffect: PathEffect?, val point: ControlDrawingStyle)

/** Coordinates are the semantic wall reference line, including non-central alignments. */
fun DrawScope.drawSketchWallControls(wall: Wall, viewport: Viewport, selected: Boolean, style: WallControlDrawingStyle) {
    val start = viewport.worldToScreen(wall.start)
    val end = viewport.worldToScreen(wall.end)
    drawLine(style.line, start.screenOffset(), end.screenOffset(), strokeWidth = style.widthPx, pathEffect = style.pathEffect)
    if (selected) {
        drawCircle(style.line, 9f, start.screenOffset(), style = Stroke(style.widthPx))
        drawCircle(style.line, 9f, end.screenOffset(), style = Stroke(style.widthPx))
    } else {
        drawSketchControlPoint(wall.start, viewport, style.point)
        drawSketchControlPoint(wall.end, viewport, style.point)
    }
}

fun DrawScope.drawSketchControlPoint(point: Vec2, viewport: Viewport, style: ControlDrawingStyle) {
    val center = viewport.worldToScreen(point).screenOffset()
    drawCircle(style.halo, 8f, center)
    drawCircle(style.control, 5f, center)
}

fun DrawScope.drawSketchWallStart(point: Vec2, viewport: Viewport, style: ControlDrawingStyle,
    crossColor: Color, crossWidthPx: Float) {
    val center = viewport.worldToScreen(point).screenOffset()
    drawCircle(style.halo, 10f, center)
    drawCircle(style.control, 7f, center)
    drawLine(crossColor, center - Offset(14f, 0f), center + Offset(14f, 0f), crossWidthPx)
    drawLine(crossColor, center - Offset(0f, 14f), center + Offset(0f, 14f), crossWidthPx)
}

private fun Vec2.screenOffset() = Offset(x.toFloat(), y.toFloat())
