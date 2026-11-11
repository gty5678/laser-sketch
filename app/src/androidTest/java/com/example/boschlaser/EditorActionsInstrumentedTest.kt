package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    @Test fun undoPositionStaysFixedWhenTrailingActionIsHidden() {
        val delete = mutableStateOf(false)
        val confirm = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                    EditorActions(delete.value, confirm.value, "Undo fixture", "Delete fixture", "Confirm fixture",
                        {}, {}, {}, { Text("U") }, { Text("D") }, { Text("C") })
                }
            }
        }
        val initial = compose.onNodeWithContentDescription("Undo fixture").fetchSemanticsNode().boundsInRoot
        for ((showDelete, showConfirm) in listOf(true to false, false to true, false to false)) {
            compose.runOnIdle { delete.value = showDelete; confirm.value = showConfirm }
            val bounds = compose.onNodeWithContentDescription("Undo fixture").fetchSemanticsNode().boundsInRoot
            assertEquals(initial.left, bounds.left, 0.5f)
            assertEquals(initial.right, bounds.right, 0.5f)
        }
    }

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
