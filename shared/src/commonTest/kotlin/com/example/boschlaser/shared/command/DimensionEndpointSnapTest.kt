package com.example.boschlaser.shared.command

import com.example.boschlaser.shared.editor.*
import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.tool.PointerContext
import kotlin.test.*

class DimensionEndpointSnapTest {
    private fun fixture(): SketchDocument {
        val walls = listOf(0.0, 3000.0, 6000.0).mapIndexed { index, x ->
            Wall("wall$index", Vec2(x, 0.0), Vec2(x, 2000.0), 200.0)
        }
        val dimension = WallDimension("existing", WallFaceReference("wall0", WallFaceSide.CENTER),
            WallFaceReference("wall1", WallFaceSide.CENTER), Vec2(1500.0, 5000.0))
        return SketchDocument(walls = walls.associateBy { it.id }, dimensions = mapOf(dimension.id to dimension))
    }
    private fun context(point: Vec2) = PointerContext(point, 20.0, 20.0, 20.0, 20.0, 30.0, 20.0)
    private fun tap(editor: SketchEditor, point: Vec2) {
        editor.pointerDown(point, context(point))
        editor.pointerUp(point, context(point))
    }

    @Test fun capturesEitherVisibleTickFarOutsideObjectAndUsesExactPoint() {
        val document = fixture()
        for ((point, expected, reference) in listOf(
            Triple(Vec2(9.0, 4991.0), Vec2(0.0, 5000.0), document.dimensions.getValue("existing").first),
            Triple(Vec2(2990.0, 5008.0), Vec2(3000.0, 5000.0), document.dimensions.getValue("existing").second),
        )) {
            assertNull(WallDimensionGeometry.pickFace(document, point, 30.0))
            val snap = assertNotNull(WallDimensionGeometry.snapEndpoint(document, point, 30.0))
            assertEquals(expected, snap.point)
            assertEquals(reference, snap.reference)
            assertEquals("existing", snap.dimensionId)
            assertNull(WallDimensionGeometry.snapEndpoint(document, point, 5.0))
        }
        assertNull(WallDimensionGeometry.snapEndpoint(document, Vec2(1500.0, 5000.0), 30.0))
    }

    @Test fun newDimensionInheritsObjectReferenceAlignsAtOldTickAndStillDrivesIndependently() {
        val original = fixture()
        val editor = SketchEditor(original)
        editor.setTool(ToolType.DIMENSION)
        val first = Vec2(3010.0, 4990.0)
        editor.pointerDown(first, context(first))
        assertEquals(SnapType.ENDPOINT, editor.state.snap?.type)
        assertEquals(Vec2(3000.0, 5000.0), editor.state.snap?.point)
        assertEquals(editor.state.snap?.point, editor.state.magnifierTarget)
        editor.pointerUp(first, context(first))
        assertEquals(original.dimensions.getValue("existing").second, editor.state.dimensionFirst)
        tap(editor, Vec2(6000.0, 1000.0))
        val placement = Vec2(12.0, 4990.0)
        editor.pointerMove(placement, context(placement))
        assertEquals(Vec2(0.0, 5000.0), (editor.state.preview as SketchPreview.Dimension).dimension.position)
        assertEquals(original, editor.document)
        tap(editor, placement)
        val added = editor.document.dimensions.values.single { it.id != "existing" }
        assertEquals(Vec2(0.0, 5000.0), added.position)
        assertEquals(WallFaceReference("wall1", WallFaceSide.CENTER), added.first)
        assertEquals(WallFaceReference("wall2", WallFaceSide.CENTER), added.second)
        assertEquals(3000.0, WallDimensionGeometry.layout(editor.document, added)!!.valueMm, .001)
        assertNull(editor.state.snap)
        assertNull(editor.state.magnifierTarget)
        assertTrue(editor.undo())
        assertEquals(original, editor.document)
        assertTrue(editor.redo())
        assertTrue(editor.execute(DeleteWallDimensionCommand("existing")))
        assertTrue(editor.execute(DriveWallDimensionCommand(added.id, 3500.125)))
        assertEquals(6500.125, editor.document.walls.getValue("wall2").start.x, .001)
    }

    @Test fun secondReferenceRejectsSameObjectNonparallelAndInvalidSourceDimensions() {
        val document = fixture()
        val first = document.dimensions.getValue("existing").first
        assertNull(WallDimensionGeometry.snapEndpoint(document, Vec2(0.0, 5000.0), 30.0, first))
        assertNotNull(WallDimensionGeometry.snapEndpoint(document, Vec2(3000.0, 5000.0), 30.0, first))
        val changed = document.walls.getValue("wall1").copy(end = Vec2(4000.0, 2000.0))
        val invalid = document.copy(walls = document.walls + (changed.id to changed))
        assertNull(WallDimensionGeometry.snapEndpoint(invalid, Vec2(3000.0, 5000.0), 30.0))
        assertFailsWith<IllegalArgumentException> { WallDimensionGeometry.snapEndpoint(document, Vec2(0.0, 0.0), Double.NaN) }
    }

    @Test fun draggingPreviewLineOrLabelMovesOnlyDimensionAndCommitsOnceOnRelease() {
        for (labelHit in listOf(false, true)) {
            val original = fixture()
            val editor = SketchEditor(original)
            editor.setTool(ToolType.DIMENSION)
            tap(editor, Vec2(5.0, 5005.0))
            tap(editor, Vec2(2995.0, 5005.0))
            val before = (editor.state.preview as SketchPreview.Dimension).dimension
            val start = Vec2(1500.0, if (labelHit) 4900.0 else 5000.0)
            val target = Vec2(1500.0, 4000.0)
            editor.pointerDown(start, context(start).copy(dimensionPreviewLabelHit = labelHit))
            assertTrue(editor.state.dimensionGesture!!.movingDimension)
            assertEquals(before, (editor.state.preview as SketchPreview.Dimension).dimension)
            val expected = before.copy(position = before.position + target - start)
            val moved = editor.pointerMove(target, context(target))
            assertEquals(Vec2(0.0, 0.0), moved.panBy)
            assertFalse(moved.documentChanged)
            assertEquals(expected, (editor.state.preview as SketchPreview.Dimension).dimension)
            assertEquals(original, editor.document)
            assertFalse(editor.canUndo)
            val update = editor.pointerUp(target, context(target))
            assertEquals(Vec2(0.0, 0.0), update.panBy)
            assertTrue(update.documentChanged)
            assertEquals(expected, editor.document.dimensions.getValue(before.id))
            assertEquals(original.walls, editor.document.walls)
            assertNull(editor.state.dimensionGesture)
            assertTrue(editor.undo())
            assertEquals(original, editor.document)
            assertFalse(editor.canUndo)
        }
    }

    @Test fun cancellingPreviewLineDragNeverPlacesDimensionOrMovesObjects() {
        val original = fixture()
        val editor = SketchEditor(original)
        editor.setTool(ToolType.DIMENSION)
        tap(editor, Vec2(5.0, 5005.0))
        tap(editor, Vec2(2995.0, 5005.0))
        val start = Vec2(1500.0, 5000.0)
        val target = Vec2(1500.0, 4000.0)
        editor.pointerDown(start, context(start))
        assertEquals(Vec2(0.0, 0.0), editor.pointerMove(target, context(target)).panBy)
        editor.cancelPointer()
        assertEquals(original, editor.document)
        assertNull(editor.state.preview)
        assertNull(editor.state.dimensionGesture)
        assertFalse(editor.canUndo)
    }

    @Test fun tapNearPreviewLinePlacesAtTapButDraggingLabelSnapsTheTranslatedPreview() {
        val editor = SketchEditor(fixture())
        editor.setTool(ToolType.DIMENSION)
        tap(editor, Vec2(0.0, 1000.0))
        tap(editor, Vec2(3000.0, 1000.0))
        val before = editor.state.preview
        val nearLine = Vec2(1500.0, 1010.0)
        editor.pointerDown(nearLine, context(nearLine))
        assertEquals(before, editor.state.preview)
        editor.pointerUp(nearLine, context(nearLine))
        assertEquals(nearLine, editor.document.dimensions.values.single { it.id != "existing" }.position)
        assertTrue(editor.undo())
        editor.setTool(ToolType.DIMENSION)
        tap(editor, Vec2(0.0, 1000.0))
        tap(editor, Vec2(3000.0, 1000.0))
        val start = Vec2(1500.0, 900.0)
        val end = Vec2(1500.0, 4890.0)
        editor.pointerDown(start, context(start).copy(dimensionPreviewLabelHit = true))
        editor.pointerMove(end, context(end))
        val preview = (editor.state.preview as SketchPreview.Dimension).dimension
        assertEquals(Vec2(3000.0, 5000.0), preview.position)
        assertEquals(preview.position, editor.state.snap?.point)
        editor.pointerUp(end, context(end))
        assertEquals(preview, editor.document.dimensions.getValue(preview.id))
    }

    @Test fun draggingPansInEveryCreationStageWithoutPickingOrCommittingAndPreservesWorldPreview() {
        for (stage in 0..2) {
            val original = fixture()
            val editor = SketchEditor(original)
            editor.setTool(ToolType.DIMENSION)
            if (stage >= 1) tap(editor, Vec2(5.0, 5005.0))
            if (stage >= 2) tap(editor, Vec2(2995.0, 5005.0))
            val first = editor.state.dimensionFirst
            val preview = editor.state.preview
            val point = Vec2(1500.0, 4000.0)
            fun pointer(screen: Vec2) = context(point).copy(screenPoint = screen, dragThreshold = 8.0, viewportScale = .1)
            editor.pointerDown(point, pointer(Vec2(100.0, 400.0)))
            assertEquals(preview, editor.state.preview)
            assertEquals(Vec2(0.0, 0.0), editor.pointerMove(point, pointer(Vec2(104.0, 400.0))).panBy)
            assertEquals(preview, editor.state.preview)
            assertEquals(Vec2(10.0, 20.0), editor.pointerMove(point, pointer(Vec2(110.0, 420.0))).panBy)
            assertEquals(Vec2(20.0, -5.0), editor.pointerMove(point, pointer(Vec2(130.0, 415.0))).panBy)
            assertEquals(preview, editor.state.preview)
            assertEquals(first, editor.state.dimensionFirst)
            assertNull(editor.state.snap)
            assertNull(editor.state.magnifierTarget)
            val update = editor.pointerUp(point, pointer(Vec2(140.0, 415.0)))
            assertEquals(Vec2(10.0, 0.0), update.panBy)
            assertFalse(update.documentChanged)
            assertEquals(first, editor.state.dimensionFirst)
            assertEquals(preview, editor.state.preview)
            assertNull(editor.state.dimensionGesture)
            assertFalse(editor.state.dragging)
            assertEquals(original, editor.document)
            assertFalse(editor.canUndo)
        }
    }

    @Test fun shortTapJitterStillPicksAndZoomInterruptClearsOnlyThePanGesture() {
        val editor = SketchEditor(fixture())
        editor.setTool(ToolType.DIMENSION)
        val first = Vec2(5.0, 5005.0)
        editor.pointerDown(first, context(first).copy(dragThreshold = 8.0))
        editor.pointerUp(first + Vec2(3.0, 2.0), context(first + Vec2(3.0, 2.0)).copy(dragThreshold = 8.0))
        assertEquals(WallFaceReference("wall0", WallFaceSide.CENTER), editor.state.dimensionFirst)
        val point = Vec2(1500.0, 4000.0)
        editor.pointerDown(point, context(point))
        editor.pointerMove(point + Vec2(100.0, 0.0), context(point + Vec2(100.0, 0.0)))
        assertTrue(editor.state.dragging)
        editor.interruptPointerForViewportChange()
        assertNull(editor.state.dimensionGesture)
        assertFalse(editor.state.dragging)
        assertEquals(WallFaceReference("wall0", WallFaceSide.CENTER), editor.state.dimensionFirst)
        tap(editor, Vec2(2995.0, 5005.0))
        assertIs<SketchPreview.Dimension>(editor.state.preview)
        assertFalse(editor.canUndo)
    }

    @Test fun viewportChangesPreserveFirstReferenceAndDimensionPreviewUntilPlacement() {
        val original = fixture()
        val editor = SketchEditor(original)
        editor.setTool(ToolType.DIMENSION)
        tap(editor, Vec2(5.0, 5005.0))
        val first = editor.state.dimensionFirst
        val second = Vec2(2995.0, 5005.0)
        editor.pointerDown(second, context(second))
        assertNotNull(editor.state.snap)
        editor.interruptPointerForViewportChange()
        assertEquals(first, editor.state.dimensionFirst)
        assertNull(editor.state.preview)
        assertNull(editor.state.snap)
        assertNull(editor.state.magnifierTarget)
        assertEquals(original, editor.document)
        assertFalse(editor.canUndo)
        tap(editor, second)
        val preview = editor.state.preview as SketchPreview.Dimension
        repeat(3) {
            editor.interruptPointerForViewportChange()
            assertEquals(preview, editor.state.preview)
            assertEquals(first, editor.state.dimensionFirst)
            assertEquals(original, editor.document)
            assertFalse(editor.canUndo)
        }
        tap(editor, Vec2(0.0, 4000.0))
        assertEquals(2, editor.document.dimensions.size)
        assertNull(editor.state.preview)
        assertTrue(editor.undo())
        assertEquals(original, editor.document)
    }

    @Test fun explicitCancelStillClearsDimensionSessionAndViewportChangesStillCancelOtherTools() {
        val editor = SketchEditor(fixture())
        editor.setTool(ToolType.DIMENSION)
        tap(editor, Vec2(5.0, 5005.0))
        editor.interruptPointerForViewportChange()
        editor.cancelPointer()
        assertNull(editor.state.dimensionFirst)
        assertNull(editor.state.preview)
        editor.setTool(ToolType.MEASURE)
        tap(editor, Vec2(1000.0, 1000.0))
        assertIs<SketchPreview.Measurement>(editor.state.preview)
        editor.interruptPointerForViewportChange()
        assertNull(editor.state.preview)
        assertFalse(editor.canUndo)
    }

    @Test fun pickingSecondEndpointAndCancellingLeavesNoDocumentOrHistoryChanges() {
        val original = fixture()
        val editor = SketchEditor(original)
        editor.setTool(ToolType.DIMENSION)
        tap(editor, Vec2(5.0, 5005.0))
        val second = Vec2(2995.0, 5005.0)
        editor.pointerMove(second, context(second))
        assertEquals(Vec2(3000.0, 5000.0), editor.state.snap?.point)
        editor.pointerUp(second, context(second))
        val dimension = (editor.state.preview as SketchPreview.Dimension).dimension
        assertEquals(original.dimensions.getValue("existing").first, dimension.first)
        assertEquals(original.dimensions.getValue("existing").second, dimension.second)
        editor.cancelPointer()
        assertNull(editor.state.snap)
        assertNull(editor.state.dimensionFirst)
        assertNull(editor.state.magnifierTarget)
        assertEquals(original, editor.document)
        assertFalse(editor.canUndo)
    }
}
