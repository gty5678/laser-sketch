package com.example.boschlaser

import com.example.boschlaser.shared.editor.Selection

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchViewDoorRenderingInstrumentedTest {
    @Test
    fun doorLeafAndSwingArcUseTheFullOpeningWidth() {
        val view = newView()
        val wall = SketchWall(id = "wall", start = SketchPoint(0f, 0f), end = SketchPoint(300f, 0f))
        val door = SketchOpening(
            id = "door",
            type = SketchOpeningType.DOOR,
            wallId = wall.id,
            position = .5f,
            width = 100f,
        )
        val bitmap = Bitmap.createBitmap(500, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }

        view.drawOpeningFixture(canvas, door, wall, 1f, 100f, 100f, false)

        assertEquals(Color.rgb(29, 94, 145), bitmap.getPixel(200, 195))
    }

    @Test
    fun doorHingeCanFlipLeftRightWithoutChangingUpDownSwing() {
        val view = newView()
        val wall = SketchWall(id = "wall", start = SketchPoint(0f, 0f), end = SketchPoint(300f, 0f))
        val door = SketchOpening(
            id = "door",
            type = SketchOpeningType.DOOR,
            wallId = wall.id,
            position = .5f,
            width = 100f,
        )
        view.restoreState(SketchState(walls = listOf(wall), openings = listOf(door)))
        view.selectEntity(Selection.Opening(door.id))

        assertTrue(view.updateSelectedOpening(door.width, flipHinge = true))
        val flippedDoor = view.snapshotState().openings.single()
        assertTrue(flippedDoor.hingeFlipped)
        assertFalse(flippedDoor.flipped)

        val bitmap = Bitmap.createBitmap(500, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
        drawOpening(view, canvas, flippedDoor, wall)

        assertEquals(Color.rgb(29, 94, 145), bitmap.getPixel(300, 195))
        assertEquals(Color.WHITE, bitmap.getPixel(200, 195))

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val projectId = SketchProjectStore.create(context)
        try {
            assertTrue(
                SketchProjectStore.save(
                    context,
                    projectId,
                    SketchState(walls = listOf(wall), openings = listOf(flippedDoor)),
                ),
            )
            assertTrue(SketchProjectStore.load(context, projectId)?.state?.openings?.single()?.hingeFlipped == true)
        } finally {
            SketchProjectStore.delete(context, projectId)
        }
    }

    @Test
    fun placingDoorNearWallEndKeepsWholeDoorInsideWall() {
        val view = newView()
        val wall = SketchWall(id = "wall", start = SketchPoint(0f, 0f), end = SketchPoint(2000f, 0f))
        view.restoreState(SketchState(walls = listOf(wall)))
        view.setMode(SketchMode.DOOR)
        val scale = SketchView::class.java.getDeclaredField("scale").apply { isAccessible = true }.getFloat(view)
        val event = android.view.MotionEvent.obtain(0L, 0L, android.view.MotionEvent.ACTION_UP, 0f * scale, 0f, 0)
        try { view.onTouchEvent(event) } finally { event.recycle() }

        val door = view.snapshotState().openings.single()
        assertEquals(900f / 2f / 2000f, door.position, .0001f)
    }

    @Test
    fun doorWiderThanWallIsNotPlaced() {
        val view = newView()
        val wall = SketchWall(id = "wall", start = SketchPoint(0f, 0f), end = SketchPoint(800f, 0f))
        view.restoreState(SketchState(walls = listOf(wall)))
        view.setMode(SketchMode.DOOR)
        val scale = SketchView::class.java.getDeclaredField("scale").apply { isAccessible = true }.getFloat(view)
        val event = android.view.MotionEvent.obtain(0L, 0L, android.view.MotionEvent.ACTION_UP, 400f * scale, 0f, 0)
        try { view.onTouchEvent(event) } finally { event.recycle() }

        assertTrue(view.snapshotState().openings.isEmpty())
    }

    @Test
    fun doorwayIsRemovedFromWallFillAndHasJambEdge() {
        val view = newView()
        val wall = SketchWall(
            id = "wall",
            start = SketchPoint(0f, 0f),
            end = SketchPoint(300f, 0f),
            thickness = 40f,
        )
        val door = SketchOpening(
            id = "door",
            type = SketchOpeningType.DOOR,
            wallId = wall.id,
            position = .5f,
            width = 100f,
        )
        view.restoreState(SketchState(walls = listOf(wall), openings = listOf(door)))
        val bitmap = Bitmap.createBitmap(500, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }

        view.drawWallNetworkFixture(canvas, 1f, 100f, 150f)

        assertEquals(Color.WHITE, bitmap.getPixel(250, 150))
        assertEquals(Color.rgb(245, 245, 242), bitmap.getPixel(150, 150))
        assertEquals(Color.rgb(28, 31, 32), bitmap.getPixel(200, 150))
    }

    @Test
    fun windowHasTwoParallelLinesWithClosedEnds() {
        val view = newView()
        val wall = SketchWall(
            id = "wall",
            start = SketchPoint(0f, 0f),
            end = SketchPoint(300f, 0f),
            thickness = 60f,
        )
        val window = SketchOpening(
            id = "window",
            type = SketchOpeningType.WINDOW,
            wallId = wall.id,
            position = .5f,
            width = 100f,
        )
        val bitmap = Bitmap.createBitmap(500, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }

        view.drawOpeningFixture(canvas, window, wall, 1f, 100f, 150f, false)

        val windowColor = Color.rgb(29, 94, 145)
        val wallSurfaceColor = Color.rgb(28, 31, 32)
        assertEquals(wallSurfaceColor, bitmap.getPixel(250, 120))
        assertEquals(wallSurfaceColor, bitmap.getPixel(250, 180))
        assertEquals(windowColor, bitmap.getPixel(250, 140))
        assertEquals(windowColor, bitmap.getPixel(250, 160))
        assertEquals(windowColor, bitmap.getPixel(200, 150))
        assertEquals(windowColor, bitmap.getPixel(300, 150))
        assertEquals(windowColor, bitmap.getPixel(200, 125))
        assertEquals(windowColor, bitmap.getPixel(300, 175))
        assertEquals(Color.WHITE, bitmap.getPixel(250, 150))
    }

    @Test
    fun doorHitAreaFollowsItsVisibleSymbolInsteadOfLargeCenterCircle() {
        val view = newView()
        val wall = SketchWall(id = "wall", start = SketchPoint(0f, 0f), end = SketchPoint(3000f, 0f), thickness = 240f)
        val door = SketchOpening(
            id = "door",
            type = SketchOpeningType.DOOR,
            wallId = wall.id,
            position = .5f,
            width = 900f,
        )

        assertTrue(openingSymbolHit( SketchPoint(1050f, 450f), door, wall, 30f))
        assertEquals(false, openingSymbolHit( SketchPoint(1500f, -500f), door, wall, 30f))

        val rightHingedDoor = door.copy(hingeFlipped = true)
        assertTrue(openingSymbolHit( SketchPoint(1950f, 450f), rightHingedDoor, wall, 30f))
        assertFalse(openingSymbolHit( SketchPoint(1050f, 450f), rightHingedDoor, wall, 30f))
    }

    @Test
    fun windowHitAreaFollowsWindowAndClosureLines() {
        val view = newView()
        val wall = SketchWall(id = "wall", start = SketchPoint(0f, 0f), end = SketchPoint(3000f, 0f), thickness = 240f)
        val window = SketchOpening(
            id = "window",
            type = SketchOpeningType.WINDOW,
            wallId = wall.id,
            position = .5f,
            width = 1200f,
        )

        assertTrue(openingSymbolHit( SketchPoint(1500f, 40f), window, wall, 30f))
        assertEquals(false, openingSymbolHit( SketchPoint(1500f, 500f), window, wall, 30f))
    }

    private fun openingSymbolHit(
        point: SketchPoint,
        opening: SketchOpening,
        wall: SketchWall,
        tolerance: Float,
    ): Boolean {
        return com.example.boschlaser.shared.editor.HitTestEngine().hitsOpening(
            opening.toCoreOpening(wall), wall.toCoreWall(), point.toVec2(), tolerance.toDouble())
    }

    private fun drawOpening(view: SketchView, canvas: Canvas, opening: SketchOpening, wall: SketchWall) {
        view.drawOpeningFixture(canvas, opening, wall, 1f, 100f, 100f, false)
    }

    private fun newView(): SketchView {
        if (Looper.myLooper() == null) Looper.prepare()
        return SketchView(InstrumentationRegistry.getInstrumentation().targetContext)
    }
}
