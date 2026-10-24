package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.command.DeleteWallCommand
import com.example.boschlaser.shared.command.WallEditStatus
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import kotlin.test.*

class WallEditSessionTest {
    private val horizontal = Wall("horizontal", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
    private val vertical = Wall("vertical", Vec2(1000.0, -1000.0), Vec2(1000.0, 1000.0))
    private fun document(vararg walls: Wall) = SketchDocument(walls = walls.associateBy { it.id })

    @Test fun trimKeepsExactFirstPickAndCommitsBothWallsAsOneHistoryEntry() {
        val original = document(horizontal, vertical)
        val editor = SketchEditor(original)
        val pick = Vec2(1900.123456789, 0.000000123)
        editor.startWallEdit(WallEditOperation.TRIM)
        assertNull(editor.pickWallEdit(horizontal.id, pick)!!.status)
        assertEquals(WallEditPick(horizontal.id, pick), editor.state.wallEditSession?.first)
        assertEquals(original, editor.document)
        assertFalse(editor.canUndo)
        val result = editor.pickWallEdit(vertical.id, Vec2(1000.0, -500.0))!!
        assertEquals(WallEditStatus.SUCCESS, result.status)
        assertTrue(result.documentChanged)
        assertNull(editor.state.wallEditSession)
        assertEquals(Vec2(1000.0, 0.0), editor.document.walls.getValue(horizontal.id).start)
        assertEquals(Vec2(1000.0, 0.0), editor.document.walls.getValue(vertical.id).end)
        assertEquals(Selection.Wall(vertical.id), editor.state.selection)
        val approved = editor.document
        assertTrue(editor.undo())
        assertEquals(original, editor.document)
        assertFalse(editor.undo())
        assertTrue(editor.redo())
        assertEquals(approved, editor.document)
    }

    @Test fun extendKeepsBoundaryAndSelectsFirstWall() {
        val editor = SketchEditor(document(horizontal, vertical))
        editor.startWallEdit(WallEditOperation.EXTEND)
        editor.pickWallEdit(horizontal.id, Vec2(1900.0, 0.0))
        val update = editor.pickWallEdit(vertical.id, Vec2(1000.0, 500.0))!!
        assertEquals(WallEditStatus.SUCCESS, update.status)
        assertEquals(vertical, editor.document.walls.getValue(vertical.id))
        assertEquals(Vec2(1000.0, 0.0), editor.document.walls.getValue(horizontal.id).start)
        assertEquals(Selection.Wall(horizontal.id), editor.state.selection)
    }

    @Test fun parallelFailureResetsPairWithoutDocumentOrHistoryChanges() {
        val parallel = horizontal.copy(id = "parallel", start = Vec2(0.0, 500.0), end = Vec2(2000.0, 500.0))
        val original = document(horizontal, parallel)
        val editor = SketchEditor(original)
        for (operation in WallEditOperation.entries) {
            editor.startWallEdit(operation)
            editor.pickWallEdit(horizontal.id, Vec2(500.0, 0.0))
            assertEquals(WallEditStatus.PARALLEL, editor.pickWallEdit(parallel.id, Vec2(500.0, 500.0))!!.status)
            assertEquals(WallEditSession(operation), editor.state.wallEditSession)
            assertNull(editor.state.selection)
            assertEquals(original, editor.document)
            assertFalse(editor.canUndo)
        }
    }

    @Test fun openingConflictCancelsWholePairAndAllowsFreshFirstPick() {
        val opening = Opening("opening", horizontal.id, 1600.0, 400.0, OpeningType.DOOR)
        val original = document(horizontal, vertical).copy(openings = mapOf(opening.id to opening))
        val editor = SketchEditor(original)
        editor.startWallEdit(WallEditOperation.TRIM)
        editor.pickWallEdit(horizontal.id, Vec2(100.0, 0.0))
        assertEquals(WallEditStatus.OPENING_CONFLICT,
            editor.pickWallEdit(vertical.id, Vec2(1000.0, 500.0))!!.status)
        assertEquals(original, editor.document)
        assertFalse(editor.canUndo)
        assertNull(editor.state.wallEditSession?.first)
        editor.pickWallEdit(horizontal.id, Vec2(1900.0, 0.0))
        assertEquals(horizontal.id, editor.state.wallEditSession?.first?.wallId)
    }

    @Test fun physicalPickUsesThicknessToleranceReverseDrawOrderAndExcludesFirst() {
        val overlay = horizontal.copy(id = "overlay")
        val editor = SketchEditor(document(horizontal, overlay))
        editor.startWallEdit(WallEditOperation.TRIM)
        assertNull(editor.pickWallEdit(Vec2(500.0, 141.0), 20.0))
        assertNull(editor.pickWallEdit(Vec2(500.0, 0.0), Double.NaN))
        assertNull(editor.pickWallEdit(Vec2(500.0, 0.0), -1.0))
        assertNotNull(editor.pickWallEdit(Vec2(500.0, 140.0), 20.0))
        assertEquals(overlay.id, editor.state.wallEditSession?.first?.wallId)
        assertEquals(horizontal.id, editor.pickWallForEdit(Vec2(500.0, 0.0), 20.0))
        val first = editor.state.wallEditSession?.first
        assertEquals(WallEditStatus.SAME_WALL, editor.pickWallEdit(overlay.id, Vec2(700.0, 0.0))!!.status)
        assertEquals(first, editor.state.wallEditSession?.first)
    }

    @Test fun nearerWallWinsOverLaterDrawnWallAndOutsideSegmentDoesNotHit() {
        val farther = horizontal.copy(id = "farther", start = Vec2(0.0, 100.0), end = Vec2(2000.0, 100.0))
        val editor = SketchEditor(document(horizontal, farther))
        editor.startWallEdit(WallEditOperation.EXTEND)
        editor.pickWallEdit(Vec2(500.0, 5.0), 20.0)
        assertEquals(horizontal.id, editor.state.wallEditSession?.first?.wallId)
        editor.startWallEdit(WallEditOperation.EXTEND)
        assertNull(editor.pickWallEdit(Vec2(-200.0, 0.0), 20.0))
    }

    @Test fun deletedFirstWallReturnsMissingAndToolChangeOrRestoreCancelsSession() {
        val editor = SketchEditor(document(horizontal, vertical))
        editor.startWallEdit(WallEditOperation.TRIM)
        editor.pickWallEdit(horizontal.id, Vec2(100.0, 0.0))
        editor.execute(DeleteWallCommand(horizontal.id))
        assertEquals(WallEditStatus.WALL_NOT_FOUND,
            editor.pickWallEdit(vertical.id, Vec2(1000.0, 500.0))!!.status)
        assertNull(editor.state.wallEditSession?.first)
        editor.setTool(ToolType.WALL)
        assertNull(editor.state.wallEditSession)
        assertNull(editor.pickWallEdit(vertical.id, vertical.start))
        editor.startWallEdit(WallEditOperation.EXTEND)
        editor.restore(document(horizontal))
        assertNull(editor.state.wallEditSession)
        assertFalse(editor.canUndo)
        editor.startWallEdit(WallEditOperation.TRIM)
        editor.stopWallEdit()
        assertNull(editor.state.wallEditSession)
    }

    @Test fun extendMissedAndAlreadyReachingBoundaryResetWithoutHistory() {
        val missed = vertical.copy(start = Vec2(1000.0, 500.0))
        for ((target, first, status) in listOf(
            Triple(missed, horizontal, WallEditStatus.TARGET_MISSED),
            Triple(vertical, horizontal.copy(end = Vec2(1000.0, 0.0)), WallEditStatus.ALREADY_REACHES),
        )) {
            val original = document(first, target)
            val editor = SketchEditor(original)
            editor.startWallEdit(WallEditOperation.EXTEND)
            editor.pickWallEdit(first.id, Vec2(100.0, 0.0))
            assertEquals(status, editor.pickWallEdit(target.id, target.start)!!.status)
            assertNull(editor.state.wallEditSession?.first)
            assertEquals(original, editor.document)
            assertFalse(editor.canUndo)
        }
    }
}
