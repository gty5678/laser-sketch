package com.example.boschlaser

import com.example.boschlaser.shared.editor.ToolType
import com.example.boschlaser.shared.editor.SketchPreview

import android.os.Looper
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchViewColumnPlacementInstrumentedTest {
    @Test
    fun columnIsPreviewedWithMagnifierAndCreatedOnlyOnFingerUp() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = SketchView(context).apply { setMode(SketchMode.RECT_COLUMN) }

        touch(view, MotionEvent.ACTION_DOWN, 100f, 100f)
        assertNotNull((view.editorState().preview as? SketchPreview.Column)?.column?.center)
        assertNotNull(view.editorState().magnifierTarget)
        assertTrue(view.snapshotState().columns.isEmpty())

        touch(view, MotionEvent.ACTION_MOVE, 160f, 160f)
        assertNotNull((view.editorState().preview as? SketchPreview.Column)?.column?.center)
        assertNotNull(view.editorState().magnifierTarget)
        assertTrue(view.snapshotState().columns.isEmpty())

        touch(view, MotionEvent.ACTION_UP, 180f, 180f)
        assertEquals(1, view.snapshotState().columns.size)
        assertEquals(SketchPoint(1500f, 1500f), view.snapshotState().columns.single().center)
        assertEquals(ToolType.RECT_COLUMN, view.editorState().tool)
        assertEquals(view.snapshotState().columns.single().id, view.selectedColumn()?.id)
        assertNull(view.editorState().magnifierTarget)
        assertNull((view.editorState().preview as? SketchPreview.Column)?.column?.center)
        assertNull(view.editorState().magnifierTarget)

        touch(view, MotionEvent.ACTION_DOWN, 260f, 260f)
        touch(view, MotionEvent.ACTION_MOVE, 300f, 300f)
        touch(view, MotionEvent.ACTION_CANCEL, 300f, 300f)
        assertEquals(1, view.snapshotState().columns.size)
        assertNull((view.editorState().preview as? SketchPreview.Column)?.column?.center)
        assertNull(view.editorState().magnifierTarget)

        touch(view, MotionEvent.ACTION_DOWN, 360f, 360f)
        assertNotNull(view.editorState().magnifierTarget)
        touch(view, MotionEvent.ACTION_UP, 360f, 360f)
        assertEquals(2, view.snapshotState().columns.size)
        assertEquals(ToolType.RECT_COLUMN, view.editorState().tool)
    }

    private fun touch(view: SketchView, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 0L, action, x, y, 0)
        try {
            view.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}
