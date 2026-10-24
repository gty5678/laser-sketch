package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchEditToolsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }
    @Test fun wallActionsShowHostStateAndSendTheCorrectIntent() {
        val join = mutableStateOf(false)
        val extend = mutableStateOf(false)
        val actions = mutableListOf<String>()
        compose.setContent {
            MaterialTheme { SketchEditTools(join.value, extend.value, "Join fixture", "Extend fixture",
                { actions += "join"; join.value = !join.value }, { actions += "extend"; extend.value = !extend.value },
                { Text("J") }, { Text("E") }) }
        }
        compose.onNodeWithContentDescription("Join fixture").performClick().assertIsOn()
        compose.onNodeWithContentDescription("Extend fixture").assertIsOff()
        compose.onNodeWithContentDescription("Extend fixture").performClick().assertIsOn()
        compose.runOnIdle { assertEquals(listOf("join", "extend"), actions) }
    }
    @Test fun snapSwitchReflectsTheValueAcceptedByTheHost() {
        val enabled = mutableStateOf(true)
        val values = mutableListOf<Boolean>()
        compose.setContent { MaterialTheme { GridSnapControl(enabled.value, "Snap fixture") { values += it; enabled.value = it } } }
        compose.onNodeWithContentDescription("Snap fixture").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithContentDescription("Snap fixture").performClick().assertIsOn()
        compose.runOnIdle { assertEquals(listOf(false, true), values) }
    }
}
