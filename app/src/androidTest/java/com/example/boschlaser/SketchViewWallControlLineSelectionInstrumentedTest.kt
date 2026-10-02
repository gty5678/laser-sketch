package com.example.boschlaser

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchViewWallControlLineSelectionInstrumentedTest {
    @Test
    fun switchingControlLineKeepsLocatorFixedAndMovesWallBody() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wall = SketchWall(
            id = "wall",
            start = SketchPoint(100f, 200f),
            end = SketchPoint(1100f, 200f),
            thickness = 240f,
        )
        val view = SketchView(context).apply {
            setGridSnapEnabled(false)
            restoreState(SketchState(walls = listOf(wall)))
        }
        privateField("scale").setFloat(view, 1f)
        privateField("selectedWallId").set(view, wall.id)

        assertEquals(true, view.updateSelectedWallControlLine(SketchWallControlLine.INNER))
        val innerWall = view.snapshotState().walls.single()
        assertEquals(SketchPoint(100f, 320f), innerWall.start)
        assertEquals(SketchPoint(1100f, 320f), innerWall.end)
        assertEquals(SketchWallControlLine.INNER, innerWall.controlLine)
        assertNotNull(findEndpoint(view, 100f, 200f))
        assertNull(findEndpoint(view, 100f, 320f))

        assertEquals(true, view.updateSelectedWallControlLine(SketchWallControlLine.OUTER))
        val outerWall = view.snapshotState().walls.single()
        assertEquals(SketchPoint(100f, 80f), outerWall.start)
        assertEquals(SketchPoint(1100f, 80f), outerWall.end)
        assertNotNull(findEndpoint(view, 100f, 200f))
        assertNull(findEndpoint(view, 100f, 80f))

        privateField("dragEndpoint").setInt(view, 0)
        moveSelection(view, SketchPoint(200f, 200f))
        assertEquals(SketchPoint(200f, 80f), view.snapshotState().walls.single().start)
    }

    @Test
    fun selectedControlLineSurvivesProjectRoundTrip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val projectId = SketchProjectStore.create(context)
        try {
            val wall = SketchWall(
                start = SketchPoint(100f, 200f),
                end = SketchPoint(1100f, 200f),
                controlLine = SketchWallControlLine.OUTER,
            )
            assertTrue(SketchProjectStore.save(context, projectId, SketchState(walls = listOf(wall))))
            assertEquals(SketchWallControlLine.OUTER, SketchProjectStore.load(context, projectId)?.state?.walls?.single()?.controlLine)
        } finally {
            SketchProjectStore.delete(context, projectId)
        }
    }

    @Test
    fun changingThicknessKeepsSelectedSideControlLineFixed() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wall = SketchWall(
            id = "wall",
            start = SketchPoint(100f, 200f),
            end = SketchPoint(1100f, 200f),
            thickness = 200f,
            controlLine = SketchWallControlLine.OUTER,
        )
        val view = SketchView(context).apply { restoreState(SketchState(walls = listOf(wall))) }
        privateField("selectedWallId").set(view, wall.id)
        val originalControls = wallControlLinePoints(wall)

        assertTrue(view.updateSelectedWallThickness(400f))

        val updated = view.snapshotState().walls.single()
        assertEquals(originalControls, wallControlLinePoints(updated))
        assertEquals(100f, updated.start.y, .001f)
        assertEquals(100f, updated.end.y, .001f)
    }

    private fun privateField(name: String) = SketchView::class.java.getDeclaredField(name).apply {
        isAccessible = true
    }

    private fun findEndpoint(view: SketchView, x: Float, y: Float): Pair<*, *>? {
        val method = SketchView::class.java.getDeclaredMethod(
            "findEndpoint",
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        return method.invoke(view, x, y) as Pair<*, *>?
    }

    private fun moveSelection(view: SketchView, pointer: SketchPoint) {
        SketchView::class.java.getDeclaredMethod(
            "moveSelection",
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            SketchPoint::class.java,
        ).apply { isAccessible = true }.invoke(view, 100f, 0f, pointer)
    }
}
