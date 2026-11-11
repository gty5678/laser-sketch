package com.example.boschlaser.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import com.example.boschlaser.shared.editor.Viewport
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.BoardFill
import com.example.boschlaser.shared.model.BoardShape
import androidx.compose.ui.geometry.Rect
import kotlin.math.floor

internal fun DrawScope.drawSketchBoard(vertices: List<Vec2>, fill: BoardFill, viewport: Viewport, outline: Color, selected: Boolean, shape: BoardShape = BoardShape.POLYGON, selectedVertex: Int? = null, showEdgeControls: Boolean = false, showControls: Boolean = true) {
    if (vertices.isEmpty()) return
    val points = vertices.map { viewport.worldToScreen(it) }
    val circular = shape == BoardShape.CIRCLE && points.size >= 3
    val bounds = Rect(points.minOf { it.x }.toFloat(), points.minOf { it.y }.toFloat(),
        points.maxOf { it.x }.toFloat(), points.maxOf { it.y }.toFloat())
    val path = Path().apply {
        if (circular) addOval(bounds) else {
            moveTo(points.first().x.toFloat(), points.first().y.toFloat())
            points.drop(1).forEach { lineTo(it.x.toFloat(),it.y.toFloat()) }
            if (points.size >= 3) close()
        }
    }
    if (points.size >= 3) {
        drawPath(path, Color.White)
        if (fill != BoardFill.WHITE) clipPath(path) {
            val spacing = (if (fill == BoardFill.GRID) 200.0 else 150.0) * viewport.scale
            // Bound pattern density at distant zoom levels.
            val step = spacing.coerceAtLeast(8.0)
            val origin = viewport.worldToScreen(Vec2(0.0,0.0))
            drawBoardPattern(fill, step, Offset(origin.x.toFloat(),origin.y.toFloat()), Color.Gray)
        }
    }
    drawPath(path, outline, style = Stroke(if (selected) 3f else 1f))
    if (selected && showControls) drawSketchBoardControls(vertices,viewport,outline,shape,selectedVertex,showEdgeControls)
}

internal fun DrawScope.drawSketchBoardControls(vertices: List<Vec2>, viewport: Viewport, outline: Color,
    shape: BoardShape, selectedVertex: Int?, showEdgeControls: Boolean) {
    if (vertices.isEmpty()) return
    val points = vertices.map { viewport.worldToScreen(it) }
    val circular = shape == BoardShape.CIRCLE && points.size >= 3
    val bounds = Rect(points.minOf { it.x }.toFloat(),points.minOf { it.y }.toFloat(),
        points.maxOf { it.x }.toFloat(),points.maxOf { it.y }.toFloat())
    val controls = if (circular) listOf(bounds.centerLeft, bounds.topCenter, bounds.centerRight, bounds.bottomCenter)
        else points.map { Offset(it.x.toFloat(),it.y.toFloat()) }
    controls.forEachIndexed { i, point ->
        drawCircle(if (i == selectedVertex) outline else Color.White,5f,point)
        drawCircle(outline,5f,point, style = Stroke(2f))
    }
    if (showEdgeControls && shape == BoardShape.POLYGON) points.indices.forEach { i ->
        val midpoint = (points[i] + points[(i+1)%points.size]) * .5
        val point = Offset(midpoint.x.toFloat(),midpoint.y.toFloat())
        drawCircle(Color.White,6f,point)
        drawCircle(outline,6f,point, style = Stroke(1f))
        drawLine(outline,point - Offset(3f,0f),point + Offset(3f,0f),1f)
        drawLine(outline,point - Offset(0f,3f),point + Offset(0f,3f),1f)
    }

}

/** Shared monochrome material samples and canvas pattern. */
internal fun DrawScope.drawBoardPattern(fill: BoardFill, step: Double, origin: Offset, color: Color) {
    var y = origin.y - floor(origin.y / step) * step - step
    var row = -floor(origin.y / step).toInt() - 1
    while (y < size.height) {
        drawLine(color, Offset(0f,y.toFloat()), Offset(size.width,y.toFloat()), 1f)
        val width = if (fill == BoardFill.WOOD) step * 5 else step
        val offset = if (fill == BoardFill.WOOD && row % 2 != 0) width / 2 else 0.0
        var x = origin.x + offset - floor((origin.x + offset) / width) * width
        while (x < size.width) {
            drawLine(color, Offset(x.toFloat(),y.toFloat()), Offset(x.toFloat(),(y+step).toFloat()),1f)
            x += width
        }
        y += step; row++
    }
}
