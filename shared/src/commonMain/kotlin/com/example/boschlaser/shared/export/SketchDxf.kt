package com.example.boschlaser.shared.export

import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*
import kotlin.math.*

/** Millimetre model-space outlines. Screen Y is reflected into CAD Y. Materials are omitted. */
fun sketchDxf(document: SketchDocument): String = buildString {
    fun pair(code: Int, value: Any) { append(code).append('\n').append(value).append('\n') }
    fun point(code: Int, p: Vec2) { pair(code, p.x); pair(code + 10, -p.y) }
    fun line(a: Vec2, b: Vec2, layer: String) {
        if (Geometry.distance(a, b) < 1e-8) return
        pair(0, "LINE"); pair(8, layer); point(10, a); point(11, b)
    }
    fun polygon(points: List<Vec2>, layer: String) {
        points.indices.forEach { line(points[it], points[(it + 1) % points.size], layer) }
    }
    fun circle(center: Vec2, radius: Double, layer: String) {
        pair(0, "CIRCLE"); pair(8, layer); point(10, center); pair(40, radius)
    }
    pair(0, "SECTION"); pair(2, "HEADER")
    pair(9, "\$ACADVER"); pair(1, "AC1015")
    pair(9, "\$INSUNITS"); pair(70, 4)
    pair(0, "ENDSEC"); pair(0, "SECTION"); pair(2, "ENTITIES")
    document.walls.values.forEach { wall ->
        val openings = document.openings.values.filter { it.hostWallId == wall.id }
        val polygon = WallNetworkGeometry.bodyPolygon(wall, document.walls.values)
        val direction = Geometry.normalize(wall.end - wall.start)
        val cuts = openings.map { opening ->
            val length = Geometry.distance(wall.start, wall.end)
            val center = OpeningGeometry.constrainedOffset(opening.offset, length, opening.width)
            (center - opening.width / 2) to (center + opening.width / 2)
        }
        polygon.indices.forEach { index ->
            val a = polygon[index]; val b = polygon[(index + 1) % polygon.size]
            val start = (a - wall.start).dot(direction); val delta = (b - a).dot(direction)
            var intervals = listOf(0.0 to 1.0)
            cuts.forEach { (low, high) ->
                if (abs(delta) < 1e-9) {
                    if (start > low && start < high) intervals = emptyList()
                } else {
                    val t1 = (low - start) / delta; val t2 = (high - start) / delta
                    val left = min(t1, t2); val right = max(t1, t2)
                    intervals = intervals.flatMap { (l, r) ->
                        if (right <= l || left >= r) listOf(l to r)
                        else listOfNotNull((l to min(r, left)).takeIf { it.second > it.first },
                            (max(l, right) to r).takeIf { it.second > it.first })
                    }
                }
            }
            intervals.forEach { (l, r) -> line(a + (b - a) * l, a + (b - a) * r, "WALLS") }
        }
    }
    document.openings.values.forEach { opening ->
        OpeningGeometry.symbolSegments(opening, document.walls.getValue(opening.hostWallId)).forEach {
            line(it.start, it.end, "OPENINGS")
        }
    }
    document.columns.values.forEach { column -> when (column) {
        is RectColumn -> polygon(ColumnGeometry.corners(column), "COLUMNS")
        is CircularColumn -> circle(column.center, column.diameter / 2, "COLUMNS")
    } }
    document.boards.values.forEach { polygon(it.vertices, "BOARDS") }
    document.dimensions.values.forEach { dimension ->
        WallDimensionGeometry.layout(document, dimension)?.let { layout ->
            line(layout.firstWitness, layout.line.start, "DIMENSIONS")
            line(layout.secondWitness, layout.line.end, "DIMENSIONS")
            val center = layout.arcCenter
            if (center == null) line(layout.line.start, layout.line.end, "DIMENSIONS")
            else {
                val radius = Geometry.distance(center, layout.line.start)
                val start = atan2(layout.line.start.y - center.y, layout.line.start.x - center.x)
                var previous = layout.line.start
                for (step in 1..48) {
                    val angle = start + layout.sweep * step / 48
                    val next = center + Vec2(cos(angle), sin(angle)) * radius
                    line(previous, next, "DIMENSIONS"); previous = next
                }
            }
            pair(0, "TEXT"); pair(8, "DIMENSIONS"); point(10, (layout.line.start + layout.line.end) * .5)
            pair(40, 60.0); pair(1, ((layout.valueMm * 10).roundToInt() / 10.0).toString())
        }
    }
    pair(0, "ENDSEC"); pair(0, "EOF")
}
