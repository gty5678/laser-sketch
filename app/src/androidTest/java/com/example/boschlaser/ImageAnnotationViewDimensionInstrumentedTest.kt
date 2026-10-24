package com.example.boschlaser

import android.graphics.Bitmap
import com.example.boschlaser.shared.annotation.PhotoPoint
import android.os.Looper
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageAnnotationViewDimensionInstrumentedTest {
    // These tests exercise the MotionEvent adapter offscreen; AnnotationCanvasInstrumentedTest
    // measures an attached Compose host and verifies the actual pointer and drawing path.
    @Test
    fun dimensionDragShowsMagnifierUntilSecondPointIsConfirmed() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = ImageAnnotationView(context).apply {
            setBitmap(Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888))
            layout(0, 0, 400, 400)
        }
        val magnifierFocusX = privateField("magnifierFocusX")
        val magnifierFocusY = privateField("magnifierFocusY")
        var manipulationStartedCount = 0
        var confirmedSelection: AnnotationSelection? = null
        view.onManipulationStarted = { manipulationStartedCount += 1 }
        view.onSelectionChanged = { confirmedSelection = it }

        touch(view, MotionEvent.ACTION_DOWN, 80f, 100f)
        assertEquals(1, manipulationStartedCount)
        assertEquals(PhotoPoint(.2, .25), view.editorState().creation.dimension?.start)
        assertTrue(view.snapshotDocument().annotations.isEmpty())
        assertNull(magnifierFocusX.get(view))
        assertNull(magnifierFocusY.get(view))

        touch(view, MotionEvent.ACTION_MOVE, 260f, 280f)
        assertEquals(1, manipulationStartedCount)
        assertEquals(260f, magnifierFocusX.get(view) as Float, 0f)
        assertEquals(280f, magnifierFocusY.get(view) as Float, 0f)
        assertEquals(PhotoPoint(.65, .7), view.editorState().creation.dimension?.end)
        assertTrue(view.snapshotDocument().annotations.isEmpty())

        touch(view, MotionEvent.ACTION_UP, 300f, 320f)
        assertNull(magnifierFocusX.get(view))
        assertNull(magnifierFocusY.get(view))
        assertNotNull(confirmedSelection as? AnnotationSelection.Dimension)
        assertNull(view.editorState().creation.dimension)
        val dimension = view.snapshotState().dimensions.single()
        assertEquals(0.2f, dimension.startX, 0.001f)
        assertEquals(0.25f, dimension.startY, 0.001f)
        assertEquals(0.75f, dimension.endX, 0.001f)
        assertEquals(0.8f, dimension.endY, 0.001f)
    }

    @Test
    fun cancellingDimensionDragHidesMagnifierAndDoesNotAddMark() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = ImageAnnotationView(context).apply {
            setBitmap(Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888))
            layout(0, 0, 400, 400)
        }
        val magnifierFocusX = privateField("magnifierFocusX")

        touch(view, MotionEvent.ACTION_DOWN, 80f, 100f)
        touch(view, MotionEvent.ACTION_MOVE, 260f, 280f)
        assertNotNull(magnifierFocusX.get(view))

        touch(view, MotionEvent.ACTION_CANCEL, 260f, 280f)
        assertNull(magnifierFocusX.get(view))
        assertEquals(emptyList<DimensionMark>(), view.snapshotState().dimensions)
        assertNull(view.editorState().creation.dimension)
        assertFalse(view.undo())
    }

    @Test
    fun dimensionEndpointCanOnlyBeAdjustedFromMoveMode() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = DimensionMark(0.2f, 0.25f, 0.75f, 0.8f, "")
        val view = ImageAnnotationView(context).apply {
            setBitmap(Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888))
            restoreState(AnnotationState(dimensions = listOf(original)))
            layout(0, 0, 400, 400)
            setMode(AnnotationMode.MOVE)
        }

        // First tap selects the dimension; a subsequent drag on its endpoint edits that endpoint.
        touch(view, MotionEvent.ACTION_DOWN, 80f, 100f)
        touch(view, MotionEvent.ACTION_UP, 80f, 100f)
        touch(view, MotionEvent.ACTION_DOWN, 80f, 100f)
        touch(view, MotionEvent.ACTION_MOVE, 120f, 140f)
        touch(view, MotionEvent.ACTION_UP, 120f, 140f)

        val adjusted = view.snapshotState().dimensions.single()
        assertEquals(0.3f, adjusted.startX, 0.001f)
        assertEquals(0.35f, adjusted.startY, 0.001f)
        assertEquals(original.endX, adjusted.endX, 0.001f)
        assertEquals(original.endY, adjusted.endY, 0.001f)
    }

    @Test
    fun dimensionCreationCallbackOnlyFiresForAValidDimension() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val createdModes = mutableListOf<AnnotationMode>()
        val view = ImageAnnotationView(context).apply {
            setBitmap(Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888))
            layout(0, 0, 400, 400)
            onAnnotationCreated = { createdModes += it }
        }

        touch(view, MotionEvent.ACTION_DOWN, 80f, 100f)
        touch(view, MotionEvent.ACTION_UP, 82f, 102f)
        assertTrue(createdModes.isEmpty())

        touch(view, MotionEvent.ACTION_DOWN, 80f, 100f)
        touch(view, MotionEvent.ACTION_UP, 300f, 320f)
        assertEquals(listOf(AnnotationMode.DIMENSION), createdModes)
    }

    @Test
    fun switchingToMoveAfterCreationCanPreserveNewDimensionSelection() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = ImageAnnotationView(context).apply {
            setBitmap(Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888))
            layout(0, 0, 400, 400)
        }
        view.onAnnotationCreated = { view.setMode(AnnotationMode.MOVE, preserveSelection = true) }

        touch(view, MotionEvent.ACTION_DOWN, 80f, 100f)
        touch(view, MotionEvent.ACTION_UP, 300f, 320f)
        touch(view, MotionEvent.ACTION_DOWN, 80f, 100f)
        touch(view, MotionEvent.ACTION_MOVE, 120f, 140f)
        touch(view, MotionEvent.ACTION_UP, 120f, 140f)

        val adjusted = view.snapshotState().dimensions.single()
        assertEquals(0.3f, adjusted.startX, 0.001f)
        assertEquals(0.35f, adjusted.startY, 0.001f)
    }

    @Test
    fun angleAndAreaCallbacksFireOnlyAfterTheirThirdPoint() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val createdModes = mutableListOf<AnnotationMode>()
        val view = ImageAnnotationView(context).apply {
            setBitmap(Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888))
            layout(0, 0, 400, 400)
            onAnnotationCreated = { createdModes += it }
            setMode(AnnotationMode.ANGLE)
        }

        tap(view, 80f, 100f)
        tap(view, 180f, 180f)
        assertEquals(listOf(PhotoPoint(.2, .25), PhotoPoint(.45, .45)), view.editorState().creation.anglePoints)
        assertTrue(view.snapshotDocument().annotations.isEmpty())
        assertTrue(createdModes.isEmpty())
        tap(view, 300f, 100f)
        assertEquals(listOf(AnnotationMode.ANGLE), createdModes)
        assertTrue(view.editorState().creation.anglePoints.isEmpty())

        view.setMode(AnnotationMode.AREA)
        tap(view, 80f, 100f)
        tap(view, 300f, 100f)
        assertEquals(2, view.editorState().creation.areaPoints.size)
        assertEquals(1, view.snapshotDocument().annotations.size)
        assertEquals(listOf(AnnotationMode.ANGLE), createdModes)
        tap(view, 180f, 300f)
        assertEquals(listOf(AnnotationMode.ANGLE, AnnotationMode.AREA), createdModes)
        assertTrue(view.editorState().creation.areaPoints.isEmpty())
    }

    @Test
    fun textCreationCallbackFiresAfterTextIsAdded() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val createdModes = mutableListOf<AnnotationMode>()
        val view = ImageAnnotationView(context).apply {
            setBitmap(Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888))
            layout(0, 0, 400, 400)
            onAnnotationCreated = { createdModes += it }
            setMode(AnnotationMode.TEXT)
        }

        tap(view, 180f, 180f)

        assertEquals(listOf(AnnotationMode.TEXT), createdModes)
        assertEquals(1, view.snapshotState().texts.size)
    }

    @Test
    fun cancellingATouchKeepsConfirmedAngleClicksButChangingToolsClearsThem() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = ImageAnnotationView(context).apply {
            setBitmap(Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888))
            layout(0, 0, 400, 400)
            setMode(AnnotationMode.ANGLE)
        }
        tap(view, 80f, 100f)
        touch(view, MotionEvent.ACTION_DOWN, 180f, 180f)
        touch(view, MotionEvent.ACTION_CANCEL, 180f, 180f)
        assertEquals(listOf(PhotoPoint(.2, .25)), view.editorState().creation.anglePoints)
        view.cancelGesture()
        assertEquals(listOf(PhotoPoint(.2, .25)), view.editorState().creation.anglePoints)
        assertTrue(view.snapshotDocument().annotations.isEmpty())
        view.setMode(AnnotationMode.AREA)
        assertTrue(view.editorState().creation.anglePoints.isEmpty())
        assertTrue(view.editorState().creation.areaPoints.isEmpty())
    }

    private fun tap(view: ImageAnnotationView, x: Float, y: Float) {
        touch(view, MotionEvent.ACTION_DOWN, x, y)
        touch(view, MotionEvent.ACTION_UP, x, y)
    }

    private fun touch(view: ImageAnnotationView, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 0L, action, x, y, 0)
        try {
            view.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun privateField(name: String) = ImageAnnotationView::class.java.getDeclaredField(name).apply {
        isAccessible = true
    }
}
