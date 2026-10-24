package com.example.boschlaser.shared.geometry

import kotlin.math.abs
import kotlin.math.hypot

/** Document coordinates are millimeters. Screen coordinates belong to the viewport adapter. */
data class Vec2(val x: Double, val y: Double) {
    init { require(x.isFinite() && y.isFinite()) }
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(factor: Double) = Vec2(x * factor, y * factor)
    fun dot(other: Vec2) = x * other.x + y * other.y
    fun cross(other: Vec2) = x * other.y - y * other.x
}

data class Segment2(val start: Vec2, val end: Vec2)
data class Line2(val origin: Vec2, val direction: Vec2)
data class Rect2(val min: Vec2, val max: Vec2) {
    init { require(min.x <= max.x && min.y <= max.y) }
}

object Geometry {
    const val EPSILON = 1e-9
    fun distance(a: Vec2, b: Vec2) = hypot(a.x - b.x, a.y - b.y)
    fun length(segment: Segment2) = distance(segment.start, segment.end)
    fun normalize(vector: Vec2): Vec2 {
        val length = hypot(vector.x, vector.y)
        require(length > EPSILON)
        return vector * (1.0 / length)
    }
    fun parameter(point: Vec2, segment: Segment2): Double {
        val direction = segment.end - segment.start
        val squaredLength = direction.dot(direction)
        return if (squaredLength <= EPSILON * EPSILON) 0.0
        else (point - segment.start).dot(direction) / squaredLength
    }
    fun project(point: Vec2, line: Line2): Vec2 {
        val squaredLength = line.direction.dot(line.direction)
        require(squaredLength > EPSILON * EPSILON)
        return line.origin + line.direction * ((point - line.origin).dot(line.direction) / squaredLength)
    }
    fun closestPoint(point: Vec2, segment: Segment2) =
        segment.start + (segment.end - segment.start) * parameter(point, segment).coerceIn(0.0, 1.0)
    fun distanceToSegment(point: Vec2, segment: Segment2) = distance(point, closestPoint(point, segment))
    fun lineIntersection(a: Line2, b: Line2, angularTolerance: Double = 1e-5): Vec2? {
        require(angularTolerance.isFinite() && angularTolerance >= 0.0)
        val lengths = hypot(a.direction.x, a.direction.y) * hypot(b.direction.x, b.direction.y)
        val denominator = a.direction.cross(b.direction)
        if (lengths <= EPSILON || abs(denominator) <= lengths * angularTolerance) return null
        val parameter = (b.origin - a.origin).cross(b.direction) / denominator
        val x = a.origin.x + a.direction.x * parameter
        val y = a.origin.y + a.direction.y * parameter
        return if (x.isFinite() && y.isFinite()) Vec2(x, y) else null
    }
    /** Collinear overlap has no unique intersection and returns null. */
    fun segmentIntersection(a: Segment2, b: Segment2): Vec2? {
        val point = lineIntersection(Line2(a.start, a.end - a.start), Line2(b.start, b.end - b.start))
            ?: return null
        return point.takeIf {
            parameter(it, a) in -EPSILON..(1.0 + EPSILON) &&
                parameter(it, b) in -EPSILON..(1.0 + EPSILON)
        }
    }
    /** Positive distance offsets toward the left normal in document coordinates. */
    fun offset(segment: Segment2, distance: Double): Segment2 {
        require(distance.isFinite())
        val unit = normalize(segment.end - segment.start)
        val delta = Vec2(-unit.y, unit.x) * distance
        return Segment2(segment.start + delta, segment.end + delta)
    }
    fun convexHull(points: List<Vec2>): List<Vec2> {
        val sorted = points.distinct().sortedWith(compareBy<Vec2> { it.x }.thenBy { it.y })
        if (sorted.size <= 2) return sorted
        fun half(input: List<Vec2>): List<Vec2> {
            val result = mutableListOf<Vec2>()
            for (point in input) {
                while (result.size >= 2 &&
                    (result.last() - result[result.lastIndex - 1]).cross(point - result.last()) <= 0.0) {
                    result.removeAt(result.lastIndex)
                }
                result += point
            }
            return result.dropLast(1)
        }
        return half(sorted) + half(sorted.asReversed())
    }
}
