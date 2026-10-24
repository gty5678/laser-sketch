package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.shared.model.WallAlignment
import com.example.boschlaser.ui.WallControlLinePicker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WallControlLinePickerInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }
    @Test fun selectionUsesCoreAlignmentAndRefreshNeverSendsCommands() {
        val selected = mutableStateOf(WallAlignment.CENTER)
        val choices = mutableListOf<WallAlignment>()
        compose.setContent { MaterialTheme {
            WallControlLinePicker(selected.value, "Line fixture", listOf("Center", "Inner", "Outer")) { choices += it }
        } }
        compose.onNodeWithContentDescription("Line fixture Center").assertIsSelected()
        compose.onNodeWithContentDescription("Line fixture Inner").performClick()
        compose.onNodeWithContentDescription("Line fixture Outer").performClick()
        compose.runOnIdle { assertEquals(listOf(WallAlignment.LEFT, WallAlignment.RIGHT), choices); selected.value = WallAlignment.RIGHT }
        compose.onNodeWithContentDescription("Line fixture Center").assertIsNotSelected()
        compose.onNodeWithContentDescription("Line fixture Outer").assertIsSelected()
        compose.runOnIdle { assertEquals(2, choices.size) }
    }
}
