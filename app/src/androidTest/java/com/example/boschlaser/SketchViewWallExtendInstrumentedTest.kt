package com.example.boschlaser

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchViewWallExtendInstrumentedTest {
    @Test
    fun extendChangesOnlyFirstWallAndKeepsItsConnectedEndpointTogether() {
        val view = newView()
        val first = SketchWall(id = "first", start = SketchPoint(0f, 0f), end = SketchPoint(100f, 0f))
        val target = SketchWall(id = "target", start = SketchPoint(150f, -50f), end = SketchPoint(150f, 50f))
        val connected = SketchWall(id = "connected", start = SketchPoint(100f, 0f), end = SketchPoint(100f, 100f))
        val original = SketchState(walls = listOf(first, target, connected))
        view.restoreState(original)

        assertEquals(WallExtendResult.SUCCESS, view.extendWallToWall(first.id, target.id))

        val walls = view.snapshotState().walls.associateBy { it.id }
        assertPointEquals(SketchPoint(150f, 0f), walls.getValue(first.id).end)
        assertEquals(target, walls.getValue(target.id))
        assertPointEquals(SketchPoint(150f, 0f), walls.getValue(connected.id).start)
        assertTrue(view.undo())
        assertEquals(original, view.snapshotState())
    }

    @Test
    fun crossingWallsTrimOnlyFirstWallAndFirstPickChoosesRetainedSide() {
        val view = newView()
        val first = SketchWall(id = "first", start = SketchPoint(0f, 0f), end = SketchPoint(200f, 0f))
        val target = SketchWall(id = "target", start = SketchPoint(100f, -50f), end = SketchPoint(100f, 50f))
        view.restoreState(SketchState(walls = listOf(first, target)))

        assertEquals(
            WallExtendResult.SUCCESS,
            view.extendWallToWall(first.id, target.id, SketchPoint(10f, 0f)),
        )
        var walls = view.snapshotState().walls.associateBy { it.id }
        assertPointEquals(first.start, walls.getValue(first.id).start)
        assertPointEquals(SketchPoint(100f, 0f), walls.getValue(first.id).end)
        assertEquals(target, walls.getValue(target.id))

        view.restoreState(SketchState(walls = listOf(first, target)))
        assertEquals(
            WallExtendResult.SUCCESS,
            view.extendWallToWall(first.id, target.id, SketchPoint(190f, 0f)),
        )
        walls = view.snapshotState().walls.associateBy { it.id }
        assertPointEquals(SketchPoint(100f, 0f), walls.getValue(first.id).start)
        assertPointEquals(first.end, walls.getValue(first.id).end)
        assertEquals(target, walls.getValue(target.id))
    }

    @Test
    fun wallWhoseEndpointAlreadyTouchesTargetIsNotChanged() {
        val view = newView()
        val first = SketchWall(id = "first", start = SketchPoint(0f, 0f), end = SketchPoint(100f, 0f))
        val target = SketchWall(id = "target", start = SketchPoint(100f, -50f), end = SketchPoint(100f, 50f))
        val original = SketchState(walls = listOf(first, target))
        view.restoreState(original)

        assertEquals(
            WallExtendResult.ALREADY_REACHES,
            view.extendWallToWall(first.id, target.id, SketchPoint(10f, 0f)),
        )
        assertEquals(original, view.snapshotState())
    }

    @Test
    fun extendRequiresIntersectionToLandOnActualTargetWall() {
        val view = newView()
        val first = SketchWall(id = "first", start = SketchPoint(0f, 0f), end = SketchPoint(100f, 0f))
        val target = SketchWall(id = "target", start = SketchPoint(150f, 100f), end = SketchPoint(150f, 200f))
        val original = SketchState(walls = listOf(first, target))
        view.restoreState(original)

        assertEquals(WallExtendResult.TARGET_MISSED, view.extendWallToWall(first.id, target.id))
        assertEquals(original, view.snapshotState())
    }

    @Test
    fun parallelWallsCannotBeExtendedTogether() {
        val view = newView()
        val first = SketchWall(id = "first", start = SketchPoint(0f, 0f), end = SketchPoint(100f, 0f))
        val target = SketchWall(id = "target", start = SketchPoint(0f, 50f), end = SketchPoint(100f, 50f))
        val original = SketchState(walls = listOf(first, target))
        view.restoreState(original)

        assertEquals(WallExtendResult.PARALLEL, view.extendWallToWall(first.id, target.id))
        assertEquals(original, view.snapshotState())
    }

    private fun newView(): SketchView {
        if (Looper.myLooper() == null) Looper.prepare()
        return SketchView(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    private fun assertPointEquals(expected: SketchPoint, actual: SketchPoint) {
        assertEquals(expected.x, actual.x, .001f)
        assertEquals(expected.y, actual.y, .001f)
    }
}
