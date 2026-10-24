package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.shared.annotation.AnnotationMode
import com.example.boschlaser.ui.AnnotationToolbar
import com.example.boschlaser.ui.AnnotationToolItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotationToolbarInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }

    @Test fun sharedToolsFollowHostStateAndPlatformActionsUseTheirOwnCallbacks() {
        val selected = mutableStateOf(AnnotationMode.DIMENSION)
        val tools = AnnotationMode.entries.map { AnnotationToolItem(it, it.name) }
        val modes = mutableListOf<AnnotationMode>()
        val actions = mutableListOf<String>()
        compose.setContent { MaterialTheme {
            AnnotationToolbar(selected.value, tools, "Back fixture", "Share fixture", "Save fixture",
                { actions += "back" }, { actions += "share" }, { actions += "save" },
                { modes += it; selected.value = it }, { Text("B") }, { Text("S") }, { Text("G") }, { Text("T") })
        } }
        compose.onNodeWithContentDescription("DIMENSION").assertIsOn()
        for (mode in AnnotationMode.entries) {
            compose.onNodeWithContentDescription(mode.name).performScrollTo().performClick().assertIsOn()
            for (other in AnnotationMode.entries.filter { it != mode }) {
                compose.onNodeWithContentDescription(other.name).assertIsOff()
            }
        }
        compose.onNodeWithContentDescription("Back fixture").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Share fixture").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Save fixture").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(AnnotationMode.entries.toList(), modes)
            assertEquals(listOf("back", "share", "save"), actions)
        }
    }
}
