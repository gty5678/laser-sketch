package com.example.boschlaser.platform

import android.graphics.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CanvasHolder
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** A single Compose drawing implementation can also target existing Android bitmap exports. */
internal class AndroidCanvasDrawing {
    private val holder = CanvasHolder()
    private val scope = CanvasDrawScope()

    fun draw(canvas: Canvas, density: Density, block: DrawScope.() -> Unit) {
        holder.drawInto(canvas) {
            scope.draw(density, LayoutDirection.Ltr, this, Size(canvas.width.toFloat(), canvas.height.toFloat()), block)
        }
    }
}
