package com.example.boschlaser.shared.topology

import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.geometry.WallGeometry
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall

/** Lightweight endpoint contacts. An interior/interior geometric crossing creates no junction. */
class SketchTopology(private val document: SketchDocument, private val endpointTolerance: Double = 5.0) {
    init { require(endpointTolerance.isFinite() && endpointTolerance > 0.0) }
    fun endpointsAt(point: Vec2): Set<WallEndpointRef> = buildSet {
        document.walls.values.forEach { wall ->
            if (Geometry.distance(wall.start, point) < endpointTolerance) add(WallEndpointRef(wall.id, WallEndpoint.START))
            if (Geometry.distance(wall.end, point) < endpointTolerance) add(WallEndpointRef(wall.id, WallEndpoint.END))
        }
    }
    fun junctionAt(point: Vec2): WallJunction? {
        val refs = endpointsAt(point)
        if (refs.isEmpty()) return null
        val endpointWallIds = refs.mapTo(mutableSetOf()) { it.wallId }
        val hosts = document.walls.values.filter { wall ->
            val line = WallGeometry.referenceLine(wall)
            val parameter = Geometry.parameter(point, line)
            wall.id !in endpointWallIds && parameter > .0001 && parameter < .9999 &&
                Geometry.distanceToSegment(point, line) < 12.0
        }.mapTo(linkedSetOf()) { it.id }
        if (refs.size < 2 && hosts.isEmpty()) return null
        val id = refs.sortedWith(compareBy<WallEndpointRef> { it.wallId }.thenBy { it.endpoint.name })
            .joinToString("|") { "${it.wallId.length}:${it.wallId}:${it.endpoint.name}" } +
            hosts.sorted().joinToString(prefix = "#", separator = "|") { "${it.length}:$it" }
        return WallJunction(id, point, refs, hosts)
    }
}

