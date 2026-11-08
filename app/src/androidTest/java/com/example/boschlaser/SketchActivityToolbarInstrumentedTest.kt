package com.example.boschlaser

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchActivityToolbarInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun assertCompactReferenceSwitch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = context.getString(R.string.wall_control_line_label)
        val options = listOf(R.string.wall_control_line_inner, R.string.wall_control_line_center, R.string.wall_control_line_outer)
        val density = context.resources.displayMetrics.density
        val bounds = options.map { option ->
            val text = context.getString(option)
            val button = compose.onNodeWithContentDescription("$prefix $text").fetchSemanticsNode().boundsInRoot
            val caption = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(button.center.x, caption.center.x, 1f)
            assertEquals(button.center.y, caption.center.y, 1f)
            assertTrue(button.height <= 31f * density)
            button
        }
        assertTrue(bounds[0].right <= bounds[1].left)
        assertTrue(bounds[1].right <= bounds[2].left)
        assertTrue(bounds.last().right - bounds.first().left <= 180f * density)
    }

    private fun assertCompactWallFillSwitch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val density = context.resources.displayMetrics.density
        val choices = listOf(R.string.wall_fill_blank, R.string.wall_fill_hatch, R.string.wall_fill_black)
        val bounds = choices.map {
            compose.onNodeWithContentDescription(context.getString(it)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        bounds.forEach {
            assertEquals(40f * density, it.width, 1f)
            assertEquals(30f * density, it.height, 1f)
        }
        assertEquals(bounds[0].right, bounds[1].left, 1f)
        assertEquals(bounds[1].right, bounds[2].left, 1f)
        // Finish the click ripple before checking the actual fill pixels.
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("wall-fill-switch").captureToImage().toPixelMap()
        // PixelCopy on device can round black by a few RGB levels during color conversion.
        for ((x, expected) in listOf(pixels.width / 6 to 1f, pixels.width * 5 / 6 to 0f)) {
            val pixel = pixels[x, pixels.height / 2]
            assertEquals(expected, pixel.red, .02f)
            assertEquals(expected, pixel.green, .02f)
            assertEquals(expected, pixel.blue, .02f)
            assertEquals(1f, pixel.alpha, .001f)
        }
        val hatch = (pixels.width / 3 + 4 until pixels.width * 2 / 3 - 4).map { pixels[it, pixels.height / 2] }
        assertTrue(hatch.any { it.red < .2f } && hatch.any { it.red > .8f })
    }

    private fun assertCompactColumnFillSwitch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val density = context.resources.displayMetrics.density
        val white = compose.onNodeWithContentDescription(context.getString(R.string.column_fill_white)).fetchSemanticsNode().boundsInRoot
        val black = compose.onNodeWithContentDescription(context.getString(R.string.column_fill_black)).fetchSemanticsNode().boundsInRoot
        assertEquals(40f * density, white.width, 1f)
        assertEquals(30f * density, white.height, 1f)
        assertEquals(white.right, black.left, 1f)
        assertEquals(white.width, black.width, 1f)
        assertEquals(white.height, black.height, 1f)
    }

    @Test fun wallCreationFillChoicesUpdatePlacementOnly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val services = ProjectServices.get(context)
        val project = runBlocking { services.createSketch("Wall fill fixture").await() }
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        try {
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_tool_wall)).performClick()
            for ((label, fill) in listOf(
                R.string.wall_fill_blank to com.example.boschlaser.shared.model.WallFill.BLANK,
                R.string.wall_fill_hatch to com.example.boschlaser.shared.model.WallFill.HATCH,
                R.string.wall_fill_black to com.example.boschlaser.shared.model.WallFill.BLACK)) {
                compose.onNodeWithContentDescription(context.getString(label)).performScrollTo().performClick().assertIsSelected()
                scenario.onActivity { activity ->
                    val field = SketchActivity::class.java.getDeclaredField("sketchView").apply { isAccessible = true }
                    val view = field.get(activity) as SketchView
                    assertEquals(fill, view.drawingWallFill)
                }
            }
            assertCompactWallFillSwitch()
            compose.onNodeWithTag("sketch-canvas").performTouchInput {
                click(androidx.compose.ui.geometry.Offset(width * .2f, height * .5f))
                click(androidx.compose.ui.geometry.Offset(width * .8f, height * .5f))
            }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_tool_select)).performClick()
            compose.onNodeWithTag("sketch-canvas").performTouchInput {
                click(androidx.compose.ui.geometry.Offset(width * .5f, height * .5f))
            }
            assertCompactReferenceSwitch()
            assertCompactWallFillSwitch()
            compose.onNodeWithText(context.getString(R.string.wall_control_line_label)).assertDoesNotExist()
            val title = compose.onNodeWithText(context.getString(R.string.sketch_wall_properties)).fetchSemanticsNode().boundsInRoot
            val reference = compose.onNodeWithContentDescription(context.getString(R.string.wall_control_line_label) + " " +
                context.getString(R.string.wall_control_line_inner)).fetchSemanticsNode().boundsInRoot
            assertTrue(title.right <= reference.left)
            assertEquals(title.center.y, reference.center.y, 1f)
            val thickness = compose.onNodeWithText(context.getString(R.string.sketch_drawing_wall_thickness, "240")).fetchSemanticsNode().boundsInRoot
            val fill = compose.onNodeWithTag("wall-fill-switch").fetchSemanticsNode().boundsInRoot
            assertTrue(thickness.right <= fill.left)
            assertEquals(thickness.center.y, fill.center.y, 1f)
            assertTrue(fill.top >= reference.bottom)
            val referenceLabel = context.getString(R.string.wall_control_line_label)
            val references = listOf(R.string.wall_control_line_center, R.string.wall_control_line_inner, R.string.wall_control_line_outer)
            for (selected in references) {
                compose.onNodeWithContentDescription("$referenceLabel ${context.getString(selected)}").performClick().assertIsSelected()
                references.filter { it != selected }.forEach {
                    compose.onNodeWithContentDescription("$referenceLabel ${context.getString(it)}").assertIsNotSelected()
                }
            }
            compose.onNodeWithContentDescription(context.getString(R.string.wall_fill_blank))
                .assertIsDisplayed().performClick().assertIsSelected()
            scenario.onActivity { activity ->
                val field = SketchActivity::class.java.getDeclaredField("sketchView").apply { isAccessible = true }
                val view = field.get(activity) as SketchView
                assertEquals(com.example.boschlaser.shared.model.WallFill.BLANK, view.snapshotDocument().walls.values.single().fill)
                assertEquals(com.example.boschlaser.shared.model.WallFill.BLACK, view.drawingWallFill)
            }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_tool_column)).performClick()
            assertCompactColumnFillSwitch()
            compose.onNodeWithContentDescription(context.getString(R.string.column_fill_white)).performClick().assertIsSelected()
            compose.onNodeWithContentDescription(context.getString(R.string.column_fill_black)).assertIsNotSelected()
            compose.onNodeWithTag("sketch-canvas").performTouchInput {
                click(androidx.compose.ui.geometry.Offset(width * .5f, height * .7f))
            }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_tool_select)).performClick()
            compose.onNodeWithTag("sketch-canvas").performTouchInput {
                click(androidx.compose.ui.geometry.Offset(width * .5f, height * .7f))
            }
            assertCompactColumnFillSwitch()
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_column_width)).assertDoesNotExist()
            compose.onNodeWithContentDescription(context.getString(R.string.column_rotation_angle)).assertDoesNotExist()
            compose.onNodeWithContentDescription(context.getString(R.string.column_fill_black)).assertIsDisplayed().performClick().assertIsSelected()
            compose.onNodeWithContentDescription(context.getString(R.string.column_fill_white)).assertIsNotSelected()
            scenario.onActivity { activity ->
                val field = SketchActivity::class.java.getDeclaredField("sketchView").apply { isAccessible = true }
                val view = field.get(activity) as SketchView
                assertEquals(com.example.boschlaser.shared.model.ColumnFill.BLACK, view.snapshotDocument().columns.values.single().fill)
                assertEquals(com.example.boschlaser.shared.model.ColumnFill.WHITE, view.drawingColumnFill)
            }
        } finally {
            finishAndCloseScenario(scenario)
            runBlocking { services.flush().await(); services.delete(project.manifest.projectId).await() }
        }
    }

    @Test fun measureAndSplitAppearOnSecondRowAndKeepToolSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val services = ProjectServices.get(context)
        val project = runBlocking { services.createSketch("Toolbar fixture").await() }
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        try {
            fun button(label: Int) = compose.onNodeWithContentDescription(context.getString(label))
            val grid = button(R.string.sketch_grid_snap).fetchSemanticsNode().boundsInRoot
            val node = button(R.string.sketch_endpoint_move_together)
            node.assertIsOff().assertIsDisplayed()
            val nodeBounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue(nodeBounds.left >= grid.right)
            assertTrue(nodeBounds.right <= compose.onNodeWithTag("sketch-canvas").fetchSemanticsNode().boundsInRoot.right)
            assertEquals(grid.center.y, nodeBounds.center.y, 1f)
            node.performClick()
            button(R.string.sketch_endpoint_move_together).assertIsOn().performClick()
            button(R.string.sketch_endpoint_move_together).assertIsOff()
            val wall = button(R.string.sketch_tool_wall).fetchSemanticsNode().boundsInRoot
            val join = button(R.string.sketch_join).fetchSemanticsNode().boundsInRoot
            val measure = button(R.string.sketch_tool_measure)
            val split = button(R.string.sketch_tool_split)
            for (tool in listOf(measure, split)) {
                tool.assertIsDisplayed()
                val bounds = tool.fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.top >= wall.bottom)
                assertEquals(join.center.y, bounds.center.y, 1f)
            }
            button(R.string.sketch_tool_wall).performClick()
            measure.assertDoesNotExist()
            split.assertDoesNotExist()
            button(R.string.sketch_join).assertDoesNotExist()
            button(R.string.sketch_extend).assertDoesNotExist()
            val lineLabel = context.getString(R.string.wall_control_line_label)
            fun line(label: Int) = compose.onNodeWithContentDescription("$lineLabel ${context.getString(label)}")
            line(R.string.wall_control_line_center).assertIsDisplayed().assertIsSelected()
            assertCompactReferenceSwitch()
            val thickness = compose.onNodeWithContentDescription(context.getString(R.string.sketch_drawing_wall_thickness, "240"))
            thickness.assertIsDisplayed()
            assertEquals(line(R.string.wall_control_line_center).fetchSemanticsNode().boundsInRoot.center.y,
                thickness.fetchSemanticsNode().boundsInRoot.center.y, 1f)
            val fillBounds = compose.onNodeWithTag("wall-fill-switch").fetchSemanticsNode().boundsInRoot
            val thicknessBounds = thickness.fetchSemanticsNode().boundsInRoot
            assertTrue(fillBounds.right <= thicknessBounds.left)
            assertTrue(thicknessBounds.right <= line(R.string.wall_control_line_inner).fetchSemanticsNode().boundsInRoot.left)
            var currentThickness = "240"
            for (preset in listOf("50", "60", "100", "120", "200", "240", "300")) {
                compose.onNodeWithContentDescription(context.getString(R.string.sketch_drawing_wall_thickness, currentThickness)).performClick()
                compose.onNode(hasText(preset) and isSelectable()).assertIsDisplayed().performClick()
                compose.onNodeWithContentDescription(context.getString(R.string.sketch_wall_thickness)).assertDoesNotExist()
                compose.onNodeWithContentDescription(context.getString(R.string.sketch_drawing_wall_thickness, preset)).assertIsDisplayed()
                currentThickness = preset
            }
            line(R.string.wall_control_line_inner).performClick().assertIsSelected()
            line(R.string.wall_control_line_center).assertIsNotSelected()
            line(R.string.wall_control_line_outer).assertIsNotSelected()
            line(R.string.wall_control_line_outer).performClick().assertIsSelected()
            line(R.string.wall_control_line_inner).assertIsNotSelected()
            line(R.string.wall_control_line_center).assertIsNotSelected()
            button(R.string.sketch_tool_door).performClick()
            line(R.string.wall_control_line_outer).assertDoesNotExist()
            measure.assertDoesNotExist()
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_creation_opening_width, "900")).assertIsDisplayed()
            button(R.string.sketch_tool_window).performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_creation_opening_width, "1200")).performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_opening_width)).performTextReplacement("850")
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_property_apply) + " " +
                context.getString(R.string.sketch_opening_width)).performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_creation_opening_width, "850")).assertIsDisplayed()
            button(R.string.sketch_tool_column).performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_creation_column_angle, "0")).performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.column_rotation_angle)).performTextReplacement("37.5")
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_property_apply) + " " +
                context.getString(R.string.column_rotation_angle)).performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_creation_column_angle, "37.5")).assertIsDisplayed()
            button(R.string.sketch_tool_select).performClick()
            measure.performClick().assertIsOn()
            split.assertIsOff().performClick().assertIsOn()
            measure.assertIsOff()
            button(R.string.sketch_tool_select).performClick().assertIsOn()
            split.assertIsOff()
            button(R.string.sketch_endpoint_move_together).performClick()
            scenario.recreate()
            button(R.string.sketch_endpoint_move_together).assertIsOn().assertIsDisplayed()
            compose.onNodeWithText("三墙以上节点：拆开此端 · 点击切换").assertDoesNotExist()
            compose.onNodeWithText("三墙以上节点：保持连接移动 · 点击切换").assertDoesNotExist()
        } finally {
            finishAndCloseScenario(scenario)
            runBlocking {
                services.flush().await()
                services.delete(project.manifest.projectId).await()
            }
        }
    }
}
