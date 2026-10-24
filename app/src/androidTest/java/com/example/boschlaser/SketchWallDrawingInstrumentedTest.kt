package com.example.boschlaser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.platform.AndroidCanvasDrawing
import com.example.boschlaser.shared.editor.Viewport
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.ui.ControlDrawingStyle
import com.example.boschlaser.ui.WallControlDrawingStyle
import com.example.boschlaser.ui.drawSketchWallControls
import com.example.boschlaser.ui.drawSketchWallStart
import com.example.boschlaser.ui.WallDrawingStyle
import com.example.boschlaser.ui.drawSketchWallNetwork
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchWallDrawingInstrumentedTest {
    @get:org.junit.Rule val foregroundHost: org.junit.rules.TestRule = ForegroundTestHostRule()

    @Test fun controlsFollowTheReferenceLineForEveryAlignment() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
            try {
                val viewport = Viewport(.1, Vec2(30.0, 40.0))
                val pointStyle = ControlDrawingStyle(androidx.compose.ui.graphics.Color.Red, androidx.compose.ui.graphics.Color.White)
                val style = WallControlDrawingStyle(androidx.compose.ui.graphics.Color.Black, 2f, null, pointStyle)
                val drawing = AndroidCanvasDrawing()
                for (alignment in WallAlignment.entries) {
                    val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(1000.0, 0.0), 200.0, alignment)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.LTGRAY)
                    drawing.draw(canvas, Density(1f)) { drawSketchWallControls(wall, viewport, false, style) }
                    assertEquals(Color.RED, bitmap.getPixel(30, 40))
                    // Stay inside the halo rather than sampling its antialiased outer edge.
                    assertEquals(Color.WHITE, bitmap.getPixel(30, 46))
                    assertEquals(Color.BLACK, bitmap.getPixel(80, 40))
                    assertEquals(Color.LTGRAY, bitmap.getPixel(80, 50))
                    canvas.drawColor(Color.LTGRAY)
                    drawing.draw(canvas, Density(1f)) { drawSketchWallControls(wall, viewport, true, style) }
                    assertEquals(Color.BLACK, bitmap.getPixel(30, 48))
                    assertEquals(Color.BLACK, bitmap.getPixel(30, 40))
                    assertEquals(Color.LTGRAY, bitmap.getPixel(30, 44))
                }
            } finally { bitmap.recycle() }
        }
    }

    @Test fun firstWallPointPreviewKeepsItsHaloAndCrossDimensions() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap).apply { drawColor(Color.LTGRAY) }
                AndroidCanvasDrawing().draw(canvas, Density(1f)) {
                    drawSketchWallStart(Vec2(0.0, 0.0), Viewport(1.0, Vec2(50.0, 50.0)),
                        ControlDrawingStyle(androidx.compose.ui.graphics.Color.Red, androidx.compose.ui.graphics.Color.White),
                        androidx.compose.ui.graphics.Color.Black, 2f)
                }
                assertEquals(Color.BLACK, bitmap.getPixel(63, 50))
                assertEquals(Color.BLACK, bitmap.getPixel(50, 37))
                assertEquals(Color.WHITE, bitmap.getPixel(56, 56))
                assertEquals(Color.RED, bitmap.getPixel(54, 53))
                assertEquals(Color.LTGRAY, bitmap.getPixel(66, 50))
            } finally { bitmap.recycle() }
        }
    }

    @Test fun sharedNetworkCutsOutTheDoorwayAndPreservesBothJambs() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val wall = Wall("host", Vec2(0.0, 0.0), Vec2(3000.0, 0.0), 200.0)
            val door = Opening("door", wall.id, 1500.0, 900.0, OpeningType.DOOR)
            val document = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(door.id to door))
            val bitmap = Bitmap.createBitmap(360, 300, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
                AndroidCanvasDrawing().draw(canvas, Density(1f)) {
                    drawSketchWallNetwork(document, Viewport(.1, Vec2(20.0, 150.0)),
                        WallDrawingStyle(androidx.compose.ui.graphics.Color.Gray, androidx.compose.ui.graphics.Color.Black, 2f))
                }
                assertEquals(Color.GRAY, bitmap.getPixel(70, 150))
                assertEquals(Color.WHITE, bitmap.getPixel(170, 150))
                assertEquals(Color.BLACK, bitmap.getPixel(125, 150))
                assertEquals(Color.BLACK, bitmap.getPixel(215, 150))
                assertEquals(Color.WHITE, bitmap.getPixel(70, 170))
                assertEquals(door, document.openings.getValue(door.id))
                assertEquals(wall, document.walls.getValue(wall.id))
            } finally { bitmap.recycle() }
        }
    }
}
