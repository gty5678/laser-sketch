package com.example.boschlaser.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.example.boschlaser.shared.editor.Viewport
import com.example.boschlaser.shared.geometry.Vec2

data class SketchMagnifier(val target: Vec2, val zoom: Double, val topOffsetPx: Double)
data class MagnifierDrawingStyle(val background: Color, val shadow: Color, val border: Color)
data class MagnifierDrawingLayout(val center: Vec2, val radius: Double, val shadowCenter: Vec2,
    val shadowRadius: Double, val viewport: Viewport)

fun magnifierDrawingLayout(viewport: Viewport, magnifier: SketchMagnifier, density: Double): MagnifierDrawingLayout {
    require(density.isFinite() && density > 0.0)
    require(magnifier.zoom.isFinite() && magnifier.zoom > 0.0 && magnifier.topOffsetPx.isFinite())
    val radius = 72.0 * density
    val center = Vec2(88.0 * density, magnifier.topOffsetPx + 84.0 * density)
    val scale = (viewport.scale * magnifier.zoom).coerceAtMost(1.8)
    return MagnifierDrawingLayout(center, radius, center + Vec2(2.0, 3.0) * density,
        radius + 4.0 * density, Viewport(scale, center - magnifier.target * scale))
}

/** Only the clipped scene uses the magnified viewport; screen controls and grid retain px sizes. */
fun DrawScope.drawSketchMagnifier(layout: MagnifierDrawingLayout, style: MagnifierDrawingStyle,
    drawScene: DrawScope.(Viewport) -> Unit) {
    val center = Offset(layout.center.x.toFloat(), layout.center.y.toFloat())
    val radius = layout.radius.toFloat()
    drawCircle(style.shadow, layout.shadowRadius.toFloat(), Offset(layout.shadowCenter.x.toFloat(), layout.shadowCenter.y.toFloat()))
    val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center - Offset(radius, radius),
        center + Offset(radius, radius))) }
    clipPath(clip) {
        drawRect(style.background)
        drawScene(layout.viewport)
    }
    drawCircle(style.border, radius, center, style = Stroke(3f * density))
}
