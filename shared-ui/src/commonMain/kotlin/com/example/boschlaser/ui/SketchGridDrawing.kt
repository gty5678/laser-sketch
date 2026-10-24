package com.example.boschlaser.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.boschlaser.shared.editor.Viewport
import com.example.boschlaser.shared.geometry.Vec2
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/** Scale thresholds retain the original Android Float screen-scale policy. */
fun gridStepForScale(baseStepMm: Double, scale: Float): Double {
    require(baseStepMm.isFinite() && baseStepMm > 0.0 && scale.isFinite() && scale > 0f)
    return baseStepMm * when { scale < .05f -> 10.0; scale < .12f -> 5.0; else -> 1.0 }
}

data class GridDrawingLine(val start: Vec2, val end: Vec2, val major: Boolean)

fun gridDrawingLines(viewport: Viewport, sizePx: Vec2, baseStepMm: Double): Sequence<GridDrawingLine> {
    require(sizePx.x >= 0.0 && sizePx.y >= 0.0)
    val step = gridStepForScale(baseStepMm, viewport.scale.toFloat())
    val majorStep = baseStepMm * 10.0
    return sequence {
        suspend fun SequenceScope<GridDrawingLine>.axis(length: Double, offset: Double, vertical: Boolean) {
            val first = floor(-offset / viewport.scale / step)
            val count = ceil(length / viewport.scale / step).toInt() + 2
            for (index in 0 until count) {
                val world = (first + index) * step
                val pixel = world * viewport.scale + offset
                if (pixel > length) break
                val ratio = world / majorStep
                val major = abs(ratio - round(ratio)) < .01
                yield(if (vertical) GridDrawingLine(Vec2(pixel, 0.0), Vec2(pixel, sizePx.y), major)
                    else GridDrawingLine(Vec2(0.0, pixel), Vec2(sizePx.x, pixel), major))
            }
        }
        axis(sizePx.x, viewport.offset.x, true)
        axis(sizePx.y, viewport.offset.y, false)
    }
}

data class GridDrawingStyle(val minor: Color, val minorWidthPx: Float, val major: Color, val majorWidthPx: Float)

fun DrawScope.drawSketchGrid(viewport: Viewport, sizePx: Vec2, baseStepMm: Double, style: GridDrawingStyle) {
    gridDrawingLines(viewport, sizePx, baseStepMm).forEach { line ->
        drawLine(if (line.major) style.major else style.minor,
            Offset(line.start.x.toFloat(), line.start.y.toFloat()), Offset(line.end.x.toFloat(), line.end.y.toFloat()),
            strokeWidth = if (line.major) style.majorWidthPx else style.minorWidthPx)
    }
}
