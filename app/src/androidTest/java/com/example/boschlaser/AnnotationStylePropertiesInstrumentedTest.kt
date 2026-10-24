package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.AnnotationStyleLabels
import com.example.boschlaser.ui.AnnotationStyleProperties
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotationStylePropertiesInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }
    @Test fun selectionRefreshDoesNotSendCommandsAndChoicesFollowAcceptedValues() {
        val size = mutableStateOf<Int?>(1)
        val background = mutableStateOf(true)
        val thickness = mutableStateOf<Int?>(null)
        val actions = mutableListOf<String>()
        val labels = AnnotationStyleLabels("Size", listOf("Small", "Medium", "Large"), "White background",
            "Width", listOf("Thin", "Medium", "Thick"))
        compose.setContent { MaterialTheme {
            AnnotationStyleProperties(size.value, background.value, thickness.value, labels,
                { actions += "size$it"; size.value = it }, { actions += "background$it"; background.value = it },
                { actions += "thickness$it"; thickness.value = it })
        } }
        compose.onNodeWithContentDescription("Size Medium").assertIsSelected()
        compose.runOnIdle { assertEquals(emptyList<String>(), actions) }
        compose.onNodeWithContentDescription("Size Large").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("White background").assertIsOn().performClick().assertIsOff()
        compose.runOnIdle { size.value = null; thickness.value = 0 }
        compose.onNodeWithContentDescription("Size Large").assertDoesNotExist()
        compose.onNodeWithContentDescription("White background").assertDoesNotExist()
        compose.onNodeWithContentDescription("Width Thin").assertIsSelected()
        compose.onNodeWithContentDescription("Width Thick").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(listOf("size2", "backgroundfalse", "thickness2"), actions); thickness.value = null }
        compose.onNodeWithContentDescription("Width Thick").assertDoesNotExist()
    }
}
