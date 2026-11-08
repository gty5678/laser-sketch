package com.example.boschlaser.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil
import kotlin.math.floor

internal data class WallHatchLine(val start: Offset, val end: Offset)

/** Bound CPU work to the canvas, preserving the screen-space x + y hatch phase. */
internal fun wallHatchLines(bounds: Rect, canvas: Rect, spacing: Float, strokeWidth: Float): Sequence<WallHatchLine> {
    require(spacing.isFinite() && spacing > 0f && strokeWidth.isFinite() && strokeWidth >= 0f)
    val visible = bounds.intersect(canvas.inflate(strokeWidth))
    if (visible.isEmpty) return emptySequence()
    val first = floor((visible.left.toDouble() + visible.top) / spacing)
    val last = ceil((visible.right.toDouble() + visible.bottom) / spacing)
    return sequence {
        for (index in 0..(last - first).toInt()) {
            val phase = (first + index) * spacing
            yield(WallHatchLine(Offset((phase - visible.bottom).toFloat(), visible.bottom),
                Offset((phase - visible.top).toFloat(), visible.top)))
        }
    }
}
