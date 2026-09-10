package com.example.boschlaser

import android.os.Looper
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchViewWallPlacementInstrumentedTest {
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
}
