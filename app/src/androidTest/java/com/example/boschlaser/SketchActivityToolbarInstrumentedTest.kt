package com.example.boschlaser

import android.content.Intent
import androidx.compose.ui.test.*
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

    @Test fun measureAndSplitAppearOnSecondRowAndKeepToolSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val services = ProjectServices.get(context)
        val project = runBlocking { services.createSketch("Toolbar fixture").await() }
        val scenario = ActivityScenario.launch<SketchActivity>(Intent(context, SketchActivity::class.java)
            .putExtra(SketchActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        try {
            fun button(label: Int) = compose.onNodeWithContentDescription(context.getString(label))
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
            measure.performClick().assertIsOn()
            split.assertIsOff().performClick().assertIsOn()
            measure.assertIsOff()
            button(R.string.sketch_tool_select).performClick().assertIsOn()
            split.assertIsOff()
        } finally {
            finishAndCloseScenario(scenario)
            runBlocking {
                services.flush().await()
                services.delete(project.manifest.projectId).await()
            }
        }
    }
}
