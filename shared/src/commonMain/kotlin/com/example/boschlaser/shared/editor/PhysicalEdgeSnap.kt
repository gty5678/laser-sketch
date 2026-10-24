package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*

/** Measurement targets come from model geometry, independent of symbol strokes and screen scale. */
internal object PhysicalEdgeSnap {
    private data class Edge(val id: String, val line: Segment2)

    fun snap(document: SketchDocument, input: Vec2, context: SnapContext): SnapResult? {
        val edges = mutableListOf<Edge>()
        fun polygon(id: String, points: List<Vec2>) {
            points.indices.forEach { index ->
                val line = Segment2(points[index], points[(index + 1) % points.size])
                if (Geometry.length(line) > Geometry.EPSILON) edges += Edge(id, line)
            }
        }
        // Keep frames/columns ahead of walls where physical edges coincide.
        document.openings.values.forEach { opening ->
            val wall = document.walls[opening.hostWallId] ?: return@forEach
            if (Geometry.length(WallGeometry.referenceLine(wall)) > Geometry.EPSILON)
                polygon(opening.id, OpeningGeometry.cutoutPolygon(opening, wall))
        }
        document.columns.values.filterIsInstance<RectColumn>().forEach { polygon(it.id, ColumnGeometry.corners(it)) }
        document.walls.values.forEach { wall ->
            if (Geometry.length(WallGeometry.referenceLine(wall)) > Geometry.EPSILON)
                polygon(wall.id, WallGeometry.build(wall).polygon)
        }
        fun nearest(candidates: List<SnapResult>): SnapResult? {
            val eligible = candidates.filter { Geometry.distance(it.point, input) <= context.tolerance }
            val distance = eligible.minOfOrNull { Geometry.distance(it.point, input) } ?: return null
            // Float screen rounding must not change the target at coincident frame/wall edges.
            return eligible.first { Geometry.distance(it.point, input) <= distance + context.tolerance * .025 }
        }
        if (context.endpoints) nearest(edges.flatMap { edge ->
            listOf(SnapResult(edge.line.start, SnapType.ENDPOINT, edge.id), SnapResult(edge.line.end, SnapType.ENDPOINT, edge.id))
        })?.let { return it }
        val circles = document.columns.values.filterIsInstance<CircularColumn>()
        if (context.midpoints) {
            val keys = edges.map { SnapResult((it.line.start + it.line.end) * .5, SnapType.MIDPOINT, it.id) } +
                circles.flatMap { column ->
                    listOf(Vec2(1.0, 0.0), Vec2(-1.0, 0.0), Vec2(0.0, 1.0), Vec2(0.0, -1.0)).map {
                        SnapResult(column.center + it * (column.diameter / 2.0), SnapType.QUADRANT, column.id)
                    }
                }
            nearest(keys)?.let { return it }
        }
        val projections = edges.map { SnapResult(Geometry.closestPoint(input, it.line), SnapType.OBJECT_EDGE, it.id) } +
            circles.mapNotNull { column ->
                val delta = input - column.center
                if (Geometry.distance(input, column.center) <= Geometry.EPSILON) null
                else SnapResult(column.center + Geometry.normalize(delta) * (column.diameter / 2.0), SnapType.OBJECT_EDGE, column.id)
            }
        return nearest(projections)
    }
}
