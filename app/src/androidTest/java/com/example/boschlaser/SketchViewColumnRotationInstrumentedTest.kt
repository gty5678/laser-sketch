package com.example.boschlaser

import android.os.Looper
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchViewColumnRotationInstrumentedTest {
    @Test
    fun draggingRotationHandleSnapsRectangleToFifteenDegrees() {
        val view = newView()
        val column = rectangle()
        view.restoreState(SketchState(columns = listOf(column)))
        privateField("selectedColumnId").set(view, column.id)
        val handle = rotationHandle(view, column)
        val radians = Math.toRadians(-53.0)
        val pointer = SketchPoint(
            column.center.x + cos(radians).toFloat() * 300f,
            column.center.y + sin(radians).toFloat() * 300f,
        )

        touch(view, MotionEvent.ACTION_DOWN, handle.x, handle.y)
        touch(view, MotionEvent.ACTION_MOVE, pointer.x, pointer.y)
        assertNull(privateField("magnifierTarget").get(view))
        touch(view, MotionEvent.ACTION_UP, pointer.x, pointer.y)

        assertEquals(30f, view.snapshotState().columns.single().rotationDegrees, .001f)
    }

    @Test
    fun rotatedRectangleHitAreaFollowsRotatedOutline() {
        val view = newView()
        val column = rectangle().copy(width = 100f, depth = 300f, rotationDegrees = 90f)
        view.restoreState(SketchState(columns = listOf(column)))

        assertEquals(column, findColumn(view, column.center.x + 140f, column.center.y))
        assertNull(findColumn(view, column.center.x, column.center.y + 140f))
    }

    @Test
    fun columnRotationSurvivesProjectRoundTrip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val projectId = SketchProjectStore.create(context)
        try {
            val column = rectangle().copy(rotationDegrees = 315f)
            assertTrue(SketchProjectStore.save(context, projectId, SketchState(columns = listOf(column))))
            assertEquals(315f, SketchProjectStore.load(context, projectId)?.state?.columns?.single()?.rotationDegrees ?: -1f, .001f)
        } finally {
            SketchProjectStore.delete(context, projectId)
        }
    }

    private fun newView(): SketchView {
        if (Looper.myLooper() == null) Looper.prepare()
        return SketchView(InstrumentationRegistry.getInstrumentation().targetContext).also {
            privateField("scale").setFloat(it, 1f)
            privateField("offsetX").setFloat(it, 0f)
            privateField("offsetY").setFloat(it, 0f)
        }
    }

    private fun rectangle() = SketchColumn(
        id = "column",
        type = SketchColumnType.RECTANGLE,
        center = SketchPoint(300f, 300f),
        width = 200f,
        depth = 200f,
    )

    private fun rotationHandle(view: SketchView, column: SketchColumn): SketchPoint =
        SketchView::class.java.getDeclaredMethod(
            "columnRotationHandleScreen",
            SketchColumn::class.java,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
        ).apply { isAccessible = true }.invoke(view, column, 1f, 0f, 0f) as SketchPoint

    private fun findColumn(view: SketchView, x: Float, y: Float): SketchColumn? =
        SketchView::class.java.getDeclaredMethod(
            "findColumn",
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
        ).apply { isAccessible = true }.invoke(view, x, y) as SketchColumn?

    private fun touch(view: SketchView, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 0L, action, x, y, 0)
        try {
            view.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun privateField(name: String) = SketchView::class.java.getDeclaredField(name).apply {
        isAccessible = true
    }
}
