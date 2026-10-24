package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.SelectionActions
import com.example.boschlaser.ui.SelectionActionTarget
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectionActionsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }

    @Test fun wallDoorAndEmptySelectionExposeOnlyTheirOwnActions() {
        val target = mutableStateOf(SelectionActionTarget.NONE)
        val actions = mutableListOf<String>()
        compose.setContent { MaterialTheme {
            SelectionActions(target.value, "Straighten fixture", "Hinge fixture", "Side fixture",
                { actions += "straighten" }, { actions += "hinge" }, { actions += "side" },
                { Text("W") }, { Text("H") }, { Text("S") })
        } }
        compose.onNodeWithContentDescription("Straighten fixture").assertDoesNotExist()
        compose.onNodeWithContentDescription("Hinge fixture").assertDoesNotExist()
        compose.onNodeWithContentDescription("Side fixture").assertDoesNotExist()
        compose.runOnIdle { target.value = SelectionActionTarget.WALL }
        compose.onNodeWithContentDescription("Straighten fixture").performClick()
        compose.onNodeWithContentDescription("Hinge fixture").assertDoesNotExist()
        compose.runOnIdle { target.value = SelectionActionTarget.DOOR }
        compose.onNodeWithContentDescription("Straighten fixture").assertDoesNotExist()
        compose.onNodeWithContentDescription("Hinge fixture").performClick()
        compose.onNodeWithContentDescription("Side fixture").performClick()
        compose.runOnIdle { assertEquals(listOf("straighten", "hinge", "side"), actions); target.value = SelectionActionTarget.NONE }
        compose.onNodeWithContentDescription("Hinge fixture").assertDoesNotExist()
        compose.onNodeWithContentDescription("Side fixture").assertDoesNotExist()
    }
}
