package com.example.boschlaser.shared.command

import com.example.boschlaser.shared.geometry.ColumnGeometry
import com.example.boschlaser.shared.model.CircularColumn
import com.example.boschlaser.shared.model.RectColumn
import com.example.boschlaser.shared.model.SketchDocument

/** Property edits copy the semantic entity, retaining its center and all untouched dimensions. */
data class ResizeColumnCommand(val columnId: String, val width: Double, val depth: Double) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        if (!width.isFinite() || !depth.isFinite() || width < 50.0 || depth < 50.0) return null
        val column = when (val old = document.columns[columnId] ?: return null) {
            is RectColumn -> old.copy(width = width, depth = depth)
            is CircularColumn -> old.copy(diameter = width)
        }
        return document.copy(columns = document.columns + (columnId to column))
    }
}

data class RotateColumnCommand(val columnId: String, val degrees: Double) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        if (!degrees.isFinite()) return null
        val old = document.columns[columnId] as? RectColumn ?: return null
        return document.copy(columns = document.columns +
            (columnId to old.copy(rotationDegrees = ColumnGeometry.normalizedDegrees(degrees))))
    }
}
