package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.CustomColorLabels
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
    @Test fun customColorPreviewAndCancelDoNotSubmitAndApplyCommitsOnce() {
        val selected = mutableStateOf<Int?>(-65536)
        val actions = mutableListOf<Int>()
        compose.setContent { MaterialTheme {
            ColorPalette(selected.value, "Color", listOf(ColorOption(-65536, "Red")),
                CustomColorLabels("Custom", "Hex", "Wheel", "Brightness", "Apply", "Cancel")) {
                actions += it; selected.value = it
            }
        } }
        compose.onNodeWithTag("annotation-custom-color").performScrollTo().performClick()
        compose.onNodeWithTag("annotation-color-hex").performTextReplacement("12345G")
        compose.onNodeWithText("Apply").assertIsNotEnabled()
        compose.onNodeWithTag("annotation-color-hex").performTextReplacement("123ABC")
        compose.runOnIdle { assertEquals(emptyList<Int>(), actions) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(-65536, selected.value) }
        compose.onNodeWithTag("annotation-custom-color").performClick()
        compose.onNodeWithTag("annotation-color-hex").performTextReplacement("123ABC")
        compose.onNodeWithText("Apply").performClick()
        compose.runOnIdle { assertEquals(listOf(0xFF123ABC.toInt()), actions) }
        compose.onNodeWithText("Hex").assertDoesNotExist()
    }

    @Test fun tappingAndDraggingTheWheelOnlyChangesPreviewUntilApply() {
        val actions = mutableListOf<Int>()
        compose.setContent { MaterialTheme {
            ColorPalette(-65536, "Color", emptyList(),
                CustomColorLabels("Custom", "Hex", "Wheel", "Brightness", "Apply", "Cancel")) { actions += it }
        } }
        compose.onNodeWithTag("annotation-custom-color").performScrollTo().performClick()
        compose.onNodeWithTag("annotation-color-wheel").performScrollTo().performTouchInput { click(center) }
        compose.onNodeWithTag("annotation-color-hex").assertTextContains("FFFFFF")
        compose.runOnIdle { assertEquals(emptyList<Int>(), actions) }
        compose.onNodeWithTag("annotation-color-wheel").performScrollTo().performTouchInput {
            swipe(center, androidx.compose.ui.geometry.Offset(width * .75f, height * .5f))
        }
        compose.runOnIdle { assertEquals(emptyList<Int>(), actions) }
        compose.onNodeWithText("Apply").performClick()
        compose.runOnIdle {
            assertEquals(1, actions.size)
            org.junit.Assert.assertNotEquals(-65536, actions.single())
        }
    }

}
