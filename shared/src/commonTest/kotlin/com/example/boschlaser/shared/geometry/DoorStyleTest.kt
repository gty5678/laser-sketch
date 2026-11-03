package com.example.boschlaser.shared.geometry

import com.example.boschlaser.shared.editor.*
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.storage.ProjectSerializer
import com.example.boschlaser.shared.tool.PointerContext
import kotlin.test.*

class DoorStyleTest {
    @Test fun slidingLeavesHaveOnlyASmallCentralLapAcrossWidthsAndRotations() {
        for (direction in listOf(Vec2(1.0, 0.0), Vec2(0.6, 0.8))) {
            val host = wall.copy(end = wall.start + direction * 6000.0)
            for (width in listOf(100.0, 900.0, 5000.0)) {
                val opening = Opening("sliding", host.id, 3000.0, width, OpeningType.DOOR,
                    doorStyle = DoorStyle.SLIDING)
                val segments = OpeningGeometry.symbolSegments(opening, host)
                val left = segments[2]
                val right = segments[3]
                val lap = left.end - right.start
                val expectedLap = kotlin.math.min(width * 0.05, 40.0)
                assertEquals(expectedLap, lap.x * direction.x + lap.y * direction.y, 1e-7)
                assertEquals((width + expectedLap) / 2.0, Geometry.length(left), 1e-7)
                assertEquals(Geometry.length(left), Geometry.length(right), 1e-7)
            }
        }
    }
    private val wall = Wall("host", Vec2(0.0, 0.0), Vec2(6000.0, 0.0))

    @Test fun placementKeepsEachStyleAndSupportsUndoSaveAndOldFiles() {
        val editor = SketchEditor(SketchDocument(walls = mapOf(wall.id to wall)))
        editor.setTool(ToolType.DOOR)
        for ((index, style) in DoorStyle.entries.withIndex()) {
            editor.placement = PlacementSettings(doorStyle = style)
            val point = Vec2(1000.0 + index * 1200.0, 0.0)
            val context = PointerContext(point, 24.0, 20.0, 10.0, 14.0, 20.0, 12.0)
            editor.pointerDown(point, context)
            editor.pointerUp(point, context)
            assertEquals(style, editor.document.openings.values.last().doorStyle)
        }
        val project = Project(ProjectManifest("styles", "Door styles", 0), editor.document)
        assertEquals(project, ProjectSerializer.decode(ProjectSerializer.encode(project)))
        assertTrue(editor.undo())
        assertEquals(3, editor.document.openings.size)
        assertTrue(editor.redo())
        assertEquals(project.sketch, editor.document)

        val single = project.copy(sketch = project.sketch.copy(openings = project.sketch.openings
            .filterValues { it.doorStyle == DoorStyle.SINGLE }))
        val files = ProjectSerializer.encode(single)
        assertFalse(files.getValue("sketch.json").contains("doorStyle"))
        assertEquals(single, ProjectSerializer.decode(files))
        assertFails { ProjectSerializer.decode(ProjectSerializer.encode(project).mapValues { (key, value) ->
            if (key == "sketch.json") value.replace("SLIDING", "UNKNOWN") else value
        }) }
    }

    @Test fun symbolsUseHalfWidthDoubleLeavesAndNoArcsForSlidingOrOpenings() {
        for (end in listOf(Vec2(6000.0, 0.0), Vec2(3600.0, 4800.0))) {
            val host = wall.copy(end = end)
            for (flip in listOf(false, true)) {
                val door = Opening("door", host.id, 3000.0, 1000.0, OpeningType.DOOR, flipped = flip)
                val single = OpeningGeometry.doorSwings(door, host).single()
                assertEquals(1000.0, single.radius, 1e-7)
                val double = OpeningGeometry.doorSwings(door.copy(doorStyle = DoorStyle.DOUBLE), host)
                assertEquals(2, double.size)
                double.forEach { assertEquals(500.0, it.radius, 1e-7) }
                assertEquals(1000.0, Geometry.distance(double[0].hinge, double[1].hinge), 1e-7)
                for (style in listOf(DoorStyle.SLIDING, DoorStyle.OPENING)) {
                    val styled = door.copy(doorStyle = style)
                    assertTrue(OpeningGeometry.doorSwings(styled, host).isEmpty())
                    assertEquals(if (style == DoorStyle.SLIDING) 4 else 2, OpeningGeometry.symbolSegments(styled, host).size)
                    assertEquals(OpeningGeometry.cutoutPolygon(door, host), OpeningGeometry.cutoutPolygon(styled, host))
                }
            }
        }
    }
}
