package com.example.boschlaser.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.boschlaser.shared.annotation.AreaAnnotation
import com.example.boschlaser.shared.annotation.DimensionAnnotation
import com.example.boschlaser.shared.annotation.PhotoPoint
import com.example.boschlaser.shared.geometry.Segment2
import com.example.boschlaser.shared.geometry.Vec2
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class PhotoDrawingViewport(val left: Double, val top: Double, val width: Double, val height: Double) {
    init { require(listOf(left, top, width, height).all { it.isFinite() } && width >= 0.0 && height >= 0.0) }
    fun screen(point: PhotoPoint) = Vec2(left + point.x * width, top + point.y * height)
    /** Raw coordinates support whole-object drags beyond the photo; creation clamps explicitly. */
    fun photo(point: Vec2, clampToPhoto: Boolean = false): PhotoPoint {
        require(width > 0.0 && height > 0.0)
        val x = (point.x - left) / width
        val y = (point.y - top) / height
        return if (clampToPhoto) PhotoPoint(x.coerceIn(0.0, 1.0), y.coerceIn(0.0, 1.0)) else PhotoPoint(x, y)
    }
}

data class DimensionDrawingLayout(val start: Vec2, val end: Vec2, val angleRadians: Double,
    val segments: List<Segment2>, val lineWidthPx: Double)

fun dimensionDrawingLayout(mark: DimensionAnnotation, viewport: PhotoDrawingViewport, scale: Double): DimensionDrawingLayout {
    require(scale.isFinite() && scale > 0.0)
    val start = viewport.screen(mark.start)
    val end = viewport.screen(mark.end)
    val angle = atan2(end.y - start.y, end.x - start.x)
    val endOffset = Vec2(cos(angle + PI / 2), sin(angle + PI / 2)) * max(21.0, 16.0 * scale)
    val tickOffset = Vec2(cos(angle - PI / 4), sin(angle - PI / 4)) * max(12.0, 9.0 * scale)
    val width = when (mark.thicknessPreset) {
        0 -> max(2.0, 1.4 * scale)
        2 -> max(5.0, 4.0 * scale)
        else -> max(3.0, 2.4 * scale)
    }
    return DimensionDrawingLayout(start, end, angle, listOf(Segment2(start, end),
        Segment2(start - endOffset, start + endOffset), Segment2(end - endOffset, end + endOffset),
        Segment2(start - tickOffset, start + tickOffset), Segment2(end - tickOffset, end + tickOffset)), width)
}

fun DrawScope.drawAnnotationDimension(layout: DimensionDrawingLayout, color: Color) {
    layout.segments.forEach { drawLine(color, it.start.offset(), it.end.offset(),
        strokeWidth = layout.lineWidthPx.toFloat(), cap = StrokeCap.Round) }
}

data class AreaDrawingLayout(val points: List<Vec2>, val labelBaselineCenter: Vec2)

fun areaDrawingLayout(mark: AreaAnnotation, viewport: PhotoDrawingViewport): AreaDrawingLayout {
    val points = mark.points.map(viewport::screen)
    return AreaDrawingLayout(points, Vec2(points.sumOf { it.x } / points.size, points.sumOf { it.y } / points.size))
}

fun DrawScope.drawAnnotationArea(layout: AreaDrawingLayout, colorArgb: Int, scale: Double) {
    require(scale.isFinite() && scale > 0.0)
    val path = pointsPath(layout.points, closed = true)
    drawPath(path, Color((colorArgb and 0x00ffffff) or (48 shl 24)))
    drawPath(path, Color(colorArgb), style = Stroke(max(3.0, 2.4 * scale).toFloat(), cap = StrokeCap.Round))
}

fun DrawScope.drawAnnotationPolyline(points: List<Vec2>, color: Color, scale: Double) {
    require(scale.isFinite() && scale > 0.0)
    drawPath(pointsPath(points, closed = false), color, style = Stroke(max(3.0, 2.4 * scale).toFloat(), cap = StrokeCap.Round))
}

data class AnnotationControlStyle(val control: Color, val halo: Color, val selectedVertex: Color)

fun DrawScope.drawAnnotationControls(points: List<Vec2>, scale: Double, style: AnnotationControlStyle, selectedIndex: Int = -1) {
    require(scale.isFinite() && scale > 0.0)
    val radius = max(9.0, 7.0 * scale).toFloat()
    val haloRadius = radius + max(3.0, 2.0 * scale).toFloat()
    points.forEachIndexed { index, point ->
        drawCircle(style.halo, haloRadius, point.offset())
        drawCircle(if (index == selectedIndex) style.selectedVertex else style.control, radius, point.offset())
    }
}

private fun Vec2.offset() = Offset(x.toFloat(), y.toFloat())
private fun pointsPath(points: List<Vec2>, closed: Boolean) = Path().apply {
    points.forEachIndexed { index, point ->
        if (index == 0) moveTo(point.x.toFloat(), point.y.toFloat()) else lineTo(point.x.toFloat(), point.y.toFloat())
    }
    if (closed && points.isNotEmpty()) close()
}
