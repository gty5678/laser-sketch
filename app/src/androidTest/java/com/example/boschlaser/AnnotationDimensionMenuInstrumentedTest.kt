package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.AnnotationDimensionMenu
import com.example.boschlaser.ui.RecentMeasurements
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotationDimensionMenuInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }

    @Test fun measurementSourcesStayCollapsedAndSelectionChangeClosesMenu() {
        val selection = mutableStateOf(1)
        val applied = mutableListOf<Int>()
        compose.setContent { MaterialTheme {
            AnnotationDimensionMenu("Dimension", "120 mm", "Done", selection.value) {
                Text("Manual quantity")
                RecentMeasurements(listOf("120 mm", "240 mm", "360 mm"), true, { applied += it })
            }
        } }
        compose.onNodeWithText("Manual quantity").assertDoesNotExist()
        compose.onNodeWithText("240 mm").assertDoesNotExist()
        compose.onNodeWithTag("annotation-dimension-menu").performClick()
        compose.onNodeWithText("Manual quantity").assertIsDisplayed()
        compose.onNodeWithText("240 mm").performClick()
        compose.runOnIdle { assertEquals(listOf(1), applied) }
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("240 mm").assertDoesNotExist()
        compose.onNodeWithTag("annotation-dimension-menu").performClick()
        compose.runOnIdle { selection.value = 2 }
        compose.onNodeWithText("Manual quantity").assertDoesNotExist()
    }
}
