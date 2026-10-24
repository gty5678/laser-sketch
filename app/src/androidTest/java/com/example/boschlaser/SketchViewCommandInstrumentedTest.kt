package com.example.boschlaser

import android.os.Looper
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchViewCommandInstrumentedTest {
    @Test
    fun cancelledDragRestoresObjectAndDoesNotSaveOrConsumeUndo() {
        val view = newView()
        val original = columnState()
        view.restoreState(original)
        var saves = 0
        view.onStateChanged = { saves++ }
        touch(view, MotionEvent.ACTION_DOWN, 120f, 120f)
        touch(view, MotionEvent.ACTION_MOVE, 180f, 180f)
        assertNotEquals(original, view.snapshotState())
        touch(view, MotionEvent.ACTION_CANCEL, 180f, 180f)
        assertEquals(original, view.snapshotState())
        assertEquals(0, saves)
        assertFalse(view.undo())
    }

    @Test
    fun wholeDragCommitsOneCommandAndUndoSavesRestoredState() {
        val view = newView()
        val original = columnState()
        view.restoreState(original)
        var saves = 0
        view.onStateChanged = { saves++ }
        touch(view, MotionEvent.ACTION_DOWN, 120f, 120f)
        touch(view, MotionEvent.ACTION_MOVE, 150f, 150f)
        touch(view, MotionEvent.ACTION_MOVE, 180f, 180f)
        assertEquals(0, saves)
        touch(view, MotionEvent.ACTION_UP, 180f, 180f)
        assertNotEquals(original, view.snapshotState())
        assertEquals(1, saves)
        assertTrue(view.undo())
        assertEquals(original, view.snapshotState())
        assertEquals(2, saves)
        assertFalse(view.undo())
    }

    @Test
    fun unchangedTapDoesNotConsumeUndo() {
        val view = newView()
        view.restoreState(columnState())
        touch(view, MotionEvent.ACTION_DOWN, 120f, 120f)
        touch(view, MotionEvent.ACTION_UP, 120f, 120f)
        assertFalse(view.undo())
    }

    private fun newView(): SketchView {
        if (Looper.myLooper() == null) Looper.prepare()
        return SketchView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
            setGridSnapEnabled(false)
        }
    }
    private fun columnState() = SketchState(columns = listOf(SketchColumn(
        id = "test-column", type = SketchColumnType.RECTANGLE, center = SketchPoint(1000f, 1000f),
    )))
    private fun touch(view: SketchView, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 0L, action, x, y, 0)
        try { view.onTouchEvent(event) } finally { event.recycle() }
    }
}
