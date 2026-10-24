package com.example.boschlaser

import com.example.boschlaser.shared.geometry.WallGeometry

// Compatibility boundary for the existing Android renderer and project format.
internal fun wallEndpointAngle(normalizedDotProduct: Float): Float =
    WallGeometry.endpointAngle(normalizedDotProduct.toDouble()).toFloat()

internal fun usesExtendedWallJoin(normalizedDotProduct: Float): Boolean =
    WallGeometry.usesExtendedJoin(normalizedDotProduct.toDouble())

internal fun wallControlLinePoints(wall: SketchWall): Pair<SketchPoint, SketchPoint> {
    val reference = WallGeometry.referenceLine(wall.toCoreWall())
    return reference.start.toSketchPoint() to reference.end.toSketchPoint()
}

internal fun wallFromControlLine(
    wall: SketchWall,
    controlStart: SketchPoint,
    controlEnd: SketchPoint,
    measuredLength: Float? = null,
): SketchWall = wall.toCoreWall().copy(
    start = controlStart.toVec2(), end = controlEnd.toVec2(), measuredLength = measuredLength?.toDouble(),
).toSketchWall()

internal fun wallCornerExtensionThroughTarget(
    corner: SketchPoint,
    outwardDirection: SketchPoint,
    target: SketchWall,
): Float? = WallGeometry.cornerExtensionThroughTarget(
    corner.toVec2(), outwardDirection.toVec2(), target.toCoreWall(),
)?.toFloat()

internal fun infiniteWallLineIntersection(first: SketchWall, second: SketchWall): SketchPoint? =
    WallGeometry.intersection(first.toCoreWall(), second.toCoreWall())?.toSketchPoint()
