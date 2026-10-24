package com.example.boschlaser

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.shared.editor.SketchPreview
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.geometry.ColumnGeometry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchMeasurePlacementInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun realCanvasMeasurementSnapsWallDoorAndColumnEdgesAndKeepsTheDocumentUnchanged() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val services = ProjectServices.get(context)
        val wall = Wall("wall", Vec2(-2500.0, -1400.0), Vec2(2500.0, -1400.0), 200.0)
        val door = Opening("door", wall.id, 1400.0, 1000.0, OpeningType.DOOR)
        val rectangle = RectColumn("rectangle", Vec2(-600.0, 600.0), 800.0, 600.0, rotationDegrees = 30.0)
        val circle = CircularColumn("circle", Vec2(1600.0, 600.0), 800.0)
        val initial = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(door.id to door),
            columns = mapOf(rectangle.id to rectangle, circle.id to circle))
        val project = runBlocking { services.createSketch("Measure edge fixture").await() }
        runBlocking { services.saveSketch(project.manifest.projectId, initial, null).await() }
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        fun view(activity: SketchActivity) = SketchActivity::class.java.getDeclaredField("sketchView")
            .apply { isAccessible = true }.get(activity) as SketchView
        try {
            compose.waitUntil(5000) {
                var loaded = false
                scenario.onActivity { loaded = view(it).isEnabled && view(it).snapshotDocument() == initial }
                loaded
            }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_tool_measure)).performScrollTo().performClick()
            scenario.onActivity { view(it).setGridSnapEnabled(false) }
            fun pick(expected: Vec2, id: String) {
                var screen = expected
                scenario.onActivity { screen = view(it).worldToScreen(expected + Vec2(3.0, 4.0)) }
                compose.onNodeWithTag("sketch-canvas").performTouchInput { down(Offset(screen.x.toFloat(), screen.y.toFloat())) }
                scenario.onActivity {
                    val state = view(it).editorState()
                    assertEquals(expected.x, state.snap!!.point.x, .02)
                    assertEquals(expected.y, state.snap!!.point.y, .02)
                    assertEquals(id, state.snap!!.targetId)
                    assertEquals(state.snap!!.point, state.magnifierTarget)
                    assertEquals(initial, view(it).snapshotDocument())
                }
                compose.onNodeWithTag("sketch-canvas").performTouchInput { up() }
                scenario.onActivity { assertNull(view(it).editorState().magnifierTarget) }
            }
            val targets = listOf(Vec2(-2500.0, -1300.0) to wall.id, Vec2(-1600.0, -1300.0) to door.id,
                ColumnGeometry.corners(rectangle)[0] to rectangle.id, Vec2(2000.0, 600.0) to circle.id)
            for (pair in targets.chunked(2)) {
                pick(pair[0].first, pair[0].second)
                scenario.onActivity { assertFalse((view(it).editorState().preview as SketchPreview.Measurement).complete) }
                pick(pair[1].first, pair[1].second)
                scenario.onActivity {
                    val result = view(it).editorState().preview as SketchPreview.Measurement
                    assertTrue(result.complete)
                    assertEquals(pair[0].first.x, result.line.start.x, .02)
                    assertEquals(pair[0].first.y, result.line.start.y, .02)
                    assertEquals(pair[1].first.x, result.line.end.x, .02)
                    assertEquals(pair[1].first.y, result.line.end.y, .02)
                    assertEquals(initial, view(it).snapshotDocument())
                }
            }
            runBlocking { services.flush().await() }
            assertEquals(initial, runBlocking { services.loadSketch(project.manifest.projectId).await() }!!.sketch)
        } finally {
            finishAndCloseScenario(scenario)
            runBlocking { services.flush().await(); services.delete(project.manifest.projectId).await() }
        }
    }

    @Test fun bothPointsShowMagnifierWhileMovingAndConfirmAtReleaseWithoutSaving() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val services = ProjectServices.get(context)
        val project = runBlocking { services.createSketch("Measure placement fixture").await() }
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        fun view(activity: SketchActivity) = SketchActivity::class.java.getDeclaredField("sketchView")
            .apply { isAccessible = true }.get(activity) as SketchView
        try {
            compose.waitUntil(5000) {
                var loaded = false
                scenario.onActivity { loaded = view(it).isEnabled }
                loaded
            }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_tool_measure)).performScrollTo().performClick()
            scenario.onActivity { view(it).setGridSnapEnabled(false) }
            fun screen(point: Vec2): Offset {
                var value = point
                scenario.onActivity { value = view(it).worldToScreen(point) }
                return Offset(value.x.toFloat(), value.y.toFloat())
            }
            fun assertTarget(point: Vec2) = scenario.onActivity {
                val target = view(it).editorState().magnifierTarget!!
                assertEquals(point.x, target.x, .02)
                assertEquals(point.y, target.y, .02)
                assertEquals(SketchDocument(), view(it).snapshotDocument())
            }
            val start = Vec2(-600.0, 500.0)
            val end = Vec2(1000.0, 700.0)
            val down = screen(Vec2(-1000.0, 200.0))
            compose.onNodeWithTag("sketch-canvas").performTouchInput { down(down) }
            assertTarget(Vec2(-1000.0, 200.0))
            val startScreen = screen(start)
            compose.onNodeWithTag("sketch-canvas").performTouchInput { moveTo(startScreen) }
            assertTarget(start)
            scenario.onActivity { assertNull("First point must stay unconfirmed", view(it).editorState().preview) }
            compose.onNodeWithTag("sketch-canvas").performTouchInput { up() }
            scenario.onActivity {
                val state = view(it).editorState()
                val preview = state.preview as SketchPreview.Measurement
                assertFalse(preview.complete)
                assertEquals(start.x, preview.line.start.x, .02)
                assertEquals(start.y, preview.line.start.y, .02)
                assertNull(state.magnifierTarget)
            }
            val secondDown = screen(Vec2(500.0, 300.0))
            compose.onNodeWithTag("sketch-canvas").performTouchInput { down(secondDown) }
            assertTarget(Vec2(500.0, 300.0))
            val endScreen = screen(end)
            compose.onNodeWithTag("sketch-canvas").performTouchInput { moveTo(endScreen) }
            assertTarget(end)
            scenario.onActivity { assertFalse((view(it).editorState().preview as SketchPreview.Measurement).complete) }
            compose.onNodeWithTag("sketch-canvas").performTouchInput { up() }
            scenario.onActivity {
                val canvas = view(it)
                val state = canvas.editorState()
                val result = state.preview as SketchPreview.Measurement
                assertTrue(result.complete)
                assertEquals(start.x, result.line.start.x, .02)
                assertEquals(start.y, result.line.start.y, .02)
                assertEquals(end.x, result.line.end.x, .02)
                assertEquals(end.y, result.line.end.y, .02)
                assertNull(state.magnifierTarget)
                assertNull(state.snap)
                assertEquals(SketchDocument(), canvas.snapshotDocument())
            }
            runBlocking { services.flush().await() }
            assertEquals(SketchDocument(), runBlocking { services.loadSketch(project.manifest.projectId).await() }!!.sketch)
        } finally {
            finishAndCloseScenario(scenario)
            runBlocking { services.flush().await(); services.delete(project.manifest.projectId).await() }
        }
    }
}
