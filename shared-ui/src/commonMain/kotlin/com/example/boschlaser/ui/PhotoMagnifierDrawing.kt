package com.example.boschlaser.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.boschlaser.shared.geometry.Vec2

data class PhotoMagnifierLayout(
    val center: Vec2,
    val radius: Double,
    val focus: Vec2,
    val zoom: Double = 2.35,
) {
    fun magnify(point: Vec2) = center + (point - focus) * zoom
}
data class PhotoMagnifierStyle(
    val background: Color,
    val border: Color,
    val crosshair: Color,
    val borderWidthPx: Float,
    val crosshairWidthPx: Float,
    val crosshairHalfLengthPx: Float,
)

fun photoMagnifierLayout(sizePx: Vec2, density: Double, focus: Vec2): PhotoMagnifierLayout? {
    require(sizePx.x >= 0 && sizePx.y >= 0 && density.isFinite() && density > 0)
    val margin = 12 * density
    val diameter = minOf(128 * density, sizePx.x - margin * 2, sizePx.y - margin * 2)
    if (diameter <= 0) return null
    val radius = diameter / 2
    return PhotoMagnifierLayout(Vec2(sizePx.x - margin - radius, margin + radius), radius, focus)
}

/** Photo bytes stay in the platform callback; clipping and magnification belong to shared UI. */
fun DrawScope.drawPhotoMagnifier(
    layout: PhotoMagnifierLayout,
    style: PhotoMagnifierStyle,
    drawContents: DrawScope.() -> Unit,
) {
    val center = Offset(layout.center.x.toFloat(), layout.center.y.toFloat())
    val radius = layout.radius.toFloat()
    drawCircle(style.background, radius, center)
    val clip = Path().apply {
        addOval(Rect(center - Offset(radius, radius), center + Offset(radius, radius)))
    }
    clipPath(clip) {
        withTransform({
            translate(center.x, center.y)
            scale(layout.zoom.toFloat(), layout.zoom.toFloat(), Offset.Zero)
            translate(-layout.focus.x.toFloat(), -layout.focus.y.toFloat())
        }) { drawContents() }
    }
    drawCircle(style.border, radius, center,style = Stroke(style.borderWidthPx))
    val cross = style.crosshairHalfLengthPx
    drawLine(style.crosshair, center - Offset(cross, 0f), center + Offset(cross, 0f), style.crosshairWidthPx)
    drawLine(style.crosshair, center - Offset(0f, cross), center + Offset(0f, cross), style.crosshairWidthPx)
}
