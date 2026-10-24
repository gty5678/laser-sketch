package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
class HomeScreenInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }

    @Test fun scrollingKeepsActionsAvailableAndDeviceToggleDoesNotResetContent() {
        val labels = HomeLabels("Import fixture", "Photos fixture", "Sketches fixture",
            "Add photo fixture", "Add sketch fixture", "Device fixture", "+")
        val style = HomeStyle(Color.Green, Color.Blue, Color.Green, Color.Gray, Color.White)
        val shown = mutableStateOf(false)
        val actions = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                HomeScreen(labels, style, connected = false, showDevice = shown.value,
                    onImport = { actions += "import" }, onAddPhoto = { actions += "photo" },
                    onAddSketch = { actions += "sketch" }, onToggleDevice = { shown.value = !shown.value },
                    photos = { Box(Modifier.height(1000.dp)) { Text("Photo content fixture") } },
                    sketches = { Text("Sketch content fixture") }, measurements = { Text("History fixture") },
                    device = { Text("Device panel fixture") }, deviceIcon = { Text("D") })
            }
        }
        compose.onNodeWithText(labels.importProject).performClick()
        compose.onNodeWithText("History fixture").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(labels.addPhoto).assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription(labels.addSketch).assertIsDisplayed().performClick()
        compose.onNodeWithText("Device panel fixture").assertDoesNotExist()
        compose.onNodeWithContentDescription(labels.device).performClick()
        compose.onNodeWithText("Device panel fixture").assertIsDisplayed()
        compose.onNodeWithContentDescription(labels.device).performClick()
        compose.onNodeWithText("Device panel fixture").assertDoesNotExist()
        compose.onNodeWithText("History fixture").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("import", "photo", "sketch"), actions) }
    }
}
