package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.WallFaceDistanceDialog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WallFaceDistanceDialogInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }

    @Test fun recentMeasurementsRefreshAndOnlySubmitAfterApply() {
        val recent = mutableStateOf(listOf("3000", "2000", "1000", "500"))
        val submitted = mutableListOf<String>()
        compose.setContent { MaterialTheme {
            WallFaceDistanceDialog("900", "Dimension fixture", "Hint fixture", "Value fixture",
                "Apply fixture", "Cancel fixture", "Error fixture",
                onApply = { submitted += it; false }, onDismiss = {},
                recentMeasurements = recent.value, recentMeasurementsLabel = "Recent fixture")
        } }
        compose.onNodeWithText("500 mm").assertDoesNotExist()
        compose.onNodeWithText("2000 mm").performClick()
        compose.onNodeWithContentDescription("Value fixture").assertTextContains("2000")
        compose.runOnIdle { assertEquals(emptyList<String>(), submitted) }
        compose.onNodeWithText("Apply fixture").performClick()
        compose.onNodeWithText("Error fixture").assertIsDisplayed()
        compose.runOnIdle { recent.value = listOf("4000", "3000", "2000") }
        compose.onNodeWithText("1000 mm").assertDoesNotExist()
        compose.onNodeWithText("4000 mm").performClick()
        compose.onNodeWithText("Error fixture").assertDoesNotExist()
        compose.onNodeWithContentDescription("Value fixture").performImeAction()
        compose.runOnIdle { assertEquals(listOf("2000", "4000"), submitted) }
    }
}
