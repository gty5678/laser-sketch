package com.example.boschlaser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.platform.AndroidCanvasDrawing
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.ui.WallLengthLabelLayout
import com.example.boschlaser.ui.drawSketchWallLabel
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class SketchWallLabelDrawingInstrumentedTest {
    @get:org.junit.Rule val foregroundHost: org.junit.rules.TestRule = ForegroundTestHostRule()

    @Test fun sharedTextRetainsNativeCenteredBaselineAndBoldGlyphBounds() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val measurer = TextMeasurer(createFontFamilyResolver(instrumentation.targetContext), Density(1f), LayoutDirection.Ltr)
            for (density in listOf(Density(1f), Density(2f, 1.3f))) {
                val native = Bitmap.createBitmap(640, 300, Bitmap.Config.ARGB_8888)
                val shared = Bitmap.createBitmap(640, 300, Bitmap.Config.ARGB_8888)
                try {
                    val text = "3123 mm"
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = with(density) { 13.sp.toPx() }
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    Canvas(native).apply { drawColor(Color.WHITE); drawText(text, 320f, 220f, paint) }
                    val canvas = Canvas(shared).apply { drawColor(Color.WHITE) }
                    AndroidCanvasDrawing().draw(canvas, density) {
                        drawSketchWallLabel(WallLengthLabelLayout(Vec2(320.0, 220.0), 3123.0), text, measurer,
                            TextStyle(color = androidx.compose.ui.graphics.Color.Black, fontSize = 13.sp,
                                fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold))
                    }
                    val expected = inkBounds(native)
                    val actual = inkBounds(shared)
                    assertTrue("native label must have visible glyphs", !expected.isEmpty)
                    assertTrue("shared label must have visible glyphs", !actual.isEmpty)
                    for ((first, second) in listOf(expected.left to actual.left, expected.top to actual.top,
                        expected.right to actual.right, expected.bottom to actual.bottom)) {
                        assertTrue("glyph bounds changed: expected $expected, actual $actual", abs(first - second) <= 2)
                    }
                } finally { native.recycle(); shared.recycle() }
            }
        }
    }

    private fun inkBounds(bitmap: Bitmap): Rect {
        val bounds = Rect(bitmap.width, bitmap.height, 0, 0)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (bitmap.getPixel(x, y) != Color.WHITE) {
                bounds.left = minOf(bounds.left, x)
                bounds.top = minOf(bounds.top, y)
                bounds.right = maxOf(bounds.right, x + 1)
                bounds.bottom = maxOf(bounds.bottom, y + 1)
            }
        }
        return bounds
    }
}
