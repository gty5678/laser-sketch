package com.example.boschlaser

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchViewHostedOpeningInstrumentedTest {
    @Test
    fun movingEndKeepsOpeningDistanceFromStartInMillimeters() {
        val view = viewWithOpening()
        selectWall(view, "wall")

        assertTrue(view.applySelectedWallLength(3000f, moveStart = false))

        val state = view.snapshotState()
        assertEquals(700f / 3000f, state.openings.single().position, .0001f)
    }

    @Test
    fun movingStartKeepsOpeningDistanceFromEndInMillimeters() {
        val view = viewWithOpening()
        selectWall(view, "wall")

        assertTrue(view.applySelectedWallLength(3000f, moveStart = true))

        val state = view.snapshotState()
        assertEquals(1700f / 3000f, state.openings.single().position, .0001f)
    }

    @Test
    fun rotatingMovedEndpointKeepsOpeningDistanceFromFixedEndpoint() {
        val view = viewWithOpening()
        selectWall(view, "wall")
        SketchView::class.java.getDeclaredField("dragEndpoint").apply {
            isAccessible = true
            setInt(view, 1)
        }
        view.setGridSnapEnabled(false)
        val moveSelection = SketchView::class.java.getDeclaredMethod(
            "moveSelection",
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            SketchPoint::class.java,
        ).apply { isAccessible = true }

        moveSelection.invoke(view, 0f, 0f, SketchPoint(0f, 3000f))

        val state = view.snapshotState()
        assertEquals(SketchPoint(0f, 3000f), state.walls.single().end)
        assertEquals(700f / 3000f, state.openings.single().position, .0001f)
    }

    @Test
    fun shorteningPastOpeningIsRejectedWithoutPartialChanges() {
        val view = viewWithOpening()
        selectWall(view, "wall")
        val original = view.snapshotState()

        assertFalse(view.applySelectedWallLength(800f, moveStart = false))

        assertTrue(view.lastWallEditHadOpeningConflict())
        assertEquals(original, view.snapshotState())
    }

    @Test
    fun extendPreservesOpeningDistanceOnExtendedWall() {
        val view = newView()
        val first = SketchWall(id = "first", start = SketchPoint(0f, 0f), end = SketchPoint(2000f, 0f))
        val target = SketchWall(id = "target", start = SketchPoint(3000f, -1000f), end = SketchPoint(3000f, 1000f))
        val opening = opening(wallId = first.id)
        view.restoreState(SketchState(walls = listOf(first, target), openings = listOf(opening)))

        assertEquals(WallExtendResult.SUCCESS, view.extendWallToWall(first.id, target.id))

        assertEquals(700f / 3000f, view.snapshotState().openings.single().position, .0001f)
    }

    @Test
    fun trimThatWouldRemoveOpeningIsRejectedAtomically() {
        val view = newView()
        val through = SketchWall(id = "through", start = SketchPoint(0f, 0f), end = SketchPoint(2000f, 0f))
        val stem = SketchWall(id = "stem", start = SketchPoint(1000f, 500f), end = SketchPoint(1000f, 1500f))
        val opening = opening(wallId = through.id, position = .15f)
        val original = SketchState(walls = listOf(through, stem), openings = listOf(opening))
        view.restoreState(original)

        assertEquals(
            WallJoinResult.OPENING_CONFLICT,
            view.joinWallsAtIntersection(
                through.id,
                SketchPoint(1900f, 0f),
                stem.id,
                SketchPoint(1000f, 550f),
            ),
        )
        assertEquals(original, view.snapshotState())
    }

    private fun viewWithOpening(): SketchView = newView().also { view ->
        val wall = SketchWall(id = "wall", start = SketchPoint(0f, 0f), end = SketchPoint(2000f, 0f))
        view.restoreState(SketchState(walls = listOf(wall), openings = listOf(opening(wall.id))))
    }

    private fun opening(wallId: String, position: Float = .35f) = SketchOpening(
        id = "opening",
        type = SketchOpeningType.WINDOW,
        wallId = wallId,
        position = position,
        width = 400f,
    )

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
}
