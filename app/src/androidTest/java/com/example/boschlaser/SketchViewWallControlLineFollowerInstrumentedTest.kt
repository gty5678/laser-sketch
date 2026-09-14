package com.example.boschlaser

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchViewWallControlLineFollowerInstrumentedTest {
    @Test
    fun endpointOnMovedWallControlLineFollowsByExtendingInOwnDirection() {
        val view = newView()
        val host = SketchWall(id = "host", start = SketchPoint(0f, 0f), end = SketchPoint(2000f, 0f))
        val follower = SketchWall(id = "follower", start = SketchPoint(500f, 0f), end = SketchPoint(1500f, 1000f))
        view.restoreState(SketchState(walls = listOf(host, follower)))
        selectWall(view, host.id)

        moveSelectedWall(view, 0f, 200f)

        val walls = view.snapshotState().walls.associateBy { it.id }
        assertPointEquals(SketchPoint(0f, 200f), walls.getValue(host.id).start)
        assertPointEquals(SketchPoint(2000f, 200f), walls.getValue(host.id).end)
        assertPointEquals(SketchPoint(700f, 200f), walls.getValue(follower.id).start)
        assertPointEquals(follower.end, walls.getValue(follower.id).end)
        val movedFollower = walls.getValue(follower.id)
        assertEquals(
            follower.end.y - follower.start.y,
            follower.end.x - follower.start.x,
            .001f,
        )
        assertEquals(
            movedFollower.end.y - movedFollower.start.y,
            movedFollower.end.x - movedFollower.start.x,
            .001f,
        )
    }

    @Test
    fun openingsOnFollowingWallKeepDistanceFromItsFixedEndpoint() {
        val view = newView()
        val host = SketchWall(id = "host", start = SketchPoint(0f, 0f), end = SketchPoint(2000f, 0f))
        val follower = SketchWall(id = "follower", start = SketchPoint(1000f, 0f), end = SketchPoint(1000f, 1000f))
        val window = SketchOpening(
            id = "window",
            type = SketchOpeningType.WINDOW,
            wallId = follower.id,
            position = .5f,
            width = 200f,
        )
        view.restoreState(SketchState(walls = listOf(host, follower), openings = listOf(window)))
        selectWall(view, host.id)

        moveSelectedWall(view, 0f, 200f)

        val state = view.snapshotState()
        assertPointEquals(SketchPoint(1000f, 200f), state.walls.associateBy { it.id }.getValue(follower.id).start)
        assertEquals(300f / 800f, state.openings.single().position, .0001f)
    }

    @Test
    fun followerOpeningConflictCancelsEntireHostWallMove() {
        val view = newView()
        val host = SketchWall(id = "host", start = SketchPoint(0f, 0f), end = SketchPoint(2000f, 0f))
        val follower = SketchWall(id = "follower", start = SketchPoint(1000f, 0f), end = SketchPoint(1000f, 500f))
        val door = SketchOpening(
            id = "door",
            type = SketchOpeningType.DOOR,
            wallId = follower.id,
            position = .5f,
            width = 400f,
        )
        val original = SketchState(walls = listOf(host, follower), openings = listOf(door))
        view.restoreState(original)
        selectWall(view, host.id)

        moveSelectedWall(view, 0f, 300f)

        assertEquals(original, view.snapshotState())
    }

    private fun moveSelectedWall(view: SketchView, dx: Float, dy: Float) {
        SketchView::class.java.getDeclaredMethod(
            "moveSelection",
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            SketchPoint::class.java,
        ).apply { isAccessible = true }.invoke(view, dx, dy, SketchPoint(0f, 0f))
    }

    private fun selectWall(view: SketchView, wallId: String) {
        SketchView::class.java.getDeclaredField("selectedWallId").apply {
            isAccessible = true
            set(view, wallId)
        }
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
