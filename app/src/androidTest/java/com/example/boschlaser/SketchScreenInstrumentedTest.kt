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
import com.example.boschlaser.ui.SketchScreen
import com.example.boschlaser.ui.SketchScreenStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchScreenInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }

    @Test fun insetsAndPropertyHeightPositionOverlaysWithoutChangingCanvas() {
        val showProperties = mutableStateOf(false)
        val propertyHeight = mutableStateOf(80.dp)
        var canvasClicks = 0
        var propertyClicks = 0
        compose.setContent {
            Box(Modifier.requiredSize(300.dp, 500.dp)) {
                SketchScreen(showProperties.value, 20.dp, 30.dp,
                    SketchScreenStyle(Color.White, 8.dp, 52.dp, 116.dp, 10.dp, 56.dp, 14.dp, 56.dp, 14.dp, 12.dp, 12.dp),
                    canvas = { Box(Modifier.fillMaxSize().testTag("canvas").clickable { canvasClicks++ }) },
                    toolbar = { Box(Modifier.fillMaxWidth().height(56.dp).testTag("toolbar")) },
                    editToolbar = { Box(Modifier.fillMaxSize().testTag("edit-toolbar")) },
                    actions = { Box(Modifier.size(120.dp, 56.dp).testTag("actions")) },
                    selectionActions = { Box(Modifier.size(56.dp, 120.dp).testTag("selection-actions")) },
                    gridSnap = { Box(Modifier.size(100.dp, 32.dp).testTag("snap")) },
                    properties = { Box(Modifier.fillMaxWidth().height(propertyHeight.value).testTag("properties")
                        .clickable { propertyClicks++ }) }, dialogs = {})
            }
        }
        val canvas = compose.onNodeWithTag("canvas").fetchSemanticsNode().boundsInRoot
        val density = canvas.height / 500f
        compose.onNodeWithTag("canvas").assertWidthIsEqualTo(300.dp).assertHeightIsEqualTo(500.dp)
        val toolbar = compose.onNodeWithTag("toolbar").fetchSemanticsNode().boundsInRoot
        val edit = compose.onNodeWithTag("edit-toolbar").fetchSemanticsNode().boundsInRoot
        assertEquals(canvas.top + 20 * density, toolbar.top, 1f)
        assertEquals(toolbar.bottom, edit.top, 1f)
        assertEquals(52 * density, edit.height, 1f)
        val actions = compose.onNodeWithTag("actions").fetchSemanticsNode().boundsInRoot
        assertEquals(canvas.top + 136 * density, actions.top, 1f)
        assertEquals(canvas.right - 10 * density, actions.right, 1f)
        val selection = compose.onNodeWithTag("selection-actions").fetchSemanticsNode().boundsInRoot
        assertEquals(canvas.center.y, selection.center.y, 1f)
        assertEquals(canvas.right - 14 * density, selection.right, 1f)
        val snap = compose.onNodeWithTag("snap").fetchSemanticsNode().boundsInRoot
        assertEquals(canvas.bottom - 44 * density, snap.bottom, 1f)
        assertEquals(canvas.center.x, snap.center.x, 1f)
        compose.onNodeWithTag("properties").assertDoesNotExist()
        compose.runOnIdle { showProperties.value = true }
        fun checkPanel() {
            val property = compose.onNodeWithTag("properties").fetchSemanticsNode().boundsInRoot
            val movedSnap = compose.onNodeWithTag("snap").fetchSemanticsNode().boundsInRoot
            assertEquals(canvas.bottom - 44 * density, property.bottom, 1f)
            assertEquals(canvas.left + 12 * density, property.left, 1f)
            assertEquals(canvas.right - 12 * density, property.right, 1f)
            assertEquals(property.top - 12 * density, movedSnap.bottom, 1f)
            assertEquals(canvas, compose.onNodeWithTag("canvas").fetchSemanticsNode().boundsInRoot)
        }
        checkPanel()
        compose.runOnIdle { propertyHeight.value = 140.dp }
        checkPanel()
        compose.onNodeWithTag("properties").performClick()
        compose.runOnIdle { assertEquals(1, propertyClicks); assertEquals(0, canvasClicks) }
        compose.runOnIdle { propertyHeight.value = 440.dp }
        compose.onNodeWithTag("properties").assertHeightIsEqualTo(440.dp)
        compose.runOnIdle { showProperties.value = false }
        compose.onNodeWithTag("properties").assertDoesNotExist()
        assertEquals(snap, compose.onNodeWithTag("snap").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("canvas").performClick()
        compose.runOnIdle { assertEquals(1, canvasClicks) }
    }
}
