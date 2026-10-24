package com.example.boschlaser

import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.shared.annotation.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class AnnotationHitTestInstrumentedTest {
    @get:org.junit.Rule val foregroundHost: org.junit.rules.TestRule = ForegroundTestHostRule()

    @Test fun nativePointerAdapterKeepsTextAndSelectedControlPriority() = withView { view ->
        val dimension = DimensionAnnotation("dimension", PhotoPoint(.1, .5), PhotoPoint(.9, .5), "", -1)
        val text = TextAnnotation("text", PhotoPoint(.5, .5), "label", -1)
        val area = square("area")
        view.restoreDocument(AnnotationDocument(linkedMapOf(area.id to area, dimension.id to dimension, text.id to text)))
        tap(view, PhotoPoint(.5, .5))
        assertEquals(AnnotationSelectionTarget(text.id), view.editorState().selection)
        assertFalse(view.undo())
        val endpointText = text.copy(position = dimension.start)
        val endpointArea = area.copy(points = listOf(dimension.start, PhotoPoint(.95, .05), PhotoPoint(.95, .95), PhotoPoint(.05, .95)))
        view.restoreDocument(AnnotationDocument(linkedMapOf(area.id to endpointArea, dimension.id to dimension, text.id to endpointText)))
        tap(view, PhotoPoint(.5, .5))
        assertEquals(AnnotationSelectionTarget(dimension.id), view.editorState().selection)
        touch(view, MotionEvent.ACTION_DOWN, dimension.start)
        assertEquals(AnnotationSelectionTarget(dimension.id, 0), view.editorState().drag?.target)
        touch(view, MotionEvent.ACTION_CANCEL, dimension.start)
        assertNull(view.editorState().drag)
        assertEquals(dimension, view.snapshotDocument().annotations[dimension.id])
    }

    @Test fun overlappingAreaInteriorsPickTheLastDocumentEntryThroughTheNativeAdapter() = withView { view ->
        val first = square("first")
        val second = square("second")
        val document = AnnotationDocument(linkedMapOf(first.id to first, second.id to second))
        view.restoreDocument(document)
        tap(view, PhotoPoint(.5, .5))
        assertEquals(AnnotationSelectionTarget(second.id), view.editorState().selection)
        assertEquals(document, view.snapshotDocument())
        assertFalse(view.undo())
    }

    @Test fun closingEdgeDoubleTapUsesEventTimeAndOneSharedUndoTransaction() = withView { view ->
        val area = AreaAnnotation("area", listOf(PhotoPoint(.1, .1), PhotoPoint(.9, .1), PhotoPoint(.9, .9)), "", -1)
        val document = AnnotationDocument(mapOf(area.id to area))
        view.restoreDocument(document)
        val messages = mutableListOf<String>()
        view.onMessage = { messages += it.text }
        tap(view, PhotoPoint(.5, .5), 100)
        assertEquals(area.id, view.editorState().areaEdgeTap?.areaId)
        assertEquals(2, view.editorState().areaEdgeTap?.edgeIndex)
        assertEquals(100L, view.editorState().areaEdgeTap?.atMillis)
        assertEquals(document, view.snapshotDocument())
        assertFalse(view.undo())
        tap(view, PhotoPoint(.5, .5), 200)
        assertNull(view.editorState().areaEdgeTap)
        assertEquals(AnnotationSelectionTarget(area.id, 3), view.editorState().selection)
        assertEquals(area.points + PhotoPoint(.5, .5), view.snapshotDocument().annotations.getValue(area.id).points)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(listOf(context.getString(R.string.annotation_area_point_add_again),
            context.getString(R.string.annotation_area_point_added)), messages)
        assertTrue(view.undo())
        assertEquals(document, view.snapshotDocument())
        assertFalse(view.undo())
    }

    private fun square(id: String) = AreaAnnotation(id, listOf(PhotoPoint(.05, .05), PhotoPoint(.95, .05),
        PhotoPoint(.95, .95), PhotoPoint(.05, .95)), "", -1)

    private fun withView(test: (ImageAnnotationView) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val source = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            try {
                val side = (400 * instrumentation.targetContext.resources.displayMetrics.density).roundToInt()
                val view = ImageAnnotationView(instrumentation.targetContext).apply {
                    setBitmap(source)
                    layout(0, 0, side, side)
                    setMode(AnnotationMode.MOVE)
                }
                test(view)
            } finally { source.recycle() }
        }
    }

    private fun tap(view: ImageAnnotationView, point: PhotoPoint, atMillis: Long = 0) {
        touch(view, MotionEvent.ACTION_DOWN, point, atMillis)
        touch(view, MotionEvent.ACTION_UP, point, atMillis)
    }

    private fun touch(view: ImageAnnotationView, action: Int, point: PhotoPoint, atMillis: Long = 0) {
        val screen = view.photoToScreen(point)
        val event = MotionEvent.obtain(atMillis, atMillis, action, screen.x, screen.y, 0)
        try { view.onTouchEvent(event) } finally { event.recycle() }
    }
}
