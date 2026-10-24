package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.ColorOption
import com.example.boschlaser.ui.ColorPalette
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColorPaletteInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }
    @Test fun argbSelectionAndExternalRefreshDoNotCreateExtraCommands() {
        val red = -65536
        val blue = -16776961
        val selected = mutableStateOf<Int?>(red)
        val colors = listOf(ColorOption(red, "Red fixture"), ColorOption(blue, "Blue fixture"))
        val actions = mutableListOf<Int>()
        compose.setContent { MaterialTheme { ColorPalette(selected.value, "Color fixture", colors) {
            actions += it; selected.value = it
        } } }
        compose.onNodeWithContentDescription("Red fixture").assertIsSelected()
        compose.onNodeWithContentDescription("Blue fixture").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Red fixture").assertIsNotSelected()
        compose.runOnIdle { selected.value = -1 }
        compose.onNodeWithContentDescription("Red fixture").assertIsNotSelected()
        compose.onNodeWithContentDescription("Blue fixture").assertIsNotSelected()
        compose.runOnIdle { selected.value = null }
        compose.onNodeWithContentDescription("Red fixture").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Blue fixture").assertIsNotEnabled()
        compose.runOnIdle { selected.value = red }
        compose.onNodeWithContentDescription("Red fixture").assertIsSelected().assertIsEnabled()
        compose.runOnIdle { assertEquals(listOf(blue), actions) }
    }
}
