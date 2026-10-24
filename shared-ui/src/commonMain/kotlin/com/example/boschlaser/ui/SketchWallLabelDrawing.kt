package com.example.boschlaser.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import com.example.boschlaser.shared.editor.Viewport
import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.geometry.WallGeometry
import com.example.boschlaser.shared.model.Wall
import kotlin.math.max

/** Baseline is anchored beside the physical center line; controls use the reference line. */
data class WallLengthLabelLayout(val baselineCenter: Vec2, val valueMm: Double)

fun wallLengthLabelLayout(wall: Wall, viewport: Viewport): WallLengthLabelLayout? {
    if (Geometry.length(WallGeometry.referenceLine(wall)) * viewport.scale < 1.0) return null
    val center = WallGeometry.build(wall).centerLine
    val start = viewport.worldToScreen(center.start)
    val end = viewport.worldToScreen(center.end)
    val direction = end - start
    val lengthPx = Geometry.distance(start, end)
    if (lengthPx < 1.0) return null
    val normal = Vec2(-direction.y, direction.x) * (max(2.5, wall.thickness * viewport.scale / 2.0) * 1.9 / lengthPx)
    return WallLengthLabelLayout((start + end) * .5 + normal,
        wall.measuredLength ?: Geometry.length(WallGeometry.referenceLine(wall)))
}

/** Host supplies localized text, font style and a platform font resolver through the measurer. */
fun DrawScope.drawSketchWallLabel(layout: WallLengthLabelLayout, text: String, measurer: TextMeasurer, style: TextStyle) {
    val measured = measurer.measure(text, style, softWrap = false, maxLines = 1, density = this)
    val centerX = (measured.getLineLeft(0) + measured.getLineRight(0)) / 2f
    drawText(measured, topLeft = Offset(layout.baselineCenter.x.toFloat() - centerX,
        layout.baselineCenter.y.toFloat() - measured.firstBaseline))
}
