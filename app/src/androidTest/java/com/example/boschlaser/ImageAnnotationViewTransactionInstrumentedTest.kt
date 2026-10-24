package com.example.boschlaser

import android.graphics.Bitmap
import com.example.boschlaser.shared.annotation.*
import android.os.Looper
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageAnnotationViewTransactionInstrumentedTest {
    private val original = DimensionMark(.25f, .5f, .75f, .5f, "1000")
    private fun view(): ImageAnnotationView {
        if (Looper.myLooper() == null) Looper.prepare()
        return ImageAnnotationView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
            setBitmap(Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888))
            restoreState(AnnotationState(dimensions = listOf(original)))
            layout(0, 0, 400, 400)
            setMode(AnnotationMode.MOVE)
        }
    }
    @Test fun cancelledBodyDragRestoresOriginalAndCreatesNoUndoRecord() {
        val view = view()
        touch(view, MotionEvent.ACTION_DOWN, 200f, 200f)
        assertEquals(AnnotationKind.DIMENSION, view.editorState().drag?.kind)
        assertEquals(PhotoPoint(.5, .5), view.editorState().drag?.start)
        assertFalse(view.editorState().drag!!.started)
        touch(view, MotionEvent.ACTION_MOVE, 240f, 230f)
        assertTrue(view.editorState().drag!!.started)
        assertNotEquals(original, view.snapshotState().dimensions.single())
        touch(view, MotionEvent.ACTION_CANCEL, 240f, 230f)
        assertNull(view.editorState().drag)
        assertEquals(original, view.snapshotState().dimensions.single())
        assertFalse(view.undo())
    }
    @Test fun multipleMovesUseAbsoluteDisplacementAndCommitOneUndo() {
        val view = view()
        var manipulationStarts = 0
        view.onManipulationStarted = { manipulationStarts++ }
        touch(view, MotionEvent.ACTION_DOWN, 200f, 200f)
        touch(view, MotionEvent.ACTION_MOVE, 220f, 220f)
        touch(view, MotionEvent.ACTION_MOVE, 240f, 230f)
        touch(view, MotionEvent.ACTION_UP, 240f, 230f)
        val moved = view.snapshotState().dimensions.single()
        assertEquals(1, manipulationStarts)
        assertNull(view.editorState().drag)
        assertEquals(.35f, moved.startX, .00001f)
        assertEquals(.575f, moved.startY, .00001f)
        assertTrue(view.undo())
        assertEquals(original, view.snapshotState().dimensions.single())
        assertFalse(view.undo())
    }
    @Test fun unchangedTapHasNoHistoryButPropertyEditCanBeUndone() {
        val view = view()
        touch(view, MotionEvent.ACTION_DOWN, 200f, 200f)
        touch(view, MotionEvent.ACTION_UP, 200f, 200f)
        assertFalse(view.undo())
        assertTrue(view.updateSelectedLabel("2000"))
        assertEquals("2000", view.snapshotState().dimensions.single().label)
        assertTrue(view.undo())
        assertEquals(original, view.snapshotState().dimensions.single())
    }
    @Test fun lifecycleCancellationKeepsSavedStateFreeOfGesturePreview() {
        val view = view()
        touch(view, MotionEvent.ACTION_DOWN, 200f, 200f)
        touch(view, MotionEvent.ACTION_MOVE, 240f, 230f)
        view.cancelGesture()
        assertNull(view.editorState().drag)
        assertEquals(original, view.snapshotState().dimensions.single())
        assertFalse(view.undo())
    }
    @Test fun editingPropertiesPreservesDoubleCoordinatesAndStableIdsForEveryKind() {
        val point = PhotoPoint(.123456789123, .234567891234)
        val items = listOf(
            DimensionAnnotation("dimension-id", point, PhotoPoint(.876543219876, point.y), "Original", -1) to PhotoPoint(.5, point.y),
            TextAnnotation("text-id", point, "Original", -1) to point,
            AngleAnnotation("angle-id", point, PhotoPoint(.8, point.y), PhotoPoint(point.x, .8), "Original", -1) to point,
            AreaAnnotation("area-id", listOf(point, PhotoPoint(.8, point.y), PhotoPoint(.5, .8)), "Original", -1) to PhotoPoint(.5, .4),
        )
        for ((annotation, hit) in items) {
            val view = view()
            val document = AnnotationDocument(mapOf(annotation.id to annotation), "photo")
            view.restoreDocument(document)
            val screen = view.photoToScreen(hit)
            touch(view, MotionEvent.ACTION_DOWN, screen.x, screen.y)
            touch(view, MotionEvent.ACTION_UP, screen.x, screen.y)
            assertEquals(annotation.id, view.editorState().selection?.annotationId)
            assertEquals(AnnotationMode.MOVE, view.editorState().mode)
            assertTrue("Select ${annotation.kind} before editing", when (annotation) {
                is DimensionAnnotation -> view.updateSelectedLabel("Edited")
                is TextAnnotation -> view.updateSelectedText("Edited")
                is AngleAnnotation -> view.updateSelectedAngleLabel("Edited")
                is AreaAnnotation -> view.updateSelectedAreaLabel("Edited")
            })
            assertTrue(view.updateSelectedColor(-65536))
            val edited = view.snapshotDocument().annotations.getValue(annotation.id)
            assertEquals(annotation.id, view.editorState().selection?.annotationId)
            assertEquals(annotation.points, edited.points)
            assertEquals(-65536, edited.colorArgb)
            assertTrue(view.undo())
            assertTrue(view.undo())
            assertEquals(document, view.snapshotDocument())
        }
    }
    @Test fun deletingTheSelectedLastAreaVertexKeepsTheOriginalRemainingVertexSelection() {
        val view = view()
        val area = AreaAnnotation("area", listOf(PhotoPoint(.1, .1), PhotoPoint(.9, .1),
            PhotoPoint(.9, .9), PhotoPoint(.1, .9)), "", -65536)
        view.restoreDocument(AnnotationDocument(mapOf(area.id to area)))
        var selection: AnnotationSelection? = null
        view.onSelectionChanged = { selection = it }
        val point = view.photoToScreen(area.points.last())
        touch(view, MotionEvent.ACTION_DOWN, point.x, point.y)
        touch(view, MotionEvent.ACTION_UP, point.x, point.y)
        assertEquals(AnnotationSelectionTarget(area.id, 3), view.editorState().selection)
        assertTrue(view.deleteSelectedAreaPoint())
        assertEquals(AnnotationSelectionTarget(area.id, 2), view.editorState().selection)
        assertEquals(2, (selection as AnnotationSelection.Area).selectedPointIndex)
        assertEquals(area.points.dropLast(1), view.snapshotDocument().annotations.getValue(area.id).points)
        assertTrue(view.undo())
        assertEquals(area, view.snapshotDocument().annotations.getValue(area.id))
        assertNull(view.editorState().selection)
    }

    @Test fun bodyDragOutsideThePhotoClampsTheWholeShapeAndToolSwitchRollsItBack() {
        val view = view()
        touch(view, MotionEvent.ACTION_DOWN, 200f, 200f)
        val target = view.editorState().drag!!.target
        touch(view, MotionEvent.ACTION_MOVE, 800f, -400f)
        val moved = view.snapshotState().dimensions.single()
        assertEquals(.5f, moved.startX, .00001f)
        assertEquals(1f, moved.endX, .00001f)
        assertEquals(0f, moved.startY, .00001f)
        assertEquals(0f, moved.endY, .00001f)
        assertEquals(target, view.editorState().drag?.target)
        view.setMode(AnnotationMode.TEXT)
        assertNull(view.editorState().drag)
        assertEquals(original, view.snapshotState().dimensions.single())
        assertFalse(view.undo())
    }

    private fun touch(view: ImageAnnotationView, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 0L, action, x, y, 0)
        try { view.onTouchEvent(event) } finally { event.recycle() }
    }
}
