package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.AnnotationScreen
import com.example.boschlaser.ui.AnnotationActions
import androidx.compose.material3.Text
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotationScreenInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }

    @Test fun propertiesOverlayCanvasWithoutChangingPhotoViewportAndActionsStayAtEnd() {
        val showProperties = mutableStateOf(false)
        var canvasClicks = 0
        var propertyClicks = 0
        compose.setContent {
            Box(Modifier.requiredSize(300.dp, 500.dp)) {
                AnnotationScreen(Color.White, 12.dp, 10.dp,
                    toolbar = { Box(Modifier.fillMaxWidth().height(40.dp).testTag("toolbar")) },
                    placementOptions = { Box(Modifier.fillMaxWidth().height(30.dp).testTag("placement")) },
                    canvas = { Box(Modifier.fillMaxSize().testTag("canvas").clickable { canvasClicks++ }) },
                    actions = { Box(Modifier.size(52.dp, 180.dp).testTag("actions")) },
                    properties = { if (showProperties.value) {
                        Box(Modifier.fillMaxWidth().height(100.dp).testTag("properties").clickable { propertyClicks++ })
                    } })
            }
        }
        compose.onNodeWithTag("canvas").assertWidthIsEqualTo(300.dp).assertHeightIsEqualTo(430.dp)
        compose.onNodeWithTag("properties").assertDoesNotExist()
        val originalCanvas = compose.onNodeWithTag("canvas").fetchSemanticsNode().boundsInRoot
        val actions = compose.onNodeWithTag("actions").fetchSemanticsNode().boundsInRoot
        val density = originalCanvas.width / 300f
        assertEquals(originalCanvas.right - 12f * density, actions.right, 1f)
        assertEquals(originalCanvas.center.y, actions.center.y, 1f)
        compose.runOnIdle { showProperties.value = true }
        val properties = compose.onNodeWithTag("properties").fetchSemanticsNode().boundsInRoot
        assertEquals(originalCanvas, compose.onNodeWithTag("canvas").fetchSemanticsNode().boundsInRoot)
        assertEquals(originalCanvas.left + 10f * density, properties.left, 1f)
        assertEquals(originalCanvas.right - 10f * density, properties.right, 1f)
        assertEquals(originalCanvas.bottom - 10f * density, properties.bottom, 1f)
        compose.onNodeWithTag("properties").performClick()
        compose.runOnIdle { assertEquals(1, propertyClicks); assertEquals(0, canvasClicks); showProperties.value = false }
        compose.onNodeWithTag("properties").assertDoesNotExist()
        compose.onNodeWithTag("canvas").performClick()
        compose.runOnIdle { assertEquals(1, canvasClicks) }
    }

    @Test fun deleteRemainsTouchableWhenTallPropertiesOverlapItsPosition() {
        var deletions = 0
        var propertyClicks = 0
        var canvasClicks = 0
        compose.setContent {
            Box(Modifier.requiredSize(300.dp, 500.dp)) {
                AnnotationScreen(Color.White, 12.dp, 10.dp,
                    toolbar = { Box(Modifier.height(40.dp)) },
                    placementOptions = {},
                    canvas = { Box(Modifier.fillMaxSize().clickable { canvasClicks++ }) },
                    actions = {
                        AnnotationActions(false, true, "Finish fixture", "Delete fixture",
                            {}, { deletions++ }, { Text("F") }, { Text("D") })
                    },
                    properties = {
                        Box(Modifier.fillMaxWidth().height(320.dp).clickable { propertyClicks++ })
                    })
            }
        }
        compose.onNodeWithContentDescription("Delete fixture").performClick()
        compose.runOnIdle {
            assertEquals(1, deletions)
            assertEquals(0, propertyClicks)
            assertEquals(0, canvasClicks)
        }
    }
}
