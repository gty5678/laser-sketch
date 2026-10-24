package com.example.boschlaser.shared.geometry

import com.example.boschlaser.shared.model.SketchDocument

/** Legacy export framing follows wall centers and column centers with 800 mm breathing room. */
fun sketchContentBounds(document: SketchDocument): Rect2 {
    val points = document.walls.values.flatMap { wall ->
        val reference = WallGeometry.referenceLine(wall)
        val center = if (Geometry.length(reference) < 1.0) reference else WallGeometry.build(wall).centerLine
        listOf(center.start, center.end)
    } + document.columns.values.map { it.center } + document.dimensions.values.flatMap { dimension ->
        WallDimensionGeometry.layout(document, dimension)?.let { listOf(it.line.start, it.line.end) } ?: listOf(dimension.position)
    }
    if (points.isEmpty()) return Rect2(Vec2(-2500.0, -1800.0), Vec2(2500.0, 1800.0))
    return Rect2(Vec2(points.minOf { it.x } - 800.0, points.minOf { it.y } - 800.0),
        Vec2(points.maxOf { it.x } + 800.0, points.maxOf { it.y } + 800.0))
}
