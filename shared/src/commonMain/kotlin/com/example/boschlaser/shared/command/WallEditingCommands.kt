package com.example.boschlaser.shared.command

import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.geometry.WallGeometry
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall
import com.example.boschlaser.shared.topology.EndpointMove
import com.example.boschlaser.shared.topology.SketchTopology
import com.example.boschlaser.shared.topology.WallEditPlanner
import kotlin.math.abs

enum class WallEditStatus { SUCCESS, SAME_WALL, WALL_NOT_FOUND, PARALLEL, ALREADY_REACHES, TARGET_MISSED, OPENING_CONFLICT, INVALID_LENGTH }
data class WallEditPlan(val status: WallEditStatus, val command: UpdateWallsCommand? = null)

/** Click positions identify retained halves, not merely the selected walls. */
data class TrimWallCommand(
    val firstWallId: String,
    val firstPick: Vec2,
    val secondWallId: String,
    val secondPick: Vec2,
) : SketchCommand {
    fun plan(document: SketchDocument): WallEditPlan {
        if (firstWallId == secondWallId) return WallEditPlan(WallEditStatus.SAME_WALL)
        val first = document.walls[firstWallId] ?: return WallEditPlan(WallEditStatus.WALL_NOT_FOUND)
        val second = document.walls[secondWallId] ?: return WallEditPlan(WallEditStatus.WALL_NOT_FOUND)
        val intersection = WallGeometry.intersection(first, second) ?: return WallEditPlan(WallEditStatus.PARALLEL)
        val changes = WallEditPlanner.endpointChanges(document, listOf(
            EndpointMove(first.id, movesStart(first, intersection, firstPick), intersection),
            EndpointMove(second.id, movesStart(second, intersection, secondPick), intersection),
        ))
        return validatedPlan(document, changes)
    }
    override fun apply(document: SketchDocument) = plan(document).command?.apply(document)
}

data class ExtendWallCommand(val wallId: String, val targetWallId: String, val retainedPick: Vec2? = null) : SketchCommand {
    fun plan(document: SketchDocument): WallEditPlan {
        if (wallId == targetWallId) return WallEditPlan(WallEditStatus.SAME_WALL)
        val wall = document.walls[wallId] ?: return WallEditPlan(WallEditStatus.WALL_NOT_FOUND)
        val target = document.walls[targetWallId] ?: return WallEditPlan(WallEditStatus.WALL_NOT_FOUND)
        val intersection = WallGeometry.intersection(wall, target) ?: return WallEditPlan(WallEditStatus.PARALLEL)
        val targetPosition = Geometry.parameter(intersection, WallGeometry.referenceLine(target))
        if (targetPosition !in -.0001..1.0001) return WallEditPlan(WallEditStatus.TARGET_MISSED)
        val position = Geometry.parameter(intersection, WallGeometry.referenceLine(wall))
        val moveStart = when {
            position < -.0001 -> true
            position > 1.0001 -> false
            position <= .0001 || position >= .9999 -> return WallEditPlan(WallEditStatus.ALREADY_REACHES)
            retainedPick != null -> movesStart(wall, intersection, retainedPick)
            else -> return WallEditPlan(WallEditStatus.ALREADY_REACHES)
        }
        val changes = WallEditPlanner.endpointChanges(document, listOf(EndpointMove(wall.id, moveStart, intersection)))
            .filterKeys { it != targetWallId }
        // The boundary wall is always unchanged, even if it shares an endpoint with the edited wall.
        return validatedPlan(document, changes)
    }
    override fun apply(document: SketchDocument) = plan(document).command?.apply(document)
}

data class MoveWallEndpointCommand(val wallId: String, val moveStart: Boolean, val point: Vec2, val detachMultiWay: Boolean = false) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        val wall = document.walls[wallId] ?: return null
        val old = if (moveStart) wall.start else wall.end
        val count = SketchTopology(document).endpointsAt(old).map { it.wallId }.distinct().size
        val changes = if (detachMultiWay && count >= 3) mapOf(wall.id to if (moveStart) wall.copy(start = point, measuredLength = null)
            else wall.copy(end = point, measuredLength = null))
        else WallEditPlanner.endpointChanges(document, listOf(EndpointMove(wall.id, moveStart, point)))
        return UpdateWallsCommand(changes).apply(document)
    }
}

/** Wall-body dragging is constrained to its normal. Attached T stems keep their opposite endpoint. */
data class MoveWallCommand(val wallId: String, val displacement: Vec2) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        val wall = document.walls[wallId] ?: return null
        if (Geometry.length(WallGeometry.referenceLine(wall)) < 1.0) return null
        val unit = Geometry.normalize(wall.end - wall.start)
        val normal = Vec2(-unit.y, unit.x)
        val delta = normal * displacement.dot(normal)
        val moved = wall.copy(start = wall.start + delta, end = wall.end + delta, measuredLength = null)
        val moves = listOf(EndpointMove(wall.id, true, moved.start), EndpointMove(wall.id, false, moved.end)) +
            WallEditPlanner.followerMoves(document, wall, moved)
        return UpdateWallsCommand(WallEditPlanner.endpointChanges(document, moves)).apply(document)
    }
}

data class StraightenWallCommand(val wallId: String, val moveStart: Boolean) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        val wall = document.walls[wallId] ?: return null
        val delta = wall.end - wall.start
        val length = Geometry.length(WallGeometry.referenceLine(wall))
        if (length < 1.0) return null
        val direction = if (abs(delta.x) >= abs(delta.y)) Vec2(if (delta.x >= 0.0) 1.0 else -1.0, 0.0)
        else Vec2(0.0, if (delta.y >= 0.0) 1.0 else -1.0)
        val point = if (moveStart) wall.end - direction * length else wall.start + direction * length
        return MoveWallEndpointCommand(wall.id, moveStart, point).apply(document)
    }
}

private fun movesStart(wall: Wall, intersection: Vec2, retained: Vec2): Boolean {
    val line = WallGeometry.referenceLine(wall)
    val position = Geometry.parameter(intersection, line)
    return when {
        position <= 0.0 -> true
        position >= 1.0 -> false
        else -> Geometry.parameter(retained, line) >= position
    }
}

private fun validatedPlan(document: SketchDocument, changes: Map<String, Wall>): WallEditPlan {
    val command = UpdateWallsCommand(changes)
    return if (command.apply(document) == null) WallEditPlan(WallEditStatus.OPENING_CONFLICT)
    else WallEditPlan(WallEditStatus.SUCCESS, command)
}
