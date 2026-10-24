package com.example.boschlaser.shared.command

import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.geometry.WallGeometry
import com.example.boschlaser.shared.model.Opening
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall
import com.example.boschlaser.shared.topology.EndpointMove
import com.example.boschlaser.shared.topology.WallEditPlanner
import com.example.boschlaser.shared.topology.WallEndpoint
import com.example.boschlaser.shared.topology.WallEndpointRef

/** Splitting never cuts through an opening. Each retained opening keeps its world position. */
data class SplitWallCommand(val wallId: String, val point: Vec2, val newWallId: String) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        val wall = document.walls[wallId] ?: return null
        if (newWallId.isBlank() || newWallId in document.walls) return null
        val line = WallGeometry.referenceLine(wall)
        if (Geometry.distanceToSegment(point, line) > .01) return null
        val parameter = Geometry.parameter(point, line)
        if (parameter <= 0.0 || parameter >= 1.0) return null
        val splitPoint = line.start + (line.end - line.start) * parameter
        val firstLength = Geometry.distance(line.start, splitPoint)
        val secondLength = Geometry.distance(splitPoint, line.end)
        if (firstLength < 100.0 || secondLength < 100.0) return null
        val first = wall.copy(end = splitPoint, measuredLength = null)
        val second = wall.copy(id = newWallId, start = splitPoint, measuredLength = null)
        val openings = document.openings.toMutableMap()
        for (opening in document.openings.values.filter { it.hostWallId == wallId }) {
            val before = opening.offset - opening.width / 2.0
            val after = opening.offset + opening.width / 2.0
            val retained: Opening = when {
                after <= firstLength -> opening
                before >= firstLength -> opening.copy(hostWallId = newWallId, offset = opening.offset - firstLength)
                else -> return null
            }
            openings[opening.id] = retained
        }
        val walls = linkedMapOf<String, Wall>()
        document.walls.forEach { (id, current) ->
            if (id == wallId) { walls[id] = first; walls[newWallId] = second }
            else walls[id] = current
        }
        return document.copy(walls = walls, openings = openings.toMap())
    }
}

/** Explicit endpoint merge; crossing lines alone never invokes this operation. */
data class JoinWallsCommand(val first: WallEndpointRef, val second: WallEndpointRef, val point: Vec2) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        if (first.wallId == second.wallId || first.wallId !in document.walls || second.wallId !in document.walls) return null
        val moves = listOf(EndpointMove(first.wallId, first.endpoint == WallEndpoint.START, point),
            EndpointMove(second.wallId, second.endpoint == WallEndpoint.START, point))
        return UpdateWallsCommand(WallEditPlanner.endpointChanges(document, moves)).apply(document)
    }
}
