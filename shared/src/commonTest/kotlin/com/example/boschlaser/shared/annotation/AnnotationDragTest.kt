package com.example.boschlaser.shared.annotation

import com.example.boschlaser.shared.geometry.Vec2
import kotlin.test.*

class AnnotationDragTest {
    private val dimension = DimensionAnnotation("dimension", PhotoPoint(.2, .3), PhotoPoint(.6, .7), "label", -1)
    private val items = listOf(dimension, TextAnnotation("text", PhotoPoint(.4, .4), "text", -1),
        AngleAnnotation("angle", PhotoPoint(.3, .4), PhotoPoint(.6, .4), PhotoPoint(.3, .7), "angle", -1),
        AreaAnnotation("area", listOf(PhotoPoint(.1, .1), PhotoPoint(.6, .1), PhotoPoint(.4, .6)), "area", -1))
    private val size = Vec2(400.0, 400.0)
    private fun editor(annotation: Annotation = dimension) =
        AnnotationEditor(AnnotationDocument(mapOf(annotation.id to annotation))).apply { setMode(AnnotationMode.MOVE) }

    @Test fun everyKindMovesFromTheGestureOriginAndCommitsOneUndoRecord() {
        for (annotation in items) {
            val editor = editor(annotation)
            val initial = editor.document
            val target = AnnotationSelectionTarget(annotation.id)
            assertTrue(editor.beginDrag(target, PhotoPoint(.5, .5)))
            assertEquals(AnnotationDrag(target, annotation.kind, PhotoPoint(.5, .5)), editor.state.drag)
            assertTrue(editor.hasPreview)
            assertTrue(editor.dragTo(PhotoPoint(.55, .55), size, 4.0))
            assertTrue(editor.dragTo(PhotoPoint(.6, .575), size, 4.0))
            assertEquals(initial, editor.document)
            val moved = editor.renderDocument.annotations.getValue(annotation.id)
            for ((before, after) in annotation.points.zip(moved.points)) {
                assertEquals(before.x + .1, after.x, 1e-12)
                assertEquals(before.y + .075, after.y, 1e-12)
            }
            assertTrue(editor.state.drag!!.started)
            assertTrue(editor.commitEdit())
            assertNull(editor.state.drag)
            assertTrue(editor.undo())
            assertEquals(initial, editor.document)
            assertFalse(editor.undo())
            assertTrue(editor.redo())
            assertEquals(moved, editor.document.annotations[annotation.id])
        }
    }

    @Test fun thresholdIsStrictAndReturningToTheStartDoesNotConsumeHistoryOrRedo() {
        val editor = editor()
        editor.execute(UpdateAnnotationCommand(dimension.copy(label = "edited")))
        editor.undo()
        val original = editor.document
        editor.beginDrag(AnnotationSelectionTarget(dimension.id), PhotoPoint(0.0, 0.0))
        assertFalse(editor.dragTo(PhotoPoint(.01, 0.0), size, 4.0))
        assertFalse(editor.state.drag!!.started)
        assertEquals(original, editor.renderDocument)
        assertTrue(editor.dragTo(PhotoPoint(.0100000001, 0.0), size, 4.0))
        assertTrue(editor.dragTo(PhotoPoint(0.0, 0.0), size, 4.0))
        assertEquals(original, editor.renderDocument)
        assertFalse(editor.commitEdit())
        assertNull(editor.state.drag)
        assertFalse(editor.canUndo)
        assertTrue(editor.canRedo)
    }

    @Test fun controlPointDragClampsOnlyThatPointAndRetainsUnchangedDoubleCoordinates() {
        for (annotation in items) {
            val editor = editor(annotation)
            val targetIndex = annotation.points.lastIndex
            val target = AnnotationSelectionTarget(annotation.id, targetIndex)
            assertTrue(editor.beginDrag(target, annotation.points[targetIndex]))
            assertTrue(editor.dragTo(PhotoPoint(2.0, -1.0), size, 4.0))
            val moved = editor.renderDocument.annotations.getValue(annotation.id)
            assertEquals(PhotoPoint(1.0, 0.0), moved.points[targetIndex])
            for (index in 0 until targetIndex) assertEquals(annotation.points[index], moved.points[index])
            assertEquals(annotation, editor.document.annotations[annotation.id])
            editor.cancelEdit()
            assertNull(editor.state.drag)
            assertEquals(annotation, editor.renderDocument.annotations[annotation.id])
            assertFalse(editor.canUndo)
        }
    }

    @Test fun wholeDragClampsTheShapeWithoutClampingThePointerAndCanStartEvenWhenMovementIsClamped() {
        val editor = editor()
        editor.beginDrag(AnnotationSelectionTarget(dimension.id), PhotoPoint(.5, .5))
        assertTrue(editor.dragTo(PhotoPoint(2.0, -1.0), size, 4.0))
        val moved = editor.renderDocument.annotations.getValue(dimension.id) as DimensionAnnotation
        assertEquals(.6, moved.start.x, 1e-12)
        assertEquals(0.0, moved.start.y, 1e-12)
        assertEquals(1.0, moved.end.x, 1e-12)
        assertEquals(.4, moved.end.y, 1e-12)
        editor.cancelEdit()
        val border = TextAnnotation("border", PhotoPoint(1.0, 1.0), "", -1)
        editor.restore(AnnotationDocument(mapOf(border.id to border)))
        editor.beginDrag(AnnotationSelectionTarget(border.id), border.position)
        assertTrue(editor.dragTo(PhotoPoint(2.0, 2.0), size, 4.0))
        assertTrue(editor.state.drag!!.started)
        assertFalse(editor.commitEdit())
        assertFalse(editor.canUndo)
    }

    @Test fun controlPointDragKeepsOtherChangesInTheSamePreviewTransaction() {
        val editor = editor()
        editor.beginDrag(AnnotationSelectionTarget(dimension.id, 0), dimension.start)
        editor.preview(UpdateAnnotationCommand(dimension.copy(label = "draft label")))
        editor.dragTo(PhotoPoint(.3, .4), size, 4.0)
        val moved = editor.renderDocument.annotations.getValue(dimension.id) as DimensionAnnotation
        assertEquals("draft label", moved.label)
        assertEquals(dimension.end, moved.end)
        assertTrue(editor.commitEdit())
        assertTrue(editor.undo())
        assertEquals(dimension, editor.document.annotations[dimension.id])
        assertFalse(editor.undo())
    }

    @Test fun stableDragTargetDoesNotFollowAChangedSelection() {
        val other = dimension.copy(id = "other")
        val editor = AnnotationEditor(AnnotationDocument(linkedMapOf(other.id to other, dimension.id to dimension)))
        editor.setMode(AnnotationMode.MOVE)
        editor.beginDrag(AnnotationSelectionTarget(dimension.id), PhotoPoint(.5, .5))
        editor.select(AnnotationSelectionTarget(other.id))
        editor.dragTo(PhotoPoint(.6, .6), size, 4.0)
        assertEquals(other, editor.renderDocument.annotations[other.id])
        assertNotEquals(dimension, editor.renderDocument.annotations[dimension.id])
        assertEquals(dimension.id, editor.state.drag?.target?.annotationId)
        assertEquals(other.id, editor.state.selection?.annotationId)
    }

    @Test fun invalidTargetsAndInvalidThresholdsDoNotReplaceAnActiveGesture() {
        val editor = editor()
        val target = AnnotationSelectionTarget(dimension.id)
        editor.beginDrag(target, PhotoPoint(.5, .5))
        editor.dragTo(PhotoPoint(.6, .6), size, 4.0)
        val state = editor.state
        val preview = editor.renderDocument
        for (invalid in listOf(AnnotationSelectionTarget("missing"), target.copy(pointIndex = -1), target.copy(pointIndex = 2))) {
            assertFalse(editor.beginDrag(invalid, PhotoPoint(.1, .1)))
            assertEquals(state, editor.state)
            assertEquals(preview, editor.renderDocument)
        }
        for (threshold in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { editor.dragTo(PhotoPoint(.7, .7), size, threshold) }
            assertEquals(state, editor.state)
        }
        assertFailsWith<IllegalArgumentException> { editor.dragTo(PhotoPoint(.7, .7), Vec2(0.0, 400.0), 4.0) }
        assertEquals(preview, editor.renderDocument)
    }

    @Test fun cancelToolSwitchRestoreAndCommandsCannotLeaveAnActiveDrag() {
        for (action in listOf("cancel", "mode", "restore", "delete")) {
            val editor = editor()
            val initial = editor.document
            editor.beginDrag(AnnotationSelectionTarget(dimension.id), PhotoPoint(.5, .5))
            editor.dragTo(PhotoPoint(.6, .6), size, 4.0)
            when (action) {
                "cancel" -> editor.cancelEdit()
                "mode" -> editor.setMode(AnnotationMode.DIMENSION, preserveSelection = true)
                "restore" -> editor.restore(initial)
                "delete" -> editor.execute(DeleteAnnotationCommand(dimension.id))
            }
            assertNull(editor.state.drag)
            assertFalse(editor.hasPreview)
            if (action != "delete") {
                assertEquals(initial, editor.document)
                assertFalse(editor.canUndo)
            } else {
                assertNull(editor.state.selection)
                assertTrue(editor.undo())
                assertEquals(initial, editor.document)
            }
        }
    }

    @Test fun previewDeletionOfTheDraggedVertexClearsItsInvalidTarget() {
        val area = AreaAnnotation("area", listOf(PhotoPoint(.1, .1), PhotoPoint(.9, .1),
            PhotoPoint(.9, .9), PhotoPoint(.1, .9)), "", -1)
        val editor = editor(area)
        editor.beginDrag(AnnotationSelectionTarget(area.id, 3), area.points.last())
        assertTrue(editor.preview(DeleteAreaPointCommand(area.id, 3)))
        assertNull(editor.state.drag)
        assertEquals(AnnotationSelectionTarget(area.id), editor.state.selection)
        assertFalse(editor.dragTo(PhotoPoint(.2, .8), size, 4.0))
        editor.cancelEdit()
        assertEquals(area, editor.renderDocument.annotations[area.id])
    }

    @Test fun wrongToolAndOverflowingPointerDisplacementCannotStartOrMutateADrag() {
        val editor = AnnotationEditor(AnnotationDocument(mapOf(dimension.id to dimension)))
        val target = AnnotationSelectionTarget(dimension.id)
        assertFalse(editor.beginDrag(target, PhotoPoint(.5, .5)))
        assertNull(editor.state.drag)
        editor.setMode(AnnotationMode.MOVE)
        editor.beginDrag(target, PhotoPoint(-Double.MAX_VALUE, .5))
        val state = editor.state
        assertFalse(editor.dragTo(PhotoPoint(Double.MAX_VALUE, .5), size, 4.0))
        assertEquals(state, editor.state)
        assertEquals(editor.document, editor.renderDocument)
    }
}
