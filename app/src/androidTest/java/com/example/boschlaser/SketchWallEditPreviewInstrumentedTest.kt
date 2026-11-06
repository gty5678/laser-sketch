package com.example.boschlaser

import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.shared.editor.WallEditOperation
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchWallEditPreviewInstrumentedTest {
    @get:Rule val foregroundHost: org.junit.rules.TestRule = ForegroundTestHostRule()

    @Test fun secondWallPressPreviewsCancelRestoresAndReleaseCommitsOnce() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val horizontal = Wall("horizontal", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
            val vertical = Wall("vertical", Vec2(1000.0, -1000.0), Vec2(1000.0, 1000.0))
            val document = SketchDocument(walls = mapOf(horizontal.id to horizontal, vertical.id to vertical))
            val view = SketchView(instrumentation.targetContext)
            view.layout(0, 0, 600, 600)
            view.restoreDocument(document)
            for ((field, value) in listOf("scale" to .125f, "offsetX" to 200f, "offsetY" to 200f)) {
                SketchView::class.java.getDeclaredField(field).apply { isAccessible = true }.setFloat(view, value)
            }
            fun touch(action: Int, x: Float, y: Float) {
                val event = MotionEvent.obtain(0, 0, action, x, y, 0)
                try { assertTrue(view.onTouchEvent(event)) } finally { event.recycle() }
            }
            fun renderedStart() = view.snapshotState().walls.first { it.id == horizontal.id }.start.x
            view.startWallEdit(WallEditOperation.TRIM)
            touch(MotionEvent.ACTION_DOWN, 437.5f, 200f)
            touch(MotionEvent.ACTION_UP, 437.5f, 200f)
            touch(MotionEvent.ACTION_DOWN, 325f, 137.5f)
            assertEquals(1000f, renderedStart(), .001f)
            assertEquals(document, view.snapshotDocument())
            touch(MotionEvent.ACTION_CANCEL, 325f, 137.5f)
            assertEquals(0f, renderedStart(), .001f)
            assertFalse(view.undo())
            touch(MotionEvent.ACTION_DOWN, 325f, 137.5f)
            touch(MotionEvent.ACTION_UP, 325f, 137.5f)
            assertEquals(Vec2(1000.0, 0.0), view.snapshotDocument().walls.getValue(horizontal.id).start)
            assertTrue(view.undo())
            assertEquals(document, view.snapshotDocument())
            assertFalse(view.undo())
        }
    }
}
