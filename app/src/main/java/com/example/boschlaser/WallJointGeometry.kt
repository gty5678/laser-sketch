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

internal fun infiniteWallLineIntersection(first: SketchWall, second: SketchWall): SketchPoint? {
    val firstDx = (first.end.x - first.start.x).toDouble()
    val firstDy = (first.end.y - first.start.y).toDouble()
    val secondDx = (second.end.x - second.start.x).toDouble()
    val secondDy = (second.end.y - second.start.y).toDouble()
    val lengthProduct = hypot(firstDx, firstDy) * hypot(secondDx, secondDy)
    if (lengthProduct < 1.0) return null
    val denominator = firstDx * secondDy - firstDy * secondDx
    if (abs(denominator) <= lengthProduct * 0.00001) return null
    val offsetX = (second.start.x - first.start.x).toDouble()
    val offsetY = (second.start.y - first.start.y).toDouble()
    val firstParameter = (offsetX * secondDy - offsetY * secondDx) / denominator
    val x = first.start.x + firstParameter * firstDx
    val y = first.start.y + firstParameter * firstDy
    if (!x.isFinite() || !y.isFinite()) return null
    return SketchPoint(x.toFloat(), y.toFloat())
}
