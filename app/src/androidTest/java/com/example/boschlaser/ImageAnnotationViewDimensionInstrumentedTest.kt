package com.example.boschlaser

import android.graphics.Bitmap
import android.os.Looper
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageAnnotationViewDimensionInstrumentedTest {
    @Test
    fun dimensionDragShowsMagnifierUntilSecondPointIsConfirmed() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = ImageAnnotationView(context).apply {
            setBitmap(Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888))
            measure(exactly(400), exactly(400))
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
        assertNull(magnifierFocusX.get(view))
        assertNull(magnifierFocusY.get(view))

        touch(view, MotionEvent.ACTION_MOVE, 260f, 280f)
        assertEquals(1, manipulationStartedCount)
        assertEquals(260f, magnifierFocusX.get(view) as Float, 0f)
        assertEquals(280f, magnifierFocusY.get(view) as Float, 0f)

        touch(view, MotionEvent.ACTION_UP, 300f, 320f)
        assertNull(magnifierFocusX.get(view))
        assertNull(magnifierFocusY.get(view))
        assertNotNull(confirmedSelection as? AnnotationSelection.Dimension)
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
            measure(exactly(400), exactly(400))
            layout(0, 0, 400, 400)
        }
        val magnifierFocusX = privateField("magnifierFocusX")

        touch(view, MotionEvent.ACTION_DOWN, 80f, 100f)
        touch(view, MotionEvent.ACTION_MOVE, 260f, 280f)
        assertNotNull(magnifierFocusX.get(view))

        touch(view, MotionEvent.ACTION_CANCEL, 260f, 280f)
        assertNull(magnifierFocusX.get(view))
        assertEquals(emptyList<DimensionMark>(), view.snapshotState().dimensions)
    }

    @Test
    fun dimensionEndpointCanOnlyBeAdjustedFromMoveMode() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = DimensionMark(0.2f, 0.25f, 0.75f, 0.8f, "")
        val view = ImageAnnotationView(context).apply {
            setBitmap(Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888))
            restoreState(AnnotationState(dimensions = listOf(original)))
            measure(exactly(400), exactly(400))
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

    private fun touch(view: ImageAnnotationView, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 0L, action, x, y, 0)
        try {
            view.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun exactly(size: Int): Int = android.view.View.MeasureSpec.makeMeasureSpec(
        size,
        android.view.View.MeasureSpec.EXACTLY,
    )

    private fun privateField(name: String) = ImageAnnotationView::class.java.getDeclaredField(name).apply {
        isAccessible = true
    }
}
