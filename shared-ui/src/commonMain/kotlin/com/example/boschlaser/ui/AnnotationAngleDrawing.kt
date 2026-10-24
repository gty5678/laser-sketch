package com.example.boschlaser.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.boschlaser.shared.annotation.AngleAnnotation
import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.Vec2
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Arc angles are clockwise screen degrees; the smaller sector is retained. */
data class AngleDrawingLayout(val vertex: Vec2, val first: Vec2, val second: Vec2,
    val startDegrees: Double, val sweepDegrees: Double, val radiusPx: Double, val labelBaselineCenter: Vec2,
    val lineWidthPx: Double)

fun angleDrawingLayout(mark: AngleAnnotation, viewport: PhotoDrawingViewport, scale: Double): AngleDrawingLayout {
    require(scale.isFinite() && scale > 0.0)
    val vertex = viewport.screen(mark.vertex)
    val first = viewport.screen(mark.first)
    val second = viewport.screen(mark.second)
    var start = atan2(first.y - vertex.y, first.x - vertex.x) * 180 / PI
    val end = atan2(second.y - vertex.y, second.x - vertex.x) * 180 / PI
    var sweep = (end - start + 360) % 360
    if (sweep > 180) { start = end; sweep = 360 - sweep }
    val radius = max(minOf(Geometry.distance(vertex, first), Geometry.distance(vertex, second)) * .32, max(22.0, 16.0 * scale))
    val middle = (start + sweep / 2) * PI / 180
    val labelRadius = radius + max(20.0, 14.0 * scale)
    return AngleDrawingLayout(vertex, first, second, start, sweep, radius,
        vertex + Vec2(cos(middle), sin(middle)) * labelRadius, max(3.0, 2.4 * scale))
}

fun DrawScope.drawAnnotationAngle(layout: AngleDrawingLayout, color: Color) {
    fun Vec2.offset() = Offset(x.toFloat(), y.toFloat())
    drawLine(color, layout.vertex.offset(), layout.first.offset(), layout.lineWidthPx.toFloat(), cap = StrokeCap.Round)
    drawLine(color, layout.vertex.offset(), layout.second.offset(), layout.lineWidthPx.toFloat(), cap = StrokeCap.Round)
    val radius = layout.radiusPx.toFloat()
    drawArc(color, layout.startDegrees.toFloat(), layout.sweepDegrees.toFloat(), false,
        layout.vertex.offset() - Offset(radius, radius), Size(radius * 2, radius * 2),
        style = Stroke(layout.lineWidthPx.toFloat(), cap = StrokeCap.Round))
}
