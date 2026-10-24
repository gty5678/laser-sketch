package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.PlacementOptions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlacementOptionsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }
    @Test fun choicesFollowHostStateAndDisappearInMoveMode() {
        val continuous = mutableStateOf(false)
        val visible = mutableStateOf(true)
        val choices = mutableListOf<Boolean>()
        compose.setContent { MaterialTheme {
            PlacementOptions(visible.value, continuous.value, "Placement fixture", "Single fixture", "Continuous fixture",
                onChange = { choices += it; continuous.value = it }, modifier = Modifier.testTag("options"))
        } }
        val bounds = compose.onNodeWithTag("options").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Single fixture").assertIsSelected()
        compose.onNodeWithContentDescription("Continuous fixture").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Single fixture").assertIsNotSelected().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(listOf(true, false), choices); visible.value = false }
        compose.onNodeWithContentDescription("Single fixture").assertDoesNotExist()
        compose.onNodeWithContentDescription("Continuous fixture").assertDoesNotExist()
        assertEquals(bounds, compose.onNodeWithTag("options").fetchSemanticsNode().boundsInRoot)
    }
}
