package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.EditorActions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorActionsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }

    @Test fun visibilityAndCallbacksFollowHostState() {
        val delete = mutableStateOf(false)
        val confirm = mutableStateOf(false)
        val actions = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                EditorActions(delete.value, confirm.value, "Undo fixture", "Delete fixture", "Confirm fixture",
                    { actions += "undo" }, { actions += "delete" }, { actions += "confirm" },
                    { Text("U") }, { Text("D") }, { Text("C") })
            }
        }
        compose.onNodeWithContentDescription("Delete fixture").assertDoesNotExist()
        compose.onNodeWithContentDescription("Confirm fixture").assertDoesNotExist()
        compose.onNodeWithContentDescription("Undo fixture").performClick()
        compose.runOnIdle { delete.value = true; confirm.value = true }
        compose.onNodeWithContentDescription("Delete fixture").performClick()
        compose.onNodeWithContentDescription("Confirm fixture").performClick()
        compose.runOnIdle { assertEquals(listOf("undo", "delete", "confirm"), actions); delete.value = false }
        compose.onNodeWithContentDescription("Delete fixture").assertDoesNotExist()
    }
}
