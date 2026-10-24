package com.example.boschlaser

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.shared.editor.Selection
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.geometry.WallDimensionGeometry
import com.example.boschlaser.shared.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SketchDimensionInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var automaticPreference: Boolean? = null
    @Before fun isolateAutomaticDimensionPreference() {
        val preferences = context.getSharedPreferences("sketch_settings", android.content.Context.MODE_PRIVATE)
        automaticPreference = if (preferences.contains("show_automatic_dimensions"))
            preferences.getBoolean("show_automatic_dimensions", true) else null
        preferences.edit().putBoolean("show_automatic_dimensions", true).commit()
    }
    @After fun restoreAutomaticDimensionPreference() {
        val editor = context.getSharedPreferences("sketch_settings", android.content.Context.MODE_PRIVATE).edit()
        automaticPreference?.let { editor.putBoolean("show_automatic_dimensions", it) }
            ?: editor.remove("show_automatic_dimensions")
        editor.commit()
    }
    private fun view(activity: SketchActivity) = SketchActivity::class.java.getDeclaredField("sketchView")
        .apply { isAccessible = true }.get(activity) as SketchView
    private fun room(): SketchDocument {
        val walls = listOf(
            Wall("left", Vec2(-2000.0, -1600.0), Vec2(-2000.0, 1600.0), 200.0),
            Wall("right", Vec2(2000.0, -1600.0), Vec2(2000.0, 1600.0), 200.0),
            Wall("bottom", Vec2(-2000.0, -1600.0), Vec2(2000.0, -1600.0), 200.0),
            Wall("top", Vec2(-2000.0, 1600.0), Vec2(2000.0, 1600.0), 200.0),
        )
        val opening = Opening("door", "right", 1400.125, 800.0, OpeningType.DOOR)
        return SketchDocument(walls = walls.associateBy { it.id }, openings = mapOf(opening.id to opening))
    }

    @Test fun draggingPreviewAfterSecondReferenceMovesDimensionWithoutMovingViewportAndPlacesOnRelease() {
        val services = ProjectServices.get(context)
        val initial = room()
        val project = runBlocking { services.createSketch("Dimension preview drag fixture").await() }
        runBlocking { services.saveSketch(project.manifest.projectId, initial, null).await() }
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        try {
            compose.waitUntil(5000) {
                var loaded = false
                scenario.onActivity { loaded = view(it).isEnabled && view(it).snapshotDocument() == initial }
                loaded
            }
            fun screen(point: Vec2): Offset {
                var result = point
                scenario.onActivity { result = view(it).worldToScreen(point) }
                return Offset(result.x.toFloat(), result.y.toFloat())
            }
            fun tap(point: Vec2) {
                val position = screen(point)
                compose.onNodeWithTag("sketch-canvas").performTouchInput { click(position) }
            }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_tool_dimension)).performScrollTo().performClick()
            tap(Vec2(-1900.0, 800.0))
            tap(Vec2(1900.0, 800.0))
            var preview: com.example.boschlaser.shared.editor.SketchPreview.Dimension? = null
            var start = Offset.Zero
            scenario.onActivity {
                val canvas = view(it)
                preview = canvas.editorState().preview as com.example.boschlaser.shared.editor.SketchPreview.Dimension
                val density = androidx.compose.ui.unit.Density(context.resources.displayMetrics.density, context.resources.configuration.fontScale)
                val style = com.example.boschlaser.platform.androidSketchDrawingStyle(context, density, 10f * density.density)
                val measurer = androidx.compose.ui.text.TextMeasurer(androidx.compose.ui.text.font.createFontFamilyResolver(context),
                    density, androidx.compose.ui.unit.LayoutDirection.Ltr)
                val origin = canvas.worldToScreen(Vec2(0.0, 0.0))
                val scale = (canvas.worldToScreen(Vec2(1000.0, 0.0)).x - origin.x) / 1000.0
                val bounds = com.example.boschlaser.ui.sketchDimensionLabelBounds(initial, preview!!.dimension,
                    com.example.boschlaser.shared.editor.Viewport(scale, origin), measurer, style.label, density,
                    textGapPx = style.dimension.textGapPx) { formatSketchMillimeters(it) }!!
                start = Offset(((bounds.min.x + bounds.max.x) * .5).toFloat(), ((bounds.min.y + bounds.max.y) * .5).toFloat())
            }
            val target = start + (screen(Vec2(0.0, -700.0)) - screen(Vec2(0.0, 800.0)))
            val origin = screen(Vec2(0.0, 0.0))
            compose.onNodeWithTag("sketch-canvas").performTouchInput { down(start) }
            scenario.onActivity { assertEquals("Pressing text must not move the line", preview, view(it).editorState().preview) }
            compose.onNodeWithTag("sketch-canvas").performTouchInput { moveTo(target) }
            scenario.onActivity {
                val canvas = view(it)
                assertTrue(canvas.editorState().dimensionGesture!!.movingDimension)
                val dimension = (canvas.editorState().preview as com.example.boschlaser.shared.editor.SketchPreview.Dimension).dimension
                assertEquals(-700.0, WallDimensionGeometry.layout(initial, dimension)!!.line.start.y, .02)
                assertEquals(initial, canvas.snapshotDocument())
            }
            assertEquals(origin, screen(Vec2(0.0, 0.0)))
            compose.onNodeWithTag("sketch-canvas").performTouchInput { up() }
            scenario.onActivity {
                val canvas = view(it)
                val document = canvas.snapshotDocument()
                assertEquals(1, document.dimensions.size)
                val layout = WallDimensionGeometry.layout(document, document.dimensions.values.single())!!
                assertEquals(-700.0, layout.line.start.y, .02)
                assertEquals(3800.0, layout.valueMm, .01)
                assertEquals(initial.walls, document.walls)
                assertTrue(canvas.undo())
                assertEquals(initial, canvas.snapshotDocument())
                assertFalse(canvas.undo())
            }
        } finally {
            finishAndCloseScenario(scenario)
            runBlocking { services.flush().await(); services.delete(project.manifest.projectId).await() }
        }
    }

    @Test fun singleFingerDragPansBeforeAndAfterReferencePicksWithoutInterruptingDimensionCreation() {
        val services = ProjectServices.get(context)
        val initial = room()
        val project = runBlocking { services.createSketch("Dimension pan fixture").await() }
        runBlocking { services.saveSketch(project.manifest.projectId, initial, null).await() }
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        try {
            compose.waitUntil(5000) {
                var loaded = false
                scenario.onActivity { loaded = view(it).isEnabled && view(it).snapshotDocument() == initial }
                loaded
            }
            fun tap(point: Vec2) {
                var screen = point
                scenario.onActivity { screen = view(it).worldToScreen(point) }
                compose.onNodeWithTag("sketch-canvas").performTouchInput { click(Offset(screen.x.toFloat(), screen.y.toFloat())) }
            }
            fun pan() {
                var origin = Vec2(0.0, 0.0)
                var first: DimensionReference? = null
                var preview: com.example.boschlaser.shared.editor.SketchPreview? = null
                scenario.onActivity {
                    origin = view(it).worldToScreen(Vec2(0.0, 0.0))
                    first = view(it).editorState().dimensionFirst
                    preview = view(it).editorState().preview
                }
                var start = Offset.Zero
                compose.onNodeWithTag("sketch-canvas").performTouchInput {
                    start = Offset(width * .45f, height * .45f)
                    down(start)
                }
                scenario.onActivity { assertEquals("Blank press must not teleport the preview", preview, view(it).editorState().preview) }
                compose.onNodeWithTag("sketch-canvas").performTouchInput { moveTo(start + Offset(2f, 1f)) }
                scenario.onActivity { assertEquals("Tap jitter must not move the preview", preview, view(it).editorState().preview) }
                compose.onNodeWithTag("sketch-canvas").performTouchInput { moveTo(start + Offset(40f, 30f)); up() }
                scenario.onActivity {
                    val canvas = view(it)
                    val moved = canvas.worldToScreen(Vec2(0.0, 0.0)) - origin
                    assertEquals(40.0, moved.x, 1.0)
                    assertEquals(30.0, moved.y, 1.0)
                    assertEquals(first, canvas.editorState().dimensionFirst)
                    assertEquals(preview, canvas.editorState().preview)
                    assertNull(canvas.editorState().snap)
                    assertNull(canvas.editorState().magnifierTarget)
                    assertNull(canvas.editorState().dimensionGesture)
                    assertEquals(initial, canvas.snapshotDocument())
                }
            }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_tool_dimension)).performScrollTo().performClick()
            pan()
            tap(Vec2(-1900.0, 800.0))
            scenario.onActivity { assertEquals(WallFaceReference("left", WallFaceSide.RIGHT), view(it).editorState().dimensionFirst) }
            pan()
            tap(Vec2(1900.0, 800.0))
            scenario.onActivity { assertTrue(view(it).editorState().preview is com.example.boschlaser.shared.editor.SketchPreview.Dimension) }
            pan()
            tap(Vec2(0.0, -700.0))
            scenario.onActivity {
                val canvas = view(it)
                assertEquals(1, canvas.snapshotDocument().dimensions.size)
                assertEquals(3800.0, WallDimensionGeometry.layout(canvas.snapshotDocument(), canvas.snapshotDocument().dimensions.values.single())!!.valueMm, .01)
                assertTrue(canvas.undo())
                assertEquals(initial, canvas.snapshotDocument())
                assertFalse(canvas.undo())
            }
        } finally {
            finishAndCloseScenario(scenario)
            runBlocking { services.flush().await(); services.delete(project.manifest.projectId).await() }
        }
    }

    @Test fun pinchZoomBetweenDimensionPicksAndBeforePlacementKeepsTheCreationSession() {
        val services = ProjectServices.get(context)
        val initial = room()
        val project = runBlocking { services.createSketch("Dimension zoom fixture").await() }
        runBlocking { services.saveSketch(project.manifest.projectId, initial, null).await() }
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        try {
            compose.waitUntil(5000) {
                var loaded = false
                scenario.onActivity { loaded = view(it).isEnabled && view(it).snapshotDocument() == initial }
                loaded
            }
            fun tap(point: Vec2) {
                var screen = point
                scenario.onActivity { screen = view(it).worldToScreen(point) }
                compose.onNodeWithTag("sketch-canvas").performTouchInput { click(Offset(screen.x.toFloat(), screen.y.toFloat())) }
            }
            fun scale(): Double {
                var value = 0.0
                scenario.onActivity { value = view(it).worldToScreen(Vec2(1000.0, 0.0)).x - view(it).worldToScreen(Vec2(0.0, 0.0)).x }
                return value
            }
            fun pinch(outward: Boolean) {
                compose.onNodeWithTag("sketch-canvas").performTouchInput {
                    val narrow0 = Offset(width * .4f, height * .5f)
                    val narrow1 = Offset(width * .6f, height * .5f)
                    val wide0 = Offset(width * .25f, height * .5f)
                    val wide1 = Offset(width * .75f, height * .5f)
                    if (outward) pinch(start0 = narrow0, end0 = wide0, start1 = narrow1, end1 = wide1)
                    else pinch(start0 = wide0, end0 = narrow0, start1 = wide1, end1 = narrow1)
                }
            }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_tool_dimension)).performScrollTo().performClick()
            tap(Vec2(-1900.0, 800.0))
            val before = scale()
            pinch(true)
            assertTrue("Pinch must actually zoom the viewport", scale() > before * 1.1)
            scenario.onActivity {
                assertEquals(WallFaceReference("left", WallFaceSide.RIGHT), view(it).editorState().dimensionFirst)
                assertNull(view(it).editorState().preview)
                assertNull(view(it).editorState().snap)
                assertNull(view(it).editorState().magnifierTarget)
                assertEquals(initial, view(it).snapshotDocument())
            }
            // Return to the original framing before picking the opposite wall.
            pinch(false)
            scenario.onActivity {
                assertEquals("The second pinch must also preserve the first pick", WallFaceReference("left", WallFaceSide.RIGHT),
                    view(it).editorState().dimensionFirst)
            }
            tap(Vec2(1900.0, 800.0))
            var preview: com.example.boschlaser.shared.editor.SketchPreview.Dimension? = null
            scenario.onActivity {
                assertTrue("Second pick must create a preview: ${view(it).editorState()}; screen=${view(it).worldToScreen(Vec2(1900.0, 800.0))}; size=${view(it).width}x${view(it).height}",
                    view(it).editorState().preview is com.example.boschlaser.shared.editor.SketchPreview.Dimension)
                preview = view(it).editorState().preview as com.example.boschlaser.shared.editor.SketchPreview.Dimension
            }
            val zoomedOut = scale()
            pinch(true)
            assertTrue(scale() > zoomedOut * 1.1)
            pinch(false)
            scenario.onActivity {
                val retained = view(it).editorState().preview as com.example.boschlaser.shared.editor.SketchPreview.Dimension
                assertEquals(preview!!.dimension.id, retained.dimension.id)
                assertEquals(preview!!.dimension.first, retained.dimension.first)
                assertEquals(preview!!.dimension.second, retained.dimension.second)
                assertEquals(initial, view(it).snapshotDocument())
                assertNull(view(it).editorState().snap)
                assertNull(view(it).editorState().magnifierTarget)
            }
            tap(Vec2(0.0, -700.0))
            scenario.onActivity {
                val canvas = view(it)
                assertEquals(1, canvas.snapshotDocument().dimensions.size)
                val dimension = canvas.snapshotDocument().dimensions.values.single()
                assertEquals(preview!!.dimension.first, dimension.first)
                assertEquals(preview!!.dimension.second, dimension.second)
                assertEquals(3800.0, WallDimensionGeometry.layout(canvas.snapshotDocument(), dimension)!!.valueMm, .01)
                assertTrue(canvas.undo())
                assertEquals(initial, canvas.snapshotDocument())
                assertFalse(canvas.undo())
            }
        } finally {
            finishAndCloseScenario(scenario)
            runBlocking { services.flush().await(); services.delete(project.manifest.projectId).await() }
        }
    }

    @Test fun newDimensionSnapsToBothExistingTicksOutsideWallsAndStillDrives() {
        val services = ProjectServices.get(context)
        val existing = WallDimension("existing", WallFaceReference("left", WallFaceSide.RIGHT),
            WallFaceReference("right", WallFaceSide.LEFT), Vec2(0.0, 2300.0))
        val initial = room().copy(dimensions = mapOf(existing.id to existing))
        val project = runBlocking { services.createSketch("Dimension endpoint snap fixture").await() }
        runBlocking { services.saveSketch(project.manifest.projectId, initial, null).await() }
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        try {
            compose.waitUntil(5000) {
                var loaded = false
                scenario.onActivity { loaded = view(it).isEnabled && view(it).snapshotDocument() == initial }
                loaded
            }
            fun screen(point: Vec2): Offset {
                var result = point
                scenario.onActivity { result = view(it).worldToScreen(point) }
                return Offset(result.x.toFloat(), result.y.toFloat())
            }
            fun tap(point: Vec2) {
                val position = screen(point)
                compose.onNodeWithTag("sketch-canvas").performTouchInput { click(position) }
            }
            fun capture(point: Vec2, expected: Vec2) {
                val position = screen(point)
                compose.onNodeWithTag("sketch-canvas").performTouchInput { down(position) }
                scenario.onActivity {
                    assertEquals(expected, view(it).editorState().snap?.point)
                    assertEquals(expected, view(it).editorState().magnifierTarget)
                    assertEquals(initial, view(it).snapshotDocument())
                }
                compose.onNodeWithTag("sketch-canvas").performTouchInput { up() }
            }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_tool_dimension)).performScrollTo().performClick()
            capture(Vec2(-1850.0, 2340.0), Vec2(-1900.0, 2300.0))
            capture(Vec2(1850.0, 2340.0), Vec2(1900.0, 2300.0))
            tap(Vec2(0.0, -700.0))
            scenario.onActivity {
                val added = view(it).snapshotDocument().dimensions.values.single { it.id != existing.id }
                assertEquals(existing.first, added.first)
                assertEquals(existing.second, added.second)
                assertNull(view(it).editorState().snap)
            }
            tap(Vec2(2000.0, 800.0))
            tap(Vec2(0.0, -700.0))
            val input = compose.onNodeWithContentDescription(context.getString(R.string.sketch_dimension_value))
            input.performTextReplacement("4200.125")
            input.performImeAction()
            compose.waitForIdle()
            scenario.onActivity {
                val document = view(it).snapshotDocument()
                assertEquals(2, document.dimensions.size)
                assertEquals(existing, document.dimensions.getValue(existing.id))
                document.dimensions.values.forEach { dimension ->
                    assertEquals(4200.125, WallDimensionGeometry.layout(document, dimension)!!.valueMm, .01)
                }
                assertEquals(initial.walls.getValue("left"), document.walls.getValue("left"))
            }
        } finally {
            finishAndCloseScenario(scenario)
            runBlocking { services.flush().await(); services.delete(project.manifest.projectId).await() }
        }
    }

    @Test fun wallCenterToColumnCenterCanBePlacedDrivenAndReopened() {
        extendedReferenceWorkflow(Selection.Column("column"), Vec2(-2000.0, 500.0), Vec2(-500.0, 500.0),
            Vec2(-1000.0, 200.0), Vec2(-500.0, 500.0), 1750.125) { initial, result ->
            assertEquals(WallFaceReference("left", WallFaceSide.CENTER), result.dimensions.values.single().first)
            assertEquals(ColumnLineReference("column", ColumnReferenceLine.CENTER_Y), result.dimensions.values.single().second)
            assertEquals(Vec2(-249.875, 500.0), result.columns.getValue("column").center)
            assertEquals(initial.walls, result.walls)
            assertEquals(initial.openings, result.openings)
        }
    }

    @Test fun sameWallStartThenEndDimensionResizesWallAndPersists() = wallLengthWorkflow(false)

    @Test fun sameWallEndThenStartDimensionResizesWallAndPersists() = wallLengthWorkflow(true)

    private fun wallLengthWorkflow(reverse: Boolean) {
        val start = Vec2(-2000.0, -1600.0)
        val end = Vec2(-2000.0, 1600.0)
        extendedReferenceWorkflow(Selection.Wall("left"), if (reverse) end else start, if (reverse) start else end,
            Vec2(-1000.0, 0.0), Vec2(-2000.0, 0.0), 3800.125) { initial, result ->
            val dimension = result.dimensions.values.single()
            assertEquals(WallFaceReference("left", if (reverse) WallFaceSide.END else WallFaceSide.START), dimension.first)
            assertEquals(WallFaceReference("left", if (reverse) WallFaceSide.START else WallFaceSide.END), dimension.second)
            val wall = result.walls.getValue("left")
            assertEquals(if (reverse) end else start, if (reverse) wall.end else wall.start)
            assertEquals(3800.125, com.example.boschlaser.shared.geometry.Geometry.distance(wall.start, wall.end), .01)
            val follower = result.walls.getValue(if (reverse) "bottom" else "top")
            assertEquals(if (reverse) wall.start else wall.end, follower.start)
            assertEquals(initial.openings, result.openings)
            assertEquals(initial.columns, result.columns)
        }
    }

    @Test fun doorJambToWallCenterCanBePlacedDrivenAndReopened() {
        extendedReferenceWorkflow(Selection.Opening("door"), Vec2(-1000.0, -1600.0), Vec2(2000.0, -599.875),
            Vec2(800.0, -1000.0), Vec2(2000.0, -599.875), 1200.125) { initial, result ->
            assertEquals(WallFaceReference("bottom", WallFaceSide.CENTER), result.dimensions.values.single().first)
            assertEquals(OpeningFrameReference("door", OpeningFrameSide.START), result.dimensions.values.single().second)
            assertEquals(initial.openings.getValue("door").copy(offset = 1600.125), result.openings.getValue("door"))
            assertEquals(initial.walls, result.walls)
            assertEquals(initial.columns, result.columns)
        }
    }

    @Test fun doorJambFirstCanPickItsHostShortEndAndDriveDoor() {
        hostWallEndWorkflow(doorFirst = true)
    }

    @Test fun hostShortEndFirstCanPickDoorJambAndDriveDoor() {
        hostWallEndWorkflow(doorFirst = false)
    }

    private fun hostWallEndWorkflow(doorFirst: Boolean) {
        val doorPoint = Vec2(2000.0, -599.875)
        val endPoint = Vec2(2000.0, -1600.0)
        extendedReferenceWorkflow(Selection.Opening("door"), if (doorFirst) doorPoint else endPoint,
            if (doorFirst) endPoint else doorPoint, Vec2(1000.0, -1000.0), doorPoint, 1200.125) { initial, result ->
            val dimension = result.dimensions.values.single()
            val wallRef = WallFaceReference("right", WallFaceSide.START)
            val doorRef = OpeningFrameReference("door", OpeningFrameSide.START)
            assertEquals(if (doorFirst) doorRef else wallRef, dimension.first)
            assertEquals(if (doorFirst) wallRef else doorRef, dimension.second)
            assertEquals(initial.openings.getValue("door").copy(offset = 1600.125), result.openings.getValue("door"))
            assertEquals(initial.walls, result.walls)
            assertEquals(initial.columns, result.columns)
        }
    }

    private fun extendedReferenceWorkflow(selection: Selection, first: Vec2, second: Vec2, position: Vec2,
        objectPoint: Vec2, target: Double, verify: (SketchDocument, SketchDocument) -> Unit) {
        val services = ProjectServices.get(context)
        val column = RectColumn("column", Vec2(-500.0, 500.0), 600.0, 500.0)
        val initial = room().copy(columns = mapOf(column.id to column))
        val project = runBlocking { services.createSketch("Object dimension UI fixture").await() }
        runBlocking { services.saveSketch(project.manifest.projectId, initial, null).await() }
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        try {
            compose.waitUntil(5000) {
                var loaded = false
                scenario.onActivity { loaded = view(it).isEnabled && view(it).snapshotDocument() == initial }
                loaded
            }
            fun tap(point: Vec2) {
                var screen = point
                scenario.onActivity { screen = view(it).worldToScreen(point) }
                compose.onNodeWithTag("sketch-canvas").performTouchInput { click(Offset(screen.x.toFloat(), screen.y.toFloat())) }
            }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_tool_dimension)).performScrollTo().performClick()
            tap(first)
            tap(second)
            scenario.onActivity { assertTrue("Two reference picks must create a preview: ${view(it).editorState()}",
                view(it).editorState().preview is com.example.boschlaser.shared.editor.SketchPreview.Dimension) }
            tap(position)
            scenario.onActivity { assertEquals(1, view(it).snapshotDocument().dimensions.size) }
            tap(objectPoint)
            scenario.onActivity { assertEquals(selection, view(it).editorState().selection) }
            var label = position
            scenario.onActivity {
                val document = view(it).snapshotDocument()
                val layout = WallDimensionGeometry.layout(document, document.dimensions.values.single())!!
                label = (layout.line.start + layout.line.end) * .5
            }
            tap(label)
            val input = compose.onNodeWithContentDescription(context.getString(R.string.sketch_dimension_value))
            compose.waitUntil(5000) {
                try { input.assertIsFocused(); true } catch (_: AssertionError) { false }
            }
            input.performTextReplacement(target.toString())
            input.performImeAction()
            compose.waitForIdle()
            var result = initial
            scenario.onActivity {
                result = view(it).snapshotDocument()
                assertEquals(selection, view(it).editorState().selection)
                assertEquals(target, WallDimensionGeometry.layout(result, result.dimensions.values.single())!!.valueMm, .01)
                verify(initial, result)
            }
            scenario.recreate()
            compose.waitUntil(5000) {
                var loaded = false
                scenario.onActivity { loaded = view(it).isEnabled && view(it).snapshotDocument() == result }
                loaded
            }
        } finally {
            finishAndCloseScenario(scenario)
            runBlocking { services.flush().await(); services.delete(project.manifest.projectId).await() }
        }
    }

    @Test fun realCanvasCreatesDimensionAndPropertyInputDrivesConnectedRoomAndPersists() {
        val services = ProjectServices.get(context)
        val project = runBlocking { services.createSketch("Dimension UI fixture").await() }
        val initial = room()
        runBlocking { services.saveSketch(project.manifest.projectId, initial, null).await() }
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        var result: SketchDocument? = null
        try {
            compose.waitUntil(5000) {
                var loaded = false
                scenario.onActivity { loaded = view(it).isEnabled && view(it).snapshotDocument() == initial }
                loaded
            }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_tool_dimension)).performScrollTo().performClick()
            fun tap(point: Vec2) {
                var screen = point
                scenario.onActivity { screen = view(it).worldToScreen(point) }
                compose.onNodeWithTag("sketch-canvas").performTouchInput { click(Offset(screen.x.toFloat(), screen.y.toFloat())) }
            }
            tap(Vec2(-1900.0, 0.0))
            // Pick the wall outside its door cutout; frame edges now have their own references.
            tap(Vec2(1900.0, 800.0))
            scenario.onActivity { assertTrue(view(it).snapshotDocument().dimensions.isEmpty()) }
            tap(Vec2(0.0, 600.0))
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_tool_select)).assertIsOn()
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_tool_dimension)).assertIsOff()
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_action_delete)).assertIsDisplayed()
            compose.onNodeWithTag("sketch-property-sheet").assertDoesNotExist()
            tap(Vec2(0.0, 600.0))
            compose.onNode(isDialog()).assertDoesNotExist()
            tap(Vec2(2000.0, 800.0))
            scenario.onActivity { assertEquals(Selection.Wall("right"), view(it).editorState().selection) }
            tap(Vec2(0.0, 600.0))
            compose.waitUntil(5000) {
                try { compose.onNodeWithContentDescription(context.getString(R.string.sketch_dimension_value)).assertIsFocused(); true }
                catch (_: AssertionError) { false }
            }
            compose.onNodeWithText(context.getString(R.string.sketch_dimension_value)).performTextReplacement("4200")
            compose.onNodeWithText(context.getString(R.string.sketch_dimension_apply)).performClick()
            compose.waitForIdle()
            scenario.onActivity {
                val document = view(it).snapshotDocument()
                val dimension = document.dimensions.values.single()
                assertEquals(4200.0, WallDimensionGeometry.layout(document, dimension)!!.valueMm, .01)
                assertEquals(initial.walls.getValue("left"), document.walls.getValue("left"))
                assertEquals(Vec2(2400.0, -1600.0), document.walls.getValue("right").start)
                assertEquals(document.walls.getValue("right").start, document.walls.getValue("bottom").end)
                assertEquals(document.walls.getValue("right").end, document.walls.getValue("top").end)
                assertEquals(initial.openings, document.openings)
                result = document
            }
            compose.onNodeWithText(context.getString(R.string.sketch_dimension_reverse)).assertDoesNotExist()
            tap(Vec2(-2000.0, 800.0))
            scenario.onActivity { assertEquals(Selection.Wall("left"), view(it).editorState().selection) }
            tap(Vec2(200.0, 600.0))
            compose.onNodeWithText(context.getString(R.string.sketch_dimension_reverse)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.sketch_dimension_value)).performTextReplacement("4000")
            compose.onNodeWithText(context.getString(R.string.sketch_dimension_apply)).performClick()
            compose.waitForIdle()
            scenario.onActivity {
                val document = view(it).snapshotDocument()
                assertEquals(result!!.walls.getValue("right"), document.walls.getValue("right"))
                assertEquals(-1800.0, document.walls.getValue("left").start.x, .01)
                result = document
            }
            // Let Android's queued instructional toasts expire before recording the preview.
            SystemClock.sleep(7000)
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            assertNotNull(screenshot)
            try {
                File(context.externalCacheDir, "dimension-driven-preview.png").outputStream().use {
                    assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { screenshot.recycle() }
        } finally {
            finishAndCloseScenario(scenario)
            try {
                runBlocking {
                    services.flush().await()
                    if (result != null) assertEquals(result, services.loadSketch(project.manifest.projectId).await()!!.sketch)
                }
            } finally { runBlocking { services.delete(project.manifest.projectId).await() } }
        }
    }

    @Test fun automaticDimensionsSettingPersistsAndHiddenLabelsCannotActivate() {
        val preferences = context.getSharedPreferences("sketch_settings", android.content.Context.MODE_PRIVATE)
        val key = "show_automatic_dimensions"
        val existed = preferences.contains(key)
        val original = preferences.getBoolean(key, true)
        preferences.edit().remove(key).commit()
        val services = ProjectServices.get(context)
        val project = runBlocking { services.createSketch("Dimension visibility fixture").await() }
        val initial = room()
        runBlocking { services.saveSketch(project.manifest.projectId, initial, null).await() }
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        try {
            fun awaitLoaded() {
                compose.waitUntil(5000) {
                    var loaded = false
                    scenario.onActivity { loaded = view(it).isEnabled && view(it).snapshotDocument() == initial }
                    loaded
                }
            }
            fun tapLabel() {
                var screen = Vec2(0.0, 0.0)
                scenario.onActivity {
                    view(it).selectEntity(Selection.Wall("right"))
                    screen = view(it).worldToScreen(Vec2(0.0, 0.0))
                }
                compose.onNodeWithTag("sketch-canvas").performTouchInput { click(Offset(screen.x.toFloat(), screen.y.toFloat())) }
            }
            awaitLoaded()
            scenario.onActivity { assertTrue(view(it).showAutomaticDimensions()) }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_settings)).performClick()
            compose.onNodeWithText(context.getString(R.string.sketch_settings_automatic_dimensions))
                .assertIsOn().performClick().assertIsOff()
            compose.onNodeWithText(context.getString(R.string.sketch_property_apply)).performClick()
            scenario.onActivity { assertFalse(view(it).showAutomaticDimensions()) }
            assertFalse(preferences.getBoolean(key, true))
            scenario.recreate()
            awaitLoaded()
            scenario.onActivity { assertFalse(view(it).showAutomaticDimensions()) }
            tapLabel()
            compose.onNode(isDialog()).assertDoesNotExist()
            scenario.onActivity {
                assertEquals(initial, view(it).snapshotDocument())
                assertFalse(view(it).undo())
                view(it).setShowAutomaticDimensions(true)
            }
            tapLabel()
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_dimension_value)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.sketch_settings_cancel)).performClick()
        } finally {
            finishAndCloseScenario(scenario)
            runBlocking { services.flush().await(); services.delete(project.manifest.projectId).await() }
            if (existed) preferences.edit().putBoolean(key, original).commit()
            else preferences.edit().remove(key).commit()
        }
    }

    @Test fun deletingAndUndoingDimensionNeverChangesWallGeometry() {
        instrumentation.runOnMainSync {
            val dimension = WallDimension("dim", WallFaceReference("left", WallFaceSide.RIGHT),
                WallFaceReference("right", WallFaceSide.LEFT), Vec2(0.0, 0.0))
            val initial = room().copy(dimensions = mapOf(dimension.id to dimension))
            val view = SketchView(context)
            view.restoreDocument(initial)
            view.selectEntity(Selection.Dimension("dim"))
            var saves = 0
            view.onStateChanged = { saves++ }
            assertTrue(view.deleteSelected())
            assertTrue(view.snapshotDocument().dimensions.isEmpty())
            assertEquals(initial.walls, view.snapshotDocument().walls)
            assertEquals(1, saves)
            assertTrue(view.undo())
            assertEquals(initial, view.snapshotDocument())
            assertEquals(2, saves)
            assertFalse(view.applyDimension("dim", Double.NaN))
            assertEquals(initial, view.snapshotDocument())
            assertEquals(2, saves)
        }
    }

    @Test fun selectedWallNetDistanceAcceptsKeyboardInputAndMovesTheSelectedWallWithoutAnnotation() {
        val services = ProjectServices.get(context)
        val initial = room()
        val project = runBlocking { services.createSketch("Selected wall input fixture").await() }
        runBlocking { services.saveSketch(project.manifest.projectId, initial, null).await() }
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        var result: SketchDocument? = null
        try {
            compose.waitUntil(5000) {
                var loaded = false
                scenario.onActivity { loaded = view(it).isEnabled && view(it).snapshotDocument() == initial }
                loaded
            }
            fun tap(point: Vec2) {
                var screen = point
                scenario.onActivity { screen = view(it).worldToScreen(point) }
                compose.onNodeWithTag("sketch-canvas").performTouchInput { click(Offset(screen.x.toFloat(), screen.y.toFloat())) }
            }
            tap(Vec2(2000.0, 800.0))
            scenario.onActivity { assertEquals(Selection.Wall("right"), view(it).editorState().selection) }
            tap(Vec2(0.0, 0.0))
            val input = compose.onNodeWithContentDescription(context.getString(R.string.sketch_dimension_value))
            compose.waitUntil(5000) {
                try { input.assertIsFocused(); true } catch (_: AssertionError) { false }
            }
            input.assertIsDisplayed().assertIsFocused()
            input.performTextInput("4200.125")
            input.assertTextContains("4200.125")
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            try {
                File(context.externalCacheDir, "wall-net-distance-input.png").outputStream().use {
                    assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { screenshot.recycle() }
            input.performImeAction()
            compose.waitForIdle()
            scenario.onActivity {
                val document = view(it).snapshotDocument()
                assertTrue(document.dimensions.isEmpty())
                assertEquals(Selection.Wall("right"), view(it).editorState().selection)
                assertEquals(initial.walls.getValue("left"), document.walls.getValue("left"))
                assertEquals(2400.125, document.walls.getValue("right").start.x, .01)
                assertEquals(document.walls.getValue("right").start, document.walls.getValue("bottom").end)
                assertEquals(document.walls.getValue("right").end, document.walls.getValue("top").end)
                assertEquals(initial.openings, document.openings)
                result = document
            }
        } finally {
            finishAndCloseScenario(scenario)
            try {
                runBlocking {
                    services.flush().await()
                    result?.let { assertEquals(it, services.loadSketch(project.manifest.projectId).await()!!.sketch) }
                }
            } finally { runBlocking { services.delete(project.manifest.projectId).await() } }
        }
    }

    @Test fun selectedWallHasNoNetDistanceEntryAndOnlyNumericTapOpensInput() {
        val services = ProjectServices.get(context)
        val initial = room()
        val project = runBlocking { services.createSketch("Net distance cancel fixture").await() }
        runBlocking { services.saveSketch(project.manifest.projectId, initial, null).await() }
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        try {
            compose.waitUntil(5000) {
                var loaded = false
                scenario.onActivity { loaded = view(it).isEnabled && view(it).snapshotDocument() == initial }
                loaded
            }
            scenario.onActivity { view(it).selectEntity(Selection.Wall("right")) }
            compose.onNodeWithText(context.getString(R.string.sketch_wall_face_distance, 1, "3800")).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.sketch_wall_face_hint)).assertDoesNotExist()
            fun tap(point: Vec2) {
                var screen = point
                scenario.onActivity { screen = view(it).worldToScreen(point) }
                compose.onNodeWithTag("sketch-canvas").performTouchInput { click(Offset(screen.x.toFloat(), screen.y.toFloat())) }
            }
            tap(Vec2(-1000.0, 0.0))
            compose.onNode(isDialog()).assertDoesNotExist()
            scenario.onActivity { view(it).selectEntity(Selection.Wall("right")) }
            tap(Vec2(0.0, 0.0))
            val input = compose.onNodeWithContentDescription(context.getString(R.string.sketch_dimension_value))
            input.performTextReplacement("-1")
            compose.onNodeWithText(context.getString(R.string.sketch_dimension_apply)).performClick()
            input.assertIsDisplayed().assertTextContains("-1")
            compose.onNodeWithText(context.getString(R.string.sketch_dimension_drive_failed)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.sketch_settings_cancel)).performClick()
            scenario.onActivity {
                assertEquals(initial, view(it).snapshotDocument())
                assertFalse(view(it).undo())
            }
        } finally {
            finishAndCloseScenario(scenario)
            runBlocking {
                services.flush().await()
                try { assertEquals(initial, services.loadSketch(project.manifest.projectId).await()!!.sketch) }
                finally { services.delete(project.manifest.projectId).await() }
            }
        }
    }

    @Test fun placedDimensionLabelOpensInputAndDrivesItsReferencedWallAfterProjectOpen() {
        val services = ProjectServices.get(context)
        val dimension = WallDimension("placed", WallFaceReference("bottom", WallFaceSide.LEFT),
            WallFaceReference("top", WallFaceSide.RIGHT), Vec2(800.0, 0.0))
        val initial = room().copy(dimensions = mapOf(dimension.id to dimension))
        val project = runBlocking { services.createSketch("Placed dimension input fixture").await() }
        runBlocking { services.saveSketch(project.manifest.projectId, initial, null).await() }
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        var result: SketchDocument? = null
        try {
            compose.waitUntil(5000) {
                var loaded = false
                scenario.onActivity { loaded = view(it).isEnabled && view(it).snapshotDocument() == initial }
                loaded
            }
            var linePoint = Vec2(0.0, 0.0)
            scenario.onActivity { linePoint = view(it).worldToScreen(Vec2(800.0, 800.0)) }
            compose.onNodeWithTag("sketch-canvas").performTouchInput { click(Offset(linePoint.x.toFloat(), linePoint.y.toFloat())) }
            compose.onNode(isDialog()).assertDoesNotExist()
            compose.onNodeWithTag("sketch-property-sheet").assertDoesNotExist()
            var labelEdge = Vec2(0.0, 0.0)
            scenario.onActivity {
                val canvas = view(it)
                // Its transient horizontal dimension crosses this permanent vertical label.
                canvas.selectEntity(Selection.Wall("right"))
                val density = androidx.compose.ui.unit.Density(context.resources.displayMetrics.density, context.resources.configuration.fontScale)
                val style = com.example.boschlaser.platform.androidSketchDrawingStyle(context, density, 10f * density.density)
                val measurer = androidx.compose.ui.text.TextMeasurer(androidx.compose.ui.text.font.createFontFamilyResolver(context),
                    density, androidx.compose.ui.unit.LayoutDirection.Ltr)
                val origin = canvas.worldToScreen(Vec2(0.0, 0.0))
                val scale = (canvas.worldToScreen(Vec2(1000.0, 0.0)).x - origin.x) / 1000.0
                val bounds = com.example.boschlaser.ui.sketchDimensionLabelBounds(initial, dimension,
                    com.example.boschlaser.shared.editor.Viewport(scale, origin), measurer, style.label, density) {
                    formatSketchMillimeters(it)
                }!!
                labelEdge = Vec2(bounds.min.x + 2.0, (bounds.min.y + bounds.max.y) / 2.0)
                assertTrue("Tap must be outside the thin geometric line hit area", kotlin.math.abs(labelEdge.x - canvas.worldToScreen(dimension.position).x) > 20.0)
            }
            fun tapLabel() = compose.onNodeWithTag("sketch-canvas").performTouchInput {
                val point = Offset(labelEdge.x.toFloat(), labelEdge.y.toFloat())
                down(point)
                moveTo(point + Offset(3f, 0f))
                up()
            }
            fun input() = compose.onNode(hasContentDescription(context.getString(R.string.sketch_dimension_value)) and hasAnyAncestor(isDialog()))
            fun waitForInput() = compose.waitUntil(5000) {
                try { input().assertIsFocused(); true } catch (_: AssertionError) { false }
            }
            tapLabel()
            compose.onNode(isDialog()).assertDoesNotExist()
            scenario.onActivity { assertEquals(initial, view(it).snapshotDocument()); view(it).selectEntity(Selection.Wall("top")) }
            tapLabel()
            waitForInput()
            input().assertTextContains("3000").performTextInput("-1")
            compose.onNode(hasText(context.getString(R.string.sketch_dimension_apply)) and hasAnyAncestor(isDialog())).performClick()
            compose.onNode(hasText(context.getString(R.string.sketch_dimension_drive_failed)) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            compose.onNode(hasText(context.getString(R.string.sketch_settings_cancel)) and hasAnyAncestor(isDialog())).performClick()
            scenario.onActivity { assertEquals(initial, view(it).snapshotDocument()); assertFalse(view(it).undo()) }
            scenario.onActivity { view(it).selectEntity(Selection.Wall("top")) }
            tapLabel()
            waitForInput()
            input().performTextInput("3200.125")
            input().performImeAction()
            compose.waitForIdle()
            scenario.onActivity {
                val document = view(it).snapshotDocument()
                assertEquals(Selection.Wall("top"), view(it).editorState().selection)
                assertEquals(initial.dimensions, document.dimensions)
                assertEquals(3200.125, WallDimensionGeometry.layout(document, dimension)!!.valueMm, .01)
                assertEquals(initial.walls.getValue("bottom"), document.walls.getValue("bottom"))
                assertEquals(1800.125, document.walls.getValue("top").start.y, .01)
                assertEquals(document.walls.getValue("top").start, document.walls.getValue("left").end)
                assertEquals(document.walls.getValue("top").end, document.walls.getValue("right").end)
                assertEquals(initial.openings, document.openings)
                result = document
            }
        } finally {
            finishAndCloseScenario(scenario)
            try {
                runBlocking {
                    services.flush().await()
                    result?.let { assertEquals(it, services.loadSketch(project.manifest.projectId).await()!!.sketch) }
                }
            } finally { runBlocking { services.delete(project.manifest.projectId).await() } }
        }
    }
}
