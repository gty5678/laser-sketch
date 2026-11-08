package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.MeasurementLabels
import com.example.boschlaser.ui.MeasurementRowState
import com.example.boschlaser.ui.MeasurementScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeasurementDeleteInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }

    @Test fun deleteCanBeUndoneAndLeavingScreenCancelsPendingDeletion() {
        val visible = mutableStateOf(true)
        val deleted = mutableListOf<String>()
        compose.setContent { MaterialTheme {
            if (visible.value) MeasurementScreen(listOf(MeasurementRowState("fixture", "Reading fixture")),
                "1000", true, MeasurementLabels("Current", "History", "Measure", "Clear", "Export", "Empty",
                    "Delete fixture", "Undo fixture", "Pending fixture"), { "More" }, {}, {}, {}, { deleted += it })
        } }
        compose.onNodeWithText("Delete fixture").performClick()
        compose.onNodeWithText("Pending fixture").assertIsDisplayed()
        compose.runOnIdle { assertEquals(emptyList<String>(), deleted) }
        compose.onNodeWithText("Undo fixture").performClick()
        compose.onNodeWithText("Pending fixture").assertDoesNotExist()
        compose.onNodeWithText("Delete fixture").performClick()
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithText("Delete fixture").assertIsDisplayed()
        compose.runOnIdle { assertEquals(emptyList<String>(), deleted) }
    }
}
