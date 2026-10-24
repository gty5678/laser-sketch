package com.example.boschlaser.shared.annotation

import kotlin.test.*

class AnnotationEditorTest {
    private val dimension = DimensionAnnotation("dimension", PhotoPoint(.2, .3), PhotoPoint(.6, .7), "1000", -1)
    private val initial = AnnotationDocument(mapOf(dimension.id to dimension), "photo")
    @Test fun dragPreviewIsIsolatedCancelsAndCommitsOneUndoRecord() {
        val editor = AnnotationEditor(initial)
        editor.beginEdit()
        editor.previewFromStart(MoveAnnotationCommand(dimension.id, .05, .05))
        editor.previewFromStart(MoveAnnotationCommand(dimension.id, .1, .1))
        assertEquals(initial, editor.document)
        val moved = editor.renderDocument.annotations.getValue(dimension.id) as DimensionAnnotation
        assertEquals(.3, moved.start.x, 1e-12)
        assertFalse(editor.canUndo)
        editor.cancelEdit()
        assertEquals(initial, editor.renderDocument)
        editor.beginEdit()
        editor.previewFromStart(MoveAnnotationCommand(dimension.id, .1, .1))
        assertTrue(editor.commitEdit())
        assertTrue(editor.undo())
        assertEquals(initial, editor.document)
        assertFalse(editor.canUndo)
        assertTrue(editor.redo())
        assertEquals(moved, editor.document.annotations.getValue(dimension.id))
    }
    @Test fun wholeMoveClampsAsOneShapeAndControlPointClampsIndependently() {
        val moved = MoveAnnotationCommand(dimension.id, 1.0, -1.0).apply(initial)!!
        val mark = moved.annotations.getValue(dimension.id) as DimensionAnnotation
        assertEquals(.6, mark.start.x, 1e-12)
        assertEquals(0.0, mark.start.y, 1e-12)
        assertEquals(1.0, mark.end.x, 1e-12)
        assertEquals(.4, mark.end.y, 1e-12)
        val endpoint = MoveAnnotationPointCommand(dimension.id, 0, PhotoPoint(-1.0, 2.0)).apply(initial)!!
            .annotations.getValue(dimension.id) as DimensionAnnotation
        assertEquals(PhotoPoint(0.0, 1.0), endpoint.start)
        assertEquals(dimension.end, endpoint.end)
    }
    @Test fun failuresAndNoopsDoNotConsumeHistoryOrDiscardRedo() {
        val editor = AnnotationEditor(initial)
        editor.execute(DeleteAnnotationCommand(dimension.id)); editor.undo()
        assertFalse(editor.execute(UpdateAnnotationCommand(dimension)))
        assertFalse(editor.execute(UpdateAnnotationCommand(TextAnnotation(dimension.id, PhotoPoint(.1, .1), "text", -1))))
        assertFalse(editor.execute(MoveAnnotationCommand("missing", .1, .1)))
        assertFalse(editor.execute(MoveAnnotationCommand(dimension.id, Double.NaN, .1)))
        editor.beginEdit(); assertFalse(editor.commitEdit())
        assertTrue(editor.canRedo)
        assertEquals(initial, editor.document)
    }
    @Test fun areaMidpointIncludesClosingEdgeAndTriangleCannotLosePoint() {
        val area = AreaAnnotation("area", listOf(PhotoPoint(0.0, 0.0), PhotoPoint(1.0, 0.0), PhotoPoint(1.0, 1.0)), "", -1)
        val document = AnnotationDocument(mapOf(area.id to area))
        assertNull(DeleteAreaPointCommand(area.id, 0).apply(document))
        assertNull(InsertAreaMidpointCommand(area.id, 3).apply(document))
        val inserted = InsertAreaMidpointCommand(area.id, 2).apply(document)!!
        val points = (inserted.annotations.getValue(area.id) as AreaAnnotation).points
        assertEquals(listOf(PhotoPoint(0.0, 0.0), PhotoPoint(1.0, 0.0), PhotoPoint(1.0, 1.0), PhotoPoint(.5, .5)), points)
        assertEquals(document, DeleteAreaPointCommand(area.id, 3).apply(inserted))
    }
    @Test fun undoPreservesOrderAndDetachesCallerOwnedAreaPoints() {
        val points = mutableListOf(PhotoPoint(0.0, 0.0), PhotoPoint(1.0, 0.0), PhotoPoint(1.0, 1.0))
        val editor = AnnotationEditor(initial)
        editor.execute(AddAnnotationCommand(AreaAnnotation("area", points, "", -1)))
        points.clear()
        editor.undo(); editor.redo()
        assertEquals(3, (editor.document.annotations.getValue("area") as AreaAnnotation).points.size)
        editor.execute(DeleteAnnotationCommand(dimension.id)); editor.undo()
        assertEquals(listOf(dimension.id, "area"), editor.document.annotations.keys.toList())
        editor.restore(initial)
        assertFalse(editor.canUndo)
        assertFalse(editor.canRedo)
    }
}
