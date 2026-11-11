package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*

/** Board editing excludes the moving board's old outline, so a handle can escape its old position. */
fun SnapEngine.boardVertex(document: SketchDocument, input: Vec2, tolerance: Double, gridStep: Double?,
    excludedBoardId: String? = null, draftVertices: List<Vec2> = emptyList()): SnapResult {
    val base = snap(document, input, SnapContext(tolerance, gridStep, midpoints = true,
        intersections = true, objectEdges = true))
    // Prefer wall junctions to a sliding projection on either face. Use actual thickness,
    // including the corner extensions rendered by the shared wall network.
    val walls = document.walls.values
    val outlines = walls.map { WallNetworkGeometry.bodyPolygon(it, walls) }
    val junctions = WallNetworkGeometry.jointPolygons(walls).flatten().toMutableList()
    for (first in outlines.indices) for (second in first + 1 until outlines.size) {
        val a = outlines[first]
        val b = outlines[second]
        for (i in a.indices) for (j in b.indices) {
            Geometry.segmentIntersection(Segment2(a[i], a[(i + 1) % a.size]),
                Segment2(b[j], b[(j + 1) % b.size]))?.let(junctions::add)
        }
    }
    val junction = junctions.filter { Geometry.distance(input, it) <= tolerance }
        .minByOrNull { Geometry.distance(input, it) }
    val discreteTypes = listOf(SnapType.ENDPOINT, SnapType.MIDPOINT, SnapType.INTERSECTION, SnapType.QUADRANT)
    val wallSnap = if (junction != null && (base.type !in discreteTypes ||
            Geometry.distance(input, junction) <= Geometry.distance(input, base.point) + Geometry.EPSILON))
        SnapResult(junction, SnapType.INTERSECTION) else base
    val boards = document.boards.values.filter { it.id != excludedBoardId }
    val vertices = draftVertices.map { SnapResult(it, SnapType.ENDPOINT) } + boards.flatMap { board ->
        board.controls.map { SnapResult(it,SnapType.ENDPOINT,board.id) }
    }
    val vertex = vertices.filter { Geometry.distance(input,it.point) <= tolerance }
        .minByOrNull { Geometry.distance(input,it.point) }
    if (vertex != null && (wallSnap.type !in listOf(SnapType.ENDPOINT, SnapType.QUADRANT) ||
            Geometry.distance(input,vertex.point) < Geometry.distance(input,wallSnap.point))) return vertex
    if (wallSnap.type in listOf(SnapType.ENDPOINT,SnapType.MIDPOINT,SnapType.INTERSECTION,SnapType.QUADRANT)) return wallSnap
    val edges = boards.flatMap { board ->
        if (board.shape == BoardShape.CIRCLE) {
            val delta = input - board.center
            val length = Geometry.distance(input,board.center)
            if (length < Geometry.EPSILON) emptyList() else listOf(SnapResult(board.center + delta * (board.radius / length),SnapType.OBJECT_EDGE,board.id))
        } else board.vertices.indices.map { i ->
            SnapResult(Geometry.closestPoint(input,Segment2(board.vertices[i],board.vertices[(i+1)%board.vertices.size])),SnapType.OBJECT_EDGE,board.id)
        }
    }
    val edge = edges.filter { Geometry.distance(input,it.point) <= tolerance }.minByOrNull { Geometry.distance(input,it.point) }
    if (edge != null && (wallSnap.type in listOf(SnapType.NONE,SnapType.GRID) || Geometry.distance(input,edge.point) < Geometry.distance(input,wallSnap.point))) return edge
    return wallSnap
}
