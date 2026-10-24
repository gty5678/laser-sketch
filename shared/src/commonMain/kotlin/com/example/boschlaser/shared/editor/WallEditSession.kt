package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.command.ExtendWallCommand
import com.example.boschlaser.shared.command.TrimWallCommand
import com.example.boschlaser.shared.command.WallEditStatus
import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.Segment2
import com.example.boschlaser.shared.geometry.Vec2

enum class WallEditOperation { TRIM, EXTEND }
data class WallEditPick(val wallId: String, val point: Vec2)
data class WallEditSession(val operation: WallEditOperation, val first: WallEditPick? = null)
data class WallEditUpdate(
    val operation: WallEditOperation,
    val status: WallEditStatus? = null,
    val documentChanged: Boolean = false,
)

/** Nearest physical wall segment wins; ties retain the last drawn wall. */
internal fun SketchEditor.pickWallForEdit(point: Vec2, tolerance: Double): String? {
    if (!tolerance.isFinite() || tolerance < 0.0) return null
    val excluded = state.wallEditSession?.first?.wallId
    return renderDocument.walls.values.toList().asReversed().asSequence()
        .filter { it.id != excluded }
        .map { it to Geometry.distanceToSegment(point, Segment2(it.start, it.end)) }
        .filter { (wall, distance) -> distance <= tolerance + wall.thickness / 2.0 }
        .minByOrNull { it.second }?.first?.id
}

/** The first pick retains its Double coordinates until an atomic command succeeds or the pair resets. */
internal fun SketchEditor.applyWallEditPick(wallId: String, point: Vec2): WallEditUpdate? {
    val session = state.wallEditSession ?: return null
    fun reset(status: WallEditStatus): WallEditUpdate {
        updateState(state.copy(wallEditSession = WallEditSession(session.operation), selection = null))
        return WallEditUpdate(session.operation, status)
    }
    if (wallId !in document.walls) return reset(WallEditStatus.WALL_NOT_FOUND)
    val first = session.first
    if (first == null) {
        updateState(state.copy(wallEditSession = session.copy(first = WallEditPick(wallId, point)),
            selection = Selection.Wall(wallId)))
        return WallEditUpdate(session.operation)
    }
    if (first.wallId == wallId) return WallEditUpdate(session.operation, WallEditStatus.SAME_WALL)
    val plan = when (session.operation) {
        WallEditOperation.TRIM -> TrimWallCommand(first.wallId, first.point, wallId, point).plan(document)
        WallEditOperation.EXTEND -> ExtendWallCommand(first.wallId, wallId, first.point).plan(document)
    }
    if (plan.status != WallEditStatus.SUCCESS) return reset(plan.status)
    val command = plan.command ?: return reset(WallEditStatus.OPENING_CONFLICT)
    val changed = execute(command)
    // A valid pair may already meet at the intersection; it still ends the tool without adding history.
    updateState(state.copy(wallEditSession = null, selection = Selection.Wall(
        if (session.operation == WallEditOperation.TRIM) wallId else first.wallId)))
    return WallEditUpdate(session.operation, WallEditStatus.SUCCESS, changed)
}
