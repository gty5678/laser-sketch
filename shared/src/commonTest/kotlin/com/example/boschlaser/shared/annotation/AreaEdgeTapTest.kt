package com.example.boschlaser.shared.annotation

import kotlin.test.*

class AreaEdgeTapTest {
    private val area = AreaAnnotation("area", listOf(PhotoPoint(.123456789123, .2),
        PhotoPoint(.8, .2), PhotoPoint(.8, .876543219876)), "", -1)
    private val initial = AnnotationDocument(mapOf(area.id to area))
    private fun editor() = AnnotationEditor(initial).apply { setMode(AnnotationMode.MOVE) }

    @Test fun closingEdgeDoubleTapIncludesTheWindowBoundaryAndWritesOneUndo() {
        val editor = editor()
        editor.execute(UpdateAnnotationCommand(area.copy(label = "edited")))
        editor.undo()
        assertEquals(AreaEdgeTapResult.PENDING, editor.tapAreaEdge(area.id, 2, 100, 450))
        assertEquals(initial, editor.document)
        assertFalse(editor.canUndo)
        assertTrue(editor.canRedo)
        assertEquals(AreaEdgeTapResult.INSERTED, editor.tapAreaEdge(area.id, 2, 550, 450))
        val points = editor.document.annotations.getValue(area.id).points
        assertEquals(area.points, points.take(3))
        assertEquals(PhotoPoint((area.points[2].x + area.points[0].x) / 2,
            (area.points[2].y + area.points[0].y) / 2), points.last())
        assertEquals(AnnotationSelectionTarget(area.id, 3), editor.state.selection)
        assertNull(editor.state.areaEdgeTap)
        assertFalse(editor.canRedo)
        assertTrue(editor.undo())
        assertEquals(initial, editor.document)
        assertFalse(editor.undo())
    }

    @Test fun expiryAndClockRollbackStartANewPairAndZeroTimestampIsNotASentinel() {
        val editor = editor()
        assertEquals(AreaEdgeTapResult.PENDING, editor.tapAreaEdge(area.id, 0, 100, 450))
        assertEquals(AreaEdgeTapResult.PENDING, editor.tapAreaEdge(area.id, 0, 551, 450))
        assertEquals(AreaEdgeTapResult.PENDING, editor.tapAreaEdge(area.id, 0, 1, 450))
        assertEquals(AreaEdgeTapResult.INSERTED, editor.tapAreaEdge(area.id, 0, 451, 450))
        editor.restore(initial)
        assertEquals(AreaEdgeTapResult.PENDING, editor.tapAreaEdge(area.id, 0, 0, 0))
        assertEquals(AreaEdgeTapResult.INSERTED, editor.tapAreaEdge(area.id, 0, 0, 0))
        assertEquals(AreaEdgeTapResult.PENDING, editor.tapAreaEdge(area.id, 0, 0, 0))
    }

    @Test fun stableAreaIdentitySurvivesOtherDeletionButDifferentAreasOrEdgesNeverCombine() {
        val other = area.copy(id = "other")
        val editor = AnnotationEditor(AnnotationDocument(linkedMapOf(other.id to other, area.id to area)))
        editor.setMode(AnnotationMode.MOVE)
        editor.tapAreaEdge(area.id, 0, 100, 450)
        editor.execute(DeleteAnnotationCommand(other.id))
        assertEquals(area.id, editor.state.areaEdgeTap?.areaId)
        assertEquals(AreaEdgeTapResult.INSERTED, editor.tapAreaEdge(area.id, 0, 150, 450))
        editor.restore(AnnotationDocument(linkedMapOf(other.id to other, area.id to area)))
        editor.tapAreaEdge(other.id, 0, 200, 450)
        assertEquals(AreaEdgeTapResult.PENDING, editor.tapAreaEdge(area.id, 0, 250, 450))
        assertEquals(AreaEdgeTapResult.PENDING, editor.tapAreaEdge(area.id, 1, 300, 450))
        assertEquals(3, editor.document.annotations.getValue(area.id).points.size)
        assertFalse(editor.canUndo)
    }

    @Test fun invalidTargetsAndTimesLeaveThePendingPairAndDocumentUntouched() {
        val text = TextAnnotation("text", PhotoPoint(.5, .5), "", -1)
        val editor = editor()
        editor.execute(AddAnnotationCommand(text))
        editor.tapAreaEdge(area.id, 0, 100, 450)
        val state = editor.state
        val document = editor.document
        for ((id, edge) in listOf("missing" to 0, text.id to 0, area.id to -1, area.id to 3)) {
            assertEquals(AreaEdgeTapResult.REJECTED, editor.tapAreaEdge(id, edge, 150, 450))
            assertEquals(state, editor.state)
            assertEquals(document, editor.document)
        }
        assertFailsWith<IllegalArgumentException> { editor.tapAreaEdge(area.id, 0, -1, 450) }
        assertFailsWith<IllegalArgumentException> { editor.tapAreaEdge(area.id, 0, 150, -1) }
        assertEquals(state, editor.state)
        editor.setMode(AnnotationMode.TEXT)
        val inactive = editor.state
        assertEquals(AreaEdgeTapResult.REJECTED, editor.tapAreaEdge(area.id, 0, 150, 450))
        assertEquals(inactive, editor.state)
        assertEquals(document, editor.document)
    }

    @Test fun geometryChangesDeletionAndRestoreInvalidateThePendingEdgeButLabelEditsDoNot() {
        val editor = editor()
        editor.tapAreaEdge(area.id, 0, 100, 450)
        editor.execute(UpdateAnnotationCommand(area.copy(label = "label")))
        assertNotNull(editor.state.areaEdgeTap)
        editor.execute(MoveAnnotationPointCommand(area.id, 1, PhotoPoint(.7, .3)))
        assertNull(editor.state.areaEdgeTap)
        editor.tapAreaEdge(area.id, 0, 150, 450)
        editor.execute(DeleteAnnotationCommand(area.id))
        assertNull(editor.state.areaEdgeTap)
        editor.undo()
        assertNull(editor.state.areaEdgeTap)
        editor.tapAreaEdge(area.id, 0, 200, 450)
        editor.restore(initial)
        assertNull(editor.state.areaEdgeTap)
        assertEquals(AreaEdgeTapResult.PENDING, editor.tapAreaEdge(area.id, 0, 250, 450))
    }

    @Test fun insertionJoinsTheActiveReleaseTransactionAndCanBeCancelledWithoutHistory() {
        for (commit in listOf(false, true)) {
            val editor = editor()
            editor.tapAreaEdge(area.id, 2, 100, 450)
            editor.beginDrag(AnnotationSelectionTarget(area.id), PhotoPoint(.5, .5))
            assertEquals(AreaEdgeTapResult.INSERTED, editor.tapAreaEdge(area.id, 2, 150, 450))
            assertEquals(initial, editor.document)
            assertEquals(4, editor.renderDocument.annotations.getValue(area.id).points.size)
            assertFalse(editor.canUndo)
            if (commit) {
                assertTrue(editor.commitEdit())
                assertTrue(editor.undo())
                assertEquals(initial, editor.document)
                assertFalse(editor.undo())
            } else {
                editor.cancelEdit()
                assertEquals(initial, editor.renderDocument)
                assertEquals(AnnotationSelectionTarget(area.id), editor.state.selection)
                assertFalse(editor.canUndo)
            }
            assertNull(editor.state.drag)
            assertNull(editor.state.areaEdgeTap)
        }
    }

    @Test fun maximumFiniteClockValuesDoNotOverflowTheWindowComparison() {
        val editor = editor()
        editor.tapAreaEdge(area.id, 0, 0, 450)
        assertEquals(AreaEdgeTapResult.PENDING, editor.tapAreaEdge(area.id, 0, Long.MAX_VALUE, 450))
        assertEquals(AreaEdgeTapResult.INSERTED, editor.tapAreaEdge(area.id, 0, Long.MAX_VALUE, 450))
    }

    @Test fun midpointInsertionPreservesExtremeFiniteAndSubnormalLegacyCoordinates() {
        for (sign in listOf(-1.0, 1.0)) {
            val large = Double.MAX_VALUE * sign
            val points = listOf(PhotoPoint(large, Double.MIN_VALUE), PhotoPoint(large, Double.MIN_VALUE), PhotoPoint(-large, 0.0))
            val legacy = area.copy(points = points)
            val document = AnnotationDocument(mapOf(legacy.id to legacy))
            val inserted = InsertAreaMidpointCommand(legacy.id, 0).apply(document)!!
            assertEquals(PhotoPoint(large, Double.MIN_VALUE), inserted.annotations.getValue(legacy.id).points[1])
            assertEquals(legacy, document.annotations[legacy.id])
            val closing = InsertAreaMidpointCommand(legacy.id, 2).apply(document)!!
            assertEquals(0.0, closing.annotations.getValue(legacy.id).points.last().x, 0.0)
        }
    }
}
