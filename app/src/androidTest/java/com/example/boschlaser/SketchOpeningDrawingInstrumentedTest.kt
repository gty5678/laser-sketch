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
import com.example.boschlaser.ui.OpeningDrawingStyle
import com.example.boschlaser.ui.drawSketchOpening
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchOpeningDrawingInstrumentedTest {
    @get:org.junit.Rule val foregroundHost: org.junit.rules.TestRule = ForegroundTestHostRule()

    private val wall = Wall("host", Vec2(0.0, 0.0), Vec2(4000.0, 0.0), 500.0)
    private val style = OpeningDrawingStyle(androidx.compose.ui.graphics.Color.Blue, 3f,
        androidx.compose.ui.graphics.Color.Black, 2f)

    private fun render(type: OpeningType, hingeFlipped: Boolean = false): Bitmap {
        lateinit var bitmap: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
            AndroidCanvasDrawing().draw(canvas, Density(1f)) {
                drawSketchOpening(Opening("fixture", wall.id, 2000.0, 1000.0, type, hingeFlipped = hingeFlipped),
                    wall, Viewport(.1, Vec2(20.0, 150.0)), style)
            }
        }
        return bitmap
    }

    @Test fun sharedDoorDrawingUsesFullWidthAndMirrorsTheHinge() {
        val normal = render(OpeningType.DOOR)
        val mirrored = render(OpeningType.DOOR, hingeFlipped = true)
        try {
            assertEquals(Color.BLUE, normal.getPixel(170, 240))
            assertEquals(Color.WHITE, normal.getPixel(270, 240))
            assertEquals(Color.BLUE, mirrored.getPixel(270, 240))
            assertEquals(Color.WHITE, mirrored.getPixel(170, 240))
            assertTrue((239..242).any { x -> (219..222).any { y -> normal.getPixel(x, y) == Color.BLUE } })
        } finally { normal.recycle(); mirrored.recycle() }
    }

    @Test fun sharedWindowDrawingKeepsOuterFrameInnerLinesAndClosures() {
        val bitmap = render(OpeningType.WINDOW)
        try {
            assertEquals(Color.BLACK, bitmap.getPixel(220, 125))
            assertEquals(Color.BLACK, bitmap.getPixel(220, 175))
            assertEquals(Color.BLUE, bitmap.getPixel(220, 142))
            assertEquals(Color.BLUE, bitmap.getPixel(220, 158))
            assertEquals(Color.BLUE, bitmap.getPixel(170, 150))
            assertEquals(Color.BLUE, bitmap.getPixel(270, 150))
            assertEquals(Color.WHITE, bitmap.getPixel(220, 150))
        } finally { bitmap.recycle() }
    }
}
