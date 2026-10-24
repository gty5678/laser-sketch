package com.example.boschlaser.shared.geometry

import com.example.boschlaser.shared.model.Opening
import com.example.boschlaser.shared.model.Wall
import com.example.boschlaser.shared.model.WallAnchor
import com.example.boschlaser.shared.model.OpeningType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min
import kotlin.math.max
import kotlin.math.atan2

/** Exact quarter-circle used by renderers; derived geometry is never persisted. */
data class DoorSwing(val hinge: Vec2, val leafEnd: Vec2, val radius: Double,
    val startDegrees: Double, val sweepDegrees: Double)

object OpeningGeometry {
    fun cutoutPolygon(opening: Opening, wall: Wall, minimumHalfWidth: Double = 0.0, margin: Double = 0.0): List<Vec2> {
        require(minimumHalfWidth.isFinite() && minimumHalfWidth >= 0.0 && margin.isFinite() && margin >= 0.0)
        val line = WallGeometry.build(wall).centerLine
        val length = Geometry.length(line)
        val unit = Geometry.normalize(line.end - line.start)
        val normal = Vec2(-unit.y, unit.x)
        val center = line.start + unit * constrainedOffset(opening.offset, length, opening.width)
        val half = min(opening.width / 2.0, length / 2.0)
        val thickness = max(minimumHalfWidth, wall.thickness / 2.0) + margin
        val start = center - unit * half
        val end = center + unit * half
        return listOf(start - normal * thickness, end - normal * thickness,
            end + normal * thickness, start + normal * thickness)
    }

    fun constrainedOffset(offset: Double, wallLength: Double, width: Double): Double {
        require(offset.isFinite() && wallLength.isFinite() && width.isFinite())
        val half = min(width / 2.0, wallLength / 2.0)
        return offset.coerceIn(half, wallLength - half)
    }

    /** Symbol polylines are derived for rendering and hit testing; never persisted. */
    fun symbolSegments(opening: Opening, wall: Wall, minimumHalfThickness: Double = 0.0): List<Segment2> {
        require(minimumHalfThickness.isFinite() && minimumHalfThickness >= 0.0)
        val line = WallGeometry.build(wall).centerLine
        val length = Geometry.length(line)
        val unit = Geometry.normalize(line.end - line.start)
        val normal = Vec2(-unit.y, unit.x)
        val center = line.start + unit * constrainedOffset(opening.offset, length, opening.width)
        val halfOpening = min(opening.width / 2.0, length / 2.0)
        val a = center - unit * halfOpening
        val b = center + unit * halfOpening
        val halfWall = max(minimumHalfThickness, wall.thickness / 2.0)
        fun offset(point: Vec2, distance: Double) = point + normal * distance
        val jambs = listOf(Segment2(offset(a, -halfWall), offset(a, halfWall)),
            Segment2(offset(b, -halfWall), offset(b, halfWall)))
        if (opening.type == OpeningType.WINDOW) {
            return jambs + listOf(-halfWall, halfWall, -halfWall / 3.0, halfWall / 3.0).map {
                Segment2(offset(a, it), offset(b, it))
            }
        }
        val side = if (opening.flipped) -1.0 else 1.0
        val hingeDirection = if (opening.hingeFlipped) -1.0 else 1.0
        val hinge = if (opening.hingeFlipped) b else a
        val closedEnd = if (opening.hingeFlipped) a else b
        val width = halfOpening * 2.0
        val segments = jambs.toMutableList()
        segments += Segment2(hinge, offset(hinge, width * side))
        var previous = closedEnd
        for (step in 1..12) {
            val angle = PI / 2.0 * step / 12.0
            val next = hinge + (unit * (hingeDirection * cos(angle)) + normal * (side * sin(angle))) * width
            segments += Segment2(previous, next)
            previous = next
        }
        return segments
    }

    fun doorSwing(opening: Opening, wall: Wall): DoorSwing? {
        if (opening.type != OpeningType.DOOR || Geometry.length(WallGeometry.referenceLine(wall)) < 1.0) return null
        val line = WallGeometry.build(wall).centerLine
        val length = Geometry.length(line)
        if (length < 1.0) return null
        val unit = Geometry.normalize(line.end - line.start)
        val normal = Vec2(-unit.y, unit.x)
        val center = line.start + unit * constrainedOffset(opening.offset, length, opening.width)
        val radius = min(opening.width, length)
        val direction = if (opening.hingeFlipped) -1.0 else 1.0
        val side = if (opening.flipped) -1.0 else 1.0
        val hinge = center - unit * (direction * radius / 2.0)
        return DoorSwing(hinge, hinge + normal * (radius * side), radius,
            atan2(unit.y * direction, unit.x * direction) * 180.0 / PI, 90.0 * side * direction)
    }

    fun fits(opening: Opening, wall: Wall): Boolean =
        opening.hostWallId == wall.id && opening.offset - opening.width / 2.0 >= -.01 &&
            opening.offset + opening.width / 2.0 <= Geometry.length(WallGeometry.referenceLine(wall)) + .01

    fun center(opening: Opening, wall: Wall): Vec2 {
        require(opening.hostWallId == wall.id)
        val line = WallGeometry.build(wall).centerLine
        return line.start + Geometry.normalize(line.end - line.start) * opening.offset
    }

    /** Preserve physical distance from the fixed endpoint; failure leaves the document untouched. */
    fun rehost(opening: Opening, old: Wall, updated: Wall, fixedAnchor: WallAnchor): Opening? {
        val offset = when (fixedAnchor) {
            WallAnchor.START -> opening.offset
            WallAnchor.END -> opening.offset + Geometry.length(WallGeometry.referenceLine(updated)) -
                Geometry.length(WallGeometry.referenceLine(old))
        }
        if (!offset.isFinite() || offset < 0.0) return null
        return opening.copy(offset = offset).takeIf { fits(it, updated) }
    }
}
