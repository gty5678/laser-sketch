package com.example.boschlaser.shared.command

import com.example.boschlaser.shared.model.Column
import com.example.boschlaser.shared.model.Opening
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall
import com.example.boschlaser.shared.model.WallDimension

/** History retains changed entities only, rather than a full document for every edit. */
class CommandManager(initial: SketchDocument = SketchDocument(), private val historyLimit: Int = 100) {
    init { require(historyLimit > 0) }
    var document: SketchDocument = initial.copy(
        walls = initial.walls.toMap(), openings = initial.openings.toMap(), columns = initial.columns.toMap(), dimensions = initial.dimensions.toMap(),
    )
        private set
    private val undoStack = ArrayDeque<DocumentDelta>()
    private val redoStack = ArrayDeque<DocumentDelta>()
    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    fun execute(command: SketchCommand): Boolean {
        val updated = command.apply(document) ?: return false
        if (updated == document) return false
        val delta = DocumentDelta.between(document, updated)
        document = updated
        undoStack.addLast(delta)
        if (undoStack.size > historyLimit) undoStack.removeFirst()
        redoStack.clear()
        return true
    }
    fun undo(): Boolean {
        val delta = undoStack.removeLastOrNull() ?: return false
        document = delta.apply(document, forward = false)
        redoStack.addLast(delta)
        return true
    }
    fun redo(): Boolean {
        val delta = redoStack.removeLastOrNull() ?: return false
        document = delta.apply(document, forward = true)
        undoStack.addLast(delta)
        return true
    }
    fun replace(document: SketchDocument) {
        this.document = document.copy(walls = document.walls.toMap(),
            openings = document.openings.toMap(), columns = document.columns.toMap(), dimensions = document.dimensions.toMap())
        undoStack.clear()
        redoStack.clear()
    }
}

private data class EntityChange<T>(val before: T?, val after: T?)
private data class DocumentDelta(
    val walls: Map<String, EntityChange<Wall>>,
    val openings: Map<String, EntityChange<Opening>>,
    val columns: Map<String, EntityChange<Column>>,
    val dimensions: Map<String, EntityChange<WallDimension>>,
    val wallOrder: Pair<List<String>, List<String>>?,
    val openingOrder: Pair<List<String>, List<String>>?,
    val columnOrder: Pair<List<String>, List<String>>?,
    val dimensionOrder: Pair<List<String>, List<String>>?,
) {
    fun apply(document: SketchDocument, forward: Boolean) = document.copy(
        walls = applyChanges(document.walls, walls, wallOrder, forward),
        openings = applyChanges(document.openings, openings, openingOrder, forward),
        columns = applyChanges(document.columns, columns, columnOrder, forward),
        dimensions = applyChanges(document.dimensions, dimensions, dimensionOrder, forward),
    )
    companion object {
        fun between(before: SketchDocument, after: SketchDocument) = DocumentDelta(
            changes(before.walls, after.walls), changes(before.openings, after.openings), changes(before.columns, after.columns),
            changes(before.dimensions, after.dimensions),
            order(before.walls, after.walls), order(before.openings, after.openings), order(before.columns, after.columns),
            order(before.dimensions, after.dimensions),
        )
        private fun <T> order(before: Map<String, T>, after: Map<String, T>): Pair<List<String>, List<String>>? =
            if (before.keys.toList() == after.keys.toList()) null else before.keys.toList() to after.keys.toList()
        private fun <T> changes(before: Map<String, T>, after: Map<String, T>): Map<String, EntityChange<T>> =
            (before.keys + after.keys).filter { before[it] != after[it] }
                .associateWith { EntityChange(before[it], after[it]) }
        private fun <T> applyChanges(
            source: Map<String, T>, changes: Map<String, EntityChange<T>>,
            order: Pair<List<String>, List<String>>?, forward: Boolean,
        ): Map<String, T> {
            val updated = source.toMutableMap().apply {
                changes.forEach { (id, change) ->
                    val value = if (forward) change.after else change.before
                    if (value == null) remove(id) else put(id, value)
                }
            }
            if (order == null) return updated.toMap()
            val keys = if (forward) order.second else order.first
            return keys.associateWith { updated.getValue(it) }
        }
    }
}
