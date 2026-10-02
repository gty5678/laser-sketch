package com.example.boschlaser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WallJointRenderingInstrumentedTest {
    @Test
    fun obliqueTJunctionFusesInsideHostWithoutProtrudingPastFarFace() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val drawWallNetwork = SketchView::class.java.getDeclaredMethod(
            "drawWallNetwork",
            Canvas::class.java,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        val host = SketchWall(
            id = "host",
            start = SketchPoint(-180f, 0f),
            end = SketchPoint(180f, 0f),
            thickness = 80f,
        )
        val stem = SketchWall(
            id = "stem",
            start = SketchPoint(-160f, -160f),
            end = SketchPoint(0f, 0f),
            thickness = 60f,
        )

        val bitmap = renderNetwork(context, drawWallNetwork, listOf(host, stem))
        val origin = bitmap.width / 2
        val hostFarFaceOffset = (host.thickness / 2f * .7f).toInt()
        val insideOffset = hostFarFaceOffset - 4
        val outsideOffset = hostFarFaceOffset + 4

        assertEquals(
            "The oblique stem must remain fused inside the host",
            Color.rgb(245, 245, 242),
            bitmap.getPixel(origin + insideOffset, origin + insideOffset),
        )
        assertEquals(
            "The oblique stem must not protrude beyond the host far face",
            Color.WHITE,
            bitmap.getPixel(origin + outsideOffset, origin + outsideOffset),
        )
    }

    @Test
    fun renderSharedEndpointThroughFullRotation() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cellWidth = 360
        val cellHeight = 260
        val columns = 4
        val angles = (0 until 360 step 15).toList()
        val sheet = Bitmap.createBitmap(cellWidth * columns, cellHeight * 6, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet).apply { drawColor(Color.rgb(232, 234, 234)) }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 24f
        }
        val drawWallNetwork = SketchView::class.java.getDeclaredMethod(
            "drawWallNetwork",
            Canvas::class.java,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
        ).apply { isAccessible = true }

        angles.forEachIndexed { index, degrees ->
            val radians = Math.toRadians(degrees.toDouble())
            val joint = SketchPoint(0f, 0f)
            val length = 180f
            val view = SketchView(context).apply {
                restoreState(
                    SketchState(
                        walls = listOf(
                            SketchWall(id = "fixed", start = joint, end = SketchPoint(length, 0f), thickness = 80f),
                            SketchWall(
                                id = "rotating",
                                start = joint,
                                end = SketchPoint(
                                    (cos(radians) * length).toFloat(),
                                    (sin(radians) * length).toFloat(),
                                ),
                                thickness = 80f,
                            ),
                        ),
                    ),
                )
            }
            val left = index % columns * cellWidth
            val top = index / columns * cellHeight
            val cellBitmap = Bitmap.createBitmap(cellWidth, cellHeight - 30, Bitmap.Config.ARGB_8888)
            val cellCanvas = Canvas(cellBitmap).apply { drawColor(Color.WHITE) }
            drawWallNetwork.invoke(
                view,
                cellCanvas,
                0.7f,
                cellWidth / 2f,
                (cellHeight - 30) / 2f,
            )
            canvas.drawBitmap(cellBitmap, left.toFloat(), (top + 30).toFloat(), null)
            canvas.drawText("$degrees°", (left + 8).toFloat(), (top + 25).toFloat(), labelPaint)
        }

        val output = File(context.getExternalFilesDir(null), "wall-joint-rotation.png")
        FileOutputStream(output).use { stream -> sheet.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        assertTrue(output.exists() && output.length() > 0)
    }

    @Test
    fun sharedEndpointRenderingIsIndependentOfEndpointStorageAndWallOrder() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val drawWallNetwork = SketchView::class.java.getDeclaredMethod(
            "drawWallNetwork",
            Canvas::class.java,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        val joint = SketchPoint(0f, 0f)
        val length = 180f

        for (degrees in 0 until 360 step 15) {
            val radians = Math.toRadians(degrees.toDouble())
            val fixedEnd = SketchPoint(length, 0f)
            val rotatingEnd = SketchPoint(
                (cos(radians) * length).toFloat(),
                (sin(radians) * length).toFloat(),
            )
            val fixed = SketchWall(id = "fixed", start = joint, end = fixedEnd, thickness = 80f)
            val rotating = SketchWall(id = "rotating", start = joint, end = rotatingEnd, thickness = 120f)
            val expected = renderNetwork(context, drawWallNetwork, listOf(fixed, rotating))
            val variants = listOf(
                listOf(fixed.copy(start = fixedEnd, end = joint), rotating),
                listOf(fixed, rotating.copy(start = rotatingEnd, end = joint)),
                listOf(
                    fixed.copy(start = fixedEnd, end = joint),
                    rotating.copy(start = rotatingEnd, end = joint),
                ),
                listOf(rotating, fixed),
            )

            variants.forEachIndexed { index, walls ->
                val actual = renderNetwork(context, drawWallNetwork, walls)
                assertTrue(
                    "Rendering changed at $degrees degrees for endpoint/order variant $index",
                    expected.sameAs(actual),
                )
            }
        }
    }

    private fun renderNetwork(
        context: android.content.Context,
        drawWallNetwork: java.lang.reflect.Method,
        walls: List<SketchWall>,
    ): Bitmap {
        val size = 320
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
        val view = SketchView(context).apply { restoreState(SketchState(walls = walls)) }
        drawWallNetwork.invoke(view, canvas, 0.7f, size / 2f, size / 2f)
        return bitmap
    }
}
