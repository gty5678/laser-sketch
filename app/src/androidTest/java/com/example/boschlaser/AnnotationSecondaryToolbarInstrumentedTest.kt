package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotationSecondaryToolbarInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }

    @Test fun toolChangesSelectionAndEditingKeepToolbarAndCanvasBoundsStable() {
        val state = mutableStateOf(0)
        compose.setContent { MaterialTheme {
            AnnotationScreen(Color.White, 12.dp, 10.dp,
                toolbar = { Box(Modifier.fillMaxWidth().height(48.dp)) },
                placementOptions = { AnnotationSecondaryToolbar(scrollable = false) {
                    when (state.value) {
                        0 -> PlacementOptions(true, false, "Placement", "Single", "Continuous", {})
                    }
                } },
                canvas = { Box(Modifier.fillMaxSize().testTag("photo")) }, actions = {}, properties = {
                    if (state.value == 1) AnnotationEditingPanel {
                        AnnotationDimensionMenu("Dimension", "120 mm", "Done", 1) { Text("Quantity editor") }
                    }
                })
        } }
        val photo = compose.onNodeWithTag("photo").fetchSemanticsNode().boundsInRoot
        val tools = compose.onNodeWithTag("annotation-secondary-toolbar").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("annotation-secondary-toolbar").assertHeightIsEqualTo(52.dp)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange)).assertCountEquals(0)
        compose.onNodeWithContentDescription("Continuous").assertIsDisplayed()
        compose.runOnIdle { state.value = 1 }
        compose.onNodeWithTag("annotation-secondary-toolbar").onChildren().assertCountEquals(0)
        compose.onNodeWithTag("annotation-editing-panel").assertIsDisplayed()
        compose.onNodeWithTag("annotation-property-handle").assertDoesNotExist()
        compose.onNodeWithText("Quantity editor").assertDoesNotExist()
        compose.onNodeWithTag("annotation-dimension-menu").performClick()
        compose.onNodeWithText("Quantity editor").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Quantity editor").assertDoesNotExist()
        assertEquals(photo, compose.onNodeWithTag("photo").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { state.value = 2 }
        compose.onNodeWithTag("annotation-editing-panel").assertDoesNotExist()
        assertEquals(photo, compose.onNodeWithTag("photo").fetchSemanticsNode().boundsInRoot)
        assertEquals(tools, compose.onNodeWithTag("annotation-secondary-toolbar").fetchSemanticsNode().boundsInRoot)
    }
}
