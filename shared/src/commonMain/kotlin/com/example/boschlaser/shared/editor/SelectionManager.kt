package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.model.SketchDocument

object SelectionManager {
    fun valid(document: SketchDocument, selection: Selection?): Selection? = when (selection) {
        is Selection.Wall -> selection.takeIf { it.id in document.walls }
        is Selection.Column -> selection.takeIf { it.id in document.columns }
        is Selection.Opening -> selection.takeIf { it.id in document.openings }
        is Selection.Dimension -> selection.takeIf { it.id in document.dimensions }
        null -> null
    }
}
