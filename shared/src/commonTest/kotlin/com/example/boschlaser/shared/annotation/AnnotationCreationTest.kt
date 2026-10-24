package com.example.boschlaser.shared.annotation

import com.example.boschlaser.shared.geometry.Vec2
import kotlin.test.*

class AnnotationCreationTest {
    private val start = PhotoPoint(.123456789123, .234567891234)
    private val second = PhotoPoint(.876543219876, .765432198765)
    private val third = PhotoPoint(.7, .2)
    private val color = -65536

    @Test fun dimensionPreviewRetainsDoublePointsAndNeverTouchesTheDocumentOrHistory() {
        val editor = AnnotationEditor()
        assertTrue(editor.beginDimension(start))
        assertEquals(DimensionPlacement(start, start), editor.state.creation.dimension)
        assertTrue(editor.updateDimension(second))
        assertEquals(DimensionPlacement(start, second), editor.state.creation.dimension)
        assertEquals(AnnotationDocument(), editor.document)
        assertEquals(editor.document, editor.renderDocument)
        assertFalse(editor.hasPreview)
        assertFalse(editor.canUndo)
        editor.cancelDimension()
        assertNull(editor.state.creation.dimension)
        assertFalse(editor.updateDimension(second))
        assertFalse(editor.undo())
    }

    @Test fun dimensionReleaseUsesTheFinalPointAndCreatesExactlyOneUndoRecord() {
        val editor = AnnotationEditor()
        editor.beginDimension(start)
        editor.updateDimension(second)
        assertTrue(editor.finishDimension(PhotoPoint(1.5, -.5), Vec2(400.0, 200.0), 12.0, "dimension", " raw label ", color))
        val expected = DimensionAnnotation("dimension", start, PhotoPoint(1.0, 0.0), " raw label ", color)
        assertEquals(expected, editor.document.annotations.getValue(expected.id))
        assertNull(editor.state.creation.dimension)
        assertEquals(AnnotationSelectionTarget(expected.id), editor.state.selection)
        assertTrue(editor.undo())
        assertEquals(AnnotationDocument(), editor.document)
        assertFalse(editor.undo())
        assertTrue(editor.redo())
        assertEquals(expected, editor.document.annotations.getValue(expected.id))
    }

    @Test fun shortOrExactlyThresholdDragsCancelWithoutConsumingRedo() {
        val editor = AnnotationEditor()
        editor.execute(AddAnnotationCommand(DimensionAnnotation("existing", start, second, "", color)))
        editor.undo()
        for (end in listOf(PhotoPoint(0.0, 0.0), PhotoPoint(.119, 0.0), PhotoPoint(.12, 0.0))) {
            editor.beginDimension(PhotoPoint(0.0, 0.0))
            assertFalse(editor.finishDimension(end, Vec2(100.0, 100.0), 12.0, "dimension", "", color))
            assertNull(editor.state.creation.dimension)
            assertFalse(editor.canUndo)
            assertTrue(editor.canRedo)
        }
        editor.beginDimension(PhotoPoint(0.0, 0.0))
        assertTrue(editor.finishDimension(PhotoPoint(.120000001, 0.0), Vec2(100.0, 100.0), 12.0, "dimension", "", color))
        assertFalse(editor.canRedo)
    }

    @Test fun duplicateDimensionIdAndInvalidThresholdRejectWithoutPartialChanges() {
        val mark = DimensionAnnotation("existing", start, second, "original", color)
        val editor = AnnotationEditor(AnnotationDocument(mapOf(mark.id to mark)))
        editor.beginDimension(start)
        editor.updateDimension(second)
        val state = editor.state
        for (id in listOf(mark.id, "", " ")) {
            assertFalse(editor.finishDimension(third, Vec2(400.0, 200.0), 12.0, id, "", color))
            assertEquals(state, editor.state)
            assertEquals(mark, editor.document.annotations[mark.id])
            assertFalse(editor.canUndo)
        }
        for (threshold in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> {
                editor.finishDimension(third, Vec2(400.0, 200.0), threshold, "new", "", color)
            }
            assertEquals(state, editor.state)
        }
        assertFailsWith<IllegalArgumentException> {
            editor.finishDimension(third, Vec2(0.0, 200.0), 12.0, "new", "", color)
        }
        assertEquals(state, editor.state)
    }

    @Test fun angleCreatesOnTheThirdPointInEndpointVertexEndpointOrder() {
        val editor = AnnotationEditor()
        editor.setMode(AnnotationMode.ANGLE)
        assertEquals(AnnotationPlacementResult.Pending(1), editor.placeAnglePoint(start, "unused", color))
        val firstSnapshot = editor.state
        assertEquals(AnnotationPlacementResult.Pending(2), editor.placeAnglePoint(second, "unused", color))
        assertEquals(listOf(start), firstSnapshot.creation.anglePoints)
        assertEquals(emptyMap(), editor.document.annotations)
        assertFalse(editor.canUndo)
        assertEquals(AnnotationPlacementResult.Created("angle"), editor.placeAnglePoint(third, "angle", color))
        assertEquals(AngleAnnotation("angle", second, start, third, "", color), editor.document.annotations["angle"])
        assertEquals(emptyList(), editor.state.creation.anglePoints)
        assertEquals(AnnotationSelectionTarget("angle"), editor.state.selection)
        assertTrue(editor.undo())
        assertFalse(editor.undo())
    }

    @Test fun areaCreatesATriangleOnlyAfterThreeClampedPointsAndKeepsThePriorSelectionUntilThen() {
        val mark = TextAnnotation("existing", start, "label", color)
        val editor = AnnotationEditor(AnnotationDocument(mapOf(mark.id to mark)))
        editor.setMode(AnnotationMode.AREA)
        editor.select(AnnotationSelectionTarget(mark.id))
        assertEquals(AnnotationPlacementResult.Pending(1), editor.placeAreaPoint(PhotoPoint(-1.0, 2.0), "unused", color))
        assertEquals(AnnotationSelectionTarget(mark.id), editor.state.selection)
        assertEquals(AnnotationPlacementResult.Pending(2), editor.placeAreaPoint(second, "unused", color))
        assertEquals(1, editor.document.annotations.size)
        assertEquals(AnnotationPlacementResult.Created("area"), editor.placeAreaPoint(third, "area", color))
        assertEquals(AreaAnnotation("area", listOf(PhotoPoint(0.0, 1.0), second, third), "", color), editor.document.annotations["area"])
        assertEquals(emptyList(), editor.state.creation.areaPoints)
        assertEquals(AnnotationSelectionTarget("area"), editor.state.selection)
        assertTrue(editor.undo())
        assertEquals(mapOf(mark.id to mark), editor.document.annotations)
        assertFalse(editor.undo())
    }

    @Test fun failedThirdPointDoesNotConsumePendingPointsAndCanBeRetriedForBothTools() {
        val existing = TextAnnotation("existing", start, "label", color)
        for (mode in listOf(AnnotationMode.ANGLE, AnnotationMode.AREA)) {
            val editor = AnnotationEditor(AnnotationDocument(mapOf(existing.id to existing)))
            editor.setMode(mode)
            fun place(point: PhotoPoint, id: String) = if (mode == AnnotationMode.ANGLE)
                editor.placeAnglePoint(point, id, color) else editor.placeAreaPoint(point, id, color)
            place(start, "unused")
            place(second, "unused")
            val state = editor.state
            for (id in listOf(existing.id, "")) {
                assertEquals(AnnotationPlacementResult.Rejected, place(third, id))
                assertEquals(state, editor.state)
                assertEquals(mapOf(existing.id to existing), editor.document.annotations)
                assertFalse(editor.canUndo)
            }
            assertEquals(AnnotationPlacementResult.Created("new"), place(third, "new"))
            assertEquals(2, editor.document.annotations.size)
        }
    }

    @Test fun textPlacementPreservesContentAndUsesSharedSelectionAndHistory() {
        val editor = AnnotationEditor()
        assertFalse(editor.placeText(start, "text", "label", color))
        editor.setMode(AnnotationMode.TEXT)
        assertTrue(editor.placeText(start, "text", " raw\ntext ", color))
        assertEquals(TextAnnotation("text", start, " raw\ntext ", color), editor.document.annotations["text"])
        assertEquals(AnnotationSelectionTarget("text"), editor.state.selection)
        val state = editor.state
        assertFalse(editor.placeText(second, "text", "replacement", color))
        assertFalse(editor.placeText(second, "", "replacement", color))
        assertEquals(state, editor.state)
        assertTrue(editor.undo())
        assertFalse(editor.undo())
    }

    @Test fun toolSwitchAndRestoreClearCreationWhileCancellingOnlyTheCurrentGestureKeepsConfirmedClicks() {
        val editor = AnnotationEditor()
        editor.beginDimension(start)
        editor.setMode(AnnotationMode.ANGLE)
        assertEquals(AnnotationCreationState(), editor.state.creation)
        editor.placeAnglePoint(start, "unused", color)
        editor.cancelEdit()
        editor.cancelDimension()
        assertEquals(listOf(start), editor.state.creation.anglePoints)
        editor.setMode(AnnotationMode.ANGLE)
        assertEquals(listOf(start), editor.state.creation.anglePoints)
        editor.setMode(AnnotationMode.AREA)
        assertEquals(AnnotationCreationState(), editor.state.creation)
        editor.placeAreaPoint(second, "unused", color)
        editor.clearPendingArea()
        assertEquals(AnnotationCreationState(), editor.state.creation)
        editor.placeAreaPoint(start, "unused", color)
        editor.restore(AnnotationDocument())
        assertEquals(AnnotationCreationState(), editor.state.creation)
        assertEquals(AnnotationMode.AREA, editor.state.mode)
        assertFalse(editor.canUndo)
    }

    @Test fun wrongToolsDoNotCreatePreviewAndLargeFiniteCoordinatesDoNotOverflowTheLengthDecision() {
        val editor = AnnotationEditor()
        val original = editor.state
        assertEquals(AnnotationPlacementResult.Rejected, editor.placeAreaPoint(start, "area", color))
        assertEquals(AnnotationPlacementResult.Rejected, editor.placeAnglePoint(start, "angle", color))
        assertEquals(original, editor.state)
        editor.setMode(AnnotationMode.MOVE)
        assertFalse(editor.beginDimension(start))
        editor.setMode(AnnotationMode.DIMENSION)
        editor.beginDimension(PhotoPoint(0.0, 0.0))
        assertTrue(editor.finishDimension(PhotoPoint(1.0, 1.0), Vec2(1e200, 1e200), 1e200, "large", "", color))
    }
}
