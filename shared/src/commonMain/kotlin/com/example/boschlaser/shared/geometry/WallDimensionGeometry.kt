package com.example.boschlaser.shared.geometry

import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.editor.Selection
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class WallDimensionLayout(val line: Segment2, val firstWitness: Vec2, val secondWitness: Vec2) {
    val valueMm get() = Geometry.length(line)
}

data class DimensionEndpointSnap(val reference: DimensionReference, val point: Vec2, val dimensionId: String)

object WallDimensionGeometry {
    /** Match both entity type and ID, including projects that reuse IDs across entity collections. */
    fun matches(reference: DimensionReference, selection: Selection?): Boolean = when (reference) {
        is WallFaceReference -> selection == Selection.Wall(reference.wallId)
        is OpeningFrameReference -> selection == Selection.Opening(reference.openingId)
        is ColumnLineReference -> selection == Selection.Column(reference.columnId)
    }

    /** Preserve stored references; orient only the edit so that the preselected object moves. */
    fun forMovingSelection(dimension: WallDimension, selection: Selection?): WallDimension? = when {
        matches(dimension.second, selection) -> dimension
        matches(dimension.first, selection) -> dimension.copy(first = dimension.second, second = dimension.first)
        else -> null
    }

    fun forMovingWall(dimension: WallDimension, wallId: String): WallDimension? =
        forMovingSelection(dimension, Selection.Wall(wallId))

    /** Nearest overlapping parallel wall on each physical side; selected wall is always the moving end. */
    fun forSelectedWall(document: SketchDocument, wallId: String): List<WallDimension> {
        val wall = document.walls[wallId] ?: return emptyList()
        if (Geometry.length(WallGeometry.referenceLine(wall)) < 1.0) return emptyList()
        val center = WallGeometry.build(wall).centerLine
        val direction = Geometry.normalize(center.end - center.start)
        val normal = Vec2(-direction.y, direction.x)
        val length = Geometry.length(center)
        val candidates = document.walls.values.mapNotNull { other ->
            if (other.id == wallId || Geometry.length(WallGeometry.referenceLine(other)) < 1.0) return@mapNotNull null
            val otherCenter = WallGeometry.build(other).centerLine
            val otherDirection = Geometry.normalize(otherCenter.end - otherCenter.start)
            if (abs(direction.cross(otherDirection)) > 1e-6) return@mapNotNull null
            val separation = (otherCenter.start - center.start).dot(normal)
            val gap = abs(separation) - (wall.thickness + other.thickness) / 2.0
            if (gap < 1.0) return@mapNotNull null
            val projections = listOf(otherCenter.start, otherCenter.end).map { (it - center.start).dot(direction) }
            val low = maxOf(0.0, projections.min())
            val high = minOf(length, projections.max())
            if (high - low < 100.0) return@mapNotNull null
            val side = if (separation > 0.0) WallFaceSide.LEFT else WallFaceSide.RIGHT
            val reference = listOf(WallFaceSide.LEFT, WallFaceSide.RIGHT).map { WallFaceReference(other.id, it) }.minBy { ref ->
                abs((face(document, ref)!!.start - center.start).dot(normal))
            }
            val position = center.start + direction * ((low + high) / 2.0)
            val dimension = WallDimension("temporary:$wallId:${other.id}", reference, WallFaceReference(wallId, side), position)
            Triple(side, gap, dimension)
        }
        return listOf(WallFaceSide.LEFT, WallFaceSide.RIGHT).mapNotNull { side ->
            candidates.filter { it.first == side }.minWithOrNull(compareBy<Triple<WallFaceSide, Double, WallDimension>> { it.second }
                .thenBy { it.third.first.entityId })?.third
        }
    }

    fun face(document: SketchDocument, reference: DimensionReference): Segment2? {
        return when (reference) {
            is WallFaceReference -> {
                val wall = document.walls[reference.wallId] ?: return null
                if (Geometry.length(WallGeometry.referenceLine(wall)) < 1.0) return null
                val shape = WallGeometry.build(wall)
                when (reference.side) {
                    WallFaceSide.LEFT -> shape.leftEdge
                    WallFaceSide.RIGHT -> shape.rightEdge
                    WallFaceSide.CENTER -> shape.centerLine
                    WallFaceSide.START -> Segment2(shape.leftEdge.start, shape.rightEdge.start)
                    WallFaceSide.END -> Segment2(shape.leftEdge.end, shape.rightEdge.end)
                }
            }
            is OpeningFrameReference -> {
                val opening = document.openings[reference.openingId] ?: return null
                val wall = document.walls[opening.hostWallId] ?: return null
                if (Geometry.length(WallGeometry.referenceLine(wall)) < 1.0) return null
                val corners = OpeningGeometry.cutoutPolygon(opening, wall)
                when (reference.side) {
                    OpeningFrameSide.START -> Segment2(corners[0], corners[3])
                    OpeningFrameSide.END -> Segment2(corners[1], corners[2])
                    OpeningFrameSide.LEFT -> Segment2(corners[3], corners[2])
                    OpeningFrameSide.RIGHT -> Segment2(corners[0], corners[1])
                }
            }
            is ColumnLineReference -> {
                val column = document.columns[reference.columnId] ?: return null
                val angle = (column as? RectColumn)?.rotationDegrees?.times(PI / 180.0) ?: 0.0
                val x = Vec2(cos(angle), sin(angle))
                val y = Vec2(-x.y, x.x)
                val halfWidth = when (column) { is RectColumn -> column.width; is CircularColumn -> column.diameter } / 2.0
                val halfDepth = when (column) { is RectColumn -> column.depth; is CircularColumn -> column.diameter } / 2.0
                fun vertical(offset: Double) = Segment2(column.center + x * offset - y * halfDepth,
                    column.center + x * offset + y * halfDepth)
                fun horizontal(offset: Double) = Segment2(column.center + y * offset - x * halfWidth,
                    column.center + y * offset + x * halfWidth)
                when (reference.line) {
                    ColumnReferenceLine.LEFT -> vertical(-halfWidth)
                    ColumnReferenceLine.RIGHT -> vertical(halfWidth)
                    ColumnReferenceLine.TOP -> horizontal(-halfDepth)
                    ColumnReferenceLine.BOTTOM -> horizontal(halfDepth)
                    ColumnReferenceLine.CENTER_X -> horizontal(0.0)
                    ColumnReferenceLine.CENTER_Y -> vertical(0.0)
                }
            }
        }
    }

    fun layout(document: SketchDocument, dimension: WallDimension): WallDimensionLayout? {
        val first = face(document, dimension.first) ?: return null
        val second = face(document, dimension.second) ?: return null
        val direction = Geometry.normalize(first.end - first.start)
        val other = Geometry.normalize(second.end - second.start)
        if (abs(direction.cross(other)) > 1e-6) return null
        val start = Geometry.project(dimension.position, Line2(first.start, direction))
        val end = Geometry.project(start, Line2(second.start, other))
        return WallDimensionLayout(Segment2(start, end), Geometry.closestPoint(start, first), Geometry.closestPoint(end, second))
    }

    fun references(document: SketchDocument): List<DimensionReference> =
        document.openings.values.flatMap { opening -> OpeningFrameSide.entries.map { OpeningFrameReference(opening.id, it) } } +
            document.columns.values.flatMap { column -> ColumnReferenceLine.entries.map { ColumnLineReference(column.id, it) } } +
            document.walls.values.flatMap { wall ->
                // At an end section's midpoint, the center axis also ends at the same point.
                // Prefer the physical section there, while keeping long faces first at corners.
                listOf(WallFaceSide.LEFT, WallFaceSide.RIGHT, WallFaceSide.START, WallFaceSide.END, WallFaceSide.CENTER)
                    .map { WallFaceReference(wall.id, it) }
            }

    /** Capture the visible tick, even when its extension line lies far outside the object's edge. */
    fun snapEndpoint(document: SketchDocument, point: Vec2, tolerance: Double,
        first: DimensionReference? = null): DimensionEndpointSnap? {
        require(tolerance.isFinite() && tolerance >= 0.0)
        return document.dimensions.values.toList().asReversed().flatMap { dimension ->
            val layout = layout(document, dimension) ?: return@flatMap emptyList()
            if (layout.valueMm < 1.0) return@flatMap emptyList()
            listOf(DimensionEndpointSnap(dimension.first, layout.line.start, dimension.id),
                DimensionEndpointSnap(dimension.second, layout.line.end, dimension.id))
        }.filter { candidate ->
            Geometry.distance(point, candidate.point) <= tolerance && (first == null ||
                (first.canDimensionWith(candidate.reference) && layout(document,
                    WallDimension("endpoint-candidate", first, candidate.reference, candidate.point))?.valueMm?.let { it >= 1.0 } == true))
        }.minByOrNull { Geometry.distance(point, it.point) }
    }

    fun pickFace(document: SketchDocument, point: Vec2, tolerance: Double,
        first: DimensionReference? = null): DimensionReference? {
        val candidates = references(document).filter { reference -> first == null || (first.canDimensionWith(reference) &&
            face(document, first)?.let { a -> face(document, reference)?.let { b ->
                abs(Geometry.normalize(a.end - a.start).cross(Geometry.normalize(b.end - b.start))) <= 1e-6
            } } == true) }
            .mapNotNull { reference -> face(document, reference)?.let { reference to Geometry.distanceToSegment(point, it) } }
            .filter { it.second <= tolerance }
        val nearest = candidates.minOfOrNull { it.second } ?: return null
        // Subpixel differences are ties: screen/world rounding must not let an axis steal
        // an overlapping physical frame or end section. Preserve the reference priority.
        return candidates.first { it.second <= nearest + tolerance * .025 }.first
    }

    fun hit(document: SketchDocument, point: Vec2, tolerance: Double): WallDimension? =
        document.dimensions.values.toList().asReversed().firstOrNull { dimension ->
            layout(document, dimension)?.let { Geometry.distanceToSegment(point, it.line) <= tolerance }
                ?: (Geometry.distance(point, dimension.position) <= tolerance)
        }
}
