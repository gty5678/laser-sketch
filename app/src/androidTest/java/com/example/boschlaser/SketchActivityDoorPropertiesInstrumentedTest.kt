package com.example.boschlaser

import com.example.boschlaser.shared.editor.Selection

import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.runBlocking
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchActivityDoorPropertiesInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test
    fun doorFlipActionsAreVerticalCircularButtonsOnTheCanvasRightSide() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val wall = SketchWall(id = "wall", start = SketchPoint(0f, 0f), end = SketchPoint(3000f, 0f))
        val door = SketchOpening(
            id = "door",
            type = SketchOpeningType.DOOR,
            wallId = wall.id,
            position = .5f,
        )
        val projectId = SketchProjectStore.create(context)
        check(SketchProjectStore.save(context, projectId, SketchState(walls = listOf(wall), openings = listOf(door))))

        val scenario = ActivityScenario.launch<SketchActivity>(
            Intent(context, SketchActivity::class.java).putExtra(SketchActivity.EXTRA_PROJECT_ID, projectId),
        )
        try {
            val deadline = SystemClock.uptimeMillis() + 5000
            var loaded = false
            while (!loaded && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    val view = SketchActivity::class.java.getDeclaredField("sketchView").apply { isAccessible = true }.get(activity) as SketchView
                    loaded = wall.id in view.snapshotDocument().walls
                }
                if (!loaded) SystemClock.sleep(20)
            }
            assertTrue("Project must finish asynchronous loading", loaded)
            scenario.onActivity { activity ->
                val sketchView = SketchActivity::class.java.getDeclaredField("sketchView").apply {
                    isAccessible = true
                }.get(activity) as SketchView
                sketchView.selectEntity(Selection.Opening(door.id))
            }
            instrumentation.waitForIdleSync()
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_tool_select)).assertExists()
            compose.onNodeWithContentDescription("保存草稿").assertDoesNotExist()
            compose.onAllNodes((hasContentDescription(context.getString(R.string.door_flip_left_right)) or
                hasContentDescription(context.getString(R.string.door_flip_up_down))) and
                hasAnyAncestor(hasTestTag("sketch-property-sheet"))).assertCountEquals(0)
            val leftRight = compose.onNodeWithContentDescription(context.getString(R.string.door_flip_left_right))
            val upDown = compose.onNodeWithContentDescription(context.getString(R.string.door_flip_up_down))
            val leftRightBounds = leftRight.fetchSemanticsNode().boundsInWindow
            val upDownBounds = upDown.fetchSemanticsNode().boundsInWindow
            assertTrue(leftRightBounds.left > context.resources.displayMetrics.widthPixels / 2)
            assertEquals(leftRightBounds.left, upDownBounds.left, 1f)
            assertTrue(leftRightBounds.bottom < upDownBounds.top)
            for (node in listOf(leftRight, upDown)) {
                val bounds = node.fetchSemanticsNode().boundsInRoot
                val size = 56f * context.resources.displayMetrics.density
                assertEquals(size, bounds.width, 1f)
                assertEquals(size, bounds.height, 1f)
                // The top midpoint is filled; a point outside the circular outline is not.
                val image = node.captureToImage()
                val pixels = image.toPixelMap()
                val filled = pixels[image.width / 2, image.height / 10]
                val outside = pixels[1, image.height / 4]
                assertEquals(122f / 255f, filled.green, .08f)
                assertEquals(92f / 255f, filled.blue, .08f)
                assertTrue(kotlin.math.abs(outside.red - filled.red) + kotlin.math.abs(outside.green - filled.green) +
                    kotlin.math.abs(outside.blue - filled.blue) > .15f)
            }
            leftRight.performClick()
            upDown.performClick()
            scenario.onActivity { activity ->
                val opening = (SketchActivity::class.java.getDeclaredField("sketchView").apply {
                    isAccessible = true
                }.get(activity) as SketchView).selectedOpening()
                assertTrue(opening?.hingeFlipped == true)
                assertTrue(opening?.flipped == true)
            }
            compose.onNodeWithContentDescription(context.getString(R.string.sketch_tool_wall)).performClick()
            leftRight.assertDoesNotExist()
            upDown.assertDoesNotExist()
            runBlocking {
                val services = ProjectServices.get(context)
                services.flush().await()
                val savedOpening = services.loadSketch("sketch-$projectId").await()?.sketch?.openings?.values?.single()
                assertTrue(savedOpening?.hingeFlipped == true)
                assertTrue(savedOpening?.flipped == true)
            }
            // Editing the migrated project must not rewrite the preserved original.
            val originalOpening = SketchProjectStore.load(context, projectId)?.state?.openings?.single()
            assertTrue(originalOpening?.hingeFlipped == false)
            assertTrue(originalOpening?.flipped == false)
        } finally {
            finishAndCloseScenario(scenario)
            runBlocking {
                val services = ProjectServices.get(context)
                services.flush().await()
                services.delete("sketch-$projectId").await()
            }
            SketchProjectStore.delete(context, projectId)
        }
    }

}
