package com.example.boschlaser

import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.shared.editor.Selection
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.RectColumn
import com.example.boschlaser.shared.model.SketchDocument
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchNavigationInstrumentedTest {
    @get:org.junit.Rule val foregroundHost: org.junit.rules.TestRule = ForegroundTestHostRule()

    @Test fun unselectedDragPansAndHandToolPansOverSelectedObject() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = SketchView(InstrumentationRegistry.getInstrumentation().targetContext)
            val column = RectColumn("column", Vec2(1000.0, 1000.0), 400.0, 400.0)
            val initial = SketchDocument(columns = mapOf(column.id to column))
            view.restoreDocument(initial)
            val start = view.worldToScreen(column.center)
            fun touch(action: Int, point: Vec2) {
                val event = MotionEvent.obtain(0, 0, action, point.x.toFloat(), point.y.toFloat(), 0)
                try { view.onTouchEvent(event) } finally { event.recycle() }
            }
            touch(MotionEvent.ACTION_DOWN, start)
            touch(MotionEvent.ACTION_MOVE, start + Vec2(50.0, 60.0))
            touch(MotionEvent.ACTION_UP, start + Vec2(50.0, 60.0))
            assertEquals(initial, view.snapshotDocument())
            assertNull(view.editorState().selection)
            val shifted = view.worldToScreen(column.center)
            assertEquals(start.x + 50.0, shifted.x, .01)
            assertEquals(start.y + 60.0, shifted.y, .01)
            touch(MotionEvent.ACTION_DOWN, shifted)
            touch(MotionEvent.ACTION_UP, shifted)
            assertEquals(Selection.Column(column.id), view.editorState().selection)
            view.setMode(SketchMode.PAN)
            touch(MotionEvent.ACTION_DOWN, shifted)
            touch(MotionEvent.ACTION_MOVE, shifted + Vec2(40.0, 0.0))
            touch(MotionEvent.ACTION_UP, shifted + Vec2(40.0, 0.0))
            assertEquals(initial, view.snapshotDocument())
            assertEquals(shifted.x + 40.0, view.worldToScreen(column.center).x, .01)
            assertFalse(view.undo())
        }
    }

    @Test fun twoFingersCancelPreviewPanZoomAndIgnoreRemainingFinger() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = SketchView(InstrumentationRegistry.getInstrumentation().targetContext)
            val column = RectColumn("column", Vec2(1000.0, 1000.0), 400.0, 400.0)
            val initial = SketchDocument(columns = mapOf(column.id to column))
            view.restoreDocument(initial)
            view.selectEntity(Selection.Column(column.id))
            val start = view.worldToScreen(column.center)
            val time = SystemClock.uptimeMillis()
            var tick = 0L
            fun send(action: Int, vararg points: Vec2) {
                val properties = Array(points.size) { MotionEvent.PointerProperties().apply {
                    id = it; toolType = MotionEvent.TOOL_TYPE_FINGER
                } }
                val coordinates = Array(points.size) { i -> MotionEvent.PointerCoords().apply {
                    x = points[i].x.toFloat(); y = points[i].y.toFloat(); pressure = 1f; size = 1f
                } }
                tick += 30
                val event = MotionEvent.obtain(time, time + tick, action, points.size, properties, coordinates,
                    0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
                try { view.onTouchEvent(event) } finally { event.recycle() }
            }
            val first = start + Vec2(40.0, 40.0)
            val second = first + Vec2(240.0, 0.0)
            send(MotionEvent.ACTION_DOWN, start)
            send(MotionEvent.ACTION_MOVE, first)
            assertNotEquals(initial, view.snapshotDocument())
            send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), first, second)
            assertEquals(initial, view.snapshotDocument())
            assertNull(view.editorState().magnifierTarget)
            val origin = view.worldToScreen(Vec2(0.0, 0.0))
            send(MotionEvent.ACTION_MOVE, first + Vec2(50.0, 30.0), second + Vec2(50.0, 30.0))
            assertEquals(origin.x + 50.0, view.worldToScreen(Vec2(0.0, 0.0)).x, .01)
            assertEquals(origin.y + 30.0, view.worldToScreen(Vec2(0.0, 0.0)).y, .01)
            fun span() = view.worldToScreen(Vec2(1000.0, 0.0)).x - view.worldToScreen(Vec2(0.0, 0.0)).x
            val before = span()
            for (step in 1..5) send(MotionEvent.ACTION_MOVE, first + Vec2(50.0 - step * 20.0, 30.0),
                second + Vec2(50.0 + step * 20.0, 30.0))
            assertTrue(span() > before)
            send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), first, second)
            send(MotionEvent.ACTION_MOVE, first + Vec2(100.0, 100.0))
            send(MotionEvent.ACTION_UP, first + Vec2(100.0, 100.0))
            assertEquals(initial, view.snapshotDocument())
            assertFalse(view.undo())
        }
    }
}
