package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.NumberProperty
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NumberPropertyInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }
    @Test fun keyboardDoneSubmitsAndValidationErrorKeepsDraft() {
        val value = mutableStateOf(TextFieldValue("invalid"))
        val error = mutableStateOf<String?>(null)
        val submitted = mutableListOf<String>()
        compose.setContent { MaterialTheme {
            NumberProperty(value.value, "Width fixture", "Apply fixture", { value.value = it },
                { submitted += value.value.text; error.value = "Width rejected fixture" }, error = error.value)
        } }
        compose.onNodeWithContentDescription("Width fixture").performImeAction()
        compose.onNodeWithText("Width rejected fixture").assertIsDisplayed()
        compose.onNodeWithContentDescription("Width fixture").assertTextContains("invalid")
        compose.runOnIdle { assertEquals(listOf("invalid"), submitted) }
    }

    @Test fun draftsAndExternalRefreshNeverApplyUntilExplicitClick() {
        val value = mutableStateOf(TextFieldValue("900", TextRange(0, 3)))
        val accepted = mutableListOf<String>()
        compose.setContent { MaterialTheme {
            NumberProperty(value.value, "Width fixture", "Apply fixture", { value.value = it }, { accepted += value.value.text })
        } }
        compose.onNodeWithContentDescription("Width fixture").performTextReplacement("1200.123456789")
        compose.runOnIdle { assertEquals(emptyList<String>(), accepted) }
        compose.onNodeWithContentDescription("Apply fixture Width fixture").performClick()
        compose.runOnIdle { value.value = TextFieldValue("1000") }
        compose.onNodeWithContentDescription("Width fixture").assertTextContains("1000")
        compose.runOnIdle { assertEquals(listOf("1200.123456789"), accepted) }
        compose.onNodeWithContentDescription("Width fixture").performTextReplacement("")
        compose.onNodeWithContentDescription("Apply fixture Width fixture").performClick()
        compose.runOnIdle { assertEquals(listOf("1200.123456789", ""), accepted) }
    }
    @Test fun openingPresetsApplyImmediatelyAndAllowCustomWidth() {
        val value = mutableStateOf(TextFieldValue("750"))
        val submitted = mutableListOf<String>()
        compose.setContent { MaterialTheme {
            NumberProperty(value.value, "Width fixture", "Apply fixture", { value.value = it },
                { submitted += value.value.text }, openingWidthPresets = true)
        } }
        for (size in listOf("800", "900", "1000", "1100", "1200")) {
            compose.onNode(hasText(size) and isSelectable()).performClick().assertIsSelected()
            compose.onNodeWithContentDescription("Width fixture").assertTextContains(size)
        }
        compose.runOnIdle { assertEquals(listOf("800", "900", "1000", "1100", "1200"), submitted) }
        compose.onNodeWithContentDescription("Width fixture").performTextReplacement("850.5")
        compose.onNodeWithContentDescription("Width fixture").performImeAction()
        compose.runOnIdle { assertEquals(listOf("800", "900", "1000", "1100", "1200", "850.5"), submitted) }
    }

    @Test fun columnAnglePresetsSubmitAndKeepCustomInputAvailable() {
        val value = mutableStateOf(TextFieldValue("12.5"))
        val submitted = mutableListOf<String>()
        compose.setContent { MaterialTheme {
            NumberProperty(value.value, "Angle fixture", "Apply fixture", { value.value = it },
                { submitted += value.value.text }, columnAnglePresets = true)
        } }
        for (angle in listOf("0", "30", "45", "60", "90", "180")) {
            compose.onNode(hasText("$angle°") and isSelectable()).performClick().assertIsSelected()
            compose.onNodeWithContentDescription("Angle fixture").assertTextContains(angle)
        }
        compose.runOnIdle { assertEquals(listOf("0", "30", "45", "60", "90", "180"), submitted) }
        compose.onNodeWithContentDescription("Angle fixture").performTextReplacement("22.5")
        compose.onNodeWithContentDescription("Angle fixture").performImeAction()
        compose.runOnIdle { assertEquals("22.5", submitted.last()) }
    }

}
