package com.example.boschlaser

import android.os.Looper
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.shared.geometry.Vec2
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchOpeningMagnifierInstrumentedTest {
    @Test fun movingDoorAndWindowShowsMagnifierAndAllGestureEndingsHideIt() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (type in SketchOpeningType.entries) {
            for (ending in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN)) {
                val view = SketchView(context)
                view.restoreState(SketchState(
                    walls = listOf(SketchWall("wall", SketchPoint(0f, 0f), SketchPoint(3000f, 0f))),
                    openings = listOf(SketchOpening("opening", type, "wall", 1f / 3f, 600f))))
                val original = view.snapshotDocument()
                fun touch(action: Int, point: Vec2) {
                    val screen = view.worldToScreen(point)
                    val event = MotionEvent.obtain(0L, 0L, action, screen.x.toFloat(), screen.y.toFloat(), 0)
                    try { view.onTouchEvent(event) } finally { event.recycle() }
                }
                val segment = com.example.boschlaser.shared.geometry.OpeningGeometry.symbolSegments(
                    original.openings.getValue("opening"), original.walls.getValue("wall")).first()
                touch(MotionEvent.ACTION_DOWN, (segment.start + segment.end) * 0.5)
                touch(MotionEvent.ACTION_MOVE, Vec2(1600.0, 0.0))
                assertNotNull(view.editorState().magnifierTarget)
                assertEquals(1600.0, view.editorState().magnifierTarget!!.x, .01)
                touch(ending, Vec2(1600.0, 0.0))
                assertNull(view.editorState().magnifierTarget)
                if (ending == MotionEvent.ACTION_UP) assertNotEquals(original, view.snapshotDocument())
                else assertEquals(original, view.snapshotDocument())
            }
        }
    }
}
