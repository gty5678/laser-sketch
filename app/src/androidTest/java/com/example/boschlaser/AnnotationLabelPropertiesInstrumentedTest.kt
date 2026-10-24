package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.AnnotationLabelProperties
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotationLabelPropertiesInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }
    @Test fun draftsSelectionRefreshAndFocusDismissalRemainUiState() {
        val text = mutableStateOf<TextFieldValue?>(TextFieldValue("Original", TextRange(8)))
        val angle = mutableStateOf<TextFieldValue?>(null)
        val dismiss = mutableStateOf(0)
        val edits = mutableListOf<String>()
        compose.setContent { MaterialTheme {
            AnnotationLabelProperties(text.value, angle.value, "Text fixture", "Angle fixture", "°", dismiss.value,
                { if (text.value?.text != it.text) edits += it.text; text.value = it },
                { if (angle.value?.text != it.text) edits += it.text; angle.value = it })
        } }
        compose.onNodeWithContentDescription("Text fixture").assertTextEquals("Original").performTextReplacement("Edited")
        compose.runOnIdle { text.value = TextFieldValue("Refreshed"); assertEquals(listOf("Edited"), edits) }
        compose.onNodeWithContentDescription("Text fixture").assertTextEquals("Refreshed").performClick().assertIsFocused()
        compose.runOnIdle { dismiss.value++ }
        compose.onNodeWithContentDescription("Text fixture").assertIsNotFocused()
        compose.runOnIdle { text.value = null; angle.value = TextFieldValue("45") }
        compose.onNodeWithContentDescription("Text fixture").assertDoesNotExist()
        compose.onNodeWithContentDescription("Angle fixture").performTextReplacement("90.5")
        compose.runOnIdle { assertEquals(listOf("Edited", "90.5"), edits) }
    }
}
