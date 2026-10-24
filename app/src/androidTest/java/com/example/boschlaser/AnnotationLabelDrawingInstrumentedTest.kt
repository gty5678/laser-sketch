package com.example.boschlaser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.platform.AndroidAnnotationTextPainter
import com.example.boschlaser.platform.AndroidCanvasDrawing
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.ui.*
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class AnnotationLabelDrawingInstrumentedTest {
    @get:org.junit.Rule val foregroundHost: org.junit.rules.TestRule = ForegroundTestHostRule()

    @Test fun dimensionLabelRotationAndWhiteBackgroundMatchTheOriginalNativeDrawing() {
        compareLabels(dimension = true)
    }
    @Test fun textBaselineAndSingleLineGlyphSemanticsMatchWithAndWithoutWhiteBackground() {
        compareLabels(dimension = false)
    }

    private fun compareLabels(dimension: Boolean) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val painter = AndroidAnnotationTextPainter()
            val adapter = AndroidCanvasDrawing()
            for (text in listOf("1234 mm", "旧文字\n内容")) for (degrees in if (dimension) listOf(0,45,135,-135) else listOf(0)) {
                for (white in if (dimension) listOf(true) else listOf(true,false)) {
                    val native = Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888)
                    val shared = Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888)
                    try {
                        val size = if (dimension) 24f else 22f
                        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            textSize = size; color = Color.RED; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
                        }
                        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225,255,255,255) }
                        val metrics = textPaint.fontMetrics
                        val width = textPaint.measureText(text)
                        val baseline = if (dimension) -13f - metrics.descent else 0f
                        val verticalPadding = if (dimension) 4f else 8f
                        var rotation = degrees.toFloat()
                        if (dimension && (rotation > 90 || rotation < -90)) rotation += 180
                        Canvas(native).apply {
                            drawColor(Color.LTGRAY)
                            translate(200f,150f)
                            rotate(rotation)
                            if (white) drawRoundRect(RectF(-width/2-8,baseline+metrics.ascent-verticalPadding,
                                width/2+8,baseline+metrics.descent+verticalPadding),8f,8f,background)
                            drawText(text,0f,baseline,textPaint)
                        }
                        val measured = painter.measure(text,size.toDouble())
                        val layout = if (dimension) dimensionLabelLayout(DimensionDrawingLayout(Vec2(150.0,150.0),Vec2(250.0,150.0),
                            degrees*PI/180,emptyList(),3.0),measured,1.0)
                        else annotationLabelLayout(Vec2(200.0,150.0),measured,size.toDouble(),8.0,whiteBackground = white)
                        adapter.draw(Canvas(shared).apply { drawColor(Color.LTGRAY) },Density(1f)) {
                            drawAnnotationLabel(layout,text,Color.RED,androidx.compose.ui.graphics.Color(0xe1ffffff),painter)
                        }
                        var visible = 0
                        var different = 0
                        for (y in 0 until native.height) for (x in 0 until native.width) {
                            val expected = native.getPixel(x,y)
                            val actual = shared.getPixel(x,y)
                            if (expected != Color.LTGRAY || actual != Color.LTGRAY) visible++
                            if (listOf(Color.red(expected)-Color.red(actual),Color.green(expected)-Color.green(actual),
                                Color.blue(expected)-Color.blue(actual)).any { abs(it)>2 }) different++
                        }
                        assertTrue("Must render visible glyphs/background",visible>0)
                        assertTrue("Label pixels changed at $degrees degrees: $different / $visible",different <= visible*.02+5)
                    } finally { native.recycle(); shared.recycle() }
                }
            }
        }
    }
}
