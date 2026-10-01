package com.example.boschlaser

import android.os.Looper
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchViewSharedEndpointDetachInstrumentedTest {
    @Test
    fun twoWallEndpointsMoveTogether() {
        val joint = SketchPoint(500f, 500f)
        val horizontal = SketchWall(id = "horizontal", start = joint, end = SketchPoint(1000f, 500f))
        val selected = SketchWall(id = "selected", start = joint, end = SketchPoint(500f, 1000f))
        val view = newView(listOf(horizontal, selected), selected.id)

        dragSharedEndpoint(view, joint, SketchPoint(650f, 500f))

        val walls = view.snapshotState().walls.associateBy { it.id }
        assertEquals(SketchPoint(650f, 500f), walls.getValue(selected.id).start)
        assertEquals(SketchPoint(650f, 500f), walls.getValue(horizontal.id).start)

        assertTrue(view.undo())
        view.snapshotState().walls.forEach { wall -> assertEquals(joint, wall.start) }
    }

    @Test
    fun selectedWallEndpointCanDetachFromThreeWallJunction() {
        val joint = SketchPoint(500f, 500f)
        val horizontal = SketchWall(id = "horizontal", start = joint, end = SketchPoint(1000f, 500f))
        val selected = SketchWall(id = "selected", start = joint, end = SketchPoint(500f, 1000f))
        val diagonal = SketchWall(id = "diagonal", start = joint, end = SketchPoint(100f, 100f))
        val view = newView(listOf(horizontal, selected, diagonal), selected.id)

        dragSharedEndpoint(view, joint, SketchPoint(650f, 500f))

        val walls = view.snapshotState().walls.associateBy { it.id }
        assertEquals(SketchPoint(650f, 500f), walls.getValue(selected.id).start)
        assertEquals(joint, walls.getValue(horizontal.id).start)
        assertEquals(joint, walls.getValue(diagonal.id).start)

        assertTrue(view.undo())
        assertEquals(joint, view.snapshotState().walls.single { it.id == selected.id }.start)
    }

    private fun newView(walls: List<SketchWall>, selectedWallId: String): SketchView {
        if (Looper.myLooper() == null) Looper.prepare()
        return SketchView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
            setGridSnapEnabled(false)
            restoreState(SketchState(walls = walls))
            privateField("scale").setFloat(this, 1f)
            privateField("selectedWallId").set(this, selectedWallId)
        }
    }

    private fun dragSharedEndpoint(view: SketchView, from: SketchPoint, to: SketchPoint) {
        touch(view, MotionEvent.ACTION_DOWN, from.x, from.y)
        touch(view, MotionEvent.ACTION_MOVE, to.x, to.y)
        touch(view, MotionEvent.ACTION_UP, to.x, to.y)
    }

    private fun privateField(name: String) = SketchView::class.java.getDeclaredField(name).apply {
        isAccessible = true
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
