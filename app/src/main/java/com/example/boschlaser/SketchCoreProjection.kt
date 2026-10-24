package com.example.boschlaser

import com.example.boschlaser.shared.model.Column
import com.example.boschlaser.shared.model.Opening
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall
import java.util.WeakHashMap

/** Retains exact legacy values while their shared entities are alive, including Undo history.
 * This avoids Float round-trip drift and preserves unused legacy circular-column fields.
 * These are adapter projections, never a second mutable document.
 */
internal class SketchCoreProjection {
    private val walls = WeakHashMap<Wall, SketchWall>()
    private val columns = WeakHashMap<Column, SketchColumn>()
    private data class HostedProjection(val host: Wall, val opening: SketchOpening)
    private val openings = WeakHashMap<Opening, HostedProjection>()
    private var lastDocument: SketchDocument? = null
    private var lastState = SketchState()

    fun remember(wall: SketchWall): Wall = wall.toCoreWall().also { walls[it] = wall }
    fun remember(column: SketchColumn): Column = column.toCoreColumn().also { columns[it] = column }
    fun remember(opening: SketchOpening, host: SketchWall): Opening =
        opening.toCoreOpening(host).also { openings[it] = HostedProjection(host.toCoreWall(), opening) }

    fun restore(state: SketchState): SketchDocument {
        walls.clear(); columns.clear(); openings.clear()
        val document = state.toDocument()
        state.walls.forEach { walls[document.walls.getValue(it.id)] = it }
        state.columns.forEach { columns[document.columns.getValue(it.id)] = it }
        state.openings.forEach { opening -> document.openings[opening.id]?.let {
            openings[it] = HostedProjection(document.walls.getValue(it.hostWallId), opening)
        } }
        lastDocument = null
        return document
    }

    fun state(document: SketchDocument): SketchState {
        if (document === lastDocument) return lastState
        val projected = document.toSketchState()
        lastState = SketchState(
            walls = projected.walls.map { walls[document.walls.getValue(it.id)] ?: it },
            columns = projected.columns.map { columns[document.columns.getValue(it.id)] ?: it },
            openings = projected.openings.map { opening ->
                val core = document.openings.getValue(opening.id)
                openings[core]?.takeIf { it.host == document.walls.getValue(core.hostWallId) }?.opening ?: opening
            },
        )
        lastDocument = document
        return lastState
    }
}
