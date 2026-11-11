package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.command.MoveWallCommand
import com.example.boschlaser.shared.command.MoveWallEndpointCommand
import com.example.boschlaser.shared.command.StraightenWallCommand
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall
import kotlin.test.*

class WallFreeEndpointTest {
    private val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(2000.0, 300.0))
    private fun editor() = SketchEditor(SketchDocument(walls = mapOf(wall.id to wall)))

    @Test fun lastCommittedEndpointBecomesFreeAndStraighteningKeepsOtherEndFixed() {
        val editor = editor()
        assertFalse(editor.wallFreeEndpointIsStart(wall.id))
        assertTrue(editor.execute(MoveWallEndpointCommand(wall.id, true, Vec2(100.0, 100.0))))
        assertTrue(editor.wallFreeEndpointIsStart(wall.id))
        assertTrue(editor.execute(StraightenWallCommand(wall.id, editor.wallFreeEndpointIsStart(wall.id))))
        val straight = editor.document.walls.getValue(wall.id)
        assertEquals(wall.end, straight.end)
        assertEquals(straight.end.y, straight.start.y, 1e-8)
        assertTrue(editor.execute(MoveWallEndpointCommand(wall.id, false, Vec2(2200.0, 500.0))))
        assertFalse(editor.wallFreeEndpointIsStart(wall.id))
        val fixed = editor.document.walls.getValue(wall.id).start
        assertTrue(editor.execute(StraightenWallCommand(wall.id, editor.wallFreeEndpointIsStart(wall.id))))
        assertEquals(fixed, editor.document.walls.getValue(wall.id).start)
    }

    @Test fun previewsOnlyUpdatePreferenceOnCommitAndTranslationPreservesIt() {
        val editor = editor()
        assertTrue(editor.preview(MoveWallEndpointCommand(wall.id, true, Vec2(100.0, 100.0))))
        assertFalse(editor.wallFreeEndpointIsStart(wall.id))
        editor.cancelEdit()
        assertFalse(editor.wallFreeEndpointIsStart(wall.id))
        assertTrue(editor.preview(MoveWallEndpointCommand(wall.id, true, Vec2(100.0, 100.0))))
        assertTrue(editor.commitEdit())
        assertTrue(editor.wallFreeEndpointIsStart(wall.id))
        assertTrue(editor.execute(MoveWallCommand(wall.id, Vec2(0.0, 200.0))))
        assertTrue(editor.wallFreeEndpointIsStart(wall.id))
        assertFalse(editor.execute(MoveWallEndpointCommand("missing", false, Vec2(0.0, 0.0))))
        assertTrue(editor.wallFreeEndpointIsStart(wall.id))
        editor.restore(SketchDocument(walls = mapOf(wall.id to wall)))
        assertFalse(editor.wallFreeEndpointIsStart(wall.id))
    }
}
