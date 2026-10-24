package com.example.boschlaser.shared.command

import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.OpeningGeometry
import com.example.boschlaser.shared.geometry.WallGeometry
import com.example.boschlaser.shared.model.Column
import com.example.boschlaser.shared.model.Opening
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall
import com.example.boschlaser.shared.model.WallAnchor
import com.example.boschlaser.shared.model.WallAlignment
import com.example.boschlaser.shared.topology.EndpointMove
import com.example.boschlaser.shared.topology.WallEditPlanner

/** Pure, atomic edit. Null means invalid input or a hosted-object conflict. */
fun interface SketchCommand {
    fun apply(document: SketchDocument): SketchDocument?
}

data class AddWallCommand(val wall: Wall) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        if (wall.id in document.walls || Geometry.length(WallGeometry.referenceLine(wall)) < 100.0) return null
        return document.copy(walls = document.walls + (wall.id to wall))
    }
}

data class DeleteWallCommand(val wallId: String) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        if (wallId !in document.walls) return null
        val walls = document.walls - wallId
        val openings = document.openings.filterValues { it.hostWallId != wallId }
        return document.copy(walls = walls, openings = openings,
            dimensions = document.dimensions.filterValues {
                it.first.exists(walls, openings, document.columns) && it.second.exists(walls, openings, document.columns)
            })
    }
}

/** All connected wall changes and opening constraints are validated before producing any new state. */
data class UpdateWallsCommand(
    val changes: Map<String, Wall>,
    val fixedAnchors: Map<String, WallAnchor> = emptyMap(),
) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        if (changes.any { (id, wall) -> id != wall.id || id !in document.walls }) return null
        val openings = document.openings.toMutableMap()
        for ((id, updated) in changes) {
            val old = document.walls.getValue(id)
            if (Geometry.length(WallGeometry.referenceLine(old)) < 1.0 ||
                Geometry.length(WallGeometry.referenceLine(updated)) < 1.0) return null
            val startChanged = Geometry.distance(old.start, updated.start) >= .001
            val endChanged = Geometry.distance(old.end, updated.end) >= .001
            val fixed = fixedAnchors[id] ?: if (startChanged && !endChanged) WallAnchor.END else WallAnchor.START
            for (opening in document.openings.values.filter { it.hostWallId == id }) {
                val hosted = OpeningGeometry.rehost(opening, old, updated, fixed) ?: return null
                // Existing mobile behavior requires a wall longer than the opening, even at exact boundaries.
                if (hosted.width >= Geometry.length(WallGeometry.referenceLine(updated))) return null
                openings[opening.id] = hosted
            }
        }
        return document.copy(walls = document.walls + changes, openings = openings.toMap())
    }
}

data class ChangeWallLengthCommand(val wallId: String, val length: Double, val fixedAnchor: WallAnchor) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        val wall = document.walls[wallId] ?: return null
        if (!length.isFinite() || length <= 10.0 || Geometry.length(WallGeometry.referenceLine(wall)) < 1.0) return null
        val updated = WallGeometry.setLength(wall, length, fixedAnchor)
        val moveStart = fixedAnchor == WallAnchor.END
        val point = if (moveStart) updated.start else updated.end
        val changes = WallEditPlanner.endpointChanges(document,
            listOf(EndpointMove(wallId, moveStart, point, length)))
        return UpdateWallsCommand(changes, mapOf(wallId to fixedAnchor)).apply(document)
    }
}

data class ChangeWallThicknessCommand(val wallId: String, val thickness: Double) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        val wall = document.walls[wallId] ?: return null
        if (!thickness.isFinite() || thickness !in 50.0..1000.0) return null
        return UpdateWallsCommand(mapOf(wallId to wall.copy(thickness = thickness))).apply(document)
    }
}

data class ChangeWallAlignmentCommand(val wallId: String, val alignment: WallAlignment) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        val wall = document.walls[wallId] ?: return null
        return UpdateWallsCommand(mapOf(wallId to wall.copy(alignment = alignment))).apply(document)
    }
}

data class AddOpeningCommand(val opening: Opening) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        val host = document.walls[opening.hostWallId] ?: return null
        if (opening.id in document.openings || !OpeningGeometry.fits(opening, host) ||
            opening.width >= Geometry.length(WallGeometry.referenceLine(host))) return null
        return document.copy(openings = document.openings + (opening.id to opening))
    }
}

data class UpdateOpeningCommand(val opening: Opening) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        val old = document.openings[opening.id] ?: return null
        val host = document.walls[opening.hostWallId] ?: return null
        if (old.hostWallId != opening.hostWallId || !OpeningGeometry.fits(opening, host) ||
            opening.width >= Geometry.length(WallGeometry.referenceLine(host))) return null
        return document.copy(openings = document.openings + (opening.id to opening))
    }
}

data class AddColumnCommand(val column: Column) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? =
        if (column.id in document.columns) null else document.copy(columns = document.columns + (column.id to column))
}

data class UpdateColumnCommand(val column: Column) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? =
        if (column.id !in document.columns) null else document.copy(columns = document.columns + (column.id to column))
}

data class DeleteOpeningCommand(val openingId: String) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        if (openingId !in document.openings) return null
        val openings = document.openings - openingId
        return document.copy(openings = openings, dimensions = document.dimensions.filterValues {
            it.first.exists(document.walls, openings, document.columns) && it.second.exists(document.walls, openings, document.columns)
        })
    }
}

data class DeleteColumnCommand(val columnId: String) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        if (columnId !in document.columns) return null
        val columns = document.columns - columnId
        return document.copy(columns = columns, dimensions = document.dimensions.filterValues {
            it.first.exists(document.walls, document.openings, columns) && it.second.exists(document.walls, document.openings, columns)
        })
    }
}
