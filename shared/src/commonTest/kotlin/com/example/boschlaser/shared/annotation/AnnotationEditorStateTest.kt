package com.example.boschlaser.shared.annotation

import kotlin.test.*

class AnnotationEditorStateTest {
    private val mark = DimensionAnnotation("dimension", PhotoPoint(.123456789123, .3), PhotoPoint(.8, .3), "label", -1)
    private val initial = AnnotationDocument(mapOf(mark.id to mark), "photo")
    private val target = AnnotationSelectionTarget(mark.id)

    @Test fun selectionUsesStableIdentityAndDoesNotWriteHistoryOrConsumeRedo() {
        val other = mark.copy(id = "other")
        val editor = AnnotationEditor(initial.copy(annotations = linkedMapOf(other.id to other, mark.id to mark)))
        editor.execute(UpdateAnnotationCommand(mark.copy(label = "edited")))
        assertTrue(editor.undo())
        editor.select(target)
        assertTrue(editor.canRedo)
        assertFalse(editor.canUndo)
        assertTrue(editor.redo())
        assertEquals(target, editor.state.selection)
        assertTrue(editor.execute(DeleteAnnotationCommand(other.id)))
        assertEquals(target, editor.state.selection)
        assertEquals(mark.points, editor.renderDocument.annotations.getValue(mark.id).points)
    }

    @Test fun invalidTargetsClearSelectionAndInvalidPointOnlyClearsThePoint() {
        val editor = AnnotationEditor(initial)
        editor.select(AnnotationSelectionTarget("missing"))
        assertNull(editor.state.selection)
        for (index in listOf(-1, 2, Int.MAX_VALUE)) {
            editor.select(target.copy(pointIndex = index))
            assertEquals(target, editor.state.selection)
        }
        editor.select(target.copy(pointIndex = 1))
        assertEquals(target.copy(pointIndex = 1), editor.state.selection)
        editor.select(null)
        assertNull(editor.state.selection)
        assertFalse(editor.canUndo)
    }

    @Test fun toolChangesCancelDraftAndExplicitlyControlSelectionPreservation() {
        val editor = AnnotationEditor(initial)
        editor.select(target)
        editor.beginEdit()
        editor.preview(MoveAnnotationCommand(mark.id, .1, .1))
        editor.setMode(AnnotationMode.MOVE, preserveSelection = true)
        assertEquals(initial, editor.renderDocument)
        assertFalse(editor.hasPreview)
        assertEquals(target, editor.state.selection)
        assertEquals(AnnotationMode.MOVE, editor.state.mode)
        assertFalse(editor.canUndo)
        editor.setMode(AnnotationMode.MOVE)
        assertEquals(target, editor.state.selection) // Re-selecting the active tool keeps existing selection.
        editor.setMode(AnnotationMode.AREA)
        assertNull(editor.state.selection)
    }

    @Test fun deletionAndHistoryNeverLeaveDanglingSelection() {
        val editor = AnnotationEditor(initial)
        editor.select(target)
        assertTrue(editor.execute(DeleteAnnotationCommand(mark.id)))
        assertNull(editor.state.selection)
        assertTrue(editor.undo())
        editor.select(target)
        assertTrue(editor.redo())
        assertNull(editor.state.selection)
        val added = mark.copy(id = "new")
        assertTrue(editor.execute(AddAnnotationCommand(added)))
        editor.select(AnnotationSelectionTarget(added.id))
        assertTrue(editor.undo())
        assertNull(editor.state.selection)
    }

    @Test fun previewSelectionIsValidatedOnCommitAndCancellation() {
        val editor = AnnotationEditor(initial)
        val previewOnly = mark.copy(id = "preview")
        editor.beginEdit()
        assertTrue(editor.preview(AddAnnotationCommand(previewOnly)))
        editor.select(AnnotationSelectionTarget(previewOnly.id))
        editor.cancelEdit()
        assertNull(editor.state.selection)
        editor.select(target)
        editor.beginEdit()
        editor.preview(MoveAnnotationCommand(mark.id, .1, .1))
        editor.cancelEdit()
        assertEquals(target, editor.state.selection)
        editor.beginEdit()
        editor.preview(DeleteAnnotationCommand(mark.id))
        editor.commitEdit()
        assertNull(editor.state.selection)
        assertEquals(emptyMap(), editor.document.annotations)
    }

    @Test fun areaVertexRemovalAndRestoreKeepStateValid() {
        val area = AreaAnnotation("area", listOf(PhotoPoint(0.0, 0.0), PhotoPoint(1.0, 0.0),
            PhotoPoint(1.0, 1.0), PhotoPoint(0.0, 1.0)), "", -1)
        val editor = AnnotationEditor(AnnotationDocument(mapOf(area.id to area)))
        editor.setMode(AnnotationMode.MOVE)
        editor.select(AnnotationSelectionTarget(area.id, 3))
        assertTrue(editor.execute(DeleteAreaPointCommand(area.id, 3)))
        assertEquals(AnnotationSelectionTarget(area.id), editor.state.selection)
        editor.restore(initial)
        assertNull(editor.state.selection)
        assertEquals(AnnotationMode.MOVE, editor.state.mode)
        assertFalse(editor.canUndo)
    }
}
