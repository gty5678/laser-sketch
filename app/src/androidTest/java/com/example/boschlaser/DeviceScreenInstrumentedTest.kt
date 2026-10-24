package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
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
class DeviceScreenInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }
    private val labels = DeviceLabels("Device fixture", "Scan", "Connect saved", "Disconnect")
    @Test fun disconnectedWithoutSavedPermissionAllowsOnlyScanRequest() {
        var scans = 0
        compose.setContent { MaterialTheme { DeviceScreen(DeviceScreenState("Disconnected", "No saved fixture"), labels, { scans++ }, {}, {}) } }
        compose.onNodeWithText(labels.connectSaved).assertIsNotEnabled()
        compose.onNodeWithText(labels.disconnect).assertIsNotEnabled()
        compose.onNodeWithText(labels.scan).performClick()
        compose.runOnIdle { assertEquals(1, scans) }
    }
    @Test fun readyTransitionUpdatesControlsAndUsesHostCallbacks() {
        val state = mutableStateOf(DeviceScreenState("Disconnected", "Saved fixture", canConnectSaved = true))
        val actions = mutableListOf<String>()
        compose.setContent { MaterialTheme { DeviceScreen(state.value, labels, { actions += "scan" }, { actions += "saved" }, { actions += "disconnect" }) } }
        compose.onNodeWithText(labels.connectSaved).performClick()
        compose.runOnIdle { state.value = state.value.copy(status = "Ready fixture", connected = true) }
        compose.onNodeWithText("Ready fixture").assertIsDisplayed()
        compose.onNodeWithText(labels.connectSaved).assertIsNotEnabled()
        compose.onNodeWithText(labels.disconnect).performClick()
        compose.runOnIdle { assertEquals(listOf("saved", "disconnect"), actions) }
    }
    @Test fun hostStatusAndDeviceChangeWithoutRecreatingScreen() {
        val state = mutableStateOf(DeviceScreenState("Scanning fixture", "First fixture"))
        compose.setContent { MaterialTheme { DeviceScreen(state.value, labels, {}, {}, {}) } }
        compose.runOnIdle { state.value = DeviceScreenState("Permission fixture", "Second fixture") }
        compose.onNodeWithText("Scanning fixture").assertDoesNotExist()
        compose.onNodeWithText("First fixture").assertDoesNotExist()
        compose.onNodeWithText("Permission fixture").assertIsDisplayed()
        compose.onNodeWithText("Second fixture").assertIsDisplayed()
    }
}
