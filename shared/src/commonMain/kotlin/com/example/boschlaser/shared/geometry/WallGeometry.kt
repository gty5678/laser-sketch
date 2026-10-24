package com.example.boschlaser.shared.geometry

import com.example.boschlaser.shared.model.Wall
import com.example.boschlaser.shared.model.WallAlignment
import com.example.boschlaser.shared.model.WallAnchor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos

data class WallShape(
    val referenceLine: Segment2,
    val centerLine: Segment2,
    val leftEdge: Segment2,
    val rightEdge: Segment2,
    val polygon: List<Vec2>,
)

object WallGeometry {
    fun referenceLine(wall: Wall) = Segment2(wall.start, wall.end)
    fun build(wall: Wall): WallShape {
        val reference = referenceLine(wall)
        val centerOffset = when (wall.alignment) {
            WallAlignment.CENTER -> 0.0
            WallAlignment.LEFT -> wall.thickness / 2.0
            WallAlignment.RIGHT -> -wall.thickness / 2.0
        }
        val center = Geometry.offset(reference, centerOffset)
        val left = Geometry.offset(center, wall.thickness / 2.0)
        val right = Geometry.offset(center, -wall.thickness / 2.0)
        return WallShape(reference, center, left, right, listOf(left.start, left.end, right.end, right.start))
    }
    fun setLength(wall: Wall, length: Double, anchor: WallAnchor): Wall {
        require(length.isFinite() && length > 10.0)
        val direction = Geometry.normalize(wall.end - wall.start)
        return when (anchor) {
            WallAnchor.START -> wall.copy(end = wall.start + direction * length, measuredLength = length)
            WallAnchor.END -> wall.copy(start = wall.end - direction * length, measuredLength = length)
        }
    }
    fun intersection(first: Wall, second: Wall) = Geometry.lineIntersection(
        Line2(first.start, first.end - first.start), Line2(second.start, second.end - second.start),
    )
    fun endpointAngle(normalizedDot: Double) = acos(normalizedDot.coerceIn(-1.0, 1.0))
    fun usesExtendedJoin(normalizedDot: Double) = endpointAngle(normalizedDot) + 1e-5 >= PI / 4.0

    /** Independent corner rays avoid the wedge gap in oblique T junctions. */
    fun cornerExtensionThroughTarget(corner: Vec2, outwardDirection: Vec2, target: Wall): Double? {
        if (Geometry.length(referenceLine(target)) < 1.0 ||
            Geometry.distance(outwardDirection, Vec2(0.0, 0.0)) <= Geometry.EPSILON) return null
        val direction = Geometry.normalize(outwardDirection)
        val targetDirection = Geometry.normalize(target.end - target.start)
        val normal = Vec2(-targetDirection.y, targetDirection.x)
        val across = normal.dot(direction)
        if (abs(across) < 1e-4) return null
        val shape = build(target)
        val distances = listOf(shape.leftEdge.start, shape.rightEdge.start).map {
            (it - corner).dot(normal) / across
        }
        return distances.filter { it.isFinite() && it > 0.0 }.maxOrNull()
    }
}
