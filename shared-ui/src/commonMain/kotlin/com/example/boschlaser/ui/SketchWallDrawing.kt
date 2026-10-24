package com.example.boschlaser.ui

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

data class WallDrawingStyle(val fill: Color, val outline: Color, val outlineWidthPx: Float)

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
    val walls = document.walls.values
    var union: Path? = null
    fun add(polygon: List<Vec2>) {
        if (polygon.isEmpty()) return
        val path = polygonDrawingPath(polygon, viewport)
        union = union?.let { combineDrawingPaths(it, path, PathOperation.Union) } ?: path
    }
    val sortedWalls = walls.sortedWith(compareBy<Wall>({ min(it.start.x, it.end.x) }, { min(it.start.y, it.end.y) },
        { max(it.start.x, it.end.x) }, { max(it.start.y, it.end.y) }))
    val cached = geometryCache?.polygons(document.walls, 2.5 / viewport.scale)
    // The cache preserves document order; union still uses the established sorted order.
    val bodies = if (cached != null) walls.mapIndexed { index, wall -> wall.id to cached[index] }.toMap() else null
    sortedWalls.forEach { wall ->
        if (Geometry.length(WallGeometry.referenceLine(wall)) * viewport.scale >= 1.0) {
            add(bodies?.getValue(wall.id) ?: WallNetworkGeometry.bodyPolygon(wall, walls, 2.5 / viewport.scale))
        }
    }
    (cached?.drop(walls.size) ?: WallNetworkGeometry.jointPolygons(walls, 2.5 / viewport.scale)).forEach(::add)
    document.openings.values.forEach { opening ->
        val host = document.walls[opening.hostWallId] ?: return@forEach
        if (Geometry.length(WallGeometry.referenceLine(host)) < 1.0) return@forEach
        val cutout = polygonDrawingPath(OpeningGeometry.cutoutPolygon(opening, host,
            minimumHalfWidth = 2.5 / viewport.scale, margin = 3.0 / viewport.scale), viewport)
        union = union?.let { combineDrawingPaths(it, cutout, PathOperation.Difference) }
    }
    union?.let { path ->
        drawPath(path, style.fill)
        drawPath(path, style.outline, style = Stroke(style.outlineWidthPx))
    }
}

fun DrawScope.drawSketchWallBody(wall: Wall, neighbors: Collection<Wall>, viewport: Viewport, style: WallDrawingStyle) {
    if (Geometry.length(WallGeometry.referenceLine(wall)) * viewport.scale < 1.0) return
    val polygon = WallNetworkGeometry.bodyPolygon(wall, neighbors, 2.5 / viewport.scale)
    if (polygon.isEmpty()) return
    val path = polygonDrawingPath(polygon, viewport)
    drawPath(path, style.fill)
    drawPath(path, style.outline, style = Stroke(style.outlineWidthPx))
}
