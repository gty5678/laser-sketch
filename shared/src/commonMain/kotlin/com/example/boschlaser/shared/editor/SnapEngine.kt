package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.geometry.WallGeometry
import com.example.boschlaser.shared.model.Column
import com.example.boschlaser.shared.model.RectColumn
import com.example.boschlaser.shared.model.SketchDocument
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class SnapType { NONE, GRID, ENDPOINT, MIDPOINT, INTERSECTION, CONTROL_LINE, HORIZONTAL, VERTICAL, ANGLE, COLUMN_CENTER, WALL_FACE, OBJECT_EDGE, QUADRANT }
data class SnapResult(val point: Vec2, val type: SnapType, val targetId: String? = null)
data class SnapContext(
    val tolerance: Double,
    val gridStep: Double? = null,
    val origin: Vec2? = null,
    val endpoints: Boolean = true,
    val controlLines: Boolean = true,
    val midpoints: Boolean = false,
    val intersections: Boolean = false,
    val axes: Boolean = false,
    val angleStepDegrees: Double? = null,
    val objectEdges: Boolean = false,
) {
    init {
        require(tolerance.isFinite() && tolerance >= 0.0)
        require(gridStep == null || gridStep.isFinite() && gridStep > 0.0)
        require(angleStepDegrees == null || angleStepDegrees.isFinite() && angleStepDegrees in 1.0..180.0)
    }
}

/** Snap decisions use world coordinates; pixel tolerances are converted by the UI adapter. */
class SnapEngine {
    fun snap(document: SketchDocument, input: Vec2, context: SnapContext): SnapResult {
        if (context.objectEdges) PhysicalEdgeSnap.snap(document, input, context)?.let { return it }
        val lines = document.walls.values.map { it.id to WallGeometry.referenceLine(it) }
        if (context.endpoints) {
            val candidate = lines.flatMap { (id, line) -> listOf(id to line.start, id to line.end) }
                .minByOrNull { Geometry.distance(it.second, input) }
            if (candidate != null && Geometry.distance(candidate.second, input) <= context.tolerance)
                return SnapResult(candidate.second, SnapType.ENDPOINT, candidate.first)
        }
        val candidates = mutableListOf<SnapResult>()
        for ((id, line) in lines) {
            if (context.midpoints) candidates += SnapResult((line.start + line.end) * .5, SnapType.MIDPOINT, id)
        }
        if (context.intersections) {
            for (first in lines.indices) for (second in first + 1 until lines.size) {
                Geometry.segmentIntersection(lines[first].second, lines[second].second)?.let {
                    candidates += SnapResult(it, SnapType.INTERSECTION)
                }
            }
        }
        val nearest = candidates.filter { Geometry.distance(it.point, input) <= context.tolerance }
            .minByOrNull { Geometry.distance(it.point, input) }
        if (nearest != null) return nearest
        if (context.controlLines) {
            val projected = lines.map { (id, line) -> SnapResult(Geometry.closestPoint(input, line), SnapType.CONTROL_LINE, id) }
                .filter { Geometry.distance(it.point, input) <= context.tolerance }
                .minByOrNull { Geometry.distance(it.point, input) }
            if (projected != null) return projected
        }
        var result = context.gridStep?.let { SnapResult(grid(input, it), SnapType.GRID) }
            ?: SnapResult(input, SnapType.NONE)
        context.origin?.let { origin ->
            val axis = if (context.axes) axis(origin, result.point) else SnapResult(result.point, SnapType.NONE)
            if (axis.type != SnapType.NONE) result = axis
            else context.angleStepDegrees?.let { step ->
                val delta = result.point - origin
                val length = hypot(delta.x, delta.y)
                if (length >= 1.0) {
                    val radians = step * PI / 180.0
                    val angle = (atan2(delta.y, delta.x) / radians).roundToInt() * radians
                    result = SnapResult(origin + Vec2(cos(angle), sin(angle)) * length, SnapType.ANGLE)
                }
            }
        }
        return result
    }

    fun grid(point: Vec2, step: Double): Vec2 {
        require(step.isFinite() && step > 0.0)
        return Vec2(gridCoordinate(point.x, step), gridCoordinate(point.y, step))
    }

    private fun gridCoordinate(value: Double, step: Double): Double {
        val index = value / step
        if (!index.isFinite()) return value
        val lower = floor(index)
        // Match roundToInt's tie toward +infinity without narrowing or adding .5 to a large integer.
        val rounded = if (index - lower >= .5) lower + 1.0 else lower
        val result = rounded * step
        if (!result.isFinite()) return value
        return if (result == 0.0) 0.0 else result
    }

    fun axis(origin: Vec2, input: Vec2): SnapResult {
        val delta = input - origin
        val length = hypot(delta.x, delta.y)
        if (length < 1.0) return SnapResult(input, SnapType.NONE)
        return when {
            abs(delta.y) / length < .18 -> SnapResult(Vec2(input.x, origin.y), SnapType.HORIZONTAL)
            abs(delta.x) / length < .18 -> SnapResult(Vec2(origin.x, input.y), SnapType.VERTICAL)
            else -> SnapResult(input, SnapType.NONE)
        }
    }

    /** Rectangles can align both faces in a corner; otherwise choose the least displacement. */
    fun columnCenter(document: SketchDocument, column: Column, proposed: Vec2, tolerance: Double): SnapResult {
        require(tolerance.isFinite() && tolerance >= 0.0)
        var center: SnapResult? = null
        var edgeX: Double? = null
        var edgeY: Double? = null
        for (wall in document.walls.values) {
            val reference = WallGeometry.referenceLine(wall)
            val projected = Geometry.closestPoint(proposed, reference)
            val distance = Geometry.distance(proposed, projected)
            if (distance <= tolerance && (center == null || distance < Geometry.distance(proposed, center.point)))
                center = SnapResult(projected, SnapType.COLUMN_CENTER, wall.id)
            if (column !is RectColumn || Geometry.length(reference) < 1.0) continue
            val radians = column.rotationDegrees * PI / 180.0
            val halfX = abs(cos(radians)) * column.width / 2.0 + abs(sin(radians)) * column.depth / 2.0
            val halfY = abs(sin(radians)) * column.width / 2.0 + abs(cos(radians)) * column.depth / 2.0
            val body = WallGeometry.build(wall).centerLine
            val delta = body.end - body.start
            val length = Geometry.length(body)
            val halfWall = wall.thickness / 2.0
            if (abs(delta.y) / length < .02) {
                if (intervalGap(min(body.start.x, body.end.x), max(body.start.x, body.end.x), proposed.x - halfX, proposed.x + halfX) > tolerance) continue
                val wallY = (body.start.y + body.end.y) / 2.0
                for (face in listOf(wallY - halfWall, wallY + halfWall)) for (edge in listOf(proposed.y - halfY, proposed.y + halfY)) {
                    val adjustment = face - edge
                    if (abs(adjustment) <= tolerance && (edgeY == null || abs(adjustment) < abs(edgeY))) edgeY = adjustment
                }
            } else if (abs(delta.x) / length < .02) {
                if (intervalGap(min(body.start.y, body.end.y), max(body.start.y, body.end.y), proposed.y - halfY, proposed.y + halfY) > tolerance) continue
                val wallX = (body.start.x + body.end.x) / 2.0
                for (face in listOf(wallX - halfWall, wallX + halfWall)) for (edge in listOf(proposed.x - halfX, proposed.x + halfX)) {
                    val adjustment = face - edge
                    if (abs(adjustment) <= tolerance && (edgeX == null || abs(adjustment) < abs(edgeX))) edgeX = adjustment
                }
            }
        }
        if (edgeX != null && edgeY != null) return SnapResult(proposed + Vec2(edgeX, edgeY), SnapType.WALL_FACE)
        val edge = when {
            edgeX != null -> SnapResult(proposed + Vec2(edgeX, 0.0), SnapType.WALL_FACE)
            edgeY != null -> SnapResult(proposed + Vec2(0.0, edgeY), SnapType.WALL_FACE)
            else -> null
        }
        return listOfNotNull(center, edge).minByOrNull { Geometry.distance(it.point, proposed) }
            ?: SnapResult(proposed, SnapType.NONE)
    }

    private fun intervalGap(firstMin: Double, firstMax: Double, secondMin: Double, secondMax: Double) =
        max(0.0, max(firstMin, secondMin) - min(firstMax, secondMax))
}
