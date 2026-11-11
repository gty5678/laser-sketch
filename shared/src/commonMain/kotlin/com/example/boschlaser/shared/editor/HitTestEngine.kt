package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.geometry.ColumnGeometry
import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.OpeningGeometry
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.geometry.WallGeometry
import com.example.boschlaser.shared.model.Column
import com.example.boschlaser.shared.model.Opening
import com.example.boschlaser.shared.model.OpeningType
import com.example.boschlaser.shared.model.DoorStyle
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall
import com.example.boschlaser.shared.topology.WallEndpoint

sealed interface HitResult {
    data class Board(val boardId: String) : HitResult
    data class Wall(val wallId: String) : HitResult
    data class WallEndpoint(val wallId: String, val endpoint: com.example.boschlaser.shared.topology.WallEndpoint) : HitResult
    data class Opening(val openingId: String) : HitResult
    data class Column(val columnId: String) : HitResult
}

data class HitTestContext(
    val endpointTolerance: Double,
    val openingTolerance: Double,
    val columnTolerance: Double,
    val wallTolerance: Double,
    val selectedWallId: String? = null,
) {
    init {
        require(listOf(endpointTolerance, openingTolerance, columnTolerance, wallTolerance)
            .all { it.isFinite() && it >= 0.0 })
    }
}

class HitTestEngine {
    /** Selected locator endpoints precede other endpoints, symbols, columns and wall bodies. */
    fun hitTest(document: SketchDocument, point: Vec2, context: HitTestContext): List<HitResult> = buildList {
        val selected = context.selectedWallId?.let(document.walls::get)
        val walls = listOfNotNull(selected) + document.walls.values.toList().asReversed().filter { it.id != selected?.id }
        walls.forEach { wall ->
            val endpoint = when {
                Geometry.distance(point, wall.start) <= context.endpointTolerance -> WallEndpoint.START
                Geometry.distance(point, wall.end) <= context.endpointTolerance -> WallEndpoint.END
                else -> null
            }
            if (endpoint != null) add(HitResult.WallEndpoint(wall.id, endpoint))
        }
        document.openings.values.toList().asReversed().forEach { opening ->
            if (hitsOpening(opening, document.walls.getValue(opening.hostWallId), point, context.openingTolerance))
                add(HitResult.Opening(opening.id))
        }
        document.columns.values.toList().asReversed().forEach { column ->
            if (hitsColumn(column, point, context.columnTolerance)) add(HitResult.Column(column.id))
        }
        document.walls.values.toList().asReversed().forEach { wall ->
            if (hitsWall(wall, point, context.wallTolerance)) add(HitResult.Wall(wall.id))
        }
        document.boards.values.toList().asReversed().forEach { if (it.contains(point)) add(HitResult.Board(it.id)) }
    }

    fun hitsWall(wall: Wall, point: Vec2, tolerance: Double): Boolean {
        require(tolerance.isFinite() && tolerance >= 0.0)
        val shape = WallGeometry.build(wall)
        return Geometry.distanceToSegment(point, shape.centerLine) <= wall.thickness / 2.0 + tolerance
    }
    fun hitsOpening(opening: Opening, wall: Wall, point: Vec2, tolerance: Double): Boolean {
        require(tolerance.isFinite() && tolerance >= 0.0)
        if (opening.type == OpeningType.DOOR && opening.doorStyle == DoorStyle.OPENING) {
            // A bare doorway has no leaf or arc to grab: its actual cutout is the handle.
            val polygon = OpeningGeometry.cutoutPolygon(opening, wall)
            val along = Geometry.normalize(polygon[1] - polygon[0])
            val across = Vec2(-along.y, along.x)
            val center = (polygon[0] + polygon[2]) * 0.5
            val delta = point - center
            val halfWidth = Geometry.distance(polygon[0], polygon[1]) / 2.0
            val halfThickness = Geometry.distance(polygon[1], polygon[2]) / 2.0
            return kotlin.math.abs(delta.x * along.x + delta.y * along.y) <= halfWidth + tolerance + 1e-7 &&
                kotlin.math.abs(delta.x * across.x + delta.y * across.y) <= halfThickness + tolerance + 1e-7
        }
        return OpeningGeometry.symbolSegments(opening, wall).any { Geometry.distanceToSegment(point, it) <= tolerance }
    }
    fun hitsColumn(column: Column, point: Vec2, tolerance: Double) = ColumnGeometry.contains(column, point, tolerance)
    /** Body-only query for callers that do not display endpoint controls. */
    fun hitTest(document: SketchDocument, point: Vec2, tolerance: Double): List<HitResult> = buildList {
        document.openings.values.toList().asReversed().forEach { opening ->
            if (hitsOpening(opening, document.walls.getValue(opening.hostWallId), point, tolerance))
                add(HitResult.Opening(opening.id))
        }
        document.columns.values.toList().asReversed().forEach { column ->
            if (hitsColumn(column, point, tolerance)) add(HitResult.Column(column.id))
        }
        document.walls.values.toList().asReversed().forEach { wall ->
            if (hitsWall(wall, point, tolerance)) add(HitResult.Wall(wall.id))
        }
        document.boards.values.toList().asReversed().forEach { if (it.contains(point)) add(HitResult.Board(it.id)) }
    }
}
