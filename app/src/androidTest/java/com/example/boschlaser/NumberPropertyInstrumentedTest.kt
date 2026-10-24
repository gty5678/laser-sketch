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
}
