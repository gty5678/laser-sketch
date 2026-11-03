package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.tool.PointerContext
import kotlin.test.*

class OpenDoorwayGestureTest {
    private fun context(point: Vec2) = PointerContext(point, 14.0, 14.0, 14.0, 14.0, 14.0, 0.0)

    @Test fun doorwayInteriorSelectsAndDragsAlongRotatedAndOffsetHosts() {
        for (alignment in WallAlignment.entries) for (direction in listOf(Vec2(1.0, 0.0), Vec2(0.6, 0.8))) {
            val wall = Wall("host", Vec2(100.0, 200.0), Vec2(100.0, 200.0) + direction * 4000.0,
                300.0, alignment)
            val opening = Opening("doorway", wall.id, 1800.0, 1000.0, OpeningType.DOOR,
                flipped = true, hingeFlipped = true, doorStyle = DoorStyle.OPENING)
            val initial = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(opening.id to opening))
            val editor = SketchEditor(initial)
            val center = OpeningGeometry.center(opening, wall)
            editor.pointerDown(center, context(center))
            assertEquals(SelectionGestureTarget.Opening(opening.id), editor.state.selectionGesture?.target)
            val end = center + direction * 400.0
            editor.pointerMove(end, context(end))
            assertEquals(2200.0, editor.renderDocument.openings.getValue(opening.id).offset, 1e-7)
            assertEquals(initial, editor.document)
            assertTrue(editor.pointerUp(end, context(end)).documentChanged)
            assertEquals(opening.copy(offset = editor.document.openings.getValue(opening.id).offset),
                editor.document.openings[opening.id])
            assertEquals(initial.walls, editor.document.walls)
            assertTrue(editor.undo())
            assertEquals(initial, editor.document)
            assertTrue(editor.redo())
            assertEquals(2200.0, editor.document.openings.getValue(opening.id).offset, 1e-7)

            val normal = Vec2(-direction.y, direction.x)
            val hit = HitTestEngine()
            assertTrue(hit.hitsOpening(opening, wall, center + normal * 150.0, 0.0))
            assertFalse(hit.hitsOpening(opening, wall, center + normal * 165.0, 14.0))
            assertFalse(hit.hitsOpening(opening, wall, center + direction * 515.0, 14.0))
        }
    }
}
