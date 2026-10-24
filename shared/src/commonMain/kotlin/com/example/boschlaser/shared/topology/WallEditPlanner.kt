package com.example.boschlaser.shared.topology

import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.geometry.WallGeometry
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall

data class EndpointMove(val wallId: String, val moveStart: Boolean, val point: Vec2, val measuredLength: Double? = null)

object WallEditPlanner {
    /** Explicitly edited walls take precedence; other coincident endpoints follow as one node. */
    fun endpointChanges(document: SketchDocument, moves: List<EndpointMove>): Map<String, Wall> {
        val explicit = moves.mapTo(mutableSetOf()) { it.wallId }
        val changes = linkedMapOf<String, Wall>()
        for (move in moves) {
            val original = document.walls[move.wallId] ?: continue
            val oldPoint = if (move.moveStart) original.start else original.end
            val current = changes[move.wallId] ?: original
            changes[move.wallId] = if (move.moveStart) current.copy(start = move.point, measuredLength = move.measuredLength)
            else current.copy(end = move.point, measuredLength = move.measuredLength)
            for (connected in document.walls.values) {
                if (connected.id in explicit) continue
                val updated = changes[connected.id] ?: connected
                when {
                    Geometry.distance(connected.start, oldPoint) < 5.0 -> changes[connected.id] = updated.copy(start = move.point, measuredLength = null)
                    Geometry.distance(connected.end, oldPoint) < 5.0 -> changes[connected.id] = updated.copy(end = move.point, measuredLength = null)
                }
            }
        }
        return changes
    }

    /** Interior endpoint attachment follows the moved host intersection without rotating its other end. */
    fun followerMoves(document: SketchDocument, oldHost: Wall, newHost: Wall): List<EndpointMove> {
        val moves = mutableListOf<EndpointMove>()
        val handled = mutableListOf<Vec2>()
        val oldLine = WallGeometry.referenceLine(oldHost)
        val newLine = WallGeometry.referenceLine(newHost)
        for (candidate in document.walls.values) {
            if (candidate.id == oldHost.id) continue
            for ((moveStart, point) in listOf(true to candidate.start, false to candidate.end)) {
                if (handled.any { Geometry.distance(it, point) < 5.0 }) continue
                if (Geometry.distanceToSegment(point, oldLine) >= 12.0) continue
                val position = Geometry.parameter(point, oldLine)
                if (position <= .0001 || position >= .9999) continue
                val intersection = WallGeometry.intersection(candidate, newHost) ?: continue
                if (Geometry.parameter(intersection, newLine) !in -.0001..1.0001) continue
                if (Geometry.distance(point, intersection) < .001) continue
                moves += EndpointMove(candidate.id, moveStart, intersection)
                handled += point
            }
        }
        return moves
    }
}
