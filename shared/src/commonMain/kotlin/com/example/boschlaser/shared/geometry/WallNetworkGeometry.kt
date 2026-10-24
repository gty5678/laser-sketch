package com.example.boschlaser.shared.geometry

import com.example.boschlaser.shared.model.Wall
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

/** Derived polygons for the light wall network. No boolean kernel, screen units or drawing cache. */
object WallNetworkGeometry {
    fun bodyPolygon(wall: Wall, neighbors: Collection<Wall>, minimumHalfWidth: Double = 0.0): List<Vec2> {
        require(minimumHalfWidth.isFinite() && minimumHalfWidth >= 0.0)
        if (Geometry.length(WallGeometry.referenceLine(wall)) < 1.0) return emptyList()
        val center = WallGeometry.build(wall).centerLine
        val startFirst = center.start.x < center.end.x ||
            (abs(center.start.x - center.end.x) < .001 && center.start.y <= center.end.y)
        val start = if (startFirst) center.start else center.end
        val end = if (startFirst) center.end else center.start
        val unit = Geometry.normalize(end - start)
        val normal = Vec2(-unit.y, unit.x)
        val half = max(minimumHalfWidth, wall.thickness / 2.0)
        val startExtension = endpointExtensions(wall, start, unit * -1.0, normal, half, neighbors)
        val endExtension = endpointExtensions(wall, end, unit, normal, half, neighbors)
        return listOf(
            start + normal * half - unit * startExtension.first,
            end + normal * half + unit * endExtension.first,
            end - normal * half + unit * endExtension.second,
            start - normal * half - unit * startExtension.second,
        )
    }

    fun jointPolygons(walls: Collection<Wall>, minimumHalfWidth: Double = 0.0): List<List<Vec2>> {
        require(minimumHalfWidth.isFinite() && minimumHalfWidth >= 0.0)
        val all = walls.toList()
        val polygons = mutableListOf<List<Vec2>>()
        val endpoints = all.flatMap { wall ->
            if (Geometry.length(WallGeometry.referenceLine(wall)) < 1.0) emptyList()
            else {
                val center = WallGeometry.build(wall).centerLine
                listOf(NodeEnd(wall, wall.start, center.start, center.end),
                    NodeEnd(wall, wall.end, center.end, center.start))
            }
        }.sortedWith(compareBy<NodeEnd>({ it.reference.x }, { it.reference.y }, { it.wall.id }))
        val nodes = mutableListOf<MutableList<NodeEnd>>()
        endpoints.forEach { end ->
            val node = nodes.firstOrNull { Geometry.distance(it.first().reference, end.reference) < 12.0 }
            if (node == null) nodes += mutableListOf(end) else node += end
        }
        nodes.forEach { node ->
            if (node.size < 2) return@forEach
            val rays = node.sortedWith(compareBy<NodeEnd>({ it.angle }, { it.wall.id }))
            val pairs = if (rays.size == 2) listOf(rays[0] to rays[1]) else {
                // Include the wraparound sector: the back of a nearly straight run
                // still needs a corner, even when its angular gap exceeds 180 degrees.
                rays.indices.map { index ->
                    rays[index] to rays[(index + 1) % rays.size]
                }
            }
            pairs.forEach { (first, second) ->
                jointPolygon(first, second, minimumHalfWidth)?.let { polygon ->
                    // A two-wall hull includes both sides of the corner. At a multi-wall
                    // node the opposite side belongs to another sector, possibly with a
                    // different thickness or alignment. Do not fill across that sector.
                    val sectors = if (rays.size == 2) listOf(polygon) else {
                        val origin = node.first().reference
                        val firstUnit = Geometry.normalize(first.other - first.joint)
                        val secondUnit = Geometry.normalize(second.other - second.joint)
                        if (positiveAngle(second.angle - first.angle) <= PI) {
                            listOf(clipToHalfPlane(clipToHalfPlane(polygon, origin, firstUnit),
                                origin, secondUnit * -1.0))
                        } else {
                            // A reflex sector is the union, rather than intersection,
                            // of the two half-planes. Neither part crosses a third ray.
                            listOf(clipToHalfPlane(polygon, origin, firstUnit),
                                clipToHalfPlane(polygon, origin, secondUnit * -1.0))
                        }
                    }
                    sectors.forEach { sector ->
                        Geometry.convexHull(sector).takeIf { it.size >= 3 }?.let(polygons::add)
                    }
                }
            }
        }
        return polygons
    }

    private data class NodeEnd(val wall: Wall, val reference: Vec2, val joint: Vec2, val other: Vec2) {
        val angle: Double get() = atan2(other.y - joint.y, other.x - joint.x)
    }

    private fun positiveAngle(angle: Double): Double = if (angle <= 0.0) angle + 2.0 * PI else angle

    /** Keep the left half-plane of a directed line, including its boundary. */
    private fun clipToHalfPlane(polygon: List<Vec2>, origin: Vec2, direction: Vec2): List<Vec2> {
        if (polygon.isEmpty()) return polygon
        val result = mutableListOf<Vec2>()
        var previous = polygon.last()
        var previousDistance = direction.cross(previous - origin)
        for (point in polygon) {
            val distance = direction.cross(point - origin)
            if ((previousDistance >= 0.0) != (distance >= 0.0)) {
                result += previous + (point - previous) * (previousDistance / (previousDistance - distance))
            }
            if (distance >= 0.0) result += point
            previous = point
            previousDistance = distance
        }
        return result
    }

    private fun jointPolygon(first: NodeEnd, second: NodeEnd, minimumHalfWidth: Double): List<Vec2>? {
        val firstUnit = Geometry.normalize(first.other - first.joint)
        val secondUnit = Geometry.normalize(second.other - second.joint)
        val rawDot = firstUnit.dot(secondUnit).coerceIn(-1.0, 1.0)
        val dot = if (abs(rawDot) < .00001) 0.0 else rawDot
        if (WallGeometry.endpointAngle(dot) < PI / 180.0) return null
        val firstNormal = Vec2(-firstUnit.y, firstUnit.x)
        val secondNormal = Vec2(-secondUnit.y, secondUnit.x)
        val firstHalf = max(minimumHalfWidth, first.wall.thickness / 2.0)
        val secondHalf = max(minimumHalfWidth, second.wall.thickness / 2.0)
        // The incoming ray's left/right sides are reversed when forming a polyline.
        val firstLeft = first.joint - firstNormal * firstHalf
        val firstRight = first.joint + firstNormal * firstHalf
        val secondLeft = second.joint + secondNormal * secondHalf
        val secondRight = second.joint - secondNormal * secondHalf
        val points = mutableListOf(firstLeft, firstRight, secondLeft, secondRight)
        if (WallGeometry.usesExtendedJoin(dot) && abs(firstUnit.cross(secondUnit)) >= .001) {
            Geometry.lineIntersection(Line2(firstLeft, firstUnit), Line2(secondLeft, secondUnit), 0.0)?.let(points::add)
            Geometry.lineIntersection(Line2(firstRight, firstUnit), Line2(secondRight, secondUnit), 0.0)?.let(points::add)
        }
        val hull = Geometry.convexHull(points)
        return hull.takeIf { it.size >= 3 }
    }

    private fun endpointExtensions(wall: Wall, point: Vec2, outward: Vec2, normal: Vec2, half: Double,
        neighbors: Collection<Wall>): Pair<Double, Double> {
        val center = WallGeometry.build(wall).centerLine
        val atStart = Geometry.distance(point, center.start) <= Geometry.distance(point, center.end)
        val reference = if (atStart) wall.start else wall.end
        val leftCorner = point + normal * half
        val rightCorner = point - normal * half
        throughRunExtensions(wall, reference, leftCorner, rightCorner, outward, neighbors)?.let { return it }
        var left = 0.0
        var right = 0.0
        for (other in neighbors) {
            if (other.id == wall.id) continue
            val sharesEndpoint = Geometry.distance(reference, other.start) < 12.0 || Geometry.distance(reference, other.end) < 12.0
            if (!sharesEndpoint && Geometry.distanceToSegment(reference, WallGeometry.referenceLine(other)) < 12.0) {
                WallGeometry.cornerExtensionThroughTarget(leftCorner, outward, other)?.let { left = max(left, it) }
                WallGeometry.cornerExtensionThroughTarget(rightCorner, outward, other)?.let { right = max(right, it) }
            }
        }
        return left to right
    }

    /** A branch of a straight three-wall node ends on the run's far face, not a square cap.
     * Signed distances also retract a corner that already protrudes beyond that face. */
    private fun throughRunExtensions(wall: Wall, reference: Vec2, left: Vec2, right: Vec2,
        outward: Vec2, neighbors: Collection<Wall>): Pair<Double, Double>? {
        val hosts = neighbors.filter { other ->
            other.id != wall.id && Geometry.length(WallGeometry.referenceLine(other)) >= 1.0 &&
                (Geometry.distance(reference, other.start) < 12.0 || Geometry.distance(reference, other.end) < 12.0)
        }.map { other ->
            val atStart = Geometry.distance(reference, other.start) <= Geometry.distance(reference, other.end)
            val end = if (atStart) other.start else other.end
            val away = if (atStart) other.end else other.start
            NodeEnd(other, end, end, away)
        }
        if (hosts.size != 2) return null
        val directions = hosts.map { Geometry.normalize(it.other - it.joint) }
        if (directions[0].dot(directions[1]) > -1.0 + 1e-6) return null
        if (abs(directions[0].cross(outward)) < .001) return null
        fun extension(corner: Vec2): Double? {
            val intervals = hosts.mapIndexedNotNull { index, host ->
                val direction = directions[index]
                val hostNormal = Vec2(-direction.y, direction.x)
                val shape = WallGeometry.build(host.wall)
                val across = hostNormal.dot(outward)
                val distances = listOf(shape.leftEdge.start, shape.rightEdge.start).map {
                    (it - corner).dot(hostNormal) / across
                }
                var enter = distances.min()
                var exit = distances.max()
                val along = (corner - host.reference).dot(direction)
                val speed = outward.dot(direction)
                if (abs(speed) < 1e-9) {
                    if (along < -.001) return@mapIndexedNotNull null
                } else if (speed > 0.0) enter = max(enter, -along / speed)
                else exit = minOf(exit, -along / speed)
                (enter to exit).takeIf { enter <= exit + 1e-9 }
            }.sortedBy { it.first }
            if (intervals.isEmpty()) return null
            var exit = intervals.first().second
            for (interval in intervals.drop(1)) {
                // Stop at a thickness step rather than filling a gap between host bands.
                if (interval.first > exit + 1e-9) break
                exit = max(exit, interval.second)
            }
            return exit
        }
        val leftExtension = extension(left) ?: return null
        val rightExtension = extension(right) ?: return null
        val length = Geometry.length(WallGeometry.referenceLine(wall))
        if (minOf(leftExtension, rightExtension) <= -length + 1.0) return null
        return leftExtension to rightExtension
    }
}
