package com.example.boschlaser

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.geometry.Offset
import com.example.boschlaser.shared.editor.Selection
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsOff
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchActivityProjectInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun <T> Deferred<T>.awaitResult(): T = runBlocking { withTimeout(5000) { await() } }
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun view(activity: SketchActivity) = SketchActivity::class.java.getDeclaredField("sketchView")
        .apply { isAccessible = true }.get(activity) as SketchView
    private fun awaitDocument(scenario: ActivityScenario<SketchActivity>, document: SketchDocument) {
        var loaded = false
        val deadline = SystemClock.uptimeMillis() + 5000
        while (!loaded && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { loaded = view(it).isEnabled && view(it).snapshotDocument() == document }
            if (!loaded) SystemClock.sleep(20)
        }
        assertTrue("Project must load its exact shared document", loaded)
    }
    private fun touch(view: SketchView, action: Int, point: Vec2) {
        val event = MotionEvent.obtain(0, 0, action, point.x.toFloat(), point.y.toFloat(), 0)
        try { view.onTouchEvent(event) } finally { event.recycle() }
    }
    @Test fun sharedCanvasForwardsRealTouchToTheSameEditorAndHonorsDisabledLoadingState() {
        val services = ProjectServices.get(context)
        val project = services.createSketch("Canvas touch fixture").awaitResult()
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        try {
            awaitDocument(scenario, SketchDocument())
            scenario.onActivity {
                view(it).setMode(SketchMode.RECT_COLUMN)
                view(it).setGridSnapEnabled(false)
                view(it).isEnabled = false
            }
            compose.onNodeWithTag("sketch-canvas").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .65f, height * .55f)) }
            scenario.onActivity { assertTrue(view(it).snapshotDocument().columns.isEmpty()); view(it).isEnabled = true }
            compose.onNodeWithTag("sketch-canvas").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .65f, height * .55f)) }
            scenario.onActivity {
                val view = view(it)
                assertEquals(1, view.snapshotDocument().columns.size)
                assertEquals(view.snapshotDocument().columns.keys.single(), view.selectedColumn()?.id)
                assertTrue(view.undo())
                assertTrue(view.snapshotDocument().columns.isEmpty())
            }
        } finally {
            finishAndCloseScenario(scenario)
            services.flush().awaitResult()
            services.delete(project.manifest.projectId).awaitResult()
        }
    }

    @Test fun committedEditSurvivesActivityDestructionWithoutFloatCoordinateRoundTrip() {
        val services = ProjectServices.get(context)
        val wall = Wall("test-wall", Vec2(-1000.123456789, 0.987654321), Vec2(1000.987654321, 0.987654321), 240.5, WallAlignment.RIGHT)
        val document = SketchDocument(walls = mapOf(wall.id to wall))
        val project = services.createSketch("Precision test fixture").awaitResult()
        services.saveSketch(project.manifest.projectId, document, null).awaitResult()
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java).putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        var scenarioClosed = false
        fun closeScenario() {
            if (!scenarioClosed) {
                finishAndCloseScenario(scenario)
                scenarioClosed = true
            }
        }
        try {
            awaitDocument(scenario, document)
            scenario.onActivity { activity ->
                val view = view(activity)
                val point = view.worldToScreen(Vec2(0.0, wall.start.y - wall.thickness / 2))
                touch(view, MotionEvent.ACTION_DOWN, point)
                touch(view, MotionEvent.ACTION_UP, point)
                assertEquals(wall.id, view.selectedWall()?.id)
                assertTrue(view.updateSelectedWallThickness(300f))
            }
            closeScenario()
            services.flush().awaitResult()
            val restored = services.loadSketch(project.manifest.projectId).awaitResult()!!.sketch.walls.getValue(wall.id)
            assertEquals(wall.start, restored.start)
            assertEquals(wall.end, restored.end)
            assertEquals(300.0, restored.thickness, 0.0)
            assertEquals(WallAlignment.RIGHT, restored.alignment)
        } finally { closeScenario(); services.flush().awaitResult(); services.delete(project.manifest.projectId).awaitResult() }
    }
    @Test fun stoppingDuringDragSavesConfirmedDocumentAndRollsBackPreview() {
        val services = ProjectServices.get(context)
        val column = RectColumn("test-column", Vec2(100.123456789, 100.987654321), 400.0, 400.0)
        val document = SketchDocument(columns = mapOf(column.id to column))
        val project = services.createSketch("Cancel test fixture").awaitResult()
        services.saveSketch(project.manifest.projectId, document, null).awaitResult()
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java).putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        var stoppedView: SketchView? = null
        var scenarioClosed = false
        fun closeScenario() {
            if (!scenarioClosed) {
                finishAndCloseScenario(scenario)
                scenarioClosed = true
            }
        }
        try {
            awaitDocument(scenario, document)
            scenario.onActivity { activity ->
                val view = view(activity)
                stoppedView = view
                view.setGridSnapEnabled(false)
                val point = view.worldToScreen(column.center)
                touch(view, MotionEvent.ACTION_DOWN, point)
                touch(view, MotionEvent.ACTION_MOVE, point + Vec2(60.0, 60.0))
                assertNotEquals(column.center.toSketchPoint(), view.snapshotState().columns.single().center)
            }
            closeScenario()
            services.flush().awaitResult()
            assertEquals(document, services.loadSketch(project.manifest.projectId).awaitResult()!!.sketch)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val view = requireNotNull(stoppedView)
                assertEquals(document, view.snapshotDocument())
                assertEquals(column.center.toSketchPoint(), view.snapshotState().columns.single().center)
                assertFalse(view.undo())
            }
        } finally { closeScenario(); services.flush().awaitResult(); services.delete(project.manifest.projectId).awaitResult() }
    }
    @Test fun sharedSnapSwitchDisablesGridInActualWallPlacement() {
        val services = ProjectServices.get(context)
        val project = services.createSketch("Snap fixture").awaitResult()
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java).putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        try {
            awaitDocument(scenario, SketchDocument())
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_grid_snap)).performClick().assertIsOff()
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_tool_wall)).performClick()
            val start = Vec2(123.0, 127.0)
            val end = Vec2(1123.0, 427.0)
            scenario.onActivity { activity ->
                val view = view(activity)
                for (point in listOf(start, end)) {
                    val screen = view.worldToScreen(point)
                    touch(view, MotionEvent.ACTION_DOWN, screen)
                    touch(view, MotionEvent.ACTION_UP, screen)
                }
                val wall = view.snapshotDocument().walls.values.single()
                assertEquals(start.x, wall.start.x, 1.0)
                assertEquals(start.y, wall.start.y, 1.0)
                assertEquals(end.x, wall.end.x, 1.0)
                assertEquals(end.y, wall.end.y, 1.0)
            }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_action_confirm)).performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_action_confirm)).assertDoesNotExist()
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_action_undo)).performClick()
            scenario.onActivity { assertTrue(view(it).snapshotDocument().walls.isEmpty()) }
        } finally {
            finishAndCloseScenario(scenario)
            services.flush().awaitResult()
            services.delete(project.manifest.projectId).awaitResult()
        }
    }

    @Test fun sharedDeleteActionDeletesSelectedColumnAndUndoRestoresIt() {
        val services = ProjectServices.get(context)
        val column = RectColumn("action-column", Vec2(0.0, 0.0), 400.0, 400.0)
        val document = SketchDocument(columns = mapOf(column.id to column))
        val project = services.createSketch("Action fixture").awaitResult()
        services.saveSketch(project.manifest.projectId, document, null).awaitResult()
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        try {
            awaitDocument(scenario, document)
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_action_delete)).assertDoesNotExist()
            scenario.onActivity { activity ->
                val view = view(activity)
                val point = view.worldToScreen(column.center)
                touch(view, MotionEvent.ACTION_DOWN, point)
                touch(view, MotionEvent.ACTION_UP, point)
            }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_action_delete)).performClick()
            scenario.onActivity { assertTrue(view(it).snapshotDocument().columns.isEmpty()) }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_action_delete)).assertDoesNotExist()
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_action_undo)).performClick()
            scenario.onActivity { assertEquals(document, view(it).snapshotDocument()) }
        } finally {
            finishAndCloseScenario(scenario)
            services.flush().awaitResult()
            services.delete(project.manifest.projectId).awaitResult()
        }
    }

    @Test fun sharedOpeningWidthAppliesExplicitlyAndPreservesSemanticPrecision() {
        val services = ProjectServices.get(context)
        val wall = Wall("width-wall", Vec2(-1000.123456789, .987654321), Vec2(2000.987654321, .987654321), 240.5)
        val opening = Opening("width-window", wall.id, 1300.123456789, 500.123456789, OpeningType.WINDOW,
            flipped = true, hingeFlipped = true)
        val document = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(opening.id to opening))
        val project = services.createSketch("Opening precision fixture").awaitResult()
        services.saveSketch(project.manifest.projectId, document, null).awaitResult()
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        var closed = false
        fun closeScenario() {
            if (!closed) { finishAndCloseScenario(scenario); closed = true }
        }
        try {
            awaitDocument(scenario, document)
            scenario.onActivity { activity ->
                val view = view(activity)
                val symbol = Vec2(wall.start.x + opening.offset, wall.start.y + wall.thickness / 6)
                val point = view.worldToScreen(symbol)
                touch(view, MotionEvent.ACTION_DOWN, point)
                touch(view, MotionEvent.ACTION_UP, point)
                assertEquals(opening.id, view.selectedOpening()?.id)
                assertEquals(document, view.snapshotDocument())
            }
            val label = context.getString(R.string.sketch_opening_width)
            val applyLabel = context.getString(R.string.sketch_property_apply) + " " + label
            compose.onNodeWithContentDescription(label).performTextReplacement("750.123456789")
            scenario.onActivity { assertEquals(document, view(it).snapshotDocument()) }
            compose.onNodeWithContentDescription(applyLabel).performClick()
            val expected = document.copy(openings = mapOf(opening.id to opening.copy(width = 750.123456789)))
            scenario.onActivity { activity ->
                val view = view(activity)
                assertEquals(expected, view.snapshotDocument())
                // A legacy flip caller still supplies a Float width; it must not round semantic values.
                assertTrue(view.updateSelectedOpening(view.selectedOpening()!!.width, flip = true))
                assertEquals(opening.offset, view.snapshotDocument().openings.getValue(opening.id).offset, 0.0)
                assertEquals(750.123456789, view.snapshotDocument().openings.getValue(opening.id).width, 0.0)
                assertTrue(view.undo())
                assertEquals(expected, view.snapshotDocument())
            }
            // Undo clears selection; select the actual symbol again before editing its properties.
            scenario.onActivity { activity ->
                val view = view(activity)
                val point = view.worldToScreen(Vec2(wall.start.x + opening.offset, wall.start.y + wall.thickness / 6))
                touch(view, MotionEvent.ACTION_DOWN, point)
                touch(view, MotionEvent.ACTION_UP, point)
                assertEquals(opening.id, view.selectedOpening()?.id)
            }
            for (invalid in listOf("", "99", "5000")) {
                compose.onNodeWithContentDescription(label).performTextReplacement(invalid)
                compose.onNodeWithContentDescription(applyLabel).performClick()
                scenario.onActivity { assertEquals(expected, view(it).snapshotDocument()) }
            }
            for ((type, tool) in listOf(SketchOpeningType.DOOR to R.string.sketch_tool_door,
                SketchOpeningType.WINDOW to R.string.sketch_tool_window)) {
                compose.onNodeWithContentDescription(context.getString(tool)).performClick()
                var originalWidth = 0f
                scenario.onActivity { originalWidth = view(it).placementOpeningWidth(type) }
                compose.onNodeWithContentDescription(label).performTextReplacement("1250.25")
                scenario.onActivity { assertEquals(originalWidth, view(it).placementOpeningWidth(type), 0f) }
                compose.onNodeWithContentDescription(applyLabel).performClick()
                scenario.onActivity { assertEquals(1250.25f, view(it).placementOpeningWidth(type), 0f) }
                compose.onNodeWithContentDescription(label).performTextReplacement("5001")
                compose.onNodeWithContentDescription(applyLabel).performClick()
                scenario.onActivity {
                    assertEquals(1250.25f, view(it).placementOpeningWidth(type), 0f)
                    assertEquals(expected, view(it).snapshotDocument())
                }
            }
            closeScenario()
            services.flush().awaitResult()
            assertEquals(expected, services.loadSketch(project.manifest.projectId).awaitResult()!!.sketch)
        } finally { closeScenario(); services.flush().awaitResult(); services.delete(project.manifest.projectId).awaitResult() }
    }

    @Test fun sharedColumnPropertiesKeepDoubleGeometryAndRejectPartialSizeEdits() {
        val services = ProjectServices.get(context)
        val rectangle = RectColumn("property-rect", Vec2(-500.123456789, .987654321), 300.123456789, 400.987654321, 15.123456789)
        val circle = CircularColumn("property-circle", Vec2(800.123456789, .987654321), 500.987654321)
        val document = SketchDocument(columns = mapOf(rectangle.id to rectangle, circle.id to circle))
        val project = services.createSketch("Column properties fixture").awaitResult()
        services.saveSketch(project.manifest.projectId, document, null).awaitResult()
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        var closed = false
        fun closeScenario() {
            if (!closed) { finishAndCloseScenario(scenario); closed = true }
        }
        fun select(column: Column) {
            scenario.onActivity { activity ->
                val view = view(activity)
                val point = view.worldToScreen(column.center)
                touch(view, MotionEvent.ACTION_DOWN, point)
                touch(view, MotionEvent.ACTION_UP, point)
                assertEquals(column.id, view.selectedColumn()?.id)
            }
        }
        try {
            awaitDocument(scenario, document)
            select(rectangle)
            val widthLabel = context.getString(R.string.sketch_column_width)
            val depthLabel = context.getString(R.string.sketch_column_depth)
            val applySize = context.getString(R.string.sketch_property_apply) + " " + widthLabel
            compose.onNodeWithContentDescription(widthLabel).performTextReplacement("550.123456789")
            compose.onNodeWithContentDescription(depthLabel).performTextReplacement("")
            scenario.onActivity { assertEquals(document, view(it).snapshotDocument()) }
            compose.onNodeWithContentDescription(applySize).performClick()
            scenario.onActivity { assertEquals(document, view(it).snapshotDocument()) }
            compose.onNodeWithContentDescription(depthLabel).performTextReplacement("650.987654321")
            compose.onNodeWithContentDescription(applySize).performClick()
            val resized = rectangle.copy(width = 550.123456789, depth = 650.987654321)
            val sizedDocument = document.copy(columns = document.columns + (rectangle.id to resized))
            scenario.onActivity { assertEquals(sizedDocument, view(it).snapshotDocument()) }
            val angleLabel = context.getString(R.string.column_rotation_angle)
            val applyAngle = context.getString(R.string.column_rotation_apply) + " " + angleLabel
            compose.onNodeWithContentDescription(angleLabel).performTextReplacement("-30.125")
            compose.onNodeWithContentDescription(applyAngle).performClick()
            val rotatedDocument = sizedDocument.copy(columns = sizedDocument.columns +
                (rectangle.id to resized.copy(rotationDegrees = 329.875)))
            scenario.onActivity { assertEquals(rotatedDocument, view(it).snapshotDocument()) }
            compose.onNodeWithContentDescription(angleLabel).performTextReplacement("")
            compose.onNodeWithContentDescription(applyAngle).performClick()
            scenario.onActivity {
                assertEquals(rotatedDocument, view(it).snapshotDocument())
                assertTrue(view(it).undo())
                assertEquals(sizedDocument, view(it).snapshotDocument())
            }
            select(circle)
            compose.onNodeWithContentDescription(depthLabel).assertDoesNotExist()
            compose.onNodeWithContentDescription(angleLabel).assertDoesNotExist()
            compose.onNodeWithContentDescription(widthLabel).performTextReplacement("700.123456789")
            compose.onNodeWithContentDescription(applySize).performClick()
            val expected = sizedDocument.copy(columns = sizedDocument.columns +
                (circle.id to circle.copy(diameter = 700.123456789)))
            scenario.onActivity { assertEquals(expected, view(it).snapshotDocument()) }
            closeScenario()
            services.flush().awaitResult()
            assertEquals(expected, services.loadSketch(project.manifest.projectId).awaitResult()!!.sketch)
        } finally { closeScenario(); services.flush().awaitResult(); services.delete(project.manifest.projectId).awaitResult() }
    }

    @Test fun propertySheetDismissalPreservesDoubleDocumentAndSelectionAndResetsUnappliedDraftOnReselection() {
        val services = ProjectServices.get(context)
        val wall = Wall("sheet-wall", Vec2(.123456789, -.987654321), Vec2(3000.123456789, -.987654321), 240.987654321)
        val document = SketchDocument(walls = mapOf(wall.id to wall))
        val project = services.createSketch("Property sheet fixture").awaitResult()
        services.saveSketch(project.manifest.projectId, document, null).awaitResult()
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        var closed = false
        fun closeScenario() {
            if (!closed) { finishAndCloseScenario(scenario); closed = true }
        }
        try {
            awaitDocument(scenario, document)
            scenario.onActivity { view(it).selectEntity(Selection.Wall(wall.id)) }
            val label = context.getString(R.string.sketch_wall_thickness)
            compose.onNodeWithContentDescription(label).performTextReplacement("432.123456789")
            scenario.onActivity { assertEquals(document, view(it).snapshotDocument()) }
            val density = context.resources.displayMetrics.density
            compose.onNodeWithTag("sketch-property-sheet").performTouchInput {
                down(Offset(width / 2f, 8f * density))
                moveBy(Offset(0f, 120f * density))
                up()
            }
            compose.onNodeWithTag("sketch-property-sheet").assertDoesNotExist()
            scenario.onActivity {
                assertEquals(wall.id, view(it).selectedWall()?.id)
                assertEquals(document, view(it).snapshotDocument())
                assertFalse(view(it).undoPlacement())
                view(it).selectEntity(Selection.Wall(wall.id))
            }
            compose.onNodeWithTag("sketch-property-sheet").assertExists()
            compose.onNodeWithContentDescription(label).assertTextContains("241")
            scenario.onActivity { assertEquals(document, view(it).snapshotDocument()) }
            closeScenario()
            services.flush().awaitResult()
            assertEquals(document, services.loadSketch(project.manifest.projectId).awaitResult()!!.sketch)
        } finally { closeScenario(); services.flush().awaitResult(); services.delete(project.manifest.projectId).awaitResult() }
    }

    @Test fun sharedWallPropertiesPreserveFixedEndsAndRejectHostedOpeningConflicts() {
        val services = ProjectServices.get(context)
        val wall = Wall("property-wall", Vec2(.123456789, -.987654321), Vec2(3000.123456789, -.987654321), 240.987654321)
        val door = Opening("property-door", wall.id, 2500.123456789, 500.987654321, OpeningType.DOOR)
        val forward = WallDimension("forward", WallFaceReference(wall.id, WallFaceSide.START),
            WallFaceReference(wall.id, WallFaceSide.END), Vec2(1500.0, 600.0))
        val backward = WallDimension("backward", forward.second, forward.first, Vec2(1500.0, -600.0))
        val document = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(door.id to door),
            dimensions = mapOf(forward.id to forward, backward.id to backward))
        val project = services.createSketch("Wall properties fixture").awaitResult()
        services.saveSketch(project.manifest.projectId, document, null).awaitResult()
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        var closed = false
        fun closeScenario() {
            if (!closed) { finishAndCloseScenario(scenario); closed = true }
        }
        try {
            awaitDocument(scenario, document)
            scenario.onActivity { activity ->
                val view = view(activity)
                val point = view.worldToScreen(Vec2(1000.0, wall.start.y))
                touch(view, MotionEvent.ACTION_DOWN, point)
                touch(view, MotionEvent.ACTION_UP, point)
                assertEquals(wall.id, view.selectedWall()?.id)
            }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_wall_length)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.sketch_wall_move_end)).assertDoesNotExist()
            fun openDimension(dimension: WallDimension) {
                scenario.onActivity {
                    val canvas = view(it)
                    val layout = com.example.boschlaser.shared.geometry.WallDimensionGeometry.layout(canvas.snapshotDocument(), dimension)!!
                    val point = canvas.worldToScreen((layout.line.start + layout.line.end) * .5)
                    touch(canvas, MotionEvent.ACTION_DOWN, point)
                    touch(canvas, MotionEvent.ACTION_UP, point)
                }
            }
            openDimension(forward)
            val lengthLabel = context.getString(R.string.sketch_dimension_value)
            val applyLength = context.getString(R.string.sketch_dimension_apply)
            compose.onNodeWithContentDescription(lengthLabel).performTextReplacement("1200")
            scenario.onActivity { assertEquals(document, view(it).snapshotDocument()) }
            compose.onNodeWithText(applyLength).performClick()
            scenario.onActivity {
                assertEquals(document, view(it).snapshotDocument())
            }
            compose.onNodeWithContentDescription(lengthLabel).assertTextContains("1200")
            val length = 3500.123456789
            compose.onNodeWithContentDescription(lengthLabel).performTextReplacement(length.toString())
            compose.onNodeWithText(applyLength).performClick()
            var expected = document.copy(walls = mapOf(wall.id to wall.copy(
                end = Vec2(wall.start.x + length, wall.end.y), measuredLength = length)))
            scenario.onActivity { assertEquals(expected, view(it).snapshotDocument()) }
            val thicknessLabel = context.getString(R.string.sketch_wall_thickness)
            val applyThickness = context.getString(R.string.sketch_wall_thickness_apply) + " " + thicknessLabel
            compose.onNodeWithContentDescription(thicknessLabel).performTextReplacement("49")
            compose.onNodeWithContentDescription(applyThickness).performClick()
            scenario.onActivity { assertEquals(expected, view(it).snapshotDocument()) }
            compose.onNodeWithContentDescription(thicknessLabel).performTextReplacement("300.123456789")
            compose.onNodeWithContentDescription(applyThickness).performClick()
            expected = expected.copy(walls = mapOf(wall.id to expected.walls.getValue(wall.id).copy(thickness = 300.123456789)))
            val lineLabel = context.getString(R.string.wall_control_line_label) + " " + context.getString(R.string.wall_control_line_inner)
            compose.onNodeWithContentDescription(lineLabel).performClick().assertIsSelected()
            expected = expected.copy(walls = mapOf(wall.id to expected.walls.getValue(wall.id).copy(alignment = WallAlignment.LEFT)))
            scenario.onActivity { assertEquals(expected, view(it).snapshotDocument()) }
            compose.onNodeWithText(context.getString(R.string.sketch_wall_move_start)).assertDoesNotExist()
            scenario.onActivity { assertEquals(expected, view(it).snapshotDocument()) }
            openDimension(backward)
            compose.onNodeWithContentDescription(lengthLabel).performTextReplacement("4000.987654321")
            compose.onNodeWithText(applyLength).performClick()
            scenario.onActivity { activity ->
                val result = view(activity).snapshotDocument()
                val updated = result.walls.getValue(wall.id)
                assertEquals(expected.walls.getValue(wall.id).end, updated.end)
                assertEquals(4000.987654321, updated.measuredLength!!, 0.0)
                assertEquals(4000.987654321, com.example.boschlaser.shared.geometry.Geometry.distance(updated.start, updated.end), 1e-7)
                val oldFixedDistance = length - door.offset
                val newFixedDistance = updated.measuredLength!! - result.openings.getValue(door.id).offset
                assertEquals(oldFixedDistance, newFixedDistance, 1e-7)
                assertEquals(door.width, result.openings.getValue(door.id).width, 0.0)
                expected = result
            }
            closeScenario()
            services.flush().awaitResult()
            assertEquals(expected, services.loadSketch(project.manifest.projectId).awaitResult()!!.sketch)
        } finally { closeScenario(); services.flush().awaitResult(); services.delete(project.manifest.projectId).awaitResult() }
    }

    @Test fun sharedDrawingSettingsApplyAtomicallyAndSurviveRecreation() {
        val services = ProjectServices.get(context)
        val project = services.createSketch("Settings fixture").awaitResult()
        val preferences = context.getSharedPreferences("sketch_settings", android.content.Context.MODE_PRIVATE)
        val gridKey = "grid_display_step_mm"
        val magnifierKey = "magnifier_zoom"
        val hadGrid = preferences.contains(gridKey)
        val hadMagnifier = preferences.contains(magnifierKey)
        val oldGrid = preferences.getFloat(gridKey, SketchView.DEFAULT_GRID_DISPLAY_STEP_MM)
        val oldMagnifier = preferences.getFloat(magnifierKey, SketchView.DEFAULT_MAGNIFIER_ZOOM)
        check(preferences.edit().putFloat(gridKey, 101.25f).putFloat(magnifierKey, 2.75f).commit())
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        val gridLabel = context.getString(R.string.sketch_settings_grid)
        val magnifierLabel = context.getString(R.string.sketch_settings_magnifier,
            SketchView.MIN_MAGNIFIER_ZOOM.toInt(), SketchView.MAX_MAGNIFIER_ZOOM.toInt())
        fun open() { compose.onNodeWithContentDescription(context.getString(R.string.sketch_settings)).performScrollTo().performClick() }
        fun assertSettings(grid: Float, magnifier: Float) {
            scenario.onActivity {
                assertEquals(grid, view(it).gridDisplayStepMm(), 0f)
                assertEquals(magnifier, view(it).magnifierZoom(), 0f)
                assertEquals(SketchDocument(), view(it).snapshotDocument())
            }
        }
        try {
            awaitDocument(scenario, SketchDocument())
            open()
            compose.onNodeWithContentDescription(gridLabel).performTextReplacement("250")
            compose.onNodeWithText(context.getString(R.string.sketch_settings_cancel)).performClick()
            assertSettings(101.25f, 2.75f)
            for ((grid, magnifier) in listOf("250" to "99", "9" to "3.25")) {
                open()
                compose.onNodeWithContentDescription(gridLabel).performTextReplacement(grid)
                compose.onNodeWithContentDescription(magnifierLabel).performTextReplacement(magnifier)
                compose.onNodeWithText(context.getString(R.string.sketch_property_apply)).performClick()
                compose.onNodeWithText(context.getString(R.string.sketch_settings_title)).assertDoesNotExist()
                assertSettings(101.25f, 2.75f)
                assertEquals(101.25f, preferences.getFloat(gridKey, 0f), 0f)
                assertEquals(2.75f, preferences.getFloat(magnifierKey, 0f), 0f)
            }
            open()
            compose.onNodeWithContentDescription(gridLabel).performTextReplacement("250.25")
            compose.onNodeWithContentDescription(magnifierLabel).performTextReplacement("3.25")
            compose.onNodeWithText(context.getString(R.string.sketch_property_apply)).performClick()
            assertSettings(250.25f, 3.25f)
            scenario.recreate()
            awaitDocument(scenario, SketchDocument())
            assertSettings(250.25f, 3.25f)
            open()
            compose.onNodeWithContentDescription(gridLabel).assertTextContains("250.25")
            compose.onNodeWithContentDescription(magnifierLabel).assertTextContains("3.25")
            compose.onNodeWithText(context.getString(R.string.sketch_settings_cancel)).performClick()
        } finally {
            try {
                finishAndCloseScenario(scenario)
                services.flush().awaitResult()
                services.delete(project.manifest.projectId).awaitResult()
            } finally {
                val restore = preferences.edit()
                if (hadGrid) restore.putFloat(gridKey, oldGrid) else restore.remove(gridKey)
                if (hadMagnifier) restore.putFloat(magnifierKey, oldMagnifier) else restore.remove(magnifierKey)
                check(restore.commit())
            }
        }
    }

    @Test fun sharedWallPairToolUsesCanvasPicksAndCommitsOneUndoStep() {
        val horizontal = Wall("pair-horizontal", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
        val vertical = Wall("pair-vertical", Vec2(1000.0, -1000.0), Vec2(1000.0, 1000.0))
        val original = SketchDocument(walls = listOf(horizontal, vertical).associateBy { it.id })
        val services = ProjectServices.get(context)
        val project = services.createSketch("Wall pair fixture").awaitResult()
        services.saveSketch(project.manifest.projectId, original, null).awaitResult()
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        try {
            awaitDocument(scenario, original)
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_join)).performClick()
            scenario.onActivity {
                val canvas = view(it)
                assertEquals(com.example.boschlaser.shared.editor.WallEditOperation.TRIM,
                    canvas.editorState().wallEditSession?.operation)
                canvas.sendWorldPointer(MotionEvent.ACTION_DOWN, SketchPoint(100f, 0f))
                canvas.sendWorldPointer(MotionEvent.ACTION_UP, SketchPoint(100f, 0f))
                assertEquals(horizontal.id, canvas.editorState().wallEditSession?.first?.wallId)
                assertEquals(original, canvas.snapshotDocument())
            }
            compose.onNodeWithTag("sketch-property-sheet").assertDoesNotExist()
            scenario.onActivity {
                val canvas = view(it)
                canvas.sendWorldPointer(MotionEvent.ACTION_DOWN, SketchPoint(1000f, 500f))
                canvas.sendWorldPointer(MotionEvent.ACTION_UP, SketchPoint(1000f, 500f))
                assertNull(canvas.editorState().wallEditSession)
                assertEquals(Selection.Wall(vertical.id), canvas.editorState().selection)
                assertEquals(Vec2(1000.0, 0.0), canvas.snapshotDocument().walls.getValue(horizontal.id).end)
                assertEquals(Vec2(1000.0, 0.0), canvas.snapshotDocument().walls.getValue(vertical.id).start)
            }
            compose.onNodeWithTag("sketch-property-sheet").assertExists()
            services.flush().awaitResult()
            val saved = services.loadSketch(project.manifest.projectId).awaitResult()!!.sketch!!
            assertEquals(Vec2(1000.0, 0.0), saved.walls.getValue(horizontal.id).end)
            scenario.onActivity {
                assertTrue(view(it).undo())
                assertEquals(original, view(it).snapshotDocument())
                assertFalse(view(it).undo())
            }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_extend)).performClick()
            scenario.onActivity {
                view(it).sendWorldPointer(MotionEvent.ACTION_UP, SketchPoint(100f, 0f))
                assertNotNull(view(it).editorState().wallEditSession?.first)
            }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_extend_cancel)).performClick()
            scenario.onActivity {
                assertNull(view(it).editorState().wallEditSession)
                assertEquals(original, view(it).snapshotDocument())
                assertFalse(view(it).undo())
            }
        } finally {
            finishAndCloseScenario(scenario)
            services.flush().awaitResult()
            services.delete(project.manifest.projectId).awaitResult()
        }
    }

    @Test fun wallPropertyDraftReadsCoreDoubleBeforeWholeMillimeterFormatting() {
        val thickness = 300.499999999
        val wall = Wall("precise-display", Vec2(0.0, 0.0), Vec2(3000.0, 0.0), thickness)
        val document = SketchDocument(walls = mapOf(wall.id to wall))
        val services = ProjectServices.get(context)
        val project = services.createSketch("Core property display fixture").awaitResult()
        services.saveSketch(project.manifest.projectId, document, null).awaitResult()
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        try {
            awaitDocument(scenario, document)
            scenario.onActivity { view(it).selectEntity(Selection.Wall(wall.id)) }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_wall_length)).assertDoesNotExist()
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_wall_thickness))
                .assertTextContains("300")
            scenario.onActivity {
                assertEquals(document, view(it).snapshotDocument())
                assertFalse(view(it).undo())
            }
        } finally {
            finishAndCloseScenario(scenario)
            services.flush().awaitResult()
            services.delete(project.manifest.projectId).awaitResult()
        }
    }

    @Test fun measureAndSplitToolbarEntriesUseCoreStateAndPersistOnlyCommittedSplit() {
        val wall = Wall("split-fixture", Vec2(-2000.0, 0.0), Vec2(2000.0, 0.0))
        val original = SketchDocument(walls = mapOf(wall.id to wall))
        val services = ProjectServices.get(context)
        val project = services.createSketch("Measure split fixture").awaitResult()
        services.saveSketch(project.manifest.projectId, original, null).awaitResult()
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        try {
            awaitDocument(scenario, original)
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_tool_measure)).performScrollTo().performClick()
            scenario.onActivity {
                val canvas = view(it)
                canvas.setGridSnapEnabled(false)
                assertEquals(com.example.boschlaser.shared.editor.ToolType.MEASURE, canvas.editorState().tool)
                for (point in listOf(SketchPoint(-1000f, 1000f), SketchPoint(1000f, 1000f))) {
                    canvas.sendWorldPointer(MotionEvent.ACTION_DOWN, point)
                    canvas.sendWorldPointer(MotionEvent.ACTION_UP, point)
                }
                val measurement = canvas.editorState().preview as com.example.boschlaser.shared.editor.SketchPreview.Measurement
                assertTrue(measurement.complete)
                assertEquals(2000.0, com.example.boschlaser.shared.geometry.Geometry.length(measurement.line), 0.1)
                assertEquals(original, canvas.snapshotDocument())
            }
            services.flush().awaitResult()
            assertEquals(original, services.loadSketch(project.manifest.projectId).awaitResult()!!.sketch)
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_action_undo)).performClick()
            scenario.onActivity { assertNull(view(it).editorState().preview); assertEquals(original, view(it).snapshotDocument()) }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_tool_split)).performScrollTo().performClick()
            scenario.onActivity {
                val canvas = view(it)
                assertEquals(com.example.boschlaser.shared.editor.ToolType.SPLIT, canvas.editorState().tool)
                canvas.sendWorldPointer(MotionEvent.ACTION_DOWN, SketchPoint(0f, 0f))
                canvas.sendWorldPointer(MotionEvent.ACTION_UP, SketchPoint(0f, 0f))
                val split = canvas.snapshotDocument()
                assertEquals(2, split.walls.size)
                assertEquals(0.0, split.walls.getValue(wall.id).end.x, 0.1)
                assertEquals(Selection.Wall(split.walls.keys.last()), canvas.editorState().selection)
            }
            services.flush().awaitResult()
            assertEquals(2, services.loadSketch(project.manifest.projectId).awaitResult()!!.sketch!!.walls.size)
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_action_undo)).performClick()
            scenario.onActivity { assertEquals(original, view(it).snapshotDocument()); assertFalse(view(it).undo()) }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_action_confirm)).performClick()
            scenario.onActivity { assertEquals(com.example.boschlaser.shared.editor.ToolType.SELECT, view(it).editorState().tool) }
        } finally {
            finishAndCloseScenario(scenario)
            services.flush().awaitResult()
            services.delete(project.manifest.projectId).awaitResult()
        }
    }

}
