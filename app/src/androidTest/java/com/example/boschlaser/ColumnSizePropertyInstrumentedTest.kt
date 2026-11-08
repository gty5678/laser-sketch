package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.ColumnSizeProperty
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColumnSizePropertyInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }
    @Test fun sizeDraftsSubmitTogetherAndCircleHidesDepth() {
        val width = mutableStateOf(TextFieldValue("300"))
        val depth = mutableStateOf(TextFieldValue("400"))
        val rectangle = mutableStateOf(true)
        val submitted = mutableListOf<Pair<String, String>>()
        compose.setContent { MaterialTheme {
            ColumnSizeProperty(width.value, if (rectangle.value) depth.value else null, "Width fixture", "Depth fixture",
                "Apply fixture", { width.value = it }, { depth.value = it }, { submitted += width.value.text to depth.value.text })
        } }
        compose.onNodeWithContentDescription("Width fixture").performTextReplacement("500.25")
        compose.onNodeWithContentDescription("Depth fixture").performTextReplacement("")
        compose.runOnIdle { assertEquals(emptyList<Pair<String, String>>(), submitted) }
        compose.onNodeWithContentDescription("Apply fixture Width fixture").performClick()
        compose.runOnIdle { assertEquals(listOf("500.25" to ""), submitted); rectangle.value = false }
        compose.onNodeWithContentDescription("Depth fixture").assertDoesNotExist()
        compose.onNodeWithContentDescription("Width fixture").assertTextContains("500.25")
    }
    @Test fun presetsFillBothDraftsAndCircleOnlyFillsDiameter() {
        val width = mutableStateOf(TextFieldValue("350"))
        val depth = mutableStateOf(TextFieldValue("450"))
        val rectangle = mutableStateOf(true)
        val submitted = mutableListOf<Pair<String, String>>()
        compose.setContent { MaterialTheme {
            ColumnSizeProperty(width.value, if (rectangle.value) depth.value else null, "Width fixture", "Depth fixture",
                "Apply fixture", { width.value = it }, { depth.value = it }, { submitted += width.value.text to depth.value.text })
        } }
        for (size in listOf("300", "400", "500", "600")) {
            compose.onNodeWithText("${size}×${size}").performClick().assertIsSelected()
            compose.onNodeWithContentDescription("Width fixture").assertTextContains(size)
            compose.onNodeWithContentDescription("Depth fixture").assertTextContains(size)
        }
        compose.runOnIdle {
            assertEquals(listOf("300" to "300", "400" to "400", "500" to "500", "600" to "600"), submitted)
            rectangle.value = false
        }
        compose.onNode(hasText("300") and isSelectable()).performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Width fixture").assertTextContains("300")
        compose.runOnIdle {
            assertEquals("600", depth.value.text)
            assertEquals("300" to "600", submitted.last())
        }
    }

}
