package com.example.boschlaser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
    @get:org.junit.Rule val foregroundHost: org.junit.rules.TestRule = ForegroundTestHostRule()

    @Test
    fun nearlyStraightThreeWallNodeHasNoInteriorSeamOnTheBackOfTheRun() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val joint = SketchPoint(0f, 0f)
            val angle = Math.toRadians(175.0)
            val walls = listOf(
                SketchWall(id = "east", start = joint, end = SketchPoint(400f, 0f), thickness = 240f),
                SketchWall(id = "west", start = joint,
                    end = SketchPoint((cos(angle) * 400).toFloat(), (sin(angle) * 400).toFloat()), thickness = 240f),
                SketchWall(id = "stem", start = joint, end = SketchPoint(0f, 400f), thickness = 60f),
            )
            val bitmap = renderNetwork(context, walls)
            val origin = bitmap.width / 2
            for (x in -3..-1) for (y in -51..-47) {
                assertEquals("The back of the nearly straight run must be fill, without a slit or cap outline at $x,$y",
                    Color.rgb(245, 245, 242), bitmap.getPixel(origin + x, origin + y))
            }
            bitmap.recycle()
        }
    }

    @Test
    fun renderThreeWallAlignmentReviewSheet() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val sheet = Bitmap.createBitmap(1600, 960, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet).apply { drawColor(Color.WHITE) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 20f }
            SketchWallControlLine.entries.forEachIndexed { row, control ->
                listOf(30.0, 45.0, 90.0, 135.0, 150.0).forEachIndexed { column, degrees ->
                    val radians = Math.toRadians(degrees)
                    val joint = SketchPoint(0f, 0f)
                    val walls = listOf(
                        SketchWall(id = "east", start = joint, end = SketchPoint(180f, 0f),
                            thickness = 80f, controlLine = SketchWallControlLine.INNER),
                        SketchWall(id = "west", start = joint, end = SketchPoint(-180f, 0f),
                            thickness = 80f, controlLine = SketchWallControlLine.OUTER),
                        SketchWall(id = "stem", start = joint,
                            end = SketchPoint((cos(radians) * 180).toFloat(), (sin(radians) * 180).toFloat()),
                            thickness = 60f, controlLine = control),
                    ).map { wallFromControlLine(it, it.start, it.end) }
                    val bitmap = renderNetwork(context, walls)
                    canvas.drawBitmap(bitmap, column * 320f, row * 320f, null)
                    canvas.drawText("$control $degrees", column * 320f + 8, row * 320f + 25, paint)
                    bitmap.recycle()
                }
            }
            val output = File(context.getExternalFilesDir(null), "three-wall-review.png")
            FileOutputStream(output).use {
                sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            assertTrue(output.exists() && output.length() > 0)
            sheet.recycle()
        }
    }

    @Test
    fun sameSideStraightRunWithObliqueBranchDoesNotFillOutsideTheNode() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val joint = SketchPoint(0f, 0f)
            val walls = listOf(
                SketchWall(id = "east", start = joint, end = SketchPoint(180f, 0f),
                    thickness = 120f, controlLine = SketchWallControlLine.INNER),
                SketchWall(id = "west", start = joint, end = SketchPoint(-180f, 0f),
                    thickness = 60f, controlLine = SketchWallControlLine.OUTER),
                SketchWall(id = "stem", start = joint, end = SketchPoint(150f, 150f), thickness = 48f),
            ).map { wallFromControlLine(it, it.start, it.end) }
            val bitmap = renderNetwork(context, walls)
            val origin = bitmap.width / 2
            assertEquals("No corner may protrude below the straight run behind the branch",
                Color.WHITE, bitmap.getPixel(origin - 7, origin - 3))
            assertEquals("The branch body cap must not protrude beyond the straight run face",
                Color.WHITE, bitmap.getPixel(origin + 4, origin - 3))
            assertEquals("The branch must remain fused to the straight run",
                Color.rgb(245, 245, 242), bitmap.getPixel(origin - 7, origin + 21))
            bitmap.recycle()
        }
    }

    @Test
    fun obliqueTJunctionFusesInsideHostWithoutProtrudingPastFarFace() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
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

            val bitmap = renderNetwork(context, listOf(host, stem))
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
            bitmap.recycle()
        }
    }

    @Test
    fun renderSharedEndpointThroughFullRotation() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
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
                view.drawWallNetworkFixture(
                    cellCanvas,
                    0.7f,
                    cellWidth / 2f,
                    (cellHeight - 30) / 2f,
                )
                canvas.drawBitmap(cellBitmap, left.toFloat(), (top + 30).toFloat(), null)
                cellBitmap.recycle()
                canvas.drawText("$degrees°", (left + 8).toFloat(), (top + 25).toFloat(), labelPaint)
            }

            val output = File(context.getExternalFilesDir(null), "wall-joint-rotation.png")
            FileOutputStream(output).use { stream -> sheet.compress(Bitmap.CompressFormat.PNG, 100, stream) }
            assertTrue(output.exists() && output.length() > 0)
            sheet.recycle()
        }
    }

    @Test
    fun sharedEndpointRenderingIsIndependentOfEndpointStorageAndWallOrder() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
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
                val expected = renderNetwork(context, listOf(fixed, rotating))
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
                    val actual = renderNetwork(context, walls)
                    assertTrue(
                        "Rendering changed at $degrees degrees for endpoint/order variant $index",
                        expected.sameAs(actual),
                    )
                    actual.recycle()
                }
                expected.recycle()
            }
        }
    }

    @Test
    fun multiWallNodesRenderTheSameAfterReorderingAndReversingEndpoints() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            fun wall(id: String, degrees: Double): SketchWall {
                val radians = Math.toRadians(degrees)
                return SketchWall(id = id, start = SketchPoint(0f, 0f),
                    end = SketchPoint((cos(radians) * 180.0).toFloat(),
                        (sin(radians) * 180.0).toFloat()), thickness = 80f)
            }
            val configurations = listOf(
                listOf(wall("east", 0.0), wall("west", 180.0), wall("stem", 90.0)),
                listOf(wall("a", 0.0), wall("b", 120.0), wall("c", 240.0)),
                listOf(wall("east", 0.0), wall("north", 90.0),
                    wall("west", 180.0), wall("south", 270.0)),
            )
            configurations.forEach { walls ->
                val expected = renderNetwork(context, walls)
                val reversed = walls.reversed().mapIndexed { index, wall ->
                    if (index % 2 == 0) wall.copy(start = wall.end, end = wall.start) else wall
                }
                val actual = renderNetwork(context, reversed)
                assertTrue("Multi-wall node changed with wall order or endpoint direction", expected.sameAs(actual))
                expected.recycle()
                actual.recycle()
            }
        }
    }

    private fun renderNetwork(
        context: android.content.Context,
        walls: List<SketchWall>,
    ): Bitmap {
        val size = 320
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
        val view = SketchView(context).apply { restoreState(SketchState(walls = walls)) }
        view.drawWallNetworkFixture(canvas, 0.7f, size / 2f, size / 2f)
        return bitmap
    }
}
