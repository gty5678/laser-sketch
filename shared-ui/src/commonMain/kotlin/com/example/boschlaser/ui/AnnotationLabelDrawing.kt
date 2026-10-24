package com.example.boschlaser.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.boschlaser.shared.geometry.Vec2
import kotlin.math.PI
import kotlin.math.max

/** Platform font adapter: native font advances and single-line glyph semantics remain explicit. */
data class AnnotationTextMetrics(val widthPx: Double, val ascentPx: Double, val descentPx: Double) {
    init { require(listOf(widthPx, ascentPx, descentPx).all { it.isFinite() } && widthPx >= 0 && descentPx >= ascentPx) }
}
interface AnnotationTextPainter {
    fun measure(text: String, fontSizePx: Double): AnnotationTextMetrics
    fun draw(scope: DrawScope, text: String, fontSizePx: Double, colorArgb: Int, baselineCenter: Vec2)
}

data class AnnotationLabelLayout(val anchor: Vec2, val baselineY: Double, val rotationDegrees: Double,
    val fontSizePx: Double, val left: Double, val top: Double, val right: Double, val bottom: Double,
    val cornerRadiusPx: Double, val whiteBackground: Boolean)

fun annotationTextSize(preset: Int, scale: Double): Double {
    require(scale.isFinite() && scale > 0)
    return when (preset) { 0 -> max(18.0, 17 * scale); 2 -> max(28.0, 28 * scale); else -> max(22.0, 22 * scale) }
}

fun annotationLabelLayout(anchor: Vec2, metrics: AnnotationTextMetrics, fontSizePx: Double,
    paddingPx: Double, verticalPaddingPx: Double = paddingPx, whiteBackground: Boolean = true,
    baselineY: Double = 0.0, rotationDegrees: Double = 0.0): AnnotationLabelLayout {
    require(fontSizePx.isFinite() && fontSizePx > 0)
    require(paddingPx.isFinite() && paddingPx >= 0 && verticalPaddingPx.isFinite() && verticalPaddingPx >= 0)
    require(baselineY.isFinite() && rotationDegrees.isFinite())
    return AnnotationLabelLayout(anchor, baselineY, rotationDegrees, fontSizePx,
        -metrics.widthPx / 2 - paddingPx, baselineY + metrics.ascentPx - verticalPaddingPx,
        metrics.widthPx / 2 + paddingPx, baselineY + metrics.descentPx + verticalPaddingPx, paddingPx, whiteBackground)
}

fun dimensionLabelLayout(shape: DimensionDrawingLayout, metrics: AnnotationTextMetrics, scale: Double): AnnotationLabelLayout {
    require(scale.isFinite() && scale > 0)
    val padding = max(8.0, 6 * scale)
    var rotation = shape.angleRadians * 180 / PI
    if (rotation > 90 || rotation < -90) rotation += 180
    return annotationLabelLayout((shape.start + shape.end) * .5, metrics, max(24.0, 18 * scale), padding, padding / 2,
        baselineY = -max(13.0, 10 * scale) - metrics.descentPx, rotationDegrees = rotation)
}

/** Shared background, transform and baseline; glyphs are drawn by the supplied font adapter. */
fun DrawScope.drawAnnotationLabel(layout: AnnotationLabelLayout, text: String, colorArgb: Int,
    background: Color, painter: AnnotationTextPainter) {
    withTransform({
        translate(layout.anchor.x.toFloat(), layout.anchor.y.toFloat())
        rotate(layout.rotationDegrees.toFloat(), Offset.Zero)
    }) {
        if (layout.whiteBackground) drawRoundRect(background, Offset(layout.left.toFloat(), layout.top.toFloat()),
            Size((layout.right - layout.left).toFloat(), (layout.bottom - layout.top).toFloat()),
            CornerRadius(layout.cornerRadiusPx.toFloat()))
        painter.draw(this, text, layout.fontSizePx, colorArgb, Vec2(0.0, layout.baselineY))
    }
}
