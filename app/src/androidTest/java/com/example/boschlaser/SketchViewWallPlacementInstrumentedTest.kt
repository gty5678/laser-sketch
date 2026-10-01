package com.example.boschlaser

import android.os.Looper
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchViewWallPlacementInstrumentedTest {
    @Test
    fun continuousWallPlacementInheritsChangedControlLine() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = SketchWall(
            id = "first",
            start = SketchPoint(0f, 0f),
            end = SketchPoint(1000f, 0f),
            thickness = 240f,
        )
        val view = SketchView(context).apply {
            setGridSnapEnabled(false)
            setMode(SketchMode.WALL)
            restoreState(SketchState(walls = listOf(first)))
        }
        privateField("selectedWallId").set(view, first.id)
        privateField("wallStart").set(view, first.end)

        assertEquals(true, view.updateSelectedWallControlLine(SketchWallControlLine.OUTER))
        handleWall(view, MotionEvent.ACTION_DOWN, SketchPoint(2000f, 0f))
        handleWall(view, MotionEvent.ACTION_UP, SketchPoint(2000f, 0f))

        val walls = view.snapshotState().walls
        assertEquals(2, walls.size)
        val next = walls.last()
        assertEquals(SketchWallControlLine.OUTER, next.controlLine)
        val (controlStart, controlEnd) = wallControlLinePoints(next)
        assertEquals(SketchPoint(1000f, 0f), controlStart)
        assertEquals(SketchPoint(2000f, 0f), controlEnd)
        assertEquals(-120f, next.start.y, .001f)
        assertEquals(-120f, next.end.y, .001f)
    }

    @Test
    fun magnifierFollowsWallPreviewUntilFingerIsReleasedOrCancelled() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = SketchView(context).apply { setMode(SketchMode.WALL) }
        val magnifierTarget = SketchView::class.java.getDeclaredField("magnifierTarget").apply {
            isAccessible = true
        }
        val wallStart = SketchView::class.java.getDeclaredField("wallStart").apply {
            isAccessible = true
        }

        touch(view, MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(view, MotionEvent.ACTION_MOVE, 140f, 140f)
        assertNotNull(magnifierTarget.get(view))
        touch(view, MotionEvent.ACTION_UP, 160f, 160f)
        assertNull(magnifierTarget.get(view))
        val confirmedStart = wallStart.get(view) as SketchPoint
        val expectedStart = SketchPoint(1300f, 1300f)
        org.junit.Assert.assertEquals(expectedStart, confirmedStart)

        touch(view, MotionEvent.ACTION_DOWN, 300f, 100f)
        touch(view, MotionEvent.ACTION_MOVE, 340f, 140f)
        assertNotNull(magnifierTarget.get(view))
        touch(view, MotionEvent.ACTION_UP, 340f, 140f)
        assertNull(magnifierTarget.get(view))

        touch(view, MotionEvent.ACTION_DOWN, 500f, 100f)
        touch(view, MotionEvent.ACTION_MOVE, 540f, 140f)
        assertNotNull(magnifierTarget.get(view))
        touch(view, MotionEvent.ACTION_CANCEL, 540f, 140f)
        assertNull(magnifierTarget.get(view))

        view.setMode(SketchMode.WALL)
        touch(view, MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(view, MotionEvent.ACTION_MOVE, 140f, 140f)
        assertNotNull(magnifierTarget.get(view))
        touch(view, MotionEvent.ACTION_CANCEL, 140f, 140f)
        assertNull(magnifierTarget.get(view))
        assertNull(wallStart.get(view))
    }

    private fun touch(view: SketchView, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 0L, action, x, y, 0)
        try {
            view.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun handleWall(view: SketchView, action: Int, point: SketchPoint) {
        val event = MotionEvent.obtain(0L, 0L, action, 0f, 0f, 0)
        try {
            SketchView::class.java.getDeclaredMethod(
                "handleWall",
                MotionEvent::class.java,
                SketchPoint::class.java,
            ).apply { isAccessible = true }.invoke(view, event, point)
        } finally {
            event.recycle()
        }
    }

    private fun privateField(name: String) = SketchView::class.java.getDeclaredField(name).apply {
        isAccessible = true
    }
}
