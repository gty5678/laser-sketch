package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.AnnotationQuantityProperty
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotationQuantityPropertyInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }
    @Test fun externalDraftRefreshDoesNotSendValueOrUnitEdits() {
        val value = mutableStateOf<TextFieldValue?>(TextFieldValue("12.5"))
        val unit = mutableStateOf(0)
        val edits = mutableListOf<String>()
        compose.setContent { MaterialTheme {
            AnnotationQuantityProperty(value.value, unit.value, listOf("mm", "m", "cm"), "Value fixture", "Unit fixture", 0,
                { if (value.value?.text != it.text) edits += it.text; value.value = it },
                { edits += "unit$it"; unit.value = it })
        } }
        compose.onNodeWithContentDescription("Value fixture").performTextReplacement("25.75")
        compose.onNodeWithContentDescription("Unit fixture").performClick()
        compose.onNodeWithText("cm").performClick()
        compose.runOnIdle { value.value = TextFieldValue("Refreshed"); unit.value = 1 }
        compose.onNodeWithContentDescription("Value fixture").assertTextEquals("Refreshed")
        compose.runOnIdle { assertEquals(listOf("25.75", "unit2"), edits); value.value = null }
        compose.onNodeWithContentDescription("Value fixture").assertDoesNotExist()
        compose.onNodeWithContentDescription("Unit fixture").assertDoesNotExist()
    }
}
