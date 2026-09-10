package com.example.boschlaser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
        val drawOpening = SketchView::class.java.getDeclaredMethod(
            "drawOpening",
            Canvas::class.java,
            SketchOpening::class.java,
            SketchWall::class.java,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }

        drawOpening.invoke(view, canvas, door, wall, 1f, 100f, 100f, false)

        assertEquals(Color.rgb(29, 94, 145), bitmap.getPixel(200, 195))
    }

    @Test
    fun placingDoorNearWallEndKeepsWholeDoorInsideWall() {
        val view = newView()
        val wall = SketchWall(id = "wall", start = SketchPoint(0f, 0f), end = SketchPoint(2000f, 0f))
        view.restoreState(SketchState(walls = listOf(wall)))
        view.setMode(SketchMode.DOOR)
        val addOpening = SketchView::class.java.getDeclaredMethod("addOpening", SketchPoint::class.java).apply {
            isAccessible = true
        }

        addOpening.invoke(view, SketchPoint(0f, 0f))

        val door = view.snapshotState().openings.single()
        assertEquals(900f / 2f / 2000f, door.position, .0001f)
    }

    @Test
    fun doorWiderThanWallIsNotPlaced() {
        val view = newView()
        val wall = SketchWall(id = "wall", start = SketchPoint(0f, 0f), end = SketchPoint(800f, 0f))
        view.restoreState(SketchState(walls = listOf(wall)))
        view.setMode(SketchMode.DOOR)
        val addOpening = SketchView::class.java.getDeclaredMethod("addOpening", SketchPoint::class.java).apply {
            isAccessible = true
        }

        addOpening.invoke(view, SketchPoint(400f, 0f))

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
        val drawWallNetwork = SketchView::class.java.getDeclaredMethod(
            "drawWallNetwork",
            Canvas::class.java,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
        ).apply { isAccessible = true }

        drawWallNetwork.invoke(view, canvas, 1f, 100f, 150f)

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
        val drawOpening = SketchView::class.java.getDeclaredMethod(
            "drawOpening",
            Canvas::class.java,
            SketchOpening::class.java,
            SketchWall::class.java,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }

        drawOpening.invoke(view, canvas, window, wall, 1f, 100f, 150f, false)

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

    private fun newView(): SketchView {
        if (Looper.myLooper() == null) Looper.prepare()
        return SketchView(InstrumentationRegistry.getInstrumentation().targetContext)
    }
}
