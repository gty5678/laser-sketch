package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.command.DeleteWallCommand
import com.example.boschlaser.shared.command.AddWallCommand
import com.example.boschlaser.shared.command.ChangeWallThicknessCommand
import com.example.boschlaser.shared.command.ChangeWallLengthCommand
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall
import com.example.boschlaser.shared.model.WallAnchor
import com.example.boschlaser.shared.model.RectColumn
import com.example.boschlaser.shared.model.Opening
import com.example.boschlaser.shared.model.OpeningType
import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SketchEditorTest {
    private val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
    @Test fun permanentDeletionAndPreviewCommitCannotLeaveADanglingSelection() {
        val opening = Opening("opening", wall.id, 500.0, 300.0, OpeningType.WINDOW)
        val document = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(opening.id to opening))
        val direct = SketchEditor(document)
        direct.select(Selection.Opening(opening.id))
        assertTrue(direct.execute(DeleteWallCommand(wall.id)))
        assertNull(direct.state.selection)
        assertTrue(direct.document.openings.isEmpty())
        assertTrue(direct.undo())
        assertEquals(document, direct.document)
        assertNull(direct.state.selection)
        val preview = SketchEditor(document)
        preview.select(Selection.Wall(wall.id))
        preview.beginEdit()
        assertTrue(preview.preview(DeleteWallCommand(wall.id)))
        assertEquals(document, preview.document)
        assertTrue(preview.commitEdit())
        assertNull(preview.state.selection)
        assertTrue(preview.document.walls.isEmpty())
    }

    @Test fun cancellingAPreviewClearsOnlyASelectionThatDoesNotExistInTheRestoredDocument() {
        val editor = SketchEditor(SketchDocument(walls = mapOf(wall.id to wall)))
        editor.select(Selection.Wall(wall.id))
        editor.beginEdit()
        assertTrue(editor.preview(ChangeWallThicknessCommand(wall.id, 400.0)))
        editor.cancelEdit()
        assertEquals(Selection.Wall(wall.id), editor.state.selection)
        assertEquals(wall, editor.document.walls.getValue(wall.id))
        editor.beginEdit()
        assertTrue(editor.preview(AddWallCommand(wall.copy(id = "temporary"))))
        editor.select(Selection.Wall("temporary"))
        editor.cancelEdit()
        assertNull(editor.state.selection)
        assertEquals(setOf(wall.id), editor.document.walls.keys)
        assertFalse(editor.canUndo)
    }

    @Test fun selectionIsTypedAndValidatedWithoutChangingTheDocumentOrHistory() {
        val column = RectColumn("column", Vec2(500.0, 500.0), 400.0, 400.0)
        val opening = Opening("opening", wall.id, 500.0, 300.0, OpeningType.WINDOW)
        val document = SketchDocument(walls = mapOf(wall.id to wall), columns = mapOf(column.id to column),
            openings = mapOf(opening.id to opening))
        val editor = SketchEditor(document)
        for (selection in listOf(Selection.Wall(wall.id), Selection.Column(column.id), Selection.Opening(opening.id))) {
            editor.select(selection)
            assertEquals(selection, editor.state.selection)
            assertEquals(document, editor.document)
            assertFalse(editor.canUndo)
        }
        for (selection in listOf(Selection.Wall(column.id), Selection.Column(opening.id), Selection.Opening("missing"))) {
            editor.select(selection)
            assertNull(editor.state.selection)
        }
        editor.select(Selection.Wall(wall.id))
        editor.setTool(ToolType.WALL)
        assertEquals(ToolType.WALL, editor.state.tool)
        assertNull(editor.state.selection)
        editor.select(Selection.Wall(wall.id))
        editor.restore(document)
        assertNull(editor.state.selection)
        assertEquals(document, editor.document)
        assertFalse(editor.canUndo)
    }

    @Test fun resumingAndCancellingAWallChainKeepsTheOriginalDoubleAnchor() {
        val editor = SketchEditor()
        val anchor = Vec2(1234.123456789, -987.987654321)
        editor.setTool(ToolType.WALL)
        editor.resumeWallChain(anchor)
        assertEquals(anchor, editor.state.wallAnchor)
        assertEquals(anchor, (editor.state.preview as SketchPreview.Wall).line.start)
        assertEquals(anchor, (editor.state.preview as SketchPreview.Wall).line.end)
        editor.cancelPointer()
        assertEquals(anchor, editor.state.wallAnchor)
        assertEquals(anchor, (editor.state.preview as SketchPreview.Wall).line.start)
        assertTrue(editor.document.walls.isEmpty())
        assertFalse(editor.canUndo)
        editor.setTool(ToolType.SELECT)
        assertNull(editor.state.wallAnchor)
        assertNull(editor.state.preview)
    }

    @Test
    fun previewDoesNotMutateDocumentAndCommitsOneUndoRecord() {
        val editor = SketchEditor()
        editor.beginEdit()
        editor.preview(AddWallCommand(wall))
        editor.preview(ChangeWallThicknessCommand(wall.id, 300.0))
        editor.preview(ChangeWallLengthCommand(wall.id, 3000.0, WallAnchor.START))
        assertTrue(editor.document.walls.isEmpty())
        assertEquals(300.0, editor.renderDocument.walls.getValue(wall.id).thickness)
        assertFalse(editor.canUndo)
        assertTrue(editor.commitEdit())
        assertTrue(editor.undo())
        assertTrue(editor.document.walls.isEmpty())
        assertFalse(editor.canUndo)
        assertTrue(editor.redo())
        assertEquals(Vec2(3000.0, 0.0), editor.document.walls.getValue(wall.id).end)
    }
    @Test
    fun cancelAndNoopDoNotCreateHistoryOrEraseRedo() {
        val editor = SketchEditor()
        editor.execute(AddWallCommand(wall))
        editor.undo()
        editor.beginEdit()
        editor.preview(AddWallCommand(wall.copy(id = "preview")))
        editor.cancelEdit()
        assertTrue(editor.document.walls.isEmpty())
        assertTrue(editor.canRedo)
        editor.beginEdit()
        assertFalse(editor.commitEdit())
        assertTrue(editor.canRedo)
    }
    @Test
    fun restoreClearsHistoryAndGesturePreview() {
        val editor = SketchEditor()
        editor.execute(AddWallCommand(wall))
        editor.beginEdit()
        editor.preview(ChangeWallThicknessCommand(wall.id, 400.0))
        editor.restore(SketchDocument())
        assertFalse(editor.hasPreview)
        assertFalse(editor.canUndo)
        assertTrue(editor.renderDocument.walls.isEmpty())
    }
}
