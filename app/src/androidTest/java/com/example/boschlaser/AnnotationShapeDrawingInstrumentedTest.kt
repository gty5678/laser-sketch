package com.example.boschlaser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.platform.AndroidCanvasDrawing
import com.example.boschlaser.shared.annotation.*
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotationShapeDrawingInstrumentedTest {
    @get:org.junit.Rule val foregroundHost: org.junit.rules.TestRule = ForegroundTestHostRule()

    private fun image(assertions: (Canvas, Bitmap) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val bitmap = Bitmap.createBitmap(300, 250, Bitmap.Config.ARGB_8888)
            try { assertions(Canvas(bitmap).apply { drawColor(Color.WHITE) }, bitmap) }
            finally { bitmap.recycle() }
        }
    }

    @Test fun dimensionsKeepPerpendicularEndsAndFortyFiveDegreeTicks() = image { canvas, bitmap ->
        val mark = DimensionAnnotation("fixture", PhotoPoint(.2, .4), PhotoPoint(1.0, .4), "", Color.RED)
        val layout = dimensionDrawingLayout(mark, PhotoDrawingViewport(0.0, 0.0, 250.0, 250.0), 1.0)
        AndroidCanvasDrawing().draw(canvas, Density(1f)) { drawAnnotationDimension(layout, androidx.compose.ui.graphics.Color.Red) }
        assertEquals(Color.RED, bitmap.getPixel(150, 100))
        assertEquals(Color.RED, bitmap.getPixel(50, 119))
        assertEquals(Color.RED, bitmap.getPixel(250, 81))
        assertTrue((54..58).any { x -> (92..96).any { y -> bitmap.getPixel(x, y) == Color.RED } })
        assertEquals(Color.WHITE, bitmap.getPixel(150, 110))
    }

    @Test fun closedAreasKeepAlphaAndHighlightOnlyTheSelectedVertex() = image { canvas, bitmap ->
        val mark = AreaAnnotation("fixture", listOf(PhotoPoint(.2, .2), PhotoPoint(.8, .2), PhotoPoint(.8, .8), PhotoPoint(.2, .8)), "", Color.BLUE)
        val layout = areaDrawingLayout(mark, PhotoDrawingViewport(0.0, 0.0, 250.0, 250.0))
        AndroidCanvasDrawing().draw(canvas, Density(1f)) {
            drawAnnotationArea(layout, mark.colorArgb, 1.0)
            drawAnnotationControls(layout.points, 1.0, AnnotationControlStyle(androidx.compose.ui.graphics.Color.Cyan,
                androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color(0xffff9800)), selectedIndex = 0)
        }
        assertEquals(Color.rgb(207, 207, 255), bitmap.getPixel(100, 100))
        assertEquals(Color.BLUE, bitmap.getPixel(50, 125))
        assertEquals(Color.rgb(255, 152, 0), bitmap.getPixel(50, 50))
        assertEquals(Color.CYAN, bitmap.getPixel(200, 50))
        assertEquals(Color.WHITE, bitmap.getPixel(50, 60))
    }

    @Test fun pendingAreasRemainOpenAndUnfilled() = image { canvas, bitmap ->
        AndroidCanvasDrawing().draw(canvas, Density(1f)) {
            drawAnnotationPolyline(listOf(Vec2(50.0, 50.0), Vec2(150.0, 50.0), Vec2(150.0, 150.0)),
                androidx.compose.ui.graphics.Color.Red, 1.0)
        }
        assertEquals(Color.RED, bitmap.getPixel(100, 50))
        assertEquals(Color.RED, bitmap.getPixel(150, 100))
        assertEquals(Color.WHITE, bitmap.getPixel(100, 100))
    }

    @Test fun anglesDrawTheMinorArcBetweenBothRays() = image { canvas, bitmap ->
        val mark = AngleAnnotation("fixture", PhotoPoint(.4, .4), PhotoPoint(.8, .4), PhotoPoint(.4, .8), "", Color.RED)
        val viewport = PhotoDrawingViewport(0.0, 0.0, 250.0, 250.0)
        val drawing = AndroidCanvasDrawing()
        for (annotation in listOf(mark, mark.copy(first = mark.second, second = mark.first))) {
            canvas.drawColor(Color.WHITE)
            drawing.draw(canvas, Density(1f)) { drawAnnotationAngle(angleDrawingLayout(annotation, viewport, 1.0),
                androidx.compose.ui.graphics.Color.Red) }
            assertEquals(Color.RED, bitmap.getPixel(150, 100))
            assertEquals(Color.RED, bitmap.getPixel(100, 150))
            assertTrue((121..125).any { x -> (121..125).any { y -> bitmap.getPixel(x, y) == Color.RED } })
            assertEquals(Color.WHITE, bitmap.getPixel(77, 77))
            assertEquals(Color.WHITE, bitmap.getPixel(115, 115))
        }
    }

    @Test fun photoExportsReuseSharedShapesAndLeaveTheOriginalDocumentAndPhotoUntouched() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val source = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            val view = ImageAnnotationView(instrumentation.targetContext)
            val dimension = DimensionAnnotation("dimension", PhotoPoint(.1, .8), PhotoPoint(.9, .8), "", Color.RED)
            val area = AreaAnnotation("area", listOf(PhotoPoint(.1, .1), PhotoPoint(.6, .1), PhotoPoint(.6, .6), PhotoPoint(.1, .6)), "", Color.BLUE)
            val angle = AngleAnnotation("angle", PhotoPoint(.7, .15), PhotoPoint(.9, .15), PhotoPoint(.7, .35), "", Color.GREEN)
            val document = AnnotationDocument(mapOf(dimension.id to dimension, area.id to area, angle.id to angle))
            try {
                view.setBitmap(source)
                view.restoreDocument(document)
                val exported = view.exportBitmap()!!
                val preview = view.exportPreviewBitmap(250)!!
                try {
                    assertEquals(Color.RED, exported.getPixel(250, 400))
                    assertEquals(Color.RED, exported.getPixel(50, 419))
                    assertEquals(Color.rgb(207, 207, 255), exported.getPixel(150, 150))
                    assertEquals(Color.GREEN, exported.getPixel(400, 75))
                    assertTrue((371..375).any { x -> (96..100).any { y -> exported.getPixel(x, y) == Color.GREEN } })
                    assertEquals(Color.GREEN, preview.getPixel(200, 37))
                    assertEquals(Color.RED, preview.getPixel(125, 200))
                    assertEquals(Color.rgb(207, 207, 255), preview.getPixel(75, 75))
                    assertEquals(document, view.snapshotDocument())
                    assertEquals(Color.WHITE, source.getPixel(250, 400))
                } finally { exported.recycle(); preview.recycle() }
            } finally { source.recycle() }
        }
    }
}
