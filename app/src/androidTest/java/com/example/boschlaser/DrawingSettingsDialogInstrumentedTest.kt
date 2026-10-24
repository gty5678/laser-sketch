package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.DrawingSettingsDialog
import com.example.boschlaser.ui.DrawingSettingsLabels
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawingSettingsDialogInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }
    @Test fun draftsSubmitTogetherAndDismissNeverSubmits() {
        val visible = mutableStateOf(true)
        val applied = mutableListOf<Triple<String, String, Boolean>>()
        var dismissed = 0
        compose.setContent { MaterialTheme { if (visible.value) {
            DrawingSettingsDialog("100", "2.6", true, DrawingSettingsLabels("Settings fixture", "Grid fixture", "Grid hint",
                "Magnifier fixture", "Magnifier hint", "Apply fixture", "Cancel fixture", "Automatic dimensions fixture"),
                { grid, magnifier, automatic -> applied += Triple(grid, magnifier, automatic); visible.value = false },
                { dismissed++; visible.value = false })
        } } }
        compose.onNodeWithContentDescription("Grid fixture").performTextReplacement("250.25")
        compose.onNodeWithContentDescription("Magnifier fixture").performTextReplacement("")
        compose.onNodeWithText("Automatic dimensions fixture").assertIsOn().performClick().assertIsOff()
        compose.runOnIdle { assertEquals(emptyList<Triple<String, String, Boolean>>(), applied) }
        compose.onNodeWithText("Apply fixture").performClick()
        compose.runOnIdle { assertEquals(listOf(Triple("250.25", "", false)), applied); visible.value = true }
        compose.onNodeWithContentDescription("Grid fixture").assertTextContains("100")
        compose.onNodeWithText("Automatic dimensions fixture").assertIsOn().performClick()
        compose.onNodeWithText("Cancel fixture").performClick()
        compose.runOnIdle { assertEquals(1, dismissed); assertEquals(1, applied.size) }
        compose.onNodeWithText("Settings fixture").assertDoesNotExist()
    }
}
