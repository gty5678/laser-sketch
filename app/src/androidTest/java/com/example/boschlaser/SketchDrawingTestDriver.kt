package com.example.boschlaser

import android.graphics.Canvas
import androidx.compose.ui.unit.Density
import com.example.boschlaser.platform.AndroidCanvasDrawing
import com.example.boschlaser.platform.androidSketchDrawingStyle
import com.example.boschlaser.shared.editor.SelectionGestureTarget
import com.example.boschlaser.shared.editor.ToolType
import com.example.boschlaser.shared.editor.Viewport
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.ui.drawSketchColumn
import com.example.boschlaser.ui.drawSketchOpening
import com.example.boschlaser.ui.drawSketchWallNetwork

/** Pixel fixtures call the same shared drawing functions as the screen and export. */
internal fun SketchView.drawWallNetworkFixture(canvas: Canvas, scale: Float, x: Float, y: Float) {
    val density = Density(resources.displayMetrics.density, resources.configuration.fontScale)
    val style = androidSketchDrawingStyle(context, density, 10f * density.density)
    AndroidCanvasDrawing().draw(canvas, density) {
        drawSketchWallNetwork(snapshotDocument(), Viewport(scale.toDouble(), Vec2(x.toDouble(), y.toDouble())), style.wall)
    }
}

internal fun SketchView.drawColumnFixture(canvas: Canvas, column: SketchColumn,
    scale: Float, x: Float, y: Float, selected: Boolean) {
    val density = Density(resources.displayMetrics.density, resources.configuration.fontScale)
    val style = androidSketchDrawingStyle(context, density, 10f * density.density)
    val state = editorState()
    AndroidCanvasDrawing().draw(canvas, density) {
        drawSketchColumn(column.toCoreColumn(), Viewport(scale.toDouble(), Vec2(x.toDouble(), y.toDouble())),
            selected, state.tool == ToolType.SELECT,
            state.dragging && state.selectionGesture?.target !is SelectionGestureTarget.ColumnRotation,
            (32f * density.density).toDouble(), style.column)
    }
}

internal fun SketchView.drawOpeningFixture(canvas: Canvas, opening: SketchOpening, wall: SketchWall,
    scale: Float, x: Float, y: Float, selected: Boolean) {
    val density = Density(resources.displayMetrics.density, resources.configuration.fontScale)
    val style = androidSketchDrawingStyle(context, density, 10f * density.density)
    AndroidCanvasDrawing().draw(canvas, density) {
        drawSketchOpening(opening.toCoreOpening(wall), wall.toCoreWall(),
            Viewport(scale.toDouble(), Vec2(x.toDouble(), y.toDouble())),
            if (selected) style.selectedOpening else style.opening)
    }
}
