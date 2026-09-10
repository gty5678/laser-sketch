package com.example.boschlaser

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchViewWallStraightenInstrumentedTest {
    @Test
    fun straightenToNearestAxisMovesConfiguredEndpointAndConnectedWalls() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = SketchView(context)
        val selectedWallId = SketchView::class.java.getDeclaredField("selectedWallId").apply {
            isAccessible = true
        }

        val horizontal = SketchWall(
            id = "horizontal",
            start = SketchPoint(0f, 0f),
            end = SketchPoint(100f, 10f),
        )
        val connected = SketchWall(
            id = "connected",
            start = horizontal.end,
            end = SketchPoint(160f, 80f),
        )
        view.restoreState(SketchState(walls = listOf(horizontal, connected)))
        selectedWallId.set(view, horizontal.id)

        assertTrue(view.straightenSelectedWall(moveStart = false))
        val horizontalResult = view.snapshotState().walls.associateBy { it.id }
        val expectedEnd = SketchPoint(hypot(100f, 10f), 0f)
        assertEquals(horizontal.start, horizontalResult.getValue(horizontal.id).start)
        assertPointEquals(expectedEnd, horizontalResult.getValue(horizontal.id).end)
        assertPointEquals(expectedEnd, horizontalResult.getValue(connected.id).start)

        val vertical = SketchWall(
            id = "vertical",
            start = SketchPoint(10f, 0f),
            end = SketchPoint(0f, 100f),
        )
        view.restoreState(SketchState(walls = listOf(vertical)))
        selectedWallId.set(view, vertical.id)

        assertTrue(view.straightenSelectedWall(moveStart = true))
        val verticalResult = view.snapshotState().walls.single()
        assertEquals(vertical.end, verticalResult.end)
        assertPointEquals(SketchPoint(0f, 100f - hypot(10f, 100f)), verticalResult.start)
    }

    @Test
    fun straightenRequiresSelectedNonZeroLengthWall() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = SketchView(context)
        assertFalse(view.straightenSelectedWall(moveStart = false))
    }

    private fun assertPointEquals(expected: SketchPoint, actual: SketchPoint) {
        assertEquals(expected.x, actual.x, .001f)
        assertEquals(expected.y, actual.y, .001f)
    }
}
