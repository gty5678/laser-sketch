package com.example.boschlaser

import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.hypot

private val minimumExtendedWallJoinAngle = Math.toRadians(45.0).toFloat()
private const val ANGLE_EPSILON = .00001f

internal fun wallEndpointAngle(normalizedDotProduct: Float): Float =
    acos(normalizedDotProduct.coerceIn(-1f, 1f))

internal fun usesExtendedWallJoin(normalizedDotProduct: Float): Boolean {
    val angle = wallEndpointAngle(normalizedDotProduct)
    return angle + ANGLE_EPSILON >= minimumExtendedWallJoinAngle
}

internal fun wallControlLinePoints(wall: SketchWall): Pair<SketchPoint, SketchPoint> {
    val dx = wall.end.x - wall.start.x
    val dy = wall.end.y - wall.start.y
    val length = hypot(dx, dy)
    if (length < 1f || wall.controlLine == SketchWallControlLine.CENTER) return wall.start to wall.end
    val side = if (wall.controlLine == SketchWallControlLine.INNER) -1f else 1f
    val offsetX = -dy / length * wall.thickness / 2f * side
    val offsetY = dx / length * wall.thickness / 2f * side
    return SketchPoint(wall.start.x + offsetX, wall.start.y + offsetY) to
        SketchPoint(wall.end.x + offsetX, wall.end.y + offsetY)
}

internal fun wallFromControlLine(
    wall: SketchWall,
    controlStart: SketchPoint,
    controlEnd: SketchPoint,
    measuredLength: Float? = null,
): SketchWall {
    val dx = controlEnd.x - controlStart.x
    val dy = controlEnd.y - controlStart.y
    val length = hypot(dx, dy)
    if (length < 1f || wall.controlLine == SketchWallControlLine.CENTER) {
        return wall.copy(start = controlStart, end = controlEnd, measuredLength = measuredLength)
    }
    val side = if (wall.controlLine == SketchWallControlLine.INNER) -1f else 1f
    val offsetX = -dy / length * wall.thickness / 2f * side
    val offsetY = dx / length * wall.thickness / 2f * side
    return wall.copy(
        start = SketchPoint(controlStart.x - offsetX, controlStart.y - offsetY),
        end = SketchPoint(controlEnd.x - offsetX, controlEnd.y - offsetY),
        measuredLength = measuredLength,
    )
}

/** Returns the distance from one wall-body corner to the far face of a target wall. */
internal fun wallCornerExtensionThroughTarget(
    corner: SketchPoint,
    outwardDirection: SketchPoint,
    target: SketchWall,
): Float? {
    val outwardX = outwardDirection.x
    val outwardY = outwardDirection.y
    val wallLength = hypot(outwardX, outwardY)
    if (wallLength < 1f) return null
    val wallUx = outwardX / wallLength
    val wallUy = outwardY / wallLength

    val targetDx = target.end.x - target.start.x
    val targetDy = target.end.y - target.start.y
    val targetLength = hypot(targetDx, targetDy)
    if (targetLength < 1f) return null
    val targetNx = -targetDy / targetLength
    val targetNy = targetDx / targetLength
    val directionAcrossTarget = targetNx * wallUx + targetNy * wallUy
    if (abs(directionAcrossTarget) < .0001f) return null

    var required = 0f
    val targetHalf = target.thickness / 2f
    val startAcrossTarget =
        (corner.x - target.start.x) * targetNx +
            (corner.y - target.start.y) * targetNy
    for (targetSide in floatArrayOf(-1f, 1f)) {
        val distanceAlongWall =
            (targetHalf * targetSide - startAcrossTarget) / directionAcrossTarget
        if (distanceAlongWall.isFinite() && distanceAlongWall > required) {
            required = distanceAlongWall
        }
    }
    return required.takeIf { it > 0f }
}

internal fun infiniteWallLineIntersection(first: SketchWall, second: SketchWall): SketchPoint? {
    val (firstStart, firstEnd) = wallControlLinePoints(first)
    val (secondStart, secondEnd) = wallControlLinePoints(second)
    val firstDx = (firstEnd.x - firstStart.x).toDouble()
    val firstDy = (firstEnd.y - firstStart.y).toDouble()
    val secondDx = (secondEnd.x - secondStart.x).toDouble()
    val secondDy = (secondEnd.y - secondStart.y).toDouble()
    val lengthProduct = hypot(firstDx, firstDy) * hypot(secondDx, secondDy)
    if (lengthProduct < 1.0) return null
    val denominator = firstDx * secondDy - firstDy * secondDx
    if (abs(denominator) <= lengthProduct * 0.00001) return null
    val offsetX = (secondStart.x - firstStart.x).toDouble()
    val offsetY = (secondStart.y - firstStart.y).toDouble()
    val firstParameter = (offsetX * secondDy - offsetY * secondDx) / denominator
    val x = firstStart.x + firstParameter * firstDx
    val y = firstStart.y + firstParameter * firstDy
    if (!x.isFinite() || !y.isFinite()) return null
    return SketchPoint(x.toFloat(), y.toFloat())
}
