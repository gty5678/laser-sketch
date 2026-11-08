package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.tool.PointerContext
import kotlin.test.*

class OpeningMagnifierTest {
    private fun context(point: Vec2) = PointerContext(point, 10.0, 10.0, 10.0, 10.0, 10.0, 0.0)

    @Test fun openingMagnifierTracksAcceptedCenterAndClearsAfterReleaseOrCancel() {
        for (type in OpeningType.entries) for (alignment in WallAlignment.entries) {
            val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(3000.0, 0.0), alignment = alignment)
            val opening = Opening("opening", wall.id, 1000.0, 600.0, type)
            val initial = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(opening.id to opening))
            val centerLine = WallGeometry.build(wall).centerLine
            val segment = OpeningGeometry.symbolSegments(opening, wall).first()
            val start = (segment.start + segment.end) * 0.5
            for (cancel in listOf(false, true)) {
                val editor = SketchEditor(initial)
                editor.pointerDown(start, context(start))
                assertEquals(SelectionGestureTarget.Opening(opening.id), editor.state.selectionGesture?.target)
                assertNull(editor.state.magnifierTarget)
                editor.pointerMove(start + Vec2(1.0, 0.0), context(start + Vec2(1.0, 0.0)))
                assertNull(editor.state.magnifierTarget)
                for ((pointer, offset) in listOf(Vec2(1600.0, 300.0) to 1600.0, Vec2(4000.0, 500.0) to 2700.0)) {
                    editor.pointerMove(pointer, context(pointer))
                    assertEquals(offset, editor.renderDocument.openings.getValue(opening.id).offset)
                    assertEquals(centerLine.start + Vec2(offset, 0.0), editor.state.magnifierTarget)
                    assertEquals(initial, editor.document)
                }
                if (cancel) editor.cancelPointer() else editor.pointerUp(Vec2(4000.0, 500.0), context(Vec2(4000.0, 500.0)))
                assertNull(editor.state.magnifierTarget)
                assertNull(editor.state.selectionGesture)
                assertFalse(editor.state.dragging)
                if (cancel) { assertEquals(initial, editor.renderDocument); assertFalse(editor.canUndo) }
                else { assertTrue(editor.undo()); assertEquals(initial, editor.document) }
            }
        }
    }
}
