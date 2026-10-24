package com.example.boschlaser

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.platform.AndroidAnnotationTextPainter
import com.example.boschlaser.platform.AndroidCanvasDrawing
import com.example.boschlaser.shared.annotation.*
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.ui.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotationSceneDrawingInstrumentedTest {
    @get:org.junit.Rule val foregroundHost: org.junit.rules.TestRule = ForegroundTestHostRule()

    @Test fun sceneOrderIsStableAndLensKeepsItsOriginalPreviewLayer() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val point = PhotoPoint(.5, .5)
            val angle = AngleAnnotation("angle", point, PhotoPoint(.8, .5), PhotoPoint(.5, .8), "angle", RED)
            val text = TextAnnotation("text", point, "text", RED)
            val dimension = DimensionAnnotation("dimension", PhotoPoint(.2, .3), PhotoPoint(.8, .3), "dimension", RED)
            val area = AreaAnnotation("area", listOf(PhotoPoint(.1, .1), PhotoPoint(.9, .1), PhotoPoint(.5, .9)), "area", RED)
            val document = AnnotationDocument(linkedMapOf(angle.id to angle, text.id to text, dimension.id to dimension, area.id to area))
            // A preview ID collision must not replace the confirmed dimension.
            val state = AnnotationSceneState(dimensionPreview = dimension.copy(label = "preview"))
            val painter = RecordingPainter()
            val bitmap = Bitmap.createBitmap(300, 250, Bitmap.Config.ARGB_8888)
            try {
                fun render(lens: Boolean, previews: Boolean = true) {
                    painter.labels.clear()
                    AndroidCanvasDrawing().draw(Canvas(bitmap), Density(1f)) {
                        drawAnnotationScene(document, PhotoDrawingViewport(0.0, 0.0, 300.0, 250.0), 1.0,
                            state, style(), painter, AnnotationSceneLabels("placeholder") { "$it°" },
                            showControls = false, showPreviews = previews, dimensionPreviewBeforeText = lens)
                    }
                }
                render(false)
                assertEquals(listOf("area", "dimension", "text", "angle°", "preview"), painter.labels)
                render(true)
                assertEquals(listOf("area", "dimension", "preview", "text", "angle°"), painter.labels)
                for (lens in listOf(false, true)) {
                    render(lens, previews = false)
                    assertEquals(listOf("area", "dimension", "text", "angle°"), painter.labels)
                }
                assertEquals("dimension", (document.annotations[dimension.id] as DimensionAnnotation).label)
                assertEquals(4, document.annotations.size)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun photoLensClipsAndTransformsContentBeforeDrawingItsCrosshair() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val bitmap = Bitmap.createBitmap(300, 250, Bitmap.Config.ARGB_8888)
            try {
                val lens = photoMagnifierLayout(Vec2(300.0, 250.0), 1.0, Vec2(50.0, 150.0))!!
                AndroidCanvasDrawing().draw(Canvas(bitmap).apply { drawColor(android.graphics.Color.WHITE) }, Density(1f)) {
                    drawPhotoMagnifier(lens, PhotoMagnifierStyle(Color.DarkGray, Color.Black, Color.Cyan, 3f, 2f, 9f)) {
                        drawRect(Color.Red, Offset(45f, 145f), Size(10f, 10f))
                        // The transformed background reaches beyond the circular clip.
                        drawRect(Color.Green, Offset(20f, 120f), Size(5f, 5f))
                    }
                }
                assertEquals(android.graphics.Color.CYAN, bitmap.getPixel(224, 76))
                assertEquals(android.graphics.Color.RED, bitmap.getPixel(234, 86))
                assertEquals(android.graphics.Color.DKGRAY, bitmap.getPixel(250, 100))
                assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(162, 14))
                // Magnification must not paint the original untransformed focus.
                assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(50, 150))
            } finally {
                bitmap.recycle()
            }
        }
    }

    private class RecordingPainter : AnnotationTextPainter {
        private val delegate = AndroidAnnotationTextPainter()
        val labels = mutableListOf<String>()
        override fun measure(text: String, fontSizePx: Double) = delegate.measure(text, fontSizePx)
        override fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope, text: String,
            fontSizePx: Double, colorArgb: Int, baselineCenter: Vec2) {
            labels += text
            delegate.draw(scope, text, fontSizePx, colorArgb, baselineCenter)
        }
    }

    private fun style() = AnnotationSceneStyle(AnnotationControlStyle(Color.White, Color.Red, Color.Yellow), Color.White, Color.Red)
    private companion object { const val RED = -65536 }
}
