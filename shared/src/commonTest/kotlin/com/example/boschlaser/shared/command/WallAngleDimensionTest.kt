package com.example.boschlaser.shared.command

import com.example.boschlaser.shared.editor.*
import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.storage.*
import kotlin.test.*

class WallAngleDimensionTest {
    private val first = Wall("a", Vec2(0.0, 0.0), Vec2(3000.0, 0.0), 200.0)
    private val second = Wall("b", Vec2(0.0, 0.0), Vec2(0.0, 2000.0), 200.0)
    private val mark = WallDimension("angle", WallFaceReference("a", WallFaceSide.CONTROL),
        WallFaceReference("b", WallFaceSide.CONTROL), Vec2(500.0, 500.0), angular = true)
    private fun document() = SketchDocument(walls = mapOf("a" to first, "b" to second), dimensions = mapOf(mark.id to mark))

    @Test fun controlLineCreationAndModeSwitchDoNotChangeGeometryUntilPlacement() {
        val original = document().copy(dimensions = emptyMap())
        val editor = SketchEditor(original)
        editor.setTool(ToolType.DIMENSION)
        editor.setAngularDimension(true)
        fun tap(point: Vec2) {
            val context = com.example.boschlaser.shared.tool.PointerContext(point, 20.0, 20.0, 20.0, 20.0, 20.0, 20.0)
            editor.pointerDown(point, context)
            editor.pointerUp(point, context)
        }
        tap(Vec2(1500.0, 0.0))
        assertEquals(mark.first, editor.state.dimensionFirst)
        tap(Vec2(0.0, 1500.0))
        assertTrue(assertIs<SketchPreview.Dimension>(editor.state.preview).dimension.angular)
        assertEquals(original, editor.document)
        tap(Vec2(500.0, 500.0))
        assertTrue(editor.document.dimensions.values.single().angular)
        assertTrue(editor.undo())
        assertEquals(original, editor.document)
        tap(Vec2(1500.0, 0.0))
        editor.setAngularDimension(false)
        assertNull(editor.state.dimensionFirst)
        assertNull(editor.state.preview)
        assertEquals(original, editor.document)
    }

    @Test fun selectedWallRotatesAndUndoRestoresWholeDocument() {
        for (wall in listOf("a", "b")) {
            val original = document()
            val oriented = assertNotNull(WallDimensionGeometry.forMovingWall(mark, wall))
            val editor = SketchEditor(original)
            assertTrue(editor.execute(DriveWallFaceDistanceCommand(oriented, 60.25)))
            assertEquals(60.25, WallDimensionGeometry.layout(editor.document, mark)!!.valueMm, .001)
            val fixed = if (wall == "a") "b" else "a"
            assertEquals(original.walls[fixed], editor.document.walls[fixed])
            assertEquals(Geometry.length(WallGeometry.referenceLine(original.walls.getValue(wall))),
                Geometry.length(WallGeometry.referenceLine(editor.document.walls.getValue(wall))), .001)
            assertEquals(original.dimensions, editor.document.dimensions)
            assertTrue(editor.undo())
            assertEquals(original, editor.document)
            assertTrue(editor.redo())
        }
    }

    @Test fun invalidAnglesAreAtomicAndStorageRoundTrips() {
        val original = document()
        for (value in listOf(0.0, 180.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(DriveWallDimensionCommand(mark.id, value).apply(original))
        }
        val project = Project(ProjectManifest("fixture", "Angle", 1L), original)
        assertEquals(project, ProjectSerializer.decode(ProjectSerializer.encode(project)))
        val parallel = original.copy(walls = original.walls + ("b" to second.copy(end = Vec2(2000.0, 0.0))))
        assertNull(WallDimensionGeometry.layout(parallel, mark))
    }

    @Test fun closedRoomCanChangeAngleWithoutRotatingNeighbours() {
        val base = first.copy(measuredLength = 3000.0)
        val right = second.copy(start = base.end, end = Vec2(3000.0, 2000.0))
        val top = Wall("top", right.end, Vec2(0.0, 2000.0), 200.0)
        val left = Wall("left", top.end, base.start, 200.0)
        val original = document().copy(walls = listOf(base, right, top, left).associateBy { it.id })
        for (angle in listOf(60.0, 120.0)) {
            val result = assertNotNull(DriveWallDimensionCommand(mark.id, angle).apply(original))
            assertEquals(base, result.walls[base.id])
            assertEquals(left, result.walls[left.id])
            assertEquals(result.walls.getValue(right.id).end, result.walls.getValue(top.id).start)
            assertEquals(2000.0, result.walls.getValue(top.id).start.y, .001)
            assertEquals(angle, WallDimensionGeometry.layout(result, mark)!!.valueMm, .001)
        }
        assertEquals(original, DriveWallDimensionCommand(mark.id, 90.0).apply(original))
    }

    @Test fun freeWallsAcceptAnglesNearBothLimitsAndGeometryConflictsAreAtomic() {
        for (angle in listOf(.5, 45.0, 179.5)) {
            val result = assertNotNull(DriveWallDimensionCommand(mark.id, angle).apply(document()))
            assertEquals(angle, WallDimensionGeometry.layout(result, mark)!!.valueMm, .001)
        }
        val follower = Wall("c", second.end, Vec2(3000.0, 2000.0), 200.0)
        val original = document().copy(walls = document().walls + (follower.id to follower))
        val editor = SketchEditor(original)
        // The intersection would reverse the connected wall.
        assertFalse(editor.execute(DriveWallDimensionCommand(mark.id, 30.0)))
        assertEquals(original, editor.document)
        assertFalse(editor.canUndo)
    }

    @Test fun attachedWallKeepsItsDirectionWhileAngleChanges() {
        val follower = Wall("c", Vec2(0.0, 2000.0), Vec2(3000.0, 2000.0), 200.0)
        val original = document().copy(walls = document().walls + (follower.id to follower))
        for (angle in listOf(45.0, 60.0, 120.0, 150.0)) {
            val editor = SketchEditor(original)
            assertTrue(editor.execute(DriveWallDimensionCommand(mark.id, angle)), "angle=$angle")
            assertEquals(angle, WallDimensionGeometry.layout(editor.document, mark)!!.valueMm, .001)
            assertEquals(first, editor.document.walls[first.id])
            val changedFollower = editor.document.walls.getValue(follower.id)
            assertEquals(follower.end, changedFollower.end)
            assertEquals(follower.start.y, changedFollower.start.y, .001)
            assertEquals(editor.document.walls.getValue(second.id).end, changedFollower.start)
            assertTrue(editor.undo())
            assertEquals(original, editor.document)
        }
    }
}
