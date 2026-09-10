package com.example.boschlaser

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchViewWallJoinInstrumentedTest {
    @Test
    fun joinExtendsAndTrimsNearestEndpointsToOneControlPointAndCanUndo() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = SketchView(context)
        val horizontal = SketchWall(
            id = "horizontal",
            start = SketchPoint(0f, 0f),
            end = SketchPoint(100f, 0f),
        )
        val vertical = SketchWall(
            id = "vertical",
            start = SketchPoint(150f, -100f),
            end = SketchPoint(150f, 50f),
        )
        val connected = SketchWall(
            id = "connected",
            start = SketchPoint(100f, 0f),
            end = SketchPoint(100f, 200f),
        )
        val original = SketchState(walls = listOf(horizontal, vertical, connected))
        view.restoreState(original)

        assertEquals(
            WallJoinResult.SUCCESS,
            view.joinWallsAtIntersection(
                horizontal.id,
                SketchPoint(90f, 0f),
                vertical.id,
                SketchPoint(150f, 40f),
            ),
        )

        val joined = view.snapshotState().walls.associateBy { it.id }
        assertPointEquals(SketchPoint(150f, 0f), joined.getValue(horizontal.id).end)
        assertPointEquals(SketchPoint(150f, 0f), joined.getValue(vertical.id).start)
        assertPointEquals(SketchPoint(150f, 0f), joined.getValue(connected.id).start)
        assertTrue(view.undo())
        assertEquals(original, view.snapshotState())
    }

    @Test
    fun parallelWallsRemainUnchanged() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = SketchView(context)
        val first = SketchWall(id = "first", start = SketchPoint(0f, 0f), end = SketchPoint(100f, 0f))
        val second = SketchWall(id = "second", start = SketchPoint(0f, 50f), end = SketchPoint(100f, 50f))
        val original = SketchState(walls = listOf(first, second))
        view.restoreState(original)

        assertEquals(
            WallJoinResult.PARALLEL,
            view.joinWallsAtIntersection(first.id, SketchPoint(90f, 0f), second.id, SketchPoint(90f, 50f)),
        )
        assertEquals(original, view.snapshotState())
    }

    @Test
    fun tWallClickingOppositeSidesChoosesOppositeTrimEndpoints() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val throughWall = SketchWall(
            id = "through",
            start = SketchPoint(0f, 0f),
            end = SketchPoint(200f, 0f),
        )
        val stem = SketchWall(
            id = "stem",
            start = SketchPoint(100f, 50f),
            end = SketchPoint(100f, 150f),
        )
        val view = SketchView(context)

        view.restoreState(SketchState(walls = listOf(throughWall, stem)))
        assertEquals(
            WallJoinResult.SUCCESS,
            view.joinWallsAtIntersection(
                throughWall.id,
                SketchPoint(10f, 0f),
                stem.id,
                SketchPoint(100f, 55f),
            ),
        )
        var joined = view.snapshotState().walls.associateBy { it.id }
        assertPointEquals(SketchPoint(0f, 0f), joined.getValue(throughWall.id).start)
        assertPointEquals(SketchPoint(100f, 0f), joined.getValue(throughWall.id).end)

        view.restoreState(SketchState(walls = listOf(throughWall, stem)))
        assertEquals(
            WallJoinResult.SUCCESS,
            view.joinWallsAtIntersection(
                throughWall.id,
                SketchPoint(190f, 0f),
                stem.id,
                SketchPoint(100f, 55f),
            ),
        )
        joined = view.snapshotState().walls.associateBy { it.id }
        assertPointEquals(SketchPoint(100f, 0f), joined.getValue(throughWall.id).start)
        assertPointEquals(SketchPoint(200f, 0f), joined.getValue(throughWall.id).end)
    }

    private fun assertPointEquals(expected: SketchPoint, actual: SketchPoint) {
        assertEquals(expected.x, actual.x, .001f)
        assertEquals(expected.y, actual.y, .001f)
    }
}
