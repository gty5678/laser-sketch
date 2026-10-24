package com.example.boschlaser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.platform.AndroidCanvasDrawing
import com.example.boschlaser.shared.editor.*
import com.example.boschlaser.shared.geometry.Segment2
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchSceneDrawingInstrumentedTest {
    @get:org.junit.Rule val foregroundHost: org.junit.rules.TestRule = ForegroundTestHostRule()

    private val green = androidx.compose.ui.graphics.Color.Green
    private val white = androidx.compose.ui.graphics.Color.White
    private val gray = androidx.compose.ui.graphics.Color.Gray
    private val black = androidx.compose.ui.graphics.Color.Black
    private val control = ControlDrawingStyle(green, white)
    private val style = SketchSceneStyle(
        GridDrawingStyle(white, 1f, white, 1f), WallDrawingStyle(gray, black, 2f), WallDrawingStyle(gray, green, 4f),
        OpeningDrawingStyle(black, 3f, black, 2f), OpeningDrawingStyle(green, 4f, black, 2f),
        ColumnDrawingStyle(gray, green, 4f, green, white, 8f), WallControlDrawingStyle(green, 2f, null, control),
        WallControlDrawingStyle(green, 4f, null, control), TextStyle(color = black, fontSize = 13.sp), green, 4f, 16.0)

    private fun render(document: SketchDocument, state: EditorState, showGrid: Boolean,
        placement: PlacementSettings = PlacementSettings(), background: Int = Color.WHITE,
        showAutomaticDimensions: Boolean = true, assertImage: (Bitmap, List<Double>) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
            try {
                val measurer = TextMeasurer(createFontFamilyResolver(instrumentation.targetContext), Density(1f), LayoutDirection.Ltr)
                val formatted = mutableListOf<Double>()
                AndroidCanvasDrawing().draw(Canvas(bitmap).apply { drawColor(background) }, Density(1f)) {
                    drawSketchScene(document, state, placement, Viewport(.1, Vec2(20.0, 50.0)), Vec2(400.0, 300.0), style,
                        SketchSceneOptions(100.0, showGrid, showGrid, rotationHandleOffsetPx = 32.0,
                            showAutomaticDimensions = showAutomaticDimensions), measurer) {
                        formatted += it
                        "${it.toInt()} mm"
                    }
                }
                assertImage(bitmap, formatted)
            } finally { bitmap.recycle() }
        }
    }

    @Test fun exportOmitsSelectionAndCreationPreviewWithoutChangingTheDocument() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(3000.0, 0.0), 200.0, measuredLength = 3123.123456789)
        val document = SketchDocument(walls = mapOf(wall.id to wall))
        val state = EditorState(tool = ToolType.WALL, selection = Selection.Wall(wall.id),
            preview = SketchPreview.Wall(Segment2(Vec2(0.0, 1000.0), Vec2(3000.0, 1000.0))))
        render(document, state, false) { bitmap, formatted ->
            assertEquals(Color.GRAY, bitmap.getPixel(170, 50))
            assertEquals(Color.WHITE, bitmap.getPixel(170, 150))
            assertTrue(formatted.isEmpty())
        }
        assertEquals(wall, document.walls.getValue(wall.id))
        assertEquals(1, document.walls.size)
    }

    @Test fun wallPreviewUsesPlacementAlignmentEvenWhenItsIdMatchesAnExistingWall() {
        val wall = Wall("preview", Vec2(0.0, 0.0), Vec2(3000.0, 0.0), 200.0)
        val document = SketchDocument(walls = mapOf(wall.id to wall))
        val state = EditorState(tool = ToolType.WALL,
            preview = SketchPreview.Wall(Segment2(Vec2(0.0, 1000.0), Vec2(3000.0, 1000.0))))
        render(document, state, true, PlacementSettings(wallThickness = 200.0, alignment = WallAlignment.LEFT)) { bitmap, formatted ->
            assertEquals(Color.GRAY, bitmap.getPixel(170, 160))
            assertEquals(Color.GREEN, bitmap.getPixel(170, 150))
            assertEquals(Color.WHITE, bitmap.getPixel(170, 140))
            assertTrue(formatted.isEmpty())
        }
        assertEquals(wall, document.walls.getValue(wall.id))
    }

    @Test fun columnPreviewUsesItsOwnDimensionsAndAlwaysDrawsThePlacementCenter() {
        val column = RectColumn("preview", Vec2(1000.0, 0.0), 400.0, 400.0)
        val preview = RectColumn("preview", Vec2(2000.0, 1000.0), 600.0, 500.0)
        val document = SketchDocument(columns = mapOf(column.id to column))
        render(document, EditorState(tool = ToolType.RECT_COLUMN, preview = SketchPreview.Column(preview)), true) { bitmap, formatted ->
            assertEquals(Color.GREEN, bitmap.getPixel(220, 150))
            assertEquals(Color.GRAY, bitmap.getPixel(245, 150))
            assertEquals(Color.WHITE, bitmap.getPixel(260, 150))
            assertEquals(Color.GRAY, bitmap.getPixel(120, 50))
            assertTrue(formatted.isEmpty())
        }
        assertEquals(column, document.columns.getValue(column.id))
    }
    @Test fun permanentFaceDimensionDrawsNetDistanceInExportButUnconfirmedDimensionDoesNot() {
        val first = Wall("first", Vec2(0.0, 0.0), Vec2(0.0, 2000.0), 200.0)
        val second = Wall("second", Vec2(3000.0, 0.0), Vec2(3000.0, 2000.0), 200.0)
        val dimension = WallDimension("dimension", WallFaceReference(first.id, WallFaceSide.RIGHT),
            WallFaceReference(second.id, WallFaceSide.LEFT), Vec2(1500.0, 1000.0))
        val document = SketchDocument(walls = mapOf(first.id to first, second.id to second), dimensions = mapOf(dimension.id to dimension))
        render(document, EditorState(selection = Selection.Dimension(dimension.id)), false, background = Color.CYAN) { bitmap, formatted ->
            assertEquals(listOf(2800.0), formatted)
            assertNotEquals("Exported dimensions use a thin neutral line", Color.CYAN, bitmap.getPixel(100, 150))
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val measurer = TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr)
            val bounds = sketchDimensionLabelBounds(document, dimension, Viewport(.1, Vec2(20.0, 50.0)),
                measurer, style.label, Density(1f)) { "${it.toInt()} mm" }!!
            assertEquals("Dimension text must not paint a white background", Color.CYAN,
                bitmap.getPixel(kotlin.math.ceil(bounds.min.x).toInt() + 1, kotlin.math.ceil(bounds.min.y).toInt() + 1))
            assertNotEquals("Architectural dimension lines remain continuous below the text", Color.CYAN,
                bitmap.getPixel(kotlin.math.ceil(bounds.min.x).toInt() + 1, 150))
        }
        render(document, EditorState(selection = Selection.Dimension(dimension.id)), true) { bitmap, formatted ->
            assertEquals(listOf(2800.0), formatted)
            assertEquals("Selected dimensions must not add a movement arrow over the wall", Color.GRAY, bitmap.getPixel(328, 150))
        }
        render(document.copy(dimensions = emptyMap()), EditorState(tool = ToolType.DIMENSION,
            preview = SketchPreview.Dimension(dimension)), false) { bitmap, formatted ->
            assertTrue(formatted.isEmpty())
            assertEquals(Color.WHITE, bitmap.getPixel(100, 150))
        }
    }

    @Test fun referencedOpeningAndColumnEmphasizeOnlyTheirEditableDimensionAndExportStaysNeutral() {
        val wall = Wall("host", Vec2(0.0, 0.0), Vec2(0.0, 2000.0), 200.0)
        val opening = Opening("window", wall.id, 1000.0, 500.0, OpeningType.WINDOW)
        val column = RectColumn("column", Vec2(3000.0, 1000.0), 400.0, 400.0)
        val dimension = WallDimension("dimension", OpeningFrameReference(opening.id, OpeningFrameSide.RIGHT),
            ColumnLineReference(column.id, ColumnReferenceLine.LEFT), Vec2(1500.0, 600.0))
        val document = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(opening.id to opening),
            columns = mapOf(column.id to column), dimensions = mapOf(dimension.id to dimension))
        var normalInk = 0
        render(document, EditorState(selection = Selection.Wall(wall.id)), true, showAutomaticDimensions = false) { bitmap, _ ->
            val pixel = bitmap.getPixel(100, 110)
            assertEquals(Color.red(pixel), Color.green(pixel))
            assertTrue(Color.red(pixel) < 255)
            normalInk = (107..113).sumOf { 255 - Color.red(bitmap.getPixel(100, it)) }
        }
        for (selection in listOf(Selection.Opening(opening.id), Selection.Column(column.id))) {
            render(document, EditorState(selection = selection), true, showAutomaticDimensions = false) { bitmap, _ ->
                val pixel = bitmap.getPixel(100, 110)
                assertTrue("Editable dimensions use the active tint", Color.green(pixel) > Color.red(pixel))
                val activeInk = (107..113).sumOf { 255 - Color.red(bitmap.getPixel(100, it)) }
                assertTrue("Editable dimensions are thicker", activeInk > normalInk)
            }
            render(document, EditorState(selection = selection), false) { bitmap, _ ->
                assertEquals(normalInk, (107..113).sumOf { 255 - Color.red(bitmap.getPixel(100, it)) })
                val pixel = bitmap.getPixel(100, 110)
                assertEquals(Color.red(pixel), Color.green(pixel))
            }
        }
    }

    @Test fun hidingAutomaticDimensionsKeepsPermanentDimensions() {
        val first = Wall("first", Vec2(0.0, 0.0), Vec2(0.0, 2000.0), 200.0)
        val second = Wall("second", Vec2(3000.0, 0.0), Vec2(3000.0, 2000.0), 200.0)
        val dimension = WallDimension("dimension", WallFaceReference(first.id, WallFaceSide.RIGHT),
            WallFaceReference(second.id, WallFaceSide.LEFT), Vec2(1500.0, 600.0))
        val document = SketchDocument(walls = mapOf(first.id to first, second.id to second),
            dimensions = mapOf(dimension.id to dimension))
        val state = EditorState(selection = Selection.Wall(second.id))
        render(document, state, true) { _, formatted -> assertEquals(listOf(2800.0, 2800.0), formatted) }
        render(document, state, true, showAutomaticDimensions = false) { _, formatted ->
            assertEquals(listOf(2800.0), formatted)
        }
        render(document.copy(dimensions = emptyMap()), state, true, showAutomaticDimensions = false) { _, formatted ->
            assertTrue(formatted.isEmpty())
        }
    }

    @Test fun pendingMeasurementStartDrawsOnlyMovingPointWithoutDistanceAndIsAbsentFromExport() {
        val point = Vec2(1500.0, 1000.0)
        val state = EditorState(tool = ToolType.MEASURE, snap = SnapResult(point, SnapType.NONE), magnifierTarget = point)
        render(SketchDocument(), state, true) { bitmap, formatted ->
            assertEquals(Color.GREEN, bitmap.getPixel(170, 150))
            assertTrue(formatted.isEmpty())
        }
        render(SketchDocument(), state, false) { bitmap, formatted ->
            assertEquals(Color.WHITE, bitmap.getPixel(170, 150))
            assertTrue(formatted.isEmpty())
        }
    }

    @Test fun measurementDrawsItsDistanceAndEndpointsButIsAbsentFromExport() {
        val line = Segment2(Vec2(0.0, 1000.0), Vec2(3000.125, 1000.0))
        val state = EditorState(tool = ToolType.MEASURE, preview = SketchPreview.Measurement(line, complete = true))
        val document = SketchDocument()
        render(document, state, true) { bitmap, formatted ->
            assertEquals(Color.GREEN, bitmap.getPixel(170, 150))
            assertEquals(Color.GREEN, bitmap.getPixel(20, 150))
            assertEquals(Color.GREEN, bitmap.getPixel(320, 150))
            assertEquals(listOf(3000.125), formatted)
        }
        render(document, state, false) { bitmap, formatted ->
            assertEquals(Color.WHITE, bitmap.getPixel(170, 150))
            assertEquals(Color.WHITE, bitmap.getPixel(20, 150))
            assertTrue(formatted.isEmpty())
        }
        assertEquals(SketchDocument(), document)
    }

}
