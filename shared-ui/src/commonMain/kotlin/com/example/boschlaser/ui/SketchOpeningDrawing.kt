package com.example.boschlaser.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.boschlaser.shared.editor.Viewport
import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.OpeningGeometry
import com.example.boschlaser.shared.geometry.Segment2
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.Opening
import com.example.boschlaser.shared.model.OpeningType
import com.example.boschlaser.shared.model.Wall

/** Pixel widths and colors come from the host theme; all document coordinates remain Double. */
data class OpeningDrawingStyle(val symbolColor: Color, val symbolWidthPx: Float,
    val wallColor: Color, val wallWidthPx: Float)

/** Used by Compose Canvas and Android bitmap/magnifier adapters, without editing or hit testing. */
fun DrawScope.drawSketchOpening(opening: Opening, wall: Wall, viewport: Viewport, style: OpeningDrawingStyle) {
    if (Geometry.length(Segment2(wall.start, wall.end)) < 1.0) return
    fun screen(point: Vec2): Offset = viewport.worldToScreen(point).let { Offset(it.x.toFloat(), it.y.toFloat()) }
    fun line(segment: Segment2, color: Color, width: Float) {
        drawLine(color, screen(segment.start), screen(segment.end), strokeWidth = width)
    }
    if (opening.type == OpeningType.WINDOW) {
        val segments = OpeningGeometry.symbolSegments(opening, wall, minimumHalfThickness = 2.5 / viewport.scale)
        // Outer frame uses the wall stroke; inner frame and closures use the active symbol stroke.
        for (index in listOf(2, 3)) line(segments[index], style.wallColor, style.wallWidthPx)
        for (index in listOf(4, 5, 0, 1)) line(segments[index], style.symbolColor, style.symbolWidthPx)
    } else {
        val swing = OpeningGeometry.doorSwing(opening, wall) ?: return
        val hinge = screen(swing.hinge)
        val radius = (swing.radius * viewport.scale).toFloat()
        drawLine(style.symbolColor, hinge, screen(swing.leafEnd), strokeWidth = style.symbolWidthPx)
        drawArc(style.symbolColor, swing.startDegrees.toFloat(), swing.sweepDegrees.toFloat(), false,
            topLeft = hinge - Offset(radius, radius), size = Size(radius * 2, radius * 2),
            style = Stroke(width = style.symbolWidthPx))
    }
}
