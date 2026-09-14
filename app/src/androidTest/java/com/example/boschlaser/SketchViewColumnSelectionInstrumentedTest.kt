package com.example.boschlaser

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchViewColumnSelectionInstrumentedTest {
    @Test
    fun columnHitAreaFollowsItsShapeWithSmallTouchTolerance() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = SketchView(context)
        privateField("scale").setFloat(view, 1f)
        val tolerance = 14f * context.resources.displayMetrics.density

        val rectangle = SketchColumn(
            id = "rectangle",
            type = SketchColumnType.RECTANGLE,
            center = SketchPoint(300f, 300f),
            width = 100f,
            depth = 200f,
        )
        view.restoreState(SketchState(columns = listOf(rectangle)))
        assertEquals(rectangle, findColumn(view, 350f + tolerance - 1f, 300f))
        assertNull(findColumn(view, 350f + tolerance + 1f, 300f))
        assertNull(findColumn(view, 300f, 400f + tolerance + 1f))

        val circle = SketchColumn(
            id = "circle",
            type = SketchColumnType.CIRCLE,
            center = SketchPoint(300f, 300f),
            width = 100f,
            depth = 100f,
        )
        view.restoreState(SketchState(columns = listOf(circle)))
        assertEquals(circle, findColumn(view, 350f + tolerance - 1f, 300f))
        assertNull(findColumn(view, 350f + tolerance + 1f, 300f))
    }

    private fun privateField(name: String) = SketchView::class.java.getDeclaredField(name).apply {
        isAccessible = true
    }

    private fun findColumn(view: SketchView, x: Float, y: Float): SketchColumn? {
        val method = SketchView::class.java.getDeclaredMethod(
            "findColumn",
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        return method.invoke(view, x, y) as SketchColumn?
    }
}
