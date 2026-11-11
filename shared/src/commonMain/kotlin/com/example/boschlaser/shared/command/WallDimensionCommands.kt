package com.example.boschlaser.shared.command

import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.topology.EndpointMove
import com.example.boschlaser.shared.topology.WallEditPlanner
import kotlin.math.abs

data class AddWallDimensionCommand(val dimension: WallDimension) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        if (dimension.id in document.dimensions) return null
        val layout = WallDimensionGeometry.layout(document, dimension) ?: return null
        if (layout.valueMm < 1.0) return null
        return document.copy(dimensions = document.dimensions + (dimension.id to dimension))
    }
}

data class DeleteWallDimensionCommand(val id: String) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? =
        if (id !in document.dimensions) null else document.copy(dimensions = document.dimensions - id)
}

/** Temporary dimensions drive geometry in one command without becoming permanent annotations. */
data class DriveWallFaceDistanceCommand(val dimension: WallDimension, val valueMm: Double) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        var id = dimension.id
        while (id in document.dimensions) id += ":temporary"
        val temporary = dimension.copy(id = id)
        val withDimension = AddWallDimensionCommand(temporary).apply(document) ?: return null
        val result = DriveWallDimensionCommand(id, valueMm).apply(withDimension) ?: return null
        return result.copy(dimensions = document.dimensions)
    }
}

/** Move a face parallel to itself and intersect attached walls on their original directions. */
data class DriveWallDimensionCommand(val id: String, val valueMm: Double) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        val dimension = document.dimensions[id] ?: return null
        if (dimension.angular) return driveAngle(document, dimension, valueMm)
        if (!valueMm.isFinite() || valueMm !in 1.0..1_000_000.0) return null
        val layout = WallDimensionGeometry.layout(document, dimension) ?: return null
        if (layout.valueMm < 1e-6) return null
        if (abs(layout.valueMm - valueMm) < 1e-6) return document
        val delta = (layout.line.end - layout.line.start) * ((valueMm - layout.valueMm) / layout.valueMm)
        val lengthDimension = dimension.first.sameEntity(dimension.second)
        val result = (if (lengthDimension) resizeWall(document, dimension.first as WallFaceReference, valueMm)
        else when (val reference = dimension.second) {
            is WallFaceReference -> moveWall(document, document.walls[reference.wallId] ?: return null, delta)
            is ColumnLineReference -> {
                val column = document.columns[reference.columnId] ?: return null
                val moved = when (column) {
                    is RectColumn -> column.copy(center = column.center + delta)
                    is CircularColumn -> column.copy(center = column.center + delta)
                }
                UpdateColumnCommand(moved).apply(document)
            }
            is OpeningFrameReference -> {
                val opening = document.openings[reference.openingId] ?: return null
                val host = document.walls[opening.hostWallId] ?: return null
                val direction = Geometry.normalize(host.end - host.start)
                // Hosted frames have only one translation degree of freedom. Never silently clamp.
                if (abs(direction.cross(delta)) > .01) return null
                val offset = opening.offset + direction.dot(delta)
                if (offset < 0.0 || !offset.isFinite()) return null
                UpdateOpeningCommand(opening.copy(offset = offset)).apply(document)
            }
        }) ?: return null
        // A moving wall may carry a supposedly fixed hosted reference along with it.
        val before = WallDimensionGeometry.face(document, dimension.first) ?: return null
        val after = WallDimensionGeometry.face(result, dimension.first) ?: return null
        if (Geometry.distance(before.start, after.start) > .01 || Geometry.distance(before.end, after.end) > .01) return null
        val fixedWall = dimension.first as? WallFaceReference
        if (!lengthDimension && fixedWall != null && document.walls[fixedWall.wallId] != result.walls[fixedWall.wallId]) return null
        val measured = WallDimensionGeometry.layout(result, dimension)?.valueMm ?: return null
        return result.takeIf { abs(measured - valueMm) < .01 }
    }

    private fun driveAngle(document: SketchDocument, dimension: WallDimension, degrees: Double): SketchDocument? {
        if (!degrees.isFinite() || degrees <= 0.0 || degrees >= 180.0) return null
        val layout = WallDimensionGeometry.angularLayout(document, dimension) ?: return null
        if (abs(layout.valueMm - degrees) < 1e-6) return document
        val fixed = (dimension.first as? WallFaceReference)?.wallId ?: return null
        val target = document.walls[(dimension.second as? WallFaceReference)?.wallId] ?: return null
        val center = layout.arcCenter ?: return null
        val rotation = (if (layout.sweep < 0) -degrees else degrees) * kotlin.math.PI / 180.0 - layout.sweep
        fun rotate(point: Vec2): Vec2 {
            val p = point - center
            return center + Vec2(p.x * kotlin.math.cos(rotation) - p.y * kotlin.math.sin(rotation),
                p.x * kotlin.math.sin(rotation) + p.y * kotlin.math.cos(rotation))
        }
        var moved = target.copy(start = rotate(target.start), end = rotate(target.end), measuredLength = null)
        val rotatedDirection = moved.end - moved.start
        val moves = mutableListOf<EndpointMove>()
        // Slide each connected node along its neighbours' original control lines.
        // A free end retains its rotated position; a connected end may change wall length.
        for ((start, point) in listOf(true to target.start, false to target.end)) {
            val attachments = document.walls.values.filter { it.id != target.id &&
                Geometry.distanceToSegment(point, WallGeometry.referenceLine(it)) < 5.0 }
            val intersections = attachments.map { WallGeometry.intersection(moved, it) ?: return null }
            var joint = intersections.firstOrNull() ?: if (start) moved.start else moved.end
            if (intersections.any { Geometry.distance(it, joint) > .01 }) return null
            // Preserve an unchanged node exactly, avoiding spurious changes to the fixed wall.
            if (Geometry.distance(point, joint) < 1e-6) joint = point
            moved = if (start) moved.copy(start = joint) else moved.copy(end = joint)
            if (Geometry.distance(point, joint) >= 1e-6) moves += EndpointMove(target.id, start, joint)
        }
        if ((moved.end - moved.start).dot(rotatedDirection) <= 0.0) return null
        for (wall in document.walls.values) {
            if (wall.id == target.id) continue
            for ((start, point) in listOf(true to wall.start, false to wall.end)) {
                val t = Geometry.parameter(point, WallGeometry.referenceLine(target))
                if (t > .0001 && t < .9999 && Geometry.distanceToSegment(point, WallGeometry.referenceLine(target)) < 5.0) {
                    val joint = WallGeometry.intersection(wall, moved) ?: return null
                    if (Geometry.parameter(joint, WallGeometry.referenceLine(moved)) !in 0.0..1.0) return null
                    if (Geometry.distance(point, joint) >= 1e-6) moves += EndpointMove(wall.id, start, joint)
                }
            }
        }
        val changes = WallEditPlanner.endpointChanges(document, moves)
        if (changes[fixed]?.let { it != document.walls[fixed] } == true) return null
        for ((wallId, updated) in changes) {
            val old = document.walls.getValue(wallId)
            if (Geometry.length(WallGeometry.referenceLine(updated)) < 100.0) return null
            if (wallId != target.id && ((updated.end - updated.start).dot(old.end - old.start) <= 0.0 ||
                abs(Geometry.normalize(updated.end - updated.start).cross(Geometry.normalize(old.end - old.start))) > 1e-6)) return null
        }
        val result = UpdateWallsCommand(changes).apply(document) ?: return null
        val measured = WallDimensionGeometry.layout(result, dimension)?.valueMm ?: return null
        return result.takeIf { abs(measured - degrees) < .01 }
    }

    private fun resizeWall(document: SketchDocument, fixed: WallFaceReference, valueMm: Double): SketchDocument? {
        if (valueMm < 100.0) return null
        val original = document.walls[fixed.wallId] ?: return null
        val result = ChangeWallLengthCommand(fixed.wallId, valueMm,
            if (fixed.side == WallFaceSide.START) WallAnchor.START else WallAnchor.END).apply(document) ?: return null
        val resized = result.walls.getValue(fixed.wallId)
        for ((id, wall) in result.walls) {
            val before = document.walls.getValue(id)
            if (wall != before && (Geometry.length(WallGeometry.referenceLine(wall)) < 100.0 ||
                (wall.end - wall.start).dot(before.end - before.start) <= 0.0)) return null
            if (id == fixed.wallId) continue
            // Do not shorten a host past an interior T attachment and leave that endpoint detached.
            for (point in listOf(before.start, before.end)) {
                val parameter = Geometry.parameter(point, WallGeometry.referenceLine(original))
                if (parameter > .0001 && parameter < .9999 &&
                    Geometry.distanceToSegment(point, WallGeometry.referenceLine(original)) < 5.0 &&
                    Geometry.parameter(point, WallGeometry.referenceLine(resized)) !in 0.0..1.0) return null
            }
        }
        return result
    }

    private fun moveWall(document: SketchDocument, target: Wall, delta: Vec2): SketchDocument? {
        var moved = target.copy(start = target.start + delta, end = target.end + delta, measuredLength = null)
        val moves = mutableListOf<EndpointMove>()
        for ((start, point) in listOf(true to target.start, false to target.end)) {
            val attachments = document.walls.values.filter { wall -> wall.id != target.id &&
                Geometry.distanceToSegment(point, WallGeometry.referenceLine(wall)) < 5.0 }
            val intersections = attachments.map { WallGeometry.intersection(moved, it) ?: return null }
            val joint = intersections.firstOrNull() ?: (point + delta)
            if (intersections.any { Geometry.distance(it, joint) > .01 }) return null
            moved = if (start) moved.copy(start = joint) else moved.copy(end = joint)
            moves += EndpointMove(target.id, start, joint)
        }
        val originalDirection = target.end - target.start
        if ((moved.end - moved.start).dot(originalDirection) <= 0.0) return null
        // T stems on the moving host must follow even when the new intersection leaves the old span.
        for (wall in document.walls.values) {
            if (wall.id == target.id) continue
            for ((start, point) in listOf(true to wall.start, false to wall.end)) {
                val line = WallGeometry.referenceLine(target)
                val parameter = Geometry.parameter(point, line)
                if (parameter <= .0001 || parameter >= .9999 || Geometry.distanceToSegment(point, line) >= 5.0) continue
                val joint = WallGeometry.intersection(wall, moved) ?: return null
                if (Geometry.parameter(joint, WallGeometry.referenceLine(moved)) !in 0.0..1.0) return null
                moves += EndpointMove(wall.id, start, joint)
            }
        }
        val changes = WallEditPlanner.endpointChanges(document, moves)
        // Preserve every affected wall's direction, prevent inversion, and keep the reference fixed.
        for ((wallId, updated) in changes) {
            val old = document.walls.getValue(wallId)
            val before = old.end - old.start
            val after = updated.end - updated.start
            if (after.dot(before) <= 0.0 || Geometry.length(WallGeometry.referenceLine(updated)) < 100.0) return null
            if (abs(Geometry.normalize(before).cross(Geometry.normalize(after))) > 1e-6) return null
        }
        return UpdateWallsCommand(changes).apply(document)
    }
}
