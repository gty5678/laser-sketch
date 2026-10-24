package com.example.boschlaser

import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.Segment2
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.geometry.WallGeometry
import com.example.boschlaser.shared.model.CircularColumn
import com.example.boschlaser.shared.model.Opening
import com.example.boschlaser.shared.model.OpeningType
import com.example.boschlaser.shared.model.RectColumn
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall
import com.example.boschlaser.shared.model.WallAlignment

internal fun SketchPoint.toVec2() = Vec2(x.toDouble(), y.toDouble())
internal fun Vec2.toSketchPoint() = SketchPoint(x.toFloat(), y.toFloat())

/** Legacy files store CENTER endpoints even when the selected control line is a side. */
internal fun SketchWall.toCoreWall(): Wall {
    val center = Segment2(start.toVec2(), end.toVec2())
    val alignment = when (controlLine) {
        SketchWallControlLine.CENTER -> WallAlignment.CENTER
        SketchWallControlLine.INNER -> WallAlignment.LEFT
        SketchWallControlLine.OUTER -> WallAlignment.RIGHT
    }
    val offset = when (alignment) {
        WallAlignment.CENTER -> 0.0
        WallAlignment.LEFT -> -thickness / 2.0
        WallAlignment.RIGHT -> thickness / 2.0
    }
    val reference = if (Geometry.length(center) < 1.0) center else Geometry.offset(center, offset)
    return Wall(id, reference.start, reference.end, thickness.toDouble(), alignment, measuredLength?.toDouble())
}

internal fun Wall.toSketchWall(): SketchWall {
    val reference = Segment2(start, end)
    val center = if (Geometry.length(reference) < 1.0) reference else WallGeometry.build(this).centerLine
    val controlLine = when (alignment) {
        WallAlignment.CENTER -> SketchWallControlLine.CENTER
        WallAlignment.LEFT -> SketchWallControlLine.INNER
        WallAlignment.RIGHT -> SketchWallControlLine.OUTER
    }
    return SketchWall(id, center.start.toSketchPoint(), center.end.toSketchPoint(), thickness.toFloat(),
        measuredLength?.toFloat(), controlLine)
}

internal fun SketchState.toDocument(): SketchDocument {
    val coreWalls = walls.associate { it.id to it.toCoreWall() }
    return SketchDocument(
        walls = coreWalls,
        openings = openings.filter { it.wallId in coreWalls }.associate { opening ->
            val host = coreWalls.getValue(opening.wallId)
            opening.id to Opening(opening.id, opening.wallId,
                opening.position * Geometry.length(Segment2(host.start, host.end)), opening.width.toDouble(),
                OpeningType.valueOf(opening.type.name), opening.flipped, opening.hingeFlipped)
        },
        columns = columns.associate { column ->
            column.id to when (column.type) {
                SketchColumnType.RECTANGLE -> RectColumn(column.id, column.center.toVec2(),
                    column.width.toDouble(), column.depth.toDouble(), column.rotationDegrees.toDouble())
                SketchColumnType.CIRCLE -> CircularColumn(column.id, column.center.toVec2(), column.width.toDouble())
            }
        },
    )
}

internal fun SketchDocument.toSketchState() = SketchState(
    walls = walls.values.map { it.toSketchWall() },
    columns = columns.values.map { column -> when (column) {
        is RectColumn -> SketchColumn(column.id, SketchColumnType.RECTANGLE, column.center.toSketchPoint(),
            column.width.toFloat(), column.depth.toFloat(), column.rotationDegrees.toFloat())
        is CircularColumn -> SketchColumn(column.id, SketchColumnType.CIRCLE, column.center.toSketchPoint(),
            column.diameter.toFloat(), column.diameter.toFloat())
    } },
    openings = openings.values.map { opening ->
        val host = walls.getValue(opening.hostWallId)
        val length = Geometry.length(Segment2(host.start, host.end))
        SketchOpening(opening.id, SketchOpeningType.valueOf(opening.type.name), opening.hostWallId,
            (opening.offset / length).toFloat(), opening.width.toFloat(), opening.flipped, opening.hingeFlipped)
    },
)

internal fun SketchColumn.toCoreColumn() = when (type) {
    SketchColumnType.RECTANGLE -> RectColumn(id, center.toVec2(), width.toDouble(), depth.toDouble(), rotationDegrees.toDouble())
    SketchColumnType.CIRCLE -> CircularColumn(id, center.toVec2(), width.toDouble())
}

internal fun SketchOpening.toCoreOpening(wall: SketchWall) = Opening(
    id, wallId, position * Geometry.length(Segment2(wall.start.toVec2(), wall.end.toVec2())),
    width.toDouble(), OpeningType.valueOf(type.name), flipped, hingeFlipped,
)
