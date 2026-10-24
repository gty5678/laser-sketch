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
import com.example.boschlaser.ui.ColumnDrawingStyle
import com.example.boschlaser.ui.drawSketchColumn
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchColumnDrawingInstrumentedTest {
    @get:org.junit.Rule val foregroundHost: org.junit.rules.TestRule = ForegroundTestHostRule()

    private fun render(column: Column, rotation: Boolean, center: Boolean = false, scale: Double = 1.0): Bitmap {
        lateinit var bitmap: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bitmap = Bitmap.createBitmap(320, 300, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
            val style = ColumnDrawingStyle(androidx.compose.ui.graphics.Color.Gray, androidx.compose.ui.graphics.Color.Green,
                4f, androidx.compose.ui.graphics.Color.Blue, androidx.compose.ui.graphics.Color.White, 10f)
            AndroidCanvasDrawing().draw(canvas, Density(1f)) {
                drawSketchColumn(column, Viewport(scale, Vec2(160.0, 150.0)), true, rotation, center, 32.0, style)
            }
        }
        return bitmap
    }

    @Test fun rotatedBodyOutlineAndHandleKeepTheirExpectedPixelLocations() {
        val column = RectColumn("fixture", Vec2(0.0, 0.0), 80.0, 40.0, 90.0)
        val selected = render(column, rotation = true)
        val placement = render(column, rotation = false)
        try {
            assertEquals(Color.GRAY, selected.getPixel(160, 180))
            assertEquals(Color.WHITE, selected.getPixel(160, 200))
            assertEquals(Color.GREEN, selected.getPixel(184, 170))
            assertEquals(Color.BLUE, selected.getPixel(212, 150))
            assertEquals(Color.WHITE, placement.getPixel(212, 150))
        } finally { selected.recycle(); placement.recycle() }
    }

    @Test fun circleHasNoHandleAndSmallRectangleRemainsVisible() {
        val circle = render(CircularColumn("circle", Vec2(0.0, 0.0), 60.0), rotation = true, center = true)
        val tiny = render(RectColumn("tiny", Vec2(0.0, 0.0), 50.0, 50.0), rotation = false, scale = .01)
        try {
            assertEquals(Color.BLUE, circle.getPixel(160, 150))
            assertEquals(Color.GRAY, circle.getPixel(170, 150))
            assertEquals(Color.GREEN, circle.getPixel(194, 150))
            assertEquals(Color.WHITE, circle.getPixel(160, 88))
            assertEquals(Color.GRAY, tiny.getPixel(164, 150))
            assertEquals(Color.WHITE, tiny.getPixel(173, 150))
        } finally { circle.recycle(); tiny.recycle() }
    }
    @Test fun temporaryPreviewCannotBeReplacedByAnExistingColumnWithTheSameId() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = SketchView(InstrumentationRegistry.getInstrumentation().targetContext)
            val existing = RectColumn("preview", Vec2(0.0, 0.0), 100.0, 100.0)
            val document = SketchDocument(columns = mapOf(existing.id to existing))
            view.restoreDocument(document)
            val preview = SketchColumn("preview", SketchColumnType.CIRCLE, SketchPoint(160f, 0f), 50f, 50f)
            val bitmap = Bitmap.createBitmap(320, 300, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
                view.drawColumnFixture(canvas, preview, 1f, 80f, 150f, false)
                assertEquals(Color.WHITE, bitmap.getPixel(80, 150))
                assertEquals(Color.rgb(110, 116, 118), bitmap.getPixel(240, 150))
                assertEquals(document, view.snapshotDocument())
            } finally { bitmap.recycle() }
        }
    }

}
