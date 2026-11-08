package com.example.boschlaser.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipPath
import com.example.boschlaser.shared.model.WallFill
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.boschlaser.shared.editor.Viewport
import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall
import kotlin.math.max
import kotlin.math.min

private fun combineDrawingPaths(current: Path, other: Path, operation: PathOperation): Path {
    val result = Path()
    return if (result.op(current, other, operation)) result else current
}

data class WallDrawingStyle(val fill: Color, val outline: Color, val outlineWidthPx: Float,
    val blankFill: Color = Color.White, val blackFill: Color = Color.Black)

internal fun polygonDrawingPath(points: List<Vec2>, viewport: Viewport): Path = Path().apply {
    points.forEachIndexed { index, point ->
        val screen = viewport.worldToScreen(point)
        if (index == 0) moveTo(screen.x.toFloat(), screen.y.toFloat()) else lineTo(screen.x.toFloat(), screen.y.toFloat())
    }
    if (points.isNotEmpty()) close()
}

/** Boolean paths are disposable drawing products; the semantic document is never changed. */
fun DrawScope.drawSketchWallNetwork(document: SketchDocument, viewport: Viewport, style: WallDrawingStyle) {
    drawSketchWallNetwork(document, viewport, style, null)
}

internal fun DrawScope.drawSketchWallNetwork(document: SketchDocument, viewport: Viewport, style: WallDrawingStyle,
    geometryCache: SketchWallGeometryCache?) {
    val paths = if (geometryCache == null) {
        buildSketchWallPaths(document, viewport, null)
    } else {
        geometryCache.paths.get(document, viewport.scale) {
            buildSketchWallPaths(document, Viewport(viewport.scale, Vec2(0.0, 0.0)), geometryCache)
        }.translated(viewport.offset)
    }
    paths.outline?.let { path ->
        clipPath(path) { paths.fills.forEach { (fill, region) -> drawWallFill(region, fill, style) } }
        drawPath(path, style.outline, style = Stroke(style.outlineWidthPx))
    }
}

internal data class SketchWallPaths(val outline: Path?, val fills: Map<WallFill, Path>) {
    fun translated(offset: Vec2): SketchWallPaths {
        if (offset == Vec2(0.0, 0.0)) return this
        val delta = Offset(offset.x.toFloat(), offset.y.toFloat())
        fun moved(path: Path) = Path().apply { addPath(path, delta) }
        return SketchWallPaths(outline?.let { moved(it) }, fills.mapValues { moved(it.value) })
    }
}

private fun DrawScope.buildSketchWallPaths(document: SketchDocument, viewport: Viewport,
    geometryCache: SketchWallGeometryCache?): SketchWallPaths {
    val walls = document.walls.values
    var union: Path? = null
    val regions = mutableMapOf<String, Path>()
    fun add(polygon: List<Vec2>, wall: Wall) {
        if (polygon.isEmpty()) return
        val path = polygonDrawingPath(polygon, viewport)
        union = union?.let { combineDrawingPaths(it, path, PathOperation.Union) } ?: path
        regions[wall.id] = regions[wall.id]?.let { combineDrawingPaths(it, path, PathOperation.Union) } ?: path
    }
    val sortedWalls = walls.sortedWith(compareBy<Wall>({ min(it.start.x, it.end.x) }, { min(it.start.y, it.end.y) },
        { max(it.start.x, it.end.x) }, { max(it.start.y, it.end.y) }))
    val cached = geometryCache?.polygons(document.walls, 2.5 / viewport.scale)
    // The cache preserves document order; union still uses the established sorted order.
    val bodies = if (cached != null) walls.mapIndexed { index, wall -> wall.id to cached[index] }.toMap() else null
    sortedWalls.forEach { wall ->
        if (Geometry.length(WallGeometry.referenceLine(wall)) * viewport.scale >= 1.0) {
            add(bodies?.getValue(wall.id) ?: WallNetworkGeometry.bodyPolygon(wall, walls, 2.5 / viewport.scale), wall)
        }
    }
    (cached?.drop(walls.size) ?: WallNetworkGeometry.jointPolygons(walls, 2.5 / viewport.scale)).forEach { polygon ->
        val center = polygon.reduce { a, b -> a + b } * (1.0 / polygon.size)
        val owner = walls.minByOrNull { Geometry.distanceToSegment(center, WallGeometry.referenceLine(it)) }
        if (owner != null) {
            add(polygon, owner)
            // Both walls must reach the seam, including the outer miter supplement.
            walls.filter { other -> other.id != owner.id && wallFillSeam(owner, other) != null }
                .filter { other -> listOf(other.start, other.end).any { endpoint ->
                    Geometry.distance(endpoint, center) <= polygon.maxOf { Geometry.distance(it, center) } + 12.0
                } }.forEach { add(polygon, it) }
        }
    }
    // Resolve ownership before grouping by material; a third wall with the same
    // material must never change which wall paints an existing intersection.
    for (i in sortedWalls.indices) for (j in i + 1 until sortedWalls.size) {
        val first = sortedWalls[i]
        val second = sortedWalls[j]
        if (first.fill == second.fill) continue
        val a = regions[first.id] ?: continue
        val b = regions[second.id] ?: continue
        val overlap = combineDrawingPaths(a, b, PathOperation.Intersect)
        val seam = wallFillSeam(first, second)
        if (seam != null) {
            val bounds = overlap.getBounds()
            if (bounds.isEmpty) continue
            val screen = viewport.worldToScreen(seam.origin)
            val radius = (max(size.width, size.height) + max(bounds.width, bounds.height) +
                kotlin.math.abs(screen.x) + kotlin.math.abs(screen.y)).toDouble() * 4.0 / viewport.scale
            val n = seam.normal
            val t = Vec2(-n.y, n.x)
            val halfPlane = polygonDrawingPath(listOf(seam.origin + t * radius,
                seam.origin - t * radius, seam.origin - t * radius + n * radius,
                seam.origin + t * radius + n * radius), viewport)
            val firstPart = combineDrawingPaths(overlap, halfPlane, PathOperation.Intersect)
            regions[first.id] = combineDrawingPaths(a,
                combineDrawingPaths(overlap, firstPart, PathOperation.Difference), PathOperation.Difference)
            regions[second.id] = combineDrawingPaths(b, firstPart, PathOperation.Difference)
        } else {
            // At a T, keep the through wall continuous; at a crossing use a stable id.
            val firstIsBranch = listOf(first.start, first.end).any {
                Geometry.distanceToSegment(it, WallGeometry.referenceLine(second)) < 12.0
            }
            val secondIsBranch = listOf(second.start, second.end).any {
                Geometry.distanceToSegment(it, WallGeometry.referenceLine(first)) < 12.0
            }
            if (firstIsBranch || (!secondIsBranch && first.id > second.id)) {
                regions[first.id] = combineDrawingPaths(a, b, PathOperation.Difference)
            } else {
                regions[second.id] = combineDrawingPaths(b, a, PathOperation.Difference)
            }
        }
    }
    val fills = mutableMapOf<WallFill, Path>()
    sortedWalls.forEach { wall -> regions[wall.id]?.let { region ->
        fills[wall.fill] = fills[wall.fill]?.let { combineDrawingPaths(it, region, PathOperation.Union) } ?: region
    } }
    document.openings.values.forEach { opening ->
        val host = document.walls[opening.hostWallId] ?: return@forEach
        if (Geometry.length(WallGeometry.referenceLine(host)) < 1.0) return@forEach
        val cutout = polygonDrawingPath(OpeningGeometry.cutoutPolygon(opening, host,
            minimumHalfWidth = 2.5 / viewport.scale, margin = 3.0 / viewport.scale), viewport)
        union = union?.let { combineDrawingPaths(it, cutout, PathOperation.Difference) }
    }
    return SketchWallPaths(union, fills)
}

fun DrawScope.drawSketchWallBody(wall: Wall, neighbors: Collection<Wall>, viewport: Viewport, style: WallDrawingStyle) {
    if (Geometry.length(WallGeometry.referenceLine(wall)) * viewport.scale < 1.0) return
    val polygon = WallNetworkGeometry.bodyPolygon(wall, neighbors, 2.5 / viewport.scale)
    if (polygon.isEmpty()) return
    val path = polygonDrawingPath(polygon, viewport)
    drawWallFill(path, wall.fill, style)
    drawPath(path, style.outline, style = Stroke(style.outlineWidthPx))
}

/** Pattern is clipped again by the opening-free network for screen, magnifier and export. */
private fun DrawScope.drawWallFill(path: Path, fill: WallFill, style: WallDrawingStyle) {
    when (fill) {
        WallFill.BLANK -> drawPath(path, style.blankFill)
        WallFill.BLACK -> drawPath(path, style.blackFill)
        WallFill.HATCH -> {
            drawPath(path, style.blankFill)
            clipPath(path) {
                val bounds = path.getBounds()
                val spacing = max(8f, style.outlineWidthPx * 6f)
                val canvas = androidx.compose.ui.geometry.Rect(Offset.Zero, size)
                wallHatchLines(bounds, canvas, spacing, style.outlineWidthPx).forEach { line ->
                    drawLine(style.outline, line.start, line.end, style.outlineWidthPx)
                }
            }
        }
    }
}
