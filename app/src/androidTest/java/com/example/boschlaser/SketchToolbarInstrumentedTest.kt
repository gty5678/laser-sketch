package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.shared.editor.ToolType
import com.example.boschlaser.ui.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchToolbarInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }
    @Test fun selectionUsesCoreToolIdentityAndHostStateControlsHighlight() {
        val selected = mutableStateOf(ToolType.SELECT)
        val actions = mutableListOf<ToolType>()
        compose.setContent {
            MaterialTheme {
                SketchToolbar(selected.value, listOf(SketchToolItem(ToolType.SELECT, "Select fixture"), SketchToolItem(ToolType.WALL, "Wall fixture")),
                    "Back fixture", "Settings fixture", {}, {}, { actions += it; selected.value = it },
                    { Text("B") }, { Text("S") }, { Text(it.name) })
            }
        }
        compose.onNodeWithContentDescription("Select fixture").assertIsOn()
        compose.onNodeWithContentDescription("Wall fixture").performClick().assertIsOn()
        compose.onNodeWithContentDescription("Select fixture").assertIsOff()
        compose.runOnIdle { assertEquals(listOf(ToolType.WALL), actions) }
    }
    @Test fun backAndSettingsRemainHostActions() {
        val actions = mutableListOf<String>()
        compose.setContent {
            MaterialTheme { SketchToolbar(ToolType.SELECT, emptyList(), "Back fixture", "Settings fixture",
                { actions += "back" }, { actions += "settings" }, {}, { Text("B") }, { Text("S") }, {}) }
        }
        compose.onNodeWithContentDescription("Back fixture").performClick()
        compose.onNodeWithContentDescription("Settings fixture").performClick()
        compose.runOnIdle { assertEquals(listOf("back", "settings"), actions) }
    }
}
